package dev.seiu.radialwheel.gui;

import com.mojang.blaze3d.platform.InputConstants;
import org.joml.Matrix3x2fStack;

import dev.seiu.radialwheel.RadialWheelClient;
import dev.seiu.radialwheel.action.ActionExecutor;
import dev.seiu.radialwheel.action.Feedback;
import dev.seiu.radialwheel.config.Page;
import dev.seiu.radialwheel.config.Slot;
import dev.seiu.radialwheel.config.Wheel;
import dev.seiu.radialwheel.config.WheelConfig;
import dev.seiu.radialwheel.hotkey.ItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The radial menu: one wheel, one page at a time.
 *
 * <p>The mouse wheel switches pages, right clicking a sector opens the settings of that very
 * sector, ESC cancels.
 */
public class WheelScreen extends Screen {
	private static final int COLOR_SECTOR = 0xB4141414;
	private static final int COLOR_SECTOR_HOVERED = 0xE62E6FA8;
	private static final int COLOR_RING = 0x66FFFFFF;
	private static final int COLOR_HUB = 0xD9101010;
	private static final int COLOR_HUB_EDGE = 0x8890C4F0;
	private static final int COLOR_TEXT = 0xFFE6E6E6;
	private static final int COLOR_TEXT_HOVERED = 0xFFFFF0A0;
	private static final int COLOR_HINT = 0xFF9BA6B2;

	/** The menu that is currently on screen, or null. */
	private static WheelScreen active;

	private final WheelConfig config;
	private final Wheel wheel;

	private int page;
	private int hovered = -1;

	public WheelScreen(WheelConfig config) {
		super(Component.translatable("radialwheel.screen.wheel"));
		this.config = config;
		this.wheel = config.wheel(0);
	}

	public static WheelScreen active() {
		return active;
	}

	public static boolean isOpen() {
		return active != null;
	}

	@Override
	protected void init() {
		active = this;
	}

	@Override
	public void removed() {
		if (active == this) {
			active = null;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0x59000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (wheel == null) {
			close();
			return;
		}

		this.hovered = sectorAt(mouseX, mouseY);
		drawWheel(graphics);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (wheel != null && wheel.pageCount() > 1) {
			int step = vertical > 0 ? -1 : 1;
			page = Math.floorMod(page + step, wheel.pageCount());
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (wheel == null) {
			return super.mouseClicked(event, doubled);
		}

		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			// right clicking a sector opens the settings of that very sector
			Minecraft minecraft = Minecraft.getInstance();
			int sector = sectorAt(event.x(), event.y());
			close();
			RadialWheelClient.openSettings(minecraft, 0, Math.max(0, sector));
			return true;
		}

		if (wheel.clickToActivate && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int sector = sectorAt(event.x(), event.y());

			if (sector >= 0) {
				activate(sector);
				return true;
			}
		}

		return super.mouseClicked(event, doubled);
	}

	@Override
	public void onClose() {
		// ESC (or anything else that closes the screen) cancels the wheel without triggering
		close();
	}

	/** Runs the sector the mouse points at and closes the menu. */
	public void activateHovered() {
		Minecraft minecraft = Minecraft.getInstance();
		double mouseX = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
		double mouseY = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
		int sector = sectorAt(mouseX, mouseY);

		if (sector >= 0) {
			activate(sector);
		} else {
			close();
		}
	}

	public void activate(int sectorIndex) {
		Slot slot = wheel.slot(page, sectorIndex);
		Minecraft minecraft = Minecraft.getInstance();
		close();

		if (slot != null && !slot.isEmpty()) {
			Feedback.setEnabled(wheel.feedback);
			ActionExecutor.execute(minecraft, slot);
		}
	}

	public void close() {
		Minecraft minecraft = Minecraft.getInstance();
		active = null;
		RadialWheelClient.latch();
		RadialWheelClient.closeScreen(minecraft);
	}

	// ------------------------------------------------------------------ geometry

	private float centerX() {
		return this.width * wheel.posX;
	}

	private float centerY() {
		return this.height * wheel.posY;
	}

	/** The sector the cursor points at, or -1 inside the dead zone. */
	private int sectorAt(double mouseX, double mouseY) {
		if (wheel == null) {
			return -1;
		}

		double deltaX = mouseX - centerX();
		double deltaY = mouseY - centerY();
		double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

		if (distance < wheel.deadZone || distance > wheel.radius * 1.35) {
			return -1;
		}

		double span = Math.PI * 2.0 / wheel.sectorCount;
		double angle = Math.atan2(deltaY, deltaX) + Math.PI / 2.0;
		angle = ((angle % (Math.PI * 2.0)) + Math.PI * 2.0) % (Math.PI * 2.0);
		return Math.min((int) (angle / span), wheel.sectorCount - 1);
	}

	/** Font scale of the wheel: either fixed or tied to its size. */
	private float textScale() {
		return wheel.effectiveFontScale();
	}

	// ------------------------------------------------------------------ rendering

	private void drawWheel(GuiGraphicsExtractor graphics) {
		float centerX = centerX();
		float centerY = centerY();
		float outer = wheel.radius;
		float inner = wheel.hubRadius();
		int sectors = wheel.sectorCount;
		float span = (float) (Math.PI * 2.0 / sectors);
		float scale = textScale();

		for (int index = 0; index < sectors; index++) {
			float start = (float) (-Math.PI / 2.0) + index * span;
			int color = index == hovered ? COLOR_SECTOR_HOVERED : COLOR_SECTOR;
			drawSector(graphics, centerX, centerY, inner, outer, start, span, color);
		}

		drawRingOutline(graphics, centerX, centerY, outer, COLOR_RING);
		drawDisc(graphics, centerX, centerY, inner, COLOR_HUB);
		drawRingOutline(graphics, centerX, centerY, inner, COLOR_HUB_EDGE);

		drawSectorContents(graphics, centerX, centerY, inner, outer, span, scale);
		drawHubText(graphics, centerX, centerY, scale);
	}

	private void drawScaledText(GuiGraphicsExtractor graphics, String text, float x, float y, int color, float scale) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		graphics.centeredText(this.font, text, 0, 0, color);
		pose.popMatrix();
	}

	private void drawSector(GuiGraphicsExtractor graphics, float centerX, float centerY, float inner, float outer,
			float startAngle, float span, int color) {
		int steps = Math.max(4, (int) Math.ceil(span / 0.022));
		float step = span / steps;
		int bands = 3;
		Matrix3x2fStack pose = graphics.pose();

		for (int index = 0; index < steps; index++) {
			float angle = startAngle + (index + 0.5F) * step;
			pose.pushMatrix();
			pose.translate(centerX, centerY);
			pose.rotate(angle);

			for (int band = 0; band < bands; band++) {
				float from = inner + (outer - inner) * band / bands;
				float to = inner + (outer - inner) * (band + 1) / bands;
				float halfWidth = to * (float) Math.sin(step / 2.0) + 0.6F;
				graphics.fill(Math.round(from), Math.round(-halfWidth), Math.round(to), Math.round(halfWidth), color);
			}

			pose.popMatrix();
		}
	}

	private void drawDisc(GuiGraphicsExtractor graphics, float centerX, float centerY, float radius, int color) {
		int limit = Math.round(radius);

		for (int offset = -limit; offset <= limit; offset++) {
			double deltaX = offset + 0.5;
			double remaining = radius * radius - deltaX * deltaX;

			if (remaining <= 0.0) {
				continue;
			}

			float deltaY = (float) Math.sqrt(remaining);
			graphics.fill(Math.round(centerX + offset), Math.round(centerY - deltaY),
					Math.round(centerX + offset + 1), Math.round(centerY + deltaY), color);
		}
	}

	private void drawRingOutline(GuiGraphicsExtractor graphics, float centerX, float centerY, float radius, int color) {
		int steps = Math.max(48, (int) (radius * 1.2));

		for (int index = 0; index < steps; index++) {
			double angle = index * (Math.PI * 2.0 / steps);
			int x = Math.round(centerX + (float) Math.cos(angle) * radius);
			int y = Math.round(centerY + (float) Math.sin(angle) * radius);
			graphics.fill(x, y, x + 1, y + 1, color);
		}
	}

	private void drawSectorContents(GuiGraphicsExtractor graphics, float centerX, float centerY, float inner,
			float outer, float span, float scale) {
		Page current = wheel.page(page);
		float labelRadius = (inner + outer) / 2.0F;
		int maxWidth = Math.max(12, Math.round((outer - inner) * 0.9F / scale));

		for (int index = 0; index < wheel.sectorCount; index++) {
			Slot slot = current == null ? null : current.slot(index);

			if (slot == null || slot.isEmpty()) {
				continue;
			}

			float angle = (float) (-Math.PI / 2.0) + (index + 0.5F) * span;
			float x = centerX + (float) Math.cos(angle) * labelRadius;
			float y = centerY + (float) Math.sin(angle) * labelRadius;
			int color = index == hovered ? COLOR_TEXT_HOVERED : COLOR_TEXT;

			if (wheel.iconMode && slot.icon != null && !slot.icon.isBlank()) {
				ItemStack stack = ItemIcons.stack(slot.icon);

				if (!stack.isEmpty()) {
					Matrix3x2fStack pose = graphics.pose();
					pose.pushMatrix();
					pose.translate(x, y);
					pose.scale(scale, scale);
					graphics.item(stack, -8, -8);
					pose.popMatrix();
					continue;
				}
			}

			if (!wheel.showLabels) {
				continue;
			}

			String label = this.font.plainSubstrByWidth(slot.displayName().getString(), maxWidth);
			drawScaledText(graphics, label, x, y - 4.0F * scale, color, scale);
		}
	}

	private void drawHubText(GuiGraphicsExtractor graphics, float centerX, float centerY, float scale) {
		Page current = wheel.page(page);
		Slot hoveredSlot = current != null && hovered >= 0 ? current.slot(hovered) : null;
		String title;
		int color;

		if (hoveredSlot != null && !hoveredSlot.isEmpty()) {
			title = hoveredSlot.displayName().getString();
			color = COLOR_TEXT_HOVERED;
		} else {
			title = Component.translatable("radialwheel.screen.wheel.hint").getString();
			color = COLOR_HINT;
		}

		int maxWidth = Math.round(wheel.hubRadius() * 1.8F / scale);
		float titleY = centerY - (wheel.pageCount() > 1 ? 4.0F : 6.0F) * scale;
		drawScaledText(graphics, this.font.plainSubstrByWidth(title, Math.max(12, maxWidth)),
				centerX, titleY, color, scale);

		if (wheel.pageCount() > 1) {
			String pageName = current != null && current.name != null && !current.name.isBlank()
					? current.name
					: (page + 1) + " / " + wheel.pageCount();
			drawScaledText(graphics, pageName, centerX, centerY + wheel.hubRadius() * 0.5F, COLOR_HINT, scale);
		}
	}
}
