package com.diktatura.transport;

import com.diktatura.util.Teleports;
import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * TransportSystem: скоростная повозка и госТакси. Повозка без гравитации летит над рельефом от
 * точки задержания к тюрьме и обратно, пассажира нельзя высадить. Если цель далеко или в
 * другом измерении, повозка взлетает, «прыгает» к цели и прилетает уже на месте.
 *
 * <p>Такси ({@link TaxiService}) едет так же, но со своей скоростью; выйти можно на ходу (поездка
 * заканчивается, деньги не возвращаются), а телега-«эконом» иногда ломается за 50 блоков до цели.
 */
public final class TransportSystem {
	public static final String WAGON_TAG = "diktatura_wagon";
	private static final int DEPART_TICKS = 40;
	private static final int MAX_TICKS = 20 * 180;
	private static final double BREAKDOWN_DISTANCE = 50;
	private static final Map<UUID, Journey> journeys = new HashMap<>();

	private TransportSystem() {
	}

	private static final class Journey {
		final UUID player;
		final RegistryKey<World> dim;
		final Vec3d target;
		final Consumer<ServerPlayerEntity> onArrive;
		final boolean hop;
		/** Класс такси или null для тюремной повозки. */
		final TaxiClass taxi;
		/** Телега сломается за {@link #BREAKDOWN_DISTANCE} блоков до цели. */
		final boolean breakdown;
		/** Откуда выехали (для выхода до «прыжка»). */
		final RegistryKey<World> startDim;
		final Vec3d start;
		boolean hopped;
		WagonEntity cart;
		int ticks;
		/** Курс (радианы, как atan2(dz, dx)); NaN — ещё не выбран. Поворачивает плавно, с ограниченной скоростью. */
		double heading = Double.NaN;
		/** Текущая скорость, блоков за тик: плавный разгон и торможение. */
		double vel = 0.08;
		/** Пройдено блоков — по нему «виляет» дорога. */
		double traveled;
		/** Своя «дорога» у каждой поездки. */
		final double seed = Math.random() * Math.PI * 2;

		Journey(UUID player, RegistryKey<World> dim, Vec3d target, Consumer<ServerPlayerEntity> onArrive, boolean hop,
				TaxiClass taxi, boolean breakdown, RegistryKey<World> startDim, Vec3d start) {
			this.player = player;
			this.dim = dim;
			this.target = target;
			this.onArrive = onArrive;
			this.hop = hop;
			this.taxi = taxi;
			this.breakdown = breakdown;
			this.startDim = startDim;
			this.start = start;
		}

		double speed() {
			return taxi != null ? taxi.speed * DiktaturaConfig.get().taxiSpeedMultiplier : DiktaturaConfig.get().transportSpeed;
		}
	}

	public static boolean isTraveling(UUID player) {
		return journeys.containsKey(player);
	}

	public static void send(ServerPlayerEntity player, RegistryKey<World> dim, Vec3d target, Consumer<ServerPlayerEntity> onArrive) {
		start(player, dim, target, onArrive, null, false);
		player.sendMessage(Text.translatable("diktatura.transport.depart").formatted(Formatting.GOLD), true);
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.0f, 1.2f);
	}

	/** Поездка на госТакси (оплату и проверки делает {@link TaxiService}). */
	public static void sendTaxi(ServerPlayerEntity player, RegistryKey<World> dim, Vec3d target, TaxiClass taxi,
								boolean breakdown, Consumer<ServerPlayerEntity> onArrive) {
		start(player, dim, target, onArrive, taxi, breakdown);
		player.sendMessage(Text.translatable("diktatura.taxi.depart", Text.translatable(taxi.nameKey())).formatted(Formatting.GOLD), true);
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.ENTITY_HORSE_AMBIENT, SoundCategory.NEUTRAL, 1.0f, 1.0f);
	}

	private static void start(ServerPlayerEntity player, RegistryKey<World> dim, Vec3d target, Consumer<ServerPlayerEntity> onArrive,
							  TaxiClass taxi, boolean breakdown) {
		cancel(player);
		boolean sameDim = player.getWorld().getRegistryKey().equals(dim);
		double dx = target.x - player.getX();
		double dz = target.z - player.getZ();
		int max = DiktaturaConfig.get().transportMaxDirectDistance;
		boolean hop = !sameDim || dx * dx + dz * dz > (double) max * max;

		Journey j = new Journey(player.getUuid(), dim, target, onArrive, hop, taxi, breakdown,
			player.getWorld().getRegistryKey(), player.getPos());
		spawnCart(j, player.getServerWorld(), player.getPos(), sameDim ? yawTo(dx, dz) : player.getYaw());
		player.startRiding(j.cart, true);
		journeys.put(player.getUuid(), j);
	}

	/** Поворот «мордой» по направлению (dx, dz): 0 — на юг (+Z), как у мобов. */
	private static float yawTo(double dx, double dz) {
		return (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
	}

	/**
	 * Поворот задаётся до появления в мире: пакет о появлении уходит игрокам сразу, и с ним
	 * клиент получает начальное направление. Дальше клиент поворачивает повозку по ходу движения.
	 */
	private static void spawnCart(Journey j, ServerWorld world, Vec3d pos, float yaw) {
		WagonEntity cart = j.taxi != null ? com.diktatura.npc.ModEntities.TAXI.create(world)
			: com.diktatura.npc.ModEntities.WAGON.create(world);
		if (cart == null) {
			return;
		}
		cart.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0.0f);
		cart.addCommandTag(WAGON_TAG);
		if (cart instanceof TaxiEntity taxi) {
			taxi.setTaxiClass(j.taxi);
			cart.setCustomName(Text.translatable(j.taxi.nameKey()));
		} else {
			cart.setCustomName(Text.translatable("entity.diktatura.wagon"));
		}
		world.spawnEntity(cart);
		j.cart = cart;
	}

	/** Прервать поездку без вызова onArrive (стадию игрока решает вызывающий). */
	public static void cancel(ServerPlayerEntity player) {
		Journey j = journeys.remove(player.getUuid());
		if (j != null) {
			player.stopRiding();
			discard(j);
		}
	}

	private static void discard(Journey j) {
		if (j.cart != null && !j.cart.isRemoved()) {
			j.cart.removeAllPassengers();
			j.cart.discard();
		}
		j.cart = null;
	}

	/** Сервер останавливается: всех пассажиров сразу высадить на месте назначения. */
	public static void shutdown(MinecraftServer server) {
		for (Journey j : new ArrayList<>(journeys.values())) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(j.player);
			if (p != null) {
				arrive(server, j, p);
			} else {
				journeys.remove(j.player);
				discard(j);
			}
		}
		journeys.clear();
	}

	public static void tick(MinecraftServer server) {
		for (Journey j : new ArrayList<>(journeys.values())) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(j.player);
			if (p == null) {
				// Игрок вышел: стадия в сохранении, при входе PrisonSystem.onJoin всё доделает.
				journeys.remove(j.player);
				discard(j);
				if (j.taxi != null) {
					TaxiService.markDropped(j.player);
				}
				continue;
			}
			try {
				step(server, j, p);
			} catch (Exception e) {
				Diktatura.LOGGER.error("Ошибка повозки, высаживаю на месте назначения", e);
				arrive(server, j, p);
			}
		}
		if (Diktatura.ticks() % 200 == 0) {
			cleanupStrayWagons(server);
		}
	}

	private static void step(MinecraftServer server, Journey j, ServerPlayerEntity p) {
		j.ticks++;
		if (j.cart == null || j.cart.isRemoved()) {
			spawnCart(j, p.getServerWorld(), p.getPos(), yawTo(j.target.x - p.getX(), j.target.z - p.getZ()));
			if (j.cart == null) {
				arrive(server, j, p);
				return;
			}
		}
		if (p.getVehicle() != j.cart) {
			if (j.taxi != null && j.ticks > 5) {
				leaveTaxi(server, j, p);
				return;
			}
			p.startRiding(j.cart, true);
		}
		ServerWorld world = (ServerWorld) j.cart.getWorld();
		Vec3d pos = j.cart.getPos();

		if (j.ticks > MAX_TICKS) {
			arrive(server, j, p);
			return;
		}

		if (j.hop && !j.hopped) {
			// Разгон с набором высоты по курсу (а не подъём строго вверх), потом «прыжок» к цели.
			if (Double.isNaN(j.heading)) {
				j.heading = Math.toRadians(j.cart.getYaw() + 90.0f);
			}
			j.vel = Math.min(j.speed(), j.vel + j.speed() / 25.0);
			double lift = Math.min(0.6, 0.05 + j.ticks * 0.02);
			move(j.cart, pos.add(Math.cos(j.heading) * j.vel, lift, Math.sin(j.heading) * j.vel), j.cart.getYaw());
			trail(world, pos);
			if (j.ticks >= DEPART_TICKS) {
				hop(server, j, p);
			}
			return;
		}

		Vec3d t = j.target;
		double dx = t.x - pos.x;
		double dz = t.z - pos.z;
		double hd = Math.sqrt(dx * dx + dz * dz);
		double speed = j.speed();
		if (j.breakdown && hd < BREAKDOWN_DISTANCE) {
			breakDown(j, p, world, pos, hd);
			return;
		}

		// Курс: к цели, но дорога плавно виляет (две синусоиды по пройденному пути), а у цели — прямо.
		double toTarget = Math.atan2(dz, dx);
		double fade = MathHelper.clamp((hd - 20) / 40.0, 0.0, 1.0);
		double amp = Math.toRadians(j.taxi == TaxiClass.ECONOMY ? 26 : j.taxi != null ? 20 : 14);
		double wander = (Math.sin(j.traveled * 0.045 + j.seed) + 0.35 * Math.sin(j.traveled * 0.11 + j.seed * 2.3)) * amp * fade;
		double desired = toTarget + wander;
		if (Double.isNaN(j.heading)) {
			j.heading = Math.toRadians(j.cart.getYaw() + 90.0f);
		}
		double turnNeeded = MathHelper.wrapDegrees(Math.toDegrees(desired - j.heading));
		double maxTurn = hd < 12 ? 15.0 : j.taxi == TaxiClass.ECONOMY ? 3.0 : j.taxi == TaxiClass.COMFORT ? 4.0 : j.taxi != null ? 4.5 : 5.0;
		j.heading += Math.toRadians(MathHelper.clamp(turnNeeded, -maxTurn, maxTurn));

		// Скорость: разгон с места, торможение у цели и на крутых поворотах.
		double targetVel = speed * MathHelper.clamp(hd / 16.0, 0.25, 1.0) * (1.0 - 0.4 * Math.min(1.0, Math.abs(turnNeeded) / 90.0));
		j.vel += MathHelper.clamp(targetVel - j.vel, -0.08, speed / 25.0);
		double stepLen = Math.min(j.vel, hd);
		double hx = Math.cos(j.heading);
		double hz = Math.sin(j.heading);
		double nx = pos.x + hx * stepLen;
		double nz = pos.z + hz * stepLen;
		j.traveled += stepLen;
		float yaw = yawTo(hx, hz);

		// Высота: по рельефу с упреждением (смотрим на 2 и 4 блока вперёд), без рывков.
		int g0 = groundAt(world, nx, nz);
		int ground = Math.max(g0, Math.max(groundAt(world, nx + hx * 2, nz + hz * 2), groundAt(world, nx + hx * 4, nz + hz * 4) - 1));
		double desiredY;
		if (j.taxi != null) {
			// Такси катится по земле (по рельефу), к цели — плавно на её высоту.
			desiredY = hd > 12 ? ground + 0.05 : Math.max(Math.max(g0 + 0.05, t.y), t.y + hd / 6.0);
		} else {
			// Заключённый бежит почти по земле: чуть выше рельефа, чтобы не цепляться за кочки.
			desiredY = hd > 12 ? Math.max(ground + 1.2, t.y + 1) : Math.max(t.y, t.y + hd / 6.0);
		}
		double ny = pos.y + MathHelper.clamp((desiredY - pos.y) * 0.4, -0.45, 0.7);
		move(j.cart, new Vec3d(nx, ny, nz), yaw);
		if (j.ticks % 2 == 0) {
			trail(world, pos);
		}
		if (j.taxi != null && j.ticks % 8 == 0) {
			world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_HORSE_GALLOP, SoundCategory.NEUTRAL, 0.35f, 1.0f);
		}
		double left = Math.sqrt((t.x - nx) * (t.x - nx) + (t.z - nz) * (t.z - nz));
		if (left < Math.max(0.8, j.vel) && Math.abs(t.y - ny) < 1.2) {
			arrive(server, j, p);
		}
	}

	private static int groundAt(ServerWorld world, double x, double z) {
		return world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
	}

	private static void hop(MinecraftServer server, Journey j, ServerPlayerEntity p) {
		ServerWorld target = server.getWorld(j.dim);
		if (target == null) {
			target = server.getOverworld();
		}
		Vec3d from = p.getPos();
		Vec3d dir = new Vec3d(j.target.x - from.x, 0, j.target.z - from.z);
		if (!p.getWorld().getRegistryKey().equals(j.dim) || dir.lengthSquared() < 1.0E-4) {
			dir = new Vec3d(-1, 0, 0);
		}
		dir = dir.normalize();
		Vec3d start = j.target.subtract(dir.multiply(48)).add(0, 25, 0);

		p.stopRiding();
		discard(j);
		Teleports.to(p, target, start.x, start.y, start.z, p.getYaw(), p.getPitch());
		spawnCart(j, target, start, yawTo(dir.x, dir.z));
		if (j.cart != null) {
			p.startRiding(j.cart, true);
		}
		j.hopped = true;
		j.ticks = 0;
		j.heading = Math.atan2(dir.z, dir.x);
		j.vel = j.speed() * 0.7;
	}

	private static void arrive(MinecraftServer server, Journey j, ServerPlayerEntity p) {
		journeys.remove(j.player);
		p.stopRiding();
		discard(j);
		ServerWorld target = server.getWorld(j.dim);
		if (target == null) {
			target = server.getOverworld();
		}
		Teleports.to(p, target, j.target.x, j.target.y, j.target.z, p.getYaw(), p.getPitch());
		p.fallDistance = 0;
		target.playSound(null, j.target.x, j.target.y, j.target.z,
			SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.6f, 1.4f);
		j.onArrive.accept(p);
	}

	/** Пассажир такси вышел на ходу: поездка окончена, трудодни не возвращаются. */
	private static void leaveTaxi(MinecraftServer server, Journey j, ServerPlayerEntity p) {
		journeys.remove(j.player);
		discard(j);
		if (j.hop && !j.hopped) {
			// Ещё не «прыгнули»: высадить там, где садились (карета успела только взлететь).
			ServerWorld w = server.getWorld(j.startDim);
			if (w != null) {
				Teleports.to(p, w, j.start.x, j.start.y, j.start.z, p.getYaw(), p.getPitch());
			}
			p.fallDistance = 0;
		} else {
			TaxiService.placeOnGround(p);
		}
		p.sendMessage(Text.translatable("diktatura.taxi.left").formatted(Formatting.YELLOW), false);
	}

	/** Телега-«эконом» сломалась: дальше пешком. */
	private static void breakDown(Journey j, ServerPlayerEntity p, ServerWorld world, Vec3d pos, double left) {
		journeys.remove(j.player);
		p.stopRiding();
		discard(j);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.6, pos.z, 16, 0.6, 0.3, 0.6, 0.02);
		world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.NEUTRAL, 1.0f, 0.6f);
		world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_HORSE_ANGRY, SoundCategory.NEUTRAL, 0.8f, 0.8f);
		TaxiService.placeOnGround(p);
		p.sendMessage(Text.translatable("diktatura.taxi.breakdown", MathHelper.ceil(left)).formatted(Formatting.YELLOW), false);
	}

	private static void move(Entity cart, Vec3d to, float yaw) {
		cart.setVelocity(Vec3d.ZERO);
		cart.setPosition(to.x, to.y, to.z);
		cart.setYaw(yaw);
		for (Entity passenger : cart.getPassengerList()) {
			cart.updatePassengerPosition(passenger);
		}
	}

	private static void trail(ServerWorld world, Vec3d pos) {
		world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.2, pos.z, 2, 0.2, 0.1, 0.2, 0.01);
	}

	/** Удалить вагоны, оставшиеся после аварийного завершения. */
	private static void cleanupStrayWagons(MinecraftServer server) {
		for (ServerWorld w : server.getWorlds()) {
			List<WagonEntity> carts = new ArrayList<>(w.getEntitiesByType(com.diktatura.npc.ModEntities.WAGON, c -> true));
			carts.addAll(w.getEntitiesByType(com.diktatura.npc.ModEntities.TAXI, c -> true));
			for (WagonEntity cart : carts) {
				boolean used = journeys.values().stream().anyMatch(j -> j.cart == cart);
				if (!used) {
					cart.removeAllPassengers();
					cart.discard();
				}
			}
			// Вагонетки из старых версий (до 0.2.26), если остались в мире.
			for (MinecartEntity cart : w.getEntitiesByType(EntityType.MINECART, c -> c.getCommandTags().contains(WAGON_TAG))) {
				cart.removeAllPassengers();
				cart.discard();
			}
		}
	}
}
