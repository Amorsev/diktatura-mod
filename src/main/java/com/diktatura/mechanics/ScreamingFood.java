package com.diktatura.mechanics;

import com.diktatura.config.DiktaturaConfig;
import com.diktatura.goose.ModSounds;
import com.diktatura.loyalty.LoyaltySystem;
import com.diktatura.ui.Voice;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Кричащая еда. Иногда, когда гражданин начинает есть, еда протестует: «Не ешь меня!»
 * (озвученные реплики), а иногда — сознательная госпродукция — читает пропаганду
 * («Съешь меня во славу вождя!»). Сытости это не меняет, но аппетит портит.
 * Озвучка — из мода-референса (MIT, © 2026 Gr1tW4lk), см. CREDITS.md.
 */
public final class ScreamingFood {
	private static final int VOICED = 5;
	private static final int PROPAGANDA = 8;
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

	private ScreamingFood() {
	}

	public static void registerEvents() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (world.isClient || !(player instanceof ServerPlayerEntity sp)) {
				return TypedActionResult.pass(stack);
			}
			DiktaturaConfig cfg = DiktaturaConfig.get();
			FoodComponent food = stack.get(DataComponentTypes.FOOD);
			if (cfg.screamingFoodChance <= 0 || food == null || !player.canConsume(food.canAlwaysEat())) {
				return TypedActionResult.pass(stack);
			}
			long now = world.getTime();
			if (now - COOLDOWN.getOrDefault(player.getUuid(), -1000L) < 60) {
				return TypedActionResult.pass(stack);
			}
			COOLDOWN.put(player.getUuid(), now);
			if (world.random.nextDouble() >= cfg.screamingFoodChance) {
				return TypedActionResult.pass(stack);
			}
			Text name = stack.getName().copy().formatted(Formatting.YELLOW);
			// В восстание еда тоже бунтует; в мирное время — чаще агитирует.
			boolean propaganda = !LoyaltySystem.isRebellion(sp.getServer()) && world.random.nextInt(3) == 0;
			if (propaganda) {
				Voice.say(sp, name, Text.translatable("diktatura.food.propaganda." + world.random.nextInt(PROPAGANDA)), null, 1f);
			} else {
				int i = world.random.nextInt(VOICED);
				Voice.say(sp, name, Text.translatable("diktatura.food.scream." + i), ModSounds.FOOD_SCREAMS[i],
					0.95f + world.random.nextFloat() * 0.15f);
			}
			return TypedActionResult.pass(stack);
		});
	}

	public static void forget(UUID id) {
		COOLDOWN.remove(id);
	}
}
