package com.diktatura.bureau;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.network.UiPayloads;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.ui.Voice;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

/**
 * Госпропаганда: неубираемая реклама на 13 секунд (у каждого игрока свой таймер) и
 * сообщения диктора госрадио в чате. Игра на время рекламы не останавливается.
 */
public final class Propaganda {
	/** Сколько разных рекламных плакатов (ключи diktatura.ad.N.*). */
	public static final int ADS = 6;
	/** Сколько реплик у диктора (diktatura.radio.N). */
	private static final int RADIO_LINES = 10;

	private Propaganda() {
	}

	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % 20 != 0) {
			return;
		}
		DiktaturaConfig cfg = DiktaturaConfig.get();
		DiktaturaState state = DiktaturaState.get(server);
		Random rnd = server.getOverworld().getRandom();
		if (cfg.adMinMinutes > 0) {
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				PlayerRecord rec = state.player(p);
				if (rec.adTicks < 0) {
					rec.adTicks = schedule(rnd, cfg.adMinMinutes, cfg.adMaxMinutes);
				}
				rec.adTicks -= 20;
				if (rec.adTicks <= 0) {
					rec.adTicks = schedule(rnd, cfg.adMinMinutes, cfg.adMaxMinutes);
					if (Visits.canVisit(p, rec)) {
						showAd(p, rnd.nextInt(ADS));
					}
				}
			}
		}
		if (cfg.propagandaMinutes > 0) {
			if (state.propagandaTicks < 0) {
				state.propagandaTicks = cfg.propagandaMinutes * 1200;
			}
			state.propagandaTicks -= 20;
			if (state.propagandaTicks <= 0) {
				state.propagandaTicks = cfg.propagandaMinutes * 1200;
				if (!server.getPlayerManager().getPlayerList().isEmpty()) {
					Voice.broadcast(server, Text.translatable("diktatura.radio.speaker"),
						Text.translatable("diktatura.radio." + rnd.nextInt(RADIO_LINES)));
				}
			}
		}
	}

	private static int schedule(Random rnd, int minMinutes, int maxMinutes) {
		int min = Math.max(1, minMinutes) * 1200;
		int max = Math.max(min, maxMinutes * 1200);
		return min + rnd.nextInt(max - min + 1);
	}

	/** Показать рекламу (клиент без мода её не увидит — и слава вождю). */
	public static void showAd(ServerPlayerEntity p, int ad) {
		if (ServerPlayNetworking.canSend(p, UiPayloads.Ad.ID)) {
			ServerPlayNetworking.send(p, new UiPayloads.Ad(Math.floorMod(ad, ADS), 13));
		}
	}
}
