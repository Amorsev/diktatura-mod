package com.diktatura.casino;

import com.diktatura.Diktatura;
import com.diktatura.casino.CasinoSettings.Odds;
import com.diktatura.economy.Economy;
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
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Государственное казино: рулетка, слоты, кости за трудодни. Шансы подкручены и это
 * не скрывается — в окне крупье рядом официальный и фактический шанс. Выигрыш облагается
 * налогом, ставок в час — не больше лимита. Перед каждой ставкой проверяются законы
 * (событие GAMBLE): вождь может запретить игру без игорной лицензии.
 */
public final class Casino {
	public static final String CROUPIER_ID = "diktatura:croupier";
	private static final int[] BET_STEPS = {1, 2, 5, 10, 20, 50, 100, 200, 500};
	private static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
	private static final int SYMBOLS = 6;
	private static final long HOUR_MS = 60L * 60 * 1000;

	private static final Map<UUID, Integer> BET = new HashMap<>();
	private static final Map<UUID, Text> LAST = new HashMap<>();

	private Casino() {
	}

	public static void registerEvents() {
		NpcInteractions.register("croupier", new CroupierHandler());
	}

	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % 100 != 0) {
			return;
		}
		CasinoBuilding.ensureGenerated(server);
		DiktaturaState state = DiktaturaState.get(server);
		if (state.casinoGenerated && Diktatura.ticks() % 200 == 0) {
			NpcPosts.ensure(server, "croupier", CROUPIER_ID, state.casinoOrigin.add(CasinoBuilding.CROUPIER), 90.0f);
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

	/** Сыграть. Возвращает текст результата (его же говорит крупье). */
	static Text play(ServerPlayerEntity p, Entity croupier, String game) {
		MinecraftServer server = p.getServer();
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(p);
		CasinoSettings s = CasinoSettings.get();
		int bet = bet(p);
		if (rec.stage != Stage.FREE) {
			return Text.translatable("diktatura.casino.no.arrested");
		}
		if (betsLeft(rec) <= 0) {
			return Text.translatable("diktatura.casino.no.limit", s.betsPerHour());
		}
		if (rec.trudodni < bet) {
			return Text.translatable("diktatura.casino.no.money", bet);
		}
		// Закон: вождь может запретить азартные игры без лицензии.
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

		Random rnd = p.getRandom();
		int payout = 0;
		MutableText shown;
		switch (game) {
			case "red", "black" -> {
				boolean wantRed = game.equals("red");
				Odds o = s.red();
				boolean win = roll(rnd, o.real());
				int n = win ? number(rnd, wantRed) : rnd.nextInt(20) == 0 ? 0 : number(rnd, !wantRed);
				shown = rouletteText(n);
				payout = win ? bet * o.payout() : 0;
			}
			case "zero" -> {
				Odds o = s.zero();
				boolean win = roll(rnd, o.real());
				int n = win ? 0 : 1 + rnd.nextInt(36);
				shown = rouletteText(n);
				payout = win ? bet * o.payout() : 0;
			}
			case "slots" -> {
				double r = rnd.nextDouble() * 100.0;
				int a = rnd.nextInt(SYMBOLS);
				int b;
				int c;
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
				shown = Text.translatable("diktatura.casino.slots.result", symbol(a), symbol(b), symbol(c));
			}
			default -> {
				Odds o = s.dice();
				boolean win = roll(rnd, o.real());
				int you;
				int house;
				do {
					you = 2 + rnd.nextInt(6) + rnd.nextInt(6);
					house = 2 + rnd.nextInt(6) + rnd.nextInt(6);
				} while (win ? you <= house : you > house);
				shown = Text.translatable("diktatura.casino.dice.result", you, house);
				payout = win ? bet * o.payout() : 0;
			}
		}
		state.markDirty();

		MutableText result;
		if (payout > 0) {
			int profit = payout - bet;
			int tax = Math.max(0, Math.round(profit * s.taxPercent() / 100f));
			int net = payout - tax;
			Economy.add(p, net, "diktatura.money.reason.casino_win", "diktatura.casino.game." + game, false);
			result = Text.translatable("diktatura.casino.win", shown, net, tax);
			Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.casino.win.title", net), Text.translatable("diktatura.casino.win.tax", tax), 5);
			p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
			if (net - bet >= s.bigWin()) {
				Hud.alertAll(server, AlertKind.GOOD, Text.translatable("diktatura.casino.big", p.getDisplayName(), net),
					Text.translatable("diktatura.casino.big.hint"), 8);
			}
		} else {
			result = Text.translatable("diktatura.casino.lose", shown, bet);
			p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.8f, 0.6f);
		}
		NpcRegistry.NpcData d = NpcRegistry.get(CROUPIER_ID);
		Text quip = Text.translatable((payout > 0 ? "diktatura.casino.quip.win." : "diktatura.casino.quip.lose.") + rnd.nextInt(4));
		Voice.say(croupier, d != null ? d.displayName() : croupier.getName(), quip, null, 1.0f);
		return result;
	}

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
		@Override
		public boolean matches(Entity entity) {
			return entity instanceof NpcEntity n && CROUPIER_ID.equals(n.npcId());
		}

		@Override
		public void onOpen(ServerPlayerEntity player, Entity npc) {
			NpcRegistry.NpcData d = NpcRegistry.get(CROUPIER_ID);
			if (d != null) {
				Text line = d.phrase(player.getRandom().nextInt(d.phraseCount()));
				LAST.put(player.getUuid(), line);
				Voice.say(npc, d.displayName(), line, d.soundEvent(), d.pitch());
			}
		}

		@Override
		public UiPayloads.NpcScreen screen(ServerPlayerEntity player, Entity npc) {
			NpcRegistry.NpcData d = NpcRegistry.get(CROUPIER_ID);
			PlayerRecord rec = DiktaturaState.get(player.getServer()).player(player);
			CasinoSettings s = CasinoSettings.get();
			int bet = bet(player);
			List<Text> info = new ArrayList<>();
			info.add(Text.translatable("diktatura.casino.info.balance", rec.trudodni, bet));
			info.add(Text.translatable("diktatura.casino.info.limit", betsLeft(rec), s.betsPerHour(), s.taxPercent()));
			info.add(Text.translatable("diktatura.casino.info.odds1", pct(s.red().official()), pct(s.red().real()), s.red().payout(),
				pct(s.zero().official()), pct(s.zero().real()), s.zero().payout()));
			info.add(Text.translatable("diktatura.casino.info.odds2", pct(s.dice().official()), pct(s.dice().real()), s.dice().payout(),
				pct(s.slotsJackpot().official()), pct(s.slotsJackpot().real()), s.slotsJackpot().payout()));
			boolean canPlay = rec.trudodni >= bet && betsLeft(rec) > 0 && rec.stage == Stage.FREE;
			List<UiPayloads.NpcAction> actions = List.of(
				new UiPayloads.NpcAction("bet:-", Text.translatable("diktatura.casino.bet.down"), bet > s.minBet()),
				new UiPayloads.NpcAction("bet:+", Text.translatable("diktatura.casino.bet.up"), bet < s.maxBet()),
				new UiPayloads.NpcAction("red", Text.translatable("diktatura.casino.btn.red"), canPlay),
				new UiPayloads.NpcAction("black", Text.translatable("diktatura.casino.btn.black"), canPlay),
				new UiPayloads.NpcAction("zero", Text.translatable("diktatura.casino.btn.zero"), canPlay),
				new UiPayloads.NpcAction("slots", Text.translatable("diktatura.casino.btn.slots"), canPlay),
				new UiPayloads.NpcAction("dice", Text.translatable("diktatura.casino.btn.dice"), canPlay),
				new UiPayloads.NpcAction("client:close", Text.translatable("diktatura.npc.leave"), true));
			Text line = LAST.getOrDefault(player.getUuid(), Text.empty());
			return new UiPayloads.NpcScreen("croupier", npc.getId(),
				d != null ? d.displayName() : npc.getName(),
				d != null ? Text.translatable(d.role()) : Text.empty(), line, info, actions,
				d != null ? d.accent() : 0xFF1F7A3A);
		}

		@Override
		public boolean action(ServerPlayerEntity player, Entity npc, String action) {
			switch (action) {
				case "bet:-" -> changeBet(player, -1);
				case "bet:+" -> changeBet(player, 1);
				case "red", "black", "zero", "slots", "dice" -> LAST.put(player.getUuid(), play(player, npc, action));
				default -> {
					return false;
				}
			}
			return true;
		}
	}
}
