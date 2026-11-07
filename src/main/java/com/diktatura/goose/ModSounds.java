package com.diktatura.goose;

import com.diktatura.Diktatura;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;

/** Звуки мода (CC0 с freesound и синтезированные в моде-референсе, авторы в CREDITS.md). */
public final class ModSounds {
	public static final SoundEvent GOOSE_HONK = register("entity.goose.honk");
	public static final SoundEvent GOOSE_HISS = register("entity.goose.hiss");
	public static final SoundEvent SNIPER_SHOT = register("sfx.sniper_shot");
	public static final SoundEvent LASER_CHARGE = register("sfx.laser_charge");
	public static final SoundEvent CAPTCHA_OK = register("sfx.captcha_ok");
	public static final SoundEvent CAPTCHA_FAIL = register("sfx.captcha_fail");
	public static final SoundEvent GLITCH = register("sfx.glitch_long");
	public static final SoundEvent BOSS_BOOM = register("sfx.boss_boom");
	public static final SoundEvent SHOCKWAVE = register("sfx.shockwave");
	public static final SoundEvent DRAGON_INHALE = register("sfx.dragon_inhale");
	public static final SoundEvent DRAGON_PHASE = register("sfx.dragon_phase");
	public static final SoundEvent METEOR_FALL = register("sfx.meteor_fall");
	public static final SoundEvent CRYSTAL_RISE = register("sfx.crystal_rise");
	public static final SoundEvent VORTEX = register("sfx.vortex");
	public static final SoundEvent TINY_STEPS = register("sfx.tiny_steps");
	public static final SoundEvent[] FOOD_SCREAMS = {register("voice.food1"), register("voice.food2"), register("voice.food3"),
		register("voice.food4"), register("voice.food5")};
	public static final SoundEvent[] ORE_SCREAMS = {register("voice.ore1"), register("voice.ore2"), register("voice.ore3"),
		register("voice.ore4")};

	private ModSounds() {
	}

	private static SoundEvent register(String path) {
		return Registry.register(Registries.SOUND_EVENT, Diktatura.id(path), SoundEvent.of(Diktatura.id(path)));
	}

	public static void register() {
		// Поля инициализируются при загрузке класса.
	}
}
