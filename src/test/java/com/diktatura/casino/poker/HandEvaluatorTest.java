package com.diktatura.casino.poker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.diktatura.casino.poker.HandEvaluator.Category;
import org.junit.jupiter.api.Test;

class HandEvaluatorTest {
	private static HandEvaluator.Hand eval(String cards) {
		return HandEvaluator.evaluate(Card.parse(cards));
	}

	private static long score(String cards) {
		return eval(cards).score();
	}

	@Test
	void categories() {
		assertEquals(Category.HIGH_CARD, eval("As Kd 9c 7h 3s").category());
		assertEquals(Category.PAIR, eval("As Ad 9c 7h 3s").category());
		assertEquals(Category.TWO_PAIR, eval("As Ad 9c 9h 3s").category());
		assertEquals(Category.THREE_OF_A_KIND, eval("As Ad Ac 7h 3s").category());
		assertEquals(Category.STRAIGHT, eval("9s Td Jc Qh Ks").category());
		assertEquals(Category.STRAIGHT, eval("As 2d 3c 4h 5s").category());
		assertEquals(Category.FLUSH, eval("As Ts 8s 4s 2s").category());
		assertEquals(Category.FULL_HOUSE, eval("Ks Kd Kc 2h 2s").category());
		assertEquals(Category.FOUR_OF_A_KIND, eval("7s 7d 7c 7h 2s").category());
		assertEquals(Category.STRAIGHT_FLUSH, eval("5h 6h 7h 8h 9h").category());
		assertEquals(Category.STRAIGHT_FLUSH, eval("Ad 2d 3d 4d 5d").category());
		assertEquals(Category.ROYAL_FLUSH, eval("Tc Jc Qc Kc Ac").category());
	}

	@Test
	void orderOfCategories() {
		String[] hands = {"As Kd 9c 7h 3s", "2s 2d 9c 7h 3s", "2s 2d 3c 3h 4s", "2s 2d 2c 7h 3s", "As 2d 3c 4h 5s",
			"2s 4s 6s 8s Ts", "2s 2d 2c 3h 3s", "2s 2d 2c 2h 3s", "As 2s 3s 4s 5s", "Ts Js Qs Ks As"};
		for (int i = 1; i < hands.length; i++) {
			assertTrue(score(hands[i]) > score(hands[i - 1]), hands[i] + " должна быть сильнее " + hands[i - 1]);
		}
	}

	@Test
	void kickers() {
		assertTrue(score("As Ad Kc 7h 3s") > score("Ah Ac Qc Jh Ts"), "пара тузов с королём сильнее пары тузов с дамой");
		assertTrue(score("Ks Kd 4c 4h As") > score("Kh Kc 4s 4d Qs"), "две пары: решает кикер");
		assertTrue(score("Qs Qd 4c 4h 2s") > score("Js Jd Tc Th As"), "две пары: решает старшая пара");
		assertTrue(score("As Kd 9c 7h 4s") > score("Ah Kc 9d 7s 3s"), "старшая карта: решает пятая карта");
		assertTrue(score("2s 3d 4c 5h 6s") > score("As 2d 3c 4h 5s"), "«колесо» — самый младший стрит");
		assertTrue(score("3s 3d 3c Ah Ks") > score("3s 3d 3c Ah Qs"), "сет: решает второй кикер");
		assertTrue(score("Ks Kd Kc 2h 2s") > score("Qs Qd Qc Ah As"), "фулл-хаус: решает тройка");
		assertTrue(score("As Ts 8s 4s 3s") > score("Ah Th 8h 4h 2h"), "флеш: решает младшая карта");
	}

	@Test
	void bestOfSeven() {
		HandEvaluator.Hand h = eval("As Ks 2d 7c Qs Js Ts");
		assertEquals(Category.ROYAL_FLUSH, h.category());
		h = eval("2c 2d 2h 9s 9d 9c Ah");
		assertEquals(Category.FULL_HOUSE, h.category());
		assertEquals(9, h.best()[0].rank(), "из двух троек берётся старшая");
		assertEquals(score("9s 9d 9c 2h 2d"), h.score());
		h = eval("As 2d 3c 4h 5s 6d Kc");
		assertEquals(Category.STRAIGHT, h.category());
		assertEquals(6, h.best()[0].rank(), "стрит до шестёрки, а не «колесо»");
	}

	@Test
	void splitOnBoard() {
		// Борд сильнее обеих рук — делёж.
		long a = HandEvaluator.evaluate(Card.parse("2c 3d Ts Js Qs Ks As")).score();
		long b = HandEvaluator.evaluate(Card.parse("4c 5d Ts Js Qs Ks As")).score();
		assertEquals(a, b);
		long c = HandEvaluator.evaluate(Card.parse("7c 7d 8s 8h 9c 9d Kh")).score();
		long d = HandEvaluator.evaluate(Card.parse("2c 3d 8s 8h 9c 9d Kh")).score();
		assertEquals(c, d, "третья пара не играет: у обоих 99 88 K");
	}

	@Test
	void equityIsSane() {
		java.util.Random rng = new java.util.Random(1);
		double aa = Equity.estimate(Card.parse("As Ah"), new Card[0], 1, 1000, rng);
		double sevenTwo = Equity.estimate(Card.parse("7c 2d"), new Card[0], 1, 1000, rng);
		assertTrue(aa > 0.8 && aa < 0.9, "AA против случайной руки ≈ 85 %: " + aa);
		assertTrue(sevenTwo > 0.3 && sevenTwo < 0.4, "72o против случайной руки ≈ 35 %: " + sevenTwo);
		double nuts = Equity.estimate(Card.parse("As Ks"), Card.parse("Qs Js Ts 2d 3c"), 3, 500, rng);
		assertEquals(1.0, nuts, 1e-9, "роял-флеш не проигрывает");
	}
}
