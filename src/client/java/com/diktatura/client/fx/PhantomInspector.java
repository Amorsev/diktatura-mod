package com.diktatura.client.fx;

import com.diktatura.npc.ModEntities;
import com.diktatura.npc.NpcEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Фантомный налоговый инспектор (паранойя от страха): существует только у одного игрока на клиенте.
 * Появляется поодаль, на краю поля зрения, стоит и смотрит. Подойдёшь ближе 7 блоков или посмотришь
 * на него в упор пару секунд — растворится в дыму. Сервер о нём ничего не знает.
 */
public final class PhantomInspector {
	private static final String ID = "diktatura:tax_inspector";
	private static NpcEntity ghost;
	private static int life;
	private static int stare;

	private PhantomInspector() {
	}

	public static void spawn(int ticks) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld world = mc.world;
		ClientPlayerEntity p = mc.player;
		if (world == null || p == null) {
			return;
		}
		remove(false);
		for (int attempt = 0; attempt < 12; attempt++) {
			// На краю поля зрения: 30–60° в сторону от взгляда, 16–24 блока.
			float side = (p.getRandom().nextBoolean() ? 1 : -1) * (30 + p.getRandom().nextInt(30));
			double a = Math.toRadians(p.getYaw() + side);
			double dist = 16 + p.getRandom().nextInt(9);
			double x = p.getX() - Math.sin(a) * dist;
			double z = p.getZ() + Math.cos(a) * dist;
			BlockPos ground = findGround(world, BlockPos.ofFloored(x, p.getY() + 6, z));
			if (ground == null) {
				continue;
			}
			NpcEntity e = ModEntities.NPC.create(world);
			if (e == null) {
				return;
			}
			e.setNpcId(ID);
			e.setId(-90000 - p.getRandom().nextInt(10000));
			e.refreshPositionAndAngles(x, ground.getY(), z, 0f, 0f);
			face(e, p);
			world.addEntity(e);
			ghost = e;
			life = ticks;
			stare = 0;
			return;
		}
	}

	private static BlockPos findGround(ClientWorld world, BlockPos start) {
		BlockPos.Mutable m = start.mutableCopy();
		for (int i = 0; i < 14; i++) {
			if (world.getBlockState(m.down()).isSolidBlock(world, m.down())
				&& world.getBlockState(m).isAir() && world.getBlockState(m.up()).isAir()) {
				return m.toImmutable();
			}
			m.move(0, -1, 0);
		}
		return null;
	}

	private static void face(NpcEntity e, ClientPlayerEntity p) {
		double dx = p.getX() - e.getX();
		double dz = p.getZ() - e.getZ();
		float yaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
		e.setYaw(yaw);
		e.setBodyYaw(yaw);
		e.setHeadYaw(yaw);
		e.prevYaw = yaw;
		e.prevBodyYaw = yaw;
		e.prevHeadYaw = yaw;
	}

	public static void tick(MinecraftClient mc) {
		if (ghost == null) {
			return;
		}
		ClientPlayerEntity p = mc.player;
		if (p == null || mc.world == null || ghost.getWorld() != mc.world) {
			ghost = null;
			return;
		}
		face(ghost, p);
		life--;
		double dist = p.distanceTo(ghost);
		Vec3d look = p.getRotationVec(1f);
		Vec3d to = ghost.getEyePos().subtract(p.getEyePos()).normalize();
		boolean staring = look.dotProduct(to) > 0.985;
		stare = staring ? stare + 1 : Math.max(0, stare - 1);
		if (life <= 0 || dist < 7 || stare > 40) {
			remove(true);
		}
	}

	private static void remove(boolean poof) {
		if (ghost == null) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world != null && ghost.getWorld() == mc.world) {
			if (poof) {
				for (int i = 0; i < 20; i++) {
					mc.world.addParticle(ParticleTypes.LARGE_SMOKE, ghost.getX() + (mc.world.random.nextDouble() - 0.5),
						ghost.getY() + mc.world.random.nextDouble() * 2, ghost.getZ() + (mc.world.random.nextDouble() - 0.5), 0, 0.03, 0);
				}
				mc.world.playSound(ghost.getX(), ghost.getY(), ghost.getZ(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE,
					SoundCategory.AMBIENT, 0.5f, 0.6f, false);
			}
			mc.world.removeEntity(ghost.getId(), Entity.RemovalReason.DISCARDED);
		}
		ghost = null;
	}
}
