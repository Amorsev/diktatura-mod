package com.diktatura.command;

import com.diktatura.util.Teleports;
import com.diktatura.dictator.Dictator;
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
}
