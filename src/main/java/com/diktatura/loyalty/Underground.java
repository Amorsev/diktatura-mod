package com.diktatura.loyalty;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.NpcEntity;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import com.diktatura.ui.Hud;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Подполье: восстание не начинается само, его поднимают игроки. Подпольные листовки (крафт из бумаги
 * и красного красителя) разбрасывают только неблагонадёжные (лояльность ≤ 40) и не слишком
 * запуганные (страх < 60) граждане. Каждая листовка — шаг брожения умов; когда наберётся
 * {@code rebellionLeaflets}, вспыхивает восстание (ночью — Кровавая луна).
 * Если рядом чиновник, железный голем или служебный гусь — листовку отбирают, страх растёт.
 * Брожение медленно утихает: одна листовка забывается раз в 5 минут.
 */
public final class Underground {
	public static final int MAX_LOYALTY = 40;
	public static final int MAX_FEAR = 60;
	private static final int COOLDOWN_TICKS = 20 * 15;
	private static final int DECAY_TICKS = 20 * 60 * 5;
	private static final double WITNESS_RADIUS = 10.0;
	private static final Map<UUID, Long> LAST_USE = new HashMap<>();

	private Underground() {
	}

	/** Разбросать листовку. true — листовка израсходована. */
	public static boolean scatter(ServerPlayerEntity p, ItemStack stack) {
		MinecraftServer server = p.getServer();
		if (server == null) {
			return false;
		}
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(p);
		DiktaturaConfig cfg = DiktaturaConfig.get();
		if (rec.stage != Stage.FREE) {
			p.sendMessage(Text.translatable("diktatura.underground.jailed").formatted(Formatting.GRAY), true);
			return false;
		}
		if (LoyaltySystem.isRebellion(server)) {
			p.sendMessage(Text.translatable("diktatura.underground.already").formatted(Formatting.GOLD), true);
			return false;
		}
		if (state.rebellionCooldown > 0) {
			p.sendMessage(Text.translatable("diktatura.underground.tired", Math.max(1, state.rebellionCooldown / 1200))
				.formatted(Formatting.GRAY), true);
			return false;
		}
		if (rec.loyalty > MAX_LOYALTY) {
			p.sendMessage(Text.translatable("diktatura.underground.too_loyal", rec.loyalty, MAX_LOYALTY).formatted(Formatting.GRAY), true);
			return false;
		}
		if (rec.fear >= MAX_FEAR) {
			p.sendMessage(Text.translatable("diktatura.underground.too_scared", rec.fear, MAX_FEAR).formatted(Formatting.GRAY), true);
			return false;
		}
		long now = Diktatura.ticks();
		Long last = LAST_USE.get(p.getUuid());
		if (last != null && now - last < COOLDOWN_TICKS) {
			p.sendMessage(Text.translatable("diktatura.underground.wait", (COOLDOWN_TICKS - (now - last)) / 20 + 1)
				.formatted(Formatting.GRAY), true);
			return false;
		}
		LAST_USE.put(p.getUuid(), now);

		// Листовки разлетаются.
		p.getServerWorld().spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, stack.copyWithCount(1)),
			p.getX(), p.getEyeY(), p.getZ(), 24, 1.2, 0.6, 1.2, 0.08);
		p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1f, 0.8f);

		LivingEntity witness = witness(p);
		if (witness != null) {
			rec.fear = Math.min(100, rec.fear + 15);
			rec.loyalty = Math.max(0, rec.loyalty - 5);
			state.markDirty();
			Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.underground.caught"),
				Text.translatable("diktatura.underground.caught.hint", witness.getDisplayName()), 6);
			return true;
		}

		state.unrest++;
		state.markDirty();
		int need = Math.max(1, cfg.rebellionLeaflets);
		if (state.unrest >= need) {
			state.unrest = 0;
			server.getPlayerManager().broadcast(Text.translatable("diktatura.underground.boiled", p.getDisplayName())
				.formatted(Formatting.RED, Formatting.BOLD), false);
			LoyaltySystem.startRebellion(server);
		} else {
			Hud.alert(p, AlertKind.EVENT, Text.translatable("diktatura.underground.scattered", state.unrest, need),
				Text.translatable("diktatura.underground.scattered.hint"), 4);
		}
		return true;
	}

	/** Кто из бдительных стоит рядом и видит игрока. */
	private static LivingEntity witness(ServerPlayerEntity p) {
		Box box = p.getBoundingBox().expand(WITNESS_RADIUS);
		List<LivingEntity> near = p.getServerWorld().getEntitiesByClass(LivingEntity.class, box,
			e -> e != p && e.isAlive() && (e instanceof NpcEntity || e instanceof IronGolemEntity
				|| e instanceof com.diktatura.goose.CorruptedGooseEntity) && e.canSee(p));
		return near.isEmpty() ? null : near.get(0);
	}

	/** Брожение утихает само: одна листовка раз в 5 минут. */
	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % DECAY_TICKS != 11) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		if (state.unrest > 0) {
			state.unrest--;
			state.markDirty();
		}
	}
}
