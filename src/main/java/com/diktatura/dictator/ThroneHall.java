package com.diktatura.dictator;

import com.diktatura.Diktatura;
import com.diktatura.prison.PrisonLayout;
import com.diktatura.state.DiktaturaState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

/**
 * Тронный зал вождя — отдельное здание к северу от тюрьмы, вход с запада (как у тюрьмы).
 * Строится кодом один раз на мир. Вождь стоит на золотом блоке у восточной стены.
 *
 * <pre>
 *  z=0  ┌───────────────────┐
 *       │ ▤       ▤       ▤ │   окна в северной и южной стене
 * вход  ═══ ковёр ═══ ▲трон │   z=6, трон x=14
 *       │ ▤       ▤       ▤ │
 *  z=12 └───────────────────┘
 *      x=0               x=16
 * </pre>
 */
public final class ThroneHall {
	public static final int SIZE_X = 17;
	public static final int SIZE_Y = 9;
	public static final int SIZE_Z = 13;
	/** Где стоит вождь (над золотым блоком), относительно угла зала. */
	public static final BlockPos THRONE = new BlockPos(14, 2, 6);
	/** Расстояние между тюрьмой и залом по Z. */
	private static final int GAP = 6;

	private ThroneHall() {
	}

	/** Точка, где стоит вождь (в мире). */
	public static BlockPos throne(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		return state.throneOrigin.add(state.throneVersion >= 2 ? GrandThroneHall.THRONE : THRONE);
	}

	/** Перестроить зал той версии, что стоит в этом мире. */
	public static void rebuild(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (state.throneVersion >= 2) {
			GrandThroneHall.build(server.getOverworld(), state.throneOrigin);
		} else {
			build(server.getOverworld(), state.throneOrigin);
		}
	}

	/** Построить зал, если его ещё нет. Нужна уже построенная тюрьма. */
	public static void ensureGenerated(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (state.throneGenerated || !state.prisonGenerated) {
			return;
		}
		ServerWorld world = server.getOverworld();
		int ox = state.prisonOrigin.getX() + (PrisonLayout.SIZE_X - SIZE_X) / 2;
		int oz = state.prisonOrigin.getZ() - GAP - SIZE_Z;
		for (int cx = (ox - 8) >> 4; cx <= (ox + SIZE_X + 8) >> 4; cx++) {
			for (int cz = (oz - 8) >> 4; cz <= (oz + SIZE_Z + 8) >> 4; cz++) {
				world.getChunk(cx, cz);
			}
		}
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ox + SIZE_X / 2, oz + SIZE_Z / 2);
		int y = Math.max(top, world.getSeaLevel() + 1) - 1;
		// Не выше и не ниже тюрьмы больше чем на 4 блока — чтобы здания смотрелись вместе.
		y = Math.max(state.prisonOrigin.getY() - 4, Math.min(state.prisonOrigin.getY() + 4, y));
		BlockPos origin = new BlockPos(ox, y, oz);
		build(world, origin);
		state.throneOrigin = origin;
		state.throneGenerated = true;
		state.markDirty();
		Diktatura.LOGGER.info("Тронный зал построен в {}", origin.toShortString());
	}

	public static void build(ServerWorld world, BlockPos o) {
		int flags = Block.NOTIFY_LISTENERS;
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState wall = Blocks.STONE_BRICKS.getDefaultState();
		BlockState pillar = Blocks.CHISELED_STONE_BRICKS.getDefaultState();
		BlockState floor = Blocks.POLISHED_ANDESITE.getDefaultState();
		BlockState roof = Blocks.DARK_OAK_PLANKS.getDefaultState();
		BlockState trim = Blocks.GOLD_BLOCK.getDefaultState();
		BlockState carpet = Blocks.RED_CARPET.getDefaultState();
		BlockState glass = Blocks.GLASS_PANE.getDefaultState();
		BlockState dais = Blocks.POLISHED_BLACKSTONE.getDefaultState();

		// Расчистка: само здание, площадка перед входом и запас вокруг.
		for (int x = -5; x < SIZE_X + 1; x++) {
			for (int z = -1; z < SIZE_Z + 1; z++) {
				for (int y = 1; y < SIZE_Y + 10; y++) {
					BlockPos p = o.add(x, y, z);
					if (!world.getBlockState(p).isAir()) {
						world.setBlockState(p, air, flags | Block.FORCE_STATE | Block.SKIP_DROPS);
					}
				}
			}
		}

		for (int x = 0; x < SIZE_X; x++) {
			for (int z = 0; z < SIZE_Z; z++) {
				boolean edgeX = x == 0 || x == SIZE_X - 1;
				boolean edgeZ = z == 0 || z == SIZE_Z - 1;
				world.setBlockState(o.add(x, 0, z), edgeX || edgeZ ? wall : floor, flags);
				world.setBlockState(o.add(x, SIZE_Y - 1, z), roof, flags);
				foundation(world, o.add(x, -1, z), wall);
				if (!edgeX && !edgeZ) {
					continue;
				}
				boolean corner = edgeX && edgeZ;
				boolean column = corner || (edgeZ && x % 4 == 0) || (edgeX && z % 4 == 0);
				for (int y = 1; y < SIZE_Y - 1; y++) {
					BlockState s = column ? pillar : wall;
					if (y == SIZE_Y - 2) {
						s = trim;                                   // золотой пояс под крышей
					} else if (!column && edgeZ && (y == 3 || y == 4) && x % 2 == 0) {
						s = glass;                                  // окна
					}
					world.setBlockState(o.add(x, y, z), s, flags);
				}
			}
		}

		// Вход с запада: проём 3×3.
		for (int z = 5; z <= 7; z++) {
			for (int y = 1; y <= 3; y++) {
				world.setBlockState(o.add(0, y, z), air, flags | Block.FORCE_STATE | Block.SKIP_DROPS);
			}
		}

		// Помост и трон.
		for (int x = 12; x <= 15; x++) {
			for (int z = 4; z <= 8; z++) {
				world.setBlockState(o.add(x, 1, z), dais, flags);
			}
		}
		world.setBlockState(o.add(THRONE).down(), Blocks.GOLD_BLOCK.getDefaultState(), flags);
		// Спинка трона и подлокотники.
		world.setBlockState(o.add(15, 2, 6), trim, flags);
		world.setBlockState(o.add(15, 3, 6), trim, flags);
		world.setBlockState(o.add(14, 2, 5), stairs(Direction.SOUTH), flags);
		world.setBlockState(o.add(14, 2, 7), stairs(Direction.NORTH), flags);
		// Ступень к помосту.
		for (int z = 5; z <= 7; z++) {
			world.setBlockState(o.add(11, 1, z), Blocks.POLISHED_BLACKSTONE_STAIRS.getDefaultState()
				.with(StairsBlock.FACING, Direction.EAST).with(StairsBlock.HALF, BlockHalf.BOTTOM), flags);
		}

		// Ковровая дорожка от входа к помосту.
		for (int x = 1; x <= 10; x++) {
			for (int z = 5; z <= 7; z++) {
				world.setBlockState(o.add(x, 1, z), carpet, flags);
			}
		}

		// Знамёна вдоль стен и за троном.
		for (int x = 3; x <= 11; x += 4) {
			world.setBlockState(o.add(x, 4, 1), banner(Direction.SOUTH), flags);
			world.setBlockState(o.add(x, 4, SIZE_Z - 2), banner(Direction.NORTH), flags);
		}
		world.setBlockState(o.add(SIZE_X - 2, 4, 4), banner(Direction.WEST), flags);
		world.setBlockState(o.add(SIZE_X - 2, 4, 8), banner(Direction.WEST), flags);

		// Свет: фонари под крышей.
		BlockState lantern = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
		for (int x = 3; x < SIZE_X - 1; x += 4) {
			for (int z = 3; z < SIZE_Z - 1; z += 3) {
				world.setBlockState(o.add(x, SIZE_Y - 2, z), lantern, flags);
			}
		}

		// Площадка перед входом.
		for (int x = -4; x < 0; x++) {
			for (int z = 4; z <= 8; z++) {
				world.setBlockState(o.add(x, 0, z), Blocks.POLISHED_ANDESITE.getDefaultState(), flags);
				foundation(world, o.add(x, -1, z), wall);
			}
		}
		world.setBlockState(o.add(-1, 1, 3), Blocks.LANTERN.getDefaultState(), flags);
		world.setBlockState(o.add(-1, 1, 9), Blocks.LANTERN.getDefaultState(), flags);
	}

	private static BlockState stairs(Direction facing) {
		return Blocks.POLISHED_BLACKSTONE_STAIRS.getDefaultState()
			.with(StairsBlock.FACING, facing).with(StairsBlock.HALF, BlockHalf.TOP);
	}

	private static BlockState banner(Direction facing) {
		return Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, facing);
	}

	/** Фундамент вниз до твёрдой земли. */
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
