package dev.seiu.radialwheel;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import org.lwjgl.glfw.GLFW;

import dev.seiu.radialwheel.action.Feedback;
import dev.seiu.radialwheel.action.KeybindActions;
import dev.seiu.radialwheel.config.Wheel;
import dev.seiu.radialwheel.config.WheelConfig;
import dev.seiu.radialwheel.gui.WheelScreen;
import dev.seiu.radialwheel.gui.WheelSettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RadialWheelClient implements ClientModInitializer {
	public static final String KEY_OPEN_SETTINGS = "key.radialwheel.settings";

	/** Set while a trigger is still held after the wheel was used, so it does not pop back up. */
	private static boolean latched;

	private static WheelConfig config;
	private static KeyMapping openSettingsKey;
	private static int lastWheelIndex;

	public static WheelConfig config() {
		return config;
	}

	/** The wheel the settings screen should open for when no wheel was used yet. */
	public static int lastWheelIndex() {
		return lastWheelIndex;
	}

	public static void setLastWheelIndex(int index) {
		lastWheelIndex = Math.clamp(index, 0, Math.max(0, config.wheelCount() - 1));
	}

	@Override
	public void onInitializeClient() {
		config = WheelConfig.load();

		// Every wheel shares one trigger key; this binding only opens the settings screen.
		openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(KEY_OPEN_SETTINGS,
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(RadialWheelClient::onEndClientTick);
		registerCommands();

		RadialWheel.LOGGER.info("Radial Wheel 已就绪：{} 个轮盘，配置文件 {}", config.wheelCount(), WheelConfig.path());
	}

	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("radialwheel")
						.executes(context -> {
							openSettings(Minecraft.getInstance());
							return Command.SINGLE_SUCCESS;
						})
						.then(ClientCommands.literal("settings").executes(context -> {
							openSettings(Minecraft.getInstance());
							return Command.SINGLE_SUCCESS;
						}))
						.then(ClientCommands.literal("reload").executes(context -> {
							reloadConfig();
							context.getSource().sendFeedback(Component.translatable("radialwheel.msg.reloaded"));
							return Command.SINGLE_SUCCESS;
						}))
						.then(ClientCommands.literal("help").executes(context -> {
							context.getSource().sendFeedback(Component.translatable("radialwheel.msg.help"));
							return Command.SINGLE_SUCCESS;
						}))));
	}

	private static void onEndClientTick(Minecraft minecraft) {
		KeybindActions.tick();



		if (minecraft.player == null || minecraft.level == null) {
			WheelScreen wheel = WheelScreen.active();

			if (wheel != null) {
				wheel.close();
			}

			return;
		}

		WheelScreen open = WheelScreen.active();

		if (open != null) {
			if (!isKeyDown(minecraft, config.triggerKey)) {
				open.activateHovered();
			}

			return;
		}

		if (latched) {
			if (!anyTriggerDown(minecraft)) {
				latched = false;
			}

			return;
		}

		if (openSettingsKey.consumeClick()) {
			openSettings(minecraft);
			return;
		}

		// Only open on top of the game itself, never over another screen.
		if (minecraft.gui.screen() != null) {
			return;
		}

		// every wheel shares one trigger key and they are all shown at once
		if (isKeyDown(minecraft, config.triggerKey)) {
			open(minecraft, new WheelScreen(config));
		}
	}

	private static boolean anyTriggerDown(Minecraft minecraft) {
		return isKeyDown(minecraft, config.triggerKey);
	}

	/** Raw key state of a trigger key; works while a screen is open, unlike {@code KeyMapping#isDown}. */
	public static boolean isKeyDown(Minecraft minecraft, int glfwKey) {
		if (glfwKey < 0) {
			return false;
		}

		return InputConstants.isKeyDown(minecraft.getWindow(), glfwKey);
	}

	public static void openSettings(Minecraft minecraft) {
		openSettings(minecraft, lastWheelIndex, 0);
	}

	public static void openSettings(Minecraft minecraft, int wheelIndex, int sectorIndex) {
		lastWheelIndex = Math.clamp(wheelIndex, 0, Math.max(0, config.wheelCount() - 1));
		open(minecraft, new WheelSettingsScreen(config, lastWheelIndex, sectorIndex));
	}

	public static void reloadConfig() {
		config = WheelConfig.load();
		lastWheelIndex = Math.clamp(lastWheelIndex, 0, Math.max(0, config.wheelCount() - 1));
	}

	/** Prevents a wheel from reopening until every trigger key was released again. */
	public static void latch() {
		latched = true;
	}

	/** Switches screens without forcing an immediate frame like {@code setScreenAndShow} does. */
	public static void open(Minecraft minecraft, Screen screen) {
		minecraft.gui.setScreen(screen);
	}

	public static void closeScreen(Minecraft minecraft) {
		minecraft.gui.setScreen(null);
	}
}
