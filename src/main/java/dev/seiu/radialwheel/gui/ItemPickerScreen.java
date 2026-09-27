package dev.seiu.radialwheel.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix3x2fStack;
import com.mojang.blaze3d.platform.InputConstants;

import dev.seiu.radialwheel.RadialWheelClient;
import dev.seiu.radialwheel.config.Slot;
import dev.seiu.radialwheel.config.WheelConfig;
import dev.seiu.radialwheel.hotkey.ItemIcons;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Icon picker: every registered item, grouped by the mod that adds it, with the item icon drawn in
 * front of the name.
 */
public class ItemPickerScreen extends Screen {
	private static final int ROW_HEIGHT = 18;
	private static final int SCROLLBAR_WIDTH = 5;

	private static final int COLOR_PANEL = 0x80000000;
	private static final int COLOR_HOVER = 0x33FFFFFF;
	private static final int COLOR_SELECTED = 0x4433AAFF;
	private static final int COLOR_TEXT = 0xFFE6E6E6;
	private static final int COLOR_TEXT_DIM = 0xFF9BA6B2;
	private static final int COLOR_TEXT_SELECTED = 0xFFFFD86B;
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

	private int categoryLeft;
	private int categoryRight;
	private int itemLeft;
	private int itemRight;
	private int paneTop;
	private int paneBottom;

	/** One item of the list. */
	private record ItemEntry(Identifier id, Component name, String searchText) {
	}

	/** A namespace group with its items. */
	private record Pane(String key, Component name, List<ItemEntry> entries) {
	}

	public ItemPickerScreen(WheelConfig config, int wheelIndex, int pageIndex, int sectorIndex) {
		super(Component.translatable("radialwheel.screen.item_picker"));
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

		int totalWidth = Math.min(430, this.width - 40);
		int left = this.width / 2 - totalWidth / 2;
		int categoryWidth = Math.clamp(totalWidth / 3, 100, 155);
		this.categoryLeft = left;
		this.categoryRight = left + categoryWidth;
		this.itemLeft = this.categoryRight + 5;
		this.itemRight = left + totalWidth;
		this.paneTop = 50;
		this.paneBottom = this.height - 52;

		EditBox searchBox = new EditBox(this.font, this.width / 2 - 110, 26, 220, 18,
				Component.translatable("radialwheel.picker.search"));
		searchBox.setMaxLength(64);
		searchBox.setValue(search);
		searchBox.setHint(Component.translatable("radialwheel.item.search.hint"));
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
			currentSlot().icon = "";
			back();
		}).bounds(categoryLeft + 74, footer, 70, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("radialwheel.settings.icon.held"), button -> {
			String held = ItemIcons.heldItemId();

			if (!held.isEmpty()) {
				currentSlot().icon = held;
				back();
			}
		}).bounds(categoryLeft + 148, footer, 90, 20).build());

		rebuildPanes();
	}

	private void rebuildPanes() {
		panes.clear();
		String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		Map<String, List<ItemEntry>> byNamespace = new HashMap<>();

		for (Identifier id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
			Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);

			if (item == null || item == net.minecraft.world.item.Items.AIR) {
				continue;
			}

			Component name = item.getName(item.getDefaultInstance());
			String searchText = (id + " " + name.getString()).toLowerCase(Locale.ROOT);

			if (!needle.isEmpty() && !searchText.contains(needle)) {
				continue;
			}

			byNamespace.computeIfAbsent(id.getNamespace(), key -> new ArrayList<>())
					.add(new ItemEntry(id, name, searchText));
		}

		Set<String> namespaces = new HashSet<>(byNamespace.keySet());
		namespaces.add("minecraft");

		for (String namespace : namespaces) {
			List<ItemEntry> entries = byNamespace.getOrDefault(namespace, List.of());

			if (entries.isEmpty()) {
				continue;
			}

			panes.add(new Pane(namespace, Component.literal(modDisplayName(namespace)), entries));
		}

		panes.sort(Comparator
				.comparing((Pane pane) -> !"minecraft".equals(pane.key()))
				.thenComparing(pane -> pane.name().getString()));

		for (Pane pane : panes) {
			pane.entries().sort(Comparator.comparing(entry -> entry.name().getString()));
		}

		selectedPane = Math.clamp(selectedPane, 0, Math.max(0, panes.size() - 1));
		entryScroll = 0;
		clampScroll();
	}

	private static String modDisplayName(String namespace) {
		if ("minecraft".equals(namespace)) {
			return Component.translatable("radialwheel.item.vanilla").getString();
		}

		try {
			return FabricLoader.getInstance().getModContainer(namespace)
					.map(container -> container.getMetadata().getName())
					.orElse(namespace);
		} catch (Throwable throwable) {
			return namespace;
		}
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

	private List<ItemEntry> selectedEntries() {
		if (panes.isEmpty()) {
			return List.of();
		}

		return panes.get(Math.clamp(selectedPane, 0, panes.size() - 1)).entries();
	}

	private int categoryIndexAt(double mouseX, double mouseY) {
		if (mouseX < categoryLeft || mouseX > categoryRight || mouseY < paneTop || mouseY > paneBottom) {
			return -1;
		}

		int index = (int) ((mouseY - paneTop + categoryScroll) / ROW_HEIGHT);
		return index >= 0 && index < panes.size() ? index : -1;
	}

	private int itemIndexAt(double mouseX, double mouseY) {
		if (mouseX < itemLeft || mouseX > itemRight || mouseY < paneTop || mouseY > paneBottom) {
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

			int itemIndex = itemIndexAt(event.x(), event.y());

			if (itemIndex >= 0) {
				currentSlot().icon = selectedEntries().get(itemIndex).id().toString();
				back();
				return true;
			}
		}

		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseX >= categoryLeft && mouseX <= categoryRight && mouseY >= paneTop && mouseY <= paneBottom) {
			if (!panes.isEmpty()) {
				selectedPane = Math.clamp(selectedPane - (int) Math.signum(vertical), 0, panes.size() - 1);
				entryScroll = 0;
				clampScroll();
			}

			return true;
		}

		if (mouseX >= itemLeft && mouseX <= itemRight && mouseY >= paneTop && mouseY <= paneBottom) {
			entryScroll -= vertical * ROW_HEIGHT * 2;
			clampScroll();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_LEFT || event.key() == InputConstants.KEY_RIGHT) {
			if (!panes.isEmpty()) {
				int step = event.key() == InputConstants.KEY_LEFT ? -1 : 1;
				selectedPane = Math.clamp(selectedPane + step, 0, panes.size() - 1);
				entryScroll = 0;
				clampScroll();
			}

			return true;
		}

		return super.keyPressed(event);
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		centeredText(graphics, this.title, this.width / 2, 8, 0xFFFFFFFF);
		graphics.fill(categoryLeft, paneTop, categoryRight, paneBottom, COLOR_PANEL);
		graphics.fill(itemLeft, paneTop, itemRight, paneBottom, COLOR_PANEL);

		int categoryHover = categoryIndexAt(mouseX, mouseY);
		int itemHover = itemIndexAt(mouseX, mouseY);
		String currentIcon = currentSlot().icon == null ? "" : currentSlot().icon;

		graphics.enableScissor(categoryLeft, paneTop, categoryRight, paneBottom);
		int y = paneTop - (int) categoryScroll;

		for (int index = 0; index < panes.size(); index++) {
			if (y + ROW_HEIGHT >= paneTop && y <= paneBottom) {
				Pane pane = panes.get(index);
				boolean selected = index == selectedPane;

				if (selected) {
					graphics.fill(categoryLeft, y, categoryRight, y + ROW_HEIGHT, COLOR_SELECTED);
				} else if (index == categoryHover) {
					graphics.fill(categoryLeft, y, categoryRight, y + ROW_HEIGHT, COLOR_HOVER);
				}

				String label = truncate(
						pane.name().getString() + " (" + pane.entries().size() + ")", categoryRight - categoryLeft - 8);
				text(graphics, label, categoryLeft + 4, y + 5,
						selected ? COLOR_TEXT_SELECTED : COLOR_TEXT);
			}

			y += ROW_HEIGHT;
		}

		graphics.disableScissor();
		drawScrollbar(graphics, categoryLeft, categoryRight, categoryScroll, panes.size());

		List<ItemEntry> entries = selectedEntries();
		graphics.enableScissor(itemLeft, paneTop, itemRight, paneBottom);
		y = paneTop - (int) entryScroll;

		for (int index = 0; index < entries.size(); index++) {
			if (y + ROW_HEIGHT >= paneTop && y <= paneBottom) {
				ItemEntry entry = entries.get(index);
				boolean isCurrent = entry.id().toString().equals(currentIcon);
				boolean hovered = index == itemHover;

				if (isCurrent) {
					graphics.fill(itemLeft, y, itemRight, y + ROW_HEIGHT, COLOR_SELECTED);
				} else if (hovered) {
					graphics.fill(itemLeft, y, itemRight, y + ROW_HEIGHT, COLOR_HOVER);
				}

				ItemStack stack = entry.id() == null ? ItemStack.EMPTY
						: ItemIcons.stack(entry.id().toString());

				if (!stack.isEmpty()) {
					graphics.item(stack, itemLeft + 4, y + 1);
				}

				String name = truncate(entry.name().getString(), itemRight - itemLeft - 30);
				text(graphics, name, itemLeft + 24, y + 5,
						isCurrent ? COLOR_TEXT_SELECTED : (hovered ? 0xFFFFFFFF : COLOR_TEXT));
			}

			y += ROW_HEIGHT;
		}

		graphics.disableScissor();
		drawScrollbar(graphics, itemLeft, itemRight, entryScroll, entries.size());

		if (panes.isEmpty()) {
			centeredText(graphics, Component.translatable("radialwheel.item.empty"),
					(itemLeft + itemRight) / 2, (paneTop + paneBottom) / 2, COLOR_TEXT_DIM);
		}

		Component count = Component.translatable("radialwheel.item.count", entries.size());
		text(graphics, count, itemRight - textWidth(count), this.height - 44, COLOR_TEXT_DIM);
	}

	// ------------------------------------------------------------------ text

	private static final float TEXT_SCALE = 0.74F;

	private int textWidth(String value) {
		return Math.round(this.font.width(value) * TEXT_SCALE);
	}

	private int textWidth(Component value) {
		return Math.round(this.font.width(value) * TEXT_SCALE);
	}

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
