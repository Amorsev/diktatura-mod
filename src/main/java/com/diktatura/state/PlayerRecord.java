package com.diktatura.state;

import net.minecraft.nbt.NbtCompound;

/** Состояние одного игрока: лояльность, страх, срок, точка задержания. */
public final class PlayerRecord {
	public int loyalty;
	public int fear;
	public int violations;
	public int arrests;
	public Stage stage = Stage.FREE;
	public int sentenceTicks;
	public int cell = -1;

	public String detentionDim = "";
	public double detX, detY, detZ;
	public float detYaw;
	public String prevGameMode = "";

	public int timeoutTicks;
	public int cleanTicks;
	public int rewardTicks;
	public boolean gotBook;

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
		return r;
	}
}
