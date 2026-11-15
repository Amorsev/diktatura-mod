package com.diktatura.client.geo;

import com.diktatura.item.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Предметы в руках гуманоида на GeckoLib (кости {@code right_arm}/{@code left_arm}) — как у ванильного
 * HeldItemFeatureRenderer. Слой вызывается с уже повёрнутой костью руки: переходим в её опору и разворачиваемся
 * в координаты ванильной модели (повёрнуты на 180° вокруг Z). Скипетр вождя держится как жезл.
 */
public class HumanoidItemLayer<T extends LivingEntity & GeoAnimatable> extends GeoRenderLayer<T> {
	private final boolean offhand;

	/** @param offhand рисовать и предмет во второй (левой) руке */
	public HumanoidItemLayer(GeoRenderer<T> renderer, boolean offhand) {
		super(renderer);
		this.offhand = offhand;
	}

	@Override
	public void renderForBone(MatrixStack m, T e, GeoBone bone, RenderLayer renderType, VertexConsumerProvider vcp,
	                          VertexConsumer buffer, float tickDelta, int light, int overlay) {
		boolean left;
		if ("right_arm".equals(bone.getName())) {
			left = false;
		} else if (offhand && "left_arm".equals(bone.getName())) {
			left = true;
		} else {
			return;
		}
		ItemStack stack = left ? e.getOffHandStack() : e.getMainHandStack();
		if (stack.isEmpty()) {
			return;
		}
		float side = left ? -1f : 1f;
		m.push();
		RenderUtil.translateToPivotPoint(m, bone);
		if (stack.isOf(ModItems.SCEPTER)) {
			// Скипетр — как жезл: рукоять зажата в кулаке, навершие смотрит вперёд и вверх (под 60° к руке).
			// Его вид «в руке» для игрока ложится вдоль руки, поэтому рисуем модель без этого вида.
			m.translate(side / 16.0f, -9.5f / 16.0f, 0f);
			m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-60f));
			m.scale(0.85f, 0.85f, 0.85f);
			m.translate(0f, 4.0f / 16.0f, 0f);
			MinecraftClient.getInstance().getItemRenderer().renderItem(e, stack, ModelTransformationMode.NONE,
				left, m, vcp, e.getWorld(), light, OverlayTexture.DEFAULT_UV, e.getId());
		} else {
			m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f));
			m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
			m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
			m.translate(side / 16.0f, 0.125f, -0.625f);
			MinecraftClient.getInstance().getItemRenderer().renderItem(e, stack,
				left ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND,
				left, m, vcp, e.getWorld(), light, OverlayTexture.DEFAULT_UV, e.getId());
		}
		m.pop();
	}
}
