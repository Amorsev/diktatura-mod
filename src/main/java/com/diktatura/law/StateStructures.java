package com.diktatura.law;

import com.diktatura.dictator.ThroneHall;
import com.diktatura.prison.PrisonLayout;
import com.diktatura.state.DiktaturaState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Постройки, которые сгенерировал мод: тюрьма, тронный зал, дом госучреждений и госказино вместе с площадками у входа.
 * Мини-игры отработки сюда не входят — там ломать камень положено по сценарию.
 */
public final class StateStructures {
	private StateStructures() {
	}

	public static boolean contains(World world, BlockPos pos) {
		MinecraftServer server = world.getServer();
		if (server == null || world.getRegistryKey() != World.OVERWORLD) {
			return false;
		}
		DiktaturaState state = DiktaturaState.get(server);
		if (state.prisonGenerated && box(state.prisonOrigin, -6, -1, -1,
			PrisonLayout.SIZE_X, PrisonLayout.SIZE_Y, PrisonLayout.SIZE_Z).contains(pos)) {
			return true;
		}
		if (state.officesGenerated && box(state.officesOrigin, -3, -1, -1, com.diktatura.office.OfficeBuilding.SIZE_X,
			com.diktatura.office.OfficeBuilding.SIZE_Y, com.diktatura.office.OfficeBuilding.SIZE_Z).contains(pos)) {
			return true;
		}
		if (state.casinoGenerated && box(state.casinoOrigin, -3, -1, -1, com.diktatura.casino.CasinoBuilding.SIZE_X,
			com.diktatura.casino.CasinoBuilding.SIZE_Y, com.diktatura.casino.CasinoBuilding.SIZE_Z).contains(pos)) {
			return true;
		}
		return state.throneGenerated && box(state.throneOrigin, -5, -1, -1,
			ThroneHall.SIZE_X, ThroneHall.SIZE_Y, ThroneHall.SIZE_Z).contains(pos);
	}

	private static BlockBox box(BlockPos o, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
		return new BlockBox(o.getX() + minX, o.getY() + minY, o.getZ() + minZ,
			o.getX() + sizeX, o.getY() + sizeY, o.getZ() + sizeZ);
	}
}
