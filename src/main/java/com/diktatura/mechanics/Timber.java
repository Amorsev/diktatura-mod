package com.diktatura.mechanics;

import com.diktatura.casino.CasinoFx;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.license.LicensedActivities;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.ui.Hud;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * «Тимбер!»: подрубленное дерево не рассыпается на брёвна, а падает целиком — от игрока, плавно
 * наклоняясь и ускоряясь. Ствол ложится на землю брёвнами (их потом рубят по одному), листва
 * осыпается саженцами и палками. Кого накроет стволом — получит урон (и сам лесоруб тоже, если
 * стоит не там). Только настоящие деревья (с природной листвой); на корточках рубится одно бревно.
 * Идея — из референсного мода (Timber), реализация своя.
 */
public final class Timber {
	private static final int MAX_LOGS = 96;
	private static final int MAX_LEAVES = 180;

	private record Part(BlockState state, Vector3f offset, DisplayEntity display, boolean log) {
	}

	private static final class Fall {
		final ServerWorld world;
		final Vec3d pivot;
		final Vector3f axis;
		final Direction dir;
		final List<Part> parts;
		int ticks;

		Fall(ServerWorld world, Vec3d pivot, Vector3f axis, Direction dir, List<Part> parts) {
			this.world = world;
			this.pivot = pivot;
			this.axis = axis;
			this.dir = dir;
			this.parts = parts;
		}
	}

	private static final List<Fall> FALLS = new ArrayList<>();

	private Timber() {
	}

	public static void registerEvents() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
			if (world instanceof ServerWorld w && player instanceof ServerPlayerEntity sp && state.isIn(BlockTags.LOGS)
				&& DiktaturaConfig.get().timber && !sp.isSneaking()) {
				fell(w, sp, pos);
			}
		});
	}

	private static boolean naturalLeaves(BlockState s) {
		return s.isIn(BlockTags.LEAVES) && !(s.contains(LeavesBlock.PERSISTENT) && s.get(LeavesBlock.PERSISTENT));
	}

	private static void fell(ServerWorld w, ServerPlayerEntity p, BlockPos start) {
		// Ствол: связанные брёвна от места сруба вверх и вбок.
		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);
		while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
			BlockPos cur = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos n = cur.add(dx, dy, dz);
						if (seen.add(n) && w.getBlockState(n).isIn(BlockTags.LOGS)) {
							logs.add(n);
							queue.add(n);
						}
					}
				}
			}
		}
		if (logs.isEmpty()) {
			return;
		}
		// Крона: природная листва в двух блоках от ствола.
		List<BlockPos> leaves = new ArrayList<>();
		Set<BlockPos> leafSeen = new HashSet<>();
		for (BlockPos log : logs) {
			for (BlockPos n : BlockPos.iterate(log.add(-2, -1, -2), log.add(2, 2, 2))) {
				if (leaves.size() >= MAX_LEAVES) {
					break;
				}
				if (!leafSeen.contains(n) && naturalLeaves(w.getBlockState(n))) {
					BlockPos im = n.toImmutable();
					leafSeen.add(im);
					leaves.add(im);
				}
			}
		}
		if (leaves.isEmpty()) {
			return; // не дерево (постройка из брёвен)
		}

		// Куда падать: от игрока.
		double ddx = start.getX() + 0.5 - p.getX();
		double ddz = start.getZ() + 0.5 - p.getZ();
		Direction dir = Math.abs(ddx) + Math.abs(ddz) < 0.01 ? p.getHorizontalFacing()
			: Direction.getFacing(ddx, 0, ddz);
		if (dir.getAxis() == Direction.Axis.Y) {
			dir = p.getHorizontalFacing();
		}
		Vec3d pivot = new Vec3d(start.getX() + 0.5 + dir.getOffsetX() * 0.5, start.getY(), start.getZ() + 0.5 + dir.getOffsetZ() * 0.5);
		Vector3f axis = new Vector3f(dir.getOffsetZ(), 0, -dir.getOffsetX());

		List<Part> parts = new ArrayList<>();
		for (BlockPos pos : logs) {
			parts.add(take(w, pos, pivot, true));
		}
		for (BlockPos pos : leaves) {
			parts.add(take(w, pos, pivot, false));
		}
		FALLS.add(new Fall(w, pivot, axis, dir, parts));
		w.playSound(null, start, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.2f, 0.6f);
		Hud.alert(p, AlertKind.INFO, Text.translatable("diktatura.timber.title"), Text.translatable("diktatura.timber.hint"), 3);
	}

	/** Убрать блок из мира и заменить его парящей копией, которая будет падать вместе с деревом. */
	private static Part take(ServerWorld w, BlockPos pos, Vec3d pivot, boolean log) {
		BlockState s = w.getBlockState(pos);
		w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		Vector3f off = new Vector3f((float) (pos.getX() - pivot.x), (float) (pos.getY() - pivot.y), (float) (pos.getZ() - pivot.z));
		DisplayEntity d = CasinoFx.block(w, pivot, s, CasinoFx.matrix(new Matrix4f().translate(off)));
		return new Part(s, off, d, log);
	}

	private static Matrix4f rotated(Vector3f axis, float degrees, Vector3f off) {
		return new Matrix4f().rotate((float) Math.toRadians(degrees), axis.x, axis.y, axis.z).translate(off);
	}

	public static void tick(MinecraftServer server) {
		for (Iterator<Fall> it = FALLS.iterator(); it.hasNext(); ) {
			Fall f = it.next();
			f.ticks++;
			// Падение с ускорением: медленный наклон, потом быстрее и быстрее.
			float target;
			int dur;
			switch (f.ticks) {
				case 1 -> {
					target = 10f;
					dur = 9;
				}
				case 10 -> {
					target = 42f;
					dur = 6;
				}
				case 16 -> {
					target = 90f;
					dur = 4;
				}
				default -> {
					target = -1f;
					dur = 0;
				}
			}
			if (target >= 0) {
				for (Part part : f.parts) {
					CasinoFx.animate(part.display(), CasinoFx.matrix(rotated(f.axis, target, part.offset())), dur);
				}
				if (f.ticks == 10) {
					f.world.playSound(null, BlockPos.ofFloored(f.pivot), SoundEvents.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 1f, 0.5f);
				}
			}
			if (f.ticks >= 20) {
				land(f);
				it.remove();
			}
		}
	}

	/** Удар о землю: брёвна ложатся, листва осыпается, кого накрыло — получает урон. */
	private static void land(Fall f) {
		ServerWorld w = f.world;
		Matrix4f r = new Matrix4f().rotate((float) Math.toRadians(90), f.axis.x, f.axis.y, f.axis.z);
		List<Box> hit = new ArrayList<>();
		int logs = 0;
		for (Part part : f.parts) {
			CasinoFx.remove(part.display());
			Vector3f c = new Vector3f(part.offset()).add(0.5f, 0.5f, 0.5f);
			r.transformPosition(c);
			BlockPos at = BlockPos.ofFloored(f.pivot.x + c.x, f.pivot.y + c.y, f.pivot.z + c.z);
			if (part.log()) {
				logs++;
				hit.add(new Box(at));
				BlockState cur = w.getBlockState(at);
				BlockState lying = part.state().contains(Properties.AXIS)
					? part.state().with(Properties.AXIS, f.dir.getAxis()) : part.state();
				if ((cur.isAir() || cur.isReplaceable()) && w.getFluidState(at).isEmpty()) {
					w.setBlockState(at, lying);
				} else {
					Block.dropStacks(part.state(), w, at);
				}
			} else {
				hit.add(new Box(at));
				w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, part.state()),
					at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
				if (w.getRandom().nextInt(3) == 0) {
					Block.dropStacks(part.state(), w, at);
				}
			}
		}
		BlockPos center = BlockPos.ofFloored(f.pivot.add(f.dir.getOffsetX() * 3, 0, f.dir.getOffsetZ() * 3));
		w.playSound(null, center, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.BLOCKS, 1.2f, 0.6f);
		w.playSound(null, center, SoundEvents.ENTITY_GENERIC_BIG_FALL, SoundCategory.BLOCKS, 1.0f, 0.7f);

		// Урон: от ствола — по его толщине и длине, от кроны — лёгкий.
		float damage = Math.min(12f, 3f + logs * 0.75f);
		Box all = hit.stream().reduce(Box::union).orElse(null);
		if (all == null) {
			return;
		}
		Set<LivingEntity> victims = new HashSet<>(w.getEntitiesByClass(LivingEntity.class, all.expand(0.3), LivingEntity::isAlive));
		for (LivingEntity e : victims) {
			Box eb = e.getBoundingBox();
			boolean underLog = false;
			boolean underLeaves = false;
			for (int i = 0; i < hit.size(); i++) {
				if (hit.get(i).expand(0.2).intersects(eb)) {
					if (i < logs) {
						underLog = true;
					} else {
						underLeaves = true;
					}
				}
			}
			if (underLog || underLeaves) {
				e.damage(w.getDamageSources().fallingBlock(null), underLog ? damage : 2f);
				if (e instanceof ServerPlayerEntity sp) {
					sp.sendMessage(Text.translatable("diktatura.timber.hit").formatted(net.minecraft.util.Formatting.RED), true);
				}
			}
		}
	}
}
