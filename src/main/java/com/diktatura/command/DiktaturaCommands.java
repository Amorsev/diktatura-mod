package com.diktatura.command;

import com.diktatura.util.Teleports;
import com.diktatura.dictator.Dictator;
import com.diktatura.dictator.ThroneHall;
import net.minecraft.util.math.BlockPos;
import com.diktatura.event.EventSystem;
import com.diktatura.event.EventType;
import com.diktatura.law.Law;
import com.diktatura.law.LawEngine;
import com.diktatura.law.LawRegistry;
import com.diktatura.minigame.MinigameSystem;
import com.diktatura.minigame.MinigameType;
import com.diktatura.minigame.MinigameZones;
import com.diktatura.network.Network;
import com.diktatura.prison.PrisonSystem;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import com.diktatura.wealth.ItemValues;
import com.diktatura.wealth.UpgraderBridge;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /diktatura laws | status | wealth — для всех;
 * law, decree, arrest, release, loyalty, prison — для операторов.
 */
public final class DiktaturaCommands {
	private static final SuggestionProvider<ServerCommandSource> LAW_IDS =
		(ctx, builder) -> CommandSource.suggestMatching(LawRegistry.ids(), builder);
	private static final SuggestionProvider<ServerCommandSource> LICENSE_IDS =
		(ctx, builder) -> CommandSource.suggestMatching(
			com.diktatura.license.LicenseRegistry.all().stream().map(com.diktatura.license.LicenseRegistry.LicenseDef::id), builder);
	private static final SuggestionProvider<ServerCommandSource> OFFICES =
		(ctx, builder) -> CommandSource.suggestMatching(java.util.Arrays.stream(com.diktatura.office.Offices.Office.values())
			.map(com.diktatura.office.Offices.Office::key), builder);
	private static final SuggestionProvider<ServerCommandSource> NPC_IDS =
		(ctx, builder) -> CommandSource.suggestMatching(
			com.diktatura.npc.NpcRegistry.all().stream().map(com.diktatura.npc.NpcRegistry.NpcData::id), builder);

	private DiktaturaCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			literal("diktatura")
				.then(literal("laws").executes(c -> laws(c.getSource())))
				.then(literal("status")
					.executes(c -> status(c.getSource(), c.getSource().getPlayerOrThrow()))
					.then(argument("player", EntityArgumentType.player()).requires(s -> s.hasPermissionLevel(2))
						.executes(c -> status(c.getSource(), EntityArgumentType.getPlayer(c, "player")))))
				.then(literal("wealth")
					.executes(c -> wealth(c.getSource(), c.getSource().getPlayerOrThrow()))
					.then(argument("player", EntityArgumentType.player()).requires(s -> s.hasPermissionLevel(2))
						.executes(c -> wealth(c.getSource(), EntityArgumentType.getPlayer(c, "player")))))
				.then(literal("law").requires(s -> s.hasPermissionLevel(2))
					.then(literal("list").executes(c -> allLaws(c.getSource())))
					.then(literal("enable").then(argument("id", IdentifierArgumentType.identifier()).suggests(LAW_IDS)
						.executes(c -> setLaw(c, true))))
					.then(literal("disable").then(argument("id", IdentifierArgumentType.identifier()).suggests(LAW_IDS)
						.executes(c -> setLaw(c, false)))))
				.then(literal("decree").requires(s -> s.hasPermissionLevel(2))
					.executes(c -> decree(c.getSource(), null))
					.then(literal("new_law").executes(c -> decree(c.getSource(), Dictator.DecreeType.NEW_LAW)))
					.then(literal("repeal").executes(c -> decree(c.getSource(), Dictator.DecreeType.REPEAL)))
					.then(literal("swap").executes(c -> decree(c.getSource(), Dictator.DecreeType.SWAP))))
				.then(literal("event").requires(s -> s.hasPermissionLevel(2))
					.executes(c -> event(c.getSource(), null))
					.then(literal("stop").executes(c -> {
						EventSystem.stop(c.getSource().getServer());
						c.getSource().sendFeedback(() -> Text.translatable("diktatura.cmd.event_stopped"), true);
						return 1;
					}))
					.then(literal("amnesty").executes(c -> event(c.getSource(), EventType.AMNESTY)))
					.then(literal("gifts").executes(c -> event(c.getSource(), EventType.GIFTS)))
					.then(literal("confiscation").executes(c -> event(c.getSource(), EventType.CONFISCATION)))
					.then(literal("blind_eye").executes(c -> event(c.getSource(), EventType.BLIND_EYE)))
					.then(literal("no_gravity").executes(c -> event(c.getSource(), EventType.NO_GRAVITY))))
				.then(literal("npc").requires(s -> s.hasPermissionLevel(2))
					.then(literal("list").executes(c -> npcList(c.getSource())))
					.then(literal("spawn").then(argument("id", IdentifierArgumentType.identifier()).suggests(NPC_IDS)
						.executes(c -> npcSpawn(c.getSource(), IdentifierArgumentType.getIdentifier(c, "id").toString()))))
					.then(literal("dictator").executes(c -> {
						Dictator.ensureNpc(c.getSource().getServer());
						Dictator.refresh(c.getSource().getServer());
						c.getSource().sendFeedback(() -> Text.translatable("diktatura.cmd.npc.dictator"), true);
						return 1;
					})))
				.then(literal("ui").requires(s -> s.hasPermissionLevel(2))
					.then(literal("alert").executes(c -> uiTest(c.getSource(), "alert")))
					.then(literal("headline").executes(c -> uiTest(c.getSource(), "headline")))
					.then(literal("speech").executes(c -> uiTest(c.getSource(), "speech")))
					.then(literal("wanted").executes(c -> uiTest(c.getSource(), "wanted"))))
				.then(literal("arrest").requires(s -> s.hasPermissionLevel(2))
					.then(argument("player", EntityArgumentType.player())
						.then(argument("seconds", IntegerArgumentType.integer(1, 86400))
							.executes(c -> {
								ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
								PrisonSystem.arrest(p, IntegerArgumentType.getInteger(c, "seconds") * 20,
									Text.translatable("diktatura.reason.command"));
								return 1;
							}))))
				.then(literal("release").requires(s -> s.hasPermissionLevel(2))
					.then(argument("player", EntityArgumentType.player())
						.executes(c -> {
							PrisonSystem.forceRelease(EntityArgumentType.getPlayer(c, "player"));
							return 1;
						})))
				.then(literal("loyalty").requires(s -> s.hasPermissionLevel(2))
					.then(argument("player", EntityArgumentType.player())
						.then(argument("value", IntegerArgumentType.integer(0, 100))
							.executes(c -> {
								ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
								DiktaturaState state = DiktaturaState.get(c.getSource().getServer());
								state.player(p).loyalty = IntegerArgumentType.getInteger(c, "value");
								state.markDirty();
								Network.sendStatus(c.getSource().getServer(), p);
								return status(c.getSource(), p);
							}))))
				.then(literal("money")
					.executes(c -> money(c.getSource(), c.getSource().getPlayerOrThrow()))
					.then(literal("reset").requires(s -> s.hasPermissionLevel(2)).executes(c -> moneyReset(c.getSource())))
					.then(argument("player", EntityArgumentType.player()).requires(s -> s.hasPermissionLevel(2))
						.executes(c -> money(c.getSource(), EntityArgumentType.getPlayer(c, "player")))
						.then(argument("amount", IntegerArgumentType.integer(-1_000_000, 1_000_000))
							.executes(c -> {
								ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
								com.diktatura.economy.Economy.add(p, IntegerArgumentType.getInteger(c, "amount"),
									"diktatura.money.reason.admin", "", true);
								return money(c.getSource(), p);
							}))))
				.then(literal("casino").requires(s -> s.hasPermissionLevel(2))
					.then(literal("tp").executes(c -> {
						ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
						MinecraftServer server = c.getSource().getServer();
						com.diktatura.casino.CasinoBuilding.ensureGenerated(server);
						DiktaturaState state = DiktaturaState.get(server);
						if (state.casinoGenerated) {
							BlockPos door = state.casinoOrigin.add(-2, 1, 5);
							Teleports.to(p, server.getOverworld(), door.getX() + 0.5, door.getY(), door.getZ() + 0.5, -90.0f, 0.0f);
						}
						return 1;
					}))
					.then(literal("rebuild").executes(c -> {
						MinecraftServer server = c.getSource().getServer();
						DiktaturaState state = DiktaturaState.get(server);
						if (state.casinoGenerated) {
							com.diktatura.casino.CasinoBuilding.build(server.getOverworld(), state.casinoOrigin);
						} else {
							com.diktatura.casino.CasinoBuilding.ensureGenerated(server);
						}
						c.getSource().sendFeedback(() -> Text.translatable("diktatura.cmd.casino.rebuilt"), true);
						return 1;
					})))
				.then(literal("office")
					.executes(c -> {
						MinecraftServer server = c.getSource().getServer();
						for (var o : com.diktatura.office.Offices.Office.values()) {
							Text line = com.diktatura.office.Offices.status(server, o);
							c.getSource().sendFeedback(() -> line, false);
						}
						return 1;
					})
					.then(literal("tp").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
						ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
						MinecraftServer server = c.getSource().getServer();
						com.diktatura.office.OfficeBuilding.ensureGenerated(server);
						BlockPos door = com.diktatura.office.Offices.doorPos(server, com.diktatura.office.Offices.Office.EMPLOYER);
						if (door != null) {
							Teleports.to(p, server.getOverworld(), door.getX() + 0.5, door.getY(), door.getZ() + 0.5, -90.0f, 0.0f);
						}
						return 1;
					}))
					.then(literal("rebuild").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
						MinecraftServer server = c.getSource().getServer();
						DiktaturaState state = DiktaturaState.get(server);
						if (state.officesGenerated) {
							com.diktatura.office.OfficeBuilding.build(server.getOverworld(), state.officesOrigin);
						} else {
							com.diktatura.office.OfficeBuilding.ensureGenerated(server);
						}
						c.getSource().sendFeedback(() -> Text.translatable("diktatura.cmd.office.rebuilt"), true);
						return 1;
					}))
					.then(literal("open").requires(s -> s.hasPermissionLevel(2))
						.then(argument("office", com.mojang.brigadier.arguments.StringArgumentType.word()).suggests(OFFICES)
							.executes(c -> officeSet(c, true))))
					.then(literal("close").requires(s -> s.hasPermissionLevel(2))
						.then(argument("office", com.mojang.brigadier.arguments.StringArgumentType.word()).suggests(OFFICES)
							.executes(c -> officeSet(c, false)))))
				.then(literal("license")
					.executes(c -> licenseList(c.getSource(), c.getSource().getPlayerOrThrow()))
					.then(literal("give").requires(s -> s.hasPermissionLevel(2))
						.then(argument("player", EntityArgumentType.player())
							.then(argument("id", IdentifierArgumentType.identifier()).suggests(LICENSE_IDS)
								.executes(c -> licenseGive(c, true)))))
					.then(literal("revoke").requires(s -> s.hasPermissionLevel(2))
						.then(argument("player", EntityArgumentType.player())
							.then(argument("id", IdentifierArgumentType.identifier()).suggests(LICENSE_IDS)
								.executes(c -> licenseGive(c, false))))))
				.then(literal("job")
					.executes(c -> jobInfo(c.getSource(), c.getSource().getPlayerOrThrow()))
					.then(literal("cancel").requires(s -> s.hasPermissionLevel(2))
						.then(argument("player", EntityArgumentType.player()).executes(c -> {
							ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
							com.diktatura.job.Jobs.clear(p, DiktaturaState.get(c.getSource().getServer()).player(p));
							c.getSource().sendFeedback(() -> Text.translatable("diktatura.cmd.job.cancelled", p.getDisplayName()), true);
							return 1;
						}))))
				.then(literal("throne").requires(s -> s.hasPermissionLevel(2))
					.then(literal("tp").executes(c -> {
						ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
						MinecraftServer server = c.getSource().getServer();
						ThroneHall.ensureGenerated(server);
						BlockPos door = DiktaturaState.get(server).throneOrigin.add(-2, 1, 6);
						Teleports.to(p, server.getOverworld(), door.getX() + 0.5, door.getY(), door.getZ() + 0.5, -90.0f, 0.0f);
						return 1;
					}))
					.then(literal("rebuild").executes(c -> {
						MinecraftServer server = c.getSource().getServer();
						DiktaturaState state = DiktaturaState.get(server);
						if (state.throneGenerated) {
							ThroneHall.build(server.getOverworld(), state.throneOrigin);
						} else {
							ThroneHall.ensureGenerated(server);
						}
						Dictator.ensureNpc(server);
						c.getSource().sendFeedback(() -> Text.translatable("diktatura.cmd.throne_rebuilt"), true);
						return 1;
					})))
				.then(literal("prison").requires(s -> s.hasPermissionLevel(2))
					.then(literal("tp").executes(c -> {
						ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
						Vec3d a = PrisonSystem.arrivalPoint(c.getSource().getServer());
						Teleports.to(p, c.getSource().getServer().getOverworld(), a.x, a.y, a.z, p.getYaw(), p.getPitch());
						return 1;
					}))
					.then(literal("minigame").then(argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((ctx, b) -> CommandSource.suggestMatching(java.util.Arrays.stream(MinigameType.values()).map(MinigameType::key), b))
						.executes(c -> {
							String key = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "type");
							MinigameType type = java.util.Arrays.stream(MinigameType.values())
								.filter(t -> t.key().equals(key)).findFirst().orElse(null);
							if (type == null) {
								c.getSource().sendError(Text.literal(key));
								return 0;
							}
							ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
							Vec3d s = Vec3d.ofBottomCenter(MinigameSystem.base(c.getSource().getServer()).add(MinigameZones.start(type)));
							Teleports.to(p, c.getSource().getServer().getOverworld(), s.x, s.y, s.z, -90.0f, 0.0f);
							return 1;
						})))
					.then(literal("rebuild").executes(c -> {
						MinecraftServer server = c.getSource().getServer();
						PrisonSystem.build(server, server.getOverworld(), PrisonSystem.origin(server));
						MinigameSystem.build(server);
						Dictator.ensureNpc(server);
						c.getSource().sendFeedback(() -> Text.translatable("diktatura.cmd.rebuilt"), true);
						return 1;
					})))
		));
	}

	private static int laws(ServerCommandSource src) {
		DiktaturaState state = DiktaturaState.get(src.getServer());
		src.sendFeedback(() -> Text.translatable("diktatura.cmd.laws_header", state.activeLaws.size())
			.formatted(Formatting.GOLD), false);
		for (String id : state.activeLaws) {
			Law law = LawRegistry.get(id);
			if (law != null) {
				MutableText line = Text.literal(" • ").append(law.name().formatted(Formatting.RED))
					.append(Text.literal(" — ").formatted(Formatting.DARK_GRAY))
					.append(law.description().formatted(Formatting.GRAY));
				src.sendFeedback(() -> line, false);
			}
		}
		return state.activeLaws.size();
	}

	private static int allLaws(ServerCommandSource src) {
		DiktaturaState state = DiktaturaState.get(src.getServer());
		for (Law law : LawRegistry.all()) {
			boolean on = state.activeLaws.contains(law.key());
			MutableText line = Text.literal(on ? "[+] " : "[ ] ").formatted(on ? Formatting.GREEN : Formatting.DARK_GRAY)
				.append(Text.literal(law.key()).formatted(Formatting.WHITE))
				.append(Text.literal(" ").append(law.name()).formatted(Formatting.GRAY))
				.append(Text.literal(law.permanent() ? " ★" : "").formatted(Formatting.GOLD));
			src.sendFeedback(() -> line, false);
		}
		return LawRegistry.all().size();
	}

	private static int setLaw(CommandContext<ServerCommandSource> c, boolean on) {
		String id = IdentifierArgumentType.getIdentifier(c, "id").toString();
		Law law = LawRegistry.get(id);
		if (law == null) {
			c.getSource().sendError(Text.translatable("diktatura.cmd.no_law", id));
			return 0;
		}
		LawEngine.setActive(c.getSource().getServer(), id, on);
		c.getSource().sendFeedback(() -> Text.translatable(on ? "diktatura.cmd.law_on" : "diktatura.cmd.law_off",
			law.name()), true);
		return 1;
	}

	private static int event(ServerCommandSource src, EventType type) {
		if (!EventSystem.start(src.getServer(), type)) {
			src.sendError(Text.translatable("diktatura.cmd.event_busy",
				Text.translatable("diktatura.event." + EventSystem.active().key())));
			return 0;
		}
		return 1;
	}

	private static int decree(ServerCommandSource src, Dictator.DecreeType type) {
		Dictator.issueDecree(src.getServer(), type);
		return 1;
	}

	private static int status(ServerCommandSource src, ServerPlayerEntity p) {
		PlayerRecord rec = DiktaturaState.get(src.getServer()).player(p);
		String stage = rec.stage.name().toLowerCase(Locale.ROOT);
		src.sendFeedback(() -> Text.translatable("diktatura.cmd.status", p.getDisplayName(), rec.loyalty, rec.fear,
			Text.translatable("diktatura.stage." + stage), rec.violations, rec.arrests), false);
		if (rec.stage == Stage.IMPRISONED) {
			src.sendFeedback(() -> Text.translatable("diktatura.cmd.sentence",
				PrisonSystem.formatTime(rec.sentenceTicks)), false);
		}
		return rec.loyalty;
	}

	private static int wealth(ServerCommandSource src, ServerPlayerEntity p) {
		double inv = ItemValues.inventoryValue(p, false);
		double all = ItemValues.inventoryValue(p, true);
		src.sendFeedback(() -> Text.translatable("diktatura.cmd.wealth", p.getDisplayName(),
			String.format(Locale.ROOT, "%.0f", inv), String.format(Locale.ROOT, "%.0f", all)), false);
		src.sendFeedback(() -> Text.literal("Upgrader: " + UpgraderBridge.status()).formatted(Formatting.DARK_GRAY), false);
		return (int) inv;
	}

	/** Проверка нового интерфейса: /diktatura ui alert|headline|speech|wanted (только себе). */
	private static int uiTest(ServerCommandSource src, String what) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity p = src.getPlayerOrThrow();
		switch (what) {
			case "alert" -> {
				com.diktatura.ui.Hud.alert(p, com.diktatura.network.UiPayloads.AlertKind.DECREE,
					Text.translatable("diktatura.ui.test.alert"), Text.translatable("diktatura.ui.test.alert.hint"), 6);
				com.diktatura.ui.Hud.alert(p, com.diktatura.network.UiPayloads.AlertKind.DANGER,
					Text.translatable("diktatura.ui.test.danger"), Text.translatable("diktatura.ui.test.danger.hint"), 6);
			}
			case "headline" -> com.diktatura.ui.Hud.headline(p, com.diktatura.network.UiPayloads.HeadlineStyle.DECREE,
				Text.translatable("diktatura.ui.headline.decree"), Text.translatable("diktatura.ui.headline.decree.sub", 0), 5);
			case "speech" -> com.diktatura.ui.Voice.say(p, Text.translatable("entity.diktatura.dictator"),
				Text.translatable("diktatura.dictator.say.1"), null, 1.0f);
			default -> com.diktatura.ui.Hud.wanted(p, Text.translatable("diktatura.reason.command"), 60);
		}
		return 1;
	}

	private static int npcList(ServerCommandSource src) {
		var all = com.diktatura.npc.NpcRegistry.all();
		src.sendFeedback(() -> Text.translatable("diktatura.cmd.npc.list", all.size()).formatted(Formatting.GOLD), false);
		for (var d : all) {
			src.sendFeedback(() -> Text.literal(" • " + d.id() + " — ").append(d.displayName()), false);
		}
		return all.size();
	}

	/** Поставить НПС из JSON на место игрока (смотрит туда же, куда игрок). */
	private static int npcSpawn(ServerCommandSource src, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity p = src.getPlayerOrThrow();
		if (com.diktatura.npc.NpcRegistry.get(id) == null) {
			src.sendError(Text.translatable("diktatura.cmd.npc.unknown", id));
			return 0;
		}
		com.diktatura.npc.NpcEntity npc = com.diktatura.npc.ModEntities.NPC.create(p.getServerWorld());
		if (npc == null) {
			return 0;
		}
		npc.setNpcId(id);
		npc.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0.0f);
		npc.setHeadYaw(p.getYaw());
		npc.setBodyYaw(p.getYaw());
		com.diktatura.npc.NpcRegistry.apply(npc);
		p.getServerWorld().spawnEntity(npc);
		src.sendFeedback(() -> Text.translatable("diktatura.cmd.npc.spawned", id), true);
		return 1;
	}

	/** Баланс трудодней и последние операции. */
	private static int money(ServerCommandSource src, ServerPlayerEntity p) {
		PlayerRecord rec = DiktaturaState.get(src.getServer()).player(p);
		src.sendFeedback(() -> Text.translatable("diktatura.money.balance", p.getDisplayName(), rec.trudodni)
			.formatted(Formatting.GOLD), false);
		int n = Math.min(5, rec.moneyLog.size());
		for (int i = 0; i < n; i++) {
			net.minecraft.nbt.NbtCompound e = rec.moneyLog.getCompound(i);
			int a = e.getInt("a");
			Text line = Text.literal((a > 0 ? " +" : " ") + a + " — ")
				.append(com.diktatura.economy.Economy.reason(e.getString("r"), e.getString("x")))
				.formatted(a > 0 ? Formatting.GREEN : Formatting.RED);
			src.sendFeedback(() -> line, false);
		}
		return rec.trudodni;
	}

	private static int moneyReset(ServerCommandSource src) {
		DiktaturaState state = DiktaturaState.get(src.getServer());
		state.players.values().forEach(r -> {
			r.trudodni = 0;
			r.moneyLog.clear();
		});
		state.markDirty();
		src.sendFeedback(() -> Text.translatable("diktatura.money.reset"), true);
		return 1;
	}

	private static int jobInfo(ServerCommandSource src, ServerPlayerEntity p) {
		PlayerRecord rec = DiktaturaState.get(src.getServer()).player(p);
		var def = com.diktatura.job.Jobs.def(rec);
		if (def == null) {
			src.sendFeedback(() -> Text.translatable("diktatura.job.none"), false);
			return 0;
		}
		int sec = com.diktatura.job.Jobs.secondsLeft(rec);
		src.sendFeedback(() -> Text.translatable("diktatura.job.current", com.diktatura.job.Jobs.title(def, rec.job.getInt("v")))
			.formatted(Formatting.GOLD), false);
		src.sendFeedback(() -> Text.translatable("diktatura.job.status", com.diktatura.job.Jobs.progress(rec),
			com.diktatura.job.Jobs.goal(rec), String.format("%d:%02d", sec / 60, sec % 60)), false);
		var t = com.diktatura.job.Jobs.target(rec);
		if (t != null && def.type() == com.diktatura.job.JobRegistry.Type.COURIER) {
			src.sendFeedback(() -> Text.translatable("diktatura.job.address", t.getX(), t.getZ()), false);
		}
		return 1;
	}

	private static int licenseList(ServerCommandSource src, ServerPlayerEntity p) {
		PlayerRecord rec = DiktaturaState.get(src.getServer()).player(p);
		long now = src.getServer().getOverworld().getTime();
		int n = 0;
		for (String id : rec.licenses.getKeys()) {
			long left = rec.licenses.getLong(id) - now;
			if (left <= 0) {
				continue;
			}
			var def = com.diktatura.license.LicenseRegistry.get(id);
			Text name = def == null ? Text.literal(id) : Text.translatable(def.name());
			src.sendFeedback(() -> Text.translatable("diktatura.cmd.license.entry", name, left / 20).formatted(Formatting.GREEN), false);
			n++;
		}
		if (n == 0) {
			src.sendFeedback(() -> Text.translatable("diktatura.cmd.license.none"), false);
		}
		return n;
	}

	private static int licenseGive(CommandContext<ServerCommandSource> c, boolean give) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
		String id = IdentifierArgumentType.getIdentifier(c, "id").toString();
		var def = com.diktatura.license.LicenseRegistry.get(id);
		if (def == null) {
			c.getSource().sendError(Text.translatable("diktatura.cmd.license.unknown", id));
			return 0;
		}
		if (give) {
			com.diktatura.license.Licenses.grant(p, def);
		} else {
			com.diktatura.license.Licenses.revoke(p, id);
		}
		c.getSource().sendFeedback(() -> Text.translatable(give ? "diktatura.cmd.license.given" : "diktatura.cmd.license.revoked",
			Text.translatable(def.name()), p.getDisplayName()), true);
		return 1;
	}

	private static int officeSet(CommandContext<ServerCommandSource> c, boolean open) {
		var o = com.diktatura.office.Offices.Office.byKey(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "office"));
		if (o == null) {
			c.getSource().sendError(Text.translatable("diktatura.cmd.office.unknown"));
			return 0;
		}
		com.diktatura.office.Offices.setOpen(c.getSource().getServer(), o, open, true);
		Text line = com.diktatura.office.Offices.status(c.getSource().getServer(), o);
		c.getSource().sendFeedback(() -> line, true);
		return 1;
	}
}
