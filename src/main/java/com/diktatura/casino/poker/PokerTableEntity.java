package com.diktatura.casino.poker;

import net.minecraft.entity.Entity;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Покерный стол госказино (модель GeckoLib {@code geo/entity/poker_table.geo.json}, рисуется ×2). Сам стол ничего
 * не решает: игру ведёт {@link PokerGame}, а стол показывает её всем вокруг — карты на сукне, ставки, банк, имена и
 * стеки над местами. Это «вид» передаётся клиентам одним NBT ({@link #VIEW}).
 *
 * <p>ПКМ по столу — открыть экран покера. Стол не сохраняется в мире: его ставит и восстанавливает казино.
 */
public class PokerTableEntity extends Entity implements GeoEntity {
	private static final TrackedData<NbtCompound> VIEW = DataTracker.registerData(PokerTableEntity.class,
		TrackedDataHandlerRegistry.NBT_COMPOUND);
	private static final RawAnimation SHUFFLE = RawAnimation.begin().thenPlay("animation.poker_table.shuffle");

	/**
	 * Места игроков: смещение от центра стола в блоках в системе стола (крупье на -Z, +X — справа), по часовой
	 * стрелке от крупье. Совпадает с табуретами модели (blockbench/gen_casino.py, SEATS ×2 / 16).
	 */
	public static final double[][] SEATS = {
		{1.625, -2.125}, {3.125, 0.0}, {1.625, 2.125}, {-1.625, 2.125}, {-3.125, 0.0}, {-1.625, -2.125}};
	/** Где стоит крупье и лежит колода (система стола, блоки). */
	public static final double[] DEALER = {0.0, -2.125};
	public static final double[] DECK = {0.0, -0.75};
	/** Высота сукна над полом, блоки. */
	public static final double FELT = 1.0;

	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

	public PokerTableEntity(EntityType<? extends PokerTableEntity> type, World world) {
		super(type, world);
		this.setNoGravity(true);
		this.setInvulnerable(true);
		this.setSilent(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(VIEW, new NbtCompound());
	}

	public NbtCompound view() {
		return dataTracker.get(VIEW);
	}

	public void setView(NbtCompound nbt) {
		dataTracker.set(VIEW, nbt);
	}

	/** Точка в системе стола (x, z — блоки, y — над полом) → мировые координаты. */
	public Vec3d local(double x, double y, double z) {
		return new Vec3d(x, y, z).rotateY((float) Math.toRadians(180.0 - getYaw())).add(getPos());
	}

	public Vec3d seatPos(int seat) {
		return local(SEATS[seat][0], 0, SEATS[seat][1]);
	}

	/** Сервер: колода «тасуется» на столе. */
	public void shuffleFx() {
		triggerAnim("fx", "shuffle");
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		if (player instanceof ServerPlayerEntity sp) {
			PokerGame.open(sp, this);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public boolean canHit() {
		return true;
	}

	@Override
	public boolean isCollidable() {
		return true;
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
		controllers.add(new AnimationController<>(this, "fx", 0, state -> PlayState.STOP).triggerableAnim("shuffle", SHUFFLE));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return geoCache;
	}
}
