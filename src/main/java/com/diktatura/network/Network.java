package com.diktatura.network;

import com.diktatura.Diktatura;
import com.diktatura.dictator.Dictator;
import com.diktatura.event.EventSystem;
import com.diktatura.event.EventType;
import com.diktatura.job.JobRegistry;
import com.diktatura.job.Jobs;
import com.diktatura.law.LawRegistry;
import com.diktatura.loyalty.LoyaltySystem;
import com.diktatura.minigame.MinigameSystem;
import com.diktatura.minigame.MinigameType;
import com.diktatura.npc.NpcInteractions;
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

	/** Растёт при любом изменении законов; по нему рассылаем {@link UiPayloads.LawList}. */
	private static volatile int lawVersion;
	private static int sentLawVersion = -1;

	public static void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(StatusPayload.ID, StatusPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(UiPayloads.Alert.ID, UiPayloads.Alert.CODEC);
		PayloadTypeRegistry.playS2C().register(UiPayloads.Headline.ID, UiPayloads.Headline.CODEC);
		PayloadTypeRegistry.playS2C().register(UiPayloads.Speech.ID, UiPayloads.Speech.CODEC);
		PayloadTypeRegistry.playS2C().register(UiPayloads.Wanted.ID, UiPayloads.Wanted.CODEC);
		PayloadTypeRegistry.playS2C().register(UiPayloads.LawList.ID, UiPayloads.LawList.CODEC);
		PayloadTypeRegistry.playS2C().register(UiPayloads.NpcScreen.ID, UiPayloads.NpcScreen.CODEC);
		PayloadTypeRegistry.playS2C().register(UiPayloads.LicenseList.ID, UiPayloads.LicenseList.CODEC);
		PayloadTypeRegistry.playC2S().register(UiPayloads.NpcClick.ID, UiPayloads.NpcClick.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(UiPayloads.NpcClick.ID, (payload, context) ->
			context.server().execute(() -> NpcInteractions.onClick(context.player(), payload)));
	}

	public static void lawsChanged() {
		lawVersion++;
	}

	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % 20 != 0) {
			return;
		}
		int v = lawVersion;
		if (v != sentLawVersion) {
			sentLawVersion = v;
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				sendLawList(server, p);
			}
		}
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			sendStatus(server, p);
			com.diktatura.license.Licenses.sendList(server, p);
		}
	}

	public static void sendLawList(MinecraftServer server, ServerPlayerEntity p) {
		if (!ServerPlayNetworking.canSend(p, UiPayloads.LawList.ID)) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		List<String> all = LawRegistry.all().stream().map(l -> l.key()).toList();
		List<String> repealed = state.repealedLaws.stream()
			.filter(id -> LawRegistry.get(id) != null && !state.activeLaws.contains(id)).toList();
		ServerPlayNetworking.send(p, new UiPayloads.LawList(all, repealed));
	}

	public static void sendStatus(MinecraftServer server, ServerPlayerEntity p) {
		if (!ServerPlayNetworking.canSend(p, StatusPayload.ID)) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(p);
		List<String> laws = state.activeLaws.stream().filter(id -> LawRegistry.get(id) != null).toList();
		EventType event = EventSystem.active();
		JobRegistry.JobDef jobDef = Jobs.def(rec);
		net.minecraft.text.Text jobTitle = jobDef == null ? net.minecraft.text.Text.empty()
			: Jobs.title(jobDef, rec.job.getInt("v"));
		net.minecraft.util.math.BlockPos jobTarget = Jobs.target(rec);
		int goal = rec.minigame == MinigameType.QUARRY.ordinal() ? MinigameSystem.QUARRY_GOAL
			: rec.minigame == MinigameType.PARKOUR.ordinal() ? 2 : 1;
		ServerPlayNetworking.send(p, new StatusPayload(rec.loyalty, rec.fear, rec.stage.ordinal(),
			rec.sentenceTicks / 20, rec.timeoutTicks / 20, Dictator.secondsToDecree(server),
			LoyaltySystem.isRebellion(server), rec.minigame, rec.progress, goal,
			event == null ? -1 : event.ordinal(), EventSystem.activeSeconds(), EventSystem.blindSide(p.getUuid()), laws,
			rec.trudodni, jobTitle, Jobs.progress(rec), Jobs.goal(rec), Jobs.secondsLeft(rec),
			jobTarget != null && jobDef != null && jobDef.type() == JobRegistry.Type.COURIER,
			jobTarget == null ? 0 : jobTarget.getX(), jobTarget == null ? 0 : jobTarget.getZ()));
	}
}
