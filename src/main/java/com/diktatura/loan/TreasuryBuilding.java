package com.diktatura.loan;

import com.diktatura.Diktatura;
import com.diktatura.dictator.GrandThroneHall;
import com.diktatura.dictator.ThroneHall;
import com.diktatura.state.DiktaturaState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

/**
 * Здание кассы микрозаймов «Казначейство» — отдельно от горы, у западного подножия (в мире без горы —
 * к западу от тронного зала). Банк в духе госучреждения: портик с четырьмя колоннами и фронтоном,
 * вывеска, зал ожидания с очередью и скамьями, стойка с решёткой и окном кассы, за ней — хранилище.
 *
 * <pre>
 *  z=0   ┌──── хранилище ────┐  северная стена
 *  z=1..3│ касса (казначей)  │  казначей x=6, z=2, лицом на юг
 *  z=4   ├─ стойка, окно x=5..7 ┤
 *  z=5..9│   зал ожидания     │
 *  z=10  └────┐ вход ┌────────┘  проём x=5..7
 *  z=11..13   портик, колонны x=1,4,8,11
 *  x=0                     x=12
 * </pre>
 */
public final class TreasuryBuilding {
	public static final int SIZE_X = 13;
	public static final int SIZE_Y = 8;
	public static final int SIZE_Z = 11;
	/** Портик и ступени перед входом (к югу от стены). */
	public static final int PORCH = 4;
	public static final int VERSION = 1;
	/** Пост казначея (от угла здания): за стойкой, напротив окна кассы. */
	public static final BlockPos POST = new BlockPos(6, 1, 2);
	public static final float POST_YAW = 0.0f;
	/** Куда ставить игрока командой «tp»: перед входом. */
	public static final BlockPos ENTRANCE = new BlockPos(6, 1, 15);

	private TreasuryBuilding() {
	}

	/** Центр столицы (тронного зала) — от него считается зона государственного кредита. */
	public static BlockPos capitalCenter(DiktaturaState state) {
		BlockPos h = state.throneOrigin;
		if (state.throneVersion >= 2) {
			return h.add(GrandThroneHall.SIZE_X / 2, 0, GrandThroneHall.SIZE_Z / 2);
		}
		return h.add(ThroneHall.SIZE_X / 2, 0, ThroneHall.SIZE_Z / 2);
	}

	/** Границы здания вместе с портиком и площадкой (для закона о госпостройках). */
	public static BlockBox box(BlockPos o) {
		return new BlockBox(o.getX() - 1, o.getY() - 1, o.getZ() - 1,
			o.getX() + SIZE_X, o.getY() + SIZE_Y + 3, o.getZ() + SIZE_Z + PORCH);
	}

	/** Построить, если ещё нет (нужен тронный зал: от него считается место). */
	public static void ensureGenerated(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (state.treasuryGenerated || !state.throneGenerated) {
			return;
		}
		ServerWorld world = server.getOverworld();
		BlockPos c = capitalCenter(state);
		// За подножием горы (плато 26 + склон 34 от хребта) с запасом; к северу от лестницы и тоннеля тюрьмы.
		int dist = state.capitalGenerated ? 74 : 30;
		int ox = c.getX() - dist - SIZE_X;
		int oz = state.capitalGenerated ? c.getZ() - 24 - SIZE_Z : c.getZ() - SIZE_Z / 2;
		for (int cx = (ox - 8) >> 4; cx <= (ox + SIZE_X + 8) >> 4; cx++) {
			for (int cz = (oz - 8) >> 4; cz <= (oz + SIZE_Z + PORCH + 8) >> 4; cz++) {
				world.getChunk(cx, cz);
			}
		}
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ox + SIZE_X / 2, oz + SIZE_Z / 2);
		int y = Math.max(top, world.getSeaLevel() + 1) - 1;
		BlockPos origin = new BlockPos(ox, y, oz);
		build(world, origin);
		if (state.capitalGenerated) {
			path(world, origin.add(6, 0, SIZE_Z + PORCH), c);
		}
		state.treasuryOrigin = origin;
		state.treasuryGenerated = true;
		state.treasuryVersion = VERSION;
		state.markDirty();
		Diktatura.LOGGER.info("Казначейство построено в {}", origin.toShortString());
	}

	public static void build(ServerWorld world, BlockPos o) {
		int flags = Block.NOTIFY_LISTENERS;
		int clear = flags | Block.FORCE_STATE | Block.SKIP_DROPS;
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState wall = Blocks.STONE_BRICKS.getDefaultState();
		BlockState plinth = Blocks.POLISHED_ANDESITE.getDefaultState();
		BlockState pillar = Blocks.QUARTZ_PILLAR.getDefaultState();
		BlockState quartz = Blocks.SMOOTH_QUARTZ.getDefaultState();
		BlockState roof = Blocks.SMOOTH_STONE.getDefaultState();
		BlockState counter = Blocks.DARK_OAK_PLANKS.getDefaultState();
		int depth = SIZE_Z + PORCH;

		// Расчистка (деревья, холмы) и фундамент под всем зданием и портиком.
		for (int x = -2; x <= SIZE_X + 1; x++) {
			for (int z = -2; z <= depth + 1; z++) {
				for (int y = 1; y < SIZE_Y + 10; y++) {
					BlockPos p = o.add(x, y, z);
					if (!world.getBlockState(p).isAir()) {
						world.setBlockState(p, air, clear);
					}
				}
			}
		}
		for (int x = -1; x <= SIZE_X; x++) {
			for (int z = -1; z <= depth; z++) {
				boolean inside = x >= 0 && x < SIZE_X && z >= 0 && z < depth;
				foundation(world, o.add(x, -1, z), Blocks.STONE_BRICKS.getDefaultState());
				world.setBlockState(o.add(x, 0, z), inside ? plinth : Blocks.GRASS_BLOCK.getDefaultState(), flags);
			}
		}

		// Пол зала: полированный диорит с андезитовой каймой.
		for (int x = 1; x < SIZE_X - 1; x++) {
			for (int z = 1; z < SIZE_Z - 1; z++) {
				boolean edge = x == 1 || x == SIZE_X - 2 || z == 1 || z == SIZE_Z - 2;
				world.setBlockState(o.add(x, 0, z), (edge ? Blocks.POLISHED_ANDESITE : Blocks.POLISHED_DIORITE).getDefaultState(), flags);
			}
		}

		// Стены: цоколь из андезита, кирпич, кварцевые пилястры, карниз; плоская крыша с парапетом.
		for (int x = 0; x < SIZE_X; x++) {
			for (int z = 0; z < SIZE_Z; z++) {
				boolean edgeX = x == 0 || x == SIZE_X - 1;
				boolean edgeZ = z == 0 || z == SIZE_Z - 1;
				world.setBlockState(o.add(x, SIZE_Y - 1, z), roof, flags);
				if (!edgeX && !edgeZ) {
					continue;
				}
				boolean column = (edgeX && edgeZ) || (edgeZ && (x == 4 || x == 8)) || (edgeX && z == 5);
				for (int y = 1; y < SIZE_Y - 1; y++) {
					BlockState s = column ? pillar : y == 1 ? plinth : y == SIZE_Y - 2 ? Blocks.POLISHED_ANDESITE.getDefaultState() : wall;
					world.setBlockState(o.add(x, y, z), s, flags);
				}
				world.setBlockState(o.add(x, SIZE_Y, z), Blocks.SMOOTH_STONE_SLAB.getDefaultState(), flags);
			}
		}
		// Окна: по бокам и на фасаде.
		for (int z : new int[] {2, 3, 7, 8}) {
			for (int y = 2; y <= 4; y++) {
				world.setBlockState(o.add(0, y, z), Blocks.GLASS_PANE.getDefaultState(), flags);
				world.setBlockState(o.add(SIZE_X - 1, y, z), Blocks.GLASS_PANE.getDefaultState(), flags);
			}
		}
		for (int x : new int[] {2, 10}) {
			for (int y = 2; y <= 4; y++) {
				world.setBlockState(o.add(x, y, SIZE_Z - 1), Blocks.GLASS_PANE.getDefaultState(), flags);
			}
		}
		// Вход: проём 3×3 посередине фасада.
		for (int x = 5; x <= 7; x++) {
			for (int y = 1; y <= 3; y++) {
				world.setBlockState(o.add(x, y, SIZE_Z - 1), air, clear);
			}
		}
		world.setBlockState(o.add(6, 4, SIZE_Z - 1), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), flags);

		// Хранилище в северной стене: тёмная бронедверь и табличка.
		for (int x = 5; x <= 7; x++) {
			for (int y = 1; y <= 3; y++) {
				world.setBlockState(o.add(x, y, 0), (x == 6 && y == 2 ? Blocks.CHISELED_POLISHED_BLACKSTONE
					: Blocks.POLISHED_BLACKSTONE_BRICKS).getDefaultState(), flags);
			}
		}
		sign(world, o.add(6, 4, 1), Direction.SOUTH, "vault", Formatting.DARK_RED);

		// Касса: бочки-ячейки и книжные шкафы у стены, конторка рядом с казначеем.
		for (int x : new int[] {1, 2, 3, 9, 10, 11}) {
			world.setBlockState(o.add(x, 1, 1), Blocks.BARREL.getDefaultState(), flags);
			world.setBlockState(o.add(x, 2, 1), (x == 1 || x == 11 ? Blocks.BOOKSHELF : Blocks.BARREL).getDefaultState(), flags);
		}
		world.setBlockState(o.add(4, 1, 2), Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, Direction.SOUTH), flags);

		// Стойка с решёткой во всю ширину; окно кассы x=5..7 на высоте лица.
		BlockState slabTop = Blocks.DARK_OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
		for (int x = 1; x < SIZE_X - 1; x++) {
			world.setBlockState(o.add(x, 1, 4), counter, flags);
			boolean window = x >= 5 && x <= 7;
			world.setBlockState(o.add(x, 2, 4), window ? air : Blocks.IRON_BARS.getDefaultState(), window ? clear : flags);
			world.setBlockState(o.add(x, 3, 4), slabTop, flags);
		}
		for (int x = 1; x < SIZE_X - 1; x++) {
			for (int y = 4; y <= 5; y++) {
				world.setBlockState(o.add(x, y, 4), Blocks.IRON_BARS.getDefaultState(), flags);
			}
		}
		world.setBlockState(o.add(6, 6, 4), Blocks.GREEN_GLAZED_TERRACOTTA.getDefaultState(), flags);

		// Зал ожидания: зелёная дорожка к окну, ограждения очереди, скамьи, плакаты с курсом.
		for (int z = 5; z <= SIZE_Z - 1; z++) {
			world.setBlockState(o.add(6, 1, z), Blocks.GREEN_CARPET.getDefaultState(), flags);
		}
		for (int z = 6; z <= 8; z++) {
			world.setBlockState(o.add(4, 1, z), Blocks.DARK_OAK_FENCE.getDefaultState(), flags);
			world.setBlockState(o.add(8, 1, z), Blocks.DARK_OAK_FENCE.getDefaultState(), flags);
		}
		for (int x : new int[] {1, 2, 10, 11}) {
			world.setBlockState(o.add(x, 1, 8), Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), flags);
		}
		sign(world, o.add(1, 2, 6), Direction.EAST, "rates", Formatting.DARK_GREEN);
		sign(world, o.add(SIZE_X - 2, 2, 6), Direction.WEST, "rules", Formatting.DARK_GREEN);

		// Люстры.
		BlockState lantern = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
		for (int x : new int[] {3, 6, 9}) {
			world.setBlockState(o.add(x, SIZE_Y - 2, 2), lantern, flags);
			world.setBlockState(o.add(x, SIZE_Y - 2, 7), lantern, flags);
		}

		// Портик: пол, четыре колонны, кровля и фронтон с эмблемой; ступени.
		int fz = SIZE_Z;
		for (int x = 0; x < SIZE_X; x++) {
			for (int z = fz; z < fz + 3; z++) {
				world.setBlockState(o.add(x, 0, z), Blocks.POLISHED_ANDESITE.getDefaultState(), flags);
				world.setBlockState(o.add(x, SIZE_Y - 2, z), quartz, flags);
			}
			world.setBlockState(o.add(x, 0, fz + 3), Blocks.STONE_BRICKS.getDefaultState(), flags);
		}
		for (int x : new int[] {1, 4, 8, 11}) {
			for (int y = 1; y < SIZE_Y - 2; y++) {
				world.setBlockState(o.add(x, y, fz + 2), pillar, flags);
			}
		}
		for (int i = 0; i < 3; i++) {
			for (int x = 2 + i * 2; x <= SIZE_X - 3 - i * 2; x++) {
				world.setBlockState(o.add(x, SIZE_Y - 1 + i, fz + 2), quartz, flags);
			}
		}
		world.setBlockState(o.add(6, SIZE_Y, fz + 2), Blocks.GREEN_GLAZED_TERRACOTTA.getDefaultState(), flags);
		for (int x : new int[] {2, 6, 10}) {
			world.setBlockState(o.add(x, SIZE_Y - 3, fz + 1), lantern, flags);
		}
		// Знамёна по сторонам от входа и главная вывеска над входом.
		for (int x : new int[] {3, 9}) {
			world.setBlockState(o.add(x, 4, fz), Blocks.GREEN_WALL_BANNER.getDefaultState().with(WallBannerBlock.FACING, Direction.SOUTH), flags);
		}
		sign(world, o.add(6, 5, fz), Direction.SOUTH, "main", Formatting.GOLD);
	}

	/** Табличка на стене: текст из переводов diktatura.treasury.sign.&lt;key&gt;.0..3. */
	private static void sign(ServerWorld world, BlockPos pos, Direction facing, String key, Formatting first) {
		world.setBlockState(pos, Blocks.DARK_OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, facing), Block.NOTIFY_LISTENERS);
		if (world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			String k = "diktatura.treasury.sign." + key + ".";
			sign.changeText(t -> t.withMessage(0, Text.translatable(k + "0").formatted(first))
				.withMessage(1, Text.translatable(k + "1"))
				.withMessage(2, Text.translatable(k + "2"))
				.withMessage(3, Text.translatable(k + "3")).withGlowing(true), true);
			sign.setWaxed(true);
		}
	}

	/** Дорожка от ступеней Казначейства к подножию лестницы на гору (у входа в тоннель тюрьмы). */
	private static void path(ServerWorld world, BlockPos from, BlockPos center) {
		int targetZ = center.getZ() - 6;
		int targetX = center.getX() - 66;
		int x = from.getX();
		int z = from.getZ();
		while (z < targetZ) {
			pave(world, x, z);
			pave(world, x + 1, z);
			z++;
		}
		while (x < targetX) {
			pave(world, x, z);
			pave(world, x, z + 1);
			x++;
		}
	}

	private static void pave(ServerWorld world, int x, int z) {
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos p = new BlockPos(x, top, z);
		BlockState s = world.getBlockState(p);
		if (!s.getFluidState().isEmpty() || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.LOGS)) {
			return;
		}
		world.setBlockState(p, Blocks.DIRT_PATH.getDefaultState(), Block.NOTIFY_LISTENERS);
		BlockPos above = p.up();
		if (!world.getBlockState(above).isAir() && world.getBlockState(above).isReplaceable()) {
			world.setBlockState(above, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
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
