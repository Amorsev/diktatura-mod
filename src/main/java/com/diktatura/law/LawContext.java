package com.diktatura.law;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/** Данные события, по которым проверяется закон. Заполняются только нужные поля. */
public final class LawContext {
	public final ServerPlayerEntity player;
	public final LawEvent event;
	public BlockPos pos;
	public BlockState state;
	public Entity target;
	public ItemStack stack = ItemStack.EMPTY;
	public String message = "";
	/** Игрок только что подобрал предмет (для законов о богатстве). */
	public boolean pickup;
	/** Плановая периодическая проверка (а не только подбор предмета). */
	public boolean regular;

	public LawContext(ServerPlayerEntity player, LawEvent event) {
		this.player = player;
		this.event = event;
	}
}
