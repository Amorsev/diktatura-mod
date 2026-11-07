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
	private static final Map<String, Long> START = new HashMap<>();
	/** Комментарии зрителей «вертикального видео» (ключи diktatura.trip.tiktok.comment.N). */
	private static final int COMMENTS = 16;
	private static long ticks;

	private ClientFx() {
	}

	public static void set(String id, int duration) {
		if (duration <= 0) {
			UNTIL.remove(id);
			START.remove(id);
		} else {
			UNTIL.put(id, ticks + duration);
			START.put(id, ticks);
		}
	}

	public static boolean active(String id) {
		Long t = UNTIL.get(id);
		return t != null && t > ticks;
	}

	/** Какая доля эффекта уже прошла (0..1). */
	public static float progress(String id) {
		Long until = UNTIL.get(id);
		Long start = START.get(id);
		if (until == null || start == null || until <= start) {
			return 0f;
		}
		return MathHelper.clamp((ticks - start) / (float) (until - start), 0f, 1f);
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
			renderPotato(ctx, tr, w);
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
			renderTikTok(ctx, MinecraftClient.getInstance().textRenderer, w, h);
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
	private static void renderPotato(DrawContext ctx, TextRenderer tr, int w) {
		// В правом верхнем углу: слева сверху — панель законов.
		int x = w - 150 - 6;
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

	/**
	 * «Вертикальное видео» (по мотивам референса Gr1tW4lk, MIT): кадр 9:16 и интерфейс видеоленты
	 * в духе госТВ — канал вождя, накрученные лайки, добровольно-принудительные комментарии граждан.
	 */
	private static void renderTikTok(DrawContext ctx, TextRenderer tr, int w, int h) {
		int vw = Math.min(w, h * 9 / 16);
		int bar = (w - vw) / 2;
		ctx.fill(0, 0, bar, h, 0xFF000000);
		ctx.fill(w - bar, 0, w, h, 0xFF000000);

		// Вкладки сверху: «Подписки» серые, «Одобренное» выделено и подчёркнуто.
		Text subs = Text.translatable("diktatura.trip.tiktok.tab.subs");
		Text main = Text.translatable("diktatura.trip.tiktok.tab.main").styled(st -> st.withBold(true));
		int gap = 10;
		int total = tr.getWidth(subs) + gap + tr.getWidth(main);
		int tx = w / 2 - total / 2;
		ctx.drawText(tr, subs, tx, 8, 0xFFB0B0B0, true);
		int mx = tx + tr.getWidth(subs) + gap;
		ctx.drawText(tr, main, mx, 8, 0xFFFFFFFF, true);
		ctx.fill(mx, 19, mx + tr.getWidth(main), 20, 0xFFFFFFFF);

		// Справа: аватар канала с «подписаться» и столбик кнопок со счётчиками.
		int right = w - bar - 16;
		int iy = h / 2 - 54;
		ctx.fill(right - 9, iy - 5, right + 9, iy + 13, 0xFFFFFFFF);
		ctx.fill(right - 8, iy - 4, right + 8, iy + 12, 0xFF8A1620);
		ctx.drawItem(new ItemStack(com.diktatura.item.ModItems.SCEPTER), right - 8, iy - 4);
		ctx.fill(right - 4, iy + 10, right + 4, iy + 16, 0xFFFF2D55);
		ctx.drawText(tr, Text.literal("+"), right - 2, iy + 9, 0xFFFFFFFF, false);
		// Лайки накручены: число растёт на глазах, как и положено госканалу.
		long likes = 1_300_000L + ticks * 1337L;
		String[] labels = {compact(likes), "48K", "0"};
		for (int i = 0; i < 3; i++) {
			int cy = iy + 30 + i * 34;
			switch (i) {
				case 0 -> drawHeart(ctx, right - 7, cy, 2, 0xFFFF2D55);
				case 1 -> drawBubble(ctx, right - 7, cy);
				default -> drawShare(ctx, right - 7, cy);
			}
			Text label = i == 2 ? Text.translatable("diktatura.trip.tiktok.share") : Text.literal(labels[i]);
			Ui.centered(ctx, tr, label, right, cy + 15, 0xFFFFFFFF, 1f);
		}

		// Подпись к видео над хотбаром.
		int lx = bar + 6;
		int capY = h - 92;
		Ui.text(ctx, tr, Text.translatable("diktatura.trip.tiktok.channel").styled(st -> st.withBold(true)), lx, capY, 0xFFFFFFFF, 1f);
		Ui.textFit(ctx, tr, Text.translatable("diktatura.trip.tiktok.caption"), lx, capY + 11, vw - 44, 0xFFDDDDDD, 1f);
		Ui.textFit(ctx, tr, Text.translatable("diktatura.trip.tiktok.sound"), lx, capY + 22, vw - 44, 0xFFBBBBBB, 1f);

		// Бегущие комментарии граждан: сменяются каждые ~1,2 секунды.
		int base = (int) (ticks / 25);
		for (int i = 0; i < 3; i++) {
			int n = (base + i * 7) % COMMENTS;
			int cy = h - 136 + i * 12;
			Text line = Text.literal(citizen(base * 31 + i * 17) + "  ").styled(st -> st.withColor(0xAAAAAA))
				.append(Text.translatable("diktatura.trip.tiktok.comment." + n).styled(st -> st.withColor(0xFFFFFF)));
			int lw = Math.min(tr.getWidth(line) + 6, vw - 40);
			ctx.fill(lx - 2, cy - 2, lx - 2 + lw, cy + 9, 0x50000000);
			ctx.drawText(tr, Ui.fit(tr, line, vw - 46), lx + 1, cy, 0xFFFFFFFF, true);
		}

		// Сердечки всплывают от кнопки лайка и тают.
		for (int i = 0; i < 6; i++) {
			float life = ((ticks + i * 13) % 60) / 60f;
			int hx = right - 18 - (int) (MathHelper.sin((ticks + i * 40) * 0.1f) * 8);
			int hy = (int) (h - 60 - life * 140);
			drawHeart(ctx, hx, hy, 1, (int) ((1 - life) * 255) << 24 | 0xFF2D55);
		}

		// Полоса прогресса «видео» — сколько осталось трипа.
		ctx.fill(bar, h - 3, w - bar, h - 1, 0x40FFFFFF);
		ctx.fill(bar, h - 3, bar + (int) (vw * progress("tiktok")), h - 1, 0xFFFFFFFF);
	}

	/** Ник зрителя: «гражданин_4821». */
	private static Text citizen(int seed) {
		return Text.translatable("diktatura.trip.tiktok.user", Math.floorMod(seed, 9000) + 1000);
	}

	private static String compact(long n) {
		if (n >= 1_000_000L) {
			return String.format(java.util.Locale.ROOT, "%.1fM", n / 1_000_000.0);
		}
		return n >= 1000 ? (n / 1000) + "K" : Long.toString(n);
	}

	private static void drawHeart(DrawContext ctx, int x, int y, int s, int color) {
		int[][] px = {{1, 0}, {2, 0}, {4, 0}, {5, 0}, {0, 1}, {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1}, {6, 1},
			{0, 2}, {1, 2}, {2, 2}, {3, 2}, {4, 2}, {5, 2}, {6, 2}, {1, 3}, {2, 3}, {3, 3}, {4, 3}, {5, 3},
			{2, 4}, {3, 4}, {4, 4}, {3, 5}};
		for (int[] p : px) {
			ctx.fill(x + p[0] * s, y + p[1] * s, x + p[0] * s + s, y + p[1] * s + s, color);
		}
	}

	/** Облачко комментариев. */
	private static void drawBubble(DrawContext ctx, int x, int y) {
		ctx.fill(x + 1, y, x + 13, y + 9, 0xFFFFFFFF);
		ctx.fill(x, y + 1, x + 14, y + 8, 0xFFFFFFFF);
		ctx.fill(x + 3, y + 9, x + 6, y + 11, 0xFFFFFFFF);
		ctx.fill(x + 3, y + 4, x + 5, y + 6, 0xFF222222);
		ctx.fill(x + 6, y + 4, x + 8, y + 6, 0xFF222222);
		ctx.fill(x + 9, y + 4, x + 11, y + 6, 0xFF222222);
	}

	/** Стрелка «поделиться». */
	private static void drawShare(DrawContext ctx, int x, int y) {
		ctx.fill(x + 8, y, x + 9, y + 11, 0xFFFFFFFF);
		for (int i = 0; i < 6; i++) {
			ctx.fill(x + 8 + i, y + i, x + 9 + i, y + 11 - i, 0xFFFFFFFF);
		}
		ctx.fill(x + 2, y + 4, x + 8, y + 7, 0xFFFFFFFF);
		ctx.fill(x, y + 6, x + 3, y + 11, 0xFFFFFFFF);
	}
}
