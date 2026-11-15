package com.diktatura.client.control;

import com.diktatura.Diktatura;
import com.diktatura.client.geo.HeadLook;
import com.diktatura.client.geo.HumanoidItemLayer;
import com.diktatura.control.CurfewDoubleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

import java.util.List;
import java.util.UUID;

/**
 * Двойник на GeckoLib: модель игрока ({@code geo/entity/player_wide|player_slim.geo.json} — по типу скина оригинала),
 * текстура — скин оригинала. Анимации общие с НПС: ходьба, присед (повторяет за оригиналом) и удар.
 * Поверх — броня и оружие оригинала, красные глаза и «мерцание» красной копией.
 */
public class CurfewDoubleRenderer extends GeoEntityRenderer<CurfewDoubleEntity> {
	private static final RenderLayer EYES = RenderLayer.getEyes(Diktatura.id("textures/entity/double/eyes.png"));

	public CurfewDoubleRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new Model());
		this.shadowRadius = 0.5f;
		addRenderLayer(new Armor(this));
		addRenderLayer(new HumanoidItemLayer<>(this, true));
		addRenderLayer(new Eyes(this));
		addRenderLayer(new Flicker(this));
	}

	static SkinTextures skin(CurfewDoubleEntity e) {
		UUID owner = e.getOwnerId().orElse(null);
		var handler = MinecraftClient.getInstance().getNetworkHandler();
		if (owner == null) {
			return DefaultSkinHelper.getSkinTextures(new UUID(0, 0));
		}
		if (handler != null) {
			PlayerListEntry entry = handler.getPlayerListEntry(owner);
			if (entry != null) {
				return entry.getSkinTextures();
			}
		}
		return DefaultSkinHelper.getSkinTextures(owner);
	}

	static final class Model extends GeoModel<CurfewDoubleEntity> {
		private static final Identifier WIDE = Diktatura.id("geo/entity/player_wide.geo.json");
		private static final Identifier SLIM = Diktatura.id("geo/entity/player_slim.geo.json");
		private static final Identifier ANIM = Diktatura.id("animations/entity/npc.animation.json");
		/** Опоры брони: их кубы не рисуются, по ним подгоняется ванильная броня. */
		private static final List<String> ARMOR_BONES = List.of("armor_head", "armor_body", "armor_right_arm", "armor_left_arm",
			"armor_legs_body", "armor_right_leg", "armor_left_leg", "armor_right_boot", "armor_left_boot");

		@Override
		public Identifier getModelResource(CurfewDoubleEntity e) {
			return skin(e).model() == SkinTextures.Model.SLIM ? SLIM : WIDE;
		}

		@Override
		public Identifier getTextureResource(CurfewDoubleEntity e) {
			return skin(e).texture();
		}

		@Override
		public Identifier getAnimationResource(CurfewDoubleEntity e) {
			return ANIM;
		}

		@Override
		public void setCustomAnimations(CurfewDoubleEntity e, long instanceId, AnimationState<CurfewDoubleEntity> state) {
			for (String name : ARMOR_BONES) {
				GeoBone bone = getAnimationProcessor().getBone(name);
				if (bone != null) {
					bone.setHidden(true);
				}
			}
			HeadLook.apply(this, state);
		}
	}

	/** Броня оригинала — ванильные модели брони, подогнанные по костям armor_*. */
	static final class Armor extends ItemArmorGeoLayer<CurfewDoubleEntity> {
		Armor(CurfewDoubleRenderer renderer) {
			super(renderer);
		}

		@Nullable
		@Override
		protected ItemStack getArmorItemForBone(GeoBone bone, CurfewDoubleEntity e) {
			return switch (bone.getName()) {
				case "armor_head" -> helmetStack;
				case "armor_body", "armor_right_arm", "armor_left_arm" -> chestplateStack;
				case "armor_legs_body", "armor_right_leg", "armor_left_leg" -> leggingsStack;
				case "armor_right_boot", "armor_left_boot" -> bootsStack;
				default -> null;
			};
		}

		@Override
		protected EquipmentSlot getEquipmentSlotForBone(GeoBone bone, ItemStack stack, CurfewDoubleEntity e) {
			return switch (bone.getName()) {
				case "armor_head" -> EquipmentSlot.HEAD;
				case "armor_legs_body", "armor_right_leg", "armor_left_leg" -> EquipmentSlot.LEGS;
				case "armor_right_boot", "armor_left_boot" -> EquipmentSlot.FEET;
				default -> EquipmentSlot.CHEST;
			};
		}

		@Override
		protected ModelPart getModelPartForBone(GeoBone bone, EquipmentSlot slot, ItemStack stack, CurfewDoubleEntity e,
		                                        BipedEntityModel<?> base) {
			return switch (bone.getName()) {
				case "armor_head" -> base.head;
				case "armor_right_arm" -> base.rightArm;
				case "armor_left_arm" -> base.leftArm;
				case "armor_right_leg", "armor_right_boot" -> base.rightLeg;
				case "armor_left_leg", "armor_left_boot" -> base.leftLeg;
				default -> base.body;
			};
		}
	}

	/** Красные светящиеся глаза поверх скина. */
	static final class Eyes extends GeoRenderLayer<CurfewDoubleEntity> {
		Eyes(CurfewDoubleRenderer renderer) {
			super(renderer);
		}

		@Override
		public void render(MatrixStack m, CurfewDoubleEntity e, BakedGeoModel model, @Nullable RenderLayer renderType,
		                   VertexConsumerProvider vcp, @Nullable VertexConsumer buffer, float tickDelta, int light, int overlay) {
			getRenderer().reRender(model, m, vcp, e, EYES, vcp.getBuffer(EYES), tickDelta,
				LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, 0xFFFFFFFF);
		}
	}

	/** Иногда двойник «мерцает» красной копией — он же из Бюро. */
	static final class Flicker extends GeoRenderLayer<CurfewDoubleEntity> {
		Flicker(CurfewDoubleRenderer renderer) {
			super(renderer);
		}

		@Override
		public void render(MatrixStack m, CurfewDoubleEntity e, BakedGeoModel model, @Nullable RenderLayer renderType,
		                   VertexConsumerProvider vcp, @Nullable VertexConsumer buffer, float tickDelta, int light, int overlay) {
			float age = e.age + tickDelta;
			if (((int) age + e.getId()) % 40 > 3) {
				return;
			}
			RenderLayer layer = RenderLayer.getEntityTranslucent(getTextureResource(e));
			m.push();
			m.translate(0.08 * Math.sin(age), 0, 0);
			getRenderer().reRender(model, m, vcp, e, layer, vcp.getBuffer(layer), tickDelta, light, overlay, 0x73FF3333);
			m.pop();
		}
	}
}
