package com.diktatura.client;

import com.diktatura.client.ui.Ui;
import com.diktatura.network.UiPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Общее окно НПС: портрет, имя, ведомство, реплика (печатная машинка), сводка, кнопки.
 * Содержимое целиком приходит с сервера ({@link UiPayloads.NpcScreen}); кнопки
 * отправляют на сервер только идентификатор действия.
 */
public class NpcScreen extends Screen {
	private static final long CHAR_MS = 22;
	private static final double CLOSE_DIST_SQ = 10.0 * 10.0;

	private final UiPayloads.NpcScreen data;
	private long opened;
	/** Прокрутка реплики (в пикселях) и прокручивал ли игрок сам (тогда не следуем за машинкой). */
	private double lineScroll;
	private boolean userScrolled;
	private int lineX;
	private int lineY;
	private int lineW;
	private static final int LINE_H = 50;
	private List<OrderedText> infoLines = List.of();
	private int left;
	private int top;
	private int winW;
	private int winH;

	public NpcScreen(UiPayloads.NpcScreen data) {
		super(data.name());
		this.data = data;
		this.opened = Ui.now();
	}

	public int entityId() {
		return data.entityId();
	}

	@Override
	protected void init() {
		int cols = data.actions().size() > 6 ? 3 : 2;
		winW = Math.min(cols == 3 ? 360 : 320, width - 20);
		int rows = (data.actions().size() + cols - 1) / cols;
		// Сводка переносится по строкам, а не обрезается.
		java.util.ArrayList<OrderedText> lines = new java.util.ArrayList<>();
		for (Text t : data.info()) {
			lines.addAll(textRenderer.wrapLines(t, winW - 20));
		}
		infoLines = lines;
		winH = Math.min(height - 20, 124 + infoLines.size() * 10 + rows * 24);
		left = (width - winW) / 2;
		top = (height - winH) / 2;

		int bw = (winW - 16 - (cols - 1) * 6) / cols;
		int by = top + winH - 8 - rows * 24 + 2;
		List<UiPayloads.NpcAction> actions = data.actions();
		for (int i = 0; i < actions.size(); i++) {
			UiPayloads.NpcAction a = actions.get(i);
			int bx = left + 8 + (i % cols) * (bw + 6);
			int y = by + (i / cols) * 24;
			ButtonWidget b = ButtonWidget.builder(a.label(), btn -> press(a.id()))
				.dimensions(bx, y, bw, 20).build();
			b.active = a.enabled();
			addDrawableChild(b);
		}
	}

	private void press(String action) {
		switch (action) {
			case "client:close" -> close();
			case "client:laws" -> {
				if (client != null) {
					client.setScreen(new LawBookScreen(this));
				}
			}
			default -> {
				ClientPlayNetworking.send(new UiPayloads.NpcClick(data.npc(), data.entityId(), action));
				// «play:» — игра идёт на столе: окно закрывается, чтобы было видно анимацию.
				if (action.startsWith("play:")) {
					close();
				}
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		// Отошёл от НПС — окно закрывается само.
		if (client != null && client.player != null && client.world != null) {
			Entity e = client.world.getEntityById(data.entityId());
			if (e == null || e.squaredDistanceTo(client.player) > CLOSE_DIST_SQ) {
				close();
			}
		}
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		// Панель рисуется под кнопками.
		Ui.window(ctx, left, top, winW, winH, data.accent() | 0xFF000000);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);

		// Портрет
		int px = left + 8;
		int py = top + 10;
		int pw = 58;
		int phh = 78;
		ctx.fill(px, py, px + pw, py + phh, Ui.BG_LIGHT);
		ctx.fill(px, py + phh - 1, px + pw, py + phh, data.accent() | 0xFF000000);
		Entity e = client == null || client.world == null ? null : client.world.getEntityById(data.entityId());
		if (e instanceof LivingEntity living) {
			InventoryScreen.drawEntity(ctx, px, py, px + pw, py + phh, 30, 0.0625f, mouseX, mouseY, living);
		} else {
			ctx.drawCenteredTextWithShadow(textRenderer, "?", px + pw / 2, py + phh / 2 - 4, Ui.MUTED);
		}

		// Имя и ведомство
		int tx = px + pw + 10;
		int tw = left + winW - 10 - tx;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(tx, top + 10, 0);
		ctx.getMatrices().scale(1.5f, 1.5f, 1f);
		ctx.drawTextWithShadow(textRenderer, data.name(), 0, 0, 0xFFFFD27F);
		ctx.getMatrices().pop();
		ctx.drawTextWithShadow(textRenderer, data.role(), tx, top + 25, Ui.MUTED);

		// Реплика печатной машинкой — в рамке с прокруткой колесом мыши.
		String full = data.line().getString();
		int shown = (int) Math.min(full.length(), (Ui.now() - opened) / CHAR_MS);
		Text line = Text.literal("«" + full.substring(0, shown) + (shown < full.length() ? "" : "»"));
		lineX = tx;
		lineY = top + 40;
		lineW = tw - 6;
		List<OrderedText> wrapped = textRenderer.wrapLines(line, lineW);
		int maxScroll = Math.max(0, wrapped.size() * 10 - LINE_H);
		if (!userScrolled) {
			lineScroll = maxScroll;
		}
		lineScroll = Math.max(0, Math.min(maxScroll, lineScroll));
		ctx.enableScissor(lineX, lineY, lineX + lineW + 6, lineY + LINE_H);
		int ly0 = lineY - (int) Math.round(lineScroll);
		for (int i = 0; i < wrapped.size(); i++) {
			int y = ly0 + i * 10;
			if (y > lineY - 10 && y < lineY + LINE_H) {
				ctx.drawTextWithShadow(textRenderer, wrapped.get(i), tx, y, Ui.TEXT);
			}
		}
		ctx.disableScissor();
		if (maxScroll > 0) {
			// Полоса прокрутки справа от реплики.
			int barX = lineX + lineW + 3;
			ctx.fill(barX, lineY, barX + 2, lineY + LINE_H, 0x30FFFFFF);
			int thumbH = Math.max(8, LINE_H * LINE_H / (LINE_H + maxScroll));
			int thumbY = lineY + (int) ((LINE_H - thumbH) * (lineScroll / maxScroll));
			ctx.fill(barX, thumbY, barX + 2, thumbY + thumbH, data.accent() | 0xFF000000);
			if (lineScroll < maxScroll) {
				ctx.drawTextWithShadow(textRenderer, Text.literal("▼"), barX - 8, lineY + LINE_H - 8, Ui.MUTED);
			}
		}
		int ly = lineY + LINE_H;

		// Сводка
		int iy = Math.max(ly + 6, top + 10 + phh + 6);
		ctx.fill(left + 8, iy - 4, left + winW - 8, iy - 3, 0x40FFFFFF);
		for (OrderedText t : infoLines) {
			ctx.drawTextWithShadow(textRenderer, t, left + 10, iy, 0xFFC8C8C8);
			iy += 10;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount != 0) {
			userScrolled = true;
			lineScroll -= verticalAmount * 10;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Клик по реплике — показать её целиком сразу (без печатной машинки).
		if (mouseX >= lineX && mouseX < lineX + lineW + 6 && mouseY >= lineY && mouseY < lineY + LINE_H) {
			opened = 0;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
