package com.diktatura.network;

import com.diktatura.Diktatura;
import com.diktatura.dictator.Dictator;
import com.diktatura.law.LawRegistry;
import com.diktatura.loyalty.LoyaltySystem;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public final class Network {
	private Network() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(StatusPayload.ID, StatusPayload.CODEC);
	}

	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % 20 != 0) {
			return;
		}
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			sendStatus(server, p);
		}
	}

	public static void sendStatus(MinecraftServer server, ServerPlayerEntity p) {
		if (!ServerPlayNetworking.canSend(p, StatusPayload.ID)) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(p);
		List<String> laws = state.activeLaws.stream().filter(id -> LawRegistry.get(id) != null).toList();
		ServerPlayNetworking.send(p, new StatusPayload(rec.loyalty, rec.fear, rec.stage.ordinal(),
			rec.sentenceTicks / 20, rec.timeoutTicks / 20, Dictator.secondsToDecree(server),
			LoyaltySystem.isRebellion(server), laws));
	}
}
