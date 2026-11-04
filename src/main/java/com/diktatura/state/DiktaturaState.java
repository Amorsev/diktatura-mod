package com.diktatura.state;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/**
 * StateManager из плана: всё, что должно пережить перезапуск сервера.
 * Хранится в data/diktatura.dat мира (обычный мир) через PersistentState (SavedData).
 */
public final class DiktaturaState extends PersistentState {
	private static final Type<DiktaturaState> TYPE = new Type<>(DiktaturaState::new, DiktaturaState::fromNbt, null);

	public final Map<UUID, PlayerRecord> players = new HashMap<>();
	public final LinkedHashSet<String> activeLaws = new LinkedHashSet<>();
	public boolean lawsInitialized;

	public boolean prisonGenerated;
	public BlockPos prisonOrigin = BlockPos.ORIGIN;
	public boolean minigamesGenerated;

	public UUID dictator;
	public int ticksToDecree = -1;
	public int decreeCount;
	/** Отдельный случайный таймер событий. */
	public int ticksToEvent = -1;
	public int eventCount;

	public int rebellionTicks;
	public int rebellionCooldown;

	public static DiktaturaState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, Diktatura.MOD_ID);
	}

	public PlayerRecord player(ServerPlayerEntity p) {
		return player(p.getUuid());
	}

	public PlayerRecord player(UUID id) {
		return players.computeIfAbsent(id, k -> {
			markDirty();
			return new PlayerRecord(DiktaturaConfig.get().startLoyalty);
		});
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		NbtCompound pl = new NbtCompound();
		players.forEach((id, rec) -> pl.put(id.toString(), rec.toNbt()));
		nbt.put("players", pl);

		NbtList laws = new NbtList();
		activeLaws.forEach(l -> laws.add(NbtString.of(l)));
		nbt.put("activeLaws", laws);
		nbt.putBoolean("lawsInitialized", lawsInitialized);

		nbt.putBoolean("prisonGenerated", prisonGenerated);
		nbt.putLong("prisonOrigin", prisonOrigin.asLong());
		nbt.putBoolean("minigamesGenerated", minigamesGenerated);
		if (dictator != null) {
			nbt.putUuid("dictator", dictator);
		}
		nbt.putInt("ticksToDecree", ticksToDecree);
		nbt.putInt("decreeCount", decreeCount);
		nbt.putInt("ticksToEvent", ticksToEvent);
		nbt.putInt("eventCount", eventCount);
		nbt.putInt("rebellionTicks", rebellionTicks);
		nbt.putInt("rebellionCooldown", rebellionCooldown);
		return nbt;
	}

	public static DiktaturaState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		DiktaturaState s = new DiktaturaState();
		NbtCompound pl = nbt.getCompound("players");
		for (String key : pl.getKeys()) {
			try {
				s.players.put(UUID.fromString(key), PlayerRecord.fromNbt(pl.getCompound(key)));
			} catch (IllegalArgumentException ignored) {
			}
		}
		NbtList laws = nbt.getList("activeLaws", NbtElement.STRING_TYPE);
		for (int i = 0; i < laws.size(); i++) {
			s.activeLaws.add(laws.getString(i));
		}
		s.lawsInitialized = nbt.getBoolean("lawsInitialized");
		s.prisonGenerated = nbt.getBoolean("prisonGenerated");
		s.prisonOrigin = BlockPos.fromLong(nbt.getLong("prisonOrigin"));
		s.minigamesGenerated = nbt.getBoolean("minigamesGenerated");
		if (nbt.containsUuid("dictator")) {
			s.dictator = nbt.getUuid("dictator");
		}
		s.ticksToDecree = nbt.contains("ticksToDecree") ? nbt.getInt("ticksToDecree") : -1;
		s.decreeCount = nbt.getInt("decreeCount");
		s.ticksToEvent = nbt.contains("ticksToEvent") ? nbt.getInt("ticksToEvent") : -1;
		s.eventCount = nbt.getInt("eventCount");
		s.rebellionTicks = nbt.getInt("rebellionTicks");
		s.rebellionCooldown = nbt.getInt("rebellionCooldown");
		return s;
	}
}
