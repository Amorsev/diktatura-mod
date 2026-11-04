package com.diktatura.law;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Мелкие помощники для чтения JSON законов. */
public final class Json {
	private Json() {
	}

	public static int i(JsonObject o, String key, int def) {
		return o.has(key) ? o.get(key).getAsInt() : def;
	}

	public static double d(JsonObject o, String key, double def) {
		return o.has(key) ? o.get(key).getAsDouble() : def;
	}

	public static boolean b(JsonObject o, String key, boolean def) {
		return o.has(key) ? o.get(key).getAsBoolean() : def;
	}

	public static String s(JsonObject o, String key, String def) {
		return o.has(key) ? o.get(key).getAsString() : def;
	}

	/** Строка или массив строк. */
	public static List<String> list(JsonObject o, String key) {
		List<String> out = new ArrayList<>();
		if (!o.has(key)) {
			return out;
		}
		JsonElement e = o.get(key);
		if (e.isJsonArray()) {
			e.getAsJsonArray().forEach(x -> out.add(x.getAsString()));
		} else {
			out.add(e.getAsString());
		}
		return out;
	}
}
