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

	private ModItems() {
	}

	public static void register() {
		Registry.register(Registries.ITEM, Diktatura.id("law_book"), LAW_BOOK);
		Registry.register(Registries.ITEM, Diktatura.id("parcel"), PARCEL);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.add(LAW_BOOK);
			entries.add(PARCEL);
		});
	}
}
