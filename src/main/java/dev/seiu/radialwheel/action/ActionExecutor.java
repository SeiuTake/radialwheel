package dev.seiu.radialwheel.action;

import dev.seiu.radialwheel.config.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

/** Runs the action that belongs to a wheel sector. */
public final class ActionExecutor {
	private ActionExecutor() {
	}

	public static void execute(Minecraft minecraft, Slot slot) {
		if (slot == null) {
			return;
		}

		switch (slot.type.migrated()) {
			case EMPTY -> {
			}
			case HOTKEY -> {
				if (slot.isMasa()) {
					MasaHotkeys.trigger(minecraft, slot.masa);
				} else {
					KeybindActions.trigger(minecraft, slot);
				}
			}
			case COMMAND -> runCommand(minecraft, slot.command);
			case RAW_INPUT -> runRawInput(minecraft, slot);
		}
	}

	private static void runCommand(Minecraft minecraft, String rawCommand) {
		if (rawCommand == null || rawCommand.isBlank()) {
			return;
		}

		String text = rawCommand.trim();
		ClientPacketListener connection = minecraft.getConnection();

		if (connection == null) {
			Feedback.error(minecraft, Component.translatable("radialwheel.msg.no_connection"));
			return;
		}

		if (text.startsWith("/")) {
			connection.sendCommand(text.substring(1));
		} else {
			connection.sendChat(text);
		}

		Feedback.info(minecraft, Component.translatable("radialwheel.msg.command_sent", text));
	}

	private static void runRawInput(Minecraft minecraft, Slot slot) {
		int key = RawInput.resolveKey(slot.rawKey);
		int mouse = RawInput.resolveMouse(slot.rawMouse);

		if (key < 0 && mouse < 0) {
			Feedback.error(minecraft, Component.translatable("radialwheel.msg.raw_missing", slot.rawDescription()));
			return;
		}

		KeybindActions.injectStandalone(key, mouse, slot);
	}
}
