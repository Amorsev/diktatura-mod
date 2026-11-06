package com.diktatura.client;

import com.diktatura.Diktatura;
import com.diktatura.npc.NpcEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * НПС рисуются на модели игрока (64×64, как скин). Текстура — по идентификатору НПС:
 * {@code diktatura:dictator} → {@code assets/diktatura/textures/entity/npc/dictator.png}.
 * Масштаб берётся из атрибута scale (синхронизируется сервером), предмет в руке — из экипировки.
 */
public class NpcRenderer extends BipedEntityRenderer<NpcEntity, PlayerEntityModel<NpcEntity>> {
	private static final Identifier FALLBACK = Diktatura.id("textures/entity/npc/dictator.png");
	private static final Map<String, Identifier> CACHE = new HashMap<>();

	public NpcRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
	}

	@Override
	public Identifier getTexture(NpcEntity entity) {
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
