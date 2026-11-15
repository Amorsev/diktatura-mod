package com.diktatura.casino.poker;

/**
 * Игральная карта: достоинство 2..14 (11 — валет, 12 — дама, 13 — король, 14 — туз) и масть 0..3
 * (♠ ♥ ♦ ♣). Запись — «As», «Td», «7c».
 */
public record Card(int rank, int suit) {
	public static final String RANKS = "23456789TJQKA";
	public static final String SUITS = "shdc";

	public Card {
		if (rank < 2 || rank > 14 || suit < 0 || suit > 3) {
			throw new IllegalArgumentException("Нет такой карты: " + rank + "/" + suit);
		}
	}

	/** Номер карты 0..51 — для передачи по сети и колоды. */
	public int index() {
		return suit * 13 + rank - 2;
	}

	public static Card ofIndex(int i) {
		return new Card(i % 13 + 2, i / 13);
	}

	public static Card of(String s) {
		if (s.length() != 2) {
			throw new IllegalArgumentException("Нет такой карты: " + s);
		}
		int r = RANKS.indexOf(Character.toUpperCase(s.charAt(0)));
		int su = SUITS.indexOf(Character.toLowerCase(s.charAt(1)));
		if (r < 0 || su < 0) {
			throw new IllegalArgumentException("Нет такой карты: " + s);
		}
		return new Card(r + 2, su);
	}

	/** Несколько карт через пробел: «As Kd 7c». */
	public static Card[] parse(String s) {
		String t = s.trim();
		if (t.isEmpty()) {
			return new Card[0];
		}
		String[] parts = t.split("\\s+");
		Card[] out = new Card[parts.length];
		for (int i = 0; i < parts.length; i++) {
			out[i] = of(parts[i]);
		}
		return out;
	}

	@Override
	public String toString() {
		return "" + RANKS.charAt(rank - 2) + SUITS.charAt(suit);
	}
}
