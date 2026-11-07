package com.diktatura.client.mimic;

import com.diktatura.Diktatura;
import com.diktatura.mimic.MimicEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Сундук-ревизор: спит — обычный сундук, проснулся — зубы, язык, глаза и ножки. Модель из референса (MIT). */
public class MimicRenderer extends MobEntityRenderer<MimicEntity, MimicRenderer.Model> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Diktatura.id("mimic"), "main");
	private static final Identifier TEXTURE = Diktatura.id("textures/entity/mimic.png");

	public MimicRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new Model(ctx.getPart(LAYER)), 0.5f);
	}

	public static void registerLayer() {
		EntityModelLayerRegistry.registerModelLayer(LAYER, MimicRenderer::texturedModelData);
	}

	@Override
	public Identifier getTexture(MimicEntity entity) {
		return TEXTURE;
	}

	public static TexturedModelData texturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		Dilation d = Dilation.NONE;
		ModelPartData base = root.addChild("base", ModelPartBuilder.create().uv(0, 0).cuboid(-7f, -10f, -7f, 14, 10, 14, d),
			ModelTransform.pivot(0f, 21f, 0f));
		base.addChild("lower_teeth", ModelPartBuilder.create().uv(34, 79).cuboid(-6f, -2f, -7f, 12, 2, 1, d)
			.uv(0, 43).cuboid(6f, -2f, -6f, 1, 2, 11, d).uv(24, 43).cuboid(-7f, -2f, -6f, 1, 2, 11, d), ModelTransform.pivot(0f, -10f, 0f));
		base.addChild("tongue", ModelPartBuilder.create().uv(0, 69).cuboid(-2.5f, -0.5f, -9f, 5, 1, 9, d), ModelTransform.pivot(0f, -10.5f, 3f));
		ModelPartData lid = root.addChild("lid", ModelPartBuilder.create().uv(0, 24).cuboid(-7f, -5f, -14f, 14, 5, 14, d),
			ModelTransform.pivot(0f, 11f, 7f));
		lid.addChild("lock", ModelPartBuilder.create().uv(28, 69).cuboid(-1f, -1f, -1f, 2, 4, 1, d), ModelTransform.pivot(0f, -2f, -14f));
		lid.addChild("upper_teeth", ModelPartBuilder.create().uv(0, 84).cuboid(-6f, 0f, -14f, 12, 2, 1, d)
			.uv(0, 56).cuboid(6f, 0f, -13f, 1, 2, 11, d).uv(24, 56).cuboid(-7f, 0f, -13f, 1, 2, 11, d), ModelTransform.pivot(0f, 0f, 0f));
		lid.addChild("eyes", ModelPartBuilder.create().uv(8, 79).cuboid(-5f, -1f, -12f, 10, 1, 3, d), ModelTransform.pivot(0f, 0f, 0f));
		root.addChild("leg_fl", ModelPartBuilder.create().uv(34, 69).cuboid(-1f, 0f, -1f, 2, 3, 2, d), ModelTransform.pivot(-5f, 21f, -5f));
		root.addChild("leg_fr", ModelPartBuilder.create().uv(42, 69).cuboid(-1f, 0f, -1f, 2, 3, 2, d), ModelTransform.pivot(5f, 21f, -5f));
		root.addChild("leg_bl", ModelPartBuilder.create().uv(50, 69).cuboid(-1f, 0f, -1f, 2, 3, 2, d), ModelTransform.pivot(-5f, 21f, 5f));
		root.addChild("leg_br", ModelPartBuilder.create().uv(0, 79).cuboid(-1f, 0f, -1f, 2, 3, 2, d), ModelTransform.pivot(5f, 21f, 5f));
		return TexturedModelData.of(data, 64, 128);
	}

	public static class Model extends SinglePartEntityModel<MimicEntity> {
		private final ModelPart root;
		private final ModelPart base;
		private final ModelPart lid;
		private final ModelPart tongue;
		private final ModelPart lowerTeeth;
		private final ModelPart upperTeeth;
		private final ModelPart eyes;
		private final ModelPart[] legs;

		public Model(ModelPart root) {
			super(RenderLayer::getEntityCutoutNoCull);
			this.root = root;
			this.base = root.getChild("base");
			this.lid = root.getChild("lid");
			this.tongue = base.getChild("tongue");
			this.lowerTeeth = base.getChild("lower_teeth");
			this.upperTeeth = lid.getChild("upper_teeth");
			this.eyes = lid.getChild("eyes");
			this.legs = new ModelPart[] {root.getChild("leg_fl"), root.getChild("leg_fr"), root.getChild("leg_bl"), root.getChild("leg_br")};
		}

		@Override
		public ModelPart getPart() {
			return root;
		}

		@Override
		public void setAngles(MimicEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
			root.traverse().forEach(ModelPart::resetTransform);
			boolean awake = e.isAwake();
			for (ModelPart l : legs) {
				l.visible = awake;
			}
			tongue.visible = awake;
			lowerTeeth.visible = awake;
			upperTeeth.visible = awake;
			eyes.visible = awake;
			if (!awake) {
				// Притворяется обычным сундуком: ножки спрятаны, крышка закрыта.
				base.pivotY += 3;
				lid.pivotY += 3;
				return;
			}
			float open = 0.45f + MathHelper.sin(age * 0.25f) * 0.12f;
			if (e.chompTicks > 0) {
				float k = e.chompTicks / 10f;
				open = MathHelper.abs(MathHelper.sin(k * MathHelper.PI)) * 1.1f;
			}
			lid.pitch = -open;
			tongue.pitch = -0.2f + MathHelper.sin(age * 0.5f) * 0.35f;
			tongue.yaw = MathHelper.sin(age * 0.3f) * 0.25f;
			float walk = limbAngle * 1.5f;
			for (int i = 0; i < legs.length; i++) {
				legs[i].pitch = MathHelper.cos(walk + (i % 2 == 0 ? 0 : MathHelper.PI)) * 0.9f * limbDistance;
			}
		}
	}
}
