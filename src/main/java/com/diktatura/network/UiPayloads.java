package com.diktatura.network;

import com.diktatura.Diktatura;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

import java.util.ArrayList;
import java.util.List;

/**
 * Пакеты нового интерфейса (этап 7). Сервер решает, что показать, клиент только рисует.
 * Тексты передаются как {@link Text}, поэтому переводятся на языке клиента.
 */
public final class UiPayloads {
	private UiPayloads() {
	}

	static void writeText(RegistryByteBuf buf, Text t) {
		TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, t);
	}

	static Text readText(RegistryByteBuf buf) {
		return TextCodecs.REGISTRY_PACKET_CODEC.decode(buf);
	}

	static void writeStrings(RegistryByteBuf buf, List<String> list) {
		buf.writeVarInt(list.size());
		for (String s : list) {
			buf.writeString(s);
		}
	}

	static List<String> readStrings(RegistryByteBuf buf) {
		int n = buf.readVarInt();
		List<String> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(buf.readString());
		}
		return out;
	}

	// ------------------------------------------------------------------ баннер

	/** Вид баннера: определяет иконку и цвет полосы. */
	public enum AlertKind { INFO, DECREE, EVENT, DANGER, GOOD;
		public static AlertKind byOrdinal(int i) {
			AlertKind[] v = values();
			return i >= 0 && i < v.length ? v[i] : INFO;
		}
	}

	/** Баннер сверху экрана. Одинаковый заголовок продлевает уже показанный баннер. */
	public record Alert(int kind, Text title, Text hint, int ticks) implements CustomPayload {
		public static final Id<Alert> ID = new Id<>(Diktatura.id("alert"));
		public static final PacketCodec<RegistryByteBuf, Alert> CODEC = PacketCodec.of(Alert::write, Alert::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(kind);
			writeText(buf, title);
			writeText(buf, hint);
			buf.writeVarInt(ticks);
		}

		private static Alert read(RegistryByteBuf buf) {
			return new Alert(buf.readVarInt(), readText(buf), readText(buf), buf.readVarInt());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	// ------------------------------------------------------------------ заголовок

	/** Стиль крупного заголовка. */
	public enum HeadlineStyle { DECREE, EMERGENCY, REBELLION, PROCLAMATION;
		public static HeadlineStyle byOrdinal(int i) {
			HeadlineStyle[] v = values();
			return i >= 0 && i < v.length ? v[i] : DECREE;
		}
	}

	/** Крупный заголовок с полосами сверху и снизу («ВОЖДЬ ИЗДАЛ УКАЗ»). */
	public record Headline(int style, Text title, Text subtitle, int ticks) implements CustomPayload {
		public static final Id<Headline> ID = new Id<>(Diktatura.id("headline"));
		public static final PacketCodec<RegistryByteBuf, Headline> CODEC = PacketCodec.of(Headline::write, Headline::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(style);
			writeText(buf, title);
			writeText(buf, subtitle);
			buf.writeVarInt(ticks);
		}

		private static Headline read(RegistryByteBuf buf) {
			return new Headline(buf.readVarInt(), readText(buf), readText(buf), buf.readVarInt());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	// ------------------------------------------------------------------ реплика

	/** Реплика персонажа: субтитры с печатной машинкой. entityId = -1, если говорит «радио». */
	public record Speech(int entityId, Text speaker, Text line, int ticks) implements CustomPayload {
		public static final Id<Speech> ID = new Id<>(Diktatura.id("speech"));
		public static final PacketCodec<RegistryByteBuf, Speech> CODEC = PacketCodec.of(Speech::write, Speech::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(entityId + 1);
			writeText(buf, speaker);
			writeText(buf, line);
			buf.writeVarInt(ticks);
		}

		private static Speech read(RegistryByteBuf buf) {
			return new Speech(buf.readVarInt() - 1, readText(buf), readText(buf), buf.readVarInt());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	// ------------------------------------------------------------------ «РАЗЫСКИВАЕТСЯ»

	/** Плакат «РАЗЫСКИВАЕТСЯ» на весь экран у арестованного. */
	public record Wanted(Text reason, int seconds) implements CustomPayload {
		public static final Id<Wanted> ID = new Id<>(Diktatura.id("wanted"));
		public static final PacketCodec<RegistryByteBuf, Wanted> CODEC = PacketCodec.of(Wanted::write, Wanted::read);

		private void write(RegistryByteBuf buf) {
			writeText(buf, reason);
			buf.writeVarInt(seconds);
		}

		private static Wanted read(RegistryByteBuf buf) {
			return new Wanted(readText(buf), buf.readVarInt());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	// ------------------------------------------------------------------ списки законов

	/** Все загруженные законы и отменённые указами — для вкладок книги законов. */
	public record LawList(List<String> all, List<String> repealed) implements CustomPayload {
		public static final Id<LawList> ID = new Id<>(Diktatura.id("law_list"));
		public static final PacketCodec<RegistryByteBuf, LawList> CODEC = PacketCodec.of(LawList::write, LawList::read);

		private void write(RegistryByteBuf buf) {
			writeStrings(buf, all);
			writeStrings(buf, repealed);
		}

		private static LawList read(RegistryByteBuf buf) {
			return new LawList(readStrings(buf), readStrings(buf));
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	// ------------------------------------------------------------------ госреклама

	/** Сервер → клиент: показать рекламный плакат N на заданное число секунд. */
	public record Ad(int ad, int seconds) implements CustomPayload {
		public static final Id<Ad> ID = new Id<>(Diktatura.id("ad"));
		public static final PacketCodec<RegistryByteBuf, Ad> CODEC = PacketCodec.of(Ad::write, Ad::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(ad);
			buf.writeVarInt(seconds);
		}

		private static Ad read(RegistryByteBuf buf) {
			return new Ad(buf.readVarInt(), buf.readVarInt());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	// ------------------------------------------------------------------ лицензии

	/** Действующая лицензия игрока: название, описание, сколько осталось (с), какие законы снимает. */
	public record LicenseEntry(Text name, Text desc, int secondsLeft, List<String> laws) {
	}

	/** Сервер → клиент: лицензии игрока для вкладки «Мои лицензии». */
	public record LicenseList(List<LicenseEntry> entries) implements CustomPayload {
		public static final Id<LicenseList> ID = new Id<>(Diktatura.id("licenses"));
		public static final PacketCodec<RegistryByteBuf, LicenseList> CODEC = PacketCodec.of(LicenseList::write, LicenseList::read);

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(entries.size());
			for (LicenseEntry e : entries) {
				writeText(buf, e.name());
				writeText(buf, e.desc());
				buf.writeVarInt(e.secondsLeft());
				writeStrings(buf, e.laws());
			}
		}

		private static LicenseList read(RegistryByteBuf buf) {
			int n = buf.readVarInt();
			List<LicenseEntry> out = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				out.add(new LicenseEntry(readText(buf), readText(buf), buf.readVarInt(), readStrings(buf)));
			}
			return new LicenseList(out);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	// ------------------------------------------------------------------ окно НПС

	/** Кнопка окна НПС. Действия с префиксом {@code client:} клиент выполняет сам. */
	public record NpcAction(String id, Text label, boolean enabled) {
	}

	/** Сервер → клиент: открыть (или обновить) окно НПС. */
	public record NpcScreen(String npc, int entityId, Text name, Text role, Text line, List<Text> info,
	                        List<NpcAction> actions, int accent) implements CustomPayload {
		public static final Id<NpcScreen> ID = new Id<>(Diktatura.id("npc_screen"));
		public static final PacketCodec<RegistryByteBuf, NpcScreen> CODEC = PacketCodec.of(NpcScreen::write, NpcScreen::read);

		private void write(RegistryByteBuf buf) {
			buf.writeString(npc);
			buf.writeVarInt(entityId + 1);
			writeText(buf, name);
			writeText(buf, role);
			writeText(buf, line);
			buf.writeVarInt(info.size());
			for (Text t : info) {
				writeText(buf, t);
			}
			buf.writeVarInt(actions.size());
			for (NpcAction a : actions) {
				buf.writeString(a.id());
				writeText(buf, a.label());
				buf.writeBoolean(a.enabled());
			}
			buf.writeInt(accent);
		}

		private static NpcScreen read(RegistryByteBuf buf) {
			String npc = buf.readString();
			int entityId = buf.readVarInt() - 1;
			Text name = readText(buf);
			Text role = readText(buf);
			Text line = readText(buf);
			int n = buf.readVarInt();
			List<Text> info = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				info.add(readText(buf));
			}
			int m = buf.readVarInt();
			List<NpcAction> actions = new ArrayList<>(m);
			for (int i = 0; i < m; i++) {
				actions.add(new NpcAction(buf.readString(), readText(buf), buf.readBoolean()));
			}
			return new NpcScreen(npc, entityId, name, role, line, info, actions, buf.readInt());
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Клиент → сервер: игрок нажал кнопку в окне НПС. Сервер всё проверяет сам. */
	public record NpcClick(String npc, int entityId, String action) implements CustomPayload {
		public static final Id<NpcClick> ID = new Id<>(Diktatura.id("npc_click"));
		public static final PacketCodec<RegistryByteBuf, NpcClick> CODEC = PacketCodec.of(NpcClick::write, NpcClick::read);

		private void write(RegistryByteBuf buf) {
			buf.writeString(npc, 64);
			buf.writeVarInt(entityId + 1);
			buf.writeString(action, 64);
		}

		private static NpcClick read(RegistryByteBuf buf) {
			return new NpcClick(buf.readString(64), buf.readVarInt() - 1, buf.readString(64));
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
