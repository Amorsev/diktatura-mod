package com.diktatura.casino;

import com.diktatura.Diktatura;
import com.diktatura.office.OfficeBuilding;
import com.diktatura.state.DiktaturaState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

/**
 * Госказино — к северу от дома госучреждений, вход с запада. Пол в шахматную клетку,
 * зелёный стол крупье у восточной стены, «игровые автоматы» вдоль стен, люстры.
 */
public final class CasinoBuilding {
	public static final int SIZE_X = 13;
	public static final int SIZE_Y = 7;
	public static final int SIZE_Z = 11;
	private static final int GAP = 6;
	/** Где стоит крупье (за столом). */
	public static final BlockPos CROUPIER = new BlockPos(9, 1, 5);

	private CasinoBuilding() {
	}

	public static void ensureGenerated(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (state.casinoGenerated || !state.officesGenerated) {
			return;
		}
		ServerWorld world = server.getOverworld();
		int ox = state.officesOrigin.getX() + (OfficeBuilding.SIZE_X - SIZE_X) / 2;
		int oz = state.officesOrigin.getZ() - GAP - SIZE_Z;
		for (int cx = (ox - 8) >> 4; cx <= (ox + SIZE_X + 8) >> 4; cx++) {
			for (int cz = (oz - 8) >> 4; cz <= (oz + SIZE_Z + 8) >> 4; cz++) {
				world.getChunk(cx, cz);
			}
		}
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ox + SIZE_X / 2, oz + SIZE_Z / 2);
		int y = Math.max(top, world.getSeaLevel() + 1) - 1;
		y = Math.max(state.officesOrigin.getY() - 4, Math.min(state.officesOrigin.getY() + 4, y));
		BlockPos origin = new BlockPos(ox, y, oz);
		build(world, origin);
		state.casinoOrigin = origin;
		state.casinoGenerated = true;
		state.markDirty();
		Diktatura.LOGGER.info("Госказино построено в {}", origin.toShortString());
	}

	public static void build(ServerWorld world, BlockPos o) {
		int flags = Block.NOTIFY_LISTENERS;
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
		BlockState pillar = Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState();
		BlockState gold = Blocks.GOLD_BLOCK.getDefaultState();
		BlockState roof = Blocks.DARK_OAK_PLANKS.getDefaultState();

		for (int x = -4; x < SIZE_X + 1; x++) {
			for (int z = -1; z < SIZE_Z + 1; z++) {
				for (int y = 1; y < SIZE_Y + 8; y++) {
					BlockPos p = o.add(x, y, z);
					if (!world.getBlockState(p).isAir()) {
						world.setBlockState(p, air, flags);
					}
				}
			}
		}
		for (int x = 0; x < SIZE_X; x++) {
			for (int z = 0; z < SIZE_Z; z++) {
				boolean edgeX = x == 0 || x == SIZE_X - 1;
				boolean edgeZ = z == 0 || z == SIZE_Z - 1;
				BlockState floor = ((x + z) % 2 == 0 ? Blocks.RED_CONCRETE : Blocks.BLACK_CONCRETE).getDefaultState();
				world.setBlockState(o.add(x, 0, z), edgeX || edgeZ ? wall : floor, flags);
				world.setBlockState(o.add(x, SIZE_Y - 1, z), roof, flags);
				foundation(world, o.add(x, -1, z), wall);
				if (!edgeX && !edgeZ) {
					continue;
				}
				boolean column = (edgeX && edgeZ) || (edgeZ && x % 4 == 0) || (edgeX && z % 5 == 0);
				for (int y = 1; y < SIZE_Y - 1; y++) {
					world.setBlockState(o.add(x, y, z), y == SIZE_Y - 2 ? gold : column ? pillar : wall, flags);
				}
			}
		}
		// Вход 3×3 с запада.
		for (int z = 4; z <= 6; z++) {
			for (int y = 1; y <= 3; y++) {
				world.setBlockState(o.add(0, y, z), air, flags);
			}
		}
		// Стол крупье: зелёное сукно на плитах.
		BlockState slab = Blocks.DARK_OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
		for (int z = 3; z <= 7; z++) {
			world.setBlockState(o.add(8, 1, z), slab, flags);
			world.setBlockState(o.add(8, 2, z), Blocks.GREEN_CARPET.getDefaultState(), flags);
		}
		// «Игровые автоматы» вдоль северной и южной стены: корпус, экран, рычаг-фонарь.
		for (int x = 2; x <= 6; x += 2) {
			for (int z : new int[] {1, SIZE_Z - 2}) {
				world.setBlockState(o.add(x, 1, z), Blocks.IRON_BLOCK.getDefaultState(), flags);
				world.setBlockState(o.add(x, 2, z), Blocks.REDSTONE_LAMP.getDefaultState(), flags);
				world.setBlockState(o.add(x, 3, z), Blocks.GOLD_BLOCK.getDefaultState(), flags);
			}
		}
		// Люстры.
		BlockState lantern = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
		for (int x = 3; x < SIZE_X - 1; x += 3) {
			for (int z = 3; z <= 7; z += 4) {
				world.setBlockState(o.add(x, SIZE_Y - 2, z), lantern, flags);
			}
		}
		// Красная дорожка от входа к столу.
		for (int x = 1; x <= 6; x++) {
			world.setBlockState(o.add(x, 1, 5), Blocks.RED_CARPET.getDefaultState(), flags);
		}
		// Площадка перед входом и вывеска.
		for (int x = -3; x < 0; x++) {
			for (int z = 3; z <= 7; z++) {
				world.setBlockState(o.add(x, 0, z), Blocks.POLISHED_ANDESITE.getDefaultState(), flags);
				foundation(world, o.add(x, -1, z), wall);
			}
		}
		BlockPos signPos = o.add(-1, 3, 7);
		world.setBlockState(signPos, Blocks.DARK_OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, Direction.WEST), flags);
		if (world.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
			sign.changeText(t -> t.withMessage(0, Text.translatable("diktatura.casino.sign.0").formatted(Formatting.GOLD))
				.withMessage(1, Text.translatable("diktatura.casino.sign.1"))
				.withMessage(2, Text.translatable("diktatura.casino.sign.2"))
				.withMessage(3, Text.translatable("diktatura.casino.sign.3")).withGlowing(true), true);
			sign.setWaxed(true);
		}
	}

	private static void foundation(ServerWorld world, BlockPos start, BlockState block) {
		for (int y = 0; y > -40; y--) {
			BlockPos p = start.add(0, y, 0);
			BlockState s = world.getBlockState(p);
			if (!s.isReplaceable() && s.getFluidState().isEmpty()) {
				break;
			}
			world.setBlockState(p, block, Block.NOTIFY_LISTENERS);
		}
	}
}
