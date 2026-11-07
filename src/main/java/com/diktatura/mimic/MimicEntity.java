package com.diktatura.mimic;

import com.diktatura.goose.ModSounds;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.ModEntities;
import com.diktatura.ui.Hud;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Сундук-ревизор (мимик). Притворяется сундуком с добычей; открыли — «внезапная ревизия»:
 * у сундука зубы и ножки. Прыгает, кусает и при каждом укусе изымает предмет из сумки
 * в своё содержимое. Одолели ревизора — всё изъятое и добыча сундука выпадают.
 * По мотивам мимика из мода-референса (MIT, © 2026 Gr1tW4lk); модель и текстура оттуда.
 */
public class MimicEntity extends HostileEntity {
	private static final TrackedData<Boolean> AWAKE = DataTracker.registerData(MimicEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final byte STATUS_CHOMP = 102;
	private final List<ItemStack> loot = new ArrayList<>();
	private int hopCooldown;
	/** Для клиента: анимация укуса. */
	public int chompTicks;

	public MimicEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 12;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 34.0)
			.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
			.add(EntityAttributes.GENERIC_ARMOR, 4.0)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
			.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6)
			.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(AWAKE, false);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(2, new MeleeAttackGoal(this, 1.2, false) {
			@Override
			public boolean canStart() {
				return isAwake() && super.canStart();
			}
		});
		this.targetSelector.add(1, new RevengeGoal(this));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true) {
			@Override
			public boolean canStart() {
				return isAwake() && super.canStart();
			}
		});
	}

	public boolean isAwake() {
		return this.dataTracker.get(AWAKE);
	}

	public void wakeUp() {
		if (isAwake()) {
			return;
		}
		this.dataTracker.set(AWAKE, true);
		this.playSound(ModSounds.MIMIC_GROWL, 1.6f, 1.0f);
		this.setVelocity(0, 0.55, 0);
		if (getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 1.1, getZ(), 4, 0.4, 0.2, 0.4, 0);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (chompTicks > 0) {
			chompTicks--;
		}
		if (getWorld().isClient) {
			return;
		}
		if (!isAwake()) {
			this.setVelocity(0, getVelocity().y, 0);
			PlayerEntity p = getWorld().getClosestPlayer(this, 2.6);
			if (p != null && !p.isSpectator() && !p.isCreative()) {
				wakeUp();
			}
			return;
		}
		// Ревизор не ходит — он прыгает.
		if (hopCooldown > 0) {
			hopCooldown--;
		}
		if (isOnGround() && hopCooldown == 0 && getTarget() != null) {
			hopCooldown = 12 + this.random.nextInt(8);
			this.jump();
			this.playSound(ModSounds.MIMIC_CHOMP, 0.7f, 1.3f);
			getWorld().sendEntityStatus(this, STATUS_CHOMP);
		}
	}

	@Override
	public void handleStatus(byte status) {
		if (status == STATUS_CHOMP) {
			chompTicks = 10;
		} else {
			super.handleStatus(status);
		}
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean r = super.tryAttack(target);
		if (r) {
			this.playSound(ModSounds.MIMIC_CHOMP, 1.4f, 0.9f);
			getWorld().sendEntityStatus(this, STATUS_CHOMP);
			if (target instanceof ServerPlayerEntity p && this.random.nextFloat() < 0.5f) {
				confiscate(p);
			}
		}
		return r;
	}

	/** Изъять при ревизии один случайный предмет из сумки (не из рук и не броню). */
	private void confiscate(ServerPlayerEntity p) {
		PlayerInventory inv = p.getInventory();
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < inv.main.size(); i++) {
			if (i != inv.selectedSlot && !inv.main.get(i).isEmpty()) {
				slots.add(i);
			}
		}
		if (slots.isEmpty()) {
			return;
		}
		int slot = slots.get(this.random.nextInt(slots.size()));
		ItemStack taken = inv.main.get(slot);
		inv.main.set(slot, ItemStack.EMPTY);
		loot.add(taken);
		setPersistent();
		Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.mimic.confiscated"),
			Text.translatable("diktatura.mimic.confiscated.hint", taken.getName()), 4);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (!getWorld().isClient && !isAwake()) {
			wakeUp();
		}
		return super.damage(source, amount);
	}

	@Override
	public void onDeath(DamageSource source) {
		if (!getWorld().isClient) {
			for (ItemStack s : loot) {
				dropStack(s);
			}
			loot.clear();
			dropStack(new ItemStack(Items.CHEST));
		}
		super.onDeath(source);
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return false;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return isAwake() ? ModSounds.MIMIC_GROWL : null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BLOCK_WOOD_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BLOCK_CHEST_CLOSE;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Awake", isAwake());
		NbtList list = new NbtList();
		for (ItemStack s : loot) {
			if (!s.isEmpty()) {
				list.add(s.encode(getRegistryManager()));
			}
		}
		nbt.put("Loot", list);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.dataTracker.set(AWAKE, nbt.getBoolean("Awake"));
		NbtList list = nbt.getList("Loot", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			ItemStack.fromNbt(getRegistryManager(), list.getCompound(i)).ifPresent(loot::add);
		}
	}

	/** Превратить сундук на месте в ревизора, забрав его содержимое. */
	public static MimicEntity fromChest(ServerWorld world, BlockPos pos, Inventory inv, Direction facing) {
		MimicEntity m = ModEntities.MIMIC.create(world);
		if (m == null) {
			return null;
		}
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty()) {
				m.loot.add(s.copy());
			}
		}
		inv.clear();
		world.removeBlock(pos, false);
		m.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, facing.asRotation(), 0);
		m.setHeadYaw(facing.asRotation());
		m.setBodyYaw(facing.asRotation());
		m.setPersistent();
		world.spawnEntity(m);
		m.wakeUp();
		world.playSound(null, pos, ModSounds.MIMIC_GROWL, SoundCategory.HOSTILE, 2.0f, 0.8f);
		return m;
	}
}
