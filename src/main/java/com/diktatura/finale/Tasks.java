package com.diktatura.finale;

import com.diktatura.Diktatura;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Отложенные действия для финального боя: «через N тиков сделать то-то». */
public final class Tasks {
	private record Task(long at, Runnable run) {
	}

	private static final List<Task> TASKS = new ArrayList<>();
	private static final List<Task> ADDED = new ArrayList<>();
	private static long now;

	private Tasks() {
	}

	public static void schedule(MinecraftServer server, int delay, Runnable r) {
		ADDED.add(new Task(now + Math.max(0, delay), r));
	}

	public static void tick(MinecraftServer server) {
		now++;
		TASKS.addAll(ADDED);
		ADDED.clear();
		for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
			Task t = it.next();
			if (t.at() <= now) {
				it.remove();
				try {
					t.run().run();
				} catch (RuntimeException e) {
					Diktatura.LOGGER.error("Ошибка в отложенном действии финального боя", e);
				}
			}
		}
	}

	public static void clear() {
		TASKS.clear();
		ADDED.clear();
	}
}
