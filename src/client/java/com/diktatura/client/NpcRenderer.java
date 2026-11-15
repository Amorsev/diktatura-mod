package com.diktatura.client;

import com.diktatura.Diktatura;
import com.diktatura.client.npc.GeoNpcRenderer;
import com.diktatura.npc.NpcEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * НПС мода — модели GeckoLib с анимациями ({@link GeoNpcRenderer}): вождь в фуражке и с плащом, инспектор в котелке,
 * крупье с козырьком и т. д. НПС без своей модели (например, добавленные датапаком) — по-старому: скин
 * {@code textures/entity/npc/<id>.png} на модели игрока.
 */
public class NpcRenderer extends EntityRenderer<NpcEntity> {
	private final GeoNpcRenderer geo;
	private final SkinRenderer skin;

	public NpcRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.geo = new GeoNpcRenderer(ctx);
		this.skin = new SkinRenderer(ctx);
		this.shadowRadius = 0.5f;
	}

	@Override
	public void render(NpcEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light) {
		if (GeoNpcRenderer.hasModel(e)) {
			geo.render(e, yaw, tickDelta, m, vcp, light);
		} else {
			skin.render(e, yaw, tickDelta, m, vcp, light);
		}
	}

	@Override
	public Identifier getTexture(NpcEntity e) {
		return GeoNpcRenderer.hasModel(e) ? geo.getTexture(e) : skin.getTexture(e);
	}

	/** Скин на модели игрока — для НПС без своей модели. */
	static final class SkinRenderer extends BipedEntityRenderer<NpcEntity, PlayerEntityModel<NpcEntity>> {
		private static final Identifier FALLBACK = Diktatura.id("textures/entity/npc/dictator.png");
		private static final Map<String, Identifier> CACHE = new HashMap<>();

		SkinRenderer(EntityRendererFactory.Context ctx) {
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
}
