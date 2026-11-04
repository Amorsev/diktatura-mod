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
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * TransportSystem: скоростная повозка. Вагонетка без гравитации летит над рельефом от
 * точки задержания к тюрьме и обратно, пассажира нельзя высадить. Если цель далеко или в
 * другом измерении, повозка взлетает, «прыгает» к цели и прилетает уже на месте.
 */
public final class TransportSystem {
	public static final String WAGON_TAG = "diktatura_wagon";
	private static final int DEPART_TICKS = 40;
	private static final int MAX_TICKS = 20 * 180;
	private static final Map<UUID, Journey> journeys = new HashMap<>();

	private TransportSystem() {
	}

	private static final class Journey {
		final UUID player;
		final RegistryKey<World> dim;
		final Vec3d target;
		final Consumer<ServerPlayerEntity> onArrive;
		final boolean hop;
		boolean hopped;
		MinecartEntity cart;
		int ticks;

		Journey(UUID player, RegistryKey<World> dim, Vec3d target, Consumer<ServerPlayerEntity> onArrive, boolean hop) {
			this.player = player;
			this.dim = dim;
			this.target = target;
			this.onArrive = onArrive;
			this.hop = hop;
		}
	}

	public static boolean isTraveling(UUID player) {
		return journeys.containsKey(player);
	}

	public static void send(ServerPlayerEntity player, RegistryKey<World> dim, Vec3d target, Consumer<ServerPlayerEntity> onArrive) {
		cancel(player);
		boolean sameDim = player.getWorld().getRegistryKey().equals(dim);
		double dx = target.x - player.getX();
		double dz = target.z - player.getZ();
		int max = DiktaturaConfig.get().transportMaxDirectDistance;
		boolean hop = !sameDim || dx * dx + dz * dz > (double) max * max;

		Journey j = new Journey(player.getUuid(), dim, target, onArrive, hop);
		spawnCart(j, player.getServerWorld(), player.getPos());
		player.startRiding(j.cart, true);
		journeys.put(player.getUuid(), j);

		player.sendMessage(Text.translatable("diktatura.transport.depart").formatted(Formatting.GOLD), true);
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.0f, 1.2f);
	}

	private static void spawnCart(Journey j, ServerWorld world, Vec3d pos) {
		MinecartEntity cart = EntityType.MINECART.create(world);
		if (cart == null) {
			return;
		}
		cart.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0f, 0.0f);
		cart.setNoGravity(true);
		cart.noClip = true;
		cart.setInvulnerable(true);
		cart.setSilent(true);
		cart.addCommandTag(WAGON_TAG);
		cart.setCustomName(Text.translatable("entity.diktatura.wagon"));
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
			spawnCart(j, p.getServerWorld(), p.getPos());
			if (j.cart == null) {
				arrive(server, j, p);
				return;
			}
		}
		if (p.getVehicle() != j.cart) {
			p.startRiding(j.cart, true);
		}
		ServerWorld world = (ServerWorld) j.cart.getWorld();
		Vec3d pos = j.cart.getPos();

		if (j.ticks > MAX_TICKS) {
			arrive(server, j, p);
			return;
		}

		if (j.hop && !j.hopped) {
			move(j.cart, pos.add(0, 0.6, 0), j.cart.getYaw());
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
		double speed = DiktaturaConfig.get().transportSpeed;

		int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(pos.x), MathHelper.floor(pos.z));
		double desiredY = hd > 12 ? Math.max(ground + 3, t.y + 3) : Math.max(t.y, t.y + hd / 4.0);
		double ny = pos.y + MathHelper.clamp(desiredY - pos.y, -0.5, 0.8);
		double stepLen = Math.min(speed, hd);
		double nx = pos.x;
		double nz = pos.z;
		float yaw = j.cart.getYaw();
		if (hd > 1.0E-4) {
			nx += dx / hd * stepLen;
			nz += dz / hd * stepLen;
			yaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
		}
		move(j.cart, new Vec3d(nx, ny, nz), yaw);
		if (j.ticks % 2 == 0) {
			trail(world, pos);
		}
		if (hd < 0.8 && Math.abs(t.y - ny) < 1.2) {
			arrive(server, j, p);
		}
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
		spawnCart(j, target, start);
		j.hopped = true;
		j.ticks = 0;
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
			for (MinecartEntity cart : w.getEntitiesByType(EntityType.MINECART, c -> c.getCommandTags().contains(WAGON_TAG))) {
				boolean used = journeys.values().stream().anyMatch(j -> j.cart == cart);
				if (!used) {
					cart.removeAllPassengers();
					cart.discard();
				}
			}
		}
	}
}
