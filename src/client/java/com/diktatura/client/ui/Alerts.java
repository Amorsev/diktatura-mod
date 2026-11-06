package com.diktatura.client.ui;

import com.diktatura.item.ModItems;
import com.diktatura.network.UiPayloads;
import com.diktatura.network.UiPayloads.AlertKind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Баннеры сверху экрана: иконка, заголовок, подсказка. Не больше {@link #MAX} сразу;
 * баннер с тем же заголовком не дублируется, а продлевается. Опасные мигают красным.
 * По образцу client/hud/Alerts из референсного мода, реализация своя.
 */
public final class Alerts {
	private static final int MAX = 3;
	private static final long SLIDE_MS = 220;
	private static final long FADE_MS = 400;

	private static final List<Banner> BANNERS = new ArrayList<>();
	private static ItemStack[] icons;

	private Alerts() {
	}

	private static final class Banner {
		final AlertKind kind;
		final String key;
		final Text title;
		Text hint;
		final long start;
		long end;

		Banner(AlertKind kind, Text title, Text hint, long start, long end) {
			this.kind = kind;
			this.key = title.getString();
			this.title = title;
			this.hint = hint;
			this.start = start;
			this.end = end;
		}
	}

	public static void push(UiPayloads.Alert p) {
		push(AlertKind.byOrdinal(p.kind()), p.title(), p.hint(), p.ticks() * 50L);
	}

	public static void push(AlertKind kind, Text title, Text hint, long durationMs) {
		long now = Ui.now();
		String key = title.getString();
		for (Banner b : BANNERS) {
			if (b.key.equals(key)) {
				b.end = Math.max(b.end, now + durationMs);
				if (!hint.getString().isEmpty()) {
					b.hint = hint;
				}
				return;
			}
		}
		BANNERS.add(0, new Banner(kind, title, hint, now, now + durationMs));
		while (BANNERS.size() > MAX) {
			BANNERS.remove(BANNERS.size() - 1);
		}
	}

	public static void clear() {
		BANNERS.clear();
	}

	private static ItemStack icon(AlertKind kind) {
		if (icons == null) {
			icons = new ItemStack[] {
				new ItemStack(Items.PAPER),
				new ItemStack(ModItems.LAW_BOOK),
				new ItemStack(Items.CLOCK),
				new ItemStack(Items.IRON_BARS),
				new ItemStack(Items.EMERALD)
			};
		}
		return icons[kind.ordinal()];
	}

	private static int accent(AlertKind kind) {
		return switch (kind) {
			case DECREE -> Ui.GOLD;
			case EVENT -> Ui.CYAN;
			case DANGER -> Ui.blink(500) ? Ui.RED : 0xFF701818;
			case GOOD -> Ui.GREEN;
			default -> Ui.GRAY;
		};
	}

	public static void render(DrawContext ctx, int screenW) {
		if (BANNERS.isEmpty()) {
			return;
		}
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		long now = Ui.now();
		BANNERS.removeIf(b -> now >= b.end);
		int y = 4;
		Iterator<Banner> it = BANNERS.iterator();
		while (it.hasNext()) {
			Banner b = it.next();
			boolean hasHint = !b.hint.getString().isEmpty();
			int textW = Math.max(tr.getWidth(b.title), hasHint ? tr.getWidth(b.hint) : 0);
			int w = Math.min(screenW - 8, textW + 32);
			int h = hasHint ? 28 : 20;
			int x = (screenW - w) / 2;

			float slide = Math.min(1f, (now - b.start) / (float) SLIDE_MS);
			float fade = Math.min(1f, (b.end - now) / (float) FADE_MS);
			int dy = Math.round((1f - slide) * -(h + 4));
			int by = y + dy;

			ctx.getMatrices().push();
			ctx.getMatrices().translate(0, 0, 300);
			Ui.cut(ctx, x, by, w, h, Ui.alpha(Ui.BG, fade));
			Ui.cut(ctx, x, by, 3, h, Ui.alpha(accent(b.kind), fade));
			ctx.fill(x + 3, by + h - 1, x + w - 3, by + h, Ui.alpha(accent(b.kind), fade * 0.6f));
			if (fade > 0.5f) {
				ctx.drawItem(icon(b.kind), x + 7, by + (h - 16) / 2);
			}
			int titleColor = b.kind == AlertKind.DANGER ? 0xFFFF6060 : b.kind == AlertKind.DECREE ? 0xFFFFD27F : Ui.TEXT;
			int tx = x + 28;
			int maxW = w - 32;
			if (hasHint) {
				Ui.textFit(ctx, tr, b.title, tx, by + 5, maxW, titleColor, fade);
				Ui.textFit(ctx, tr, b.hint, tx, by + 16, maxW, Ui.MUTED, fade);
			} else {
				Ui.textFit(ctx, tr, b.title, tx, by + 6, maxW, titleColor, fade);
			}
			ctx.getMatrices().pop();
			y += h + 3;
		}
	}
}
