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
