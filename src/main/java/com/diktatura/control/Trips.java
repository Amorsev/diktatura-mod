package com.diktatura.control;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.network.FxPayloads;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.ui.Hud;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * «Препарат с искажениями» (референс 7): странные грибы и полминуты экранного безумия —
 * мир вверх ногами, перепутанное управление, «вертикальное видео», турбо, невесомость, глюки.
 * Всё выключено по умолчанию (опция {@code distortionDrug}): без неё грибы не растут и не действуют.
 */
public final class Trips {
	public static final String[] IDS = {"flip", "inverted", "tiktok", "turbo", "float", "glitch"};

	private Trips() {
	}

	public static void random(ServerPlayerEntity p, int ticks) {
		apply(p, IDS[p.getRandom().nextInt(IDS.length)], ticks);
	}

	public static void apply(ServerPlayerEntity p, String id, int ticks) {
		switch (id) {
			case "float" -> {
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 60, 1, false, true));
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 200, 0, false, true));
			}
			case "turbo" -> {
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 3, false, true));
				fx(p, id, ticks);
			}
			default -> fx(p, id, "glitch".equals(id) ? ticks / 2 : ticks);
		}
		Hud.alert(p, AlertKind.EVENT, Text.translatable("diktatura.trip." + id), Text.translatable("diktatura.trip.hint"), 5);
	}

	private static void fx(ServerPlayerEntity p, String id, int ticks) {
		if (ServerPlayNetworking.canSend(p, FxPayloads.Fx.ID)) {
			ServerPlayNetworking.send(p, new FxPayloads.Fx(id, ticks));
		}
	}

	/** Раз в две минуты рядом с игроками в тени деревьев вырастают странные грибы (если включено). */
	public static void tick(MinecraftServer server) {
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
