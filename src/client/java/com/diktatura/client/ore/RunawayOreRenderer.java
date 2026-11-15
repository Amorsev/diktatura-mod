package com.diktatura.client.ore;

import com.diktatura.Diktatura;
import com.diktatura.ore.RunawayOreEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Руда на ножках с машущими ручками и испуганным лицом — GeckoLib ({@code geo/entity/runaway_ore.geo.json}).
 * Тело — сам блок руды (0.75 блока) на кости body: подпрыгивает и качается вместе с руками и лицом.
 */
public class RunawayOreRenderer extends GeoEntityRenderer<RunawayOreEntity> {
	public RunawayOreRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new DefaultedEntityGeoModel<>(Diktatura.id("runaway_ore")));
		this.shadowRadius = 0.45f;
		addRenderLayer(new BlockBody(this));
	}

	/** Блок руды: низ — в опоре кости body (6 пикселей над землёй), по центру. */
	static final class BlockBody extends GeoRenderLayer<RunawayOreEntity> {
		BlockBody(RunawayOreRenderer renderer) {
			super(renderer);
		}

		@Override
		public void renderForBone(MatrixStack m, RunawayOreEntity e, GeoBone bone, RenderLayer renderType, VertexConsumerProvider vcp,
		                          VertexConsumer buffer, float tickDelta, int light, int overlay) {
			if (!"body".equals(bone.getName())) {
				return;
			}
			m.push();
			RenderUtil.translateToPivotPoint(m, bone);
			m.scale(0.75f, 0.75f, 0.75f);
			m.translate(-0.5f, 0f, -0.5f);
			MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(e.getBlockState(), m, vcp, light,
				LivingEntityRenderer.getOverlay(e, 0f));
			m.pop();
		}
	}
}
