package com.diktatura.loan;

import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.ui.Hud;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;

/**
 * Прописка должника и зона государственного кредита.
 *
 * <p>Прописка — табличка «Адрес должника» ({@link AddressPlateBlock}), повешенная в верхнем мире рядом с
 * кроватью (не дальше {@link #BED_RADIUS} блоков). У игрока один адрес: новая табличка снимает старую.
 * Без прописки займ не выдают, а должник без прописки нарушает закон «Обязательная прописка».
 * По адресу будут приходить коллекторы.
 *
 * <p>Зона кредита — круг радиусом {@link LoanSettings#zoneRadius()} вокруг столицы (центра тронного зала)
 * в верхнем мире. Проценты начисляются только в ней; за её пределами долг заморожен.
 */
public final class Registration {
	public static final int BED_RADIUS = 6;

	private Registration() {
	}

	public static boolean hasAddress(PlayerRecord rec) {
		return !rec.addrDim.isEmpty();
	}

	public static BlockPos address(PlayerRecord rec) {
		return hasAddress(rec) ? BlockPos.fromLong(rec.addrPos) : null;
	}

	/** Должник без прописки (проверка закона «Обязательная прописка»). */
	public static boolean unregisteredDebtor(ServerPlayerEntity p) {
		PlayerRecord rec = DiktaturaState.get(p.getServer()).player(p);
		return Treasury.hasLoan(rec) && !hasAddress(rec);
	}

	/** Игрок в зоне государственного кредита (там идут проценты). */
	public static boolean inZone(ServerPlayerEntity p) {
		if (p.getWorld().getRegistryKey() != World.OVERWORLD) {
			return false;
		}
		DiktaturaState state = DiktaturaState.get(p.getServer());
		if (!state.throneGenerated) {
			return true;
		}
		BlockPos c = TreasuryBuilding.capitalCenter(state);
		double dx = p.getX() - c.getX();
		double dz = p.getZ() - c.getZ();
		double r = LoanSettings.get().zoneRadius();
		return dx * dx + dz * dz <= r * r;
	}

	private static boolean bedNear(World world, BlockPos pos) {
		for (BlockPos b : BlockPos.iterate(pos.add(-BED_RADIUS, -2, -BED_RADIUS), pos.add(BED_RADIUS, 2, BED_RADIUS))) {
			if (world.getBlockState(b).isIn(BlockTags.BEDS)) {
				return true;
			}
		}
		return false;
	}

	/** Табличка повешена. Если не у кровати или не в верхнем мире — снимается обратно (выпадает). */
	static void onPlaced(World world, BlockPos pos, LivingEntity placer) {
		if (world.isClient || !(placer instanceof ServerPlayerEntity p)) {
			return;
		}
		if (world.getRegistryKey() != World.OVERWORLD || !bedNear(world, pos)) {
			world.breakBlock(pos, !p.isCreative());
			Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.address.refused"),
				Text.translatable(world.getRegistryKey() != World.OVERWORLD ? "diktatura.address.refused.dim"
					: "diktatura.address.refused.bed", BED_RADIUS), 6);
			return;
		}
		DiktaturaState state = DiktaturaState.get(p.getServer());
		PlayerRecord rec = state.player(p);
		BlockPos old = address(rec);
		rec.addrDim = World.OVERWORLD.getValue().toString();
		rec.addrPos = pos.asLong();
		state.markDirty();
		// Переезд: старая табличка снимается (адрес уже новый, поэтому снятие не считается выпиской).
		if (old != null && !old.equals(pos) && world.isChunkLoaded(old) && world.getBlockState(old).isOf(LoanBlocks.ADDRESS_PLATE)) {
			world.breakBlock(old, false);
		}
		Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.address.registered"),
			Text.translatable("diktatura.address.registered.hint", pos.getX(), pos.getY(), pos.getZ()), 6);
	}

	/** Табличку сняли (сломали, взорвали, отвалилась стена): владелец выписан. */
	static void onRemoved(World world, BlockPos pos) {
		MinecraftServer server = world.getServer();
		if (world.isClient || server == null || world.getRegistryKey() != World.OVERWORLD) {
			return;
		}
		DiktaturaState state = DiktaturaState.get(server);
		long at = pos.asLong();
		for (Map.Entry<UUID, PlayerRecord> e : state.players.entrySet()) {
			PlayerRecord rec = e.getValue();
			if (hasAddress(rec) && rec.addrPos == at) {
				rec.addrDim = "";
				rec.addrPos = 0;
				state.markDirty();
				ServerPlayerEntity owner = server.getPlayerManager().getPlayer(e.getKey());
				if (owner != null) {
					Hud.alert(owner, Treasury.hasLoan(rec) ? AlertKind.DANGER : AlertKind.INFO,
						Text.translatable("diktatura.address.lost"),
						Text.translatable(Treasury.hasLoan(rec) ? "diktatura.address.lost.debtor" : "diktatura.address.lost.hint"), 8);
				}
			}
		}
	}

	/** Снять прописку командой оператора. */
	public static void clear(ServerPlayerEntity p) {
		DiktaturaState state = DiktaturaState.get(p.getServer());
		PlayerRecord rec = state.player(p);
		rec.addrDim = "";
		rec.addrPos = 0;
		state.markDirty();
	}
}
