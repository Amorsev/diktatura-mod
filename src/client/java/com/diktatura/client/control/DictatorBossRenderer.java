package com.diktatura.client.control;

import com.diktatura.client.npc.NpcModels;
import com.diktatura.finale.DictatorBossEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Identifier;

/** Вождь в финальном бою: та же модель из Blockbench, что у вождя на троне, со скипетром в руке. */
public class DictatorBossRenderer extends BipedEntityRenderer<DictatorBossEntity, BipedEntityModel<DictatorBossEntity>> {
	private static final Identifier TEXTURE = NpcModels.texture("dictator");

	public DictatorBossRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, NpcModels.model(ctx, "dictator"), 0.6f);
	}

	@Override
	public Identifier getTexture(DictatorBossEntity entity) {
		return TEXTURE;
	}
}
