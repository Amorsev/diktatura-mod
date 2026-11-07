package com.diktatura.job;

import com.diktatura.Diktatura;
import com.diktatura.dictator.ThroneHall;
import com.diktatura.economy.Economy;
import com.diktatura.item.ModItems;
import com.diktatura.job.JobRegistry.JobDef;
import com.diktatura.job.JobRegistry.Type;
import com.diktatura.law.LawEngine;
import com.diktatura.network.UiPayloads;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.ModEntities;
import com.diktatura.npc.NpcEntity;
import com.diktatura.npc.NpcInteractions;
import com.diktatura.npc.NpcRegistry;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import com.diktatura.ui.Hud;
import com.diktatura.ui.Voice;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Трудовая биржа: работы за трудодни. Одна работа на игрока; прогресс и оставшееся время
 * хранятся в {@link PlayerRecord#job} и переживают перезапуск (таймер идёт, только пока игрок онлайн).
 * Работа не отменяет законы: арест срывает работу без оплаты.
 */
public final class Jobs {
	public static final String EMPLOYER_ID = "diktatura:employer";
	public static final String RECIPIENT_ID = "diktatura:recipient";
	/** Штраф за отказ от работы. */
	public static final int REFUSE_FINE = 3;

	private static final Map<UUID, Integer> LAST_PHRASE = new HashMap<>();

	private Jobs() {
	}

	// ================================================================== доступ к записи

	public static JobDef def(PlayerRecord rec) {
		return rec.job == null ? null : JobRegistry.get(rec.job.getString("id"));
	}

	/** Заголовок работы для HUD и окна: «Шахтёр: уголь». */
	public static MutableText title(JobDef def, int variant) {
		MutableText t = Text.translatable(def.name());
		String label = def.labelFor(variant);
		if (!label.isEmpty()) {
			t.append(": ").append(Text.translatable(label));
		}
		return t;
	}

	public static int progress(PlayerRecord rec) {
		return rec.job == null ? 0 : rec.job.getInt("p");
	}

	public static int goal(PlayerRecord rec) {
		JobDef d = def(rec);
		return d == null ? 1 : d.goalFor(rec.job.getInt("v"));
	}

	public static int secondsLeft(PlayerRecord rec) {
		return rec.job == null ? 0 : Math.max(0, rec.job.getInt("t") / 20);
	}

	public static BlockPos target(PlayerRecord rec) {
		return rec.job == null || !rec.job.contains("tx") ? null
			: new BlockPos(rec.job.getInt("tx"), rec.job.getInt("ty"), rec.job.getInt("tz"));
	}

	// ================================================================== события

	public static void registerEvents() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
			if (player instanceof ServerPlayerEntity sp) {
				onBlockBreak(sp, state);
			}
		});
		// Получатель без хозяина (работа закончилась, пока его чанк был выгружен) — убрать.
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof NpcEntity npc && RECIPIENT_ID.equals(npc.npcId())) {
				UUID id = npc.getUuid();
				LawEngine.defer(() -> {
					if (!npc.isRemoved() && !hasOwner(world.getServer(), id)) {
						npc.discard();
					}
				});
			}
		});
		NpcInteractions.register("employer", new EmployerHandler());
		NpcInteractions.register("recipient", new RecipientHandler());
	}

	public static void tick(MinecraftServer server) {
		long t = Diktatura.ticks();
		if (t % 20 != 0) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			PlayerRecord rec = state.player(p);
			if (rec.job == null) {
				continue;
			}
			JobDef def = def(rec);
			if (def == null) {
				clear(p, rec);
				continue;
			}
			rec.job.putInt("t", rec.job.getInt("t") - 20);
			if (def.type() == Type.BUILDER) {
				RoyalSite.tick(p, rec, def);
				if (rec.job == null) {
					continue;
				}
			}
			if (rec.job.getInt("t") <= 0) {
				timeout(p, rec, def);
			}
		}
		if (t % 100 == 0) {
			state.markDirty();
		}
	}

	// ================================================================== принять / сдать / сорвать

	public static boolean accept(ServerPlayerEntity p, JobDef def) {
		MinecraftServer server = p.getServer();
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(p);
		if (rec.job != null) {
			Hud.alert(p, AlertKind.INFO, Text.translatable("diktatura.job.already"), title(def(rec) == null ? def : def(rec),
				rec.job.getInt("v")), 4);
			return false;
		}
		if (rec.stage != Stage.FREE) {
			return false;
		}
		Random rnd = p.getRandom();
		int variant = def.type() == Type.BUILDER ? rnd.nextInt(Math.max(1, def.blueprints().size()))
			: def.variants().isEmpty() ? 0 : rnd.nextInt(def.variants().size());
		NbtCompound job = new NbtCompound();
		job.putString("id", def.id());
		job.putInt("v", variant);
		job.putInt("p", 0);
		job.putInt("t", def.minutes() * 1200);
		rec.job = job;

		MutableText info;
		switch (def.type()) {
			case COURIER -> {
				BlockPos dest = courierDestination(p, def);
				job.putInt("tx", dest.getX());
				job.putInt("ty", dest.getY());
				job.putInt("tz", dest.getZ());
				NpcEntity r = spawnRecipient(p.getServerWorld(), dest, p);
				if (r != null) {
					job.putUuid("r", r.getUuid());
				}
				p.getInventory().offerOrDrop(new ItemStack(ModItems.PARCEL));
				info = Text.translatable("diktatura.job.courier.start", dest.getX(), dest.getZ(), def.minutes());
			}
			case BUILDER -> info = RoyalSite.start(p, def, variant, job);
			default -> info = Text.translatable("diktatura.job.count.start", def.goalFor(variant),
				Text.translatable(def.labelFor(variant)), def.minutes());
		}
		state.markDirty();
		Hud.alert(p, AlertKind.INFO, Text.translatable("diktatura.job.taken", title(def, variant)), info, 8);
		p.sendMessage(Text.translatable("diktatura.job.taken", title(def, variant)).formatted(Formatting.GOLD), false);
		p.sendMessage(info.formatted(Formatting.GRAY), false);
		return true;
	}

	/** Королевская стройка улетела: заплатить и вернуть игрока. */
	static void finishBuilder(ServerPlayerEntity p) {
		PlayerRecord rec = DiktaturaState.get(p.getServer()).player(p);
		JobDef def = def(rec);
		if (def == null || def.type() != Type.BUILDER || rec.job == null) {
			return;
		}
		complete(p, rec, def, def.rewardFor(rec.job.getInt("v")));
	}

	/** Работа выполнена: заплатить и закрыть. */
	static void complete(ServerPlayerEntity p, PlayerRecord rec, JobDef def, int pay) {
		int variant = rec.job.getInt("v");
		cleanup(p, rec);
		rec.job = null;
		Economy.add(p, pay, "diktatura.money.reason.job", def.name(), false);
		Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.job.done", title(def, variant)),
			Text.translatable("diktatura.job.done.pay", pay), 6);
		markDirty(p);
	}

	private static void timeout(ServerPlayerEntity p, PlayerRecord rec, JobDef def) {
		int done = progress(rec);
		int variant = rec.job.getInt("v");
		cleanup(p, rec);
		rec.job = null;
		int units = done * def.unitReward();
		if (units > 0) {
			Economy.add(p, units, "diktatura.money.reason.job_units", def.name(), false);
		}
		if (def.lateFine() > 0) {
			Economy.add(p, -def.lateFine(), "diktatura.money.reason.late", def.name(), false);
		}
		Text hint = def.lateFine() > 0 ? Text.translatable("diktatura.job.late.fine", def.lateFine())
			: Text.translatable("diktatura.job.late.paid", units);
		Hud.alert(p, def.lateFine() > 0 ? AlertKind.DANGER : AlertKind.INFO,
			Text.translatable("diktatura.job.late", title(def, variant)), hint, 6);
		markDirty(p);
	}

	/** Отказ от работы у биржи: небольшой штраф. */
	public static void refuse(ServerPlayerEntity p) {
		PlayerRecord rec = DiktaturaState.get(p.getServer()).player(p);
		JobDef def = def(rec);
		if (rec.job == null) {
			return;
		}
		cleanup(p, rec);
		rec.job = null;
		Economy.add(p, -REFUSE_FINE, "diktatura.money.reason.refuse", def == null ? "" : def.name(), true);
		markDirty(p);
	}

	/** Арест срывает работу: без оплаты и без штрафа за опоздание. */
	public static void onArrest(ServerPlayerEntity p) {
		PlayerRecord rec = DiktaturaState.get(p.getServer()).player(p);
		if (rec.job == null) {
			return;
		}
		JobDef def = def(rec);
		cleanup(p, rec);
		rec.job = null;
		if (def != null) {
			p.sendMessage(Text.translatable("diktatura.job.failed.arrest", Text.translatable(def.name()))
				.formatted(Formatting.RED), false);
		}
		markDirty(p);
	}

	/** Отменить работу без штрафа (оператор, удалённый JSON). */
	public static void clear(ServerPlayerEntity p, PlayerRecord rec) {
		cleanup(p, rec);
		rec.job = null;
		markDirty(p);
	}

	private static void markDirty(ServerPlayerEntity p) {
		DiktaturaState.get(p.getServer()).markDirty();
	}

	/** Убрать следы работы: получателя, посылки, королевскую стройку. */
	private static void cleanup(ServerPlayerEntity p, PlayerRecord rec) {
		if (rec.job == null) {
			return;
		}
		RoyalSite.cleanup(p, rec);
		if (rec.job.containsUuid("r")) {
			UUID id = rec.job.getUuid("r");
			rec.job.remove("r");
			for (ServerWorld w : p.getServer().getWorlds()) {
				Entity e = w.getEntity(id);
				if (e != null) {
					e.discard();
				}
			}
		}
		removeParcels(p, Integer.MAX_VALUE);
	}

	private static int removeParcels(ServerPlayerEntity p, int max) {
		PlayerInventory inv = p.getInventory();
		int removed = 0;
		for (int i = 0; i < inv.size() && removed < max; i++) {
			ItemStack st = inv.getStack(i);
			if (st.isOf(ModItems.PARCEL)) {
				int n = Math.min(st.getCount(), max - removed);
				st.decrement(n);
				removed += n;
			}
		}
		return removed;
	}

	private static boolean hasParcel(ServerPlayerEntity p) {
		PlayerInventory inv = p.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			if (inv.getStack(i).isOf(ModItems.PARCEL)) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== счётные работы

	/** Засчитать в работу блоки, сломанные не самим игроком (например, упавшее дерево). */
	public static void countExtra(ServerPlayerEntity p, BlockState state, int times) {
		for (int i = 0; i < times; i++) {
			onBlockBreak(p, state);
		}
	}

	private static void onBlockBreak(ServerPlayerEntity p, BlockState state) {
		PlayerRecord rec = DiktaturaState.get(p.getServer()).player(p);
		JobDef def = def(rec);
		if (def == null || !def.type().countsBlocks()) {
			return;
		}
		int v = rec.job.getInt("v");
		if (v < 0 || v >= def.variants().size() || !def.variants().get(v).blocks().test(state)) {
			return;
		}
		int prog = rec.job.getInt("p") + 1;
		rec.job.putInt("p", prog);
		int goal = def.goalFor(v);
		if (prog >= goal) {
			complete(p, rec, def, def.rewardFor(v) + goal * def.unitReward());
		} else {
			p.sendMessage(Text.translatable("diktatura.job.progress", title(def, v), prog, goal).formatted(Formatting.GOLD), true);
		}
	}

	// ================================================================== доставщик

	private static BlockPos courierDestination(ServerPlayerEntity p, JobDef def) {
		ServerWorld world = p.getServerWorld();
		BlockPos from = employerPos(p.getServer());
		if (from == null) {
			from = p.getBlockPos();
		}
		Random rnd = p.getRandom();
		BlockPos best = null;
		for (int attempt = 0; attempt < 10; attempt++) {
			double angle = rnd.nextDouble() * Math.PI * 2;
			int dist = def.minDistance() + rnd.nextInt(Math.max(1, def.maxDistance() - def.minDistance()));
			int x = from.getX() + MathHelper.floor(Math.cos(angle) * dist);
			int z = from.getZ() + MathHelper.floor(Math.sin(angle) * dist);
			world.getChunk(x >> 4, z >> 4);
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			best = pos;
			if (world.getBlockState(pos.down()).getFluidState().isEmpty()) {
				return pos;
			}
		}
		return best;
	}

	private static NpcEntity spawnRecipient(ServerWorld world, BlockPos pos, ServerPlayerEntity owner) {
		NpcEntity npc = ModEntities.NPC.create(world);
		if (npc == null) {
			return null;
		}
		npc.setNpcId(RECIPIENT_ID);
		npc.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, owner.getRandom().nextFloat() * 360f, 0f);
		NpcRegistry.apply(npc);
		// Запоминаем получателя в работе до появления в мире: проверка «есть ли хозяин» при загрузке.
		DiktaturaState.get(owner.getServer()).player(owner).job.putUuid("r", npc.getUuid());
		world.spawnEntity(npc);
		return npc;
	}

	/** Есть ли у получателя хозяин с активной доставкой (даже если хозяин сейчас не в сети). */
	private static boolean hasOwner(MinecraftServer server, UUID recipient) {
		if (server == null) {
			return false;
		}
		for (PlayerRecord rec : DiktaturaState.get(server).players.values()) {
			NbtCompound job = rec.job;
			if (job != null && job.containsUuid("r") && job.getUuid("r").equals(recipient)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isRecipientOf(PlayerRecord rec, Entity npc) {
		return rec.job != null && rec.job.containsUuid("r") && rec.job.getUuid("r").equals(npc.getUuid());
	}

	// ================================================================== служащий биржи

	/** Откуда считать расстояние до получателя: кабинет Кадрова (или тронный зал, если кабинетов ещё нет). */
	public static BlockPos employerPos(MinecraftServer server) {
		BlockPos desk = com.diktatura.office.Offices.deskPos(server, com.diktatura.office.Offices.Office.EMPLOYER);
		if (desk != null) {
			return desk;
		}
		DiktaturaState state = DiktaturaState.get(server);
		return state.throneGenerated ? state.throneOrigin : null;
	}

	private static final class EmployerHandler implements NpcInteractions.Handler {
		@Override
		public boolean matches(Entity entity) {
			return entity instanceof NpcEntity n && EMPLOYER_ID.equals(n.npcId());
		}

		@Override
		public void onOpen(ServerPlayerEntity player, Entity npc) {
			NpcRegistry.NpcData d = NpcRegistry.get(EMPLOYER_ID);
			if (d != null) {
				int i = player.getRandom().nextInt(d.phraseCount());
				LAST_PHRASE.put(player.getUuid(), i);
				Voice.say(npc, d.displayName(), d.phrase(i), d.soundEvent(), d.pitch());
			}
		}

		@Override
		public UiPayloads.NpcScreen screen(ServerPlayerEntity player, Entity npc) {
			NpcRegistry.NpcData d = NpcRegistry.get(EMPLOYER_ID);
			PlayerRecord rec = DiktaturaState.get(player.getServer()).player(player);
			JobDef cur = def(rec);
			List<Text> info = new ArrayList<>();
			List<UiPayloads.NpcAction> actions = new ArrayList<>();
			info.add(Text.translatable("diktatura.job.balance", rec.trudodni));
			if (cur == null) {
				info.add(Text.translatable("diktatura.job.choose"));
				for (JobDef j : JobRegistry.all()) {
					Text label = Text.translatable("diktatura.job.offer", Text.translatable(j.name()), j.reward(), j.minutes());
					actions.add(new UiPayloads.NpcAction("take:" + j.id(), label, rec.stage == Stage.FREE));
				}
			} else {
				int v = rec.job.getInt("v");
				info.add(Text.translatable("diktatura.job.current", title(cur, v)));
				info.add(Text.translatable("diktatura.job.status", progress(rec), goal(rec), timeStr(secondsLeft(rec))));
				BlockPos t = target(rec);
				if (cur.type() == Type.COURIER && t != null) {
					info.add(Text.translatable("diktatura.job.address", t.getX(), t.getZ()));
				}
				actions.add(new UiPayloads.NpcAction("refuse", Text.translatable("diktatura.job.refuse", REFUSE_FINE), true));
			}
			actions.add(new UiPayloads.NpcAction("client:close", Text.translatable("diktatura.npc.leave"), true));
			Text line = d != null ? d.phrase(LAST_PHRASE.getOrDefault(player.getUuid(), 0)) : Text.empty();
			return new UiPayloads.NpcScreen("employer", npc.getId(),
				d != null ? d.displayName() : npc.getName(),
				d != null ? Text.translatable(d.role()) : Text.empty(), line, info, actions,
				d != null ? d.accent() : 0xFF5E6670);
		}

		@Override
		public boolean action(ServerPlayerEntity player, Entity npc, String action) {
			if (action.startsWith("take:")) {
				JobDef def = JobRegistry.get(action.substring(5));
				if (def != null) {
					accept(player, def);
				}
				return true;
			}
			switch (action) {
				case "refuse" -> refuse(player);
				default -> {
					return false;
				}
			}
			return true;
		}
	}

	private static String timeStr(int seconds) {
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	// ================================================================== получатель посылки

	private static final class RecipientHandler implements NpcInteractions.Handler {
		@Override
		public boolean matches(Entity entity) {
			return entity instanceof NpcEntity n && RECIPIENT_ID.equals(n.npcId());
		}

		@Override
		public UiPayloads.NpcScreen screen(ServerPlayerEntity player, Entity npc) {
			NpcRegistry.NpcData d = NpcRegistry.get(RECIPIENT_ID);
			PlayerRecord rec = DiktaturaState.get(player.getServer()).player(player);
			boolean mine = isRecipientOf(rec, npc);
			List<UiPayloads.NpcAction> actions = new ArrayList<>();
			Text line;
			if (mine) {
				boolean has = hasParcel(player);
				line = Text.translatable(has ? "npc.diktatura.recipient.waiting" : "npc.diktatura.recipient.no_parcel");
				actions.add(new UiPayloads.NpcAction("deliver", Text.translatable("diktatura.job.deliver"), has));
			} else {
				line = d != null ? d.phrase(player.getRandom().nextInt(d.phraseCount())) : Text.empty();
			}
			actions.add(new UiPayloads.NpcAction("client:close", Text.translatable("diktatura.npc.leave"), true));
			return new UiPayloads.NpcScreen("recipient", npc.getId(),
				d != null ? d.displayName() : npc.getName(),
				d != null ? Text.translatable(d.role()) : Text.empty(), line,
				List.of(Text.translatable(mine ? "diktatura.job.recipient.yours" : "diktatura.job.recipient.not_yours")),
				actions, d != null ? d.accent() : 0xFF2F5E9E);
		}

		@Override
		public boolean action(ServerPlayerEntity player, Entity npc, String action) {
			if (!"deliver".equals(action)) {
				return false;
			}
			PlayerRecord rec = DiktaturaState.get(player.getServer()).player(player);
			JobDef def = def(rec);
			if (def == null || def.type() != Type.COURIER || !isRecipientOf(rec, npc) || removeParcels(player, 1) < 1) {
				return true;
			}
			NpcRegistry.NpcData d = NpcRegistry.get(RECIPIENT_ID);
			Voice.say(npc, d != null ? d.displayName() : npc.getName(), Text.translatable("npc.diktatura.recipient.thanks"),
				d != null ? d.soundEvent() : null, d != null ? d.pitch() : 1.0f);
			complete(player, rec, def, def.reward());
			return false;
		}
	}
}
