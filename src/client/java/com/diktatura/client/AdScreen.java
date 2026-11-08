package com.diktatura.client;

import com.diktatura.client.ui.Ui;
import com.diktatura.goose.ModSounds;
import com.diktatura.item.ModItems;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * Госреклама на «ГосТВ» (по мотивам AdScreen из референса Gr1tW4lk, MIT): яркий плакат с лучами,
 * товар крупным планом, мигающий заголовок, ценник, наклейка, мелкий шрифт и бегущая строка.
 * Esc не работает, игра не на паузе. «Пропустить» появляется через 5 секунд и убегает от курсора —
 * но если догнать её четыре раза, она сдаётся, и рекламу можно закрыть.
 */
public class AdScreen extends Screen {
	private static final int[] BG = {0xFF8D1A2A, 0xFF1F3A93, 0xFF2E6B32, 0xFF7A5200, 0xFF4A148C, 0xFF0E5E6E};
	private static final int SKIP_AFTER_MS = 5000;
	private static final int DODGES = 4;
	private static final int SKIP_H = 16;

	private final int ad;
	private final long start;
	private final long end;
	private final Random rng = Random.create();
	private int skipX = -1;
	private int skipY;
	private int dodges;

	public AdScreen(int ad, int seconds) {
		super(Text.translatable("diktatura.ad." + ad + ".title"));
		this.ad = Math.floorMod(ad, BG.length);
		this.start = Ui.now();
		this.end = start + seconds * 1000L;
	}

	private void play(SoundEvent sound, float pitch) {
		if (client != null) {
			client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, 0.8f));
		}
	}

	@Override
	protected void init() {
		if (skipX < 0) {
			play(ModSounds.AD_JINGLE, 1f);
			skipX = 0;
			placeSkip();
		}
	}

	private int[] panel() {
		int pw = Math.min(380, width - 24);
		int ph = Math.min(210, height - 70);
		return new int[] {(width - pw) / 2, (height - ph) / 2 - 6, pw, ph};
	}

	private int skipW() {
		return textRenderer.getWidth(Text.translatable("diktatura.ad.skip")) + 12;
	}

	private void placeSkip() {
		int[] p = panel();
		int bw = skipW();
		skipX = p[0] + 10 + rng.nextInt(Math.max(1, p[2] - bw - 20));
		skipY = p[1] + 10 + rng.nextInt(Math.max(1, p[3] - SKIP_H - 20));
		if (dodges == 0) {
			skipX = p[0] + p[2] - bw - 8;
			skipY = p[1] + p[3] - SKIP_H - 8;
		}
	}

	private boolean skipVisible() {
		return Ui.now() - start >= SKIP_AFTER_MS;
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
		float t = (Ui.now() - start) / 50f;
		int[] p = panel();
		int x = p[0];
		int y = p[1];
		int pw = p[2];
		int ph = p[3];
		int bg = BG[ad];
		ctx.fill(0, 0, width, height, 0xC0000000);

		// Плакат: фон, вращающиеся лучи за товаром, золотые кромки.
		ctx.fill(x, y, x + pw, y + ph, bg);
		int cx = x + pw / 4 + 6;
		int cy = y + ph / 2 + 4;
		ctx.enableScissor(x, y, x + pw, y + ph);
		for (int i = 0; i < 12; i++) {
			double a = t * 0.02 + i * Math.PI / 6;
			for (int s = 0; s < 40; s++) {
				int rx = cx + (int) (Math.cos(a) * s * 8);
				int ry = cy + (int) (Math.sin(a) * s * 8);
				int size = 2 + s / 5;
				ctx.fill(rx - size / 2, ry - size / 2, rx + size / 2 + 1, ry + size / 2 + 1, 0x1CFFFFFF);
			}
		}
		// Конфетти.
		for (int i = 0; i < 18; i++) {
			float fx = (i * 53 + t * (0.6f + (i % 5) * 0.15f)) % pw;
			float fy = (i * 37 + t * (1.0f + (i % 3) * 0.3f)) % ph;
			int c = (i % 3 == 0) ? 0xC0FFD700 : (i % 3 == 1) ? 0xA0FFFFFF : 0xA0FF5252;
			ctx.fill(x + (int) fx, y + (int) fy, x + (int) fx + 2, y + (int) fy + 3, c);
		}
		ctx.disableScissor();
		ctx.fill(x, y, x + pw, y + 3, 0xFFFFD700);
		ctx.fill(x, y + ph - 3, x + pw, y + ph, 0xFFFFD700);
		ctx.fill(x, y, x + 2, y + ph, 0x80FFD700);
		ctx.fill(x + pw - 2, y, x + pw, y + ph, 0x80FFD700);

		// Товар крупным планом: покачивается.
		ctx.getMatrices().push();
		ctx.getMatrices().translate(cx, cy + MathHelper.sin(t * 0.15f) * 4, 100);
		ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(t * 0.08f) * 8));
		float scale = Math.min(5f, ph / 40f);
		ctx.getMatrices().scale(scale, scale, 1f);
		ctx.drawItem(product(), -8, -8);
		ctx.getMatrices().pop();

		// Заголовок: жёлтый, «дышит».
		int tx = x + pw / 2;
		int tw = pw / 2 - 12;
		float wob = 1f + MathHelper.sin(t * 0.3f) * 0.05f;
		float head = Math.min(2.0f, tw / (float) Math.max(1, textRenderer.getWidth(title))) * wob;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(tx + tw / 2f, y + 18, 0);
		ctx.getMatrices().scale(head, head, 1f);
		ctx.drawCenteredTextWithShadow(textRenderer, title, 0, 0, 0xFFFFEB3B);
		ctx.getMatrices().pop();

		int ly = y + 44;
		List<OrderedText> lines = textRenderer.wrapLines(Text.translatable("diktatura.ad." + ad + ".text"), tw);
		for (OrderedText l : lines) {
			if (ly > y + ph - 74) {
				break;
			}
			ctx.drawTextWithShadow(textRenderer, l, tx, ly, 0xFFFFFFFF);
			ly += 10;
		}

		// Ценник.
		int py = y + ph - 64;
		ctx.fill(tx, py, tx + tw, py + 26, 0xFFE53935);
		ctx.fill(tx, py, tx + tw, py + 1, 0xFFFF8A80);
		ctx.fill(tx + tw - 4, py, tx + tw, py + 26, 0xFFB71C1C);
		ctx.drawTextWithShadow(textRenderer, Text.translatable("diktatura.ad.price"), tx + 5, py + 3, 0xFFFFFFFF);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(tx + 5, py + 13, 0);
		ctx.getMatrices().scale(1.25f, 1.25f, 1f);
		ctx.drawTextWithShadow(textRenderer, Ui.fit(textRenderer, Text.translatable("diktatura.ad." + ad + ".price"),
			(int) ((tw - 10) / 1.25f)), 0, 0, 0xFFFFFF00);
		ctx.getMatrices().pop();

		// Мелкий шрифт под ценником.
		int fy = py + 30;
		for (OrderedText l : textRenderer.wrapLines(Text.translatable("diktatura.ad." + ad + ".fine"), tw)) {
			if (fy > y + ph - 12) {
				break;
			}
			ctx.getMatrices().push();
			ctx.getMatrices().translate(tx, fy, 0);
			ctx.getMatrices().scale(0.75f, 0.75f, 1f);
			ctx.drawText(textRenderer, l, 0, 0, 0xC0FFFFFF, false);
			ctx.getMatrices().pop();
			fy += 8;
		}

		// Наклейка в углу.
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x + 34, y + 24, 150);
		ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-15 + MathHelper.sin(t * 0.2f) * 3));
		Text sticker = Text.translatable("diktatura.ad." + ad + ".sticker");
		int sw = textRenderer.getWidth(sticker) + 10;
		ctx.fill(-sw / 2 - 1, -8, sw / 2 + 1, 10, 0xFFB8860B);
		ctx.fill(-sw / 2, -7, sw / 2, 9, 0xFFFFD700);
		ctx.drawCenteredTextWithShadow(textRenderer, sticker, 0, -3, 0xFFD50000);
		ctx.getMatrices().pop();

		// Шапка «ГосТВ · РЕКЛАМА» и таймер.
		int left = (int) Math.max(0, (end - Ui.now() + 999) / 1000);
		Text tag = Text.translatable("diktatura.ad.tag");
		int tagW = textRenderer.getWidth(tag) + 12;
		ctx.fill(x, y - 15, x + tagW, y, 0xFFFFC107);
		ctx.drawText(textRenderer, tag, x + 6, y - 11, 0xFF000000, false);
		if (Ui.blink(800)) {
			ctx.fill(x + tagW + 6, y - 10, x + tagW + 11, y - 5, 0xFFFF1744);
		}
		ctx.drawTextWithShadow(textRenderer, Text.translatable("diktatura.ad.timer", left), x + tagW + 15, y - 11, 0xFFFFFFFF);

		// Полоса прогресса.
		float prog = MathHelper.clamp((Ui.now() - start) / (float) (end - start), 0f, 1f);
		ctx.fill(x, y + ph + 3, x + pw, y + ph + 6, 0x60FFFFFF);
		ctx.fill(x, y + ph + 3, x + (int) (pw * prog), y + ph + 6, 0xFFFFC107);

		// Бегущая строка внизу экрана.
		int by = height - 18;
		ctx.fill(0, by, width, height, 0xE0B71C1C);
		ctx.fill(0, by, width, by + 1, 0xFFFFD700);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 4; i++) {
			sb.append(Text.translatable("diktatura.ad.ticker." + i).getString()).append("   ★   ");
		}
		String ticker = sb.toString();
		int twid = textRenderer.getWidth(ticker);
		int off = (int) (t * 1.6f) % Math.max(1, twid);
		ctx.drawTextWithShadow(textRenderer, ticker, -off, by + 5, 0xFFFFFFFF);
		ctx.drawTextWithShadow(textRenderer, ticker, -off + twid, by + 5, 0xFFFFFFFF);

		// «Пропустить».
		if (skipVisible()) {
			Text label = Text.translatable(dodges >= DODGES ? "diktatura.ad.skip.give_up" : "diktatura.ad.skip");
			int bw = textRenderer.getWidth(label) + 12;
			boolean near = Math.abs(mouseX - (skipX + bw / 2)) < bw / 2 + 16 && Math.abs(mouseY - (skipY + SKIP_H / 2)) < SKIP_H / 2 + 12;
			if (near && dodges < DODGES) {
				dodges++;
				placeSkip();
				play(ModSounds.POPUP_CLOSE, 1.5f);
			}
			boolean hover = dodges >= DODGES && mouseX >= skipX && mouseX <= skipX + bw && mouseY >= skipY && mouseY <= skipY + SKIP_H;
			ctx.fill(skipX, skipY, skipX + bw, skipY + SKIP_H, hover ? 0xF0303030 : 0xE0000000);
			ctx.fill(skipX, skipY, skipX + bw, skipY + 1, dodges >= DODGES ? 0xFF4CAF50 : 0xFFFFFFFF);
			ctx.drawTextWithShadow(textRenderer, label, skipX + 6, skipY + 4, 0xFFFFFFFF);
		} else {
			int s = (int) ((SKIP_AFTER_MS - (Ui.now() - start)) / 1000) + 1;
			Text wait = Text.translatable("diktatura.ad.skip.wait", s);
			int ww = textRenderer.getWidth(wait) + 12;
			ctx.fill(x + pw - ww - 6, y + ph - 22, x + pw - 6, y + ph - 6, 0xC0000000);
			ctx.drawTextWithShadow(textRenderer, wait, x + pw - ww, y + ph - 18, 0xFFAAAAAA);
		}
	}

	private ItemStack product() {
		return switch (ad) {
			case 0 -> new ItemStack(Items.GOLD_INGOT);
			case 1 -> new ItemStack(Items.SPYGLASS);
			case 2 -> new ItemStack(Items.IRON_PICKAXE);
			case 3 -> new ItemStack(Items.EMERALD);
			case 4 -> new ItemStack(Items.ANVIL);
			default -> new ItemStack(ModItems.PARCEL);
		};
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (skipVisible() && dodges >= DODGES) {
			int bw = textRenderer.getWidth(Text.translatable("diktatura.ad.skip.give_up")) + 12;
			if (mouseX >= skipX && mouseX <= skipX + bw && mouseY >= skipY && mouseY <= skipY + SKIP_H) {
				play(ModSounds.POPUP_CLOSE, 1f);
				close();
				return true;
			}
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
