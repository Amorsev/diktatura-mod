package com.diktatura.ore;

import com.diktatura.goose.ModSounds;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.ModEntities;
import com.diktatura.ui.Hud;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;

/**
 * Руда-эмигрант: отрастила ножки и бежит за границу. Задержали — двойной «улов».
 * Через две минуты, если рядом никого, закапывается обратно («эмигрировала»).
 * По мотивам убегающего блока из мода-референса (MIT, © 2026 Gr1tW4lk).
 */
public class RunawayOreEntity extends PathAwareEntity {
	private static final TrackedData<Optional<BlockState>> STATE = DataTracker.registerData(RunawayOreEntity.class,
		TrackedDataHandlerRegistry.OPTIONAL_BLOCK_STATE);

	private int screamCooldown;
	private int lifetime;

	public RunawayOreEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 8.0)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
			.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20.0)
			.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(STATE, Optional.of(Blocks.IRON_ORE.getDefaultState()));
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new FleeEntityGoal<>(this, PlayerEntity.class, 14.0f, 1.4, 1.85));
		this.goalSelector.add(4, new WanderAroundFarGoal(this, 1.0));
		this.goalSelector.add(5, new LookAroundGoal(this));
	}

	public BlockState getBlockState() {
		return this.dataTracker.get(STATE).orElse(Blocks.IRON_ORE.getDefaultState());
	}

	public void setBlockState(BlockState state) {
		this.dataTracker.set(STATE, Optional.of(state));
	}

	public boolean isPrecious() {
		BlockState s = getBlockState();
		return s.isIn(BlockTags.DIAMOND_ORES) || s.isIn(BlockTags.EMERALD_ORES) || s.isIn(BlockTags.GOLD_ORES);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient) {
			return;
		}
		lifetime++;
		if (screamCooldown > 0) {
			screamCooldown--;
		}
		PlayerEntity near = this.getWorld().getClosestPlayer(this, 7.0);
		if (near != null && screamCooldown == 0) {
			screamCooldown = 50 + this.random.nextInt(60);
			this.playSound(ModSounds.ORE_SCREAMS[this.random.nextInt(ModSounds.ORE_SCREAMS.length)], 1.3f, 0.9f + this.random.nextFloat() * 0.3f);
		}
		if (this.age % 4 == 0 && this.getVelocity().horizontalLengthSquared() > 0.002 && this.isOnGround()) {
			this.playSound(ModSounds.TINY_STEPS, 0.5f, 1.2f + this.random.nextFloat() * 0.4f);
		}
		// Через 2 минуты руда «эмигрирует»: закапывается обратно в землю, если никто не видит.
		if (lifetime > 2400 && near == null && this.getWorld() instanceof ServerWorld sw) {
			BlockPos pos = getBlockPos();
			if (sw.getBlockState(pos).isAir() && sw.getBlockState(pos.down()).isSolidBlock(sw, pos.down())) {
				sw.setBlockState(pos, getBlockState());
			}
			sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, getBlockState()), getX(), getY() + 0.5, getZ(),
				30, 0.3, 0.3, 0.3, 0.1);
			discard();
		}
	}

	@Override
	protected void dropLoot(DamageSource source, boolean causedByPlayer) {
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}
		BlockState state = getBlockState();
		List<ItemStack> drops = Block.getDroppedStacks(state, sw, getBlockPos(), null);
		// Задержан при попытке эмиграции — двойной улов.
		for (int i = 0; i < 2; i++) {
			for (ItemStack s : drops) {
				this.dropStack(s.copy());
			}
		}
		ExperienceOrbEntity.spawn(sw, getPos(), isPrecious() ? 6 + this.random.nextInt(8) : 2 + this.random.nextInt(3));
		sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), getX(), getY() + 0.6, getZ(), 40, 0.3, 0.3, 0.3, 0.15);
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (!this.getWorld().isClient && source.getAttacker() instanceof ServerPlayerEntity p) {
			Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.ore.caught"), Text.translatable("diktatura.ore.caught.hint"), 4);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return getBlockState().getSoundGroup().getHitSound();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return getBlockState().getSoundGroup().getBreakSound();
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.put("BlockState", NbtHelper.fromBlockState(getBlockState()));
		nbt.putInt("Lifetime", lifetime);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("BlockState")) {
			setBlockState(NbtHelper.toBlockState(this.getWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK), nbt.getCompound("BlockState")));
		}
		lifetime = nbt.getInt("Lifetime");
	}

	/** Руда под киркой отращивает ножки. */
	public static RunawayOreEntity spawnFrom(ServerWorld world, BlockPos pos, BlockState state, PlayerEntity player) {
		RunawayOreEntity e = ModEntities.RUNAWAY_ORE.create(world);
		if (e == null) {
			return null;
		}
		world.setBlockState(pos, Blocks.AIR.getDefaultState());
		e.setBlockState(state);
		e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), 0);
		if (e.isPrecious()) {
			var speed = e.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
			if (speed != null) {
				speed.setBaseValue(0.38);
			}
		}
		e.setVelocity(player.getRotationVector().multiply(0.3).add(0, 0.42, 0));
		world.spawnEntity(e);
		world.playSound(null, pos, ModSounds.ORE_SCREAMS[0], SoundCategory.NEUTRAL, 1.5f, 1.0f);
		world.spawnParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.02);
		return e;
	}
}
