package com.diktatura.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/** HUD в левом верхнем углу: лояльность, страх, срок, таймер указа. */
public final class LoyaltyHud {
	private static final int BAR_W = 80;

	private LoyaltyHud() {
	}

	public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!ClientState.received || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) {
			return;
		}
		TextRenderer tr = mc.textRenderer;
		int x = 4;
		int y = 4;

		ctx.drawTextWithShadow(tr, Text.translatable("diktatura.hud.loyalty", ClientState.loyalty), x, y, 0xFFFFFF);
		y += 10;
		bar(ctx, x, y, ClientState.loyalty, loyaltyColor(ClientState.loyalty));
		y += 7;
		ctx.drawTextWithShadow(tr, Text.translatable("diktatura.hud.fear", ClientState.fear), x, y, 0xB0B0B0);
		y += 10;
		bar(ctx, x, y, ClientState.fear, 0xFF8E44AD);
		y += 8;

		switch (ClientState.stage) {
			case 1 -> {
				ctx.drawTextWithShadow(tr, Text.translatable("diktatura.hud.to_prison"), x, y, 0xFF5555);
				y += 10;
			}
			case 2 -> {
				ctx.drawTextWithShadow(tr, Text.translatable("diktatura.hud.sentence",
					ClientState.time(ClientState.sentenceSeconds)), x, y, 0xFF5555);
				y += 10;
			}
			case 3 -> {
				ctx.drawTextWithShadow(tr, Text.translatable("diktatura.hud.returning"), x, y, 0x55FF55);
				y += 10;
			}
			default -> {
			}
		}
		if (ClientState.timeoutSeconds > 0) {
			ctx.drawTextWithShadow(tr, Text.translatable("diktatura.hud.timeout",
				ClientState.time(ClientState.timeoutSeconds)), x, y, 0xFFAA00);
			y += 10;
		}
		if (ClientState.rebellion) {
			ctx.drawTextWithShadow(tr, Text.translatable("diktatura.hud.rebellion"), x, y, 0xFF2222);
			y += 10;
		}
		ctx.drawTextWithShadow(tr, Text.translatable("diktatura.hud.decree",
			ClientState.time(ClientState.decreeSeconds), ClientState.laws.size()), x, y, 0x909090);
	}

	private static void bar(DrawContext ctx, int x, int y, int value, int color) {
		ctx.fill(x, y, x + BAR_W, y + 4, 0x90000000);
		int w = Math.round(BAR_W * Math.max(0, Math.min(100, value)) / 100f);
		ctx.fill(x, y, x + w, y + 4, color);
	}

	private static int loyaltyColor(int v) {
		if (v >= 60) {
			return 0xFF3FBF3F;
		}
		if (v >= 25) {
			return 0xFFE0C030;
		}
		return 0xFFD03030;
	}
}
