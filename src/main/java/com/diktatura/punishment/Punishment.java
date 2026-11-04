package com.diktatura.punishment;

import com.diktatura.law.Law;
import com.diktatura.law.LawContext;
import net.minecraft.server.network.ServerPlayerEntity;

/** Одно наказание из списка "punishments" закона. Реализации — в {@link Punishments}. */
public interface Punishment {
	void apply(ServerPlayerEntity player, Law law, LawContext ctx);

	/** Насколько наказание пугает (прибавка к шкале страха). */
	int fear();

	default boolean isArrest() {
		return false;
	}
}
