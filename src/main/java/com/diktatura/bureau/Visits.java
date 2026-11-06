package com.diktatura.bureau;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import com.diktatura.economy.Economy;
import com.diktatura.item.ModItems;
import com.diktatura.law.LawEngine;
import com.diktatura.loyalty.LoyaltySystem;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.npc.ModEntities;
import com.diktatura.npc.NpcEntity;
import com.diktatura.npc.NpcRegistry;
import com.diktatura.prison.PrisonSystem;
import com.diktatura.state.DiktaturaState;
import com.diktatura.state.PlayerRecord;
import com.diktatura.transport.TransportSystem;
import com.diktatura.ui.Hud;
import com.diktatura.ui.Voice;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Ведомства, которые приходят к игроку сами: налоговик (13% трудодней) и курьер госпочты
 * (посылка-сюрприз). Визитёр появляется в нескольких блоках, подходит, делает своё дело
 * и уходит. Таймер у каждого игрока свой и идёт, только пока игрок в сети.
 */
public final class Visits {
	public static final String TAX_ID = "diktatura:tax_inspector";
	public static final String COURIER_ID = "diktatura:courier";
	/** Налог — 13% трудодней (с округлением вверх). */
	public static final int TAX_PERCENT = 13;

	public enum Kind { TAX, COURIER }

	private enum Stage { APPROACH, DONE }

	private static final class Visit {
		final UUID player;
		final Kind kind;
		Stage stage = Stage.APPROACH;
		int ticks;

		Visit(UUID player, Kind kind) {
			this.player = player;
			this.kind = kind;
		}
	}

	/** Активные визиты: UUID визитёра → визит. В мире не сохраняются: после перезапуска визитёры уходят. */
	private static final Map<UUID, Visit> ACTIVE = new HashMap<>();
	/** Кто уже ударил налоговика (приставы вызываются один раз за визит). */
	private static final Map<UUID, Long> LAST_ASSAULT = new HashMap<>();

	private Visits() {
	}

	public static void registerEvents() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof NpcEntity npc && (TAX_ID.equals(npc.npcId()) || COURIER_ID.equals(npc.npcId()))) {
				LawEngine.defer(() -> {
					if (!npc.isRemoved() && !ACTIVE.containsKey(npc.getUuid())) {
						npc.discard();
					}
				});
			}
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(entity instanceof NpcEntity npc) || !ACTIVE.containsKey(npc.getUuid())) {
				return ActionResult.PASS;
			}
			if (!world.isClient && player instanceof ServerPlayerEntity sp) {
				onAttack(sp, npc);
			}
			return ActionResult.SUCCESS;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (!stack.isOf(ModItems.MYSTERY_PARCEL)) {
				return TypedActionResult.pass(stack);
			}
			if (!world.isClient && player instanceof ServerPlayerEntity sp) {
				stack.decrement(1);
				openParcel(sp);
			}
			return TypedActionResult.success(stack, world.isClient);
		});
	}

	private static int randomTicks(Random rnd, int minMinutes, int maxMinutes) {
		int min = Math.max(1, minMinutes) * 1200;
		int max = Math.max(min, maxMinutes * 1200);
		return min + rnd.nextInt(max - min + 1);
	}

	public static void tick(MinecraftServer server) {
		long t = Diktatura.ticks();
		if (t % 5 == 0) {
			tickActive(server);
		}
		if (t % 20 != 0) {
			return;
		}
		DiktaturaConfig cfg = DiktaturaConfig.get();
		DiktaturaState state = DiktaturaState.get(server);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			PlayerRecord rec = state.player(p);
			if (rec.visitTicks < 0) {
				rec.visitTicks = randomTicks(p.getRandom(), cfg.visitMinMinutes, cfg.visitMaxMinutes);
			}
			rec.visitTicks -= 20;
			if (rec.visitTicks <= 0) {
				rec.visitTicks = randomTicks(p.getRandom(), cfg.visitMinMinutes, cfg.visitMaxMinutes);
				if (canVisit(p, rec)) {
					start(p, p.getRandom().nextBoolean() ? Kind.TAX : Kind.COURIER);
				}
			}
		}
	}

	/** Визит возможен, только если игрок на свободе, в верхнем мире и никуда не едет. */
	static boolean canVisit(ServerPlayerEntity p, PlayerRecord rec) {
		return LawEngine.applies(p, rec) && p.getWorld().getRegistryKey() == World.OVERWORLD && !p.isSpectator()
			&& !TransportSystem.isTraveling(p.getUuid());
	}

	/** Начать визит (по таймеру или командой). */
	public static boolean start(ServerPlayerEntity p, Kind kind) {
		String id = kind == Kind.TAX ? TAX_ID : COURIER_ID;
		if (NpcRegistry.get(id) == null) {
			return false;
		}
		ServerWorld world = p.getServerWorld();
		NpcEntity npc = ModEntities.NPC.create(world);
		if (npc == null) {
			return false;
		}
		BlockPos spot = spawnSpot(world, p);
		npc.setNpcId(id);
		npc.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
		NpcRegistry.apply(npc);
		ACTIVE.put(npc.getUuid(), new Visit(p.getUuid(), kind));
		world.spawnEntity(npc);
		world.spawnParticles(ParticleTypes.POOF, npc.getX(), npc.getY() + 1, npc.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		NpcRegistry.NpcData d = NpcRegistry.get(id);
		Hud.alert(p, kind == Kind.TAX ? AlertKind.DANGER : AlertKind.INFO,
			Text.translatable(kind == Kind.TAX ? "diktatura.visit.tax.coming" : "diktatura.visit.courier.coming"),
			Text.translatable(kind == Kind.TAX ? "diktatura.visit.tax.coming.hint" : "diktatura.visit.courier.coming.hint"), 6);
		Voice.say(npc, d.displayName(), d.phrase(p.getRandom().nextInt(d.phraseCount())), d.soundEvent(), d.pitch());
		return true;
	}

	/** Свободное место в 5–7 блоках от игрока на его уровне (или рядом, если всё занято). */
	private static BlockPos spawnSpot(ServerWorld world, ServerPlayerEntity p) {
		Random rnd = p.getRandom();
		BlockPos base = p.getBlockPos();
		for (int i = 0; i < 16; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			int r = 5 + rnd.nextInt(3);
			BlockPos c = base.add(MathHelper.floor(Math.cos(a) * r), 0, MathHelper.floor(Math.sin(a) * r));
			for (int dy = 2; dy >= -2; dy--) {
				BlockPos pos = c.up(dy);
				if (world.getBlockState(pos).isAir() && world.getBlockState(pos.up()).isAir()
					&& world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
					return pos;
				}
			}
		}
		return base;
	}

	private static void tickActive(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Visit>> it = ACTIVE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Visit> e = it.next();
			Visit v = e.getValue();
			v.ticks += 5;
			Entity ent = find(server, e.getKey());
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(v.player);
			if (!(ent instanceof NpcEntity npc) || npc.isRemoved()) {
				it.remove();
				continue;
			}
			boolean playerGone = p == null || p.getWorld() != npc.getWorld() || p.squaredDistanceTo(npc) > 48 * 48;
			if (v.stage == Stage.DONE) {
				if (v.ticks >= 50) {
					leave(npc);
					it.remove();
				}
				continue;
			}
			if (playerGone || v.ticks > 20 * 40) {
				leave(npc);
				it.remove();
				continue;
			}
			npc.getLookControl().lookAt(p, 30f, 30f);
			if (npc.squaredDistanceTo(p) <= 2.6 * 2.6) {
				npc.getNavigation().stop();
				v.stage = Stage.DONE;
				v.ticks = 0;
				if (v.kind == Kind.TAX) {
					collectTax(p, npc);
				} else {
					deliver(p, npc);
				}
			} else if (v.ticks % 20 == 0 || npc.getNavigation().isIdle()) {
				npc.getNavigation().startMovingTo(p, 1.0);
				// Застрял (вода, обрыв, стены) — служба не ждёт: подходит «административно».
				if (v.ticks > 20 * 15) {
					npc.refreshPositionAndAngles(p.getX() + 1.5, p.getY(), p.getZ() + 1.5, npc.getYaw(), 0f);
				}
			}
		}
	}

	private static Entity find(MinecraftServer server, UUID id) {
		for (ServerWorld w : server.getWorlds()) {
			Entity e = w.getEntity(id);
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	private static void leave(NpcEntity npc) {
		if (npc.getWorld() instanceof ServerWorld w) {
			w.spawnParticles(ParticleTypes.POOF, npc.getX(), npc.getY() + 1, npc.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		}
		npc.discard();
	}

	// ================================================================== налоговик

	private static void collectTax(ServerPlayerEntity p, NpcEntity npc) {
		NpcRegistry.NpcData d = NpcRegistry.get(TAX_ID);
		Text name = d != null ? d.displayName() : npc.getName();
		// Квитанция спасает от одной проверки.
		if (removeOne(p, ModItems.TAX_RECEIPT)) {
			Voice.say(npc, name, Text.translatable("diktatura.visit.tax.receipt"), null, 1f);
			Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.visit.tax.receipt.title"),
				Text.translatable("diktatura.visit.tax.receipt.hint"), 5);
			return;
		}
		int balance = Economy.balance(p);
		if (balance <= 0) {
			Voice.say(npc, name, Text.translatable("diktatura.visit.tax.empty"), null, 1f);
			return;
		}
		int tax = Math.max(1, MathHelper.ceil(balance * TAX_PERCENT / 100.0));
		Economy.add(p, -tax, "diktatura.money.reason.tax", "", false);
		Voice.say(npc, name, Text.translatable("diktatura.visit.tax.take." + p.getRandom().nextInt(3), tax),
			d != null ? d.soundEvent() : null, d != null ? d.pitch() : 1f);
		Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.visit.tax.paid", tax),
			Text.translatable("diktatura.visit.tax.paid.hint", TAX_PERCENT), 6);
		// Иногда — квитанция на память (спасёт от следующей проверки).
		if (p.getRandom().nextInt(4) == 0) {
			p.getInventory().offerOrDrop(new ItemStack(ModItems.TAX_RECEIPT));
			p.sendMessage(Text.translatable("diktatura.visit.tax.got_receipt").formatted(Formatting.AQUA), false);
		}
	}

	/** Удар по визитёру. Налоговик — покушение на налоговую: приставы, то есть арест. */
	private static void onAttack(ServerPlayerEntity p, NpcEntity npc) {
		Visit v = ACTIVE.get(npc.getUuid());
		ServerWorld world = p.getServerWorld();
		world.sendEntityDamage(npc, world.getDamageSources().playerAttack(p));
		long now = Diktatura.ticks();
		Long last = LAST_ASSAULT.get(p.getUuid());
		if (last != null && now - last < 40) {
			return;
		}
		LAST_ASSAULT.put(p.getUuid(), now);
		NpcRegistry.NpcData d = NpcRegistry.get(npc.npcId());
		Text name = d != null ? d.displayName() : npc.getName();
		if (v == null || v.kind != Kind.TAX) {
			Voice.say(npc, name, Text.translatable("diktatura.visit.courier.hit"), SoundEvents.ENTITY_VILLAGER_HURT, 1.2f);
			return;
		}
		Voice.say(npc, name, Text.translatable("diktatura.visit.tax.hit"), SoundEvents.ENTITY_VILLAGER_HURT, 0.7f);
		PlayerRecord rec = DiktaturaState.get(p.getServer()).player(p);
		if (!LawEngine.applies(p, rec)) {
			return;
		}
		int balance = Economy.balance(p);
		Economy.add(p, -Math.max(10, balance / 4), "diktatura.money.reason.bailiffs", "", true);
		LoyaltySystem.change(p, -15);
		PrisonSystem.arrest(p, 60 * 20, Text.translatable("diktatura.reason.tax_assault"));
		v.stage = Stage.DONE;
		v.ticks = 0;
	}

	// ================================================================== курьер и посылка-сюрприз

	private static void deliver(ServerPlayerEntity p, NpcEntity npc) {
		NpcRegistry.NpcData d = NpcRegistry.get(COURIER_ID);
		p.getInventory().offerOrDrop(new ItemStack(ModItems.MYSTERY_PARCEL));
		Voice.say(npc, d != null ? d.displayName() : npc.getName(), Text.translatable("diktatura.visit.courier.give"),
			d != null ? d.soundEvent() : null, d != null ? d.pitch() : 1f);
		Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.visit.courier.got"),
			Text.translatable("diktatura.visit.courier.got.hint"), 6);
	}

	/** Что лежит в посылке — решает госпочта (то есть случай). */
	public static void openParcel(ServerPlayerEntity p) {
		ServerWorld w = p.getServerWorld();
		Random rnd = p.getRandom();
		w.playSound(null, p.getBlockPos(), SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.PLAYERS, 1f, 1f);
		int roll = rnd.nextInt(100);
		String key;
		if (roll < 20) {
			int n = 5 + rnd.nextInt(16);
			Economy.add(p, n, "diktatura.money.reason.parcel", "", false);
			key = "money";
			Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.parcel.money", n), Text.empty(), 5);
		} else if (roll < 32) {
			p.getInventory().offerOrDrop(new ItemStack(Items.DIAMOND, 1 + rnd.nextInt(2)));
			key = "diamond";
		} else if (roll < 50) {
			p.getInventory().offerOrDrop(new ItemStack(Items.BREAD, 3 + rnd.nextInt(6)));
			key = "bread";
		} else if (roll < 62) {
			for (int i = 0; i < 10; i++) {
				ChickenEntity c = EntityType.CHICKEN.create(w);
				if (c != null) {
					c.refreshPositionAndAngles(p.getX() + rnd.nextDouble() * 2 - 1, p.getY() + 0.5, p.getZ() + rnd.nextDouble() * 2 - 1,
						rnd.nextFloat() * 360f, 0f);
					w.spawnEntity(c);
				}
			}
			key = "chickens";
		} else if (roll < 72) {
			// Наковальня на голову — с красной меткой, чтобы успеть отпрыгнуть.
			BlockPos above = p.getBlockPos().up(9);
			if (w.getBlockState(above).isAir()) {
				FallingBlockEntity anvil = FallingBlockEntity.spawnFromBlock(w, above, Blocks.ANVIL.getDefaultState());
				anvil.setHurtEntities(2.0f, 20);
				Weather.marker(w, p.getBlockPos(), 40);
			}
			key = "anvil";
		} else if (roll < 82) {
			LoyaltySystem.change(p, 5);
			key = "portrait";
			p.getInventory().offerOrDrop(new ItemStack(Items.PAINTING));
		} else if (roll < 92) {
			w.spawnParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 1.5, p.getZ(), 60, 0.6, 0.6, 0.6, 0.15);
			w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 1f, 1f);
			key = "confetti";
		} else {
			p.getInventory().offerOrDrop(new ItemStack(ModItems.TAX_RECEIPT));
			key = "receipt";
		}
		p.sendMessage(Text.translatable("diktatura.parcel." + key + ".msg").formatted(Formatting.GOLD), false);
	}

	private static boolean removeOne(ServerPlayerEntity p, net.minecraft.item.Item item) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack st = inv.getStack(i);
			if (st.isOf(item)) {
				st.decrement(1);
				return true;
			}
		}
		return false;
	}
}
