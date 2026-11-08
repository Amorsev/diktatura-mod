package com.diktatura.client;

import com.diktatura.Diktatura;
import com.diktatura.client.ui.Ui;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.resource.Resource;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * «Что нового в Диктатуре»: окно из главного меню с кратким списком основных изменений.
 * Текст — assets/diktatura/changelog/&lt;язык&gt;.json (версия, дата, заголовок, пункты),
 * для незнакомого языка — английский.
 */
public class ChangelogScreen extends Screen {
	private record Entry(String version, String date, String title, List<String> items) {
	}

	private final Screen parent;
	private final List<Entry> entries = new ArrayList<>();
	private int scroll;
	private int contentHeight;
	private int boxW;
	private int left;
	private int top;
	private int bottom;

	public ChangelogScreen(Screen parent) {
		super(Text.translatable("diktatura.changelog.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		boxW = Math.min(380, width - 30);
		left = (width - boxW) / 2;
		top = 34;
		bottom = height - 34;
		if (entries.isEmpty()) {
			load();
		}
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
			.dimensions(width / 2 - 50, height - 27, 100, 20).build());
	}

	private void load() {
		MinecraftClient mc = MinecraftClient.getInstance();
		String lang = mc.getLanguageManager().getLanguage();
		Optional<Resource> res = mc.getResourceManager().getResource(Diktatura.id("changelog/" + lang + ".json"));
		if (res.isEmpty()) {
			res = mc.getResourceManager().getResource(Diktatura.id("changelog/en_us.json"));
		}
		if (res.isEmpty()) {
			return;
		}
		try (Reader r = res.get().getReader()) {
			JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
			for (JsonElement el : arr) {
				JsonObject o = el.getAsJsonObject();
				List<String> items = new ArrayList<>();
				for (JsonElement i : o.getAsJsonArray("items")) {
					items.add(i.getAsString());
				}
				entries.add(new Entry(o.get("version").getAsString(), o.get("date").getAsString(),
					o.has("title") ? o.get("title").getAsString() : "", items));
			}
		} catch (Exception e) {
			Diktatura.LOGGER.warn("Не удалось прочитать список изменений", e);
		}
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		Ui.window(ctx, left - 8, 6, boxW + 16, bottom - 2, Ui.GOLD);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.GOLD, Formatting.BOLD), width / 2, 14, 0xFFFFFF);
		ctx.enableScissor(left - 4, top - 2, left + boxW + 4, bottom);
		int y = top - scroll;
		int start = y;
		for (Entry e : entries) {
			// Плашка версии.
			Text ver = Text.literal(e.version()).formatted(Formatting.BOLD);
			int vw = textRenderer.getWidth(ver) + 10;
			ctx.fill(left, y, left + vw, y + 13, 0xFFB3202A);
			ctx.drawTextWithShadow(textRenderer, ver, left + 5, y + 3, 0xFFFFE27A);
			ctx.drawTextWithShadow(textRenderer, Text.literal(e.date()), left + vw + 6, y + 3, 0xFF9A9AA0);
			y += 17;
			if (!e.title().isEmpty()) {
				for (OrderedText line : textRenderer.wrapLines(Text.literal(e.title()).formatted(Formatting.GOLD), boxW)) {
					ctx.drawTextWithShadow(textRenderer, line, left, y, 0xFFFFD27F);
					y += 10;
				}
				y += 4;
			}
			for (String item : e.items()) {
				List<OrderedText> lines = textRenderer.wrapLines(Text.literal(item), boxW - 12);
				ctx.fill(left + 3, y + 3, left + 6, y + 6, 0xFFD4A017);
				for (OrderedText line : lines) {
					ctx.drawTextWithShadow(textRenderer, line, left + 12, y, 0xFFE8E8E8);
					y += 10;
				}
				y += 3;
			}
			y += 10;
		}
		ctx.disableScissor();
		contentHeight = y - start;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int max = Math.max(0, contentHeight - (bottom - top));
		scroll = (int) Math.max(0, Math.min(max, scroll - verticalAmount * 12));
		return true;
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}
}
