package com.diktatura.ore;

import com.diktatura.config.DiktaturaConfig;
import com.diktatura.license.Licenses;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.state.DiktaturaState;
import com.diktatura.ui.Hud;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/**
 * Эмиграция руды: при добыче руда может отрастить ножки и убежать «за границу». Алмазы бегут чаще всех.
 * Шахтёрская лицензия вдвое снижает шансы (лицензированную кирку руда уважает).
 */
public final class OreEmigration {
	private OreEmigration() {
	}

	public static void registerEvents() {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
			if (world.isClient || !(player instanceof ServerPlayerEntity sp) || sp.isCreative() || !(world instanceof ServerWorld sw)) {
				return true;
			}
			DiktaturaConfig cfg = DiktaturaConfig.get();
			if (!cfg.runawayOre) {
				return true;
			}
			double chance = chance(state, cfg);
			if (chance <= 0) {
				return true;
			}
			if (Licenses.has(sw.getServer(), DiktaturaState.get(sw.getServer()).player(sp), "diktatura:mining")) {
				chance /= 2;
			}
			if (sw.random.nextDouble() >= chance) {
				return true;
			}
			if (RunawayOreEntity.spawnFrom(sw, pos, state, sp) == null) {
				return true;
			}
			Hud.alert(sp, AlertKind.EVENT, Text.translatable("diktatura.ore.ran"), Text.translatable("diktatura.ore.ran.hint"), 4);
			return false;
		});
	}

	private static double chance(BlockState s, DiktaturaConfig cfg) {
		if (s.isIn(BlockTags.DIAMOND_ORES)) {
			return cfg.runawayDiamondChance;
		}
		if (s.isIn(BlockTags.EMERALD_ORES) || s.isIn(BlockTags.GOLD_ORES)) {
			return cfg.runawayDiamondChance * 0.6;
		}
		if (s.isIn(BlockTags.IRON_ORES) || s.isIn(BlockTags.COAL_ORES) || s.isIn(BlockTags.REDSTONE_ORES)
			|| s.isIn(BlockTags.LAPIS_ORES) || s.isIn(BlockTags.COPPER_ORES)) {
			return cfg.runawayOreChance;
		}
		return 0;
	}
}
