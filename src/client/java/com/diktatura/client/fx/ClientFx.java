package com.diktatura.client.fx;

import com.diktatura.client.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
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
		if (!active("freeze")) {
			FreezeFrame.reset();
		}
		PostFx.tick(client);
	}

	public static void clear() {
		UNTIL.clear();
	}

	/** «Вертикальное видео»: чёрные поля по бокам, посередине кадр 9:16. И цветной шум при глюке. */
	public static void render(DrawContext ctx, int w, int h) {
		if (active("freeze")) {
			FreezeFrame.draw(ctx, w, h);
		}
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		if (active("potato")) {
			renderPotato(ctx, tr);
		}
		if (active("inverted")) {
			renderInverted(ctx, tr, w, h);
		}
		if (active("turbo")) {
			renderTurbo(ctx, w, h);
		}
		if (active("lag")) {
			renderLag(ctx, tr, w, h);
		}
		if (active("tiktok")) {
			int frame = Math.min(w, h * 9 / 16);
			int side = (w - frame) / 2;
			ctx.fill(0, 0, side, h, 0xFF000000);
			ctx.fill(w - side, 0, w, h, 0xFF000000);
		}
		if (active("bloodmoon")) {
			// Кровавая луна: лёгкая красная дымка по краям.
			int edge = Math.max(8, Math.min(w, h) / 8);
			ctx.fillGradient(0, 0, w, edge, 0x30600000, 0x00600000);
			ctx.fillGradient(0, h - edge, w, h, 0x00600000, 0x30600000);
		}
		if (active("end_rage")) {
			// Последний рывок дракона: края экрана наливаются красным.
			float pulse = MathHelper.sin(ticks * 0.08f) * 0.5f + 0.5f;
			int a = (int) (0x30 + pulse * 0x30);
			int edge = Math.max(8, Math.min(w, h) / 6);
			ctx.fillGradient(0, 0, w, edge, a << 24 | 0x900000, 0x00900000);
			ctx.fillGradient(0, h - edge, w, h, 0x00900000, a << 24 | 0x900000);
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

	/** «Графика: КАРТОШКА» и честные 5–9 кадров в секунду. */
	private static void renderPotato(DrawContext ctx, TextRenderer tr) {
		int x = 6;
		int y = 6;
		Ui.panel(ctx, x, y, 150, 34, 0xFFD27D2D);
		ctx.drawItem(new ItemStack(Items.BAKED_POTATO), x + 8, y + 9);
		Ui.text(ctx, tr, Text.translatable("diktatura.trip.potato.title"), x + 30, y + 7, 0xFFF0B060, 1f);
		int fps = 5 + (int) (Math.abs(MathHelper.sin(ticks * 0.13f)) * 4);
		Ui.text(ctx, tr, Text.translatable("diktatura.trip.potato.fps", fps), x + 30, y + 19, Ui.MUTED, 1f);
	}

	/** Подсказка, что кнопки перепутаны. */
	private static void renderInverted(DrawContext ctx, TextRenderer tr, int w, int h) {
		Text s = Text.translatable("diktatura.trip.inverted.keys");
		int bw = tr.getWidth(s) + 16;
		int x = (w - bw) / 2;
		int y = h / 2 + 14;
		Ui.panel(ctx, x, y, bw, 15, 0xFFFF5555);
		Ui.centered(ctx, tr, s, w / 2, y + 4, 0xFFFF7070, 0.75f + 0.25f * MathHelper.sin(ticks * 0.4f));
	}

	/** Полосы скорости по краям экрана. */
	private static void renderTurbo(DrawContext ctx, int w, int h) {
		java.util.Random r = new java.util.Random(ticks / 2);
		for (int i = 0; i < 24; i++) {
			int y = r.nextInt(Math.max(1, h));
			int len = 20 + r.nextInt(60);
			boolean left = r.nextBoolean();
			int x = left ? r.nextInt(Math.max(1, w / 4)) : w - r.nextInt(Math.max(1, w / 4)) - len;
			ctx.fill(x, y, x + len, y + 1, 0x80FFFFFF);
		}
	}

	/** Значок плохой связи, «ПИНГ 999» и мигающее «Соединение потеряно...». */
	private static void renderLag(DrawContext ctx, TextRenderer tr, int w, int h) {
		int ping = 800 + (int) (Math.abs(MathHelper.sin(ticks * 0.07f)) * 1400);
		Text lost = Text.translatable("diktatura.trip.lag.lost", 30 + (int) (ticks * 1.3) % 40);
		Text pingText = Text.translatable("diktatura.trip.lag.ping", ping);
		int pw = Math.max(tr.getWidth(lost), tr.getWidth(pingText)) + 40;
		int ph = 30;
		int x = w - pw - 6;
		int y = h / 3;
		Ui.panel(ctx, x, y, pw, ph, 0xFFFF4040);
		for (int i = 0; i < 4; i++) {
			int bh = 4 + i * 4;
			ctx.fill(x + 7 + i * 5, y + 23 - bh, x + 10 + i * 5, y + 23, i == 0 ? 0xFFFF4040 : 0xFF3A3040);
		}
		Ui.text(ctx, tr, pingText, x + 32, y + 6, 0xFFFF5050, 1f);
		Ui.text(ctx, tr, lost, x + 32, y + 17, Ui.MUTED, 1f);
		if ((ticks / 15) % 2 == 0) {
			Ui.centered(ctx, tr, Text.translatable("diktatura.trip.lag.title"), w / 2, h / 2 - 34, 0xFFFFE070, 1f);
		}
	}
}
