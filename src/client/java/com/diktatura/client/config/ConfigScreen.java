package com.diktatura.client.config;

import com.diktatura.Diktatura;
import com.diktatura.config.DiktaturaConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Экран настроек мода (config/diktatura.json) — открывается из Mod Menu или кнопкой в книге
 * законов (в одиночной игре). Поля берутся из {@link DiktaturaConfig} автоматически и
 * сгруппированы по разделам; подсказка — при наведении на название.
 */
public class ConfigScreen extends Screen {
	private static final Map<String, String[]> SECTIONS = new LinkedHashMap<>();

	static {
		SECTIONS.put("laws", new String[] {"decreeIntervalMinutes", "minActiveLaws", "maxActiveLaws", "checkIntervalTicks",
			"eventMinMinutes", "eventMaxMinutes", "gravityPulsesMin", "gravityPulsesMax", "exemptOps", "exemptCreative", "giveLawBook"});
		SECTIONS.put("loyalty", new String[] {"startLoyalty", "loyaltyRegenPerMinute", "lowLoyalty", "highLoyalty", "rebellionLeaflets",
			"rebellionMinutes", "rebellionCooldownMinutes", "bloodMoon", "bloodMoonEveryNights", "bloodMoonExtraMobs"});
		SECTIONS.put("prison", new String[] {"minSentenceSeconds", "maxSentenceSeconds", "defaultArrestSeconds", "prisonOffsetX",
			"prisonOffsetZ", "capitalMountain", "transportSpeed", "transportMaxDirectDistance"});
		SECTIONS.put("content", new String[] {"wildGeese", "gooseFrequency", "gooseSpawnWeight", "captchaChance", "curfewDouble", "enforcerMobs", "builderZombieChance",
			"sniperSkeletonChance", "distortionDrug", "screamingFoodChance", "mimicChance", "firstVisitLoading", "runawayOre",
			"runawayDiamondChance", "runawayOreChance", "timber", "caveInChance", "coldAfterSeconds", "coldMinutes"});
		SECTIONS.put("finale", new String[] {"finalFight", "dragonHealth", "dictatorHealth", "immunityDays"});
		SECTIONS.put("bureau", new String[] {"officeOpenMinSeconds", "officeOpenMaxSeconds", "officeClosedMinMinutes",
			"officeClosedMaxMinutes", "visitMinMinutes", "visitMaxMinutes", "adMinMinutes", "adMaxMinutes", "weatherMinMinutes",
			"weatherMaxMinutes", "propagandaMinutes"});
	}

	private final Screen parent;
	private ConfigList list;
	private Text error;
	/** Подсказка строки под мышью (ставится при отрисовке строк). */
	Text hoverTip;

	public ConfigScreen(Screen parent) {
		super(Text.translatable("diktatura.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		list = new ConfigList(client, width, height - 32 - 60, 32, 24);
		DiktaturaConfig cfg = DiktaturaConfig.get();
		Set<String> placed = new LinkedHashSet<>();
		for (Map.Entry<String, String[]> sec : SECTIONS.entrySet()) {
			list.add(new Header(Text.translatable("diktatura.config.section." + sec.getKey())));
			for (String name : sec.getValue()) {
				Row r = row(cfg, name);
				if (r != null) {
					list.add(r);
					placed.add(name);
				}
			}
		}
		List<Row> rest = new ArrayList<>();
		for (Field f : DiktaturaConfig.class.getFields()) {
			if (!Modifier.isStatic(f.getModifiers()) && !placed.contains(f.getName()) && !f.getName().equals("configVersion")) {
				Row r = row(cfg, f.getName());
				if (r != null) {
					rest.add(r);
				}
			}
		}
		if (!rest.isEmpty()) {
			list.add(new Header(Text.translatable("diktatura.config.section.misc")));
			rest.forEach(list::add);
		}
		addDrawableChild(list);
		addDrawableChild(ButtonWidget.builder(Text.translatable("diktatura.config.done"), b -> save())
			.dimensions(width / 2 - 154, height - 26, 150, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("diktatura.config.cancel"), b -> close())
			.dimensions(width / 2 + 4, height - 26, 150, 20).build());
	}

	private Row row(DiktaturaConfig cfg, String name) {
		try {
			Field f = DiktaturaConfig.class.getField(name);
			Class<?> t = f.getType();
			if (t != boolean.class && t != int.class && t != double.class && t != String.class) {
				return null;
			}
			return new Row(f, f.get(cfg));
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private void save() {
		DiktaturaConfig cfg = DiktaturaConfig.get();
		List<Runnable> apply = new ArrayList<>();
		for (ConfigList.Entry e : list.children()) {
			if (e instanceof Row r) {
				Runnable a = r.parse(cfg);
				if (a == null) {
					error = Text.translatable("diktatura.config.bad", r.label).formatted(Formatting.RED);
					return;
				}
				apply.add(a);
			}
		}
		apply.forEach(Runnable::run);
		DiktaturaConfig.save();
		// Перечитать файл: там же проверяются границы значений.
		DiktaturaConfig.load();
		close();
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		hoverTip = null;
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFD27F);
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("diktatura.config.note").formatted(Formatting.GRAY),
			width / 2, 20, 0xFFA0A0A0);
		if (error != null) {
			ctx.drawCenteredTextWithShadow(textRenderer, error, width / 2, height - 42, 0xFFFF5555);
		}
		if (hoverTip != null) {
			ctx.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(hoverTip, 260), mouseX, mouseY);
		}
	}

	// ------------------------------------------------------------------ список

	class ConfigList extends ElementListWidget<ConfigList.Entry> {
		abstract static class Entry extends ElementListWidget.Entry<Entry> {
		}

		ConfigList(MinecraftClient client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void add(Entry e) {
			addEntry(e);
		}

		@Override
		public int getRowWidth() {
			return Math.min(340, width - 30);
		}
	}

	private class Header extends ConfigList.Entry {
		private final Text text;

		Header(Text text) {
			this.text = text.copy().formatted(Formatting.GOLD, Formatting.BOLD);
		}

		@Override
		public List<? extends Element> children() {
			return List.of();
		}

		@Override
		public List<? extends Selectable> selectableChildren() {
			return List.of();
		}

		@Override
		public void render(DrawContext ctx, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY,
		                   boolean hovered, float tickDelta) {
			ctx.drawTextWithShadow(textRenderer, text, x, y + 8, 0xFFFFFFFF);
			ctx.fill(x, y + 19, x + entryWidth, y + 20, 0x40FFFFFF);
		}
	}

	private class Row extends ConfigList.Entry {
		final Field field;
		final Text label;
		private final Text tip;
		private boolean bool;
		private ButtonWidget toggle;
		private TextFieldWidget input;

		Row(Field field, Object value) {
			this.field = field;
			String key = "diktatura.config." + field.getName();
			this.label = I18n.hasTranslation(key) ? Text.translatable(key) : Text.literal(field.getName());
			this.tip = I18n.hasTranslation(key + ".desc") ? Text.translatable(key + ".desc") : null;
			if (field.getType() == boolean.class) {
				bool = (Boolean) value;
				toggle = ButtonWidget.builder(onOff(), b -> {
					bool = !bool;
					b.setMessage(onOff());
				}).dimensions(0, 0, 90, 20).build();
			} else {
				input = new TextFieldWidget(textRenderer, 0, 0, 90, 18, label);
				input.setMaxLength(128);
				String text = value instanceof Double d ? trim(d) : String.valueOf(value);
				input.setText(text);
			}
		}

		private Text onOff() {
			return bool ? Text.translatable("diktatura.config.on").formatted(Formatting.GREEN)
				: Text.translatable("diktatura.config.off").formatted(Formatting.RED);
		}

		private String trim(double d) {
			return d == Math.floor(d) && Math.abs(d) < 1e9 ? String.valueOf((long) d) : String.valueOf(d);
		}

		/** Проверить ввод; вернуть действие, записывающее значение (или null при ошибке). */
		Runnable parse(DiktaturaConfig cfg) {
			try {
				Object v;
				if (toggle != null) {
					v = bool;
				} else if (field.getType() == int.class) {
					v = Integer.parseInt(input.getText().trim());
				} else if (field.getType() == double.class) {
					v = Double.parseDouble(input.getText().trim().replace(',', '.'));
				} else {
					v = input.getText();
				}
				Object val = v;
				return () -> {
					try {
						field.set(cfg, val);
					} catch (IllegalAccessException e) {
						Diktatura.LOGGER.error("Не удалось записать настройку {}", field.getName(), e);
					}
				};
			} catch (NumberFormatException e) {
				return null;
			}
		}

		@Override
		public List<? extends Element> children() {
			return toggle != null ? List.of(toggle) : List.of(input);
		}

		@Override
		public List<? extends Selectable> selectableChildren() {
			return toggle != null ? List.of(toggle) : List.of(input);
		}

		@Override
		public void render(DrawContext ctx, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY,
		                   boolean hovered, float tickDelta) {
			int wx = x + entryWidth - 92;
			ctx.drawTextWithShadow(textRenderer, label, x, y + 6, 0xFFE0E0E0);
			if (toggle != null) {
				toggle.setX(wx);
				toggle.setY(y);
				toggle.render(ctx, mouseX, mouseY, tickDelta);
			} else {
				input.setX(wx + 1);
				input.setY(y + 1);
				input.render(ctx, mouseX, mouseY, tickDelta);
			}
			if (tip != null && mouseX >= x && mouseX < wx - 4 && mouseY >= y && mouseY < y + entryHeight) {
				hoverTip = tip;
			}
		}
	}

	static String lower(String s) {
		return s.toLowerCase(Locale.ROOT);
	}
}
