package com.diktatura.client.goose;

import com.diktatura.Diktatura;
import com.diktatura.goose.GooseEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Рендер гусей: общая модель, своя текстура у дикого и служебного, предмет в клюве. */
public class GooseRenderer<T extends GooseEntity> extends MobEntityRenderer<T, GooseModel<T>> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Diktatura.id("goose"), "main");

	private final Identifier texture;

	public GooseRenderer(EntityRendererFactory.Context ctx, String textureName) {
		super(ctx, new GooseModel<>(ctx.getPart(LAYER)), 0.4f);
		this.texture = Diktatura.id("textures/entity/goose/" + textureName + ".png");
		this.addFeature(new HeldItem<>(this));
	}

	public static void registerLayer() {
		EntityModelLayerRegistry.registerModelLayer(LAYER, GooseModel::texturedModelData);
	}

	@Override
	public Identifier getTexture(T entity) {
		return texture;
	}

	@Override
	protected void scale(T entity, MatrixStack matrices, float amount) {
		if (entity.isBaby()) {
			matrices.scale(0.55f, 0.55f, 0.55f);
		}
	}

	/** Украденный (или конфискованный) предмет в клюве. */
	static class HeldItem<T extends GooseEntity> extends FeatureRenderer<T, GooseModel<T>> {
		HeldItem(FeatureRendererContext<T, GooseModel<T>> ctx) {
			super(ctx);
		}

		@Override
		public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, T e, float limbAngle,
		                   float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
			ItemStack stack = e.getHeld();
			if (stack.isEmpty()) {
				return;
			}
			GooseModel<T> m = getContextModel();
			matrices.push();
			m.neck().rotate(matrices);
			m.head().rotate(matrices);
			matrices.translate(0, -0.06, -0.42);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
			matrices.scale(0.55f, 0.55f, 0.55f);
			MinecraftClient.getInstance().getItemRenderer().renderItem(e, stack, ModelTransformationMode.GROUND, false,
				matrices, vcp, e.getWorld(), light, OverlayTexture.DEFAULT_UV, e.getId());
			matrices.pop();
		}
	}
}
