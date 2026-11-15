package com.diktatura.network;

import com.diktatura.Diktatura;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Пакеты покерного стола: состояние стола для экрана игрока и его действия. */
public final class PokerPayloads {
	private PokerPayloads() {
	}

	/** Карта: номер 0..51, {@link #HIDDEN} — рубашкой вверх, {@link #NONE} — карты нет. */
	public static final int HIDDEN = -1;
	public static final int NONE = -2;

	/** Одно место за столом глазами конкретного игрока (чужие карты скрыты до вскрытия). */
	public record SeatView(boolean present, Text name, int bot, int stack, int bet, boolean inHand, boolean folded,
	                       boolean allIn, boolean ready, int card0, int card1, Text action, int won) {
		public static final SeatView EMPTY = new SeatView(false, Text.empty(), -1, 0, 0, false, false, false, false,
			NONE, NONE, Text.empty(), 0);

		void write(RegistryByteBuf buf) {
			buf.writeBoolean(present);
			if (!present) {
				return;
			}
			UiPayloads.writeText(buf, name);
			buf.writeVarInt(bot + 1);
			buf.writeVarInt(stack);
			buf.writeVarInt(bet);
			buf.writeByte((inHand ? 1 : 0) | (folded ? 2 : 0) | (allIn ? 4 : 0) | (ready ? 8 : 0));
			buf.writeVarInt(card0 + 2);
			buf.writeVarInt(card1 + 2);
			UiPayloads.writeText(buf, action);
			buf.writeVarInt(won);
		}

		static SeatView read(RegistryByteBuf buf) {
			if (!buf.readBoolean()) {
				return EMPTY;
			}
			Text name = UiPayloads.readText(buf);
			int bot = buf.readVarInt() - 1;
			int stack = buf.readVarInt();
			int bet = buf.readVarInt();
			int f = buf.readByte();
			int c0 = buf.readVarInt() - 2;
			int c1 = buf.readVarInt() - 2;
			Text action = UiPayloads.readText(buf);
			int won = buf.readVarInt();
			return new SeatView(true, name, bot, stack, bet, (f & 1) != 0, (f & 2) != 0, (f & 4) != 0, (f & 8) != 0,
				c0, c1, action, won);
		}
	}

	/**
	 * Состояние стола. open — открыть экран (ПКМ по столу или крупье); иначе — обновить, если открыт.
	 * turnLeft — секунд на ход у того, чья очередь (-1 — не идёт), nextHandIn — секунд до следующей раздачи.
	 */
	public record PokerState(int table, boolean open, int phase, int hand, int pot, int currentBet, int dealer, int toAct,
	                         int turnLeft, int nextHandIn, List<Integer> board, List<SeatView> seats, int mySeat, int toCall,
	                         int minRaise, int maxRaise, int balance, int minBuyIn, int maxBuyIn, int bigBlind,
	                         int taxPercent, List<Text> log) implements CustomPayload {
		public static final Id<PokerState> ID = new Id<>(Diktatura.id("poker_state"));
		public static final PacketCodec<RegistryByteBuf, PokerState> CODEC = PacketCodec.of(PokerState::write, PokerState::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(table);
			buf.writeBoolean(open);
			buf.writeVarInt(phase);
			buf.writeVarInt(hand);
			buf.writeVarInt(pot);
			buf.writeVarInt(currentBet);
			buf.writeVarInt(dealer + 1);
			buf.writeVarInt(toAct + 1);
			buf.writeVarInt(turnLeft + 1);
			buf.writeVarInt(nextHandIn + 1);
			UiPayloads.writeInts(buf, board);
			buf.writeVarInt(seats.size());
			for (SeatView s : seats) {
				s.write(buf);
			}
			buf.writeVarInt(mySeat + 1);
			buf.writeVarInt(toCall);
			buf.writeVarInt(minRaise + 1);
			buf.writeVarInt(maxRaise);
			buf.writeVarInt(balance);
			buf.writeVarInt(minBuyIn);
			buf.writeVarInt(maxBuyIn);
			buf.writeVarInt(bigBlind);
			buf.writeVarInt(taxPercent);
			buf.writeVarInt(log.size());
			for (Text t : log) {
				UiPayloads.writeText(buf, t);
			}
		}

		private static PokerState read(RegistryByteBuf buf) {
			int table = buf.readVarInt();
			boolean open = buf.readBoolean();
			int phase = buf.readVarInt();
			int hand = buf.readVarInt();
			int pot = buf.readVarInt();
			int currentBet = buf.readVarInt();
			int dealer = buf.readVarInt() - 1;
			int toAct = buf.readVarInt() - 1;
			int turnLeft = buf.readVarInt() - 1;
			int nextHandIn = buf.readVarInt() - 1;
			List<Integer> board = UiPayloads.readInts(buf);
			int n = buf.readVarInt();
			List<SeatView> seats = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				seats.add(SeatView.read(buf));
			}
			int mySeat = buf.readVarInt() - 1;
			int toCall = buf.readVarInt();
			int minRaise = buf.readVarInt() - 1;
			int maxRaise = buf.readVarInt();
			int balance = buf.readVarInt();
			int minBuyIn = buf.readVarInt();
			int maxBuyIn = buf.readVarInt();
			int bigBlind = buf.readVarInt();
			int tax = buf.readVarInt();
			int ln = buf.readVarInt();
			List<Text> log = new ArrayList<>(ln);
			for (int i = 0; i < ln; i++) {
				log.add(UiPayloads.readText(buf));
			}
			return new PokerState(table, open, phase, hand, pot, currentBet, dealer, toAct, turnLeft, nextHandIn, board,
				seats, mySeat, toCall, minRaise, maxRaise, balance, minBuyIn, maxBuyIn, bigBlind, tax, log);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/**
	 * Действие игрока у стола: sit (amount — бай-ин), rebuy (amount), stand, ready, bot (amount — уровень),
	 * kick (amount — место бота), fold, check, call, raise (amount — «рейз до»), allin, close (экран закрыт).
	 */
	public record PokerAction(int table, String action, int amount) implements CustomPayload {
		public static final Id<PokerAction> ID = new Id<>(Diktatura.id("poker_action"));
		public static final PacketCodec<RegistryByteBuf, PokerAction> CODEC = PacketCodec.of(PokerAction::write, PokerAction::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(table);
			buf.writeString(action, 16);
			buf.writeVarInt(amount);
		}

		private static PokerAction read(RegistryByteBuf buf) {
			return new PokerAction(buf.readVarInt(), buf.readString(16), buf.readVarInt());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
