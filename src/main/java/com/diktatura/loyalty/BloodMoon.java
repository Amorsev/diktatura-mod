package com.diktatura.loyalty;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.control.CurfewDoubleEntity;
import com.diktatura.control.CurfewDoubles;
import com.diktatura.economy.Economy;
import com.diktatura.finale.BossFx;
import com.diktatura.goose.ModSounds;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.network.UiPayloads.HeadlineStyle;
import com.diktatura.state.DiktaturaState;
import com.diktatura.ui.Hud;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Кровавая луна — ночь народного гнева, новый вид восстания. Начинается, если восстание вспыхнуло
 * ночью, и сама по себе раз в N ночей. До рассвета: законы не действуют (как при восстании),
 * небо и туман красные, мобов вдвое больше, силовиков среди них чаще, спать нельзя, а ближе
 * к полуночи к каждому приходит двойник. Кто дожил до рассвета — получает трудодни за стойкость.
 * По мотивам Кровавой луны из мода-референса (MIT, © 2026 Gr1tW4lk).
 */
public final class BloodMoon {
	private static final int SURVIVAL_BONUS = 15;
	/** Кому двойник уже приходил этой луной. */
	private static final Set<UUID> DOUBLES = new HashSet<>();

	private BloodMoon() {
	}

	public static void registerEvents() {
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> {
			if (player.getServer() != null && isActive(player.getServer())) {
				if (player instanceof ServerPlayerEntity sp) {
					Hud.alert(sp, AlertKind.DANGER, Text.translatable("diktatura.bloodmoon.no_sleep"),
						Text.translatable("diktatura.bloodmoon.no_sleep.hint"), 4);
				}
				return PlayerEntity.SleepFailureReason.OTHER_PROBLEM;
			}
			return null;
		});
	}

	public static boolean isActive(MinecraftServer server) {
		return DiktaturaState.get(server).bloodMoon && LoyaltySystem.isRebellion(server);
	}

	/** Активна ли сейчас (для механик без доступа к серверу). */
	public static boolean isActive() {
		MinecraftServer s = Diktatura.server();
		return s != null && isActive(s);
	}

	private static boolean night(ServerWorld w) {
		long t = w.getTimeOfDay() % 24000L;
		return t >= 13000L && t < 23000L;
	}

	private static int ticksToDawn(ServerWorld w) {
		long t = w.getTimeOfDay() % 24000L;
		long left = 23000L - t;
		return (int) Math.max(20, (left / 20) * 20);
	}

	/**
	 * Начать Кровавую луну, если сейчас ночь. {@code force} — по команде (не проверять настройку).
	 *
	 * @return true, если луна началась (обычное восстание тогда не нужно)
	 */
	public static boolean start(MinecraftServer server, boolean force) {
		DiktaturaConfig cfg = DiktaturaConfig.get();
		ServerWorld w = server.getOverworld();
		if ((!force && !cfg.bloodMoon) || !night(w)) {
			return false;
		}
		DiktaturaState state = DiktaturaState.get(server);
		state.bloodMoon = true;
		state.bloodMoonDay = w.getTimeOfDay() / 24000L;
		state.rebellionTicks = ticksToDawn(w);
		state.rebellionCooldown = state.rebellionTicks + cfg.rebellionCooldownMinutes * 1200;
		state.markDirty();
		DOUBLES.clear();
		Hud.headlineAll(server, HeadlineStyle.REBELLION, Text.translatable("diktatura.bloodmoon.start"),
			Text.translatable("diktatura.bloodmoon.start.sub"), 7);
		for (ServerPlayerEntity p : w.getPlayers()) {
			BossFx.fx(p, "bloodmoon", state.rebellionTicks);
			w.playSound(null, p.getBlockPos(), ModSounds.DRAGON_PHASE, SoundCategory.AMBIENT, 1.2f, 0.6f);
		}
		return true;
	}

	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % 20 != 3) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		ServerWorld w = server.getOverworld();
		DiktaturaConfig cfg = DiktaturaConfig.get();
		if (state.bloodMoon && !LoyaltySystem.isRebellion(server)) {
			end(server, state, w);
			return;
		}
		if (!state.bloodMoon) {
			// Сама по себе: раз в N ночей, в начале ночи.
			long day = w.getTimeOfDay() / 24000L;
			long t = w.getTimeOfDay() % 24000L;
			if (cfg.bloodMoon && cfg.bloodMoonEveryNights > 0 && t >= 13000L && t < 13400L
				&& day % cfg.bloodMoonEveryNights == cfg.bloodMoonEveryNights - 1 && state.bloodMoonDay != day
				&& !LoyaltySystem.isRebellion(server) && !w.getPlayers().isEmpty()) {
				start(server, false);
			}
			return;
		}
		// Идёт Кровавая луна.
		long sec = Diktatura.ticks() / 20;
		if (sec % 5 == 0) {
			for (ServerPlayerEntity p : w.getPlayers()) {
				BossFx.fx(p, "bloodmoon", state.rebellionTicks);
			}
		}
		Random r = w.getRandom();
		long tod = w.getTimeOfDay() % 24000L;
		for (ServerPlayerEntity p : w.getPlayers()) {
			if (p.isSpectator() || p.isCreative()) {
				continue;
			}
			if (cfg.bloodMoonExtraMobs && sec % 10 == 0) {
				spawnNightmare(w, p, r);
			}
			if (tod > 16000L && cfg.curfewDouble && DOUBLES.add(p.getUuid())) {
				CurfewDoubleEntity d = CurfewDoubles.spawnFor(w, p);
				if (d != null) {
					d.bloodMoon = true;
				}
			}
		}
	}

	/** Ещё один враждебный моб рядом с игроком, если вокруг их пока немного. */
	private static void spawnNightmare(ServerWorld w, ServerPlayerEntity p, Random r) {
		int near = w.getEntitiesByClass(HostileEntity.class, new Box(p.getBlockPos()).expand(40), e -> true).size();
		if (near >= 14) {
			return;
		}
		for (int attempt = 0; attempt < 6; attempt++) {
			double a = r.nextDouble() * Math.PI * 2;
			int rr = 16 + r.nextInt(13);
			int x = p.getBlockX() + MathHelper.floor(Math.cos(a) * rr);
			int z = p.getBlockZ() + MathHelper.floor(Math.sin(a) * rr);
			if (!w.isChunkLoaded(x >> 4, z >> 4)) {
				continue;
			}
			int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			if (Math.abs(y - p.getBlockY()) > 12 || !w.getBlockState(pos).isAir() || !w.getBlockState(pos.up()).isAir()
				|| !w.getBlockState(pos.down()).isSolidBlock(w, pos.down())) {
				continue;
			}
			float roll = r.nextFloat();
			EntityType<? extends HostileEntity> type = roll < 0.4f ? EntityType.ZOMBIE : roll < 0.65f ? EntityType.SKELETON
				: roll < 0.85f ? EntityType.SPIDER : EntityType.CREEPER;
			HostileEntity m = type.create(w);
			if (m == null) {
				continue;
			}
			m.refreshPositionAndAngles(x + 0.5, y, z + 0.5, r.nextFloat() * 360f, 0f);
			m.initialize(w, w.getLocalDifficulty(pos), SpawnReason.EVENT, null);
			w.spawnEntity(m);
			return;
		}
	}

	private static void end(MinecraftServer server, DiktaturaState state, ServerWorld w) {
		state.bloodMoon = false;
		state.markDirty();
		DOUBLES.clear();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			BossFx.fx(p, "bloodmoon", 0);
			if (p.isAlive() && !p.isSpectator() && p.getWorld() == w) {
				Economy.add(p, SURVIVAL_BONUS, "diktatura.money.reason.bloodmoon", "", true);
			}
		}
		Hud.headlineAll(server, HeadlineStyle.PROCLAMATION, Text.translatable("diktatura.bloodmoon.end"),
			Text.translatable("diktatura.bloodmoon.end.sub", SURVIVAL_BONUS), 6);
	}

	/** Команда: устроить Кровавую луну сейчас (днём — сначала наступает ночь). */
	public static int cmdStart(net.minecraft.server.command.ServerCommandSource src) {
		MinecraftServer server = src.getServer();
		ServerWorld w = server.getOverworld();
		if (!night(w)) {
			long day = w.getTimeOfDay() / 24000L;
			w.setTimeOfDay(day * 24000L + 13000L);
		}
		DiktaturaState state = DiktaturaState.get(server);
		if (state.bloodMoon && LoyaltySystem.isRebellion(server)) {
			src.sendError(Text.translatable("diktatura.cmd.bloodmoon.running"));
			return 0;
		}
		boolean ok = start(server, true);
		if (ok) {
			src.sendFeedback(() -> Text.translatable("diktatura.cmd.bloodmoon.started"), true);
		}
		return ok ? 1 : 0;
	}

	/** Команда: закончить луну сейчас (восстание тоже заканчивается). */
	public static int cmdStop(net.minecraft.server.command.ServerCommandSource src) {
		DiktaturaState state = DiktaturaState.get(src.getServer());
		if (!state.bloodMoon) {
			src.sendError(Text.translatable("diktatura.cmd.bloodmoon.not_running"));
			return 0;
		}
		state.rebellionTicks = 20;
		state.markDirty();
		src.sendFeedback(() -> Text.translatable("diktatura.cmd.bloodmoon.stopped"), true);
		return 1;
	}
}
