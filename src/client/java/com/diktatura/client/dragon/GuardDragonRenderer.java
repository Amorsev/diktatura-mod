package com.diktatura.client.dragon;

import com.diktatura.Diktatura;
import com.diktatura.finale.GuardDragonLook;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.EnderDragonEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Дракон госохраны: ванильный дракон, увеличенный в {@link GuardDragonLook#SCALE} раз,
 * плюс надстройка {@link GuardDragonModel} — рога, кокарда, кристаллы и повязка.
 * Кристаллы светятся цветом фазы боя и пульсируют. Обычные драконы рисуются как в ванили.
 */
public class GuardDragonRenderer extends EnderDragonEntityRenderer {
	private static final Identifier TEXTURE = Diktatura.id("textures/entity/guard_dragon.png");
	private static final Identifier GLOW = Diktatura.id("textures/entity/guard_dragon_glow.png");

	private final EnderDragonEntityRenderer.DragonEntityModel extras;

	public GuardDragonRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.extras = new EnderDragonEntityRenderer.DragonEntityModel(GuardDragonModel.create().createModel());
	}

	@Override
	public void render(EnderDragonEntity d, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light) {
		int phase = GuardDragonLook.phase(d);
		if (phase == 0) {
			super.render(d, yaw, tickDelta, m, vcp, light);
			return;
		}
		float s = GuardDragonLook.SCALE;
		// Луч от кристалла рисуем сами, без увеличения — иначе он улетел бы мимо кристалла.
		EndCrystalEntity crystal = d.connectedCrystal;
		d.connectedCrystal = null;
		m.push();
		try {
			m.scale(s, s, s);
			super.render(d, yaw, tickDelta, m, vcp, light);
			renderExtras(d, phase, tickDelta, m, vcp, light);
		} finally {
			m.pop();
			d.connectedCrystal = crystal;
		}
		if (crystal != null) {
			m.push();
			float dx = (float) (crystal.getX() - MathHelper.lerp(tickDelta, d.prevX, d.getX()));
			float dy = (float) (crystal.getY() - MathHelper.lerp(tickDelta, d.prevY, d.getY()));
			float dz = (float) (crystal.getZ() - MathHelper.lerp(tickDelta, d.prevZ, d.getZ()));
			renderCrystalBeam(dx, dy + EndCrystalEntityRenderer.getYOffset(crystal, tickDelta), dz, tickDelta, d.age, m, vcp, light);
			m.pop();
		}
	}

	/** Те же преобразования, что у ванильной модели, затем надстройка: обычный проход и свечение. */
	private void renderExtras(EnderDragonEntity d, int phase, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light) {
		m.push();
		float bodyYaw = (float) d.getSegmentProperties(7, tickDelta)[0];
		float bodyPitch = (float) (d.getSegmentProperties(5, tickDelta)[1] - d.getSegmentProperties(10, tickDelta)[1]);
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
		m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(bodyPitch * 10f));
		m.translate(0f, 0f, 1f);
		m.scale(-1f, -1f, 1f);
		m.translate(0f, -1.501f, 0f);
		extras.animateModel(d, 0f, 0f, tickDelta);

		float fade = d.ticksSinceDeath > 0 ? MathHelper.clamp(1f - (d.ticksSinceDeath + tickDelta) / 160f, 0f, 1f) : 1f;
		if (d.ticksSinceDeath > 0) {
			int a = MathHelper.floor(fade * 255f);
			if (a > 8) {
				extras.render(m, vcp.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE)), light,
					OverlayTexture.DEFAULT_UV, (a << 24) | 0xFFFFFF);
			}
		} else {
			extras.render(m, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light,
				OverlayTexture.getUv(0f, d.hurtTime > 0), -1);
		}

		// Свечение кристаллов: фиолетовое → оранжевое (ярость) → красное (последний рывок), пульсирует.
		float t = d.age + tickDelta;
		float speed = phase == 3 ? 0.35f : phase == 2 ? 0.2f : 0.1f;
		float pulse = (0.72f + 0.28f * MathHelper.sin(t * speed)) * fade;
		int rgb = phase == 3 ? 0xFF1A1A : phase == 2 ? 0xFF7A26 : 0xC768FF;
		int r = MathHelper.floor(((rgb >> 16) & 0xFF) * pulse);
		int g = MathHelper.floor(((rgb >> 8) & 0xFF) * pulse);
		int b = MathHelper.floor((rgb & 0xFF) * pulse);
		extras.render(m, vcp.getBuffer(RenderLayer.getEyes(GLOW)), light, OverlayTexture.DEFAULT_UV,
			0xFF000000 | (r << 16) | (g << 8) | b);
		m.pop();
	}
}
