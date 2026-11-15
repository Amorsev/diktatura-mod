package com.diktatura.transport;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Карета госТакси. Как и тюремная повозка, сама не ездит — её ведёт {@link TransportSystem}; в мире не
 * сохраняется. Класс кареты синхронизируется с клиентом (от него зависят модель и место пассажира).
 *
 * <p>Анимации GeckoLib: «trot» — рысь, колёса, кучер погоняет, телегу трясёт (скорость подстраивается под ход
 * кареты), «idle» — стоят: лошади переминаются, машут хвостом.
 */
public class TaxiEntity extends WagonEntity {
	private static final RawAnimation TROT = RawAnimation.begin().thenLoop("animation.taxi.trot");
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.taxi.idle");

	private static final TrackedData<Integer> TAXI_CLASS = net.minecraft.entity.data.DataTracker.registerData(
		TaxiEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public TaxiEntity(EntityType<? extends TaxiEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(TAXI_CLASS, 0);
	}

	public TaxiClass taxiClass() {
		return TaxiClass.byOrdinal(dataTracker.get(TAXI_CLASS));
	}

	public void setTaxiClass(TaxiClass c) {
		dataTracker.set(TAXI_CLASS, c.ordinal());
	}

	/**
	 * Место пассажира. Смещение задано в координатах кареты, поэтому поворачиваем его вместе с ней
	 * (стандартные точки посадки ванила поворачивает сама, а переопределение — нет).
	 */
	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "move", 4, state -> {
			double v = speedPerSecond();
			if (v < 0.3) {
				state.getController().setAnimationSpeed(1.0);
				return state.setAndContinue(IDLE);
			}
			state.getController().setAnimationSpeed(animSpeed(v, taxiClass().animNominal));
			return state.setAndContinue(TROT);
		}));
	}

	@Override
	protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
		return taxiClass().seat.rotateY(-getYaw() * ((float) Math.PI / 180.0f));
	}
}
