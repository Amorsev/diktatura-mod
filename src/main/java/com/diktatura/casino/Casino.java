package com.diktatura.casino;

import com.diktatura.Diktatura;
import com.diktatura.casino.CasinoSettings.Odds;
import com.diktatura.economy.Economy;
import com.diktatura.finale.Tasks;
import com.diktatura.item.ModItems;
import com.diktatura.law.LawContext;
import com.diktatura.law.LawEngine;
import com.diktatura.law.LawEvent;
import com.diktatura.network.UiPayloads;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.NpcEntity;
import com.diktatura.npc.NpcInteractions;
import com.diktatura.npc.NpcPosts;
import com.diktatura.npc.NpcRegistry;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import com.diktatura.ui.Hud;
import com.diktatura.ui.Voice;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Государственное казино: три стола и три крупье — рулетка (товарищ Фортунов), игровой
 * автомат (смотритель) и кости. Ставки в трудоднях, шансы подкручены открыто: в окне крупье
 * рядом официальный и фактический шанс. Игра видна на столе: колесо крутится, барабаны
 * вертятся, кости летят; итог — после анимации. Выигрыш облагается налогом, ставок в час —
 * не больше лимита. Перед ставкой проверяются законы (вождь может запретить игру без лицензии).
 */
public final class Casino {
	public static final String CROUPIER_ID = "diktatura:croupier";
	public static final String SLOTS_ID = "diktatura:slots_operator";
	public static final String DICE_ID = "diktatura:dice_croupier";
	private static final int[] BET_STEPS = {1, 2, 5, 10, 20, 50, 100, 200, 500};
	private static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
	private static final int SYMBOLS = 6;
	private static final Item[] SYMBOL_ITEMS = {Items.NETHER_STAR, Items.GOLDEN_HELMET, Items.WHEAT, Items.MINECART,
		ModItems.TAX_RECEIPT, Items.BLACK_DYE};
	private static final long HOUR_MS = 60L * 60 * 1000;
	private static final int SEGMENTS = 16;

	/** Стол: какой крупье, где стоит и какие игры ведёт. */
	enum Table {
		ROULETTE("croupier", CROUPIER_ID, 90f),
		SLOTS("slots_operator", SLOTS_ID, 0f),
		DICE("dice_croupier", DICE_ID, 180f);

		final String key;
		final String npcId;
		final float yaw;

		Table(String key, String npcId, float yaw) {
			this.key = key;
			this.npcId = npcId;
			this.yaw = yaw;
		}

		BlockPos post() {
			return switch (this) {
				case ROULETTE -> CasinoBuilding.CROUPIER;
				case SLOTS -> CasinoBuilding.SLOTS_OPERATOR;
				case DICE -> CasinoBuilding.DICE_CROUPIER;
			};
		}

		static Table of(String npcId) {
			for (Table t : values()) {
				if (t.npcId.equals(npcId)) {
					return t;
				}
			}
			return null;
		}
	}

	private static final Map<UUID, Integer> BET = new HashMap<>();
	private static final Map<UUID, Text> LAST = new HashMap<>();
	/** Кто сейчас играет за столом (стол занят до конца анимации). */
	private static final Map<Table, UUID> BUSY = new EnumMap<>(Table.class);
	/** Пост крупье покера (окно — экран покерного стола). */
	public static final String POKER_KEY = "casino_poker";

	/** Крупье покера: ПКМ открывает экран стола вместо общего окна. */
	private static final class PokerDealerHandler implements NpcInteractions.Handler {
		@Override
		public boolean matches(Entity entity) {
			return entity instanceof NpcEntity n && n.npcId().endsWith("poker_dealer");
		}

		@Override
		public boolean customOpen(ServerPlayerEntity player, Entity npc) {
			com.diktatura.casino.poker.PokerGame.open(player, npc);
			return true;
		}

		@Override
		public UiPayloads.NpcScreen screen(ServerPlayerEntity player, Entity npc) {
			return new UiPayloads.NpcScreen(POKER_KEY, npc.getId(), npc.getName(), Text.empty(), Text.empty(), List.of(),
				List.of(), 0xFF7A1E2C);
		}

		@Override
		public boolean action(ServerPlayerEntity player, Entity npc, String action) {
			return false;
		}
	}

	private Casino() {
	}

	public static void registerEvents() {
		for (Table t : Table.values()) {
			NpcInteractions.register(t.key, new CroupierHandler(t));
		}
		NpcInteractions.register(POKER_KEY, new PokerDealerHandler());
		CasinoFx.registerEvents();
	}

	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % 100 != 0) {
			return;
		}
		CasinoBuilding.ensureGenerated(server);
		DiktaturaState state = DiktaturaState.get(server);
		if (!state.casinoGenerated) {
			return;
		}
		ServerWorld w = server.getOverworld();
		BlockPos o = state.casinoOrigin;
		if (state.casinoVersion < CasinoBuilding.VERSION && w.isChunkLoaded(o.getX() >> 4, o.getZ() >> 4) && BUSY.isEmpty()) {
			// Новый интерьер: три стола. Перестраиваем казино на том же месте один раз.
			CasinoBuilding.build(w, o);
			// Убрать предметы, которые могли выпасть при перестройке (старые версии оставляли ковры).
			w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
				new net.minecraft.util.math.Box(o).expand(CasinoBuilding.SIZE_X + 4, CasinoBuilding.SIZE_Y + 8, CasinoBuilding.SIZE_X + 4),
				e -> true).forEach(Entity::discard);
			state.casinoVersion = CasinoBuilding.VERSION;
			state.markDirty();
			Diktatura.LOGGER.info("Госказино перестроено: три стола (версия {})", CasinoBuilding.VERSION);
		}
		if (Diktatura.ticks() % 200 == 0) {
			for (Table t : Table.values()) {
				NpcPosts.ensure(server, t.key, t.npcId, o.add(t.post()), t.yaw);
			}
			ensureProps(w, o);
			NpcEntity dealer = NpcPosts.ensure(server, POKER_KEY, "diktatura:poker_dealer", o.add(CasinoBuilding.POKER_DEALER), 0.0f);
			double[] c = CasinoBuilding.POKER_TABLE;
			com.diktatura.casino.poker.PokerGame.ensure(w, new net.minecraft.util.math.Vec3d(o.getX() + c[0], o.getY() + c[1],
				o.getZ() + c[2]), dealer);
		}
	}

	private static int bet(ServerPlayerEntity p) {
		CasinoSettings s = CasinoSettings.get();
		int b = BET.getOrDefault(p.getUuid(), s.minBet());
		return Math.max(s.minBet(), Math.min(s.maxBet(), b));
	}

	private static void changeBet(ServerPlayerEntity p, int dir) {
		CasinoSettings s = CasinoSettings.get();
		int cur = bet(p);
		int next = cur;
		if (dir > 0) {
			for (int step : BET_STEPS) {
				if (step > cur) {
					next = step;
					break;
				}
			}
			if (next == cur) {
				next = s.maxBet();
			}
		} else {
			for (int i = BET_STEPS.length - 1; i >= 0; i--) {
				if (BET_STEPS[i] < cur) {
					next = BET_STEPS[i];
					break;
				}
			}
			if (next == cur) {
				next = s.minBet();
			}
		}
		BET.put(p.getUuid(), Math.max(s.minBet(), Math.min(s.maxBet(), next)));
	}

	private static int betsLeft(PlayerRecord rec) {
		long now = System.currentTimeMillis();
		if (now - rec.casinoHourStart >= HOUR_MS) {
			return CasinoSettings.get().betsPerHour();
		}
		return Math.max(0, CasinoSettings.get().betsPerHour() - rec.casinoBets);
	}

	private static boolean roll(Random rnd, double percent) {
		return rnd.nextDouble() * 100.0 < percent;
	}

	/** Итог раунда, известный серверу заранее; показывается после анимации. */
	private record Outcome(String game, int bet, int payout, MutableText shown, int number, int[] reels, int[] dice) {
	}

	/**
	 * Начать игру. Возвращает причину отказа (крупье её скажет) или null, если раунд пошёл.
	 */
	static Text play(ServerPlayerEntity p, Entity croupier, Table table, String game) {
		MinecraftServer server = p.getServer();
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(p);
		CasinoSettings s = CasinoSettings.get();
		int bet = bet(p);
		if (BUSY.containsKey(table)) {
			return Text.translatable("diktatura.casino.no.busy");
		}
		if (rec.stage != Stage.FREE) {
			return Text.translatable("diktatura.casino.no.arrested");
		}
		if (betsLeft(rec) <= 0) {
			return Text.translatable("diktatura.casino.no.limit", s.betsPerHour());
		}
		if (rec.trudodni < bet) {
			return Text.translatable("diktatura.casino.no.money", bet);
		}
		if (LawEngine.fire(new LawContext(p, LawEvent.GAMBLE))) {
			return Text.translatable("diktatura.casino.no.law");
		}
		long now = System.currentTimeMillis();
		if (now - rec.casinoHourStart >= HOUR_MS) {
			rec.casinoHourStart = now;
			rec.casinoBets = 0;
		}
		rec.casinoBets++;
		Economy.add(p, -bet, "diktatura.money.reason.casino_bet", "diktatura.casino.game." + game, false);
		state.markDirty();

		Outcome out = resolve(p.getRandom(), s, game, bet);
		BUSY.put(table, p.getUuid());
		ServerWorld w = p.getServerWorld();
		BlockPos o = state.casinoOrigin;
		NpcRegistry.NpcData d = NpcRegistry.get(table.npcId);
		Voice.say(croupier, d != null ? d.displayName() : croupier.getName(),
			Text.translatable("diktatura.casino.start." + table.key), null, 1.0f);
		if (croupier instanceof com.diktatura.npc.NpcEntity n) {
			// Крупье раскручивает рулетку, оператор дёргает рычаг автомата, крупье костей трясёт стакан.
			n.gesture(switch (table) {
				case ROULETTE -> com.diktatura.npc.NpcEntity.SPIN;
				case SLOTS -> com.diktatura.npc.NpcEntity.LEVER;
				case DICE -> com.diktatura.npc.NpcEntity.SHAKE;
			});
		}
		int duration = switch (table) {
			case ROULETTE -> spinRoulette(w, o, out);
			case SLOTS -> spinSlots(w, o, out);
			case DICE -> throwDice(w, o, out);
		};
		UUID player = p.getUuid();
		Tasks.schedule(server, duration, () -> finish(server, w, o, table, croupier, player, out));
		return null;
	}

	private static Outcome resolve(Random rnd, CasinoSettings s, String game, int bet) {
		switch (game) {
			case "red", "black" -> {
				boolean wantRed = game.equals("red");
				Odds o = s.red();
				boolean win = roll(rnd, o.real());
				int n = win ? number(rnd, wantRed) : rnd.nextInt(20) == 0 ? 0 : number(rnd, !wantRed);
				return new Outcome(game, bet, win ? bet * o.payout() : 0, rouletteText(n), n, null, null);
			}
			case "zero" -> {
				Odds o = s.zero();
				boolean win = roll(rnd, o.real());
				int n = win ? 0 : 1 + rnd.nextInt(36);
				return new Outcome(game, bet, win ? bet * o.payout() : 0, rouletteText(n), n, null, null);
			}
			case "slots" -> {
				double r = rnd.nextDouble() * 100.0;
				int a = rnd.nextInt(SYMBOLS);
				int b;
				int c;
				int payout = 0;
				if (r < s.slotsJackpot().real()) {
					b = a;
					c = a;
					payout = bet * s.slotsJackpot().payout();
				} else if (r < s.slotsJackpot().real() + s.slotsPair().real()) {
					b = a;
					c = (a + 1 + rnd.nextInt(SYMBOLS - 1)) % SYMBOLS;
					payout = bet * s.slotsPair().payout();
				} else {
					b = (a + 1 + rnd.nextInt(SYMBOLS - 1)) % SYMBOLS;
					do {
						c = rnd.nextInt(SYMBOLS);
					} while (c == a || c == b);
				}
				return new Outcome(game, bet, payout,
					Text.translatable("diktatura.casino.slots.result", symbol(a), symbol(b), symbol(c)), 0, new int[] {a, b, c}, null);
			}
			default -> {
				Odds o = s.dice();
				boolean win = roll(rnd, o.real());
				int[] dd = new int[4];
				do {
					for (int i = 0; i < 4; i++) {
						dd[i] = 1 + rnd.nextInt(6);
					}
				} while (win ? dd[0] + dd[1] <= dd[2] + dd[3] : dd[0] + dd[1] > dd[2] + dd[3]);
				MutableText shown = Text.translatable("diktatura.casino.dice.result2", dd[0], dd[1], dd[0] + dd[1],
					dd[2], dd[3], dd[2] + dd[3]);
				return new Outcome(game, bet, win ? bet * o.payout() : 0, shown, 0, null, dd);
			}
		}
	}

	/** Конец анимации: выплата, реплика крупье, табличка с итогом над столом. */
	private static void finish(MinecraftServer server, ServerWorld w, BlockPos o, Table table, Entity croupier, UUID playerId,
	                           Outcome out) {
		CasinoSettings s = CasinoSettings.get();
		ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerId);
		Random rnd = w.getRandom();
		MutableText result;
		int payout = out.payout();
		int net = 0;
		int tax = 0;
		if (payout > 0) {
			int profit = payout - out.bet();
			tax = Math.max(0, Math.round(profit * s.taxPercent() / 100f));
			net = payout - tax;
			result = Text.translatable("diktatura.casino.win", out.shown(), net, tax);
		} else {
			result = Text.translatable("diktatura.casino.lose", out.shown(), out.bet());
		}
		if (p != null) {
			LAST.put(playerId, result);
			if (payout > 0) {
				Economy.add(p, net, "diktatura.money.reason.casino_win", "diktatura.casino.game." + out.game(), false);
				Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.casino.win.title", net),
					Text.translatable("diktatura.casino.win.tax", tax), 5);
				w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
				if (net - out.bet() >= s.bigWin()) {
					Hud.alertAll(server, AlertKind.GOOD, Text.translatable("diktatura.casino.big", p.getDisplayName(), net),
						Text.translatable("diktatura.casino.big.hint"), 8);
				}
			} else {
				Hud.alert(p, AlertKind.INFO, Text.translatable("diktatura.casino.lose.title", out.bet()), out.shown(), 4);
				w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.8f, 0.6f);
			}
		} else if (payout > 0) {
			// Игрок вышел посреди раунда — выигрыш всё равно зачисляется.
			DiktaturaState st = DiktaturaState.get(server);
			st.player(playerId).trudodni += net;
			st.markDirty();
		}
		if (croupier.isAlive()) {
			NpcRegistry.NpcData d = NpcRegistry.get(table.npcId);
			Text quip = Text.translatable((payout > 0 ? "diktatura.casino.quip.win." : "diktatura.casino.quip.lose.") + rnd.nextInt(4));
			Voice.say(croupier, d != null ? d.displayName() : croupier.getName(), quip, null, 1.0f);
		}
		Vec3d at = switch (table) {
			case ROULETTE -> new Vec3d(o.getX() + CasinoBuilding.WHEEL[0], o.getY() + 3.4, o.getZ() + CasinoBuilding.WHEEL[2]);
			case SLOTS -> new Vec3d(o.getX() + 4.5, o.getY() + 4.75, o.getZ() + 2.3);
			case DICE -> new Vec3d(o.getX() + 5.0, o.getY() + 3.2, o.getZ() + 8.5);
		};
		Text label = Text.empty().append(out.shown()).append("\n")
			.append(payout > 0 ? Text.translatable("diktatura.casino.label.win", net).formatted(Formatting.GREEN)
				: Text.translatable("diktatura.casino.label.lose", out.bet()).formatted(Formatting.RED));
		DisplayEntity sign = CasinoFx.text(w, at, label);
		Tasks.schedule(server, 70, () -> {
			CasinoFx.remove(sign);
			cleanup(table);
			BUSY.remove(table);
		});
	}

	// ================================================================== анимации

	private static final Map<Table, List<DisplayEntity>> PROPS = new EnumMap<>(Table.class);

	private static void prop(Table t, DisplayEntity e) {
		if (e != null) {
			PROPS.computeIfAbsent(t, k -> new ArrayList<>()).add(e);
		}
	}

	private static void cleanup(Table t) {
		List<DisplayEntity> list = PROPS.remove(t);
		if (list != null) {
			list.forEach(CasinoFx::remove);
		}
	}

	/** Стол-модель (колесо, автомат, кости) по виду игры; null — ещё не поставлен. */
	private static CasinoPropEntity prop(ServerWorld w, CasinoPropEntity.Kind kind) {
		UUID id = PROP_IDS.get(kind);
		Entity e = id == null ? null : w.getEntity(id);
		return e instanceof CasinoPropEntity c && !c.isRemoved() ? c : null;
	}

	private static final Map<CasinoPropEntity.Kind, UUID> PROP_IDS = new EnumMap<>(CasinoPropEntity.Kind.class);

	/** Где стоят столы-модели (от угла казино) и куда смотрят. */
	private static double[] propPlace(CasinoPropEntity.Kind kind) {
		return switch (kind) {
			case ROULETTE -> new double[]{CasinoBuilding.WHEEL[0], CasinoBuilding.WHEEL[1], CasinoBuilding.WHEEL[2], 0};
			case SLOTS -> new double[]{CasinoBuilding.SLOTS[0], CasinoBuilding.SLOTS[1], CasinoBuilding.SLOTS[2], 0};
			case DICE -> new double[]{CasinoBuilding.DICE[0], CasinoBuilding.DICE[1], CasinoBuilding.DICE[2], 180};
		};
	}

	/** Поставить столы-модели, если их нет (и убрать лишние). */
	private static void ensureProps(ServerWorld w, BlockPos o) {
		for (CasinoPropEntity.Kind kind : CasinoPropEntity.Kind.values()) {
			double[] p = propPlace(kind);
			Vec3d at = new Vec3d(o.getX() + p[0], o.getY() + p[1], o.getZ() + p[2]);
			if (!w.isChunkLoaded((int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4)) {
				continue;
			}
			CasinoPropEntity keep = null;
			for (CasinoPropEntity e : w.getEntitiesByClass(CasinoPropEntity.class, new net.minecraft.util.math.Box(at, at).expand(2),
				e -> e.kind() == kind)) {
				if (keep == null && e.getPos().squaredDistanceTo(at) < 0.04) {
					keep = e;
				} else {
					e.discard();
				}
			}
			if (keep == null) {
				keep = com.diktatura.npc.ModEntities.CASINO_PROP.create(w);
				if (keep == null) {
					continue;
				}
				keep.setKind(kind);
				keep.refreshPositionAndAngles(at.x, at.y, at.z, (float) p[3], 0.0f);
				w.spawnEntity(keep);
			}
			PROP_IDS.put(kind, keep.getUuid());
		}
	}

	/** ПКМ по столу-модели: окно крупье этого стола. */
	static void openTable(ServerPlayerEntity p, CasinoPropEntity.Kind kind) {
		Table t = switch (kind) {
			case ROULETTE -> Table.ROULETTE;
			case SLOTS -> Table.SLOTS;
			case DICE -> Table.DICE;
		};
		UUID id = DiktaturaState.get(p.getServer()).npcPosts.get(t.key);
		Entity npc = id == null ? null : p.getServerWorld().getEntity(id);
		if (npc != null) {
			NpcInteractions.open(p, npc);
		}
	}

	private static void spin(ServerWorld w, CasinoPropEntity.Kind kind, NbtCompound nbt) {
		CasinoPropEntity e = prop(w, kind);
		if (e != null) {
			nbt.putLong("start", w.getTime());
			e.setSpin(nbt);
		}
	}

	/** Рулетка: колесо замедляется, шарик бежит навстречу и ложится в лунку итога (расчёт — в рендере колеса). */
	private static int spinRoulette(ServerWorld w, BlockPos o, Outcome out) {
		Vec3d c = new Vec3d(o.getX() + CasinoBuilding.WHEEL[0], o.getY() + CasinoBuilding.WHEEL[1], o.getZ() + CasinoBuilding.WHEEL[2]);
		// Лунка итога: зеро — 0, красное — нечётная, чёрное — чётная (не 0).
		Random rnd = w.getRandom();
		int seg;
		if (out.number() == 0) {
			seg = 0;
		} else if (RED.contains(out.number())) {
			seg = 1 + 2 * rnd.nextInt(SEGMENTS / 2);
		} else {
			seg = 2 + 2 * rnd.nextInt(SEGMENTS / 2 - 1);
		}
		int total = ROULETTE_TICKS;
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("seg", seg);
		nbt.putInt("dur", total);
		spin(w, CasinoPropEntity.Kind.ROULETTE, nbt);
		for (int k = 3; k < total; k += Math.max(2, k / 6)) {
			Tasks.schedule(w.getServer(), k, () ->
				w.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 0.6f, 1.6f));
		}
		Tasks.schedule(w.getServer(), total - 2, () ->
			w.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BAMBOO_HIT, SoundCategory.BLOCKS, 0.8f, 1.4f));
		w.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.5f, 1.6f);
		return total + 6;
	}

	/** Автомат: рычаг, лампа мигает, три барабана останавливаются по одному на символах итога. */
	private static int spinSlots(ServerWorld w, BlockPos o, Outcome out) {
		Vec3d m = new Vec3d(o.getX() + CasinoBuilding.SLOTS[0], o.getY() + 2.5, o.getZ() + CasinoBuilding.SLOTS[2]);
		NbtCompound nbt = new NbtCompound();
		nbt.putIntArray("reels", out.reels());
		nbt.putBoolean("win", out.payout() > 0);
		spin(w, CasinoPropEntity.Kind.SLOTS, nbt);
		w.playSound(null, m.x, m.y, m.z, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1f, 0.6f);
		for (int tick = 4; tick <= SLOT_STOPS[2]; tick += 4) {
			Tasks.schedule(w.getServer(), tick, () -> w.playSound(null, m.x, m.y, m.z, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(),
				SoundCategory.BLOCKS, 0.4f, 0.8f + w.getRandom().nextFloat()));
		}
		for (int i = 0; i < 3; i++) {
			float pitch = 0.8f + i * 0.3f;
			Tasks.schedule(w.getServer(), SLOT_STOPS[i], () -> w.playSound(null, m.x, m.y, m.z,
				SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.8f, pitch));
		}
		return SLOT_STOPS[2] + 8;
	}

	/** Кости: крупье бросает четыре кости (белые — ваши, красные — дома), они кувыркаются и ложатся очками итога. */
	private static int throwDice(ServerWorld w, BlockPos o, Outcome out) {
		Vec3d hand = new Vec3d(o.getX() + 5.5, o.getY() + 2.9, o.getZ() + 9.3);
		NbtCompound nbt = new NbtCompound();
		nbt.putIntArray("dice", out.dice());
		spin(w, CasinoPropEntity.Kind.DICE, nbt);
		w.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, SoundCategory.BLOCKS, 1f, 0.7f);
		Tasks.schedule(w.getServer(), 14, () -> w.playSound(null, hand.x, hand.y - 0.8, hand.z - 0.8,
			SoundEvents.BLOCK_BONE_BLOCK_HIT, SoundCategory.BLOCKS, 1f, 1.4f));
		Tasks.schedule(w.getServer(), 18, () -> w.playSound(null, hand.x, hand.y - 0.8, hand.z - 0.8,
			SoundEvents.BLOCK_BONE_BLOCK_STEP, SoundCategory.BLOCKS, 1f, 1.6f));
		return 30;
	}

	private static final int ROULETTE_TICKS = CasinoPropEntity.ROULETTE_TICKS;
	private static final int[] SLOT_STOPS = CasinoPropEntity.SLOT_STOPS;

	// ================================================================== тексты

	private static int number(Random rnd, boolean red) {
		int n;
		do {
			n = 1 + rnd.nextInt(36);
		} while (RED.contains(n) != red);
		return n;
	}

	private static MutableText rouletteText(int n) {
		String color = n == 0 ? "zero" : RED.contains(n) ? "red" : "black";
		Formatting f = n == 0 ? Formatting.GREEN : RED.contains(n) ? Formatting.RED : Formatting.DARK_GRAY;
		return Text.translatable("diktatura.casino.roulette.result", n, Text.translatable("diktatura.casino.color." + color).formatted(f));
	}

	private static Text symbol(int i) {
		return Text.translatable("diktatura.casino.symbol." + i);
	}

	private static String pct(double v) {
		return String.format(Locale.ROOT, v == Math.floor(v) ? "%.0f" : "%.1f", v);
	}

	// ================================================================== крупье

	private static final class CroupierHandler implements NpcInteractions.Handler {
		private final Table table;

		CroupierHandler(Table table) {
			this.table = table;
		}

		@Override
		public boolean matches(Entity entity) {
			return entity instanceof NpcEntity n && table.npcId.equals(n.npcId());
		}

		@Override
		public void onOpen(ServerPlayerEntity player, Entity npc) {
			NpcRegistry.NpcData d = NpcRegistry.get(table.npcId);
			if (d != null) {
				Text line = d.phrase(player.getRandom().nextInt(d.phraseCount()));
				LAST.put(player.getUuid(), line);
				Voice.say(npc, d.displayName(), line, d.soundEvent(), d.pitch());
			}
		}

		@Override
		public UiPayloads.NpcScreen screen(ServerPlayerEntity player, Entity npc) {
			NpcRegistry.NpcData d = NpcRegistry.get(table.npcId);
			PlayerRecord rec = DiktaturaState.get(player.getServer()).player(player);
			CasinoSettings s = CasinoSettings.get();
			int bet = bet(player);
			List<Text> info = new ArrayList<>();
			info.add(Text.translatable("diktatura.casino.info.balance", rec.trudodni, bet));
			info.add(Text.translatable("diktatura.casino.info.limit", betsLeft(rec), s.betsPerHour(), s.taxPercent()));
			switch (table) {
				case ROULETTE -> info.add(Text.translatable("diktatura.casino.info.odds1", pct(s.red().official()), pct(s.red().real()),
					s.red().payout(), pct(s.zero().official()), pct(s.zero().real()), s.zero().payout()));
				case SLOTS -> info.add(Text.translatable("diktatura.casino.info.slots", pct(s.slotsJackpot().official()),
					pct(s.slotsJackpot().real()), s.slotsJackpot().payout(), pct(s.slotsPair().official()), pct(s.slotsPair().real()),
					s.slotsPair().payout()));
				case DICE -> info.add(Text.translatable("diktatura.casino.info.dice", pct(s.dice().official()), pct(s.dice().real()),
					s.dice().payout()));
			}
			boolean busy = BUSY.containsKey(table);
			if (busy) {
				info.add(Text.translatable("diktatura.casino.info.busy").formatted(Formatting.GOLD));
			}
			boolean canPlay = !busy && rec.trudodni >= bet && betsLeft(rec) > 0 && rec.stage == Stage.FREE;
			List<UiPayloads.NpcAction> actions = new ArrayList<>();
			actions.add(new UiPayloads.NpcAction("bet:-", Text.translatable("diktatura.casino.bet.down"), bet > s.minBet()));
			actions.add(new UiPayloads.NpcAction("bet:+", Text.translatable("diktatura.casino.bet.up"), bet < s.maxBet()));
			switch (table) {
				case ROULETTE -> {
					actions.add(new UiPayloads.NpcAction("play:red", Text.translatable("diktatura.casino.btn.red"), canPlay));
					actions.add(new UiPayloads.NpcAction("play:black", Text.translatable("diktatura.casino.btn.black"), canPlay));
					actions.add(new UiPayloads.NpcAction("play:zero", Text.translatable("diktatura.casino.btn.zero"), canPlay));
				}
				case SLOTS -> actions.add(new UiPayloads.NpcAction("play:slots", Text.translatable("diktatura.casino.btn.spin"), canPlay));
				case DICE -> actions.add(new UiPayloads.NpcAction("play:dice", Text.translatable("diktatura.casino.btn.roll"), canPlay));
			}
			actions.add(new UiPayloads.NpcAction("client:close", Text.translatable("diktatura.npc.leave"), true));
			Text line = LAST.getOrDefault(player.getUuid(), Text.empty());
			return new UiPayloads.NpcScreen(table.key, npc.getId(),
				d != null ? d.displayName() : npc.getName(),
				d != null ? Text.translatable(d.role()) : Text.empty(), line, info, actions,
				d != null ? d.accent() : 0xFF1F7A3A);
		}

		@Override
		public boolean action(ServerPlayerEntity player, Entity npc, String action) {
			switch (action) {
				case "bet:-" -> changeBet(player, -1);
				case "bet:+" -> changeBet(player, 1);
				default -> {
					if (!action.startsWith("play:")) {
						return false;
					}
					String game = action.substring(5);
					boolean allowed = switch (table) {
						case ROULETTE -> game.equals("red") || game.equals("black") || game.equals("zero");
						case SLOTS -> game.equals("slots");
						case DICE -> game.equals("dice");
					};
					if (!allowed) {
						return false;
					}
					Text refusal = play(player, npc, table, game);
					if (refusal == null) {
						// Окно закрылось на клиенте — смотрим на стол.
						return false;
					}
					LAST.put(player.getUuid(), refusal);
					NpcRegistry.NpcData d = NpcRegistry.get(table.npcId);
					Voice.say(npc, d != null ? d.displayName() : npc.getName(), refusal, null, 1.0f);
					Hud.alert(player, AlertKind.DANGER, refusal, Text.empty(), 3);
				}
			}
			return true;
		}
	}
}
