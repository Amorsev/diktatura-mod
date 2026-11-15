package com.diktatura.client.mimic;

import com.diktatura.Diktatura;
import com.diktatura.mimic.MimicEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Сундук-ревизор на GeckoLib ({@code geo/entity/mimic.geo.json}): спит — обычный сундук, проснулся — зубы, язык,
 * глаза и ножки, пасть дышит, ходит, кусает. Модель из референса (MIT), анимации — {@code blockbench/gl_anims.py}.
 */
public class MimicRenderer extends GeoEntityRenderer<MimicEntity> {
	public MimicRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new DefaultedEntityGeoModel<>(Diktatura.id("mimic")));
		this.shadowRadius = 0.5f;
	}
}
