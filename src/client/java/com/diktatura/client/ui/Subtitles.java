package com.diktatura.client.ui;

import com.diktatura.network.UiPayloads;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Субтитры реплик НПС над хотбаром: имя говорящего, текст печатной машинкой,
 * стрелка в сторону говорящего. По образцу util/Voice из референсного мода.
 */
public final class Subtitles {
	private static final int MAX = 2;
	private static final long CHAR_MS = 28;
	private static final long FADE_MS = 400;
	private static final int WIDTH = 260;

	private static final List<Line> LINES = new ArrayList<>();
	private static ItemStack radio;

	private record Line(int entityId, Text speaker, String text, long start, long end) {
	}

	private Subtitles() {
	}

	public static void show(UiPayloads.Speech p) {
		long now = Ui.now();
		String text = p.line().getString();
		LINES.removeIf(l -> l.entityId == p.entityId() && l.text.equals(text));
		long typing = text.length() * CHAR_MS;
		LINES.add(new Line(p.entityId(), p.speaker(), text, now, now + typing + p.ticks() * 50L));
		while (LINES.size() > MAX) {
			LINES.remove(0);
		}
	}

	public static void clear() {
		LINES.clear();
	}

	public static void render(DrawContext ctx, int w, int h) {
		if (LINES.isEmpty()) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		TextRenderer tr = mc.textRenderer;
		long now = Ui.now();
		LINES.removeIf(l -> now >= l.end);
		if (radio == null) {
			radio = new ItemStack(Items.BELL);
		}
		int boxW = Math.min(WIDTH, w - 20);
		int y = h - 62;
		for (int i = LINES.size() - 1; i >= 0; i--) {
			Line l = LINES.get(i);
			int shown = (int) Math.min(l.text.length(), (now - l.start) / CHAR_MS);
			// Высота — по полному тексту (блок не «прыгает»), рисуем только уже напечатанное.
			int lines = Math.max(1, Math.min(3, tr.wrapLines(Text.literal(l.text), boxW - 28).size()));
			List<OrderedText> wrapped = shown > 0
				? tr.wrapLines(Text.literal(l.text.substring(0, shown)), boxW - 28) : List.of();
			int boxH = 14 + lines * 10;
			y -= boxH + 3;
			int x = (w - boxW) / 2;
			float a = Math.min(1f, (l.end - now) / (float) FADE_MS);

			ctx.getMatrices().push();
			ctx.getMatrices().translate(0, 0, 250);
			Ui.cut(ctx, x, y, boxW, boxH, Ui.alpha(Ui.BG, a));
			Ui.cut(ctx, x, y, 3, boxH, Ui.alpha(Ui.GOLD, a));
			if (a > 0.5f) {
				ctx.drawItem(radio, x + 6, y + (boxH - 16) / 2);
			}
			Ui.text(ctx, tr, l.speaker, x + 26, y + 3, 0xFFFFD27F, a);
			String arrow = arrow(mc, l.entityId);
			if (!arrow.isEmpty()) {
				Ui.text(ctx, tr, Text.literal(arrow), x + boxW - 12, y + 3, Ui.MUTED, a);
			}
			// Длинная реплика сама «прокручивается»: видны последние напечатанные строки.
			int from = Math.max(0, wrapped.size() - lines);
			for (int j = from; j < wrapped.size(); j++) {
				ctx.drawTextWithShadow(tr, wrapped.get(j), x + 26, y + 13 + (j - from) * 10, Ui.alpha(Ui.TEXT, a));
			}
			ctx.getMatrices().pop();
		}
	}

	/** Стрелка в сторону говорящего (как у ванильных субтитров звуков). */
	private static String arrow(MinecraftClient mc, int entityId) {
		if (entityId < 0 || mc.world == null || mc.player == null) {
			return "";
		}
		Entity e = mc.world.getEntityById(entityId);
		if (e == null) {
			return "";
		}
		double dx = e.getX() - mc.player.getX();
		double dz = e.getZ() - mc.player.getZ();
		if (dx * dx + dz * dz < 4.0) {
			return "";
		}
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		double rel = ((yaw - mc.player.getYaw()) % 360 + 540) % 360 - 180;
		if (rel > 30) {
			return ">";
		}
		if (rel < -30) {
			return "<";
		}
		return "";
	}
}
