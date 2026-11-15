package com.diktatura.client.goose;

import com.diktatura.Diktatura;
import com.diktatura.goose.GooseEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Рендер гусей на GeckoLib: общая модель и анимации, своя текстура у дикого и служебного, предмет в клюве,
 * голова поворачивается за взглядом (поверх анимации). Гусята — в 0.55 размера.
 */
public class GooseRenderer<T extends GooseEntity> extends GeoEntityRenderer<T> {
	public GooseRenderer(EntityRendererFactory.Context ctx, String textureName) {
		super(ctx, new Model<>(Diktatura.id("textures/entity/goose/" + textureName + ".png")));
		this.shadowRadius = 0.4f;
		addRenderLayer(new HeldItem<>(this));
	}

	@Override
	public void render(T e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light) {
		if (e.isBaby()) {
			m.push();
			m.scale(0.55f, 0.55f, 0.55f);
			super.render(e, yaw, tickDelta, m, vcp, light);
			m.pop();
		} else {
			super.render(e, yaw, tickDelta, m, vcp, light);
		}
	}

	static final class Model<T extends GooseEntity> extends GeoModel<T> {
		private static final Identifier GEO = Diktatura.id("geo/entity/goose.geo.json");
		private static final Identifier ANIM = Diktatura.id("animations/entity/goose.animation.json");
		private final Identifier texture;

		Model(Identifier texture) {
			this.texture = texture;
		}

		@Override
		public Identifier getModelResource(T e) {
			return GEO;
		}

		@Override
		public Identifier getTextureResource(T e) {
			return texture;
		}

		@Override
		public Identifier getAnimationResource(T e) {
			return ANIM;
		}

		@Override
		public void setCustomAnimations(T e, long instanceId, AnimationState<T> state) {
			if (e.isHissing()) {
				return; // в позе шипения шея и голова заданы анимацией
			}
			EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
			if (data == null) {
				return;
			}
			GeoBone neck = getAnimationProcessor().getBone("neck");
			GeoBone head = getAnimationProcessor().getBone("head");
			if (neck != null) {
				neck.setRotY(neck.getRotY() + data.netHeadYaw() * MathHelper.RADIANS_PER_DEGREE * 0.7f);
			}
			if (head != null) {
				head.setRotX(head.getRotX() + data.headPitch() * MathHelper.RADIANS_PER_DEGREE * 0.6f);
			}
		}
	}

	/** Украденный (или конфискованный) предмет в клюве. */
	static final class HeldItem<T extends GooseEntity> extends BlockAndItemGeoLayer<T> {
		HeldItem(GooseRenderer<T> renderer) {
			super(renderer, (bone, e) -> "head".equals(bone.getName()) && e.hasHeld() ? e.getHeld() : null, (bone, e) -> null);
		}

		@Override
		protected ModelTransformationMode getTransformTypeForStack(GeoBone bone, ItemStack stack, T e) {
			return ModelTransformationMode.GROUND;
		}

		@Override
		protected void renderStackForBone(MatrixStack m, GeoBone bone, ItemStack stack, T e, VertexConsumerProvider vcp,
		                                  float tickDelta, int light, int overlay) {
			// От опоры головы — к кончику клюва.
			m.translate(0, 2.5 / 16.0, -7.0 / 16.0);
			m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
			m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
			m.scale(0.55f, 0.55f, 0.55f);
			super.renderStackForBone(m, bone, stack, e, vcp, tickDelta, light, overlay);
		}
	}
}
