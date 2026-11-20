package com.diktatura.loan;

import com.diktatura.Diktatura;
import com.diktatura.economy.Economy;
import com.diktatura.network.UiPayloads;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.NpcEntity;
import com.diktatura.npc.NpcInteractions;
import com.diktatura.npc.NpcPosts;
import com.diktatura.npc.NpcRegistry;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import com.diktatura.ui.Hud;
import com.diktatura.ui.Voice;
import com.diktatura.wealth.ItemValues;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Касса микрозаймов «Казначейство» — отдельное здание у подножия горы ({@link TreasuryBuilding}).
 * Казначей выдаёт займ в трудоднях только прописанным ({@link Registration}); на долг каждый кредитный день
 * начисляются сложные проценты ({@link LoanSettings#ratePercent()}). Кредитный день идёт, только пока должник
 * в сети и в зоне государственного кредита. Погасить можно трудоднями или сдать предмет по оценке ценности
 * (сдачи касса не выдаёт). Долг хранится в сотых долях трудодня.
 */
public final class Treasury {
	public static final String TREASURER_ID = "diktatura:treasurer";
	public static final String POST_KEY = "treasurer";

	/** Какую сумму игрок выбрал и ещё не подписал (0 — выбрал банкротство). */
	private static final Map<UUID, Integer> PENDING = new HashMap<>();
	private static final int BANKRUPT = 0;
	private static final Map<UUID, Integer> LAST_PHRASE = new HashMap<>();

	private Treasury() {
	}

	public static void registerEvents() {
		NpcInteractions.register("treasurer", new TreasurerHandler());
		// Казначей, оказавшийся не на своём посту (например, старый у казино из 1.0.2-beta.1), уходит.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof NpcEntity npc && TREASURER_ID.equals(npc.npcId())) {
				com.diktatura.law.LawEngine.defer(() -> {
					MinecraftServer server = world.getServer();
					if (server == null || npc.isRemoved()) {
						return;
					}
					UUID posted = DiktaturaState.get(server).npcPosts.get(POST_KEY);
					if (posted != null && !posted.equals(npc.getUuid())) {
						npc.discard();
					}
				});
			}
		});
	}

	// ================================================================== долг

	/** Долг в целых трудоднях (округление вверх). */
	public static int debt(PlayerRecord rec) {
		return (int) Math.min(Integer.MAX_VALUE, (rec.debt + 99) / 100);
	}

	/** Потолок долга текущего цикла (трудодни), 0 — займа нет. */
	public static int limit(PlayerRecord rec) {
		return rec.debt <= 0 ? 0 : rec.loanBase * LoanSettings.get().ceiling();
	}

	public static boolean hasLoan(PlayerRecord rec) {
		return rec.debt > 0;
	}

	/** Выдать займ (после подписи договора). */
	public static boolean grant(ServerPlayerEntity p, int amount) {
		DiktaturaState state = DiktaturaState.get(p.getServer());
		PlayerRecord rec = state.player(p);
		if (hasLoan(rec) || amount <= 0 || rec.stage != Stage.FREE || !Registration.hasAddress(rec)
			|| blacklistMinutes(p.getServer(), rec) > 0) {
			return false;
		}
		rec.debt = amount * 100L;
		rec.loanBase = amount;
		rec.loanDayTicks = 0;
		rec.loanDays = 0;
		rec.loanCycle = 0;
		rec.loanPaid = 0;
		rec.loanMax = rec.debt;
		rec.certificateDay = -1;
		rec.collectorVisits = 0;
		rec.collectorTicks = Collectors.periodTicks();
		state.markDirty();
		Economy.add(p, amount, "diktatura.money.reason.loan", "", true);
		return true;
	}

	/** Погасить трудоднями: не больше долга и не больше баланса. Возвращает, сколько списано. */
	public static int pay(ServerPlayerEntity p, int wanted) {
		DiktaturaState state = DiktaturaState.get(p.getServer());
		PlayerRecord rec = state.player(p);
		int amount = Math.min(Math.min(wanted, debt(rec)), Economy.balance(p));
		if (amount <= 0 || !Economy.take(p, amount, "diktatura.money.reason.loan_pay", "")) {
			return 0;
		}
		reduce(p, rec, amount * 100L);
		return amount;
	}

	private static void reduce(ServerPlayerEntity p, PlayerRecord rec, long hundredths) {
		long paid = Math.min(hundredths, rec.debt);
		rec.debt -= paid;
		rec.loanPaid += paid;
		DiktaturaState.get(p.getServer()).markDirty();
		if (rec.debt <= 0) {
			close(p, rec);
		}
	}

	/** Списать часть долга (опись коллекторов): не больше долга. Возвращает, сколько трудодней списано. */
	public static int writeOff(ServerPlayerEntity p, int trudodni) {
		PlayerRecord rec = DiktaturaState.get(p.getServer()).player(p);
		int worth = Math.min(trudodni, debt(rec));
		if (worth > 0) {
			reduce(p, rec, worth * 100L);
		}
		return Math.max(0, worth);
	}

	/** Штраф к долгу в процентах (неявка, сопротивление взысканию), не меньше 1 трудодня. Возвращает трудодни. */
	public static int penalty(ServerPlayerEntity p, double percent) {
		DiktaturaState state = DiktaturaState.get(p.getServer());
		PlayerRecord rec = state.player(p);
		if (!hasLoan(rec)) {
			return 0;
		}
		long add = Math.max(100, (long) Math.ceil(rec.debt * percent / 100.0));
		rec.debt += add;
		rec.loanMax = Math.max(rec.loanMax, rec.debt);
		state.markDirty();
		return (int) ((add + 99) / 100);
	}

	/** Сколько минут ещё в чёрном списке заёмщиков (0 — не в списке). */
	public static int blacklistMinutes(MinecraftServer server, PlayerRecord rec) {
		long left = rec.blacklistUntil - server.getOverworld().getTime();
		return left <= 0 ? 0 : (int) Math.max(1, (left + 1199) / 1200);
	}

	/**
	 * Банкротство: всё имущество из инвентаря (кроме книги законов) изымается, положительный счёт трудодней
	 * обнуляется, долг списан, лояльность −10, чёрный список заёмщиков на {@link LoanSettings#blacklistDays()} дней.
	 */
	public static void bankrupt(ServerPlayerEntity p) {
		MinecraftServer server = p.getServer();
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(p);
		if (!hasLoan(rec)) {
			return;
		}
		var inv = p.getInventory();
		int items = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack st = inv.getStack(i);
			if (!st.isEmpty() && !st.isOf(com.diktatura.item.ModItems.LAW_BOOK)) {
				items += st.getCount();
				inv.setStack(i, ItemStack.EMPTY);
			}
		}
		if (rec.trudodni > 0) {
			Economy.add(p, -rec.trudodni, "diktatura.money.reason.bankrupt", "", false);
		}
		Collectors.cancel(p);
		int debt = debt(rec);
		rec.debt = 0;
		rec.loanBase = 0;
		rec.loanDayTicks = 0;
		rec.loanCycle = 0;
		rec.collectorTicks = -1;
		rec.blacklistUntil = server.getOverworld().getTime() + (long) LoanSettings.get().blacklistDays() * LoanSettings.get().dayTicks();
		state.markDirty();
		com.diktatura.loyalty.LoyaltySystem.change(p, -10);
		Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.loan.bankrupt.done"),
			Text.translatable("diktatura.loan.bankrupt.done.hint", items, debt, LoanSettings.get().blacklistDays()), 10);
		Hud.alertAll(server, AlertKind.INFO, Text.translatable("diktatura.loan.bankrupt.all", p.getDisplayName()), Text.empty(), 6);
	}

	/** Сдать предмет из руки в счёт долга: берётся ровно столько штук, сколько нужно, сдачи нет. */
	public static int payWithItem(ServerPlayerEntity p) {
		PlayerRecord rec = DiktaturaState.get(p.getServer()).player(p);
		ItemStack stack = p.getMainHandStack();
		int debt = debt(rec);
		if (debt <= 0 || stack.isEmpty()) {
			return 0;
		}
		double rate = LoanSettings.get().itemRate();
		double per = ItemValues.value(stack) / stack.getCount();
		if (per <= 0 || per * stack.getCount() < rate) {
			return 0;
		}
		int needed = (int) Math.min(stack.getCount(), Math.ceil(debt * rate / per));
		int worth = (int) Math.min(debt, Math.floor(per * needed / rate));
		if (worth <= 0) {
			return 0;
		}
		stack.decrement(needed);
		reduce(p, rec, worth * 100L);
		return worth;
	}

	/** Оценка предмета в руке (трудодни, без учёта долга). */
	public static int handValue(ServerPlayerEntity p) {
		ItemStack stack = p.getMainHandStack();
		return stack.isEmpty() ? 0 : (int) Math.floor(ItemValues.value(stack) / LoanSettings.get().itemRate());
	}

	private static void close(ServerPlayerEntity p, PlayerRecord rec) {
		int paid = (int) ((rec.loanPaid + 99) / 100);
		int max = (int) ((rec.loanMax + 99) / 100);
		rec.debt = 0;
		rec.loanBase = 0;
		rec.loanDayTicks = 0;
		DiktaturaState.get(p.getServer()).markDirty();
		Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.loan.closed"),
			Text.translatable("diktatura.loan.closed.hint", paid, max, rec.loanDays), 8);
	}

	/** Начислить проценты за кредитный день (по таймеру или командой). */
	public static void accrue(ServerPlayerEntity p) {
		DiktaturaState state = DiktaturaState.get(p.getServer());
		PlayerRecord rec = state.player(p);
		if (!hasLoan(rec)) {
			return;
		}
		long add = (long) Math.ceil(rec.debt * LoanSettings.get().ratePercent() / 100.0);
		rec.debt += add;
		rec.loanMax = Math.max(rec.loanMax, rec.debt);
		rec.loanDays++;
		state.markDirty();
		Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.loan.interest", (add + 99) / 100),
			Text.translatable("diktatura.loan.interest.hint", debt(rec), limit(rec)), 6);
	}

	/** Сбросить займ (команда оператора). */
	public static void clear(ServerPlayerEntity p) {
		DiktaturaState state = DiktaturaState.get(p.getServer());
		PlayerRecord rec = state.player(p);
		rec.debt = 0;
		rec.loanBase = 0;
		rec.loanDayTicks = 0;
		rec.loanDays = 0;
		rec.loanCycle = 0;
		state.markDirty();
	}

	/** Установить долг (команда оператора): если займа не было — он открывается с такой базой. */
	public static void setDebt(ServerPlayerEntity p, int amount) {
		DiktaturaState state = DiktaturaState.get(p.getServer());
		PlayerRecord rec = state.player(p);
		if (amount <= 0) {
			clear(p);
			return;
		}
		if (rec.loanBase <= 0) {
			rec.loanBase = amount;
		}
		rec.debt = amount * 100L;
		rec.loanMax = Math.max(rec.loanMax, rec.debt);
		state.markDirty();
	}

	public static int minutesToDay(PlayerRecord rec) {
		int left = Math.max(0, LoanSettings.get().dayTicks() - rec.loanDayTicks);
		return Math.max(1, (left + 1199) / 1200);
	}

	public static void tick(MinecraftServer server) {
		long t = Diktatura.ticks();
		if (t % 20 != 0) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		TreasuryBuilding.ensureGenerated(server);
		if (state.treasuryGenerated) {
			NpcPosts.ensure(server, POST_KEY, TREASURER_ID, state.treasuryOrigin.add(TreasuryBuilding.POST),
				TreasuryBuilding.POST_YAW);
		}
		int day = LoanSettings.get().dayTicks();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			PlayerRecord rec = state.player(p);
			if (!hasLoan(rec) || !Registration.inZone(p)) {
				continue;
			}
			rec.loanDayTicks += 20;
			if (rec.loanDayTicks >= day) {
				rec.loanDayTicks -= day;
				accrue(p);
			}
		}
		if (t % 200 == 0) {
			state.markDirty();
		}
	}

	// ================================================================== окно казначея

	private static void say(Entity npc, String key, Object... args) {
		NpcRegistry.NpcData d = NpcRegistry.get(TREASURER_ID);
		if (d != null) {
			Voice.say(npc, d.displayName(), Text.translatable(key, args), d.soundEvent(), d.pitch());
		}
	}

	private static final class TreasurerHandler implements NpcInteractions.Handler {
		@Override
		public boolean matches(Entity entity) {
			return entity instanceof NpcEntity n && TREASURER_ID.equals(n.npcId());
		}

		@Override
		public void onOpen(ServerPlayerEntity player, Entity npc) {
			PENDING.remove(player.getUuid());
			NpcRegistry.NpcData d = NpcRegistry.get(TREASURER_ID);
			if (d != null) {
				int i = player.getRandom().nextInt(d.phraseCount());
				LAST_PHRASE.put(player.getUuid(), i);
				Voice.say(npc, d.displayName(), d.phrase(i), d.soundEvent(), d.pitch());
			}
		}

		@Override
		public UiPayloads.NpcScreen screen(ServerPlayerEntity player, Entity npc) {
			NpcRegistry.NpcData d = NpcRegistry.get(TREASURER_ID);
			LoanSettings cfg = LoanSettings.get();
			PlayerRecord rec = DiktaturaState.get(player.getServer()).player(player);
			List<Text> info = new ArrayList<>();
			List<UiPayloads.NpcAction> actions = new ArrayList<>();
			Text line = d != null ? d.phrase(LAST_PHRASE.getOrDefault(player.getUuid(), 0)) : Text.empty();
			String rate = rate(cfg.ratePercent());
			info.add(Text.translatable("diktatura.job.balance", rec.trudodni));
			boolean registered = Registration.hasAddress(rec);
			if (registered) {
				BlockPos a = Registration.address(rec);
				info.add(Text.translatable("diktatura.loan.info.address", a.getX(), a.getY(), a.getZ()));
			} else {
				info.add(Text.translatable("diktatura.loan.info.no_address", Registration.BED_RADIUS).formatted(Formatting.GOLD));
				boolean hasPlate = player.getInventory().count(LoanBlocks.ADDRESS_PLATE_ITEM) > 0;
				actions.add(new UiPayloads.NpcAction("plate", Text.translatable("diktatura.loan.get_plate"), !hasPlate));
			}
			Integer pending = PENDING.get(player.getUuid());
			if (hasLoan(rec) && pending != null && pending == BANKRUPT) {
				line = Text.translatable("diktatura.loan.bankrupt.line");
				info.add(Text.translatable("diktatura.loan.bankrupt.terms.0").formatted(Formatting.RED));
				info.add(Text.translatable("diktatura.loan.bankrupt.terms.1"));
				info.add(Text.translatable("diktatura.loan.bankrupt.terms.2", cfg.blacklistDays()));
				actions.add(new UiPayloads.NpcAction("bankrupt_ok", Text.translatable("diktatura.loan.bankrupt.confirm"), true));
				actions.add(new UiPayloads.NpcAction("cancel", Text.translatable("diktatura.loan.cancel"), true));
			} else if (hasLoan(rec)) {
				int debt = debt(rec);
				info.add(Text.translatable("diktatura.loan.info.debt", debt, limit(rec)).formatted(Formatting.RED));
				info.add(Text.translatable("diktatura.loan.info.day", rate, minutesToDay(rec), rec.loanDays));
				if (!Registration.inZone(player)) {
					info.add(Text.translatable("diktatura.loan.info.frozen").formatted(Formatting.AQUA));
				}
				info.add(Text.translatable(Collectors.visiting(player.getUuid()) ? "diktatura.loan.info.collectors_now"
					: "diktatura.loan.info.collectors", Collectors.minutesToVisit(rec)));
				int hand = handValue(player);
				if (hand > 0) {
					info.add(Text.translatable("diktatura.loan.info.hand", player.getMainHandStack().getName(), hand));
				} else {
					info.add(Text.translatable("diktatura.loan.info.hand_none"));
				}
				int balance = rec.trudodni;
				for (int part : new int[] {10, 50}) {
					if (part < debt) {
						actions.add(new UiPayloads.NpcAction("pay:" + part,
							Text.translatable("diktatura.loan.pay", part), balance >= part));
					}
				}
				int all = Math.min(debt, Math.max(0, balance));
				actions.add(new UiPayloads.NpcAction("pay:all", Text.translatable(all >= debt
					? "diktatura.loan.pay_all" : "diktatura.loan.pay_part", all), all > 0));
				actions.add(new UiPayloads.NpcAction("item", Text.translatable("diktatura.loan.pay_item",
					Math.min(hand, debt)), hand > 0));
				actions.add(new UiPayloads.NpcAction("bankrupt", Text.translatable("diktatura.loan.bankrupt"), true));
			} else if (pending != null && pending != BANKRUPT) {
				line = Text.translatable("diktatura.loan.confirm.line");
				info.add(Text.translatable("diktatura.loan.confirm.sum", pending));
				info.add(Text.translatable("diktatura.loan.confirm.rate", rate));
				info.add(Text.translatable("diktatura.loan.confirm.ceiling", pending * cfg.ceiling()));
				info.add(Text.translatable("diktatura.loan.confirm.small_print").formatted(Formatting.DARK_GRAY));
				actions.add(new UiPayloads.NpcAction("sign", Text.translatable("diktatura.loan.sign", pending), true));
				actions.add(new UiPayloads.NpcAction("cancel", Text.translatable("diktatura.loan.cancel"), true));
			} else {
				boolean free = rec.stage == Stage.FREE;
				info.add(Text.translatable(free ? "diktatura.loan.info.offer" : "diktatura.loan.info.arrested", rate));
				info.add(Text.translatable("diktatura.loan.info.zone", cfg.zoneRadius()));
				int black = blacklistMinutes(player.getServer(), rec);
				if (black > 0) {
					info.add(Text.translatable("diktatura.loan.info.blacklist", black).formatted(Formatting.DARK_RED));
				}
				for (int a : cfg.amounts()) {
					actions.add(new UiPayloads.NpcAction("take:" + a, Text.translatable("diktatura.loan.take", a),
						free && registered && black == 0));
				}
			}
			actions.add(new UiPayloads.NpcAction("client:close", Text.translatable("diktatura.npc.leave"), true));
			return new UiPayloads.NpcScreen("treasurer", npc.getId(),
				d != null ? d.displayName() : npc.getName(),
				d != null ? Text.translatable(d.role()) : Text.empty(), line, info, actions,
				d != null ? d.accent() : 0xFF2F4A3A);
		}

		@Override
		public boolean action(ServerPlayerEntity player, Entity npc, String action) {
			UUID uuid = player.getUuid();
			PlayerRecord rec = DiktaturaState.get(player.getServer()).player(player);
			if (action.startsWith("take:")) {
				int amount;
				try {
					amount = Integer.parseInt(action.substring(5));
				} catch (NumberFormatException e) {
					return true;
				}
				if (!hasLoan(rec) && Registration.hasAddress(rec) && amount > 0 && LoanSettings.get().amounts().contains(amount)) {
					PENDING.put(uuid, amount);
				}
				return true;
			}
			if ("cancel".equals(action)) {
				PENDING.remove(uuid);
				return true;
			}
			if ("bankrupt".equals(action)) {
				if (hasLoan(rec)) {
					PENDING.put(uuid, BANKRUPT);
				}
				return true;
			}
			if ("bankrupt_ok".equals(action)) {
				Integer pend = PENDING.remove(uuid);
				if (pend != null && pend == BANKRUPT && hasLoan(rec)) {
					bankrupt(player);
					say(npc, "npc.diktatura.treasurer.bankrupt");
					if (npc instanceof NpcEntity n) {
						n.gesture(NpcEntity.WRITE);
					}
				}
				return true;
			}
			if ("plate".equals(action)) {
				if (!Registration.hasAddress(rec) && player.getInventory().count(LoanBlocks.ADDRESS_PLATE_ITEM) == 0) {
					player.getInventory().offerOrDrop(new ItemStack(LoanBlocks.ADDRESS_PLATE_ITEM));
					say(npc, "npc.diktatura.treasurer.plate", Registration.BED_RADIUS);
				}
				return true;
			}
			if ("sign".equals(action)) {
				Integer amount = PENDING.remove(uuid);
				if (amount != null && amount > 0 && grant(player, amount)) {
					say(npc, "npc.diktatura.treasurer.signed", amount);
					if (npc instanceof NpcEntity n) {
						n.gesture(NpcEntity.WRITE);
					}
				}
				return true;
			}
			if (action.startsWith("pay:")) {
				String what = action.substring(4);
				int wanted;
				if ("all".equals(what)) {
					wanted = Integer.MAX_VALUE;
				} else {
					try {
						wanted = Integer.parseInt(what);
					} catch (NumberFormatException e) {
						return true;
					}
				}
				int paid = pay(player, Math.max(0, wanted));
				if (paid > 0) {
					say(npc, hasLoan(rec) ? "npc.diktatura.treasurer.paid" : "npc.diktatura.treasurer.closed", paid);
				}
				return true;
			}
			if ("item".equals(action)) {
				ItemStack before = player.getMainHandStack().copy();
				int worth = payWithItem(player);
				if (worth > 0) {
					say(npc, "npc.diktatura.treasurer.item", before.getName(), worth);
					if (npc instanceof NpcEntity n) {
						n.gesture(NpcEntity.WRITE);
					}
				} else {
					say(npc, "npc.diktatura.treasurer.worthless");
				}
				return true;
			}
			return false;
		}

		@Override
		public void fallback(ServerPlayerEntity player, Entity npc) {
			player.sendMessage(Text.translatable("diktatura.loan.need_mod").formatted(Formatting.GRAY), false);
		}
	}

	private static String rate(double r) {
		return r == Math.floor(r) ? String.valueOf((int) r) : String.valueOf(r);
	}
}
