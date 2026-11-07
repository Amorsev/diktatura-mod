package com.diktatura.client.ore;

import com.diktatura.Diktatura;
import com.diktatura.ore.RunawayOreEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Руда на ножках с машущими ручками и испуганным лицом (по мотивам референса). */
public class RunawayOreRenderer extends MobEntityRenderer<RunawayOreEntity, RunawayOreRenderer.Model> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Diktatura.id("runaway_ore"), "main");
	private static final Identifier TEXTURE = Diktatura.id("textures/entity/runaway_ore.png");

	public RunawayOreRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new Model(ctx.getPart(LAYER)), 0.45f);
		this.addFeature(new BlockBody(this));
	}

	public static void registerLayer() {
		EntityModelLayerRegistry.registerModelLayer(LAYER, RunawayOreRenderer::texturedModelData);
	}

	static TexturedModelData texturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		Dilation d = Dilation.NONE;
		root.addChild("left_leg", ModelPartBuilder.create().uv(0, 0).cuboid(-1, 0, -1, 2, 5, 2, d)
			.uv(16, 0).cuboid(-1.5f, 4, -3, 3, 2, 4, d), ModelTransform.pivot(3, 18, 0));
		root.addChild("right_leg", ModelPartBuilder.create().uv(8, 0).cuboid(-1, 0, -1, 2, 5, 2, d)
			.uv(30, 0).cuboid(-1.5f, 4, -3, 3, 2, 4, d), ModelTransform.pivot(-3, 18, 0));
		root.addChild("left_arm", ModelPartBuilder.create().uv(0, 7).cuboid(0, -1, -1, 5, 2, 2, d)
			.uv(44, 0).cuboid(5, -1.5f, -1.5f, 2, 3, 3, d), ModelTransform.pivot(6, 12, 0));
		root.addChild("right_arm", ModelPartBuilder.create().uv(14, 7).cuboid(-5, -1, -1, 5, 2, 2, d)
			.uv(54, 0).cuboid(-7, -1.5f, -1.5f, 2, 3, 3, d), ModelTransform.pivot(-6, 12, 0));
		root.addChild("face", ModelPartBuilder.create().uv(28, 7).cuboid(1, -2.5f, -1, 3, 3, 1, d)
			.uv(36, 7).cuboid(-4, -2.5f, -1, 3, 3, 1, d)
			.uv(44, 7).cuboid(-2, 2, -1, 4, 3, 1, d), ModelTransform.pivot(0, 11, -6));
		return TexturedModelData.of(data, 64, 32);
	}

	@Override
	public Identifier getTexture(RunawayOreEntity entity) {
		return TEXTURE;
	}

	static float bob(float limbAngle, float limbDistance) {
		return Math.abs(MathHelper.sin(limbAngle * 1.3f)) * limbDistance * 1.8f;
	}

	public static class Model extends SinglePartEntityModel<RunawayOreEntity> {
		private final ModelPart root;
		private final ModelPart leftLeg;
		private final ModelPart rightLeg;
		private final ModelPart leftArm;
		private final ModelPart rightArm;
		private final ModelPart face;

		public Model(ModelPart root) {
			this.root = root;
			this.leftLeg = root.getChild("left_leg");
			this.rightLeg = root.getChild("right_leg");
			this.leftArm = root.getChild("left_arm");
			this.rightArm = root.getChild("right_arm");
			this.face = root.getChild("face");
		}

		@Override
		public ModelPart getPart() {
			return root;
		}

		@Override
		public void setAngles(RunawayOreEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
			root.traverse().forEach(ModelPart::resetTransform);
			float run = limbAngle * 1.3f;
			leftLeg.pitch = MathHelper.cos(run) * 1.4f * limbDistance;
			rightLeg.pitch = MathHelper.cos(run + MathHelper.PI) * 1.4f * limbDistance;
			float b = -bob(limbAngle, limbDistance);
			float flail = MathHelper.sin(age * 0.9f);
			leftArm.roll = -0.9f - flail * 0.6f;
			rightArm.roll = 0.9f + flail * 0.6f;
			leftArm.pivotY += b;
			rightArm.pivotY += b;
			face.pivotY += b;
			face.pivotX = MathHelper.sin(age * 0.5f) * 0.3f;
		}
	}

	/** Сам блок руды на ножках. */
	static class BlockBody extends FeatureRenderer<RunawayOreEntity, Model> {
		BlockBody(FeatureRendererContext<RunawayOreEntity, Model> ctx) {
			super(ctx);
		}

		@Override
		public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, RunawayOreEntity e, float limbAngle,
		                   float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
			matrices.push();
			float b = bob(limbAngle, limbDistance);
			matrices.translate(0, (18 - b) / 16f, 0);
			matrices.multiply(RotationAxis.POSITIVE_Z.rotation(MathHelper.sin(age * 0.4f) * 0.06f));
			matrices.scale(-0.75f, -0.75f, 0.75f);
			matrices.translate(-0.5, 0, -0.5);
			MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(e.getBlockState(), matrices, vcp, light,
				LivingEntityRenderer.getOverlay(e, 0f));
			matrices.pop();
		}
	}
}
