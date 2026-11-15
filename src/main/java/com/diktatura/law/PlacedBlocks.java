package com.diktatura.law;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/**
 * Где игроки поставили «природные» блоки (землю, камень, песок… — тег natural_ground). Нужно закону о высоте
 * построек: такие блоки — постройка, а не земля, иначе башню из земли можно было строить сколько угодно.
 * Хранится в data/diktatura_placed.dat каждого измерения; запись снимается, когда игрок ломает блок.
 */
public final class PlacedBlocks extends PersistentState {
	private static final Type<PlacedBlocks> TYPE = new Type<>(PlacedBlocks::new, PlacedBlocks::fromNbt, null);
	private final LongOpenHashSet positions = new LongOpenHashSet();

	private static PlacedBlocks get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(TYPE, "diktatura_placed");
	}

	public static void mark(ServerWorld world, BlockPos pos) {
		PlacedBlocks s = get(world);
		if (s.positions.add(pos.asLong())) {
			s.markDirty();
		}
	}

	public static void unmark(ServerWorld world, BlockPos pos) {
		PlacedBlocks s = get(world);
		if (s.positions.remove(pos.asLong())) {
			s.markDirty();
		}
	}

	public static boolean isPlaced(ServerWorld world, BlockPos pos) {
		return get(world).positions.contains(pos.asLong());
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		nbt.putLongArray("Positions", positions.toLongArray());
		return nbt;
	}

	private static PlacedBlocks fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		PlacedBlocks s = new PlacedBlocks();
		for (long l : nbt.getLongArray("Positions")) {
			s.positions.add(l);
		}
		return s;
	}
}
