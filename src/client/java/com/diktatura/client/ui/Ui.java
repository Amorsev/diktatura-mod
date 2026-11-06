package com.diktatura.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import net.minecraft.util.Util;

/**
 * Общий стиль интерфейса «Диктатуры»: тёмные панели со срезанными углами и цветной
 * полосой ведомства. Всё рисуется кодом, без текстур — работает с любым ресурспаком,
 * Sodium и Iris (только {@link DrawContext}).
 */
public final class Ui {
	public static final int BG = 0xD0101014;
	public static final int BG_LIGHT = 0xE0202028;
	public static final int EDGE = 0x60FFFFFF;
	public static final int TEXT = 0xFFE8E8E8;
	public static final int MUTED = 0xFF9A9AA0;

	public static final int GOLD = 0xFFD4A017;
	public static final int RED = 0xFFD03030;
	public static final int GREEN = 0xFF3FBF3F;
	public static final int CYAN = 0xFF3FB8C8;
	public static final int PURPLE = 0xFF8E44AD;
	public static final int GRAY = 0xFF808088;

	private static final int CUT = 3;

	private Ui() {
	}

	/** Миллисекунды для анимаций (плавно, не зависит от частоты тиков). */
	public static long now() {
		return Util.getMeasuringTimeMs();
	}

	/** Прямоугольник со срезанными углами. */
	public static void cut(DrawContext ctx, int x, int y, int w, int h, int color) {
		int c = Math.min(CUT, Math.min(w, h) / 2);
		for (int i = 0; i < c; i++) {
			int inset = c - i;
			ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			ctx.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
		}
		ctx.fill(x, y + c, x + w, y + h - c, color);
	}

	/** Панель: фон, светлая кромка сверху и полоса ведомства слева. */
	public static void panel(DrawContext ctx, int x, int y, int w, int h, int accent) {
		cut(ctx, x, y, w, h, BG);
		ctx.fill(x + CUT, y, x + w - CUT, y + 1, EDGE);
		ctx.fill(x, y + CUT, x + 2, y + h - CUT, accent);
	}

	/** Панель с полосой сверху (для окон). */
	public static void window(DrawContext ctx, int x, int y, int w, int h, int accent) {
		cut(ctx, x, y, w, h, BG);
		ctx.fill(x + CUT, y, x + w - CUT, y + 2, accent);
		ctx.fill(x + CUT, y + h - 1, x + w - CUT, y + h, EDGE);
	}

	/** Полоса значения 0–100. */
	public static void bar(DrawContext ctx, int x, int y, int w, int h, int value, int color) {
		ctx.fill(x, y, x + w, y + h, 0x90000000);
		int fill = Math.round(w * Math.max(0, Math.min(100, value)) / 100f);
		ctx.fill(x, y, x + fill, y + h, color);
		ctx.fill(x, y, x + fill, y + 1, 0x40FFFFFF);
	}

	/** Мигание: возвращает true в «яркую» половину периода. */
	public static boolean blink(int periodMs) {
		return (now() / (periodMs / 2)) % 2 == 0;
	}

	/** Смешать цвет с прозрачностью 0..1 (умножает альфу). */
	public static int alpha(int color, float a) {
		int base = (color >>> 24) & 0xFF;
		int na = Math.round(base * Math.max(0f, Math.min(1f, a)));
		return (na << 24) | (color & 0x00FFFFFF);
	}

	/** Текст с альфой; слишком прозрачный текст ванильный рендер рисует непрозрачным, поэтому отсекаем. */
	public static void text(DrawContext ctx, TextRenderer tr, Text t, int x, int y, int color, float a) {
		if (a < 0.05f) {
			return;
		}
		ctx.drawTextWithShadow(tr, t, x, y, alpha(color | 0xFF000000, a));
	}

	/** Текст, обрезанный по ширине (с многоточием). */
	public static void textFit(DrawContext ctx, TextRenderer tr, Text t, int x, int y, int maxW, int color, float a) {
		if (a < 0.05f) {
			return;
		}
		ctx.drawTextWithShadow(tr, fit(tr, t, maxW), x, y, alpha(color | 0xFF000000, a));
	}

	public static OrderedText fit(TextRenderer tr, Text t, int maxW) {
		if (tr.getWidth(t) <= maxW) {
			return t.asOrderedText();
		}
		int dots = tr.getWidth("…");
		StringVisitable cut = tr.trimToWidth(t, Math.max(0, maxW - dots));
		return Language.getInstance().reorder(StringVisitable.concat(cut, StringVisitable.plain("…")));
	}

	public static void centered(DrawContext ctx, TextRenderer tr, Text t, int cx, int y, int color, float a) {
		text(ctx, tr, t, cx - tr.getWidth(t) / 2, y, color, a);
	}

	/** Время в формате м:сс. */
	public static String time(int seconds) {
		int s = Math.max(0, seconds);
		return String.format("%d:%02d", s / 60, s % 60);
	}
}
