package com.diktatura.item;

import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.ModEntities;
import com.diktatura.ui.Hud;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

/** Летящий тапок: без гравитации, бьёт всё на пути, через ~0,6 с возвращается к хозяину. */
public class SlipperEntity extends ThrownItemEntity {
	private boolean returning;
	private int flight;
	private final Set<Integer> hit = new HashSet<>();

	public SlipperEntity(EntityType<? extends ThrownItemEntity> type, World world) {
		super(type, world);
	}

	public SlipperEntity(World world, LivingEntity owner, ItemStack stack) {
		super(ModEntities.SLIPPER, owner, world);
		setItem(stack.copyWithCount(1));
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.SLIPPER;
	}

	@Override
	protected double getGravity() {
		return 0.0;
	}

	@Override
	public void tick() {
		if (!getWorld().isClient) {
			flight++;
			Entity owner = getOwner();
			if (flight > 12) {
				returning = true;
			}
			if (returning) {
				if (owner == null || !owner.isAlive() || owner.getWorld() != getWorld()) {
					dropAndDiscard();
					return;
				}
				Vec3d to = owner.getEyePos().subtract(getPos());
				if (to.lengthSquared() < 2.0) {
					giveBack(owner);
					return;
				}
				noClip = true;
				setVelocity(to.normalize().multiply(Math.min(1.4, 0.6 + flight * 0.02)));
			}
			if (flight > 140) {
				dropAndDiscard();
				return;
			}
		}
		super.tick();
	}

	private void giveBack(Entity owner) {
		if (owner instanceof PlayerEntity p) {
			ItemStack s = getStack().copy();
			if (!p.getAbilities().creativeMode && !p.getInventory().insertStack(s)) {
				p.dropItem(s, false);
			}
		}
		discard();
	}

	private void dropAndDiscard() {
		dropStack(getStack().copy());
		discard();
	}

	@Override
	protected boolean canHit(Entity entity) {
		return entity != getOwner() && !hit.contains(entity.getId()) && super.canHit(entity);
	}

	@Override
	protected void onEntityHit(EntityHitResult result) {
		Entity target = result.getEntity();
		if (target == getOwner() || !hit.add(target.getId())) {
			return;
		}
		boolean crit = random.nextFloat() < 0.2f;
		target.damage(getDamageSources().thrown(this, getOwner()), crit ? 20f : 7f);
		if (target instanceof LivingEntity le) {
			Vec3d push = getVelocity().normalize().multiply(crit ? 1.6 : 0.7);
			le.addVelocity(push.x, 0.35, push.z);
		}
		getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,
			SoundCategory.PLAYERS, 1.6f, crit ? 0.7f : 1.1f);
		if (getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(crit ? ParticleTypes.CRIT : ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.6), target.getZ(),
				crit ? 20 : 1, 0.3, 0.3, 0.3, 0.2);
		}
		if (crit && getOwner() instanceof ServerPlayerEntity sp) {
			Hud.alert(sp, AlertKind.GOOD, Text.translatable("diktatura.slipper.crit"),
				Text.translatable("diktatura.slipper.crit." + random.nextInt(3)), 3);
		}
		returning = true;
		ItemStack s = getStack().copy();
		if (s.isDamageable()) {
			s.setDamage(s.getDamage() + 1);
			if (s.getDamage() >= s.getMaxDamage()) {
				discard();
				return;
			}
			setItem(s);
		}
	}

	@Override
	protected void onBlockHit(BlockHitResult result) {
		if (!returning) {
			returning = true;
			getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.6f, 1.6f);
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Returning", returning);
		nbt.putInt("Flight", flight);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		returning = nbt.getBoolean("Returning");
		flight = nbt.getInt("Flight");
	}
}
