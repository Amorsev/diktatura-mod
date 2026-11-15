package com.diktatura.client.transport;

import com.diktatura.Diktatura;
import com.diktatura.transport.TaxiClass;
import com.diktatura.transport.TaxiEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

/**
 * Кареты госТакси (модели из Blockbench, {@code blockbench/gen_taxi.py}). Колёса катятся по пройденному
 * пути, лошади шагают рысью (диагональные пары ног вместе), кивают головой и помахивают хвостом;
 * телега-«эконом» ещё и трясётся на ухабах.
 */
public class TaxiRenderer extends EntityRenderer<TaxiEntity> {
	public static final EntityModelLayer[] LAYERS = {
		new EntityModelLayer(Diktatura.id("taxi_economy"), "main"),
		new EntityModelLayer(Diktatura.id("taxi_comfort"), "main"),
		new EntityModelLayer(Diktatura.id("taxi_business"), "main"),
	};
	private static final String[] HORSES = {"horse", "horse_r", "horse_l", "horse_ar", "horse_al", "horse_br", "horse_bl"};
	/** Радиусы колёс в пикселях: передние / задние (у «эконома» передних нет). */
	private static final float[][] WHEEL_RADIUS = {{6f, 6f}, {5f, 6f}, {5f, 7f}};

	private final Rig[] rigs = new Rig[3];

	/** Модель одного класса и её подвижные части. */
	private static final class Rig {
		final ModelPart root;
		final Identifier texture;
		final List<ModelPart> wheels = new ArrayList<>();
		final List<Float> wheelRadius = new ArrayList<>();
		/** Ноги рысью: пара A (правая передняя + левая задняя) и пара B. */
		final List<ModelPart> legsA = new ArrayList<>();
		final List<ModelPart> legsB = new ArrayList<>();
		final List<ModelPart> necks = new ArrayList<>();
		final List<Float> neckPitch = new ArrayList<>();
		final List<ModelPart> tails = new ArrayList<>();
		final List<Float> tailPitch = new ArrayList<>();

		Rig(ModelPart root, TaxiClass c) {
			this.root = root;
			this.texture = Diktatura.id("textures/entity/taxi_" + c.id + ".png");
			ModelPart body = root.getChild("body");
			float[] r = WHEEL_RADIUS[c.ordinal()];
			for (String side : new String[] {"left", "right"}) {
				addWheel(body, "wheel_front_" + side, r[0]);
				addWheel(body, "wheel_rear_" + side, r[1]);
			}
			for (String h : HORSES) {
				if (!body.hasChild(h + "_neck")) {
					continue;
				}
				legsA.add(body.getChild(h + "_leg_fr"));
				legsA.add(body.getChild(h + "_leg_bl"));
				legsB.add(body.getChild(h + "_leg_fl"));
				legsB.add(body.getChild(h + "_leg_br"));
				ModelPart neck = body.getChild(h + "_neck");
				necks.add(neck);
				neckPitch.add(neck.pitch);
				ModelPart tail = body.getChild(h + "_tail");
				tails.add(tail);
				tailPitch.add(tail.pitch);
			}
		}

		private void addWheel(ModelPart body, String name, float radiusPx) {
			if (body.hasChild(name)) {
				wheels.add(body.getChild(name));
				wheelRadius.add(radiusPx / 16f);
			}
		}

		void pose(float dist, float amount, float age) {
			for (int i = 0; i < wheels.size(); i++) {
				wheels.get(i).pitch = dist / wheelRadius.get(i);
			}
			float swing = MathHelper.cos(dist * 2.4f) * 0.55f * amount;
			for (ModelPart leg : legsA) {
				leg.pitch = swing;
			}
			for (ModelPart leg : legsB) {
				leg.pitch = -swing;
			}
			for (int i = 0; i < necks.size(); i++) {
				// Кивок в такт шагу, у каждой лошади чуть со сдвигом.
				necks.get(i).pitch = neckPitch.get(i) + MathHelper.sin(dist * 4.8f + i) * 0.06f * amount
					+ MathHelper.sin(age * 0.05f + i * 1.7f) * 0.02f;
			}
			for (int i = 0; i < tails.size(); i++) {
				ModelPart tail = tails.get(i);
				tail.pitch = tailPitch.get(i) - 0.25f * amount;
				tail.yaw = MathHelper.sin(age * 0.12f + i * 2.1f) * 0.2f;
			}
		}
	}

	public TaxiRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		for (TaxiClass c : TaxiClass.values()) {
			rigs[c.ordinal()] = new Rig(ctx.getPart(LAYERS[c.ordinal()]), c);
		}
		this.shadowRadius = 1.3f;
	}

	@Override
	public boolean shouldRender(TaxiEntity e, Frustum frustum, double x, double y, double z) {
		// Модель с лошадьми длиннее хитбокса (до ~6 блоков) — не отсекать, пока видна хоть часть.
		return frustum.isVisible(e.getBoundingBox().expand(4.5, 2.5, 4.5));
	}

	@Override
	public void render(TaxiEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light) {
		TaxiClass c = e.taxiClass();
		Rig rig = rigs[c.ordinal()];
		float dist = MathHelper.lerp(tickDelta, e.prevDistance, e.distance);
		float speed = MathHelper.clamp((e.distance - e.prevDistance) * 2.5f, 0f, 1f);
		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, e.prevYaw, e.getYaw());
		float age = e.age + tickDelta;

		m.push();
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));
		if (c == TaxiClass.ECONOMY) {
			// Телегу трясёт на ухабах.
			m.translate(0f, Math.abs(MathHelper.sin(dist * 3.1f)) * 0.04f * speed, 0f);
			m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(dist * 1.7f) * 1.5f * speed));
		}
		m.scale(-1f, -1f, 1f);
		m.translate(0f, -1.501f, 0f);
		rig.pose(dist, Math.max(speed, 0.0f), age);
		rig.root.render(m, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(rig.texture)), light, OverlayTexture.DEFAULT_UV);
		m.pop();
		super.render(e, yaw, tickDelta, m, vcp, light);
	}

	@Override
	public Identifier getTexture(TaxiEntity entity) {
		return Diktatura.id("textures/entity/taxi_" + entity.taxiClass().id + ".png");
	}
}
