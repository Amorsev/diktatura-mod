package com.diktatura.ui;

import com.diktatura.network.UiPayloads;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.network.UiPayloads.HeadlineStyle;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Серверная сторона нового HUD: баннеры, крупные заголовки, плакат «РАЗЫСКИВАЕТСЯ».
 * Если у клиента нет мода (например, прокси или старая версия), показывается обычный
 * ванильный заголовок или сообщение в чате — информация не теряется.
 */
public final class Hud {
	private Hud() {
	}

	public static void alert(ServerPlayerEntity p, AlertKind kind, Text title, Text hint, int seconds) {
		if (ServerPlayNetworking.canSend(p, UiPayloads.Alert.ID)) {
			ServerPlayNetworking.send(p, new UiPayloads.Alert(kind.ordinal(), title, hint, seconds * 20));
		} else {
			p.sendMessage(title, true);
		}
	}

	public static void alertAll(MinecraftServer server, AlertKind kind, Text title, Text hint, int seconds) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			alert(p, kind, title, hint, seconds);
		}
	}

	public static void headline(ServerPlayerEntity p, HeadlineStyle style, Text title, Text subtitle, int seconds) {
		if (ServerPlayNetworking.canSend(p, UiPayloads.Headline.ID)) {
			ServerPlayNetworking.send(p, new UiPayloads.Headline(style.ordinal(), title, subtitle, seconds * 20));
		} else {
			p.networkHandler.sendPacket(new TitleS2CPacket(title));
			p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
		}
	}

	public static void headlineAll(MinecraftServer server, HeadlineStyle style, Text title, Text subtitle, int seconds) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			headline(p, style, title, subtitle, seconds);
		}
	}

	public static void wanted(ServerPlayerEntity p, Text reason, int seconds) {
		if (ServerPlayNetworking.canSend(p, UiPayloads.Wanted.ID)) {
			ServerPlayNetworking.send(p, new UiPayloads.Wanted(reason, seconds));
		}
	}
}
