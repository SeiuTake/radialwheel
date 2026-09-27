# Modrinth / CurseForge listing text

## Summary (max ~256 chars)

A client-side radial menu for Minecraft 26.2. Hold a key, point at a sector, release: fire any key bind
(vanilla, other mods, and Masa/malilib hotkeys), run a command, or inject raw input. Multiple pages and
item icons included.

## Description

### What it is

Radial Wheel is a **client-side** radial menu for Minecraft **26.2** (Fabric). Hold a key, the wheel
appears, point at a sector and release — the sector fires.

A sector can:

- **Hotkey** — any registered key bind: vanilla, any other mod, **and Masa/malilib hotkeys**
  (Tweakeroo, Litematica, MiniHUD), which normally never show up in `Options → Controls`.
- **Command** — run `/command` or send chat text, with vanilla command completion.
- **Raw input** — inject a key + mouse button + modifiers combination for mods that only read raw input.

Why: key binds pile up until there is no free physical key left. Fold them into a wheel, unbind the
originals, and **get your keyboard back**.

### Features

- One wheel, unlimited **pages** — switch with the mouse wheel while the wheel is open
- Up to 12 sectors per wheel
- **Hotkey picker** modelled on the vanilla controls screen: grouped by mod, further split by the mod's
  own sub-categories (e.g. Tweakeroo's Generic / Fixes / Lists / Toggles / Hotkeys / Disable), searchable
  across every group
- **Masa hotkeys** are triggered through malilib's own callback, not by faking key presses, so they behave
  exactly like pressing them yourself
- **Item icons** per sector, with a two-pane item picker (real icons, search, "held item"), plus an icon mode
- Appearance: size, position, dead zone, sector count, labels, action-bar feedback, click-to-fire, and a
  font size that can be fixed or follow the wheel size
- Config file `config/radialwheel.json`, hot-reloadable with `/radialwheel reload`
- English and Simplified Chinese in-game text

### Requirements

- Minecraft 26.2, Fabric Loader **0.19.5+** (recommended), Fabric API, Java 25
- Client side only — the server does not need it

> Note: on Fabric Loader 0.19.3 and older, multi-version wrapper mods (e.g. `gugle-carpet-addition`) can
> resolve to a nested jar for a newer Minecraft and abort startup with `Incompatible mods found!`.
> Updating the loader fixes it; it is unrelated to this mod.

### Usage

Hold the trigger key (default `Left Alt`), pick a sector with the mouse, release. Mouse wheel switches
pages, right click opens that sector's settings, `ESC` cancels. Configure with `/radialwheel` or the
(unbound by default) `Radial Wheel: Open settings` key bind.

### License

MIT. Modpack inclusion is welcome.

## Version / release notes template

```
Radial Wheel <version> for Minecraft 26.2

- <change>
- <change>

Requires Fabric Loader 0.19.5+ (0.18.4 minimum), Fabric API 0.161.0+26.2 and Java 25.
Client side only.
```
