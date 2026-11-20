package com.diktatura.loan;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Справка о бедности: ПКМ — коллекторы уходят, следующий визит откладывается на 5 минут (раз в кредитный день). */
public class PovertyCertificateItem extends Item {
	public PovertyCertificateItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) {
			return TypedActionResult.success(stack, true);
		}
		if (user instanceof ServerPlayerEntity p && Collectors.useCertificate(p)) {
			if (!p.isCreative()) {
				stack.decrement(1);
			}
			return TypedActionResult.success(stack, false);
		}
		return TypedActionResult.fail(stack);
	}
}
