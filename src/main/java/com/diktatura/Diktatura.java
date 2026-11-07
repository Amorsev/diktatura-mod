package com.diktatura;

import com.diktatura.command.DiktaturaCommands;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.dictator.Dictator;
import com.diktatura.event.EventSystem;
import com.diktatura.item.ModItems;
import com.diktatura.law.LawEngine;
import com.diktatura.law.LawRegistry;
import com.diktatura.loyalty.LoyaltySystem;
import com.diktatura.minigame.MinigameSystem;
import com.diktatura.job.JobRegistry;
import com.diktatura.job.Jobs;
import com.diktatura.license.LicenseRegistry;
import com.diktatura.license.Licenses;
import com.diktatura.network.Network;
import com.diktatura.npc.ModEntities;
import com.diktatura.npc.NpcInteractions;
import com.diktatura.npc.NpcRegistry;
import com.diktatura.prison.PrisonSystem;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.transport.TransportSystem;
import com.diktatura.wealth.UpgraderBridge;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входа мода. Вся игровая логика работает на сервере (в том числе на встроенном
 * сервере одиночной игры); клиент получает только данные для HUD и книги законов.
 */
public class Diktatura implements ModInitializer {
	public static final String MOD_ID = "diktatura";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static MinecraftServer server;
	private static long ticks;

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	public static MinecraftServer server() {
		return server;
	}

	/** Счётчик тиков сервера с момента запуска (для кулдаунов и периодических проверок). */
	public static long ticks() {
		return ticks;
	}

	@Override
	public void onInitialize() {
		DiktaturaConfig.load();
		com.diktatura.goose.ModSounds.register();
		ModEntities.register();
		ModItems.register();
		com.diktatura.control.ModControl.register();
		Network.registerPayloads();
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new LawRegistry.ReloadListener());
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new NpcRegistry.ReloadListener());
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new JobRegistry.ReloadListener());
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new LicenseRegistry.ReloadListener());
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new com.diktatura.casino.CasinoSettings.ReloadListener());
		LawEngine.registerEvents();
		Dictator.registerEvents();
		Jobs.registerEvents();
		Licenses.registerEvents();
		com.diktatura.office.Offices.registerEvents();
		com.diktatura.casino.Casino.registerEvents();
		com.diktatura.bureau.Visits.registerEvents();
		com.diktatura.bureau.Captcha.registerEvents();
		com.diktatura.control.Enforcers.registerEvents();
		com.diktatura.finale.BossFx.registerEvents();
		com.diktatura.finale.GuardDragon.registerEvents();
		com.diktatura.finale.FinalFight.registerEvents();
		com.diktatura.mechanics.Timber.registerEvents();
		com.diktatura.mechanics.CaveIns.registerEvents();
		NpcInteractions.registerEvents();
		MinigameSystem.registerEvents();
		EventSystem.registerEvents();
		DiktaturaCommands.register();
		UpgraderBridge.init();

		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			ticks = 0;
			LawEngine.initLaws(s);
			com.diktatura.world.Capital.ensureGenerated(s);
			PrisonSystem.ensureGenerated(s);
			com.diktatura.dictator.ThroneHall.ensureGenerated(s);
			MinigameSystem.ensureGenerated(s);
			Dictator.ensureNpc(s);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			EventSystem.stop(s);
			com.diktatura.bureau.Weather.clear();
			com.diktatura.mechanics.CaveIns.clear();
			TransportSystem.shutdown(s);
			LawEngine.clear();
			com.diktatura.finale.GuardDragon.clear();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((s, manager, success) -> Dictator.refresh(s));

		ServerTickEvents.END_SERVER_TICK.register(Diktatura::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
			com.diktatura.bureau.Captcha.forget(handler.player.getUuid());
			com.diktatura.control.CurfewDoubles.forget(handler.player.getUuid());
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			ServerPlayerEntity player = handler.player;
			LawEngine.defer(() -> onJoin(s, player));
		});
	}

	private static void tick(MinecraftServer s) {
		ticks++;
		LawEngine.tick(s);
		TransportSystem.tick(s);
		MinigameSystem.tick(s);
		PrisonSystem.tick(s);
		LoyaltySystem.tick(s);
		Dictator.tick(s);
		Jobs.tick(s);
		Licenses.tick(s);
		com.diktatura.office.Offices.tick(s);
		com.diktatura.casino.Casino.tick(s);
		com.diktatura.bureau.Visits.tick(s);
		com.diktatura.bureau.Weather.tick(s);
		com.diktatura.bureau.Propaganda.tick(s);
		com.diktatura.mechanics.CaveIns.tick(s);
		com.diktatura.mechanics.Colds.tick(s);
		com.diktatura.goose.GooseSpawner.tick(s);
		com.diktatura.control.CurfewDoubles.tick(s);
		com.diktatura.control.Trips.tick(s);
		com.diktatura.finale.GuardDragon.tick(s);
		com.diktatura.finale.FinalFight.tick(s);
		EventSystem.tick(s);
		Network.tick(s);
	}

	private static void onJoin(MinecraftServer s, ServerPlayerEntity joined) {
		ServerPlayerEntity player = s.getPlayerManager().getPlayer(joined.getUuid());
		if (player == null) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(s);
		PlayerRecord rec = state.player(player);
		if (!rec.gotBook && DiktaturaConfig.get().giveLawBook && rec.stage == com.diktatura.state.Stage.FREE) {
			rec.gotBook = true;
			player.getInventory().offerOrDrop(new ItemStack(ModItems.LAW_BOOK));
			state.markDirty();
		}
		EventSystem.onJoin(player);
		PrisonSystem.onJoin(s, player);
		Network.sendStatus(s, player);
		Network.sendLawList(s, player);
	}
}
