package com.diktatura.state;

/** Где сейчас игрок с точки зрения правосудия. */
public enum Stage {
	FREE,
	TO_PRISON,
	IMPRISONED,
	RETURNING;

	public static Stage byOrdinal(int i) {
		Stage[] v = values();
		return i >= 0 && i < v.length ? v[i] : FREE;
	}
}
