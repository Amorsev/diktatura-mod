package com.diktatura.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Тапок правосудия: ПКМ — бросок бумерангом, бьёт всех на пути и возвращается в руку.
 * Иногда — критический удар голосом мамы. Идея — из референсного мода (SlipperItem), реализация своя.
 */
public class SlipperItem extends Item {
	public SlipperItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient) {
			SlipperEntity e = new SlipperEntity(world, user, stack);
			e.setVelocity(user, user.getPitch(), user.getYaw(), 0f, 1.6f, 0.5f);
			world.spawnEntity(e);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW,
				SoundCategory.PLAYERS, 1.0f, 0.6f);
		}
		user.getItemCooldownManager().set(this, 10);
		ItemStack left = user.getAbilities().creativeMode ? stack : ItemStack.EMPTY;
		return TypedActionResult.success(left, world.isClient);
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		target.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,
			SoundCategory.PLAYERS, 1.2f, 1.4f);
		return true;
	}
}
