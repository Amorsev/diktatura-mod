package com.diktatura.mechanics;

import com.diktatura.config.DiktaturaConfig;
import com.diktatura.job.Jobs;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.ui.Hud;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * «Лесная повинность»: срубленный ствол валит всё дерево — все связанные брёвна падают
 * с дропом. Только у настоящих деревьев (рядом есть листва), чтобы не разбирать дома.
 * На корточках рубится одно бревно. Идея — из референсного мода (Timber), реализация своя.
 */
public final class Timber {
	private static final int MAX_LOGS = 128;

	private Timber() {
	}

	public static void registerEvents() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
			if (world instanceof ServerWorld w && player instanceof ServerPlayerEntity sp && state.isIn(BlockTags.LOGS)
				&& DiktaturaConfig.get().timber && !sp.isSneaking()) {
				fell(w, sp, pos);
			}
		});
	}

	private static void fell(ServerWorld w, ServerPlayerEntity p, BlockPos start) {
		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);
		boolean leaves = false;
		while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
			BlockPos cur = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos n = cur.add(dx, dy, dz);
						if (!seen.add(n)) {
							continue;
						}
						BlockState s = w.getBlockState(n);
						if (s.isIn(BlockTags.LOGS)) {
							logs.add(n);
							queue.add(n);
						} else if (s.isIn(BlockTags.LEAVES)) {
							leaves = true;
						}
					}
				}
			}
		}
		if (logs.isEmpty() || !leaves) {
			return;
		}
		BlockState first = w.getBlockState(logs.get(0));
		int felled = 0;
		for (BlockPos pos : logs) {
			if (w.breakBlock(pos, true, p)) {
				felled++;
			}
		}
		if (felled > 0) {
			w.playSound(null, start, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.BLOCKS, 0.8f, 0.7f);
			if (felled >= 4) {
				Hud.alert(p, AlertKind.INFO, Text.translatable("diktatura.timber.title"), Text.translatable("diktatura.timber.hint", felled + 1), 3);
			}
			Jobs.countExtra(p, first, felled);
		}
	}
}
