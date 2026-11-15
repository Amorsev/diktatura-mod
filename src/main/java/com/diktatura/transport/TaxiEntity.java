package com.diktatura.transport;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Карета госТакси. Как и тюремная повозка, сама не ездит — её ведёт {@link TransportSystem}; в мире не
 * сохраняется. Класс кареты синхронизируется с клиентом (от него зависят модель и место пассажира).
 */
public class TaxiEntity extends WagonEntity {
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
	protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
		return taxiClass().seat.rotateY(-getYaw() * ((float) Math.PI / 180.0f));
	}
}
