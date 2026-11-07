package com.diktatura.control;

import com.diktatura.Diktatura;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

/** Блоки и предметы этапа 14 (странный гриб). */
public final class ModControl {
	public static final Block STRANGE_MUSHROOM = Registry.register(Registries.BLOCK, Diktatura.id("strange_mushroom"),
		new StrangeMushroomBlock(AbstractBlock.Settings.create().mapColor(MapColor.PINK).noCollision().breakInstantly()
			.sounds(BlockSoundGroup.GRASS).luminance(s -> 5).offset(AbstractBlock.OffsetType.XZ)
			.pistonBehavior(PistonBehavior.DESTROY)));
	public static final Item STRANGE_MUSHROOM_ITEM = Registry.register(Registries.ITEM, Diktatura.id("strange_mushroom"),
		new StrangeMushroomItem(STRANGE_MUSHROOM, new Item.Settings()
			.food(new FoodComponent.Builder().nutrition(7).saturationModifier(1.0f).alwaysEdible().build())));

	private ModControl() {
	}

	public static void register() {
		// Поля регистрируются при загрузке класса; пятна грибов — в генерацию мира.
		Trips.registerWorldgen();
	}
}
