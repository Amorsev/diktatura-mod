package com.diktatura.npc;

import com.diktatura.Diktatura;
import com.diktatura.network.UiPayloads;
import com.diktatura.ui.Voice;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.LinkedHashMap;
import java.util.List;
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

		/** Игрок только что кликнул по НПС (до показа окна): например, сказать реплику. */
		default void onOpen(ServerPlayerEntity player, Entity npc) {
		}

		/** Содержимое окна для этого игрока прямо сейчас. */
		UiPayloads.NpcScreen screen(ServerPlayerEntity player, Entity npc);

		/** Нажата кнопка. Вернуть true, чтобы переслать игроку обновлённое окно. */
		boolean action(ServerPlayerEntity player, Entity npc, String action);

		/** Что делать, если у клиента нет мода и окно показать нельзя. */
		default void fallback(ServerPlayerEntity player, Entity npc) {
		}
	}

	private static final Map<String, Handler> HANDLERS = new LinkedHashMap<>();
	/** Ключ окна для НПС без своей логики: всё берётся из JSON. */
	public static final String GENERIC = "npc";

	private NpcInteractions() {
	}

	/** ПКМ по любому НПС открывает его окно. Вызывать после регистрации всех ведомств. */
	public static void registerEvents() {
		HANDLERS.put(GENERIC, new GenericHandler());
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(entity instanceof NpcEntity)) {
				return ActionResult.PASS;
			}
			if (!world.isClient && hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity sp) {
				String key = keyOf(entity);
				if (key != null) {
					HANDLERS.get(key).onOpen(sp, entity);
					open(sp, entity);
				}
			}
			return ActionResult.SUCCESS;
		});
	}

	public static void register(String key, Handler handler) {
		if (GENERIC.equals(key)) {
			throw new IllegalArgumentException("Ключ «npc» занят общим окном");
		}
		HANDLERS.put(key, handler);
		// Общее окно всегда проверяется последним.
		Handler generic = HANDLERS.remove(GENERIC);
		if (generic != null) {
			HANDLERS.put(GENERIC, generic);
		}
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

	/** Окно НПС, у которого нет своей логики: имя, ведомство, реплика из JSON. */
	private static final class GenericHandler implements Handler {
		private final Map<java.util.UUID, Integer> lastPhrase = new java.util.HashMap<>();

		@Override
		public boolean matches(Entity entity) {
			return entity instanceof NpcEntity;
		}

		private static NpcRegistry.NpcData data(Entity npc) {
			return npc instanceof NpcEntity n ? NpcRegistry.get(n.npcId()) : null;
		}

		@Override
		public void onOpen(ServerPlayerEntity player, Entity npc) {
			NpcRegistry.NpcData d = data(npc);
			if (d == null) {
				return;
			}
			int i = player.getRandom().nextInt(d.phraseCount());
			lastPhrase.put(player.getUuid(), i);
			Voice.say(npc, d.displayName(), d.phrase(i), d.soundEvent(), d.pitch());
		}

		@Override
		public UiPayloads.NpcScreen screen(ServerPlayerEntity player, Entity npc) {
			NpcRegistry.NpcData d = data(npc);
			Text name = d != null ? d.displayName() : npc.getName();
			Text role = d != null ? Text.translatable(d.role()) : Text.translatable("diktatura.npc.unknown.role");
			Text line = d != null ? d.phrase(lastPhrase.getOrDefault(player.getUuid(), 0))
				: Text.translatable("diktatura.npc.unknown.say");
			List<UiPayloads.NpcAction> actions = List.of(
				new UiPayloads.NpcAction("talk", Text.translatable("diktatura.npc.talk"), d != null),
				new UiPayloads.NpcAction("client:close", Text.translatable("diktatura.npc.leave"), true));
			return new UiPayloads.NpcScreen(GENERIC, npc.getId(), name, role, line,
				List.of(Text.translatable("diktatura.npc.generic.info")), actions, d != null ? d.accent() : 0xFF808088);
		}

		@Override
		public boolean action(ServerPlayerEntity player, Entity npc, String action) {
			if ("talk".equals(action)) {
				onOpen(player, npc);
				return true;
			}
			return false;
		}

		@Override
		public void fallback(ServerPlayerEntity player, Entity npc) {
			NpcRegistry.NpcData d = data(npc);
			if (d != null) {
				player.sendMessage(Text.empty().append(d.displayName()).append(": ")
					.append(d.phrase(player.getRandom().nextInt(d.phraseCount()))), false);
			}
		}
	}
}
