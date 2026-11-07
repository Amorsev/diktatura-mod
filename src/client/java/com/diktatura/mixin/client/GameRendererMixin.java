package com.diktatura.mixin.client;

import com.diktatura.client.fx.ClientFx;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Искажения: мир вверх ногами («Австралия»), поле зрения турбо и «дыхание» глюка. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), require = 0)
	private void diktatura$flip(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		if (ClientFx.active("flip")) {
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f));
		}
	}

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void diktatura$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
		double fov = cir.getReturnValue();
		if (ClientFx.active("glitch")) {
			fov += MathHelper.sin(ClientFx.time(tickDelta) * 0.3f) * 18.0;
		}
		if (ClientFx.active("turbo")) {
			fov += 30.0;
		}
		cir.setReturnValue(fov);
	}
}
