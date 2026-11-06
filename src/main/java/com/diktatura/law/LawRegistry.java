package com.diktatura.law;

import com.diktatura.Diktatura;
import com.diktatura.punishment.Punishment;
import com.diktatura.punishment.Punishments;
import com.diktatura.wealth.ItemValues;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * LawRegistry: законы читаются из datapack-ов по пути data/&lt;ns&gt;/diktatura/laws/*.json.
 * /reload перечитывает их без перезапуска и пересборки.
 */
public final class LawRegistry {
	public static final String FOLDER = "diktatura/laws";
	private static Map<String, Law> laws = Collections.emptyMap();

	private LawRegistry() {
	}

	public static Collection<Law> all() {
		return laws.values();
	}

	public static Law get(String id) {
		return laws.get(id);
	}

	public static Set<String> ids() {
		return laws.keySet();
	}

	static Law parse(Identifier id, JsonObject o) {
		LawCheck check = Checks.parse(o.getAsJsonObject("check"));
		List<Punishment> punishments = new ArrayList<>();
		if (o.has("punishments")) {
			for (JsonElement e : o.getAsJsonArray("punishments")) {
				punishments.add(Punishments.parse(e.getAsJsonObject()));
			}
		}
		return new Law(id, check, List.copyOf(punishments),
			Json.i(o, "cooldown_seconds", 10) * 20,
			Json.b(o, "prevent", false),
			Json.b(o, "enabled_by_default", false),
			Json.b(o, "permanent", false),
			Math.max(0, Json.i(o, "weight", 10)));
	}

	public static final class ReloadListener implements SimpleSynchronousResourceReloadListener {
		@Override
		public Identifier getFabricId() {
			return Diktatura.id("laws");
		}

		@Override
		public void reload(ResourceManager manager) {
			Map<String, Law> loaded = new LinkedHashMap<>();
			Map<Identifier, Resource> found = new TreeMap<>(
				manager.findResources(FOLDER, id -> id.getPath().endsWith(".json")));
			for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
				Identifier file = entry.getKey();
				String path = file.getPath();
				String name = path.substring(FOLDER.length() + 1, path.length() - ".json".length());
				Identifier lawId = Identifier.of(file.getNamespace(), name);
				try (Reader r = entry.getValue().getReader()) {
					JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
					loaded.put(lawId.toString(), parse(lawId, o));
				} catch (Exception e) {
					Diktatura.LOGGER.error("Ошибка в законе {}: {}", file, e.getMessage());
				}
			}
			laws = Collections.unmodifiableMap(loaded);
			Diktatura.LOGGER.info("Загружено законов: {}", laws.size());
			com.diktatura.network.Network.lawsChanged();
			ItemValues.reload(manager);
		}
	}
}
