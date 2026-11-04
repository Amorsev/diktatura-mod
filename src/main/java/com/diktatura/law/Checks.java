package com.diktatura.law;

import com.diktatura.Diktatura;
import com.diktatura.wealth.ItemValues;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.block.Block;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Типы проверок законов. Тип задаётся полем "check.type" в JSON закона.
 * Новый тип = новый вложенный класс + строка в {@link #parse(JsonObject)}.
 */
public final class Checks {
	/** Блоки, которые считаются «землёй», а не постройкой (для закона о высоте домов). */
	public static final TagKey<Block> NATURAL_GROUND = TagKey.of(RegistryKeys.BLOCK, Diktatura.id("natural_ground"));

	private Checks() {
	}

	public static LawCheck parse(JsonObject o) {
		String type = Json.s(o, "type", "");
		return switch (type) {
			case "look_up" -> new LookUp(Json.d(o, "min_angle", 60));
			case "outside_at_night" -> new OutsideAtNight();
			case "sprinting" -> new Simple(p -> p.isSprinting());
			case "sneaking" -> new Simple(p -> p.isSneaking());
			case "in_water" -> new Simple(p -> p.isTouchingWater());
			case "hunger" -> new Hunger(Json.i(o, "max_food", 6));
			case "hold_item" -> new HoldItem(Matchers.items(o));
			case "have_item" -> new HaveItem(Matchers.items(o), Json.i(o, "min_count", 1));
			case "wealth" -> new Wealth(Json.d(o, "threshold", 12800), Json.b(o, "include_ender_chest", false));
			case "altitude" -> new Altitude(o.has("above") ? o.get("above").getAsInt() : null,
				o.has("below") ? o.get("below").getAsInt() : null);
			case "equipment" -> new Equipment(EquipmentSlot.byName(Json.s(o, "slot", "head")),
				Json.b(o, "required", true), Matchers.items(o), o.has("items") || o.has("tag"));
			case "dimension" -> new Dimension(new HashSet<>(Json.list(o, "dimensions")));
			case "standing_on" -> new StandingOn(Matchers.blocks(o));
			case "block_break" -> new BlockBreak(Matchers.blocks(o), Json.i(o, "spawn_radius", -1));
			case "block_place" -> new BlockPlace(Matchers.blocks(o), Json.i(o, "max_build_height", -1),
				Json.i(o, "spawn_radius", -1));
			case "chat" -> new Chat(Json.list(o, "words"), Json.s(o, "regex", ""), Json.d(o, "caps_ratio", -1),
				Json.i(o, "min_length", 1), Json.b(o, "any", false));
			case "kill_entity" -> new EntityEvent(LawEvent.KILL, Matchers.entities(o));
			case "attack_entity" -> new EntityEvent(LawEvent.ATTACK, Matchers.entities(o));
			case "use_item" -> new UseItem(Matchers.items(o));
			case "sleep" -> new Sleep();
			default -> throw new IllegalArgumentException("Неизвестный тип проверки: " + type);
		};
	}

	static boolean nearSpawn(World world, BlockPos pos, int radius) {
		if (radius < 0) {
			return true;
		}
		if (world.getRegistryKey() != World.OVERWORLD) {
			return false;
		}
		BlockPos spawn = world.getSpawnPos();
		long dx = pos.getX() - spawn.getX();
		long dz = pos.getZ() - spawn.getZ();
		return dx * dx + dz * dz <= (long) radius * radius;
	}

	/** Периодические проверки состояния игрока. */
	abstract static class TickCheck implements LawCheck {
		@Override
		public LawEvent event() {
			return LawEvent.TICK;
		}

		@Override
		public boolean test(LawContext ctx) {
			return ctx.regular && testPlayer(ctx.player);
		}

		abstract boolean testPlayer(ServerPlayerEntity p);
	}

	static final class Simple extends TickCheck {
		private final Predicate<ServerPlayerEntity> pred;

		Simple(Predicate<ServerPlayerEntity> pred) {
			this.pred = pred;
		}

		@Override
		boolean testPlayer(ServerPlayerEntity p) {
			return pred.test(p);
		}
	}

	/** «Запрещено смотреть вверх». */
	static final class LookUp extends TickCheck {
		private final double minAngle;

		LookUp(double minAngle) {
			this.minAngle = minAngle;
		}

		@Override
		boolean testPlayer(ServerPlayerEntity p) {
			return p.getPitch() <= -minAngle;
		}
	}

	/** Комендантский час: ночь и над головой открытое небо. */
	static final class OutsideAtNight extends TickCheck {
		@Override
		boolean testPlayer(ServerPlayerEntity p) {
			ServerWorld w = p.getServerWorld();
			if (!w.getDimension().hasSkyLight() || w.getDimension().hasFixedTime()) {
				return false;
			}
			long t = w.getTimeOfDay() % 24000L;
			boolean night = t >= 13000L && t <= 23000L;
			return night && w.isSkyVisible(BlockPos.ofFloored(p.getEyePos()));
		}
	}

	static final class Hunger extends TickCheck {
		private final int maxFood;

		Hunger(int maxFood) {
			this.maxFood = maxFood;
		}

		@Override
		boolean testPlayer(ServerPlayerEntity p) {
			return p.getHungerManager().getFoodLevel() <= maxFood;
		}
	}

	static final class HoldItem extends TickCheck {
		private final Predicate<ItemStack> items;

		HoldItem(Predicate<ItemStack> items) {
			this.items = items;
		}

		@Override
		boolean testPlayer(ServerPlayerEntity p) {
			return items.test(p.getMainHandStack()) || items.test(p.getOffHandStack());
		}

		@Override
		public boolean isOffending(ItemStack stack) {
			return items.test(stack);
		}
	}

	static final class HaveItem extends TickCheck {
		private final Predicate<ItemStack> items;
		private final int minCount;

		HaveItem(Predicate<ItemStack> items, int minCount) {
			this.items = items;
			this.minCount = minCount;
		}

		@Override
		public boolean test(LawContext ctx) {
			return (ctx.regular || ctx.pickup) && testPlayer(ctx.player);
		}

		@Override
		boolean testPlayer(ServerPlayerEntity p) {
			int count = 0;
			var inv = p.getInventory();
			for (int i = 0; i < inv.size(); i++) {
				ItemStack s = inv.getStack(i);
				if (items.test(s)) {
					count += s.getCount();
					if (count >= minCount) {
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public boolean isOffending(ItemStack stack) {
			return items.test(stack);
		}

		@Override
		public boolean checksOnPickup() {
			return true;
		}
	}

	/** «Нельзя быть богаче короля»: сумма ценностей инвентаря больше порога. */
	public static final class Wealth implements LawCheck {
		public final double threshold;
		public final boolean includeEnderChest;

		Wealth(double threshold, boolean includeEnderChest) {
			this.threshold = threshold;
			this.includeEnderChest = includeEnderChest;
		}

		@Override
		public LawEvent event() {
			return LawEvent.TICK;
		}

		@Override
		public boolean test(LawContext ctx) {
			if (!ctx.regular && !ctx.pickup) {
				return false;
			}
			return ItemValues.cachedInventoryValue(ctx.player, includeEnderChest, ctx.pickup) > threshold;
		}

		@Override
		public boolean checksOnPickup() {
			return true;
		}
	}

	static final class Altitude extends TickCheck {
		private final Integer above;
		private final Integer below;

		Altitude(Integer above, Integer below) {
			this.above = above;
			this.below = below;
		}

		@Override
		boolean testPlayer(ServerPlayerEntity p) {
			double y = p.getY();
			return (above != null && y > above) || (below != null && y < below);
		}
	}

	/**
	 * Дресс-код. required=true: в слоте обязан быть предмет (из списка, если он задан).
	 * required=false: предмет из списка в этом слоте запрещён.
	 */
	static final class Equipment extends TickCheck {
		private final EquipmentSlot slot;
		private final boolean required;
		private final Predicate<ItemStack> items;
		private final boolean hasFilter;

		Equipment(EquipmentSlot slot, boolean required, Predicate<ItemStack> items, boolean hasFilter) {
			this.slot = slot;
			this.required = required;
			this.items = items;
			this.hasFilter = hasFilter;
		}

		@Override
		boolean testPlayer(ServerPlayerEntity p) {
			ItemStack s = p.getEquippedStack(slot);
			if (required) {
				return s.isEmpty() || (hasFilter && !items.test(s));
			}
			return items.test(s);
		}

		@Override
		public boolean isOffending(ItemStack stack) {
			return !required && items.test(stack);
		}
	}

	static final class Dimension extends TickCheck {
		private final Set<String> dims;

		Dimension(Set<String> dims) {
			this.dims = dims;
		}

		@Override
		boolean testPlayer(ServerPlayerEntity p) {
			return dims.contains(p.getWorld().getRegistryKey().getValue().toString());
		}
	}

	static final class StandingOn extends TickCheck {
		private final Predicate<BlockState> blocks;

		StandingOn(Predicate<BlockState> blocks) {
			this.blocks = blocks;
		}

		@Override
		boolean testPlayer(ServerPlayerEntity p) {
			if (!p.isOnGround()) {
				return false;
			}
			return blocks.test(p.getWorld().getBlockState(p.getBlockPos().down()));
		}
	}

	static final class BlockBreak implements LawCheck {
		private final Predicate<BlockState> blocks;
		private final int spawnRadius;

		BlockBreak(Predicate<BlockState> blocks, int spawnRadius) {
			this.blocks = blocks;
			this.spawnRadius = spawnRadius;
		}

		@Override
		public LawEvent event() {
			return LawEvent.BLOCK_BREAK;
		}

		@Override
		public boolean test(LawContext ctx) {
			return blocks.test(ctx.state) && nearSpawn(ctx.player.getWorld(), ctx.pos, spawnRadius);
		}
	}

	/** Установка блоков; max_build_height — «запрещено строить выше N блоков». */
	static final class BlockPlace implements LawCheck {
		private final Predicate<BlockState> blocks;
		private final int maxHeight;
		private final int spawnRadius;

		BlockPlace(Predicate<BlockState> blocks, int maxHeight, int spawnRadius) {
			this.blocks = blocks;
			this.maxHeight = maxHeight;
			this.spawnRadius = spawnRadius;
		}

		@Override
		public LawEvent event() {
			return LawEvent.BLOCK_PLACE;
		}

		@Override
		public boolean test(LawContext ctx) {
			if (!(ctx.stack.getItem() instanceof BlockItem bi)) {
				return false;
			}
			if (!blocks.test(bi.getBlock().getDefaultState())) {
				return false;
			}
			World w = ctx.player.getWorld();
			if (!nearSpawn(w, ctx.pos, spawnRadius)) {
				return false;
			}
			return maxHeight <= 0 || columnHeight(w, ctx.pos) > maxHeight;
		}

		/** Высота столба построек под позицией, включая ставящийся блок. */
		private static int columnHeight(World w, BlockPos pos) {
			int h = 1;
			BlockPos p = pos.down();
			while (h <= 64 && p.getY() >= w.getBottomY()) {
				BlockState s = w.getBlockState(p);
				if (s.isAir() || !s.getFluidState().isEmpty() || s.isIn(NATURAL_GROUND)) {
					break;
				}
				h++;
				p = p.down();
			}
			return h;
		}
	}

	static final class Chat implements LawCheck {
		private final List<String> words;
		private final Pattern regex;
		private final double capsRatio;
		private final int minLength;
		private final boolean any;

		Chat(List<String> words, String regex, double capsRatio, int minLength, boolean any) {
			this.words = words.stream().map(w -> w.toLowerCase(Locale.ROOT)).toList();
			this.regex = regex.isEmpty() ? null : Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			this.capsRatio = capsRatio;
			this.minLength = minLength;
			this.any = any;
		}

		@Override
		public LawEvent event() {
			return LawEvent.CHAT;
		}

		@Override
		public boolean test(LawContext ctx) {
			String msg = ctx.message;
			if (msg.length() < minLength) {
				return false;
			}
			if (any) {
				return true;
			}
			String lower = msg.toLowerCase(Locale.ROOT);
			for (String w : words) {
				if (lower.contains(w)) {
					return true;
				}
			}
			if (regex != null && regex.matcher(msg).find()) {
				return true;
			}
			if (capsRatio > 0) {
				int letters = 0;
				int upper = 0;
				for (int i = 0; i < msg.length(); i++) {
					char c = msg.charAt(i);
					if (Character.isLetter(c)) {
						letters++;
						if (Character.isUpperCase(c)) {
							upper++;
						}
					}
				}
				return letters >= minLength && upper >= letters * capsRatio;
			}
			return false;
		}
	}

	static final class EntityEvent implements LawCheck {
		private final LawEvent event;
		private final Predicate<Entity> entities;

		EntityEvent(LawEvent event, Predicate<Entity> entities) {
			this.event = event;
			this.entities = entities;
		}

		@Override
		public LawEvent event() {
			return event;
		}

		@Override
		public boolean test(LawContext ctx) {
			return entities.test(ctx.target);
		}
	}

	static final class UseItem implements LawCheck {
		private final Predicate<ItemStack> items;

		UseItem(Predicate<ItemStack> items) {
			this.items = items;
		}

		@Override
		public LawEvent event() {
			return LawEvent.USE_ITEM;
		}

		@Override
		public boolean test(LawContext ctx) {
			return items.test(ctx.stack);
		}

		@Override
		public boolean isOffending(ItemStack stack) {
			return items.test(stack);
		}
	}

	static final class Sleep implements LawCheck {
		@Override
		public LawEvent event() {
			return LawEvent.SLEEP;
		}

		@Override
		public boolean test(LawContext ctx) {
			return true;
		}
	}
}
