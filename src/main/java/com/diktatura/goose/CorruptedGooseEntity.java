package com.diktatura.goose;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Служебный гусь таможни. Ходит за своим таможенником, шипит на досматриваемого и носит
 * в клюве конфискат. Неуязвим, не размножается и не сохраняется: без хозяина исчезает.
 */
public class CorruptedGooseEntity extends GooseEntity {
	@Nullable
	private UUID owner;
	@Nullable
	private UUID suspect;
	private int orphanTicks;

	public CorruptedGooseEntity(EntityType<? extends AnimalEntity> type, World world) {
		super(type, world);
		this.setInvulnerable(true);
	}

	public void setOwner(Entity owner) {
		this.owner = owner.getUuid();
	}

	/** На кого шипеть (досматриваемый игрок). */
	public void setSuspect(@Nullable PlayerEntity p) {
		this.suspect = p == null ? null : p.getUuid();
	}

	@Nullable
	public Entity owner() {
		return owner != null && this.getWorld() instanceof ServerWorld sw ? sw.getEntity(owner) : null;
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new FollowOwnerGoal(this));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
		this.goalSelector.add(7, new LookAroundGoal(this));
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient) {
			return;
		}
		Entity o = owner();
		if (o == null || !o.isAlive()) {
			// Таможенник ушёл — гусь тоже (после перезапуска хозяина нет вовсе).
			if (++orphanTicks > 40) {
				vanish();
			}
			return;
		}
		orphanTicks = 0;
		PlayerEntity s = suspect == null ? null : this.getWorld().getPlayerByUuid(suspect);
		boolean near = s != null && s.isAlive() && squaredDistanceTo(s) < 6 * 6;
		setHissing(near && this.age % 80 < 30);
		if (near && this.age % 80 == 0) {
			getLookControl().lookAt(s, 30f, 30f);
			hiss();
		}
	}

	/** Исчезнуть с облачком (визит закончен). */
	public void vanish() {
		if (this.getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 8, 0.3, 0.3, 0.3, 0.02);
		}
		discard();
	}

	/** Показать в клюве конфискованный предмет (только для вида). */
	public void showConfiscated(ItemStack stack) {
		setHeld(stack.copyWithCount(1));
		honk(0.8f);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)
			|| source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL)) {
			return super.damage(source, amount);
		}
		if (!this.getWorld().isClient) {
			hiss();
		}
		return false;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean isBreedingItem(ItemStack stack) {
		return false;
	}

	@Nullable
	@Override
	public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
		return null;
	}

	/** Держаться в паре блоков от таможенника. */
	static class FollowOwnerGoal extends Goal {
		private final CorruptedGooseEntity goose;

		FollowOwnerGoal(CorruptedGooseEntity goose) {
			this.goose = goose;
			this.setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			Entity o = goose.owner();
			return o != null && goose.squaredDistanceTo(o) > 3 * 3;
		}

		@Override
		public boolean shouldContinue() {
			Entity o = goose.owner();
			return o != null && goose.squaredDistanceTo(o) > 2 * 2 && !goose.getNavigation().isIdle();
		}

		@Override
		public void tick() {
			Entity o = goose.owner();
			if (o == null) {
				return;
			}
			if (goose.squaredDistanceTo(o) > 20 * 20) {
				goose.refreshPositionAndAngles(o.getX(), o.getY(), o.getZ(), goose.getYaw(), 0f);
			} else if (goose.age % 10 == 0 || goose.getNavigation().isIdle()) {
				goose.getNavigation().startMovingTo(o, 1.3);
			}
		}

		@Override
		public void stop() {
			goose.getNavigation().stop();
		}
	}
}
