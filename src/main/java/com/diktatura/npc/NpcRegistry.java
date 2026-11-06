package com.diktatura.npc;

import com.diktatura.Diktatura;
import com.diktatura.law.Json;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Данные НПС из data/&lt;namespace&gt;/diktatura/npcs/&lt;id&gt;.json. {@code /reload} перечитывает.
 * Формат описан в README («Формат НПС»).
 */
public final class NpcRegistry {
	public static final String FOLDER = "diktatura/npcs";

	/**
	 * @param name        ключ перевода имени
	 * @param role        ключ перевода ведомства
	 * @param accent      цвет полосы ведомства (ARGB)
	 * @param phrasePrefix префикс ключей реплик: prefix + 0..phraseCount-1
	 * @param sound       звук реплики (может быть null)
	 * @param scale       масштаб модели (1.0 — как игрок)
	 * @param heldItem    предмет в руке (может быть null)
	 *                    Текстура всегда assets/&lt;ns&gt;/textures/entity/npc/&lt;id&gt;.png (модель игрока 64×64).
	 */
	public record NpcData(String id, String name, String role, int accent, String phrasePrefix, int phraseCount,
	                      Identifier sound, float pitch, float scale, Identifier heldItem) {

		public Text displayName() {
			return Text.translatable(name);
		}

		public Text phrase(int i) {
			return Text.translatable(phrasePrefix + Math.floorMod(i, Math.max(1, phraseCount)));
		}

		public SoundEvent soundEvent() {
			return sound == null ? null : Registries.SOUND_EVENT.get(sound);
		}
	}

	private static volatile Map<String, NpcData> npcs = Map.of();

	private NpcRegistry() {
	}

	public static NpcData get(String id) {
		return npcs.get(id);
	}

	public static Collection<NpcData> all() {
		return npcs.values();
	}

	/** Применить данные к сущности: имя над головой, масштаб, предмет в руке. */
	public static void apply(NpcEntity npc) {
		NpcData d = get(npc.npcId());
		if (d == null) {
			return;
		}
		npc.setCustomName(d.displayName().copy().formatted(Formatting.GOLD, Formatting.BOLD));
		npc.setCustomNameVisible(true);
		EntityAttributeInstance scale = npc.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
		if (scale != null) {
			scale.setBaseValue(d.scale());
		}
		if (d.heldItem() != null) {
			Item item = Registries.ITEM.get(d.heldItem());
			npc.equipStack(EquipmentSlot.MAINHAND, new ItemStack(item));
			npc.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f);
		}
	}

	private static NpcData parse(Identifier id, JsonObject o) {
		String ns = id.getNamespace();
		String path = id.getPath();
		String accentHex = Json.s(o, "accent", "#808088").replace("#", "");
		int accent = 0xFF000000 | (int) Long.parseLong(accentHex, 16);
		String sound = Json.s(o, "sound", "");
		String held = Json.s(o, "held_item", "");
		return new NpcData(id.toString(),
			Json.s(o, "name", "npc." + ns + "." + path),
			Json.s(o, "role", "npc." + ns + "." + path + ".role"),
			accent,
			Json.s(o, "phrase_prefix", "npc." + ns + "." + path + ".say."),
			Math.max(1, Json.i(o, "phrase_count", 1)),
			sound.isEmpty() ? null : Identifier.tryParse(sound),
			(float) Json.d(o, "pitch", 1.0),
			(float) Math.max(0.3, Math.min(3.0, Json.d(o, "scale", 1.0))),
			held.isEmpty() ? null : Identifier.tryParse(held));
	}

	public static final class ReloadListener implements SimpleSynchronousResourceReloadListener {
		@Override
		public Identifier getFabricId() {
			return Diktatura.id("npcs");
		}

		@Override
		public void reload(ResourceManager manager) {
			Map<String, NpcData> loaded = new LinkedHashMap<>();
			Map<Identifier, Resource> found = new TreeMap<>(
				manager.findResources(FOLDER, id -> id.getPath().endsWith(".json")));
			for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
				Identifier file = entry.getKey();
				String p = file.getPath();
				Identifier npcId = Identifier.of(file.getNamespace(),
					p.substring(FOLDER.length() + 1, p.length() - ".json".length()));
				try (Reader r = entry.getValue().getReader()) {
					JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
					loaded.put(npcId.toString(), parse(npcId, o));
				} catch (Exception e) {
					Diktatura.LOGGER.error("Ошибка в НПС {}: {}", file, e.getMessage());
				}
			}
			npcs = Collections.unmodifiableMap(loaded);
			Diktatura.LOGGER.info("Загружено НПС: {}", npcs.size());
		}
	}
}
