package com.diktatura.dictator;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Большой тронный зал (с 0.2.1) — на вершине горы-столицы. Кварцевые стены на каменном
 * цоколе, золотой пояс, высокие витражи, двускатная крыша, четыре угловые башни с зубцами
 * и флагами, портик с колоннами у входа (запад). Внутри — колоннада, люстры, помост с троном.
 *
 * <pre>
 *        башня ┌───────────────────────────┐ башня
 *              │ ▥  ▥  ▥  ▥  ▥  ▥   помост │
 *   портик  ═══ ковровая дорожка ══ ▲трон   │   z=10, трон x=23
 *              │ ▥  ▥  ▥  ▥  ▥  ▥          │
 *        башня └───────────────────────────┘ башня
 *             x=0                        x=26
 * </pre>
 */
public final class GrandThroneHall {
	public static final int SIZE_X = 27;
	public static final int SIZE_Z = 21;
	/** Высота стен (y 1..WALL), над ними — потолок и крыша. */
	public static final int WALL = 10;
	/** Полная высота с башнями (для защиты и расчистки). */
	public static final int SIZE_Y = 26;
	/** Где стоит вождь: на золотом блоке посреди помоста у восточной стены. */
	public static final BlockPos THRONE = new BlockPos(23, 2, 10);
	/** Центр входа (портик с запада). */
	public static final int DOOR_Z = 10;

	private GrandThroneHall() {
	}

	public static void build(ServerWorld world, BlockPos o) {
		int flags = Block.NOTIFY_LISTENERS;
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState base = Blocks.STONE_BRICKS.getDefaultState();
		BlockState wall = Blocks.SMOOTH_QUARTZ.getDefaultState();
		BlockState pillar = Blocks.QUARTZ_PILLAR.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
		BlockState gold = Blocks.GOLD_BLOCK.getDefaultState();
		BlockState floor = Blocks.POLISHED_BLACKSTONE.getDefaultState();
		BlockState planks = Blocks.DARK_OAK_PLANKS.getDefaultState();

		// Расчистка над залом и вокруг (башни выступают на 2 блока, портик — на 4).
		for (int x = -6; x < SIZE_X + 3; x++) {
			for (int z = -3; z < SIZE_Z + 3; z++) {
				for (int y = 1; y < SIZE_Y + 2; y++) {
					BlockPos p = o.add(x, y, z);
					if (!world.getBlockState(p).isAir()) {
						world.setBlockState(p, air, flags);
					}
				}
			}
		}

		// Пол, стены, потолок.
		for (int x = 0; x < SIZE_X; x++) {
			for (int z = 0; z < SIZE_Z; z++) {
				boolean edgeX = x == 0 || x == SIZE_X - 1;
				boolean edgeZ = z == 0 || z == SIZE_Z - 1;
				world.setBlockState(o.add(x, 0, z), edgeX || edgeZ ? base : floor, flags);
				world.setBlockState(o.add(x, WALL + 1, z), planks, flags);
				if (!edgeX && !edgeZ) {
					continue;
				}
				boolean column = (edgeZ && x % 4 == 1) || (edgeX && z % 4 == 2);
				for (int y = 1; y <= WALL; y++) {
					BlockState s = y <= 2 ? base : column ? pillar : wall;
					if (y == WALL) {
						s = gold;
					}
					// Высокие витражи в северной и южной стенах (между колоннами).
					if (edgeZ && !column && x % 4 == 3 && y >= 3 && y <= 8) {
						s = (y % 2 == 0 ? Blocks.YELLOW_STAINED_GLASS_PANE : Blocks.RED_STAINED_GLASS_PANE).getDefaultState();
					}
					world.setBlockState(o.add(x, y, z), s, flags);
				}
			}
		}
		// Шахматный узор пола у трона и красная ковровая дорожка.
		for (int x = 1; x <= 18; x++) {
			for (int z = DOOR_Z - 1; z <= DOOR_Z + 1; z++) {
				world.setBlockState(o.add(x, 1, z), Blocks.RED_CARPET.getDefaultState(), flags);
			}
		}

		// Двускатная крыша: конёк вдоль X, скаты к северу и югу; фронтоны — кварц.
		int half = SIZE_Z / 2;
		for (int k = 0; k <= half; k++) {
			int y = WALL + 2 + k;
			for (int x = -1; x <= SIZE_X; x++) {
				boolean gable = x == 0 || x == SIZE_X - 1;
				for (int z = k; z <= SIZE_Z - 1 - k; z++) {
					boolean north = z == k;
					boolean south = z == SIZE_Z - 1 - k;
					BlockState s;
					if (gable && !(north || south)) {
						s = (k % 3 == 2 && z == half) ? Blocks.YELLOW_STAINED_GLASS.getDefaultState() : wall;
					} else if (north && south) {
						s = Blocks.DARK_OAK_SLAB.getDefaultState();
					} else if (north) {
						s = Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH);
					} else if (south) {
						s = Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH);
					} else if (x == -1 || x == SIZE_X) {
						continue;
					} else {
						s = planks;
					}
					if ((x == -1 || x == SIZE_X) && !(north || south)) {
						continue;
					}
					world.setBlockState(o.add(x, y, z), s, flags);
				}
			}
		}
		// Золотой шпиль на коньке посередине.
		for (int y = 0; y < 3; y++) {
			world.setBlockState(o.add(SIZE_X / 2, WALL + 3 + half + y, half), y == 2 ? gold : Blocks.GOLD_BLOCK.getDefaultState(), flags);
		}

		// Угловые башни 5×5 с зубцами и флагами.
		int[][] corners = {{-2, -2}, {SIZE_X - 3, -2}, {-2, SIZE_Z - 3}, {SIZE_X - 3, SIZE_Z - 3}};
		int towerTop = WALL + 9;
		for (int[] c : corners) {
			for (int dx = 0; dx < 5; dx++) {
				for (int dz = 0; dz < 5; dz++) {
					boolean edge = dx == 0 || dz == 0 || dx == 4 || dz == 4;
					foundation(world, o.add(c[0] + dx, -1, c[1] + dz), base);
					for (int y = 0; y <= towerTop; y++) {
						BlockState s;
						if (y == 0 || y == towerTop - 1) {
							s = base;
						} else if (!edge) {
							s = y == WALL ? gold : air;
						} else if (y <= 2) {
							s = base;
						} else if ((dx == 2 || dz == 2) && y % 4 == 1 && y > 3) {
							s = Blocks.GLASS_PANE.getDefaultState();
						} else {
							s = y == WALL ? gold : wall;
						}
						if (y == towerTop) {
							// Зубцы через один.
							s = edge && (dx + dz) % 2 == 0 ? Blocks.STONE_BRICK_WALL.getDefaultState() : air;
						}
						world.setBlockState(o.add(c[0] + dx, y, c[1] + dz), s, flags);
					}
				}
			}
			// Флагшток с красным знаменем.
			BlockPos pole = o.add(c[0] + 2, towerTop, c[1] + 2);
			for (int y = 0; y < 4; y++) {
				world.setBlockState(pole.up(y), Blocks.DARK_OAK_FENCE.getDefaultState(), flags);
			}
			world.setBlockState(pole.up(4), gold, flags);
			// Флаг из шерсти: красное полотнище с золотой полосой.
			world.setBlockState(pole.up(3).west(), Blocks.RED_WOOL.getDefaultState(), flags);
			world.setBlockState(pole.up(3).west(2), Blocks.RED_WOOL.getDefaultState(), flags);
			world.setBlockState(pole.up(2).west(), Blocks.YELLOW_WOOL.getDefaultState(), flags);
			world.setBlockState(pole.up(2).west(2), Blocks.RED_WOOL.getDefaultState(), flags);
		}

		// Вход: проём 5×6 и портик с колоннами и ступенями.
		for (int z = DOOR_Z - 2; z <= DOOR_Z + 2; z++) {
			for (int y = 1; y <= 6; y++) {
				world.setBlockState(o.add(0, y, z), air, flags);
			}
			world.setBlockState(o.add(0, 7, z), gold, flags);
		}
		for (int x = -4; x <= -1; x++) {
			for (int z = DOOR_Z - 4; z <= DOOR_Z + 4; z++) {
				world.setBlockState(o.add(x, 0, z), Blocks.POLISHED_DIORITE.getDefaultState(), flags);
				foundation(world, o.add(x, -1, z), base);
				world.setBlockState(o.add(x, 9, z), x == -4 ? Blocks.QUARTZ_STAIRS.getDefaultState()
					.with(StairsBlock.FACING, Direction.EAST).with(StairsBlock.HALF, BlockHalf.TOP) : wall, flags);
			}
		}
		for (int z : new int[] {DOOR_Z - 4, DOOR_Z - 2, DOOR_Z + 2, DOOR_Z + 4}) {
			for (int y = 1; y <= 8; y++) {
				world.setBlockState(o.add(-3, y, z), pillar, flags);
			}
		}
		for (int z = DOOR_Z - 4; z <= DOOR_Z + 4; z++) {
			world.setBlockState(o.add(-4, 10, z), gold, flags);
		}
		// Знамёна на фасаде по сторонам портика.
		for (int z : new int[] {DOOR_Z - 7, DOOR_Z + 7}) {
			world.setBlockState(o.add(-1, 7, z), Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.WEST), flags);
		}

		// Колоннада внутри.
		for (int x = 4; x <= 18; x += 4) {
			for (int z : new int[] {4, SIZE_Z - 5}) {
				for (int y = 1; y <= WALL; y++) {
					world.setBlockState(o.add(x, y, z), y == WALL ? gold : pillar, flags);
				}
			}
		}
		// Люстры на цепях.
		for (int x = 6; x <= 18; x += 6) {
			for (int z : new int[] {7, 13}) {
				world.setBlockState(o.add(x, WALL, z), Blocks.CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y), flags);
				world.setBlockState(o.add(x, WALL - 1, z), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), flags);
			}
		}
		// Знамёна вдоль стен.
		for (int x = 3; x <= 19; x += 4) {
			world.setBlockState(o.add(x, 6, 1), Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.SOUTH), flags);
			world.setBlockState(o.add(x, 6, SIZE_Z - 2), Blocks.RED_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.NORTH), flags);
		}

		// Помост и трон у восточной стены.
		for (int x = 20; x <= 25; x++) {
			for (int z = 5; z <= 15; z++) {
				world.setBlockState(o.add(x, 1, z), Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), flags);
			}
		}
		for (int z = 7; z <= 13; z++) {
			world.setBlockState(o.add(19, 1, z), Blocks.POLISHED_BLACKSTONE_STAIRS.getDefaultState()
				.with(StairsBlock.FACING, Direction.EAST), flags);
		}
		for (int x = 20; x <= 22; x++) {
			for (int z = DOOR_Z - 1; z <= DOOR_Z + 1; z++) {
				world.setBlockState(o.add(x, 2, z), Blocks.RED_CARPET.getDefaultState(), flags);
			}
		}
		world.setBlockState(o.add(THRONE).down(), gold, flags);
		for (int y = 2; y <= 6; y++) {
			world.setBlockState(o.add(THRONE.getX() + 1, y, THRONE.getZ()), gold, flags);
		}
		world.setBlockState(o.add(THRONE.getX(), 6, THRONE.getZ()), Blocks.RED_WALL_BANNER.getDefaultState()
			.with(WallBannerBlock.FACING, Direction.WEST), flags);
		world.setBlockState(o.add(THRONE.getX(), 2, THRONE.getZ() - 1), Blocks.QUARTZ_STAIRS.getDefaultState()
			.with(StairsBlock.FACING, Direction.SOUTH).with(StairsBlock.HALF, BlockHalf.TOP), flags);
		world.setBlockState(o.add(THRONE.getX(), 2, THRONE.getZ() + 1), Blocks.QUARTZ_STAIRS.getDefaultState()
			.with(StairsBlock.FACING, Direction.NORTH).with(StairsBlock.HALF, BlockHalf.TOP), flags);
		for (int z : new int[] {6, 14}) {
			world.setBlockState(o.add(24, 2, z), Blocks.GOLD_BLOCK.getDefaultState(), flags);
			world.setBlockState(o.add(24, 3, z), Blocks.LANTERN.getDefaultState(), flags);
		}

		// Фундамент до земли.
		for (int x = 0; x < SIZE_X; x++) {
			for (int z = 0; z < SIZE_Z; z++) {
				foundation(world, o.add(x, -1, z), base);
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
