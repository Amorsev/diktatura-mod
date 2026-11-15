package com.diktatura.client.goose;

import com.diktatura.Diktatura;
import com.diktatura.goose.GooseEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
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
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

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

		/**
		 * Голова поворачивается за взглядом поверх анимации. Угол задаётся заново каждый кадр от исходного
		 * положения кости (а не прибавляется к текущему — иначе голова закручивалась без конца).
		 */
		@Override
		public void setCustomAnimations(T e, long instanceId, AnimationState<T> state) {
			GeoBone head = getAnimationProcessor().getBone("head");
			if (head == null) {
				return;
			}
			if (e.isHissing()) {
				head.setRotY(head.getInitialSnapshot().getRotY());
				return; // в позе шипения наклон головы задан анимацией
			}
			EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
			if (data == null) {
				return;
			}
			float yaw = MathHelper.clamp(MathHelper.wrapDegrees(data.netHeadYaw()), -50f, 50f);
			float pitch = MathHelper.clamp(data.headPitch(), -40f, 40f);
			head.setRotY(head.getInitialSnapshot().getRotY() + yaw * MathHelper.RADIANS_PER_DEGREE);
			head.setRotX(head.getInitialSnapshot().getRotX() + pitch * MathHelper.RADIANS_PER_DEGREE * 0.6f);
		}
	}

	/**
	 * Украденный (или конфискованный) предмет в клюве. Слой вызывается, когда кость головы уже повёрнута
	 * (вместе с шеей), поэтому только переносимся в её опору — без повторного поворота, как делает
	 * стандартный BlockAndItemGeoLayer (из-за него предмет «уезжал» из клюва).
	 */
	static final class HeldItem<T extends GooseEntity> extends GeoRenderLayer<T> {
		HeldItem(GooseRenderer<T> renderer) {
			super(renderer);
		}

		@Override
		public void renderForBone(MatrixStack m, T e, GeoBone bone, RenderLayer renderType, VertexConsumerProvider vcp,
		                          VertexConsumer buffer, float tickDelta, int light, int overlay) {
			if (!"head".equals(bone.getName()) || !e.hasHeld()) {
				return;
			}
			ItemStack stack = e.getHeld();
			m.push();
			RenderUtil.translateToPivotPoint(m, bone);
			// От опоры головы — к кончику клюва (клюв: 2–3 пикселя выше опоры, 3.5–7.5 пикселя вперёд).
			m.translate(0, 2.4 / 16.0, -6.5 / 16.0);
			m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
			m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
			m.scale(0.55f, 0.55f, 0.55f);
			MinecraftClient.getInstance().getItemRenderer().renderItem(e, stack, ModelTransformationMode.GROUND, false,
				m, vcp, e.getWorld(), light, OverlayTexture.DEFAULT_UV, e.getId());
			m.pop();
		}
	}
}
