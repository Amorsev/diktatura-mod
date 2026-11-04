package com.diktatura.config;

import com.diktatura.Diktatura;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Настройки мода: config/diktatura.json. Законы настраиваются отдельно, в datapack. */
public final class DiktaturaConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static DiktaturaConfig instance = new DiktaturaConfig();

	/** Версия файла настроек (для переноса старых значений). */
	public int configVersion = 0;

	/** Как часто (в тиках) проверяются «постоянные» законы: взгляд, ночь, бег и т. п. */
	public int checkIntervalTicks = 20;
	/** Через сколько минут диктатор издаёт новый указ. */
	public int decreeIntervalMinutes = 5;
	/** Минимум и максимум одновременно действующих законов. */
	public int minActiveLaws = 6;
	public int maxActiveLaws = 12;

	/** Лояльность нового игрока (0–100). */
	public int startLoyalty = 50;
	/** На сколько растёт лояльность за минуту без нарушений. */
	public int loyaltyRegenPerMinute = 1;
	/** Ниже этого значения игрок считается неблагонадёжным. */
	public int lowLoyalty = 20;
	/** Выше этого значения игрок считается патриотом и получает награды. */
	public int highLoyalty = 80;
	/** Если средняя лояльность онлайн-игроков ниже — начинается восстание. */
	public int rebellionThreshold = 25;
	public int rebellionMinutes = 5;
	public int rebellionCooldownMinutes = 30;

	/** Срок в камере после отработки, секунды: от и до (если закон не задал своё). */
	public int minSentenceSeconds = 10;
	public int maxSentenceSeconds = 60;
	/** Срок «врага государства» (лояльность 0) в секундах. */
	public int defaultArrestSeconds = 30;
	/** Смещение тюрьмы от точки спавна мира. */
	public int prisonOffsetX = 40;
	public int prisonOffsetZ = 0;

	/** Скорость повозки в блоках за тик. */
	public double transportSpeed = 1.2;
	/** Дальше этого расстояния повозка «прыгает» поближе к цели, чтобы не грузить сотни чанков. */
	public int transportMaxDirectDistance = 600;

	/** Освободить операторов (уровень 2+) от законов. */
	public boolean exemptOps = false;
	/** Освободить игроков в творческом режиме от законов. */
	public boolean exemptCreative = false;
	/** Выдавать книгу законов при первом входе. */
	public boolean giveLawBook = true;

	/** Пытаться брать ценность предметов из мода Upgrader. */
	public boolean useUpgrader = true;
	/** Явно заданный класс и статический метод Upgrader (ItemStack или Item → число). Пусто — искать автоматически. */
	public String upgraderValueClass = "";
	public String upgraderValueMethod = "";

	public static DiktaturaConfig get() {
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(Diktatura.MOD_ID + ".json");
	}

	public static void load() {
		Path file = path();
		try {
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					DiktaturaConfig loaded = GSON.fromJson(r, DiktaturaConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
			migrate(instance);
			save();
		} catch (Exception e) {
			Diktatura.LOGGER.error("Не удалось прочитать {}, используются значения по умолчанию", file, e);
		}
	}

	private static final int CURRENT_VERSION = 2;

	/** Перенос старых настроек: v2 — указы раз в 5 минут вместо 20, срок в секундах. */
	private static void migrate(DiktaturaConfig c) {
		if (c.configVersion < 2) {
			if (c.decreeIntervalMinutes == 20) {
				c.decreeIntervalMinutes = 5;
			}
		}
		if (c.minSentenceSeconds < 1) {
			c.minSentenceSeconds = 10;
		}
		if (c.maxSentenceSeconds < c.minSentenceSeconds) {
			c.maxSentenceSeconds = c.minSentenceSeconds;
		}
		c.configVersion = CURRENT_VERSION;
	}

	public static void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
				GSON.toJson(instance, w);
			}
		} catch (Exception e) {
			Diktatura.LOGGER.error("Не удалось сохранить конфиг", e);
		}
	}
}
