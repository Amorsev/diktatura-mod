package com.diktatura.casino.poker;

import com.diktatura.casino.CasinoSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.List;

/** Служебное для покера: прогон раздач между ботами ({@code /diktatura poker sim}). Игру за столом ведёт {@link PokerGame}. */
public final class PokerService {
	private PokerService() {
	}

	/** Команда /diktatura poker sim: прогон раздач в отдельном потоке, итог — в чат. */
	public static void simulate(MinecraftServer server, int hands, java.util.function.Consumer<List<Text>> report) {
		int iterations = Math.min(120, CasinoSettings.get().poker().botIterations());
		Thread th = new Thread(() -> {
			PokerSim.Result r = PokerSim.run(hands, System.nanoTime(), iterations);
			List<Text> out = new java.util.ArrayList<>();
			out.add(Text.translatable("diktatura.cmd.poker.sim.result", r.hands(), r.tax(), r.sidePots(), r.splits(), r.bluffs()));
			for (PokerBot.Level l : PokerBot.Level.values()) {
				out.add(Text.translatable("diktatura.cmd.poker.sim.profit",
					Text.translatable("diktatura.poker.bot." + l.id), r.profit().getOrDefault(l, 0L)));
			}
			out.add(r.mismatches() == 0 && r.errors() == 0
				? Text.translatable("diktatura.cmd.poker.sim.ok")
				: Text.translatable("diktatura.cmd.poker.sim.fail", r.mismatches(), r.errors(), String.valueOf(r.firstError())));
			server.execute(() -> report.accept(out));
		}, "diktatura-poker-sim");
		th.setDaemon(true);
		th.start();
	}
}
