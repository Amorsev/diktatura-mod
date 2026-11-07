package com.diktatura.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Доступ к загрузке пост-шейдера (картошка и глюки трипа). */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Invoker("loadPostProcessor")
	void diktatura$loadPostProcessor(Identifier id);
}
