package com.diktatura.bureau;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.state.DiktaturaState;
import com.diktatura.ui.Hud;
import com.diktatura.ui.Voice;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Ведомство погоды: по плану (раз в 20–35 минут) объявляет осадки — коровы на парашютах,
 * наковальни или гуманитарная помощь (хлеб с неба). Куда сейчас упадёт наковальня — показывает
 * красная метка на земле. Действует на игроков в верхнем мире под открытым небом.
 */
public final class Weather {
	public enum Kind { COWS, ANVILS, AID;
		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.1f, 0.1f), 1.6f);

	private record Marker(ServerWorld world, BlockPos pos, long until) {
	}

	private static final List<Marker> MARKERS = new ArrayList<>();
	private static Kind active;
	private static long activeUntil;

	private Weather() {
	}

	public static void tick(MinecraftServer server) {
		long t = Diktatura.ticks();
		tickMarkers(t);
		ServerWorld ow = server.getOverworld();
		if (active != null) {
			if (t >= activeUntil) {
				active = null;
			} else {
				tickActive(ow, t);
			}
		}
		if (t % 20 != 0) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		DiktaturaConfig cfg = DiktaturaConfig.get();
		if (state.weatherTicks < 0) {
			state.weatherTicks = schedule(ow.getRandom(), cfg);
		}
		state.weatherTicks -= 20;
		if (state.weatherTicks <= 0) {
			state.weatherTicks = schedule(ow.getRandom(), cfg);
			Kind[] kinds = Kind.values();
			start(server, kinds[ow.getRandom().nextInt(kinds.length)]);
		}
	}

	private static int schedule(Random rnd, DiktaturaConfig cfg) {
		int min = Math.max(1, cfg.weatherMinMinutes) * 1200;
		int max = Math.max(min, cfg.weatherMaxMinutes * 1200);
		return min + rnd.nextInt(max - min + 1);
	}

	/** Объявить осадки (по плану ведомства или командой). */
	public static void start(MinecraftServer server, Kind kind) {
		active = kind;
		activeUntil = Diktatura.ticks() + (kind == Kind.AID ? 20 * 20 : 20 * 35);
		Hud.alertAll(server, kind == Kind.AID ? AlertKind.GOOD : AlertKind.EVENT,
			Text.translatable("diktatura.weather." + kind.key()), Text.translatable("diktatura.weather." + kind.key() + ".hint"), 8);
		Voice.broadcast(server, Text.translatable("diktatura.weather.speaker"),
			Text.translatable("diktatura.weather." + kind.key() + ".voice"));
	}

	private static void tickActive(ServerWorld ow, long t) {
		int period = switch (active) {
			case COWS -> 8;
			case ANVILS -> 25;
			case AID -> 12;
		};
		if (t % period != 0) {
			return;
		}
		Random rnd = ow.getRandom();
		for (ServerPlayerEntity p : ow.getPlayers()) {
			if (p.isSpectator() || !ow.isSkyVisible(p.getBlockPos())) {
				continue;
			}
			BlockPos sky = skySpot(ow, p.getBlockPos(), active == Kind.ANVILS ? 7 : 12, rnd);
			if (sky == null) {
				continue;
			}
			switch (active) {
				case COWS -> {
					CowEntity cow = EntityType.COW.create(ow);
					if (cow != null) {
						cow.refreshPositionAndAngles(sky.getX() + 0.5, sky.getY(), sky.getZ() + 0.5, rnd.nextFloat() * 360f, 0f);
						// 65% коров — с парашютом (медленное падение). Остальным не повезло.
						if (rnd.nextFloat() < 0.65f) {
							cow.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 20 * 30, 0, false, false));
						}
						ow.spawnEntity(cow);
					}
				}
				case ANVILS -> {
					FallingBlockEntity anvil = FallingBlockEntity.spawnFromBlock(ow, sky, Blocks.ANVIL.getDefaultState());
					anvil.setHurtEntities(2.0f, 40);
					int ground = ow.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sky.getX(), sky.getZ());
					marker(ow, new BlockPos(sky.getX(), ground, sky.getZ()), 50);
				}
				case AID -> {
					ItemStack aid = rnd.nextInt(5) == 0 ? new ItemStack(Items.COOKED_BEEF, 2) : new ItemStack(Items.BREAD, 2);
					ItemEntity item = new ItemEntity(ow, sky.getX() + 0.5, sky.getY(), sky.getZ() + 0.5, aid);
					ow.spawnEntity(item);
				}
			}
		}
	}

	/** Точка в небе над случайным местом рядом с игроком (null — над головой не пусто). */
	private static BlockPos skySpot(ServerWorld w, BlockPos around, int radius, Random rnd) {
		int x = around.getX() + rnd.nextInt(radius * 2 + 1) - radius;
		int z = around.getZ() + rnd.nextInt(radius * 2 + 1) - radius;
		int ground = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
		BlockPos pos = new BlockPos(x, Math.min(w.getTopY() - 2, ground + 20 + rnd.nextInt(8)), z);
		return w.getBlockState(pos).isAir() ? pos : null;
	}

	/** Красная метка на земле: сюда сейчас что-то упадёт. */
	public static void marker(ServerWorld w, BlockPos pos, int ticks) {
		MARKERS.add(new Marker(w, pos, Diktatura.ticks() + ticks));
	}

	private static void tickMarkers(long t) {
		if (MARKERS.isEmpty() || t % 3 != 0) {
			return;
		}
		Iterator<Marker> it = MARKERS.iterator();
		while (it.hasNext()) {
			Marker m = it.next();
			if (t > m.until()) {
				it.remove();
				continue;
			}
			m.world().spawnParticles(RED, m.pos().getX() + 0.5, m.pos().getY() + 0.1, m.pos().getZ() + 0.5, 8, 0.6, 0.02, 0.6, 0);
		}
	}

	public static void clear() {
		MARKERS.clear();
		active = null;
	}
}
