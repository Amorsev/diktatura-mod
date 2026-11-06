package com.diktatura.economy;

import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.ui.Hud;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * Трудодни — виртуальная валюта. Хранится в {@link PlayerRecord} (мировое состояние),
 * не теряется при смерти и не изымается конфискацией: отнять её может только закон или налоговик.
 * Каждая операция пишется в короткую историю (последние {@link #LOG_SIZE}).
 */
public final class Economy {
	public static final int LOG_SIZE = 20;

	private Economy() {
	}

	public static int balance(ServerPlayerEntity p) {
		return record(p).trudodni;
	}

	private static PlayerRecord record(ServerPlayerEntity p) {
		return DiktaturaState.get(p.getServer()).player(p);
	}

	/**
	 * Начислить (amount &gt; 0) или списать (amount &lt; 0). Баланс может уйти в минус только
	 * через {@code allowDebt} — штрафы государство взыскивает в долг.
	 *
	 * @param reasonKey ключ перевода причины (с одним аргументом {@code arg}, может быть пустым)
	 */
	public static void add(ServerPlayerEntity p, int amount, String reasonKey, String arg, boolean notify) {
		if (amount == 0) {
			return;
		}
		MinecraftServer server = p.getServer();
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(p);
		rec.trudodni += amount;
		NbtCompound e = new NbtCompound();
		e.putInt("a", amount);
		e.putString("r", reasonKey);
		e.putString("x", arg == null ? "" : arg);
		e.putLong("t", System.currentTimeMillis());
		rec.moneyLog.add(0, e);
		while (rec.moneyLog.size() > LOG_SIZE) {
			rec.moneyLog.remove(rec.moneyLog.size() - 1);
		}
		state.markDirty();
		if (notify) {
			Text title = Text.translatable(amount > 0 ? "diktatura.money.plus" : "diktatura.money.minus", Math.abs(amount));
			Hud.alert(p, amount > 0 ? AlertKind.GOOD : AlertKind.DANGER, title, reason(reasonKey, arg), 5);
		}
	}

	/** Списать, если хватает. Возвращает false и ничего не делает, если денег мало. */
	public static boolean take(ServerPlayerEntity p, int amount, String reasonKey, String arg) {
		if (amount <= 0) {
			return true;
		}
		if (balance(p) < amount) {
			return false;
		}
		add(p, -amount, reasonKey, arg, true);
		return true;
	}

	/** Установить баланс (команда оператора). */
	public static void set(ServerPlayerEntity p, int value) {
		add(p, value - balance(p), "diktatura.money.reason.admin", "", true);
	}

	public static MutableText reason(String key, String arg) {
		return arg == null || arg.isEmpty() ? Text.translatable(key) : Text.translatable(key, Text.translatable(arg));
	}
}
