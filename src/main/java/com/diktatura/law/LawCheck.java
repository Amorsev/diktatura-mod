package com.diktatura.law;

import net.minecraft.item.ItemStack;

/** Условие нарушения закона. Реализации — в {@link Checks}. */
public interface LawCheck {
	LawEvent event();

	/** true — закон нарушен. */
	boolean test(LawContext ctx);

	/** Является ли предмет «уликой» (для конфискации запрещённого). */
	default boolean isOffending(ItemStack stack) {
		return false;
	}

	/** Проверка срабатывает и при подборе предмета, а не только по таймеру. */
	default boolean checksOnPickup() {
		return false;
	}
}
