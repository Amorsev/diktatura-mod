package com.diktatura.transport;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Повозка госперевозки (модель из Blockbench): деревянная телега, в которую впряжён
 * гражданин-заключённый. Движет её {@link TransportSystem} — сама она никуда не едет,
 * не сохраняется в мире и пропадает, если в ней никого нет. На клиенте плавно догоняет
 * позицию с сервера и считает пройденный путь — по нему крутятся колёса и шагает заключённый.
 *
 * <p>Модель и анимация — GeckoLib ({@code geo/entity/wagon.geo.json}, {@code animations/entity/wagon.animation.json},
 * генераторы {@code blockbench/gen_wagon.py} и {@code blockbench/gl_anims.py}). Скорость анимации подстраивается
 * под скорость повозки, чтобы колёса катились без проскальзывания.
 */
public class WagonEntity extends Entity implements GeoEntity {
	private static final RawAnimation ROLL = RawAnimation.begin().thenLoop("animation.wagon.roll");
	/** Путь за один цикл «roll» (5 оборотов колеса r = 6 px) за 0.49 с — номинальная скорость, блоков/с. */
	private static final double ROLL_NOMINAL = 11.781 / 0.49;
	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

	private int lerpSteps;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private float lerpYaw;
	/** Пройденный путь в блоках (только на клиенте, для анимации). */
	public float distance;
	public float prevDistance;
	/** Клиент: направление уже взято из движения (дальше поворот с сервера не нужен). */
	private boolean headingKnown;

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

	/** Скорость за последний тик, блоков в секунду (на клиенте). */
	public double speedPerSecond() {
		return (distance - prevDistance) * 20.0;
	}

	/** Множитель скорости анимации: реальная скорость / номинальная скорость цикла. */
	protected static double animSpeed(double v, double nominal) {
		return MathHelper.clamp(v / nominal, 0.1, 4.0);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "move", 3, state -> {
			double v = speedPerSecond();
			if (v < 0.3) {
				return PlayState.STOP;
			}
			state.getController().setAnimationSpeed(animSpeed(v, ROLL_NOMINAL));
			return state.setAndContinue(ROLL);
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return geoCache;
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
				if (!headingKnown) {
					// Пока не поехали — поворот с сервера (при появлении).
					setYaw(getYaw() + MathHelper.wrapDegrees(lerpYaw - getYaw()) / lerpSteps);
				}
				lerpSteps--;
				setPosition(nx, ny, nz);
			}
			prevDistance = distance;
			double dx = getX() - ox;
			double dz = getZ() - oz;
			double step = Math.sqrt(dx * dx + dz * dz);
			distance += (float) step;
			// Морда — по направлению движения. Поворот считаем на клиенте по пройденному пути: с сервера
			// он для такой сущности не доходит, и повозка ехала «боком» в ту сторону, куда смотрела при появлении.
			if (step > 0.02) {
				float heading = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
				float yaw = headingKnown ? getYaw() + MathHelper.wrapDegrees(heading - getYaw()) * 0.35f : heading;
				headingKnown = true;
				setYaw(yaw);
			}
		} else if (age > 40 && !hasPassengers()) {
			discard();
		}
	}
}
