package com.diktatura.event;

import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.network.UiPayloads.HeadlineStyle;
import com.diktatura.ui.Hud;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.network.Network;
import com.diktatura.prison.PrisonSystem;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * События идут по своему случайному таймеру, отдельно от указов о законах.
 * Одновременно идёт не больше одного события; следующий отсчёт начинается,
 * когда текущее закончилось.
 */
public final class EventSystem {
	/** Длительность «одного глаза». */
	public static final int BLIND_TICKS = 20 * 30;
	/** Невесомость: 5 секунд без гравитации, 5 секунд с гравитацией. */
	public static final int GRAVITY_ON_TICKS = 20 * 5;
	public static final int GRAVITY_OFF_TICKS = 20 * 5;

	/** Активное событие (не сохраняется: после перезапуска сервера оно просто заканчивается). */
	private static EventType active;
	private static int activeTicks;
	private static int pulsesLeft;
	private static boolean gravityOff;
	/** Сторона «закрытого глаза» у игрока: 1 — левая, 2 — правая. */
	private static final Map<UUID, Integer> blindSide = new HashMap<>();
	/** Кто сейчас в невесомости (чтобы точно вернуть гравитацию). */
	private static final Map<UUID, Boolean> floating = new HashMap<>();
	/** Защита от урона при падении до этого тика. */
	private static long fallSafeUntil;

	private EventSystem() {
	}

	// ---------------------------------------------------------------- группы конфискации

	private record Group(String key, Predicate<ItemStack> match) {
	}

	private static Predicate<ItemStack> items(Item... items) {
		Set<Item> set = Set.of(items);
		return s -> set.contains(s.getItem());
	}

	private static Predicate<ItemStack> tag(TagKey<Item> tag) {
		return s -> s.isIn(tag);
	}

	private static final List<Group> GROUPS = List.of(
		new Group("gold", items(Items.GOLD_INGOT, Items.GOLD_NUGGET, Items.RAW_GOLD, Items.GOLD_BLOCK, Items.RAW_GOLD_BLOCK)),
		new Group("iron", items(Items.IRON_INGOT, Items.IRON_NUGGET, Items.RAW_IRON, Items.IRON_BLOCK, Items.RAW_IRON_BLOCK)),
		new Group("coal", items(Items.COAL, Items.CHARCOAL, Items.COAL_BLOCK)),
		new Group("light", items(Items.TORCH, Items.SOUL_TORCH, Items.LANTERN, Items.SOUL_LANTERN)),
		new Group("sweets", items(Items.COOKIE, Items.CAKE, Items.PUMPKIN_PIE, Items.SWEET_BERRIES, Items.HONEY_BOTTLE, Items.SUGAR)),
		new Group("arrows", items(Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW)),
		new Group("flowers", tag(ItemTags.FLOWERS)),
		new Group("meat", items(Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP,
			Items.CHICKEN, Items.COOKED_CHICKEN, Items.MUTTON, Items.COOKED_MUTTON))
	);

	// ---------------------------------------------------------------- подарки

	private static ItemStack randomGift(Random rnd) {
		return switch (rnd.nextInt(8)) {
			case 0 -> new ItemStack(Items.BREAD, 8);
			case 1 -> new ItemStack(Items.COOKIE, 16);
			case 2 -> new ItemStack(Items.IRON_INGOT, 4);
			case 3 -> new ItemStack(Items.EMERALD, 2);
			case 4 -> new ItemStack(Items.GOLDEN_APPLE, 1);
			case 5 -> new ItemStack(Items.TORCH, 16);
			case 6 -> new ItemStack(Items.COOKED_BEEF, 6);
			default -> new ItemStack(Items.EXPERIENCE_BOTTLE, 5);
		};
	}

	// ---------------------------------------------------------------- регистрация

	public static void registerEvents() {
		// Невесомость не должна убивать: урон от падения отключён на время события и чуть после.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayerEntity && source.isIn(DamageTypeTags.IS_FALL)
				&& Diktatura.ticks() < fallSafeUntil));
	}

	public static EventType active() {
		return active;
	}

	public static int activeSeconds() {
		return active == null ? 0 : activeTicks / 20;
	}

	public static int blindSide(UUID player) {
		return blindSide.getOrDefault(player, 0);
	}

	private static int randomDelay(Random rnd) {
		DiktaturaConfig cfg = DiktaturaConfig.get();
		int lo = Math.max(1, cfg.eventMinMinutes) * 1200;
		int hi = Math.max(lo, cfg.eventMaxMinutes * 1200);
		return lo + rnd.nextInt(hi - lo + 1);
	}

	// ---------------------------------------------------------------- тик

	public static void tick(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		Random rnd = server.getOverworld().getRandom();
		if (active != null) {
			tickActive(server);
			return;
		}
		if (state.ticksToEvent < 0) {
			state.ticksToEvent = randomDelay(rnd);
		}
		if (--state.ticksToEvent <= 0) {
			start(server, null);
		}
		if (Diktatura.ticks() % 100 == 0) {
			state.markDirty();
		}
	}

	/** Начать событие (null — случайное). Возвращает false, если уже идёт другое. */
	public static boolean start(MinecraftServer server, EventType forced) {
		if (active != null) {
			return false;
		}
		DiktaturaState state = DiktaturaState.get(server);
		Random rnd = server.getOverworld().getRandom();
		EventType type = forced;
		if (type == null) {
			boolean anyArrested = state.players.values().stream().anyMatch(r -> r.stage.isArrested());
			List<EventType> pool = new ArrayList<>(List.of(EventType.values()));
			if (!anyArrested) {
				pool.remove(EventType.AMNESTY);
			}
			type = pool.get(rnd.nextInt(pool.size()));
		}
		state.eventCount++;
		state.markDirty();
		List<MutableText> lines = new ArrayList<>();
		switch (type) {
			case AMNESTY -> lines.add(Text.translatable("diktatura.event.amnesty.text", PrisonSystem.amnesty(server)));
			case GIFTS -> {
				for (ServerPlayerEntity p : free(server)) {
					ItemStack gift = randomGift(p.getRandom());
					Text name = gift.getName();
					int count = gift.getCount();
					p.getInventory().offerOrDrop(gift);
					p.sendMessage(Text.translatable("diktatura.event.gifts.you", count, name).formatted(Formatting.GREEN), false);
				}
				lines.add(Text.translatable("diktatura.event.gifts.text"));
			}
			case CONFISCATION -> {
				Group g = GROUPS.get(rnd.nextInt(GROUPS.size()));
				int total = 0;
				for (ServerPlayerEntity p : free(server)) {
					int n = confiscate(p, g.match());
					total += n;
					if (n > 0) {
						p.sendMessage(Text.translatable("diktatura.punish.confiscate", n).formatted(Formatting.GOLD), false);
					}
				}
				lines.add(Text.translatable("diktatura.event.confiscation.text",
					Text.translatable("diktatura.event.confiscation.group." + g.key()).formatted(Formatting.RED), total));
			}
			case BLIND_EYE -> {
				for (ServerPlayerEntity p : free(server)) {
					blindSide.put(p.getUuid(), 1 + p.getRandom().nextInt(2));
				}
				active = type;
				activeTicks = BLIND_TICKS;
				lines.add(Text.translatable("diktatura.event.blind_eye.text", BLIND_TICKS / 20));
			}
			case NO_GRAVITY -> {
				DiktaturaConfig cfg = DiktaturaConfig.get();
				int lo = Math.max(1, cfg.gravityPulsesMin);
				int hi = Math.max(lo, cfg.gravityPulsesMax);
				pulsesLeft = lo + rnd.nextInt(hi - lo + 1);
				active = type;
				gravityOff = false;
				activeTicks = 60; // 3 секунды на «приготовиться»
				fallSafeUntil = Long.MAX_VALUE;
				lines.add(Text.translatable("diktatura.event.no_gravity.text", pulsesLeft, GRAVITY_ON_TICKS / 20));
			}
		}
		announce(server, type, lines);
		if (active == null) {
			finish(server);
		}
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			Network.sendStatus(server, p);
		}
		return true;
	}

	private static void tickActive(MinecraftServer server) {
		activeTicks--;
		switch (active) {
			case BLIND_EYE -> {
				// Новые игроки, зашедшие во время события, тоже «закрывают глаз».
				if (Diktatura.ticks() % 20 == 0) {
					for (ServerPlayerEntity p : free(server)) {
						blindSide.computeIfAbsent(p.getUuid(), k -> 1 + p.getRandom().nextInt(2));
					}
				}
				if (activeTicks <= 0) {
					blindSide.clear();
					broadcastEnd(server);
					finish(server);
				}
			}
			case NO_GRAVITY -> {
				if (gravityOff) {
					for (ServerPlayerEntity p : free(server)) {
						setFloating(p, true);
					}
				}
				if (activeTicks > 0) {
					return;
				}
				if (!gravityOff) {
					if (pulsesLeft <= 0) {
						fallSafeUntil = Diktatura.ticks() + 20 * 10;
						broadcastEnd(server);
						finish(server);
						return;
					}
					pulsesLeft--;
					gravityOff = true;
					activeTicks = GRAVITY_ON_TICKS;
					actionbar(server, Text.translatable("diktatura.event.no_gravity.off").formatted(Formatting.LIGHT_PURPLE));
				} else {
					gravityOff = false;
					activeTicks = GRAVITY_OFF_TICKS;
					restoreGravityAll(server);
					for (ServerPlayerEntity p : free(server)) {
						p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 20 * 4, 0, false, false));
					}
					actionbar(server, Text.translatable("diktatura.event.no_gravity.on").formatted(Formatting.GRAY));
				}
			}
			default -> finish(server);
		}
	}

	private static void finish(MinecraftServer server) {
		if (active == EventType.NO_GRAVITY) {
			restoreGravityAll(server);
		}
		active = null;
		activeTicks = 0;
		pulsesLeft = 0;
		gravityOff = false;
		blindSide.clear();
		DiktaturaState state = DiktaturaState.get(server);
		state.ticksToEvent = randomDelay(server.getOverworld().getRandom());
		state.markDirty();
	}

	/** Остановить текущее событие (команда, остановка сервера). */
	public static void stop(MinecraftServer server) {
		if (active != null) {
			fallSafeUntil = Diktatura.ticks() + 20 * 10;
			finish(server);
		}
		restoreGravityAll(server);
	}

	// ---------------------------------------------------------------- невесомость

	private static void setFloating(ServerPlayerEntity p, boolean on) {
		if (p.isCreative() || p.isSpectator()) {
			return;
		}
		if (on) {
			if (!p.hasNoGravity()) {
				p.setNoGravity(true);
			}
			// Без allowFlying ванильный сервер выкинет игрока за «полёт».
			if (!p.getAbilities().allowFlying) {
				p.getAbilities().allowFlying = true;
				p.sendAbilitiesUpdate();
			}
			floating.put(p.getUuid(), true);
		} else if (floating.remove(p.getUuid()) != null || p.hasNoGravity()) {
			p.setNoGravity(false);
			p.getAbilities().allowFlying = false;
			p.getAbilities().flying = false;
			p.sendAbilitiesUpdate();
		}
	}

	private static void restoreGravityAll(MinecraftServer server) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (floating.containsKey(p.getUuid())) {
				setFloating(p, false);
			}
		}
		floating.clear();
	}

	/** Вход игрока: если он вышел в невесомости, а событие уже кончилось — вернуть гравитацию. */
	public static void onJoin(ServerPlayerEntity p) {
		if ((active != EventType.NO_GRAVITY || !gravityOff) && p.hasNoGravity() && !p.isCreative() && !p.isSpectator()) {
			p.setNoGravity(false);
			p.getAbilities().allowFlying = false;
			p.getAbilities().flying = false;
			p.sendAbilitiesUpdate();
		}
	}

	// ---------------------------------------------------------------- помощники

	private static List<ServerPlayerEntity> free(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		List<ServerPlayerEntity> out = new ArrayList<>();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			PlayerRecord rec = state.player(p);
			if (rec.stage == Stage.FREE && !p.isSpectator()) {
				out.add(p);
			}
		}
		return out;
	}

	private static int confiscate(ServerPlayerEntity p, Predicate<ItemStack> match) {
		PlayerInventory inv = p.getInventory();
		int n = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty() && match.test(s)) {
				n += s.getCount();
				inv.setStack(i, ItemStack.EMPTY);
			}
		}
		inv.markDirty();
		return n;
	}

	private static void actionbar(MinecraftServer server, Text text) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			p.sendMessage(text, true);
		}
	}

	private static void broadcastEnd(MinecraftServer server) {
		server.getPlayerManager().broadcast(Text.translatable("diktatura.event.end").formatted(Formatting.GRAY), false);
		Hud.alertAll(server, AlertKind.INFO, Text.translatable("diktatura.event.end"), Text.empty(), 4);
	}

	private static void announce(MinecraftServer server, EventType type, List<MutableText> lines) {
		var pm = server.getPlayerManager();
		Text title = Text.translatable("diktatura.event." + type.key()).formatted(Formatting.AQUA, Formatting.BOLD);
		pm.broadcast(Text.translatable("diktatura.event.header", title).formatted(Formatting.AQUA), false);
		for (MutableText line : lines) {
			pm.broadcast(line, false);
		}
		Text first = lines.isEmpty() ? Text.empty() : lines.get(0);
		boolean good = type == EventType.AMNESTY || type == EventType.GIFTS;
		Text name = Text.translatable("diktatura.event." + type.key());
		Hud.headlineAll(server, good ? HeadlineStyle.PROCLAMATION : HeadlineStyle.EMERGENCY,
			Text.translatable(good ? "diktatura.ui.headline.proclamation" : "diktatura.ui.headline.emergency"), name, 4);
		Hud.alertAll(server, good ? AlertKind.GOOD : AlertKind.EVENT, name, first, 10);
		for (ServerPlayerEntity p : pm.getPlayerList()) {
			p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
				SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 0.7f, 1.2f);
		}
	}
}
