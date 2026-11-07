package com.diktatura.mixin;

import com.diktatura.finale.GuardDragonLook;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Дракон госохраны вдвое больше: части тела (голова, шея, туловище, хвост, крылья) отодвигаются
 * от центра во столько же раз, сколько увеличена модель, — бить можно туда, куда видно.
 */
@Mixin(EnderDragonEntity.class)
public abstract class EnderDragonEntityMixin {
	@ModifyVariable(method = "movePart", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double diktatura$scaleDx(double dx) {
		return dx * GuardDragonLook.scale((EnderDragonEntity) (Object) this);
	}

	@ModifyVariable(method = "movePart", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private double diktatura$scaleDy(double dy) {
		return dy * GuardDragonLook.scale((EnderDragonEntity) (Object) this);
	}

	@ModifyVariable(method = "movePart", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private double diktatura$scaleDz(double dz) {
		return dz * GuardDragonLook.scale((EnderDragonEntity) (Object) this);
	}

	/** Размер части пересчитывается, когда дракон стал (или перестал быть) драконом госохраны. */
	@Inject(method = "movePart", at = @At("HEAD"))
	private void diktatura$resizePart(EnderDragonPart part, double dx, double dy, double dz, CallbackInfo ci) {
		EntityDimensions want = part.getDimensions(part.getPose());
		if (Math.abs(part.getWidth() - want.width()) > 1.0e-4f || Math.abs(part.getHeight() - want.height()) > 1.0e-4f) {
			part.calculateDimensions();
		}
	}
}
