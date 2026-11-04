package com.diktatura.client;

import net.minecraft.client.gui.DrawContext;

/** Событие «один глаз»: левая или правая половина экрана закрашивается чёрным. */
public final class BlindOverlay {
	private BlindOverlay() {
	}

	public static void draw(DrawContext ctx, int width, int height) {
		int side = ClientState.blindSide;
		if (side == 0) {
			return;
		}
		int half = width / 2;
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 400);
		if (side == 1) {
			ctx.fill(0, 0, half, height, 0xFF000000);
		} else {
			ctx.fill(half, 0, width, height, 0xFF000000);
		}
		ctx.getMatrices().pop();
	}
}
