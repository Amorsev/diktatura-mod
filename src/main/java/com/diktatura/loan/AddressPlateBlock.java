package com.diktatura.loan;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * Табличка «Адрес должника» — эмалированная табличка на стену. Вешается рядом с кроватью;
 * поставивший игрок считается прописанным по этому адресу ({@link Registration}).
 * FACING — куда смотрит лицевая сторона (стена — с противоположной стороны).
 */
public class AddressPlateBlock extends HorizontalFacingBlock {
	public static final MapCodec<AddressPlateBlock> CODEC = createCodec(AddressPlateBlock::new);
	private static final VoxelShape NORTH = Block.createCuboidShape(2, 4, 15, 14, 12, 16);
	private static final VoxelShape SOUTH = Block.createCuboidShape(2, 4, 0, 14, 12, 1);
	private static final VoxelShape EAST = Block.createCuboidShape(0, 4, 2, 1, 12, 14);
	private static final VoxelShape WEST = Block.createCuboidShape(15, 4, 2, 16, 12, 14);

	public AddressPlateBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return switch (state.get(FACING)) {
			case SOUTH -> SOUTH;
			case EAST -> EAST;
			case WEST -> WEST;
			default -> NORTH;
		};
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		Direction facing = state.get(FACING);
		BlockPos wall = pos.offset(facing.getOpposite());
		return world.getBlockState(wall).isSideSolidFullSquare(world, wall, facing);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		Direction side = ctx.getSide();
		if (side.getAxis().isHorizontal()) {
			BlockState s = getDefaultState().with(FACING, side);
			if (s.canPlaceAt(ctx.getWorld(), ctx.getBlockPos())) {
				return s;
			}
		}
		for (Direction d : ctx.getPlacementDirections()) {
			if (d.getAxis().isHorizontal()) {
				BlockState s = getDefaultState().with(FACING, d.getOpposite());
				if (s.canPlaceAt(ctx.getWorld(), ctx.getBlockPos())) {
					return s;
				}
			}
		}
		return null;
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
	                                               WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (direction == state.get(FACING).getOpposite() && !state.canPlaceAt(world, pos)) {
			return Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		Registration.onPlaced(world, pos, placer);
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			Registration.onRemoved(world, pos);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
