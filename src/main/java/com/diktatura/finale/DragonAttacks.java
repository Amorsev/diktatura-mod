package com.diktatura.finale;

import com.diktatura.goose.ModSounds;
import com.diktatura.goose.WildGooseEntity;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.ModEntities;
import com.diktatura.ui.Hud;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.EndermiteEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Атаки дракона госохраны. У каждой есть предупреждение и способ увернуться: метка на земле,
 * звук или баннер. Каждая возвращает, сколько тиков она занимает дракона.
 * Порт атак Пробуждённого Эндер-дракона из мода-референса (MIT, © 2026 Gr1tW4lk).
 */
public final class DragonAttacks {
	private static final BlockStateParticleEffect DEBRIS = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.END_STONE.getDefaultState());

	interface Ticker {
		/** false — закончилось. */
		boolean tick();
	}

	private static final List<Ticker> ACTIVE = new ArrayList<>();
	private static final List<Ticker> PENDING = new ArrayList<>();

	private DragonAttacks() {
	}

	private static void start(Ticker t) {
		PENDING.add(t);
	}

	static void tickAll() {
		ACTIVE.addAll(PENDING);
		PENDING.clear();
		for (Iterator<Ticker> it = ACTIVE.iterator(); it.hasNext(); ) {
			Ticker t = it.next();
			boolean alive;
			try {
				alive = t.tick();
			} catch (RuntimeException e) {
				com.diktatura.Diktatura.LOGGER.error("Ошибка в атаке дракона", e);
				alive = false;
			}
			if (!alive) {
				it.remove();
			}
		}
	}

	static void clear() {
		ACTIVE.clear();
		PENDING.clear();
	}

	private static void sound(ServerWorld w, Vec3d at, SoundEvent ev, float volume, float pitch) {
		w.playSound(null, at.x, at.y, at.z, ev, SoundCategory.HOSTILE, volume, pitch);
	}

	static boolean hittable(ServerPlayerEntity p) {
		return p.isAlive() && !p.isSpectator() && !p.isCreative();
	}

	private static DamageSource hit(ServerWorld w, EnderDragonEntity d) {
		return w.getDamageSources().mobAttack(d);
	}

	private static void warn(ServerPlayerEntity p, AlertKind kind, String id) {
		Hud.alert(p, kind, Text.translatable("diktatura.dragon.att." + id), Text.translatable("diktatura.dragon.att." + id + ".hint"), 4);
	}

	static Vec3d headPos(EnderDragonEntity d) {
		return d.head.getBoundingBox().getCenter();
	}

	static Vec3d groundUnder(ServerWorld w, Vec3d at) {
		int g = BossFx.ground(w, at.x, at.z);
		return new Vec3d(at.x, Math.max(g, w.getBottomY()), at.z);
	}

	// ================================================================ ударная волна

	/** Кольцо бежит по земле от центра. Кто стоит на земле — получает удар и подлетает. Перепрыгни его. */
	public static void shockwave(ServerWorld w, EnderDragonEntity d, Vec3d c, double maxR, double speed, float damage, boolean rage) {
		Set<UUID> hitSet = new HashSet<>();
		double[] r = {1.0};
		ParticleEffect dust = rage ? BossFx.RED : BossFx.PURPLE;
		start(() -> {
			r[0] += speed;
			int points = (int) Math.min(96, 12 + r[0] * 4);
			for (int i = 0; i < points; i++) {
				double a = Math.PI * 2 * i / points + r[0] * 0.07;
				double x = c.x + Math.cos(a) * r[0];
				double z = c.z + Math.sin(a) * r[0];
				int g = BossFx.ground(w, x, z);
				if (g <= w.getBottomY()) {
					continue;
				}
				BossFx.particle(w, dust, x, g + 0.2, z, 1, 0.05, 0.05, 0.05, 0);
				if (i % 3 == 0) {
					BossFx.particle(w, DEBRIS, x, g + 0.1, z, 2, 0.15, 0.05, 0.15, 0.2);
				}
			}
			if (damage > 0) {
				for (ServerPlayerEntity p : w.getPlayers()) {
					if (!hittable(p) || hitSet.contains(p.getUuid())) {
						continue;
					}
					double dist = Math.hypot(p.getX() - c.x, p.getZ() - c.z);
					if (Math.abs(dist - r[0]) > 1.2) {
						continue;
					}
					int g = BossFx.ground(w, p.getX(), p.getZ());
					if ((!p.isOnGround() && p.getY() - g > 0.3) || p.getY() - g > 1.5) {
						continue;
					}
					hitSet.add(p.getUuid());
					p.damage(hit(w, d), damage);
					Vec3d out = new Vec3d(p.getX() - c.x, 0, p.getZ() - c.z).normalize().multiply(0.7);
					p.addVelocity(out.x, 0.8, out.z);
					p.velocityModified = true;
				}
			}
			return r[0] < maxR;
		});
		sound(w, c, ModSounds.SHOCKWAVE, 8f, rage ? 0.8f : 1.0f);
	}

	// ================================================================ рёв

	/** Вдох (частицы летят в пасть) и рёв, сносящий всех вокруг. Щит держит удар. */
	public static int roar(ServerWorld w, EnderDragonEntity d, List<ServerPlayerEntity> players) {
		Vec3d head0 = headPos(d);
		for (ServerPlayerEntity p : players) {
			if (p.getPos().distanceTo(head0) < 70) {
				warn(p, AlertKind.DANGER, "roar");
			}
		}
		sound(w, head0, ModSounds.DRAGON_INHALE, 8f, 1f);
		Random rnd = w.random;
		for (int t = 0; t < 28; t += 2) {
			Tasks.schedule(w.getServer(), t, () -> {
				if (!d.isAlive()) {
					return;
				}
				Vec3d head = headPos(d);
				for (int i = 0; i < 16; i++) {
					Vec3d off = new Vec3d(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize()
						.multiply(6 + rnd.nextDouble() * 6);
					BossFx.flow(w, ParticleTypes.PORTAL, head, off);
				}
			});
		}
		Tasks.schedule(w.getServer(), 30, () -> {
			if (!d.isAlive()) {
				return;
			}
			Vec3d head = headPos(d);
			sound(w, head, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 14f, 0.42f);
			sound(w, head, ModSounds.BOSS_BOOM, 10f, 0.9f);
			BossFx.particle(w, ParticleTypes.EXPLOSION_EMITTER, head.x, head.y, head.z, 1, 0, 0, 0, 0);
			for (int k = 0; k < 4; k++) {
				int kk = k;
				Tasks.schedule(w.getServer(), k * 3, () -> BossFx.ring(w, head, 4 + kk * 6, ParticleTypes.SONIC_BOOM, 10 + kk * 4, false));
			}
			for (ServerPlayerEntity p : w.getPlayers()) {
				if (!hittable(p)) {
					continue;
				}
				double dist = p.getPos().distanceTo(head);
				if (dist > 42) {
					continue;
				}
				Vec3d toDragon = head.subtract(p.getEyePos()).normalize();
				boolean shield = p.isBlocking() && p.getRotationVec(1f).dotProduct(toDragon) > 0.2;
				double k = shield ? 0.12 : 1.7 - dist / 42 * 1.1;
				Vec3d push = new Vec3d(p.getX() - head.x, 0, p.getZ() - head.z).normalize().multiply(k);
				p.addVelocity(push.x, shield ? 0.1 : 0.55, push.z);
				p.velocityModified = true;
				BossFx.fx(p, "shake_hard", 35);
				if (shield) {
					Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.dragon.shield"), Text.empty(), 3);
				} else {
					p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 1));
					p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 40, 0, false, false));
				}
			}
		});
		return 50;
	}

	// ================================================================ пикирование

	/** Красный круг на месте игрока, потом дракон таранит это место и пускает ударную волну. */
	public static int dive(ServerWorld w, EnderDragonEntity d, ServerPlayerEntity target, GuardDragon.State st) {
		Vec3d aim = groundUnder(w, target.getPos());
		if (aim.y <= w.getBottomY()) {
			return 0;
		}
		warn(target, AlertKind.DANGER, "dive");
		sound(w, headPos(d), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 10f, 1.35f);
		sound(w, aim, ModSounds.METEOR_FALL, 4f, 1.4f);
		st.diveAim = aim;
		st.diveTicks = -30;
		Tasks.schedule(w.getServer(), 30, () -> {
			if (!d.isAlive() || st.diveAim == null || d.getPhaseManager().getCurrent().getType() == PhaseType.DYING) {
				return;
			}
			d.getPhaseManager().setPhase(PhaseType.CHARGING_PLAYER);
			d.getPhaseManager().create(PhaseType.CHARGING_PLAYER).setPathTarget(aim.add(0, 3, 0));
			sound(w, headPos(d), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 12f, 0.7f);
		});
		return 160;
	}

	static void diveTick(ServerWorld w, EnderDragonEntity d, GuardDragon.State st) {
		Vec3d aim = st.diveAim;
		st.diveTicks++;
		if (st.diveTicks % 4 == 0) {
			BossFx.ring(w, aim.add(0, 0.2, 0), 4.0, BossFx.RED, 28, true);
			BossFx.ring(w, aim.add(0, 0.2, 0), 1.8, BossFx.RED, 12, true);
		}
		if (st.diveTicks <= 0) {
			return;
		}
		double dist = d.getPos().distanceTo(aim);
		boolean charging = d.getPhaseManager().getCurrent().getType() == PhaseType.CHARGING_PLAYER;
		if (charging) {
			Vec3d to = aim.add(0, 3, 0).subtract(d.getPos());
			double len = to.length();
			if (len > 1) {
				Vec3d v = d.getVelocity().add(to.multiply(0.1 / len));
				double sp = v.length();
				if (sp > 1.6) {
					v = v.multiply(1.6 / sp);
				}
				d.setVelocity(v);
			}
			Vec3d tail = d.getPos();
			BossFx.particle(w, st.phase >= 3 ? BossFx.RED : BossFx.PURPLE, tail.x, tail.y + 1, tail.z, 6, 2.5, 1.5, 2.5, 0);
		}
		if (dist < 18) {
			diveImpact(w, d, aim, st.phase);
			st.diveAim = null;
			st.busy = Math.min(st.busy, 40);
		} else if (st.diveTicks > 140 || (!charging && st.diveTicks > 8)) {
			st.diveAim = null;
			st.busy = Math.min(st.busy, 20);
		}
	}

	private static void diveImpact(ServerWorld w, EnderDragonEntity d, Vec3d aim, int phase) {
		sound(w, aim, ModSounds.BOSS_BOOM, 12f, 0.8f);
		sound(w, aim, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 6f, 0.6f);
		BossFx.particle(w, ParticleTypes.EXPLOSION_EMITTER, aim.x, aim.y + 1, aim.z, 2, 1.5, 0.5, 1.5, 0);
		BossFx.particle(w, DEBRIS, aim.x, aim.y + 0.5, aim.z, 160, 2.5, 0.5, 2.5, 0.6);
		BossFx.particle(w, ParticleTypes.DRAGON_BREATH, aim.x, aim.y + 1, aim.z, 80, 3, 1, 3, 0.05);
		BossFx.shake(w, aim, 80, 30, true);
		for (ServerPlayerEntity p : w.getPlayers()) {
			if (hittable(p) && p.getPos().distanceTo(aim) < 4.5) {
				p.damage(hit(w, d), 6f);
				p.addVelocity(0, 1.0, 0);
				p.velocityModified = true;
			}
		}
		shockwave(w, d, aim, phase >= 3 ? 26 : 20, 0.75, phase >= 3 ? 8f : 6f, phase >= 3);
	}

	// ================================================================ кристальные шипы

	/** Волна кристаллов вырывается из земли по линии к игроку. Трещины — за полсекунды до удара. */
	public static int spikes(ServerWorld w, EnderDragonEntity d, ServerPlayerEntity target, int phase) {
		Random r = w.random;
		Vec3d tp = target.getPos();
		Vec3d fromDragon = new Vec3d(d.getX() - tp.x, 0, d.getZ() - tp.z);
		if (fromDragon.lengthSquared() < 1) {
			fromDragon = new Vec3d(1, 0, 0);
		}
		Vec3d start = tp.add(fromDragon.normalize().multiply(Math.min(22, fromDragon.length())));
		Vec3d dir = new Vec3d(tp.x - start.x, 0, tp.z - start.z).normalize();
		double len = start.distanceTo(new Vec3d(tp.x, start.y, tp.z)) + 10;
		int n = (int) Math.min(30, len / 1.5);
		warn(target, AlertKind.DANGER, "spikes");
		sound(w, tp, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 4f, 0.5f);
		boolean dark = phase >= 3;
		for (int i = 0; i < n; i++) {
			double side = (r.nextDouble() - 0.5);
			Vec3d p0 = start.add(dir.multiply(i * 1.5)).add(-dir.z * side, 0, dir.x * side);
			int g = BossFx.ground(w, p0.x, p0.z);
			if (g <= w.getBottomY()) {
				continue;
			}
			Vec3d base = new Vec3d(p0.x, g, p0.z);
			int delay = i * 2;
			Tasks.schedule(w.getServer(), delay, () -> {
				BossFx.particle(w, DEBRIS, base.x, base.y + 0.1, base.z, 8, 0.4, 0.05, 0.4, 0.1);
				BossFx.particle(w, dark ? BossFx.RED : BossFx.PURPLE, base.x, base.y + 0.15, base.z, 4, 0.4, 0.02, 0.4, 0);
			});
			Tasks.schedule(w.getServer(), delay + 12, () -> erupt(w, d, base, r, dark));
		}
		return n * 2 + 30;
	}

	private static void erupt(ServerWorld w, EnderDragonEntity d, Vec3d base, Random r, boolean dark) {
		Quaternionf rot = BossFx.tilt(r, 14);
		float h = 3.2f + r.nextFloat() * 1.6f;
		BlockState core = dark ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.AMETHYST_BLOCK.getDefaultState();
		DisplayEntity spike = BossFx.block(w, base, core, BossFx.tf(0.7f, 0.01f, 0.7f, rot, -0.3f), true);
		DisplayEntity crystal = BossFx.block(w, base, Blocks.AMETHYST_CLUSTER.getDefaultState(), BossFx.tf(1.8f, 0.01f, 1.8f, rot, -0.3f), true);
		Tasks.schedule(w.getServer(), 1, () -> {
			BossFx.animate(spike, BossFx.tf(0.7f, h, 0.7f, rot, -0.3f), 3);
			BossFx.animate(crystal, BossFx.tf(1.8f, 2.4f, 1.8f, rot, -0.3f), 3);
		});
		sound(w, base, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 2.5f, 0.5f + r.nextFloat() * 0.3f);
		if (r.nextInt(3) == 0) {
			sound(w, base, ModSounds.CRYSTAL_RISE, 2.5f, 0.8f + r.nextFloat() * 0.4f);
		}
		BossFx.particle(w, ParticleTypes.END_ROD, base.x, base.y + 1, base.z, 6, 0.3, 0.8, 0.3, 0.05);
		for (ServerPlayerEntity p : w.getPlayers()) {
			if (!hittable(p)) {
				continue;
			}
			if (Math.hypot(p.getX() - base.x, p.getZ() - base.z) < 1.4 && p.getY() > base.y - 0.5 && p.getY() < base.y + h) {
				p.damage(hit(w, d), dark ? 8f : 6f);
				p.addVelocity(0, 1.1, 0);
				p.velocityModified = true;
			}
		}
		Tasks.schedule(w.getServer(), 34, () -> {
			BossFx.animate(spike, BossFx.tf(0.7f, 0.01f, 0.7f, rot, -0.3f), 8);
			BossFx.animate(crystal, BossFx.tf(1.8f, 0.01f, 1.8f, rot, -0.3f), 8);
		});
		Tasks.schedule(w.getServer(), 44, () -> {
			BossFx.remove(spike);
			BossFx.remove(crystal);
		});
	}

	// ================================================================ вихрь Пустоты

	/** Чёрная дыра тянет к себе 3,5 секунды и взрывается. Беги и не прыгай. */
	public static int vortex(ServerWorld w, EnderDragonEntity d, ServerPlayerEntity target, int phase) {
		Random r = w.random;
		double side = r.nextDouble() * Math.PI * 2;
		double off = 3 + r.nextDouble() * 2;
		Vec3d g0 = groundUnder(w, target.getPos().add(Math.cos(side) * off, 0, Math.sin(side) * off));
		Vec3d g = g0.y <= w.getBottomY() ? target.getPos() : g0;
		Vec3d c = g.add(0, 2.2, 0);
		double radius = 14;
		int life = 70;
		warn(target, AlertKind.DANGER, "vortex");
		sound(w, c, ModSounds.VORTEX, 6f, 1f);
		Quaternionf q0 = new Quaternionf();
		DisplayEntity core = BossFx.block(w, c, Blocks.CRYING_OBSIDIAN.getDefaultState(), BossFx.tf(0.01f, 0.01f, 0.01f, q0, -0.7f), true);
		DisplayEntity shell = BossFx.block(w, c, Blocks.BLACK_STAINED_GLASS.getDefaultState(), BossFx.tf(0.01f, 0.01f, 0.01f, q0, -1.2f), false);
		int[] t = {0};
		start(() -> {
			int now = t[0]++;
			if (now % 10 == 0 && now < life) {
				float grow = Math.min(1f, now / 20f + 0.5f);
				float a = now * 0.12f;
				BossFx.animate(core, BossFx.tf(1.4f * grow, 1.4f * grow, 1.4f * grow,
					new Quaternionf().rotateY(a + 1.2f).rotateX(a * 0.7f + 0.6f), -0.7f * grow), 10);
				BossFx.animate(shell, BossFx.tf(2.4f * grow, 2.4f * grow, 2.4f * grow,
					new Quaternionf().rotateY(-a - 1.2f).rotateZ(a * 0.5f + 0.4f), -1.2f * grow), 10);
			}
			if (now < life) {
				for (int arm = 0; arm < 3; arm++) {
					for (int j = 0; j < 5; j++) {
						double rr = 2.0 + j * 2.4;
						double ang = now * 0.22 + arm * (Math.PI * 2 / 3) + rr * 0.35;
						Vec3d p = c.add(Math.cos(ang) * rr, (r.nextDouble() - 0.5) * 1.5, Math.sin(ang) * rr);
						Vec3d v = c.subtract(p).multiply(0.08).add(-Math.sin(ang) * 0.25, 0, Math.cos(ang) * 0.25);
						BossFx.flow(w, j % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.SQUID_INK, p, v.multiply(0.6));
					}
				}
				if (now % 4 == 0) {
					BossFx.ring(w, g.add(0, 0.2, 0), radius, BossFx.PURPLE, 40, true);
				}
				for (ServerPlayerEntity p : w.getPlayers()) {
					if (!hittable(p)) {
						continue;
					}
					Vec3d to = c.subtract(p.getPos());
					double dist = to.length();
					if (dist > radius || dist < 0.5) {
						continue;
					}
					double k = 0.045 + 0.04 * (1 - dist / radius);
					Vec3d pull = new Vec3d(to.x, 0, to.z).normalize().multiply(k);
					p.addVelocity(pull.x, p.isOnGround() ? 0 : 0.01, pull.z);
					p.velocityModified = true;
				}
				return true;
			}
			if (now == life) {
				BossFx.animate(core, BossFx.tf(0.01f, 0.01f, 0.01f, q0, 0f), 4);
				BossFx.animate(shell, BossFx.tf(0.01f, 0.01f, 0.01f, q0, 0f), 4);
				sound(w, c, SoundEvents.BLOCK_BEACON_DEACTIVATE, 4f, 0.5f);
				return true;
			}
			if (now == life + 5) {
				BossFx.remove(core);
				BossFx.remove(shell);
				sound(w, c, ModSounds.BOSS_BOOM, 9f, 1.1f);
				BossFx.particle(w, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				BossFx.particle(w, ParticleTypes.END_ROD, c.x, c.y, c.z, 140, 0.3, 0.3, 0.3, 0.7);
				BossFx.shake(w, c, 50, 25, true);
				for (ServerPlayerEntity p : w.getPlayers()) {
					if (!hittable(p)) {
						continue;
					}
					double dist = p.getPos().distanceTo(c);
					if (dist > 6) {
						continue;
					}
					p.damage(hit(w, d), (float) (3 + 9 * (1 - dist / 6)) + (phase >= 3 ? 2 : 0));
					Vec3d out = p.getPos().subtract(c).normalize().multiply(1.3);
					p.addVelocity(out.x, 0.6, out.z);
					p.velocityModified = true;
				}
				return false;
			}
			return true;
		});
		return life + 25;
	}

	// ================================================================ гигантский метеорит

	/** Огромная глыба магмы падает с неба. Большой красный круг — место удара. */
	public static int bigMeteor(ServerWorld w, EnderDragonEntity d, ServerPlayerEntity target) {
		Random r = w.random;
		Vec3d g = groundUnder(w, target.getPos().add((r.nextDouble() - 0.5) * 6, 0, (r.nextDouble() - 0.5) * 6));
		if (g.y <= w.getBottomY()) {
			return 0;
		}
		int fall = 50;
		float height = 48;
		double blast = 7.5;
		warn(target, AlertKind.DANGER, "meteor");
		sound(w, g, ModSounds.METEOR_FALL, 10f, 0.8f);
		Quaternionf[] rot = {BossFx.tilt(r, 40)};
		DisplayEntity rock = BossFx.block(w, g, Blocks.MAGMA_BLOCK.getDefaultState(), BossFx.tf(5, 5, 5, rot[0], height), true);
		int[] t = {0};
		start(() -> {
			int now = t[0]++;
			if (now < fall) {
				if (now % 10 == 0) {
					int to = Math.min(fall, now + 10);
					float h = height * (1 - to / (float) fall);
					rot[0] = new Quaternionf(rot[0]).rotateY(0.9f).rotateX(0.5f);
					BossFx.animate(rock, BossFx.tf(5, 5, 5, rot[0], h), 10);
				}
				double h = height * (1 - now / (double) fall) + 2.5;
				BossFx.particle(w, ParticleTypes.FLAME, g.x, g.y + h, g.z, 10, 1.6, 1.6, 1.6, 0.05);
				BossFx.particle(w, ParticleTypes.LARGE_SMOKE, g.x, g.y + h + 3, g.z, 6, 1.5, 2, 1.5, 0.02);
				if (now % 3 == 0) {
					BossFx.ring(w, g.add(0, 0.2, 0), blast, BossFx.RED, 44, true);
					BossFx.ring(w, g.add(0, 0.2, 0), blast * (now / (double) fall), BossFx.RED, 24, true);
				}
				return true;
			}
			if (now == fall) {
				sound(w, g, ModSounds.BOSS_BOOM, 16f, 0.65f);
				BossFx.particle(w, ParticleTypes.EXPLOSION_EMITTER, g.x, g.y + 1.5, g.z, 4, 2.5, 1, 2.5, 0);
				BossFx.particle(w, ParticleTypes.LAVA, g.x, g.y + 1, g.z, 70, 2, 1, 2, 0);
				BossFx.particle(w, DEBRIS, g.x, g.y + 1, g.z, 220, 3, 1, 3, 0.8);
				BossFx.shake(w, g, 110, 40, true);
				for (ServerPlayerEntity p : w.getPlayers()) {
					if (!hittable(p)) {
						continue;
					}
					double dist = p.getPos().distanceTo(g);
					if (dist > blast) {
						continue;
					}
					p.damage(w.getDamageSources().explosion(d, d), (float) (2 + 16 * (1 - dist / blast)));
					p.setOnFireFor(4);
					Vec3d out = new Vec3d(p.getX() - g.x, 0, p.getZ() - g.z).normalize().multiply(1.1);
					p.addVelocity(out.x, 0.9, out.z);
					p.velocityModified = true;
				}
				shockwave(w, d, g, 18, 0.8, 5f, true);
				BossFx.animate(rock, BossFx.tf(5.5f, 1.2f, 5.5f, rot[0], -0.6f), 3);
				return true;
			}
			if (now < fall + 60) {
				if (now == fall + 40) {
					BossFx.animate(rock, BossFx.tf(0.01f, 0.01f, 0.01f, rot[0], -0.6f), 20);
				}
				return true;
			}
			BossFx.remove(rock);
			return false;
		});
		return fall + 30;
	}

	// ================================================================ простые атаки

	/** Метеоритный дождь из драконьего пламени с красными метками на земле. */
	public static int meteors(ServerWorld w, EnderDragonEntity d, ServerPlayerEntity p, int count) {
		Random r = w.random;
		for (int i = 0; i < count; i++) {
			int delay = i * 6;
			double x = p.getX() + (r.nextDouble() - 0.5) * 16;
			double z = p.getZ() + (r.nextDouble() - 0.5) * 16;
			int ground = BossFx.ground(w, x, z);
			if (ground <= w.getBottomY()) {
				continue;
			}
			BossFx.marker(w, new Vec3d(x, ground, z), 50 + delay);
			Tasks.schedule(w.getServer(), delay, () -> {
				DragonFireballEntity fb = new DragonFireballEntity(w, d, new Vec3d((r.nextDouble() - 0.5) * 0.1, -1.0, (r.nextDouble() - 0.5) * 0.1));
				fb.refreshPositionAndAngles(x, ground + 32, z, 0, 0);
				w.spawnEntity(fb);
			});
		}
		w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_SHOOT, SoundCategory.HOSTILE, 2f, 0.6f);
		warn(p, AlertKind.EVENT, "meteors");
		return count * 6 + 20;
	}

	public static int swarm(ServerWorld w, ServerPlayerEntity p, int count) {
		Random r = w.random;
		for (int i = 0; i < count; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double rr = 3 + r.nextDouble() * 4;
			double x = p.getX() + Math.cos(a) * rr;
			double z = p.getZ() + Math.sin(a) * rr;
			int g = BossFx.ground(w, x, z);
			if (g <= w.getBottomY()) {
				continue;
			}
			EndermiteEntity m = EntityType.ENDERMITE.create(w);
			if (m == null) {
				continue;
			}
			m.refreshPositionAndAngles(x, g, z, r.nextFloat() * 360, 0);
			m.setTarget(p);
			w.spawnEntity(m);
			w.spawnParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 0.3, m.getZ(), 20, 0.3, 0.3, 0.3, 0.3);
		}
		warn(p, AlertKind.EVENT, "swarm");
		return 20;
	}

	/** Гусиный десант: дикие гуси плавно спускаются с неба и воруют вещи прямо во время боя. */
	public static int geese(ServerWorld w, ServerPlayerEntity p, int count) {
		for (int i = 0; i < count; i++) {
			WildGooseEntity g = ModEntities.WILD_GOOSE.create(w);
			if (g == null) {
				continue;
			}
			double x = p.getX() + (w.random.nextDouble() - 0.5) * 10;
			double z = p.getZ() + (w.random.nextDouble() - 0.5) * 10;
			g.refreshPositionAndAngles(x, p.getY() + 14 + w.random.nextInt(5), z, w.random.nextFloat() * 360, 0);
			g.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 200, 0, false, false));
			g.stealCooldown = 0;
			w.spawnEntity(g);
		}
		w.playSound(null, p.getBlockPos(), ModSounds.GOOSE_HONK, SoundCategory.HOSTILE, 2f, 0.8f);
		warn(p, AlertKind.EVENT, "geese");
		return 30;
	}

	/** Наковальни госстандарта: красные метки, через две секунды сверху падают наковальни. */
	public static int anvils(ServerWorld w, ServerPlayerEntity p, int count) {
		Random r = w.random;
		for (int i = 0; i < count; i++) {
			double x = p.getX() + (r.nextDouble() - 0.5) * 8;
			double z = p.getZ() + (r.nextDouble() - 0.5) * 8;
			int ground = BossFx.ground(w, x, z);
			if (ground <= w.getBottomY()) {
				continue;
			}
			BossFx.marker(w, new Vec3d(x, ground, z), 40);
			BlockPos top = BlockPos.ofFloored(x, Math.max(p.getY(), ground) + 14, z);
			Tasks.schedule(w.getServer(), 10 + i * 4, () -> {
				if (w.getBlockState(top).isAir()) {
					FallingBlockEntity.spawnFromBlock(w, top, Blocks.ANVIL.getDefaultState());
				}
			});
		}
		warn(p, AlertKind.EVENT, "anvils");
		return 40;
	}

	/** Лазер Пустоты: фиолетовая полоса на земле, через полторы секунды по ней бьёт стена света. */
	public static int laser(ServerWorld w, EnderDragonEntity d, ServerPlayerEntity target) {
		Random r = w.random;
		double ang = r.nextDouble() * Math.PI * 2;
		Vec3d dir = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
		Vec3d center = target.getPos();
		int warnTicks = 30;
		for (int t = 0; t < warnTicks; t += 3) {
			Tasks.schedule(w.getServer(), t, () -> {
				for (int i = -16; i <= 16; i++) {
					Vec3d q = center.add(dir.multiply(i));
					BossFx.particle(w, ParticleTypes.WITCH, q.x, q.y + 0.2, q.z, 2, 0.15, 0.05, 0.15, 0);
				}
			});
		}
		w.playSound(null, target.getBlockPos(), ModSounds.LASER_CHARGE, SoundCategory.HOSTILE, 2.5f, 0.7f);
		warn(target, AlertKind.DANGER, "laser");
		Tasks.schedule(w.getServer(), warnTicks, () -> {
			for (int i = -16; i <= 16; i++) {
				Vec3d q = center.add(dir.multiply(i));
				BossFx.particle(w, ParticleTypes.DRAGON_BREATH, q.x, q.y + 0.5, q.z, 6, 0.2, 0.4, 0.2, 0.02);
				for (int k = 0; k < 6; k++) {
					BossFx.particle(w, ParticleTypes.END_ROD, q.x, q.y + k * 1.5, q.z, 1, 0.08, 0.6, 0.08, 0.01);
				}
			}
			for (ServerPlayerEntity p : w.getPlayers()) {
				if (!hittable(p)) {
					continue;
				}
				Vec3d rel = p.getPos().subtract(center);
				double along = rel.dotProduct(dir);
				double side = rel.subtract(dir.multiply(along)).horizontalLength();
				if (Math.abs(along) <= 16.5 && side < 1.7 && Math.abs(rel.y) < 4) {
					p.damage(w.getDamageSources().magic(), 9f);
					p.setOnFireFor(3);
				}
			}
			w.playSound(null, target.getBlockPos(), ModSounds.BOSS_BOOM, SoundCategory.HOSTILE, 4f, 1.5f);
			BossFx.shake(w, center, 40, 15, false);
		});
		return warnTicks + 20;
	}

	public static int gravity(List<ServerPlayerEntity> players) {
		for (ServerPlayerEntity p : players) {
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 40, 1, false, true));
			warn(p, AlertKind.EVENT, "gravity");
		}
		return 30;
	}
}
