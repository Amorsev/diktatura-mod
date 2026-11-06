package com.diktatura.law;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.loyalty.LoyaltySystem;
import com.diktatura.punishment.PunishmentSystem;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import com.diktatura.transport.TransportSystem;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * LawEngine: подписывается на игровые события, проверяет действующие законы для каждого
 * игрока отдельно и передаёт нарушения в {@link PunishmentSystem}.
 * Наказания выполняются в начале следующего тика, чтобы не ломать обработку текущего события.
 */
public final class LawEngine {
	private static final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
	private static final Set<UUID> pickups = new HashSet<>();
	private static final Queue<Runnable> pending = new ArrayDeque<>();
	private static final Map<UUID, Long> graceUntil = new HashMap<>();

	private LawEngine() {
	}

	public static void defer(Runnable r) {
		pending.add(r);
	}

	public static void markPickup(ServerPlayerEntity p) {
		pickups.add(p.getUuid());
	}

	public static void clear() {
		cooldowns.clear();
		pickups.clear();
		pending.clear();
		graceUntil.clear();
	}

	public static void registerEvents() {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (world.isClient || !(player instanceof ServerPlayerEntity sp)) {
				return true;
			}
			LawContext c = new LawContext(sp, LawEvent.BLOCK_BREAK);
			c.pos = pos;
			c.state = state;
			return !fire(c);
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient || !(player instanceof ServerPlayerEntity sp)) {
				return ActionResult.PASS;
			}
			ItemStack stack = player.getStackInHand(hand);
			if (!(stack.getItem() instanceof BlockItem)) {
				return ActionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (!world.getBlockState(pos).isReplaceable()) {
				pos = pos.offset(hit.getSide());
			}
			LawContext c = new LawContext(sp, LawEvent.BLOCK_PLACE);
			c.pos = pos;
			c.stack = stack;
			return fire(c) ? ActionResult.FAIL : ActionResult.PASS;
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (world.isClient || !(player instanceof ServerPlayerEntity sp)) {
				return TypedActionResult.pass(stack);
			}
			LawContext c = new LawContext(sp, LawEvent.USE_ITEM);
			c.stack = stack;
			return fire(c) ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient || !(player instanceof ServerPlayerEntity sp)) {
				return ActionResult.PASS;
			}
			LawContext c = new LawContext(sp, LawEvent.ATTACK);
			c.target = entity;
			return fire(c) ? ActionResult.FAIL : ActionResult.PASS;
		});

		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
			if (killer instanceof ServerPlayerEntity sp) {
				LawContext c = new LawContext(sp, LawEvent.KILL);
				c.target = killed;
				fire(c);
			}
		});

		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			MinecraftServer server = sender.getServer();
			if (server == null) {
				return true;
			}
			PlayerRecord rec = DiktaturaState.get(server).player(sender);
			if (rec.timeoutTicks > 0) {
				sender.sendMessage(Text.translatable("diktatura.timeout.muted", rec.timeoutTicks / 20)
					.formatted(Formatting.RED), false);
				return false;
			}
			LawContext c = new LawContext(sender, LawEvent.CHAT);
			c.message = message.getContent().getString();
			return !fire(c);
		});

		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayerEntity sp) {
				fire(new LawContext(sp, LawEvent.SLEEP));
			}
		});

		// Пассажир повозки и арестованные неуязвимы: повозка летит сквозь блоки,
		// а отработка в небе не должна убивать.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayerEntity sp)) {
				return true;
			}
			if (TransportSystem.isTraveling(sp.getUuid())) {
				return false;
			}
			MinecraftServer server = sp.getServer();
			return server == null || !DiktaturaState.get(server).player(sp).stage.isArrested();
		});
	}

	/** Действуют ли законы на игрока прямо сейчас. */
	public static boolean applies(ServerPlayerEntity p, PlayerRecord rec) {
		DiktaturaConfig cfg = DiktaturaConfig.get();
		if (p.isSpectator() || (cfg.exemptCreative && p.isCreative()) || (cfg.exemptOps && p.hasPermissionLevel(2))) {
			return false;
		}
		return rec.stage == Stage.FREE && !TransportSystem.isTraveling(p.getUuid());
	}

	/**
	 * Проверяет все действующие законы для события.
	 *
	 * @return true, если хотя бы один нарушенный закон требует отменить действие
	 */
	public static boolean fire(LawContext ctx) {
		MinecraftServer server = ctx.player.getServer();
		if (server == null) {
			return false;
		}
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(ctx.player);
		if (!applies(ctx.player, rec)) {
			return false;
		}
		if (ctx.event == LawEvent.TICK && inGrace(ctx.player.getUuid())) {
			return false;
		}
		boolean rebellion = LoyaltySystem.isRebellion(server);
		boolean prevent = false;
		for (String id : new ArrayList<>(state.activeLaws)) {
			Law law = LawRegistry.get(id);
			if (law == null || law.check().event() != ctx.event || (rebellion && !law.permanent())) {
				continue;
			}
			if (ctx.event == LawEvent.TICK && !ctx.regular && !law.check().checksOnPickup()) {
				continue;
			}
			if (!law.check().test(ctx)) {
				continue;
			}
			prevent |= law.prevent();
			if (onCooldown(ctx.player.getUuid(), law)) {
				if (law.prevent()) {
					ctx.player.sendMessage(Text.translatable("diktatura.prevented", law.name()).formatted(Formatting.RED), true);
				}
				continue;
			}
			UUID uuid = ctx.player.getUuid();
			defer(() -> {
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
				if (p != null) {
					PunishmentSystem.punish(p, law, ctx);
				}
			});
		}
		return prevent;
	}

	private static boolean onCooldown(UUID player, Law law) {
		Map<String, Long> map = cooldowns.computeIfAbsent(player, k -> new HashMap<>());
		long now = Diktatura.ticks();
		Long last = map.get(law.key());
		if (last != null && now - last < law.cooldownTicks()) {
			return true;
		}
		map.put(law.key(), now);
		return false;
	}

	/**
	 * Передышка после освобождения: «постоянные» законы (TICK) не проверяются заданное время,
	 * иначе богач, вернувшийся из тюрьмы, сразу уехал бы обратно.
	 */
	public static void grace(UUID player, int ticks) {
		graceUntil.put(player, Diktatura.ticks() + ticks);
	}

	private static boolean inGrace(UUID player) {
		Long until = graceUntil.get(player);
		return until != null && Diktatura.ticks() < until;
	}

	public static void tick(MinecraftServer server) {
		for (int i = pending.size(); i > 0 && !pending.isEmpty(); i--) {
			try {
				pending.poll().run();
			} catch (Exception e) {
				Diktatura.LOGGER.error("Ошибка при исполнении наказания", e);
			}
		}

		boolean regular = Diktatura.ticks() % Math.max(1, DiktaturaConfig.get().checkIntervalTicks) == 0;
		if (!regular && pickups.isEmpty()) {
			return;
		}
		for (ServerPlayerEntity p : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
			boolean pick = pickups.contains(p.getUuid());
			if (!regular && !pick) {
				continue;
			}
			LawContext c = new LawContext(p, LawEvent.TICK);
			c.regular = regular;
			c.pickup = pick;
			fire(c);
		}
		pickups.clear();
	}

	/** Первый запуск мира: включить законы, отмеченные enabled_by_default. */
	public static void initLaws(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (!state.lawsInitialized) {
			for (Law law : LawRegistry.all()) {
				if (law.enabledByDefault()) {
					state.activeLaws.add(law.key());
				}
			}
			state.lawsInitialized = true;
			state.markDirty();
		}
	}

	public static boolean setActive(MinecraftServer server, String id, boolean active) {
		DiktaturaState state = DiktaturaState.get(server);
		boolean changed = active ? state.activeLaws.add(id) : state.activeLaws.remove(id);
		if (changed) {
			if (active) {
				state.repealedLaws.remove(id);
			} else {
				state.repealedLaws.add(id);
			}
			state.markDirty();
			com.diktatura.network.Network.lawsChanged();
		}
		return changed;
	}
}
