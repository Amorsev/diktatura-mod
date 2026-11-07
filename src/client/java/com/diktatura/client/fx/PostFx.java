package com.diktatura.client.fx;

import com.diktatura.Diktatura;
import com.diktatura.mixin.client.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/**
 * Пост-шейдеры трипов (по мотивам референса Gr1tW4lk, MIT): «графика — картошка» (крупные пиксели
 * и мало цветов) и глюк, который каждые полсекунды–секунду меняет картинку: инверсия, зелёный экран,
 * пиксели, серость, кислотные цвета, иногда — обычная картинка, чтобы было ещё неприятнее.
 * Чужие пост-шейдеры (вид крипера в режиме наблюдателя) не трогаем: выключаем только свой.
 */
public final class PostFx {
	private static final Identifier POTATO = Diktatura.id("shaders/post/potato.json");
	private static final Identifier[] GLITCH = {
		Identifier.ofVanilla("shaders/post/invert.json"),
		Identifier.ofVanilla("shaders/post/creeper.json"),
		POTATO,
		Diktatura.id("shaders/post/desaturate.json"),
		Diktatura.id("shaders/post/acid.json"),
	};
	private static final Random RNG = Random.create();
	private static Identifier current;
	private static Identifier glitchPick;
	private static long nextSwitch;
	private static long ticks;

	private PostFx() {
	}

	public static void tick(MinecraftClient mc) {
		ticks++;
		if (mc.world == null || mc.gameRenderer == null) {
			current = null;
			return;
		}
		Identifier want = null;
		if (ClientFx.active("potato")) {
			want = POTATO;
		} else if (ClientFx.active("glitch")) {
			if (ticks >= nextSwitch) {
				glitchPick = RNG.nextInt(4) == 0 ? null : GLITCH[RNG.nextInt(GLITCH.length)];
				nextSwitch = ticks + 10 + RNG.nextInt(25);
			}
			want = glitchPick;
		}
		if (want == null ? current == null : want.equals(current)) {
			return;
		}
		try {
			if (want == null) {
				mc.gameRenderer.disablePostProcessor();
			} else {
				((GameRendererAccessor) mc.gameRenderer).diktatura$loadPostProcessor(want);
			}
			current = want;
		} catch (RuntimeException e) {
			// Шейдерпаки (Iris) могут не дать пост-эффект — тогда трип остаётся без него.
			Diktatura.LOGGER.warn("Пост-шейдер {} не загрузился: {}", want, e.toString());
			current = want;
		}
	}
}
