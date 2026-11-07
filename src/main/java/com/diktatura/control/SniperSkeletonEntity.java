package com.diktatura.control;

import com.diktatura.goose.ModSounds;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Снайпер госохраны: сначала светит красным лазером, потом стреляет больно и точно.
 * По мотивам скелета-снайпера из мода-референса (MIT, © 2026 Gr1tW4lk).
 */
public class SniperSkeletonEntity extends SkeletonEntity {
	public static final int AIM_TIME = 45;
	private static final TrackedData<Integer> AIM = DataTracker.registerData(SniperSkeletonEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> AIM_TARGET = DataTracker.registerData(SniperSkeletonEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public SniperSkeletonEntity(EntityType<? extends SkeletonEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 12;
	}

	public static DefaultAttributeContainer.Builder createSniperAttributes() {
		return AbstractSkeletonEntity.createAbstractSkeletonAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
			.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(AIM, 0);
		builder.add(AIM_TARGET, -1);
	}

	@Override
	protected void initGoals() {
		super.initGoals();
		this.goalSelector.add(1, new SniperGoal(this));
	}

	@Override
	public void updateAttackType() {
		// Своё поведение вместо обычного лука.
	}

	@Override
	protected boolean isAffectedByDaylight() {
		return false;
	}

	public int getAim() {
		return this.dataTracker.get(AIM);
	}

	public int getAimTargetId() {
		return this.dataTracker.get(AIM_TARGET);
	}

	void shoot(LivingEntity target) {
		ItemStack bow = getMainHandStack().isOf(Items.BOW) ? getMainHandStack() : new ItemStack(Items.BOW);
		PersistentProjectileEntity p = ProjectileUtil.createArrowProjectile(this, new ItemStack(Items.ARROW), 1.0f, bow);
		double dx = target.getX() - getX();
		double dy = target.getBodyY(0.5) - p.getY();
		double dz = target.getZ() - getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		p.setVelocity(dx, dy + d * 0.04, dz, 3.4f, 0.0f);
		p.setDamage(2.2);
		p.setCritical(true);
		getWorld().spawnEntity(p);
		getWorld().playSound(null, getBlockPos(), ModSounds.SNIPER_SHOT, SoundCategory.HOSTILE, 3.0f, 1.0f);
	}

	/** Каска цвета хаки, лук и подпись. */
	public void equipSniper() {
		ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
		helmet.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(0x2B3A1E, false));
		equipStack(EquipmentSlot.HEAD, helmet);
		equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		setEquipmentDropChance(EquipmentSlot.HEAD, 0f);
		setCustomName(Text.translatable("entity.diktatura.sniper").formatted(Formatting.DARK_RED));
	}

	static class SniperGoal extends Goal {
		private final SniperSkeletonEntity mob;
		private int cooldown;

		SniperGoal(SniperSkeletonEntity mob) {
			this.mob = mob;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity t = mob.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public void stop() {
			mob.dataTracker.set(AIM, 0);
			mob.dataTracker.set(AIM_TARGET, -1);
			mob.setAttacking(false);
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity t = mob.getTarget();
			if (t == null) {
				return;
			}
			double dist = mob.squaredDistanceTo(t);
			boolean visible = mob.getVisibilityCache().canSee(t);
			mob.getLookControl().lookAt(t, 30f, 30f);
			if (dist > 30 * 30 || !visible) {
				mob.getNavigation().startMovingTo(t, 1.0);
			} else {
				mob.getNavigation().stop();
			}
			if (cooldown > 0) {
				cooldown--;
				return;
			}
			int aim = mob.getAim();
			if (visible && dist < 44 * 44) {
				if (aim == 0) {
					mob.playSound(ModSounds.LASER_CHARGE, 1.2f, 1.0f);
				}
				aim++;
				mob.dataTracker.set(AIM_TARGET, t.getId());
				mob.setAttacking(true);
				if (aim >= AIM_TIME) {
					mob.shoot(t);
					aim = 0;
					cooldown = 50;
					mob.dataTracker.set(AIM_TARGET, -1);
				}
			} else {
				aim = Math.max(0, aim - 2);
				if (aim == 0) {
					mob.dataTracker.set(AIM_TARGET, -1);
				}
			}
			mob.dataTracker.set(AIM, aim);
		}
	}
}
