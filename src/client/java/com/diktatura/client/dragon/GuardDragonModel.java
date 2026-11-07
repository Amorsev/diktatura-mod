package com.diktatura.client.dragon;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

/**
 * Надстройка над ванильной моделью дракона: тот же скелет (те же имена и точки крепления частей,
 * поэтому её анимирует тот же код {@code DragonEntityModel}), но кубы только свои —
 * рога с золотыми концами, кокарда на лбу, кристаллы на спине, гребень из кристаллов на шее и хвосте,
 * красная повязка госохраны на лапе. Рисуется поверх ванильной модели своей текстурой 64×64.
 */
public final class GuardDragonModel {
	private GuardDragonModel() {
	}

	public static TexturedModelData create() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartBuilder empty = ModelPartBuilder.create();

		// Голова: большие рога назад-вверх, малые рога по бокам, кокарда на лбу.
		ModelPartData head = root.addChild("head", empty, ModelTransform.NONE);
		head.addChild("jaw", empty, ModelTransform.pivot(0f, 4f, -8f));
		for (int side = -1; side <= 1; side += 2) {
			String s = side > 0 ? "left" : "right";
			ModelPartData horn = head.addChild(s + "_horn",
				ModelPartBuilder.create().uv(0, 0).cuboid(-1.5f, -13f, -1.5f, 3f, 13f, 3f),
				ModelTransform.of(5.5f * side, -7f, 3f, -0.75f, 0f, 0.3f * side));
			horn.addChild(s + "_horn_tip",
				ModelPartBuilder.create().uv(12, 0).cuboid(-1f, -9f, -1f, 2f, 9f, 2f),
				ModelTransform.of(0f, -12f, 0f, -0.55f, 0f, 0f));
			head.addChild(s + "_side_horn",
				ModelPartBuilder.create().uv(20, 0).cuboid(-1f, -1f, 0f, 2f, 2f, 8f),
				ModelTransform.of(7.5f * side, -1f, 4f, 0.3f, 0.35f * side, 0f));
		}
		head.addChild("badge", ModelPartBuilder.create().uv(40, 0).cuboid(-2.5f, -6.5f, -10.8f, 5f, 5f, 1f),
			ModelTransform.NONE);

		// Шея (и хвост — тот же кусок): по паре маленьких кристаллов на каждом позвонке.
		ModelPartData neck = root.addChild("neck", empty, ModelTransform.NONE);
		for (int side = -1; side <= 1; side += 2) {
			neck.addChild((side > 0 ? "left" : "right") + "_crest",
				ModelPartBuilder.create().uv(28, 16).cuboid(-1f, -4f, -1f, 2f, 4f, 2f),
				ModelTransform.of(3.2f * side, -5f, 1f, 0f, 0.785f, 0.45f * side));
		}

		// Туловище: три пары кристаллических друз вдоль спины.
		ModelPartData body = root.addChild("body", empty, ModelTransform.pivot(0f, 4f, 8f));
		float[] rows = {-6f, 14f, 34f};
		for (int i = 0; i < rows.length; i++) {
			for (int side = -1; side <= 1; side += 2) {
				String s = (side > 0 ? "left" : "right") + "_crystal" + i;
				body.addChild(s,
					ModelPartBuilder.create().uv(0, 16).cuboid(-2f, -11f, -2f, 4f, 11f, 4f),
					ModelTransform.of(7f * side, 0.5f, rows[i], 0.15f, 0.785f, 0.35f * side));
				body.addChild(s + "_small",
					ModelPartBuilder.create().uv(16, 16).cuboid(-1.5f, -6f, -1.5f, 3f, 6f, 3f),
					ModelTransform.of(10.5f * side, 0.5f, rows[i] + 5f, -0.2f, 0.4f, 0.7f * side));
			}
		}

		// Крылья и лапы — без своих кубов, кроме повязки на левой передней лапе.
		ModelPartData lw = root.addChild("left_wing", empty, ModelTransform.pivot(12f, 5f, 2f));
		lw.addChild("left_wing_tip", empty, ModelTransform.pivot(56f, 0f, 0f));
		ModelPartData rw = root.addChild("right_wing", empty, ModelTransform.pivot(-12f, 5f, 2f));
		rw.addChild("right_wing_tip", empty, ModelTransform.pivot(-56f, 0f, 0f));

		ModelPartData lfl = root.addChild("left_front_leg",
			ModelPartBuilder.create().uv(0, 32).cuboid(-4f, 3f, -4f, 8f, 4f, 8f, new Dilation(0.6f)),
			ModelTransform.pivot(12f, 20f, 2f));
		lfl.addChild("left_front_leg_tip", empty, ModelTransform.pivot(0f, 20f, -1f))
			.addChild("left_front_foot", empty, ModelTransform.pivot(0f, 23f, 0f));
		root.addChild("right_front_leg", empty, ModelTransform.pivot(-12f, 20f, 2f))
			.addChild("right_front_leg_tip", empty, ModelTransform.pivot(0f, 20f, -1f))
			.addChild("right_front_foot", empty, ModelTransform.pivot(0f, 23f, 0f));
		root.addChild("left_hind_leg", empty, ModelTransform.pivot(16f, 16f, 42f))
			.addChild("left_hind_leg_tip", empty, ModelTransform.pivot(0f, 32f, -4f))
			.addChild("left_hind_foot", empty, ModelTransform.pivot(0f, 31f, 4f));
		root.addChild("right_hind_leg", empty, ModelTransform.pivot(-16f, 16f, 42f))
			.addChild("right_hind_leg_tip", empty, ModelTransform.pivot(0f, 32f, -4f))
			.addChild("right_hind_foot", empty, ModelTransform.pivot(0f, 31f, 4f));
		return TexturedModelData.of(data, 64, 64);
	}
}
