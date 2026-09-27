package dev.seiu.radialwheel.gui;

import org.joml.Matrix3x2fStack;
import com.mojang.blaze3d.platform.InputConstants;

import dev.seiu.radialwheel.RadialWheelClient;
import dev.seiu.radialwheel.action.RawInput;
import dev.seiu.radialwheel.config.Page;
import dev.seiu.radialwheel.config.Slot;
import dev.seiu.radialwheel.config.SlotType;
import dev.seiu.radialwheel.config.Wheel;
import dev.seiu.radialwheel.config.WheelConfig;
import dev.seiu.radialwheel.hotkey.HotkeyCatalog;
import dev.seiu.radialwheel.hotkey.ItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Editor for the wheels.
 *
 * <p>Three tabs: the sector editor (with wheel and page navigation), the look and behaviour of the
 * current wheel, and the list of wheels.
 */
public class WheelSettingsScreen extends Screen {

	// row indices, kept in one place so the labels always line up with the widgets
	private static final int ROW_SECTOR = 0;
	private static final int ROW_TYPE = 1;
	private static final int ROW_TARGET = 2;
	private static final int ROW_LABEL = 3;
	private static final int ROW_ICON = 4;
	private static final int ROW_ICON_BUTTONS = 5;
	private static final int ROW_RAW_INJECT = 6;
	private static final int ROW_HOLD = 7;
	private static final int ROW_MODIFIERS = 8;

	// rows of the middle column (pages / trigger)
	private static final int ROW_PAGE = 0;
	private static final int ROW_PAGE_NAME = 1;
	private static final int ROW_PAGE_BUTTONS = 2;
	private static final int ROW_TRIGGER = 3;

	// rows of the look column
	private static final int ROW_SHOW_LABELS = 0;
	private static final int ROW_ICON_MODE = 1;
	private static final int ROW_FEEDBACK = 2;
	private static final int ROW_CLICK = 3;
	private static final int ROW_FONT_FOLLOW = 4;
	private static final int ROW_FONT_SCALE = 5;
	private static final int ROW_SIZE = 6;
	private static final int ROW_POS_X = 7;
	private static final int ROW_POS_Y = 8;
	private static final int ROW_DEAD_ZONE = 9;
	private static final int ROW_SECTORS = 10;
	/** Rows the look column needs, used to keep everything inside its panel. */
	private static final int LOOK_ROWS = 11;

	/** The settings screen draws its text a bit smaller than vanilla. */
	private static final float TEXT_SCALE = 0.72F;
	/** Height of the text fields, smaller than a full row. */
	private static final int FIELD_HEIGHT = 14;
	/** Labels are right aligned against this column, values start right after it. */
	private static final int LABEL_WIDTH = 68;
	/** Width of a step button. */
	private static final int STEP_WIDTH = 22;

	private static final int COLOR_LABEL = 0xFFB8C4D0;
	private static final int COLOR_VALUE = 0xFFFFF0A0;
	private static final int COLOR_TITLE = 0xFFFFFFFF;
	private static final int COLOR_WARN = 0xFFFF7070;
	private static final int COLOR_HINT = 0xFF7C8794;
	private static final int COLOR_PANEL = 0x66101010;
	private static final int COLOR_PANEL_EDGE = 0x40FFFFFF;
	private static final int COLOR_PANEL_HEADER = 0x30FFFFFF;
	private static final int COLOR_ROW_BAND = 0x14FFFFFF;

	private final WheelConfig config;
	private int wheelIndex;
	private int pageIndex;
	private int sectorIndex;
	private int rowHeight;
	private boolean awaitingTrigger;

	private int left;
	private int middle;
	private int right;
	private int columnWidth;
	private int panelTop;
	private int panelBottom;
	private int top;

	/** Left edge of the widgets of a column (the labels are right aligned against it). */
	private int widgetX(int columnX) {
		return columnX + LABEL_WIDTH;
	}

	private int widgetWidth() {
		return columnWidth - LABEL_WIDTH;
	}

	private int stepMinusX(int columnX) {
		return columnX + LABEL_WIDTH;
	}

	private int stepPlusX(int columnX) {
		return columnX + columnWidth - STEP_WIDTH;
	}

	private int stepValueX(int columnX) {
		return (stepMinusX(columnX) + STEP_WIDTH + stepPlusX(columnX)) / 2;
	}

	/** Command completion for the command field, same component as the vanilla command block. */
	private CommandSuggestions commandSuggestions;
	private Slot hoveredSlotCache;

	public WheelSettingsScreen(WheelConfig config, int wheelIndex, int sectorIndex) {
		super(Component.translatable("radialwheel.screen.settings"));
		this.config = config;
		this.wheelIndex = Math.clamp(wheelIndex, 0, Math.max(0, config.wheelCount() - 1));
		this.sectorIndex = Math.max(0, sectorIndex);
	}

	private Wheel wheel() {
		return config.wheel(wheelIndex);
	}

	private Page page() {
		Wheel wheel = wheel();

		if (wheel == null) {
			return null;
		}

		pageIndex = Math.clamp(pageIndex, 0, Math.max(0, wheel.pageCount() - 1));
		return wheel.page(pageIndex);
	}

	private Slot currentSlot() {
		Wheel wheel = wheel();

		if (wheel == null) {
			return new Slot();
		}

		sectorIndex = Math.clamp(sectorIndex, 0, wheel.sectorCount - 1);
		return wheel.slot(pageIndex, sectorIndex);
	}

	@Override
	protected void init() {
		clearWidgets();
		this.commandSuggestions = null;
		this.columnWidth = Math.min(224, (this.width - 48) / 3);
		int total = columnWidth * 3 + 16;
		this.left = (this.width - total) / 2;
		this.middle = left + columnWidth + 8;
		this.right = middle + columnWidth + 8;
		this.panelTop = 34;
		this.panelBottom = this.height - 34;
		// keep the tallest column inside its panel
		this.rowHeight = Math.max(12,
				Math.min(22, (panelBottom - panelTop - 26) / LOOK_ROWS));
		this.top = panelTop + 24;

		Wheel wheel = wheel();

		if (wheel == null) {
			return;
		}

		initSectorTab(wheel);
		initLookTab(wheel);

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
			config.save();
			onClose();
		}).bounds(this.width / 2 - 100, this.height - 26, 200, 20).build());
	}

	private void refresh() {
		clearWidgets();
		init();
	}

	// ------------------------------------------------------------------ sector tab

	private void initSectorTab(Wheel wheel) {
		Slot slot = currentSlot();

		// ---- left column: the selected sector
		addRenderableWidget(Button.builder(Component.literal("<"), button -> {
			sectorIndex = (sectorIndex - 1 + wheel.sectorCount) % wheel.sectorCount;
			refresh();
		}).bounds(left, top, 20, rowHeight).build());
		addRenderableWidget(Button.builder(Component.literal(">"), button -> {
			sectorIndex = (sectorIndex + 1) % wheel.sectorCount;
			refresh();
		}).bounds(left + columnWidth - 20, top, 20, rowHeight).build());

		addRenderableWidget(Button.builder(Component.translatable(slot.type.migrated().translationKey), button -> {
			slot.type = slot.type.next();
			refresh();
		}).bounds(widgetX(left), top + rowHeight, widgetWidth(), rowHeight).build());

		int targetRow = top + rowHeight * 2 + (rowHeight - FIELD_HEIGHT) / 2;

		switch (slot.type.migrated()) {
			case HOTKEY -> addRenderableWidget(Button.builder(HotkeyCatalog.describe(slot), button ->
					RadialWheelClient.open(Minecraft.getInstance(),
							new HotkeyPickerScreen(config, wheelIndex, pageIndex, sectorIndex)))
					.bounds(widgetX(left), top + rowHeight * 2, widgetWidth(), rowHeight).build());
			case COMMAND -> {
				EditBox command = new EditBox(this.font, widgetX(left), targetRow, widgetWidth(), FIELD_HEIGHT,
						Component.translatable("radialwheel.settings.command"));
				command.setMaxLength(256);
				command.setValue(slot.command);
				command.setHint(Component.translatable("radialwheel.settings.command.hint"));
				command.setResponder(value -> {
					if (!value.equals(slot.command)) {
						slot.command = value;
					}

					if (this.commandSuggestions != null) {
						this.commandSuggestions.updateCommandInfo();
					}
				});
				addRenderableWidget(command);
				setInitialFocus(command);

				if (this.minecraft.player != null && this.minecraft.getConnection() != null) {
					this.commandSuggestions = new CommandSuggestions(this.minecraft, this, command, this.font,
							true, false, 0, 7, false, 0xD0000000);
					this.commandSuggestions.setAllowSuggestions(true);
					this.commandSuggestions.updateCommandInfo();
				}
			}
			case RAW_INPUT -> {
				int half = (widgetWidth() - 4) / 2;
				EditBox key = new EditBox(this.font, widgetX(left), targetRow, half, FIELD_HEIGHT,
						Component.translatable("radialwheel.settings.raw_key"));
				key.setMaxLength(32);
				key.setValue(slot.rawKey);
				key.setHint(Component.translatable("radialwheel.settings.raw_key.hint"));
				key.setResponder(value -> slot.rawKey = value);
				addRenderableWidget(key);

				EditBox mouse = new EditBox(this.font, widgetX(left) + half + 4, targetRow,
						half, FIELD_HEIGHT, Component.translatable("radialwheel.settings.raw_mouse"));
				mouse.setMaxLength(8);
				mouse.setValue(slot.rawMouse);
				mouse.setHint(Component.translatable("radialwheel.settings.raw_mouse.hint"));
				mouse.setResponder(value -> slot.rawMouse = value);
				addRenderableWidget(mouse);
			}
			case EMPTY -> {
			}
		}

		EditBox label = new EditBox(this.font, widgetX(left),
				top + rowHeight * ROW_LABEL + (rowHeight - FIELD_HEIGHT) / 2, widgetWidth(), FIELD_HEIGHT,
				Component.translatable("radialwheel.settings.label"));
		label.setMaxLength(48);
		label.setValue(slot.label);
		label.setResponder(value -> {
			if (!value.equals(slot.label)) {
				slot.label = value;
				slot.labelAuto = false;
			}
		});
		addRenderableWidget(label);

		int iconRow = top + rowHeight * ROW_ICON + (rowHeight - FIELD_HEIGHT) / 2;
		EditBox icon = new EditBox(this.font, widgetX(left), iconRow, widgetWidth() - 62, FIELD_HEIGHT,
				Component.translatable("radialwheel.settings.icon"));
		icon.setMaxLength(96);
		icon.setValue(slot.icon);
		icon.setHint(Component.translatable("radialwheel.settings.icon.hint"));
		icon.setResponder(value -> slot.icon = value);
		addRenderableWidget(icon);
		addRenderableWidget(Button.builder(Component.translatable("radialwheel.settings.icon.pick"), button ->
				RadialWheelClient.open(Minecraft.getInstance(),
						new ItemPickerScreen(config, wheelIndex, pageIndex, sectorIndex)))
				.bounds(left + columnWidth - 60, iconRow - (rowHeight - FIELD_HEIGHT) / 2, 60, rowHeight).build());
		addRenderableWidget(Button.builder(Component.translatable("radialwheel.settings.icon.held"), button -> {
			String held = ItemIcons.heldItemId();

			if (!held.isEmpty()) {
				slot.icon = held;
				refresh();
			}
		}).bounds(left + columnWidth - 60, top + rowHeight * ROW_ICON_BUTTONS, 60, rowHeight).build());

		addRenderableWidget(Button.builder(onOff(slot.rawInject), button -> {
			slot.rawInject = !slot.rawInject;
			refresh();
		}).bounds(widgetX(left), top + rowHeight * ROW_RAW_INJECT, widgetWidth(), rowHeight).build());

		int holdRow = top + rowHeight * ROW_HOLD;
		addRenderableWidget(Button.builder(Component.literal("-"), button -> {
			slot.holdTicks = Math.max(1, slot.holdTicks - 1);
			refresh();
		}).bounds(stepMinusX(left), holdRow, STEP_WIDTH, rowHeight).build());
		addRenderableWidget(Button.builder(Component.literal("+"), button -> {
			slot.holdTicks = Math.min(40, slot.holdTicks + 1);
			refresh();
		}).bounds(stepPlusX(left), holdRow, STEP_WIDTH, rowHeight).build());

		int modifierRow = top + rowHeight * ROW_MODIFIERS;
		int modifierWidth = (widgetWidth() - 8) / 3;
		addRenderableWidget(Button.builder(modifierLabel("Shift", slot.shift), button -> {
			slot.shift = !slot.shift;
			refresh();
		}).bounds(widgetX(left), modifierRow, modifierWidth, rowHeight).build());
		addRenderableWidget(Button.builder(modifierLabel("Ctrl", slot.ctrl), button -> {
			slot.ctrl = !slot.ctrl;
			refresh();
		}).bounds(widgetX(left) + modifierWidth + 4, modifierRow, modifierWidth, rowHeight).build());
		addRenderableWidget(Button.builder(modifierLabel("Alt", slot.alt), button -> {
			slot.alt = !slot.alt;
			refresh();
		}).bounds(widgetX(left) + (modifierWidth + 4) * 2, modifierRow, modifierWidth, rowHeight).build());

		// ---- middle column: pages and the trigger key
		addRenderableWidget(Button.builder(Component.literal("<"), button -> {
			switchPage(-1);
		}).bounds(middle, top + rowHeight * ROW_PAGE, 20, rowHeight).build());
		addRenderableWidget(Button.builder(Component.literal(">"), button -> {
			switchPage(1);
		}).bounds(middle + columnWidth - 20, top + rowHeight * ROW_PAGE, 20, rowHeight).build());

		Page page = page();
		EditBox pageName = new EditBox(this.font, widgetX(middle),
				top + rowHeight * ROW_PAGE_NAME + (rowHeight - FIELD_HEIGHT) / 2, widgetWidth(), FIELD_HEIGHT,
				Component.translatable("radialwheel.settings.page_name"));
		pageName.setMaxLength(32);
		pageName.setValue(page == null ? "" : page.name);
		pageName.setHint(Component.translatable("radialwheel.settings.page_name.hint"));
		pageName.setResponder(value -> {
			Page current = page();

			if (current != null) {
				current.name = value;
			}
		});
		addRenderableWidget(pageName);

		addRenderableWidget(Button.builder(Component.translatable("radialwheel.settings.page_add"), button -> {
			Wheel current = wheel();
			current.pages.add(new Page());
			pageIndex = current.pageCount() - 1;
			refresh();
		}).bounds(widgetX(middle), top + rowHeight * ROW_PAGE_BUTTONS, (widgetWidth() - 4) / 2, rowHeight).build());
		addRenderableWidget(Button.builder(Component.translatable("radialwheel.settings.page_remove"), button -> {
			Wheel current = wheel();

			if (current.pageCount() > 1) {
				current.pages.remove(pageIndex);
				pageIndex = Math.max(0, pageIndex - 1);
				refresh();
			}
		}).bounds(widgetX(middle) + (widgetWidth() + 4) / 2, top + rowHeight * ROW_PAGE_BUTTONS, (widgetWidth() - 4) / 2,
				rowHeight).build());

		addRenderableWidget(Button.builder(triggerLabel(), button -> {
			awaitingTrigger = true;
			refresh();
		}).bounds(widgetX(middle), top + rowHeight * ROW_TRIGGER, widgetWidth(), rowHeight).build());
	}

	private void switchPage(int step) {
		Wheel wheel = wheel();
		pageIndex = Math.floorMod(pageIndex + step, Math.max(1, wheel.pageCount()));
		refresh();
	}

	// ------------------------------------------------------------------ look & behaviour column

	/** Appearance and behaviour of the wheel, shown in the third column of the sector tab. */
	private void initLookTab(Wheel wheel) {
		int rightTop = top;
		int valueX = stepPlusX(right);

		addRenderableWidget(toggle(right, rightTop + rowHeight * ROW_SHOW_LABELS, wheel.showLabels, () -> {
			wheel.showLabels = !wheel.showLabels;
		}));
		addRenderableWidget(toggle(right, rightTop + rowHeight * ROW_ICON_MODE, wheel.iconMode, () -> {
			wheel.iconMode = !wheel.iconMode;
		}));
		addRenderableWidget(toggle(right, rightTop + rowHeight * ROW_FEEDBACK, wheel.feedback, () -> {
			wheel.feedback = !wheel.feedback;
		}));
		addRenderableWidget(toggle(right, rightTop + rowHeight * ROW_CLICK, wheel.clickToActivate, () -> {
			wheel.clickToActivate = !wheel.clickToActivate;
		}));
		addRenderableWidget(toggle(right, rightTop + rowHeight * ROW_FONT_FOLLOW, wheel.fontFollowsRadius, () -> {
			wheel.fontFollowsRadius = !wheel.fontFollowsRadius;
		}));

		Button minusFont = stepButton("-", stepMinusX(right), rightTop + rowHeight * ROW_FONT_SCALE, () -> {
			wheel.fontScale = Math.max(0.5F, wheel.fontScale - 0.1F);
		});
		Button plusFont = stepButton("+", valueX, rightTop + rowHeight * ROW_FONT_SCALE, () -> {
			wheel.fontScale = Math.min(2.5F, wheel.fontScale + 0.1F);
		});
		minusFont.active = !wheel.fontFollowsRadius;
		plusFont.active = !wheel.fontFollowsRadius;
		addRenderableWidget(minusFont);
		addRenderableWidget(plusFont);

		addRenderableWidget(stepButton("-", stepMinusX(right), rightTop + rowHeight * ROW_SIZE, () -> {
			wheel.radius = Math.max(40.0F, wheel.radius - 4.0F);
		}));
		addRenderableWidget(stepButton("+", valueX, rightTop + rowHeight * ROW_SIZE, () -> {
			wheel.radius = Math.min(260.0F, wheel.radius + 4.0F);
		}));

		addRenderableWidget(stepButton("-", stepMinusX(right), rightTop + rowHeight * ROW_POS_X, () -> {
			wheel.posX = Math.max(0.05F, wheel.posX - 0.02F);
		}));
		addRenderableWidget(stepButton("+", valueX, rightTop + rowHeight * ROW_POS_X, () -> {
			wheel.posX = Math.min(0.95F, wheel.posX + 0.02F);
		}));

		addRenderableWidget(stepButton("-", stepMinusX(right), rightTop + rowHeight * ROW_POS_Y, () -> {
			wheel.posY = Math.max(0.05F, wheel.posY - 0.02F);
		}));
		addRenderableWidget(stepButton("+", valueX, rightTop + rowHeight * ROW_POS_Y, () -> {
			wheel.posY = Math.min(0.95F, wheel.posY + 0.02F);
		}));

		addRenderableWidget(stepButton("-", stepMinusX(right), rightTop + rowHeight * ROW_DEAD_ZONE, () -> {
			wheel.deadZone = Math.max(0.0F, wheel.deadZone - 2.0F);
		}));
		addRenderableWidget(stepButton("+", valueX, rightTop + rowHeight * ROW_DEAD_ZONE, () -> {
			wheel.deadZone = Math.min(160.0F, wheel.deadZone + 2.0F);
		}));

		addRenderableWidget(stepButton("-", stepMinusX(right), rightTop + rowHeight * ROW_SECTORS, () -> {
			wheel.sectorCount = Math.max(Wheel.MIN_SECTORS, wheel.sectorCount - 1);
			wheel.normalize();
		}));
		addRenderableWidget(stepButton("+", valueX, rightTop + rowHeight * ROW_SECTORS, () -> {
			wheel.sectorCount = Math.min(WheelConfig.MAX_SECTORS, wheel.sectorCount + 1);
			wheel.normalize();
		}));
	}

	private Button toggle(int columnX, int y, boolean value, Runnable action) {
		return Button.builder(onOff(value), button -> {
			action.run();
			refresh();
		}).bounds(widgetX(columnX), y, widgetWidth(), rowHeight).build();
	}

	private Component triggerLabel() {
		if (awaitingTrigger) {
			return Component.translatable("radialwheel.settings.trigger.awaiting");
		}

		if (config.triggerKey < 0) {
			return Component.translatable("radialwheel.settings.trigger.none");
		}

		return Component.literal(RawInput.keyName(config.triggerKey));
	}

	private Button stepButton(String label, int x, int y, Runnable action) {
		return Button.builder(Component.literal(label), button -> {
			action.run();
			refresh();
		}).bounds(x, y, 20, rowHeight).build();
	}


	// ------------------------------------------------------------------ helpers

	static Component onOff(boolean value) {
		return Component.translatable(value ? "radialwheel.settings.on" : "radialwheel.settings.off");
	}

	private static Component modifierLabel(String name, boolean value) {
		return Component.literal((value ? "§a" : "§7") + name);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (awaitingTrigger) {
			if (event.key() == InputConstants.KEY_ESCAPE) {
				awaitingTrigger = false;
			} else {
				config.triggerKey = event.key();
				awaitingTrigger = false;
			}

			refresh();
			return true;
		}

		if (this.commandSuggestions != null && this.commandSuggestions.keyPressed(event)) {
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (this.commandSuggestions != null && this.commandSuggestions.mouseClicked(event)) {
			return true;
		}

		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (this.commandSuggestions != null && this.commandSuggestions.mouseScrolled(vertical)) {
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		Wheel wheel = wheel();

		if (wheel == null) {
			return;
		}

		Slot slot = currentSlot();
		this.hoveredSlotCache = slot;

		centeredText(graphics, this.title, this.width / 2, 10, COLOR_TITLE);
		graphics.fill(left, 26, right + columnWidth, 27, COLOR_PANEL_EDGE);

		renderSectorTab(graphics, wheel, slot);
		renderLookColumn(graphics, wheel);

		if (this.commandSuggestions != null) {
			this.commandSuggestions.extractRenderState(graphics, mouseX, mouseY);
		}

		if (awaitingTrigger) {
			centeredText(graphics, Component.translatable("radialwheel.settings.trigger.hint"),
					this.width / 2, this.height - 46, COLOR_VALUE);
		}
	}

	/** Background panel and header of one column. */
	private void panel(GuiGraphicsExtractor graphics, int columnX, Component title) {
		graphics.fill(columnX, panelTop, columnX + columnWidth, panelBottom, COLOR_PANEL);
		graphics.fill(columnX, panelTop, columnX + 1, panelBottom, COLOR_PANEL_EDGE);
		graphics.fill(columnX + columnWidth - 1, panelTop, columnX + columnWidth, panelBottom, COLOR_PANEL_EDGE);
		graphics.fill(columnX, panelBottom - 1, columnX + columnWidth, panelBottom, COLOR_PANEL_EDGE);
		graphics.fill(columnX, panelTop, columnX + columnWidth, panelTop + 20, COLOR_PANEL_HEADER);
		centeredText(graphics, title, columnX + columnWidth / 2, panelTop + 6, COLOR_TITLE);
	}

	private void renderSectorTab(GuiGraphicsExtractor graphics, Wheel wheel, Slot slot) {
		panel(graphics, left, Component.translatable("radialwheel.settings.sector_column"));
		panel(graphics, middle, Component.translatable("radialwheel.settings.page_column"));
		panel(graphics, right, Component.translatable("radialwheel.settings.look_column"));

		centeredText(graphics, Component.translatable("radialwheel.settings.sector",
				(sectorIndex + 1) + " / " + wheel.sectorCount), left + columnWidth / 2,
				top + (rowHeight - 9) / 2 + 1, COLOR_VALUE);
		row(graphics, left, top + rowHeight * ROW_TYPE, Component.translatable("radialwheel.settings.type"), Component.empty());
		row(graphics, left, top + rowHeight * ROW_TARGET, Component.translatable("radialwheel.settings.target"), Component.empty());
		row(graphics, left, top + rowHeight * ROW_LABEL, Component.translatable("radialwheel.settings.label"), Component.empty());
		row(graphics, left, top + rowHeight * ROW_ICON, Component.translatable("radialwheel.settings.icon"), Component.empty());
		row(graphics, left, top + rowHeight * ROW_RAW_INJECT, Component.translatable("radialwheel.settings.raw_inject"), Component.empty());
		rowStepper(graphics, left, top + rowHeight * ROW_HOLD, Component.translatable("radialwheel.settings.hold_ticks"),
				Component.literal(String.valueOf(slot.holdTicks)));
		row(graphics, left, top + rowHeight * ROW_MODIFIERS, Component.translatable("radialwheel.settings.modifiers"), Component.empty());

		rowNav(graphics, middle, top + rowHeight * ROW_PAGE, Component.translatable("radialwheel.settings.page"),
				Component.literal((pageIndex + 1) + " / " + wheel.pageCount()));
		row(graphics, middle, top + rowHeight * ROW_PAGE_NAME, Component.translatable("radialwheel.settings.page_name"),
				Component.empty());
		row(graphics, middle, top + rowHeight * ROW_TRIGGER, Component.translatable("radialwheel.settings.trigger"),
				Component.empty());

		// icon preview / validation, right next to the icon field
		if (slot.icon != null && !slot.icon.isBlank()) {
			ItemStack stack = ItemIcons.stack(slot.icon);

			if (stack.isEmpty()) {
				text(graphics, Component.translatable("radialwheel.settings.icon.unknown"),
						widgetX(left), top + rowHeight * ROW_ICON_BUTTONS + 6, COLOR_WARN);
			} else {
				graphics.item(stack, widgetX(left), top + rowHeight * ROW_ICON_BUTTONS + 3);
			}
		}

		// raw key name resolution, under the modifiers row of the same column
		if (slot.type.migrated() == SlotType.RAW_INPUT && slot.rawKey != null && !slot.rawKey.isBlank()) {
			int code = RawInput.resolveKey(slot.rawKey);
			Component resolved = code >= 0
					? Component.translatable("radialwheel.settings.raw_key.resolved", code)
					: Component.translatable("radialwheel.settings.raw_key.unknown");
			text(graphics, resolved, left, top + rowHeight * (ROW_MODIFIERS + 1) + 4,
					code >= 0 ? COLOR_VALUE : COLOR_WARN);
		}
	}

	private void renderLookColumn(GuiGraphicsExtractor graphics, Wheel wheel) {
		row(graphics, right, top + rowHeight * ROW_SHOW_LABELS, Component.translatable("radialwheel.settings.show_labels"),
				Component.empty());
		row(graphics, right, top + rowHeight * ROW_ICON_MODE, Component.translatable("radialwheel.settings.icon_mode"),
				Component.empty());
		row(graphics, right, top + rowHeight * ROW_FEEDBACK, Component.translatable("radialwheel.settings.feedback"),
				Component.empty());
		row(graphics, right, top + rowHeight * ROW_CLICK,
				Component.translatable("radialwheel.settings.click_to_activate"), Component.empty());
		row(graphics, right, top + rowHeight * ROW_FONT_FOLLOW, Component.translatable("radialwheel.settings.font_size"),
				Component.empty());
		rowStepper(graphics, right, top + rowHeight * ROW_FONT_SCALE, Component.translatable("radialwheel.settings.font_scale"),
				Component.literal(Math.round(wheel.fontScale * 100.0F) + "%"));
		rowStepper(graphics, right, top + rowHeight * ROW_SIZE, Component.translatable("radialwheel.settings.radius"),
				Component.literal(String.valueOf(Math.round(wheel.radius))));
		rowStepper(graphics, right, top + rowHeight * ROW_POS_X, Component.translatable("radialwheel.settings.pos_x"),
				Component.literal(String.format("%.2f", wheel.posX)));
		rowStepper(graphics, right, top + rowHeight * ROW_POS_Y, Component.translatable("radialwheel.settings.pos_y"),
				Component.literal(String.format("%.2f", wheel.posY)));
		rowStepper(graphics, right, top + rowHeight * ROW_DEAD_ZONE, Component.translatable("radialwheel.settings.dead_zone"),
				Component.literal(String.valueOf(Math.round(wheel.deadZone))));
		rowStepper(graphics, right, top + rowHeight * ROW_SECTORS, Component.translatable("radialwheel.settings.sector_count"),
				Component.literal(String.valueOf(wheel.sectorCount)));
	}


	// ------------------------------------------------------------------ text

	/** Text of the settings screen is drawn at {@link #TEXT_SCALE} so it fits the compact rows. */
	private void text(GuiGraphicsExtractor graphics, Component component, int x, int y, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(TEXT_SCALE, TEXT_SCALE);
		graphics.text(this.font, component, 0, 0, color);
		pose.popMatrix();
	}

	private void centeredText(GuiGraphicsExtractor graphics, Component component, int centerX, int y, int color) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX, y);
		pose.scale(TEXT_SCALE, TEXT_SCALE);
		graphics.centeredText(this.font, component, 0, 0, color);
		pose.popMatrix();
	}

	/** One row: the label is right aligned, the value (if any) starts at the widget column. */
	private void row(GuiGraphicsExtractor graphics, int columnX, int y, Component label, Component value) {
		int baseline = y + (rowHeight - 9) / 2 + 1;
		text(graphics, label, columnX + LABEL_WIDTH - 6 - textWidth(label), baseline, COLOR_LABEL);

		if (value.getString().isEmpty()) {
			return;
		}

		text(graphics, value, columnX + LABEL_WIDTH, baseline, COLOR_VALUE);
	}

	/** Width of a piece of text as it is actually drawn (scaled). */
	private int textWidth(Component component) {
		return Math.round(this.font.width(component) * TEXT_SCALE);
	}

	/** A row whose value sits centred between the two step buttons. */
	private void rowStepper(GuiGraphicsExtractor graphics, int columnX, int y, Component label, Component value) {
		int baseline = y + (rowHeight - 9) / 2 + 1;
		text(graphics, label, columnX + LABEL_WIDTH - 6 - textWidth(label), baseline, COLOR_LABEL);
		centeredText(graphics, value, stepValueX(columnX), baseline, COLOR_VALUE);
	}

	/** A row whose value sits centred between two navigation buttons. */
	private void rowNav(GuiGraphicsExtractor graphics, int columnX, int y, Component label, Component value) {
		int baseline = y + (rowHeight - 9) / 2 + 1;
		text(graphics, label, columnX + LABEL_WIDTH - 6 - textWidth(label), baseline, COLOR_LABEL);
		centeredText(graphics, value, columnX + columnWidth / 2, baseline, COLOR_VALUE);
	}

	@Override
	public void onClose() {
		config.save();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}



	public WheelConfig config() {
		return config;
	}

	public Slot hoveredSlot() {
		return hoveredSlotCache;
	}

	@Override
	public void removed() {
		config.save();
	}
}
