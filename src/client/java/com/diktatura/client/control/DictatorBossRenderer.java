package com.diktatura.client.control;

import com.diktatura.Diktatura;
import com.diktatura.client.geo.HeadLook;
import com.diktatura.client.geo.HumanoidItemLayer;
import com.diktatura.client.npc.NpcModels;
import com.diktatura.finale.DictatorBossEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Вождь в финальном бою на GeckoLib: та же модель, что у вождя на троне ({@code geo/entity/npc/dictator.geo.json}),
 * анимации общие с НПС (покой, ходьба, удар скипетром). В правой руке — скипетр (или оружие приёма), в левой — книга законов.
 */
public class DictatorBossRenderer extends GeoEntityRenderer<DictatorBossEntity> {
	public DictatorBossRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new Model());
		this.shadowRadius = 0.6f;
		addRenderLayer(new HumanoidItemLayer<>(this, true));
	}

	static final class Model extends GeoModel<DictatorBossEntity> {
		private static final Identifier GEO = Diktatura.id("geo/entity/npc/dictator.geo.json");
		private static final Identifier TEXTURE = NpcModels.texture("dictator");
		private static final Identifier ANIM = Diktatura.id("animations/entity/npc.animation.json");

		@Override
		public Identifier getModelResource(DictatorBossEntity e) {
			return GEO;
		}

		@Override
		public Identifier getTextureResource(DictatorBossEntity e) {
			return TEXTURE;
		}

		@Override
		public Identifier getAnimationResource(DictatorBossEntity e) {
			return ANIM;
		}

		@Override
		public void setCustomAnimations(DictatorBossEntity e, long instanceId, AnimationState<DictatorBossEntity> state) {
			HeadLook.apply(this, state);
		}
	}
}
