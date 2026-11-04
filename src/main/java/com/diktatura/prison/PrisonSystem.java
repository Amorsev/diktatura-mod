package com.diktatura.prison;

import com.diktatura.util.Teleports;
import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.law.LawEngine;
import com.diktatura.minigame.MinigameSystem;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import com.diktatura.transport.TransportSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * PrisonSystem: тюрьма у спавна, аресты, камеры, сроки и освобождение.
 * Тюрьма ставится один раз при первом запуске мира из NBT-структуры datapack-а.
 */
public final class PrisonSystem {
	public static final Identifier STRUCTURE = Diktatura.id("prison");

	private PrisonSystem() {
	}

	// ---------------------------------------------------------------- генерация

	public static void ensureGenerated(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (state.prisonGenerated) {
			return;
		}
		ServerWorld world = server.getOverworld();
		DiktaturaConfig cfg = DiktaturaConfig.get();
		BlockPos spawn = world.getSpawnPos();
		int ox = spawn.getX() + cfg.prisonOffsetX;
		int oz = spawn.getZ() + cfg.prisonOffsetZ - PrisonLayout.SIZE_Z / 2;

		for (int cx = (ox - 8) >> 4; cx <= (ox + PrisonLayout.SIZE_X + 8) >> 4; cx++) {
			for (int cz = (oz - 8) >> 4; cz <= (oz + PrisonLayout.SIZE_Z + 8) >> 4; cz++) {
				world.getChunk(cx, cz);
			}
		}
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
			ox + PrisonLayout.SIZE_X / 2, oz + PrisonLayout.SIZE_Z / 2);
		int y = Math.max(top, world.getSeaLevel() + 1) - 1;
		BlockPos origin = new BlockPos(ox, y, oz);

		build(server, world, origin);
		state.prisonOrigin = origin;
		state.prisonGenerated = true;
		state.markDirty();
		Diktatura.LOGGER.info("Тюрьма построена в {}", origin.toShortString());
	}

	/** Поставить постройку (из NBT, а если её нет — упрощённую коробку), фундамент и площадку. */
	public static void build(MinecraftServer server, ServerWorld world, BlockPos origin) {
		int flags = Block.NOTIFY_LISTENERS;
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState foundation = Blocks.STONE_BRICKS.getDefaultState();

		// Расчистить место над тюрьмой и перед входом.
		for (int x = -6; x < PrisonLayout.SIZE_X + 1; x++) {
			for (int z = -1; z < PrisonLayout.SIZE_Z + 1; z++) {
				for (int y = 1; y < PrisonLayout.SIZE_Y + 12; y++) {
					BlockPos p = origin.add(x, y, z);
					if (!world.getBlockState(p).isAir()) {
						world.setBlockState(p, air, flags);
					}
				}
			}
		}

		Optional<StructureTemplate> template = server.getStructureTemplateManager().getTemplate(STRUCTURE);
		if (template.isPresent()) {
			StructurePlacementData data = new StructurePlacementData().setUpdateNeighbors(true);
			template.get().place(world, origin, origin, data, world.getRandom(), Block.NOTIFY_ALL);
		} else {
			Diktatura.LOGGER.error("Структура {} не найдена, ставлю упрощённую тюрьму", STRUCTURE);
			buildFallback(world, origin);
		}

		// Площадка у входа и фундамент до земли.
		for (int x = -6; x < PrisonLayout.SIZE_X; x++) {
			for (int z = 0; z < PrisonLayout.SIZE_Z; z++) {
				if (x < 0 && (z < 7 || z > 13)) {
					continue;
				}
				if (x < 0) {
					world.setBlockState(origin.add(x, 0, z), Blocks.POLISHED_ANDESITE.getDefaultState(), flags);
				}
				for (int y = -1; y > -40; y--) {
					BlockPos p = origin.add(x, y, z);
					BlockState s = world.getBlockState(p);
					if (!s.isReplaceable() && s.getFluidState().isEmpty()) {
						break;
					}
					world.setBlockState(p, foundation, flags);
				}
			}
		}
	}

	private static void buildFallback(ServerWorld world, BlockPos origin) {
		BlockState wall = Blocks.STONE_BRICKS.getDefaultState();
		BlockState bars = Blocks.IRON_BARS.getDefaultState();
		for (int x = 0; x < PrisonLayout.SIZE_X; x++) {
			for (int z = 0; z < PrisonLayout.SIZE_Z; z++) {
				world.setBlockState(origin.add(x, 0, z), wall);
				world.setBlockState(origin.add(x, PrisonLayout.SIZE_Y - 2, z), wall);
				boolean edge = x == 0 || z == 0 || x == PrisonLayout.SIZE_X - 1 || z == PrisonLayout.SIZE_Z - 1;
				for (int y = 1; y < PrisonLayout.SIZE_Y - 2; y++) {
					if (edge) {
						world.setBlockState(origin.add(x, y, z), wall);
					}
				}
			}
		}
		for (int i = 0; i < PrisonLayout.CELLS.length; i++) {
			BlockPos c = PrisonLayout.cell(origin, i);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					for (int y = 0; y <= 3; y++) {
						if (Math.abs(dx) == 2 || Math.abs(dz) == 2 || y == 3) {
							BlockPos p = c.add(dx, y, dz);
							if (world.getBlockState(p).isAir()) {
								world.setBlockState(p, y == 3 ? wall : bars);
							}
						}
					}
				}
			}
		}
		world.setBlockState(origin.add(PrisonLayout.THRONE).down(), Blocks.GOLD_BLOCK.getDefaultState());
	}

	// ---------------------------------------------------------------- арест

	public static BlockPos origin(MinecraftServer server) {
		return DiktaturaState.get(server).prisonOrigin;
	}

	public static Vec3d arrivalPoint(MinecraftServer server) {
		return Vec3d.ofBottomCenter(origin(server).add(PrisonLayout.ARRIVAL));
	}

	/**
	 * Арестовать игрока: запомнить точку задержания и отправить повозку в зону отработки.
	 * Срок ({@code ticks}) начнёт идти только после прохождения мини-игры.
	 */
	public static void arrest(ServerPlayerEntity player, int ticks, Text reason) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ensureGenerated(server);
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(player);
		int seconds = Math.max(1, Math.round(ticks / 20f));

		if (rec.stage.isArrested()) {
			rec.sentenceTicks += ticks;
			state.markDirty();
			player.sendMessage(Text.translatable("diktatura.arrest.extended", seconds).formatted(Formatting.RED), false);
			return;
		}
		if (rec.stage == Stage.RETURNING) {
			TransportSystem.cancel(player);
		} else {
			rec.detentionDim = player.getWorld().getRegistryKey().getValue().toString();
			rec.detX = player.getX();
			rec.detY = player.getY();
			rec.detZ = player.getZ();
			rec.detYaw = player.getYaw();
		}
		rec.stage = Stage.TO_PRISON;
		rec.sentenceTicks = ticks;
		rec.arrests++;
		state.markDirty();

		server.getPlayerManager().broadcast(Text.translatable("diktatura.arrest.broadcast",
			player.getDisplayName(), reason, seconds).formatted(Formatting.DARK_RED), false);
		TransportSystem.send(player, World.OVERWORLD, MinigameSystem.prepare(player), MinigameSystem::begin);
	}

	/** Отработка пройдена: посадить в камеру, с этого момента идёт срок. */
	public static void admit(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(player);
		rec.stage = Stage.IMPRISONED;
		rec.cell = freeCell(server, state, player);
		if (rec.prevGameMode.isEmpty()) {
			rec.prevGameMode = player.interactionManager.getGameMode().getName();
		}
		player.changeGameMode(GameMode.ADVENTURE);
		putInCell(server, player, rec);
		state.markDirty();
		player.sendMessage(Text.translatable("diktatura.prison.admitted",
			formatTime(rec.sentenceTicks)).formatted(Formatting.RED), false);
	}

	private static int freeCell(MinecraftServer server, DiktaturaState state, ServerPlayerEntity player) {
		Set<Integer> taken = new HashSet<>();
		for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
			if (other != player) {
				PlayerRecord r = state.player(other);
				if (r.stage == Stage.IMPRISONED) {
					taken.add(r.cell);
				}
			}
		}
		for (int i = 0; i < PrisonLayout.CELLS.length; i++) {
			if (!taken.contains(i)) {
				return i;
			}
		}
		return player.getRandom().nextInt(PrisonLayout.CELLS.length);
	}

	private static void putInCell(MinecraftServer server, ServerPlayerEntity player, PlayerRecord rec) {
		BlockPos c = PrisonLayout.cell(origin(server), Math.max(0, rec.cell));
		Teleports.to(player, server.getOverworld(), c.getX() + 0.5, c.getY(), c.getZ() + 0.5, player.getYaw(), player.getPitch());
		player.fallDistance = 0;
	}

	private static boolean insideCell(MinecraftServer server, ServerPlayerEntity player, PlayerRecord rec) {
		if (player.getWorld().getRegistryKey() != World.OVERWORLD) {
			return false;
		}
		BlockPos c = PrisonLayout.cell(origin(server), Math.max(0, rec.cell));
		BlockPos p = player.getBlockPos();
		return Math.abs(p.getX() - c.getX()) <= 1 && Math.abs(p.getZ() - c.getZ()) <= 1
			&& p.getY() >= c.getY() - 1 && p.getY() <= c.getY() + 2;
	}

	// ---------------------------------------------------------------- освобождение

	/** Срок вышел: та же повозка везёт игрока обратно на точку задержания. */
	public static void release(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(player);
		rec.stage = Stage.RETURNING;
		rec.sentenceTicks = 0;
		rec.minigame = -1;
		rec.progress = 0;
		rec.pardoned = false;
		state.markDirty();
		player.sendMessage(Text.translatable("diktatura.prison.released").formatted(Formatting.GREEN), false);

		Vec3d start = arrivalPoint(server);
		Teleports.to(player, server.getOverworld(), start.x, start.y, start.z, player.getYaw(), player.getPitch());
		TransportSystem.send(player, detentionWorld(server, rec), detentionPoint(server, rec), PrisonSystem::finishReturn);
	}

	/** Освобождение по команде или амнистии: работает на любой стадии. */
	public static void forceRelease(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		PlayerRecord rec = DiktaturaState.get(server).player(player);
		switch (rec.stage) {
			case IMPRISONED, MINIGAME -> release(player);
			case TO_PRISON -> {
				TransportSystem.cancel(player);
				teleportHome(server, player, rec);
				finishReturn(player);
			}
			default -> {
			}
		}
	}

	public static void finishReturn(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(player);
		rec.stage = Stage.FREE;
		rec.cell = -1;
		rec.sentenceTicks = 0;
		GameMode mode = GameMode.byName(rec.prevGameMode, GameMode.SURVIVAL);
		player.changeGameMode(mode);
		rec.prevGameMode = "";
		MinigameSystem.restoreInventory(player, rec);
		state.markDirty();
		// Пять минут передышки от «постоянных» законов (богатство, взгляд и т. п.).
		LawEngine.grace(player.getUuid(), 20 * 60 * 5);
		player.sendMessage(Text.translatable("diktatura.prison.home").formatted(Formatting.GREEN), false);
	}

	private static RegistryKey<World> detentionWorld(MinecraftServer server, PlayerRecord rec) {
		Identifier id = Identifier.tryParse(rec.detentionDim);
		if (id == null) {
			return World.OVERWORLD;
		}
		RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
		return server.getWorld(key) != null ? key : World.OVERWORLD;
	}

	private static Vec3d detentionPoint(MinecraftServer server, PlayerRecord rec) {
		if (!rec.hasDetention()) {
			return Vec3d.ofBottomCenter(server.getOverworld().getSpawnPos());
		}
		return new Vec3d(rec.detX, rec.detY, rec.detZ);
	}

	private static void teleportHome(MinecraftServer server, ServerPlayerEntity player, PlayerRecord rec) {
		ServerWorld w = server.getWorld(detentionWorld(server, rec));
		Vec3d p = detentionPoint(server, rec);
		Teleports.to(player, w == null ? server.getOverworld() : w, p.x, p.y, p.z, rec.detYaw, player.getPitch());
		player.fallDistance = 0;
	}

	// ---------------------------------------------------------------- тик и вход

	public static void tick(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		boolean second = Diktatura.ticks() % 20 == 0;
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			PlayerRecord rec = state.player(p);
			if (rec.stage != Stage.IMPRISONED) {
				continue;
			}
			rec.sentenceTicks--;
			if (rec.sentenceTicks <= 0) {
				release(p);
				continue;
			}
			if (second) {
				if (!insideCell(server, p, rec) && !p.isDead()) {
					putInCell(server, p, rec);
				}
				if (p.interactionManager.getGameMode() != GameMode.ADVENTURE) {
					p.changeGameMode(GameMode.ADVENTURE);
				}
			}
			// Тюремная баланда раз в минуту, если заключённый голоден.
			if (rec.sentenceTicks % 1200 == 0 && p.getHungerManager().getFoodLevel() < 10) {
				p.getInventory().offerOrDrop(new ItemStack(Items.BREAD));
				p.sendMessage(Text.translatable("diktatura.prison.food").formatted(Formatting.GRAY), true);
			}
			if (second) {
				state.markDirty();
			}
		}
	}

	/** Восстановить стадию после перезапуска сервера или выхода во время поездки. */
	public static void onJoin(MinecraftServer server, ServerPlayerEntity player) {
		PlayerRecord rec = DiktaturaState.get(server).player(player);
		if (TransportSystem.isTraveling(player.getUuid())) {
			return;
		}
		switch (rec.stage) {
			case TO_PRISON -> {
				if (rec.minigame < 0) {
					MinigameSystem.prepare(player);
				}
				MinigameSystem.begin(player);
			}
			case MINIGAME -> MinigameSystem.resume(player);
			case RETURNING -> {
				teleportHome(server, player, rec);
				finishReturn(player);
			}
			case IMPRISONED -> {
				player.changeGameMode(GameMode.ADVENTURE);
				putInCell(server, player, rec);
			}
			default -> {
			}
		}
	}

	/** Амнистия: всех заключённых и отрабатывающих (и тех, кто офлайн) освобождают при следующем тике/входе. */
	public static int amnesty(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		int n = 0;
		for (PlayerRecord rec : state.players.values()) {
			if (rec.stage.isArrested()) {
				rec.sentenceTicks = Math.min(rec.sentenceTicks, 1);
				rec.pardoned = true;
				n++;
			}
		}
		state.markDirty();
		return n;
	}

	public static String formatTime(int ticks) {
		int s = Math.max(0, ticks / 20);
		return String.format("%d:%02d", s / 60, s % 60);
	}
}
