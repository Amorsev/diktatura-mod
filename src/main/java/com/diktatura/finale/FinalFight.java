package com.diktatura.finale;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.control.Enforcers;
import com.diktatura.control.SniperSkeletonEntity;
import com.diktatura.dictator.Dictator;
import com.diktatura.economy.Economy;
import com.diktatura.goose.ModSounds;
import com.diktatura.goose.WildGooseEntity;
import com.diktatura.item.ModItems;
import com.diktatura.item.SlipperEntity;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.network.UiPayloads.HeadlineStyle;
import com.diktatura.npc.ModEntities;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.ui.Hud;
import com.diktatura.ui.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Фаза 2 финального боя: вождь без охраны. Начинается, когда после гибели дракона госохраны
 * кто-то бьёт вождя. Вождь встаёт с трона, швыряет тапки, издаёт указы прямо посреди боя,
 * зовёт охрану и гусей. Убить его нельзя: на 5% здоровья он объявляет ничью в свою пользу,
 * а участники получают орден «За ничью с вождём» и иммунитет от законов на несколько дней.
 */
public final class FinalFight {
	private static final String[] DECREES = {"no_hurry", "no_looking", "no_weapons", "no_gravity", "dance"};
	private static final int RADIUS = 48;

	private static UUID boss;
	private static ServerBossBar bar;
	private static final Set<UUID> PARTICIPANTS = new HashSet<>();
	private static final List<UUID> SUMMONS = new ArrayList<>();
	private static int nextSpecial;
	private static int phase;
	private static int emptyTicks;
	private static boolean drawing;

	private FinalFight() {
	}

	public static void registerEvents() {
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> reset(s, false));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (boss != null && !drawing && entity instanceof ServerPlayerEntity p && PARTICIPANTS.contains(p.getUuid())
				&& p.getServer() != null && p.getServer().getOverworld().getEntity(boss) instanceof DictatorBossEntity b) {
				say(b, "diktatura.finale.taunt." + p.getRandom().nextInt(3));
			}
		});
	}

	public static boolean isActive() {
		return boss != null;
	}

	public static boolean isDrawing() {
		return drawing;
	}

	public static void participant(ServerPlayerEntity p) {
		PARTICIPANTS.add(p.getUuid());
	}

	/** Можно ли сейчас вызвать вождя на бой (охрана пала, бой не идёт). */
	public static boolean canStart(MinecraftServer server) {
		return DiktaturaConfig.get().finalFight && boss == null && DiktaturaState.get(server).guardDown;
	}

	public static void start(ServerPlayerEntity challenger, Entity npc) {
		MinecraftServer server = challenger.getServer();
		ServerWorld w = challenger.getServerWorld();
		DictatorBossEntity b = ModEntities.DICTATOR_BOSS.create(w);
		if (b == null) {
			return;
		}
		b.refreshPositionAndAngles(npc.getX(), npc.getY(), npc.getZ(), npc.getYaw(), 0f);
		float hp = (float) Math.max(50, DiktaturaConfig.get().dictatorHealth);
		var attr = b.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH);
		if (attr != null) {
			attr.setBaseValue(hp);
		}
		b.setHealth(hp);
		b.setCustomName(Text.translatable("entity.diktatura.dictator").formatted(Formatting.GOLD, Formatting.BOLD));
		b.setCustomNameVisible(true);
		b.setTarget(challenger);
		npc.discard();
		DiktaturaState.get(server).dictator = null;
		w.spawnEntity(b);
		boss = b.getUuid();
		phase = 1;
		drawing = false;
		emptyTicks = 0;
		nextSpecial = 80;
		PARTICIPANTS.clear();
		PARTICIPANTS.add(challenger.getUuid());
		SUMMONS.clear();
		bar = new ServerBossBar(Text.translatable("diktatura.finale.bar").formatted(Formatting.GOLD),
			BossBar.Color.YELLOW, BossBar.Style.NOTCHED_10);
		w.playSound(null, b.getBlockPos(), ModSounds.BOSS_BOOM, SoundCategory.HOSTILE, 3f, 0.8f);
		w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, b.getX(), b.getY() + 1, b.getZ(), 1, 0, 0, 0, 0);
		for (ServerPlayerEntity p : near(w, b)) {
			Hud.headline(p, HeadlineStyle.EMERGENCY, Text.translatable("diktatura.finale.start"),
				Text.translatable("diktatura.finale.start.sub"), 5);
			BossFx.fx(p, "shake_hard", 30);
		}
		say(b, "diktatura.finale.say.start");
	}

	private static List<ServerPlayerEntity> near(ServerWorld w, Entity b) {
		return w.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(b) < RADIUS * RADIUS);
	}

	private static void say(Entity b, String key) {
		Voice.say(b, Text.translatable("entity.diktatura.dictator"), Text.translatable(key), SoundEvents.ENTITY_VILLAGER_AMBIENT, 0.6f);
	}

	public static void tick(MinecraftServer server) {
		if (boss == null) {
			return;
		}
		ServerWorld w = server.getOverworld();
		Entity e = w.getEntity(boss);
		if (!(e instanceof DictatorBossEntity b) || !b.isAlive()) {
			// Вождь пропал (выгрузился чанк, команда /kill) — бой отменяется, вождь вернётся на трон.
			reset(server, true);
			return;
		}
		List<ServerPlayerEntity> players = near(w, b);
		if (bar != null) {
			bar.setPercent(b.getHealth() / b.getMaxHealth());
			for (ServerPlayerEntity p : new ArrayList<>(bar.getPlayers())) {
				if (!players.contains(p)) {
					bar.removePlayer(p);
				}
			}
			for (ServerPlayerEntity p : players) {
				bar.addPlayer(p);
			}
		}
		if (drawing) {
			return;
		}
		if (players.isEmpty()) {
			if (++emptyTicks > 20 * 30) {
				say(b, "diktatura.finale.say.leave");
				Hud.alertAll(server, AlertKind.INFO, Text.translatable("diktatura.finale.retreat"),
					Text.translatable("diktatura.finale.retreat.hint"), 6);
				reset(server, true);
			}
			return;
		}
		emptyTicks = 0;
		if (phase == 1 && b.getHealth() < b.getMaxHealth() * 0.5f) {
			phase = 2;
			nextSpecial = 40;
			b.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 600, 0, false, false));
			if (bar != null) {
				bar.setColor(BossBar.Color.RED);
			}
			for (ServerPlayerEntity p : players) {
				Hud.headline(p, HeadlineStyle.DECREE, Text.translatable("diktatura.finale.phase2"),
					Text.translatable("diktatura.finale.phase2.sub"), 5);
			}
			say(b, "diktatura.finale.say.phase2");
		}
		if (--nextSpecial > 0) {
			return;
		}
		Random r = w.random;
		nextSpecial = phase == 1 ? 120 + r.nextInt(60) : 80 + r.nextInt(40);
		ServerPlayerEntity target = b.getTarget() instanceof ServerPlayerEntity t && players.contains(t)
			? t : players.get(r.nextInt(players.size()));
		int roll = r.nextInt(phase == 1 ? 4 : 5);
		switch (roll) {
			case 0 -> slippers(w, b, target, phase == 1 ? 2 : 4);
			case 1 -> decree(w, b, players);
			case 2 -> propaganda(w, b, players);
			case 3 -> geese(w, b, target);
			default -> guards(w, b, target);
		}
	}

	// ------------------------------------------------------------------ приёмы вождя

	private static void slippers(ServerWorld w, DictatorBossEntity b, ServerPlayerEntity target, int count) {
		say(b, "diktatura.finale.say.slipper");
		for (int i = 0; i < count; i++) {
			Tasks.schedule(w.getServer(), i * 6, () -> {
				if (!b.isAlive() || drawing) {
					return;
				}
				SlipperEntity s = new SlipperEntity(w, b, new ItemStack(ModItems.SLIPPER));
				s.bossThrow = true;
				s.setPosition(b.getX(), b.getEyeY() - 0.2, b.getZ());
				Vec3d to = target.getEyePos().subtract(s.getPos());
				s.setVelocity(to.x, to.y + 0.1, to.z, 1.4f, 4f);
				w.spawnEntity(s);
				w.playSound(null, b.getBlockPos(), SoundEvents.ENTITY_WITCH_THROW, SoundCategory.HOSTILE, 1f, 0.8f);
			});
		}
	}

	private static void decree(ServerWorld w, DictatorBossEntity b, List<ServerPlayerEntity> players) {
		String id = DECREES[w.random.nextInt(DECREES.length)];
		say(b, "diktatura.finale.decree." + id + ".say");
		for (ServerPlayerEntity p : players) {
			Hud.headline(p, HeadlineStyle.DECREE, Text.translatable("diktatura.finale.decree"),
				Text.translatable("diktatura.finale.decree." + id), 3);
			switch (id) {
				case "no_hurry" -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1));
				case "no_looking" -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 70, 0));
				case "no_weapons" -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 1));
				case "no_gravity" -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 40, 0));
				default -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 140, 0));
			}
		}
	}

	private static void propaganda(ServerWorld w, DictatorBossEntity b, List<ServerPlayerEntity> players) {
		say(b, "diktatura.finale.say.propaganda");
		w.playSound(null, b.getBlockPos(), ModSounds.SHOCKWAVE, SoundCategory.HOSTILE, 3f, 1.2f);
		Vec3d c = b.getPos();
		for (int k = 0; k < 4; k++) {
			int kk = k;
			Tasks.schedule(w.getServer(), k * 2, () -> BossFx.ring(w, c.add(0, 0.3, 0), 2 + kk * 2, BossFx.GOLD, 16 + kk * 6, true));
		}
		for (ServerPlayerEntity p : players) {
			double d = p.getPos().distanceTo(c);
			if (d > 8) {
				continue;
			}
			p.damage(w.getDamageSources().mobAttack(b), 3f);
			Vec3d out = new Vec3d(p.getX() - c.x, 0, p.getZ() - c.z).normalize().multiply(1.2);
			p.addVelocity(out.x, 0.5, out.z);
			p.velocityModified = true;
			BossFx.fx(p, "shake", 20);
		}
	}

	private static void geese(ServerWorld w, DictatorBossEntity b, ServerPlayerEntity target) {
		say(b, "diktatura.finale.say.geese");
		for (int i = 0; i < 3; i++) {
			WildGooseEntity g = ModEntities.WILD_GOOSE.create(w);
			if (g == null) {
				continue;
			}
			g.refreshPositionAndAngles(b.getX() + (w.random.nextDouble() - 0.5) * 3, b.getY(), b.getZ() + (w.random.nextDouble() - 0.5) * 3,
				w.random.nextFloat() * 360f, 0f);
			g.stealCooldown = 0;
			w.spawnEntity(g);
			SUMMONS.add(g.getUuid());
		}
		w.playSound(null, b.getBlockPos(), ModSounds.GOOSE_HONK, SoundCategory.HOSTILE, 2f, 0.9f);
	}

	private static void guards(ServerWorld w, DictatorBossEntity b, ServerPlayerEntity target) {
		say(b, "diktatura.finale.say.guards");
		for (int i = 0; i < 2; i++) {
			SniperSkeletonEntity s = ModEntities.SNIPER.create(w);
			if (s != null) {
				s.refreshPositionAndAngles(b.getX() + (i == 0 ? 2 : -2), b.getY(), b.getZ() + 1, b.getYaw(), 0f);
				s.initialize(w, w.getLocalDifficulty(b.getBlockPos()), SpawnReason.MOB_SUMMONED, null);
				s.equipSniper();
				s.addCommandTag(Enforcers.TAG_ROLLED);
				s.setTarget(target);
				w.spawnEntity(s);
				SUMMONS.add(s.getUuid());
			}
			ZombieEntity z = EntityType.ZOMBIE.create(w);
			if (z != null) {
				z.refreshPositionAndAngles(b.getX() + (i == 0 ? 2 : -2), b.getY(), b.getZ() - 1, b.getYaw(), 0f);
				z.initialize(w, w.getLocalDifficulty(b.getBlockPos()), SpawnReason.MOB_SUMMONED, null);
				z.addCommandTag(Enforcers.TAG_ROLLED);
				Enforcers.makeBuilder(z);
				z.setTarget(target);
				w.spawnEntity(z);
				SUMMONS.add(z.getUuid());
			}
			w.spawnParticles(ParticleTypes.POOF, b.getX(), b.getY() + 1, b.getZ(), 20, 1.5, 0.5, 1.5, 0.02);
		}
	}

	// ------------------------------------------------------------------ ничья по-диктаторски

	/** Вождь на 5% здоровья: «Стоп! Ничья!» — и награждает всех, кто дрался. */
	public static void draw(DictatorBossEntity b) {
		if (drawing || !(b.getWorld() instanceof ServerWorld w)) {
			return;
		}
		drawing = true;
		MinecraftServer server = w.getServer();
		b.setAiDisabled(true);
		b.setTarget(null);
		b.getNavigation().stop();
		clearSummons(w);
		if (bar != null) {
			bar.setColor(BossBar.Color.GREEN);
			bar.setName(Text.translatable("diktatura.finale.bar.draw").formatted(Formatting.GREEN));
		}
		say(b, "diktatura.finale.say.draw0");
		Tasks.schedule(server, 50, () -> say(b, "diktatura.finale.say.draw1"));
		Tasks.schedule(server, 100, () -> say(b, "diktatura.finale.say.draw2"));
		Tasks.schedule(server, 150, () -> {
			Hud.headlineAll(server, HeadlineStyle.PROCLAMATION, Text.translatable("diktatura.finale.draw"),
				Text.translatable("diktatura.finale.draw.sub"), 8);
			award(server, w, b);
			for (int i = 0; i < 6; i++) {
				Tasks.schedule(server, i * 8, () -> firework(w, b.getPos().add((w.random.nextDouble() - 0.5) * 8, 1, (w.random.nextDouble() - 0.5) * 8)));
			}
		});
		Tasks.schedule(server, 220, () -> {
			w.spawnParticles(ParticleTypes.POOF, b.getX(), b.getY() + 1, b.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
			DiktaturaState state = DiktaturaState.get(server);
			state.guardDown = false;
			state.markDirty();
			reset(server, true);
		});
	}

	private static void award(MinecraftServer server, ServerWorld w, DictatorBossEntity b) {
		DiktaturaConfig cfg = DiktaturaConfig.get();
		long until = server.getOverworld().getTime() + (long) Math.max(1, cfg.immunityDays) * 24000L;
		DiktaturaState state = DiktaturaState.get(server);
		for (UUID id : PARTICIPANTS) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
			if (p == null) {
				continue;
			}
			PlayerRecord rec = state.player(p);
			rec.immuneUntil = Math.max(rec.immuneUntil, until);
			p.getInventory().offerOrDrop(new ItemStack(ModItems.ORDER));
			Economy.add(p, 50, "diktatura.money.reason.draw", "", true);
			Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.finale.award"),
				Text.translatable("diktatura.finale.award.hint", cfg.immunityDays), 10);
		}
		state.markDirty();
	}

	private static void firework(ServerWorld w, Vec3d at) {
		FireworkRocketEntity f = new FireworkRocketEntity(w, at.x, at.y, at.z, new ItemStack(Items.FIREWORK_ROCKET));
		w.spawnEntity(f);
	}

	private static void clearSummons(ServerWorld w) {
		for (UUID id : SUMMONS) {
			Entity e = w.getEntity(id);
			if (e instanceof MobEntity m && m.isAlive()) {
				w.spawnParticles(ParticleTypes.POOF, m.getX(), m.getY() + 0.5, m.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
				m.discard();
			}
		}
		SUMMONS.clear();
	}

	/** Закончить бой: убрать босса и охрану, вернуть вождя-НПС на трон. */
	private static void reset(MinecraftServer server, boolean restoreNpc) {
		ServerWorld w = server.getOverworld();
		if (boss != null && w.getEntity(boss) instanceof DictatorBossEntity b) {
			b.discard();
		}
		clearSummons(w);
		if (bar != null) {
			bar.clearPlayers();
			bar = null;
		}
		boss = null;
		drawing = false;
		PARTICIPANTS.clear();
		if (restoreNpc) {
			Diktatura.LOGGER.info("Финальный бой окончен, вождь возвращается на трон");
			Dictator.ensureNpc(server);
		}
	}
}
