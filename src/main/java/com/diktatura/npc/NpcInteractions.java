package com.diktatura.npc;

import com.diktatura.Diktatura;
import com.diktatura.network.UiPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Общее окно для всех НПС-ведомств. Каждое ведомство регистрирует обработчик:
 * как узнать своего НПС, что показать в окне и что делать по кнопкам.
 * Логика целиком на сервере: клиент присылает только «нажата кнопка X у сущности Y».
 */
public final class NpcInteractions {
	/** Максимальная дистанция до НПС для нажатия кнопок (квадрат). */
	private static final double MAX_DIST_SQ = 8.0 * 8.0;

	public interface Handler {
		/** Наш ли это НПС. */
		boolean matches(Entity entity);

		/** Содержимое окна для этого игрока прямо сейчас. */
		UiPayloads.NpcScreen screen(ServerPlayerEntity player, Entity npc);

		/** Нажата кнопка. Вернуть true, чтобы переслать игроку обновлённое окно. */
		boolean action(ServerPlayerEntity player, Entity npc, String action);

		/** Что делать, если у клиента нет мода и окно показать нельзя. */
		default void fallback(ServerPlayerEntity player, Entity npc) {
		}
	}

	private static final Map<String, Handler> HANDLERS = new LinkedHashMap<>();

	private NpcInteractions() {
	}

	public static void register(String key, Handler handler) {
		HANDLERS.put(key, handler);
	}

	public static String keyOf(Entity entity) {
		for (Map.Entry<String, Handler> e : HANDLERS.entrySet()) {
			if (e.getValue().matches(entity)) {
				return e.getKey();
			}
		}
		return null;
	}

	/** Открыть окно НПС игроку. Возвращает false, если сущность — не наш НПС. */
	public static boolean open(ServerPlayerEntity player, Entity npc) {
		String key = keyOf(npc);
		if (key == null) {
			return false;
		}
		Handler h = HANDLERS.get(key);
		if (ServerPlayNetworking.canSend(player, UiPayloads.NpcScreen.ID)) {
			ServerPlayNetworking.send(player, h.screen(player, npc));
		} else {
			h.fallback(player, npc);
		}
		return true;
	}

	/** Обработка нажатия кнопки от клиента. Все данные клиента проверяются. */
	public static void onClick(ServerPlayerEntity player, UiPayloads.NpcClick click) {
		Handler h = HANDLERS.get(click.npc());
		if (h == null || click.action().startsWith("client:")) {
			return;
		}
		Entity npc = player.getServerWorld().getEntityById(click.entityId());
		if (npc == null || npc.isRemoved() || !h.matches(npc) || player.squaredDistanceTo(npc) > MAX_DIST_SQ) {
			return;
		}
		try {
			if (h.action(player, npc, click.action())) {
				ServerPlayNetworking.send(player, h.screen(player, npc));
			}
		} catch (RuntimeException e) {
			Diktatura.LOGGER.error("Ошибка в окне НПС {} (действие {})", click.npc(), click.action(), e);
		}
	}
}
