package com.diktatura.mimic;

import com.diktatura.config.DiktaturaConfig;
import com.diktatura.law.StateStructures;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.ui.Hud;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

/**
 * Внезапная ревизия: одиночный сундук с ещё не открытой добычей (данжи, деревни, крепости)
 * с шансом {@code mimicChance} оказывается сундуком-ревизором.
 */
public final class Mimics {
	private Mimics() {
	}

	public static void registerEvents() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity sp) || sp.isSpectator()
				|| sp.isCreative()) {
				return ActionResult.PASS;
			}
			double chance = DiktaturaConfig.get().mimicChance;
			if (chance <= 0) {
				return ActionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof ChestBlock) || state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE
				|| !(world.getBlockEntity(pos) instanceof ChestBlockEntity chest) || chest.getLootTable() == null
				|| StateStructures.contains(world, pos)) {
				return ActionResult.PASS;
			}
			if (world.random.nextDouble() >= chance) {
				return ActionResult.PASS;
			}
			chest.generateLoot(player);
			MimicEntity.fromChest((ServerWorld) world, pos, chest, state.get(ChestBlock.FACING));
			Hud.alert(sp, AlertKind.DANGER, Text.translatable("diktatura.mimic.audit"), Text.translatable("diktatura.mimic.audit.hint"), 5);
			return ActionResult.FAIL;
		});
	}
}
