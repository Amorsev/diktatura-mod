package com.diktatura.casino.poker;

import com.diktatura.casino.CasinoSettings;
import com.diktatura.economy.Economy;
import com.diktatura.law.LawContext;
import com.diktatura.law.LawEngine;
import com.diktatura.law.LawEvent;
import com.diktatura.network.PokerPayloads;
import com.diktatura.npc.ModEntities;
import com.diktatura.npc.NpcEntity;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Покер за столом госказино: связывает {@link PokerTable} с миром. Бай-ин и возврат фишек через трудодни,
 * кнопка «Готов», боты «думают» 1–2 секунды, на ход человека — 30 секунд (потом чек или фолд), отошёл от стола
 * дальше 8 блоков или вышел из игры — встал из-за стола. Крупье показывает жесты, стол — карты и фишки.
 * Экраны игроков и вид стола обновляются при каждом событии.
 */
public final class PokerGame {
	public static final int TURN_TICKS = 30 * 20;
	private static final int NEXT_HAND_TICKS = 6 * 20;
	private static final double MAX_DIST = 8.0;
	private static final int LOG_LINES = 9;

	private static PokerGame casino;

	private final PokerTable table = new PokerTable(CasinoSettings.get().poker().rules(), new Random());
	private final Random rng = new Random();
	private final boolean[] ready = new boolean[PokerTable.SEATS];
	private final Text[] names = new Text[PokerTable.SEATS];
	private final Text[] lastAction = new Text[PokerTable.SEATS];
	private final int[] won = new int[PokerTable.SEATS];
	private final Card[][] shown = new Card[PokerTable.SEATS][];
	private final Set<UUID> watchers = new HashSet<>();
	private final Deque<Text> log = new ArrayDeque<>();
	private int botCounter;

	private UUID tableEntity;
	private UUID dealerNpc;
	private long turnStart;
	private int turnSeat = -1;
	private long botAt;
	private long nextHandAt;
	/** Время мира: начало сдачи, открытие каждой общей карты, вскрытие, раздача банка — для анимаций на столе. */
	private long dealtAt;
	private final long[] boardAt = new long[5];
	private long showAt;
	private long potAt;
	private int potWinner = -1;
	private boolean dirty = true;

	private PokerGame() {
	}

	public static PokerGame casino() {
		if (casino == null) {
			casino = new PokerGame();
		}
		return casino;
	}

	// ------------------------------------------------------------------ стол в мире

	/** Казино (раз в 10 с): поставить стол на место, если его нет, и запомнить крупье. */
	public static void ensure(ServerWorld world, Vec3d center, NpcEntity dealer) {
		PokerGame g = casino();
		if (dealer != null) {
			g.dealerNpc = dealer.getUuid();
		}
		if (!world.isChunkLoaded((int) Math.floor(center.x) >> 4, (int) Math.floor(center.z) >> 4)) {
			return;
		}
		List<PokerTableEntity> found = world.getEntitiesByClass(PokerTableEntity.class, new Box(center, center).expand(6), e -> true);
		PokerTableEntity keep = null;
		for (PokerTableEntity e : found) {
			if (keep == null && e.getPos().squaredDistanceTo(center) < 0.25) {
				keep = e;
			} else {
				e.discard();
			}
		}
		if (keep == null) {
			keep = ModEntities.POKER_TABLE.create(world);
			if (keep == null) {
				return;
			}
			keep.refreshPositionAndAngles(center.x, center.y, center.z, 180.0f, 0.0f);
			world.spawnEntity(keep);
		}
		if (!keep.getUuid().equals(g.tableEntity)) {
			g.tableEntity = keep.getUuid();
			g.dirty = true;
		}
	}

	private PokerTableEntity entity(MinecraftServer server) {
		if (tableEntity == null) {
			return null;
		}
		Entity e = server.getOverworld().getEntity(tableEntity);
		return e instanceof PokerTableEntity t && !t.isRemoved() ? t : null;
	}

	private NpcEntity dealer(MinecraftServer server) {
		if (dealerNpc == null) {
			return null;
		}
		Entity e = server.getOverworld().getEntity(dealerNpc);
		return e instanceof NpcEntity n && !n.isRemoved() ? n : null;
	}

	private void gesture(MinecraftServer server, String name) {
		NpcEntity d = dealer(server);
		if (d != null) {
			d.gesture(name);
		}
	}

	private void sound(MinecraftServer server, SoundEvent sound, float pitch) {
		PokerTableEntity e = entity(server);
		if (e != null) {
			e.getWorld().playSound(null, e.getX(), e.getY() + 1, e.getZ(), sound, SoundCategory.BLOCKS, 0.6f, pitch);
		}
	}

	// ------------------------------------------------------------------ игроки

	/** ПКМ по столу или крупье: открыть экран покера. */
	public static void open(ServerPlayerEntity p, Entity source) {
		PokerGame g = casino();
		g.watchers.add(p.getUuid());
		g.send(p, true);
	}

	/** Действие с экрана. Все данные клиента проверяются. */
	public static void onAction(ServerPlayerEntity p, PokerPayloads.PokerAction a) {
		PokerGame g = casino();
		MinecraftServer server = p.getServer();
		if (server == null) {
			return;
		}
		if ("close".equals(a.action())) {
			g.watchers.remove(p.getUuid());
			return;
		}
		PokerTableEntity e = g.entity(server);
		if (e == null || p.squaredDistanceTo(e) > MAX_DIST * MAX_DIST) {
			p.sendMessage(Text.translatable("diktatura.poker.no.far").formatted(Formatting.RED), true);
			return;
		}
		Text error;
		try {
			error = g.handle(p, a);
		} catch (IllegalArgumentException | IllegalStateException ex) {
			error = Text.translatable("diktatura.poker.no.move");
		}
		if (error != null) {
			p.sendMessage(error.copy().formatted(Formatting.RED), true);
		}
		g.afterChange(server);
	}

	private Text handle(ServerPlayerEntity p, PokerPayloads.PokerAction a) {
		String id = p.getUuidAsString();
		int seat = table.seatOf(id);
		CasinoSettings.Poker s = CasinoSettings.get().poker();
		switch (a.action()) {
			case "sit" -> {
				if (seat >= 0) {
					return Text.translatable("diktatura.poker.no.seated");
				}
				int free = table.freeSeat();
				if (free < 0) {
					return Text.translatable("diktatura.poker.no.full");
				}
				Text no = canPay(p, a.amount(), s);
				if (no != null) {
					return no;
				}
				Economy.add(p, -a.amount(), "diktatura.money.reason.poker_buy_in", "", false);
				table.sit(free, new PokerTable.Seat(id, p.getName().getString(), null, a.amount()));
				names[free] = p.getName().copy();
				ready[free] = false;
				lastAction[free] = null;
				won[free] = 0;
				say(Text.translatable("diktatura.poker.log.sit", p.getName(), a.amount()));
			}
			case "rebuy" -> {
				if (seat < 0) {
					return Text.translatable("diktatura.poker.no.not_seated");
				}
				if (table.inHand() && table.inCurrentHand(seat)) {
					return Text.translatable("diktatura.poker.no.in_hand");
				}
				if (table.seat(seat).stack() + a.amount() > s.maxBuyIn()) {
					return Text.translatable("diktatura.poker.no.rebuy", s.maxBuyIn());
				}
				Text no = canPay(p, a.amount(), s);
				if (no != null) {
					return no;
				}
				Economy.add(p, -a.amount(), "diktatura.money.reason.poker_buy_in", "", false);
				table.addChips(seat, a.amount());
				say(Text.translatable("diktatura.poker.log.rebuy", p.getName(), a.amount()));
			}
			case "stand" -> {
				if (seat < 0) {
					return null;
				}
				stand(p, seat, false);
			}
			case "ready" -> {
				if (seat < 0) {
					return Text.translatable("diktatura.poker.no.not_seated");
				}
				if (table.seat(seat).stack() == 0) {
					return Text.translatable("diktatura.poker.no.broke");
				}
				ready[seat] = !ready[seat];
				if (ready[seat] && nextHandAt < now(p.getServer())) {
					nextHandAt = now(p.getServer()) + 20;
				}
			}
			case "bot" -> {
				if (seat < 0) {
					return Text.translatable("diktatura.poker.no.not_seated");
				}
				PokerBot.Level[] levels = PokerBot.Level.values();
				PokerBot.Level level = levels[Math.floorMod(a.amount(), levels.length)];
				int free = table.freeSeat();
				if (free < 0) {
					return Text.translatable("diktatura.poker.no.full");
				}
				botCounter++;
				table.sit(free, new PokerTable.Seat("bot-" + UUID.randomUUID(), String.valueOf(botCounter), level,
					s.maxBuyIn() / 2));
				names[free] = botName(level, botCounter);
				ready[free] = true;
				lastAction[free] = null;
				won[free] = 0;
				say(Text.translatable("diktatura.poker.log.bot_sit", names[free], p.getName()));
			}
			case "kick" -> {
				int target = a.amount();
				if (seat < 0 || target < 0 || target >= PokerTable.SEATS || table.seat(target) == null || !table.seat(target).isBot()) {
					return null;
				}
				Text name = names[target];
				table.leave(target);
				say(Text.translatable("diktatura.poker.log.bot_left", name));
			}
			case "fold", "check", "call", "raise", "allin" -> {
				if (seat < 0 || table.toAct() != seat) {
					return Text.translatable("diktatura.poker.no.turn");
				}
				PokerTable.Action act = switch (a.action()) {
					case "fold" -> PokerTable.Action.FOLD;
					case "check" -> PokerTable.Action.CHECK;
					case "call" -> PokerTable.Action.CALL;
					case "raise" -> PokerTable.Action.raiseTo(a.amount());
					default -> PokerTable.Action.ALL_IN;
				};
				table.act(seat, act);
			}
			default -> {
				return null;
			}
		}
		return null;
	}

	private static Text canPay(ServerPlayerEntity p, int amount, CasinoSettings.Poker s) {
		if (DiktaturaState.get(p.getServer()).player(p).stage != Stage.FREE) {
			return Text.translatable("diktatura.casino.no.arrested");
		}
		if (amount < s.minBuyIn() || amount > s.maxBuyIn()) {
			return Text.translatable("diktatura.poker.no.buy_in", s.minBuyIn(), s.maxBuyIn());
		}
		if (Economy.balance(p) < amount) {
			return Text.translatable("diktatura.casino.no.money", amount);
		}
		if (LawEngine.fire(new LawContext(p, LawEvent.GAMBLE))) {
			return Text.translatable("diktatura.casino.no.law");
		}
		return null;
	}

	/** Встать: фишки на баланс (в раздаче — карты сброшены). */
	private void stand(ServerPlayerEntity p, int seat, boolean forced) {
		int chips = table.leave(seat);
		ready[seat] = false;
		if (chips > 0) {
			Economy.add(p, chips, "diktatura.money.reason.poker_cash_out", "", true);
		}
		say(Text.translatable(forced ? "diktatura.poker.log.left_far" : "diktatura.poker.log.stand", p.getName(), chips));
	}

	/** Игрок вышел с сервера: встаёт из-за стола, фишки — на баланс. */
	public static void onDisconnect(ServerPlayerEntity p) {
		if (casino == null) {
			return;
		}
		PokerGame g = casino;
		g.watchers.remove(p.getUuid());
		int seat = g.table.seatOf(p.getUuidAsString());
		if (seat >= 0) {
			g.stand(p, seat, false);
			g.afterChange(p.getServer());
		}
	}

	/** Сервер останавливается: вернуть фишки всем, кто сидит за столом. */
	public static void onServerStop(MinecraftServer server) {
		if (casino == null) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		for (int i = 0; i < PokerTable.SEATS; i++) {
			PokerTable.Seat seat = casino.table.seat(i);
			if (seat == null || seat.isBot()) {
				continue;
			}
			ServerPlayerEntity p = player(server, seat.id);
			int chips = casino.table.leave(i);
			if (p != null) {
				Economy.add(p, chips, "diktatura.money.reason.poker_cash_out", "", false);
			} else {
				try {
					PlayerRecord rec = state.players.get(UUID.fromString(seat.id));
					if (rec != null) {
						rec.trudodni += chips;
					}
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
		state.markDirty();
		casino = null;
	}

	// ------------------------------------------------------------------ тик

	public static void tick(MinecraftServer server) {
		if (casino == null) {
			return;
		}
		casino.update(server);
	}

	private static long now(MinecraftServer server) {
		return server.getOverworld().getTime();
	}

	private void update(MinecraftServer server) {
		long now = now(server);
		PokerTableEntity e = entity(server);
		// Раз в секунду: кто отошёл, вышел, арестован — встаёт; таймер на экранах.
		if (now % 20 == 0) {
			for (int i = 0; i < PokerTable.SEATS; i++) {
				PokerTable.Seat seat = table.seat(i);
				if (seat == null || seat.isBot()) {
					continue;
				}
				ServerPlayerEntity p = player(server, seat.id);
				if (p == null) {
					continue;
				}
				boolean far = e == null || p.squaredDistanceTo(e) > MAX_DIST * MAX_DIST || p.getWorld() != e.getWorld();
				if (far || DiktaturaState.get(server).player(p).stage != Stage.FREE) {
					stand(p, i, true);
				}
			}
			watchers.removeIf(id -> {
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
				return p == null || e == null || p.squaredDistanceTo(e) > 12 * 12;
			});
			// За столом одни боты — они расходятся.
			if (!table.inHand() && humans() == 0) {
				for (int i = 0; i < PokerTable.SEATS; i++) {
					if (table.seat(i) != null) {
						table.leave(i);
						dirty = true;
					}
				}
			}
			dirty = true;
		}
		if (table.inHand()) {
			int seat = table.toAct();
			if (seat != turnSeat) {
				turnSeat = seat;
				turnStart = now;
				botAt = 0;
			}
			PokerTable.Seat who = seat >= 0 ? table.seat(seat) : null;
			if (who == null && seat >= 0) {
				// Сидевший встал во время своего хода (такого не бывает: leave() сам сбрасывает) — на всякий случай.
				table.act(seat, PokerTable.Action.FOLD);
			} else if (who != null && who.isBot()) {
				if (botAt == 0) {
					botAt = now + 20 + rng.nextInt(25);
				} else if (now >= botAt) {
					table.act(seat, PokerBot.decide(table, seat, rng, CasinoSettings.get().poker().botIterations()));
					botAt = 0;
				}
			} else if (who != null && now - turnStart > TURN_TICKS) {
				boolean free = table.toCall(seat) == 0;
				table.act(seat, free ? PokerTable.Action.CHECK : PokerTable.Action.FOLD);
				say(Text.translatable("diktatura.poker.log.timeout", names[seat]));
			}
		} else if (now >= nextHandAt && readyToStart()) {
			startHand(server, now);
		}
		afterChange(server);
	}

	private int humans() {
		int n = 0;
		for (int i = 0; i < PokerTable.SEATS; i++) {
			if (table.seat(i) != null && !table.seat(i).isBot()) {
				n++;
			}
		}
		return n;
	}

	/** Раздачу можно начинать: хотя бы двое с фишками, один из них человек, и все люди с фишками нажали «Готов». */
	private boolean readyToStart() {
		if (!table.canStart()) {
			return false;
		}
		boolean human = false;
		for (int i = 0; i < PokerTable.SEATS; i++) {
			PokerTable.Seat s = table.seat(i);
			if (s != null && !s.isBot() && s.stack() > 0) {
				human = true;
				if (!ready[i]) {
					return false;
				}
			}
		}
		return human;
	}

	private void startHand(MinecraftServer server, long now) {
		for (int i = 0; i < PokerTable.SEATS; i++) {
			lastAction[i] = null;
			won[i] = 0;
			shown[i] = null;
		}
		potWinner = -1;
		potAt = 0;
		showAt = 0;
		java.util.Arrays.fill(boardAt, 0);
		dealtAt = now + 32; // после тасовки
		table.startHand();
		PokerTableEntity e = entity(server);
		if (e != null) {
			e.shuffleFx();
		}
		gesture(server, NpcEntity.SHUFFLE);
		sound(server, SoundEvents.ITEM_BOOK_PAGE_TURN, 0.8f);
	}

	// ------------------------------------------------------------------ события

	private void afterChange(MinecraftServer server) {
		List<PokerTable.Event> events = table.drainEvents();
		long now = now(server);
		for (PokerTable.Event ev : events) {
			dirty = true;
			switch (ev) {
				case PokerTable.Event.HandStarted h -> say(Text.translatable("diktatura.poker.log.hand", h.hand(), names[h.dealer()]));
				case PokerTable.Event.Blind b -> {
					lastAction[b.seat()] = Text.translatable("diktatura.poker.act.blind", b.amount());
					say(Text.translatable("diktatura.poker.log.blind", names[b.seat()], b.amount()));
				}
				case PokerTable.Event.HoleDealt d -> {
				}
				case PokerTable.Event.Acted a -> {
					Text label = actionLabel(a);
					lastAction[a.seat()] = label;
					say(Text.empty().append(names[a.seat()] == null ? Text.literal("?") : names[a.seat()]).append(": ").append(label));
					sound(server, a.type() == PokerTable.ActionType.FOLD ? SoundEvents.ITEM_BOOK_PUT
						: a.put() > 0 ? SoundEvents.BLOCK_CHAIN_PLACE : SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.2f);
				}
				case PokerTable.Event.BoardDealt b -> {
					int from = b.board().size() == 3 ? 0 : b.board().size() - 1;
					long base = Math.max(Math.max(now, dealtAt + 40), lastReveal() + 20);
					for (int i = from; i < b.board().size(); i++) {
						boardAt[i] = base + (i - from) * 6L;
					}
					say(Text.translatable("diktatura.poker.log.board." + b.phase().name().toLowerCase(java.util.Locale.ROOT),
						cards(b.board().subList(from, b.board().size()))));
					gesture(server, NpcEntity.FLIP);
					for (int i = 0; i < PokerTable.SEATS; i++) {
						if (lastAction[i] != null && table.inCurrentHand(i) && !table.folded(i) && !table.allIn(i)) {
							lastAction[i] = null;
						}
					}
				}
				case PokerTable.Event.Refund r -> say(Text.translatable("diktatura.poker.log.refund", names[r.seat()], r.amount()));
				case PokerTable.Event.Shown s -> {
					shown[s.seat()] = s.hole();
					showAt = Math.max(Math.max(showAt, now), lastReveal() + 20);
					lastAction[s.seat()] = Text.translatable("diktatura.poker.hand." + s.hand().category().id);
					say(Text.translatable("diktatura.poker.log.shown", names[s.seat()], cards(List.of(s.hole())),
						Text.translatable("diktatura.poker.hand." + s.hand().category().id)));
				}
				case PokerTable.Event.PotWon w -> {
					potAt = showAt > 0 ? showAt + 30 : now;
					List<Text> who = new ArrayList<>();
					for (int[] sh : w.shares()) {
						won[sh[0]] += sh[1];
						potWinner = sh[0];
						who.add(names[sh[0]]);
					}
					Text winners = joinNames(who);
					say(w.shares().size() > 1
						? Text.translatable("diktatura.poker.log.split", winners, w.amount() - w.tax(), w.tax())
						: Text.translatable("diktatura.poker.log.won", winners, w.amount() - w.tax(), w.tax()));
					gesture(server, NpcEntity.PUSH);
					sound(server, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f);
				}
				case PokerTable.Event.BluffRevealed b -> {
					ServerPlayerEntity p = player(server, b.id());
					if (p != null) {
						LawEngine.fire(new LawContext(p, LawEvent.POKER_BLUFF));
					}
				}
				case PokerTable.Event.HandEnded h -> {
					nextHandAt = now + NEXT_HAND_TICKS;
					turnSeat = -1;
					for (int i = 0; i < PokerTable.SEATS; i++) {
						PokerTable.Seat s = table.seat(i);
						if (s == null) {
							continue;
						}
						if (s.stack() == 0) {
							if (s.isBot()) {
								say(Text.translatable("diktatura.poker.log.bot_broke", names[i]));
								table.leave(i);
							} else {
								ready[i] = false;
								say(Text.translatable("diktatura.poker.log.broke", names[i]));
							}
						}
					}
				}
			}
		}
		if (table.inHand() && table.toAct() >= 0 && table.seat(table.toAct()) != null && dealtAt > 0) {
			// Сдача карт — жест крупье, когда игра только началась.
			if (table.phase() == PokerTable.Phase.PREFLOP && now == dealtAt) {
				gesture(server, NpcEntity.DEAL);
			}
		}
		if (dirty) {
			dirty = false;
			updateView(server);
			broadcast(server);
		}
	}

	private long lastReveal() {
		long m = 0;
		for (long t : boardAt) {
			m = Math.max(m, t);
		}
		return m;
	}

	private static Text joinNames(List<Text> parts) {
		MutableText out = Text.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(parts.get(i) == null ? Text.literal("?") : parts.get(i));
		}
		return out;
	}

	private Text actionLabel(PokerTable.Event.Acted a) {
		return switch (a.type()) {
			case FOLD -> Text.translatable("diktatura.poker.act.fold");
			case CHECK -> Text.translatable("diktatura.poker.act.check");
			case CALL -> Text.translatable("diktatura.poker.act.call", a.put());
			case RAISE -> Text.translatable("diktatura.poker.act.raise", a.bet());
			case ALL_IN -> Text.translatable("diktatura.poker.act.allin", a.bet());
		};
	}

	private static Text botName(PokerBot.Level level, int n) {
		return Text.translatable("diktatura.poker.bot." + level.id).append(" №" + n);
	}

	/** Карты текстом: «A♠ 10♥» (червы и бубны красные). */
	public static Text cards(List<Card> list) {
		MutableText out = Text.empty();
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				out.append(" ");
			}
			out.append(card(list.get(i)));
		}
		return out;
	}

	public static Text card(Card c) {
		String r = c.rank() == 10 ? "10" : String.valueOf(Card.RANKS.charAt(c.rank() - 2));
		String suit = String.valueOf("♠♥♦♣".charAt(c.suit()));
		return Text.literal(r + suit).formatted(c.suit() == 1 || c.suit() == 2 ? Formatting.RED : Formatting.WHITE);
	}

	private void say(Text line) {
		log.addLast(line);
		while (log.size() > LOG_LINES) {
			log.removeFirst();
		}
		dirty = true;
	}

	private static ServerPlayerEntity player(MinecraftServer server, String id) {
		try {
			return server.getPlayerManager().getPlayer(UUID.fromString(id));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------ вид

	/** Вид стола для всех вокруг: карты (свои игрок видит на экране), ставки, банк, имена, время анимаций. */
	private void updateView(MinecraftServer server) {
		PokerTableEntity e = entity(server);
		if (e == null) {
			return;
		}
		NbtCompound v = new NbtCompound();
		v.putInt("hand", table.handNumber());
		v.putBoolean("live", table.inHand());
		v.putInt("dealer", table.dealer());
		v.putInt("toAct", table.toAct());
		v.putLong("dealt", dealtAt);
		v.putInt("pot", table.pot());
		v.putLong("potAt", potAt);
		v.putInt("winner", potWinner);
		List<Card> board = table.board();
		int[] b = new int[board.size()];
		for (int i = 0; i < b.length; i++) {
			b[i] = board.get(i).index();
		}
		v.putIntArray("board", b);
		v.putLongArray("boardAt", boardAt.clone());
		v.putLong("showAt", showAt);
		NbtList seats = new NbtList();
		for (int i = 0; i < PokerTable.SEATS; i++) {
			NbtCompound s = new NbtCompound();
			PokerTable.Seat seat = table.seat(i);
			s.putBoolean("on", seat != null);
			if (seat != null) {
				s.putString("name", Text.Serialization.toJsonString(names[i] == null ? Text.literal(seat.name) : names[i],
					server.getRegistryManager()));
				s.putInt("stack", seat.stack());
			}
			s.putBoolean("in", table.inCurrentHand(i) && (table.inHand() || shown[i] != null));
			s.putBoolean("fold", table.folded(i));
			s.putInt("bet", table.bet(i));
			s.putInt("won", won[i]);
			if (shown[i] != null) {
				s.putIntArray("cards", new int[]{shown[i][0].index(), shown[i][1].index()});
			}
			seats.add(s);
		}
		v.put("seats", seats);
		e.setView(v);
	}

	private void broadcast(MinecraftServer server) {
		for (UUID id : watchers) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
			if (p != null) {
				send(p, false);
			}
		}
	}

	private void send(ServerPlayerEntity p, boolean open) {
		if (!ServerPlayNetworking.canSend(p, PokerPayloads.PokerState.ID)) {
			if (open) {
				p.sendMessage(Text.translatable("diktatura.poker.no.client"), false);
			}
			return;
		}
		MinecraftServer server = p.getServer();
		long now = now(server);
		CasinoSettings.Poker s = CasinoSettings.get().poker();
		int me = table.seatOf(p.getUuidAsString());
		List<PokerPayloads.SeatView> seats = new ArrayList<>();
		for (int i = 0; i < PokerTable.SEATS; i++) {
			PokerTable.Seat seat = table.seat(i);
			if (seat == null) {
				seats.add(PokerPayloads.SeatView.EMPTY);
				continue;
			}
			boolean inHand = table.inCurrentHand(i) && (table.inHand() || shown[i] != null);
			int c0 = PokerPayloads.NONE;
			int c1 = PokerPayloads.NONE;
			if (inHand && !table.folded(i)) {
				Card[] hole = shown[i] != null ? shown[i] : i == me ? table.hole(i) : null;
				c0 = hole == null ? PokerPayloads.HIDDEN : hole[0].index();
				c1 = hole == null ? PokerPayloads.HIDDEN : hole[1].index();
			}
			seats.add(new PokerPayloads.SeatView(true, names[i] == null ? Text.literal(seat.name) : names[i],
				seat.bot == null ? -1 : seat.bot.ordinal(), seat.stack(), table.bet(i), inHand, table.folded(i),
				table.allIn(i), ready[i], c0, c1, lastAction[i] == null ? Text.empty() : lastAction[i], won[i]));
		}
		List<Integer> board = new ArrayList<>();
		for (Card c : table.board()) {
			board.add(c.index());
		}
		boolean myTurn = me >= 0 && table.inHand() && table.toAct() == me;
		int turnLeft = table.inHand() && table.toAct() >= 0 && table.seat(table.toAct()) != null && !table.seat(table.toAct()).isBot()
			? (int) Math.max(0, (TURN_TICKS - (now - turnStart)) / 20) : -1;
		int nextIn = !table.inHand() && readyToStart() ? (int) Math.max(0, (nextHandAt - now) / 20) : -1;
		PokerTableEntity e = entity(server);
		ServerPlayNetworking.send(p, new PokerPayloads.PokerState(e == null ? -1 : e.getId(), open, table.phase().ordinal(),
			table.handNumber(), table.pot(), table.currentBet(), table.dealer(), table.toAct(), turnLeft, nextIn, board, seats,
			me, myTurn ? table.toCall(me) : 0, myTurn ? table.minRaiseTo(me) : -1, myTurn ? table.maxRaiseTo(me) : 0,
			Economy.balance(p), s.minBuyIn(), s.maxBuyIn(), s.bigBlind(), s.taxPercent(), List.copyOf(log)));
	}
}
