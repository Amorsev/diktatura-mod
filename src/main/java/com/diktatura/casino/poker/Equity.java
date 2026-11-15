package com.diktatura.casino.poker;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Шансы руки (доля выигрыша, ничья делится) против нескольких случайных рук — метод Монте-Карло.
 * На префлопе результаты для 169 классов рук запоминаются (считаются один раз точнее).
 */
public final class Equity {
	private static final Map<Integer, Double> PREFLOP = new ConcurrentHashMap<>();
	private static final int PREFLOP_ITERATIONS = 3000;

	private Equity() {
	}

	/**
	 * @param hole      две карты руки
	 * @param board     открытые общие карты (0, 3, 4 или 5)
	 * @param opponents число соперников (1..9)
	 */
	public static double estimate(Card[] hole, Card[] board, int opponents, int iterations, Random rng) {
		opponents = Math.max(1, Math.min(9, opponents));
		if (board.length == 0) {
			int key = preflopKey(hole) * 16 + opponents;
			Double cached = PREFLOP.get(key);
			if (cached != null) {
				return cached;
			}
			double v = simulate(hole, board, opponents, PREFLOP_ITERATIONS, new Random(key));
			PREFLOP.put(key, v);
			return v;
		}
		return simulate(hole, board, opponents, iterations, rng);
	}

	static double simulate(Card[] hole, Card[] board, int opponents, int iterations, Random rng) {
		boolean[] used = new boolean[52];
		used[hole[0].index()] = true;
		used[hole[1].index()] = true;
		for (Card c : board) {
			used[c.index()] = true;
		}
		int[] free = new int[52];
		int nFree = 0;
		for (int i = 0; i < 52; i++) {
			if (!used[i]) {
				free[nFree++] = i;
			}
		}
		int needBoard = 5 - board.length;
		int need = needBoard + 2 * opponents;
		Card[] fullBoard = new Card[5];
		System.arraycopy(board, 0, fullBoard, 0, board.length);
		Card[] mine = new Card[7];
		Card[] theirs = new Card[7];
		double total = 0;
		for (int it = 0; it < iterations; it++) {
			// Частичное перемешивание: берём need случайных карт из свободных.
			for (int i = 0; i < need; i++) {
				int j = i + rng.nextInt(nFree - i);
				int t = free[i];
				free[i] = free[j];
				free[j] = t;
			}
			for (int i = 0; i < needBoard; i++) {
				fullBoard[board.length + i] = Card.ofIndex(free[i]);
			}
			mine[0] = hole[0];
			mine[1] = hole[1];
			System.arraycopy(fullBoard, 0, mine, 2, 5);
			long my = HandEvaluator.bestScore(mine);
			boolean lost = false;
			int ties = 0;
			for (int o = 0; o < opponents && !lost; o++) {
				theirs[0] = Card.ofIndex(free[needBoard + 2 * o]);
				theirs[1] = Card.ofIndex(free[needBoard + 2 * o + 1]);
				System.arraycopy(fullBoard, 0, theirs, 2, 5);
				long s = HandEvaluator.bestScore(theirs);
				if (s > my) {
					lost = true;
				} else if (s == my) {
					ties++;
				}
			}
			if (!lost) {
				total += 1.0 / (ties + 1);
			}
		}
		return total / iterations;
	}

	/** Класс руки на префлопе: пара / одномастные / разномастные (169 вариантов). */
	static int preflopKey(Card[] hole) {
		int hi = Math.max(hole[0].rank(), hole[1].rank());
		int lo = Math.min(hole[0].rank(), hole[1].rank());
		int suited = hole[0].suit() == hole[1].suit() ? 1 : 0;
		return (hi * 15 + lo) * 2 + suited;
	}
}
