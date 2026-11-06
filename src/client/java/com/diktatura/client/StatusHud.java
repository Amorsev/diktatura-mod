package com.diktatura.client;

import com.diktatura.client.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-панели в левом верхнем углу (замена старого LoyaltyHud): «Гражданин»
 * (лояльность и страх), «Статус» (арест, отработка, таймаут, восстание, событие) и «Указ».
 * Сервер присылает только числа ({@code StatusPayload}), всё остальное — отрисовка.
 */
public final class StatusHud {
	private static final int W = 112;
	private static final int PAD = 5;

	private record Line(Text text, int color) {
	}

	private StatusHud() {
	}

	public static void render(DrawContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!ClientState.received || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) {
			return;
		}
		TextRenderer tr = mc.textRenderer;
		int x = 4;
		int y = 4;

		// --- Гражданин
		int ph = 55;
		Ui.panel(ctx, x, y, W, ph, loyaltyColor(ClientState.loyalty));
		ctx.drawTextWithShadow(tr, Text.translatable("diktatura.ui.panel.citizen"), x + PAD + 2, y + 4, Ui.MUTED);
		int ly = y + 15;
		ctx.drawTextWithShadow(tr, Text.translatable("diktatura.hud.loyalty", ClientState.loyalty), x + PAD + 2, ly, Ui.TEXT);
		Ui.bar(ctx, x + PAD + 2, ly + 9, W - PAD * 2 - 4, 3, ClientState.loyalty, loyaltyColor(ClientState.loyalty));
		ly += 15;
		ctx.drawTextWithShadow(tr, Text.translatable("diktatura.hud.fear", ClientState.fear), x + PAD + 2, ly, 0xFFB8B0C0);
		Ui.bar(ctx, x + PAD + 2, ly + 9, W - PAD * 2 - 4, 3, ClientState.fear, Ui.PURPLE);
		ly += 15;
		ctx.drawTextWithShadow(tr, Text.translatable("diktatura.hud.balance", ClientState.balance), x + PAD + 2, ly,
			ClientState.balance < 0 ? 0xFFFF7070 : 0xFFFFD27F);
		y += ph + 3;

		// --- Статус (только если есть что показать)
		List<Line> status = statusLines();
		if (!status.isEmpty()) {
			boolean danger = ClientState.rebellion || ClientState.stage == 1 || ClientState.stage == 2;
			int accent = ClientState.rebellion ? (Ui.blink(500) ? Ui.RED : 0xFF701818)
				: danger ? Ui.RED : ClientState.stage == 3 ? Ui.GREEN : Ui.CYAN;
			int sh = 6 + status.size() * 10;
			int sw = W;
			for (Line l : status) {
				sw = Math.max(sw, tr.getWidth(l.text) + PAD * 2 + 4);
			}
			Ui.panel(ctx, x, y, sw, sh, accent);
			int sy = y + 4;
			for (Line l : status) {
				ctx.drawTextWithShadow(tr, l.text, x + PAD + 2, sy, l.color);
				sy += 10;
			}
			y += sh + 3;
		}

		// --- Указ
		Text decree = Text.translatable("diktatura.hud.decree", Ui.time(ClientState.decreeSeconds), ClientState.laws.size());
		int dw = Math.max(W, tr.getWidth(decree) + PAD * 2 + 4);
		boolean soon = ClientState.decreeSeconds <= 10;
		Ui.panel(ctx, x, y, dw, 15, soon && Ui.blink(400) ? 0xFFFFE080 : Ui.GOLD);
		ctx.drawTextWithShadow(tr, decree, x + PAD + 2, y + 4, soon ? 0xFFFFD27F : 0xFFA8A8A8);
	}

	private static List<Line> statusLines() {
		List<Line> out = new ArrayList<>();
		switch (ClientState.stage) {
			case 1 -> out.add(new Line(Text.translatable("diktatura.hud.to_prison"), 0xFFFF5555));
			case 4 -> {
				String key = switch (ClientState.minigame) {
					case 0 -> "parkour";
					case 1 -> "quarry";
					default -> "maze";
				};
				out.add(new Line(Text.translatable("diktatura.hud.minigame",
					Text.translatable("diktatura.minigame." + key)), 0xFFFFAA00));
				if (ClientState.minigame == 1) {
					out.add(new Line(Text.translatable("diktatura.hud.quarry", ClientState.progress, ClientState.goal), 0xFFFFD27F));
				} else if (ClientState.minigame == 0 && ClientState.progress > 0) {
					out.add(new Line(Text.translatable("diktatura.hud.checkpoint"), 0xFF55FF55));
				}
				out.add(new Line(Text.translatable("diktatura.hud.then_sentence", Ui.time(ClientState.sentenceSeconds)), 0xFFB0B0B0));
			}
			case 2 -> out.add(new Line(Text.translatable("diktatura.hud.sentence", Ui.time(ClientState.sentenceSeconds)), 0xFFFF5555));
			case 3 -> out.add(new Line(Text.translatable("diktatura.hud.returning"), 0xFF55FF55));
			default -> {
			}
		}
		if (!ClientState.jobTitle.getString().isEmpty()) {
			out.add(new Line(Text.translatable("diktatura.hud.job", ClientState.jobTitle), 0xFFFFD27F));
			out.add(new Line(Text.translatable("diktatura.hud.job.status", ClientState.jobProgress, ClientState.jobGoal,
				Ui.time(ClientState.jobSeconds)), ClientState.jobSeconds <= 30 ? 0xFFFF7070 : 0xFFC8C8C8));
			if (ClientState.jobHasTarget) {
				MinecraftClient mc = MinecraftClient.getInstance();
				int dist = mc.player == null ? 0 : (int) Math.sqrt(mc.player.squaredDistanceTo(
					ClientState.jobX + 0.5, mc.player.getY(), ClientState.jobZ + 0.5));
				out.add(new Line(Text.translatable("diktatura.hud.job.address", ClientState.jobX, ClientState.jobZ, dist),
					0xFF8FD0FF));
			}
		}
		if (ClientState.timeoutSeconds > 0) {
			out.add(new Line(Text.translatable("diktatura.hud.timeout", Ui.time(ClientState.timeoutSeconds)), 0xFFFFAA00));
		}
		if (ClientState.rebellion) {
			out.add(new Line(Text.translatable("diktatura.hud.rebellion"), Ui.blink(500) ? 0xFFFF2222 : 0xFFAA1111));
		}
		if (ClientState.event >= 0) {
			String[] keys = {"amnesty", "gifts", "confiscation", "blind_eye", "no_gravity"};
			String key = ClientState.event < keys.length ? keys[ClientState.event] : "unknown";
			out.add(new Line(Text.translatable("diktatura.hud.event",
				Text.translatable("diktatura.event." + key), Ui.time(ClientState.eventSeconds)), 0xFF55FFFF));
		}
		return out;
	}

	private static int loyaltyColor(int v) {
		if (v >= 60) {
			return Ui.GREEN;
		}
		if (v >= 25) {
			return 0xFFE0C030;
		}
		return Ui.RED;
	}
}
