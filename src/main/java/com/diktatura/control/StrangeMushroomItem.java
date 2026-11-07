package com.diktatura.control;

import com.diktatura.config.DiktaturaConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AliasedBlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/** Странный гриб: очень сытный, но потом полминуты «искажений» (если они включены в настройках). */
public class StrangeMushroomItem extends AliasedBlockItem {
	public StrangeMushroomItem(Block block, Settings settings) {
		super(block, settings);
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		ItemStack r = super.finishUsing(stack, world, user);
		if (user instanceof ServerPlayerEntity sp) {
			if (DiktaturaConfig.get().distortionDrug) {
				Trips.random(sp, 20 * 30);
			} else {
				sp.sendMessage(Text.translatable("diktatura.trip.disabled").formatted(Formatting.GRAY), true);
			}
		}
		return r;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.diktatura.strange_mushroom.lore1").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.translatable("item.diktatura.strange_mushroom.lore2").formatted(Formatting.GRAY, Formatting.ITALIC));
	}
}
