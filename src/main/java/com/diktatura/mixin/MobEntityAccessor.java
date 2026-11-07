package com.diktatura.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Доступ к целям моба: стройбату добавляется своя цель «строить». */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {
	@Accessor("goalSelector")
	GoalSelector diktatura$getGoalSelector();
}
