package com.diktatura.client.control;

import com.diktatura.Diktatura;
import com.diktatura.control.CurfewDoubleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Двойник рисуется скином своего оригинала, с красными глазами и «мерцанием». */
public class CurfewDoubleRenderer extends BipedEntityRenderer<CurfewDoubleEntity, PlayerEntityModel<CurfewDoubleEntity>> {
	private static final RenderLayer EYES = RenderLayer.getEyes(Diktatura.id("textures/entity/double/eyes.png"));

	public CurfewDoubleRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
		this.addFeature(new ArmorFeatureRenderer<>(this,
			new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
			new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
			ctx.getModelManager()));
		this.addFeature(new EyesFeatureRenderer<>(this) {
			@Override
			public RenderLayer getEyesTexture() {
				return EYES;
			}
		});
		this.addFeature(new Flicker(this));
	}

	@Override
	public Identifier getTexture(CurfewDoubleEntity entity) {
		UUID owner = entity.getOwnerId().orElse(null);
		var handler = MinecraftClient.getInstance().getNetworkHandler();
		if (owner != null && handler != null) {
			PlayerListEntry entry = handler.getPlayerListEntry(owner);
			if (entry != null) {
				return entry.getSkinTextures().texture();
			}
			return DefaultSkinHelper.getSkinTextures(owner).texture();
		}
		return DefaultSkinHelper.getTexture();
	}

	@Override
	public void render(CurfewDoubleEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
		PlayerEntityModel<CurfewDoubleEntity> m = getModel();
		m.sneaking = e.isInSneakingPose();
		m.rightArmPose = e.getMainHandStack().isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
		m.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
		super.render(e, yaw, tickDelta, matrices, vcp, light);
	}

	/** Иногда двойник «мерцает» красной копией — он же из Бюро. */
	static class Flicker extends FeatureRenderer<CurfewDoubleEntity, PlayerEntityModel<CurfewDoubleEntity>> {
		Flicker(FeatureRendererContext<CurfewDoubleEntity, PlayerEntityModel<CurfewDoubleEntity>> ctx) {
			super(ctx);
		}

		@Override
		public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, CurfewDoubleEntity e, float limbAngle,
		                   float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
			if (((int) age + e.getId()) % 40 > 3) {
				return;
			}
			var vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(getTexture(e)));
			matrices.push();
			matrices.translate(0.08 * Math.sin(age), 0, 0);
			getContextModel().render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0x73FF3333);
			matrices.pop();
		}
	}
}
