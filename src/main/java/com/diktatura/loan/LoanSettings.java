package com.diktatura.loan;

import com.diktatura.Diktatura;
import com.diktatura.law.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Настройки кассы микрозаймов: data/diktatura/diktatura/loans.json. {@code /reload} перечитывает.
 *
 * @param amounts         суммы займа на выбор (трудодни)
 * @param ratePercent     ставка за кредитный день, сложные проценты
 * @param dayTicks        длина кредитного дня в тиках (идёт, только пока должник в сети)
 * @param itemRate        сколько единиц ценности предмета (Upgrader / item_values.json) за один трудодень
 * @param ceiling         потолок долга: во сколько раз от базовой суммы цикла (этап D)
 * @param rebase          во сколько раз растёт база после тотальной конфискации (этап D)
 * @param cycles          сколько конфискаций до амнистии (этап D)
 * @param amnestyStone    камня на каменоломне для амнистии (этап D)
 * @param amnestyCooldownDays кредитных дней без новых займов после амнистии (этап D)
 * @param collectorMinutes не чаще чем раз в столько минут приходят коллекторы (этап C)
 * @param seizeRadius     радиус изъятия приставами с анимацией (этап D)
 * @param zoneRadius      радиус зоны государственного кредита вокруг столицы: за ней проценты не идут
 * @param blacklistDays   кредитных дней в чёрном списке заёмщиков после банкротства
 */
public record LoanSettings(List<Integer> amounts, double ratePercent, int dayTicks, double itemRate, int ceiling,
                           int rebase, int cycles, int amnestyStone, int amnestyCooldownDays, int collectorMinutes,
                           int seizeRadius, int zoneRadius, int blacklistDays) {
	public static final Identifier FILE = Diktatura.id("diktatura/loans.json");
	public static final LoanSettings DEFAULT = new LoanSettings(List.of(10, 25, 50, 100), 7.0, 24000, 32.0, 5, 3, 5,
		320, 3, 10, 10, 256, 5);

	private static volatile LoanSettings current = DEFAULT;

	public static LoanSettings get() {
		return current;
	}

	static LoanSettings parse(JsonObject o) {
		List<Integer> amounts = new ArrayList<>();
		if (o.has("amounts") && o.get("amounts").isJsonArray()) {
			for (JsonElement e : o.getAsJsonArray("amounts")) {
				int v = e.getAsInt();
				if (v > 0 && amounts.size() < 6) {
					amounts.add(v);
				}
			}
		}
		if (amounts.isEmpty()) {
			amounts = DEFAULT.amounts();
		}
		return new LoanSettings(List.copyOf(amounts),
			Math.max(0, Math.min(100, Json.d(o, "rate_percent", DEFAULT.ratePercent()))),
			Math.max(1200, Json.i(o, "day_ticks", DEFAULT.dayTicks())),
			Math.max(1, Json.d(o, "item_rate", DEFAULT.itemRate())),
			Math.max(2, Json.i(o, "ceiling", DEFAULT.ceiling())),
			Math.max(1, Json.i(o, "rebase", DEFAULT.rebase())),
			Math.max(1, Json.i(o, "cycles", DEFAULT.cycles())),
			Math.max(1, Json.i(o, "amnesty_stone", DEFAULT.amnestyStone())),
			Math.max(0, Json.i(o, "amnesty_cooldown_days", DEFAULT.amnestyCooldownDays())),
			Math.max(1, Json.i(o, "collector_minutes", DEFAULT.collectorMinutes())),
			Math.max(1, Math.min(32, Json.i(o, "seize_radius", DEFAULT.seizeRadius()))),
			Math.max(16, Json.i(o, "zone_radius", DEFAULT.zoneRadius())),
			Math.max(0, Json.i(o, "blacklist_days", DEFAULT.blacklistDays())));
	}

	public static final class ReloadListener implements SimpleSynchronousResourceReloadListener {
		@Override
		public Identifier getFabricId() {
			return Diktatura.id("loans");
		}

		@Override
		public void reload(ResourceManager manager) {
			Optional<Resource> res = manager.getResource(FILE);
			LoanSettings loaded = DEFAULT;
			if (res.isPresent()) {
				try (Reader r = res.get().getReader()) {
					loaded = parse(JsonParser.parseReader(r).getAsJsonObject());
				} catch (Exception e) {
					Diktatura.LOGGER.error("Ошибка в {}: {}", FILE, e.getMessage());
				}
			}
			current = loaded;
			Diktatura.LOGGER.info("Касса микрозаймов: суммы {}, ставка {}% в кредитный день", loaded.amounts(), loaded.ratePercent());
		}
	}
}
