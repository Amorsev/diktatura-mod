package com.diktatura.anim;

import net.minecraft.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Анимации гуманоидов-противников на GeckoLib (вождь-босс, двойник комендантского часа) — из общего файла
 * НПС {@code animations/entity/npc.animation.json}: покой, ходьба, присед (стоя и на ходу) и удар.
 */
public final class HumanoidAnims {
	public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.npc.idle");
	public static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.npc.walk");
	public static final RawAnimation SNEAK = RawAnimation.begin().thenLoop("animation.npc.sneak");
	public static final RawAnimation SNEAK_WALK = RawAnimation.begin().thenLoop("animation.npc.sneak_walk");
	public static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.npc.attack");

	private HumanoidAnims() {
	}

	/** Контроллеры «move» (покой/ходьба/присед) и «action» (удар по взмаху руки). */
	public static <T extends LivingEntity & GeoEntity> void register(T e, AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(e, "move", 5, state -> {
			boolean moving = state.isMoving();
			if (e.isInSneakingPose()) {
				return state.setAndContinue(moving ? SNEAK_WALK : SNEAK);
			}
			return state.setAndContinue(moving ? WALK : IDLE);
		}));
		controllers.add(new AnimationController<>(e, "action", 2, state -> PlayState.STOP)
			.triggerableAnim("attack", ATTACK));
	}

	/**
	 * Вызывать из {@code tick()}: на клиенте начало взмаха руки (сервер присылает его при ударе) запускает анимацию удара.
	 * Возвращает новое значение «рука уже машет» — его нужно хранить в сущности.
	 */
	public static <T extends LivingEntity & GeoEntity> boolean tickSwing(T e, boolean wasSwinging) {
		if (e.getWorld().isClient && e.handSwinging && !wasSwinging) {
			e.triggerAnim("action", "attack");
		}
		return e.handSwinging;
	}
}
