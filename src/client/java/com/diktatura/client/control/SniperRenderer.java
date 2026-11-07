package com.diktatura.client.control;

import com.diktatura.control.SniperSkeletonEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.SkeletonEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Обычный скелет, но с красным лазерным прицелом. */
public class SniperRenderer extends SkeletonEntityRenderer<SniperSkeletonEntity> {
	public SniperRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(SniperSkeletonEntity entity, Frustum frustum, double x, double y, double z) {
		if (entity.getAimTargetId() >= 0) {
			return true;
		}
		return super.shouldRender(entity, frustum, x, y, z);
	}

	@Override
	public void render(SniperSkeletonEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
		super.render(entity, yaw, tickDelta, matrices, vcp, light);
		SniperSkeletonEntity s = entity;
		int targetId = s.getAimTargetId();
		if (targetId < 0) {
			return;
		}
		Entity target = entity.getWorld().getEntityById(targetId);
		if (target == null) {
			return;
		}
		float aim = Math.min(1f, s.getAim() / (float) SniperSkeletonEntity.AIM_TIME);
		Vec3d self = entity.getLerpedPos(tickDelta);
		Vec3d from = new Vec3d(0, entity.getStandingEyeHeight() - 0.1, 0).add(entity.getRotationVec(tickDelta).multiply(0.4));
		Vec3d to = target.getLerpedPos(tickDelta).add(0, target.getHeight() * 0.55, 0).subtract(self);
		float alpha = 0.25f + aim * 0.7f;
		if (aim > 0.8f && (entity.age / 2) % 2 == 0) {
			alpha = 1f;
		}
		VertexConsumer vc = vcp.getBuffer(RenderLayer.getLightning());
		drawBeam(matrices, vc, from, to, 0.015f + aim * 0.02f, alpha);
		drawBeam(matrices, vc, to.add(-0.08, 0, 0), to.add(0.08, 0, 0), 0.08f, alpha);
	}

	static void drawBeam(MatrixStack matrices, VertexConsumer vc, Vec3d a, Vec3d b, float w, float alpha) {
		Matrix4f m = matrices.peek().getPositionMatrix();
		Vec3d dir = b.subtract(a);
		Vec3d side = dir.crossProduct(new Vec3d(0, 1, 0));
		if (side.lengthSquared() < 1e-6) {
			side = new Vec3d(1, 0, 0);
		}
		side = side.normalize().multiply(w);
		Vec3d up = dir.crossProduct(side).normalize().multiply(w);
		quad(vc, m, a, b, side, alpha);
		quad(vc, m, a, b, up, alpha);
	}

	private static void quad(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, Vec3d off, float alpha) {
		v(vc, m, a.subtract(off), alpha);
		v(vc, m, a.add(off), alpha);
		v(vc, m, b.add(off), alpha);
		v(vc, m, b.subtract(off), alpha);
		// Обратная сторона.
		v(vc, m, b.subtract(off), alpha);
		v(vc, m, b.add(off), alpha);
		v(vc, m, a.add(off), alpha);
		v(vc, m, a.subtract(off), alpha);
	}

	private static void v(VertexConsumer vc, Matrix4f m, Vec3d p, float alpha) {
		vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(1f, 0.05f, 0.05f, alpha);
	}
}
