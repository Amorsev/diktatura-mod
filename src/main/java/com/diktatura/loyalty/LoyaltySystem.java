package com.diktatura.loyalty;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Лояльность (0–100) и страх (0–100) у каждого игрока свои.
 * Без нарушений лояльность растёт, страх спадает. Низкая лояльность — слежка и обыски,
 * высокая — награды. Средняя лояльность ниже порога запускает восстание.
 */
public final class LoyaltySystem {
	private LoyaltySystem() {
	}

	public static void change(ServerPlayerEntity p, int delta) {
		MinecraftServer server = p.getServer();
		if (server == null) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(p);
		rec.loyalty = Math.max(0, Math.min(100, rec.loyalty + delta));
		state.markDirty();
	}

	public static void addFear(ServerPlayerEntity p, int delta) {
		MinecraftServer server = p.getServer();
		if (server == null) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(p);
		rec.fear = Math.max(0, Math.min(100, rec.fear + delta));
		state.markDirty();
	}

	public static boolean isRebellion(MinecraftServer server) {
		return DiktaturaState.get(server).rebellionTicks > 0;
	}

	public static void tick(MinecraftServer server) {
		long t = Diktatura.ticks();
		if (t % 20 != 0) {
			return;
		}
		boolean minute = t % 1200 == 0;
		DiktaturaConfig cfg = DiktaturaConfig.get();
		DiktaturaState state = DiktaturaState.get(server);
		List<ServerPlayerEntity> online = new ArrayList<>(server.getPlayerManager().getPlayerList());

		for (ServerPlayerEntity p : online) {
			PlayerRecord rec = state.player(p);
			if (rec.timeoutTicks > 0) {
				rec.timeoutTicks = Math.max(0, rec.timeoutTicks - 20);
			}
			if (rec.stage != Stage.FREE) {
				continue;
			}
			rec.cleanTicks += 20;
			if (rec.cleanTicks >= 1200) {
				rec.cleanTicks -= 1200;
				// Страх делает людей послушнее: при сильном страхе лояльность растёт вдвое быстрее.
				int regen = cfg.loyaltyRegenPerMinute * (rec.fear >= 70 ? 2 : 1);
				rec.loyalty = Math.min(100, rec.loyalty + regen);
				rec.fear = Math.max(0, rec.fear - 2);
			}
			if (minute) {
				if (rec.loyalty <= cfg.lowLoyalty) {
					suspect(p);
				}
				if (rec.loyalty >= cfg.highLoyalty) {
					rec.rewardTicks += 1200;
					if (rec.rewardTicks >= 12000) {
						rec.rewardTicks = 0;
						p.getInventory().offerOrDrop(new ItemStack(Items.EMERALD, 2));
						p.addStatusEffect(new StatusEffectInstance(StatusEffects.HERO_OF_THE_VILLAGE, 20 * 120, 0));
						p.sendMessage(Text.translatable("diktatura.loyalty.reward").formatted(Formatting.GREEN), false);
					}
				}
			}
		}

		if (state.rebellionCooldown > 0) {
			state.rebellionCooldown = Math.max(0, state.rebellionCooldown - 20);
		}
		if (state.rebellionTicks > 0) {
			state.rebellionTicks = Math.max(0, state.rebellionTicks - 20);
			if (state.rebellionTicks == 0) {
				endRebellion(server, state, online);
			}
		} else if (minute && state.rebellionCooldown == 0 && !online.isEmpty()) {
			double avg = online.stream().mapToInt(p -> state.player(p).loyalty).average().orElse(100);
			if (avg < cfg.rebellionThreshold) {
				startRebellion(server, state, cfg);
			}
		}
		state.markDirty();
	}

	/** Неблагонадёжный: подсветка для всех и случайный обыск. */
	private static void suspect(ServerPlayerEntity p) {
		p.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 65, 0, false, false));
		if (p.getRandom().nextFloat() < 0.15f) {
			PlayerInventory inv = p.getInventory();
			List<Integer> slots = new ArrayList<>();
			for (int i = 0; i < inv.size(); i++) {
				if (!inv.getStack(i).isEmpty()) {
					slots.add(i);
				}
			}
			if (!slots.isEmpty()) {
				int slot = slots.get(p.getRandom().nextInt(slots.size()));
				ItemStack s = inv.getStack(slot);
				int n = Math.max(1, s.getCount() / 2);
				Text name = s.getName();
				s.decrement(n);
				inv.markDirty();
				p.sendMessage(Text.translatable("diktatura.loyalty.search", n, name).formatted(Formatting.RED), false);
				return;
			}
		}
		p.sendMessage(Text.translatable("diktatura.loyalty.watched").formatted(Formatting.DARK_RED), true);
	}

	private static void startRebellion(MinecraftServer server, DiktaturaState state, DiktaturaConfig cfg) {
		state.rebellionTicks = cfg.rebellionMinutes * 1200;
		state.rebellionCooldown = (cfg.rebellionMinutes + cfg.rebellionCooldownMinutes) * 1200;
		server.getPlayerManager().broadcast(Text.translatable("diktatura.rebellion.start", cfg.rebellionMinutes)
			.formatted(Formatting.RED, Formatting.BOLD), false);
	}

	private static void endRebellion(MinecraftServer server, DiktaturaState state, List<ServerPlayerEntity> online) {
		for (ServerPlayerEntity p : online) {
			PlayerRecord rec = state.player(p);
			rec.loyalty = Math.max(rec.loyalty, 40);
			rec.fear = Math.min(100, rec.fear + 20);
		}
		server.getPlayerManager().broadcast(Text.translatable("diktatura.rebellion.end")
			.formatted(Formatting.GOLD, Formatting.BOLD), false);
	}
}
