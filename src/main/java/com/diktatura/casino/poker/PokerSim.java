package com.diktatura.casino.poker;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import com.diktatura.casino.poker.PokerTable.Event;

/**
 * Прогон раздач между ботами (команда {@code /diktatura poker sim}): проверяет, что фишки не теряются и не
 * появляются, а боты не делают неверных ходов. За столом — по два бота каждого уровня и «игрок-новичок»
 * (играет как новобранец, но считается человеком — на нём проверяется закон о блефе). Иногда кто-то встаёт
 * посреди раздачи и садится снова; разорившиеся докупают фишки.
 */
public final class PokerSim {
	/** Итог: раздач, расхождений по фишкам, ошибок, налог, выигрыш по уровням ботов, вскрытых блефов. */
	public record Result(int hands, int mismatches, int errors, long tax, Map<PokerBot.Level, Long> profit,
	                     int bluffs, int sidePots, int splits, String firstError) {
	}

	private PokerSim() {
	}

	public static Result run(int hands, long seed, int iterations) {
		Random rng = new Random(seed);
		PokerTable t = new PokerTable(new PokerTable.Rules(1, 2, 5, 0.38), rng);
		int buyIn = 100;
		PokerBot.Level[] levels = {PokerBot.Level.RECRUIT, PokerBot.Level.OFFICIAL, PokerBot.Level.MINISTER,
			PokerBot.Level.RECRUIT, PokerBot.Level.OFFICIAL, null};
		long injected = 0;
		Map<PokerBot.Level, Long> profit = new EnumMap<>(PokerBot.Level.class);
		long[] spent = new long[PokerTable.SEATS];
		for (int i = 0; i < PokerTable.SEATS; i++) {
			t.sit(i, seatFor(i, levels[i], buyIn));
			injected += buyIn;
			spent[i] = buyIn;
		}
		int mismatches = 0;
		int errors = 0;
		int bluffs = 0;
		int sidePots = 0;
		int splits = 0;
		String firstError = null;
		long returned = 0;
		int played = 0;
		for (int h = 0; h < hands; h++) {
			// Разорившиеся и вставшие — докупают / садятся снова.
			for (int i = 0; i < PokerTable.SEATS; i++) {
				if (t.seat(i) == null) {
					t.sit(i, seatFor(i, levels[i], buyIn));
					injected += buyIn;
					spent[i] += buyIn;
				} else if (t.seat(i).stack() == 0) {
					t.addChips(i, buyIn);
					injected += buyIn;
					spent[i] += buyIn;
				}
			}
			t.startHand();
			played++;
			int guard = 0;
			while (t.inHand() && guard++ < 500) {
				int seat = t.toAct();
				if (rng.nextDouble() < 0.004) {
					// Встал посреди раздачи (или вышел из игры) — фишки на баланс.
					int leaver = rng.nextInt(PokerTable.SEATS);
					if (t.seat(leaver) != null) {
						long back = t.leave(leaver);
						returned += back;
						spent[leaver] -= back;
					}
					continue;
				}
				PokerTable.Action a = PokerBot.decide(t, seat, rng, iterations);
				try {
					t.act(seat, a);
				} catch (IllegalArgumentException e) {
					errors++;
					if (firstError == null) {
						firstError = "раздача " + t.handNumber() + ", место " + seat + ": " + a + " — " + e.getMessage();
					}
					t.act(seat, t.toCall(seat) == 0 ? PokerTable.Action.CHECK : PokerTable.Action.FOLD);
				}
			}
			if (t.inHand()) {
				errors++;
				if (firstError == null) {
					firstError = "раздача " + t.handNumber() + " не закончилась";
				}
				break;
			}
			for (Event e : t.drainEvents()) {
				if (e instanceof Event.BluffRevealed) {
					bluffs++;
				} else if (e instanceof Event.PotWon w) {
					if (w.index() > 0) {
						sidePots++;
					}
					if (w.shares().size() > 1) {
						splits++;
					}
				}
			}
			long onTable = t.chipsOnTable();
			if (onTable + t.taxCollected() + returned != injected) {
				mismatches++;
				if (firstError == null) {
					firstError = "раздача " + t.handNumber() + ": фишек " + (onTable + t.taxCollected() + returned)
						+ " вместо " + injected;
				}
			}
		}
		for (int i = 0; i < PokerTable.SEATS; i++) {
			if (levels[i] != null) {
				long now = t.seat(i) == null ? 0 : t.seat(i).stack();
				profit.merge(levels[i], now - spent[i], Long::sum);
			}
		}
		return new Result(played, mismatches, errors, t.taxCollected(), profit, bluffs, sidePots, splits, firstError);
	}

	private static PokerTable.Seat seatFor(int i, PokerBot.Level level, int buyIn) {
		return level == null
			? new PokerTable.Seat("sim-human", "Игрок", null, buyIn)
			: new PokerTable.Seat("sim-bot-" + i, level.id + " " + i, level, buyIn);
	}
}
