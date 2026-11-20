package com.diktatura.loan;

import com.diktatura.Diktatura;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Rarity;

/** Блоки и предметы кассы микрозаймов: табличка «Адрес должника» (выдаёт казначей) и справка о бедности. */
public final class LoanBlocks {
	public static final Block ADDRESS_PLATE = Registry.register(Registries.BLOCK, Diktatura.id("address_plate"),
		new AddressPlateBlock(AbstractBlock.Settings.create().mapColor(MapColor.BLUE).noCollision().strength(0.5f)
			.sounds(BlockSoundGroup.METAL).pistonBehavior(PistonBehavior.DESTROY)));
	public static final Item ADDRESS_PLATE_ITEM = Registry.register(Registries.ITEM, Diktatura.id("address_plate"),
		new BlockItem(ADDRESS_PLATE, new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));
	/** Справка о бедности (крафт: бумага + чернильный мешок). */
	public static final Item POVERTY_CERTIFICATE = Registry.register(Registries.ITEM, Diktatura.id("poverty_certificate"),
		new PovertyCertificateItem(new Item.Settings().maxCount(16)));

	private LoanBlocks() {
	}

	public static void register() {
		net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.TOOLS)
			.register(entries -> entries.add(POVERTY_CERTIFICATE));
	}
}
