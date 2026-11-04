package com.diktatura.client;

import com.diktatura.item.ModItems;
import com.diktatura.network.StatusPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.TypedActionResult;
import org.lwjgl.glfw.GLFW;

public class DiktaturaClient implements ClientModInitializer {
	private static KeyBinding openLaws;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(StatusPayload.ID,
			(payload, context) -> context.client().execute(() -> ClientState.update(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());

		HudRenderCallback.EVENT.register(LoyaltyHud::render);

		openLaws = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.diktatura.laws",
			InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "category.diktatura"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openLaws.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new LawBookScreen());
				}
			}
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (world.isClient && stack.isOf(ModItems.LAW_BOOK)) {
				MinecraftClient.getInstance().setScreen(new LawBookScreen());
				return TypedActionResult.success(stack);
			}
			return TypedActionResult.pass(stack);
		});
	}
}
