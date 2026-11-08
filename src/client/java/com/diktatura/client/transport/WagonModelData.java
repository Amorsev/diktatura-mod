package com.diktatura.client.transport;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

/**
 * Модель повозки госперевозки, сделанная в Blockbench (исходник — blockbench/models/wagon.bbmodel).
 * Файл создан скриптом blockbench/import_models.py из экспорта Blockbench «1.17+ Yarn» — не править руками.
 */
@SuppressWarnings("unused")
public final class WagonModelData {
	private WagonModelData() {
	}

	public static TexturedModelData wagon() {
		ModelData modelData = new ModelData();
		ModelPartData modelPartData = modelData.getRoot();
		ModelPartData body = modelPartData.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-10.0F, -10.0F, -8.0F, 20.0F, 2.0F, 28.0F, new Dilation(0.0F))
		.uv(0, 30).cuboid(-10.0F, -16.0F, -8.0F, 1.0F, 6.0F, 28.0F, new Dilation(0.0F))
		.uv(58, 30).cuboid(9.0F, -16.0F, -8.0F, 1.0F, 6.0F, 28.0F, new Dilation(0.0F))
		.uv(0, 64).cuboid(-9.0F, -16.0F, 19.0F, 18.0F, 6.0F, 1.0F, new Dilation(0.0F))
		.uv(38, 64).cuboid(-9.0F, -13.0F, -8.0F, 18.0F, 3.0F, 1.0F, new Dilation(0.0F))
		.uv(76, 64).cuboid(-8.0F, -14.0F, 9.0F, 16.0F, 2.0F, 5.0F, new Dilation(0.0F))
		.uv(0, 71).cuboid(-8.0F, -19.0F, 13.0F, 16.0F, 5.0F, 1.0F, new Dilation(0.0F))
		.uv(34, 71).cuboid(-12.0F, -7.0F, 5.0F, 24.0F, 2.0F, 2.0F, new Dilation(0.0F))
		.uv(0, 77).cuboid(-7.0F, -12.0F, -30.0F, 1.0F, 1.0F, 22.0F, new Dilation(0.0F))
		.uv(46, 77).cuboid(6.0F, -12.0F, -30.0F, 1.0F, 1.0F, 22.0F, new Dilation(0.0F))
		.uv(92, 77).cuboid(-7.0F, -12.0F, -31.0F, 14.0F, 1.0F, 1.0F, new Dilation(0.0F))
		.uv(122, 77).cuboid(8.5F, -32.0F, 18.0F, 1.0F, 16.0F, 1.0F, new Dilation(0.0F))
		.uv(0, 100).cuboid(1.5F, -31.0F, 18.0F, 7.0F, 5.0F, 1.0F, new Dilation(0.0F)), ModelTransform.pivot(0.0F, 24.0F, 0.0F));

		ModelPartData wheel_right = modelPartData.addChild("wheel_right", ModelPartBuilder.create().uv(16, 100).cuboid(-0.5F, -6.0F, -2.5F, 1.0F, 12.0F, 5.0F, new Dilation(0.0F))
		.uv(28, 100).cuboid(-0.5F, -2.5F, -6.0F, 1.0F, 5.0F, 12.0F, new Dilation(0.0F)), ModelTransform.pivot(-11.5F, 18.0F, 6.0F));

		ModelPartData wheel_right_r = wheel_right.addChild("wheel_right_r", ModelPartBuilder.create().uv(54, 100).cuboid(-0.5F, -6.0F, -2.5F, 1.0F, 12.0F, 5.0F, new Dilation(0.0F))
		.uv(66, 100).cuboid(-0.5F, -2.5F, -6.0F, 1.0F, 5.0F, 12.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

		ModelPartData wheel_left = modelPartData.addChild("wheel_left", ModelPartBuilder.create().uv(92, 100).cuboid(-0.5F, -6.0F, -2.5F, 1.0F, 12.0F, 5.0F, new Dilation(0.0F))
		.uv(0, 117).cuboid(-0.5F, -2.5F, -6.0F, 1.0F, 5.0F, 12.0F, new Dilation(0.0F)), ModelTransform.pivot(11.5F, 18.0F, 6.0F));

		ModelPartData wheel_left_r = wheel_left.addChild("wheel_left_r", ModelPartBuilder.create().uv(26, 117).cuboid(-0.5F, -6.0F, -2.5F, 1.0F, 12.0F, 5.0F, new Dilation(0.0F))
		.uv(38, 117).cuboid(-0.5F, -2.5F, -6.0F, 1.0F, 5.0F, 12.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));
		return TexturedModelData.of(modelData, 128, 256);
	}
}
