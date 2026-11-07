package com.diktatura.casino;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Декорации игр казино: колесо рулетки, барабаны автомата, кости и табличка с результатом —
 * display-сущности, настраиваемые через NBT. Живут только пока идёт игра; остатки после
 * перезапуска сервера удаляются при загрузке.
 */
public final class CasinoFx {
	private static final String TAG = "diktatura_casino_fx";
	private static final Set<UUID> LIVE = new HashSet<>();

	private CasinoFx() {
	}

	public static void registerEvents() {
		ServerEntityEvents.ENTITY_LOAD.register((e, w) -> {
			if (e instanceof DisplayEntity && e.getCommandTags().contains(TAG) && !LIVE.contains(e.getUuid())) {
				e.discard();
			}
		});
	}

	private static NbtElement encode(AffineTransformation t) {
		// Трансформация из матрицы раскладывается лениво: без этого вызова кодек получает
		// пустые translation/rotation и падает с NullPointerException (вылет у строителя).
		t.getTranslation();
		return AffineTransformation.ANY_CODEC.encodeStart(NbtOps.INSTANCE, t).result().orElse(new NbtCompound());
	}

	/** Трансформация: поворот вокруг вертикали, сдвиг по радиусу, масштаб; блок центрирован по X/Z. */
	public static AffineTransformation ring(float angleRad, float radius, float lift, float sx, float sy, float sz) {
		Matrix4f m = new Matrix4f().rotateY(angleRad).translate(radius, lift, 0).scale(sx, sy, sz).translate(-0.5f, 0f, -0.5f);
		return new AffineTransformation(m);
	}

	public static AffineTransformation matrix(Matrix4f m) {
		return new AffineTransformation(m);
	}

	private static <T extends DisplayEntity> T spawn(ServerWorld w, T e, Vec3d pos, NbtCompound extra) {
		e.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
		NbtCompound n = e.writeNbt(new NbtCompound());
		n.copyFrom(extra);
		n.putFloat("view_range", 1.5f);
		e.readNbt(n);
		e.addCommandTag(TAG);
		LIVE.add(e.getUuid());
		w.spawnEntity(e);
		return e;
	}

	public static DisplayEntity block(ServerWorld w, Vec3d pos, BlockState state, AffineTransformation t) {
		DisplayEntity.BlockDisplayEntity e = EntityType.BLOCK_DISPLAY.create(w);
		if (e == null) {
			return null;
		}
		NbtCompound x = new NbtCompound();
		x.put("block_state", NbtHelper.fromBlockState(state));
		x.put("transformation", encode(t));
		return spawn(w, e, pos, x);
	}

	public static DisplayEntity item(ServerWorld w, Vec3d pos, ItemStack stack, float scale) {
		DisplayEntity.ItemDisplayEntity e = EntityType.ITEM_DISPLAY.create(w);
		if (e == null) {
			return null;
		}
		NbtCompound x = new NbtCompound();
		x.put("item", stack.encode(w.getRegistryManager()));
		x.putString("billboard", "center");
		x.put("transformation", encode(new AffineTransformation(new Matrix4f().scale(scale))));
		return spawn(w, e, pos, x);
	}

	public static void setItem(ServerWorld w, DisplayEntity e, ItemStack stack) {
		if (e == null || e.isRemoved()) {
			return;
		}
		NbtCompound n = e.writeNbt(new NbtCompound());
		n.put("item", stack.encode(w.getRegistryManager()));
		e.readNbt(n);
	}

	public static DisplayEntity text(ServerWorld w, Vec3d pos, Text text) {
		DisplayEntity.TextDisplayEntity e = EntityType.TEXT_DISPLAY.create(w);
		if (e == null) {
			return null;
		}
		NbtCompound x = new NbtCompound();
		x.putString("text", Text.Serialization.toJsonString(text, w.getRegistryManager()));
		x.putString("billboard", "center");
		x.putInt("background", 0xB0000000);
		x.putInt("line_width", 220);
		x.put("transformation", encode(new AffineTransformation(new Matrix4f().scale(0.9f))));
		return spawn(w, e, pos, x);
	}

	public static void animate(DisplayEntity e, AffineTransformation t, int ticks) {
		if (e == null || e.isRemoved()) {
			return;
		}
		NbtCompound n = e.writeNbt(new NbtCompound());
		n.put("transformation", encode(t));
		n.putInt("interpolation_duration", ticks);
		n.putInt("start_interpolation", 0);
		e.readNbt(n);
	}

	public static void remove(DisplayEntity e) {
		if (e == null) {
			return;
		}
		LIVE.remove(e.getUuid());
		e.discard();
	}
}
