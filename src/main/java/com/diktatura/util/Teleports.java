package com.diktatura.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Set;

/** Телепортация игрока в любое измерение (одна точка для всех систем мода). */
public final class Teleports {
	private Teleports() {
	}

	public static void to(ServerPlayerEntity player, ServerWorld world, double x, double y, double z, float yaw, float pitch) {
		player.teleport(world, x, y, z, Set.of(), yaw, pitch);
		player.fallDistance = 0;
	}
}
