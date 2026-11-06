package com.diktatura.dictator;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.law.Law;
import com.diktatura.law.LawEngine;
import com.diktatura.law.LawRegistry;
import com.diktatura.loyalty.LoyaltySystem;
import com.diktatura.network.UiPayloads;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.network.UiPayloads.HeadlineStyle;
import com.diktatura.npc.ModEntities;
import com.diktatura.npc.NpcEntity;
import com.diktatura.npc.NpcInteractions;
import com.diktatura.npc.NpcRegistry;
import com.diktatura.ui.Hud;
import com.diktatura.ui.Voice;
import com.diktatura.prison.PrisonLayout;
import com.diktatura.prison.PrisonSystem;
import com.diktatura.punishment.Punishments;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.Stage;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Диктатор — NPC на троне в тюрьме, один на сервер. По фиксированному таймеру издаёт
 * указы: вводит, отменяет или заменяет законы.
 */
public final class Dictator {
	public static final String TAG = "diktatura_dictator";
	private static final int PHRASES = 8;

	/** Указы меняют только законы. Амнистия, подарки и прочее — события ({@link com.diktatura.event.EventSystem}). */
	public enum DecreeType { NEW_LAW, REPEAL, SWAP }

	private Dictator() {
	}

	/** Последняя реплика вождя для каждого игрока — чтобы окно и субтитры совпадали. */
	private static final Map<UUID, Integer> LAST_PHRASE = new HashMap<>();
	/** Цвет полосы ведомства «Администрация вождя». */
	public static final int ACCENT = 0xFFD4A017;

	/** Идентификатор вождя в реестре НПС (data/diktatura/diktatura/npcs/dictator.json). */
	public static final String NPC_ID = "diktatura:dictator";

	public static void registerEvents() {
		NpcInteractions.register("dictator", new NpcInteractions.Handler() {
			@Override
			public boolean matches(Entity entity) {
				return isDictator(entity);
			}

			@Override
			public void onOpen(ServerPlayerEntity player, Entity npc) {
				newPhrase(player, npc);
			}

			@Override
			public UiPayloads.NpcScreen screen(ServerPlayerEntity player, Entity npc) {
				return window(player, npc);
			}

			@Override
			public boolean action(ServerPlayerEntity player, Entity npc, String action) {
				if ("talk".equals(action)) {
					newPhrase(player, npc);
					return true;
				}
				return false;
			}

			@Override
			public void fallback(ServerPlayerEntity player, Entity npc) {
				speak(player);
			}
		});
	}

	private static NpcRegistry.NpcData data() {
		return NpcRegistry.get(NPC_ID);
	}

	private static Text name() {
		NpcRegistry.NpcData d = data();
		return d != null ? d.displayName() : Text.translatable("entity.diktatura.dictator");
	}

	private static int phraseCount() {
		NpcRegistry.NpcData d = data();
		return d != null ? d.phraseCount() : PHRASES;
	}

	private static Text phrase(int i) {
		NpcRegistry.NpcData d = data();
		return d != null ? d.phrase(i) : Text.translatable("diktatura.dictator.say." + Math.floorMod(i, PHRASES));
	}

	private static void newPhrase(ServerPlayerEntity player, Entity npc) {
		int phrase = player.getRandom().nextInt(phraseCount());
		LAST_PHRASE.put(player.getUuid(), phrase);
		NpcRegistry.NpcData d = data();
		Voice.say(npc, name(), phrase(phrase),
			d != null ? d.soundEvent() : SoundEvents.ENTITY_VILLAGER_AMBIENT, d != null ? d.pitch() : 0.6f);
	}

	/** Окно вождя: реплика, сводка, кнопки. */
	private static UiPayloads.NpcScreen window(ServerPlayerEntity player, Entity npc) {
		MinecraftServer server = player.getServer();
		DiktaturaState state = DiktaturaState.get(server);
		int phrase = LAST_PHRASE.getOrDefault(player.getUuid(), 0);
		int laws = (int) state.activeLaws.stream().filter(id -> LawRegistry.get(id) != null).count();
		int sec = secondsToDecree(server);
		List<Text> info = new ArrayList<>();
		info.add(Text.translatable("diktatura.npc.dictator.info.laws", laws));
		info.add(Text.translatable("diktatura.npc.dictator.info.loyalty", state.player(player).loyalty));
		info.add(Text.translatable("diktatura.npc.dictator.info.decree", String.format("%d:%02d", sec / 60, sec % 60)));
		info.add(Text.translatable("diktatura.npc.dictator.info.decrees", state.decreeCount));
		if (LoyaltySystem.isRebellion(server)) {
			info.add(Text.translatable("diktatura.npc.dictator.info.rebellion").formatted(Formatting.RED));
		}
		List<UiPayloads.NpcAction> actions = List.of(
			new UiPayloads.NpcAction("talk", Text.translatable("diktatura.npc.dictator.talk"), true),
			new UiPayloads.NpcAction("client:laws", Text.translatable("diktatura.npc.laws"), true),
			new UiPayloads.NpcAction("petition", Text.translatable("diktatura.npc.dictator.petition"), false),
			new UiPayloads.NpcAction("client:close", Text.translatable("diktatura.npc.leave"), true));
		NpcRegistry.NpcData d = data();
		return new UiPayloads.NpcScreen("dictator", npc.getId(), name(),
			d != null ? Text.translatable(d.role()) : Text.translatable("diktatura.npc.dictator.role"),
			phrase(phrase), info, actions, d != null ? d.accent() : ACCENT);
	}

	public static boolean isDictator(Entity e) {
		return e instanceof NpcEntity n && NPC_ID.equals(n.npcId());
	}

	/** Вождь из версий до 0.1.5 — житель с тегом. Нужен только для замены. */
	private static boolean isLegacyDictator(Entity e) {
		return e instanceof VillagerEntity && e.getCommandTags().contains(TAG);
	}

	private static int intervalTicks() {
		return Math.max(1, DiktaturaConfig.get().decreeIntervalMinutes) * 1200;
	}

	public static int secondsToDecree(MinecraftServer server) {
		return Math.max(0, DiktaturaState.get(server).ticksToDecree / 20);
	}

	public static void tick(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (state.ticksToDecree < 0 || state.ticksToDecree > intervalTicks()) {
			state.ticksToDecree = intervalTicks();
		}
		if (--state.ticksToDecree <= 0) {
			state.ticksToDecree = intervalTicks();
			issueDecree(server, null);
		}
		if (Diktatura.ticks() % 100 == 0) {
			state.markDirty();
		}
		if (Diktatura.ticks() % 200 == 0) {
			ensureNpc(server);
		}
	}

	// ---------------------------------------------------------------- NPC

	public static void ensureNpc(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (!state.prisonGenerated) {
			return;
		}
		ServerWorld world = server.getOverworld();
		BlockPos throne = state.prisonOrigin.add(PrisonLayout.THRONE);
		if (!world.isChunkLoaded(throne.getX() >> 4, throne.getZ() >> 4)) {
			return;
		}
		Box area = new Box(throne).expand(48);

		// Миграция: старого вождя-жителя заменяем новым НПС (один раз на мир).
		List<VillagerEntity> legacy = world.getEntitiesByClass(VillagerEntity.class, area, Dictator::isLegacyDictator);
		if (!legacy.isEmpty()) {
			legacy.forEach(Entity::discard);
			if (state.npcVersion < 2) {
				Diktatura.LOGGER.info("Миграция мира: старый вождь-житель ({} шт.) заменён на НПС {}", legacy.size(), NPC_ID);
			}
			state.dictator = null;
		}
		if (state.npcVersion < 2) {
			state.npcVersion = 2;
			state.markDirty();
		}

		Entity npc = state.dictator == null ? null : world.getEntity(state.dictator);
		if (!(npc instanceof NpcEntity) || npc.isRemoved()) {
			List<NpcEntity> found = world.getEntitiesByClass(NpcEntity.class, area, Dictator::isDictator);
			if (!found.isEmpty()) {
				npc = found.get(0);
				for (int i = 1; i < found.size(); i++) {
					found.get(i).discard();
				}
			} else {
				npc = spawn(world, throne);
			}
			if (npc != null) {
				state.dictator = npc.getUuid();
				state.markDirty();
			}
		}
		if (npc != null && npc.squaredDistanceTo(throne.getX() + 0.5, throne.getY(), throne.getZ() + 0.5) > 2.0) {
			npc.refreshPositionAndAngles(throne.getX() + 0.5, throne.getY(), throne.getZ() + 0.5, 90.0f, 0.0f);
		}
	}

	private static NpcEntity spawn(ServerWorld world, BlockPos throne) {
		NpcEntity npc = ModEntities.NPC.create(world);
		if (npc == null) {
			return null;
		}
		npc.setNpcId(NPC_ID);
		npc.refreshPositionAndAngles(throne.getX() + 0.5, throne.getY(), throne.getZ() + 0.5, 90.0f, 0.0f);
		npc.setHeadYaw(90.0f);
		npc.setBodyYaw(90.0f);
		npc.addCommandTag(TAG);
		NpcRegistry.apply(npc);
		if (npc.getCustomName() == null) {
			npc.setCustomName(Text.translatable("entity.diktatura.dictator").formatted(Formatting.GOLD, Formatting.BOLD));
			npc.setCustomNameVisible(true);
		}
		world.spawnEntity(npc);
		return npc;
	}

	/** Перечитать данные (имя, масштаб, предмет) после /reload. */
	public static void refresh(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (state.dictator != null && server.getOverworld().getEntity(state.dictator) instanceof NpcEntity npc) {
			NpcRegistry.apply(npc);
		}
	}

	public static void speak(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		int phrase = player.getRandom().nextInt(phraseCount());
		player.sendMessage(Text.translatable("diktatura.dictator.prefix").formatted(Formatting.GOLD)
			.append(phrase(phrase).copy().formatted(Formatting.YELLOW)), false);
		player.sendMessage(Text.translatable("diktatura.dictator.info", state.activeLaws.size(),
			state.player(player).loyalty, secondsToDecree(server) / 60).formatted(Formatting.GRAY), false);
	}

	// ---------------------------------------------------------------- указы

	public static void issueDecree(MinecraftServer server, DecreeType forced) {
		DiktaturaState state = DiktaturaState.get(server);
		DiktaturaConfig cfg = DiktaturaConfig.get();
		Random rnd = server.getOverworld().getRandom();

		List<Law> inactive = new ArrayList<>();
		List<Law> repealable = new ArrayList<>();
		for (Law law : LawRegistry.all()) {
			if (state.activeLaws.contains(law.key())) {
				if (!law.permanent()) {
					repealable.add(law);
				}
			} else if (law.weight() > 0) {
				inactive.add(law);
			}
		}
		int active = (int) state.activeLaws.stream().filter(id -> LawRegistry.get(id) != null).count();

		DecreeType type = forced;
		if (type == null) {
			if (active < cfg.minActiveLaws && !inactive.isEmpty()) {
				type = DecreeType.NEW_LAW;
			} else if (active > cfg.maxActiveLaws && !repealable.isEmpty()) {
				type = DecreeType.REPEAL;
			} else {
				int r = rnd.nextInt(100);
				type = r < 45 ? DecreeType.NEW_LAW : r < 70 ? DecreeType.REPEAL : DecreeType.SWAP;
			}
		}
		if ((type == DecreeType.NEW_LAW || type == DecreeType.SWAP) && inactive.isEmpty()) {
			type = DecreeType.REPEAL;
		}
		if ((type == DecreeType.REPEAL || type == DecreeType.SWAP) && repealable.isEmpty()) {
			type = DecreeType.NEW_LAW;
		}
		if ((type == DecreeType.NEW_LAW && inactive.isEmpty()) || (type == DecreeType.REPEAL && repealable.isEmpty())) {
			return; // менять нечего
		}

		state.decreeCount++;
		List<MutableText> lines = new ArrayList<>();
		switch (type) {
			case NEW_LAW -> {
				Law law = pickWeighted(inactive, rnd);
				LawEngine.setActive(server, law.key(), true);
				lines.add(Text.translatable("diktatura.decree.new_law", law.name().formatted(Formatting.RED)));
				lines.add(law.description().formatted(Formatting.GRAY));
			}
			case REPEAL -> {
				Law law = repealable.get(rnd.nextInt(repealable.size()));
				LawEngine.setActive(server, law.key(), false);
				lines.add(Text.translatable("diktatura.decree.repeal", law.name().formatted(Formatting.GREEN)));
			}
			case SWAP -> {
				Law out = repealable.get(rnd.nextInt(repealable.size()));
				Law in = pickWeighted(inactive, rnd);
				LawEngine.setActive(server, out.key(), false);
				LawEngine.setActive(server, in.key(), true);
				lines.add(Text.translatable("diktatura.decree.repeal", out.name().formatted(Formatting.GREEN)));
				lines.add(Text.translatable("diktatura.decree.new_law", in.name().formatted(Formatting.RED)));
				lines.add(in.description().formatted(Formatting.GRAY));
			}
		}
		state.markDirty();
		announce(server, state.decreeCount, lines);
	}

	private static Law pickWeighted(List<Law> laws, Random rnd) {
		int total = laws.stream().mapToInt(Law::weight).sum();
		if (total <= 0) {
			return laws.get(rnd.nextInt(laws.size()));
		}
		int r = rnd.nextInt(total);
		for (Law l : laws) {
			r -= l.weight();
			if (r < 0) {
				return l;
			}
		}
		return laws.get(laws.size() - 1);
	}

	private static void announce(MinecraftServer server, int number, List<MutableText> lines) {
		var pm = server.getPlayerManager();
		pm.broadcast(Text.translatable("diktatura.decree.header", number).formatted(Formatting.GOLD, Formatting.BOLD), false);
		for (MutableText line : lines) {
			pm.broadcast(line, false);
		}
		Text first = lines.isEmpty() ? Text.empty() : lines.get(0);
		Hud.headlineAll(server, HeadlineStyle.DECREE, Text.translatable("diktatura.ui.headline.decree"),
			Text.translatable("diktatura.ui.headline.decree.sub", number), 5);
		Hud.alertAll(server, AlertKind.DECREE, Text.translatable("diktatura.decree.title", number), first, 10);
		Voice.broadcast(server, name(), Text.translatable("diktatura.dictator.decree_voice." + rndPhrase(server)));
		for (ServerPlayerEntity p : pm.getPlayerList()) {
			p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
				SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.MASTER, 0.6f, 1.0f);
		}
	}

	private static final int DECREE_VOICES = 4;

	private static int rndPhrase(MinecraftServer server) {
		return server.getOverworld().getRandom().nextInt(DECREE_VOICES);
	}
}
