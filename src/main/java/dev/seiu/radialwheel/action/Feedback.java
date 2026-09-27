package dev.seiu.radialwheel.action;

import dev.seiu.radialwheel.RadialWheel;
import dev.seiu.radialwheel.config.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Short action bar messages shown after a wheel sector was activated. */
public final class Feedback {
	private static boolean enabled = true;

	private Feedback() {
	}

	/** Every wheel decides for itself whether it shows the action bar feedback. */
	public static void setEnabled(boolean value) {
		enabled = value;
	}

	private static boolean enabled() {
		return enabled;
	}

	public static void success(Minecraft minecraft, Slot slot) {
		if (!enabled()) {
			return;
		}

		info(minecraft, Component.translatable("radialwheel.msg.activated", slot.displayName()));
	}

	public static void success(Minecraft minecraft, Component label) {
		if (!enabled()) {
			return;
		}

		info(minecraft, Component.translatable("radialwheel.msg.activated", label));
	}

	public static void error(Minecraft minecraft, Component message) {
		info(minecraft, Component.translatable("radialwheel.msg.prefix").append(message));
	}

	public static void info(Minecraft minecraft, Component message) {
		RadialWheel.LOGGER.info("[wheel] {}", message.getString());

		if (minecraft.player != null) {
			minecraft.player.sendOverlayMessage(message);
		}
	}
}
