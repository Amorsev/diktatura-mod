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
import net.minecraft.block.LeverBlock;
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

	private Casino() {
	}

	public static void registerEvents() {
		for (Table t : Table.values()) {
			NpcInteractions.register(t.key, new CroupierHandler(t));
		}
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
			state.casinoVersion = CasinoBuilding.VERSION;
			state.markDirty();
			Diktatura.LOGGER.info("Госказино перестроено: три стола (версия {})", CasinoBuilding.VERSION);
		}
		if (Diktatura.ticks() % 200 == 0) {
			for (Table t : Table.values()) {
				NpcPosts.ensure(server, t.key, t.npcId, o.add(t.post()), t.yaw);
			}
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
			case SLOTS -> new Vec3d(o.getX() + 4.5, o.getY() + 4.6, o.getZ() + 2.4);
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

	/** Колесо из 16 секторов (зелёный — зеро, дальше красный/чёрный) и шарик, бегущий навстречу. */
	private static int spinRoulette(ServerWorld w, BlockPos o, Outcome out) {
		Vec3d c = new Vec3d(o.getX() + CasinoBuilding.WHEEL[0], o.getY() + CasinoBuilding.WHEEL[1], o.getZ() + CasinoBuilding.WHEEL[2]);
		Table t = Table.ROULETTE;
		prop(t, CasinoFx.block(w, c, Blocks.DARK_OAK_PLANKS.getDefaultState(), CasinoFx.ring(0, 0, 0, 1.45f, 0.06f, 1.45f)));
		DisplayEntity hub = CasinoFx.block(w, c, Blocks.GOLD_BLOCK.getDefaultState(), CasinoFx.ring(0, 0, 0.06f, 0.28f, 0.14f, 0.28f));
		prop(t, hub);
		List<DisplayEntity> segs = new ArrayList<>();
		float step = (float) (Math.PI * 2 / SEGMENTS);
		for (int i = 0; i < SEGMENTS; i++) {
			BlockState st = (i == 0 ? Blocks.LIME_CONCRETE : i % 2 == 1 ? Blocks.RED_CONCRETE : Blocks.BLACK_CONCRETE).getDefaultState();
			DisplayEntity e = CasinoFx.block(w, c, st, CasinoFx.ring(i * step, 0.52f, 0.06f, 0.2f, 0.05f, 0.2f));
			segs.add(e);
			prop(t, e);
		}
		DisplayEntity ball = CasinoFx.block(w, c, Blocks.QUARTZ_BLOCK.getDefaultState(), CasinoFx.ring(0, 0.62f, 0.12f, 0.09f, 0.09f, 0.09f));
		prop(t, ball);

		// Сектор итога: зеро — 0, красное — нечётный, чёрное — чётный (не 0).
		Random rnd = w.getRandom();
		int seg;
		if (out.number() == 0) {
			seg = 0;
		} else if (RED.contains(out.number())) {
			seg = 1 + 2 * rnd.nextInt(SEGMENTS / 2);
		} else {
			seg = 2 + 2 * rnd.nextInt(SEGMENTS / 2 - 1);
		}
		int total = 66;
		int dt = 3;
		int steps = total / dt;
		// Колесо: замедляется; шарик бежит навстречу и останавливается на нужном секторе.
		float[] wheel = new float[steps + 1];
		for (int k = 1; k <= steps; k++) {
			float speed = (float) Math.toRadians(42.0 * (1.0 - (double) k / steps) + 3.0);
			wheel[k] = wheel[k - 1] + speed;
		}
		float ballEnd = wheel[steps] + seg * step;
		for (int k = 1; k <= steps; k++) {
			int kk = k;
			double left = 1.0 - (double) k / steps;
			float ballA = (float) (ballEnd + Math.PI * 6 * left * left);
			Tasks.schedule(w.getServer(), k * dt - dt, () -> {
				for (int i = 0; i < SEGMENTS; i++) {
					CasinoFx.animate(segs.get(i), CasinoFx.ring(wheel[kk] + i * step, 0.52f, 0.06f, 0.2f, 0.05f, 0.2f), dt);
				}
				CasinoFx.animate(ball, CasinoFx.ring(ballA, kk >= steps - 2 ? 0.52f : 0.62f, kk >= steps - 2 ? 0.11f : 0.12f,
					0.09f, 0.09f, 0.09f), dt);
				if (kk % Math.max(1, kk / 6) == 0) {
					w.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 0.6f, 1.6f);
				}
			});
		}
		w.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.5f, 1.6f);
		return total + 6;
	}

	/** Три барабана перед экраном автомата: крутятся и останавливаются по одному. */
	private static int spinSlots(ServerWorld w, BlockPos o, Outcome out) {
		Table t = Table.SLOTS;
		BlockPos lever = o.add(CasinoBuilding.LEVER);
		BlockState lv = w.getBlockState(lever);
		if (lv.isOf(Blocks.LEVER)) {
			w.setBlockState(lever, lv.with(LeverBlock.POWERED, true));
			w.playSound(null, lever, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1f, 0.6f);
		}
		BlockPos lamp = o.add(4, 4, 1);
		BlockState lampState = w.getBlockState(lamp);
		if (lampState.isOf(Blocks.REDSTONE_LAMP)) {
			w.setBlockState(lamp, lampState.with(net.minecraft.block.RedstoneLampBlock.LIT, true), net.minecraft.block.Block.NOTIFY_LISTENERS);
		}
		Random rnd = w.getRandom();
		DisplayEntity[] reels = new DisplayEntity[3];
		for (int i = 0; i < 3; i++) {
			Vec3d pos = new Vec3d(o.getX() + CasinoBuilding.REELS_X[i], o.getY() + CasinoBuilding.REELS_Y, o.getZ() + CasinoBuilding.REELS_Z);
			reels[i] = CasinoFx.item(w, pos, new ItemStack(SYMBOL_ITEMS[rnd.nextInt(SYMBOLS)]), 0.7f);
			prop(t, reels[i]);
		}
		int[] stops = {24, 38, 52};
		for (int tick = 2; tick <= stops[2]; tick += 2) {
			int now = tick;
			Tasks.schedule(w.getServer(), tick, () -> {
				for (int i = 0; i < 3; i++) {
					if (now < stops[i]) {
						CasinoFx.setItem(w, reels[i], new ItemStack(SYMBOL_ITEMS[w.getRandom().nextInt(SYMBOLS)]));
					} else if (now == stops[i]) {
						CasinoFx.setItem(w, reels[i], new ItemStack(SYMBOL_ITEMS[out.reels()[i]]));
						w.playSound(null, lamp, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.8f, 0.8f + i * 0.3f);
					}
				}
				if (now % 4 == 0) {
					w.playSound(null, lamp, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 0.4f, 0.8f + w.getRandom().nextFloat());
				}
			});
		}
		Tasks.schedule(w.getServer(), stops[2] + 4, () -> {
			BlockState l = w.getBlockState(lever);
			if (l.isOf(Blocks.LEVER)) {
				w.setBlockState(lever, l.with(LeverBlock.POWERED, false));
			}
			BlockState lm = w.getBlockState(lamp);
			if (lm.isOf(Blocks.REDSTONE_LAMP)) {
				w.setBlockState(lamp, lm.with(net.minecraft.block.RedstoneLampBlock.LIT, false), net.minecraft.block.Block.NOTIFY_LISTENERS);
			}
		});
		return stops[2] + 8;
	}

	/** Крупье бросает четыре кости через стол: белые — ваши, красные — дома. Кувыркаются и падают. */
	private static int throwDice(ServerWorld w, BlockPos o, Outcome out) {
		Table t = Table.DICE;
		Random rnd = w.getRandom();
		Vec3d hand = new Vec3d(o.getX() + 5.5, o.getY() + 2.9, o.getZ() + 9.3);
		double[][] land = {{3.9, 8.35}, {4.5, 8.65}, {5.6, 8.35}, {6.2, 8.65}};
		for (int n = 0; n < 4; n++) {
			final int i = n;
			Vec3d spot = new Vec3d(o.getX() + land[i][0], o.getY() + 2.07, o.getZ() + land[i][1]);
			Vec3d from = hand.subtract(spot);
			BlockState st = (i < 2 ? Blocks.WHITE_CONCRETE : Blocks.RED_CONCRETE).getDefaultState();
			float s = 0.22f;
			DisplayEntity die = CasinoFx.block(w, spot, st, cube((float) from.x, (float) from.y, (float) from.z, 0, 0, s));
			prop(t, die);
			float spinA = (float) (Math.PI * (2 + rnd.nextInt(3)));
			float spinB = (float) (Math.PI * (1 + rnd.nextInt(3)));
			Vec3d peak = from.multiply(0.5).add(0, 0.9, 0);
			Tasks.schedule(w.getServer(), 1, () -> CasinoFx.animate(die, cube((float) peak.x, (float) peak.y, (float) peak.z, spinA * 0.5f, spinB * 0.5f, s), 7));
			Tasks.schedule(w.getServer(), 8, () -> CasinoFx.animate(die, cube(0, 0.18f, 0, spinA * 0.9f, spinB * 0.9f, s), 6));
			Tasks.schedule(w.getServer(), 14, () -> CasinoFx.animate(die, cube(0, 0, 0, spinA, spinB, s), 4));
			// Над каждой костью — её очко.
			int value = out.dice()[i];
			Vec3d label = spot.add(0, 0.55, 0);
			Tasks.schedule(w.getServer(), 20, () -> prop(t, CasinoFx.text(w, label,
				Text.literal(String.valueOf(value)).formatted(i < 2 ? Formatting.WHITE : Formatting.RED, Formatting.BOLD))));
		}
		w.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, SoundCategory.BLOCKS, 1f, 0.7f);
		Tasks.schedule(w.getServer(), 14, () -> w.playSound(null, hand.x, hand.y - 0.8, hand.z - 0.8,
			SoundEvents.BLOCK_BONE_BLOCK_HIT, SoundCategory.BLOCKS, 1f, 1.4f));
		Tasks.schedule(w.getServer(), 18, () -> w.playSound(null, hand.x, hand.y - 0.8, hand.z - 0.8,
			SoundEvents.BLOCK_BONE_BLOCK_STEP, SoundCategory.BLOCKS, 1f, 1.6f));
		return 30;
	}

	/** Кубик со сдвигом (dx, dy, dz) от точки падения и поворотом; повороты кратны 90° к концу. */
	private static net.minecraft.util.math.AffineTransformation cube(float dx, float dy, float dz, float a, float b, float s) {
		Matrix4f m = new Matrix4f().translate(dx, dy + s / 2, dz).rotateX(a).rotateZ(b).scale(s).translate(-0.5f, -0.5f, -0.5f);
		return CasinoFx.matrix(m);
	}

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
