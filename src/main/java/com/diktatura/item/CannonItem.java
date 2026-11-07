package com.diktatura.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Госпушка «Драконобой»: зажал ПКМ — очередь лучей (почти 7 выстрелов в секунду), дальность
 * 128 блоков, лёгкий автоприцел. Расходует прочность. Идея — из референсного мода
 * (DragonCannonItem), реализация своя.
 */
public class CannonItem extends Item {
	public static final double RANGE = 128;
	private static final int RATE = 3;
	private static final float DAMAGE = 8f;
	private static final DustParticleEffect CORE = new DustParticleEffect(new Vector3f(0.35f, 0.95f, 1.0f), 0.9f);

	public CannonItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		user.setCurrentHand(hand);
		return TypedActionResult.consume(user.getStackInHand(hand));
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.NONE;
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (world instanceof ServerWorld w && user instanceof ServerPlayerEntity p
			&& (getMaxUseTime(stack, user) - remainingUseTicks) % RATE == 0) {
			fire(w, p);
			if (!p.getAbilities().creativeMode) {
				stack.damage(1, p, p.getActiveHand() == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
			}
		}
	}

	private static void fire(ServerWorld w, ServerPlayerEntity p) {
		Vec3d eye = p.getEyePos();
		Vec3d dir = p.getRotationVec(1f).add((w.random.nextDouble() - 0.5) * 0.012,
			(w.random.nextDouble() - 0.5) * 0.012, (w.random.nextDouble() - 0.5) * 0.012).normalize();
		Vec3d far = eye.add(dir.multiply(RANGE));
		HitResult blockHit = w.raycast(new RaycastContext(eye, far, RaycastContext.ShapeType.COLLIDER,
			RaycastContext.FluidHandling.NONE, p));
		Vec3d end = blockHit.getType() == HitResult.Type.MISS ? far : blockHit.getPos();
		double best = eye.distanceTo(end);
		Entity target = null;
		List<Entity> others = w.getOtherEntities(p, new Box(eye, end).expand(2), e -> e instanceof LivingEntity le && le.isAlive());
		for (Entity e : others) {
			Optional<Vec3d> hit = e.getBoundingBox().expand(0.8).raycast(eye, end);
			if (hit.isPresent() && eye.distanceTo(hit.get()) < best) {
				best = eye.distanceTo(hit.get());
				target = e;
			}
		}
		Vec3d impact = eye.add(dir.multiply(best));
		if (target instanceof com.diktatura.finale.DictatorBossEntity boss) {
			// У вождя иммунитет к драконобою: оружие госохраны против своих не стреляет.
			boss.deflectCannon(p, eye.add(dir.multiply(best)));
		} else if (target instanceof LivingEntity le) {
			le.timeUntilRegen = 0;
			le.damage(w.getDamageSources().playerAttack(p), DAMAGE);
			le.takeKnockback(0.4, -dir.x, -dir.z);
		}
		Vec3d right = dir.crossProduct(new Vec3d(0, 1, 0)).normalize();
		Vec3d muzzle = eye.add(dir.multiply(0.9)).add(right.multiply(0.35)).add(0, -0.25, 0);
		double len = muzzle.distanceTo(impact);
		Vec3d step = impact.subtract(muzzle).normalize();
		for (double d = 2.2; d < len; d += 0.55) {
			Vec3d q = muzzle.add(step.multiply(d));
			w.spawnParticles(CORE, q.x, q.y, q.z, 1, 0.01, 0.01, 0.01, 0);
		}
		w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 3, 0.05, 0.05, 0.05, 0.1);
		w.spawnParticles(ParticleTypes.END_ROD, impact.x, impact.y, impact.z, 10, 0.1, 0.1, 0.1, 0.22);
		w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_SHOOT, SoundCategory.PLAYERS, 0.9f,
			1.4f + w.random.nextFloat() * 0.3f);
		w.playSound(null, impact.x, impact.y, impact.z, target != null ? SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST
			: SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 1.2f, 1.3f);
	}
}
