package com.diktatura.client;

import com.diktatura.Diktatura;
import com.diktatura.client.npc.NpcModels;
import com.diktatura.npc.NpcEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * НПС мода рисуются своими моделями из Blockbench ({@link NpcModels}): вождь в фуражке и с плащом,
 * инспектор в котелке, крупье с козырьком и т. д. НПС без своей модели — на модели игрока со скином
 * {@code textures/entity/npc/<id>.png}. Масштаб — из атрибута scale, предмет в руке — из экипировки.
 */
public class NpcRenderer extends BipedEntityRenderer<NpcEntity, BipedEntityModel<NpcEntity>> {
	private static final Identifier FALLBACK = Diktatura.id("textures/entity/npc/dictator.png");
	private static final Map<String, Identifier> CACHE = new HashMap<>();

	private final BipedEntityModel<NpcEntity> playerModel;
	private final Map<String, BipedEntityModel<NpcEntity>> custom;

	public NpcRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
		this.playerModel = this.model;
		this.custom = NpcModels.create(ctx);
	}

	/** Путь идентификатора НПС из мода (diktatura:courier → courier), если у него есть своя модель. */
	private String customId(NpcEntity e) {
		Identifier id = Identifier.tryParse(e.npcId());
		if (id == null || !id.getNamespace().equals(Diktatura.MOD_ID) || !custom.containsKey(id.getPath())) {
			return null;
		}
		return id.getPath();
	}

	@Override
	public void render(NpcEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
		String id = customId(e);
		this.model = id == null ? playerModel : custom.get(id);
		super.render(e, yaw, tickDelta, matrices, vcp, light);
	}

	@Override
	public Identifier getTexture(NpcEntity entity) {
		String custom = customId(entity);
		if (custom != null) {
			return NpcModels.texture(custom);
		}
		String id = entity.npcId();
		if (id.isEmpty()) {
			return FALLBACK;
		}
		return CACHE.computeIfAbsent(id, k -> {
			Identifier parsed = Identifier.tryParse(k);
			return parsed == null ? FALLBACK : Identifier.of(parsed.getNamespace(), "textures/entity/npc/" + parsed.getPath() + ".png");
		});
	}
}
