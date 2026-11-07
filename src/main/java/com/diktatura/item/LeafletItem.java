package com.diktatura.item;

import com.diktatura.loyalty.Underground;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** Подпольная листовка: ПКМ — разбросать. Поднимает брожение умов, из которого вспыхивает восстание. */
public class LeafletItem extends Item {
	public LeafletItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user instanceof ServerPlayerEntity sp && Underground.scatter(sp, stack)) {
			stack.decrementUnlessCreative(1, user);
			user.getItemCooldownManager().set(this, 20);
			return TypedActionResult.success(stack, false);
		}
		return TypedActionResult.pass(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.diktatura.leaflet.lore1").formatted(Formatting.RED));
		tooltip.add(Text.translatable("item.diktatura.leaflet.lore2", Underground.MAX_LOYALTY, Underground.MAX_FEAR)
			.formatted(Formatting.GRAY, Formatting.ITALIC));
	}
}
