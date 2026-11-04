package com.diktatura.client;

import com.diktatura.network.StatusPayload;

import java.util.List;

/** Последние данные от сервера. Только отображение, никакой логики. */
public final class ClientState {
	public static boolean received;
	public static int loyalty;
	public static int fear;
	public static int stage;
	public static int sentenceSeconds;
	public static int timeoutSeconds;
	public static int decreeSeconds;
	public static boolean rebellion;
	public static List<String> laws = List.of();

	private ClientState() {
	}

	static void update(StatusPayload p) {
		received = true;
		loyalty = p.loyalty();
		fear = p.fear();
		stage = p.stage();
		sentenceSeconds = p.sentenceSeconds();
		timeoutSeconds = p.timeoutSeconds();
		decreeSeconds = p.decreeSeconds();
		rebellion = p.rebellion();
		laws = List.copyOf(p.laws());
	}

	static void reset() {
		received = false;
		laws = List.of();
	}

	static String time(int seconds) {
		int s = Math.max(0, seconds);
		return String.format("%d:%02d", s / 60, s % 60);
	}
}
