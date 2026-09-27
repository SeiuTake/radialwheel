package dev.seiu.radialwheel.hotkey;

import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Item ids used as wheel icons. */
public final class ItemIcons {
	private ItemIcons() {
	}

	/** Resolves an item id, accepting {@code minecraft:apple}, {@code apple} and {@code Apple}. */
	public static Item resolve(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}

		String value = id.trim().toLowerCase(Locale.ROOT);

		if (!value.contains(":")) {
			value = "minecraft:" + value;
		}

		try {
			Identifier identifier = Identifier.tryParse(value);

			if (identifier == null) {
				return null;
			}

			Item item = BuiltInRegistries.ITEM.getValue(identifier);
			return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
		} catch (Throwable throwable) {
			return null;
		}
	}

	public static ItemStack stack(String id) {
		Item item = resolve(id);
		return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
	}

	public static Component displayName(String id) {
		Item item = resolve(id);

		if (item == null) {
			return Component.literal(id == null ? "" : id);
		}

		return item.getName(item.getDefaultInstance());
	}

	/** The id of the item the player currently holds, or an empty string. */
	public static String heldItemId() {
		try {
			var minecraft = net.minecraft.client.Minecraft.getInstance();

			if (minecraft.player == null) {
				return "";
			}

			ItemStack stack = minecraft.player.getMainHandItem();

			if (stack.isEmpty()) {
				return "";
			}

			Identifier identifier = BuiltInRegistries.ITEM.getKey(stack.getItem());
			return identifier == null ? "" : identifier.toString();
		} catch (Throwable throwable) {
			return "";
		}
	}

	public static String idOf(Item item) {
		try {
			Identifier identifier = BuiltInRegistries.ITEM.getKey(item);
			return identifier == null ? "" : identifier.toString();
		} catch (Throwable throwable) {
			return "";
		}
	}
}
