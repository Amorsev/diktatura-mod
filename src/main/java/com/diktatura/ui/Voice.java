package com.diktatura.ui;

import com.diktatura.network.UiPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Реплики персонажей. Звук идёт из точки, где стоит НПС, субтитры получают игроки
 * в радиусе {@link #RADIUS} блоков. Длительность показа зависит от длины фразы.
 * Идея — из референсного мода (util/Voice), реализация своя.
 */
public final class Voice {
	public static final double RADIUS = 32.0;

	private Voice() {
	}

	/** Сколько тиков держать реплику на экране: 2 с минимум плюс время на чтение. */
	public static int durationTicks(Text line) {
		int len = line.getString().length();
		return Math.min(20 * 20, 40 + len * 3);
	}

	/** НПС говорит вслух: звук из его точки, субтитры всем рядом. */
	public static void say(Entity speaker, Text name, Text line, SoundEvent sound, float pitch) {
		if (!(speaker.getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (sound != null) {
			world.playSound(null, speaker.getX(), speaker.getY(), speaker.getZ(), sound, SoundCategory.NEUTRAL, 1.0f, pitch);
		}
		int ticks = durationTicks(line);
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.squaredDistanceTo(speaker) <= RADIUS * RADIUS) {
				send(p, speaker.getId(), name, line, ticks);
			}
		}
	}

	/** Сообщение «по радио» — всем игрокам на сервере, без звука из точки. */
	public static void broadcast(MinecraftServer server, Text name, Text line) {
		int ticks = durationTicks(line);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			send(p, -1, name, line, ticks);
		}
	}

	private static void send(ServerPlayerEntity p, int entityId, Text name, Text line, int ticks) {
		if (ServerPlayNetworking.canSend(p, UiPayloads.Speech.ID)) {
			ServerPlayNetworking.send(p, new UiPayloads.Speech(entityId, name, line, ticks));
		} else {
			p.sendMessage(Text.empty().append(name.copy().formatted(Formatting.GOLD)).append(": ")
				.append(line.copy().formatted(Formatting.YELLOW)), false);
		}
	}
}
