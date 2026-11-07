package com.diktatura.mixin.client;

import com.diktatura.client.fx.ClientFx;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Глюк трясёт камеру. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	public abstract float getYaw();

	@Shadow
	public abstract float getPitch();

	@Inject(method = "update", at = @At("TAIL"), require = 0)
	private void diktatura$shake(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
	                             float tickDelta, CallbackInfo ci) {
		if (!ClientFx.active("glitch")) {
			return;
		}
		double t = ClientFx.time(tickDelta) * 1.7;
		float power = 0.6f;
		float dy = (float) (Math.sin(t * 3.1) + Math.sin(t * 7.3) * 0.5) * power;
		float dp = (float) (Math.cos(t * 2.7) + Math.sin(t * 5.9) * 0.5) * power;
		setRotation(getYaw() + dy, getPitch() + dp);
	}
}
