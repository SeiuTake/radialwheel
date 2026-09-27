package dev.seiu.radialwheel.tools;

import com.mojang.blaze3d.platform.InputConstants;

import dev.seiu.radialwheel.action.RawInput;

/** Temporary probe used to verify key name resolution without launching the game. */
public final class KeyNameProbe {
	private KeyNameProbe() {
	}

	public static void main(String[] args) {
		System.out.println("PROBE mc name for 96    = " + InputConstants.Type.KEYSYM.getOrCreate(96).getName());
		System.out.println("PROBE mc name for 294   = " + InputConstants.Type.KEYSYM.getOrCreate(294).getName());
		System.out.println("PROBE mc name for 65    = " + InputConstants.Type.KEYSYM.getOrCreate(65).getName());

		for (String name : new String[]{"grave", "grave.accent", "`", "~", "F6", "f6", "r", "left.alt",
				"key.keyboard.f6", "key.keyboard.96", "96", "GLFW_KEY_F6", "space", "lshift", "return", "nonsense"}) {
			System.out.println("PROBE resolve('" + name + "') = " + RawInput.resolveKey(name));
		}

		for (String name : new String[]{"left", "right", "middle", "4", "nonsense"}) {
			System.out.println("PROBE resolveMouse('" + name + "') = " + RawInput.resolveMouse(name));
		}
	}
}
