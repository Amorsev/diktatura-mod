package com.diktatura.mixin;

import com.diktatura.law.LawContext;
import com.diktatura.law.LawEngine;
import com.diktatura.law.LawEvent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Игрок забрал результат крафта (в том числе shift-кликом) — проверить законы о крафте. */
@Mixin(CraftingResultSlot.class)
public abstract class CraftingResultSlotMixin {
	@Inject(method = "onTakeItem", at = @At("HEAD"))
	private void diktatura$onCraft(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
		if (player instanceof ServerPlayerEntity sp && !stack.isEmpty()) {
			LawContext c = new LawContext(sp, LawEvent.CRAFT);
			c.stack = stack.copy();
			LawEngine.fire(c);
		}
	}
}
