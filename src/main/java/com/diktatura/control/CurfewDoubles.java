package com.diktatura.control;

import com.diktatura.Diktatura;
import com.diktatura.goose.ModSounds;
import com.diktatura.law.Law;
import com.diktatura.law.LawEngine;
import com.diktatura.law.LawRegistry;
import com.diktatura.license.Licenses;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.ModEntities;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.ui.Hud;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Комендантский час с двойником: кто гуляет под открытым небом ночью при действующем законе
 * «Комендантский час», к тому раз за ночь приходит двойник-агент (не сразу — через полминуты на улице).
 */
public final class CurfewDoubles {
	public static final String CURFEW_LAW = "diktatura:curfew";
	private static final int OUTSIDE_TICKS = 20 * 30;
	/** Сколько тиков игрок уже нарушает этой ночью. */
	private static final Map<UUID, Integer> OUTSIDE = new HashMap<>();
	/** В какую ночь (номер дня) двойник уже приходил. */
	private static final Map<UUID, Long> DONE = new HashMap<>();

	private CurfewDoubles() {
	}

	public static boolean curfewActive(MinecraftServer server) {
		return DiktaturaState.get(server).activeLaws.contains(CURFEW_LAW);
	}

	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % 20 != 0) {
			return;
		}
		if (!com.diktatura.config.DiktaturaConfig.get().curfewDouble || !curfewActive(server)) {
			OUTSIDE.clear();
			return;
		}
		ServerWorld world = server.getOverworld();
		long tod = world.getTimeOfDay() % 24000L;
		long day = world.getTimeOfDay() / 24000L;
		boolean night = tod >= 13000L && tod <= 22000L;
		Law law = LawRegistry.get(CURFEW_LAW);
		DiktaturaState state = DiktaturaState.get(server);
		for (ServerPlayerEntity p : world.getPlayers()) {
			PlayerRecord rec = state.player(p);
			boolean violating = night && law != null && LawEngine.applies(p, rec) && !p.isCreative()
				&& world.isSkyVisible(BlockPos.ofFloored(p.getEyePos())) && !Licenses.covers(p, rec, law);
			if (!violating) {
				OUTSIDE.remove(p.getUuid());
				continue;
			}
			int t = OUTSIDE.merge(p.getUuid(), 20, Integer::sum);
			if (t >= OUTSIDE_TICKS && DONE.getOrDefault(p.getUuid(), -1L) != day) {
				DONE.put(p.getUuid(), day);
				spawnFor(world, p);
			}
		}
	}

	public static CurfewDoubleEntity spawnFor(ServerWorld world, ServerPlayerEntity p) {
		BlockPos pos = spot(world, p.getBlockPos());
		CurfewDoubleEntity c = ModEntities.CURFEW_DOUBLE.create(world);
		if (c == null) {
			return null;
		}
		float yaw = (float) (MathHelper.atan2(p.getZ() - pos.getZ(), p.getX() - pos.getX()) * (180 / Math.PI)) - 90f;
		c.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
		c.copyFrom(p);
		c.setTarget(p);
		world.spawnEntity(c);
		world.playSound(null, pos, ModSounds.GLITCH, SoundCategory.HOSTILE, 1.2f, 1.0f);
		Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.double.arrived"),
			Text.translatable("diktatura.double.arrived.hint"), 7);
		return c;
	}

	private static BlockPos spot(ServerWorld world, BlockPos center) {
		var rnd = world.getRandom();
		for (int i = 0; i < 12; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			int r = 9 + rnd.nextInt(6);
			int x = center.getX() + MathHelper.floor(Math.cos(a) * r);
			int z = center.getZ() + MathHelper.floor(Math.sin(a) * r);
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (Math.abs(y - center.getY()) <= 6) {
				return new BlockPos(x, y, z);
			}
		}
		return center;
	}

	public static void forget(UUID id) {
		OUTSIDE.remove(id);
	}
}
