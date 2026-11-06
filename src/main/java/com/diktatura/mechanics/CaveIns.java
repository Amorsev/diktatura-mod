package com.diktatura.mechanics;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.license.Licenses;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.state.DiktaturaState;
import com.diktatura.ui.Hud;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Обвалы в шахтах (Горное управление): при добыче камня под землёй с небольшим шансом
 * потолок осыпается. За полторы секунды — пыль и треск, потом над головой падает булыжник
 * и гравий. С шахтёрской лицензией («техника безопасности оплачена») шанс вчетверо меньше.
 */
public final class CaveIns {
	private record Pending(UUID player, ServerWorld world, BlockPos center, long at) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private CaveIns() {
	}

	public static void registerEvents() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
			if (world instanceof ServerWorld w && player instanceof ServerPlayerEntity sp) {
				onBreak(w, sp, pos, state);
			}
		});
	}

	private static void onBreak(ServerWorld w, ServerPlayerEntity p, BlockPos pos, BlockState state) {
		double chance = DiktaturaConfig.get().caveInChance;
		if (chance <= 0 || w.getRegistryKey() != World.OVERWORLD || p.isCreative()
			|| !(state.isIn(BlockTags.BASE_STONE_OVERWORLD)) || pos.getY() > 50 || w.isSkyVisible(pos.up())) {
			return;
		}
		if (Licenses.has(w.getServer(), DiktaturaState.get(w.getServer()).player(p), "diktatura:mining")) {
			chance /= 4;
		}
		if (w.getRandom().nextDouble() * 100 >= chance) {
			return;
		}
		start(p);
	}

	/** Обвал над игроком (по шансу или командой). */
	public static void start(ServerPlayerEntity p) {
		ServerWorld w = p.getServerWorld();
		PENDING.add(new Pending(p.getUuid(), w, p.getBlockPos(), Diktatura.ticks() + 30));
		w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 1.4f, 0.5f);
		Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.cavein.title"), Text.translatable("diktatura.cavein.hint"), 4);
	}

	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		long now = Diktatura.ticks();
		Iterator<Pending> it = PENDING.iterator();
		while (it.hasNext()) {
			Pending c = it.next();
			if (now < c.at()) {
				if (now % 4 == 0) {
					c.world().spawnParticles(ParticleTypes.ASH, c.center().getX() + 0.5, c.center().getY() + 3, c.center().getZ() + 0.5,
						25, 1.2, 0.3, 1.2, 0.01);
					c.world().playSound(null, c.center(), SoundEvents.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.8f, 0.6f);
				}
				continue;
			}
			it.remove();
			collapse(c.world(), c.center());
		}
	}

	private static void collapse(ServerWorld w, BlockPos center) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 2; dy <= 4; dy++) {
					BlockPos p = center.add(dx, dy, dz);
					BlockState s = w.getBlockState(p);
					if (!s.isIn(BlockTags.BASE_STONE_OVERWORLD) || !w.getBlockState(p.down()).isAir() && dy == 2) {
						continue;
					}
					// Порода над головой превращается в падающие булыжник и гравий.
					w.setBlockState(p, (w.getRandom().nextBoolean() ? Blocks.COBBLESTONE : Blocks.GRAVEL).getDefaultState());
					FallingBlockEntity f = FallingBlockEntity.spawnFromBlock(w, p, w.getBlockState(p));
					f.setHurtEntities(1.0f, 8);
				}
			}
		}
		w.playSound(null, center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.6f, 0.6f);
	}

	public static void clear() {
		PENDING.clear();
	}
}
