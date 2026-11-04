package com.diktatura.minigame;

import com.diktatura.Diktatura;
import com.diktatura.prison.PrisonSystem;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import com.diktatura.util.Teleports;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

/**
 * Отработка перед сроком. Арестованного везут повозкой прямо в зону мини-игры
 * (паркур, каменоломня или лабиринт), его вещи на это время изымаются на хранение.
 * После прохождения он садится в камеру, и только тогда начинает идти срок.
 */
public final class MinigameSystem {
	public static final int QUARRY_GOAL = 64;

	private MinigameSystem() {
	}

	// ---------------------------------------------------------------- генерация

	public static BlockPos base(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		return MinigameZones.base(state.prisonOrigin, server.getOverworld());
	}

	public static void ensureGenerated(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (!state.prisonGenerated || state.minigamesGenerated) {
			return;
		}
		build(server);
	}

	public static void build(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		MinigameZones.build(server.getOverworld(), base(server));
		state.minigamesGenerated = true;
		state.markDirty();
		Diktatura.LOGGER.info("Зоны отработки построены над тюрьмой: {}", base(server).toShortString());
	}

	// ---------------------------------------------------------------- события

	public static void registerEvents() {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, blockState, be) -> {
			if (world.isClient || !(player instanceof ServerPlayerEntity sp) || sp.getServer() == null) {
				return true;
			}
			MinecraftServer server = sp.getServer();
			DiktaturaState state = DiktaturaState.get(server);
			PlayerRecord rec = state.player(sp);
			if (rec.stage == Stage.MINIGAME) {
				if (MinigameType.byOrdinal(rec.minigame) == MinigameType.QUARRY && blockState.isOf(Blocks.STONE)
					&& world.getRegistryKey() == World.OVERWORLD
					&& MinigameZones.QUARRY_STONE.contains(pos.subtract(base(server)))) {
					mined(sp, rec, (ServerWorld) world, pos);
				}
				// Камень «восстанавливается»: блок остаётся на месте, ничего не выпадает.
				return false;
			}
			// Зоны отработки защищены от остальных (кроме операторов в творческом режиме).
			if (world.getRegistryKey() == World.OVERWORLD && state.minigamesGenerated
				&& MinigameZones.inAnyZone(pos.subtract(base(server)))) {
				return sp.isCreative() && sp.hasPermissionLevel(2);
			}
			return true;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient || !(player instanceof ServerPlayerEntity sp) || sp.getServer() == null) {
				return ActionResult.PASS;
			}
			Stage stage = DiktaturaState.get(sp.getServer()).player(sp).stage;
			return stage == Stage.MINIGAME ? ActionResult.FAIL : ActionResult.PASS;
		});
	}

	private static void mined(ServerPlayerEntity p, PlayerRecord rec, ServerWorld world, BlockPos pos) {
		rec.progress++;
		world.spawnParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.01);
		world.playSound(null, pos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 0.8f, 1.0f);
		p.sendMessage(Text.translatable("diktatura.minigame.quarry.progress", rec.progress, QUARRY_GOAL)
			.formatted(Formatting.GRAY), true);
		DiktaturaState.get(p.getServer()).markDirty();
	}

	// ---------------------------------------------------------------- старт

	/** Выбрать мини-игру для арестованного и вернуть точку, куда его везти. */
	public static Vec3d prepare(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		DiktaturaState state = DiktaturaState.get(server);
		ensureGenerated(server);
		PlayerRecord rec = state.player(player);
		MinigameType type = MinigameType.values()[player.getRandom().nextInt(MinigameType.values().length)];
		rec.minigame = type.ordinal();
		rec.progress = 0;
		state.markDirty();
		return startPoint(server, type, 0);
	}

	private static Vec3d startPoint(MinecraftServer server, MinigameType type, int progress) {
		BlockPos rel = type == MinigameType.PARKOUR && progress >= 1 ? MinigameZones.PARKOUR_CHECKPOINT : MinigameZones.start(type);
		return Vec3d.ofBottomCenter(base(server).add(rel));
	}

	/** Повозка доставила игрока в зону: начать отработку. */
	public static void begin(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(player);
		MinigameType type = MinigameType.byOrdinal(rec.minigame);
		if (type == null) {
			type = MinigameType.PARKOUR;
			rec.minigame = type.ordinal();
		}
		rec.stage = Stage.MINIGAME;
		rec.pardoned = false;
		if (rec.prevGameMode.isEmpty()) {
			rec.prevGameMode = player.interactionManager.getGameMode().getName();
		}
		stashInventory(player, rec);
		giveKit(player, type);
		applyMode(player, type);
		Vec3d s = startPoint(server, type, rec.progress);
		Teleports.to(player, server.getOverworld(), s.x, s.y, s.z, -90.0f, 0.0f);
		state.markDirty();

		player.sendMessage(Text.translatable("diktatura.minigame.start",
			Text.translatable("diktatura.minigame." + type.key())).formatted(Formatting.GOLD, Formatting.BOLD), false);
		player.sendMessage(Text.translatable("diktatura.minigame." + type.key() + ".help", QUARRY_GOAL)
			.formatted(Formatting.YELLOW), false);
	}

	/** Игрок зашёл на сервер посреди отработки. */
	public static void resume(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		PlayerRecord rec = DiktaturaState.get(server).player(player);
		MinigameType type = MinigameType.byOrdinal(rec.minigame);
		if (type == null) {
			complete(player);
			return;
		}
		applyMode(player, type);
		Vec3d s = startPoint(server, type, rec.progress);
		Teleports.to(player, server.getOverworld(), s.x, s.y, s.z, -90.0f, 0.0f);
	}

	private static void applyMode(ServerPlayerEntity player, MinigameType type) {
		GameMode mode = type == MinigameType.QUARRY ? GameMode.SURVIVAL : GameMode.ADVENTURE;
		if (player.interactionManager.getGameMode() != mode) {
			player.changeGameMode(mode);
		}
	}

	private static void giveKit(ServerPlayerEntity player, MinigameType type) {
		if (type == MinigameType.QUARRY && !hasItem(player.getInventory(), Items.STONE_PICKAXE)) {
			player.getInventory().offerOrDrop(new ItemStack(Items.STONE_PICKAXE));
		}
	}

	private static boolean hasItem(PlayerInventory inv, net.minecraft.item.Item item) {
		for (int i = 0; i < inv.size(); i++) {
			if (inv.getStack(i).isOf(item)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- вещи на хранение

	/** Забрать вещи на хранение (если ещё не забраны) и очистить инвентарь. */
	private static void stashInventory(ServerPlayerEntity player, PlayerRecord rec) {
		PlayerInventory inv = player.getInventory();
		if (rec.stash == null) {
			rec.stash = inv.writeNbt(new NbtList());
		}
		inv.clear();
	}

	/** Вернуть вещи, сданные на хранение при аресте. */
	public static void restoreInventory(ServerPlayerEntity player, PlayerRecord rec) {
		if (rec.stash == null) {
			return;
		}
		PlayerInventory inv = player.getInventory();
		inv.clear();
		inv.readNbt(rec.stash);
		rec.stash = null;
		player.sendMessage(Text.translatable("diktatura.minigame.items_returned").formatted(Formatting.GREEN), false);
	}

	// ---------------------------------------------------------------- тик

	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % 5 != 0) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		BlockPos base = null;
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			PlayerRecord rec = state.player(p);
			if (rec.stage != Stage.MINIGAME) {
				continue;
			}
			if (base == null) {
				base = base(server);
			}
			MinigameType type = MinigameType.byOrdinal(rec.minigame);
			if (type == null || rec.pardoned) {
				complete(p);
				continue;
			}
			if (Diktatura.ticks() % 20 == 0) {
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 20 * 30, 0, false, false));
				p.getHungerManager().setFoodLevel(20);
				applyMode(p, type);
				giveKit(p, type);
			}
			BlockPos rel = p.getBlockPos().subtract(base);
			boolean inWorld = p.getWorld().getRegistryKey() == World.OVERWORLD;

			switch (type) {
				case PARKOUR -> {
					if (near(rel, MinigameZones.PARKOUR_FINISH, 2)) {
						complete(p);
						continue;
					}
					if (rec.progress < 1 && near(rel, MinigameZones.PARKOUR_CHECKPOINT, 1)) {
						rec.progress = 1;
						state.markDirty();
						p.sendMessage(Text.translatable("diktatura.minigame.parkour.checkpoint").formatted(Formatting.GREEN), true);
					}
					if (!inWorld || rel.getY() < MinigameZones.PARKOUR_MIN_Y - 3 || !MinigameZones.PARKOUR_BOX.contains(rel)) {
						p.sendMessage(Text.translatable("diktatura.minigame.parkour.fell").formatted(Formatting.RED), true);
						resetPosition(server, p, type, rec);
					}
				}
				case QUARRY -> {
					if (rec.progress >= QUARRY_GOAL) {
						complete(p);
						continue;
					}
					if (!inWorld || !MinigameZones.QUARRY_BOX.contains(rel)) {
						resetPosition(server, p, type, rec);
					}
				}
				case MAZE -> {
					if (rel.getX() == MinigameZones.MAZE_EXIT.getX() && rel.getZ() == MinigameZones.MAZE_EXIT.getZ()
						&& Math.abs(rel.getY() - MinigameZones.MAZE_EXIT.getY()) <= 1) {
						complete(p);
						continue;
					}
					if (!inWorld || !MinigameZones.MAZE_BOX.contains(rel)) {
						resetPosition(server, p, type, rec);
					}
				}
			}
		}
	}

	private static boolean near(BlockPos rel, BlockPos center, int r) {
		return Math.abs(rel.getX() - center.getX()) <= r && Math.abs(rel.getZ() - center.getZ()) <= r
			&& rel.getY() >= center.getY() - 1 && rel.getY() <= center.getY() + 2;
	}

	private static void resetPosition(MinecraftServer server, ServerPlayerEntity p, MinigameType type, PlayerRecord rec) {
		Vec3d s = startPoint(server, type, rec.progress);
		Teleports.to(p, server.getOverworld(), s.x, s.y, s.z, -90.0f, 0.0f);
	}

	/** Отработка пройдена: в камеру, и только теперь начинается срок. */
	public static void complete(ServerPlayerEntity p) {
		MinecraftServer server = p.getServer();
		if (server == null) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(p);
		boolean pardoned = rec.pardoned;
		rec.minigame = -1;
		rec.progress = 0;
		rec.pardoned = false;
		// Кирка и прочий инвентарь отработки не нужны.
		p.getInventory().clear();
		p.removeStatusEffect(StatusEffects.NIGHT_VISION);
		state.markDirty();
		if (!pardoned) {
			p.sendMessage(Text.translatable("diktatura.minigame.done").formatted(Formatting.GREEN, Formatting.BOLD), false);
			p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
				SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.0f);
		}
		PrisonSystem.admit(p);
	}
}
