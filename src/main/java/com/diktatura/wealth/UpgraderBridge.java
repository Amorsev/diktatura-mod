package com.diktatura.wealth;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Интеграция с модом Upgrader (Modrinth: upgrade-items). Публичного API у него нет,
 * а лицензия ARR не позволяет копировать код, поэтому:
 * <ol>
 *     <li>если в конфиге задан класс и метод — вызываем его рефлексией;</li>
 *     <li>иначе ищем в jar Upgrader класс с «value/price/worth» в имени и методом
 *     (ItemStack или Item) → число;</li>
 *     <li>дополнительно читаем его конфиг (valueOverrides, globalValueMultiplier,
 *     valueMultipliers, blacklist) и применяем к своей таблице.</li>
 * </ol>
 * Ничего не нашлось — работает своя таблица ценностей.
 */
public final class UpgraderBridge {
	private static String modId;
	private static Method method;
	private static Object instance;
	private static boolean itemParam;
	private static int errors;

	private UpgraderBridge() {
	}

	public static String status() {
		if (modId == null) {
			return "не установлен";
		}
		if (method == null) {
			return modId + " найден, метод ценности не найден (используется своя таблица и его конфиг)";
		}
		return modId + " → " + method.getDeclaringClass().getName() + "#" + method.getName();
	}

	public static void init() {
		if (!DiktaturaConfig.get().useUpgrader) {
			return;
		}
		ModContainer container = null;
		for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
			String id = m.getMetadata().getId().toLowerCase(Locale.ROOT);
			if (id.contains("upgrad") && !id.equals(Diktatura.MOD_ID)) {
				container = m;
				break;
			}
		}
		if (container == null) {
			Diktatura.LOGGER.info("Upgrader не установлен, используется своя таблица ценностей");
			return;
		}
		modId = container.getMetadata().getId();
		DiktaturaConfig cfg = DiktaturaConfig.get();
		try {
			if (!cfg.upgraderValueClass.isEmpty()) {
				Class<?> c = Class.forName(cfg.upgraderValueClass, true, UpgraderBridge.class.getClassLoader());
				inspect(c, cfg.upgraderValueMethod);
			} else {
				scan(container);
			}
		} catch (Throwable t) {
			Diktatura.LOGGER.warn("Не удалось подключиться к Upgrader: {}", t.toString());
		}
		Diktatura.LOGGER.info("Upgrader: {}", status());
	}

	private static void scan(ModContainer container) {
		for (Path root : container.getRootPaths()) {
			try (Stream<Path> files = Files.walk(root)) {
				List<String> names = files
					.map(p -> root.relativize(p).toString().replace('\\', '/'))
					.filter(n -> n.endsWith(".class") && !n.toLowerCase(Locale.ROOT).contains("mixin"))
					.filter(n -> {
						String simple = n.substring(n.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
						return simple.contains("value") || simple.contains("price") || simple.contains("worth");
					})
					.toList();
				for (String n : names) {
					String cls = n.substring(0, n.length() - ".class".length()).replace('/', '.');
					try {
						Class<?> c = Class.forName(cls, false, UpgraderBridge.class.getClassLoader());
						if (inspect(c, "")) {
							return;
						}
					} catch (Throwable ignored) {
					}
				}
			} catch (Exception e) {
				Diktatura.LOGGER.debug("Не удалось просмотреть {}", root, e);
			}
		}
	}

	private static boolean isNumber(Class<?> t) {
		return t == double.class || t == float.class || t == int.class || t == long.class || Number.class.isAssignableFrom(t);
	}

	private static boolean inspect(Class<?> c, String wanted) throws Exception {
		Method best = null;
		for (Method m : c.getDeclaredMethods()) {
			Class<?>[] params = m.getParameterTypes();
			if (params.length != 1 || !isNumber(m.getReturnType())) {
				continue;
			}
			if (params[0] != ItemStack.class && params[0] != Item.class) {
				continue;
			}
			if (!wanted.isEmpty() && !m.getName().equals(wanted)) {
				continue;
			}
			if (best == null || m.getName().toLowerCase(Locale.ROOT).contains("value")) {
				best = m;
			}
		}
		if (best == null) {
			return false;
		}
		Object inst = null;
		if (!Modifier.isStatic(best.getModifiers())) {
			inst = singleton(c);
			if (inst == null) {
				return false;
			}
		}
		best.setAccessible(true);
		method = best;
		instance = inst;
		itemParam = best.getParameterTypes()[0] == Item.class;
		return true;
	}

	private static Object singleton(Class<?> c) throws Exception {
		for (Field f : c.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers()) && f.getType() == c) {
				f.setAccessible(true);
				Object v = f.get(null);
				if (v != null) {
					return v;
				}
			}
		}
		for (Method m : c.getDeclaredMethods()) {
			if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType() == c) {
				m.setAccessible(true);
				return m.invoke(null);
			}
		}
		return null;
	}

	/** Ценность одного предмета по Upgrader, или -1, если недоступно. */
	public static double value(ItemStack stack) {
		if (method == null) {
			return -1;
		}
		try {
			Object r = method.invoke(instance, itemParam ? stack.getItem() : stack.copyWithCount(1));
			return r instanceof Number n ? n.doubleValue() : -1;
		} catch (Throwable t) {
			if (++errors > 20) {
				Diktatura.LOGGER.warn("Upgrader постоянно падает при оценке предметов, отключаю: {}", t.toString());
				method = null;
			}
			return -1;
		}
	}

	/** Найти конфиг Upgrader в папке config и перенести его настройки ценности. */
	static void applyConfig() {
		if (modId == null) {
			return;
		}
		Path dir = FabricLoader.getInstance().getConfigDir();
		try (Stream<Path> files = Files.walk(dir, 2)) {
			for (Path p : files.toList()) {
				String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
				if (!name.contains("upgrad") || !(name.endsWith(".json") || name.endsWith(".json5"))) {
					continue;
				}
				try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
					JsonReader jr = new JsonReader(r);
					jr.setLenient(true);
					JsonElement root = JsonParser.parseReader(jr);
					apply(root);
					Diktatura.LOGGER.info("Прочитан конфиг Upgrader: {}", p);
				} catch (Exception e) {
					Diktatura.LOGGER.warn("Не удалось прочитать конфиг Upgrader {}: {}", p, e.getMessage());
				}
			}
		} catch (Exception ignored) {
		}
	}

	private static void apply(JsonElement root) {
		JsonElement e;
		if ((e = find(root, "valueOverrides")) != null && e.isJsonObject()) {
			for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
				if (en.getValue().isJsonPrimitive()) {
					ItemValues.override(en.getKey(), en.getValue().getAsDouble());
				}
			}
		}
		if ((e = find(root, "valueMultipliers")) != null && e.isJsonObject()) {
			for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
				if (en.getValue().isJsonPrimitive()) {
					ItemValues.multiplier(en.getKey(), en.getValue().getAsDouble());
				}
			}
		}
		if ((e = find(root, "globalValueMultiplier")) != null && e.isJsonPrimitive()) {
			ItemValues.globalMultiplier(e.getAsDouble());
		}
		if ((e = find(root, "blacklist")) != null && e.isJsonArray()) {
			e.getAsJsonArray().forEach(x -> {
				if (x.isJsonPrimitive()) {
					ItemValues.blacklist(x.getAsString());
				}
			});
		}
	}

	private static JsonElement find(JsonElement e, String key) {
		if (e == null || !e.isJsonObject()) {
			return null;
		}
		JsonObject o = e.getAsJsonObject();
		if (o.has(key)) {
			return o.get(key);
		}
		for (Map.Entry<String, JsonElement> en : o.entrySet()) {
			JsonElement r = find(en.getValue(), key);
			if (r != null) {
				return r;
			}
		}
		return null;
	}
}
