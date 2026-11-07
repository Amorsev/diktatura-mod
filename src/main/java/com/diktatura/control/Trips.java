package com.diktatura.control;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.goose.ModSounds;
import com.diktatura.network.FxPayloads;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.ui.Hud;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.GenerationStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * «Препарат с искажениями» (референс 7, мод Gr1tW4lk, MIT): странный гриб — сытный, но после него
 * полминуты «трипа», один из восьми: мир вверх ногами, перепутанное управление, «вертикальное видео»,
 * графика-картошка, невесомость, турбо, лаги с откатом назад и глюки реальности.
 * Грибы растут в мире сами (во всех биомах верхнего мира, как в референсе) и в тени рядом с игроками.
 * Опция {@code distortionDrug} (по умолчанию включена) выключает действие гриба.
 */
public final class Trips {
	public static final String[] IDS = {"flip", "inverted", "tiktok", "potato", "float", "turbo", "lag", "glitch"};

	/** Позиции игроков за последние 10 секунд — для «лага», который откатывает назад. */
	private static final int HISTORY = 200;
	private static final Map<UUID, Vec3d[]> POSITIONS = new HashMap<>();
	private static final Map<UUID, Integer> HEAD = new HashMap<>();
	private record Pending(long at, UUID player) {
	}
	private static final List<Pending> ROLLBACKS = new ArrayList<>();

	private Trips() {
	}

	/** Природная генерация: пятна грибов в верхнем мире (data/diktatura/worldgen/…/strange_mushroom.json). */
	public static void registerWorldgen() {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.VEGETAL_DECORATION,
			RegistryKey.of(RegistryKeys.PLACED_FEATURE, Diktatura.id("strange_mushroom")));
	}

	public static void random(ServerPlayerEntity p, int ticks) {
		apply(p, IDS[p.getRandom().nextInt(IDS.length)], ticks);
	}

	public static void apply(ServerPlayerEntity p, String id, int ticks) {
		SoundEvent sound = ModSounds.GLITCH_SHORT;
		switch (id) {
			case "float" -> {
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 60, 1, false, true));
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 200, 0, false, true));
				sound = null;
			}
			case "turbo" -> {
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 3, false, true));
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, Math.min(ticks, 200), 0, false, false));
				fx(p, id, ticks);
				sound = null;
			}
			case "lag" -> {
				lagSpike(p, ticks / 2);
				sound = ModSounds.LAG_STUTTER;
			}
			case "tiktok" -> {
				fx(p, id, ticks);
				sound = ModSounds.TIKTOK_SWOOSH;
			}
			case "glitch" -> {
				fx(p, id, ticks / 2);
				sound = ModSounds.GLITCH;
			}
			default -> fx(p, id, ticks);
		}
		if (sound != null) {
			p.playSoundToPlayer(sound, SoundCategory.MASTER, 0.8f, 1.0f);
		}
		Hud.alert(p, AlertKind.EVENT, Text.translatable("diktatura.trip." + id), Text.translatable("diktatura.trip.hint"), 5);
	}

	/** «Пинг 999»: значок плохой связи, а трижды за лаг игрока откатывает на секунду назад с застывшим кадром. */
	public static void lagSpike(ServerPlayerEntity p, int ticks) {
		fx(p, "lag", ticks);
		for (int delay : new int[] {20, 55, 95}) {
			if (delay < ticks) {
				ROLLBACKS.add(new Pending(Diktatura.ticks() + delay, p.getUuid()));
			}
		}
	}

	private static void fx(ServerPlayerEntity p, String id, int ticks) {
		if (ServerPlayNetworking.canSend(p, FxPayloads.Fx.ID)) {
			ServerPlayNetworking.send(p, new FxPayloads.Fx(id, ticks));
		}
	}

	public static void tick(MinecraftServer server) {
		recordPositions(server);
		runRollbacks(server);
		growNearPlayers(server);
	}

	private static void recordPositions(MinecraftServer server) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			Vec3d[] ring = POSITIONS.computeIfAbsent(p.getUuid(), u -> new Vec3d[HISTORY]);
			int head = HEAD.getOrDefault(p.getUuid(), 0);
			if (!p.isAlive()) {
				java.util.Arrays.fill(ring, null);
				continue;
			}
			ring[head] = p.getPos();
			HEAD.put(p.getUuid(), (head + 1) % HISTORY);
		}
	}

	/** Где игрок был ticksAgo тиков назад (или null, если не знаем). */
	private static Vec3d positionAgo(ServerPlayerEntity p, int ticksAgo) {
		Vec3d[] ring = POSITIONS.get(p.getUuid());
		if (ring == null) {
			return null;
		}
		int head = HEAD.getOrDefault(p.getUuid(), 0);
		return ring[Math.floorMod(head - 1 - ticksAgo, HISTORY)];
	}

	private static void runRollbacks(MinecraftServer server) {
		long now = Diktatura.ticks();
		for (Iterator<Pending> it = ROLLBACKS.iterator(); it.hasNext(); ) {
			Pending r = it.next();
			if (r.at() > now) {
				continue;
			}
			it.remove();
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(r.player());
			if (p == null || !p.isAlive()) {
				continue;
			}
			Vec3d back = positionAgo(p, 20);
			if (back != null && back.squaredDistanceTo(p.getPos()) < 64 * 64) {
				p.networkHandler.requestTeleport(back.x, back.y, back.z, p.getYaw(), p.getPitch());
				fx(p, "freeze", 10);
			}
		}
	}

	/** Раз в две минуты рядом с игроками в тени деревьев вырастают странные грибы. */
	private static void growNearPlayers(MinecraftServer server) {
		if (Diktatura.ticks() % (20 * 120) != 7 || !DiktaturaConfig.get().distortionDrug) {
			return;
		}
		ServerWorld world = server.getOverworld();
		Random rnd = world.getRandom();
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (rnd.nextFloat() > 0.4f) {
				continue;
			}
			for (int attempt = 0; attempt < 10; attempt++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				int r = 12 + rnd.nextInt(20);
				int x = p.getBlockX() + MathHelper.floor(Math.cos(a) * r);
				int z = p.getBlockZ() + MathHelper.floor(Math.sin(a) * r);
				if (!world.isChunkLoaded(x >> 4, z >> 4)) {
					continue;
				}
				int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos pos = new BlockPos(x, y, z);
				BlockState below = world.getBlockState(pos.down());
				boolean ground = below.isOf(Blocks.GRASS_BLOCK) || below.isOf(Blocks.PODZOL) || below.isOf(Blocks.MYCELIUM)
					|| below.isIn(BlockTags.DIRT);
				// В тени: над головой листва (лес) — грибы любят полумрак.
				boolean shade = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) > y + 2;
				if (ground && shade && world.getBlockState(pos).isAir()) {
					world.setBlockState(pos, ModControl.STRANGE_MUSHROOM.getDefaultState());
					break;
				}
			}
		}
	}
}
