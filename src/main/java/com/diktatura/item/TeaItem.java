package com.diktatura.item;

import com.diktatura.mechanics.Colds;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.ui.Hud;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/** Казённый чай: пьётся как зелье, лечит простуду, возвращает пузырёк. */
public class TeaItem extends Item {
	public TeaItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		return ItemUsage.consumeHeldItem(world, user, hand);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.DRINK;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 32;
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (user instanceof ServerPlayerEntity p) {
			boolean cured = Colds.cure(p);
			Hud.alert(p, cured ? AlertKind.GOOD : AlertKind.INFO,
				Text.translatable(cured ? "diktatura.tea.cured" : "diktatura.tea.healthy"), Text.empty(), 4);
		}
		if (user instanceof PlayerEntity p && p.getAbilities().creativeMode) {
			return stack;
		}
		stack.decrement(1);
		if (stack.isEmpty()) {
			return new ItemStack(Items.GLASS_BOTTLE);
		}
		if (user instanceof PlayerEntity p) {
			p.getInventory().offerOrDrop(new ItemStack(Items.GLASS_BOTTLE));
		}
		return stack;
	}
}
