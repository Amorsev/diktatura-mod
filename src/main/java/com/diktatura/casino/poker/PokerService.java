package com.diktatura.casino.poker;

import com.diktatura.casino.CasinoSettings;
import com.diktatura.economy.Economy;
import com.diktatura.law.LawContext;
import com.diktatura.law.LawEngine;
import com.diktatura.law.LawEvent;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.Stage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Покерные столы на сервере: деньги и законы вокруг {@link PokerTable}. Бай-ин списывается с баланса трудодней,
 * встал из-за стола — оставшиеся фишки возвращаются; вскрытый блеф против бота — закон {@code no_bluffing}.
 * Экран стола, таймер хода и боты «с задержкой» подключаются к этому классу в этапе 7.
 */
public final class PokerService {
	private static final Map<String, PokerTable> TABLES = new HashMap<>();

	private PokerService() {
	}

	/** Стол по ключу (создаётся по правилам из casino.json). */
	public static PokerTable table(String key) {
		return TABLES.computeIfAbsent(key, k -> new PokerTable(CasinoSettings.get().poker().rules(), new Random()));
	}

	/** Сесть за стол: проверки и бай-ин. Возвращает null при успехе или причину отказа. */
	public static Text sit(ServerPlayerEntity p, String key, int buyIn) {
		CasinoSettings.Poker s = CasinoSettings.get().poker();
		PokerTable t = table(key);
		if (t.seatOf(p.getUuidAsString()) >= 0) {
			return Text.translatable("diktatura.poker.no.seated");
		}
		int seat = t.freeSeat();
		if (seat < 0) {
			return Text.translatable("diktatura.poker.no.full");
		}
		if (DiktaturaState.get(p.getServer()).player(p).stage != Stage.FREE) {
			return Text.translatable("diktatura.casino.no.arrested");
		}
		if (buyIn < s.minBuyIn() || buyIn > s.maxBuyIn()) {
			return Text.translatable("diktatura.poker.no.buy_in", s.minBuyIn(), s.maxBuyIn());
		}
		if (Economy.balance(p) < buyIn) {
			return Text.translatable("diktatura.casino.no.money", buyIn);
		}
		if (LawEngine.fire(new LawContext(p, LawEvent.GAMBLE))) {
			return Text.translatable("diktatura.casino.no.law");
		}
		Economy.add(p, -buyIn, "diktatura.money.reason.poker_buy_in", "", false);
		t.sit(seat, new PokerTable.Seat(p.getUuidAsString(), p.getName().getString(), null, buyIn));
		return null;
	}

	/** Посадить бота (фишки у бота свои, с баланса никого не списываются). */
	public static boolean addBot(String key, PokerBot.Level level, String name) {
		PokerTable t = table(key);
		int seat = t.freeSeat();
		if (seat < 0) {
			return false;
		}
		int stack = CasinoSettings.get().poker().maxBuyIn() / 2;
		t.sit(seat, new PokerTable.Seat("bot-" + UUID.randomUUID(), name, level, stack));
		return true;
	}

	/** Встать из-за стола (в раздаче — сброс карт); фишки — на баланс. */
	public static int stand(ServerPlayerEntity p, String key) {
		PokerTable t = TABLES.get(key);
		if (t == null) {
			return 0;
		}
		int seat = t.seatOf(p.getUuidAsString());
		if (seat < 0) {
			return 0;
		}
		int chips = t.leave(seat);
		if (chips > 0) {
			Economy.add(p, chips, "diktatura.money.reason.poker_cash_out", "", true);
		}
		handle(p.getServer(), t.drainEvents());
		return chips;
	}

	/** Ходы ботов, пока очередь за ними (в этапе 7 — с паузами «на подумать»). */
	public static void runBots(PokerTable t, Random rng) {
		int guard = 0;
		while (t.inHand() && t.toAct() >= 0 && t.seat(t.toAct()) != null && t.seat(t.toAct()).isBot() && guard++ < 200) {
			int seat = t.toAct();
			t.act(seat, PokerBot.decide(t, seat, rng, CasinoSettings.get().poker().botIterations()));
		}
	}

	/** Законы по событиям стола: вскрытый блеф против бота — проверка poker_bluff. */
	public static List<PokerTable.Event> handle(MinecraftServer server, List<PokerTable.Event> events) {
		for (PokerTable.Event e : events) {
			if (e instanceof PokerTable.Event.BluffRevealed b) {
				ServerPlayerEntity p = player(server, b.id());
				if (p != null) {
					LawEngine.fire(new LawContext(p, LawEvent.POKER_BLUFF));
				}
			}
		}
		return events;
	}

	private static ServerPlayerEntity player(MinecraftServer server, String id) {
		try {
			return server.getPlayerManager().getPlayer(UUID.fromString(id));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Команда /diktatura poker sim: прогон раздач в отдельном потоке, итог — в чат. */
	public static void simulate(MinecraftServer server, int hands, java.util.function.Consumer<List<Text>> report) {
		int iterations = Math.min(120, CasinoSettings.get().poker().botIterations());
		Thread th = new Thread(() -> {
			PokerSim.Result r = PokerSim.run(hands, System.nanoTime(), iterations);
			List<Text> out = new java.util.ArrayList<>();
			out.add(Text.translatable("diktatura.cmd.poker.sim.result", r.hands(), r.tax(), r.sidePots(), r.splits(), r.bluffs()));
			for (PokerBot.Level l : PokerBot.Level.values()) {
				out.add(Text.translatable("diktatura.cmd.poker.sim.profit",
					Text.translatable("diktatura.poker.bot." + l.id), r.profit().getOrDefault(l, 0L)));
			}
			out.add(r.mismatches() == 0 && r.errors() == 0
				? Text.translatable("diktatura.cmd.poker.sim.ok")
				: Text.translatable("diktatura.cmd.poker.sim.fail", r.mismatches(), r.errors(), String.valueOf(r.firstError())));
			server.execute(() -> report.accept(out));
		}, "diktatura-poker-sim");
		th.setDaemon(true);
		th.start();
	}
}
