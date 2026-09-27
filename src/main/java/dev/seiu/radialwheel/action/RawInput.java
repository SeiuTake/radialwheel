package dev.seiu.radialwheel.action;

import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;

import dev.seiu.radialwheel.RadialWheel;
import net.minecraft.client.Minecraft;

/**
 * Injects synthetic GLFW input events.
 *
 * <p>Some mods (and most of the Masa mod family) do not read the vanilla
 * {@code KeyMapping} state at all: they hook the raw GLFW callbacks and match the
 * configured hotkey against the raw key codes. Re-dispatching the events through the
 * previously installed GLFW callback makes those mods see the press exactly as if the
 * player had typed it.
 */
public final class RawInput {
	/** Lazily built lookup of symbolic key names, see {@link #resolveKey(String)}. */
	private static volatile java.util.Map<String, Integer> symbolicKeyNames;

	private RawInput() {
	}

	/** Presses and holds a key; the caller is responsible for releasing it. */
	public static void pressKey(int glfwKey, int modifiers) {
		injectKey(glfwKey, GLFW.GLFW_PRESS, modifiers);
	}

	public static void releaseKey(int glfwKey, int modifiers) {
		injectKey(glfwKey, GLFW.GLFW_RELEASE, modifiers);
	}

	private static void injectKey(int glfwKey, int action, int modifiers) {
		if (glfwKey < 0) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		long window = minecraft.getWindow().handle();
		GLFWKeyCallbackI previous = GLFW.glfwSetKeyCallback(window, null);

		if (previous == null) {
			RadialWheel.LOGGER.warn("找不到 GLFW 键盘回调，无法注入原始按键事件");
			return;
		}

		try {
			int scancode = GLFW.glfwGetKeyScancode(glfwKey);
			previous.invoke(window, glfwKey, scancode, action, modifiers);
		} catch (Throwable throwable) {
			RadialWheel.LOGGER.error("注入原始按键事件失败 (key {})", glfwKey, throwable);
		} finally {
			GLFW.glfwSetKeyCallback(window, previous);
		}
	}

	/** Presses and holds a mouse button; the caller is responsible for releasing it. */
	public static void pressMouse(int glfwMouseButton, int modifiers) {
		injectMouse(glfwMouseButton, GLFW.GLFW_PRESS, modifiers);
	}

	public static void releaseMouse(int glfwMouseButton, int modifiers) {
		injectMouse(glfwMouseButton, GLFW.GLFW_RELEASE, modifiers);
	}

	private static void injectMouse(int glfwMouseButton, int action, int modifiers) {
		if (glfwMouseButton < 0) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		long window = minecraft.getWindow().handle();
		GLFWMouseButtonCallbackI previous = GLFW.glfwSetMouseButtonCallback(window, null);

		if (previous == null) {
			RadialWheel.LOGGER.warn("找不到 GLFW 鼠标回调，无法注入原始鼠标事件");
			return;
		}

		try {
			previous.invoke(window, glfwMouseButton, action, modifiers);
		} catch (Throwable throwable) {
			RadialWheel.LOGGER.error("注入原始鼠标事件失败 (button {})", glfwMouseButton, throwable);
		} finally {
			GLFW.glfwSetMouseButtonCallback(window, previous);
		}
	}

	/**
	 * Resolves a user supplied key name into a GLFW key code.
	 *
	 * <p>Accepted forms: {@code grave}, {@code grave.accent}, {@code F6}, {@code left.alt},
	 * {@code key.keyboard.f6}, {@code GLFW_KEY_F6} and a plain numeric key code such as {@code 96}.
	 *
	 * <p>Minecraft 26.2 serialises keys as {@code key.keyboard.<number>}, so the symbolic names are
	 * collected from the key table once instead of relying on {@code InputConstants.getKey(String)}.
	 */
	public static int resolveKey(String name) {
		if (name == null || name.isBlank()) {
			return -1;
		}

		String trimmed = name.trim();

		if (trimmed.regionMatches(true, 0, "key.keyboard.", 0, 13)) {
			trimmed = trimmed.substring(13);
		}

		try {
			return Integer.parseInt(trimmed);
		} catch (NumberFormatException ignored) {
			// not a plain key code, keep looking
		}

		Integer code = symbolicKeyNames().get(trimmed.toLowerCase(Locale.ROOT));

		if (code != null) {
			return code;
		}

		try {
			String upper = trimmed.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
			String constant = upper.startsWith("GLFW_KEY_") ? upper : "GLFW_KEY_" + upper;
			return GLFW.class.getField(constant).getInt(null);
		} catch (Throwable ignored) {
			// fall through to the error below
		}

		RadialWheel.LOGGER.warn("无法解析按键名称 '{}'，可用写法：grave / F6 / key.keyboard.96 / 数字键码", name);
		return -1;
	}

	/** Lower case symbolic key name (without the {@code key.keyboard.} prefix) to GLFW key code. */
	private static java.util.Map<String, Integer> symbolicKeyNames() {
		if (symbolicKeyNames == null) {
			java.util.Map<String, Integer> names = new java.util.HashMap<>();

			for (int code = 0; code <= 512; code++) {
				try {
					String name = InputConstants.Type.KEYSYM.getOrCreate(code).getName();

					if (name == null || !name.startsWith("key.keyboard.")) {
						continue;
					}

					String suffix = name.substring("key.keyboard.".length()).toLowerCase(Locale.ROOT);

					if (!suffix.isEmpty() && !suffix.chars().allMatch(Character::isDigit)) {
						names.putIfAbsent(suffix, code);
					}
				} catch (Throwable ignored) {
					// unknown key codes are not interesting here
				}
			}

			// Minecraft calls it "grave.accent", everybody else calls it "grave" or "`".
			names.putIfAbsent("grave", GLFW.GLFW_KEY_GRAVE_ACCENT);
			names.putIfAbsent("`", GLFW.GLFW_KEY_GRAVE_ACCENT);
			names.putIfAbsent("~", GLFW.GLFW_KEY_GRAVE_ACCENT);
			names.putIfAbsent("lalt", GLFW.GLFW_KEY_LEFT_ALT);
			names.putIfAbsent("ralt", GLFW.GLFW_KEY_RIGHT_ALT);
			names.putIfAbsent("lctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
			names.putIfAbsent("rctrl", GLFW.GLFW_KEY_RIGHT_CONTROL);
			names.putIfAbsent("lshift", GLFW.GLFW_KEY_LEFT_SHIFT);
			names.putIfAbsent("rshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
			names.putIfAbsent("return", GLFW.GLFW_KEY_ENTER);
			names.putIfAbsent("capslock", GLFW.GLFW_KEY_CAPS_LOCK);
			names.putIfAbsent("scrolllock", GLFW.GLFW_KEY_SCROLL_LOCK);
			names.putIfAbsent("printscreen", GLFW.GLFW_KEY_PRINT_SCREEN);

			symbolicKeyNames = names;
		}

		return symbolicKeyNames;
	}

	/** Resolves a user supplied mouse button name into a GLFW mouse button code. */
	public static int resolveMouse(String name) {
		if (name == null || name.isBlank()) {
			return -1;
		}

		return switch (name.trim().toLowerCase(Locale.ROOT)) {
			case "left", "lmb", "0" -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
			case "right", "rmb", "1" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
			case "middle", "mmb", "2" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
			case "4" -> GLFW.GLFW_MOUSE_BUTTON_4;
			case "5" -> GLFW.GLFW_MOUSE_BUTTON_5;
			case "6" -> GLFW.GLFW_MOUSE_BUTTON_6;
			case "7" -> GLFW.GLFW_MOUSE_BUTTON_7;
			case "8" -> GLFW.GLFW_MOUSE_BUTTON_8;
			default -> -1;
		};
	}

	public static int modifierMask(boolean shift, boolean ctrl, boolean alt) {
		int mask = 0;

		if (shift) {
			mask |= GLFW.GLFW_MOD_SHIFT;
		}

		if (ctrl) {
			mask |= GLFW.GLFW_MOD_CONTROL;
		}

		if (alt) {
			mask |= GLFW.GLFW_MOD_ALT;
		}

		return mask;
	}

	public static String keyName(int glfwKey) {
		if (glfwKey < 0) {
			return "?";
		}

		try {
			return InputConstants.Type.KEYSYM.getOrCreate(glfwKey).getName().replace("key.keyboard.", "");
		} catch (Throwable throwable) {
			return String.valueOf(glfwKey);
		}
	}
}
