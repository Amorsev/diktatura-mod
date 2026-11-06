package com.diktatura.npc;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * Базовый НПС-ведомство: вождь, налоговик, курьер и т. д. Один тип сущности на всех,
 * какой это персонаж — решает {@link #npcId()} (данные в data/&lt;ns&gt;/diktatura/npcs/&lt;id&gt;.json).
 * Неуязвим, не двигается, не толкается, не исчезает, смотрит на игрока рядом.
 */
public class NpcEntity extends PathAwareEntity {
	private static final TrackedData<String> NPC_ID = DataTracker.registerData(NpcEntity.class,
		TrackedDataHandlerRegistry.STRING);

	public NpcEntity(EntityType<? extends NpcEntity> type, World world) {
		super(type, world);
		setInvulnerable(true);
		setPersistent();
		setCanPickUpLoot(false);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
			.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(NPC_ID, "");
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f, 1.0f));
		goalSelector.add(2, new LookAroundGoal(this));
	}

	/** Идентификатор персонажа, например {@code diktatura:dictator}. */
	public String npcId() {
		return dataTracker.get(NPC_ID);
	}

	public void setNpcId(String id) {
		dataTracker.set(NPC_ID, id == null ? "" : id);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("NpcId", npcId());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		setNpcId(nbt.getString("NpcId"));
		setInvulnerable(true);
		setPersistent();
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		// Только /kill и пустота: государственный служащий бессмертен по должности.
		if (isInvulnerableTo(source)) {
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void pushAwayFrom(Entity entity) {
	}

	@Override
	public boolean isPushedByFluids() {
		return false;
	}
}
