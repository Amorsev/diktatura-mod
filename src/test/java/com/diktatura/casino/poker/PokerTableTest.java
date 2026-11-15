package com.diktatura.casino.poker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.diktatura.casino.poker.PokerTable.Action;
import com.diktatura.casino.poker.PokerTable.Event;
import org.junit.jupiter.api.Test;

class PokerTableTest {
	private static PokerTable table() {
		return new PokerTable(new PokerTable.Rules(1, 2, 5, 0.38), new Random(7));
	}

	private static PokerTable.Seat human(String id, int stack) {
		return new PokerTable.Seat(id, id, null, stack);
	}

	private static PokerTable.Seat bot(String id, int stack) {
		return new PokerTable.Seat(id, id, PokerBot.Level.OFFICIAL, stack);
	}

	private static List<Card> cards(String s) {
		return Arrays.asList(Card.parse(s));
	}

	@Test
	void headsUpBlindsAndFold() {
		PokerTable t = table();
		t.sit(0, human("a", 100));
		t.sit(1, human("b", 100));
		t.startHand();
		// Один на один: дилер ставит малый блайнд и ходит первым до флопа.
		assertEquals(0, t.dealer());
		assertEquals(1, t.bet(0));
		assertEquals(2, t.bet(1));
		assertEquals(0, t.toAct());
		t.act(0, Action.raiseTo(10));
		t.act(1, Action.FOLD);
		assertFalse(t.inHand());
		// Неуравненные 8 вернулись, банк 4 (налог 5 % с 4 = 0).
		assertEquals(102, t.seat(0).stack());
		assertEquals(98, t.seat(1).stack());
		assertTrue(t.drainEvents().stream().anyMatch(e -> e instanceof Event.Refund r && r.amount() == 8));
	}

	@Test
	void illegalActionsAreRejected() {
		PokerTable t = table();
		t.sit(0, human("a", 100));
		t.sit(1, human("b", 100));
		t.startHand();
		assertThrows(IllegalArgumentException.class, () -> t.act(1, Action.CHECK), "не его ход");
		assertThrows(IllegalArgumentException.class, () -> t.act(0, Action.CHECK), "чек при ставке");
		assertThrows(IllegalArgumentException.class, () -> t.act(0, Action.raiseTo(3)), "рейз меньше минимума");
		assertEquals(4, t.minRaiseTo(0));
		t.act(0, Action.CALL);
		assertEquals(1, t.toAct(), "большой блайнд может поднять");
		t.act(1, Action.CHECK);
		assertEquals(PokerTable.Phase.FLOP, t.phase());
		assertEquals(3, t.board().size());
		assertEquals(1, t.toAct(), "после флопа первым ходит не дилер");
	}

	@Test
	void sidePotsAndRefund() {
		PokerTable t = table();
		t.sit(0, human("short", 50));
		t.sit(1, human("mid", 100));
		t.sit(2, human("big", 200));
		// Дилер — место 0; карманные по кругу: 1, 2, 0, 1, 2, 0; затем сжечь, флоп, сжечь, тёрн, сжечь, ривер.
		t.presetNextDeck(cards("Kh Ks As Kd 8d Ad 4h 2c 5d 9h 4d Js 4c 3c"));
		t.startHand();
		assertEquals(0, t.toAct());
		t.act(0, Action.ALL_IN);
		t.act(1, Action.ALL_IN);
		t.act(2, Action.ALL_IN);
		assertFalse(t.inHand());
		// Основной банк 150: тузы (место 0) → 150 − 7 = 143; побочный 100: короли (место 1) → 95; лишние 100 — назад.
		assertEquals(143, t.seat(0).stack());
		assertEquals(95, t.seat(1).stack());
		assertEquals(100, t.seat(2).stack());
		assertEquals(12, t.taxCollected());
		assertEquals(350, t.chipsOnTable() + t.taxCollected());
		List<Event> ev = t.drainEvents();
		assertEquals(2, ev.stream().filter(e -> e instanceof Event.PotWon).count());
		assertTrue(ev.stream().anyMatch(e -> e instanceof Event.Refund r && r.seat() == 2 && r.amount() == 100));
	}

	@Test
	void splitPot() {
		PokerTable t = table();
		t.sit(0, human("a", 100));
		t.sit(1, human("b", 100));
		// Один на один дилер (0) получает карты вторым: 1, 0, 1, 0. Борд — стрит до туза у обоих.
		t.presetNextDeck(cards("2c 3d 4h 5s 6c Ts Js Qs 7d Kd 8d Ah"));
		t.startHand();
		t.act(0, Action.raiseTo(21));
		t.act(1, Action.CALL);
		while (t.inHand()) {
			t.act(t.toAct(), Action.CHECK);
		}
		// Банк 42, налог 2, каждому по 20.
		assertEquals(99, t.seat(0).stack());
		assertEquals(99, t.seat(1).stack());
		assertEquals(2, t.taxCollected());
	}

	@Test
	void bluffOnlyAgainstBot() {
		for (boolean withBot : new boolean[]{true, false}) {
			PokerTable t = table();
			t.sit(0, human("me", 100));
			t.sit(1, withBot ? bot("bot", 100) : human("friend", 100));
			// Один на один: карты — место 1, место 0, место 1, место 0. У меня 7-2 разномастные.
			t.presetNextDeck(cards("As 7c Ad 2d"));
			t.startHand();
			t.act(0, Action.ALL_IN);
			t.act(1, Action.CALL);
			assertFalse(t.inHand());
			boolean revealed = t.drainEvents().stream().anyMatch(e -> e instanceof Event.BluffRevealed b && b.seat() == 0);
			assertEquals(withBot, revealed, withBot ? "блеф против бота вскрылся" : "против людей блефовать можно");
		}
	}

	@Test
	void leavingMidHandFolds() {
		PokerTable t = table();
		t.sit(0, human("a", 100));
		t.sit(1, human("b", 100));
		t.sit(2, human("c", 100));
		t.startHand();
		// Дилер — 0, малый блайнд — 1, большой — 2, первым ходит 0. Малый блайнд встаёт: его 1 фишка остаётся в банке.
		assertEquals(0, t.toAct());
		int back = t.leave(1);
		assertEquals(99, back);
		assertTrue(t.inHand());
		t.act(0, Action.FOLD);
		assertFalse(t.inHand());
		assertEquals(101, t.seat(2).stack());
		assertEquals(300, t.chipsOnTable() + t.taxCollected() + back);
	}

	@Test
	void simulationKeepsChips() {
		PokerSim.Result r = PokerSim.run(300, 42, 60);
		assertEquals(0, r.mismatches(), r.firstError());
		assertEquals(0, r.errors(), r.firstError());
		assertEquals(300, r.hands());
	}
}
