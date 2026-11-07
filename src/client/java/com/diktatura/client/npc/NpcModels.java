package com.diktatura.client.npc;

import com.diktatura.Diktatura;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Свои модели НПС из Blockbench. Скелет гуманоида (head, hat, body, руки, ноги) совпадает с ванильным,
 * поэтому анимации ходьбы, поворота головы и предмета в руке — от {@link BipedEntityModel}.
 * НПС без своей модели (например, добавленные датапаком) рисуются по-старому — скином на модели игрока.
 */
public final class NpcModels {
	private static final Map<String, EntityModelLayer> LAYERS = new HashMap<>();

	static {
		for (String id : NpcModelData.IDS) {
			LAYERS.put(id, new EntityModelLayer(Diktatura.id("npc_" + id), "main"));
		}
	}

	private NpcModels() {
	}

	public static void registerLayers() {
		for (String id : NpcModelData.IDS) {
			EntityModelLayerRegistry.registerModelLayer(LAYERS.get(id), () -> NpcModelData.of(id));
		}
	}

	/** Модели всех НПС с собственной моделью: путь идентификатора (dictator, courier…) → модель. */
	public static <T extends LivingEntity> Map<String, BipedEntityModel<T>> create(EntityRendererFactory.Context ctx) {
		Map<String, BipedEntityModel<T>> out = new HashMap<>();
		for (String id : NpcModelData.IDS) {
			out.put(id, model(ctx, id));
		}
		return out;
	}

	public static <T extends LivingEntity> BipedEntityModel<T> model(EntityRendererFactory.Context ctx, String id) {
		ModelPart root = ctx.getPart(LAYERS.get(id));
		return new BipedEntityModel<>(root);
	}

	public static Identifier texture(String id) {
		return Diktatura.id("textures/entity/npc_model/" + id + ".png");
	}
}
