package com.diktatura.law;

import com.diktatura.Diktatura;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Фильтры по предметам, блокам и сущностям из JSON:
 * <pre>"items": ["minecraft:diamond"], "tag": "minecraft:flowers"</pre>
 * (то же для "blocks" и "entities"). Если не задано ничего — подходит всё.
 */
public final class Matchers {
	private Matchers() {
	}

	private static Identifier parse(String raw) {
		String s = raw.startsWith("#") ? raw.substring(1) : raw;
		Identifier id = Identifier.tryParse(s);
		if (id == null) {
			Diktatura.LOGGER.warn("Неверный идентификатор в законе: {}", raw);
		}
		return id;
	}

	public static Predicate<ItemStack> items(JsonObject o) {
		Set<Item> set = new HashSet<>();
		for (String s : Json.list(o, "items")) {
			Identifier id = parse(s);
			if (id != null) {
				Registries.ITEM.getOrEmpty(id).ifPresentOrElse(set::add,
					() -> Diktatura.LOGGER.warn("Неизвестный предмет в законе: {}", s));
			}
		}
		TagKey<Item> tag = null;
		if (o.has("tag")) {
			Identifier id = parse(o.get("tag").getAsString());
			if (id != null) {
				tag = TagKey.of(RegistryKeys.ITEM, id);
			}
		}
		boolean any = set.isEmpty() && tag == null;
		TagKey<Item> t = tag;
		return st -> !st.isEmpty() && (any || set.contains(st.getItem()) || (t != null && st.isIn(t)));
	}

	public static Predicate<BlockState> blocks(JsonObject o) {
		Set<Block> set = new HashSet<>();
		for (String s : Json.list(o, "blocks")) {
			Identifier id = parse(s);
			if (id != null) {
				Registries.BLOCK.getOrEmpty(id).ifPresentOrElse(set::add,
					() -> Diktatura.LOGGER.warn("Неизвестный блок в законе: {}", s));
			}
		}
		TagKey<Block> tag = null;
		if (o.has("tag")) {
			Identifier id = parse(o.get("tag").getAsString());
			if (id != null) {
				tag = TagKey.of(RegistryKeys.BLOCK, id);
			}
		}
		boolean any = set.isEmpty() && tag == null;
		TagKey<Block> t = tag;
		return st -> st != null && (any || set.contains(st.getBlock()) || (t != null && st.isIn(t)));
	}

	public static Predicate<Entity> entities(JsonObject o) {
		Set<EntityType<?>> set = new HashSet<>();
		for (String s : Json.list(o, "entities")) {
			Identifier id = parse(s);
			if (id != null) {
				Registries.ENTITY_TYPE.getOrEmpty(id).ifPresentOrElse(set::add,
					() -> Diktatura.LOGGER.warn("Неизвестная сущность в законе: {}", s));
			}
		}
		TagKey<EntityType<?>> tag = null;
		if (o.has("tag")) {
			Identifier id = parse(o.get("tag").getAsString());
			if (id != null) {
				tag = TagKey.of(RegistryKeys.ENTITY_TYPE, id);
			}
		}
		boolean any = set.isEmpty() && tag == null;
		TagKey<EntityType<?>> t = tag;
		return e -> e != null && (any || set.contains(e.getType()) || (t != null && e.getType().isIn(t)));
	}
}
