package dev.seiu.radialwheel.action;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import dev.seiu.radialwheel.RadialWheel;
import dev.seiu.radialwheel.config.Slot;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Triggers key mappings that belong to vanilla or to other mods. */
public final class KeybindActions {
	/** Presses that are still held down by the mod and that have to be released later. */
	private static final List<Pending> PENDING = new ArrayList<>();

	private static Field clickCountField;
	private static boolean clickCountUnavailable;

	private KeybindActions() {
	}

	public static void trigger(Minecraft minecraft, Slot slot) {
		KeyMapping mapping = KeyMapping.get(slot.keybind);

		if (mapping == null) {
			Feedback.error(minecraft, Component.translatable("radialwheel.msg.keybind_missing", slot.keybind));
			return;
		}

		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(mapping);
		boolean boundKnown = bound != null && !bound.equals(InputConstants.UNKNOWN);
		int modifiers = RawInput.modifierMask(slot.shift, slot.ctrl, slot.alt);
		int key = RawInput.resolveKey(slot.rawKey);
		int mouse = RawInput.resolveMouse(slot.rawMouse);
		boolean explicitRaw = key >= 0 || mouse >= 0;

		if (slot.rawInject && !explicitRaw && boundKnown) {
			if (bound.getType() == InputConstants.Type.KEYSYM) {
				key = bound.getValue();
			} else if (bound.getType() == InputConstants.Type.MOUSE) {
				mouse = bound.getValue();
			}
		}

		boolean coversVanillaPath = false;

		if (key >= 0 || mouse >= 0) {
			coversVanillaPath = boundKnown
					&& ((key >= 0 && bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() == key)
					|| (mouse >= 0 && bound.getType() == InputConstants.Type.MOUSE && bound.getValue() == mouse));

			if (key >= 0) {
				RawInput.pressKey(key, modifiers);
			}

			if (mouse >= 0) {
				RawInput.pressMouse(mouse, modifiers);
			}
		}

		int holdTicks = Math.max(1, slot.holdTicks);

		if (!coversVanillaPath) {
			// The mapping is unbound, or the injected combination is a different one:
			// simulate the press on the vanilla level as well.
			incrementClickCount(mapping);
			mapping.setDown(true);
		}

		PENDING.add(new Pending(mapping, key, mouse, modifiers, holdTicks));
		Feedback.success(minecraft, slot);
	}

	/** Injects a plain key / mouse combination without touching any key mapping. */
	public static void injectStandalone(int key, int mouse, Slot slot) {
		int modifiers = RawInput.modifierMask(slot.shift, slot.ctrl, slot.alt);

		if (key >= 0) {
			RawInput.pressKey(key, modifiers);
		}

		if (mouse >= 0) {
			RawInput.pressMouse(mouse, modifiers);
		}

		PENDING.add(new Pending(null, key, mouse, modifiers, Math.max(1, slot.holdTicks)));
		Feedback.success(Minecraft.getInstance(), slot);
	}

	/** Releases everything that was held down for a given amount of ticks. */
	public static void tick() {
		if (PENDING.isEmpty()) {
			return;
		}

		Iterator<Pending> iterator = PENDING.iterator();

		while (iterator.hasNext()) {
			Pending pending = iterator.next();
			pending.ticksLeft--;

			if (pending.ticksLeft > 0) {
				continue;
			}

			if (pending.key >= 0) {
				RawInput.releaseKey(pending.key, pending.modifiers);
			}

			if (pending.mouse >= 0) {
				RawInput.releaseMouse(pending.mouse, pending.modifiers);
			}

			if (pending.mapping != null) {
				pending.mapping.setDown(false);
			}

			iterator.remove();
		}
	}

	private static void incrementClickCount(KeyMapping mapping) {
		if (clickCountUnavailable) {
			return;
		}

		try {
			if (clickCountField == null) {
				clickCountField = KeyMapping.class.getDeclaredField("clickCount");
				clickCountField.setAccessible(true);
			}

			clickCountField.setInt(mapping, clickCountField.getInt(mapping) + 1);
		} catch (Throwable throwable) {
			clickCountUnavailable = true;
			RadialWheel.LOGGER.warn("无法直接增加按键的点击计数，将只使用按下状态模拟", throwable);
		}
	}

	private static final class Pending {
		private final KeyMapping mapping;
		private final int key;
		private final int mouse;
		private final int modifiers;
		private int ticksLeft;

		private Pending(KeyMapping mapping, int key, int mouse, int modifiers, int ticksLeft) {
			this.mapping = mapping;
			this.key = key;
			this.mouse = mouse;
			this.modifiers = modifiers;
			this.ticksLeft = ticksLeft;
		}
	}
}
