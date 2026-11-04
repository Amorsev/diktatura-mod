package com.diktatura.minigame;

import java.util.Locale;

/** Виды «отработки», которую арестованный проходит до начала срока. */
public enum MinigameType {
	/** Допрыгать до финиша. Упал — назад к старту или контрольной точке. */
	PARKOUR,
	/** Добыть 64 камня. Камень сразу восстанавливается, ничего не выпадает. */
	QUARRY,
	/** Найти выход из лабиринта. */
	MAZE;

	public static MinigameType byOrdinal(int i) {
		MinigameType[] v = values();
		return i >= 0 && i < v.length ? v[i] : null;
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}
}
