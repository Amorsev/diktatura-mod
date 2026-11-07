package com.diktatura.mechanics;

import com.diktatura.config.DiktaturaConfig;
import com.diktatura.finale.BossFx;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

/**
 * Первый вход в Незер или Край: «Загружаем Незер… не выключайте компьютер». Долгая
 * государственная загрузка (8 секунд): полоса ползёт и застревает на 99%, гражданин стоит
 * как вкопанный — зато неуязвим, пока грузится.
 */
public final class FirstVisit {
	private static final int TICKS = 160;

	private FirstVisit() {
	}

	public static void registerEvents() {
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			if (!DiktaturaConfig.get().firstVisitLoading || player.isSpectator()) {
				return;
			}
			int bit = destination.getRegistryKey() == World.NETHER ? 1 : destination.getRegistryKey() == World.END ? 2 : 0;
			if (bit == 0) {
				return;
			}
			DiktaturaState state = DiktaturaState.get(player.getServer());
			PlayerRecord rec = state.player(player);
			if ((rec.visited & bit) != 0) {
				return;
			}
			rec.visited |= bit;
			state.markDirty();
			freeze(player);
			BossFx.fx(player, "loading_long", TICKS);
		});
	}

	private static void freeze(ServerPlayerEntity p) {
		p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, TICKS, 255, false, false));
		p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, TICKS + 20, 4, false, false));
		p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, TICKS + 20, 0, false, false));
		p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, TICKS, 0, false, false));
	}
}
