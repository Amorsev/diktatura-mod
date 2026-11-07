package com.diktatura.control;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.joml.Vector3f;

/** Странный гриб: светится и пускает розовые споры. Растёт, только если препарат включён в настройках. */
public class StrangeMushroomBlock extends PlantBlock {
	public static final MapCodec<StrangeMushroomBlock> CODEC = createCodec(StrangeMushroomBlock::new);
	private static final VoxelShape SHAPE = PlantBlock.createCuboidShape(4, 0, 4, 12, 9, 12);
	private static final DustParticleEffect SPORE = new DustParticleEffect(new Vector3f(1f, 0.4f, 0.9f), 0.6f);

	public StrangeMushroomBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends PlantBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		Vec3d off = state.getModelOffset(world, pos);
		return SHAPE.offset(off.x, off.y, off.z);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(4) == 0) {
			world.addParticle(SPORE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.6,
				pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
		}
	}
}
