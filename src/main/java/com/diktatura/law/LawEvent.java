package com.diktatura.law;

/** Игровое событие, на которое реагирует проверка закона. */
public enum LawEvent {
	/** Периодическая проверка состояния игрока (взгляд, время суток, инвентарь...). */
	TICK,
	BLOCK_BREAK,
	BLOCK_PLACE,
	CHAT,
	KILL,
	ATTACK,
	USE_ITEM,
	SLEEP,
	/** Ставка в госказино. */
	GAMBLE
}
