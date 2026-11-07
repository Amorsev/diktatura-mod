package com.diktatura.client.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

/** Экранные эффекты на клиенте: какой эффект до какого тика действует. */
public final class ClientFx {
	private static final Map<String, Long> UNTIL = new HashMap<>();
	private static long ticks;

	private ClientFx() {
	}

	public static void set(String id, int duration) {
		if (duration <= 0) {
			UNTIL.remove(id);
		} else {
			UNTIL.put(id, ticks + duration);
		}
	}

	public static boolean active(String id) {
		Long t = UNTIL.get(id);
		return t != null && t > ticks;
	}

	public static float time(float tickDelta) {
		return ticks + tickDelta;
	}

	public static void tick(MinecraftClient client) {
		ticks++;
		if (client.world == null && !UNTIL.isEmpty()) {
			UNTIL.clear();
		}
	}

	public static void clear() {
		UNTIL.clear();
	}

	/** «Вертикальное видео»: чёрные поля по бокам, посередине кадр 9:16. И цветной шум при глюке. */
	public static void render(DrawContext ctx, int w, int h) {
		if (active("tiktok")) {
			int frame = Math.min(w, h * 9 / 16);
			int side = (w - frame) / 2;
			ctx.fill(0, 0, side, h, 0xFF000000);
			ctx.fill(w - side, 0, w, h, 0xFF000000);
		}
		if (active("glitch")) {
			int seed = (int) (ticks / 2);
			java.util.Random r = new java.util.Random(seed * 31L);
			for (int i = 0; i < 6; i++) {
				int y = r.nextInt(Math.max(1, h));
				int bh = 2 + r.nextInt(10);
				int color = (0x40 + r.nextInt(0x50)) << 24 | (r.nextBoolean() ? 0xFF00FF : 0x00FFFF);
				ctx.fill(0, y, w, Math.min(h, y + bh), color);
			}
			float pulse = MathHelper.sin(ticks * 0.5f) * 0.5f + 0.5f;
			ctx.fill(0, 0, w, h, ((int) (pulse * 0x18) << 24) | 0x8000FF);
		}
	}
}
