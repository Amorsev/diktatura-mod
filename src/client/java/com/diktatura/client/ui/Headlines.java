package com.diktatura.client.ui;

import com.diktatura.network.UiPayloads;
import com.diktatura.network.UiPayloads.HeadlineStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Крупный заголовок посреди экрана с полосами сверху и снизу:
 * «ВОЖДЬ ИЗДАЛ УКАЗ», «ЧРЕЗВЫЧАЙНОЕ ПОЛОЖЕНИЕ», «ВОССТАНИЕ». Показывается один, новый заменяет старый.
 */
public final class Headlines {
	private static final long IN_MS = 300;
	private static final long OUT_MS = 600;

	private static HeadlineStyle style;
	private static Text title;
	private static Text subtitle;
	private static long start;
	private static long end;

	private Headlines() {
	}

	public static void show(UiPayloads.Headline p) {
		style = HeadlineStyle.byOrdinal(p.style());
		title = p.title();
		subtitle = p.subtitle();
		start = Ui.now();
		end = start + p.ticks() * 50L;
	}

	public static void clear() {
		title = null;
	}

	private static int color(HeadlineStyle s) {
		return switch (s) {
			case EMERGENCY -> Ui.blink(600) ? Ui.RED : 0xFF801818;
			case REBELLION -> Ui.blink(300) ? 0xFFFF2020 : 0xFF600000;
			case PROCLAMATION -> Ui.GREEN;
			default -> Ui.GOLD;
		};
	}

	public static void render(DrawContext ctx, int w, int h) {
		if (title == null) {
			return;
		}
		long now = Ui.now();
		if (now >= end) {
			title = null;
			return;
		}
		float a = Math.min(Math.min(1f, (now - start) / (float) IN_MS), Math.min(1f, (end - now) / (float) OUT_MS));
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		int accent = color(style);
		float scale = w < 360 ? 2.0f : 3.0f;
		int cy = h / 3;
		int bandH = Math.round(9 * scale) + 22;
		// Полосы разъезжаются от центра при появлении.
		float open = Math.min(1f, (now - start) / (float) IN_MS);
		int half = Math.round(w / 2f * open);

		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 350);
		ctx.fill(0, cy - bandH / 2, w, cy + bandH / 2, Ui.alpha(0xA0000000, a));
		ctx.fill(w / 2 - half, cy - bandH / 2 - 3, w / 2 + half, cy - bandH / 2, Ui.alpha(accent, a));
		ctx.fill(w / 2 - half, cy + bandH / 2, w / 2 + half, cy + bandH / 2 + 3, Ui.alpha(accent, a));

		ctx.getMatrices().push();
		ctx.getMatrices().translate(w / 2f, cy - bandH / 2f + 6, 0);
		ctx.getMatrices().scale(scale, scale, 1f);
		Ui.centered(ctx, tr, title, 0, 0, accent, a);
		ctx.getMatrices().pop();

		if (subtitle != null && !subtitle.getString().isEmpty()) {
			Ui.centered(ctx, tr, subtitle, w / 2, cy + bandH / 2 - 12, Ui.TEXT, a);
		}
		ctx.getMatrices().pop();
	}
}
