package com.diktatura.event;

import java.util.Locale;

/** Случайные события. Одновременно может идти только одно. */
public enum EventType {
	/** Мгновенно: освобождение всех арестованных. */
	AMNESTY,
	/** Мгновенно: каждому подарок от вождя. */
	GIFTS,
	/** Мгновенно: изъятие одной группы предметов (золото, факелы, сладости...). */
	CONFISCATION,
	/** 30 секунд: половина экрана (левая или правая) у каждого игрока чёрная. */
	BLIND_EYE,
	/** Импульсы: гравитация отключается на 5 секунд, затем возвращается, и так несколько раз. */
	NO_GRAVITY;

	public static EventType byOrdinal(int i) {
		EventType[] v = values();
		return i >= 0 && i < v.length ? v[i] : null;
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}
}
