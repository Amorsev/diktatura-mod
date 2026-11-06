package com.diktatura.npc;

import com.diktatura.Diktatura;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	/** Все НПС-ведомства — одна сущность; персонаж задаётся идентификатором из JSON. */
	public static final EntityType<NpcEntity> NPC = Registry.register(Registries.ENTITY_TYPE, Diktatura.id("npc"),
		EntityType.Builder.create(NpcEntity::new, SpawnGroup.MISC)
			.dimensions(0.6f, 1.95f)
			.eyeHeight(1.62f)
			.maxTrackingRange(10)
			.build(Diktatura.id("npc").toString()));

	private ModEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(NPC, NpcEntity.createAttributes());
	}
}
