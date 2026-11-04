package com.diktatura.wealth;

import com.diktatura.Diktatura;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Ценность предметов для законов о богатстве.
 * Порядок: значение из Upgrader (если мод установлен и метод найден) → своя таблица
 * data/diktatura/diktatura/item_values.json с поправками из конфига Upgrader.
 */
public final class ItemValues {
	public static final Identifier TABLE = Diktatura.id("diktatura/item_values.json");
	private static final Map<Item, Double> table = new HashMap<>();
	private static final Map<Item, Double> multipliers = new HashMap<>();
	private static final Set<Item> blacklist = new HashSet<>();
	private static double globalMultiplier = 1.0;
	private static final Map<String, Long> cacheTick = new HashMap<>();
	private static final Map<String, Double> cacheValue = new HashMap<>();

	private ItemValues() {
	}

	public static void reload(ResourceManager manager) {
		table.clear();
		multipliers.clear();
		blacklist.clear();
		globalMultiplier = 1.0;
		cacheTick.clear();
		cacheValue.clear();
		Optional<Resource> res = manager.getResource(TABLE);
		if (res.isPresent()) {
			try (Reader r = res.get().getReader()) {
				JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
				JsonObject values = o.has("values") ? o.getAsJsonObject("values") : o;
				for (Map.Entry<String, JsonElement> e : values.entrySet()) {
					put(table, e.getKey(), e.getValue().getAsDouble());
				}
			} catch (Exception e) {
				Diktatura.LOGGER.error("Не удалось прочитать {}", TABLE, e);
			}
		}
		UpgraderBridge.applyConfig();
		Diktatura.LOGGER.info("Таблица ценностей: {} предметов, Upgrader: {}", table.size(), UpgraderBridge.status());
	}

	static void put(Map<Item, Double> map, String id, double value) {
		Identifier i = Identifier.tryParse(id);
		if (i != null) {
			Registries.ITEM.getOrEmpty(i).ifPresent(item -> map.put(item, value));
		}
	}

	static void override(String id, double value) {
		put(table, id, value);
	}

	static void multiplier(String id, double value) {
		put(multipliers, id, value);
	}

	static void blacklist(String id) {
		Identifier i = Identifier.tryParse(id);
		if (i != null) {
			Registries.ITEM.getOrEmpty(i).ifPresent(blacklist::add);
		}
	}

	static void globalMultiplier(double value) {
		globalMultiplier = value;
	}

	/** Ценность всего стека с учётом количества и износа. */
	public static double value(ItemStack stack) {
		if (stack.isEmpty() || blacklist.contains(stack.getItem())) {
			return 0;
		}
		Item item = stack.getItem();
		double one = UpgraderBridge.value(stack);
		if (one <= 0) {
			one = table.getOrDefault(item, 0.0) * multipliers.getOrDefault(item, 1.0) * globalMultiplier;
			if (stack.isDamageable() && stack.getMaxDamage() > 0) {
				one *= (stack.getMaxDamage() - stack.getDamage()) / (double) stack.getMaxDamage();
			}
		}
		return one * stack.getCount();
	}

	public static double inventoryValue(ServerPlayerEntity player, boolean includeEnderChest) {
		double sum = 0;
		PlayerInventory inv = player.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			sum += value(inv.getStack(i));
		}
		if (includeEnderChest) {
			EnderChestInventory ender = player.getEnderChestInventory();
			for (int i = 0; i < ender.size(); i++) {
				sum += value(ender.getStack(i));
			}
		}
		return sum;
	}

	/** То же с кэшем: пересчёт не чаще раза в 5 секунд (или 0,5 секунды при подборе предмета). */
	public static double cachedInventoryValue(ServerPlayerEntity player, boolean includeEnderChest, boolean pickup) {
		String key = player.getUuid() + (includeEnderChest ? "+ender" : "");
		long now = Diktatura.ticks();
		Long last = cacheTick.get(key);
		long maxAge = pickup ? 10 : 100;
		Double cached = cacheValue.get(key);
		if (last != null && cached != null && now - last >= 0 && now - last < maxAge) {
			return cached;
		}
		double v = inventoryValue(player, includeEnderChest);
		cacheTick.put(key, now);
		cacheValue.put(key, v);
		return v;
	}
}
