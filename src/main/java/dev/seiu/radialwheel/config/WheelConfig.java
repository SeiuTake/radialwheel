package dev.seiu.radialwheel.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.seiu.radialwheel.RadialWheel;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Persisted settings: a list of wheels, each with its own pages.
 *
 * <p>Version 1 of the config file held a single wheel; those files are migrated on load.
 */
public class WheelConfig {
	public static final int MAX_SECTORS = 12;
	/** There is only ever one wheel; it can still hold several pages. */
	public static final int MAX_WHEELS = 1;

	public int version = 3;

	/** GLFW key code shared by every wheel, or -1 when unbound. */
	public int triggerKey = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;

	public List<Wheel> wheels = new ArrayList<>();

	// ---------------------------------------------------------------- legacy v1 fields
	// only read to migrate an old config file, never written back

	public Integer sectorCount;
	public Float radius;
	public Float deadZone;
	public Float hubRatio;
	public Boolean showLabels;
	public Boolean clickToActivate;
	public Boolean feedback;
	public List<Slot> slots;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(RadialWheel.MOD_ID + ".json");
	}

	public static WheelConfig load() {
		Path path = path();
		WheelConfig config = null;

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				config = GSON.fromJson(reader, WheelConfig.class);
			} catch (Exception exception) {
				RadialWheel.LOGGER.error("无法读取配置文件 {}，将使用默认配置", path, exception);
			}
		}

		if (config == null) {
			config = new WheelConfig();
		}

		config.normalize();

		if (!Files.exists(path)) {
			config.save();
		}

		return config;
	}

	public void save() {
		normalize();
		Path path = path();

		try {
			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			RadialWheel.LOGGER.error("无法保存配置文件 {}", path, exception);
		}
	}

	public void normalize() {
		if (wheels == null) {
			wheels = new ArrayList<>();
		}

		migrateLegacy();
		migrateTriggerKey();
		version = 3;

		if (wheels.isEmpty()) {
			wheels.add(Wheel.createDefault(""));
		}

		while (wheels.size() > MAX_WHEELS) {
			wheels.remove(wheels.size() - 1);
		}

		if (wheels.size() > 1) {
			RadialWheel.LOGGER.info("只保留第一个轮盘，其余 {} 个已忽略", wheels.size() - 1);
		}

		for (Wheel wheel : wheels) {
			wheel.normalize();
		}
	}

	/**
	 * Up to version 2 every wheel had its own trigger key. They now share one, so the first bound
	 * key found in the file becomes the shared one.
	 */
	private void migrateTriggerKey() {
		if (version >= 3) {
			return;
		}

		for (Wheel wheel : wheels) {
			if (wheel != null && wheel.triggerKey >= 0) {
				triggerKey = wheel.triggerKey;
				break;
			}
		}

		if (triggerKey < -1 || (triggerKey >= 0 && (triggerKey < 32 || triggerKey > 348))) {
			triggerKey = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
		}
	}

	/** Turns a version 1 config (one wheel, flat slot list) into a version 2 config. */
	private void migrateLegacy() {
		if (!wheels.isEmpty() || slots == null) {
			return;
		}

		Wheel wheel = new Wheel("");
		wheel.triggerKey = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
		wheel.sectorCount = sectorCount == null ? Math.max(Wheel.MIN_SECTORS, slots.size()) : sectorCount;
		wheel.radius = radius == null ? wheel.radius : radius;
		wheel.deadZone = deadZone == null ? wheel.deadZone : deadZone;
		wheel.hubRatio = hubRatio == null ? wheel.hubRatio : hubRatio;
		wheel.showLabels = showLabels == null || showLabels;
		wheel.clickToActivate = clickToActivate == null || clickToActivate;
		wheel.feedback = feedback == null || feedback;

		Page page = new Page();
		page.slots = slots;
		wheel.pages.add(page);
		wheels.add(wheel);

		// keep the new file clean
		slots = null;
		sectorCount = null;
		radius = null;
		deadZone = null;
		hubRatio = null;
		showLabels = null;
		clickToActivate = null;
		feedback = null;
		RadialWheel.LOGGER.info("已把旧版单轮盘配置迁移为多轮盘配置");
	}

	public Wheel wheel(int index) {
		if (index < 0 || index >= wheels.size()) {
			return null;
		}

		return wheels.get(index);
	}

	public int wheelCount() {
		return wheels.size();
	}
}
