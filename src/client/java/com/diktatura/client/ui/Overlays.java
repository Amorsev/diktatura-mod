package com.diktatura.client.ui;

import com.diktatura.network.UiPayloads;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;

/**
 * Полноэкранные плакаты: «РАЗЫСКИВАЕТСЯ» при аресте, штамп на экране смерти
 * и «государственная загрузка» при смене измерения. Управление не перехватывают.
 */
public final class Overlays {
	private static final long WANTED_MS = 3800;
	private static final long LOADING_MS = 2400;
	private static final int PAPER = 0xFFE9DDB5;
	private static final int INK = 0xFF2A2018;
	private static final int STAMP = 0xFFB01818;

	private static Text wantedReason;
	private static int wantedSeconds;
	private static long wantedStart = -1;

	private static RegistryKey<World> lastDim;
	private static RegistryKey<World> loadingDim;
	private static long loadingStart = -1;

	private Overlays() {
	}

	public static void wanted(UiPayloads.Wanted p) {
		wantedReason = p.reason();
		wantedSeconds = p.seconds();
		wantedStart = Ui.now();
	}

	/** Вызывается каждый клиентский тик: ловим смену измерения. */
	public static void tick(MinecraftClient mc) {
		if (mc.world == null) {
			lastDim = null;
			return;
		}
		RegistryKey<World> dim = mc.world.getRegistryKey();
		if (lastDim != null && !lastDim.equals(dim)) {
			loadingDim = dim;
			loadingStart = Ui.now();
		}
		lastDim = dim;
	}

	public static void clear() {
		wantedStart = -1;
		loadingStart = -1;
		lastDim = null;
	}

	public static void render(DrawContext ctx, int w, int h) {
		renderLoading(ctx, w, h);
		renderWanted(ctx, w, h);
	}

	// ------------------------------------------------------------ «РАЗЫСКИВАЕТСЯ»

	private static void renderWanted(DrawContext ctx, int w, int h) {
		if (wantedStart < 0) {
			return;
		}
		long t = Ui.now() - wantedStart;
		if (t >= WANTED_MS) {
			wantedStart = -1;
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		TextRenderer tr = mc.textRenderer;
		float a = Math.min(Math.min(1f, t / 250f), Math.min(1f, (WANTED_MS - t) / 500f));

		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 450);
		ctx.fill(0, 0, w, h, Ui.alpha(0xB0000000, a));

		int pw = Math.min(200, w - 40);
		int ph = Math.min(200, h - 40);
		int px = (w - pw) / 2;
		int py = (h - ph) / 2;
		ctx.fill(px, py, px + pw, py + ph, Ui.alpha(PAPER, a));
		ctx.fill(px + 4, py + 4, px + pw - 4, py + 5, Ui.alpha(INK, a));
		ctx.fill(px + 4, py + ph - 5, px + pw - 4, py + ph - 4, Ui.alpha(INK, a));

		ctx.getMatrices().push();
		ctx.getMatrices().translate(w / 2f, py + 10, 0);
		ctx.getMatrices().scale(2f, 2f, 1f);
		Ui.centered(ctx, tr, Text.translatable("diktatura.ui.wanted.title"), 0, 0, INK, a);
		ctx.getMatrices().pop();

		int face = Math.min(64, ph - 110);
		if (face >= 16 && mc.player != null && a > 0.3f) {
			PlayerSkinDrawer.draw(ctx, mc.player.getSkinTextures(), w / 2 - face / 2, py + 34, face);
		}
		int ty = py + 40 + Math.max(face, 0);
		if (mc.player != null) {
			Ui.centered(ctx, tr, mc.player.getName(), w / 2, ty, INK, a);
		}
		Ui.centered(ctx, tr, Text.translatable("diktatura.ui.wanted.for"), w / 2, ty + 14, 0xFF5A4A38, a);
		if (wantedReason != null) {
			int rw = Math.min(tr.getWidth(wantedReason), pw - 20);
			Ui.textFit(ctx, tr, wantedReason, w / 2 - rw / 2, ty + 25, pw - 20, INK, a);
		}
		Ui.centered(ctx, tr, Text.translatable("diktatura.ui.wanted.sentence", wantedSeconds), w / 2, ty + 40, INK, a);

		// Штамп «ЗАДЕРЖАН» — появляется с задержкой и «ударом».
		if (t > 900) {
			float hit = Math.min(1f, (t - 900) / 120f);
			float s = 2.6f - 0.8f * hit;
			ctx.getMatrices().push();
			ctx.getMatrices().translate(px + pw * 0.68f, py + ph * 0.62f, 10);
			ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-14));
			ctx.getMatrices().scale(s, s, 1f);
			Text stamp = Text.translatable("diktatura.ui.wanted.stamp");
			int sw = tr.getWidth(stamp);
			ctx.fill(-sw / 2 - 3, -3, sw / 2 + 3, -2, Ui.alpha(STAMP, a));
			ctx.fill(-sw / 2 - 3, 9, sw / 2 + 3, 10, Ui.alpha(STAMP, a));
			ctx.drawText(tr, stamp, -sw / 2, 0, Ui.alpha(STAMP, a), false);
			ctx.getMatrices().pop();
		}
		ctx.getMatrices().pop();
	}

	/** Штамп на экране смерти. */
	public static void deathStamp(DrawContext ctx, int w, int h) {
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(w * 0.78f, h * 0.18f, 400);
		ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(12));
		ctx.getMatrices().scale(1.8f, 1.8f, 1f);
		Text title = Text.translatable("diktatura.ui.wanted.title");
		Text sub = Text.translatable("diktatura.ui.death.reason");
		int tw = Math.max(tr.getWidth(title), tr.getWidth(sub) / 2);
		ctx.fill(-tw / 2 - 4, -4, tw / 2 + 4, -3, STAMP);
		ctx.fill(-tw / 2 - 4, 10, tw / 2 + 4, 11, STAMP);
		ctx.drawText(tr, title, -tr.getWidth(title) / 2, 0, STAMP, false);
		ctx.getMatrices().scale(0.5f, 0.5f, 1f);
		ctx.drawText(tr, sub, -tr.getWidth(sub) / 2, 26, 0xFFFF8080, true);
		ctx.getMatrices().pop();
	}

	// ------------------------------------------------------------ госзагрузка

	private static void renderLoading(DrawContext ctx, int w, int h) {
		if (loadingStart < 0) {
			return;
		}
		long t = Ui.now() - loadingStart;
		if (t >= LOADING_MS) {
			loadingStart = -1;
			return;
		}
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		float a = Math.min(1f, (LOADING_MS - t) / 400f);
		float progress = Math.min(1f, t / (LOADING_MS - 500f));

		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 440);
		ctx.fill(0, 0, w, h, Ui.alpha(0xF0100C08, a));
		int bw = Math.min(220, w - 40);
		int bx = (w - bw) / 2;
		int cy = h / 2;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(w / 2f, cy - 40, 0);
		ctx.getMatrices().scale(1.5f, 1.5f, 1f);
		Ui.centered(ctx, tr, Text.translatable("diktatura.ui.loading.title"), 0, 0, Ui.GOLD, a);
		ctx.getMatrices().pop();

		String path = loadingDim == null ? "unknown" : loadingDim.getValue().getPath();
		Text dimName = Text.translatableWithFallback("diktatura.ui.loading.dim." + path, path);
		Ui.centered(ctx, tr, Text.translatable("diktatura.ui.loading.to", dimName), w / 2, cy - 18, Ui.TEXT, a);

		Ui.bar(ctx, bx, cy, bw, 6, Math.round(progress * 100), Ui.alpha(Ui.GOLD, a));
		int step = Math.min(3, (int) (progress * 4));
		Ui.centered(ctx, tr, Text.translatable("diktatura.ui.loading.step." + step), w / 2, cy + 12, Ui.MUTED, a);
		ctx.getMatrices().pop();
	}
}
