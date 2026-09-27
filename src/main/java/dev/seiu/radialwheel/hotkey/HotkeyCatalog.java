package dev.seiu.radialwheel.hotkey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.seiu.radialwheel.action.MasaHotkeys;
import dev.seiu.radialwheel.config.Slot;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The single list of everything a wheel sector can trigger: vanilla and mod key binds plus the
 * hotkeys of the Masa (malilib) mods, grouped into categories.
 *
 * <p>Grouping follows the mods' own categories:
 * <ul>
 *   <li>a mod key bind goes into its mod's group, using the mod's own key bind category as a
 *       sub group when that mod registered more than one,</li>
 *   <li>a Masa hotkey uses the category malilib reports for it (Generic / Fixes / Tweaks / ...),
 *       translated with the mod's own language entries,</li>
 *   <li>vanilla key binds keep the vanilla categories.</li>
 * </ul>
 *
 * <p>A mod that has only one category is shown as a single group, exactly as asked: "if the mod
 * itself has categories, use them, otherwise don't split".
 */
public final class HotkeyCatalog {
	/** One selectable hotkey. */
	public static final class Entry {
		private final String id;
		private final String ownerKey;
		private final Component ownerName;
		private final String subKey;
		private final Component subName;
		private final boolean vanilla;
		private final Component name;
		private final String boundKey;
		private final String searchText;
		private final String keybindName;
		private final String masaTarget;

		private String categoryKey;
		private Component categoryName;

		private Entry(String id, String ownerKey, Component ownerName, String subKey, Component subName,
				boolean vanilla, Component name, String boundKey, String searchText, String keybindName,
				String masaTarget) {
			this.id = id;
			this.ownerKey = ownerKey;
			this.ownerName = ownerName;
			this.subKey = subKey;
			this.subName = subName;
			this.vanilla = vanilla;
			this.name = name;
			this.boundKey = boundKey;
			this.searchText = searchText;
			this.keybindName = keybindName;
			this.masaTarget = masaTarget;
		}

		public String id() {
			return id;
		}

		public String categoryKey() {
			return categoryKey;
		}

		public Component categoryName() {
			return categoryName;
		}

		public Component name() {
			return name;
		}

		public String boundKey() {
			return boundKey;
		}

		public boolean isMasa() {
			return masaTarget != null;
		}

		public boolean isVanilla() {
			return vanilla;
		}

		public String keybindName() {
			return keybindName;
		}

		public String masaTarget() {
			return masaTarget;
		}

		public String searchText() {
			return searchText;
		}
	}

	/** A group shown in the left column of the picker. */
	public static final class Category {
		private final String key;
		private final Component name;
		private final boolean vanilla;
		private final List<Entry> entries = new ArrayList<>();

		private Category(String key, Component name, boolean vanilla) {
			this.key = key;
			this.name = name;
			this.vanilla = vanilla;
		}

		public String key() {
			return key;
		}

		public Component name() {
			return name;
		}

		public boolean isVanilla() {
			return vanilla;
		}

		public List<Entry> entries() {
			return entries;
		}
	}

	private static List<Category> cache;

	private HotkeyCatalog() {
	}

	/** All categories with their entries; built once and cached. */
	public static synchronized List<Category> categories() {
		if (cache != null) {
			return cache;
		}

		List<Entry> entries = new ArrayList<>();
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.options != null) {
			for (KeyMapping mapping : minecraft.options.keyMappings) {
				String keybindName = mapping.getName();
				Component translated = Component.translatable(keybindName);
				String ownerMod = ownerModId(mapping);
				Component ownerName;
				Component subName;
				boolean vanilla;

				if (ownerMod != null) {
					ownerName = Component.literal(modDisplayName(ownerMod));
					subName = mapping.getCategory().label();
					vanilla = false;
				} else {
					ownerName = mapping.getCategory().label();
					subName = null;
					vanilla = true;
				}

				String bound = mapping.isUnbound()
						? Component.translatable("radialwheel.picker.unbound").getString()
						: mapping.getTranslatedKeyMessage().getString();
				String search = (keybindName + " " + translated.getString() + " " + bound + " "
						+ ownerName.getString() + " " + (subName == null ? "" : subName.getString()))
						.toLowerCase(Locale.ROOT);
				entries.add(new Entry("kb:" + keybindName, ownerMod == null ? "kb:" + mapping.getCategory().id() : "mod:" + ownerMod,
						ownerName, ownerMod == null ? null : String.valueOf(mapping.getCategory().id()), subName,
						vanilla, translated, bound, search, keybindName, null));
			}
		}

		for (MasaHotkeys.Option option : MasaHotkeys.options()) {
			String bound = option.keysDisplay();
			Component subName = MasaHotkeys.categoryDisplayName(option.modId(), option.category());
			String search = (option.searchText() + " " + bound + " " + (subName == null ? "" : subName.getString()))
					.toLowerCase(Locale.ROOT);
			entries.add(new Entry("masa:" + option.target(), "mod:" + option.modId(), option.modName(),
					option.category(), subName, false, option.displayName(), bound, search, null, option.target()));
		}

		// A mod group is only split up when the mod really has several differently named
		// categories. Mods that register many categories under the same label (JEI does that)
		// stay a single group, otherwise the column would repeat the same name over and over.
		Map<String, Set<String>> labelsPerOwner = new HashMap<>();

		for (Entry entry : entries) {
			if (entry.ownerKey.startsWith("mod:") && entry.subName != null) {
				labelsPerOwner.computeIfAbsent(entry.ownerKey, key -> new HashSet<>()).add(entry.subName.getString());
			}
		}

		Map<String, Category> byKey = new HashMap<>();
		List<Category> categories = new ArrayList<>();

		for (Entry entry : entries) {
			Set<String> labels = labelsPerOwner.getOrDefault(entry.ownerKey, Set.of());
			boolean split = entry.ownerKey.startsWith("mod:") && entry.subName != null && labels.size() > 1;
			entry.categoryKey = split ? entry.ownerKey + "|" + entry.subName.getString() : entry.ownerKey;
			entry.categoryName = split
					? Component.literal(entry.ownerName.getString() + " · " + entry.subName.getString())
					: entry.ownerName;

			Category category = byKey.get(entry.categoryKey);

			if (category == null) {
				category = new Category(entry.categoryKey, entry.categoryName, entry.vanilla);
				byKey.put(entry.categoryKey, category);
				categories.add(category);
			}

			category.entries.add(entry);
		}

		// vanilla categories first, then the mods, both alphabetically
		categories.sort(Comparator
				.comparing((Category category) -> !category.isVanilla())
				.thenComparing(category -> category.name().getString()));

		for (Category category : categories) {
			category.entries.sort(Comparator.comparing(entry -> entry.name().getString()));
		}

		cache = categories;
		return categories;
	}

	/** Mod id when the key bind belongs to a loaded mod, otherwise null. */
	private static String ownerModId(KeyMapping mapping) {
		String fromName = modIdFromKeybindName(mapping.getName());

		if (fromName != null) {
			return fromName;
		}

		// some mods keep their own key bind category but name the keys differently
		Identifier categoryId = mapping.getCategory().id();
		String namespace = categoryId.getNamespace();

		if (!"minecraft".equals(namespace) && isLoadedMod(namespace)) {
			return namespace;
		}

		return null;
	}

	private static String modIdFromKeybindName(String keybindName) {
		if (keybindName == null) {
			return null;
		}

		String[] parts = keybindName.split("\\.");

		if (parts.length < 3 || !"key".equals(parts[0])) {
			return null;
		}

		return isLoadedMod(parts[1]) ? parts[1] : null;
	}

	private static boolean isLoadedMod(String modId) {
		try {
			return FabricLoader.getInstance().getModContainer(modId).isPresent();
		} catch (Throwable throwable) {
			return false;
		}
	}

	private static String modDisplayName(String modId) {
		try {
			return FabricLoader.getInstance().getModContainer(modId)
					.map(container -> container.getMetadata().getName())
					.orElse(modId);
		} catch (Throwable throwable) {
			return modId;
		}
	}

	public static synchronized void invalidate() {
		cache = null;
		MasaHotkeys.invalidate();
	}

	/** The entry a sector currently points at, or null. */
	public static Entry current(Slot slot) {
		if (slot == null) {
			return null;
		}

		String id = slot.isMasa() ? "masa:" + slot.masa : "kb:" + slot.keybind;

		for (Category category : categories()) {
			for (Entry entry : category.entries()) {
				if (entry.id().equals(id)) {
					return entry;
				}
			}
		}

		return null;
	}

	/** Points a sector at an entry and clears the other flavour. */
	public static void apply(Slot slot, Entry entry) {
		if (entry.isMasa()) {
			slot.masa = entry.masaTarget();
			slot.keybind = "";
		} else {
			slot.keybind = entry.keybindName();
			slot.masa = "";
		}

		// First time (or while the player never edited it): name the sector after the hotkey.
		if (slot.labelAuto || slot.label == null || slot.label.isBlank()) {
			slot.label = entry.name().getString();
			slot.labelAuto = true;
		}
	}

	public static void clear(Slot slot) {
		slot.keybind = "";
		slot.masa = "";
	}

	/** Display name of the hotkey a sector points at, for the wheel and the settings screen. */
	public static Component nameOf(Slot slot) {
		Entry entry = current(slot);

		if (entry != null) {
			return entry.name();
		}

		KeyMapping mapping = slot.keyMapping();

		if (mapping != null) {
			return Component.translatable(mapping.getName());
		}

		if (slot.isMasa()) {
			return Component.literal(slot.masa);
		}

		return Component.literal(slot.keybind == null || slot.keybind.isBlank() ? "?" : slot.keybind);
	}

	/** Label of the settings screen button that opens the picker. */
	public static Component describe(Slot slot) {
		if (slot.isEmpty()) {
			return Component.translatable("radialwheel.settings.pick_hotkey");
		}

		Entry entry = current(slot);
		Component name = nameOf(slot);

		if (entry == null) {
			return name;
		}

		String bound = entry.boundKey().isEmpty()
				? Component.translatable("radialwheel.picker.unbound").getString()
				: entry.boundKey();
		return Component.literal(name.getString() + " §7[" + bound + "] §8" + entry.categoryName().getString());
	}
}
