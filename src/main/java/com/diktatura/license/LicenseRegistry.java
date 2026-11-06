package com.diktatura.license;

import com.diktatura.Diktatura;
import com.diktatura.law.Json;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Лицензии: data/&lt;namespace&gt;/diktatura/licenses/&lt;id&gt;.json. Какие законы лицензия снимает,
 * задаёт сам закон полем {@code exempt_license}. Цены и сроки — в JSON, {@code /reload} перечитывает.
 */
public final class LicenseRegistry {
	public static final String FOLDER = "diktatura/licenses";
	/** Игровой день в тиках мира. */
	public static final long DAY = 24000L;

	/**
	 * @param days           срок в игровых днях (1 день = 20 минут)
	 * @param revokeOnArrest отзывается при аресте
	 * @param forSale        продаётся ли в лицензионном отделе (false — выдаёт только оператор)
	 */
	public record LicenseDef(String id, String name, String desc, int order, int price, double days,
	                         boolean revokeOnArrest, boolean forSale) {
		public long durationTicks() {
			return Math.max(20L, Math.round(days * DAY));
		}
	}

	private static volatile Map<String, LicenseDef> licenses = Map.of();

	private LicenseRegistry() {
	}

	public static LicenseDef get(String id) {
		return id == null ? null : licenses.get(id);
	}

	public static Collection<LicenseDef> all() {
		return licenses.values();
	}

	private static LicenseDef parse(Identifier id, JsonObject o) {
		String base = "license." + id.getNamespace() + "." + id.getPath();
		return new LicenseDef(id.toString(), Json.s(o, "name", base), Json.s(o, "desc", base + ".desc"),
			Json.i(o, "order", 100), Math.max(0, Json.i(o, "price", 10)), Math.max(0.01, Json.d(o, "days", 1)),
			Json.b(o, "revoke_on_arrest", true), Json.b(o, "for_sale", true));
	}

	public static final class ReloadListener implements SimpleSynchronousResourceReloadListener {
		@Override
		public Identifier getFabricId() {
			return Diktatura.id("licenses");
		}

		@Override
		public void reload(ResourceManager manager) {
			List<LicenseDef> loaded = new ArrayList<>();
			Map<Identifier, Resource> found = new TreeMap<>(
				manager.findResources(FOLDER, id -> id.getPath().endsWith(".json")));
			for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
				Identifier file = entry.getKey();
				String p = file.getPath();
				Identifier licId = Identifier.of(file.getNamespace(),
					p.substring(FOLDER.length() + 1, p.length() - ".json".length()));
				try (Reader r = entry.getValue().getReader()) {
					loaded.add(parse(licId, JsonParser.parseReader(r).getAsJsonObject()));
				} catch (Exception e) {
					Diktatura.LOGGER.error("Ошибка в лицензии {}: {}", file, e.getMessage());
				}
			}
			loaded.sort(Comparator.comparingInt(LicenseDef::order).thenComparing(LicenseDef::id));
			Map<String, LicenseDef> map = new LinkedHashMap<>();
			loaded.forEach(l -> map.put(l.id(), l));
			licenses = map;
			Diktatura.LOGGER.info("Загружено лицензий: {}", licenses.size());
		}
	}
}
