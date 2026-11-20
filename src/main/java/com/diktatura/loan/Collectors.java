package com.diktatura.loan;

import com.diktatura.Diktatura;
import com.diktatura.economy.Economy;
import com.diktatura.item.ModItems;
import com.diktatura.law.LawEngine;
import com.diktatura.loyalty.LoyaltySystem;
import com.diktatura.network.UiPayloads;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.ModEntities;
import com.diktatura.npc.NpcEntity;
import com.diktatura.npc.NpcInteractions;
import com.diktatura.npc.NpcRegistry;
import com.diktatura.prison.PrisonSystem;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.transport.TransportSystem;
import com.diktatura.ui.Hud;
import com.diktatura.ui.Voice;
import com.diktatura.wealth.ItemValues;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Группа взыскания. Раз в {@link LoanSettings#collectorMinutes()} минут (таймер идёт, пока должник в сети)
 * к должнику выезжают коллекторы: по адресу прописки, если он дома (в 64 блоках от таблички), иначе — к нему лично.
 * Когда долг дошёл до половины потолка, приходит VIP-коллектор с напарником.
 *
 * <p>Коллектор подходит и предъявляет требование — 10 % долга. ПКМ по коллектору — окно: заплатить трудоднями,
 * сдать предмет из руки или отказаться. На ответ {@link #WAIT_SECONDS} секунд. Отказ или молчание: обычный
 * коллектор описывает самый ценный предмет в счёт долга, VIP-коллектор ещё и арестовывает. Удар по коллектору —
 * сопротивление взысканию (+10 % к долгу, VIP — арест). Сбежать — неявка (+5 % к долгу).
 * Справка о бедности отсылает коллекторов и откладывает следующий визит на 5 минут (раз в кредитный день).
 */
public final class Collectors {
	public static final String COLLECTOR_ID = "diktatura:collector";
	public static final String VIP_ID = "diktatura:collector_vip";
	public static final int WAIT_SECONDS = 20;
	private static final int APPROACH_TICKS = 20 * 45;
	private static final double DEMAND_PERCENT = 10;
	private static final double NO_SHOW_PERCENT = 5;
	private static final double RESIST_PERCENT = 10;
	private static final int CERTIFICATE_TICKS = 5 * 1200;

	private enum Phase { APPROACH, WAIT, DONE }

	private static final class Group {
		final UUID player;
		final boolean vip;
		final boolean atAddress;
		final List<UUID> members = new ArrayList<>();
		UUID lead;
		Phase phase = Phase.APPROACH;
		int ticks;
		int demand;

		Group(UUID player, boolean vip, boolean atAddress) {
			this.player = player;
			this.vip = vip;
			this.atAddress = atAddress;
		}
	}

	/** Активные группы: должник → группа; коллектор → должник. В мире не сохраняются: после перезапуска уходят. */
	private static final Map<UUID, Group> BY_PLAYER = new HashMap<>();
	private static final Map<UUID, UUID> BY_NPC = new HashMap<>();
	private static final Map<UUID, Long> LAST_ASSAULT = new HashMap<>();

	private Collectors() {
	}

	public static void registerEvents() {
		NpcInteractions.register("collector", new CollectorHandler());
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof NpcEntity npc && (COLLECTOR_ID.equals(npc.npcId()) || VIP_ID.equals(npc.npcId()))) {
				LawEngine.defer(() -> {
					if (!npc.isRemoved() && !BY_NPC.containsKey(npc.getUuid())) {
						npc.discard();
					}
				});
			}
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(entity instanceof NpcEntity npc) || !BY_NPC.containsKey(npc.getUuid())) {
				return ActionResult.PASS;
			}
			if (!world.isClient && player instanceof ServerPlayerEntity sp) {
				onAttack(sp, npc);
			}
			return ActionResult.SUCCESS;
		});
	}

	public static void clear() {
		BY_PLAYER.clear();
		BY_NPC.clear();
		LAST_ASSAULT.clear();
	}

	public static int periodTicks() {
		return LoanSettings.get().collectorMinutes() * 1200;
	}

	/** Минут до следующего визита (для окна кассы). */
	public static int minutesToVisit(PlayerRecord rec) {
		return Math.max(0, (rec.collectorTicks + 1199) / 1200);
	}

	public static boolean visiting(UUID player) {
		return BY_PLAYER.containsKey(player);
	}

	private static boolean canVisit(ServerPlayerEntity p, PlayerRecord rec) {
		return LawEngine.applies(p, rec) && p.getWorld().getRegistryKey() == World.OVERWORLD && !p.isSpectator()
			&& !TransportSystem.isTraveling(p.getUuid());
	}

	public static void tick(MinecraftServer server) {
		long t = Diktatura.ticks();
		if (t % 5 == 0) {
			tickActive(server);
		}
		if (t % 20 != 0) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			PlayerRecord rec = state.player(p);
			if (!Treasury.hasLoan(rec)) {
				rec.collectorTicks = -1;
				continue;
			}
			if (rec.collectorTicks < 0) {
				rec.collectorTicks = periodTicks();
			}
			if (visiting(p.getUuid())) {
				continue;
			}
			rec.collectorTicks = Math.max(0, rec.collectorTicks - 20);
			if (rec.collectorTicks == 0 && canVisit(p, rec)) {
				rec.collectorTicks = periodTicks();
				start(p);
			}
		}
	}

	/** Выслать группу взыскания сейчас (по таймеру или командой). */
	public static boolean start(ServerPlayerEntity p) {
		DiktaturaState state = DiktaturaState.get(p.getServer());
		PlayerRecord rec = state.player(p);
		if (!Treasury.hasLoan(rec) || visiting(p.getUuid()) || NpcRegistry.get(COLLECTOR_ID) == null) {
			return false;
		}
		ServerWorld world = p.getServerWorld();
		int debt = Treasury.debt(rec);
		int limit = Treasury.limit(rec);
		boolean vip = limit > 0 && debt * 2 >= limit && NpcRegistry.get(VIP_ID) != null;
		BlockPos addr = Registration.address(rec);
		boolean atAddress = addr != null && world.isChunkLoaded(addr.getX() >> 4, addr.getZ() >> 4) && p.squaredDistanceTo(addr.toCenterPos()) <= 64 * 64;
		Random rnd = p.getRandom();
		BlockPos spot = atAddress ? spotNear(world, addr, 1, 3, rnd) : spotNear(world, p.getBlockPos(), 5, 7, rnd);
		Group g = new Group(p.getUuid(), vip, atAddress);
		g.demand = Math.max(1, MathHelper.ceil(debt * DEMAND_PERCENT / 100.0));
		NpcEntity lead = spawn(world, vip ? VIP_ID : COLLECTOR_ID, spot, g);
		if (lead == null) {
			return false;
		}
		g.lead = lead.getUuid();
		if (vip) {
			spawn(world, COLLECTOR_ID, spot.add(1, 0, 1), g);
		}
		BY_PLAYER.put(p.getUuid(), g);
		rec.collectorVisits++;
		state.markDirty();
		if (atAddress) {
			lead.gesture(NpcEntity.KNOCK);
			world.playSound(null, spot, SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.HOSTILE, 0.7f, 1.2f);
		}
		Text hint = atAddress ? Text.translatable("diktatura.collector.notice.address", addr.getX(), addr.getY(), addr.getZ())
			: addr == null ? Text.translatable("diktatura.collector.notice.unregistered")
			: Text.translatable("diktatura.collector.notice.away");
		Hud.alert(p, AlertKind.DANGER, Text.translatable(vip ? "diktatura.collector.notice.vip" : "diktatura.collector.notice"),
			hint, 8);
		NpcRegistry.NpcData d = NpcRegistry.get(vip ? VIP_ID : COLLECTOR_ID);
		Voice.say(lead, d.displayName(), d.phrase(rnd.nextInt(d.phraseCount())), d.soundEvent(), d.pitch());
		return true;
	}

	private static NpcEntity spawn(ServerWorld world, String id, BlockPos spot, Group g) {
		NpcEntity npc = ModEntities.NPC.create(world);
		if (npc == null) {
			return null;
		}
		npc.setNpcId(id);
		npc.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
		NpcRegistry.apply(npc);
		world.spawnEntity(npc);
		world.spawnParticles(ParticleTypes.POOF, npc.getX(), npc.getY() + 1, npc.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		g.members.add(npc.getUuid());
		BY_NPC.put(npc.getUuid(), g.player);
		return npc;
	}

	/** Свободное место в minR..maxR блоках от точки (или сама точка, если всё занято). */
	private static BlockPos spotNear(ServerWorld world, BlockPos base, int minR, int maxR, Random rnd) {
		for (int i = 0; i < 20; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			int r = minR + rnd.nextInt(maxR - minR + 1);
			BlockPos c = base.add(MathHelper.floor(Math.cos(a) * r), 0, MathHelper.floor(Math.sin(a) * r));
			for (int dy = 2; dy >= -3; dy--) {
				BlockPos pos = c.up(dy);
				if (world.getBlockState(pos).isAir() && world.getBlockState(pos.up()).isAir()
					&& world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
					return pos;
				}
			}
		}
		return base;
	}

	private static Entity find(MinecraftServer server, UUID id) {
		for (ServerWorld w : server.getWorlds()) {
			Entity e = w.getEntity(id);
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	private static NpcRegistry.NpcData data(boolean vip) {
		return NpcRegistry.get(vip ? VIP_ID : COLLECTOR_ID);
	}

	private static void say(NpcEntity npc, Group g, String key, Object... args) {
		NpcRegistry.NpcData d = data(g.vip);
		Text name = d != null ? d.displayName() : npc.getName();
		Voice.say(npc, name, Text.translatable(key, args), d != null ? d.soundEvent() : null, d != null ? d.pitch() : 1f);
	}

	private static void tickActive(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Group>> it = BY_PLAYER.entrySet().iterator();
		while (it.hasNext()) {
			Group g = it.next().getValue();
			g.ticks += 5;
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(g.player);
			Entity e = find(server, g.lead);
			if (!(e instanceof NpcEntity lead) || lead.isRemoved() || p == null) {
				dismiss(server, g);
				it.remove();
				continue;
			}
			PlayerRecord rec = DiktaturaState.get(server).player(p);
			if (g.phase == Phase.DONE) {
				if (g.ticks >= 60) {
					dismiss(server, g);
					it.remove();
				}
				continue;
			}
			if (!Treasury.hasLoan(rec)) {
				say(lead, g, "diktatura.collector.settled");
				finish(g);
				continue;
			}
			boolean gone = p.getWorld() != lead.getWorld() || p.squaredDistanceTo(lead) > 48 * 48 || !canVisit(p, rec);
			if (gone || (g.phase == Phase.APPROACH && g.ticks > APPROACH_TICKS)) {
				if (rec.stage == com.diktatura.state.Stage.FREE) {
					int add = Treasury.penalty(p, NO_SHOW_PERCENT);
					Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.collector.no_show", add),
						Text.translatable("diktatura.collector.no_show.hint"), 6);
				}
				finish(g);
				continue;
			}
			follow(server, g, p);
			lead.getLookControl().lookAt(p, 30f, 30f);
			if (g.phase == Phase.APPROACH) {
				if (lead.squaredDistanceTo(p) <= 2.8 * 2.8) {
					g.phase = Phase.WAIT;
					g.ticks = 0;
					lead.getNavigation().stop();
					lead.gesture(NpcEntity.DEMAND);
					say(lead, g, g.vip ? "diktatura.collector.demand.vip" : "diktatura.collector.demand", g.demand);
					Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.collector.demand.title", g.demand),
						Text.translatable("diktatura.collector.demand.hint", WAIT_SECONDS), WAIT_SECONDS);
				}
			} else if (g.phase == Phase.WAIT) {
				if (g.ticks % 100 == 0) {
					lead.gesture(NpcEntity.THREATEN);
				}
				if (g.ticks >= WAIT_SECONDS * 20) {
					refuse(p, g, lead);
				}
			}
		}
	}

	/** Все в группе идут за должником; застрявший «административно» подходит сам. */
	private static void follow(MinecraftServer server, Group g, ServerPlayerEntity p) {
		double speed = g.vip ? 1.3 : 1.0;
		for (UUID id : g.members) {
			if (!(find(server, id) instanceof NpcEntity npc)) {
				continue;
			}
			double d2 = npc.squaredDistanceTo(p);
			if (d2 <= 2.4 * 2.4) {
				npc.getNavigation().stop();
				npc.getLookControl().lookAt(p, 30f, 30f);
				continue;
			}
			if (g.ticks % 20 == 0 || npc.getNavigation().isIdle()) {
				npc.getNavigation().startMovingTo(p, speed);
			}
			if (g.ticks % 300 == 0 && d2 > 6 * 6) {
				npc.refreshPositionAndAngles(p.getX() + 1.5, p.getY(), p.getZ() + 1.5, npc.getYaw(), 0f);
			}
		}
	}

	private static void finish(Group g) {
		g.phase = Phase.DONE;
		g.ticks = 0;
	}

	private static void dismiss(MinecraftServer server, Group g) {
		for (UUID id : g.members) {
			BY_NPC.remove(id);
			if (find(server, id) instanceof NpcEntity npc) {
				if (npc.getWorld() instanceof ServerWorld w) {
					w.spawnParticles(ParticleTypes.POOF, npc.getX(), npc.getY() + 1, npc.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
				}
				npc.discard();
			}
		}
		g.members.clear();
	}

	/** Отозвать группу (справка о бедности, банкротство). */
	public static void cancel(ServerPlayerEntity p) {
		Group g = BY_PLAYER.get(p.getUuid());
		if (g != null && g.phase != Phase.DONE) {
			if (find(p.getServer(), g.lead) instanceof NpcEntity lead) {
				say(lead, g, "diktatura.collector.leave");
			}
			finish(g);
		}
	}

	/** Отказ или молчание: опись (VIP — опись и арест). */
	private static void refuse(ServerPlayerEntity p, Group g, NpcEntity lead) {
		lead.gesture(NpcEntity.STAMP);
		Text seized = seize(p);
		if (seized != null) {
			say(lead, g, "diktatura.collector.seized", seized);
		} else {
			int add = Treasury.penalty(p, NO_SHOW_PERCENT);
			LoyaltySystem.change(p, -5);
			say(lead, g, "diktatura.collector.nothing", add);
		}
		if (g.vip) {
			PrisonSystem.arrest(p, 90 * 20, Text.translatable("diktatura.reason.debt"));
		}
		finish(g);
	}

	/** Опись: самый ценный предмет из инвентаря (весь стак) — в счёт долга по курсу кассы, сдачи нет. */
	private static Text seize(ServerPlayerEntity p) {
		PlayerInventory inv = p.getInventory();
		double rate = LoanSettings.get().itemRate();
		int best = -1;
		double bestValue = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack st = inv.getStack(i);
			if (st.isEmpty() || st.isOf(ModItems.LAW_BOOK) || st.isOf(LoanBlocks.ADDRESS_PLATE_ITEM)) {
				continue;
			}
			double v = ItemValues.value(st);
			if (v >= rate && v > bestValue) {
				best = i;
				bestValue = v;
			}
		}
		if (best < 0) {
			return null;
		}
		ItemStack st = inv.getStack(best);
		Text name = Text.literal(st.getCount() + " × ").append(st.getName());
		inv.setStack(best, ItemStack.EMPTY);
		int worth = Treasury.writeOff(p, (int) Math.floor(bestValue / rate));
		Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.collector.seized.title"),
			Text.translatable("diktatura.collector.seized.hint", name, worth), 8);
		return name;
	}

	/** Платёж в окне коллектора: уменьшает требование; выполнено — группа уходит. */
	private static void paid(ServerPlayerEntity p, Group g, NpcEntity lead, int amount) {
		if (amount <= 0) {
			return;
		}
		g.demand -= amount;
		PlayerRecord rec = DiktaturaState.get(p.getServer()).player(p);
		if (g.demand <= 0 || !Treasury.hasLoan(rec)) {
			lead.gesture(NpcEntity.TALK);
			say(lead, g, "diktatura.collector.thanks", LoanSettings.get().collectorMinutes());
			finish(g);
		} else {
			say(lead, g, "diktatura.collector.more", g.demand);
		}
	}

	private static void onAttack(ServerPlayerEntity p, NpcEntity npc) {
		ServerWorld world = p.getServerWorld();
		world.sendEntityDamage(npc, world.getDamageSources().playerAttack(p));
		long now = Diktatura.ticks();
		Long last = LAST_ASSAULT.get(p.getUuid());
		if (last != null && now - last < 40) {
			return;
		}
		LAST_ASSAULT.put(p.getUuid(), now);
		UUID debtor = BY_NPC.get(npc.getUuid());
		Group g = debtor == null ? null : BY_PLAYER.get(debtor);
		if (g == null || g.phase == Phase.DONE) {
			return;
		}
		npc.gesture(NpcEntity.THREATEN);
		ServerPlayerEntity target = p.getServer().getPlayerManager().getPlayer(debtor);
		if (target == null) {
			return;
		}
		int add = Treasury.penalty(target, RESIST_PERCENT);
		say(npc, g, "diktatura.collector.hit", add);
		Hud.alert(target, AlertKind.DANGER, Text.translatable("diktatura.collector.resist", add),
			Text.translatable("diktatura.collector.resist.hint"), 6);
		if (g.vip && target == p) {
			npc.gesture(NpcEntity.STAMP);
			PrisonSystem.arrest(p, 90 * 20, Text.translatable("diktatura.reason.collector_assault"));
			finish(g);
		}
	}

	// ================================================================== справка о бедности

	/** Предъявить справку о бедности. true — справка принята (расходуется). */
	public static boolean useCertificate(ServerPlayerEntity p) {
		DiktaturaState state = DiktaturaState.get(p.getServer());
		PlayerRecord rec = state.player(p);
		if (!Treasury.hasLoan(rec)) {
			Hud.alert(p, AlertKind.INFO, Text.translatable("diktatura.certificate.no_debt"), Text.empty(), 4);
			return false;
		}
		if (rec.certificateDay == rec.loanDays) {
			Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.certificate.used"),
				Text.translatable("diktatura.certificate.used.hint"), 5);
			return false;
		}
		rec.certificateDay = rec.loanDays;
		rec.collectorTicks = Math.max(0, rec.collectorTicks) + CERTIFICATE_TICKS;
		state.markDirty();
		cancel(p);
		Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.certificate.accepted"),
			Text.translatable("diktatura.certificate.accepted.hint", minutesToVisit(rec)), 6);
		return true;
	}

	// ================================================================== окно коллектора

	private static final class CollectorHandler implements NpcInteractions.Handler {
		@Override
		public boolean matches(Entity entity) {
			return entity instanceof NpcEntity n && BY_NPC.containsKey(n.getUuid());
		}

		@Override
		public UiPayloads.NpcScreen screen(ServerPlayerEntity player, Entity npc) {
			UUID debtor = BY_NPC.get(npc.getUuid());
			Group g = debtor == null ? null : BY_PLAYER.get(debtor);
			boolean vip = g != null && g.vip;
			NpcRegistry.NpcData d = data(vip);
			List<Text> info = new ArrayList<>();
			List<UiPayloads.NpcAction> actions = new ArrayList<>();
			Text line;
			if (g == null || !player.getUuid().equals(g.player) || g.phase == Phase.DONE) {
				line = Text.translatable("diktatura.collector.not_you");
			} else {
				PlayerRecord rec = DiktaturaState.get(player.getServer()).player(player);
				line = Text.translatable(vip ? "diktatura.collector.demand.vip" : "diktatura.collector.demand", g.demand);
				info.add(Text.translatable("diktatura.loan.info.debt", Treasury.debt(rec), Treasury.limit(rec)).formatted(Formatting.RED));
				info.add(Text.translatable("diktatura.collector.info.demand", g.demand));
				info.add(Text.translatable("diktatura.job.balance", rec.trudodni));
				if (g.phase == Phase.WAIT) {
					info.add(Text.translatable("diktatura.collector.info.time", Math.max(0, WAIT_SECONDS - g.ticks / 20)));
				}
				info.add(Text.translatable(vip ? "diktatura.collector.info.refuse.vip" : "diktatura.collector.info.refuse")
					.formatted(Formatting.DARK_GRAY));
				int balance = Math.max(0, rec.trudodni);
				int part = Math.min(balance, g.demand);
				actions.add(new UiPayloads.NpcAction("pay", Text.translatable(part >= g.demand
					? "diktatura.collector.pay" : "diktatura.collector.pay_part", part), part > 0));
				int hand = Treasury.handValue(player);
				actions.add(new UiPayloads.NpcAction("item", Text.translatable("diktatura.loan.pay_item",
					Math.min(hand, Treasury.debt(rec))), hand > 0));
				actions.add(new UiPayloads.NpcAction("refuse", Text.translatable("diktatura.collector.refuse"), true));
			}
			actions.add(new UiPayloads.NpcAction("client:close", Text.translatable("diktatura.npc.leave"), true));
			return new UiPayloads.NpcScreen("collector", npc.getId(),
				d != null ? d.displayName() : npc.getName(),
				d != null ? Text.translatable(d.role()) : Text.empty(), line, info, actions,
				d != null ? d.accent() : 0xFF1E1C20);
		}

		@Override
		public boolean action(ServerPlayerEntity player, Entity npc, String action) {
			Group g = BY_PLAYER.get(player.getUuid());
			if (g == null || g.phase == Phase.DONE || !BY_NPC.containsKey(npc.getUuid())
				|| !(find(player.getServer(), g.lead) instanceof NpcEntity lead)) {
				return true;
			}
			switch (action) {
				case "pay" -> paid(player, g, lead, Treasury.pay(player, g.demand));
				case "item" -> paid(player, g, lead, Treasury.payWithItem(player));
				case "refuse" -> refuse(player, g, lead);
				default -> {
					return false;
				}
			}
			return true;
		}

		@Override
		public void fallback(ServerPlayerEntity player, Entity npc) {
			player.sendMessage(Text.translatable("diktatura.loan.need_mod").formatted(Formatting.GRAY), false);
		}
	}
}
