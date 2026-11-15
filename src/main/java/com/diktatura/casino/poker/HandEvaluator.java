package com.diktatura.casino.poker;

import java.util.Arrays;

/**
 * Оценка покерной руки: лучшая пятёрка из 5–7 карт. Сила — одно число {@link Hand#score()}:
 * больше — сильнее, равные — делёж банка. Комбинация и кикеры закодированы внутри (категория, затем
 * до пяти достоинств по 4 бита).
 */
public final class HandEvaluator {
	/** Комбинации от слабой к сильной; ключ перевода — diktatura.poker.hand.&lt;id&gt;. */
	public enum Category {
		HIGH_CARD("high_card"), PAIR("pair"), TWO_PAIR("two_pair"), THREE_OF_A_KIND("three"), STRAIGHT("straight"),
		FLUSH("flush"), FULL_HOUSE("full_house"), FOUR_OF_A_KIND("four"), STRAIGHT_FLUSH("straight_flush"),
		ROYAL_FLUSH("royal_flush");

		public final String id;

		Category(String id) {
			this.id = id;
		}
	}

	/** Результат: сила, комбинация и лучшая пятёрка (по убыванию значимости). */
	public record Hand(long score, Category category, Card[] best) {
		@Override
		public String toString() {
			return category + " " + Arrays.toString(best);
		}
	}

	private HandEvaluator() {
	}

	/** Лучшая пятёрка из 5–7 карт. */
	public static Hand evaluate(Card... cards) {
		int n = cards.length;
		if (n < 5 || n > 7) {
			throw new IllegalArgumentException("Нужно 5–7 карт, а не " + n);
		}
		long best = -1;
		Card[] bestFive = null;
		Card[] five = new Card[5];
		// Все сочетания по 5 (из 7 — 21 штука).
		for (int a = 0; a < n; a++) {
			for (int b = a + 1; b < n; b++) {
				for (int c = b + 1; c < n; c++) {
					for (int d = c + 1; d < n; d++) {
						for (int e = d + 1; e < n; e++) {
							five[0] = cards[a];
							five[1] = cards[b];
							five[2] = cards[c];
							five[3] = cards[d];
							five[4] = cards[e];
							long s = score5(five);
							if (s > best) {
								best = s;
								bestFive = five.clone();
							}
						}
					}
				}
			}
		}
		return new Hand(best, categoryOf(best), order(bestFive, best));
	}

	/** Только сила (быстрее: без поиска пятёрки) — для ботов и подсчёта шансов. */
	public static long score(Card[] hole, Card[] board, int boardSize) {
		Card[] all = new Card[2 + boardSize];
		all[0] = hole[0];
		all[1] = hole[1];
		System.arraycopy(board, 0, all, 2, boardSize);
		return bestScore(all);
	}

	static long bestScore(Card[] cards) {
		int n = cards.length;
		long best = -1;
		Card[] five = new Card[5];
		for (int a = 0; a < n; a++) {
			for (int b = a + 1; b < n; b++) {
				for (int c = b + 1; c < n; c++) {
					for (int d = c + 1; d < n; d++) {
						for (int e = d + 1; e < n; e++) {
							five[0] = cards[a];
							five[1] = cards[b];
							five[2] = cards[c];
							five[3] = cards[d];
							five[4] = cards[e];
							long s = score5(five);
							if (s > best) {
								best = s;
							}
						}
					}
				}
			}
		}
		return best;
	}

	public static Category categoryOf(long score) {
		return Category.values()[(int) (score >> 20)];
	}

	/** Сила пятёрки: категория &lt;&lt; 20 | пять достоинств по 4 бита (по убыванию значимости). */
	static long score5(Card[] c) {
		int[] count = new int[15];
		boolean flush = true;
		int mask = 0;
		for (int i = 0; i < 5; i++) {
			count[c[i].rank()]++;
			mask |= 1 << c[i].rank();
			if (c[i].suit() != c[0].suit()) {
				flush = false;
			}
		}
		int straightHigh = 0;
		if (Integer.bitCount(mask) == 5) {
			int hi = 31 - Integer.numberOfLeadingZeros(mask);
			int lo = Integer.numberOfTrailingZeros(mask);
			if (hi - lo == 4) {
				straightHigh = hi;
			} else if (mask == ((1 << 14) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5))) {
				straightHigh = 5; // «колесо»: A-2-3-4-5
			}
		}
		if (straightHigh > 0 && flush) {
			return pack(straightHigh == 14 ? Category.ROYAL_FLUSH : Category.STRAIGHT_FLUSH, straightHigh);
		}
		// Достоинства по группам: сначала по размеру группы, затем по старшинству.
		int[] ranks = new int[5];
		int k = 0;
		int four = 0, three = 0, pairs = 0;
		for (int size = 4; size >= 1; size--) {
			for (int r = 14; r >= 2; r--) {
				if (count[r] == size) {
					ranks[k++] = r;
					if (size == 4) {
						four++;
					} else if (size == 3) {
						three++;
					} else if (size == 2) {
						pairs++;
					}
				}
			}
		}
		Category cat;
		if (four > 0) {
			cat = Category.FOUR_OF_A_KIND;
		} else if (three > 0 && pairs > 0) {
			cat = Category.FULL_HOUSE;
		} else if (flush) {
			cat = Category.FLUSH;
		} else if (straightHigh > 0) {
			return pack(Category.STRAIGHT, straightHigh);
		} else if (three > 0) {
			cat = Category.THREE_OF_A_KIND;
		} else if (pairs == 2) {
			cat = Category.TWO_PAIR;
		} else if (pairs == 1) {
			cat = Category.PAIR;
		} else {
			cat = Category.HIGH_CARD;
		}
		return pack(cat, Arrays.copyOf(ranks, k));
	}

	private static long pack(Category cat, int... ranks) {
		long s = (long) cat.ordinal() << 20;
		for (int i = 0; i < 5; i++) {
			s |= (long) (i < ranks.length ? ranks[i] : 0) << (16 - 4 * i);
		}
		return s;
	}

	/** Пятёрка в порядке значимости (сначала группы, «колесо» — пятёрка вперёд туза). */
	private static Card[] order(Card[] five, long score) {
		Card[] out = five.clone();
		int[] count = new int[15];
		for (Card c : out) {
			count[c.rank()]++;
		}
		Category cat = categoryOf(score);
		boolean wheel = (cat == Category.STRAIGHT || cat == Category.STRAIGHT_FLUSH) && ((score >> 16) & 15) == 5;
		Arrays.sort(out, (x, y) -> {
			int rx = wheel && x.rank() == 14 ? 1 : x.rank();
			int ry = wheel && y.rank() == 14 ? 1 : y.rank();
			if (count[x.rank()] != count[y.rank()]) {
				return count[y.rank()] - count[x.rank()];
			}
			return ry - rx;
		});
		return out;
	}
}
