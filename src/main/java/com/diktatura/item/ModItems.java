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

	private ModItems() {
	}

	public static void register() {
		Registry.register(Registries.ITEM, Diktatura.id("law_book"), LAW_BOOK);
		Registry.register(Registries.ITEM, Diktatura.id("parcel"), PARCEL);
		Registry.register(Registries.ITEM, Diktatura.id("tax_receipt"), TAX_RECEIPT);
		Registry.register(Registries.ITEM, Diktatura.id("mystery_parcel"), MYSTERY_PARCEL);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.add(LAW_BOOK);
			entries.add(PARCEL);
			entries.add(TAX_RECEIPT);
			entries.add(MYSTERY_PARCEL);
		});
	}
}
