package com.diktatura.goose;

import com.diktatura.Diktatura;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;

/** Звуки мода (гусиные — CC0 с freesound, авторы в CREDITS.md). */
public final class ModSounds {
	public static final SoundEvent GOOSE_HONK = register("entity.goose.honk");
	public static final SoundEvent GOOSE_HISS = register("entity.goose.hiss");

	private ModSounds() {
	}

	private static SoundEvent register(String path) {
		return Registry.register(Registries.SOUND_EVENT, Diktatura.id(path), SoundEvent.of(Diktatura.id(path)));
	}

	public static void register() {
		// Поля инициализируются при загрузке класса.
	}
}
