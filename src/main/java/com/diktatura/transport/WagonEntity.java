package com.diktatura.transport;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Повозка госперевозки (модель из Blockbench): деревянная телега, в которую впряжён
 * гражданин-заключённый. Движет её {@link TransportSystem} — сама она никуда не едет,
 * не сохраняется в мире и пропадает, если в ней никого нет. На клиенте плавно догоняет
 * позицию с сервера и считает пройденный путь — по нему крутятся колёса и шагает заключённый.
 */
public class WagonEntity extends Entity {
	private int lerpSteps;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private float lerpYaw;
	/** Пройденный путь в блоках (только на клиенте, для анимации). */
	public float distance;
	public float prevDistance;

	public WagonEntity(EntityType<? extends WagonEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.setNoGravity(true);
		this.setInvulnerable(true);
		this.setSilent(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps) {
		this.lerpX = x;
		this.lerpY = y;
		this.lerpZ = z;
		this.lerpYaw = yaw;
		this.lerpSteps = 3;
	}

	@Override
	public void tick() {
		double ox = getX();
		double oz = getZ();
		super.tick();
		if (getWorld().isClient) {
			if (lerpSteps > 0) {
				double nx = getX() + (lerpX - getX()) / lerpSteps;
				double ny = getY() + (lerpY - getY()) / lerpSteps;
				double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
				setYaw(getYaw() + MathHelper.wrapDegrees(lerpYaw - getYaw()) / lerpSteps);
				lerpSteps--;
				setPosition(nx, ny, nz);
			}
			prevDistance = distance;
			double dx = getX() - ox;
			double dz = getZ() - oz;
			distance += (float) Math.sqrt(dx * dx + dz * dz);
		} else if (age > 40 && !hasPassengers()) {
			discard();
		}
	}
}
