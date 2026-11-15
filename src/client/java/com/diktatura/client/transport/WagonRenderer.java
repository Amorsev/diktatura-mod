package com.diktatura.client.transport;

import com.diktatura.Diktatura;
import com.diktatura.transport.WagonEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Повозка госперевозки — модель GeckoLib вместе с впряжённым в оглобли заключённым ({@code geo/entity/wagon.geo.json}):
 * колёса катятся, заключённый бежит, наклонившись вперёд, и держит оглобли (анимация «roll», скорость — по ходу повозки).
 */
public class WagonRenderer extends GeoEntityRenderer<WagonEntity> {
	public WagonRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new DefaultedEntityGeoModel<>(Diktatura.id("wagon")));
		this.shadowRadius = 0.9f;
	}

	/**
	 * GeckoLib поворачивает по направлению только мобов (LivingEntity), а повозку всегда рисовал бы «на юг».
	 * Подставляем её собственный поворот — тот же, что у пассажира.
	 */
	@Override
	protected void applyRotations(WagonEntity e, MatrixStack m, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
		super.applyRotations(e, m, ageInTicks, MathHelper.lerpAngleDegrees(partialTick, e.prevYaw, e.getYaw()), partialTick, nativeScale);
	}
}
