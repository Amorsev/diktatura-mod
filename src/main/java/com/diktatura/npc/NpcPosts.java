package com.diktatura.npc;

import com.diktatura.state.DiktaturaState;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.UUID;

/**
 * Постоянные посты НПС-ведомств: на каждом посту ровно один НПС, он восстанавливается,
 * если пропал, и возвращается на место, если его сдвинули. UUID хранятся в мировом состоянии.
 */
public final class NpcPosts {
	private NpcPosts() {
	}

	/** Обеспечить НПС {@code npcId} на посту {@code key} (в верхнем мире). Возвращает его или null. */
	public static NpcEntity ensure(MinecraftServer server, String key, String npcId, BlockPos post, float yaw) {
		if (post == null || NpcRegistry.get(npcId) == null) {
			return null;
		}
		DiktaturaState state = DiktaturaState.get(server);
		ServerWorld world = server.getOverworld();
		if (!world.isChunkLoaded(post.getX() >> 4, post.getZ() >> 4)) {
			return null;
		}
		UUID id = state.npcPosts.get(key);
		Entity found = id == null ? null : world.getEntity(id);
		NpcEntity npc = found instanceof NpcEntity n && !n.isRemoved() ? n : null;
		if (npc == null) {
			List<NpcEntity> near = world.getEntitiesByClass(NpcEntity.class, new Box(post).expand(16),
				e -> npcId.equals(e.npcId()));
			if (!near.isEmpty()) {
				npc = near.get(0);
				for (int i = 1; i < near.size(); i++) {
					near.get(i).discard();
				}
			} else {
				npc = ModEntities.NPC.create(world);
				if (npc == null) {
					return null;
				}
				npc.setNpcId(npcId);
				npc.refreshPositionAndAngles(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, yaw, 0.0f);
				npc.setHeadYaw(yaw);
				npc.setBodyYaw(yaw);
				NpcRegistry.apply(npc);
				world.spawnEntity(npc);
			}
			state.npcPosts.put(key, npc.getUuid());
			state.markDirty();
		}
		if (npc.squaredDistanceTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5) > 2.0) {
			npc.refreshPositionAndAngles(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, yaw, 0.0f);
		}
		return npc;
	}
}
