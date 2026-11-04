package com.diktatura.client;

import com.diktatura.law.Law;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Книга законов: список действующих законов с описаниями, прокрутка колесом. */
public class LawBookScreen extends Screen {
	private static final int TOP = 34;
	private int scroll;
	private int contentHeight;

	public LawBookScreen() {
		super(Text.translatable("diktatura.book.title"));
	}

	@Override
	protected void init() {
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
			.dimensions(width / 2 - 50, height - 26, 100, 20).build());
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.GOLD, Formatting.BOLD), width / 2, 10, 0xFFFFFF);
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("diktatura.book.subtitle", ClientState.laws.size(),
			ClientState.time(ClientState.decreeSeconds)), width / 2, 21, 0xA0A0A0);

		int boxW = Math.min(320, width - 40);
		int left = (width - boxW) / 2;
		int bottom = height - 32;
		ctx.fill(left - 6, TOP - 4, left + boxW + 6, bottom + 2, 0xA0201810);

		ctx.enableScissor(left - 6, TOP - 4, left + boxW + 6, bottom + 2);
		int y = TOP - scroll;
		int start = y;
		if (ClientState.laws.isEmpty()) {
			ctx.drawTextWithShadow(textRenderer, Text.translatable("diktatura.book.empty"), left, y, 0xC0C0C0);
			y += 12;
		}
		int n = 1;
		for (String id : ClientState.laws) {
			Text name = Text.literal(n++ + ". ").append(Text.translatable(Law.translationKey(id)));
			ctx.drawTextWithShadow(textRenderer, name, left, y, 0xFFD27F);
			y += 11;
			List<OrderedText> lines = new ArrayList<>(textRenderer.wrapLines(
				Text.translatable(Law.translationKey(id) + ".desc"), boxW - 8));
			for (OrderedText line : lines) {
				ctx.drawTextWithShadow(textRenderer, line, left + 8, y, 0xD8D8D8);
				y += 10;
			}
			y += 6;
		}
		ctx.disableScissor();
		contentHeight = y - start;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int visible = height - 32 - TOP;
		int max = Math.max(0, contentHeight - visible);
		scroll = (int) Math.max(0, Math.min(max, scroll - verticalAmount * 12));
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
