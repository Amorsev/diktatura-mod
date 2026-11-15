package com.diktatura.client.npc;

import com.diktatura.Diktatura;
import net.minecraft.util.Identifier;

/** Текстуры моделей НПС из Blockbench ({@code textures/entity/npc_model/<id>.png}); сами модели — GeckoLib ({@code geo/entity/npc}). */
public final class NpcModels {
	private NpcModels() {
	}

	public static Identifier texture(String id) {
		return Diktatura.id("textures/entity/npc_model/" + id + ".png");
	}
}
