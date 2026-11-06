package com.diktatura.office;

import com.diktatura.Diktatura;
import com.diktatura.dictator.ThroneHall;
import com.diktatura.state.DiktaturaState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

/**
 * Дом госучреждений: два кабинета под одной крышей, к северу от тронного зала, двери на запад.
 * У каждой двери — табличка с режимом приёма и цветной индикатор (зелёный — приём идёт).
 *
 * <pre>
 *  z=0  ┌───────┐
 *       │ биржа │  кабинет 0: z 1..6, дверь z=3
 *  z=7  ├───────┤
 *       │ отдел │  кабинет 1: z 8..13, дверь z=10
 *  z=14 └───────┘
 *      x=0     x=8   стол x=4, чиновник x=6 (за столом), полки x=7
 * </pre>
 */
public final class OfficeBuilding {
	public static final int SIZE_X = 9;
	public static final int SIZE_Y = 6;
	public static final int SIZE_Z = 15;
	private static final int GAP = 6;
	/** Центр кабинета по Z (дверь, стол, чиновник). */
	private static final int[] CENTER_Z = {3, 10};

	private OfficeBuilding() {
	}

	/** Где стоит чиновник кабинета (относительно угла здания). */
	public static BlockPos desk(int cabinet) {
		return new BlockPos(6, 1, CENTER_Z[cabinet]);
	}

	/** Индикатор приёма над дверью. */
	public static BlockPos lamp(int cabinet) {
		return new BlockPos(0, 3, CENTER_Z[cabinet]);
	}

	/** Табличка у двери (снаружи, справа от двери). */
	public static BlockPos sign(int cabinet) {
		return new BlockPos(-1, 2, CENTER_Z[cabinet] + 1);
	}

	/** Вход в кабинет снаружи (для телепорта). */
	public static BlockPos door(int cabinet) {
		return new BlockPos(-2, 1, CENTER_Z[cabinet]);
	}

	public static void ensureGenerated(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (state.officesGenerated || !state.throneGenerated) {
			return;
		}
		ServerWorld world = server.getOverworld();
		int ox = state.throneOrigin.getX() + (ThroneHall.SIZE_X - SIZE_X) / 2;
		int oz = state.throneOrigin.getZ() - GAP - SIZE_Z;
		for (int cx = (ox - 8) >> 4; cx <= (ox + SIZE_X + 8) >> 4; cx++) {
			for (int cz = (oz - 8) >> 4; cz <= (oz + SIZE_Z + 8) >> 4; cz++) {
				world.getChunk(cx, cz);
			}
		}
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ox + SIZE_X / 2, oz + SIZE_Z / 2);
		int y = Math.max(top, world.getSeaLevel() + 1) - 1;
		y = Math.max(state.throneOrigin.getY() - 4, Math.min(state.throneOrigin.getY() + 4, y));
		BlockPos origin = new BlockPos(ox, y, oz);
		build(world, origin);
		state.officesOrigin = origin;
		state.officesGenerated = true;
		state.markDirty();
		Diktatura.LOGGER.info("Дом госучреждений построен в {}", origin.toShortString());
	}

	public static void build(ServerWorld world, BlockPos o) {
		int flags = Block.NOTIFY_LISTENERS;
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState wall = Blocks.STONE_BRICKS.getDefaultState();
		BlockState pillar = Blocks.CHISELED_STONE_BRICKS.getDefaultState();
		BlockState plaster = Blocks.WHITE_TERRACOTTA.getDefaultState();
		BlockState floor = Blocks.OAK_PLANKS.getDefaultState();
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
				boolean edgeZ = z == 0 || z == SIZE_Z - 1 || z == 7;   // z=7 — стена между кабинетами
				world.setBlockState(o.add(x, 0, z), edgeX || edgeZ ? wall : floor, flags);
				world.setBlockState(o.add(x, SIZE_Y - 1, z), roof, flags);
				foundation(world, o.add(x, -1, z), wall);
				if (!edgeX && !edgeZ) {
					continue;
				}
				boolean corner = (edgeX && edgeZ) || (edgeX && z == 7);
				for (int y = 1; y < SIZE_Y - 1; y++) {
					BlockState s = corner ? pillar : y == 1 ? wall : plaster;
					// Окна в задней стене.
					if (x == SIZE_X - 1 && y == 2 && !edgeZ && z % 2 == 0) {
						s = Blocks.GLASS_PANE.getDefaultState();
					}
					world.setBlockState(o.add(x, y, z), s, flags);
				}
			}
		}

		for (int c = 0; c < 2; c++) {
			int cz = CENTER_Z[c];
			// Дверь.
			BlockState door = Blocks.SPRUCE_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.EAST)
				.with(DoorBlock.HINGE, DoorHinge.LEFT);
			world.setBlockState(o.add(0, 1, cz), door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER), flags);
			world.setBlockState(o.add(0, 2, cz), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER), flags);
			// Стол: три верхние плиты, на столе фонарь и бумаги (книжная полка-тумба).
			BlockState desk = Blocks.SPRUCE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
			for (int dz = -1; dz <= 1; dz++) {
				world.setBlockState(o.add(4, 1, cz + dz), desk, flags);
			}
			world.setBlockState(o.add(4, 2, cz - 1), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), flags);
			// Шкафы за спиной чиновника.
			for (int dz = -2; dz <= 2; dz++) {
				if (dz != 0) {
					world.setBlockState(o.add(7, 1, cz + dz), Blocks.BOOKSHELF.getDefaultState(), flags);
					world.setBlockState(o.add(7, 2, cz + dz), Blocks.BOOKSHELF.getDefaultState(), flags);
				}
			}
			// Стул для посетителя.
			world.setBlockState(o.add(2, 1, cz + 1), Blocks.SPRUCE_STAIRS.getDefaultState()
				.with(net.minecraft.block.StairsBlock.FACING, Direction.WEST), flags);
			// Свет под потолком.
			world.setBlockState(o.add(4, SIZE_Y - 2, cz), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), flags);
			// Табличка и индикатор снаружи.
			world.setBlockState(o.add(sign(c)), Blocks.SPRUCE_WALL_SIGN.getDefaultState()
				.with(WallSignBlock.FACING, Direction.WEST), flags);
			world.setBlockState(o.add(lamp(c)), Blocks.RED_CONCRETE.getDefaultState(), flags);
			// Ступенька-площадка перед дверью.
			for (int x = -3; x < 0; x++) {
				for (int dz = -1; dz <= 1; dz++) {
					world.setBlockState(o.add(x, 0, cz + dz), Blocks.POLISHED_ANDESITE.getDefaultState(), flags);
					foundation(world, o.add(x, -1, cz + dz), wall);
				}
			}
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
