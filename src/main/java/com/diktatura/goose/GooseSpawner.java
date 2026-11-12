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
	/** Подселение — раз в 3 минуты (было раз в минуту). */
	private static final int PERIOD = 20 * 180;
	/** Базовый шанс подселения на игрока за проверку (было 0.35). */
	private static final float CHANCE = 0.15f;
	/** Базовый предел гусей в радиусе 64 блоков (было 3). */
	private static final int CAP = 2;

	private GooseSpawner() {
	}

	public static void register() {
		// Вес природного спавна читается один раз при запуске игры (0 — природного спавна нет).
		int weight = MathHelper.clamp(DiktaturaConfig.get().gooseSpawnWeight, 0, 10);
		SpawnRestriction.register(ModEntities.WILD_GOOSE, SpawnLocationTypes.ON_GROUND,
			Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AnimalEntity::isValidNaturalSpawn);
		if (weight > 0) {
			BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS,
				BiomeKeys.MEADOW, BiomeKeys.RIVER, BiomeKeys.SWAMP, BiomeKeys.FLOWER_FOREST, BiomeKeys.SAVANNA,
				BiomeKeys.CHERRY_GROVE),
			SpawnGroup.CREATURE, ModEntities.WILD_GOOSE, weight, 1, 2);
		}
	}

	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % PERIOD != 0 || !DiktaturaConfig.get().wildGeese) {
			return;
		}
		ServerWorld world = server.getOverworld();
		if (!world.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_SPAWNING)) {
			return;
		}
		double freq = MathHelper.clamp(DiktaturaConfig.get().gooseFrequency, 0.0, 2.0);
		if (freq <= 0) {
			return;
		}
		float chance = (float) Math.min(1.0, CHANCE * freq);
		int cap = Math.max(1, (int) Math.round(CAP * freq));
		Random rnd = world.getRandom();
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.isSpectator() || rnd.nextFloat() > chance) {
				continue;
			}
			int near = world.getEntitiesByType(ModEntities.WILD_GOOSE, new Box(p.getBlockPos()).expand(64), e -> true).size();
			if (near >= cap) {
				continue;
			}
			BlockPos spot = findSpot(world, p.getBlockPos(), rnd);
			if (spot == null) {
				continue;
			}
			int n = 1 + rnd.nextInt(Math.min(2, cap - near));
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
