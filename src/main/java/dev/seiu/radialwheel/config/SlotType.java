package dev.seiu.radialwheel.config;

/** What a wheel sector does when it is selected. */
public enum SlotType {
	/** Nothing happens. */
	EMPTY("radialwheel.type.empty", false),
	/** Triggers a hotkey: a vanilla/mod key bind or a Masa (malilib) hotkey. */
	HOTKEY("radialwheel.type.hotkey", false),
	/** Sends a chat message or a command. */
	COMMAND("radialwheel.type.command", false),
	/** Injects a low level key / mouse button combination. */
	RAW_INPUT("radialwheel.type.raw", false),
	/** Legacy alias of {@link #HOTKEY} (key bind variant), kept so old configs still load. */
	KEYBIND("radialwheel.type.hotkey", true),
	/** Legacy alias of {@link #HOTKEY} (Masa hotkey variant), kept so old configs still load. */
	MASA_HOTKEY("radialwheel.type.hotkey", true);

	public final String translationKey;
	/** Legacy values are not offered by the type button any more. */
	public final boolean legacy;

	SlotType(String translationKey, boolean legacy) {
		this.translationKey = translationKey;
		this.legacy = legacy;
	}

	/** The type after migrating legacy values. */
	public SlotType migrated() {
		return legacy ? HOTKEY : this;
	}

	/** Cycles through the types the player can pick from the settings screen. */
	public SlotType next() {
		SlotType[] selectable = {EMPTY, HOTKEY, COMMAND, RAW_INPUT};
		SlotType current = migrated();

		for (int index = 0; index < selectable.length; index++) {
			if (selectable[index] == current) {
				return selectable[(index + 1) % selectable.length];
			}
		}

		return EMPTY;
	}

	public static SlotType byName(String name) {
		if (name != null) {
			for (SlotType type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
		}

		return EMPTY;
	}
}
