package com.diktatura.casino;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Стол госказино на GeckoLib: колесо рулетки, автомат «Пятилетка» или кости. Итог решает сервер ({@link Casino}),
 * а сущность только показывает его: время начала и результат передаются клиентам ({@link #SPIN}), и колесо, шарик,
 * барабаны и кости останавливаются ровно на нём (рулетка и барабаны — расчётом в рендере, бросок костей — анимацией
 * {@code animation.dice.throw}). Между играми на столе остаётся последний итог. ПКМ — окно крупье этого стола.
 */
public class CasinoPropEntity extends Entity implements GeoEntity {
	public enum Kind {
		ROULETTE("roulette_wheel", 1.4f, 0.35f), SLOTS("slot_machine", 1.0f, 3.3f), DICE("dice_set", 1.2f, 0.35f);

		public final String model;
		final float width;
		final float height;

		Kind(String model, float width, float height) {
			this.model = model;
			this.width = width;
			this.height = height;
		}

		public static Kind byOrdinal(int i) {
			Kind[] v = values();
			return i >= 0 && i < v.length ? v[i] : ROULETTE;
		}
	}

	/** Длительность вращения рулетки и моменты остановки барабанов автомата (тики) — общие для сервера и рендера. */
	public static final int ROULETTE_TICKS = 66;
	public static final int[] SLOT_STOPS = {24, 38, 52};

	private static final TrackedData<Integer> KIND = DataTracker.registerData(CasinoPropEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<NbtCompound> SPIN = DataTracker.registerData(CasinoPropEntity.class,
		TrackedDataHandlerRegistry.NBT_COMPOUND);
	private static final RawAnimation THROW = RawAnimation.begin().thenPlay("animation.dice.throw");

	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

	public CasinoPropEntity(EntityType<? extends CasinoPropEntity> type, World world) {
		super(type, world);
		this.setNoGravity(true);
		this.setInvulnerable(true);
		this.setSilent(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(KIND, 0);
		builder.add(SPIN, new NbtCompound());
	}

	public Kind kind() {
		return Kind.byOrdinal(dataTracker.get(KIND));
	}

	public void setKind(Kind kind) {
		dataTracker.set(KIND, kind.ordinal());
		calculateDimensions();
	}

	/** Последняя игра: start — время мира начала, остальное — итог (сектор, барабаны, очки костей). */
	public NbtCompound spin() {
		return dataTracker.get(SPIN);
	}

	public void setSpin(NbtCompound nbt) {
		dataTracker.set(SPIN, nbt);
		if (kind() == Kind.DICE) {
			triggerAnim("fx", "throw");
		}
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (KIND.equals(data)) {
			calculateDimensions();
		}
	}

	@Override
	public EntityDimensions getDimensions(EntityPose pose) {
		Kind k = kind();
		return EntityDimensions.fixed(k.width, k.height);
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		if (player instanceof ServerPlayerEntity sp) {
			Casino.openTable(sp, kind());
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public boolean canHit() {
		return true;
	}

	@Override
	public boolean isCollidable() {
		return kind() == Kind.SLOTS;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "fx", 0, state -> PlayState.STOP).triggerableAnim("throw", THROW));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return geoCache;
	}
}
