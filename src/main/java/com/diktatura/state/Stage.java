package com.diktatura.state;

/** Где сейчас игрок с точки зрения правосудия. */
public enum Stage {
	FREE,
	TO_PRISON,
	IMPRISONED,
	RETURNING,
	/** Отработка (мини-игра) перед сроком. Добавлено в конец, чтобы старые сохранения читались. */
	MINIGAME;

	/** Арестован: едет в тюрьму, отрабатывает или сидит. */
	public boolean isArrested() {
		return this == TO_PRISON || this == MINIGAME || this == IMPRISONED;
	}

	public static Stage byOrdinal(int i) {
		Stage[] v = values();
		return i >= 0 && i < v.length ? v[i] : FREE;
	}
}
