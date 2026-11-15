package com.diktatura.client.npc;

import com.diktatura.Diktatura;
import com.diktatura.client.geo.HeadLook;
import com.diktatura.client.geo.HumanoidItemLayer;
import com.diktatura.npc.NpcEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.Set;

/**
 * НПС мода на GeckoLib: модель {@code geo/entity/npc/<id>.geo.json}, текстура {@code textures/entity/npc_model/<id>.png},
 * анимации общие ({@code animations/entity/npc.animation.json}). Голова поворачивается за взглядом (кроме жестов),
 * предмет из экипировки — в правой руке.
 */
public class GeoNpcRenderer extends GeoEntityRenderer<NpcEntity> {
	/** НПС мода со своей моделью (blockbench/gen_npcs.py → gl_build.js). */
	public static final Set<String> IDS = Set.of("dictator", "tax_inspector", "customs", "courier", "croupier", "poker_dealer",
		"dice_croupier", "slots_operator", "employer", "licensor", "recipient");

	public GeoNpcRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new Model());
		this.shadowRadius = 0.5f;
		addRenderLayer(new HumanoidItemLayer<>(this, false));
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
			HeadLook.apply(this, state);
		}
	}
}
