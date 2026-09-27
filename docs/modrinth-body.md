# Radial Wheel

A **client-side** radial menu for Minecraft **26.2** (Fabric). Hold a key, the wheel appears, point at a
sector and release - the sector fires.

## A sector can

- **Hotkey** - any registered key bind: vanilla, any other mod, and **Masa/malilib hotkeys**
  (Tweakeroo, Litematica, MiniHUD), which normally never show up in `Options -> Controls`.
- **Command** - run `/command` or send chat text, with vanilla command completion.
- **Raw input** - inject a key + mouse + modifier combination for mods that only read raw input.

Why: key binds pile up until no physical key is left. Fold them into one wheel, unbind the originals and
**get your keyboard back**.

## Features

- One wheel, unlimited **pages** - switch with the mouse wheel while the wheel is open
- Up to 12 sectors per wheel
- **Hotkey picker** modelled on the vanilla controls screen: grouped by mod, and by the mod's own
  sub-categories (e.g. Tweakeroo's Generic / Fixes / Lists / Toggles / Hotkeys / Disable), searchable
  across every group
- **Masa hotkeys** are triggered through malilib's own callback, not by faking key presses, so they behave
  exactly like pressing them yourself
- **Item icons** per sector, with a two-pane item picker (real icons, search, "held item") and an icon mode
- Appearance: size, position, dead zone, sector count, labels, feedback, click-to-fire, and a font size
  that can be fixed or follow the wheel size
- Config file `config/radialwheel.json`, hot-reloadable with `/radialwheel reload`
- English and Simplified Chinese in-game text

## Usage

Hold the trigger key (default `Left Alt`), pick a sector with the mouse, release. The mouse wheel switches
pages, right click opens that sector's settings, `ESC` cancels. Configure with `/radialwheel` or the
(unbound by default) `Radial Wheel: Open settings` key bind.

## Notes

- Client side only - the server does not need it.
- Requires Fabric Loader 0.19.5 or newer (0.18.4 loads, but see below) and Fabric API.
- On Fabric Loader 0.19.3 and older, multi-version wrapper mods such as `gugle-carpet-addition` can
  resolve to a nested jar for a newer Minecraft and abort startup with `Incompatible mods found!`.
  Updating the loader fixes it; it is unrelated to this mod.
