package com.diktatura.control;

import com.diktatura.config.DiktaturaConfig;
import com.diktatura.law.LawEngine;
import com.diktatura.mixin.MobEntityAccessor;
import com.diktatura.npc.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Мобы-силовики. Часть зомби — «стройбат»: в жёлтых касках, строят столбы и мосты, чтобы
 * добраться до гражданина. Часть скелетов — снайперы госохраны с лазерным прицелом.
 * Решение принимается один раз при первом появлении моба (тег), шансы — в конфиге.
 * По мотивам зомби-строителей и снайперов из мода-референса (MIT, © 2026 Gr1tW4lk).
 */
public final class Enforcers {
	public static final String TAG_ROLLED = "diktatura_enforcer_rolled";
	public static final String TAG_BUILDER = "diktatura_builder";

	private Enforcers() {
	}

	public static void registerEvents() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ZombieEntity z && (z.getClass() == ZombieEntity.class || z.getClass() == HuskEntity.class)) {
				if (!z.getCommandTags().contains(TAG_ROLLED)) {
					z.addCommandTag(TAG_ROLLED);
					DiktaturaConfig cfg = DiktaturaConfig.get();
					if (cfg.enforcerMobs && !z.isBaby() && world.random.nextDouble() < cfg.builderZombieChance * mult()) {
						makeBuilder(z);
					}
				}
				if (z.getCommandTags().contains(TAG_BUILDER)) {
					((MobEntityAccessor) z).diktatura$getGoalSelector().add(1, new BuildGoal(z));
				}
			} else if (entity instanceof SkeletonEntity sk && sk.getClass() == SkeletonEntity.class
				&& !sk.getCommandTags().contains(TAG_ROLLED)) {
				sk.addCommandTag(TAG_ROLLED);
				DiktaturaConfig cfg = DiktaturaConfig.get();
				if (cfg.enforcerMobs && world.random.nextDouble() < cfg.sniperSkeletonChance * mult()) {
					LawEngine.defer(() -> toSniper(world, sk));
				}
			}
		});
	}

	/** В Кровавую луну силовиков вдвое с половиной больше. */
	private static double mult() {
		return com.diktatura.loyalty.BloodMoon.isActive() ? 2.5 : 1.0;
	}

	public static void makeBuilder(ZombieEntity z) {
		z.addCommandTag(TAG_BUILDER);
		ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
		helmet.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(0xF5C518, false));
		z.equipStack(EquipmentSlot.HEAD, helmet);
		z.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIRT, 64));
		z.setEquipmentDropChance(EquipmentSlot.HEAD, 0f);
		z.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.2f);
		z.setCustomName(Text.translatable("entity.diktatura.builder_zombie").formatted(Formatting.YELLOW));
	}

	private static void toSniper(ServerWorld w, SkeletonEntity sk) {
		if (sk.isRemoved() || !sk.isAlive()) {
			return;
		}
		SniperSkeletonEntity s = ModEntities.SNIPER.create(w);
		if (s == null) {
			return;
		}
		s.refreshPositionAndAngles(sk.getX(), sk.getY(), sk.getZ(), sk.getYaw(), sk.getPitch());
		s.initialize(w, w.getLocalDifficulty(sk.getBlockPos()), SpawnReason.CONVERSION, null);
		s.equipSniper();
		for (String t : sk.getCommandTags()) {
			s.addCommandTag(t);
		}
		if (sk.isPersistent()) {
			s.setPersistent();
		}
		if (sk.getTarget() != null) {
			s.setTarget(sk.getTarget());
		}
		sk.discard();
		w.spawnEntity(s);
	}

	/** Стройбат: столб под собой, если цель выше, и мост, если впереди пропасть. */
	static class BuildGoal extends Goal {
		private final ZombieEntity z;
		private int cooldown;
		private int stuck;
		private double lastDist;

		BuildGoal(ZombieEntity z) {
			this.z = z;
			this.setControls(EnumSet.noneOf(Control.class));
		}

		@Override
		public boolean canStart() {
			return z.getTarget() != null && z.getTarget().isAlive();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity t = z.getTarget();
			if (t == null) {
				return;
			}
			if (cooldown > 0) {
				cooldown--;
				return;
			}
			World w = z.getWorld();
			double dy = t.getY() - z.getY();
			double horiz = Math.sqrt(Math.pow(t.getX() - z.getX(), 2) + Math.pow(t.getZ() - z.getZ(), 2));
			double dist = z.distanceTo(t);
			stuck = Math.abs(dist - lastDist) < 0.05 ? stuck + 1 : 0;
			lastDist = dist;
			if (dy > 1.5 && horiz < 5 && z.isOnGround()) {
				z.getJumpControl().setActive();
				cooldown = 6;
				return;
			}
			if (dy > 1.5 && horiz < 5 && !z.isOnGround() && z.getVelocity().y < 0.1) {
				BlockPos under = z.getBlockPos();
				if (place(w, under)) {
					z.setPosition(z.getX(), under.getY() + 1.0, z.getZ());
					cooldown = 8;
				}
				return;
			}
			if (stuck > 20 || (z.isOnGround() && horiz > 1.5)) {
				Direction dir = Direction.getFacing(t.getX() - z.getX(), 0, t.getZ() - z.getZ());
				BlockPos front = z.getBlockPos().offset(dir);
				BlockPos below = front.down();
				if (w.getBlockState(below).isAir() && w.getBlockState(below.down()).isAir()) {
					if (place(w, below)) {
						cooldown = 10;
					}
				} else if (stuck > 20 && w.getBlockState(front).isAir() && dy > 0.5 && place(w, front)) {
					z.getJumpControl().setActive();
					cooldown = 10;
				}
			}
		}

		private boolean place(World w, BlockPos pos) {
			BlockState s = w.getBlockState(pos);
			// Госстройки (тюрьма, зал, кабинеты) стройбат не трогает.
			if (!s.isReplaceable() || com.diktatura.law.StateStructures.contains(w, pos)) {
				return false;
			}
			w.setBlockState(pos, Blocks.DIRT.getDefaultState());
			w.playSound(null, pos, SoundEvents.BLOCK_GRAVEL_PLACE, SoundCategory.HOSTILE, 1f, 1f);
			z.swingHand(Hand.MAIN_HAND);
			return true;
		}
	}
}
