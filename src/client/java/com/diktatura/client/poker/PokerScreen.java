package com.diktatura.client.poker;

import com.diktatura.Diktatura;
import com.diktatura.client.ui.Ui;
import com.diktatura.network.PokerPayloads;
import com.diktatura.network.PokerPayloads.PokerState;
import com.diktatura.network.PokerPayloads.SeatView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран покерного стола: овал стола с общими картами и банком, шесть мест вокруг (имя, стек, ставка, последнее
 * действие, карты), свои карты крупно, журнал раздачи и кнопки. Не за столом — бай-ин и «Сесть»; за столом между
 * раздачами — «Готов», докупить, посадить бота, встать; в свой ход — фолд, чек/колл, рейз (сумма кнопками), олл-ин.
 * Всё решает сервер: экран только показывает {@link PokerState} и отправляет нажатия.
 */
public class PokerScreen extends Screen {
	public static final Identifier CARDS = Diktatura.id("textures/entity/poker/cards.png");
	private static final int ACCENT = 0xFF7A1E2C;
	private static final int FELT = 0xFF1E6B3A;
	private static final int FELT_EDGE = 0xFF4A2612;
	private static final double CLOSE_DIST_SQ = 10.0 * 10.0;
	private static final String[] PHASES = {"waiting", "preflop", "flop", "turn", "river"};
	/** Места на экране (доли полуосей овала): как за столом в мире, крупье сверху. */
	private static final double[][] SEAT_XY = {{0.55, -1.0}, {1.0, 0.0}, {0.55, 1.0}, {-0.55, 1.0}, {-1.0, 0.0}, {-0.55, -1.0}};
	private static int botLevel = 1;
	private static int buyIn = -1;

	private PokerState state;
	private int raiseTo;
	private int left;
	private int top;
	private int winW;
	private int winH;
	private int tableW;

	public PokerScreen(PokerState state) {
		super(Text.translatable("diktatura.poker.title"));
		this.state = state;
	}

	public int tableId() {
		return state.table();
	}

	/** Новое состояние с сервера: перерисовать кнопки, сохранив выбранную сумму рейза. */
	public void update(PokerState s) {
		boolean newTurn = s.minRaise() != state.minRaise() || s.hand() != state.hand() || s.toAct() != state.toAct();
		this.state = s;
		if (newTurn) {
			raiseTo = 0;
		}
		clearAndInit();
	}

	private void send(String action, int amount) {
		ClientPlayNetworking.send(new PokerPayloads.PokerAction(state.table(), action, amount));
	}

	private boolean seated() {
		return state.mySeat() >= 0;
	}

	private SeatView me() {
		return seated() ? state.seats().get(state.mySeat()) : null;
	}

	private boolean myTurn() {
		return seated() && state.phase() > 0 && state.toAct() == state.mySeat();
	}

	@Override
	protected void init() {
		winW = Math.min(460, width - 12);
		winH = Math.min(276, height - 12);
		left = (width - winW) / 2;
		top = (height - winH) / 2;
		tableW = winW - 150;
		int bx = left + tableW + 6;
		int bw = winW - tableW - 12;
		int by = top + winH - 8;
		List<ButtonWidget> col = new ArrayList<>();
		if (buyIn < 0) {
			buyIn = Math.min(state.maxBuyIn(), Math.max(state.minBuyIn(), state.bigBlind() * 50));
		}
		buyIn = Math.max(state.minBuyIn(), Math.min(state.maxBuyIn(), buyIn));
		int step = Math.max(1, state.bigBlind() * 10);
		if (!seated()) {
			addAmountRow(bx, by - 46, bw, Text.translatable("diktatura.poker.buy_in", buyIn), () -> {
				buyIn = Math.max(state.minBuyIn(), buyIn - step);
				clearAndInit();
			}, () -> {
				buyIn = Math.min(state.maxBuyIn(), buyIn + step);
				clearAndInit();
			});
			ButtonWidget sit = ButtonWidget.builder(Text.translatable("diktatura.poker.sit"), b -> send("sit", buyIn))
				.dimensions(bx, by - 22, bw, 20).build();
			sit.active = state.balance() >= buyIn && state.seats().stream().anyMatch(s -> !s.present());
			addDrawableChild(sit);
		} else if (myTurn()) {
			SeatView me = me();
			int toCall = state.toCall();
			if (raiseTo <= 0) {
				raiseTo = state.minRaise();
			}
			raiseTo = Math.max(state.minRaise(), Math.min(state.maxRaise(), raiseTo));
			int y = by - 22;
			ButtonWidget allIn = ButtonWidget.builder(Text.translatable("diktatura.poker.btn.allin", me.stack() + me.bet()),
				b -> send("allin", 0)).dimensions(bx, y, bw, 20).build();
			addDrawableChild(allIn);
			y -= 24;
			if (state.minRaise() > 0) {
				int rstep = Math.max(state.bigBlind(), (state.maxRaise() - state.minRaise()) / 10);
				ButtonWidget raise = ButtonWidget.builder(Text.translatable(state.currentBet() == 0
					? "diktatura.poker.btn.bet" : "diktatura.poker.btn.raise", raiseTo), b -> send("raise", raiseTo))
					.dimensions(bx, y, bw, 20).build();
				addDrawableChild(raise);
				y -= 24;
				addAmountRow(bx, y, bw, Text.translatable("diktatura.poker.amount", raiseTo), () -> {
					raiseTo = Math.max(state.minRaise(), raiseTo - rstep);
					clearAndInit();
				}, () -> {
					raiseTo = Math.min(state.maxRaise(), raiseTo + rstep);
					clearAndInit();
				});
				y -= 24;
				int half = state.currentBet() + Math.max(state.bigBlind(), (state.pot() + toCall) / 2);
				int pot = state.currentBet() + Math.max(state.bigBlind(), state.pot() + toCall);
				int hw = (bw - 4) / 2;
				addDrawableChild(ButtonWidget.builder(Text.translatable("diktatura.poker.btn.half_pot"), b -> {
					raiseTo = Math.max(state.minRaise(), Math.min(state.maxRaise(), half));
					clearAndInit();
				}).dimensions(bx, y, hw, 20).build());
				addDrawableChild(ButtonWidget.builder(Text.translatable("diktatura.poker.btn.pot"), b -> {
					raiseTo = Math.max(state.minRaise(), Math.min(state.maxRaise(), pot));
					clearAndInit();
				}).dimensions(bx + bw - hw, y, hw, 20).build());
				y -= 24;
			}
			ButtonWidget call = ButtonWidget.builder(toCall == 0 ? Text.translatable("diktatura.poker.btn.check")
				: Text.translatable("diktatura.poker.btn.call", toCall), b -> send(toCall == 0 ? "check" : "call", 0))
				.dimensions(bx, y, bw, 20).build();
			addDrawableChild(call);
			y -= 24;
			addDrawableChild(ButtonWidget.builder(Text.translatable("diktatura.poker.btn.fold"), b -> send("fold", 0))
				.dimensions(bx, y, bw, 20).build());
		} else {
			SeatView me = me();
			boolean live = state.phase() > 0;
			int y = by - 22;
			addDrawableChild(ButtonWidget.builder(Text.translatable("diktatura.poker.stand"), b -> send("stand", 0))
				.dimensions(bx, y, bw, 20).build());
			y -= 24;
			ButtonWidget bot = ButtonWidget.builder(Text.translatable("diktatura.poker.add_bot",
				Text.translatable("diktatura.poker.bot." + new String[]{"recruit", "official", "minister"}[botLevel])),
				b -> send("bot", botLevel)).dimensions(bx + 22, y, bw - 22, 20).build();
			bot.active = state.seats().stream().anyMatch(s -> !s.present());
			addDrawableChild(bot);
			addDrawableChild(ButtonWidget.builder(Text.literal("⟳"), b -> {
				botLevel = (botLevel + 1) % 3;
				clearAndInit();
			}).dimensions(bx, y, 20, 20).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
				Text.translatable("diktatura.poker.bot_level"))).build());
			y -= 24;
			int room = state.maxBuyIn() - me.stack();
			int rebuy = Math.min(room, Math.max(state.minBuyIn(), buyIn));
			ButtonWidget rb = ButtonWidget.builder(Text.translatable("diktatura.poker.rebuy", rebuy), b -> send("rebuy", rebuy))
				.dimensions(bx, y, bw, 20).build();
			rb.active = rebuy >= state.minBuyIn() && state.balance() >= rebuy && (!live || !me.inHand());
			addDrawableChild(rb);
			y -= 24;
			ButtonWidget ready = ButtonWidget.builder(Text.translatable(me.ready() ? "diktatura.poker.not_ready"
				: "diktatura.poker.ready"), b -> send("ready", 0)).dimensions(bx, y, bw, 20).build();
			ready.active = me.stack() > 0;
			addDrawableChild(ready);
			col.add(ready);
		}
	}

	private void addAmountRow(int x, int y, int w, Text label, Runnable minus, Runnable plus) {
		addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> minus.run()).dimensions(x, y, 20, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> plus.run()).dimensions(x + w - 20, y, 20, 20).build());
		ButtonWidget mid = ButtonWidget.builder(label, b -> {
		}).dimensions(x + 22, y, w - 44, 20).build();
		mid.active = false;
		addDrawableChild(mid);
	}

	@Override
	public void tick() {
		super.tick();
		if (client != null && client.player != null && client.world != null) {
			Entity e = client.world.getEntityById(state.table());
			if (e == null || e.squaredDistanceTo(client.player) > CLOSE_DIST_SQ) {
				close();
			}
		}
	}

	@Override
	public void removed() {
		send("close", 0);
		super.removed();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		Ui.window(ctx, left, top, winW, winH, ACCENT);
		drawTable(ctx, mouseX, mouseY);
		drawSide(ctx);
	}

	// ------------------------------------------------------------------ стол

	private int tcx() {
		return left + tableW / 2 + 2;
	}

	private int tcy() {
		return top + 18 + (winH - 18 - 62) / 2;
	}

	private int ax() {
		return tableW / 2 - 50;
	}

	private int az() {
		return (winH - 18 - 62) / 2 - 22;
	}

	private void drawTable(DrawContext ctx, int mouseX, int mouseY) {
		// Заголовок: фаза, раздача, банк, таймер.
		Text phase = Text.translatable("diktatura.poker.phase." + PHASES[Math.max(0, Math.min(4, state.phase()))]);
		Text head = state.hand() > 0 ? Text.translatable("diktatura.poker.head", state.hand(), phase) : phase;
		ctx.drawTextWithShadow(textRenderer, Text.translatable("diktatura.poker.title").copy().append(" · ").append(head),
			left + 8, top + 6, Ui.GOLD);
		int cx = tcx();
		int cy = tcy();
		int rx = ax() - 14;
		int rz = az() - 6;
		oval(ctx, cx, cy, rx + 4, rz + 4, FELT_EDGE);
		oval(ctx, cx, cy, rx, rz, FELT);
		// Общие карты.
		int cw = 20;
		int ch = 28;
		int bx = cx - (5 * (cw + 3) - 3) / 2;
		for (int i = 0; i < 5; i++) {
			int x = bx + i * (cw + 3);
			if (i < state.board().size()) {
				card(ctx, state.board().get(i), x, cy - ch / 2 - 4, cw, ch);
			} else {
				ctx.fill(x, cy - ch / 2 - 4, x + cw, cy + ch / 2 - 4, 0x30000000);
			}
		}
		Text pot = Text.translatable("diktatura.poker.pot", state.pot());
		ctx.drawCenteredTextWithShadow(textRenderer, pot, cx, cy + ch / 2, Ui.GOLD);
		if (state.nextHandIn() >= 0 && state.phase() == 0) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("diktatura.poker.next_hand", state.nextHandIn()),
				cx, cy - ch / 2 - 16, Ui.TEXT);
		} else if (state.phase() == 0) {
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable(seated() ? "diktatura.poker.wait_ready"
				: "diktatura.poker.wait_sit"), cx, cy - ch / 2 - 16, Ui.MUTED);
		}
		for (int i = 0; i < state.seats().size(); i++) {
			drawSeat(ctx, i, mouseX, mouseY);
		}
		// Свои карты крупно — внизу слева.
		SeatView me = me();
		int my = top + winH - 62;
		if (me != null && me.card0() >= 0) {
			card(ctx, me.card0(), left + 10, my, 40, 56);
			card(ctx, me.card1(), left + 54, my, 40, 56);
		}
		if (me != null) {
			ctx.drawTextWithShadow(textRenderer, Text.translatable("diktatura.poker.my_stack", me.stack()), left + 100, my + 8, Ui.TEXT);
			ctx.drawTextWithShadow(textRenderer, Text.translatable("diktatura.poker.balance", state.balance()), left + 100, my + 20, Ui.MUTED);
			if (myTurn()) {
				ctx.drawTextWithShadow(textRenderer, Text.translatable("diktatura.poker.your_turn", state.turnLeft()),
					left + 100, my + 34, Ui.blink(800) ? Ui.GOLD : Ui.TEXT);
			}
		} else {
			ctx.drawTextWithShadow(textRenderer, Text.translatable("diktatura.poker.balance", state.balance()), left + 10, my + 20, Ui.MUTED);
			ctx.drawTextWithShadow(textRenderer, Text.translatable("diktatura.poker.rules", state.bigBlind() / 2 == 0 ? 1
				: state.bigBlind() / 2, state.bigBlind(), state.taxPercent()), left + 10, my + 34, Ui.MUTED);
		}
	}

	private int[] seatBox(int i) {
		int w = 84;
		int h = 38;
		int x = tcx() + (int) Math.round(SEAT_XY[i][0] * ax()) - w / 2;
		int y = tcy() + (int) Math.round(SEAT_XY[i][1] * az()) - h / 2;
		return new int[]{x, y, w, h};
	}

	private void drawSeat(DrawContext ctx, int i, int mouseX, int mouseY) {
		SeatView s = state.seats().get(i);
		int[] b = seatBox(i);
		int x = b[0];
		int y = b[1];
		boolean acting = state.phase() > 0 && state.toAct() == i;
		if (!s.present()) {
			ctx.fill(x + 10, y + 8, x + b[2] - 10, y + b[3] - 8, 0x40000000);
			ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("diktatura.poker.empty"), x + b[2] / 2, y + b[3] / 2 - 4, Ui.MUTED);
			return;
		}
		int bg = acting ? 0xE0503A10 : i == state.mySeat() ? 0xE0283A28 : Ui.BG_LIGHT;
		Ui.cut(ctx, x, y, b[2], b[3], bg);
		if (acting) {
			ctx.fill(x, y + b[3] - 2, x + b[2], y + b[3], Ui.GOLD);
			if (state.turnLeft() >= 0) {
				int w = (int) (b[2] * Math.min(1.0, state.turnLeft() / 30.0));
				ctx.fill(x, y + b[3] - 2, x + w, y + b[3], Ui.RED);
			}
		}
		int nameColor = s.folded() ? Ui.MUTED : s.bot() >= 0 ? Ui.CYAN : Ui.TEXT;
		Ui.textFit(ctx, textRenderer, s.name(), x + 3, y + 3, b[2] - 6 - (i == state.dealer() ? 10 : 0), nameColor, 1f);
		if (i == state.dealer()) {
			ctx.fill(x + b[2] - 10, y + 2, x + b[2] - 2, y + 10, 0xFFF0F0F0);
			ctx.drawText(textRenderer, "D", x + b[2] - 9, y + 2, 0xFF202020, false);
		}
		ctx.drawTextWithShadow(textRenderer, Text.translatable("diktatura.poker.stack", s.stack()), x + 3, y + 14, Ui.GOLD);
		Text line = s.won() > 0 ? Text.translatable("diktatura.poker.won", s.won())
			: !s.action().getString().isEmpty() ? s.action()
			: state.phase() == 0 ? Text.translatable(s.bot() >= 0 || s.ready() ? "diktatura.poker.is_ready" : "diktatura.poker.not_ready_yet")
			: Text.empty();
		Ui.textFit(ctx, textRenderer, line, x + 3, y + 26, b[2] - 34, s.won() > 0 ? Ui.GREEN : Ui.MUTED, 1f);
		// Маленькие карты справа.
		if (s.inHand() && !s.folded()) {
			card(ctx, s.card0(), x + b[2] - 30, y + 13, 13, 18);
			card(ctx, s.card1(), x + b[2] - 16, y + 13, 13, 18);
		}
		if (s.bet() > 0) {
			Text bet = Text.literal("● " + s.bet());
			int tx = (x + b[2] / 2 + tcx()) / 2;
			int ty = (y + b[3] / 2 + tcy()) / 2;
			ctx.drawCenteredTextWithShadow(textRenderer, bet, tx, ty, 0xFFFFE080);
		}
		if (s.bot() >= 0 && seated() && state.phase() == 0 && mouseX >= x && mouseX < x + b[2] && mouseY >= y && mouseY < y + b[3]) {
			ctx.drawTooltip(textRenderer, Text.translatable("diktatura.poker.kick_hint"), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (super.mouseClicked(mx, my, button)) {
			return true;
		}
		if (seated() && state.phase() == 0) {
			for (int i = 0; i < state.seats().size(); i++) {
				SeatView s = state.seats().get(i);
				int[] b = seatBox(i);
				if (s.present() && s.bot() >= 0 && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
					send("kick", i);
					return true;
				}
			}
		}
		return false;
	}

	private void drawSide(DrawContext ctx) {
		int x = left + tableW + 6;
		int w = winW - tableW - 12;
		int y = top + 18;
		ctx.fill(x, y, x + w, top + winH - 8 - 24 * 6, 0x50000000);
		List<OrderedText> lines = new ArrayList<>();
		for (Text t : state.log()) {
			lines.addAll(textRenderer.wrapLines(t, w - 6));
		}
		int maxLines = Math.max(0, (top + winH - 8 - 24 * 6 - y - 4) / 9);
		int from = Math.max(0, lines.size() - maxLines);
		for (int i = from; i < lines.size(); i++) {
			ctx.drawText(textRenderer, lines.get(i), x + 3, y + 3 + (i - from) * 9, i == lines.size() - 1 ? Ui.TEXT : Ui.MUTED, false);
		}
	}

	/** Овал (горизонтальными полосами). */
	private static void oval(DrawContext ctx, int cx, int cy, int rx, int rz, int color) {
		for (int dy = -rz; dy <= rz; dy++) {
			double k = 1.0 - (dy * dy) / (double) (rz * rz);
			int half = (int) Math.round(rx * Math.sqrt(Math.max(0, k)));
			ctx.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
		}
	}

	/** Карта из атласа: номер 0..51 (масть*13 + достоинство-2), HIDDEN — рубашка. */
	public static void card(DrawContext ctx, int card, int x, int y, int w, int h) {
		if (card == PokerPayloads.NONE) {
			return;
		}
		int u = card < 0 ? 13 * 20 : (card % 13) * 20;
		int v = card < 0 ? 0 : (card / 13) * 28;
		ctx.drawTexture(CARDS, x, y, w, h, u, v, 20, 28, 512, 128);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
	}
}
