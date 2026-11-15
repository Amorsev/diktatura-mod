package com.diktatura.casino.poker;

import java.util.Random;

import com.diktatura.casino.poker.PokerTable.Action;

/**
 * Боты покерного стола трёх уровней:
 * <ul>
 *   <li>«Новобранец» — играет почти наугад;</li>
 *   <li>«Чиновник» — по силе руки (шансы против оставшихся соперников), банк не считает;</li>
 *   <li>«Министр» — сила руки + шансы банка, размер ставки от банка и изредка блефует (ему ничего не бывает).</li>
 * </ul>
 */
public final class PokerBot {
	public enum Level {
		RECRUIT("recruit"), OFFICIAL("official"), MINISTER("minister");

		/** Ключ перевода — diktatura.poker.bot.&lt;id&gt;. */
		public final String id;

		Level(String id) {
			this.id = id;
		}

		public static Level byId(String id) {
			for (Level l : values()) {
				if (l.id.equals(id)) {
					return l;
				}
			}
			return null;
		}
	}

	private PokerBot() {
	}

	/** Решение бота на месте seat (должен быть его ход). iterations — точность подсчёта шансов. */
	public static Action decide(PokerTable t, int seat, Random rng, int iterations) {
		Level level = t.seat(seat).bot;
		return switch (level == null ? Level.RECRUIT : level) {
			case RECRUIT -> recruit(t, seat, rng);
			case OFFICIAL -> official(t, seat, rng, iterations);
			case MINISTER -> minister(t, seat, rng, iterations);
		};
	}

	private static Action recruit(PokerTable t, int seat, Random rng) {
		int toCall = t.toCall(seat);
		double r = rng.nextDouble();
		if (toCall == 0) {
			if (r < 0.5) {
				return Action.CHECK;
			}
			if (r < 0.85) {
				return raise(t, seat, t.minRaiseTo(seat));
			}
			return r < 0.96 ? raise(t, seat, t.currentBet() + t.pot() / 2) : Action.ALL_IN;
		}
		// Новобранец азартен: сбрасывает редко, только если ставка уж очень дорогая.
		double fold = toCall * 2 > t.seat(seat).stack() ? 0.3 : 0.12;
		if (r < fold) {
			return Action.FOLD;
		}
		if (r < 0.8) {
			return Action.CALL;
		}
		return r < 0.95 ? raise(t, seat, t.minRaiseTo(seat)) : Action.ALL_IN;
	}

	private static Action official(PokerTable t, int seat, Random rng, int iterations) {
		int opp = t.activeOpponents(seat);
		double s = strength(t, seat, opp, rng, iterations);
		int toCall = t.toCall(seat);
		int pot = t.pot();
		int bb = t.rules().bigBlind();
		boolean preflop = t.board().isEmpty();
		if (toCall == 0) {
			if (s > 1.35 || (s > 1.0 && rng.nextDouble() < 0.35) || rng.nextDouble() < 0.08) {
				return raise(t, seat, t.currentBet() + Math.max((int) (pot * (0.5 + rng.nextDouble() * 0.3)), bb));
			}
			return Action.CHECK;
		}
		if (s > 1.6) {
			return raise(t, seat, t.currentBet() + Math.max(pot, bb * 3));
		}
		if (s > 1.0) {
			return rng.nextDouble() < 0.15 ? raise(t, seat, t.currentBet() * 2 + bb) : Action.CALL;
		}
		if (s > 0.75 && toCall <= Math.max(pot, bb * 2)) {
			return Action.CALL;
		}
		if (preflop && s > 0.55 && toCall <= bb * 3) {
			return Action.CALL;
		}
		// Иногда чиновник упирается «из принципа».
		return rng.nextDouble() < 0.08 ? Action.CALL : Action.FOLD;
	}

	private static Action minister(PokerTable t, int seat, Random rng, int iterations) {
		int opp = t.activeOpponents(seat);
		Card[] board = t.board().toArray(new Card[0]);
		double eq = Equity.estimate(t.hole(seat), board, opp, iterations, rng);
		double s = eq * (opp + 1);
		int toCall = t.toCall(seat);
		int pot = t.pot();
		int bb = t.rules().bigBlind();
		double size = 0.5 + rng.nextDouble() * 0.5; // доля банка
		if (toCall == 0) {
			if (s > 1.3 && rng.nextDouble() < 0.85) {
				if (board.length == 5 && s > 2.2 && rng.nextDouble() < 0.3) {
					return Action.ALL_IN;
				}
				return raise(t, seat, t.currentBet() + Math.max((int) (pot * size), bb * 2));
			}
			// Блеф: на постфлопе против одного-двух соперников — часто, на префлопе — изредка.
			double bluff = board.length >= 3 && opp <= 2 ? 0.22 : 0.1;
			if (rng.nextDouble() < bluff) {
				return raise(t, seat, t.currentBet() + Math.max((int) (pot * 0.6), bb * 2));
			}
			return Action.CHECK;
		}
		if (s > 1.6) {
			if (board.length == 5 && s > 2.2 && rng.nextDouble() < 0.4) {
				return Action.ALL_IN;
			}
			return raise(t, seat, t.currentBet() * 2 + Math.max(toCall, (int) (pot * size)));
		}
		double potOdds = toCall / (double) (pot + toCall);
		// Против ставки рука соперника обычно сильнее случайной — небольшая скидка.
		double pressure = Math.min(1.0, toCall / (double) Math.max(1, pot));
		double adj = eq * (1.0 - 0.1 * pressure);
		if (adj > potOdds - 0.03) {
			if (s > 1.2 && rng.nextDouble() < 0.25) {
				return raise(t, seat, t.currentBet() * 2 + bb);
			}
			return Action.CALL;
		}
		// Полублеф-рейз или «чтение» соперника.
		double r = rng.nextDouble();
		if (r < 0.08) {
			return raise(t, seat, t.currentBet() * 2 + bb);
		}
		return r < 0.18 && toCall <= pot ? Action.CALL : Action.FOLD;
	}

	/** Сила руки относительно «честной доли»: 1 — средняя рука против стольких соперников, 2 — вдвое лучше. */
	static double strength(PokerTable t, int seat, int opp, Random rng, int iterations) {
		double eq = Equity.estimate(t.hole(seat), t.board().toArray(new Card[0]), opp, iterations, rng);
		return eq * (Math.max(1, opp) + 1);
	}

	/** Рейз до to в пределах правил: меньше минимума — до минимума; почти весь стек — олл-ин; нельзя — колл/чек. */
	private static Action raise(PokerTable t, int seat, int to) {
		int min = t.minRaiseTo(seat);
		if (min < 0) {
			return t.toCall(seat) == 0 ? Action.CHECK : Action.CALL;
		}
		int max = t.maxRaiseTo(seat);
		to = Math.max(min, Math.min(to, max));
		if (to >= max * 0.8) {
			return Action.ALL_IN;
		}
		return Action.raiseTo(to);
	}
}
