package com.diktatura.client;

import com.diktatura.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Госреклама: плакат на весь экран на 13 секунд. Закрыть нельзя (Esc не работает),
 * кнопка «Пропустить» убегает от курсора, игра при этом не на паузе.
 * Идея — из референсного мода (AdScreen), реализация своя.
 */
public class AdScreen extends Screen {
	private static final int[] COLORS = {0xFFB0182A, 0xFF1F6B3A, 0xFF24304A, 0xFF9A6A00, 0xFF5E2A7A, 0xFF1F5E7A};

	private final int ad;
	private final long end;
	private float skipX = -1;
	private float skipY = -1;
	private int skipW;
	private static final int SKIP_H = 16;

	public AdScreen(int ad, int seconds) {
		super(Text.translatable("diktatura.ad." + ad + ".title"));
		this.ad = ad;
		this.end = Ui.now() + seconds * 1000L;
	}

	@Override
	protected void init() {
		if (client != null) {
			client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.8f));
		}
		skipW = textRenderer.getWidth(Text.translatable("diktatura.ad.skip")) + 12;
		if (skipX < 0) {
			skipX = width - skipW - 12;
			skipY = height - SKIP_H - 12;
		}
	}

	private int secondsLeft() {
		return (int) Math.max(0, (end - Ui.now() + 999) / 1000);
	}

	@Override
	public void tick() {
		super.tick();
		if (Ui.now() >= end) {
			close();
		}
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// Без размытия: реклама должна быть яркой.
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int accent = COLORS[Math.floorMod(ad, COLORS.length)];
		ctx.fill(0, 0, width, height, 0xF0101014);
		int pw = Math.min(420, width - 40);
		int ph = Math.min(240, height - 60);
		int px = (width - pw) / 2;
		int py = (height - ph) / 2 - 10;
		Ui.window(ctx, px, py, pw, ph, accent);
		ctx.fill(px + 6, py + 6, px + pw - 6, py + 46, accent);

		ctx.getMatrices().push();
		ctx.getMatrices().translate(width / 2f, py + 14, 0);
		float scale = pw < 300 ? 1.5f : 2.2f;
		ctx.getMatrices().scale(scale, scale, 1f);
		Ui.centered(ctx, textRenderer, title, 0, 0, 0xFFFFFFFF, 1f);
		ctx.getMatrices().pop();

		int y = py + 60;
		List<OrderedText> lines = textRenderer.wrapLines(Text.translatable("diktatura.ad." + ad + ".text"), pw - 40);
		for (OrderedText l : lines) {
			ctx.drawCenteredTextWithShadow(textRenderer, l, width / 2, y, 0xFFE8E8E8);
			y += 11;
		}
		y += 8;
		for (OrderedText l : textRenderer.wrapLines(Text.translatable("diktatura.ad." + ad + ".fine"), pw - 40)) {
			ctx.drawCenteredTextWithShadow(textRenderer, l, width / 2, Math.min(y, py + ph - 30), 0xFF8A8A92);
			y += 10;
		}
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("diktatura.ad.timer", secondsLeft()),
			width / 2, py + ph - 16, 0xFFFFD27F);

		// «Пропустить» убегает от мыши.
		float cx = skipX + skipW / 2f;
		float cy = skipY + SKIP_H / 2f;
		float dx = cx - mouseX;
		float dy = cy - mouseY;
		float dist = MathHelper.sqrt(dx * dx + dy * dy);
		if (dist < 60 && dist > 0.01f) {
			skipX += dx / dist * 9;
			skipY += dy / dist * 9;
		}
		if (skipX < 4 || skipX > width - skipW - 4 || skipY < 4 || skipY > height - SKIP_H - 4) {
			// Прижали к краю — прыгает в случайное место.
			skipX = 8 + (float) Math.random() * Math.max(1, width - skipW - 16);
			skipY = 8 + (float) Math.random() * Math.max(1, height - SKIP_H - 16);
		}
		int sx = Math.round(skipX);
		int sy = Math.round(skipY);
		Ui.cut(ctx, sx, sy, skipW, SKIP_H, 0xE0303038);
		ctx.drawTextWithShadow(textRenderer, Text.translatable("diktatura.ad.skip"), sx + 6, sy + 4, 0xFFE8E8E8);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX >= skipX && mouseX <= skipX + skipW && mouseY >= skipY && mouseY <= skipY + SKIP_H) {
			// Поймали! Но реклама всё равно досмотрится — таков порядок.
			skipX = 8 + (float) Math.random() * Math.max(1, width - skipW - 16);
			skipY = 8 + (float) Math.random() * Math.max(1, height - SKIP_H - 16);
		}
		return true;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
