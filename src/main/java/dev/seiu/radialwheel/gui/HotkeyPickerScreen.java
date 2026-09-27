package dev.seiu.radialwheel.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joml.Matrix3x2fStack;
import com.mojang.blaze3d.platform.InputConstants;

import dev.seiu.radialwheel.RadialWheelClient;
import dev.seiu.radialwheel.config.Slot;
import dev.seiu.radialwheel.config.WheelConfig;
import dev.seiu.radialwheel.hotkey.HotkeyCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Hotkey picker with a category column on the left and the entries of the selected category on the
 * right.
 *
 * <p>The left column is switched with the mouse wheel (or the arrow keys) when the cursor is over
 * it, the right column scrolls like a normal list. Categories are the vanilla ones plus one group
 * per mod, both for mod key binds and for the Masa (malilib) hotkeys.
 */
public class HotkeyPickerScreen extends Screen {
	private static final int ROW_HEIGHT = 18;
	private static final int SCROLLBAR_WIDTH = 5;
	private static final String SEARCH_PANE_KEY = "__search__";

	private static final int COLOR_PANEL = 0x80000000;
	private static final int COLOR_HOVER = 0x33FFFFFF;
	private static final int COLOR_SELECTED = 0x4433AAFF;
	private static final int COLOR_TEXT = 0xFFE6E6E6;
	private static final int COLOR_TEXT_DIM = 0xFF9BA6B2;
	private static final int COLOR_TEXT_SELECTED = 0xFFFFD86B;
	private static final int COLOR_TEXT_UNBOUND = 0xFF6E7681;
	private static final int COLOR_SCROLL_TRACK = 0x30FFFFFF;
	private static final int COLOR_SCROLL_THUMB = 0x99CCCCCC;

	private final WheelConfig config;
	private final int wheelIndex;
	private final int pageIndex;
	private final int sectorIndex;
	private final List<Pane> panes = new ArrayList<>();

	private String search = "";
	private int selectedPane;
	private double categoryScroll;
	private double entryScroll;
	private boolean initialSelection = true;

	private int categoryLeft;
	private int categoryRight;
	private int leftPaneLeft;
	private int leftPaneRight;
	private int paneTop;
	private int paneBottom;

	/** A category column entry: a name plus the hotkeys it contains. */
	private record Pane(String key, Component name, List<HotkeyCatalog.Entry> entries) {
	}

	public HotkeyPickerScreen(WheelConfig config, int wheelIndex, int pageIndex, int sectorIndex) {
		super(Component.translatable("radialwheel.screen.picker"));
		this.config = config;
		this.wheelIndex = wheelIndex;
		this.pageIndex = pageIndex;
		this.sectorIndex = sectorIndex;
	}

	private Slot currentSlot() {
		dev.seiu.radialwheel.config.Wheel wheel = config.wheel(wheelIndex);

		if (wheel == null) {
			return new Slot();
		}

		return wheel.slot(pageIndex, sectorIndex);
	}

	@Override
	protected void init() {
		clearWidgets();

		int totalWidth = Math.min(420, this.width - 40);
		int left = this.width / 2 - totalWidth / 2;
		int categoryWidth = Math.clamp(totalWidth / 3, 100, 155);
		this.categoryLeft = left;
		this.categoryRight = left + categoryWidth;
		this.leftPaneLeft = this.categoryRight + 5;
		this.leftPaneRight = left + totalWidth;
		this.paneTop = 50;
		this.paneBottom = this.height - 52;

		EditBox searchBox = new EditBox(this.font, this.width / 2 - 110, 26, 220, 18,
				Component.translatable("radialwheel.picker.search"));
		searchBox.setMaxLength(64);
		searchBox.setValue(search);
		searchBox.setHint(Component.translatable("radialwheel.picker.search.hint"));
		searchBox.setResponder(value -> {
			if (!value.equals(search)) {
				search = value;
				rebuildPanes();
			}
		});
		addRenderableWidget(searchBox);

		int footer = this.height - 26;
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> back())
				.bounds(categoryLeft, footer, 70, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("radialwheel.picker.clear"), button -> {
			HotkeyCatalog.clear(currentSlot());
			back();
		}).bounds(categoryLeft + 74, footer, 70, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("radialwheel.picker.refresh"), button -> {
			HotkeyCatalog.invalidate();
			rebuildPanes();
		}).bounds(categoryLeft + 148, footer, 70, 20).build());

		rebuildPanes();
	}

	private void rebuildPanes() {
		panes.clear();
		String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<HotkeyCatalog.Category> categories = HotkeyCatalog.categories();

		if (needle.isEmpty()) {
			for (HotkeyCatalog.Category category : categories) {
				panes.add(new Pane(category.key(), category.name(), category.entries()));
			}
		} else {
			List<HotkeyCatalog.Entry> matches = new ArrayList<>();

			for (HotkeyCatalog.Category category : categories) {
				List<HotkeyCatalog.Entry> hits = new ArrayList<>();

				for (HotkeyCatalog.Entry entry : category.entries()) {
					if (entry.searchText().contains(needle)) {
						hits.add(entry);
						matches.add(entry);
					}
				}

				if (!hits.isEmpty()) {
					panes.add(new Pane(category.key(), category.name(), hits));
				}
			}

			if (!matches.isEmpty()) {
				panes.add(0, new Pane(SEARCH_PANE_KEY,
						Component.translatable("radialwheel.picker.all_matches", matches.size()), matches));
			}
		}

		if (panes.isEmpty()) {
			selectedPane = 0;
		} else if (initialSelection && needle.isEmpty()) {
			initialSelection = false;
			HotkeyCatalog.Entry current = HotkeyCatalog.current(currentSlot());
			selectedPane = 0;

			if (current != null) {
				for (int index = 0; index < panes.size(); index++) {
					if (panes.get(index).entries().contains(current)) {
						selectedPane = index;
						break;
					}
				}
			}

			scrollToSelectedEntry();
		} else {
			selectedPane = Math.clamp(selectedPane, 0, panes.size() - 1);
			entryScroll = 0;
		}

		clampScroll();
	}

	private void back() {
		RadialWheelClient.open(Minecraft.getInstance(),
				new WheelSettingsScreen(config, wheelIndex, sectorIndex));
	}

	@Override
	public void onClose() {
		back();
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	// ------------------------------------------------------------------ geometry

	private Pane selected() {
		return panes.isEmpty() ? null : panes.get(Math.clamp(selectedPane, 0, panes.size() - 1));
	}

	private List<HotkeyCatalog.Entry> selectedEntries() {
		Pane pane = selected();
		return pane == null ? List.of() : pane.entries();
	}

	private int categoryIndexAt(double mouseX, double mouseY) {
		if (mouseX < categoryLeft || mouseX > categoryRight || mouseY < paneTop || mouseY > paneBottom) {
			return -1;
		}

		int index = (int) ((mouseY - paneTop + categoryScroll) / ROW_HEIGHT);
		return index >= 0 && index < panes.size() ? index : -1;
	}

	private int entryIndexAt(double mouseX, double mouseY) {
		if (mouseX < leftPaneLeft || mouseX > leftPaneRight || mouseY < paneTop || mouseY > paneBottom) {
			return -1;
		}

		int index = (int) ((mouseY - paneTop + entryScroll) / ROW_HEIGHT);
		return index >= 0 && index < selectedEntries().size() ? index : -1;
	}

	private void clampScroll() {
		double visible = Math.max(1, paneBottom - paneTop);
		categoryScroll = Math.clamp(categoryScroll, 0.0, Math.max(0.0, panes.size() * (double) ROW_HEIGHT - visible));
		entryScroll = Math.clamp(entryScroll, 0.0,
				Math.max(0.0, selectedEntries().size() * (double) ROW_HEIGHT - visible));
	}

	private void scrollToSelectedEntry() {
		HotkeyCatalog.Entry current = HotkeyCatalog.current(currentSlot());

		if (current == null) {
			return;
		}

		List<HotkeyCatalog.Entry> entries = selectedEntries();
		int index = entries.indexOf(current);

		if (index >= 0) {
			double visible = Math.max(1, paneBottom - paneTop);
			entryScroll = Math.clamp(index * (double) ROW_HEIGHT - visible / 2, 0.0,
					Math.max(0.0, entries.size() * (double) ROW_HEIGHT - visible));
		}
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() == 0) {
			int categoryIndex = categoryIndexAt(event.x(), event.y());

			if (categoryIndex >= 0) {
				selectedPane = categoryIndex;
				entryScroll = 0;
				clampScroll();
				return true;
			}

			int entryIndex = entryIndexAt(event.x(), event.y());

			if (entryIndex >= 0) {
				HotkeyCatalog.apply(currentSlot(), selectedEntries().get(entryIndex));
				back();
				return true;
			}
		}

		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseX >= categoryLeft && mouseX <= categoryRight && mouseY >= paneTop && mouseY <= paneBottom) {
			// over the category column: the wheel switches categories
			if (!panes.isEmpty()) {
				selectedPane = Math.clamp(selectedPane - (int) Math.signum(vertical), 0, panes.size() - 1);
				entryScroll = 0;
				scrollCategoryIntoView();
				clampScroll();
			}

			return true;
		}

		if (mouseX >= leftPaneLeft && mouseX <= leftPaneRight && mouseY >= paneTop && mouseY <= paneBottom) {
			entryScroll -= vertical * ROW_HEIGHT * 2;
			clampScroll();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	private void scrollCategoryIntoView() {
		double visible = Math.max(1, paneBottom - paneTop);
		double top = selectedPane * (double) ROW_HEIGHT;

		if (top < categoryScroll) {
			categoryScroll = top;
		} else if (top + ROW_HEIGHT > categoryScroll + visible) {
			categoryScroll = top + ROW_HEIGHT - visible;
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();

		if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) {
			if (!panes.isEmpty()) {
				int step = key == InputConstants.KEY_LEFT ? -1 : 1;
				selectedPane = Math.clamp(selectedPane + step, 0, panes.size() - 1);
				entryScroll = 0;
				scrollCategoryIntoView();
				clampScroll();
			}

			return true;
		}

		if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
			entryScroll += (key == InputConstants.KEY_UP ? -1 : 1) * ROW_HEIGHT;
			clampScroll();
			return true;
		}

		return super.keyPressed(event);
	}

	// ------------------------------------------------------------------ text

	private static final float TEXT_SCALE = 0.74F;

	private int textWidth(String value) {
		return Math.round(this.font.width(value) * TEXT_SCALE);
	}

	private int textWidth(Component value) {
		return Math.round(this.font.width(value) * TEXT_SCALE);
	}

	/** Truncates text so that it still fits once scaled down. */
	private String truncate(String value, int maxWidth) {
		return this.font.plainSubstrByWidth(value, Math.max(8, Math.round(maxWidth / TEXT_SCALE)));
	}

	private void text(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(TEXT_SCALE, TEXT_SCALE);
		graphics.text(this.font, value, 0, 0, color);
		pose.popMatrix();
	}

	private void text(GuiGraphicsExtractor graphics, Component value, int x, int y, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(TEXT_SCALE, TEXT_SCALE);
		graphics.text(this.font, value, 0, 0, color);
		pose.popMatrix();
	}

	private void centeredText(GuiGraphicsExtractor graphics, Component value, int centerX, int y, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX, y);
		pose.scale(TEXT_SCALE, TEXT_SCALE);
		graphics.centeredText(this.font, value, 0, 0, color);
		pose.popMatrix();
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		centeredText(graphics, this.title, this.width / 2, 8, 0xFFFFFFFF);
		graphics.fill(categoryLeft, paneTop, categoryRight, paneBottom, COLOR_PANEL);
		graphics.fill(leftPaneLeft, paneTop, leftPaneRight, paneBottom, COLOR_PANEL);

		int categoryHover = categoryIndexAt(mouseX, mouseY);
		int entryHover = entryIndexAt(mouseX, mouseY);
		HotkeyCatalog.Entry selectedEntry = HotkeyCatalog.current(currentSlot());

		graphics.enableScissor(categoryLeft, paneTop, categoryRight, paneBottom);
		int y = paneTop - (int) categoryScroll;

		for (int index = 0; index < panes.size(); index++) {
			if (y + ROW_HEIGHT >= paneTop && y <= paneBottom) {
				drawCategory(graphics, panes.get(index), y, index == selectedPane, index == categoryHover);
			}

			y += ROW_HEIGHT;
		}

		graphics.disableScissor();
		drawScrollbar(graphics, categoryLeft, categoryRight, categoryScroll, panes.size());

		graphics.enableScissor(leftPaneLeft, paneTop, leftPaneRight, paneBottom);
		y = paneTop - (int) entryScroll;
		List<HotkeyCatalog.Entry> entries = selectedEntries();

		for (int index = 0; index < entries.size(); index++) {
			if (y + ROW_HEIGHT >= paneTop && y <= paneBottom) {
				drawEntry(graphics, entries.get(index), y, index == entryHover, entries.get(index) == selectedEntry);
			}

			y += ROW_HEIGHT;
		}

		graphics.disableScissor();
		drawScrollbar(graphics, leftPaneLeft, leftPaneRight, entryScroll, entries.size());

		if (panes.isEmpty()) {
			centeredText(graphics, Component.translatable("radialwheel.picker.empty"),
					(leftPaneLeft + leftPaneRight) / 2, (paneTop + paneBottom) / 2, COLOR_TEXT_DIM);
		}

		Component count = Component.translatable("radialwheel.picker.count", entries.size());
		text(graphics, count, leftPaneRight - textWidth(count), this.height - 44, COLOR_TEXT_DIM);
		text(graphics, Component.translatable("radialwheel.picker.hint"),
				categoryLeft, this.height - 44, COLOR_TEXT_UNBOUND);
	}

	private void drawCategory(GuiGraphicsExtractor graphics, Pane pane, int y, boolean selected, boolean hovered) {
		if (selected) {
			graphics.fill(categoryLeft, y, categoryRight, y + ROW_HEIGHT, COLOR_SELECTED);
		} else if (hovered) {
			graphics.fill(categoryLeft, y, categoryRight, y + ROW_HEIGHT, COLOR_HOVER);
		}

		String label = pane.name().getString() + " (" + pane.entries().size() + ")";
		label = truncate(label, categoryRight - categoryLeft - 8);
		text(graphics, label, categoryLeft + 4, y + 5,
				selected ? COLOR_TEXT_SELECTED : (hovered ? 0xFFFFFFFF : COLOR_TEXT));
	}

	private void drawEntry(GuiGraphicsExtractor graphics, HotkeyCatalog.Entry entry, int y, boolean hovered,
			boolean selected) {
		if (selected) {
			graphics.fill(leftPaneLeft, y, leftPaneRight, y + ROW_HEIGHT, COLOR_SELECTED);
		} else if (hovered) {
			graphics.fill(leftPaneLeft, y, leftPaneRight, y + ROW_HEIGHT, COLOR_HOVER);
		}

		String unboundText = Component.translatable("radialwheel.picker.unbound").getString();
		boolean unbound = entry.boundKey().isEmpty() || entry.boundKey().equals(unboundText);
		String bound = unbound ? unboundText : entry.boundKey();
		int boundWidth = textWidth(bound);

		String name = truncate(entry.name().getString(), leftPaneRight - leftPaneLeft - boundWidth - 20);
		int textColor = selected ? COLOR_TEXT_SELECTED : (hovered ? 0xFFFFFFFF : COLOR_TEXT);
		text(graphics, name, leftPaneLeft + 8, y + 5, textColor);
		text(graphics, bound, leftPaneRight - 8 - boundWidth, y + 5,
				unbound ? COLOR_TEXT_UNBOUND : (selected ? COLOR_TEXT_SELECTED : COLOR_TEXT_DIM));
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics, int left, int right, double scroll, int rows) {
		double content = rows * (double) ROW_HEIGHT;
		double visible = Math.max(1, paneBottom - paneTop);

		if (content <= visible) {
			return;
		}

		int trackX = right - SCROLLBAR_WIDTH - 2;
		graphics.fill(trackX, paneTop, trackX + SCROLLBAR_WIDTH, paneBottom, COLOR_SCROLL_TRACK);
		int thumbHeight = Math.max(12, (int) (visible * (visible / content)));
		int thumbY = paneTop + (int) ((paneBottom - paneTop - thumbHeight) * (scroll / (content - visible)));
		graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, COLOR_SCROLL_THUMB);
	}
}
