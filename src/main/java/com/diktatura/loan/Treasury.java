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
 * Касса микрозаймов «Казначейство» у входа в госказино. Казначей выдаёт займ в трудоднях,
 * на долг каждый кредитный день начисляются сложные проценты ({@link LoanSettings#ratePercent()}).
 * Кредитный день идёт, только пока должник в сети. Погасить можно трудоднями или сдать предмет
 * по оценке ценности (сдачи касса не выдаёт). Долг хранится в сотых долях трудодня.
 */
public final class Treasury {
	public static final String TREASURER_ID = "diktatura:treasurer";
	public static final String POST_KEY = "treasurer";
	/** Пост казначея: площадка перед входом в казино (от угла казино), лицом на запад. */
	public static final BlockPos POST = new BlockPos(-2, 1, 3);

	/** Какую сумму игрок выбрал и ещё не подписал. */
	private static final Map<UUID, Integer> PENDING = new HashMap<>();
	private static final Map<UUID, Integer> LAST_PHRASE = new HashMap<>();

	private Treasury() {
	}

	public static void registerEvents() {
		NpcInteractions.register("treasurer", new TreasurerHandler());
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
		if (hasLoan(rec) || amount <= 0 || rec.stage != Stage.FREE) {
			return false;
		}
		rec.debt = amount * 100L;
		rec.loanBase = amount;
		rec.loanDayTicks = 0;
		rec.loanDays = 0;
		rec.loanCycle = 0;
		rec.loanPaid = 0;
		rec.loanMax = rec.debt;
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
		if (state.casinoGenerated) {
			NpcPosts.ensure(server, POST_KEY, TREASURER_ID, state.casinoOrigin.add(POST), 90.0f);
		}
		int day = LoanSettings.get().dayTicks();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			PlayerRecord rec = state.player(p);
			if (!hasLoan(rec)) {
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
			Integer pending = PENDING.get(player.getUuid());
			if (hasLoan(rec)) {
				int debt = debt(rec);
				info.add(Text.translatable("diktatura.loan.info.debt", debt, limit(rec)).formatted(Formatting.RED));
				info.add(Text.translatable("diktatura.loan.info.day", rate, minutesToDay(rec), rec.loanDays));
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
			} else if (pending != null) {
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
				for (int a : cfg.amounts()) {
					actions.add(new UiPayloads.NpcAction("take:" + a, Text.translatable("diktatura.loan.take", a), free));
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
				if (!hasLoan(rec) && LoanSettings.get().amounts().contains(amount)) {
					PENDING.put(uuid, amount);
				}
				return true;
			}
			if ("cancel".equals(action)) {
				PENDING.remove(uuid);
				return true;
			}
			if ("sign".equals(action)) {
				Integer amount = PENDING.remove(uuid);
				if (amount != null && grant(player, amount)) {
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
