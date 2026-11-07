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
	/** Законы, отменённые указами (для вкладки «Отменённые» в книге законов). */
	public final LinkedHashSet<String> repealedLaws = new LinkedHashSet<>();

	public boolean prisonGenerated;
	public BlockPos prisonOrigin = BlockPos.ORIGIN;
	public boolean minigamesGenerated;
	/** Тронный зал вождя (с 0.1.5): отдельное здание к северу от тюрьмы. */
	public boolean throneGenerated;
	public BlockPos throneOrigin = BlockPos.ORIGIN;
	/** 2 — большой тронный зал на горе (0.2.1+), иначе старый маленький. */
	public int throneVersion;
	/** Столица сгенерирована горой: тюрьма внутри, зал, кабинеты и казино на вершине (0.2.1+). */
	public boolean capitalGenerated;
	/** Дом госучреждений (с 0.1.8): кабинеты Кадрова и Печаткина и их график. */
	public boolean officesGenerated;
	public BlockPos officesOrigin = BlockPos.ORIGIN;
	public boolean officeScheduleStarted;
	/** Госказино (с 0.1.9). */
	public boolean casinoGenerated;
	/** Версия интерьера казино (2 — три стола: рулетка, слоты, кости). */
	public int casinoVersion;
	/** Мусор (выпавшие при стройке фонари и ковры) с плато столицы уже убран. */
	public boolean capitalSwept;
	/** Финальный бой: дракон госохраны повержен, вождь без защиты (можно вызвать на бой). */
	public boolean guardDown;
	public BlockPos casinoOrigin = BlockPos.ORIGIN;
	public final boolean[] officeOpen = new boolean[2];
	public final int[] officeTicks = new int[2];

	public UUID dictator;
	/** Служащий трудовой биржи у тронного зала. */
	public UUID employer;
	/** Посты НПС-ведомств: ключ поста → UUID НПС ({@link com.diktatura.npc.NpcPosts}). */
	public final Map<String, UUID> npcPosts = new HashMap<>();
	/** 2 — вождь уже переведён с жителя на НПС (версия 0.1.5). */
	public int npcVersion;
	public int ticksToDecree = -1;
	public int decreeCount;
	/** Отдельный случайный таймер событий. */
	public int ticksToEvent = -1;
	public int eventCount;

	/** Ведомство погоды и пропаганда: тики до следующего раза (-1 — назначить). */
	public int weatherTicks = -1;
	public int propagandaTicks = -1;

	public int rebellionTicks;
	public int rebellionCooldown;
	/** Текущее восстание — Кровавая луна (до рассвета). */
	public boolean bloodMoon;
	/** Номер дня последней Кровавой луны (чтобы не повторялась в ту же ночь). */
	public long bloodMoonDay = -1;

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
		NbtList repealed = new NbtList();
		repealedLaws.forEach(l -> repealed.add(NbtString.of(l)));
		nbt.put("repealedLaws", repealed);

		nbt.putBoolean("prisonGenerated", prisonGenerated);
		nbt.putLong("prisonOrigin", prisonOrigin.asLong());
		nbt.putBoolean("minigamesGenerated", minigamesGenerated);
		nbt.putBoolean("throneGenerated", throneGenerated);
		nbt.putLong("throneOrigin", throneOrigin.asLong());
		nbt.putInt("throneVersion", throneVersion);
		nbt.putBoolean("capitalGenerated", capitalGenerated);
		nbt.putBoolean("officesGenerated", officesGenerated);
		nbt.putLong("officesOrigin", officesOrigin.asLong());
		nbt.putBoolean("officeScheduleStarted", officeScheduleStarted);
		nbt.putBoolean("casinoGenerated", casinoGenerated);
		nbt.putInt("casinoVersion", casinoVersion);
		nbt.putBoolean("capitalSwept", capitalSwept);
		nbt.putBoolean("guardDown", guardDown);
		nbt.putLong("casinoOrigin", casinoOrigin.asLong());
		for (int i = 0; i < officeOpen.length; i++) {
			nbt.putBoolean("officeOpen" + i, officeOpen[i]);
			nbt.putInt("officeTicks" + i, officeTicks[i]);
		}
		if (dictator != null) {
			nbt.putUuid("dictator", dictator);
		}
		if (employer != null) {
			nbt.putUuid("employer", employer);
		}
		NbtCompound posts = new NbtCompound();
		npcPosts.forEach((k, v) -> posts.putUuid(k, v));
		nbt.put("npcPosts", posts);
		nbt.putInt("npcVersion", npcVersion);
		nbt.putInt("ticksToDecree", ticksToDecree);
		nbt.putInt("decreeCount", decreeCount);
		nbt.putInt("ticksToEvent", ticksToEvent);
		nbt.putInt("eventCount", eventCount);
		nbt.putInt("rebellionTicks", rebellionTicks);
		nbt.putInt("weatherTicks", weatherTicks);
		nbt.putInt("propagandaTicks", propagandaTicks);
		nbt.putInt("rebellionCooldown", rebellionCooldown);
		nbt.putBoolean("bloodMoon", bloodMoon);
		nbt.putLong("bloodMoonDay", bloodMoonDay);
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
		NbtList repealed = nbt.getList("repealedLaws", NbtElement.STRING_TYPE);
		for (int i = 0; i < repealed.size(); i++) {
			s.repealedLaws.add(repealed.getString(i));
		}
		s.prisonGenerated = nbt.getBoolean("prisonGenerated");
		s.prisonOrigin = BlockPos.fromLong(nbt.getLong("prisonOrigin"));
		s.minigamesGenerated = nbt.getBoolean("minigamesGenerated");
		s.throneGenerated = nbt.getBoolean("throneGenerated");
		s.throneOrigin = BlockPos.fromLong(nbt.getLong("throneOrigin"));
		s.throneVersion = nbt.getInt("throneVersion");
		s.capitalGenerated = nbt.getBoolean("capitalGenerated");
		s.officesGenerated = nbt.getBoolean("officesGenerated");
		s.officesOrigin = BlockPos.fromLong(nbt.getLong("officesOrigin"));
		s.officeScheduleStarted = nbt.getBoolean("officeScheduleStarted");
		s.casinoGenerated = nbt.getBoolean("casinoGenerated");
		s.casinoVersion = nbt.getInt("casinoVersion");
		s.capitalSwept = nbt.getBoolean("capitalSwept");
		s.guardDown = nbt.getBoolean("guardDown");
		s.casinoOrigin = BlockPos.fromLong(nbt.getLong("casinoOrigin"));
		for (int i = 0; i < s.officeOpen.length; i++) {
			s.officeOpen[i] = nbt.getBoolean("officeOpen" + i);
			s.officeTicks[i] = nbt.getInt("officeTicks" + i);
		}
		if (nbt.containsUuid("dictator")) {
			s.dictator = nbt.getUuid("dictator");
		}
		if (nbt.containsUuid("employer")) {
			s.employer = nbt.getUuid("employer");
		}
		NbtCompound posts = nbt.getCompound("npcPosts");
		for (String k : posts.getKeys()) {
			if (posts.containsUuid(k)) {
				s.npcPosts.put(k, posts.getUuid(k));
			}
		}
		s.npcVersion = nbt.getInt("npcVersion");
		s.ticksToDecree = nbt.contains("ticksToDecree") ? nbt.getInt("ticksToDecree") : -1;
		s.decreeCount = nbt.getInt("decreeCount");
		s.ticksToEvent = nbt.contains("ticksToEvent") ? nbt.getInt("ticksToEvent") : -1;
		s.eventCount = nbt.getInt("eventCount");
		s.rebellionTicks = nbt.getInt("rebellionTicks");
		s.weatherTicks = nbt.contains("weatherTicks") ? nbt.getInt("weatherTicks") : -1;
		s.propagandaTicks = nbt.contains("propagandaTicks") ? nbt.getInt("propagandaTicks") : -1;
		s.rebellionCooldown = nbt.getInt("rebellionCooldown");
		s.bloodMoon = nbt.getBoolean("bloodMoon");
		s.bloodMoonDay = nbt.contains("bloodMoonDay") ? nbt.getLong("bloodMoonDay") : -1;
		return s;
	}
}
