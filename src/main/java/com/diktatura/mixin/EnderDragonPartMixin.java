package com.diktatura.mixin;

import com.diktatura.finale.GuardDragonLook;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Хитбоксы частей дракона госохраны растут вместе с моделью. */
@Mixin(EnderDragonPart.class)
public abstract class EnderDragonPartMixin {
	@Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
	private void diktatura$guardSize(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		// owner ещё null, пока конструктор части считает размер в первый раз
		float s = GuardDragonLook.scale(((EnderDragonPart) (Object) this).owner);
		if (s != 1f) {
			cir.setReturnValue(cir.getReturnValue().scaled(s));
		}
	}
}
