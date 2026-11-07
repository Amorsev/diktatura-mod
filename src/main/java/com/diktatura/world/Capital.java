package com.diktatura.world;

import com.diktatura.Diktatura;
import com.diktatura.casino.CasinoBuilding;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.dictator.GrandThroneHall;
import com.diktatura.office.OfficeBuilding;
import com.diktatura.prison.PrisonLayout;
import com.diktatura.prison.PrisonSystem;
import com.diktatura.state.DiktaturaState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * Столица (с 0.2.1): при создании мира у спавна вырастает гора. Внутри горы — тюрьма
 * (к её воротам с западного подножия ведёт тоннель), наверху ровное плато: посередине
 * большой тронный зал, к северу — дом госучреждений, к югу — госказино. На вершину
 * поднимается лестница по западному склону.
 *
 * <p>Форма горы — «стадион»: плато — все точки в {@link #PLATEAU} блоках от отрезка
 * север–юг через центр тюрьмы, дальше пологий склон шириной {@link #SLOPE} с шумом.
 */
public final class Capital {
	/** Высота плато над уровнем земли у тюрьмы. */
	public static final int HEIGHT = 26;
	/** Полудлина центрального отрезка (север–юг). */
	private static final int SPINE = 14;
	/** Радиус плато вокруг отрезка. */
	private static final int PLATEAU = 26;
	/** Ширина склона. */
	private static final int SLOPE = 34;
	/** Тоннель: z относительно угла тюрьмы и высота прохода. */
	private static final int TUNNEL_Z = 10;
	/** Лестница на вершину: смещение по z от центра горы. */
	private static final int STAIRS_DZ = 6;

	private Capital() {
	}

	/** Сгенерировать столицу в новом мире (если тюрьмы ещё нет и это не выключено в настройках). */
	public static void ensureGenerated(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (state.prisonGenerated || state.capitalGenerated || !DiktaturaConfig.get().capitalMountain) {
			return;
		}
		long started = System.currentTimeMillis();
		ServerWorld world = server.getOverworld();
		DiktaturaConfig cfg = DiktaturaConfig.get();
		BlockPos spawn = world.getSpawnPos();
		int ox = spawn.getX() + cfg.prisonOffsetX;
		int oz = spawn.getZ() + cfg.prisonOffsetZ - PrisonLayout.SIZE_Z / 2;
		int cx = ox + PrisonLayout.SIZE_X / 2;
		int cz = oz + PrisonLayout.SIZE_Z / 2;
		int reach = PLATEAU + SLOPE + SPINE + 4;
		for (int chx = (cx - reach) >> 4; chx <= (cx + reach) >> 4; chx++) {
			for (int chz = (cz - reach) >> 4; chz <= (cz + reach) >> 4; chz++) {
				world.getChunk(chx, chz);
			}
		}
		int ground = Math.max(world.getTopY(Heightmap.Type.OCEAN_FLOOR, cx, cz), world.getSeaLevel() + 1) - 1;
		int top = ground + HEIGHT;
		Random rnd = Random.create(world.getSeed() ^ 0x5EED_CA9L);

		// Флаг ставим сразу: дальше здания строятся «в горном режиме».
		state.capitalGenerated = true;
		mountain(world, cx, cz, ground, rnd);

		BlockPos prison = new BlockPos(ox, ground, oz);
		PrisonSystem.build(server, world, prison, true);
		state.prisonOrigin = prison;
		state.prisonGenerated = true;
		tunnel(world, prison, cx, cz, ground);
		stairs(world, cx, cz, ground, top);

		// Плато: зал посередине, кабинеты к северу, казино к югу; площадь перед ними с запада.
		BlockPos hall = new BlockPos(cx - GrandThroneHall.SIZE_X / 2, top, cz - GrandThroneHall.SIZE_Z / 2);
		BlockPos offices = new BlockPos(cx - 9, top, hall.getZ() - 6 - OfficeBuilding.SIZE_Z);
		BlockPos casino = new BlockPos(cx - 9, top, hall.getZ() + GrandThroneHall.SIZE_Z + 6);
		// Сначала здания, потом площадь: расчистка под здания не должна сносить фонари проспекта.
		GrandThroneHall.build(world, hall);
		OfficeBuilding.build(world, offices);
		CasinoBuilding.build(world, casino);
		plaza(world, hall, offices, casino, top);
		sweepLitter(world, cx, cz, top);

		state.throneOrigin = hall;
		state.throneGenerated = true;
		state.throneVersion = 2;
		state.officesOrigin = offices;
		state.officesGenerated = true;
		state.casinoOrigin = casino;
		state.casinoGenerated = true;
		state.casinoVersion = CasinoBuilding.VERSION;
		state.capitalSwept = true;
		state.markDirty();
		Diktatura.LOGGER.info("Столица построена: гора у {} (вершина y={}), за {} мс",
			new BlockPos(cx, ground, cz).toShortString(), top, System.currentTimeMillis() - started);
	}

	/** Тоннель от ворот тюрьмы на запад до подножия (для защиты законом о госпостройках). */
	public static BlockBox tunnelBox(DiktaturaState state) {
		BlockPos o = state.prisonOrigin;
		return new BlockBox(o.getX() - PLATEAU - SLOPE - 6, o.getY(), o.getZ() + TUNNEL_Z - 3,
			o.getX() - 1, o.getY() + 6, o.getZ() + TUNNEL_Z + 3);
	}

	// ================================================================== гора

	/** Расстояние от точки до центрального отрезка север–юг. */
	private static double spineDist(int x, int z, int cx, int cz) {
		double dz = Math.max(0, Math.abs(z - cz) - SPINE);
		double dx = x - cx;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Высота горы над землёй в точке (0 — за подножием). */
	private static int heightAt(int x, int z, int cx, int cz) {
		double e = spineDist(x, z, cx, cz);
		if (e <= PLATEAU) {
			return HEIGHT;
		}
		double t = (e - PLATEAU) / SLOPE;
		if (t >= 1) {
			return 0;
		}
		double smooth = 1 - t * t * (3 - 2 * t);
		// Шум на склоне — сильнее всего посередине склона, у плато и подножия гаснет.
		double noise = 2.6 * Math.sin(x * 0.13 + z * 0.07) + 1.9 * Math.sin(z * 0.11 - x * 0.05)
			+ 1.2 * Math.sin((x + z) * 0.31);
		double h = HEIGHT * smooth + noise * 4 * t * (1 - t);
		return Math.max(0, (int) Math.round(h));
	}

	/**
	 * Убрать с плато выпавшие при стройке предметы (фонари, ковры, саженцы и т. п.):
	 * при замене блоков их опоры иногда исчезают раньше них самих.
	 */
	public static int sweepLitter(ServerWorld world, int cx, int cz, int top) {
		return sweepLitter(world, cx, cz, top, e -> true);
	}

	/** Только «строительный мусор»: фонари, ковры, знамёна, цепи, факелы, саженцы, палки, яблоки, семена. */
	private static boolean isLitter(net.minecraft.entity.ItemEntity e) {
		var st = e.getStack();
		var item = st.getItem();
		return st.isIn(net.minecraft.registry.tag.ItemTags.WOOL_CARPETS) || st.isIn(net.minecraft.registry.tag.ItemTags.BANNERS)
			|| st.isIn(net.minecraft.registry.tag.ItemTags.SAPLINGS) || item == net.minecraft.item.Items.LANTERN
			|| item == net.minecraft.item.Items.CHAIN || item == net.minecraft.item.Items.TORCH || item == net.minecraft.item.Items.STICK
			|| item == net.minecraft.item.Items.APPLE || item == net.minecraft.item.Items.WHEAT_SEEDS
			|| item == net.minecraft.item.Items.SPRUCE_SAPLING;
	}

	private static int sweepLitter(ServerWorld world, int cx, int cz, int top, java.util.function.Predicate<net.minecraft.entity.ItemEntity> filter) {
		net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(cx - PLATEAU - SLOPE, top - HEIGHT - 4, cz - PLATEAU - SLOPE - SPINE,
			cx + PLATEAU + SLOPE, top + 40, cz + PLATEAU + SLOPE + SPINE);
		var items = world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, box, filter);
		items.forEach(net.minecraft.entity.Entity::discard);
		return items.size();
	}

	/** Для мира, построенного до исправления: один раз убрать мусор, когда столица загружена. */
	public static void sweepOnce(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (state.capitalSwept || !state.capitalGenerated || !state.throneGenerated) {
			return;
		}
		ServerWorld world = server.getOverworld();
		BlockPos hall = state.throneOrigin;
		int cx = hall.getX() + GrandThroneHall.SIZE_X / 2;
		int cz = hall.getZ() + GrandThroneHall.SIZE_Z / 2;
		if (!world.isChunkLoaded(cx >> 4, cz >> 4) || !world.isChunkLoaded(state.casinoOrigin.getX() >> 4, state.casinoOrigin.getZ() >> 4)) {
			return;
		}
		int n = sweepLitter(world, cx, cz, hall.getY(), Capital::isLitter);
		state.capitalSwept = true;
		state.markDirty();
		if (n > 0) {
			Diktatura.LOGGER.info("Столица: убрано выпавших при стройке предметов — {}", n);
		}
	}

	private static void mountain(ServerWorld world, int cx, int cz, int ground, Random rnd) {
		// Без обновлений соседей и без выпадения предметов: иначе трава, цветы и листва роняют добычу.
		int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
		BlockState stone = Blocks.STONE.getDefaultState();
		BlockState dirt = Blocks.DIRT.getDefaultState();
		BlockState grass = Blocks.GRASS_BLOCK.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		int rx = PLATEAU + SLOPE;
		int rz = PLATEAU + SLOPE + SPINE;
		for (int x = cx - rx; x <= cx + rx; x++) {
			for (int z = cz - rz; z <= cz + rz; z++) {
				int h = heightAt(x, z, cx, cz);
				if (h <= 0) {
					continue;
				}
				int target = ground + h;
				boolean plateau = h >= HEIGHT;
				int floor = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
				// На склоне природный рельеф выше горы не трогаем — гора с ним сливается.
				if (!plateau && floor - 1 >= target) {
					continue;
				}
				boolean rocky = h > 6 && !plateau && Math.abs(Math.sin(x * 0.21) + Math.cos(z * 0.17)) > 1.45;
				for (int y = Math.min(floor, ground - 2); y <= target; y++) {
					BlockState s = y == target ? (rocky ? stone : grass) : y >= target - 3 && !rocky ? dirt : stone;
					world.setBlockState(new BlockPos(x, y, z), s, flags);
				}
				// Над горой — чисто (деревья, трава и холмы, оказавшиеся внутри, убираем).
				int clearTo = target + (plateau ? 40 : 14);
				for (int y = target + 1; y <= clearTo; y++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!world.getBlockState(p).isAir()) {
						world.setBlockState(p, air, flags | Block.FORCE_STATE | Block.SKIP_DROPS);
					}
				}
			}
		}
		// Ели на склонах.
		for (int i = 0; i < 60; i++) {
			int x = cx + rnd.nextInt(rx * 2 + 1) - rx;
			int z = cz + rnd.nextInt(rz * 2 + 1) - rz;
			int h = heightAt(x, z, cx, cz);
			if (h < 3 || h > HEIGHT - 4 || Math.abs(z - cz - STAIRS_DZ) < 4 || Math.abs(z - cz - TUNNEL_Z + PrisonLayout.SIZE_Z / 2) < 4) {
				continue;
			}
			BlockPos base = new BlockPos(x, ground + h + 1, z);
			if (world.getBlockState(base.down()).isOf(Blocks.GRASS_BLOCK)) {
				spruce(world, base, 5 + rnd.nextInt(3));
			}
		}
	}

	private static void spruce(ServerWorld world, BlockPos base, int height) {
		int flags = Block.NOTIFY_LISTENERS;
		BlockState log = Blocks.SPRUCE_LOG.getDefaultState();
		BlockState leaves = Blocks.SPRUCE_LEAVES.getDefaultState()
			.with(net.minecraft.block.LeavesBlock.PERSISTENT, true);
		for (int y = 0; y < height; y++) {
			world.setBlockState(base.up(y), log, flags);
		}
		for (int y = 2; y <= height; y++) {
			int r = y == height ? 0 : (height - y) % 2 == 0 ? 2 : 1;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) + Math.abs(dz) > r + 1 || (dx == 0 && dz == 0 && y < height)) {
						continue;
					}
					BlockPos p = base.add(dx, y, dz);
					if (world.getBlockState(p).isAir()) {
						world.setBlockState(p, leaves, flags);
					}
				}
			}
		}
		world.setBlockState(base.up(height + 1), leaves, flags);
	}

	// ================================================================== тоннель и лестница

	/** Верхний твёрдый блок в столбце (по реальному рельефу, после генерации горы). */
	private static int surface(ServerWorld world, int x, int z) {
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		// Стволы ёлок, трава и прочее — не рельеф: спускаемся до настоящей земли.
		for (int i = 0; i < 24; i++) {
			BlockState s = world.getBlockState(new BlockPos(x, y, z));
			if (!(s.isIn(BlockTags.LOGS) || s.isIn(BlockTags.LEAVES) || s.isReplaceable())) {
				break;
			}
			y--;
		}
		return y;
	}

	/** Самая высокая поверхность поперёк прохода шириной 2·half+1. */
	private static int surfaceAcross(ServerWorld world, int x, int z, int half) {
		int m = Integer.MIN_VALUE;
		for (int dz = -half; dz <= half; dz++) {
			m = Math.max(m, surface(world, x, z + dz));
		}
		return m;
	}

	/**
	 * Тоннель от ворот тюрьмы (запад) до подножия, с порталом и вывеской. Копается, пока
	 * над ним есть гора или естественный рельеф выше пола; за порталом — площадка и, если
	 * земля ниже, ступени вниз до неё. Так выход никогда не упирается в стену и не висит в воздухе.
	 */
	private static void tunnel(ServerWorld world, BlockPos prison, int cx, int cz, int ground) {
		int flags = Block.NOTIFY_LISTENERS;
		BlockState wall = Blocks.STONE_BRICKS.getDefaultState();
		BlockState floor = Blocks.POLISHED_ANDESITE.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		int z0 = prison.getZ() + TUNNEL_Z;
		int limit = prison.getX() - PLATEAU - SLOPE - 60;
		int x = prison.getX() - 1;
		while (x > limit && (heightAt(x, z0, cx, cz) > 5 || surfaceAcross(world, x, z0, 3) > ground + 1)) {
			x--;
		}
		int exitX = x - 1;
		for (int tx = prison.getX() - 1; tx >= exitX; tx--) {
			boolean lamp = (prison.getX() - tx) % 6 == 3;
			for (int dz = -3; dz <= 3; dz++) {
				for (int y = 0; y <= 5; y++) {
					BlockPos p = new BlockPos(tx, ground + y, z0 + dz);
					boolean side = Math.abs(dz) == 3;
					BlockState st;
					if (y == 0) {
						st = side ? wall : floor;
					} else if (y == 5 || side) {
						st = wall;
					} else {
						st = air;
					}
					world.setBlockState(p, st, flags);
				}
				fill(world, tx, ground - 1, z0 + dz, wall);
			}
			if (lamp) {
				world.setBlockState(new BlockPos(tx, ground + 4, z0 - 2), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), flags);
				world.setBlockState(new BlockPos(tx, ground + 4, z0 + 2), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), flags);
			}
		}
		// Портал: арка из резного кирпича и вывеска.
		for (int dz = -3; dz <= 3; dz++) {
			for (int y = 0; y <= 6; y++) {
				boolean frame = Math.abs(dz) == 3 || y == 6 || (y == 5 && Math.abs(dz) == 2);
				if (frame) {
					world.setBlockState(new BlockPos(exitX, ground + y, z0 + dz), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), flags);
				}
			}
		}
		// Площадка перед порталом (5 блоков) — расчищена до неба, на уровне пола.
		int px = exitX - 1;
		for (; px >= exitX - 5; px--) {
			for (int dz = -2; dz <= 2; dz++) {
				world.setBlockState(new BlockPos(px, ground, z0 + dz), floor, flags);
				fill(world, px, ground - 1, z0 + dz, wall);
				for (int y = 1; y <= 6; y++) {
					world.setBlockState(new BlockPos(px, ground + y, z0 + dz), air, flags | Block.FORCE_STATE | Block.SKIP_DROPS);
				}
			}
		}
		// Если земля дальше ниже площадки — ступени вниз, пока не встанем на неё.
		int y = ground;
		while (y > ground - 30 && surfaceAcross(world, px, z0, 2) < y - 1) {
			y--;
			for (int dz = -2; dz <= 2; dz++) {
				world.setBlockState(new BlockPos(px, y, z0 + dz), Blocks.STONE_BRICK_STAIRS.getDefaultState()
					.with(StairsBlock.FACING, Direction.EAST), flags);
				fill(world, px, y - 1, z0 + dz, wall);
				for (int yy = 1; yy <= 4; yy++) {
					world.setBlockState(new BlockPos(px, y + yy, z0 + dz), air, flags | Block.FORCE_STATE | Block.SKIP_DROPS);
				}
			}
			px--;
		}
		BlockPos sign = new BlockPos(exitX - 1, ground + 5, z0);
		world.setBlockState(sign.east(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), flags);
		world.setBlockState(sign, Blocks.DARK_OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, Direction.WEST), flags);
		if (world.getBlockEntity(sign) instanceof SignBlockEntity be) {
			be.changeText(t -> t.withMessage(0, Text.translatable("diktatura.capital.sign.prison.0").formatted(Formatting.DARK_RED))
				.withMessage(1, Text.translatable("diktatura.capital.sign.prison.1"))
				.withMessage(2, Text.translatable("diktatura.capital.sign.prison.2"))
				.withMessage(3, Text.translatable("diktatura.capital.sign.prison.3")), true);
			be.setWaxed(true);
		}
	}

	/** Засыпать вниз от (x, y, z) до твёрдой земли (фундамент, насыпь). */
	private static void fill(ServerWorld world, int x, int yTop, int z, BlockState block) {
		for (int y = yTop; y > yTop - 40; y--) {
			BlockPos q = new BlockPos(x, y, z);
			BlockState s = world.getBlockState(q);
			if (!s.isReplaceable() && s.getFluidState().isEmpty()) {
				break;
			}
			world.setBlockState(q, block, Block.NOTIFY_LISTENERS);
		}
	}

	/** Лестница по западному склону от подножия до плато: не круче одного блока на шаг. */
	private static void stairs(ServerWorld world, int cx, int cz, int ground, int top) {
		int flags = Block.NOTIFY_LISTENERS;
		BlockState path = Blocks.STONE_BRICKS.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		int z0 = cz + STAIRS_DZ;
		int startX = cx - PLATEAU - SLOPE - 2;
		int endX = cx - PLATEAU + 2;
		int n = endX - startX + 1;
		int[] y = new int[n];
		// Высота лестницы — по настоящему рельефу (гора + природные холмы у подножия).
		for (int i = 0; i < n; i++) {
			y[i] = Math.min(top, surfaceAcross(world, startX + i, z0, 2));
		}
		y[n - 1] = top;
		// Сверху вниз: подъём не больше блока на шаг (где круче — насыпь)...
		for (int i = n - 2; i >= 0; i--) {
			y[i] = Math.max(y[i], y[i + 1] - 1);
		}
		// ...и снизу вверх: спуск тоже не больше блока (после бугра — насыпь, а не обрыв).
		for (int i = 1; i < n; i++) {
			y[i] = Math.min(top, Math.max(y[i], y[i - 1] - 1));
		}
		for (int i = 0; i < n; i++) {
			int x = startX + i;
			boolean up = i + 1 < n && y[i + 1] > y[i];
			for (int dz = -2; dz <= 2; dz++) {
				boolean edge = Math.abs(dz) == 2;
				BlockPos p = new BlockPos(x, y[i], z0 + dz);
				// Насыпь до земли.
				for (int yy = y[i] - 1; yy > y[i] - 30; yy--) {
					BlockPos q = new BlockPos(x, yy, z0 + dz);
					BlockState s = world.getBlockState(q);
					if (!s.isReplaceable() && s.getFluidState().isEmpty()) {
						break;
					}
					world.setBlockState(q, path, flags);
				}
				world.setBlockState(p, path, flags);
				// Над лестницей — проход 3 блока (вырезается в склоне).
				for (int yy = 1; yy <= 4; yy++) {
					world.setBlockState(p.up(yy), air, flags | Block.FORCE_STATE | Block.SKIP_DROPS);
				}
				if (edge) {
					world.setBlockState(p.up(), Blocks.STONE_BRICK_WALL.getDefaultState(), flags);
					if (i % 6 == 0) {
						world.setBlockState(p.up(2), Blocks.LANTERN.getDefaultState(), flags);
					}
				} else if (up) {
					// Ступень: следующий блок выше — ставим ступеньку на этот уровень + 1.
					world.setBlockState(p.up(), Blocks.STONE_BRICK_STAIRS.getDefaultState()
						.with(StairsBlock.FACING, Direction.EAST), flags);
				} else if (i > 0 && y[i - 1] > y[i]) {
					// Спуск после бугра: ступенька, обращённая назад (на запад).
					world.setBlockState(p.up(), Blocks.STONE_BRICK_STAIRS.getDefaultState()
						.with(StairsBlock.FACING, Direction.WEST), flags);
				}
			}
		}
	}

	// ================================================================== площадь на плато

	/**
	 * Проспект вдоль фасадов (x = центр − 21…−19) от кабинетов до казино, ответвления к входам
	 * и к верхней ступени лестницы. Фонари вдоль проспекта.
	 */
	private static void plaza(ServerWorld world, BlockPos hall, BlockPos offices, BlockPos casino, int top) {
		int flags = Block.NOTIFY_LISTENERS;
		int cx = hall.getX() + GrandThroneHall.SIZE_X / 2;
		int cz = hall.getZ() + GrandThroneHall.SIZE_Z / 2;
		int x0 = cx - 21;
		int x1 = cx - 19;
		int z0 = offices.getZ() - 1;
		int z1 = casino.getZ() + CasinoBuilding.SIZE_Z;
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean border = x == x0 - 1 || x == x1 + 1;
				world.setBlockState(new BlockPos(x, top, z), (border ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS).getDefaultState(), flags);
			}
		}
		for (int z = z0 + 3; z <= z1; z += 7) {
			for (int x : new int[] {x0 - 2, x1 + 2}) {
				world.setBlockState(new BlockPos(x, top + 1, z), Blocks.STONE_BRICK_WALL.getDefaultState(), flags);
				world.setBlockState(new BlockPos(x, top + 2, z), Blocks.LANTERN.getDefaultState(), flags);
			}
		}
		// Ответвления к входам: два кабинета, казино, портик зала; и к лестнице с запада.
		// До -2: в столбце x = -1 у двери висит табличка кабинета, расчистка над дорожкой её сносила.
		path(world, x1 + 2, offices.getX() - 2, offices.getZ() + 3, top);
		path(world, x1 + 2, offices.getX() - 2, offices.getZ() + 10, top);
		path(world, x1 + 2, casino.getX() - 1, casino.getZ() + 5, top);
		path(world, x1 + 2, hall.getX() - 5, hall.getZ() + GrandThroneHall.DOOR_Z, top);
		path(world, cx - PLATEAU + 2, x0 - 2, cz + STAIRS_DZ, top);
	}

	private static void path(ServerWorld world, int fromX, int toX, int z, int top) {
		for (int x = Math.min(fromX, toX); x <= Math.max(fromX, toX); x++) {
			for (int dz = -1; dz <= 1; dz++) {
				world.setBlockState(new BlockPos(x, top, z + dz), Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
				// Над дорожкой — чисто (ёлки и неровности плато не перегораживают проход).
				for (int y = 1; y <= 4; y++) {
					BlockPos q = new BlockPos(x, top + y, z + dz);
					if (!world.getBlockState(q).isAir()) {
						world.setBlockState(q, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
					}
				}
			}
		}
	}

}
