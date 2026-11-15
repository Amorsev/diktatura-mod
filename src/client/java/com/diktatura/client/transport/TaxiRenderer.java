package com.diktatura.client.transport;

import com.diktatura.Diktatura;
import com.diktatura.transport.TaxiEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Кареты госТакси на GeckoLib: модель, текстура и анимации выбираются по классу кареты
 * ({@code geo/entity/taxi_<класс>.geo.json}, {@code animations/entity/taxi_<класс>.animation.json}).
 * Рысь, колёса, кучер и тряска телеги — анимации из Blockbench ({@code blockbench/gl_anims.py}).
 */
public class TaxiRenderer extends GeoEntityRenderer<TaxiEntity> {
	public TaxiRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new Model());
		this.shadowRadius = 1.3f;
	}

	/**
	 * GeckoLib поворачивает по направлению только мобов (LivingEntity), а повозку всегда рисовал бы «на юг».
	 * Подставляем её собственный поворот — тот же, что у пассажира и заключённого в упряжке.
	 */
	@Override
	protected void applyRotations(TaxiEntity e, MatrixStack m, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
		super.applyRotations(e, m, ageInTicks, MathHelper.lerpAngleDegrees(partialTick, e.prevYaw, e.getYaw()), partialTick, nativeScale);
	}

	@Override
	public boolean shouldRender(TaxiEntity e, Frustum frustum, double x, double y, double z) {
		// Модель с лошадьми длиннее хитбокса (до ~6 блоков) — не отсекать, пока видна хоть часть.
		return frustum.isVisible(e.getBoundingBox().expand(4.5, 2.5, 4.5));
	}

	/** Модель GeckoLib, своя для каждого класса кареты. */
	static final class Model extends GeoModel<TaxiEntity> {
		@Override
		public Identifier getModelResource(TaxiEntity e) {
			return Diktatura.id("geo/entity/taxi_" + e.taxiClass().id + ".geo.json");
		}

		@Override
		public Identifier getTextureResource(TaxiEntity e) {
			return Diktatura.id("textures/entity/taxi_" + e.taxiClass().id + ".png");
		}

		@Override
		public Identifier getAnimationResource(TaxiEntity e) {
			return Diktatura.id("animations/entity/taxi_" + e.taxiClass().id + ".animation.json");
		}
	}
}
