package com.diktatura.job;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.util.List;

/** Королевский блок ставится только в отмеченные точки королевской стройки. */
public class RoyalBlockItem extends BlockItem {
	public RoyalBlockItem(Block block, Settings settings) {
		super(block, settings);
	}

	@Override
	public ActionResult place(ItemPlacementContext context) {
		if (context.getWorld().isClient) {
			return super.place(context);
		}
		if (!(context.getPlayer() instanceof ServerPlayerEntity sp) || !RoyalSite.isTarget(sp, context.getBlockPos())) {
			if (context.getPlayer() instanceof ServerPlayerEntity sp) {
				sp.sendMessage(Text.translatable("diktatura.royal.wrong_spot").formatted(Formatting.RED), true);
			}
			return ActionResult.FAIL;
		}
		ActionResult r = super.place(context);
		if (r.isAccepted()) {
			RoyalSite.onPlaced(sp, context.getBlockPos());
		}
		return r;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("block.diktatura.royal_block.lore1").formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("block.diktatura.royal_block.lore2").formatted(Formatting.GRAY, Formatting.ITALIC));
	}
}
