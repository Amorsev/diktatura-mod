package com.diktatura.mixin.client;

import com.diktatura.client.fx.ClientFx;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Небо Кровавой луны. */
@Mixin(ClientWorld.class)
public class ClientWorldMixin {
	@Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 0)
	private void diktatura$bloodSky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
		if (!ClientFx.active("bloodmoon")) {
			return;
		}
		Vec3d c = cir.getReturnValue();
		cir.setReturnValue(new Vec3d(Math.min(1, c.x * 0.4 + 0.3), c.y * 0.15, c.z * 0.15));
	}
}
