package com.diktatura.license;

import com.diktatura.Diktatura;
import com.diktatura.dictator.ThroneHall;
import com.diktatura.economy.Economy;
import com.diktatura.law.Law;
import com.diktatura.law.LawRegistry;
import com.diktatura.license.LicenseRegistry.LicenseDef;
import com.diktatura.network.UiPayloads;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.NpcEntity;
import com.diktatura.npc.NpcInteractions;
import com.diktatura.npc.NpcPosts;
import com.diktatura.npc.NpcRegistry;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.ui.Hud;
import com.diktatura.ui.Voice;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Лицензии — платное разрешение на то, что законом запрещено. Покупаются за трудодни
 * в лицензионном отделе (с подтверждением), действуют N игровых дней, отзываются при аресте.
 */
public final class Licenses {
	public static final String LICENSOR_ID = "diktatura:licensor";

	/** Какую лицензию игрок выбрал и ещё не подтвердил. */
	private static final Map<UUID, String> PENDING = new HashMap<>();
	/** Цена ластика (стирает один протокол ареста). */
	public static final int ERASER_PRICE = 60;
	private static final Map<UUID, Integer> LAST_PHRASE = new HashMap<>();

	private Licenses() {
	}

	public static void registerEvents() {
		NpcInteractions.register("licensor", new LicensorHandler());
	}

	private static long now(MinecraftServer server) {
		return server.getOverworld().getTime();
	}

	/** До какого игрового времени действует лицензия (0 — нет). */
	public static long until(PlayerRecord rec, String id) {
		return rec.licenses.contains(id) ? rec.licenses.getLong(id) : 0L;
	}

	public static boolean has(MinecraftServer server, PlayerRecord rec, String id) {
		return until(rec, id) > now(server);
	}

	/** Снимает ли какая-то из лицензий игрока этот закон. */
	public static boolean covers(ServerPlayerEntity p, PlayerRecord rec, Law law) {
		if (law.exemptLicenses().isEmpty()) {
			return false;
		}
		MinecraftServer server = p.getServer();
		for (String id : law.exemptLicenses()) {
			// "job:lumberjack" — исключение для тех, кто сейчас на такой работе (госзаказ).
			if (id.startsWith("job:")) {
				var def = com.diktatura.job.Jobs.def(rec);
				if (def != null && def.type().name().equalsIgnoreCase(id.substring(4))) {
					return true;
				}
			} else if (has(server, rec, id)) {
				return true;
			}
		}
		return false;
	}

	/** Законы, которые снимает лицензия. */
	public static List<Law> lawsFor(String licenseId) {
		List<Law> out = new ArrayList<>();
		for (Law l : LawRegistry.all()) {
			if (l.exemptLicenses().contains(licenseId)) {
				out.add(l);
			}
		}
		return out;
	}

	/** Выдать или продлить лицензию (продление прибавляется к остатку). */
	public static void grant(ServerPlayerEntity p, LicenseDef def) {
		MinecraftServer server = p.getServer();
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(p);
		long start = Math.max(now(server), until(rec, def.id()));
		rec.licenses.putLong(def.id(), start + def.durationTicks());
		state.markDirty();
	}

	public static void revoke(ServerPlayerEntity p, String id) {
		DiktaturaState state = DiktaturaState.get(p.getServer());
		state.player(p).licenses.remove(id);
		state.markDirty();
	}

	/** Арест: лицензии с revoke_on_arrest аннулируются. */
	public static void onArrest(ServerPlayerEntity p) {
		MinecraftServer server = p.getServer();
		PlayerRecord rec = DiktaturaState.get(server).player(p);
		int n = 0;
		for (String id : new ArrayList<>(rec.licenses.getKeys())) {
			LicenseDef def = LicenseRegistry.get(id);
			if ((def == null || def.revokeOnArrest()) && has(server, rec, id)) {
				rec.licenses.remove(id);
				n++;
			}
		}
		if (n > 0) {
			p.sendMessage(Text.translatable("diktatura.license.revoked_arrest", n).formatted(Formatting.RED), false);
			DiktaturaState.get(server).markDirty();
		}
	}

	public static void tick(MinecraftServer server) {
		long t = Diktatura.ticks();
		if (t % 100 != 0) {
			return;
		}
		// Истёкшие лицензии: сообщить и убрать.
		long now = now(server);
		DiktaturaState state = DiktaturaState.get(server);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			PlayerRecord rec = state.player(p);
			for (String id : new ArrayList<>(rec.licenses.getKeys())) {
				if (rec.licenses.getLong(id) <= now) {
					rec.licenses.remove(id);
					state.markDirty();
					LicenseDef def = LicenseRegistry.get(id);
					Hud.alert(p, AlertKind.INFO, Text.translatable("diktatura.license.expired",
						def == null ? Text.literal(id) : Text.translatable(def.name())), Text.translatable("diktatura.license.expired.hint"), 6);
				}
			}
		}
	}

	/** Список лицензий игрока для книги законов. */
	public static void sendList(MinecraftServer server, ServerPlayerEntity p) {
		if (!ServerPlayNetworking.canSend(p, UiPayloads.LicenseList.ID)) {
			return;
		}
		PlayerRecord rec = DiktaturaState.get(server).player(p);
		long now = now(server);
		List<UiPayloads.LicenseEntry> out = new ArrayList<>();
		for (String id : rec.licenses.getKeys()) {
			long left = rec.licenses.getLong(id) - now;
			if (left <= 0) {
				continue;
			}
			LicenseDef def = LicenseRegistry.get(id);
			if (def == null) {
				continue; // лицензия, которой больше нет в моде (например, отменённая в 0.2.29)
			}
			out.add(new UiPayloads.LicenseEntry(Text.translatable(def.name()), Text.translatable(def.desc()),
				(int) Math.min(Integer.MAX_VALUE, left / 20),
				lawsFor(id).stream().map(Law::key).toList()));
		}
		List<UiPayloads.ActivityEntry> acts = new ArrayList<>();
		for (LicensedActivities.Activity a : LicensedActivities.ALL) {
			if (!rec.activities.contains(a.id())) {
				continue;
			}
			LicenseDef def = LicenseRegistry.get(a.license());
			long left = rec.licenses.contains(a.license()) ? rec.licenses.getLong(a.license()) - now : 0;
			acts.add(new UiPayloads.ActivityEntry(Text.translatable(a.key()), Text.translatable(a.key() + ".desc"),
				def == null ? Text.literal(a.license()) : Text.translatable(def.name()),
				def == null ? 0 : def.price(), def == null ? "?" : days(def.days()), a.fine(),
				(int) Math.max(0, Math.min(Integer.MAX_VALUE, left / 20))));
		}
		ServerPlayNetworking.send(p, new UiPayloads.LicenseList(out, acts));
	}

	private static String days(double d) {
		return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
	}

	// ================================================================== лицензионный отдел

	private static final class LicensorHandler implements NpcInteractions.Handler {
		@Override
		public boolean matches(Entity entity) {
			return entity instanceof NpcEntity n && LICENSOR_ID.equals(n.npcId());
		}

		@Override
		public void onOpen(ServerPlayerEntity player, Entity npc) {
			PENDING.remove(player.getUuid());
			NpcRegistry.NpcData d = NpcRegistry.get(LICENSOR_ID);
			if (d != null) {
				int i = player.getRandom().nextInt(d.phraseCount());
				LAST_PHRASE.put(player.getUuid(), i);
				Voice.say(npc, d.displayName(), d.phrase(i), d.soundEvent(), d.pitch());
			}
		}

		@Override
		public UiPayloads.NpcScreen screen(ServerPlayerEntity player, Entity npc) {
			NpcRegistry.NpcData d = NpcRegistry.get(LICENSOR_ID);
			MinecraftServer server = player.getServer();
			PlayerRecord rec = DiktaturaState.get(server).player(player);
			List<Text> info = new ArrayList<>();
			List<UiPayloads.NpcAction> actions = new ArrayList<>();
			info.add(Text.translatable("diktatura.job.balance", rec.trudodni));
			Text line = d != null ? d.phrase(LAST_PHRASE.getOrDefault(player.getUuid(), 0)) : Text.empty();

			LicenseDef pending = LicenseRegistry.get(PENDING.get(player.getUuid()));
			if (pending != null) {
				line = Text.translatable("diktatura.license.confirm.line");
				info.add(Text.translatable("diktatura.license.confirm.what", Text.translatable(pending.name())));
				info.add(Text.translatable(pending.desc()));
				info.add(Text.translatable("diktatura.license.confirm.terms", pending.price(), days(pending.days())));
				if (has(server, rec, pending.id())) {
					info.add(Text.translatable("diktatura.license.confirm.extend"));
				}
				boolean affordable = rec.trudodni >= pending.price();
				actions.add(new UiPayloads.NpcAction("confirm",
					Text.translatable(affordable ? "diktatura.license.buy" : "diktatura.license.poor", pending.price()), affordable));
				actions.add(new UiPayloads.NpcAction("cancel", Text.translatable("diktatura.license.cancel"), true));
			} else {
				info.add(Text.translatable("diktatura.license.choose"));
				for (LicenseDef l : LicenseRegistry.all()) {
					if (!l.forSale()) {
						continue;
					}
					String key = has(server, rec, l.id()) ? "diktatura.license.offer.owned" : "diktatura.license.offer";
					actions.add(new UiPayloads.NpcAction("pick:" + l.id(),
						Text.translatable(key, Text.translatable(l.name()), l.price(), days(l.days())), true));
				}
			}
			if (pending == null) {
				actions.add(new UiPayloads.NpcAction("buy:eraser",
					Text.translatable("diktatura.license.eraser", ERASER_PRICE), rec.trudodni >= ERASER_PRICE));
			}
			actions.add(new UiPayloads.NpcAction("client:close", Text.translatable("diktatura.npc.leave"), true));
			return new UiPayloads.NpcScreen("licensor", npc.getId(),
				d != null ? d.displayName() : npc.getName(),
				d != null ? Text.translatable(d.role()) : Text.empty(), line, info, actions,
				d != null ? d.accent() : 0xFF8A6A3A);
		}

		@Override
		public boolean action(ServerPlayerEntity player, Entity npc, String action) {
			UUID uuid = player.getUuid();
			if (action.startsWith("pick:")) {
				LicenseDef def = LicenseRegistry.get(action.substring(5));
				if (def != null && def.forSale()) {
					PENDING.put(uuid, def.id());
				}
				return true;
			}
			if ("buy:eraser".equals(action)) {
				if (Economy.take(player, ERASER_PRICE, "diktatura.money.reason.eraser", "")) {
					player.getInventory().offerOrDrop(new net.minecraft.item.ItemStack(com.diktatura.item.ModItems.ERASER));
					Hud.alert(player, AlertKind.GOOD, Text.translatable("diktatura.eraser.bought"),
						Text.translatable("diktatura.eraser.bought.hint"), 6);
				}
				return true;
			}
			if ("cancel".equals(action)) {
				PENDING.remove(uuid);
				return true;
			}
			if (!"confirm".equals(action)) {
				return false;
			}
			LicenseDef def = LicenseRegistry.get(PENDING.remove(uuid));
			if (def == null || !def.forSale()) {
				return true;
			}
			if (!Economy.take(player, def.price(), "diktatura.money.reason.license", def.name())) {
				Hud.alert(player, AlertKind.DANGER, Text.translatable("diktatura.license.no_money"),
					Text.translatable("diktatura.license.no_money.hint", def.price()), 5);
				return true;
			}
			grant(player, def);
			Hud.alert(player, AlertKind.GOOD, Text.translatable("diktatura.license.bought", Text.translatable(def.name())),
				Text.translatable("diktatura.license.bought.hint", days(def.days())), 6);
			NpcRegistry.NpcData d = NpcRegistry.get(LICENSOR_ID);
			if (d != null) {
				Voice.say(npc, d.displayName(), Text.translatable("npc.diktatura.licensor.stamp"), d.soundEvent(), d.pitch());
			}
			return true;
		}
	}
}
