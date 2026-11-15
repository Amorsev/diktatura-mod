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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Базовый НПС-ведомство: вождь, налоговик, курьер и т. д. Один тип сущности на всех,
 * какой это персонаж — решает {@link #npcId()} (данные в data/&lt;ns&gt;/diktatura/npcs/&lt;id&gt;.json).
 * Неуязвим, не двигается, не толкается, не исчезает, смотрит на игрока рядом.
 *
 * <p>Анимации — GeckoLib ({@code animations/entity/npc.animation.json}, общие для всех NPC мода): покой и ходьба,
 * а по событию — жест ({@link #gesture}): разговор, вождь машет, инспектор пишет, крупье крутит рулетку,
 * оператор дёргает рычаг, крупье костей трясёт стакан.
 */
public class NpcEntity extends PathAwareEntity implements GeoEntity {
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.npc.idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.npc.walk");
	/** Жесты по событию (имя → анимация). */
	public static final String TALK = "talk";
	public static final String WAVE = "wave";
	public static final String WRITE = "write";
	public static final String SPIN = "spin";
	public static final String LEVER = "lever";
	public static final String SHAKE = "shake";
	private static final String[] GESTURES = {TALK, WAVE, WRITE, SPIN, LEVER, SHAKE};

	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
	private AnimationController<NpcEntity> actionController;
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

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "move", 5,
			state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
		AnimationController<NpcEntity> action = new AnimationController<>(this, "action", 3, state -> PlayState.STOP);
		for (String g : GESTURES) {
			action.triggerableAnim(g, RawAnimation.begin().thenPlay("animation.npc." + g));
		}
		this.actionController = action;
		controllers.add(action);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return geoCache;
	}

	/** Сервер: показать жест всем рядом (GeckoLib сам отправит клиентам). */
	public void gesture(String name) {
		if (!getWorld().isClient) {
			triggerAnim("action", name);
		}
	}

	/** Жест при разговоре: вождь машет, налоговый инспектор пишет, остальные — жест рукой. */
	public void greetGesture() {
		String path = npcId().contains(":") ? npcId().substring(npcId().indexOf(':') + 1) : npcId();
		gesture(switch (path) {
			case "dictator" -> WAVE;
			case "tax_inspector" -> WRITE;
			default -> TALK;
		});
	}

	/** Клиент: идёт жест — голову ведёт анимация, а не взгляд. */
	public boolean isGesturing() {
		return actionController != null && actionController.isPlayingTriggeredAnimation();
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
