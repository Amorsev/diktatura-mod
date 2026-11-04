package com.diktatura.minigame;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Геометрия зон отработки. Все три зоны висят в небе над тюрьмой, чтобы не зависеть
 * от рельефа: база = угол тюрьмы, поднятый на {@link #HEIGHT_ABOVE_PRISON} блоков.
 * Все координаты ниже — относительно базы.
 */
public final class MinigameZones {
	public static final int HEIGHT_ABOVE_PRISON = 60;

	// ---- паркур: вдоль +X, z около 2
	/** Шаги (dx, dy, dz) от одного блока к следующему: первая половина до контрольной точки, вторая до финиша. */
	private static final int[][] PARKOUR_LEG_1 = {
		{3, 0, 0}, {3, 0, 1}, {2, 1, 0}, {3, 0, -1}, {2, 1, 0},
		{3, 0, 0}, {2, -1, 1}, {3, 0, 0}, {2, 1, -1}, {3, 0, 0}
	};
	private static final int[][] PARKOUR_LEG_2 = {
		{3, 0, 0}, {2, 1, 1}, {3, 0, 0}, {2, 1, -1}, {3, 0, 0},
		{3, -1, 0}, {2, 1, 0}, {3, 0, 1}, {2, 1, 0}, {3, 0, -1}
	};
	public static final BlockPos PARKOUR_START = new BlockPos(2, 1, 2);
	public static final BlockPos PARKOUR_CHECKPOINT;
	/** Центр финишной площадки 5x5. */
	public static final BlockPos PARKOUR_FINISH;
	public static final List<BlockPos> PARKOUR_BLOCKS = new ArrayList<>();
	public static final int PARKOUR_MIN_Y;
	public static final BlockBox PARKOUR_BOX;

	// ---- каменоломня: 13x13, z от 30
	public static final int QUARRY_Z = 30;
	public static final int QUARRY_SIZE = 13;
	public static final BlockPos QUARRY_START = new BlockPos(6, 1, QUARRY_Z + 8);
	/** Где камень засчитывается: внутренний пол и «забой» у северной стены. */
	public static final BlockBox QUARRY_STONE = new BlockBox(1, 0, QUARRY_Z + 1, QUARRY_SIZE - 2, 2, QUARRY_Z + QUARRY_SIZE - 2);
	public static final BlockBox QUARRY_BOX = new BlockBox(0, -1, QUARRY_Z, QUARRY_SIZE - 1, 6, QUARRY_Z + QUARRY_SIZE - 1);

	// ---- лабиринт: 15x15, z от 52
	public static final int MAZE_Z = 52;
	public static final int MAZE_SIZE = 15;
	public static final BlockPos MAZE_START = new BlockPos(1, 1, MAZE_Z + 1);
	public static final BlockPos MAZE_EXIT = new BlockPos(MAZE_SIZE - 2, 1, MAZE_Z + MAZE_SIZE - 2);
	public static final BlockBox MAZE_BOX = new BlockBox(0, -1, MAZE_Z, MAZE_SIZE - 1, 6, MAZE_Z + MAZE_SIZE - 1);

	static {
		int x = 4;
		int y = 0;
		int z = 2;
		int minY = 0;
		for (int[] s : PARKOUR_LEG_1) {
			x += s[0];
			y += s[1];
			z += s[2];
			minY = Math.min(minY, y);
			PARKOUR_BLOCKS.add(new BlockPos(x, y, z));
		}
		x += 3;
		PARKOUR_CHECKPOINT = new BlockPos(x, y + 1, z);
		for (int[] s : PARKOUR_LEG_2) {
			x += s[0] + (s == PARKOUR_LEG_2[0] ? 1 : 0);
			y += s[1];
			z += s[2];
			minY = Math.min(minY, y);
			PARKOUR_BLOCKS.add(new BlockPos(x, y, z));
		}
		x += 4;
		PARKOUR_FINISH = new BlockPos(x, y + 1, z);
		PARKOUR_MIN_Y = minY;
		PARKOUR_BOX = new BlockBox(-2, minY - 4, -6, x + 4, y + 8, 10);
	}

	private MinigameZones() {
	}

	public static BlockPos base(BlockPos prisonOrigin, ServerWorld world) {
		int y = Math.min(prisonOrigin.getY() + HEIGHT_ABOVE_PRISON, world.getTopY() - 24);
		return new BlockPos(prisonOrigin.getX(), y, prisonOrigin.getZ());
	}

	public static BlockPos start(MinigameType type) {
		return switch (type) {
			case PARKOUR -> PARKOUR_START;
			case QUARRY -> QUARRY_START;
			case MAZE -> MAZE_START;
		};
	}

	public static BlockBox box(MinigameType type) {
		return switch (type) {
			case PARKOUR -> PARKOUR_BOX;
			case QUARRY -> QUARRY_BOX;
			case MAZE -> MAZE_BOX;
		};
	}

	public static boolean inAnyZone(BlockPos rel) {
		return PARKOUR_BOX.contains(rel) || QUARRY_BOX.contains(rel) || MAZE_BOX.contains(rel);
	}

	// ---------------------------------------------------------------- постройка

	public static void build(ServerWorld world, BlockPos base) {
		for (int cx = (base.getX() - 8) >> 4; cx <= (base.getX() + 80) >> 4; cx++) {
			for (int cz = (base.getZ() - 8) >> 4; cz <= (base.getZ() + MAZE_Z + MAZE_SIZE + 8) >> 4; cz++) {
				world.getChunk(cx, cz);
			}
		}
		buildParkour(world, base);
		buildQuarry(world, base);
		buildMaze(world, base);
	}

	private static void set(ServerWorld w, BlockPos base, int x, int y, int z, BlockState s) {
		w.setBlockState(base.add(x, y, z), s, Block.NOTIFY_LISTENERS);
	}

	private static void platform(ServerWorld w, BlockPos base, BlockPos center, int radius, int y, BlockState s) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				set(w, base, center.getX() + dx, y, center.getZ() + dz, s);
			}
		}
	}

	private static void buildParkour(ServerWorld w, BlockPos base) {
		platform(w, base, new BlockPos(2, 0, 2), 2, 0, Blocks.POLISHED_ANDESITE.getDefaultState());
		BlockState[] colors = {
			Blocks.RED_CONCRETE.getDefaultState(), Blocks.ORANGE_CONCRETE.getDefaultState(),
			Blocks.YELLOW_CONCRETE.getDefaultState(), Blocks.WHITE_CONCRETE.getDefaultState()
		};
		for (int i = 0; i < PARKOUR_BLOCKS.size(); i++) {
			BlockPos p = PARKOUR_BLOCKS.get(i);
			set(w, base, p.getX(), p.getY(), p.getZ(), colors[i % colors.length]);
		}
		BlockPos cp = PARKOUR_CHECKPOINT;
		platform(w, base, cp, 1, cp.getY() - 1, Blocks.LIME_CONCRETE.getDefaultState());
		BlockPos f = PARKOUR_FINISH;
		platform(w, base, f, 2, f.getY() - 1, Blocks.GOLD_BLOCK.getDefaultState());
	}

	private static void buildQuarry(ServerWorld w, BlockPos base) {
		BlockState wall = Blocks.STONE_BRICKS.getDefaultState();
		BlockState stone = Blocks.STONE.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		int z0 = QUARRY_Z;
		int n = QUARRY_SIZE;
		for (int x = 0; x < n; x++) {
			for (int z = z0; z < z0 + n; z++) {
				boolean edge = x == 0 || x == n - 1 || z == z0 || z == z0 + n - 1;
				set(w, base, x, -1, z, wall);
				set(w, base, x, 0, z, edge ? wall : stone);
				for (int y = 1; y <= 5; y++) {
					set(w, base, x, y, z, edge && y <= 4 ? wall : air);
				}
			}
		}
		// забой: две ступени камня у северной стены
		for (int x = 1; x < n - 1; x++) {
			for (int y = 1; y <= 2; y++) {
				set(w, base, x, y, z0 + 1, stone);
				if (y == 1) {
					set(w, base, x, y, z0 + 2, stone);
				}
			}
		}
		for (int x : new int[]{3, 9}) {
			set(w, base, x, 4, z0 + n - 1, Blocks.LANTERN.getDefaultState());
		}
	}

	private static void buildMaze(ServerWorld w, BlockPos base) {
		int n = MAZE_SIZE;
		int z0 = MAZE_Z;
		boolean[][] open = new boolean[n][n];
		// Лабиринт всегда один и тот же: детерминированный «рекурсивный обход».
		Random rnd = new Random(1984L);
		Deque<int[]> stack = new ArrayDeque<>();
		open[1][1] = true;
		stack.push(new int[]{1, 1});
		int[][] dirs = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
		while (!stack.isEmpty()) {
			int[] c = stack.peek();
			List<int[]> next = new ArrayList<>();
			for (int[] d : dirs) {
				int nx = c[0] + d[0];
				int nz = c[1] + d[1];
				if (nx > 0 && nz > 0 && nx < n - 1 && nz < n - 1 && !open[nx][nz]) {
					next.add(new int[]{nx, nz, d[0] / 2, d[1] / 2});
				}
			}
			if (next.isEmpty()) {
				stack.pop();
				continue;
			}
			Collections.shuffle(next, rnd);
			int[] t = next.get(0);
			open[c[0] + t[2]][c[1] + t[3]] = true;
			open[t[0]][t[1]] = true;
			stack.push(new int[]{t[0], t[1]});
		}
		BlockState wall = Blocks.STONE_BRICKS.getDefaultState();
		BlockState mossy = Blocks.MOSSY_STONE_BRICKS.getDefaultState();
		BlockState floor = Blocks.POLISHED_ANDESITE.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		for (int x = 0; x < n; x++) {
			for (int z = 0; z < n; z++) {
				set(w, base, x, -1, z0 + z, wall);
				set(w, base, x, 0, z0 + z, floor);
				for (int y = 1; y <= 4; y++) {
					BlockState s = open[x][z] || y == 4 ? air : ((x * 3 + z + y) % 7 == 0 ? mossy : wall);
					set(w, base, x, y, z0 + z, s);
				}
			}
		}
		set(w, base, MAZE_EXIT.getX(), 0, MAZE_EXIT.getZ(), Blocks.GOLD_BLOCK.getDefaultState());
		set(w, base, MAZE_START.getX(), 0, MAZE_START.getZ(), Blocks.LIME_CONCRETE.getDefaultState());
	}
}
