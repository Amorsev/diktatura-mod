package com.diktatura.office;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.job.Jobs;
import com.diktatura.law.LawEngine;
import com.diktatura.license.Licenses;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.NpcEntity;
import com.diktatura.npc.NpcPosts;
import com.diktatura.npc.NpcRegistry;
import com.diktatura.state.DiktaturaState;
import com.diktatura.ui.Hud;
import com.diktatura.ui.Voice;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.Locale;

/**
 * Кабинеты чиновников и их «гибкий график»: чиновник появляется за столом изредка и ненадолго
 * (officeOpenMin..MaxSeconds), потом пропадает (officeClosedMin..MaxMinutes). У каждого свой
 * случайный график. Режим виден на табличке и индикаторе у двери, открытие и закрытие
 * объявляются всем. Расписание хранится в мире.
 */
public final class Offices {
	public enum Office {
		EMPLOYER(Jobs.EMPLOYER_ID, 0),
		LICENSOR(Licenses.LICENSOR_ID, 1);

		public final String npcId;
		public final int cabinet;

		Office(String npcId, int cabinet) {
			this.npcId = npcId;
			this.cabinet = cabinet;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Office byKey(String key) {
			for (Office o : values()) {
				if (o.key().equals(key)) {
					return o;
				}
			}
			return null;
		}
	}

	/** Последнее, что написано на табличке (чтобы не переписывать каждую секунду). */
	private static final String[] SIGN_CACHE = new String[Office.values().length];

	private Offices() {
	}

	public static void registerEvents() {
		// Чиновник, оказавшийся в мире в нерабочее время (чанк был выгружен), — уходит.
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof NpcEntity npc)) {
				return;
			}
			for (Office o : Office.values()) {
				if (o.npcId.equals(npc.npcId())) {
					LawEngine.defer(() -> {
						MinecraftServer server = world.getServer();
						if (!npc.isRemoved() && server != null && !isOpen(server, o)) {
							npc.discard();
						}
					});
				}
			}
		});
	}

	public static boolean isOpen(MinecraftServer server, Office o) {
		return DiktaturaState.get(server).officeOpen[o.ordinal()];
	}

	/** Сколько секунд осталось до смены режима. */
	public static int secondsLeft(MinecraftServer server, Office o) {
		return Math.max(0, DiktaturaState.get(server).officeTicks[o.ordinal()] / 20);
	}

	/** Место чиновника за столом (в мире) или null, если здания ещё нет. */
	public static BlockPos deskPos(MinecraftServer server, Office o) {
		DiktaturaState state = DiktaturaState.get(server);
		return state.officesGenerated ? state.officesOrigin.add(OfficeBuilding.desk(o.cabinet)) : null;
	}

	public static BlockPos doorPos(MinecraftServer server, Office o) {
		DiktaturaState state = DiktaturaState.get(server);
		return state.officesGenerated ? state.officesOrigin.add(OfficeBuilding.door(o.cabinet)) : null;
	}

	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % 20 != 0) {
			return;
		}
		OfficeBuilding.ensureGenerated(server);
		DiktaturaState state = DiktaturaState.get(server);
		if (!state.officesGenerated) {
			return;
		}
		if (!state.officeScheduleStarted) {
			// Первый день: оба на месте — чтобы граждане успели с ними познакомиться.
			for (Office o : Office.values()) {
				state.officeOpen[o.ordinal()] = true;
				state.officeTicks[o.ordinal()] = openTicks(server.getOverworld().getRandom()) * 2;
			}
			state.officeScheduleStarted = true;
			state.markDirty();
		}
		for (Office o : Office.values()) {
			int i = o.ordinal();
			state.officeTicks[i] -= 20;
			if (state.officeTicks[i] <= 0) {
				setOpen(server, o, !state.officeOpen[i], true);
			}
			syncNpc(server, o);
			updateSign(server, o, false);
		}
		if (Diktatura.ticks() % 200 == 0) {
			state.markDirty();
			sweepOldPosts(server);
		}
	}

	private static int openTicks(Random rnd) {
		DiktaturaConfig cfg = DiktaturaConfig.get();
		int min = Math.max(10, cfg.officeOpenMinSeconds);
		int max = Math.max(min, cfg.officeOpenMaxSeconds);
		return (min + rnd.nextInt(max - min + 1)) * 20;
	}

	private static int closedTicks(Random rnd) {
		DiktaturaConfig cfg = DiktaturaConfig.get();
		int min = Math.max(1, cfg.officeClosedMinMinutes) * 60;
		int max = Math.max(min, cfg.officeClosedMaxMinutes * 60);
		return (min + rnd.nextInt(max - min + 1)) * 20;
	}

	/** Открыть или закрыть приём (по графику или командой). */
	public static void setOpen(MinecraftServer server, Office o, boolean open, boolean announce) {
		DiktaturaState state = DiktaturaState.get(server);
		int i = o.ordinal();
		Random rnd = server.getOverworld().getRandom();
		state.officeOpen[i] = open;
		state.officeTicks[i] = open ? openTicks(rnd) : closedTicks(rnd);
		state.markDirty();
		NpcRegistry.NpcData d = NpcRegistry.get(o.npcId);
		Text name = d != null ? d.displayName() : Text.literal(o.npcId);
		Text dept = d != null ? Text.translatable(d.role()) : Text.empty();
		if (!open) {
			Entity npc = currentNpc(server, o);
			if (npc != null && d != null) {
				Voice.say(npc, name, Text.translatable("diktatura.office.leave." + rnd.nextInt(4)), d.soundEvent(), d.pitch());
			}
		}
		syncNpc(server, o);
		updateSign(server, o, true);
		if (announce) {
			int minutes = Math.max(1, (state.officeTicks[i] + 1199) / 1200);
			if (open) {
				Hud.alertAll(server, AlertKind.GOOD, Text.translatable("diktatura.office.open", name),
					Text.translatable("diktatura.office.open.hint", dept, minutes), 8);
			} else {
				Hud.alertAll(server, AlertKind.INFO, Text.translatable("diktatura.office.closed", name),
					Text.translatable("diktatura.office.closed.hint", dept), 6);
			}
		}
	}

	private static Entity currentNpc(MinecraftServer server, Office o) {
		var id = DiktaturaState.get(server).npcPosts.get(o.key());
		return id == null ? null : server.getOverworld().getEntity(id);
	}

	/** На работе — сидит за столом; не на работе — его нет. */
	private static void syncNpc(MinecraftServer server, Office o) {
		BlockPos desk = deskPos(server, o);
		if (desk == null) {
			return;
		}
		if (isOpen(server, o)) {
			NpcPosts.ensure(server, o.key(), o.npcId, desk, 90.0f);
			return;
		}
		ServerWorld world = server.getOverworld();
		if (!world.isChunkLoaded(desk.getX() >> 4, desk.getZ() >> 4)) {
			return;
		}
		Entity e = currentNpc(server, o);
		if (e != null) {
			e.discard();
		}
		for (NpcEntity n : world.getEntitiesByClass(NpcEntity.class, new Box(desk).expand(16), x -> o.npcId.equals(x.npcId()))) {
			n.discard();
		}
	}

	/** До 0.1.8 Кадров и Печаткин стояли на площадке у тронного зала — убрать их оттуда. */
	private static void sweepOldPosts(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (!state.throneGenerated) {
			return;
		}
		ServerWorld world = server.getOverworld();
		BlockPos plaza = state.throneOrigin.add(-3, 1, 6);
		if (!world.isChunkLoaded(plaza.getX() >> 4, plaza.getZ() >> 4)) {
			return;
		}
		List<NpcEntity> old = world.getEntitiesByClass(NpcEntity.class, new Box(plaza).expand(5),
			x -> Jobs.EMPLOYER_ID.equals(x.npcId()) || Licenses.LICENSOR_ID.equals(x.npcId()));
		old.forEach(Entity::discard);
	}

	/** Табличка у двери: отдел, режим, примерное время (честно предупреждаем, что неточно). */
	private static void updateSign(MinecraftServer server, Office o, boolean force) {
		DiktaturaState state = DiktaturaState.get(server);
		ServerWorld world = server.getOverworld();
		BlockPos signPos = state.officesOrigin.add(OfficeBuilding.sign(o.cabinet));
		BlockPos lampPos = state.officesOrigin.add(OfficeBuilding.lamp(o.cabinet));
		if (!world.isChunkLoaded(signPos.getX() >> 4, signPos.getZ() >> 4)) {
			return;
		}
		// Табличку могла снести дорожка площади (столицы, построенные в 0.2.16–0.2.19) — ставим обратно.
		if (!(world.getBlockEntity(signPos) instanceof SignBlockEntity) && world.getBlockState(signPos).isAir()
			&& world.getBlockState(signPos.east()).isSolidBlock(world, signPos.east())) {
			world.setBlockState(signPos, Blocks.SPRUCE_WALL_SIGN.getDefaultState()
				.with(net.minecraft.block.WallSignBlock.FACING, net.minecraft.util.math.Direction.WEST), Block.NOTIFY_LISTENERS);
			force = true;
		}
		boolean open = isOpen(server, o);
		int minutes = Math.max(1, (state.officeTicks[o.ordinal()] + 1199) / 1200);
		String cache = open + ":" + minutes;
		if (!force && cache.equals(SIGN_CACHE[o.ordinal()])) {
			return;
		}
		SIGN_CACHE[o.ordinal()] = cache;
		BlockState lamp = (open ? Blocks.LIME_CONCRETE : Blocks.RED_CONCRETE).getDefaultState();
		if (!world.getBlockState(lampPos).isOf(lamp.getBlock())) {
			world.setBlockState(lampPos, lamp, Block.NOTIFY_LISTENERS);
		}
		if (world.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
			String dept = "diktatura.office.sign." + o.key();
			Text l1 = Text.translatable(open ? "diktatura.office.sign.open" : "diktatura.office.sign.closed")
				.formatted(open ? Formatting.DARK_GREEN : Formatting.DARK_RED);
			Text l2 = Text.translatable(open ? "diktatura.office.sign.open.time" : "diktatura.office.sign.closed.time", minutes);
			Text l3 = Text.translatable(open ? "diktatura.office.sign.open.note" : "diktatura.office.sign.closed.note");
			sign.changeText(t -> t.withMessage(0, Text.translatable(dept))
				.withMessage(1, l1).withMessage(2, l2).withMessage(3, l3), true);
			sign.setWaxed(true);
		}
	}

	/** Строка для команды /diktatura office. */
	public static Text status(MinecraftServer server, Office o) {
		NpcRegistry.NpcData d = NpcRegistry.get(o.npcId);
		Text dept = d != null ? Text.translatable(d.role()) : Text.literal(o.key());
		int minutes = Math.max(1, (secondsLeft(server, o) + 59) / 60);
		return isOpen(server, o)
			? Text.translatable("diktatura.office.status.open", dept, minutes).formatted(Formatting.GREEN)
			: Text.translatable("diktatura.office.status.closed", dept, minutes).formatted(Formatting.RED);
	}
}
