package com.diktatura.client.casino;

import com.diktatura.Diktatura;
import com.diktatura.casino.CasinoPropEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Столы госказино на GeckoLib. Рулетка и автомат «доигрывают» итог сервера расчётом: колесо замедляется,
 * шарик бежит навстречу и ложится в лунку итога; барабаны крутятся и по очереди встают на символы итога,
 * рычаг дёргается, лампа мигает. У костей видна та грань (вариант кости), которая выпала; бросок — анимация.
 */
public class CasinoPropRenderer extends GeoEntityRenderer<CasinoPropEntity> {
	private static final float SEG = (float) (Math.PI * 2 / 16);
	/** Начальная скорость колеса, рад/тик. */
	private static final float WHEEL_SPEED = 0.26f;

	public CasinoPropRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new Model());
		this.shadowRadius = 0.6f;
	}

	@Override
	protected void applyRotations(CasinoPropEntity e, MatrixStack m, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
		super.applyRotations(e, m, ageInTicks, e.getYaw(), partialTick, nativeScale);
	}

	@Override
	public boolean shouldRender(CasinoPropEntity e, Frustum frustum, double x, double y, double z) {
		return frustum.isVisible(e.getBoundingBox().expand(2.0, 2.0, 2.0));
	}

	static final class Model extends GeoModel<CasinoPropEntity> {
		@Override
		public Identifier getModelResource(CasinoPropEntity e) {
			return Diktatura.id("geo/entity/" + e.kind().model + ".geo.json");
		}

		@Override
		public Identifier getTextureResource(CasinoPropEntity e) {
			return Diktatura.id("textures/entity/" + e.kind().model + ".png");
		}

		@Override
		public Identifier getAnimationResource(CasinoPropEntity e) {
			return Diktatura.id("animations/entity/" + e.kind().model + ".animation.json");
		}

		@Override
		public void setCustomAnimations(CasinoPropEntity e, long instanceId, AnimationState<CasinoPropEntity> state) {
			NbtCompound spin = e.spin();
			float delta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
			float t = spin.contains("start") ? Math.max(0, e.getWorld().getTime() - spin.getLong("start") + delta) : -1;
			switch (e.kind()) {
				case ROULETTE -> roulette(spin, t);
				case SLOTS -> slots(spin, t);
				case DICE -> dice(spin);
			}
		}

		private GeoBone bone(String name) {
			return getAnimationProcessor().getBone(name);
		}

		private void roulette(NbtCompound spin, float t) {
			GeoBone wheel = bone("wheel");
			GeoBone orbit = bone("ball_orbit");
			GeoBone ball = bone("ball");
			if (wheel == null || orbit == null) {
				return;
			}
			float dur = spin.contains("dur") ? spin.getInt("dur") : CasinoPropEntity.ROULETTE_TICKS;
			float tau = t < 0 ? dur : Math.min(t, dur);
			// Колесо равномерно замедляется до остановки; шарик — навстречу, догоняя лунку итога к концу.
			float wheelA = WHEEL_SPEED * (tau - tau * tau / (2 * dur));
			float left = 1 - tau / dur;
			float ballRel = spin.getInt("seg") * SEG - (float) (Math.PI * 6) * left * left;
			wheel.setRotY(wheelA);
			orbit.setRotY(wheelA + ballRel);
			if (ball != null) {
				float hop = left < 0.3f && left > 0 ? Math.abs(MathHelper.sin(tau * 0.9f)) * 1.2f * (left / 0.3f) : 0;
				ball.setPosY(left > 0.3f ? 1.0f : hop);
			}
		}

		private void slots(NbtCompound spin, float t) {
			int[] reels = spin.getIntArray("reels");
			for (int i = 0; i < 3; i++) {
				// Барабан reel0 модели стоит справа, если смотреть на автомат спереди: итог читается слева направо,
				// поэтому первый символ итога — на левом барабане (reel2), он же останавливается первым.
				GeoBone reel = bone("reel" + (2 - i));
				if (reel == null) {
					continue;
				}
				int sym = reels.length == 3 ? reels[i] : i;
				float fin = (float) Math.toRadians(sym * 60.0);
				float stop = CasinoPropEntity.SLOT_STOPS[i];
				float a;
				if (t < 0 || t >= stop) {
					a = fin;
				} else {
					float k = 1 - t / stop;
					float turns = (float) (Math.PI * 2 * (3 + i * 2));
					a = fin + turns * k * k * k;
				}
				reel.setRotX(a);
			}
			GeoBone lever = bone("lever");
			if (lever != null) {
				float pull = t >= 0 && t < 12 ? MathHelper.sin((float) (t / 12 * Math.PI)) : 0;
				lever.setRotX((float) Math.toRadians(-50) * pull);
			}
			GeoBone on = bone("lamp_on");
			GeoBone off = bone("lamp_off");
			if (on != null && off != null) {
				boolean spinning = t >= 0 && t < CasinoPropEntity.SLOT_STOPS[2];
				boolean won = spin.getBoolean("win") && t >= CasinoPropEntity.SLOT_STOPS[2] && t < CasinoPropEntity.SLOT_STOPS[2] + 60;
				boolean lit = spinning ? ((int) (t / 3)) % 2 == 0 : won && ((int) (t / 5)) % 2 == 0;
				on.setHidden(!lit);
				off.setHidden(lit);
			}
		}

		private void dice(NbtCompound spin) {
			int[] dice = spin.getIntArray("dice");
			for (int i = 0; i < 4; i++) {
				int value = dice.length == 4 ? dice[i] : (i % 6) + 1;
				for (int v = 1; v <= 6; v++) {
					GeoBone b = bone("die" + i + "_" + v);
					if (b != null) {
						b.setHidden(v != value);
					}
				}
			}
		}
	}
}
