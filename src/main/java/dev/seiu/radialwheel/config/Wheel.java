package dev.seiu.radialwheel.config;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

/** One radial wheel: its own trigger key, screen position, look and pages. */
public class Wheel {
	public static final int MIN_SECTORS = 2;

	/** Shown in the wheel list when empty. */
	public String name = "";

	/** Legacy, per wheel trigger key. All wheels now share {@link WheelConfig#triggerKey}. */
	public int triggerKey = -1;

	/** Screen position of the wheel centre, as a fraction of the GUI size. */
	public float posX = 0.5F;
	public float posY = 0.5F;

	/** Outer radius in GUI pixels. */
	public float radius = 92.0F;
	/** Radius below which no sector is selected. */
	public float deadZone = 22.0F;
	/** Hub radius relative to {@link #radius}. */
	public float hubRatio = 0.42F;

	/** Label size in percent (50 - 250) when {@link #fontFollowsRadius} is off. */
	public float fontScale = 1.0F;
	/** Keep the label size in step with {@link #radius}. */
	public boolean fontFollowsRadius = true;

	public boolean showLabels = true;
	/** Draw the slot icons instead of the labels. */
	public boolean iconMode = false;
	public boolean feedback = true;
	public boolean clickToActivate = true;

	public int sectorCount = 6;

	public List<Page> pages = new ArrayList<>();

	public Wheel() {
	}

	public Wheel(String name) {
		this.name = name;
	}

	public int pageCount() {
		return pages == null ? 0 : pages.size();
	}

	public Page page(int index) {
		if (pages == null || index < 0 || index >= pages.size()) {
			return null;
		}

		return pages.get(index);
	}

	public Slot slot(int pageIndex, int sectorIndex) {
		Page page = page(pageIndex);

		if (page == null) {
			return null;
		}

		Slot slot = page.slot(sectorIndex);

		if (slot != null) {
			return slot;
		}

		Slot created = new Slot();
		page.slots.add(created);
		return created;
	}

	public void normalize() {
		if (name == null) {
			name = "";
		}

		posX = Math.clamp(posX, 0.05F, 0.95F);
		posY = Math.clamp(posY, 0.05F, 0.95F);
		radius = Math.clamp(radius, 40.0F, 260.0F);
		deadZone = Math.clamp(deadZone, 0.0F, 200.0F);
		hubRatio = Math.clamp(hubRatio, 0.15F, 0.9F);
		fontScale = Math.clamp(fontScale, 0.5F, 2.5F);
		sectorCount = Math.clamp(sectorCount, MIN_SECTORS, WheelConfig.MAX_SECTORS);

		if (pages == null) {
			pages = new ArrayList<>();
		}

		if (pages.isEmpty()) {
			pages.add(new Page());
		}

		for (Page page : pages) {
			page.normalize(sectorCount);
		}
	}

	/** A brand new wheel with one empty page. */
	public static Wheel createDefault(String name) {
		Wheel wheel = new Wheel(name);
		wheel.triggerKey = GLFW.GLFW_KEY_LEFT_ALT;
		wheel.pages.add(new Page());
		wheel.normalize();
		return wheel;
	}

	/** The font scale actually used when drawing this wheel. */
	public float effectiveFontScale() {
		float scale = fontFollowsRadius ? radius / 92.0F : fontScale;
		return Math.clamp(scale, 0.5F, 2.5F);
	}

	public float hubRadius() {
		return radius * hubRatio;
	}

	public Wheel copy(String newName) {
		Wheel copy = new Wheel(newName);
		copy.triggerKey = -1;
		copy.posX = posX;
		copy.posY = posY;
		copy.radius = radius;
		copy.deadZone = deadZone;
		copy.hubRatio = hubRatio;
		copy.showLabels = showLabels;
		copy.iconMode = iconMode;
		copy.fontScale = fontScale;
		copy.fontFollowsRadius = fontFollowsRadius;
		copy.feedback = feedback;
		copy.clickToActivate = clickToActivate;
		copy.sectorCount = sectorCount;
		copy.pages = new ArrayList<>();

		for (Page page : pages) {
			copy.pages.add(page.copy());
		}

		return copy;
	}
}
