package com.diktatura.job;

import com.diktatura.casino.CasinoFx;
import com.diktatura.finale.Tasks;
import com.diktatura.job.JobRegistry.Blueprint;
import com.diktatura.job.JobRegistry.JobDef;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.network.UiPayloads.HeadlineStyle;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.ui.Hud;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Королевская стройка — работа строителя. Игрока переносят на парящую стройплощадку, выдают
 * «универсальные супер модульные королевские блоки» и показывают голограмму постройки: блоки
 * ставятся только в отмеченные точки. Когда всё готово, постройка взлетает и исчезает в небе
 * («будет продана как труд Его Величества»), игроку платят и возвращают туда, откуда забрали.
 * На стройке законы не действуют: здесь трудится сам Его Величество.
 */
public final class RoyalSite {
	public static final int SIZE = 15;
	private static final int Y = 250;
	private static final int WALL_H = 14;
	private static final int SPARE = 3;
	/** Голограммы ещё не поставленных блоков: игрок → (точка → призрак). */
	private static final Map<UUID, Map<BlockPos, DisplayEntity>> GHOSTS = new HashMap<>();

	private RoyalSite() {
	}

	// ------------------------------------------------------------------ состояние в NBT работы

	public static boolean onSite(PlayerRecord rec) {
		return rec.job != null && rec.job.contains("sx");
	}

	private static BlockPos siteOrigin(NbtCompound job) {
		return new BlockPos(job.getInt("sx"), job.getInt("sy"), job.getInt("sz"));
	}

	private static BlockPos anchor(NbtCompound job) {
		return new BlockPos(job.getInt("ax"), job.getInt("ay"), job.getInt("az"));
	}

	private static List<BlockPos> targets(NbtCompound job, Blueprint bp) {
		BlockPos a = anchor(job);
		List<BlockPos> out = new ArrayList<>(bp.size());
		for (BlockPos o : bp.offsets()) {
			out.add(a.add(o));
		}
		return out;
	}

	private static BlockPos originFor(MinecraftServer server, UUID player) {
		BlockPos spawn = server.getOverworld().getSpawnPos();
		int slot = Math.floorMod(player.hashCode(), 400);
		return new BlockPos(spawn.getX() + 6000 + slot * 40, Y, spawn.getZ() + 6000);
	}

	// ------------------------------------------------------------------ начало

	/** Принять работу строителя: площадка, блоки, голограмма, перенос. Возвращает текст задания. */
	static MutableText start(ServerPlayerEntity p, JobDef def, int variant, NbtCompound job) {
		MinecraftServer server = p.getServer();
		ServerWorld ow = server.getOverworld();
		Blueprint bp = def.blueprint(variant);
		BlockPos o = originFor(server, p.getUuid());
		for (int cx = (o.getX() - 2) >> 4; cx <= (o.getX() + SIZE + 2) >> 4; cx++) {
			for (int cz = (o.getZ() - 2) >> 4; cz <= (o.getZ() + SIZE + 2) >> 4; cz++) {
				ow.getChunk(cx, cz);
			}
		}
		buildPlatform(ow, o);
		clearSite(ow, o);
		int ax = o.getX() + (SIZE - bp.width()) / 2;
		int az = o.getZ() + (SIZE - bp.depth()) / 2;
		job.putInt("sx", o.getX());
		job.putInt("sy", o.getY());
		job.putInt("sz", o.getZ());
		job.putInt("ax", ax);
		job.putInt("ay", o.getY() + 1);
		job.putInt("az", az);
		// Откуда забрали — туда и вернём.
		job.putString("rdim", p.getServerWorld().getRegistryKey().getValue().toString());
		job.putDouble("rx", p.getX());
		job.putDouble("ry", p.getY());
		job.putDouble("rz", p.getZ());
		job.putFloat("ryaw", p.getYaw());
		job.putFloat("rpitch", p.getPitch());

		int count = bp.size() + SPARE;
		while (count > 0) {
			int n = Math.min(64, count);
			p.getInventory().offerOrDrop(new ItemStack(RoyalBlocks.ITEM, n));
			count -= n;
		}
		p.teleport(ow, o.getX() + SIZE / 2.0, o.getY() + 1, o.getZ() + 1.5, 0f, 20f);
		ow.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1.2f);
		Hud.headline(p, HeadlineStyle.DECREE, Text.translatable("diktatura.royal.title"),
			Text.translatable("diktatura.royal.sub", Text.translatable(bp.label())), 5);
		return Text.translatable("diktatura.royal.start", Text.translatable(bp.label()), bp.size(), def.minutes());
	}

	// ------------------------------------------------------------------ каждую секунду

	static void tick(ServerPlayerEntity p, PlayerRecord rec, JobDef def) {
		NbtCompound job = rec.job;
		if (!job.contains("sx")) {
			// Работа взята в старой версии (стройка «перед собой») — отменяем без штрафа.
			p.sendMessage(Text.translatable("diktatura.royal.legacy").formatted(Formatting.GRAY), false);
			Jobs.clear(p, rec);
			return;
		}
		if (job.getBoolean("done")) {
			return;
		}
		ServerWorld ow = p.getServer().getOverworld();
		BlockPos o = siteOrigin(job);
		Vec3d center = new Vec3d(o.getX() + SIZE / 2.0, o.getY() + 1, o.getZ() + SIZE / 2.0);
		if (p.getServerWorld() != ow || p.getPos().squaredDistanceTo(center) > 30 * 30) {
			// Со стройки не уходят: возвращаем на площадку.
			p.teleport(ow, o.getX() + SIZE / 2.0, o.getY() + 1, o.getZ() + 1.5, 0f, 20f);
		}
		Blueprint bp = def.blueprint(job.getInt("v"));
		List<BlockPos> missing = new ArrayList<>();
		int placed = 0;
		for (BlockPos t : targets(job, bp)) {
			if (ow.getBlockState(t).isOf(RoyalBlocks.BLOCK)) {
				placed++;
			} else {
				missing.add(t);
			}
		}
		job.putInt("p", placed);
		ensureGhosts(p, ow, missing);
		for (int i = 0; i < Math.min(40, missing.size()); i++) {
			BlockPos m = missing.get(i);
			ow.spawnParticles(p, ParticleTypes.HAPPY_VILLAGER, true, m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5, 1, 0.15, 0.15, 0.15, 0);
		}
		if (missing.isEmpty()) {
			finish(p, rec, def);
		}
	}

	private static void ensureGhosts(ServerPlayerEntity p, ServerWorld w, List<BlockPos> missing) {
		Map<BlockPos, DisplayEntity> map = GHOSTS.computeIfAbsent(p.getUuid(), k -> new HashMap<>());
		for (BlockPos m : missing) {
			DisplayEntity g = map.get(m);
			if (g == null || g.isRemoved()) {
				Matrix4f t = new Matrix4f().translate(0.5f, 0.15f, 0.5f).scale(0.7f).translate(-0.5f, 0f, -0.5f);
				map.put(m.toImmutable(), CasinoFx.block(w, Vec3d.of(m), Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState(), CasinoFx.matrix(t)));
			}
		}
	}

	private static void removeGhost(UUID player, BlockPos pos) {
		Map<BlockPos, DisplayEntity> map = GHOSTS.get(player);
		if (map != null) {
			CasinoFx.remove(map.remove(pos));
		}
	}

	private static void removeGhosts(UUID player) {
		Map<BlockPos, DisplayEntity> map = GHOSTS.remove(player);
		if (map != null) {
			map.values().forEach(CasinoFx::remove);
		}
	}

	// ------------------------------------------------------------------ постановка блока

	/** Можно ли поставить королевский блок сюда (это ещё не заполненная точка чертежа). */
	public static boolean isTarget(ServerPlayerEntity p, BlockPos pos) {
		PlayerRecord rec = DiktaturaState.get(p.getServer()).player(p);
		JobDef def = Jobs.def(rec);
		if (def == null || def.type() != JobRegistry.Type.BUILDER || !onSite(rec) || rec.job.getBoolean("done")
			|| p.getServerWorld() != p.getServer().getOverworld()) {
			return false;
		}
		return targets(rec.job, def.blueprint(rec.job.getInt("v"))).contains(pos)
			&& !p.getServerWorld().getBlockState(pos).isOf(RoyalBlocks.BLOCK);
	}

	public static void onPlaced(ServerPlayerEntity p, BlockPos pos) {
		removeGhost(p.getUuid(), pos.toImmutable());
		ServerWorld w = p.getServerWorld();
		w.spawnParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
		PlayerRecord rec = DiktaturaState.get(p.getServer()).player(p);
		JobDef def = Jobs.def(rec);
		if (def == null || rec.job == null) {
			return;
		}
		Blueprint bp = def.blueprint(rec.job.getInt("v"));
		int placed = 0;
		for (BlockPos t : targets(rec.job, bp)) {
			if (w.getBlockState(t).isOf(RoyalBlocks.BLOCK)) {
				placed++;
			}
		}
		rec.job.putInt("p", placed);
		p.sendMessage(Text.translatable("diktatura.job.progress", Jobs.title(def, rec.job.getInt("v")), placed, bp.size())
			.formatted(Formatting.GOLD), true);
		if (placed >= bp.size()) {
			finish(p, rec, def);
		}
	}

	// ------------------------------------------------------------------ сдача: постройка улетает

	private static void finish(ServerPlayerEntity p, PlayerRecord rec, JobDef def) {
		if (rec.job.getBoolean("done")) {
			return;
		}
		rec.job.putBoolean("done", true);
		removeGhosts(p.getUuid());
		ServerWorld w = p.getServer().getOverworld();
		Blueprint bp = def.blueprint(rec.job.getInt("v"));
		List<DisplayEntity> flying = new ArrayList<>();
		BlockState royal = RoyalBlocks.BLOCK.getDefaultState();
		int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
		for (BlockPos t : targets(rec.job, bp)) {
			w.setBlockState(t, Blocks.AIR.getDefaultState(), flags);
			flying.add(CasinoFx.block(w, Vec3d.of(t), royal, CasinoFx.matrix(new Matrix4f())));
		}
		BlockPos a = anchor(rec.job);
		Vec3d base = new Vec3d(a.getX() + bp.width() / 2.0, a.getY(), a.getZ() + bp.depth() / 2.0);
		w.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.5f, 1.2f);
		Hud.headline(p, HeadlineStyle.PROCLAMATION, Text.translatable("diktatura.royal.done"),
			Text.translatable("diktatura.royal.done.sub"), 7);
		// Сначала постройка дрожит и приподнимается, потом взмывает в небо и пропадает.
		Tasks.schedule(w.getServer(), 2, () -> {
			for (DisplayEntity e : flying) {
				CasinoFx.animate(e, CasinoFx.matrix(new Matrix4f().translate(0, 0.6f, 0)), 18);
			}
		});
		for (int t = 0; t < 20; t += 4) {
			Tasks.schedule(w.getServer(), t, () -> w.spawnParticles(ParticleTypes.CLOUD, base.x, base.y + 0.2, base.z, 12,
				bp.width() / 2.0, 0.1, bp.depth() / 2.0, 0.02));
		}
		Tasks.schedule(w.getServer(), 20, () -> {
			w.playSound(null, base.x, base.y, base.z, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.BLOCKS, 2f, 0.6f);
			w.playSound(null, base.x, base.y, base.z, SoundEvents.ITEM_TOTEM_USE, SoundCategory.BLOCKS, 0.6f, 1.4f);
			for (DisplayEntity e : flying) {
				CasinoFx.animate(e, CasinoFx.matrix(new Matrix4f().translate(0, 80f, 0)), 50);
			}
		});
		for (int t = 20; t < 60; t += 3) {
			int tt = t;
			Tasks.schedule(w.getServer(), t, () -> {
				double y = base.y + 0.6 + (tt - 20) * 1.6;
				w.spawnParticles(ParticleTypes.FIREWORK, base.x, y, base.z, 10, bp.width() / 2.0, 0.6, bp.depth() / 2.0, 0.05);
				w.spawnParticles(ParticleTypes.LARGE_SMOKE, base.x, y - 1.5, base.z, 6, 0.6, 0.3, 0.6, 0.02);
			});
		}
		Tasks.schedule(w.getServer(), 72, () -> flying.forEach(CasinoFx::remove));
		UUID id = p.getUuid();
		Tasks.schedule(w.getServer(), 90, () -> {
			ServerPlayerEntity pl = w.getServer().getPlayerManager().getPlayer(id);
			if (pl != null) {
				Jobs.finishBuilder(pl);
			}
		});
	}

	// ------------------------------------------------------------------ уборка и возврат

	/** Конец работы (сдана, сорвана, отменена): голограммы, остатки блоков, площадка, возврат игрока. */
	static void cleanup(ServerPlayerEntity p, PlayerRecord rec) {
		if (!onSite(rec)) {
			return;
		}
		NbtCompound job = rec.job;
		removeGhosts(p.getUuid());
		PlayerInventory inv = p.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			if (inv.getStack(i).isOf(RoyalBlocks.ITEM)) {
				inv.setStack(i, ItemStack.EMPTY);
			}
		}
		MinecraftServer server = p.getServer();
		ServerWorld ow = server.getOverworld();
		BlockPos o = siteOrigin(job);
		clearSite(ow, o);
		ServerWorld back = ow;
		Identifier dimId = Identifier.tryParse(job.getString("rdim"));
		if (dimId != null) {
			ServerWorld w = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimId));
			if (w != null) {
				back = w;
			}
		}
		Vec3d center = new Vec3d(o.getX() + SIZE / 2.0, o.getY() + 1, o.getZ() + SIZE / 2.0);
		if (p.getServerWorld() == ow && p.getPos().squaredDistanceTo(center) < 40 * 40) {
			p.teleport(back, job.getDouble("rx"), job.getDouble("ry"), job.getDouble("rz"), job.getFloat("ryaw"), job.getFloat("rpitch"));
			back.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
		}
		for (String k : new String[] {"sx", "sy", "sz", "ax", "ay", "az", "rdim", "rx", "ry", "rz", "ryaw", "rpitch", "done"}) {
			job.remove(k);
		}
	}

	/** Убрать со стройплощадки всё лишнее (включая блоки, поставленные игроком). */
	private static void clearSite(ServerWorld w, BlockPos o) {
		int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
		for (int x = 1; x < SIZE - 1; x++) {
			for (int z = 1; z < SIZE - 1; z++) {
				for (int y = 1; y <= WALL_H; y++) {
					BlockPos p = o.add(x, y, z);
					boolean post = (x == 1 || x == SIZE - 2) && (z == 1 || z == SIZE - 2) && y <= 2;
					if (!post && !w.getBlockState(p).isAir()) {
						w.setBlockState(p, Blocks.AIR.getDefaultState(), flags);
					}
				}
			}
		}
	}

	/** Парящая площадка: пол в клетку с золотой каймой, невидимые стены, фонари по углам. */
	private static void buildPlatform(ServerWorld w, BlockPos o) {
		int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
		BlockState barrier = Blocks.BARRIER.getDefaultState();
		for (int x = -1; x <= SIZE; x++) {
			for (int z = -1; z <= SIZE; z++) {
				boolean ring = x == -1 || z == -1 || x == SIZE || z == SIZE;
				if (ring) {
					for (int y = 0; y <= WALL_H; y++) {
						w.setBlockState(o.add(x, y, z), barrier, flags);
					}
					continue;
				}
				boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
				BlockState floor = edge ? Blocks.GOLD_BLOCK.getDefaultState()
					: ((x + z) % 2 == 0 ? Blocks.SMOOTH_QUARTZ : Blocks.WHITE_CONCRETE).getDefaultState();
				w.setBlockState(o.add(x, 0, z), floor, flags);
				w.setBlockState(o.add(x, -1, z), Blocks.STONE_BRICKS.getDefaultState(), flags);
				w.setBlockState(o.add(x, WALL_H + 1, z), barrier, flags);
			}
		}
		for (int[] c : new int[][] {{1, 1}, {SIZE - 2, 1}, {1, SIZE - 2}, {SIZE - 2, SIZE - 2}}) {
			w.setBlockState(o.add(c[0], 1, c[1]), Blocks.GOLD_BLOCK.getDefaultState(), flags);
			w.setBlockState(o.add(c[0], 2, c[1]), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), flags);
		}
	}

	/** Сервер остановился посреди показа: голограммы удаляются сами (CasinoFx). */
	public static void clearRuntime() {
		GHOSTS.clear();
	}

	static boolean inOverworld(ServerPlayerEntity p) {
		return p.getServerWorld().getRegistryKey() == World.OVERWORLD;
	}
}
