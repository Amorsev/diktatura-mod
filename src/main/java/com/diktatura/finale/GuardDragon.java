package com.diktatura.finale;

import com.diktatura.config.DiktaturaConfig;
import com.diktatura.goose.ModSounds;
import com.diktatura.mixin.EnderDragonFightAccessor;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.network.UiPayloads.HeadlineStyle;
import com.diktatura.state.DiktaturaState;
import com.diktatura.ui.Hud;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.EndSpikeFeature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Фаза 1 финального боя: дракон Края служит в госохране вождя. Больше здоровья, три фазы
 * с катсценами и очередь атак — одна большая атака за раз, чтобы от каждой можно было увернуться.
 * Когда дракон повержен, вождь остаётся без охраны — начинается фаза 2 ({@link FinalFight}).
 * Контроллер по мотивам DragonRage из мода-референса (MIT, © 2026 Gr1tW4lk).
 */
public final class GuardDragon {
	private static final String TAG_INIT = "diktatura_guard";
	private static final String TAG_PHASE = "diktatura_guard_phase";

	enum Attack {
		METEORS, ROAR, DIVE, SPIKES, VORTEX, SWARM, GEESE, ANVILS, LASER, BIG_METEOR, GRAVITY,
		SENTENCE, MINES, WITNESSES, TRANSFER, BREATH
	}

	/** Вес атаки в фазах 1, 2, 3 (0 — в этой фазе её нет). */
	private static final Map<Attack, int[]> WEIGHTS = new HashMap<>();

	static {
		WEIGHTS.put(Attack.METEORS, new int[] {3, 2, 1});
		WEIGHTS.put(Attack.ROAR, new int[] {2, 2, 2});
		WEIGHTS.put(Attack.DIVE, new int[] {3, 3, 3});
		WEIGHTS.put(Attack.SPIKES, new int[] {0, 3, 3});
		WEIGHTS.put(Attack.VORTEX, new int[] {0, 2, 2});
		WEIGHTS.put(Attack.SWARM, new int[] {0, 1, 1});
		WEIGHTS.put(Attack.GEESE, new int[] {1, 1, 1});
		WEIGHTS.put(Attack.ANVILS, new int[] {0, 0, 2});
		WEIGHTS.put(Attack.LASER, new int[] {0, 0, 2});
		WEIGHTS.put(Attack.BIG_METEOR, new int[] {0, 0, 3});
		WEIGHTS.put(Attack.GRAVITY, new int[] {0, 0, 1});
		WEIGHTS.put(Attack.SENTENCE, new int[] {2, 2, 2});
		WEIGHTS.put(Attack.BREATH, new int[] {2, 2, 2});
		WEIGHTS.put(Attack.MINES, new int[] {0, 2, 2});
		WEIGHTS.put(Attack.WITNESSES, new int[] {0, 1, 1});
		WEIGHTS.put(Attack.TRANSFER, new int[] {0, 1, 2});
	}

	public static final class State {
		public int phase = 1;
		int busy;
		int next = 200;
		int cinema;
		Attack last;
		Vec3d diveAim;
		int diveTicks;
		final Set<UUID> seen = new HashSet<>();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private GuardDragon() {
	}

	public static void registerEvents() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof EnderDragonEntity d && d.getWorld() instanceof ServerWorld w && DiktaturaConfig.get().finalFight) {
				STATES.remove(d.getUuid());
				onDefeated(w.getServer());
			}
		});
	}

	/** Охрана пала: вождь без защиты. */
	private static void onDefeated(MinecraftServer server) {
		DiktaturaState state = DiktaturaState.get(server);
		state.guardDown = true;
		state.markDirty();
		Hud.headlineAll(server, HeadlineStyle.EMERGENCY, Text.translatable("diktatura.finale.guard_down"),
			Text.translatable("diktatura.finale.guard_down.sub"), 8);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			BossFx.fx(p, "end_rage", 0);
		}
	}

	public static void tick(MinecraftServer server) {
		Tasks.tick(server);
		DragonAttacks.tickAll();
		if (!DiktaturaConfig.get().finalFight) {
			return;
		}
		ServerWorld end = server.getWorld(World.END);
		if (end == null) {
			return;
		}
		for (EnderDragonEntity d : end.getEntitiesByType(TypeFilter.instanceOf(EnderDragonEntity.class), e -> !e.isRemoved())) {
			try {
				tickDragon(end, d);
			} catch (RuntimeException e) {
				com.diktatura.Diktatura.LOGGER.error("Ошибка в бою с драконом госохраны", e);
			}
		}
	}

	static State state(EnderDragonEntity d) {
		return STATES.computeIfAbsent(d.getUuid(), u -> {
			State fresh = new State();
			if (d.getCommandTags().contains(TAG_PHASE + 3)) {
				fresh.phase = 3;
			} else if (d.getCommandTags().contains(TAG_PHASE + 2)) {
				fresh.phase = 2;
			}
			return fresh;
		});
	}

	private static void tickDragon(ServerWorld w, EnderDragonEntity d) {
		if (!d.getCommandTags().contains(TAG_INIT)) {
			d.addCommandTag(TAG_INIT);
			float hp = (float) Math.max(200, DiktaturaConfig.get().dragonHealth);
			var attr = d.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
			if (attr != null) {
				attr.setBaseValue(hp);
			}
			d.setHealth(hp);
		}
		if (d.ticksSinceDeath > 0 || d.getPhaseManager().getCurrent().getType() == PhaseType.DYING) {
			dying(w, d);
			return;
		}
		if (!d.isAlive()) {
			return;
		}
		State st = state(d);
		List<ServerPlayerEntity> players = w.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
			&& p.squaredDistanceTo(d) < 220 * 220);
		for (ServerPlayerEntity p : players) {
			if (st.seen.add(p.getUuid())) {
				intro(w, p);
			}
		}
		if (w.getTime() % 20 == 0) {
			bossBar(w, d, st.phase);
		}
		if (st.phase >= 3 && w.getTime() % 100 == 0) {
			for (ServerPlayerEntity p : players) {
				BossFx.fx(p, "end_rage", 140);
			}
		}
		if (players.isEmpty()) {
			return;
		}
		float frac = d.getHealth() / d.getMaxHealth();
		int want = frac > 0.6f ? 1 : frac > 0.3f ? 2 : 3;
		if (want > st.phase) {
			st.phase = want;
			d.addCommandTag(TAG_PHASE + want);
			st.diveAim = null;
			cinematic(w, d, st, want);
			bossBar(w, d, want);
		}
		if (st.cinema > 0) {
			if (--st.cinema == 0) {
				d.getPhaseManager().setPhase(PhaseType.HOLDING_PATTERN);
			} else if (d.getPhaseManager().getCurrent().getType() != PhaseType.HOVER) {
				d.getPhaseManager().setPhase(PhaseType.HOVER);
			}
			return;
		}
		if (st.diveAim != null) {
			DragonAttacks.diveTick(w, d, st);
		}
		if (st.busy > 0) {
			st.busy--;
			return;
		}
		PhaseType<?> type = d.getPhaseManager().getCurrent().getType();
		boolean perched = type == PhaseType.SITTING_FLAMING || type == PhaseType.SITTING_SCANNING
			|| type == PhaseType.SITTING_ATTACKING || type == PhaseType.LANDING || type == PhaseType.TAKEOFF
			|| type == PhaseType.LANDING_APPROACH;
		if (--st.next > 0) {
			return;
		}
		Random r = w.random;
		ServerPlayerEntity target = players.get(r.nextInt(players.size()));
		Attack a = pick(r, st.phase, st.last, perched);
		st.last = a;
		st.busy = run(w, d, st, a, target, players);
		if (st.phase >= 3 && r.nextFloat() < 0.3f && a != Attack.METEORS && a != Attack.ANVILS) {
			run(w, d, st, r.nextBoolean() ? Attack.METEORS : Attack.ANVILS, target, players);
		}
		int gap = st.phase == 1 ? 150 + r.nextInt(90) : st.phase == 2 ? 110 + r.nextInt(70) : 75 + r.nextInt(55);
		st.next = perched ? gap * 3 / 2 : gap;
	}

	private static Attack pick(Random r, int phase, Attack last, boolean perched) {
		List<Attack> pool = new ArrayList<>();
		for (Map.Entry<Attack, int[]> e : WEIGHTS.entrySet()) {
			Attack a = e.getKey();
			if (a == last || (perched && a == Attack.DIVE)) {
				continue;
			}
			for (int i = 0; i < e.getValue()[phase - 1]; i++) {
				pool.add(a);
			}
		}
		return pool.isEmpty() ? Attack.METEORS : pool.get(r.nextInt(pool.size()));
	}

	static int run(ServerWorld w, EnderDragonEntity d, State st, Attack a, ServerPlayerEntity target, List<ServerPlayerEntity> players) {
		int phase = st.phase;
		int busy = 0;
		switch (a) {
			case METEORS -> {
				for (ServerPlayerEntity p : players) {
					busy = Math.max(busy, DragonAttacks.meteors(w, d, p, 3 + phase));
				}
			}
			case ROAR -> busy = DragonAttacks.roar(w, d, players);
			case DIVE -> busy = DragonAttacks.dive(w, d, target, st);
			case SPIKES -> busy = DragonAttacks.spikes(w, d, target, phase);
			case VORTEX -> busy = DragonAttacks.vortex(w, d, target, phase);
			case SWARM -> {
				for (ServerPlayerEntity p : players) {
					busy = DragonAttacks.swarm(w, p, 3 + w.random.nextInt(3));
				}
			}
			case GEESE -> {
				for (ServerPlayerEntity p : players) {
					busy = DragonAttacks.geese(w, p, 3);
				}
			}
			case ANVILS -> {
				for (ServerPlayerEntity p : players) {
					busy = DragonAttacks.anvils(w, p, 4);
				}
			}
			case LASER -> busy = DragonAttacks.laser(w, d, target);
			case BIG_METEOR -> busy = DragonAttacks.bigMeteor(w, d, target);
			case GRAVITY -> busy = DragonAttacks.gravity(players);
			case SENTENCE -> busy = DragonAttacks.sentence(w, d, players);
			case BREATH -> busy = DragonAttacks.breathLine(w, d, target);
			case MINES -> {
				for (ServerPlayerEntity p : players) {
					busy = DragonAttacks.crystalMines(w, p, 2 + phase);
				}
			}
			case WITNESSES -> {
				for (ServerPlayerEntity p : players) {
					busy = DragonAttacks.witnesses(w, p, 2);
				}
			}
			case TRANSFER -> busy = DragonAttacks.transfer(w, target);
		}
		return busy;
	}

	// ------------------------------------------------------------------ шоу

	private static void intro(ServerWorld w, ServerPlayerEntity p) {
		Hud.headline(p, HeadlineStyle.EMERGENCY, Text.translatable("diktatura.dragon.intro"),
			Text.translatable("diktatura.dragon.intro.sub"), 6);
		w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 3f, 0.45f);
		w.playSound(null, p.getBlockPos(), ModSounds.BOSS_BOOM, SoundCategory.HOSTILE, 2f, 0.7f);
		BossFx.fx(p, "shake_hard", 40);
	}

	private static void cinematic(ServerWorld w, EnderDragonEntity d, State st, int phase) {
		st.cinema = 70;
		st.busy = 0;
		st.next = 60;
		d.getPhaseManager().setPhase(PhaseType.HOVER);
		Vec3d c = d.getPos();
		for (ServerPlayerEntity p : w.getPlayers()) {
			if (p.getPos().distanceTo(c) > 260) {
				continue;
			}
			Hud.headline(p, HeadlineStyle.EMERGENCY, Text.translatable("diktatura.dragon.phase" + phase),
				Text.translatable("diktatura.dragon.phase" + phase + ".sub"), 4);
			if (phase >= 3) {
				BossFx.fx(p, "end_rage", 140);
			}
			BossFx.fx(p, "shake_hard", 60);
			w.playSound(null, p.getBlockPos(), ModSounds.DRAGON_PHASE, SoundCategory.HOSTILE, 2f, 1f);
		}
		w.playSound(null, d.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 16f, 0.4f);
		Random r = w.random;
		for (int i = 0; i < 12; i++) {
			Tasks.schedule(w.getServer(), 4 + i * 4, () -> {
				double a = r.nextDouble() * Math.PI * 2;
				double rr = 8 + r.nextDouble() * 30;
				double x = Math.cos(a) * rr;
				double z = Math.sin(a) * rr;
				int g = BossFx.ground(w, x, z);
				if (g > w.getBottomY()) {
					BossFx.lightning(w, new Vec3d(x, g, z));
				}
			});
		}
		Tasks.schedule(w.getServer(), 20, () -> {
			if (!d.isAlive()) {
				return;
			}
			Vec3d body = d.getPos();
			BossFx.particle(w, ParticleTypes.EXPLOSION_EMITTER, body.x, body.y, body.z, 4, 6, 3, 6, 0);
			BossFx.particle(w, ParticleTypes.END_ROD, body.x, body.y, body.z, 200, 2, 2, 2, 0.8);
			BossFx.particle(w, phase >= 3 ? BossFx.RED : BossFx.PURPLE, body.x, body.y, body.z, 200, 8, 4, 8, 0);
			Vec3d g = DragonAttacks.groundUnder(w, body);
			if (g.y > w.getBottomY()) {
				DragonAttacks.shockwave(w, d, g, 42, 0.9, 4f, phase >= 3);
			}
			w.playSound(null, body.x, body.y, body.z, ModSounds.BOSS_BOOM, SoundCategory.HOSTILE, 16f, 0.6f);
		});
		if (phase == 2) {
			Tasks.schedule(w.getServer(), 30, () -> respawnCrystals(w, 4));
		}
	}

	private static void dying(ServerWorld w, EnderDragonEntity d) {
		Vec3d c = d.getPos();
		int t = d.ticksSinceDeath;
		if (w.getTime() % 5 == 0) {
			BossFx.shake(w, c, 220, 8, t > 100);
			Random r = w.random;
			BossFx.particle(w, ParticleTypes.EXPLOSION, c.x + (r.nextDouble() - 0.5) * 12, c.y + (r.nextDouble() - 0.5) * 6,
				c.z + (r.nextDouble() - 0.5) * 12, 2, 1, 1, 1, 0);
		}
		if (t == 196) {
			BossFx.shake(w, c, 260, 40, true);
			w.playSound(null, c.x, c.y, c.z, ModSounds.BOSS_BOOM, SoundCategory.HOSTILE, 30f, 0.5f);
			Vec3d g = DragonAttacks.groundUnder(w, c);
			if (g.y > w.getBottomY()) {
				DragonAttacks.shockwave(w, d, g, 60, 1.2, 0f, false);
			}
		}
	}

	private static void bossBar(ServerWorld w, EnderDragonEntity d, int phase) {
		Text name = Text.translatable("diktatura.dragon.name" + phase)
			.formatted(phase == 1 ? Formatting.LIGHT_PURPLE : phase == 2 ? Formatting.RED : Formatting.DARK_RED);
		if (!name.equals(d.getCustomName())) {
			d.setCustomName(name);
		}
		EnderDragonFight fight = w.getEnderDragonFight();
		if (fight == null) {
			return;
		}
		ServerBossBar bar = ((EnderDragonFightAccessor) fight).diktatura$bossBar();
		BossBar.Color color = phase == 1 ? BossBar.Color.PURPLE : phase == 2 ? BossBar.Color.PINK : BossBar.Color.RED;
		if (bar.getColor() != color) {
			bar.setColor(color);
		}
		if (bar.getStyle() != BossBar.Style.NOTCHED_10) {
			bar.setStyle(BossBar.Style.NOTCHED_10);
		}
		if (!name.equals(bar.getName())) {
			bar.setName(name);
		}
	}

	/** Возвращает кристаллы на вершины обсидиановых столбов. */
	public static int respawnCrystals(ServerWorld w, int max) {
		int made = 0;
		List<EndSpikeFeature.Spike> spikes = new ArrayList<>(EndSpikeFeature.getSpikes(w));
		java.util.Collections.shuffle(spikes, new java.util.Random(w.random.nextLong()));
		for (EndSpikeFeature.Spike s : spikes) {
			if (made >= max) {
				break;
			}
			BlockPos top = new BlockPos(s.getCenterX(), s.getHeight(), s.getCenterZ());
			Box near = new Box(top).expand(3, 4, 3);
			if (!w.getEntitiesByClass(EndCrystalEntity.class, near, e -> true).isEmpty()) {
				continue;
			}
			w.setBlockState(top, Blocks.BEDROCK.getDefaultState());
			EndCrystalEntity c = EntityType.END_CRYSTAL.create(w);
			if (c == null) {
				continue;
			}
			c.refreshPositionAndAngles(top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5, w.random.nextFloat() * 360f, 0);
			c.setShowBottom(true);
			w.spawnEntity(c);
			BossFx.lightning(w, Vec3d.ofBottomCenter(top.up()));
			made++;
		}
		return made;
	}

	public static void clear() {
		STATES.clear();
		DragonAttacks.clear();
		Tasks.clear();
	}

	public static java.util.List<String> attackNames() {
		java.util.List<String> out = new ArrayList<>();
		for (Attack a : Attack.values()) {
			out.add(a.name().toLowerCase(java.util.Locale.ROOT));
		}
		return out;
	}

	/** Команда: заставить дракона госохраны провести атаку прямо сейчас. */
	public static int cmdAttack(net.minecraft.server.command.ServerCommandSource src, String name) {
		Attack a;
		try {
			a = Attack.valueOf(name.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			src.sendError(Text.translatable("diktatura.cmd.finale.no_attack", name));
			return 0;
		}
		ServerWorld end = src.getServer().getWorld(World.END);
		EnderDragonEntity d = end == null ? null
			: end.getEntitiesByType(TypeFilter.instanceOf(EnderDragonEntity.class), e -> e.isAlive()).stream().findFirst().orElse(null);
		List<ServerPlayerEntity> players = end == null ? List.of()
			: end.getPlayers(p -> p.isAlive() && !p.isSpectator());
		if (d == null || players.isEmpty()) {
			src.sendError(Text.translatable("diktatura.cmd.finale.no_dragon"));
			return 0;
		}
		ServerPlayerEntity target = players.get(end.random.nextInt(players.size()));
		run(end, d, state(d), a, target, players);
		src.sendFeedback(() -> Text.translatable("diktatura.cmd.finale.attack", name), false);
		return 1;
	}

	public static void alertAll(MinecraftServer server, String key) {
		Hud.alertAll(server, AlertKind.EVENT, Text.translatable(key), Text.empty(), 4);
	}
}
