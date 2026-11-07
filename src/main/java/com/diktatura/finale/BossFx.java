package com.diktatura.finale;

import com.diktatura.network.FxPayloads;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Частицы, тряска и анимированные блоки финального боя. Блоки-декорации (шипы, вихрь,
 * метеорит) — block display, настраиваются через NBT (без доступа к закрытым методам).
 * По мотивам боя с драконом из мода-референса (MIT, © 2026 Gr1tW4lk).
 */
public final class BossFx {
	public static final DustParticleEffect PURPLE = new DustParticleEffect(new Vector3f(0.78f, 0.25f, 1.0f), 2.5f);
	public static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.1f, 0.08f), 2.5f);
	public static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0f, 0.8f, 0.1f), 2.0f);

	private static final String TAG = "diktatura_fx";
	private static final Set<UUID> LIVE = new HashSet<>();

	private BossFx() {
	}

	public static void registerEvents() {
		// Декорации, оставшиеся с прошлого запуска (сервер выключили посреди атаки), убираем.
		ServerEntityEvents.ENTITY_LOAD.register((e, w) -> {
			if (e instanceof DisplayEntity && e.getCommandTags().contains(TAG) && !LIVE.contains(e.getUuid())) {
				e.discard();
			}
		});
	}

	/** Частицы, которые видно издалека. */
	public static void particle(ServerWorld w, ParticleEffect e, double x, double y, double z, int count,
	                            double dx, double dy, double dz, double speed) {
		for (ServerPlayerEntity p : w.getPlayers()) {
			if (p.squaredDistanceTo(x, y, z) < 200 * 200) {
				w.spawnParticles(p, e, true, x, y, z, count, dx, dy, dz, speed);
			}
		}
	}

	public static void flow(ServerWorld w, ParticleEffect e, Vec3d at, Vec3d v) {
		particle(w, e, at.x, at.y, at.z, 0, v.x, v.y, v.z, 1.0);
	}

	public static void ring(ServerWorld w, Vec3d c, double r, ParticleEffect e, int points, boolean followGround) {
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			double x = c.x + Math.cos(a) * r;
			double z = c.z + Math.sin(a) * r;
			double y = c.y;
			if (followGround) {
				int g = ground(w, x, z);
				if (g <= w.getBottomY()) {
					continue;
				}
				y = g + 0.15;
			}
			particle(w, e, x, y, z, 1, 0.02, 0.02, 0.02, 0);
		}
	}

	/** Красная метка на земле на ticks тиков: сюда что-то упадёт. */
	public static void marker(ServerWorld w, Vec3d at, int ticks) {
		for (int t = 0; t < ticks; t += 4) {
			Tasks.schedule(w.getServer(), t, () -> {
				ring(w, at.add(0, 0.2, 0), 1.2, RED, 12, true);
				ring(w, at.add(0, 0.2, 0), 0.5, RED, 6, true);
			});
		}
	}

	public static int ground(ServerWorld w, double x, double z) {
		return w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
	}

	public static void fx(ServerPlayerEntity p, String id, int ticks) {
		if (ServerPlayNetworking.canSend(p, FxPayloads.Fx.ID)) {
			ServerPlayNetworking.send(p, new FxPayloads.Fx(id, ticks));
		}
	}

	/** Тряска камеры: близко — сильная, дальше — слабая. */
	public static void shake(ServerWorld w, Vec3d c, double radius, int ticks, boolean hard) {
		for (ServerPlayerEntity p : w.getPlayers()) {
			double d = p.getPos().distanceTo(c);
			if (d <= radius) {
				fx(p, hard && d < radius / 2 ? "shake_hard" : "shake", ticks);
			}
		}
	}

	public static void fxNear(ServerWorld w, Vec3d c, double radius, String id, int ticks) {
		for (ServerPlayerEntity p : w.getPlayers()) {
			if (p.getPos().distanceTo(c) <= radius) {
				fx(p, id, ticks);
			}
		}
	}

	public static void lightning(ServerWorld w, Vec3d at) {
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
		if (bolt == null) {
			return;
		}
		bolt.refreshPositionAfterTeleport(at);
		bolt.setCosmetic(true);
		w.spawnEntity(bolt);
	}

	// ------------------------------------------------------------------ анимированные блоки

	public static AffineTransformation tf(float sx, float sy, float sz, Quaternionf rot, float lift) {
		Matrix4f m = new Matrix4f().translate(0, lift, 0).rotate(rot).scale(sx, sy, sz).translate(-0.5f, 0f, -0.5f);
		return new AffineTransformation(m);
	}

	private static NbtElement encode(AffineTransformation t) {
		return AffineTransformation.ANY_CODEC.encodeStart(NbtOps.INSTANCE, t).result().orElse(new NbtCompound());
	}

	public static DisplayEntity.BlockDisplayEntity block(ServerWorld w, Vec3d pos, BlockState state, AffineTransformation t, boolean glow) {
		DisplayEntity.BlockDisplayEntity e = EntityType.BLOCK_DISPLAY.create(w);
		if (e == null) {
			return null;
		}
		e.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
		NbtCompound n = e.writeNbt(new NbtCompound());
		n.put("block_state", NbtHelper.fromBlockState(state));
		n.put("transformation", encode(t));
		n.putFloat("view_range", 4f);
		if (glow) {
			NbtCompound b = new NbtCompound();
			b.putInt("sky", 15);
			b.putInt("block", 15);
			n.put("brightness", b);
		}
		e.readNbt(n);
		e.addCommandTag(TAG);
		LIVE.add(e.getUuid());
		w.spawnEntity(e);
		return e;
	}

	/** Плавно перевести блок в новое положение за ticks тиков (интерполяция на клиенте). */
	public static void animate(DisplayEntity e, AffineTransformation t, int ticks) {
		if (e == null || e.isRemoved()) {
			return;
		}
		NbtCompound n = e.writeNbt(new NbtCompound());
		n.put("transformation", encode(t));
		n.putInt("interpolation_duration", ticks);
		n.putInt("start_interpolation", 0);
		e.readNbt(n);
	}

	public static void remove(DisplayEntity e) {
		if (e == null) {
			return;
		}
		LIVE.remove(e.getUuid());
		e.discard();
	}

	public static Quaternionf tilt(net.minecraft.util.math.random.Random r, float maxDeg) {
		float a = (float) Math.toRadians((r.nextFloat() * 2 - 1) * maxDeg);
		float b = (float) Math.toRadians((r.nextFloat() * 2 - 1) * maxDeg);
		return new Quaternionf().rotateY(r.nextFloat() * (float) Math.PI * 2).rotateX(a).rotateZ(b);
	}
}
