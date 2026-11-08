package com.diktatura.license;

import com.diktatura.Diktatura;
import com.diktatura.economy.Economy;
import com.diktatura.job.JobRegistry;
import com.diktatura.job.Jobs;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import com.diktatura.ui.Hud;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Лицензируемые занятия: рубка леса, добыча руды, выезд в Незер и Край, купание. Они требуют
 * лицензии всегда — это не законы, вождь их не вводит и не отменяет. Игрок узнаёт о них, только
 * нарушив: первое нарушение открывает скрытый раздел книги законов «Лицензируемые занятия»,
 * и каждое новое нарушение добавляет туда запись о своей лицензии.
 * Без лицензии — штраф в трудоднях (нечем платить — минус лояльность). Игорная лицензия по-прежнему
 * работает через закон «Азартные игры — только по лицензии».
 */
public final class LicensedActivities {
	/** Занятие: какая лицензия нужна, штраф, как часто штрафовать, какая работа освобождает. */
	public record Activity(String id, String license, int fine, int cooldownTicks, JobRegistry.Type job) {
		public String key() {
			return "diktatura.activity." + id;
		}
	}

	public static final Activity FOREST = new Activity("forest", "diktatura:forest", 3, 20 * 20, JobRegistry.Type.LUMBERJACK);
	public static final Activity MINING = new Activity("mining", "diktatura:mining", 4, 20 * 15, JobRegistry.Type.MINER);
	public static final Activity PORTAL = new Activity("portal", "diktatura:portal", 15, 20 * 5, null);
	public static final Activity WATER = new Activity("water", "diktatura:water", 2, 20 * 30, null);
	public static final List<Activity> ALL = List.of(FOREST, MINING, PORTAL, WATER);

	private static final Map<String, Long> LAST = new HashMap<>();

	private LicensedActivities() {
	}

	public static Activity byId(String id) {
		for (Activity a : ALL) {
			if (a.id().equals(id)) {
				return a;
			}
		}
		return null;
	}

	public static void registerEvents() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
			if (!(world instanceof ServerWorld w) || !(player instanceof ServerPlayerEntity p) || p.isCreative()) {
				return;
			}
			if (state.isIn(BlockTags.LOGS) && isTree(w, pos)) {
				violate(p, FOREST);
			} else if (isOre(state)) {
				violate(p, MINING);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			if (destination.getRegistryKey() == World.NETHER || destination.getRegistryKey() == World.END) {
				violate(player, PORTAL);
			}
		});
	}

	public static boolean isOre(BlockState s) {
		return s.isIn(BlockTags.COAL_ORES) || s.isIn(BlockTags.IRON_ORES) || s.isIn(BlockTags.COPPER_ORES)
			|| s.isIn(BlockTags.GOLD_ORES) || s.isIn(BlockTags.REDSTONE_ORES) || s.isIn(BlockTags.LAPIS_ORES)
			|| s.isIn(BlockTags.DIAMOND_ORES) || s.isIn(BlockTags.EMERALD_ORES)
			|| s.isOf(net.minecraft.block.Blocks.NETHER_QUARTZ_ORE) || s.isOf(net.minecraft.block.Blocks.ANCIENT_DEBRIS);
	}

	/** Настоящее дерево: рядом с бревном есть природная (не поставленная игроком) листва. */
	public static boolean isTree(ServerWorld w, BlockPos log) {
		for (BlockPos n : BlockPos.iterate(log.add(-2, -1, -2), log.add(2, 3, 2))) {
			BlockState s = w.getBlockState(n);
			if (s.isIn(BlockTags.LEAVES) && !(s.contains(LeavesBlock.PERSISTENT) && s.get(LeavesBlock.PERSISTENT))) {
				return true;
			}
		}
		return false;
	}

	/** Купание без водной лицензии: раз в секунду проверка, слабость и штраф раз в 30 секунд. */
	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % 20 != 3) {
			return;
		}
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (!p.isCreative() && !p.isSpectator() && (p.isSubmergedInWater() || p.isSwimming()) && !p.hasVehicle()) {
				if (violate(p, WATER)) {
					p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 20 * 10, 0, false, true));
				}
			}
		}
	}

	/** Нарушение лицензируемого занятия. true — наказание применено (лицензии нет). */
	public static boolean violate(ServerPlayerEntity p, Activity a) {
		MinecraftServer server = p.getServer();
		if (server == null) {
			return false;
		}
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(p);
		if (rec.stage != Stage.FREE || covered(server, rec, a)) {
			return false;
		}
		boolean first = rec.activities.isEmpty();
		boolean isNew = !rec.activities.contains(a.id());
		if (isNew) {
			rec.activities.putLong(a.id(), server.getOverworld().getTime());
			state.markDirty();
			Hud.alert(p, AlertKind.INFO,
				Text.translatable(first ? "diktatura.activity.unlocked" : "diktatura.activity.new"),
				Text.translatable("diktatura.activity.new.hint", Text.translatable(a.key())), 7);
			Licenses.sendList(server, p);
		}
		String key = p.getUuid() + ":" + a.id();
		long now = Diktatura.ticks();
		Long last = LAST.get(key);
		if (last != null && now - last < a.cooldownTicks()) {
			return true;
		}
		LAST.put(key, now);
		LicenseRegistry.LicenseDef def = LicenseRegistry.get(a.license());
		Text license = def == null ? Text.literal(a.license()) : Text.translatable(def.name());
		if (Economy.take(p, a.fine(), "diktatura.money.reason.activity", a.key())) {
			p.sendMessage(Text.translatable("diktatura.activity.fined", license, a.fine()).formatted(Formatting.RED), true);
		} else {
			rec.loyalty = Math.max(0, rec.loyalty - 3);
			state.markDirty();
			p.sendMessage(Text.translatable("diktatura.activity.broke", license).formatted(Formatting.RED), true);
		}
		return true;
	}

	private static boolean covered(MinecraftServer server, PlayerRecord rec, Activity a) {
		if (Licenses.has(server, rec, a.license())) {
			return true;
		}
		if (a.job() != null) {
			var def = Jobs.def(rec);
			return def != null && def.type() == a.job();
		}
		return false;
	}

	public static void forget(UUID player) {
		LAST.keySet().removeIf(k -> k.startsWith(player.toString()));
	}
}
