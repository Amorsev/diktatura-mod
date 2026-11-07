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

	/** Летящий тапок правосудия. */
	public static final EntityType<com.diktatura.item.SlipperEntity> SLIPPER = Registry.register(Registries.ENTITY_TYPE,
		Diktatura.id("slipper"),
		EntityType.Builder.<com.diktatura.item.SlipperEntity>create(com.diktatura.item.SlipperEntity::new, SpawnGroup.MISC)
			.dimensions(0.4f, 0.4f)
			.maxTrackingRange(4)
			.trackingTickInterval(10)
			.build(Diktatura.id("slipper").toString()));

	/** Дикий гусь: бродит по миру и ворует вещи. */
	public static final EntityType<com.diktatura.goose.WildGooseEntity> WILD_GOOSE = Registry.register(Registries.ENTITY_TYPE,
		Diktatura.id("wild_goose"),
		EntityType.Builder.<com.diktatura.goose.WildGooseEntity>create(com.diktatura.goose.WildGooseEntity::new, SpawnGroup.CREATURE)
			.dimensions(0.6f, 1.1f)
			.maxTrackingRange(10)
			.build(Diktatura.id("wild_goose").toString()));

	/** Служебный гусь таможни: ходит за таможенником, неуязвим. */
	public static final EntityType<com.diktatura.goose.CorruptedGooseEntity> CORRUPTED_GOOSE = Registry.register(Registries.ENTITY_TYPE,
		Diktatura.id("corrupted_goose"),
		EntityType.Builder.<com.diktatura.goose.CorruptedGooseEntity>create(com.diktatura.goose.CorruptedGooseEntity::new, SpawnGroup.MISC)
			.dimensions(0.6f, 1.1f)
			.maxTrackingRange(10)
			.build(Diktatura.id("corrupted_goose").toString()));

	/** Двойник-агент комендантского часа. */
	public static final EntityType<com.diktatura.control.CurfewDoubleEntity> CURFEW_DOUBLE = Registry.register(Registries.ENTITY_TYPE,
		Diktatura.id("curfew_double"),
		EntityType.Builder.<com.diktatura.control.CurfewDoubleEntity>create(com.diktatura.control.CurfewDoubleEntity::new, SpawnGroup.MONSTER)
			.dimensions(0.6f, 1.8f)
			.eyeHeight(1.62f)
			.maxTrackingRange(10)
			.build(Diktatura.id("curfew_double").toString()));

	/** Снайпер госохраны (скелет с лазерным прицелом). */
	public static final EntityType<com.diktatura.control.SniperSkeletonEntity> SNIPER = Registry.register(Registries.ENTITY_TYPE,
		Diktatura.id("sniper"),
		EntityType.Builder.<com.diktatura.control.SniperSkeletonEntity>create(com.diktatura.control.SniperSkeletonEntity::new, SpawnGroup.MONSTER)
			.dimensions(0.6f, 1.99f)
			.eyeHeight(1.74f)
			.maxTrackingRange(10)
			.build(Diktatura.id("sniper").toString()));

	/** Вождь в финальном бою. */
	public static final EntityType<com.diktatura.finale.DictatorBossEntity> DICTATOR_BOSS = Registry.register(Registries.ENTITY_TYPE,
		Diktatura.id("dictator_boss"),
		EntityType.Builder.<com.diktatura.finale.DictatorBossEntity>create(com.diktatura.finale.DictatorBossEntity::new, SpawnGroup.MONSTER)
			.dimensions(0.6f, 1.95f)
			.eyeHeight(1.62f)
			.makeFireImmune()
			.maxTrackingRange(10)
			.build(Diktatura.id("dictator_boss").toString()));

	/** Руда-эмигрант на ножках. */
	public static final EntityType<com.diktatura.ore.RunawayOreEntity> RUNAWAY_ORE = Registry.register(Registries.ENTITY_TYPE,
		Diktatura.id("runaway_ore"),
		EntityType.Builder.<com.diktatura.ore.RunawayOreEntity>create(com.diktatura.ore.RunawayOreEntity::new, SpawnGroup.MISC)
			.dimensions(0.8f, 1.3f)
			.maxTrackingRange(8)
			.build(Diktatura.id("runaway_ore").toString()));

	private ModEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(NPC, NpcEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WILD_GOOSE, com.diktatura.goose.GooseEntity.createGooseAttributes());
		FabricDefaultAttributeRegistry.register(CORRUPTED_GOOSE, com.diktatura.goose.GooseEntity.createGooseAttributes()
			.add(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE, 1.3));
		com.diktatura.goose.GooseSpawner.register();
		FabricDefaultAttributeRegistry.register(CURFEW_DOUBLE, com.diktatura.control.CurfewDoubleEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(DICTATOR_BOSS, com.diktatura.finale.DictatorBossEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(RUNAWAY_ORE, com.diktatura.ore.RunawayOreEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SNIPER, com.diktatura.control.SniperSkeletonEntity.createSniperAttributes());
	}
}
