package com.diktatura.client.npc;

import com.diktatura.Diktatura;
import com.diktatura.npc.NpcEntity;
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

import java.util.Set;

/**
 * НПС мода на GeckoLib: модель {@code geo/entity/npc/<id>.geo.json}, текстура {@code textures/entity/npc_model/<id>.png},
 * анимации общие ({@code animations/entity/npc.animation.json}). Голова поворачивается за взглядом (кроме жестов),
 * предмет из экипировки — в правой руке.
 */
public class GeoNpcRenderer extends GeoEntityRenderer<NpcEntity> {
	/** НПС мода со своей моделью (blockbench/gen_npcs.py → gl_build.js). */
	public static final Set<String> IDS = Set.of("dictator", "tax_inspector", "customs", "courier", "croupier",
		"dice_croupier", "slots_operator", "employer", "licensor", "recipient");

	public GeoNpcRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new Model());
		this.shadowRadius = 0.5f;
		addRenderLayer(new HeldItem(this));
	}

	/** Путь идентификатора НПС из мода (diktatura:courier → courier), если у него есть своя модель; иначе null. */
	public static String modelId(NpcEntity e) {
		Identifier id = Identifier.tryParse(e.npcId());
		if (id == null || !id.getNamespace().equals(Diktatura.MOD_ID) || !IDS.contains(id.getPath())) {
			return null;
		}
		return id.getPath();
	}

	public static boolean hasModel(NpcEntity e) {
		return modelId(e) != null;
	}

	static final class Model extends GeoModel<NpcEntity> {
		private static final Identifier ANIM = Diktatura.id("animations/entity/npc.animation.json");

		private static String id(NpcEntity e) {
			String id = modelId(e);
			return id == null ? "dictator" : id;
		}

		@Override
		public Identifier getModelResource(NpcEntity e) {
			return Diktatura.id("geo/entity/npc/" + id(e) + ".geo.json");
		}

		@Override
		public Identifier getTextureResource(NpcEntity e) {
			return NpcModels.texture(id(e));
		}

		@Override
		public Identifier getAnimationResource(NpcEntity e) {
			return ANIM;
		}

		/** Голова — за взглядом; угол задаётся от исходного положения кости каждый кадр (не накапливается). */
		@Override
		public void setCustomAnimations(NpcEntity e, long instanceId, AnimationState<NpcEntity> state) {
			if (e.isGesturing()) {
				return;
			}
			GeoBone head = getAnimationProcessor().getBone("head");
			EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
			if (head == null || data == null) {
				return;
			}
			float yaw = MathHelper.clamp(MathHelper.wrapDegrees(data.netHeadYaw()), -70f, 70f);
			float pitch = MathHelper.clamp(data.headPitch(), -45f, 45f);
			head.setRotY(head.getInitialSnapshot().getRotY() + yaw * MathHelper.RADIANS_PER_DEGREE);
			head.setRotX(head.getInitialSnapshot().getRotX() + pitch * MathHelper.RADIANS_PER_DEGREE);
		}
	}

	/**
	 * Предмет в правой руке — как у ванильного HeldItemFeatureRenderer. Слой вызывается с уже повёрнутой костью руки:
	 * переходим в её опору и разворачиваемся в координаты ванильной модели (повёрнуты на 180° вокруг Z).
	 */
	static final class HeldItem extends GeoRenderLayer<NpcEntity> {
		HeldItem(GeoNpcRenderer renderer) {
			super(renderer);
		}

		@Override
		public void renderForBone(MatrixStack m, NpcEntity e, GeoBone bone, RenderLayer renderType, VertexConsumerProvider vcp,
		                          VertexConsumer buffer, float tickDelta, int light, int overlay) {
			if (!"right_arm".equals(bone.getName())) {
				return;
			}
			ItemStack stack = e.getMainHandStack();
			if (stack.isEmpty()) {
				return;
			}
			m.push();
			RenderUtil.translateToPivotPoint(m, bone);
			m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180f));
			m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
			m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
			m.translate(1.0f / 16.0f, 0.125f, -0.625f);
			MinecraftClient.getInstance().getItemRenderer().renderItem(e, stack, ModelTransformationMode.THIRD_PERSON_RIGHT_HAND,
				false, m, vcp, e.getWorld(), light, OverlayTexture.DEFAULT_UV, e.getId());
			m.pop();
		}
	}
}
