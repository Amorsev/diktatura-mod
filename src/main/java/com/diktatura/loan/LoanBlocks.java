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

/** Блоки кассы микрозаймов: табличка «Адрес должника». Выдаёт казначей, в творческом меню её нет. */
public final class LoanBlocks {
	public static final Block ADDRESS_PLATE = Registry.register(Registries.BLOCK, Diktatura.id("address_plate"),
		new AddressPlateBlock(AbstractBlock.Settings.create().mapColor(MapColor.BLUE).noCollision().strength(0.5f)
			.sounds(BlockSoundGroup.METAL).pistonBehavior(PistonBehavior.DESTROY)));
	public static final Item ADDRESS_PLATE_ITEM = Registry.register(Registries.ITEM, Diktatura.id("address_plate"),
		new BlockItem(ADDRESS_PLATE, new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	private LoanBlocks() {
	}

	public static void register() {
		// Поля регистрируются при загрузке класса.
	}
}
