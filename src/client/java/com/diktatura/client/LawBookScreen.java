package com.diktatura.client;

import com.diktatura.client.ui.Ui;
import com.diktatura.law.Law;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import com.diktatura.network.UiPayloads;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Книга законов: вкладки «Действующие», «Отменённые» (будущие законы заранее не видны), «Мои лицензии» и поиск
 * по названию и описанию. Прокрутка колесом.
 */
public class LawBookScreen extends Screen {
	private enum Tab { ACTIVE, REPEALED, LICENSES }

	private static Tab lastTab = Tab.ACTIVE;

	private final Screen parent;
	private Tab tab = lastTab;
	private String query = "";
	private TextFieldWidget search;
	private final List<ButtonWidget> tabButtons = new ArrayList<>();
	private int scroll;
	private int contentHeight;
	private int boxW;
	private int left;
	private int listTop;
	private int listBottom;

	public LawBookScreen() {
		this(null);
	}

	public LawBookScreen(Screen parent) {
		super(Text.translatable("diktatura.book.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		boxW = Math.min(340, width - 30);
		left = (width - boxW) / 2;
		int tabsY = 32;
		listTop = tabsY + 46;
		listBottom = height - 32;

		tabButtons.clear();
		Tab[] tabs = Tab.values();
		int tw = (boxW - (tabs.length - 1) * 3) / tabs.length;
		for (int i = 0; i < tabs.length; i++) {
			Tab t = tabs[i];
			ButtonWidget b = ButtonWidget.builder(Text.translatable("diktatura.book.tab." + t.name().toLowerCase(Locale.ROOT)),
				btn -> selectTab(t)).dimensions(left + i * (tw + 3), tabsY, tw, 18).build();
			tabButtons.add(addDrawableChild(b));
		}
		search = new TextFieldWidget(textRenderer, left, tabsY + 23, boxW, 16, Text.translatable("diktatura.book.search"));
		search.setPlaceholder(Text.translatable("diktatura.book.search").formatted(Formatting.DARK_GRAY));
		search.setText(query);
		search.setChangedListener(s -> {
			query = s;
			scroll = 0;
		});
		addDrawableChild(search);
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
			.dimensions(width / 2 - 50, height - 26, 100, 20).build());
		updateTabs();
	}

	private void selectTab(Tab t) {
		tab = t;
		lastTab = t;
		scroll = 0;
		updateTabs();
	}

	private void updateTabs() {
		for (int i = 0; i < tabButtons.size(); i++) {
			tabButtons.get(i).active = Tab.values()[i] != tab;
		}
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}

	private List<String> source() {
		return switch (tab) {
			case ACTIVE -> ClientState.laws;
			case REPEALED -> ClientState.repealedLaws;
			case LICENSES -> List.of();
		};
	}

	private boolean matches(String id) {
		if (query.isBlank()) {
			return true;
		}
		String q = query.toLowerCase(Locale.ROOT).trim();
		String name = Text.translatable(Law.translationKey(id)).getString().toLowerCase(Locale.ROOT);
		String desc = Text.translatable(Law.translationKey(id) + ".desc").getString().toLowerCase(Locale.ROOT);
		return name.contains(q) || desc.contains(q);
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		Ui.window(ctx, left - 6, 4, boxW + 12, listBottom - 2, Ui.GOLD);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.GOLD, Formatting.BOLD), width / 2, 10, 0xFFFFFF);
		ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("diktatura.book.subtitle", ClientState.laws.size(),
			Ui.time(ClientState.decreeSeconds)), width / 2, 21, 0xA0A0A0);

		ctx.enableScissor(left - 4, listTop - 2, left + boxW + 4, listBottom);
		int y = listTop - scroll;
		int start = y;
		if (tab == Tab.LICENSES) {
			ctx.drawTextWithShadow(textRenderer, Text.translatable("diktatura.hud.balance", ClientState.balance), left, y, 0xFFFFD27F);
			y += 14;
			if (ClientState.licenses.isEmpty()) {
				for (OrderedText line : textRenderer.wrapLines(Text.translatable("diktatura.book.licenses.empty"), boxW)) {
					ctx.drawTextWithShadow(textRenderer, line, left, y, 0xC0C0C0);
					y += 10;
				}
			}
			for (UiPayloads.LicenseEntry e : ClientState.licenses) {
				Text left2 = Text.translatable("diktatura.book.license.left", longTime(e.secondsLeft()));
				ctx.drawTextWithShadow(textRenderer, Ui.fit(textRenderer, e.name(), boxW - textRenderer.getWidth(left2) - 8),
					left, y, 0xFF8FD08F);
				ctx.drawTextWithShadow(textRenderer, left2, left + boxW - textRenderer.getWidth(left2), y,
					e.secondsLeft() < 120 ? 0xFFFF7070 : 0xFFA0A0A0);
				y += 11;
				for (OrderedText line : textRenderer.wrapLines(e.desc(), boxW - 8)) {
					ctx.drawTextWithShadow(textRenderer, line, left + 8, y, 0xD8D8D8);
					y += 10;
				}
				if (!e.laws().isEmpty()) {
					MutableText laws = Text.translatable("diktatura.book.license.laws");
					for (int i = 0; i < e.laws().size(); i++) {
						laws.append(i == 0 ? " " : ", ").append(Text.translatable(Law.translationKey(e.laws().get(i))));
					}
					for (OrderedText line : textRenderer.wrapLines(laws.formatted(Formatting.GRAY), boxW - 8)) {
						ctx.drawTextWithShadow(textRenderer, line, left + 8, y, 0xA0A0A0);
						y += 10;
					}
				}
				y += 6;
			}
		} else {
			List<String> ids = source().stream().filter(this::matches).toList();
			if (ids.isEmpty()) {
				String key = !query.isBlank() ? "diktatura.book.nothing_found"
					: tab == Tab.REPEALED ? "diktatura.book.repealed.empty" : "diktatura.book.empty";
				ctx.drawTextWithShadow(textRenderer, Text.translatable(key), left, y, 0xC0C0C0);
				y += 12;
			}
			int n = 1;
			for (String id : ids) {
				int color = 0xFFFFD27F;
				Text name = Text.literal(n++ + ". ").append(Text.translatable(Law.translationKey(id)));
				if (tab == Tab.REPEALED) {
					color = 0xFFA09070;
				}
				ctx.drawTextWithShadow(textRenderer, Ui.fit(textRenderer, name, boxW - 70), left, y, color);
				y += 11;
				for (OrderedText line : textRenderer.wrapLines(Text.translatable(Law.translationKey(id) + ".desc"), boxW - 8)) {
					ctx.drawTextWithShadow(textRenderer, line, left + 8, y, 0xD8D8D8);
					y += 10;
				}
				y += 6;
			}
		}
		ctx.disableScissor();
		contentHeight = y - start;
	}

	private static String longTime(int seconds) {
		int h = seconds / 3600;
		int m = seconds / 60 % 60;
		int sec = seconds % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int visible = listBottom - listTop;
		int max = Math.max(0, contentHeight - visible);
		scroll = (int) Math.max(0, Math.min(max, scroll - verticalAmount * 12));
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
