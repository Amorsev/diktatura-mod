package com.diktatura.control;

import com.diktatura.economy.Economy;
import com.diktatura.goose.ModSounds;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.ui.Hud;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/**
 * Двойник-агент Бюро надзора. Ночью приходит к нарушителю комендантского часа: его скин,
 * его броня, его оружие и красные глаза. Каждый удар — штраф «за нарушение самим собой».
 * Задержали двойника — премия. На рассвете уходит.
 * Механика по мотивам двойника Кровавой луны из мода-референса (MIT, © 2026 Gr1tW4lk).
 */
public class CurfewDoubleEntity extends HostileEntity {
	private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(CurfewDoubleEntity.class,
		TrackedDataHandlerRegistry.OPTIONAL_UUID);
	private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(0.6f, 0f, 0f), 0.8f);
	public static final int FINE = 2;
	public static final int BOUNTY = 5;
	/** Вызван тестовой командой: не исчезает днём и без закона (только если оригинал ушёл). */
	public boolean testMode;
	/** Пришёл в Кровавую луну: уходит, когда луна закончится. */
	public boolean bloodMoon;

	public CurfewDoubleEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 10;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setEquipmentDropChance(slot, 0f);
		}
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
			.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.31)
			.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(OWNER, Optional.empty());
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(2, new MeleeAttackGoal(this, 1.25, true));
		this.goalSelector.add(7, new LookAroundGoal(this));
		// Охотится только на своего оригинала.
		this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, false, false,
			e -> getOwnerId().map(id -> id.equals(e.getUuid())).orElse(true)));
	}

	public Optional<UUID> getOwnerId() {
		return this.dataTracker.get(OWNER);
	}

	public void copyFrom(PlayerEntity p) {
		this.dataTracker.set(OWNER, Optional.of(p.getUuid()));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack s = p.getEquippedStack(slot);
			this.equipStack(slot, s.isEmpty() ? ItemStack.EMPTY : s.copy());
		}
		if (getMainHandStack().isEmpty()) {
			equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		}
		this.setCustomName(Text.translatable("entity.diktatura.curfew_double.named", p.getName()).formatted(Formatting.DARK_RED));
		this.setCustomNameVisible(true);
	}

	public ServerPlayerEntity owner() {
		if (getOwnerId().isEmpty() || !(this.getWorld() instanceof ServerWorld sw)) {
			return null;
		}
		return sw.getServer().getPlayerManager().getPlayer(getOwnerId().get());
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient) {
			return;
		}
		if (getOwnerId().isEmpty()) {
			// Призван яйцом (для проверки) — обычный враждебный моб без хозяина.
			return;
		}
		ServerPlayerEntity o = owner();
		long t = this.getWorld().getTimeOfDay() % 24000L;
		boolean night = t >= 13000L && t <= 23000L;
		if (o == null || o.getWorld() != this.getWorld() || squaredDistanceTo(o) > 80 * 80
			|| (bloodMoon && !com.diktatura.loyalty.BloodMoon.isActive(o.getServer()))
			|| (!testMode && !bloodMoon && (!night || !CurfewDoubles.curfewActive(o.getServer())))) {
			vanish();
			return;
		}
		if (getTarget() == null) {
			setTarget(o);
		}
		// Повторяет за оригиналом: вы присели — и он присел.
		boolean sneak = o.isSneaking();
		if (sneak != isSneaking()) {
			setSneaking(sneak);
			setPose(sneak ? EntityPose.CROUCHING : EntityPose.STANDING);
		}
		if (this.age % 4 == 0 && this.getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(RED, getX(), getY() + this.random.nextDouble() * 1.8, getZ(), 1, 0.25, 0.1, 0.25, 0);
		}
	}

	@Override
	public boolean tryAttack(Entity target) {
		boolean hit = super.tryAttack(target);
		if (hit && target instanceof ServerPlayerEntity p && getOwnerId().map(id -> id.equals(p.getUuid())).orElse(false)) {
			if (Economy.take(p, FINE, "diktatura.money.reason.double", "")) {
				Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.double.fined", FINE),
					Text.translatable("diktatura.double.fined.hint"), 4);
			}
		}
		return hit;
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (!this.getWorld().isClient && source.getAttacker() instanceof ServerPlayerEntity p) {
			Economy.add(p, BOUNTY, "diktatura.money.reason.double_caught", "", true);
			Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.double.caught"),
				Text.translatable("diktatura.double.caught.hint", BOUNTY), 6);
		}
	}

	public void vanish() {
		if (this.getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
		}
		discard();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.GLITCH;
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return false;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}
}
