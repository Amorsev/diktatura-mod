package com.diktatura.job;

import com.diktatura.Diktatura;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Rarity;

/** «Универсальный супер модульный королевский блок» — материал королевской стройки. */
public final class RoyalBlocks {
	public static final Block BLOCK = Registry.register(Registries.BLOCK, Diktatura.id("royal_block"),
		new Block(AbstractBlock.Settings.create().mapColor(MapColor.GOLD).strength(-1.0f, 3600000.0f).dropsNothing()
			.sounds(BlockSoundGroup.AMETHYST_BLOCK).luminance(s -> 6).pistonBehavior(PistonBehavior.BLOCK)));
	public static final Item ITEM = Registry.register(Registries.ITEM, Diktatura.id("royal_block"),
		new RoyalBlockItem(BLOCK, new Item.Settings().rarity(Rarity.EPIC)));

	private RoyalBlocks() {
	}

	public static void register() {
		// Поля регистрируются при загрузке класса.
	}
}
