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
	/** События (амнистия, подарки, конфискация, «один глаз», невесомость) — случайно, раз в N–M минут. */
	public int eventMinMinutes = 3;
	public int eventMaxMinutes = 8;
	/** Сколько раз за событие «невесомость» отключается гравитация (по 5 секунд). */
	public int gravityPulsesMin = 3;
	public int gravityPulsesMax = 5;

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
	/** В новом мире строить столицу горой: тюрьма внутри, зал, кабинеты и казино на вершине. */
	public boolean capitalMountain = true;
	/** Дикие гуси-воры: подселять стайки рядом с игроками (природный спавн на лугах — всегда). */
	public boolean wildGeese = true;
	/** Бюро проверки гражданства: шанс капчи у стола зачарований, наковальни и пр. (0 — выключить). */
	public double captchaChance = 0.5;
	/** Комендантский час с двойником: нарушителю ночью раз за ночь является двойник-агент. */
	public boolean curfewDouble = true;
	/** Мобы-силовики: зомби-стройбат и снайперы госохраны. */
	public boolean enforcerMobs = true;
	public double builderZombieChance = 0.2;
	public double sniperSkeletonChance = 0.1;
	/** Препарат с искажениями (странные грибы, экранные эффекты). По умолчанию выключен. */
	public boolean distortionDrug = false;
	/** Финальный бой: дракон Края — госохрана вождя; после его гибели вождя можно вызвать на бой. */
	public boolean finalFight = true;
	/** Кровавая луна: восстание, начавшееся ночью, длится до рассвета; плюс сама раз в N ночей (0 — только при восстании). */
	public boolean bloodMoon = true;
	public int bloodMoonEveryNights = 7;
	/** В Кровавую луну рядом с игроками появляются дополнительные враждебные мобы. */
	public boolean bloodMoonExtraMobs = true;
	/** Кричащая еда: шанс, что еда заговорит, когда её начинают есть (0 — выключить). */
	public double screamingFoodChance = 0.3;
	/** Сундук-ревизор: шанс, что одиночный сундук с добычей окажется мимиком (0 — выключить). */
	public double mimicChance = 0.3;
	/** Эмиграция руды: при добыче руда может отрастить ножки и убежать (шахтёрская лицензия вдвое снижает шанс). */
	public boolean runawayOre = true;
	public double runawayDiamondChance = 0.35;
	public double runawayOreChance = 0.06;
	public int dragonHealth = 400;
	public int dictatorHealth = 300;
	/** Сколько игровых дней законы не действуют на кавалеров ордена «За ничью с вождём». */
	public int immunityDays = 3;

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

	/** График чиновников (Кадров, Печаткин): на месте столько секунд... */
	public int officeOpenMinSeconds = 90;
	public int officeOpenMaxSeconds = 180;
	/** ...а потом пропадают на столько минут. */
	public int officeClosedMinMinutes = 8;
	public int officeClosedMaxMinutes = 15;

	/** Ведомства-визитёры (налоговик, курьер): каждому игроку раз в столько минут. */
	public int visitMinMinutes = 10;
	public int visitMaxMinutes = 18;
	/** Госреклама на 13 секунд: раз в столько минут (0 — выключить). */
	public int adMinMinutes = 15;
	public int adMaxMinutes = 25;
	/** Ведомство погоды (коровопад, наковальнепад, гуманитарная помощь): раз в столько минут. */
	public int weatherMinMinutes = 20;
	public int weatherMaxMinutes = 35;
	/** Пропаганда по радио в чате: раз в столько минут (0 — выключить). */
	public int propagandaMinutes = 7;

	/** Дерево падает целиком, если срубить ствол (на корточках — по одному бревну). */
	public boolean timber = true;
	/** Обвалы при добыче камня под землёй: шанс в процентах (с шахтёрской лицензией — вчетверо меньше). */
	public double caveInChance = 0.6;
	/** Простуда: секунд под дождём или в воде до болезни (0 — выключить) и сколько болеть. */
	public int coldAfterSeconds = 60;
	public int coldMinutes = 5;

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
		if (c.eventMinMinutes < 1) {
			c.eventMinMinutes = 3;
		}
		if (c.eventMaxMinutes < c.eventMinMinutes) {
			c.eventMaxMinutes = c.eventMinMinutes;
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
