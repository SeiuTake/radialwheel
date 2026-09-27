package dev.seiu.radialwheel;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared constants for the Radial Wheel mod. */
public final class RadialWheel {
	public static final String MOD_ID = "radialwheel";
	public static final Logger LOGGER = LoggerFactory.getLogger("RadialWheel");

	private RadialWheel() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
