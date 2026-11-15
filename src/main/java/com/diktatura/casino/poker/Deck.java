package com.diktatura.casino.poker;

import java.util.List;
import java.util.Random;

/** Колода из 52 карт, перемешанная заново на каждую раздачу. */
public final class Deck {
	private final int[] cards = new int[52];
	private int next;

	public Deck(Random rng) {
		for (int i = 0; i < 52; i++) {
			cards[i] = i;
		}
		for (int i = 51; i > 0; i--) {
			int j = rng.nextInt(i + 1);
			int t = cards[i];
			cards[i] = cards[j];
			cards[j] = t;
		}
	}

	/** Колода с заданными верхними картами (для тестов), остальные — перемешаны. */
	Deck(List<Card> top, Random rng) {
		this(rng);
		for (int k = 0; k < top.size(); k++) {
			int want = top.get(k).index();
			for (int i = k; i < 52; i++) {
				if (cards[i] == want) {
					cards[i] = cards[k];
					cards[k] = want;
					break;
				}
			}
		}
	}

	public Card deal() {
		if (next >= 52) {
			throw new IllegalStateException("Колода кончилась");
		}
		return Card.ofIndex(cards[next++]);
	}

	public int left() {
		return 52 - next;
	}
}
