package com.diktatura.client.transport;

import com.diktatura.Diktatura;
import com.diktatura.client.npc.NpcModels;
import com.diktatura.transport.WagonEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Повозка госперевозки: телега из Blockbench (колёса крутятся по пройденному пути) и впряжённый
 * в оглобли гражданин-заключённый (модель prisoner): бежит, наклонившись вперёд, держит оглобли.
 */
public class WagonRenderer extends EntityRenderer<WagonEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Diktatura.id("wagon"), "main");
	private static final Identifier TEXTURE = Diktatura.id("textures/entity/wagon.png");
	private static final Identifier PRISONER_TEXTURE = NpcModels.texture("prisoner");
	/** Радиус колеса в блоках (12 пикселей в диаметре). */
	private static final float WHEEL_RADIUS = 6f / 16f;
	/** Где стоит заключённый: между оглоблями, впереди кузова (в пикселях модели). */
	private static final float PRISONER_Z = -21f / 16f;

	private final ModelPart root;
	private final ModelPart wheelLeft;
	private final ModelPart wheelRight;
	private final BipedEntityModel<LivingEntity> prisoner;

	public WagonRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.root = ctx.getPart(LAYER);
		this.wheelLeft = root.getChild("wheel_left");
		this.wheelRight = root.getChild("wheel_right");
		this.prisoner = NpcModels.model(ctx, "prisoner");
		this.shadowRadius = 0.9f;
	}

	@Override
	public void render(WagonEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light) {
		float dist = MathHelper.lerp(tickDelta, e.prevDistance, e.distance);
		float speed = MathHelper.clamp((e.distance - e.prevDistance) * 3f, 0f, 1f);
		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, e.prevYaw, e.getYaw());

		m.push();
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));
		m.scale(-1f, -1f, 1f);
		m.translate(0f, -1.501f, 0f);

		// Телега: колёса катятся вперёд.
		float spin = dist / WHEEL_RADIUS;
		wheelLeft.pitch = spin;
		wheelRight.pitch = spin;
		root.render(m, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV);

		// Заключённый: бег на месте, ноги — по пройденному пути, руки держат оглобли.
		m.push();
		m.translate(0f, 0f, PRISONER_Z);
		// Наклон вперёд вокруг ступней (ступни — на 24 пикселя ниже точки отсчёта модели).
		m.translate(0f, 1.5f, 0f);
		m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(8f + 10f * speed));
		m.translate(0f, -1.5f, 0f);
		poseRunner(dist * 2.2f, Math.max(speed, 0.08f), e.age + tickDelta);
		prisoner.render(m, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(PRISONER_TEXTURE)), light, OverlayTexture.DEFAULT_UV);
		m.pop();

		m.pop();
		super.render(e, yaw, tickDelta, m, vcp, light);
	}

	private void poseRunner(float phase, float amount, float age) {
		float swing = MathHelper.cos(phase) * 1.1f * amount;
		prisoner.rightLeg.pitch = swing;
		prisoner.leftLeg.pitch = -swing;
		prisoner.rightLeg.yaw = 0f;
		prisoner.leftLeg.yaw = 0f;
		// Руки опущены вдоль тела к оглоблям и чуть качаются в такт шагам.
		float sway = MathHelper.sin(phase) * 0.12f * amount;
		prisoner.rightArm.pitch = -0.1f + sway;
		prisoner.leftArm.pitch = -0.1f - sway;
		prisoner.rightArm.roll = 0.12f;
		prisoner.leftArm.roll = -0.12f;
		prisoner.rightArm.yaw = 0f;
		prisoner.leftArm.yaw = 0f;
		prisoner.body.pitch = 0f;
		// Голова поднята: смотрит вперёд, тяжело дышит.
		prisoner.head.pitch = -0.25f + MathHelper.sin(age * 0.3f) * 0.04f;
		prisoner.head.yaw = 0f;
		prisoner.hat.copyTransform(prisoner.head);
	}

	@Override
	public Identifier getTexture(WagonEntity entity) {
		return TEXTURE;
	}
}
