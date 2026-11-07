package com.diktatura.bureau;

import com.diktatura.config.DiktaturaConfig;
import com.diktatura.goose.ModSounds;
import com.diktatura.network.FxPayloads;
import com.diktatura.network.UiPayloads.AlertKind;
import com.diktatura.ui.Hud;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BrewingStandBlock;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.SmithingTableBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Бюро проверки гражданства: перед столом зачарований, наковальней, эндер-сундуком,
 * варочной стойкой и столом кузнеца гражданин доказывает, что он не робот. Задачу и ответ
 * знает только сервер. Ошибка — молния рядом и немного урона; отказ — просто без доступа.
 * Прошедший проверку минуту пользуется этим блоком свободно.
 */
public final class Captcha {
	private enum Category {
		FOOD(Items.APPLE, Items.BREAD, Items.COOKED_BEEF, Items.CARROT, Items.CAKE, Items.COOKIE, Items.MELON_SLICE,
			Items.BAKED_POTATO, Items.PUMPKIN_PIE),
		TOOLS(Items.IRON_PICKAXE, Items.DIAMOND_AXE, Items.STONE_SHOVEL, Items.GOLDEN_HOE, Items.WOODEN_PICKAXE,
			Items.SHEARS, Items.FLINT_AND_STEEL, Items.FISHING_ROD),
		WEAPONS(Items.IRON_SWORD, Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.DIAMOND_SWORD, Items.WOODEN_SWORD, Items.MACE),
		TREASURE(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.AMETHYST_SHARD, Items.LAPIS_LAZULI, Items.NETHERITE_INGOT),
		PLANTS(Items.OAK_SAPLING, Items.POPPY, Items.DANDELION, Items.CACTUS, Items.SUGAR_CANE, Items.FERN, Items.BAMBOO, Items.SUNFLOWER);

		final Item[] items;

		Category(Item... items) {
			this.items = items;
		}
	}

	private static final Item[] FILLER = {Items.COBBLESTONE, Items.STICK, Items.STRING, Items.BONE, Items.GUNPOWDER,
		Items.ROTTEN_FLESH, Items.FEATHER, Items.CLAY_BALL, Items.BRICK, Items.COAL, Items.PAPER, Items.GLASS_BOTTLE,
		Items.BUCKET, Items.TORCH};

	private record Pending(int token, BlockPos pos, int mask, long expires) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	/** Кто недавно прошёл проверку у какого блока: игрок → (позиция, до какого тика). */
	private static final Map<UUID, Map<BlockPos, Long>> PASSED = new HashMap<>();
	private static final int PASS_TICKS = 20 * 60;
	private static boolean bypass;
	private static int nextToken = 1;

	private Captcha() {
	}

	public static void registerEvents() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient || bypass || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity sp)
				|| sp.isSpectator() || sp.isCreative()) {
				return ActionResult.PASS;
			}
			double chance = DiktaturaConfig.get().captchaChance;
			if (chance <= 0) {
				return ActionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (!needsCaptcha(state.getBlock())) {
				return ActionResult.PASS;
			}
			if (player.isSneaking() && !player.getMainHandStack().isEmpty()) {
				return ActionResult.PASS;
			}
			long now = world.getTime();
			Long until = PASSED.getOrDefault(sp.getUuid(), Map.of()).get(pos);
			if (until != null && until > now) {
				return ActionResult.PASS;
			}
			if (!ServerPlayNetworking.canSend(sp, FxPayloads.Captcha.ID) || world.random.nextDouble() >= chance) {
				return ActionResult.PASS;
			}
			send(sp, pos.toImmutable(), now);
			return ActionResult.FAIL;
		});
	}

	private static boolean needsCaptcha(Block b) {
		return b instanceof EnchantingTableBlock || b instanceof AnvilBlock || b instanceof EnderChestBlock
			|| b instanceof BrewingStandBlock || b instanceof SmithingTableBlock;
	}

	private static void send(ServerPlayerEntity p, BlockPos pos, long now) {
		Random r = p.getRandom();
		Category cat = Category.values()[r.nextInt(Category.values().length)];
		int good = 3 + r.nextInt(3);
		List<Integer> idx = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			idx.add(i);
		}
		Collections.shuffle(idx, new java.util.Random(r.nextLong()));
		ItemStack[] cells = new ItemStack[9];
		int mask = 0;
		for (int i = 0; i < 9; i++) {
			int cell = idx.get(i);
			if (i < good) {
				cells[cell] = new ItemStack(cat.items[r.nextInt(cat.items.length)]);
				mask |= 1 << cell;
			} else {
				cells[cell] = new ItemStack(FILLER[r.nextInt(FILLER.length)]);
			}
		}
		int token = nextToken++;
		PENDING.put(p.getUuid(), new Pending(token, pos, mask, now + 20 * 120));
		ServerPlayNetworking.send(p, new FxPayloads.Captcha(token,
			Text.translatable("diktatura.captcha.cat." + cat.name().toLowerCase(java.util.Locale.ROOT)), List.of(cells)));
	}

	public static void onAnswer(ServerPlayerEntity p, FxPayloads.CaptchaAnswer answer) {
		Pending pending = PENDING.get(p.getUuid());
		if (pending == null || pending.token() != answer.token()) {
			return;
		}
		PENDING.remove(p.getUuid());
		ServerWorld w = p.getServerWorld();
		if (answer.cancelled() || w.getTime() > pending.expires()) {
			return;
		}
		BlockPos pos = pending.pos();
		if (answer.mask() == pending.mask()) {
			w.playSound(null, p.getBlockPos(), ModSounds.CAPTCHA_OK, SoundCategory.PLAYERS, 1f, 1f);
			Hud.alert(p, AlertKind.GOOD, Text.translatable("diktatura.captcha.ok"), Text.translatable("diktatura.captcha.ok.hint"), 4);
			PASSED.computeIfAbsent(p.getUuid(), k -> new HashMap<>()).put(pos, w.getTime() + PASS_TICKS);
			BlockState state = w.getBlockState(pos);
			if (needsCaptcha(state.getBlock()) && p.squaredDistanceTo(Vec3d.ofCenter(pos)) < 64) {
				bypass = true;
				try {
					state.onUse(w, p, new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
				} finally {
					bypass = false;
				}
			}
		} else {
			w.playSound(null, p.getBlockPos(), ModSounds.CAPTCHA_FAIL, SoundCategory.PLAYERS, 1f, 1f);
			Hud.alert(p, AlertKind.DANGER, Text.translatable("diktatura.captcha.fail"), Text.translatable("diktatura.captcha.fail.hint"), 5);
			LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
			if (bolt != null) {
				bolt.setCosmetic(true);
				bolt.refreshPositionAfterTeleport(p.getX() + 1.5, p.getY(), p.getZ() + 1.5);
				w.spawnEntity(bolt);
			}
			p.damage(p.getDamageSources().lightningBolt(), 3f);
		}
	}

	/** Тестовая капча (команда): без блока, просто проверить окно и ответ. */
	public static void test(ServerPlayerEntity p) {
		if (ServerPlayNetworking.canSend(p, FxPayloads.Captcha.ID)) {
			send(p, p.getBlockPos(), p.getServerWorld().getTime());
		}
	}

	/** Игрок вышел — забыть его проверки. */
	public static void forget(UUID id) {
		PENDING.remove(id);
		PASSED.remove(id);
	}
}
