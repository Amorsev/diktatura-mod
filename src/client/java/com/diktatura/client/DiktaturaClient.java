package com.diktatura.client;

import com.diktatura.client.ui.Alerts;
import com.diktatura.client.ui.Headlines;
import com.diktatura.client.ui.Overlays;
import com.diktatura.client.ui.Subtitles;
import com.diktatura.item.ModItems;
import com.diktatura.network.StatusPayload;
import com.diktatura.network.UiPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.TypedActionResult;
import org.lwjgl.glfw.GLFW;

public class DiktaturaClient implements ClientModInitializer {
	private static KeyBinding openLaws;

	@Override
	public void onInitializeClient() {
		registerReceivers();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientState.reset();
			Alerts.clear();
			Headlines.clear();
			Subtitles.clear();
			Overlays.clear();
		});

		// Порядок слоёв: панели → субтитры → баннеры → заголовок → плакаты → «один глаз».
		HudRenderCallback.EVENT.register((ctx, tick) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			int w = ctx.getScaledWindowWidth();
			int h = ctx.getScaledWindowHeight();
			if (!mc.options.hudHidden) {
				StatusHud.render(ctx);
				Subtitles.render(ctx, w, h);
				Alerts.render(ctx, w);
				Headlines.render(ctx, w, h);
			}
			Overlays.render(ctx, w, h);
			BlindOverlay.draw(ctx, w, h);
		});
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof DeathScreen) {
				ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) ->
					Overlays.deathStamp(ctx, s.width, s.height));
			}
			// Закрытый глаз закрывает половину экрана и в меню (инвентарь, сундуки).
			ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) ->
				BlindOverlay.draw(ctx, s.width, s.height));
		});

		openLaws = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.diktatura.laws",
			InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "category.diktatura"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Overlays.tick(client);
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

	private static void registerReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(StatusPayload.ID,
			(payload, context) -> context.client().execute(() -> ClientState.update(payload)));
		ClientPlayNetworking.registerGlobalReceiver(UiPayloads.LawList.ID,
			(payload, context) -> context.client().execute(() -> ClientState.updateLaws(payload)));
		ClientPlayNetworking.registerGlobalReceiver(UiPayloads.Alert.ID,
			(payload, context) -> context.client().execute(() -> Alerts.push(payload)));
		ClientPlayNetworking.registerGlobalReceiver(UiPayloads.Headline.ID,
			(payload, context) -> context.client().execute(() -> Headlines.show(payload)));
		ClientPlayNetworking.registerGlobalReceiver(UiPayloads.Speech.ID,
			(payload, context) -> context.client().execute(() -> Subtitles.show(payload)));
		ClientPlayNetworking.registerGlobalReceiver(UiPayloads.Wanted.ID,
			(payload, context) -> context.client().execute(() -> Overlays.wanted(payload)));
		ClientPlayNetworking.registerGlobalReceiver(UiPayloads.NpcScreen.ID,
			(payload, context) -> context.client().execute(() -> {
				MinecraftClient mc = context.client();
				// Окно открывается, только если игрок не занят другим меню (кроме окна того же НПС).
				if (mc.currentScreen == null || mc.currentScreen instanceof NpcScreen) {
					mc.setScreen(new NpcScreen(payload));
				}
			}));
	}
}
