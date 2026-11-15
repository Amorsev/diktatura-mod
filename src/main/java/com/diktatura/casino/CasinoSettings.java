package com.diktatura.casino;

import com.diktatura.Diktatura;
import com.diktatura.law.Json;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.Optional;

/**
 * Настройки госказино: data/diktatura/diktatura/casino.json. Шансы — в процентах.
 * «Официальный» шанс показывается игроку, «фактический» используется на самом деле.
 * Казино жульничает открыто: оба числа видны в окне крупье.
 */
public record CasinoSettings(int minBet, int maxBet, int betsPerHour, int taxPercent, int bigWin,
                             Odds red, Odds zero, Odds slotsJackpot, Odds slotsPair, Odds dice, Poker poker) {

	/**
	 * Покер (раздел "poker"): блайнды, пределы бай-ина, налог государства с банка (%), порог «слабой руки» для
	 * скрытого закона о блефе (шансы против одного соперника, 0..1) и точность расчёта шансов у ботов.
	 */
	public record Poker(int smallBlind, int bigBlind, int minBuyIn, int maxBuyIn, int taxPercent, double bluffThreshold,
	                    int botIterations) {
		static Poker parse(JsonObject root) {
			JsonObject o = root.has("poker") ? root.getAsJsonObject("poker") : new JsonObject();
			int sb = Math.max(1, Json.i(o, "small_blind", 1));
			int bb = Math.max(sb, Json.i(o, "big_blind", 2));
			int min = Math.max(bb * 2, Json.i(o, "min_buy_in", 20));
			return new Poker(sb, bb, min, Math.max(min, Json.i(o, "max_buy_in", 200)),
				Math.max(0, Math.min(100, Json.i(o, "tax_percent", 5))),
				Math.max(0, Math.min(1, Json.d(o, "bluff_threshold", 0.38))),
				Math.max(50, Math.min(5000, Json.i(o, "bot_iterations", 300))));
		}

		public com.diktatura.casino.poker.PokerTable.Rules rules() {
			return new com.diktatura.casino.poker.PokerTable.Rules(smallBlind, bigBlind, taxPercent, bluffThreshold);
		}
	}

	/** Шанс выигрыша: официальный (для вывески), фактический (для броска) и множитель выплаты. */
	public record Odds(double official, double real, int payout) {
		static Odds parse(JsonObject root, String key, double official, double real, int payout) {
			if (!root.has(key)) {
				return new Odds(official, real, payout);
			}
			JsonObject o = root.getAsJsonObject(key);
			return new Odds(Json.d(o, "official_chance", official), Json.d(o, "real_chance", real),
				Math.max(1, Json.i(o, "payout", payout)));
		}
	}

	public static final Identifier FILE = Identifier.of(Diktatura.MOD_ID, "diktatura/casino.json");

	private static volatile CasinoSettings current = defaults();

	public static CasinoSettings get() {
		return current;
	}

	static CasinoSettings defaults() {
		return parse(new JsonObject());
	}

	static CasinoSettings parse(JsonObject o) {
		int min = Math.max(1, Json.i(o, "min_bet", 1));
		return new CasinoSettings(min, Math.max(min, Json.i(o, "max_bet", 50)), Math.max(1, Json.i(o, "bets_per_hour", 30)),
			Math.max(0, Math.min(100, Json.i(o, "tax_percent", 13))), Math.max(1, Json.i(o, "big_win", 100)),
			Odds.parse(o, "roulette_color", 48.6, 30, 2),
			Odds.parse(o, "roulette_zero", 2.7, 0.8, 30),
			Odds.parse(o, "slots_jackpot", 5, 0.5, 30),
			Odds.parse(o, "slots_pair", 30, 12, 3),
			Odds.parse(o, "dice", 50, 30, 2),
			Poker.parse(o));
	}

	public static final class ReloadListener implements SimpleSynchronousResourceReloadListener {
		@Override
		public Identifier getFabricId() {
			return Diktatura.id("casino");
		}

		@Override
		public void reload(ResourceManager manager) {
			Optional<Resource> res = manager.getResource(FILE);
			if (res.isEmpty()) {
				current = defaults();
				return;
			}
			try (Reader r = res.get().getReader()) {
				current = parse(JsonParser.parseReader(r).getAsJsonObject());
			} catch (Exception e) {
				Diktatura.LOGGER.error("Ошибка в {}: {}", FILE, e.getMessage());
				current = defaults();
			}
		}
	}
}
