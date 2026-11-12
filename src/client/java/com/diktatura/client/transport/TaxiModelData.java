package com.diktatura.client.transport;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

/**
 * Модели карет госТакси, сделанные в Blockbench (исходники — blockbench/models/taxi_*.bbmodel,
 * генератор — blockbench/gen_taxi.py). Файл создан скриптом blockbench/import_models.py --taxi
 * из экспорта Blockbench «1.17+ Yarn» — не править руками.
 */
@SuppressWarnings("unused")
public final class TaxiModelData {
	private TaxiModelData() {
	}

	public static TexturedModelData economy() {
		ModelData modelData = new ModelData();
		ModelPartData modelPartData = modelData.getRoot();
		ModelPartData body = modelPartData.addChild("body", ModelPartBuilder.create().uv(0, 46).cuboid(-10.0F, -11.0F, 0.0F, 20.0F, 2.0F, 26.0F, new Dilation(0.0F))
		.uv(0, 74).cuboid(-10.0F, -15.0F, 0.0F, 1.0F, 4.0F, 26.0F, new Dilation(0.0F))
		.uv(54, 74).cuboid(9.0F, -15.0F, 0.0F, 1.0F, 4.0F, 26.0F, new Dilation(0.0F))
		.uv(0, 104).cuboid(-9.0F, -14.0F, 0.0F, 18.0F, 3.0F, 1.0F, new Dilation(0.0F))
		.uv(38, 104).cuboid(-9.0F, -15.0F, 25.0F, 18.0F, 4.0F, 1.0F, new Dilation(0.0F))
		.uv(0, 109).cuboid(-8.0F, -15.0F, 6.0F, 16.0F, 4.0F, 18.0F, new Dilation(0.0F))
		.uv(68, 109).cuboid(-6.0F, -17.0F, 9.0F, 12.0F, 2.0F, 12.0F, new Dilation(0.0F))
		.uv(0, 131).cuboid(-7.0F, -13.0F, -34.0F, 1.0F, 1.0F, 34.0F, new Dilation(0.0F))
		.uv(0, 166).cuboid(6.0F, -13.0F, -34.0F, 1.0F, 1.0F, 34.0F, new Dilation(0.0F))
		.uv(70, 166).cuboid(-6.0F, -18.0F, -33.0F, 12.0F, 1.0F, 1.0F, new Dilation(0.0F))
		.uv(26, 201).cuboid(-12.0F, -7.0F, 14.0F, 24.0F, 2.0F, 2.0F, new Dilation(0.0F)), ModelTransform.pivot(0.0F, 24.0F, 0.0F));

		ModelPartData horse = body.addChild("horse", ModelPartBuilder.create().uv(0, 0).cuboid(-5.0F, -5.0F, -11.0F, 10.0F, 10.0F, 22.0F, new Dilation(0.0F)), ModelTransform.pivot(0.0F, -16.0F, -24.0F));

		ModelPartData horse_leg_fr = body.addChild("horse_leg_fr", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-3.0F, -11.0F, -32.0F));

		ModelPartData horse_leg_fl = body.addChild("horse_leg_fl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(3.0F, -11.0F, -32.0F));

		ModelPartData horse_leg_br = body.addChild("horse_leg_br", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-3.0F, -11.0F, -16.0F));

		ModelPartData horse_leg_bl = body.addChild("horse_leg_bl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(3.0F, -11.0F, -16.0F));

		ModelPartData horse_neck = body.addChild("horse_neck", ModelPartBuilder.create().uv(80, 0).cuboid(-2.0F, -10.0F, -4.0F, 4.0F, 12.0F, 7.0F, new Dilation(0.0F))
		.uv(102, 0).cuboid(-1.0F, -12.0F, 3.0F, 2.0F, 13.0F, 2.0F, new Dilation(0.0F))
		.uv(0, 32).cuboid(-2.5F, -10.0F, -11.0F, 5.0F, 5.0F, 7.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(-1.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(0.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, -19.0F, -33.0F, 0.5236F, 0.0F, 0.0F));

		ModelPartData horse_tail = body.addChild("horse_tail", ModelPartBuilder.create().uv(28, 32).cuboid(-1.5F, 0.0F, 0.0F, 3.0F, 11.0F, 3.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, -20.0F, -13.0F, 0.4363F, 0.0F, 0.0F));

		ModelPartData wheel_rear_right = body.addChild("wheel_rear_right", ModelPartBuilder.create().uv(96, 166).cuboid(-0.5F, -6.0F, -2.5F, 1.0F, 12.0F, 5.0F, new Dilation(0.0F))
		.uv(0, 201).cuboid(-0.5F, -2.5F, -6.0F, 1.0F, 5.0F, 12.0F, new Dilation(0.0F)), ModelTransform.pivot(-11.5F, -6.0F, 15.0F));

		ModelPartData wheel_rear_right_r = wheel_rear_right.addChild("wheel_rear_right_r", ModelPartBuilder.create().uv(96, 166).cuboid(-0.5F, -6.0F, -2.5F, 1.0F, 12.0F, 5.0F, new Dilation(0.0F))
		.uv(0, 201).cuboid(-0.5F, -2.5F, -6.0F, 1.0F, 5.0F, 12.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

		ModelPartData wheel_rear_left = body.addChild("wheel_rear_left", ModelPartBuilder.create().uv(96, 166).cuboid(-0.5F, -6.0F, -2.5F, 1.0F, 12.0F, 5.0F, new Dilation(0.0F))
		.uv(0, 201).cuboid(-0.5F, -2.5F, -6.0F, 1.0F, 5.0F, 12.0F, new Dilation(0.0F)), ModelTransform.pivot(11.5F, -6.0F, 15.0F));

		ModelPartData wheel_rear_left_r = wheel_rear_left.addChild("wheel_rear_left_r", ModelPartBuilder.create().uv(96, 166).cuboid(-0.5F, -6.0F, -2.5F, 1.0F, 12.0F, 5.0F, new Dilation(0.0F))
		.uv(0, 201).cuboid(-0.5F, -2.5F, -6.0F, 1.0F, 5.0F, 12.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));
		return TexturedModelData.of(modelData, 128, 256);
	}

	public static TexturedModelData comfort() {
		ModelData modelData = new ModelData();
		ModelPartData modelPartData = modelData.getRoot();
		ModelPartData body = modelPartData.addChild("body", ModelPartBuilder.create().uv(40, 32).cuboid(-1.0F, -13.0F, -38.0F, 2.0F, 1.0F, 36.0F, new Dilation(0.0F))
		.uv(0, 69).cuboid(-11.0F, -13.0F, -14.0F, 22.0F, 1.0F, 1.0F, new Dilation(0.0F))
		.uv(0, 71).cuboid(-11.0F, -12.0F, 0.0F, 22.0F, 2.0F, 28.0F, new Dilation(0.0F))
		.uv(0, 101).cuboid(-11.0F, -22.0F, 0.0F, 1.0F, 10.0F, 28.0F, new Dilation(0.0F))
		.uv(58, 101).cuboid(10.0F, -22.0F, 0.0F, 1.0F, 10.0F, 28.0F, new Dilation(0.0F))
		.uv(0, 139).cuboid(-10.0F, -22.0F, 0.0F, 20.0F, 10.0F, 1.0F, new Dilation(0.0F))
		.uv(42, 139).cuboid(-10.0F, -22.0F, 27.0F, 20.0F, 10.0F, 1.0F, new Dilation(0.0F))
		.uv(84, 139).cuboid(-11.0F, -30.0F, 0.0F, 1.0F, 8.0F, 1.0F, new Dilation(0.0F))
		.uv(84, 139).cuboid(10.0F, -30.0F, 0.0F, 1.0F, 8.0F, 1.0F, new Dilation(0.0F))
		.uv(84, 139).cuboid(-11.0F, -30.0F, 27.0F, 1.0F, 8.0F, 1.0F, new Dilation(0.0F))
		.uv(84, 139).cuboid(10.0F, -30.0F, 27.0F, 1.0F, 8.0F, 1.0F, new Dilation(0.0F))
		.uv(88, 139).cuboid(-11.0F, -30.0F, 13.0F, 1.0F, 8.0F, 2.0F, new Dilation(0.0F))
		.uv(88, 139).cuboid(10.0F, -30.0F, 13.0F, 1.0F, 8.0F, 2.0F, new Dilation(0.0F))
		.uv(0, 150).cuboid(-12.0F, -32.0F, -1.0F, 24.0F, 2.0F, 30.0F, new Dilation(0.0F))
		.uv(0, 182).cuboid(-10.0F, -33.0F, 1.0F, 20.0F, 1.0F, 26.0F, new Dilation(0.0F))
		.uv(0, 209).cuboid(-8.0F, -35.0F, 4.0F, 16.0F, 2.0F, 20.0F, new Dilation(0.0F))
		.uv(72, 209).cuboid(-9.0F, -15.0F, 18.0F, 18.0F, 3.0F, 8.0F, new Dilation(0.0F))
		.uv(0, 231).cuboid(-9.0F, -21.0F, 25.0F, 18.0F, 6.0F, 2.0F, new Dilation(0.0F))
		.uv(40, 231).cuboid(-9.0F, -22.0F, -6.0F, 18.0F, 6.0F, 6.0F, new Dilation(0.0F))
		.uv(0, 243).cuboid(-9.0F, -23.0F, -5.0F, 18.0F, 1.0F, 5.0F, new Dilation(0.0F))
		.uv(46, 243).cuboid(-13.0F, -27.0F, -1.0F, 2.0F, 4.0F, 2.0F, new Dilation(0.0F))
		.uv(54, 243).cuboid(-13.5F, -28.0F, -1.5F, 3.0F, 1.0F, 3.0F, new Dilation(0.0F))
		.uv(46, 243).cuboid(11.0F, -27.0F, -1.0F, 2.0F, 4.0F, 2.0F, new Dilation(0.0F))
		.uv(54, 243).cuboid(10.5F, -28.0F, -1.5F, 3.0F, 1.0F, 3.0F, new Dilation(0.0F))
		.uv(0, 258).cuboid(-12.0F, -6.0F, 5.0F, 24.0F, 2.0F, 2.0F, new Dilation(0.0F))
		.uv(0, 275).cuboid(-12.0F, -7.0F, 21.0F, 24.0F, 2.0F, 2.0F, new Dilation(0.0F)), ModelTransform.pivot(0.0F, 24.0F, 0.0F));

		ModelPartData horse_r = body.addChild("horse_r", ModelPartBuilder.create().uv(0, 0).cuboid(-5.0F, -5.0F, -11.0F, 10.0F, 10.0F, 22.0F, new Dilation(0.0F)), ModelTransform.pivot(-6.0F, -16.0F, -27.0F));

		ModelPartData horse_r_leg_fr = body.addChild("horse_r_leg_fr", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-9.0F, -11.0F, -35.0F));

		ModelPartData horse_r_leg_fl = body.addChild("horse_r_leg_fl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-3.0F, -11.0F, -35.0F));

		ModelPartData horse_r_leg_br = body.addChild("horse_r_leg_br", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-9.0F, -11.0F, -19.0F));

		ModelPartData horse_r_leg_bl = body.addChild("horse_r_leg_bl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-3.0F, -11.0F, -19.0F));

		ModelPartData horse_r_neck = body.addChild("horse_r_neck", ModelPartBuilder.create().uv(80, 0).cuboid(-2.0F, -10.0F, -4.0F, 4.0F, 12.0F, 7.0F, new Dilation(0.0F))
		.uv(102, 0).cuboid(-1.0F, -12.0F, 3.0F, 2.0F, 13.0F, 2.0F, new Dilation(0.0F))
		.uv(0, 32).cuboid(-2.5F, -10.0F, -11.0F, 5.0F, 5.0F, 7.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(-1.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(0.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F)), ModelTransform.of(-6.0F, -19.0F, -36.0F, 0.5236F, 0.0F, 0.0F));

		ModelPartData horse_r_tail = body.addChild("horse_r_tail", ModelPartBuilder.create().uv(28, 32).cuboid(-1.5F, 0.0F, 0.0F, 3.0F, 11.0F, 3.0F, new Dilation(0.0F)), ModelTransform.of(-6.0F, -20.0F, -16.0F, 0.4363F, 0.0F, 0.0F));

		ModelPartData horse_l = body.addChild("horse_l", ModelPartBuilder.create().uv(0, 0).cuboid(-5.0F, -5.0F, -11.0F, 10.0F, 10.0F, 22.0F, new Dilation(0.0F)), ModelTransform.pivot(6.0F, -16.0F, -27.0F));

		ModelPartData horse_l_leg_fr = body.addChild("horse_l_leg_fr", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(3.0F, -11.0F, -35.0F));

		ModelPartData horse_l_leg_fl = body.addChild("horse_l_leg_fl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(9.0F, -11.0F, -35.0F));

		ModelPartData horse_l_leg_br = body.addChild("horse_l_leg_br", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(3.0F, -11.0F, -19.0F));

		ModelPartData horse_l_leg_bl = body.addChild("horse_l_leg_bl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(9.0F, -11.0F, -19.0F));

		ModelPartData horse_l_neck = body.addChild("horse_l_neck", ModelPartBuilder.create().uv(80, 0).cuboid(-2.0F, -10.0F, -4.0F, 4.0F, 12.0F, 7.0F, new Dilation(0.0F))
		.uv(102, 0).cuboid(-1.0F, -12.0F, 3.0F, 2.0F, 13.0F, 2.0F, new Dilation(0.0F))
		.uv(0, 32).cuboid(-2.5F, -10.0F, -11.0F, 5.0F, 5.0F, 7.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(-1.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(0.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F)), ModelTransform.of(6.0F, -19.0F, -36.0F, 0.5236F, 0.0F, 0.0F));

		ModelPartData horse_l_tail = body.addChild("horse_l_tail", ModelPartBuilder.create().uv(28, 32).cuboid(-1.5F, 0.0F, 0.0F, 3.0F, 11.0F, 3.0F, new Dilation(0.0F)), ModelTransform.of(6.0F, -20.0F, -16.0F, 0.4363F, 0.0F, 0.0F));

		ModelPartData wheel_front_right = body.addChild("wheel_front_right", ModelPartBuilder.create().uv(66, 243).cuboid(-0.5F, -5.0F, -2.5F, 1.0F, 10.0F, 5.0F, new Dilation(0.0F))
		.uv(78, 243).cuboid(-0.5F, -2.5F, -5.0F, 1.0F, 5.0F, 10.0F, new Dilation(0.0F)), ModelTransform.pivot(-11.5F, -5.0F, 6.0F));

		ModelPartData wheel_front_right_r = wheel_front_right.addChild("wheel_front_right_r", ModelPartBuilder.create().uv(66, 243).cuboid(-0.5F, -5.0F, -2.5F, 1.0F, 10.0F, 5.0F, new Dilation(0.0F))
		.uv(78, 243).cuboid(-0.5F, -2.5F, -5.0F, 1.0F, 5.0F, 10.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

		ModelPartData wheel_front_left = body.addChild("wheel_front_left", ModelPartBuilder.create().uv(66, 243).cuboid(-0.5F, -5.0F, -2.5F, 1.0F, 10.0F, 5.0F, new Dilation(0.0F))
		.uv(78, 243).cuboid(-0.5F, -2.5F, -5.0F, 1.0F, 5.0F, 10.0F, new Dilation(0.0F)), ModelTransform.pivot(11.5F, -5.0F, 6.0F));

		ModelPartData wheel_front_left_r = wheel_front_left.addChild("wheel_front_left_r", ModelPartBuilder.create().uv(66, 243).cuboid(-0.5F, -5.0F, -2.5F, 1.0F, 10.0F, 5.0F, new Dilation(0.0F))
		.uv(78, 243).cuboid(-0.5F, -2.5F, -5.0F, 1.0F, 5.0F, 10.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

		ModelPartData wheel_rear_right = body.addChild("wheel_rear_right", ModelPartBuilder.create().uv(52, 258).cuboid(-0.5F, -6.0F, -2.5F, 1.0F, 12.0F, 5.0F, new Dilation(0.0F))
		.uv(64, 258).cuboid(-0.5F, -2.5F, -6.0F, 1.0F, 5.0F, 12.0F, new Dilation(0.0F)), ModelTransform.pivot(-11.5F, -6.0F, 22.0F));

		ModelPartData wheel_rear_right_r = wheel_rear_right.addChild("wheel_rear_right_r", ModelPartBuilder.create().uv(52, 258).cuboid(-0.5F, -6.0F, -2.5F, 1.0F, 12.0F, 5.0F, new Dilation(0.0F))
		.uv(64, 258).cuboid(-0.5F, -2.5F, -6.0F, 1.0F, 5.0F, 12.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

		ModelPartData wheel_rear_left = body.addChild("wheel_rear_left", ModelPartBuilder.create().uv(52, 258).cuboid(-0.5F, -6.0F, -2.5F, 1.0F, 12.0F, 5.0F, new Dilation(0.0F))
		.uv(64, 258).cuboid(-0.5F, -2.5F, -6.0F, 1.0F, 5.0F, 12.0F, new Dilation(0.0F)), ModelTransform.pivot(11.5F, -6.0F, 22.0F));

		ModelPartData wheel_rear_left_r = wheel_rear_left.addChild("wheel_rear_left_r", ModelPartBuilder.create().uv(52, 258).cuboid(-0.5F, -6.0F, -2.5F, 1.0F, 12.0F, 5.0F, new Dilation(0.0F))
		.uv(64, 258).cuboid(-0.5F, -2.5F, -6.0F, 1.0F, 5.0F, 12.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));
		return TexturedModelData.of(modelData, 128, 512);
	}

	public static TexturedModelData business() {
		ModelData modelData = new ModelData();
		ModelPartData modelPartData = modelData.getRoot();
		ModelPartData body = modelPartData.addChild("body", ModelPartBuilder.create().uv(0, 46).cuboid(-1.0F, -13.0F, -64.0F, 2.0F, 1.0F, 62.0F, new Dilation(0.0F))
		.uv(0, 109).cuboid(-11.0F, -13.0F, -14.0F, 22.0F, 1.0F, 1.0F, new Dilation(0.0F))
		.uv(46, 109).cuboid(-11.0F, -13.0F, -40.0F, 22.0F, 1.0F, 1.0F, new Dilation(0.0F))
		.uv(0, 111).cuboid(-12.0F, -13.0F, 0.0F, 24.0F, 2.0F, 30.0F, new Dilation(0.0F))
		.uv(0, 143).cuboid(-12.0F, -24.0F, 0.0F, 1.0F, 11.0F, 30.0F, new Dilation(0.0F))
		.uv(62, 143).cuboid(11.0F, -24.0F, 0.0F, 1.0F, 11.0F, 30.0F, new Dilation(0.0F))
		.uv(0, 184).cuboid(-11.0F, -24.0F, 0.0F, 22.0F, 11.0F, 1.0F, new Dilation(0.0F))
		.uv(46, 184).cuboid(-11.0F, -24.0F, 29.0F, 22.0F, 11.0F, 1.0F, new Dilation(0.0F))
		.uv(92, 184).cuboid(-12.0F, -32.0F, 0.0F, 1.0F, 8.0F, 1.0F, new Dilation(0.0F))
		.uv(92, 184).cuboid(11.0F, -32.0F, 0.0F, 1.0F, 8.0F, 1.0F, new Dilation(0.0F))
		.uv(92, 184).cuboid(-12.0F, -32.0F, 29.0F, 1.0F, 8.0F, 1.0F, new Dilation(0.0F))
		.uv(92, 184).cuboid(11.0F, -32.0F, 29.0F, 1.0F, 8.0F, 1.0F, new Dilation(0.0F))
		.uv(96, 184).cuboid(-12.0F, -32.0F, 14.0F, 1.0F, 8.0F, 2.0F, new Dilation(0.0F))
		.uv(96, 184).cuboid(11.0F, -32.0F, 14.0F, 1.0F, 8.0F, 2.0F, new Dilation(0.0F))
		.uv(102, 184).cuboid(-12.5F, -31.0F, 1.0F, 1.0F, 6.0F, 2.0F, new Dilation(-0.25F))
		.uv(102, 184).cuboid(11.5F, -31.0F, 1.0F, 1.0F, 6.0F, 2.0F, new Dilation(-0.25F))
		.uv(0, 196).cuboid(-13.0F, -34.0F, -1.0F, 26.0F, 2.0F, 32.0F, new Dilation(0.0F))
		.uv(0, 230).cuboid(-12.0F, -35.0F, 0.0F, 24.0F, 1.0F, 30.0F, new Dilation(0.0F))
		.uv(0, 261).cuboid(-10.0F, -36.0F, 2.0F, 20.0F, 1.0F, 26.0F, new Dilation(0.0F))
		.uv(92, 261).cuboid(-12.0F, -37.0F, 0.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(92, 261).cuboid(11.0F, -37.0F, 0.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(92, 261).cuboid(-12.0F, -37.0F, 29.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(92, 261).cuboid(11.0F, -37.0F, 29.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(96, 261).cuboid(-2.5F, -40.0F, 13.0F, 5.0F, 4.0F, 4.0F, new Dilation(0.0F))
		.uv(0, 288).cuboid(-10.0F, -16.0F, 20.0F, 20.0F, 3.0F, 8.0F, new Dilation(0.0F))
		.uv(56, 288).cuboid(-10.0F, -23.0F, 27.0F, 20.0F, 7.0F, 2.0F, new Dilation(0.0F))
		.uv(0, 299).cuboid(-6.0F, -10.0F, 30.0F, 12.0F, 1.0F, 4.0F, new Dilation(0.0F))
		.uv(32, 299).cuboid(-9.0F, -24.0F, -8.0F, 18.0F, 7.0F, 8.0F, new Dilation(0.0F))
		.uv(0, 314).cuboid(-9.0F, -25.0F, -6.0F, 18.0F, 1.0F, 6.0F, new Dilation(0.0F))
		.uv(36, 343).cuboid(-14.0F, -28.0F, -1.0F, 2.0F, 4.0F, 2.0F, new Dilation(0.0F))
		.uv(44, 343).cuboid(-14.5F, -29.0F, -1.5F, 3.0F, 1.0F, 3.0F, new Dilation(0.0F))
		.uv(36, 343).cuboid(12.0F, -28.0F, -1.0F, 2.0F, 4.0F, 2.0F, new Dilation(0.0F))
		.uv(44, 343).cuboid(11.5F, -29.0F, -1.5F, 3.0F, 1.0F, 3.0F, new Dilation(0.0F))
		.uv(0, 362).cuboid(-13.0F, -6.0F, 5.0F, 26.0F, 2.0F, 2.0F, new Dilation(0.0F))
		.uv(0, 381).cuboid(-13.0F, -8.0F, 23.0F, 26.0F, 2.0F, 2.0F, new Dilation(0.0F)), ModelTransform.pivot(0.0F, 24.0F, 0.0F));

		ModelPartData horse_ar = body.addChild("horse_ar", ModelPartBuilder.create().uv(0, 0).cuboid(-5.0F, -5.0F, -11.0F, 10.0F, 10.0F, 22.0F, new Dilation(0.0F)), ModelTransform.pivot(-6.0F, -16.0F, -27.0F));

		ModelPartData horse_ar_leg_fr = body.addChild("horse_ar_leg_fr", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-9.0F, -11.0F, -35.0F));

		ModelPartData horse_ar_leg_fl = body.addChild("horse_ar_leg_fl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-3.0F, -11.0F, -35.0F));

		ModelPartData horse_ar_leg_br = body.addChild("horse_ar_leg_br", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-9.0F, -11.0F, -19.0F));

		ModelPartData horse_ar_leg_bl = body.addChild("horse_ar_leg_bl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-3.0F, -11.0F, -19.0F));

		ModelPartData horse_ar_neck = body.addChild("horse_ar_neck", ModelPartBuilder.create().uv(80, 0).cuboid(-2.0F, -10.0F, -4.0F, 4.0F, 12.0F, 7.0F, new Dilation(0.0F))
		.uv(102, 0).cuboid(-1.0F, -12.0F, 3.0F, 2.0F, 13.0F, 2.0F, new Dilation(0.0F))
		.uv(0, 32).cuboid(-2.5F, -10.0F, -11.0F, 5.0F, 5.0F, 7.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(-1.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(0.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(28, 32).cuboid(-0.5F, -15.0F, -8.0F, 1.0F, 5.0F, 2.0F, new Dilation(0.0F)), ModelTransform.of(-6.0F, -19.0F, -36.0F, 0.5236F, 0.0F, 0.0F));

		ModelPartData horse_ar_tail = body.addChild("horse_ar_tail", ModelPartBuilder.create().uv(34, 32).cuboid(-1.5F, 0.0F, 0.0F, 3.0F, 11.0F, 3.0F, new Dilation(0.0F)), ModelTransform.of(-6.0F, -20.0F, -16.0F, 0.4363F, 0.0F, 0.0F));

		ModelPartData horse_al = body.addChild("horse_al", ModelPartBuilder.create().uv(0, 0).cuboid(-5.0F, -5.0F, -11.0F, 10.0F, 10.0F, 22.0F, new Dilation(0.0F)), ModelTransform.pivot(6.0F, -16.0F, -27.0F));

		ModelPartData horse_al_leg_fr = body.addChild("horse_al_leg_fr", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(3.0F, -11.0F, -35.0F));

		ModelPartData horse_al_leg_fl = body.addChild("horse_al_leg_fl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(9.0F, -11.0F, -35.0F));

		ModelPartData horse_al_leg_br = body.addChild("horse_al_leg_br", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(3.0F, -11.0F, -19.0F));

		ModelPartData horse_al_leg_bl = body.addChild("horse_al_leg_bl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(9.0F, -11.0F, -19.0F));

		ModelPartData horse_al_neck = body.addChild("horse_al_neck", ModelPartBuilder.create().uv(80, 0).cuboid(-2.0F, -10.0F, -4.0F, 4.0F, 12.0F, 7.0F, new Dilation(0.0F))
		.uv(102, 0).cuboid(-1.0F, -12.0F, 3.0F, 2.0F, 13.0F, 2.0F, new Dilation(0.0F))
		.uv(0, 32).cuboid(-2.5F, -10.0F, -11.0F, 5.0F, 5.0F, 7.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(-1.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(0.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(28, 32).cuboid(-0.5F, -15.0F, -8.0F, 1.0F, 5.0F, 2.0F, new Dilation(0.0F)), ModelTransform.of(6.0F, -19.0F, -36.0F, 0.5236F, 0.0F, 0.0F));

		ModelPartData horse_al_tail = body.addChild("horse_al_tail", ModelPartBuilder.create().uv(34, 32).cuboid(-1.5F, 0.0F, 0.0F, 3.0F, 11.0F, 3.0F, new Dilation(0.0F)), ModelTransform.of(6.0F, -20.0F, -16.0F, 0.4363F, 0.0F, 0.0F));

		ModelPartData horse_br = body.addChild("horse_br", ModelPartBuilder.create().uv(0, 0).cuboid(-5.0F, -5.0F, -11.0F, 10.0F, 10.0F, 22.0F, new Dilation(0.0F)), ModelTransform.pivot(-6.0F, -16.0F, -53.0F));

		ModelPartData horse_br_leg_fr = body.addChild("horse_br_leg_fr", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-9.0F, -11.0F, -61.0F));

		ModelPartData horse_br_leg_fl = body.addChild("horse_br_leg_fl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-3.0F, -11.0F, -61.0F));

		ModelPartData horse_br_leg_br = body.addChild("horse_br_leg_br", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-9.0F, -11.0F, -45.0F));

		ModelPartData horse_br_leg_bl = body.addChild("horse_br_leg_bl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(-3.0F, -11.0F, -45.0F));

		ModelPartData horse_br_neck = body.addChild("horse_br_neck", ModelPartBuilder.create().uv(80, 0).cuboid(-2.0F, -10.0F, -4.0F, 4.0F, 12.0F, 7.0F, new Dilation(0.0F))
		.uv(102, 0).cuboid(-1.0F, -12.0F, 3.0F, 2.0F, 13.0F, 2.0F, new Dilation(0.0F))
		.uv(0, 32).cuboid(-2.5F, -10.0F, -11.0F, 5.0F, 5.0F, 7.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(-1.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(0.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(28, 32).cuboid(-0.5F, -15.0F, -8.0F, 1.0F, 5.0F, 2.0F, new Dilation(0.0F)), ModelTransform.of(-6.0F, -19.0F, -62.0F, 0.5236F, 0.0F, 0.0F));

		ModelPartData horse_br_tail = body.addChild("horse_br_tail", ModelPartBuilder.create().uv(34, 32).cuboid(-1.5F, 0.0F, 0.0F, 3.0F, 11.0F, 3.0F, new Dilation(0.0F)), ModelTransform.of(-6.0F, -20.0F, -42.0F, 0.4363F, 0.0F, 0.0F));

		ModelPartData horse_bl = body.addChild("horse_bl", ModelPartBuilder.create().uv(0, 0).cuboid(-5.0F, -5.0F, -11.0F, 10.0F, 10.0F, 22.0F, new Dilation(0.0F)), ModelTransform.pivot(6.0F, -16.0F, -53.0F));

		ModelPartData horse_bl_leg_fr = body.addChild("horse_bl_leg_fr", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(3.0F, -11.0F, -61.0F));

		ModelPartData horse_bl_leg_fl = body.addChild("horse_bl_leg_fl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(9.0F, -11.0F, -61.0F));

		ModelPartData horse_bl_leg_br = body.addChild("horse_bl_leg_br", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(3.0F, -11.0F, -45.0F));

		ModelPartData horse_bl_leg_bl = body.addChild("horse_bl_leg_bl", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(9.0F, -11.0F, -45.0F));

		ModelPartData horse_bl_neck = body.addChild("horse_bl_neck", ModelPartBuilder.create().uv(80, 0).cuboid(-2.0F, -10.0F, -4.0F, 4.0F, 12.0F, 7.0F, new Dilation(0.0F))
		.uv(102, 0).cuboid(-1.0F, -12.0F, 3.0F, 2.0F, 13.0F, 2.0F, new Dilation(0.0F))
		.uv(0, 32).cuboid(-2.5F, -10.0F, -11.0F, 5.0F, 5.0F, 7.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(-1.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(24, 32).cuboid(0.5F, -12.0F, -5.0F, 1.0F, 2.0F, 1.0F, new Dilation(0.0F))
		.uv(28, 32).cuboid(-0.5F, -15.0F, -8.0F, 1.0F, 5.0F, 2.0F, new Dilation(0.0F)), ModelTransform.of(6.0F, -19.0F, -62.0F, 0.5236F, 0.0F, 0.0F));

		ModelPartData horse_bl_tail = body.addChild("horse_bl_tail", ModelPartBuilder.create().uv(34, 32).cuboid(-1.5F, 0.0F, 0.0F, 3.0F, 11.0F, 3.0F, new Dilation(0.0F)), ModelTransform.of(6.0F, -20.0F, -42.0F, 0.4363F, 0.0F, 0.0F));

		ModelPartData coachman = body.addChild("coachman", ModelPartBuilder.create().uv(48, 314).cuboid(-4.0F, -12.0F, -2.0F, 8.0F, 12.0F, 4.0F, new Dilation(0.0F))
		.uv(72, 314).cuboid(-4.0F, -20.0F, -4.0F, 8.0F, 8.0F, 8.0F, new Dilation(0.0F))
		.uv(0, 330).cuboid(-4.0F, -25.0F, -4.0F, 8.0F, 5.0F, 8.0F, new Dilation(0.3F))
		.uv(32, 330).cuboid(-5.5F, -21.0F, -5.5F, 11.0F, 1.0F, 11.0F, new Dilation(0.0F))
		.uv(76, 330).cuboid(-8.0F, -9.0F, -10.0F, 4.0F, 4.0F, 8.0F, new Dilation(0.0F))
		.uv(100, 330).cuboid(4.0F, -9.0F, -10.0F, 4.0F, 4.0F, 8.0F, new Dilation(0.0F))
		.uv(0, 343).cuboid(-4.0F, -4.0F, -10.0F, 8.0F, 4.0F, 8.0F, new Dilation(0.0F))
		.uv(32, 343).cuboid(-9.0F, -23.0F, -11.0F, 1.0F, 18.0F, 1.0F, new Dilation(0.0F)), ModelTransform.pivot(0.0F, -25.0F, -3.0F));

		ModelPartData wheel_front_right = body.addChild("wheel_front_right", ModelPartBuilder.create().uv(56, 343).cuboid(-0.5F, -5.0F, -2.5F, 1.0F, 10.0F, 5.0F, new Dilation(0.0F))
		.uv(68, 343).cuboid(-0.5F, -2.5F, -5.0F, 1.0F, 5.0F, 10.0F, new Dilation(0.0F)), ModelTransform.pivot(-12.5F, -5.0F, 6.0F));

		ModelPartData wheel_front_right_r = wheel_front_right.addChild("wheel_front_right_r", ModelPartBuilder.create().uv(56, 343).cuboid(-0.5F, -5.0F, -2.5F, 1.0F, 10.0F, 5.0F, new Dilation(0.0F))
		.uv(68, 343).cuboid(-0.5F, -2.5F, -5.0F, 1.0F, 5.0F, 10.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

		ModelPartData wheel_front_left = body.addChild("wheel_front_left", ModelPartBuilder.create().uv(56, 343).cuboid(-0.5F, -5.0F, -2.5F, 1.0F, 10.0F, 5.0F, new Dilation(0.0F))
		.uv(68, 343).cuboid(-0.5F, -2.5F, -5.0F, 1.0F, 5.0F, 10.0F, new Dilation(0.0F)), ModelTransform.pivot(12.5F, -5.0F, 6.0F));

		ModelPartData wheel_front_left_r = wheel_front_left.addChild("wheel_front_left_r", ModelPartBuilder.create().uv(56, 343).cuboid(-0.5F, -5.0F, -2.5F, 1.0F, 10.0F, 5.0F, new Dilation(0.0F))
		.uv(68, 343).cuboid(-0.5F, -2.5F, -5.0F, 1.0F, 5.0F, 10.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

		ModelPartData wheel_rear_right = body.addChild("wheel_rear_right", ModelPartBuilder.create().uv(56, 362).cuboid(-0.5F, -7.0F, -2.5F, 1.0F, 14.0F, 5.0F, new Dilation(0.0F))
		.uv(68, 362).cuboid(-0.5F, -2.5F, -7.0F, 1.0F, 5.0F, 14.0F, new Dilation(0.0F)), ModelTransform.pivot(-12.5F, -7.0F, 24.0F));

		ModelPartData wheel_rear_right_r = wheel_rear_right.addChild("wheel_rear_right_r", ModelPartBuilder.create().uv(56, 362).cuboid(-0.5F, -7.0F, -2.5F, 1.0F, 14.0F, 5.0F, new Dilation(0.0F))
		.uv(68, 362).cuboid(-0.5F, -2.5F, -7.0F, 1.0F, 5.0F, 14.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

		ModelPartData wheel_rear_left = body.addChild("wheel_rear_left", ModelPartBuilder.create().uv(56, 362).cuboid(-0.5F, -7.0F, -2.5F, 1.0F, 14.0F, 5.0F, new Dilation(0.0F))
		.uv(68, 362).cuboid(-0.5F, -2.5F, -7.0F, 1.0F, 5.0F, 14.0F, new Dilation(0.0F)), ModelTransform.pivot(12.5F, -7.0F, 24.0F));

		ModelPartData wheel_rear_left_r = wheel_rear_left.addChild("wheel_rear_left_r", ModelPartBuilder.create().uv(56, 362).cuboid(-0.5F, -7.0F, -2.5F, 1.0F, 14.0F, 5.0F, new Dilation(0.0F))
		.uv(68, 362).cuboid(-0.5F, -2.5F, -7.0F, 1.0F, 5.0F, 14.0F, new Dilation(0.0F)), ModelTransform.of(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));
		return TexturedModelData.of(modelData, 128, 512);
	}
}
