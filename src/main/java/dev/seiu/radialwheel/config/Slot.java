package dev.seiu.radialwheel.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/** One sector of the wheel. */
public class Slot {
	/** What this sector does. */
	public SlotType type = SlotType.EMPTY;

	/** Key bind name of a {@link SlotType#HOTKEY} sector, e.g. {@code key.togglePerspective}. */
	public String keybind = "";

	/** Masa hotkey target of a {@link SlotType#HOTKEY} sector, written as {@code mod:configName}. */
	public String masa = "";

	/** Chat message (no leading slash) or command (leading slash) to run. */
	public String command = "";

	/** Low level key name used by {@link SlotType#RAW_INPUT} and by raw injection, e.g. {@code grave}. */
	public String rawKey = "";

	/** Low level mouse button used by {@link SlotType#RAW_INPUT}: left/right/middle/4..8. */
	public String rawMouse = "";

	public boolean alt = false;
	public boolean ctrl = false;
	public boolean shift = false;

	/**
	 * When triggering a key bind, also inject the physical key press so that mods
	 * which read raw input (instead of {@link KeyMapping}) still notice it.
	 */
	public boolean rawInject = true;

	/** Number of ticks the injected press is held down. */
	public int holdTicks = 2;

	/** Optional custom label shown in the wheel. */
	public String label = "";

	/** Optional icon, an item id such as {@code minecraft:diamond_sword} or {@code diamond_sword}. */
	public String icon = "";

	/**
	 * True while {@link #label} was filled in automatically from the picked hotkey.
	 * The label then follows the hotkey until the player edits it by hand.
	 */
	public boolean labelAuto = true;

	/** True when this sector points at a Masa (malilib) hotkey instead of a key bind. */
	public boolean isMasa() {
		return masa != null && !masa.isBlank();
	}

	public boolean isEmpty() {
		return switch (type.migrated()) {
			case EMPTY -> true;
			case HOTKEY -> (keybind == null || keybind.isBlank()) && (masa == null || masa.isBlank());
			case COMMAND -> command == null || command.isBlank();
			case RAW_INPUT -> (rawKey == null || rawKey.isBlank()) && (rawMouse == null || rawMouse.isBlank());
			// migrated() never yields the legacy values, this branch only satisfies the compiler
			default -> true;
		};
	}

	/** The key mapping this slot points at, or null. */
	public KeyMapping keyMapping() {
		if (keybind == null || keybind.isBlank()) {
			return null;
		}

		return KeyMapping.get(keybind);
	}

	public InputConstants.Key boundKeyOf(KeyMapping mapping) {
		return mapping == null ? InputConstants.UNKNOWN : KeyMappingHelper.getBoundKeyOf(mapping);
	}

	/** Human readable label, used by the wheel and by the settings screen. */
	public Component displayName() {
		if (label != null && !label.isBlank()) {
			return Component.literal(label);
		}

		return switch (type.migrated()) {
			case EMPTY -> Component.translatable("radialwheel.type.empty");
			case HOTKEY -> dev.seiu.radialwheel.hotkey.HotkeyCatalog.nameOf(this);
			case COMMAND -> Component.literal(command == null || command.isBlank() ? "?" : command);
			case RAW_INPUT -> Component.literal(rawDescription());
			// migrated() never yields the legacy values, this branch only satisfies the compiler
			default -> Component.translatable("radialwheel.type.empty");
		};
	}

	public String rawDescription() {
		StringBuilder builder = new StringBuilder();

		if (shift) {
			builder.append("Shift+");
		}

		if (ctrl) {
			builder.append("Ctrl+");
		}

		if (alt) {
			builder.append("Alt+");
		}

		if (rawKey != null && !rawKey.isBlank()) {
			builder.append(rawKey);
		}

		if (rawMouse != null && !rawMouse.isBlank()) {
			if (builder.length() > 0) {
				builder.append('+');
			}

			builder.append(rawMouse);
		}

		return builder.length() == 0 ? "?" : builder.toString();
	}

	public Slot copy() {
		Slot copy = new Slot();
		copy.type = type;
		copy.keybind = keybind;
		copy.masa = masa;
		copy.command = command;
		copy.rawKey = rawKey;
		copy.rawMouse = rawMouse;
		copy.alt = alt;
		copy.ctrl = ctrl;
		copy.shift = shift;
		copy.rawInject = rawInject;
		copy.holdTicks = holdTicks;
		copy.label = label;
		copy.icon = icon;
		copy.labelAuto = labelAuto;
		return copy;
	}

	public void validate() {
		type = SlotType.byName(type == null ? "EMPTY" : type.name()).migrated();

		if (keybind == null) {
			keybind = "";
		}

		if (masa == null) {
			masa = "";
		}

		if (command == null) {
			command = "";
		}

		if (rawKey == null) {
			rawKey = "";
		}

		if (rawMouse == null) {
			rawMouse = "";
		}

		if (label == null) {
			label = "";
		}

		if (icon == null) {
			icon = "";
		}

		holdTicks = Math.clamp(holdTicks, 1, 40);
	}
}
