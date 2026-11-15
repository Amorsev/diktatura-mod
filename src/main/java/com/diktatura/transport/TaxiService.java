package com.diktatura.transport;

import com.diktatura.config.DiktaturaConfig;
import com.diktatura.dictator.GrandThroneHall;
import com.diktatura.economy.Economy;
import com.diktatura.job.JobRegistry;
import com.diktatura.job.Jobs;
import com.diktatura.network.UiPayloads;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Гостакси до горы вождя: заказ из книги законов (вкладка «Такси»). Цена фиксированная по классу
 * (3 / 7 / 10 трудодней в настройках), при лояльности ниже 50 — надбавка за неблагонадёжность
 * +10 / +20 / +30 % (пороги 35 и 20), округление вверх. Везёт только к площади перед тронным залом,
 * обратно — своим ходом. Выйти можно на ходу, деньги не возвращаются.
 */
public final class TaxiService {
	/** Ближе этого к площади такси не нужно. */
	private static final double MIN_DISTANCE = 24;
	/** Игроки, вышедшие из игры во время поездки: при входе ставим на землю. */
	private static final Set<UUID> DROPPED = new HashSet<>();

	private TaxiService() {
	}

	/** Надбавка за низкую лояльность, в процентах. */
	public static int surcharge(int loyalty) {
		if (loyalty >= 50) {
			return 0;
		}
		if (loyalty >= 35) {
			return 10;
		}
		return loyalty >= 20 ? 20 : 30;
	}

	public static int price(TaxiClass c, int loyalty) {
		int base = Math.max(0, c.basePrice());
		return (base * (100 + surcharge(loyalty)) + 99) / 100;
	}

	/** Куда везёт такси: проспект у портика тронного зала на плато. null — столицы нет. */
	public static Vec3d target(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		if (!state.capitalGenerated || !state.throneGenerated) {
			return null;
		}
		BlockPos hall = state.throneOrigin;
		int cx = hall.getX() + GrandThroneHall.SIZE_X / 2;
		return new Vec3d(cx - 20 + 0.5, hall.getY() + 1, hall.getZ() + GrandThroneHall.DOOR_Z + 0.5);
	}

	/** Почему нельзя заказать прямо сейчас (ключ перевода) или null. */
	private static String blockReason(MinecraftServer server, ServerPlayerEntity p, Vec3d target) {
		if (target == null) {
			return "diktatura.taxi.no_capital";
		}
		PlayerRecord rec = DiktaturaState.get(server).player(p);
		if (rec.stage != Stage.FREE) {
			return "diktatura.taxi.busy.arrested";
		}
		if (TransportSystem.isTraveling(p.getUuid())) {
			return "diktatura.taxi.busy.traveling";
		}
		if (rec.minigame >= 0) {
			return "diktatura.taxi.busy.arrested";
		}
		JobRegistry.JobDef job = Jobs.def(rec);
		if (job != null && job.type() == JobRegistry.Type.BUILDER) {
			return "diktatura.taxi.busy.job";
		}
		if (p.getWorld().getRegistryKey() == World.OVERWORLD
			&& p.getPos().squaredDistanceTo(target.x, p.getY(), target.z) < MIN_DISTANCE * MIN_DISTANCE) {
			return "diktatura.taxi.already_here";
		}
		return null;
	}

	/** Примерное время в пути, секунды. */
	private static int eta(ServerPlayerEntity p, Vec3d target, TaxiClass c) {
		if (target == null) {
			return 0;
		}
		boolean sameDim = p.getWorld().getRegistryKey() == World.OVERWORLD;
		double d = Math.sqrt(p.getPos().squaredDistanceTo(target.x, p.getY(), target.z));
		int max = DiktaturaConfig.get().transportMaxDirectDistance;
		double ticks = !sameDim || d > max ? 40 + 60 / c.speed : d / c.speed;
		return Math.max(1, MathHelper.ceil(ticks / 20.0));
	}

	/** Раз в секунду: цены и доступность для вкладки «Такси». */
	public static void sendInfo(MinecraftServer server, ServerPlayerEntity p) {
		if (!ServerPlayNetworking.canSend(p, UiPayloads.TaxiInfo.ID)) {
			return;
		}
		int loyalty = DiktaturaState.get(server).player(p).loyalty;
		Vec3d target = target(server);
		String reason = blockReason(server, p, target);
		List<Integer> prices = new ArrayList<>();
		List<Integer> seconds = new ArrayList<>();
		for (TaxiClass c : TaxiClass.values()) {
			prices.add(price(c, loyalty));
			seconds.add(eta(p, target, c));
		}
		ServerPlayNetworking.send(p, new UiPayloads.TaxiInfo(reason == null ? "" : reason, surcharge(loyalty), prices, seconds));
	}

	/** Игрок нажал «Заказать». Сервер всё проверяет заново: клиенту не верим. */
	public static void order(ServerPlayerEntity p, UiPayloads.TaxiOrder payload) {
		MinecraftServer server = p.getServer();
		if (server == null || payload.taxiClass() < 0 || payload.taxiClass() >= TaxiClass.values().length) {
			return;
		}
		TaxiClass c = TaxiClass.byOrdinal(payload.taxiClass());
		Vec3d target = target(server);
		String reason = blockReason(server, p, target);
		if (reason != null) {
			p.sendMessage(Text.translatable(reason).formatted(Formatting.RED), false);
			return;
		}
		int loyalty = DiktaturaState.get(server).player(p).loyalty;
		int price = price(c, loyalty);
		if (!Economy.take(p, price, "diktatura.money.reason.taxi", c.nameKey())) {
			p.sendMessage(Text.translatable("diktatura.taxi.no_money", price, Economy.balance(p)).formatted(Formatting.RED), false);
			return;
		}
		boolean breakdown = c == TaxiClass.ECONOMY && p.getRandom().nextDouble() < DiktaturaConfig.get().taxiBreakdownChance;
		TransportSystem.sendTaxi(p, World.OVERWORLD, target, c, breakdown, arrived -> {
			arrived.sendMessage(Text.translatable("diktatura.taxi.arrived").formatted(Formatting.GOLD), false);
			arrived.getServerWorld().playSound(null, arrived.getX(), arrived.getY(), arrived.getZ(),
				SoundEvents.ENTITY_HORSE_AMBIENT, SoundCategory.NEUTRAL, 0.8f, 1.0f);
		});
	}

	/** Поездку прервал выход из игры. */
	static void markDropped(UUID player) {
		DROPPED.add(player);
	}

	/** Вошёл после выхода посреди поездки: поставить на землю (карета летела над рельефом). */
	public static void onJoin(ServerPlayerEntity p) {
		if (DROPPED.remove(p.getUuid())) {
			placeOnGround(p);
		}
	}

	static void placeOnGround(ServerPlayerEntity p) {
		ServerWorld w = p.getServerWorld();
		p.fallDistance = 0;
		if (w.getDimension().hasCeiling()) {
			return; // в Незере «верх» — потолок из бедрока
		}
		int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(p.getX()), MathHelper.floor(p.getZ()));
		com.diktatura.util.Teleports.to(p, w, p.getX(), top, p.getZ(), p.getYaw(), p.getPitch());
		p.fallDistance = 0;
	}
}
