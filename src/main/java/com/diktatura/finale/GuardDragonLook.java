package com.diktatura.finale;

import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/**
 * Облик дракона госохраны: он вдвое больше ванильного. Узнаём его по имени фазы
 * ({@code diktatura.dragon.nameN}), которое сервер ставит в {@link GuardDragon}: имя приходит
 * и на клиент, так что модель и хитбоксы части тела меняются одинаково на обеих сторонах.
 */
public final class GuardDragonLook {
	/** Во сколько раз дракон госохраны больше обычного (и модель, и хитбоксы частей). */
	public static final float SCALE = 2.0f;
	private static final String KEY = "diktatura.dragon.name";

	private GuardDragonLook() {
	}

	/** Фаза боя 1–3, или 0 — обычный дракон. */
	public static int phase(EnderDragonEntity d) {
		if (d == null) {
			return 0;
		}
		Text name = d.getCustomName();
		if (name != null && name.getContent() instanceof TranslatableTextContent t && t.getKey().startsWith(KEY)) {
			String n = t.getKey().substring(KEY.length());
			return n.equals("3") ? 3 : n.equals("2") ? 2 : 1;
		}
		return 0;
	}

	public static boolean isGuard(EnderDragonEntity d) {
		return phase(d) > 0;
	}

	public static float scale(EnderDragonEntity d) {
		return isGuard(d) ? SCALE : 1f;
	}
}
