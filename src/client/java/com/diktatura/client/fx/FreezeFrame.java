package com.diktatura.client.fx;

import com.diktatura.Diktatura;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;

/**
 * Застывший кадр «лага» (по мотивам референса Gr1tW4lk, MIT): на полсекунды экран замирает
 * на последнем кадре, а игрока в это время откатывает назад.
 */
public final class FreezeFrame {
	private static final Identifier ID = Diktatura.id("freeze_frame");
	private static NativeImageBackedTexture texture;
	private static int texW;
	private static int texH;
	private static boolean captured;

	private FreezeFrame() {
	}

	public static void draw(DrawContext ctx, int w, int h) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!captured) {
			captured = true;
			capture(mc);
			return;
		}
		if (texture != null) {
			ctx.drawTexture(ID, 0, 0, w, h, 0, 0, texW, texH, texW, texH);
		}
	}

	private static void capture(MinecraftClient mc) {
		release();
		try {
			NativeImage img = ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());
			texW = img.getWidth();
			texH = img.getHeight();
			texture = new NativeImageBackedTexture(img);
			mc.getTextureManager().registerTexture(ID, texture);
		} catch (RuntimeException e) {
			texture = null;
		}
	}

	/** Эффект закончился — отпускаем кадр. */
	public static void reset() {
		if (captured) {
			captured = false;
			release();
		}
	}

	private static void release() {
		if (texture != null) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(ID);
			texture = null;
		}
	}
}
