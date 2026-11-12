package com.diktatura.punishment;

import com.diktatura.law.Json;
import com.diktatura.law.Law;
import com.diktatura.law.LawContext;
import com.diktatura.loyalty.LoyaltySystem;
import com.diktatura.prison.PrisonSystem;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.google.gson.JsonObject;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Типы наказаний. Задаются в законе:
 * <pre>"punishments": [{"type": "fine", "amount": 2}, {"type": "loyalty", "amount": -5}]</pre>
 */
public final class Punishments {
	private Punishments() {
	}

	public static Punishment parse(JsonObject o) {
		String type = Json.s(o, "type", "");
		return switch (type) {
			case "warning" -> new Warning();
			// Без "item" штраф берётся в трудоднях (с 0.2.0), с "item" — предметами, как раньше.
			case "fine" -> o.has("item") ? new Fine(item(Json.s(o, "item", "minecraft:emerald")), Json.i(o, "amount", 1))
				: new MoneyFine(Json.i(o, "amount", 5));
			case "confiscate" -> new Confiscate(Json.s(o, "mode", "offending").toLowerCase(Locale.ROOT));
			case "arrest" -> new Arrest(Json.i(o, "seconds", -1), Json.i(o, "min_seconds", -1), Json.i(o, "max_seconds", -1));
			case "timeout" -> new Timeout(Json.i(o, "seconds", 30));
			case "effect" -> new Effect(effect(Json.s(o, "effect", "minecraft:slowness")),
				Json.i(o, "seconds", 10), Json.i(o, "amplifier", 0));
			case "loyalty" -> new Loyalty(Json.i(o, "amount", -1));
			case "lightning" -> new Lightning();
			case "dismount" -> new Dismount();
			case "damage" -> new Damage((float) Json.d(o, "amount", 2));
			default -> throw new IllegalArgumentException("Неизвестный тип наказания: " + type);
		};
	}

	private static Item item(String id) {
		Identifier i = Identifier.tryParse(id);
		return i == null ? Items.EMERALD : Registries.ITEM.getOrEmpty(i).orElse(Items.EMERALD);
	}

	private static RegistryEntry<StatusEffect> effect(String id) {
		Identifier i = Identifier.tryParse(id);
		if (i == null) {
			return StatusEffects.SLOWNESS;
		}
		return Registries.STATUS_EFFECT.getOrEmpty(i).map(Registries.STATUS_EFFECT::getEntry).orElse(StatusEffects.SLOWNESS);
	}

	/** Ссадить с транспорта (лошади, лодки…). */
	static final class Dismount implements Punishment {
		@Override
		public void apply(ServerPlayerEntity p, Law law, LawContext ctx) {
			if (p.hasVehicle()) {
				p.stopRiding();
				tell(p, Text.translatable("diktatura.punish.dismount").formatted(Formatting.GOLD));
			}
		}

		@Override
		public int fear() {
			return 1;
		}
	}

	static void tell(ServerPlayerEntity p, Text text) {
		p.sendMessage(text, false);
	}

	/** Убрать из инвентаря до {@code amount} предметов. Возвращает, сколько удалось забрать. */
	public static int take(ServerPlayerEntity p, Item item, int amount) {
		PlayerInventory inv = p.getInventory();
		int left = amount;
		for (int i = 0; i < inv.size() && left > 0; i++) {
			ItemStack s = inv.getStack(i);
			if (s.isOf(item)) {
				int n = Math.min(left, s.getCount());
				s.decrement(n);
				left -= n;
			}
		}
		inv.markDirty();
		return amount - left;
	}

	static final class Warning implements Punishment {
		@Override
		public void apply(ServerPlayerEntity p, Law law, LawContext ctx) {
			tell(p, Text.translatable("diktatura.punish.warning").formatted(Formatting.YELLOW));
		}

		@Override
		public int fear() {
			return 1;
		}
	}

	/** Штраф предметами (по умолчанию изумрудами). Нечем платить — лояльность падает втрое. */
	static final class Fine implements Punishment {
		private final Item item;
		private final int amount;

		Fine(Item item, int amount) {
			this.item = item;
			this.amount = amount;
		}

		@Override
		public void apply(ServerPlayerEntity p, Law law, LawContext ctx) {
			int paid = take(p, item, amount);
			Text name = item.getName();
			if (paid == amount) {
				tell(p, Text.translatable("diktatura.punish.fine", amount, name).formatted(Formatting.GOLD));
			} else {
				int debt = amount - paid;
				LoyaltySystem.change(p, -debt * 3);
				tell(p, Text.translatable("diktatura.punish.fine_unpaid", paid, amount, name, debt * 3)
					.formatted(Formatting.RED));
			}
		}

		@Override
		public int fear() {
			return 3;
		}
	}

	/** Штраф в трудоднях. Денег нет — записывается в долг (баланс уходит в минус). */
	static final class MoneyFine implements Punishment {
		private final int amount;

		MoneyFine(int amount) {
			this.amount = Math.max(0, amount);
		}

		@Override
		public void apply(ServerPlayerEntity p, Law law, LawContext ctx) {
			com.diktatura.economy.Economy.add(p, -amount, "diktatura.money.reason.fine", Law.translationKey(law.key()), false);
			int balance = com.diktatura.economy.Economy.balance(p);
			tell(p, (balance < 0 ? Text.translatable("diktatura.punish.fine_debt", amount, balance)
				: Text.translatable("diktatura.punish.fine_trudodni", amount)).formatted(Formatting.GOLD));
		}

		@Override
		public int fear() {
			return 3;
		}
	}

	/**
	 * Конфискация. mode: held — предмет в руке, offending — все «улики» по закону,
	 * random — случайный стек из инвентаря.
	 */
	static final class Confiscate implements Punishment {
		private final String mode;

		Confiscate(String mode) {
			this.mode = mode;
		}

		@Override
		public void apply(ServerPlayerEntity p, Law law, LawContext ctx) {
			PlayerInventory inv = p.getInventory();
			int taken = 0;
			switch (mode) {
				case "held" -> {
					ItemStack s = p.getMainHandStack();
					if (!s.isEmpty()) {
						taken = s.getCount();
						p.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
					}
				}
				case "random" -> {
					List<Integer> slots = new ArrayList<>();
					for (int i = 0; i < inv.size(); i++) {
						if (!inv.getStack(i).isEmpty()) {
							slots.add(i);
						}
					}
					if (!slots.isEmpty()) {
						int slot = slots.get(p.getRandom().nextInt(slots.size()));
						taken = inv.getStack(slot).getCount();
						inv.setStack(slot, ItemStack.EMPTY);
					}
				}
				default -> {
					for (int i = 0; i < inv.size(); i++) {
						ItemStack s = inv.getStack(i);
						if (!s.isEmpty() && law.check().isOffending(s)) {
							taken += s.getCount();
							inv.setStack(i, ItemStack.EMPTY);
						}
					}
					// Предмет «на курсоре» (например, только что скрафченный хлеб) — тоже улика.
					ItemStack cursor = p.currentScreenHandler.getCursorStack();
					if (!cursor.isEmpty() && law.check().isOffending(cursor)) {
						taken += cursor.getCount();
						p.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
						p.currentScreenHandler.syncState();
					}
				}
			}
			inv.markDirty();
			if (taken > 0) {
				tell(p, Text.translatable("diktatura.punish.confiscate", taken).formatted(Formatting.GOLD));
			}
		}

		@Override
		public int fear() {
			return 5;
		}
	}

	/**
	 * Арест: отработка (мини-игра), затем срок в камере.
	 * "seconds" — фиксированный срок; иначе случайный от "min_seconds" до "max_seconds"
	 * (по умолчанию из конфига: 10–60 секунд).
	 */
	static final class Arrest implements Punishment {
		private final int seconds;
		private final int min;
		private final int max;

		Arrest(int seconds, int min, int max) {
			this.seconds = seconds;
			this.min = min;
			this.max = max;
		}

		@Override
		public void apply(ServerPlayerEntity p, Law law, LawContext ctx) {
			PrisonSystem.arrest(p, sentenceSeconds(p) * 20, law.name());
		}

		private int sentenceSeconds(ServerPlayerEntity p) {
			if (seconds > 0) {
				return seconds;
			}
			var cfg = com.diktatura.config.DiktaturaConfig.get();
			int lo = min > 0 ? min : cfg.minSentenceSeconds;
			int hi = Math.max(lo, max > 0 ? max : cfg.maxSentenceSeconds);
			return lo + p.getRandom().nextInt(hi - lo + 1);
		}

		@Override
		public int fear() {
			return 15;
		}

		@Override
		public boolean isArrest() {
			return true;
		}
	}

	/** Таймаут: молчание в чате и тяжёлые ноги. */
	static final class Timeout implements Punishment {
		private final int seconds;

		Timeout(int seconds) {
			this.seconds = seconds;
		}

		@Override
		public void apply(ServerPlayerEntity p, Law law, LawContext ctx) {
			var server = p.getServer();
			if (server == null) {
				return;
			}
			PlayerRecord rec = DiktaturaState.get(server).player(p);
			rec.timeoutTicks = Math.max(rec.timeoutTicks, seconds * 20);
			DiktaturaState.get(server).markDirty();
			int ticks = seconds * 20;
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 5));
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, ticks, 3));
			tell(p, Text.translatable("diktatura.punish.timeout", seconds).formatted(Formatting.GOLD));
		}

		@Override
		public int fear() {
			return 5;
		}
	}

	static final class Effect implements Punishment {
		private final RegistryEntry<StatusEffect> effect;
		private final int seconds;
		private final int amplifier;

		Effect(RegistryEntry<StatusEffect> effect, int seconds, int amplifier) {
			this.effect = effect;
			this.seconds = seconds;
			this.amplifier = amplifier;
		}

		@Override
		public void apply(ServerPlayerEntity p, Law law, LawContext ctx) {
			p.addStatusEffect(new StatusEffectInstance(effect, seconds * 20, amplifier));
		}

		@Override
		public int fear() {
			return 2;
		}
	}

	static final class Loyalty implements Punishment {
		private final int amount;

		Loyalty(int amount) {
			this.amount = amount;
		}

		@Override
		public void apply(ServerPlayerEntity p, Law law, LawContext ctx) {
			LoyaltySystem.change(p, amount);
			tell(p, Text.translatable("diktatura.punish.loyalty", amount).formatted(Formatting.GRAY));
		}

		@Override
		public int fear() {
			return 1;
		}
	}

	/** Показательная (безвредная) молния. */
	static final class Lightning implements Punishment {
		@Override
		public void apply(ServerPlayerEntity p, Law law, LawContext ctx) {
			LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(p.getServerWorld());
			if (bolt != null) {
				bolt.refreshPositionAfterTeleport(p.getPos());
				bolt.setCosmetic(true);
				p.getServerWorld().spawnEntity(bolt);
			}
		}

		@Override
		public int fear() {
			return 6;
		}
	}

	static final class Damage implements Punishment {
		private final float amount;

		Damage(float amount) {
			this.amount = amount;
		}

		@Override
		public void apply(ServerPlayerEntity p, Law law, LawContext ctx) {
			p.damage(p.getDamageSources().magic(), amount);
		}

		@Override
		public int fear() {
			return 4;
		}
	}
}
