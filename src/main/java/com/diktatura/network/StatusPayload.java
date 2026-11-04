package com.diktatura.network;

import com.diktatura.Diktatura;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/** Сервер → клиент: всё, что нужно HUD и книге законов. Логики на клиенте нет. */
public record StatusPayload(int loyalty, int fear, int stage, int sentenceSeconds, int timeoutSeconds,
                            int decreeSeconds, boolean rebellion, List<String> laws) implements CustomPayload {
	public static final Id<StatusPayload> ID = new Id<>(Diktatura.id("status"));
	public static final PacketCodec<RegistryByteBuf, StatusPayload> CODEC = PacketCodec.of(StatusPayload::write, StatusPayload::read);

	private void write(RegistryByteBuf buf) {
		buf.writeVarInt(loyalty);
		buf.writeVarInt(fear);
		buf.writeVarInt(stage);
		buf.writeVarInt(sentenceSeconds);
		buf.writeVarInt(timeoutSeconds);
		buf.writeVarInt(decreeSeconds);
		buf.writeBoolean(rebellion);
		buf.writeVarInt(laws.size());
		for (String l : laws) {
			buf.writeString(l);
		}
	}

	private static StatusPayload read(RegistryByteBuf buf) {
		int loyalty = buf.readVarInt();
		int fear = buf.readVarInt();
		int stage = buf.readVarInt();
		int sentence = buf.readVarInt();
		int timeout = buf.readVarInt();
		int decree = buf.readVarInt();
		boolean rebellion = buf.readBoolean();
		int n = buf.readVarInt();
		List<String> laws = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			laws.add(buf.readString());
		}
		return new StatusPayload(loyalty, fear, stage, sentence, timeout, decree, rebellion, laws);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
