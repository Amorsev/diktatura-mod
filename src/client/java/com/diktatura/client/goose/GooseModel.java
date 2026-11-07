package com.diktatura.client.goose;

import com.diktatura.goose.GooseEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/** Модель гуся (по мотивам референса): длинная шея, раскрывающийся клюв, крылья-хлопалки. */
public class GooseModel<T extends GooseEntity> extends SinglePartEntityModel<T> {
	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart tail;
	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart leftWing;
	private final ModelPart rightWing;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;

	public GooseModel(ModelPart root) {
		this.root = root;
		this.body = root.getChild("body");
		this.tail = body.getChild("tail");
		this.neck = root.getChild("neck");
		this.head = neck.getChild("head");
		this.jaw = head.getChild("jaw");
		this.leftWing = root.getChild("left_wing");
		this.rightWing = root.getChild("right_wing");
		this.leftLeg = root.getChild("left_leg");
		this.rightLeg = root.getChild("right_leg");
	}

	public static TexturedModelData texturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		Dilation d = Dilation.NONE;
		ModelPartData body = root.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-3.5f, -3.0f, -5.0f, 7, 6, 11, d),
			ModelTransform.pivot(0.0f, 17.0f, 0.0f));
		body.addChild("tail", ModelPartBuilder.create().uv(0, 31).cuboid(-2.5f, -1.5f, -1.0f, 5, 3, 4, d),
			ModelTransform.of(0.0f, -2.0f, 5.5f, -0.5f, 0.0f, 0.0f));
		ModelPartData neck = root.addChild("neck", ModelPartBuilder.create().uv(20, 17).cuboid(-1.5f, -9.0f, -1.5f, 3, 10, 3, d),
			ModelTransform.pivot(0.0f, 15.0f, -4.0f));
		ModelPartData head = neck.addChild("head", ModelPartBuilder.create().uv(32, 17).cuboid(-2.0f, -4.0f, -3.5f, 4, 4, 6, d),
			ModelTransform.pivot(0.0f, -9.0f, 0.0f));
		head.addChild("beak", ModelPartBuilder.create().uv(18, 31).cuboid(-1.5f, -1.0f, -4.0f, 3, 1, 4, d),
			ModelTransform.pivot(0.0f, -2.0f, -3.5f));
		head.addChild("jaw", ModelPartBuilder.create().uv(40, 31).cuboid(-1.5f, 0.0f, -3.0f, 3, 1, 3, d),
			ModelTransform.pivot(0.0f, -1.0f, -3.5f));
		root.addChild("left_wing", ModelPartBuilder.create().uv(36, 0).cuboid(0.0f, -0.5f, 0.0f, 1, 5, 9, d),
			ModelTransform.pivot(3.5f, 15.0f, -2.0f));
		root.addChild("right_wing", ModelPartBuilder.create().uv(0, 17).cuboid(-1.0f, -0.5f, 0.0f, 1, 5, 9, d),
			ModelTransform.pivot(-3.5f, 15.0f, -2.0f));
		root.addChild("left_leg", ModelPartBuilder.create().uv(32, 31).cuboid(-0.5f, 0.0f, -0.5f, 1, 4, 1, d)
			.uv(52, 31).cuboid(-1.5f, 3.0f, -2.5f, 3, 1, 3, d), ModelTransform.pivot(1.5f, 20.0f, 1.0f));
		root.addChild("right_leg", ModelPartBuilder.create().uv(36, 31).cuboid(-0.5f, 0.0f, -0.5f, 1, 4, 1, d)
			.uv(0, 38).cuboid(-1.5f, 3.0f, -2.5f, 3, 1, 3, d), ModelTransform.pivot(-1.5f, 20.0f, 1.0f));
		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	@Override
	public void setAngles(T e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
		root.traverse().forEach(ModelPart::resetTransform);
		float walk = limbAngle * 1.4f;
		leftLeg.pitch = MathHelper.cos(walk) * 1.2f * limbDistance;
		rightLeg.pitch = MathHelper.cos(walk + MathHelper.PI) * 1.2f * limbDistance;
		// Вперевалочку.
		body.roll = MathHelper.sin(walk) * 0.12f * limbDistance;
		neck.roll = body.roll * 0.6f;
		neck.pitch = MathHelper.sin(walk * 2) * 0.08f * limbDistance;
		neck.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE * 0.7f;
		head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE * 0.6f;
		tail.yaw = MathHelper.sin(age * 0.3f) * 0.15f;

		boolean hiss = e.isHissing();
		if (hiss) {
			// Шея вперёд, голова к земле — классическая поза гуся.
			neck.pitch = 1.1f;
			head.pitch = -0.9f;
			leftWing.roll = -0.5f - MathHelper.sin(age * 0.8f) * 0.15f;
			rightWing.roll = 0.5f + MathHelper.sin(age * 0.8f) * 0.15f;
		}
		if (e.hasHeld() && limbDistance > 0.05f) {
			// Бежит с добычей и машет крыльями.
			float flap = MathHelper.sin(age * 1.6f);
			leftWing.roll = -0.6f - flap * 0.7f;
			rightWing.roll = 0.6f + flap * 0.7f;
			neck.pitch = 0.35f;
		}
		jaw.pitch = e.mouthOpenTicks > 0 || hiss ? 0.55f + MathHelper.sin(age * 1.3f) * 0.1f : 0f;
	}

	ModelPart neck() {
		return neck;
	}

	ModelPart head() {
		return head;
	}
}
