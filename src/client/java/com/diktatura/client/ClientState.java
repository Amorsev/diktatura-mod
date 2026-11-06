package com.diktatura.client;

import com.diktatura.network.StatusPayload;
import com.diktatura.network.UiPayloads;

import net.minecraft.text.Text;

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
	public static int minigame = -1;
	public static int progress;
	public static int goal;
	public static int event = -1;
	public static int eventSeconds;
	/** 0 — видно всё, 1 — закрыт левый глаз, 2 — правый. */
	public static int blindSide;
	public static List<String> laws = List.of();
	public static int balance;
	public static List<UiPayloads.LicenseEntry> licenses = List.of();
	/** Текущая работа: пустой заголовок — работы нет. */
	public static Text jobTitle = Text.empty();
	public static int jobProgress;
	public static int jobGoal;
	public static int jobSeconds;
	public static boolean jobHasTarget;
	public static int jobX;
	public static int jobZ;
	/** Все загруженные законы и отменённые указами (пакет LawList). */
	public static List<String> allLaws = List.of();
	public static List<String> repealedLaws = List.of();

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
		minigame = p.minigame();
		progress = p.progress();
		goal = p.goal();
		event = p.event();
		eventSeconds = p.eventSeconds();
		blindSide = p.blindSide();
		laws = List.copyOf(p.laws());
		balance = p.balance();
		jobTitle = p.jobTitle();
		jobProgress = p.jobProgress();
		jobGoal = p.jobGoal();
		jobSeconds = p.jobSeconds();
		jobHasTarget = p.jobHasTarget();
		jobX = p.jobX();
		jobZ = p.jobZ();
	}

	static void updateLaws(UiPayloads.LawList p) {
		allLaws = List.copyOf(p.all());
		repealedLaws = List.copyOf(p.repealed());
	}

	static void updateLicenses(UiPayloads.LicenseList p) {
		licenses = List.copyOf(p.entries());
	}

	static void reset() {
		received = false;
		licenses = List.of();
		jobTitle = Text.empty();
		allLaws = List.of();
		repealedLaws = List.of();
		blindSide = 0;
		event = -1;
		laws = List.of();
	}

	static String time(int seconds) {
		int s = Math.max(0, seconds);
		return String.format("%d:%02d", s / 60, s % 60);
	}
}
