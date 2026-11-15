package com.diktatura.casino.poker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Стол техасского холдема — чистая логика без Minecraft: до 6 мест, дилер и блайнды,
 * префлоп → флоп → тёрн → ривер → вскрытие; фолд / чек / колл / рейз / олл-ин, побочные банки,
 * возврат неуравненной ставки, налог государства с каждого банка.
 *
 * <p>Фишки не теряются и не появляются: сумма стеков + {@link #taxCollected()} + фишки в текущей раздаче
 * неизменна (проверяет {@code /diktatura poker sim}). Деньги игрока (бай-ин и возврат) — забота сервера.
 *
 * <p>Всё, что произошло, складывается в события ({@link #drainEvents()}) — по ним сервер шлёт обновления
 * игрокам, крупье показывает анимации, а закон о блефе получает {@link Event.BluffRevealed}.
 */
public final class PokerTable {
	public static final int SEATS = 6;

	public enum Phase {WAITING, PREFLOP, FLOP, TURN, RIVER}

	public enum ActionType {FOLD, CHECK, CALL, RAISE, ALL_IN}

	/** Действие игрока; для RAISE amount — до какой суммы поднять свою ставку на этой улице. */
	public record Action(ActionType type, int amount) {
		public static final Action FOLD = new Action(ActionType.FOLD, 0);
		public static final Action CHECK = new Action(ActionType.CHECK, 0);
		public static final Action CALL = new Action(ActionType.CALL, 0);
		public static final Action ALL_IN = new Action(ActionType.ALL_IN, 0);

		public static Action raiseTo(int amount) {
			return new Action(ActionType.RAISE, amount);
		}
	}

	/** Правила стола: блайнды, налог (%) и порог «слабой руки» для закона о блефе (доля шансов 0..1). */
	public record Rules(int smallBlind, int bigBlind, int taxPercent, double bluffThreshold) {
		public Rules {
			if (smallBlind < 1 || bigBlind < smallBlind) {
				throw new IllegalArgumentException("Неверные блайнды: " + smallBlind + "/" + bigBlind);
			}
			taxPercent = Math.max(0, Math.min(100, taxPercent));
		}
	}

	/** Участник за столом: игрок (id — UUID) или бот (bot != null). */
	public static final class Seat {
		public final String id;
		public final String name;
		public final PokerBot.Level bot;
		int stack;

		public Seat(String id, String name, PokerBot.Level bot, int stack) {
			this.id = id;
			this.name = name;
			this.bot = bot;
			this.stack = stack;
		}

		public int stack() {
			return stack;
		}

		public boolean isBot() {
			return bot != null;
		}
	}

	/** Участник текущей раздачи. */
	static final class Player {
		final int seat;
		final Seat who;
		final Card[] hole = new Card[2];
		int bet;
		int contributed;
		boolean folded;
		boolean allIn;
		boolean acted;
		boolean canRaise = true;
		boolean bluff;

		Player(int seat, Seat who) {
			this.seat = seat;
			this.who = who;
		}
	}

	/** События стола (в порядке, как случились). */
	public sealed interface Event {
		record HandStarted(int hand, int dealer, int smallBlind, int bigBlind) implements Event {
		}

		record Blind(int seat, int amount) implements Event {
		}

		/** Игроку сданы карты (сами карты — только ему: {@link #hole(int)}). */
		record HoleDealt(int seat) implements Event {
		}

		/** put — сколько фишек положено сейчас, bet — ставка на улице после действия. */
		record Acted(int seat, ActionType type, int put, int bet, boolean allIn) implements Event {
		}

		record BoardDealt(Phase phase, List<Card> board) implements Event {
		}

		/** Неуравненная часть ставки вернулась игроку. */
		record Refund(int seat, int amount) implements Event {
		}

		record Shown(int seat, Card[] hole, HandEvaluator.Hand hand) implements Event {
		}

		/**
		 * Банк (основной — index 0, дальше побочные) разыгран: amount — весь банк, tax — налог,
		 * shares — сколько получил каждый победитель (seat → фишки). category — null, если без вскрытия.
		 */
		record PotWon(int index, int amount, int tax, List<int[]> shares, HandEvaluator.Category category) implements Event {
		}

		/** Игрок пошёл олл-ин со слабой рукой против бота, и это вскрылось на шоудауне. */
		record BluffRevealed(int seat, String id, double equity) implements Event {
		}

		record HandEnded(int hand) implements Event {
		}
	}

	private final Rules rules;
	private final Random rng;
	private final Seat[] seats = new Seat[SEATS];
	private final List<Event> events = new ArrayList<>();

	private Phase phase = Phase.WAITING;
	private int dealer = -1;
	private int hand;
	private final List<Player> players = new ArrayList<>();
	private final Player[] bySeat = new Player[SEATS];
	private final List<Card> board = new ArrayList<>();
	private Deck deck;
	private int toAct = -1;
	private int currentBet;
	private int minRaise;
	private long taxCollected;
	private List<Card> presetDeck;

	/** Тесты: следующая раздача начнётся с этих карт (карманные по кругу слева от дилера, затем сжигание и борд). */
	void presetNextDeck(List<Card> top) {
		this.presetDeck = top;
	}

	public PokerTable(Rules rules, Random rng) {
		this.rules = rules;
		this.rng = rng;
	}

	// ------------------------------------------------------------------ места

	public Rules rules() {
		return rules;
	}

	public Seat seat(int i) {
		return seats[i];
	}

	public int freeSeat() {
		for (int i = 0; i < SEATS; i++) {
			if (seats[i] == null) {
				return i;
			}
		}
		return -1;
	}

	public int seatOf(String id) {
		for (int i = 0; i < SEATS; i++) {
			if (seats[i] != null && seats[i].id.equals(id)) {
				return i;
			}
		}
		return -1;
	}

	public int seated() {
		int n = 0;
		for (Seat s : seats) {
			if (s != null) {
				n++;
			}
		}
		return n;
	}

	public boolean hasBot() {
		for (Seat s : seats) {
			if (s != null && s.isBot()) {
				return true;
			}
		}
		return false;
	}

	/** Сесть на место с бай-ином (фишки уже списаны с баланса сервером). */
	public void sit(int index, Seat who) {
		if (seats[index] != null || (inHand() && bySeat[index] != null)) {
			throw new IllegalStateException("Место " + index + " занято");
		}
		if (who.stack <= 0) {
			throw new IllegalArgumentException("Нужен бай-ин");
		}
		seats[index] = who;
	}

	/** Докупить фишки — только между раздачами. */
	public void addChips(int index, int amount) {
		if (inHand()) {
			throw new IllegalStateException("Идёт раздача");
		}
		seats[index].stack += amount;
	}

	/**
	 * Встать из-за стола. Если идёт раздача и он в ней — сбрасывает карты (поставленное остаётся в банке).
	 * Возвращает фишки, которые нужно вернуть на баланс.
	 */
	public int leave(int index) {
		Seat s = seats[index];
		if (s == null) {
			return 0;
		}
		Player p = bySeat[index];
		if (inHand() && p != null && !p.folded) {
			if (toAct == index) {
				act(index, Action.FOLD);
			} else {
				p.folded = true;
				events.add(new Event.Acted(index, ActionType.FOLD, 0, p.bet, false));
				if (active() == 1) {
					finishUncontested();
				}
			}
		}
		seats[index] = null;
		int chips = s.stack;
		s.stack = 0;
		return chips;
	}

	// ------------------------------------------------------------------ раздача

	public Phase phase() {
		return phase;
	}

	public boolean inHand() {
		return phase != Phase.WAITING;
	}

	public int handNumber() {
		return hand;
	}

	public int dealer() {
		return dealer;
	}

	/** Можно начать раздачу: хотя бы двое с фишками. */
	public boolean canStart() {
		if (inHand()) {
			return false;
		}
		int n = 0;
		for (Seat s : seats) {
			if (s != null && s.stack > 0) {
				n++;
			}
		}
		return n >= 2;
	}

	public void startHand() {
		if (!canStart()) {
			throw new IllegalStateException("Нельзя начать раздачу");
		}
		hand++;
		players.clear();
		Arrays.fill(bySeat, null);
		board.clear();
		deck = presetDeck != null ? new Deck(presetDeck, rng) : new Deck(rng);
		presetDeck = null;
		dealer = nextSeatWithChips(dealer);
		for (int k = 1; k <= SEATS; k++) {
			int i = (dealer + k) % SEATS;
			if (seats[i] != null && seats[i].stack > 0) {
				Player p = new Player(i, seats[i]);
				players.add(p);
				bySeat[i] = p;
			}
		}
		// players — по кругу, начиная слева от дилера; дилер последний.
		boolean headsUp = players.size() == 2;
		Player sb = headsUp ? bySeat[dealer] : players.get(0);
		Player bb = headsUp ? players.get(0) : players.get(1);
		phase = Phase.PREFLOP;
		events.add(new Event.HandStarted(hand, dealer, sb.seat, bb.seat));
		post(sb, rules.smallBlind());
		post(bb, rules.bigBlind());
		currentBet = rules.bigBlind();
		minRaise = rules.bigBlind();
		for (int r = 0; r < 2; r++) {
			for (Player p : players) {
				p.hole[r] = deck.deal();
			}
		}
		for (Player p : players) {
			events.add(new Event.HoleDealt(p.seat));
		}
		toAct = bb.seat;
		advance();
	}

	private void post(Player p, int amount) {
		int put = Math.min(amount, p.who.stack);
		pay(p, put);
		events.add(new Event.Blind(p.seat, put));
	}

	private void pay(Player p, int amount) {
		p.who.stack -= amount;
		p.bet += amount;
		p.contributed += amount;
		if (p.who.stack == 0) {
			p.allIn = true;
		}
	}

	/** Чей ход (номер места) или -1. */
	public int toAct() {
		return toAct;
	}

	public int currentBet() {
		return currentBet;
	}

	public List<Card> board() {
		return Collections.unmodifiableList(board);
	}

	/** Карты игрока на месте seat в текущей раздаче (или null). */
	public Card[] hole(int seat) {
		Player p = bySeat[seat];
		return p == null ? null : p.hole.clone();
	}

	public boolean inCurrentHand(int seat) {
		return bySeat[seat] != null;
	}

	public boolean folded(int seat) {
		Player p = bySeat[seat];
		return p == null || p.folded;
	}

	public boolean allIn(int seat) {
		Player p = bySeat[seat];
		return p != null && p.allIn;
	}

	/** Ставка игрока на текущей улице. */
	public int bet(int seat) {
		Player p = bySeat[seat];
		return p == null ? 0 : p.bet;
	}

	/** Всего фишек в банке (включая ставки текущей улицы). */
	public int pot() {
		int s = 0;
		for (Player p : players) {
			s += p.contributed;
		}
		return s;
	}

	public long taxCollected() {
		return taxCollected;
	}

	/** Сколько нужно доставить, чтобы уравнять. */
	public int toCall(int seat) {
		Player p = bySeat[seat];
		return p == null ? 0 : Math.max(0, Math.min(currentBet - p.bet, p.who.stack));
	}

	/** Минимальная сумма «рейз до» (или -1, если поднять нельзя). */
	public int minRaiseTo(int seat) {
		Player p = bySeat[seat];
		if (p == null || !p.canRaise || p.who.stack + p.bet <= currentBet) {
			return -1;
		}
		int min = currentBet == 0 ? rules.bigBlind() : currentBet + minRaise;
		return Math.min(min, p.who.stack + p.bet);
	}

	/** Максимальная сумма «рейз до» — весь стек. */
	public int maxRaiseTo(int seat) {
		Player p = bySeat[seat];
		return p == null ? 0 : p.who.stack + p.bet;
	}

	/** Соперников, ещё не сбросивших карты. */
	public int activeOpponents(int seat) {
		int n = 0;
		for (Player p : players) {
			if (p.seat != seat && !p.folded) {
				n++;
			}
		}
		return n;
	}

	private int active() {
		int n = 0;
		for (Player p : players) {
			if (!p.folded) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Действие игрока на месте seat. Неверное действие (не его ход, чек при ставке, рейз меньше минимума)
	 * — IllegalArgumentException; состояние стола не меняется.
	 */
	public void act(int seat, Action a) {
		if (!inHand() || seat != toAct) {
			throw new IllegalArgumentException("Не ход места " + seat);
		}
		Player p = bySeat[seat];
		int stackBefore = p.who.stack;
		int betBefore = p.bet;
		int currentBefore = currentBet;
		ActionType type = a.type();
		switch (type) {
			case FOLD -> p.folded = true;
			case CHECK -> {
				if (p.bet != currentBet) {
					throw new IllegalArgumentException("Чек невозможен: нужно уравнять " + (currentBet - p.bet));
				}
			}
			case CALL -> {
				int put = Math.min(currentBet - p.bet, p.who.stack);
				if (put <= 0) {
					type = ActionType.CHECK;
				} else {
					pay(p, put);
				}
			}
			case RAISE, ALL_IN -> {
				int to = type == ActionType.ALL_IN ? p.who.stack + p.bet : a.amount();
				int max = p.who.stack + p.bet;
				if (type == ActionType.ALL_IN && (to <= currentBet || !p.canRaise)) {
					// Олл-ин не дотягивает до рейза (или поднимать уже нельзя) — это колл.
					int put = Math.min(currentBet - p.bet, p.who.stack);
					pay(p, put);
				} else {
					int min = minRaiseTo(seat);
					if (min < 0) {
						throw new IllegalArgumentException("Поднимать нельзя");
					}
					if (to > max) {
						to = max;
					}
					if (to < min) {
						throw new IllegalArgumentException("Рейз меньше минимума " + min);
					}
					int raise = to - currentBet;
					pay(p, to - p.bet);
					if (raise >= minRaise || currentBet == 0) {
						minRaise = Math.max(raise, rules.bigBlind());
						for (Player o : players) {
							if (o != p && !o.folded && !o.allIn) {
								o.acted = false;
								o.canRaise = true;
							}
						}
					} else {
						// Неполный рейз (олл-ин меньше минимума): кто уже ходил — может только уравнять.
						for (Player o : players) {
							if (o != p && !o.folded && !o.allIn) {
								if (o.acted) {
									o.canRaise = false;
								}
								o.acted = false;
							}
						}
					}
					currentBet = to;
				}
			}
		}
		p.acted = true;
		// Блеф — это олл-ин с повышением (не колл чужого олл-ина).
		if (p.allIn && stackBefore > 0 && currentBet > currentBefore && !p.who.isBot()) {
			checkBluff(p);
		}
		if (p.allIn && type != ActionType.FOLD) {
			type = ActionType.ALL_IN;
		}
		events.add(new Event.Acted(seat, type, stackBefore - p.who.stack, p.bet, p.allIn));
		if (betBefore > p.bet) {
			throw new IllegalStateException("Ставка уменьшилась");
		}
		advance();
	}

	/** Олл-ин человека со слабой рукой при боте за столом — запоминаем; наказание, если вскроется. */
	private void checkBluff(Player p) {
		if (!hasBot()) {
			return;
		}
		int opp = Math.max(1, activeOpponents(p.seat));
		double eq = Equity.estimate(p.hole, board.toArray(new Card[0]), opp, 400, rng);
		// Сравниваем с «честной долей» против этого числа соперников: порог задан для одного соперника.
		double fair = 1.0 / (opp + 1);
		if (eq < rules.bluffThreshold() * fair * 2) {
			p.bluff = true;
			bluffEquity[p.seat] = eq;
		}
	}

	private final double[] bluffEquity = new double[SEATS];

	private boolean needsAction(Player p) {
		return !p.folded && !p.allIn && (!p.acted || p.bet < currentBet);
	}

	private void advance() {
		if (active() == 1) {
			finishUncontested();
			return;
		}
		if (roundComplete()) {
			endRound();
			return;
		}
		// Следующий по кругу после toAct, кому нужно действовать.
		for (int k = 1; k <= SEATS; k++) {
			int i = (toAct + k) % SEATS;
			Player p = bySeat[i];
			if (p != null && needsAction(p)) {
				toAct = i;
				return;
			}
		}
		endRound();
	}

	private boolean roundComplete() {
		int actionable = 0;
		Player last = null;
		int maxBet = 0;
		for (Player p : players) {
			if (!p.folded) {
				maxBet = Math.max(maxBet, p.bet);
				if (!p.allIn) {
					actionable++;
					last = p;
				}
			}
		}
		if (actionable == 0) {
			return true;
		}
		if (actionable == 1 && last.bet >= maxBet && (last.acted || otherActiveAllIn(last))) {
			return true;
		}
		for (Player p : players) {
			if (needsAction(p)) {
				return false;
			}
		}
		return true;
	}

	/** Все остальные в раздаче — олл-ин (ставить не против кого). */
	private boolean otherActiveAllIn(Player last) {
		for (Player p : players) {
			if (p != last && !p.folded && !p.allIn) {
				return false;
			}
		}
		return true;
	}

	/** Неуравненная часть самой большой ставки улицы возвращается её хозяину. */
	private void refundUncalled() {
		Player top = null;
		int second = 0;
		for (Player p : players) {
			if (top == null || p.bet > top.bet) {
				if (top != null) {
					second = Math.max(second, top.bet);
				}
				top = p;
			} else {
				second = Math.max(second, p.bet);
			}
		}
		if (top != null && !top.folded && top.bet > second) {
			int diff = top.bet - second;
			top.bet -= diff;
			top.contributed -= diff;
			top.who.stack += diff;
			if (top.who.stack > 0) {
				top.allIn = false;
			}
			events.add(new Event.Refund(top.seat, diff));
		}
	}

	private void endRound() {
		refundUncalled();
		for (Player p : players) {
			p.bet = 0;
			p.acted = false;
			p.canRaise = true;
		}
		currentBet = 0;
		minRaise = rules.bigBlind();
		switch (phase) {
			case PREFLOP -> dealBoard(Phase.FLOP, 3);
			case FLOP -> dealBoard(Phase.TURN, 1);
			case TURN -> dealBoard(Phase.RIVER, 1);
			case RIVER -> {
				showdown();
				return;
			}
			default -> throw new IllegalStateException(phase.name());
		}
		int actionable = 0;
		for (Player p : players) {
			if (!p.folded && !p.allIn) {
				actionable++;
			}
		}
		if (actionable <= 1) {
			endRound(); // ставить больше некому — докладываем общие карты
			return;
		}
		toAct = dealer;
		for (int k = 1; k <= SEATS; k++) {
			int i = (dealer + k) % SEATS;
			Player p = bySeat[i];
			if (p != null && needsAction(p)) {
				toAct = i;
				return;
			}
		}
	}

	private void dealBoard(Phase next, int n) {
		deck.deal(); // сжечь карту
		for (int i = 0; i < n; i++) {
			board.add(deck.deal());
		}
		phase = next;
		events.add(new Event.BoardDealt(next, List.copyOf(board)));
	}

	/** Все, кроме одного, сбросили — банк ему без вскрытия. */
	private void finishUncontested() {
		refundUncalled();
		Player winner = null;
		for (Player p : players) {
			if (!p.folded) {
				winner = p;
			}
		}
		int pot = pot();
		int tax = tax(pot);
		int win = pot - tax;
		winner.who.stack += win;
		taxCollected += tax;
		events.add(new Event.PotWon(0, pot, tax, List.of(new int[]{winner.seat, win}), null));
		endHand();
	}

	private int tax(int pot) {
		return pot * rules.taxPercent() / 100;
	}

	private void showdown() {
		Card[] full = board.toArray(new Card[0]);
		long[] score = new long[SEATS];
		HandEvaluator.Hand[] hands = new HandEvaluator.Hand[SEATS];
		for (Player p : players) {
			if (!p.folded) {
				Card[] seven = new Card[7];
				seven[0] = p.hole[0];
				seven[1] = p.hole[1];
				System.arraycopy(full, 0, seven, 2, 5);
				hands[p.seat] = HandEvaluator.evaluate(seven);
				score[p.seat] = hands[p.seat].score();
				events.add(new Event.Shown(p.seat, p.hole.clone(), hands[p.seat]));
			}
		}
		for (Player p : players) {
			if (!p.folded && p.bluff) {
				events.add(new Event.BluffRevealed(p.seat, p.who.id, bluffEquity[p.seat]));
			}
		}
		// Банки по уровням вложений: основной и побочные.
		List<Integer> levels = new ArrayList<>();
		for (Player p : players) {
			if (p.contributed > 0 && !levels.contains(p.contributed)) {
				levels.add(p.contributed);
			}
		}
		Collections.sort(levels);
		int prev = 0;
		int index = 0;
		int carry = 0;
		for (int level : levels) {
			int amount = carry;
			List<Player> eligible = new ArrayList<>();
			for (Player p : players) {
				amount += Math.min(p.contributed, level) - Math.min(p.contributed, prev);
				if (!p.folded && p.contributed >= level) {
					eligible.add(p);
				}
			}
			prev = level;
			if (amount == 0) {
				continue;
			}
			if (eligible.isEmpty()) {
				carry = amount; // вложения сбросивших сверх всех оставшихся — в следующий банк
				continue;
			}
			carry = 0;
			long best = -1;
			for (Player p : eligible) {
				best = Math.max(best, score[p.seat]);
			}
			List<Player> winners = new ArrayList<>();
			for (Player p : players) { // порядок players — слева от дилера: лишняя фишка первому
				if (eligible.contains(p) && score[p.seat] == best) {
					winners.add(p);
				}
			}
			int tax = tax(amount);
			int share = (amount - tax) / winners.size();
			int odd = (amount - tax) % winners.size();
			List<int[]> shares = new ArrayList<>();
			for (int w = 0; w < winners.size(); w++) {
				int got = share + (w < odd ? 1 : 0);
				winners.get(w).who.stack += got;
				shares.add(new int[]{winners.get(w).seat, got});
			}
			taxCollected += tax;
			events.add(new Event.PotWon(index++, amount, tax, shares, HandEvaluator.categoryOf(best)));
		}
		if (carry > 0) {
			// Не должно случаться; на всякий случай — последнему оставшемуся, чтобы фишки не пропали.
			for (Player p : players) {
				if (!p.folded) {
					p.who.stack += carry;
					break;
				}
			}
		}
		endHand();
	}

	private void endHand() {
		for (Player p : players) {
			p.contributed = 0;
			p.bet = 0;
		}
		phase = Phase.WAITING;
		toAct = -1;
		Arrays.fill(bluffEquity, 0);
		events.add(new Event.HandEnded(hand));
	}

	private int nextSeatWithChips(int from) {
		for (int k = 1; k <= SEATS; k++) {
			int i = ((from < 0 ? -1 : from) + k + SEATS) % SEATS;
			if (seats[i] != null && seats[i].stack > 0) {
				return i;
			}
		}
		throw new IllegalStateException("Нет игроков с фишками");
	}

	/** Забрать накопившиеся события. */
	public List<Event> drainEvents() {
		List<Event> out = new ArrayList<>(events);
		events.clear();
		return out;
	}

	/** Всего фишек на столе: стеки сидящих + текущий банк (для проверки сохранения фишек). */
	public long chipsOnTable() {
		long s = pot();
		for (Seat seat : seats) {
			if (seat != null) {
				s += seat.stack;
			}
		}
		return s;
	}
}
