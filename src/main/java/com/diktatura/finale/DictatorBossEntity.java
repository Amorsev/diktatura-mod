package com.diktatura.finale;

import com.diktatura.item.ModItems;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * Вождь в финальном бою (фаза 2): встал с трона, в руке тапок, в другой — книга законов.
 * Умереть не может: на последних 5% здоровья объявляет «ничью по-диктаторски».
 */
public class DictatorBossEntity extends HostileEntity {
	/** Двойник вождя (приём «размножение указом»): падает с одного удара. */
	public boolean decoy;
	private long lastCannonLine;
	/** Сколько тиков вождь держит «приёмное» оружие (тапок, мегафон), потом снова берёт скипетр. */
	private int wieldTicks;
	public DictatorBossEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 0;
		equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.SCEPTER));
		equipStack(EquipmentSlot.OFFHAND, new ItemStack(ModItems.LAW_BOOK));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			setEquipmentDropChance(slot, 0f);
		}
	}

	/** Взять в правую руку оружие приёма на время; затем вождь вернётся к скипетру. */
	public void wield(ItemStack stack, int ticks) {
		equipStack(EquipmentSlot.MAINHAND, stack);
		wieldTicks = ticks;
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient && wieldTicks > 0 && --wieldTicks == 0) {
			equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.SCEPTER));
		}
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 300.0)
			.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
			.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
			.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8)
			.add(EntityAttributes.GENERIC_SCALE, 1.15);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(2, new MeleeAttackGoal(this, 1.1, true));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 16f));
		this.goalSelector.add(7, new LookAroundGoal(this));
		this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false,
			e -> !e.isSpectator() && !((PlayerEntity) e).isCreative()));
	}

	/** Порог «ничьей»: ниже него вождь не опускается. */
	public float drawThreshold() {
		return getMaxHealth() * 0.05f;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) {
			return super.damage(source, amount);
		}
		if (this.getWorld().isClient || FinalFight.isDrawing()) {
			return false;
		}
		if (decoy) {
			if (this.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
				sw.spawnParticles(net.minecraft.particle.ParticleTypes.POOF, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
				sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, net.minecraft.sound.SoundCategory.HOSTILE, 1f, 1f);
			}
			if (source.getAttacker() instanceof ServerPlayerEntity p) {
				com.diktatura.ui.Hud.alert(p, com.diktatura.network.UiPayloads.AlertKind.INFO,
					net.minecraft.text.Text.translatable("diktatura.finale.decoy"), net.minecraft.text.Text.empty(), 3);
			}
			discard();
			return true;
		}
		if (source.getAttacker() instanceof ServerPlayerEntity p) {
			FinalFight.participant(p);
			FinalFight.onPlayerHit(p, amount);
		}
		float min = drawThreshold();
		float capped = Math.min(amount, Math.max(0f, getHealth() - min));
		boolean r = super.damage(source, capped);
		if (getHealth() <= min + 0.01f) {
			FinalFight.draw(this);
		}
		return r;
	}

	/** Драконобой — на драконов: по вождю луч отскакивает искрами. */
	public void deflectCannon(ServerPlayerEntity shooter, net.minecraft.util.math.Vec3d impact) {
		if (!(this.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) {
			return;
		}
		sw.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK, impact.x, impact.y, impact.z, 12, 0.2, 0.2, 0.2, 0.4);
		sw.spawnParticles(BossFx.GOLD, impact.x, impact.y, impact.z, 4, 0.2, 0.2, 0.2, 0);
		long now = sw.getTime();
		if (now - lastCannonLine > 60) {
			lastCannonLine = now;
			sw.playSound(null, getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, net.minecraft.sound.SoundCategory.HOSTILE, 1.2f, 0.7f);
			com.diktatura.ui.Voice.say(this, net.minecraft.text.Text.translatable("entity.diktatura.dictator"),
				net.minecraft.text.Text.translatable("diktatura.finale.cannon." + this.random.nextInt(3)),
				SoundEvents.ENTITY_VILLAGER_NO, 0.6f);
			com.diktatura.ui.Hud.alert(shooter, com.diktatura.network.UiPayloads.AlertKind.INFO,
				net.minecraft.text.Text.translatable("diktatura.finale.cannon"),
				net.minecraft.text.Text.translatable("diktatura.finale.cannon.hint"), 4);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_VILLAGER_HURT;
	}

	@Override
	public float getSoundPitch() {
		return 0.6f;
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

	@Override
	public boolean cannotDespawn() {
		return true;
	}
}
