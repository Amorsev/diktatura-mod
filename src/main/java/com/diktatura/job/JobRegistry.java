package com.diktatura.job;

import com.diktatura.Diktatura;
import com.diktatura.law.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Работы с трудовой биржи: data/&lt;namespace&gt;/diktatura/jobs/&lt;id&gt;.json. {@code /reload} перечитывает.
 * Срок, награда и штраф лежат в JSON, чтобы баланс правился без пересборки.
 */
public final class JobRegistry {
	public static final String FOLDER = "diktatura/jobs";

	public enum Type { COURIER, CLEANER, MINER, LUMBERJACK, BUILDER;
		static Type parse(String s) {
			return valueOf(s.toUpperCase(Locale.ROOT));
		}

		/** Работа считается по сломанным блокам. */
		public boolean countsBlocks() {
			return this == CLEANER || this == MINER || this == LUMBERJACK;
		}
	}

	/** Вариант задания: что именно ломать и сколько. reward = -1 — брать награду работы. */
	public record Variant(String label, int goal, int reward, Predicate<BlockState> blocks) {
	}

	/** Чертёж для строителя: смещения от угла и нужный блок в каждой точке. */
	public record Blueprint(String label, List<BlockPos> offsets, List<Block> blocks) {
		public int size() {
			return offsets.size();
		}
	}

	/**
	 * @param minutes      срок на выполнение
	 * @param reward       награда за выполнение
	 * @param unitReward   плата за каждую единицу (платится и при опоздании, без штрафа)
	 * @param lateFine     штраф за опоздание
	 * @param minAccuracy  строитель: минимальная точность в процентах, чтобы сдать
	 */
	public record JobDef(String id, Type type, String name, int order, int minutes, int reward, int unitReward,
	                     int lateFine, int minDistance, int maxDistance, int minAccuracy,
	                     List<Variant> variants, Blueprint blueprint) {

		public int rewardFor(int variant) {
			if (variant >= 0 && variant < variants.size() && variants.get(variant).reward() >= 0) {
				return variants.get(variant).reward();
			}
			return reward;
		}

		public int goalFor(int variant) {
			return switch (type) {
				case COURIER -> 1;
				case BUILDER -> blueprint == null ? 1 : blueprint.size();
				default -> variant >= 0 && variant < variants.size() ? variants.get(variant).goal() : 1;
			};
		}

		/** Ключ перевода «что делаем» для заголовка: вариант, чертёж или пусто. */
		public String labelFor(int variant) {
			if (type == Type.BUILDER && blueprint != null) {
				return blueprint.label();
			}
			if (variant >= 0 && variant < variants.size()) {
				return variants.get(variant).label();
			}
			return "";
		}
	}

	private static volatile Map<String, JobDef> jobs = Map.of();

	private JobRegistry() {
	}

	public static JobDef get(String id) {
		return id == null ? null : jobs.get(id);
	}

	/** Все работы в порядке поля {@code order}. */
	public static Collection<JobDef> all() {
		return jobs.values();
	}

	/** Список блоков и тегов: "minecraft:oak_log", "#minecraft:logs". */
	static Predicate<BlockState> blocks(JsonArray arr) {
		Set<Block> set = new HashSet<>();
		List<TagKey<Block>> tags = new ArrayList<>();
		for (JsonElement el : arr) {
			String s = el.getAsString();
			if (s.startsWith("#")) {
				Identifier id = Identifier.tryParse(s.substring(1));
				if (id != null) {
					tags.add(TagKey.of(RegistryKeys.BLOCK, id));
				}
			} else {
				Identifier id = Identifier.tryParse(s);
				if (id != null) {
					Registries.BLOCK.getOrEmpty(id).ifPresentOrElse(set::add,
						() -> Diktatura.LOGGER.warn("Неизвестный блок в работе: {}", s));
				}
			}
		}
		return st -> {
			if (set.contains(st.getBlock())) {
				return true;
			}
			for (TagKey<Block> t : tags) {
				if (st.isIn(t)) {
					return true;
				}
			}
			return false;
		};
	}

	private static Blueprint blueprint(JsonObject o) {
		JsonObject palette = o.getAsJsonObject("palette");
		JsonArray layers = o.getAsJsonArray("layers");
		List<BlockPos> offsets = new ArrayList<>();
		List<Block> blocks = new ArrayList<>();
		for (int y = 0; y < layers.size(); y++) {
			JsonArray rows = layers.get(y).getAsJsonArray();
			for (int z = 0; z < rows.size(); z++) {
				String row = rows.get(z).getAsString();
				for (int x = 0; x < row.length(); x++) {
					String key = String.valueOf(row.charAt(x));
					if (!palette.has(key)) {
						continue;
					}
					Identifier id = Identifier.tryParse(palette.get(key).getAsString());
					Block b = id == null ? null : Registries.BLOCK.getOrEmpty(id).orElse(null);
					if (b == null) {
						Diktatura.LOGGER.warn("Неизвестный блок в чертеже: {}", palette.get(key));
						continue;
					}
					offsets.add(new BlockPos(x, y, z));
					blocks.add(b);
				}
			}
		}
		return new Blueprint(Json.s(o, "label", "diktatura.job.builder"), List.copyOf(offsets), List.copyOf(blocks));
	}

	private static JobDef parse(Identifier id, JsonObject o) {
		Type type = Type.parse(Json.s(o, "type", "cleaner"));
		List<Variant> variants = new ArrayList<>();
		if (o.has("variants")) {
			for (JsonElement el : o.getAsJsonArray("variants")) {
				JsonObject v = el.getAsJsonObject();
				variants.add(new Variant(Json.s(v, "label", ""), Math.max(1, Json.i(v, "goal", 1)),
					Json.i(v, "reward", -1), blocks(v.has("blocks") ? v.getAsJsonArray("blocks") : new JsonArray())));
			}
		}
		Blueprint bp = o.has("blueprint") ? blueprint(o.getAsJsonObject("blueprint")) : null;
		if (type.countsBlocks() && variants.isEmpty()) {
			throw new IllegalArgumentException("нужен хотя бы один вариант (variants)");
		}
		if (type == Type.BUILDER && (bp == null || bp.size() == 0)) {
			throw new IllegalArgumentException("строителю нужен чертёж (blueprint)");
		}
		return new JobDef(id.toString(), type, Json.s(o, "name", "diktatura.job." + id.getPath()),
			Json.i(o, "order", 100), Math.max(1, Json.i(o, "minutes", 5)), Math.max(0, Json.i(o, "reward", 10)),
			Math.max(0, Json.i(o, "unit_reward", 0)), Math.max(0, Json.i(o, "late_fine", 0)),
			Math.max(16, Json.i(o, "min_distance", 80)), Math.max(32, Json.i(o, "max_distance", 180)),
			Math.max(1, Math.min(100, Json.i(o, "min_accuracy", 50))), List.copyOf(variants), bp);
	}

	public static final class ReloadListener implements SimpleSynchronousResourceReloadListener {
		@Override
		public Identifier getFabricId() {
			return Diktatura.id("jobs");
		}

		@Override
		public void reload(ResourceManager manager) {
			List<JobDef> loaded = new ArrayList<>();
			Map<Identifier, Resource> found = new TreeMap<>(
				manager.findResources(FOLDER, id -> id.getPath().endsWith(".json")));
			for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
				Identifier file = entry.getKey();
				String p = file.getPath();
				Identifier jobId = Identifier.of(file.getNamespace(),
					p.substring(FOLDER.length() + 1, p.length() - ".json".length()));
				try (Reader r = entry.getValue().getReader()) {
					loaded.add(parse(jobId, JsonParser.parseReader(r).getAsJsonObject()));
				} catch (Exception e) {
					Diktatura.LOGGER.error("Ошибка в работе {}: {}", file, e.getMessage());
				}
			}
			loaded.sort(Comparator.comparingInt(JobDef::order).thenComparing(JobDef::id));
			Map<String, JobDef> map = new LinkedHashMap<>();
			loaded.forEach(j -> map.put(j.id(), j));
			jobs = map;
			Diktatura.LOGGER.info("Загружено работ: {}", jobs.size());
		}
	}
}
