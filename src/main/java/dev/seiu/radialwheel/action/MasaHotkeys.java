package dev.seiu.radialwheel.action;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.seiu.radialwheel.RadialWheel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Support for the hotkeys of the Masa mod family (the malilib based mods such as Tweakeroo,
 * Litematica, MiniHUD, ItemScroller and TweakerMore).
 *
 * <p>Those mods do not register vanilla {@code KeyMapping}s: every hotkey lives in the malilib
 * config and is matched against raw input events. malilib keeps a registry of all of them, which
 * is what its own "all hotkeys" screen is built from:
 *
 * <pre>
 * InputEventHandler.getKeybindManager().getKeybindCategories()
 *     -&gt; KeybindCategory.getModName() / getHotkeys() -&gt; IHotkey
 * </pre>
 *
 * <p>Everything is called reflectively, so there is no compile time dependency on malilib, and the
 * list also works when the mods are loaded from a development classpath (no jar file access).
 *
 * <p>Triggering uses the same public path the mods use themselves:
 * {@code ((KeybindMulti) hotkey.getKeybind()).getCallback().onKeyAction(KeyAction.PRESS, keybind)}.
 */
public final class MasaHotkeys {
	private static final Map<String, Option> OPTIONS = new LinkedHashMap<>();
	private static List<Option> cachedOptions;

	private MasaHotkeys() {
	}

	/** One hotkey of a Masa mod. */
	public static final class Option {
		private final String target;
		private final String modId;
		private final Component modName;
		private final String category;
		private final String configName;
		private final Component displayName;
		private final String searchText;
		private final Object configOption;

		private Object callback;
		private Method onKeyAction;
		private Object keyActionPress;
		private Method booleanGetter;
		private Method booleanSetter;
		private boolean prepared;
		private boolean triggerable;

		private Option(String target, String modId, Component modName, String category, String configName,
				Component displayName, String searchText, Object configOption) {
			this.target = target;
			this.modId = modId;
			this.modName = modName;
			this.category = category;
			this.configName = configName;
			this.displayName = displayName;
			this.searchText = searchText;
			this.configOption = configOption;
		}

		public String target() {
			return target;
		}

		public String modId() {
			return modId;
		}

		public Component modName() {
			return modName;
		}

		public String category() {
			return category;
		}

		public String configName() {
			return configName;
		}

		public Component displayName() {
			return displayName;
		}

		public String searchText() {
			return searchText;
		}

		/** The currently assigned key combination, or an empty string when unbound. */
		public String keysDisplay() {
			Object keybind = invokeNoArg(configOption, "getKeybind");

			if (keybind == null) {
				return "";
			}

			Object display = invokeNoArg(keybind, "getKeysDisplayString");
			return display == null ? "" : String.valueOf(display);
		}

		private boolean prepare() {
			if (prepared) {
				return triggerable;
			}

			prepared = true;

			try {
				Object keybind = invokeNoArg(configOption, "getKeybind");

				if (keybind != null) {
					callback = invokeNoArg(keybind, "getCallback");

					if (callback != null) {
						Class<?> keyActionClass = Class.forName("fi.dy.masa.malilib.hotkeys.KeyAction");
						Class<?> keybindInterface = Class.forName("fi.dy.masa.malilib.hotkeys.IKeybind");

						for (Object constant : keyActionClass.getEnumConstants()) {
							if (((Enum<?>) constant).name().equals("PRESS")) {
								keyActionPress = constant;
								break;
							}
						}

						if (keyActionPress != null) {
							onKeyAction = callback.getClass().getMethod("onKeyAction", keyActionClass, keybindInterface);
							makeAccessible(onKeyAction);
							triggerable = true;
							return true;
						}
					}
				}
			} catch (Throwable throwable) {
				RadialWheel.LOGGER.debug("无法准备 masa 热键 {}", target, throwable);
			}

			triggerable = prepareBoolean();
			return triggerable;
		}

		private boolean prepareBoolean() {
			try {
				booleanGetter = configOption.getClass().getMethod("getBooleanValue");
				booleanSetter = configOption.getClass().getMethod("setBooleanValue", boolean.class);
				makeAccessible(booleanGetter);
				makeAccessible(booleanSetter);
				return true;
			} catch (Throwable throwable) {
				return false;
			}
		}

		private boolean invoke() {
			if (!prepare()) {
				return false;
			}

			try {
				if (callback != null && onKeyAction != null && keyActionPress != null) {
					onKeyAction.invoke(callback, keyActionPress, invokeNoArg(configOption, "getKeybind"));
					return true;
				}

				if (booleanGetter != null && booleanSetter != null) {
					booleanSetter.invoke(configOption, !((Boolean) booleanGetter.invoke(configOption)));
					return true;
				}
			} catch (Throwable throwable) {
				RadialWheel.LOGGER.error("调用 masa 热键 {} 失败", target, throwable);
			}

			return false;
		}
	}

	/** All hotkeys registered with malilib, sorted by mod and name. */
	public static synchronized List<Option> options() {
		if (cachedOptions != null) {
			return cachedOptions;
		}

		OPTIONS.clear();

		if (!scanMalilibRegistry()) {
			RadialWheel.LOGGER.warn("未能从 malilib 注册表读取热键，尝试回退方案");
			scanKnownConfigClasses();
		}

		List<Option> options = new ArrayList<>(OPTIONS.values());
		options.sort(Comparator
				.comparing((Option option) -> option.modName().getString())
				.thenComparing(option -> option.displayName().getString()));
		cachedOptions = options;
		RadialWheel.LOGGER.info("已读取 {} 个 masa 热键（来自 malilib 注册表）", options.size());
		return options;
	}

	public static synchronized void invalidate() {
		cachedOptions = null;
		OPTIONS.clear();
	}

	/**
	 * Localised name of a malilib hotkey category ("Generic", "Tweaks", ...).
	 *
	 * <p>The mods ship translations for these under
	 * {@code <modid>.hotkeys.category.<category>} and {@code <modid>.gui.button.config_gui.<category>};
	 * the raw string is used when neither exists.
	 */
	public static Component categoryDisplayName(String modId, String category) {
		if (category == null || category.isBlank()) {
			return null;
		}

		String slug = category.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("(^_)|(_$)", "");
		String singular = slug.endsWith("s") ? slug.substring(0, slug.length() - 1) : slug;
		String[] keys = {
				modId + ".hotkeys.category." + slug,
				modId + ".hotkeys.category." + singular,
				modId + ".gui.button.config_gui." + slug,
				modId + ".gui.button.config_gui." + singular,
				modId + ".gui.button.config_gui." + slug + "s"
		};

		for (String key : keys) {
			try {
				Component translated = Component.translatable(key);
				String text = translated.getString();

				if (!text.equals(key)) {
					return translated;
				}
			} catch (Throwable throwable) {
				// missing translations simply fall through
			}
		}

		return Component.literal(category);
	}

	public static boolean isKnown(String target) {
		if (target == null || target.isBlank()) {
			return false;
		}

		options();
		return OPTIONS.containsKey(target);
	}

	public static boolean trigger(Minecraft minecraft, String target) {
		if (target == null || target.isBlank()) {
			Feedback.error(minecraft, Component.translatable("radialwheel.msg.masa_empty"));
			return false;
		}

		options();
		Option option = OPTIONS.get(target.trim());

		if (option == null) {
			Feedback.error(minecraft, Component.translatable("radialwheel.msg.masa_not_found", target));
			return false;
		}

		if (!option.invoke()) {
			Feedback.error(minecraft, Component.translatable("radialwheel.msg.masa_failed", target));
			return false;
		}

		Feedback.success(minecraft, option.displayName());
		return true;
	}

	// ------------------------------------------------------------------ discovery

	/** Reads the hotkey registry of malilib (works in dev and in a normal installation). */
	private static boolean scanMalilibRegistry() {
		try {
			Class<?> inputEventHandler = Class.forName("fi.dy.masa.malilib.event.InputEventHandler");
			Object manager = inputEventHandler.getMethod("getKeybindManager").invoke(null);

			if (manager == null) {
				return false;
			}

			Object categories = invokeNoArg(manager, "getKeybindCategories");

			if (!(categories instanceof List<?> categoryList)) {
				return false;
			}

			for (Object category : categoryList) {
				String modName = asString(invokeNoArg(category, "getModName"));
				String categoryName = asString(invokeNoArg(category, "getCategory"));
				Object hotkeys = invokeNoArg(category, "getHotkeys");

				if (!(hotkeys instanceof List<?> hotkeyList)) {
					continue;
				}

				for (Object hotkey : hotkeyList) {
					collect(hotkey, modName, categoryName, null);
				}
			}

			return !OPTIONS.isEmpty();
		} catch (Throwable throwable) {
			RadialWheel.LOGGER.debug("读取 malilib 热键注册表失败", throwable);
			return false;
		}
	}

	/**
	 * Fallback for the (unlikely) case that the malilib registry is not reachable: walk the config
	 * classes of the loaded mods by name and collect everything that carries a key bind.
	 */
	private static void scanKnownConfigClasses() {
		String[] classNames = {
				"fi.dy.masa.tweakeroo.config.FeatureToggle",
				"fi.dy.masa.tweakeroo.config.Hotkeys",
				"fi.dy.masa.tweakeroo.config.Configs$Generic",
				"fi.dy.masa.litematica.config.Hotkeys",
				"fi.dy.masa.litematica.config.Configs$Generic",
				"fi.dy.masa.minihud.config.Hotkeys",
				"fi.dy.masa.minihud.config.Configs$Generic",
				"fi.dy.masa.itemscroller.config.Hotkeys",
				"fi.dy.masa.itemscroller.config.Configs$Generic",
				"me.fallenbreath.tweakermore.config.TweakerMoreConfigs"
		};

		for (String className : classNames) {
			Class<?> clazz;

			try {
				clazz = Class.forName(className, false, MasaHotkeys.class.getClassLoader());
			} catch (Throwable throwable) {
				continue;
			}

			String modName = modNameFromClassName(className);

			try {
				if (clazz.isEnum()) {
					for (Object constant : clazz.getEnumConstants()) {
						collect(constant, modName, null, null);
					}
				} else {
					for (java.lang.reflect.Field field : clazz.getFields()) {
						if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
							continue;
						}

						try {
							collect(field.get(null), modName, null, field.getName());
						} catch (Throwable ignored) {
							// not accessible
						}
					}
				}
			} catch (Throwable throwable) {
				RadialWheel.LOGGER.debug("扫描配置类 {} 失败", className, throwable);
			}
		}
	}

	private static String modNameFromClassName(String className) {
		String modId = modIdFromPackage(className);

		try {
			return FabricLoader.getInstance().getModContainer(modId)
					.map(container -> container.getMetadata().getName())
					.orElse(modId);
		} catch (Throwable throwable) {
			return modId;
		}
	}

	/** {@code fi.dy.masa.tweakeroo.config.FeatureToggle} becomes {@code tweakeroo}. */
	private static String modIdFromPackage(String className) {
		if (className.startsWith("fi.dy.masa.")) {
			String rest = className.substring("fi.dy.masa.".length());
			int dot = rest.indexOf('.');
			return dot > 0 ? rest.substring(0, dot) : rest;
		}

		String[] parts = className.split("\\.");
		return parts.length >= 2 ? parts[parts.length - 2] : className;
	}

	private static void collect(Object hotkey, String modName, String category, String fallbackName) {
		if (hotkey == null || invokeNoArg(hotkey, "getKeybind") == null) {
			return;
		}

		try {
			String configName = asString(invokeNoArg(hotkey, "getName"));

			if (configName == null || configName.isBlank()) {
				configName = fallbackName;
			}

			if (configName == null || configName.isBlank()) {
				return;
			}

			String modId = modIdFromPackage(hotkey.getClass().getName());
			String target = modId + ":" + configName;

			if (OPTIONS.containsKey(target)) {
				return;
			}

			String translated = asString(invokeNoArg(hotkey, "getTranslatedName"));
			Component displayName = Component.literal(
					translated == null || translated.isBlank() ? configName : translated);
			String resolvedModName = modName == null || modName.isBlank() ? modId : modName;
			String search = (target + " " + configName + " " + displayName.getString() + " " + resolvedModName
					+ " " + (category == null ? "" : category)).toLowerCase(Locale.ROOT);
			OPTIONS.put(target, new Option(target, modId, Component.literal(resolvedModName), category, configName,
					displayName, search, hotkey));
		} catch (Throwable throwable) {
			RadialWheel.LOGGER.debug("读取 masa 热键失败", throwable);
		}
	}

	// ------------------------------------------------------------------ reflection helpers

	private static Object invokeNoArg(Object instance, String methodName) {
		if (instance == null) {
			return null;
		}

		try {
			Method method = instance.getClass().getMethod(methodName);
			makeAccessible(method);
			return method.invoke(instance);
		} catch (Throwable throwable) {
			return null;
		}
	}

	private static String asString(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private static void makeAccessible(Method method) {
		try {
			method.setAccessible(true);
		} catch (Throwable throwable) {
			RadialWheel.LOGGER.debug("无法放宽访问权限: {}", method, throwable);
		}
	}
}
