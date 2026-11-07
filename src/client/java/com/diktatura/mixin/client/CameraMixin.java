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

/** Глюк и удары босса трясут камеру. */
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
		float power = 0f;
		if (ClientFx.active("glitch")) {
			power = 0.6f;
		}
		if (ClientFx.active("shake")) {
			power = Math.max(power, 1.2f);
		}
		if (ClientFx.active("shake_hard")) {
			power = Math.max(power, 3.0f);
		}
		if (power <= 0f) {
			return;
		}
		double t = ClientFx.time(tickDelta) * 1.7;
		float dy = (float) (Math.sin(t * 3.1) + Math.sin(t * 7.3) * 0.5) * power;
		float dp = (float) (Math.cos(t * 2.7) + Math.sin(t * 5.9) * 0.5) * power;
		setRotation(getYaw() + dy, getPitch() + dp);
	}
}
