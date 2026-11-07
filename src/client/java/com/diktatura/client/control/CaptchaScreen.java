package com.diktatura.client.control;

import com.diktatura.network.FxPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Бюро проверки гражданства: «Докажите, что вы не робот». Клиент только отмечает клетки —
 * верный ответ знает сервер. По мотивам капчи из мода-референса (MIT, © 2026 Gr1tW4lk).
 */
public class CaptchaScreen extends Screen {
	private final int token;
	private final Text category;
	private final List<ItemStack> cells;
	private final boolean[] selected = new boolean[9];
	private boolean sent;

	public CaptchaScreen(FxPayloads.Captcha payload) {
		super(Text.translatable("diktatura.captcha.title"));
		this.token = payload.token();
		this.category = payload.category();
		this.cells = payload.cells();
	}

	private int cell() {
		return Math.max(24, Math.min(56, (height - 120) / 3));
	}

	private int gx() {
		return width / 2 - cell() * 3 / 2;
	}

	private int gy() {
		return height / 2 - cell() * 3 / 2 + 12;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int c = cell();
		int gx = gx();
		int gy = gy();
		Text ask = Text.translatable("diktatura.captcha.ask");
		Text title = Text.translatable("diktatura.captcha.title");
		int pw = Math.max(c * 3 + 16, Math.max(textRenderer.getWidth(ask), textRenderer.getWidth(title)) + 16);
		int px = width / 2 - pw / 2;
		int py = gy - 56;
		ctx.fill(px - 1, py - 1, px + pw + 1, gy + c * 3 + 41, 0xFFB0B8C8);
		ctx.fill(px, py, px + pw, gy + c * 3 + 40, 0xFFFFFFFF);
		ctx.fill(px, py, px + pw, py + 46, 0xFF8A1C1C);
		ctx.drawText(textRenderer, title, px + 8, py + 6, 0xFFFFFFFF, false);
		ctx.drawText(textRenderer, ask, px + 8, py + 18, 0xFFFFE0E0, false);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(px + 8, py + 30, 0);
		ctx.getMatrices().scale(1.3f, 1.3f, 1);
		ctx.drawText(textRenderer, category, 0, 0, 0xFFFFFFFF, false);
		ctx.getMatrices().pop();

		for (int i = 0; i < 9 && i < cells.size(); i++) {
			int x = gx + (i % 3) * c;
			int y = gy + (i / 3) * c;
			boolean hover = mouseX >= x && mouseX < x + c && mouseY >= y && mouseY < y + c;
			ctx.fill(x + 1, y + 1, x + c - 1, y + c - 1, selected[i] ? 0xFFFFC9C9 : hover ? 0xFFEFEFEF : 0xFFE0E0E0);
			ctx.getMatrices().push();
			ctx.getMatrices().translate(x + c / 2f, y + c / 2f, 0);
			float s = (c - 14) / 16f;
			ctx.getMatrices().scale(s, s, 1);
			ctx.drawItem(cells.get(i), -8, -8);
			ctx.getMatrices().pop();
			if (selected[i]) {
				ctx.fill(x + 3, y + 3, x + 13, y + 13, 0xFF8A1C1C);
				ctx.drawText(textRenderer, Text.literal("✔"), x + 4, y + 4, 0xFFFFFFFF, false);
			}
		}
		int bx = gx + c * 3 - 90;
		int by = gy + c * 3 + 10;
		boolean hover = mouseX >= bx && mouseX < bx + 90 && mouseY >= by && mouseY < by + 20;
		ctx.fill(bx, by, bx + 90, by + 20, hover ? 0xFF6E1414 : 0xFF8A1C1C);
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("diktatura.captcha.confirm"), bx + 45, by + 6, 0xFFFFFFFF);
		ctx.drawText(textRenderer, Text.translatable("diktatura.captcha.footer"), gx, by + 6, 0xFF888888, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int c = cell();
		int gx = gx();
		int gy = gy();
		for (int i = 0; i < 9; i++) {
			int x = gx + (i % 3) * c;
			int y = gy + (i / 3) * c;
			if (mouseX >= x && mouseX < x + c && mouseY >= y && mouseY < y + c) {
				selected[i] = !selected[i];
				return true;
			}
		}
		int bx = gx + c * 3 - 90;
		int by = gy + c * 3 + 10;
		if (mouseX >= bx && mouseX < bx + 90 && mouseY >= by && mouseY < by + 20) {
			int mask = 0;
			for (int i = 0; i < 9; i++) {
				if (selected[i]) {
					mask |= 1 << i;
				}
			}
			send(mask, false);
			close();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void send(int mask, boolean cancelled) {
		if (sent) {
			return;
		}
		sent = true;
		ClientPlayNetworking.send(new FxPayloads.CaptchaAnswer(token, mask, cancelled));
	}

	@Override
	public void removed() {
		send(0, true);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
