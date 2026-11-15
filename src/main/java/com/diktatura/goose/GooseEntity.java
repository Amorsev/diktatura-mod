package com.diktatura.goose;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Общая часть гусей: предмет в клюве, шипение, гогот и «раскрытый клюв» для модели.
 * Дикий гусь ({@link WildGooseEntity}) ворует, служебный ({@link CorruptedGooseEntity}) — конфискует.
 *
 * <p>Модель и анимации — GeckoLib ({@code geo/entity/goose.geo.json}, {@code animations/entity/goose.animation.json};
 * генераторы {@code blockbench/gen_goose.py} и {@code blockbench/gl_anims.py}): ходьба вперевалку, покой,
 * шипение, бег с добычей и гогот.
 */
public abstract class GooseEntity extends AnimalEntity implements GeoEntity {
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.goose.walk");
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.goose.idle");
	private static final RawAnimation HISS = RawAnimation.begin().thenLoop("animation.goose.hiss");
	private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.goose.run");
	private static final RawAnimation HONK = RawAnimation.begin().thenPlay("animation.goose.honk");
	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
	private static final TrackedData<ItemStack> HELD = DataTracker.registerData(GooseEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
	private static final TrackedData<Boolean> HISSING = DataTracker.registerData(GooseEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final byte STATUS_HONK = 101;

	/** Для клиента: сколько тиков ещё открыт клюв. */
	public int mouthOpenTicks;

	protected GooseEntity(EntityType<? extends AnimalEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createGooseAttributes() {
		return MobEntity.createMobAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
			.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(HELD, ItemStack.EMPTY);
		builder.add(HISSING, false);
	}

	/** Предмет в клюве (украденный или конфискованный). */
	public ItemStack getHeld() {
		return this.dataTracker.get(HELD);
	}

	public void setHeld(ItemStack stack) {
		this.dataTracker.set(HELD, stack);
	}

	public boolean hasHeld() {
		return !getHeld().isEmpty();
	}

	public boolean isHissing() {
		return this.dataTracker.get(HISSING);
	}

	public void setHissing(boolean b) {
		this.dataTracker.set(HISSING, b);
	}

	public void honk(float pitch) {
		this.playSound(ModSounds.GOOSE_HONK, 1.4f, pitch);
		this.getWorld().sendEntityStatus(this, STATUS_HONK);
	}

	public void hiss() {
		this.playSound(ModSounds.GOOSE_HISS, 1.0f, 0.9f + this.random.nextFloat() * 0.2f);
		this.getWorld().sendEntityStatus(this, STATUS_HONK);
	}

	@Override
	public void handleStatus(byte status) {
		if (status == STATUS_HONK) {
			mouthOpenTicks = 8;
		} else {
			super.handleStatus(status);
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "body", 4, state -> {
			state.getController().setAnimationSpeed(1.0);
			if (isHissing()) {
				return state.setAndContinue(HISS);
			}
			if (state.isMoving()) {
				if (hasHeld()) {
					return state.setAndContinue(RUN);
				}
				// Шаг чаще, когда гусь идёт быстрее.
				state.getController().setAnimationSpeed(0.6 + Math.min(1.0, state.getLimbSwingAmount()) * 1.2);
				return state.setAndContinue(WALK);
			}
			return state.setAndContinue(IDLE);
		}));
		// Гогот — отдельно: клюв раскрывается поверх любой позы.
		controllers.add(new AnimationController<>(this, "beak", 2, state -> {
			if (mouthOpenTicks > 0 && !isHissing()) {
				return state.setAndContinue(HONK);
			}
			return PlayState.STOP;
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return geoCache;
	}

	@Override
	public void tick() {
		super.tick();
		if (mouthOpenTicks > 0) {
			mouthOpenTicks--;
		}
	}

	@Nullable
	@Override
	protected SoundEvent getAmbientSound() {
		return this.random.nextInt(3) == 0 ? ModSounds.GOOSE_HONK : null;
	}

	@Override
	public void playAmbientSound() {
		super.playAmbientSound();
		this.getWorld().sendEntityStatus(this, STATUS_HONK);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.GOOSE_HISS;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.GOOSE_HONK;
	}

	@Override
	protected float getSoundVolume() {
		return 1.2f;
	}
}
