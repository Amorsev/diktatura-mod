package com.diktatura.prison;

import net.minecraft.util.math.BlockPos;

/**
 * Разметка тюрьмы относительно угла структуры data/diktatura/structure/prison.nbt.
 * Если меняете постройку — сохраните размер и эти точки (генератор: tools/gen_prison.py).
 *
 * <pre>
 *  z=0 ┌──────────────────────┐   север: 5 камер (z 1..3), решётки z=4
 *      │ ▣ ▣ ▣ ▣ ▣            │
 *  вход│  ════ ковёр ════ трон│   зал z 5..15, трон диктатора x=18 z=10
 *      │ ▣ ▣ ▣ ▣ ▣            │   юг: 5 камер (z 17..19), решётки z=16
 *  z=20└──────────────────────┘
 *     x=0                   x=20
 * </pre>
 */
public final class PrisonLayout {
	public static final int SIZE_X = 21;
	public static final int SIZE_Y = 9;
	public static final int SIZE_Z = 21;

	/** Центры камер (x, z); пол камер на y=1. */
	public static final int[][] CELLS = {
		{2, 2}, {6, 2}, {10, 2}, {14, 2}, {18, 2},
		{2, 18}, {6, 18}, {10, 18}, {14, 18}, {18, 18}
	};

	/** Где стоит диктатор (на золотом блоке трона). */
	public static final BlockPos THRONE = new BlockPos(18, 2, 10);
	/** Площадка перед входом: сюда прибывает и отсюда уезжает повозка. */
	public static final BlockPos ARRIVAL = new BlockPos(-4, 1, 10);

	private PrisonLayout() {
	}

	public static BlockPos cell(BlockPos origin, int index) {
		int[] c = CELLS[Math.floorMod(index, CELLS.length)];
		return origin.add(c[0], 1, c[1]);
	}
}
