package dev.seiu.radialwheel.config;

import java.util.ArrayList;
import java.util.List;

/** One page of a wheel: one slot per sector. */
public class Page {
	/** Optional page name, shown in the wheel's page indicator. */
	public String name = "";

	public List<Slot> slots = new ArrayList<>();

	public Page() {
	}

	public Page(String name) {
		this.name = name;
	}

	public Slot slot(int index) {
		if (index < 0 || index >= slots.size()) {
			return null;
		}

		return slots.get(index);
	}

	public void normalize(int sectorCount) {
		if (name == null) {
			name = "";
		}

		if (slots == null) {
			slots = new ArrayList<>();
		}

		while (slots.size() < sectorCount) {
			slots.add(new Slot());
		}

		while (slots.size() > WheelConfig.MAX_SECTORS) {
			slots.remove(slots.size() - 1);
		}

		for (Slot slot : slots) {
			slot.validate();
		}
	}

	public Page copy() {
		Page copy = new Page(name);

		for (Slot slot : slots) {
			copy.slots.add(slot.copy());
		}

		return copy;
	}
}
