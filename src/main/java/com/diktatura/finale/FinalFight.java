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
	/** «Пятичасовой доклад»: сколько ещё тиков вождь читает речь (и лечится) и сколько урона его уже перебило. */
	private static int speechTicks;
	private static float speechDamage;
	/** «Налог на покушение»: до какого тика каждый удар по вождю стоит трудодней. */
	private static long taxUntil;
	private static final int TAX = 3;

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
		if (speechTicks > 0) {
			speechTick(w, b, players);
			return;
		}
		if (--nextSpecial > 0) {
			return;
		}
		Random r = w.random;
		nextSpecial = phase == 1 ? 120 + r.nextInt(60) : 80 + r.nextInt(40);
		ServerPlayerEntity target = b.getTarget() instanceof ServerPlayerEntity t && players.contains(t)
			? t : players.get(r.nextInt(players.size()));
		int roll = r.nextInt(phase == 1 ? 6 : 10);
		switch (roll) {
			case 0 -> slippers(w, b, target, phase == 1 ? 2 : 4);
			case 1 -> decree(w, b, players);
			case 2 -> propaganda(w, b, players);
			case 3 -> geese(w, b, target);
			case 4 -> confiscate(w, b, target);
			case 5 -> busts(w, b, players);
			case 6 -> guards(w, b, target);
			case 7 -> decoys(w, b, target);
			case 8 -> tax(w, b, players);
			default -> speech(w, b, players);
		}
	}

	// ------------------------------------------------------------------ команды для проверки

	public static int cmdGuardDown(net.minecraft.server.command.ServerCommandSource src) {
		MinecraftServer server = src.getServer();
		DiktaturaState state = DiktaturaState.get(server);
		state.guardDown = true;
		state.markDirty();
		Hud.headlineAll(server, HeadlineStyle.EMERGENCY, Text.translatable("diktatura.finale.guard_down"),
			Text.translatable("diktatura.finale.guard_down.sub"), 6);
		src.sendFeedback(() -> Text.translatable("diktatura.cmd.finale.guard_down"), true);
		return 1;
	}

	/** Сразу начать бой: охрана снимается, вождь встаёт с трона против вызвавшего. */
	public static int cmdStart(net.minecraft.server.command.ServerCommandSource src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity p = src.getPlayerOrThrow();
		MinecraftServer server = src.getServer();
		if (boss != null) {
			src.sendError(Text.translatable("diktatura.cmd.finale.running"));
			return 0;
		}
		DiktaturaState state = DiktaturaState.get(server);
		state.guardDown = true;
		state.markDirty();
		Dictator.ensureNpc(server);
		Entity npc = state.dictator == null ? null : server.getOverworld().getEntity(state.dictator);
		if (npc == null) {
			src.sendError(Text.translatable("diktatura.cmd.finale.no_npc"));
			return 0;
		}
		start(p, npc);
		src.sendFeedback(() -> Text.translatable("diktatura.cmd.finale.started"), true);
		return 1;
	}

	public static int cmdStop(net.minecraft.server.command.ServerCommandSource src) {
		if (boss == null) {
			src.sendError(Text.translatable("diktatura.cmd.finale.not_running"));
			return 0;
		}
		reset(src.getServer(), true);
		src.sendFeedback(() -> Text.translatable("diktatura.cmd.finale.stopped"), true);
		return 1;
	}

	/** Вернуть охрану на пост (бой снова только после дракона). */
	public static int cmdReset(net.minecraft.server.command.ServerCommandSource src) {
		if (boss != null) {
			reset(src.getServer(), true);
		}
		DiktaturaState state = DiktaturaState.get(src.getServer());
		state.guardDown = false;
		state.markDirty();
		src.sendFeedback(() -> Text.translatable("diktatura.cmd.finale.reset"), true);
		return 1;
	}

	// ------------------------------------------------------------------ приёмы вождя

	private static void slippers(ServerWorld w, DictatorBossEntity b, ServerPlayerEntity target, int count) {
		say(b, "diktatura.finale.say.slipper");
		b.wield(new ItemStack(ModItems.SLIPPER), count * 6 + 20);
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
		b.wield(new ItemStack(ModItems.MEGAPHONE), 60);
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

	/** Конфискация: оружие из руки вылетает в сторону «в пользу государства» (подобрать можно). */
	private static void confiscate(ServerWorld w, DictatorBossEntity b, ServerPlayerEntity target) {
		ItemStack held = target.getMainHandStack();
		if (held.isEmpty()) {
			slippers(w, b, target, 2);
			return;
		}
		say(b, "diktatura.finale.say.confiscate");
		ItemStack taken = held.copy();
		target.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, ItemStack.EMPTY);
		net.minecraft.entity.ItemEntity item = new net.minecraft.entity.ItemEntity(w, target.getX(), target.getEyeY(), target.getZ(), taken);
		double a = w.random.nextDouble() * Math.PI * 2;
		item.setVelocity(Math.cos(a) * 0.45, 0.45, Math.sin(a) * 0.45);
		item.setPickupDelay(40);
		w.spawnEntity(item);
		w.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.HOSTILE, 1.5f, 0.5f);
		Hud.alert(target, AlertKind.DANGER, Text.translatable("diktatura.finale.confiscated"),
			Text.translatable("diktatura.finale.confiscated.hint", taken.getName()), 4);
	}

	/** Бюсты вождя: красные метки, через секунду сверху падают золотые бюсты (и разбиваются). */
	private static void busts(ServerWorld w, DictatorBossEntity b, List<ServerPlayerEntity> players) {
		say(b, "diktatura.finale.say.busts");
		for (ServerPlayerEntity p : players) {
			for (int i = 0; i < 3; i++) {
				double x = p.getX() + (w.random.nextDouble() - 0.5) * 6;
				double z = p.getZ() + (w.random.nextDouble() - 0.5) * 6;
				int top = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
				// Под крышей зала — падают с потолка (не выше 10 блоков над игроком).
				int y = (int) Math.min(p.getY() + 10, Math.max(p.getY() + 3, top + 0.0));
				net.minecraft.util.math.BlockPos pos = net.minecraft.util.math.BlockPos.ofFloored(x, y, z);
				int floor = (int) Math.floor(p.getY());
				BossFx.marker(w, new Vec3d(x, floor, z), 30);
				Tasks.schedule(w.getServer(), 15 + i * 5, () -> {
					net.minecraft.util.math.BlockPos spot = pos;
					while (!w.getBlockState(spot).isAir() && spot.getY() > floor + 1) {
						spot = spot.down();
					}
					if (!w.getBlockState(spot).isAir()) {
						return;
					}
					net.minecraft.entity.FallingBlockEntity f = net.minecraft.entity.FallingBlockEntity.spawnFromBlock(w, spot,
						net.minecraft.block.Blocks.GOLD_BLOCK.getDefaultState());
					f.setHurtEntities(2.0f, 12);
					f.setDestroyedOnLanding();
				});
			}
		}
	}

	/** Размножение указом: рядом появляются два двойника вождя, падающие с одного удара. */
	private static void decoys(ServerWorld w, DictatorBossEntity b, ServerPlayerEntity target) {
		say(b, "diktatura.finale.say.decoys");
		for (int i = 0; i < 2; i++) {
			DictatorBossEntity c = ModEntities.DICTATOR_BOSS.create(w);
			if (c == null) {
				continue;
			}
			c.decoy = true;
			double a = w.random.nextDouble() * Math.PI * 2;
			c.refreshPositionAndAngles(b.getX() + Math.cos(a) * 3, b.getY(), b.getZ() + Math.sin(a) * 3, b.getYaw(), 0f);
			var hp = c.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH);
			if (hp != null) {
				hp.setBaseValue(1.0);
			}
			c.setHealth(1f);
			var dmg = c.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
			if (dmg != null) {
				dmg.setBaseValue(2.0);
			}
			c.setCustomName(b.getCustomName());
			c.setCustomNameVisible(true);
			c.setTarget(target);
			w.spawnEntity(c);
			SUMMONS.add(c.getUuid());
			w.spawnParticles(ParticleTypes.POOF, c.getX(), c.getY() + 1, c.getZ(), 15, 0.3, 0.6, 0.3, 0.02);
		}
		// Сам вождь тоже «переставляется», чтобы не было понятно, кто настоящий.
		double a = w.random.nextDouble() * Math.PI * 2;
		b.requestTeleport(b.getX() + Math.cos(a) * 2, b.getY(), b.getZ() + Math.sin(a) * 2);
		w.playSound(null, b.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 2f, 0.8f);
	}

	/** Налог на покушение: 6 секунд каждый удар по вождю стоит трудодней. */
	private static void tax(ServerWorld w, DictatorBossEntity b, List<ServerPlayerEntity> players) {
		say(b, "diktatura.finale.say.tax");
		taxUntil = w.getTime() + 120;
		for (ServerPlayerEntity p : players) {
			Hud.headline(p, HeadlineStyle.DECREE, Text.translatable("diktatura.finale.tax"),
				Text.translatable("diktatura.finale.tax.sub", TAX), 3);
		}
		w.playSound(null, b.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.HOSTILE, 2f, 0.6f);
	}

	/** Удар игрока по вождю: налог и прерывание доклада. */
	public static void onPlayerHit(ServerPlayerEntity p, float amount) {
		if (p.getServerWorld().getTime() < taxUntil) {
			if (Economy.take(p, TAX, "diktatura.money.reason.assault_tax", "")) {
				Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.finale.tax.paid", TAX), Text.empty(), 2);
			}
		}
		if (speechTicks > 0) {
			speechDamage += amount;
		}
	}

	/** Пятичасовой доклад: вождь стоит и читает речь, слушатели вянут, а он лечится. Перебейте его! */
	private static void speech(ServerWorld w, DictatorBossEntity b, List<ServerPlayerEntity> players) {
		say(b, "diktatura.finale.say.speech");
		speechTicks = 100;
		b.wield(new ItemStack(ModItems.MEGAPHONE), 110);
		speechDamage = 0;
		for (ServerPlayerEntity p : players) {
			Hud.headline(p, HeadlineStyle.PROCLAMATION, Text.translatable("diktatura.finale.speech"),
				Text.translatable("diktatura.finale.speech.sub"), 3);
		}
	}

	private static void speechTick(ServerWorld w, DictatorBossEntity b, List<ServerPlayerEntity> players) {
		speechTicks--;
		b.getNavigation().stop();
		if (speechDamage >= 15f) {
			speechTicks = 0;
			say(b, "diktatura.finale.say.interrupted");
			b.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2, false, true));
			nextSpecial = 60;
			return;
		}
		b.heal(b.getMaxHealth() * 0.0008f);
		if (speechTicks % 20 == 0) {
			BossFx.ring(w, b.getPos().add(0, 0.3, 0), 6, BossFx.GOLD, 30, true);
			w.spawnParticles(ParticleTypes.NOTE, b.getX(), b.getEyeY() + 0.6, b.getZ(), 3, 0.4, 0.2, 0.4, 1);
			for (ServerPlayerEntity p : players) {
				if (p.squaredDistanceTo(b) < 16 * 16) {
					p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0, false, false));
					p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 30, 1, false, false));
				}
			}
		}
		if (speechTicks == 0) {
			say(b, "diktatura.finale.say.speech_end");
		}
	}

	// ------------------------------------------------------------------ ничья по-диктаторски

	/** Вождь на 5% здоровья: «Стоп! Ничья!» — и награждает всех, кто дрался. */
	public static void draw(DictatorBossEntity b) {
		if (drawing || !(b.getWorld() instanceof ServerWorld w)) {
			return;
		}
		drawing = true;
		speechTicks = 0;
		taxUntil = 0;
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
		speechTicks = 0;
		taxUntil = 0;
		PARTICIPANTS.clear();
		if (restoreNpc) {
			Diktatura.LOGGER.info("Финальный бой окончен, вождь возвращается на трон");
			Dictator.ensureNpc(server);
		}
	}
}
