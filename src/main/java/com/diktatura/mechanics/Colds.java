package com.diktatura.mechanics;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.law.LawContext;
import com.diktatura.law.LawEngine;
import com.diktatura.law.LawEvent;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.ui.Hud;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/**
 * Простуда (Санитарная служба): минута под дождём, в воде или в рыхлом снегу — и гражданин
 * простужен. Пока болеет — чихает (звук, брызги, отдача) и иногда вял. Каждый чих — событие
 * для законов: «не чихать в общественных местах». Лечит казённый чай.
 * Идея — из референсного мода (ColdSystem), реализация своя.
 */
public final class Colds {
	private Colds() {
	}

	public static boolean isSick(PlayerRecord rec) {
		return rec.coldTicks > 0;
	}

	public static void tick(MinecraftServer server) {
		if (Diktatura.ticks() % 20 != 0) {
			return;
		}
		DiktaturaConfig cfg = DiktaturaConfig.get();
		if (cfg.coldAfterSeconds <= 0) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (p.isSpectator() || p.isCreative()) {
				continue;
			}
			PlayerRecord rec = state.player(p);
			boolean wet = p.isTouchingWaterOrRain() || p.inPowderSnow;
			if (rec.coldTicks > 0) {
				rec.coldTicks -= 20;
				if (rec.coldTicks <= 0) {
					rec.coldTicks = 0;
					rec.chill = 0;
					Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.cold.over"), Text.empty(), 4);
				} else if (p.getRandom().nextInt(12) == 0) {
					sneeze(p);
				}
				continue;
			}
			rec.chill = wet ? rec.chill + 20 : Math.max(0, rec.chill - 10);
			if (rec.chill >= cfg.coldAfterSeconds * 20) {
				rec.coldTicks = Math.max(1, cfg.coldMinutes) * 1200;
				rec.chill = 0;
				state.markDirty();
				Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.cold.start"),
					Text.translatable("diktatura.cold.start.hint"), 6);
			}
		}
	}

	/** Чихнуть: звук, брызги, лёгкая отдача, иногда вялость; закон может счесть это нарушением. */
	public static void sneeze(ServerPlayerEntity p) {
		var w = p.getServerWorld();
		w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PANDA_SNEEZE, SoundCategory.PLAYERS, 1.2f,
			0.8f + p.getRandom().nextFloat() * 0.3f);
		Vec3d look = p.getRotationVec(1f);
		w.spawnParticles(ParticleTypes.SNEEZE, p.getX() + look.x * 0.6, p.getEyeY() - 0.1, p.getZ() + look.z * 0.6,
			12, 0.15, 0.1, 0.15, 0.04);
		p.takeKnockback(0.25, look.x, look.z);
		p.velocityModified = true;
		if (p.getRandom().nextInt(3) == 0) {
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 8, 0, false, true));
		}
		p.sendMessage(Text.translatable("diktatura.cold.sneeze").formatted(Formatting.GRAY), true);
		LawEngine.fire(new LawContext(p, LawEvent.SNEEZE));
	}

	/** Вылечить (казённый чай). Возвращает true, если было что лечить. */
	public static boolean cure(ServerPlayerEntity p) {
		DiktaturaState state = DiktaturaState.get(p.getServer());
		PlayerRecord rec = state.player(p);
		boolean was = rec.coldTicks > 0 || rec.chill > 0;
		rec.coldTicks = 0;
		rec.chill = 0;
		state.markDirty();
		return was;
	}

	/** Простудить (команда оператора). */
	public static void infect(ServerPlayerEntity p) {
		DiktaturaState state = DiktaturaState.get(p.getServer());
		state.player(p).coldTicks = Math.max(1, DiktaturaConfig.get().coldMinutes) * 1200;
		state.markDirty();
		Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.cold.start"), Text.translatable("diktatura.cold.start.hint"), 6);
	}
}
