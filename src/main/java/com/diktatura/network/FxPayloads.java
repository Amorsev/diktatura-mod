package com.diktatura.network;

import com.diktatura.Diktatura;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Пакеты этапа 14: капча Бюро проверки гражданства и экранные эффекты (искажения). */
public final class FxPayloads {
	private FxPayloads() {
	}

	/** Сервер → клиент: «докажите, что вы не робот». Правильный ответ знает только сервер. */
	public record Captcha(int token, Text category, List<ItemStack> cells) implements CustomPayload {
		public static final Id<Captcha> ID = new Id<>(Diktatura.id("captcha"));
		public static final PacketCodec<RegistryByteBuf, Captcha> CODEC = PacketCodec.of(Captcha::write, Captcha::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(token);
			UiPayloads.writeText(buf, category);
			buf.writeVarInt(cells.size());
			for (ItemStack s : cells) {
				ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, s);
			}
		}

		private static Captcha read(RegistryByteBuf buf) {
			int token = buf.readVarInt();
			Text cat = UiPayloads.readText(buf);
			int n = Math.min(16, buf.readVarInt());
			List<ItemStack> cells = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				cells.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buf));
			}
			return new Captcha(token, cat, cells);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Клиент → сервер: отмеченные клетки (битовая маска) или отказ от проверки. */
	public record CaptchaAnswer(int token, int mask, boolean cancelled) implements CustomPayload {
		public static final Id<CaptchaAnswer> ID = new Id<>(Diktatura.id("captcha_answer"));
		public static final PacketCodec<RegistryByteBuf, CaptchaAnswer> CODEC = PacketCodec.of(CaptchaAnswer::write, CaptchaAnswer::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(token);
			buf.writeVarInt(mask);
			buf.writeBoolean(cancelled);
		}

		private static CaptchaAnswer read(RegistryByteBuf buf) {
			return new CaptchaAnswer(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Сервер → клиент: включить экранный эффект на N тиков (0 — выключить). */
	public record Fx(String effect, int ticks) implements CustomPayload {
		public static final Id<Fx> ID = new Id<>(Diktatura.id("fx"));
		public static final PacketCodec<RegistryByteBuf, Fx> CODEC = PacketCodec.of(Fx::write, Fx::read);

		private void write(RegistryByteBuf buf) {
			buf.writeString(effect, 32);
			buf.writeVarInt(ticks);
		}

		private static Fx read(RegistryByteBuf buf) {
			return new Fx(buf.readString(32), buf.readVarInt());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
