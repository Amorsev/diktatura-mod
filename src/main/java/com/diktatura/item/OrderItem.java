package com.diktatura.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Орден «За ничью с вождём»: память о финальном бое (иммунитет выдаётся при награждении). */
public class OrderItem extends Item {
	public OrderItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.diktatura.order.lore1").formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("item.diktatura.order.lore2").formatted(Formatting.GRAY, Formatting.ITALIC));
	}
}
