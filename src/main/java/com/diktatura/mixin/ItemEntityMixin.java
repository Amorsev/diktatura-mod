package com.diktatura.mixin;

import com.diktatura.law.LawEngine;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Подбор предмета: отметить игрока, чтобы законы о богатстве проверились сразу, а не по таймеру. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	@Inject(method = "onPlayerCollision", at = @At("HEAD"))
	private void diktatura$onPickup(PlayerEntity player, CallbackInfo ci) {
		if (player instanceof ServerPlayerEntity sp) {
			LawEngine.markPickup(sp);
		}
	}
}
