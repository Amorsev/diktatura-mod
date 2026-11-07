package com.diktatura.goose;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.npc.ModEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;

/**
 * Откуда берутся дикие гуси: природный спавн на лугах и у рек (новые чанки) и
 * подселение стайки рядом с игроком в уже исследованных местах — гуси всегда где-то рядом.
 */
public final class GooseSpawner {
	private static final int PERIOD = 20 * 60;
	private static final int CAP = 3;

	private GooseSpawner() {
	}

	public static void register() {
		SpawnRestriction.register(ModEntities.WILD_GOOSE, SpawnLocationTypes.ON_GROUND,
			Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AnimalEntity::isValidNaturalSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS,
				BiomeKeys.MEADOW, BiomeKeys.RIVER, BiomeKeys.SWAMP, BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST,
				BiomeKeys.FLOWER_FOREST, BiomeKeys.SAVANNA, BiomeKeys.TAIGA, BiomeKeys.CHERRY_GROVE),
			SpawnGroup.CREATURE, ModEntities.WILD_GOOSE, 6, 2, 4);
	}

	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % PERIOD != 0 || !DiktaturaConfig.get().wildGeese) {
			return;
		}
		ServerWorld world = server.getOverworld();
		if (!world.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_SPAWNING)) {
			return;
		}
		Random rnd = world.getRandom();
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.isSpectator() || rnd.nextFloat() > 0.35f) {
				continue;
			}
			int near = world.getEntitiesByType(ModEntities.WILD_GOOSE, new Box(p.getBlockPos()).expand(64), e -> true).size();
			if (near >= CAP) {
				continue;
			}
			BlockPos spot = findSpot(world, p.getBlockPos(), rnd);
			if (spot == null) {
				continue;
			}
			int n = 1 + rnd.nextInt(Math.min(3, CAP - near));
			for (int i = 0; i < n; i++) {
				WildGooseEntity g = ModEntities.WILD_GOOSE.create(world);
				if (g == null) {
					continue;
				}
				g.refreshPositionAndAngles(spot.getX() + 0.5 + i * 0.7, spot.getY(), spot.getZ() + 0.5, rnd.nextFloat() * 360f, 0f);
				g.stealCooldown = 200 + rnd.nextInt(400);
				world.spawnEntity(g);
			}
		}
	}

	/** Травяная поляна под открытым небом в 20–40 блоках от игрока. */
	private static BlockPos findSpot(ServerWorld world, BlockPos center, Random rnd) {
		for (int attempt = 0; attempt < 8; attempt++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			int r = 20 + rnd.nextInt(21);
			int x = center.getX() + MathHelper.floor(Math.cos(a) * r);
			int z = center.getZ() + MathHelper.floor(Math.sin(a) * r);
			if (!world.isChunkLoaded(x >> 4, z >> 4)) {
				continue;
			}
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			BlockState below = world.getBlockState(pos.down());
			if (below.isOf(Blocks.GRASS_BLOCK) && world.getBlockState(pos).isReplaceable()
				&& world.getBlockState(pos.up()).isAir() && world.getRegistryKey() == World.OVERWORLD) {
				return pos;
			}
		}
		return null;
	}
}
