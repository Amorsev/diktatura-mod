package com.diktatura.mixin.client;

import com.diktatura.client.fx.ClientFx;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Искажения: перепутанное управление (вперёд — назад, влево — вправо). */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("TAIL"), require = 0)
	private void diktatura$invert(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		if (!ClientFx.active("inverted")) {
			return;
		}
		Input in = (Input) (Object) this;
		in.movementForward = -in.movementForward;
		in.movementSideways = -in.movementSideways;
		boolean f = in.pressingForward;
		in.pressingForward = in.pressingBack;
		in.pressingBack = f;
		boolean l = in.pressingLeft;
		in.pressingLeft = in.pressingRight;
		in.pressingRight = l;
	}
}
