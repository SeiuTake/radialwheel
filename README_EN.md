# Radial Wheel

A client-side **radial menu** for Minecraft **26.2** (Fabric): hold a key, point at a sector, let go —
each sector can fire a key bind, run a command, or inject a raw key/mouse combination.
One wheel, several pages, optional item icons.

[中文说明 / Chinese README](README.md)

## Why

Keybinds pile up: vanilla, every mod, and the Masa suite (Tweakeroo / Litematica / MiniHUD) that does not
even show up in `Options → Controls`. Radial Wheel folds them into one wheel so you can **give the
physical keys back to your keyboard** — the original binds can be unbound once they live in a page.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | **≥ 0.19.5 recommended** (≥ 0.18.4 loads, see below) |
| Fabric API | ≥ 0.161.0+26.2 |
| Java | 25 |

> **Use Fabric Loader 0.19.5 or newer.** On 0.19.3 and older, multi-version wrapper mods such as
> `gugle-carpet-addition` resolve to a nested jar for a *newer* Minecraft and abort the game with
> `Incompatible mods found!`. This is not caused by Radial Wheel, but you will hit it on an old loader.

Pure client mod: nothing has to be installed on the server.

## Install

1. Install Fabric Loader (0.19.5+) for Minecraft 26.2 and put Fabric API in `mods/`.
2. Drop `radialwheel-<version>.jar` into `.minecraft/mods/`.
3. Launch. A config file is created at `.minecraft/config/radialwheel.json`.

Everything starts empty on purpose: 6 empty sectors, no preset keys or commands. The first time you pick
a hotkey for a sector, its label is filled in with the hotkey name (until you edit the label yourself).

## Usage

| Action | Result |
| --- | --- |
| Hold the trigger key | Opens the wheel (default `Left Alt`) |
| Move the mouse | Select a sector; staying in the dead zone cancels |
| Release the trigger key | Fires the sector under the cursor |
| Mouse wheel | Switch page |
| Left click a sector | Fire immediately (can be turned off) |
| Right click | Open the settings of the sector under the cursor |
| `ESC` | Cancel |

There is also a `Radial Wheel: Open settings` key bind, unbound by default, and the client command
`/radialwheel` (`reload`, `help`).

## Sector types

- **Hotkey** — any registered key bind: vanilla, other mods, **and Masa/malilib hotkeys**.
  A two-pane picker groups everything by mod, and by the mod's own sub-categories when it has them
  (Tweakeroo's `Generic / Fixes / Lists / Toggles / Hotkeys / Disable`), using the mod's own translations.
- **Command** — `/command` or plain chat text, with vanilla command completion.
- **Raw input** — inject key + mouse + modifiers (`grave`, `F6`, `key.keyboard.96`, `GLFW_KEY_F6`, `left`…)
  for mods that only listen to raw input.

Item icons can be assigned per sector (two-pane item picker with real item icons, search, and
"held item"); `Icons` mode draws them instead of the labels.

## Settings screen

Three panels: **Current sector** / **Pages & trigger** / **Look & behaviour**.
Size, position, dead zone, sector count, labels, icon mode, feedback, click-to-fire, and font size
(fixed, or following the wheel size) all live in the right panel.

## Masa (malilib) hotkeys

Masa hotkeys are not vanilla key binds, so they never appear in `Options → Controls`. This mod reads
malilib's own registry (`InputEventHandler.getKeybindManager().getKeybindCategories()`) and triggers them
through malilib's callback, so they behave exactly like pressing them yourself:

```java
((KeybindMulti) hotkey.getKeybind()).getCallback().onKeyAction(KeyAction.PRESS, keybind)
```

## Build from source

```bash
./gradlew build        # jar lands in build/libs/
./gradlew runClient    # dev client
```

Requires JDK 25. Minecraft 26.2 ships unobfuscated, so no mappings are involved.
`./gradlew keyProbe` runs a small tool that prints every supported raw key-name spelling.

## License

MIT — see [LICENSE](LICENSE). You are free to include this mod in modpacks.
