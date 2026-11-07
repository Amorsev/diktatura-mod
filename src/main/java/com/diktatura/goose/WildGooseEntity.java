package com.diktatura.goose;

import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.ModEntities;
import com.diktatura.ui.Hud;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.AnimalMateGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Дикий гусь. Бродит по миру, подкрадывается с шипением, вырывает предмет из руки
 * (или из сумки) и с гоготом убегает. Убил — добыча выпадает. Размножается хлебом.
 */
public class WildGooseEntity extends GooseEntity {
	public int stealCooldown = 100;
	private int honkTimer;

	public WildGooseEntity(EntityType<? extends AnimalEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(1, new FleeWithLootGoal(this));
		this.goalSelector.add(2, new StealGoal(this));
		this.goalSelector.add(3, new AnimalMateGoal(this, 1.0));
		this.goalSelector.add(4, new TemptGoal(this, 1.1, stack -> stack.isOf(Items.BREAD), false));
		this.goalSelector.add(5, new WanderAroundFarGoal(this, 1.0));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
		this.goalSelector.add(7, new LookAroundGoal(this));
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.getWorld().isClient) {
			if (stealCooldown > 0) {
				stealCooldown--;
			}
			if (hasHeld() && --honkTimer <= 0) {
				honkTimer = 25 + this.random.nextInt(30);
				honk(1.0f + this.random.nextFloat() * 0.3f);
			}
		}
	}

	/** Вырвать предмет: из руки, а если рука пуста — из случайного слота сумки. */
	public boolean stealFrom(PlayerEntity player) {
		PlayerInventory inv = player.getInventory();
		ItemStack hand = player.getMainHandStack();
		ItemStack taken;
		if (!hand.isEmpty()) {
			taken = hand.copy();
			player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
		} else {
			List<Integer> slots = new ArrayList<>();
			for (int i = 0; i < inv.main.size(); i++) {
				if (!inv.main.get(i).isEmpty()) {
					slots.add(i);
				}
			}
			if (slots.isEmpty()) {
				return false;
			}
			int slot = slots.get(this.random.nextInt(slots.size()));
			taken = inv.main.get(slot).copy();
			inv.main.set(slot, ItemStack.EMPTY);
		}
		setHeld(taken);
		setPersistent();
		honk(1.3f);
		if (this.getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, taken), getX(), getY() + 0.8, getZ(), 12, 0.2, 0.2, 0.2, 0.1);
			sw.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 6, 0.3, 0.2, 0.3, 0.02);
		}
		if (player instanceof ServerPlayerEntity sp) {
			Hud.alert(sp, AlertKind.EVENT, Text.translatable("diktatura.goose.stole"),
				Text.translatable("diktatura.goose.stole.hint", taken.getName()), 5);
		}
		return true;
	}

	@Override
	public void onDeath(DamageSource source) {
		if (!this.getWorld().isClient && hasHeld()) {
			ItemEntity e = this.dropStack(getHeld().copy());
			if (e != null) {
				e.setVelocity(e.getVelocity().add(0, 0.25, 0));
			}
			setHeld(ItemStack.EMPTY);
		}
		super.onDeath(source);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean r = super.damage(source, amount);
		if (r && !this.getWorld().isClient && !hasHeld()) {
			// Обиженный гусь не убегает — он идёт мстить.
			stealCooldown = 0;
		}
		return r;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("StealCooldown", stealCooldown);
		if (hasHeld()) {
			nbt.put("Stolen", getHeld().encode(this.getRegistryManager()));
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		stealCooldown = nbt.getInt("StealCooldown");
		if (nbt.contains("Stolen")) {
			setHeld(ItemStack.fromNbt(this.getRegistryManager(), nbt.getCompound("Stolen")).orElse(ItemStack.EMPTY));
		}
	}

	@Override
	public boolean cannotDespawn() {
		return hasHeld() || super.cannotDespawn();
	}

	@Override
	public boolean isBreedingItem(ItemStack stack) {
		return stack.isOf(Items.BREAD);
	}

	@Nullable
	@Override
	public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
		return ModEntities.WILD_GOOSE.create(world);
	}

	// ================================================================== ИИ

	static boolean isVictim(PlayerEntity p) {
		return p.isAlive() && !p.isSpectator() && !p.getAbilities().creativeMode;
	}

	/** Подойти к игроку, зашипеть и вырвать предмет. */
	static class StealGoal extends Goal {
		private final WildGooseEntity goose;
		private PlayerEntity target;
		private int ticks;

		StealGoal(WildGooseEntity goose) {
			this.goose = goose;
			this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			if (goose.hasHeld() || goose.stealCooldown > 0 || goose.isBaby()) {
				return false;
			}
			PlayerEntity p = goose.getWorld().getClosestPlayer(goose, 20.0);
			if (p == null || !isVictim(p) || p.getInventory().isEmpty()) {
				return false;
			}
			target = p;
			return true;
		}

		@Override
		public boolean shouldContinue() {
			return target != null && isVictim(target) && !goose.hasHeld() && ticks < 400
				&& goose.squaredDistanceTo(target) < 26 * 26;
		}

		@Override
		public void start() {
			ticks = 0;
			goose.setHissing(true);
			goose.hiss();
		}

		@Override
		public void stop() {
			goose.setHissing(false);
			goose.getNavigation().stop();
			if (!goose.hasHeld()) {
				goose.stealCooldown = 200;
			}
			target = null;
		}

		@Override
		public void tick() {
			ticks++;
			goose.getLookControl().lookAt(target, 30f, 30f);
			if (ticks % 10 == 0) {
				goose.getNavigation().startMovingTo(target, 1.55);
			}
			if (goose.squaredDistanceTo(target) < 2.2 && goose.stealFrom(target)) {
				target.damage(goose.getDamageSources().mobAttack(goose), 1.0f);
				goose.stealCooldown = 600;
			}
		}
	}

	/** С добычей — удирать от людей и гоготать. */
	static class FleeWithLootGoal extends Goal {
		private final WildGooseEntity goose;

		FleeWithLootGoal(WildGooseEntity goose) {
			this.goose = goose;
			this.setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return goose.hasHeld() && goose.getWorld().getClosestPlayer(goose, 14.0) != null;
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void tick() {
			PlayerEntity p = goose.getWorld().getClosestPlayer(goose, 14.0);
			if (p == null) {
				return;
			}
			if (goose.getNavigation().isIdle() || goose.age % 15 == 0) {
				Vec3d away = NoPenaltyTargeting.findFrom(goose, 16, 6, p.getPos());
				if (away != null) {
					goose.getNavigation().startMovingTo(away.x, away.y, away.z, 1.75);
				}
			}
		}

		@Override
		public void stop() {
			goose.getNavigation().stop();
		}
	}
}
