package com.diktatura.client.geo;

import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Голова гуманоида на GeckoLib — за взглядом; угол задаётся от исходного положения кости каждый кадр (не накапливается). */
public final class HeadLook {
	private HeadLook() {
	}

	public static <T extends GeoAnimatable> void apply(GeoModel<T> model, AnimationState<T> state) {
		GeoBone head = model.getAnimationProcessor().getBone("head");
		EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
		if (head == null || data == null) {
			return;
		}
		float yaw = MathHelper.clamp(MathHelper.wrapDegrees(data.netHeadYaw()), -70f, 70f);
		float pitch = MathHelper.clamp(data.headPitch(), -45f, 45f);
		head.setRotY(head.getInitialSnapshot().getRotY() + yaw * MathHelper.RADIANS_PER_DEGREE);
		head.setRotX(head.getInitialSnapshot().getRotX() + pitch * MathHelper.RADIANS_PER_DEGREE);
	}
}
