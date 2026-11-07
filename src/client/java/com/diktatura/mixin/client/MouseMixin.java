package com.diktatura.mixin.client;

import com.diktatura.client.fx.ClientFx;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Перепутанное управление переворачивает и мышь. */
@Mixin(Mouse.class)
public class MouseMixin {
	@Redirect(method = "updateMouse", require = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
	private void diktatura$invertLook(ClientPlayerEntity player, double dx, double dy) {
		if (ClientFx.active("inverted")) {
			player.changeLookDirection(-dx, -dy);
		} else {
			player.changeLookDirection(dx, dy);
		}
	}
}
