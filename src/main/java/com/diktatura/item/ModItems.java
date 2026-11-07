package com.diktatura.item;

import com.diktatura.Diktatura;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
	/** Книга законов: ПКМ открывает список действующих законов (экран на клиенте). */
	public static final Item LAW_BOOK = new Item(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON));

	/** Госпосылка для работы «доставщик». Что внутри — государственная тайна. */
	public static final Item PARCEL = new Item(new Item.Settings().maxCount(16));

	/** Квитанция об уплате налога: спасает от одной налоговой проверки. */
	public static final Item TAX_RECEIPT = new Item(new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON));
	/** Посылка-сюрприз от госпочты: ПКМ — открыть. Внутри что угодно. */
	public static final Item MYSTERY_PARCEL = new Item(new Item.Settings().maxCount(16));

	/** Казённый чай: лечит простуду. */
	public static final Item TEA = new TeaItem(new Item.Settings().maxCount(16));
	/** Ластик канцелярии: если лежит в инвентаре при аресте — стирает протокол (одноразовый). */
	public static final Item ERASER = new Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE));
	/** Тапок правосудия: бросок-бумеранг. */
	public static final Item SLIPPER = new SlipperItem(new Item.Settings().maxDamage(64));
	/** Госпушка «Драконобой»: очередь лучей при зажатой ПКМ. */
	public static final Item CANNON = new CannonItem(new Item.Settings().maxDamage(1024).rarity(Rarity.EPIC));

	/** Яйца призыва гусей (творческий режим). */
	public static final Item WILD_GOOSE_SPAWN_EGG = new net.minecraft.item.SpawnEggItem(com.diktatura.npc.ModEntities.WILD_GOOSE,
		0xE6E6E0, 0xF2A33A, new Item.Settings());
	public static final Item CORRUPTED_GOOSE_SPAWN_EGG = new net.minecraft.item.SpawnEggItem(com.diktatura.npc.ModEntities.CORRUPTED_GOOSE,
		0x3A3E4A, 0xAA1419, new Item.Settings());

	private ModItems() {
	}

	public static void register() {
		Registry.register(Registries.ITEM, Diktatura.id("law_book"), LAW_BOOK);
		Registry.register(Registries.ITEM, Diktatura.id("parcel"), PARCEL);
		Registry.register(Registries.ITEM, Diktatura.id("tax_receipt"), TAX_RECEIPT);
		Registry.register(Registries.ITEM, Diktatura.id("mystery_parcel"), MYSTERY_PARCEL);
		Registry.register(Registries.ITEM, Diktatura.id("tea"), TEA);
		Registry.register(Registries.ITEM, Diktatura.id("eraser"), ERASER);
		Registry.register(Registries.ITEM, Diktatura.id("slipper"), SLIPPER);
		Registry.register(Registries.ITEM, Diktatura.id("cannon"), CANNON);
		Registry.register(Registries.ITEM, Diktatura.id("wild_goose_spawn_egg"), WILD_GOOSE_SPAWN_EGG);
		Registry.register(Registries.ITEM, Diktatura.id("corrupted_goose_spawn_egg"), CORRUPTED_GOOSE_SPAWN_EGG);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
			entries.add(WILD_GOOSE_SPAWN_EGG);
			entries.add(CORRUPTED_GOOSE_SPAWN_EGG);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.add(LAW_BOOK);
			entries.add(PARCEL);
			entries.add(TAX_RECEIPT);
			entries.add(MYSTERY_PARCEL);
			entries.add(TEA);
			entries.add(ERASER);
			entries.add(SLIPPER);
			entries.add(CANNON);
		});
	}
}
