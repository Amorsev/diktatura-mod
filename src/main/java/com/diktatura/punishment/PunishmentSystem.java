package com.diktatura.punishment;

import com.diktatura.config.DiktaturaConfig;
import com.diktatura.law.Law;
import com.diktatura.law.LawContext;
import com.diktatura.loyalty.LoyaltySystem;
import com.diktatura.prison.PrisonSystem;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.state.Stage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * PunishmentSystem: исполняет наказания закона и обновляет страх.
 * Арест передаётся в {@link PrisonSystem}, а та — в TransportSystem.
 */
public final class PunishmentSystem {
	private PunishmentSystem() {
	}

	public static void punish(ServerPlayerEntity player, Law law, LawContext ctx) {
		var server = player.getServer();
		if (server == null) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		PlayerRecord rec = state.player(player);
		if (rec.stage != Stage.FREE) {
			return;
		}
		rec.violations++;
		rec.cleanTicks = 0;

		player.sendMessage(Text.translatable("diktatura.violation", law.name()).formatted(Formatting.RED, Formatting.BOLD), false);
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0f, 0.7f);

		int fear = 2;
		boolean arrested = false;
		for (Punishment p : law.punishments()) {
			p.apply(player, law, ctx);
			fear += p.fear();
			arrested |= p.isArrest();
		}

		// Враг государства: при нулевой лояльности любое нарушение ведёт в тюрьму.
		if (!arrested && rec.loyalty <= 0 && rec.stage == Stage.FREE) {
			PrisonSystem.arrest(player, DiktaturaConfig.get().defaultArrestMinutes * 1200,
				Text.translatable("diktatura.enemy_of_state"));
		}
		LoyaltySystem.addFear(player, fear);
		state.markDirty();
	}
}
