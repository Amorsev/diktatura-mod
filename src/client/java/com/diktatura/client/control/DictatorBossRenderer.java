package com.diktatura.client.control;

import com.diktatura.Diktatura;
import com.diktatura.finale.DictatorBossEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** Вождь в финальном бою: та же внешность, что у вождя на троне. */
public class DictatorBossRenderer extends BipedEntityRenderer<DictatorBossEntity, PlayerEntityModel<DictatorBossEntity>> {
	private static final Identifier TEXTURE = Diktatura.id("textures/entity/npc/dictator.png");

	public DictatorBossRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.6f);
	}

	@Override
	public Identifier getTexture(DictatorBossEntity entity) {
		return TEXTURE;
	}
}
