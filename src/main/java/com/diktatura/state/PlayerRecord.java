package com.diktatura.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/** Состояние одного игрока: лояльность, страх, срок, точка задержания. */
public final class PlayerRecord {
	public int loyalty;
	public int fear;
	public int violations;
	public int arrests;
	public Stage stage = Stage.FREE;
	public int sentenceTicks;
	public int cell = -1;
	/** Мини-игра отработки ({@link com.diktatura.minigame.MinigameType}), -1 — нет. */
	public int minigame = -1;
	/** Прогресс отработки: добытый камень или пройденная контрольная точка паркура. */
	public int progress;
	/** Помилован (амнистия) во время отработки. */
	public boolean pardoned;
	/** Вещи, изъятые на время ареста. null — ничего не изъято. */
	public NbtList stash;

	public String detentionDim = "";
	public double detX, detY, detZ;
	public float detYaw;
	public String prevGameMode = "";

	public int timeoutTicks;
	public int cleanTicks;
	public int rewardTicks;
	public boolean gotBook;
	/** Трудодни — виртуальная валюта (не предмет: не теряется при смерти, не конфискуется). */
	public int trudodni;
	/** Последние операции с трудоднями: {a: сумма, r: ключ причины, x: аргумент, t: время мс}. */
	public NbtList moneyLog = new NbtList();
	/** Лицензии: id → игровое время мира (overworld.getTime), до которого лицензия действует. */
	public NbtCompound licenses = new NbtCompound();
	/** Открытые игроком лицензируемые занятия (раздел книги законов, пополняется при нарушениях). */
	public NbtCompound activities = new NbtCompound();
	/** Казино: начало текущего часа (мс) и сколько ставок в нём сделано. */
	public long casinoHourStart;
	public int casinoBets;
	/** Таймеры ведомств (тики, идут только онлайн): следующий визит и следующая реклама. */
	public int visitTicks = -1;
	public int adTicks = -1;
	/** Простуда: накопленный холод (тики под дождём или в воде) и сколько ещё болеть. */
	public int chill;
	public int coldTicks;
	/** Иммунитет от законов (орден «За ничью с вождём»): до какого игрового времени мира. */
	public long immuneUntil;
	/** В каких измерениях уже был (1 — Незер, 2 — Край): первый вход — долгая госзагрузка. */
	public int visited;
	/** Текущая работа с биржи (null — без работы). */
	public NbtCompound job;

	public PlayerRecord(int loyalty) {
		this.loyalty = loyalty;
	}

	public boolean hasDetention() {
		return !detentionDim.isEmpty();
	}

	public NbtCompound toNbt() {
		NbtCompound n = new NbtCompound();
		n.putInt("loyalty", loyalty);
		n.putInt("fear", fear);
		n.putInt("violations", violations);
		n.putInt("arrests", arrests);
		n.putInt("stage", stage.ordinal());
		n.putInt("sentence", sentenceTicks);
		n.putInt("cell", cell);
		n.putString("detDim", detentionDim);
		n.putDouble("detX", detX);
		n.putDouble("detY", detY);
		n.putDouble("detZ", detZ);
		n.putFloat("detYaw", detYaw);
		n.putString("prevGameMode", prevGameMode);
		n.putInt("timeout", timeoutTicks);
		n.putInt("clean", cleanTicks);
		n.putInt("reward", rewardTicks);
		n.putBoolean("gotBook", gotBook);
		n.putInt("trudodni", trudodni);
		n.put("moneyLog", moneyLog);
		n.put("licenses", licenses);
		n.put("activities", activities);
		n.putLong("casinoHourStart", casinoHourStart);
		n.putInt("casinoBets", casinoBets);
		n.putInt("visitTicks", visitTicks);
		n.putInt("adTicks", adTicks);
		n.putInt("chill", chill);
		n.putInt("coldTicks", coldTicks);
		n.putLong("immuneUntil", immuneUntil);
		n.putInt("visited", visited);
		if (job != null) {
			n.put("job", job);
		}
		n.putInt("minigame", minigame);
		n.putInt("progress", progress);
		n.putBoolean("pardoned", pardoned);
		if (stash != null) {
			n.put("stash", stash);
		}
		return n;
	}

	public static PlayerRecord fromNbt(NbtCompound n) {
		PlayerRecord r = new PlayerRecord(n.getInt("loyalty"));
		r.fear = n.getInt("fear");
		r.violations = n.getInt("violations");
		r.arrests = n.getInt("arrests");
		r.stage = Stage.byOrdinal(n.getInt("stage"));
		r.sentenceTicks = n.getInt("sentence");
		r.cell = n.contains("cell") ? n.getInt("cell") : -1;
		r.detentionDim = n.getString("detDim");
		r.detX = n.getDouble("detX");
		r.detY = n.getDouble("detY");
		r.detZ = n.getDouble("detZ");
		r.detYaw = n.getFloat("detYaw");
		r.prevGameMode = n.getString("prevGameMode");
		r.timeoutTicks = n.getInt("timeout");
		r.cleanTicks = n.getInt("clean");
		r.rewardTicks = n.getInt("reward");
		r.gotBook = n.getBoolean("gotBook");
		r.trudodni = n.getInt("trudodni");
		r.moneyLog = n.getList("moneyLog", NbtElement.COMPOUND_TYPE);
		r.licenses = n.getCompound("licenses");
		r.activities = n.getCompound("activities");
		r.casinoHourStart = n.getLong("casinoHourStart");
		r.casinoBets = n.getInt("casinoBets");
		r.visitTicks = n.contains("visitTicks") ? n.getInt("visitTicks") : -1;
		r.adTicks = n.contains("adTicks") ? n.getInt("adTicks") : -1;
		r.chill = n.getInt("chill");
		r.coldTicks = n.getInt("coldTicks");
		r.immuneUntil = n.getLong("immuneUntil");
		r.visited = n.getInt("visited");
		if (n.contains("job")) {
			r.job = n.getCompound("job");
		}
		r.minigame = n.contains("minigame") ? n.getInt("minigame") : -1;
		r.progress = n.getInt("progress");
		r.pardoned = n.getBoolean("pardoned");
		if (n.contains("stash")) {
			r.stash = n.getList("stash", NbtElement.COMPOUND_TYPE);
		}
		return r;
	}
}
