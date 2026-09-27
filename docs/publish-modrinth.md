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

## 发布记录 / Published channels

| 渠道 | 地址 | 状态 |
| --- | --- | --- |
| GitHub 仓库 | https://github.com/SeiuTake/radialwheel | 已发布（公开，MIT） |
| GitHub Release | https://github.com/SeiuTake/radialwheel/releases | v1.4.0、v1.4.1 |
| Modrinth | https://modrinth.com/mod/radialwheel | 项目 ID `dxkasJz3`，1.4.1 已提交，等待审核（`processing`） |
| MC百科 | 待创建条目 | 文案见 `docs/publish-mcmod.md` |

### 发布下一个版本

```powershell
# 1) GitHub：改 gradle.properties 里的 mod_version 后
.\gradlew build
git add -A ; git commit -m "1.4.2: ..." ; git push
gh release create v1.4.2 build\libs\radialwheel-1.4.2.jar --title "Radial Wheel 1.4.2" --notes "..."

# 2) Modrinth：令牌放在 E:\AI\DeepSeek\.tools\modrinth-token.txt，然后
powershell -File E:\AI\DeepSeek\tools\modrinth-publish.ps1 -Version 1.4.2
```

发布脚本用的是 Modrinth 官方 HTTP API（`tools/modrinth-publish.ps1`）。踩过的坑记一下，免得下次再撞：

- 建项目接口是 **multipart**：JSON 放在 `data` 字段里，图标可作为同名 `icon` 文件一起传
- 必填字段是 **`license_id`**（SPDX ID，必须大写 `MIT`；小写 `mit` 会被当成自定义协议并要求提供 URL）
- 客户端/服务端标记要用 **`environment: ["client_only"]`**，老写法 `client_side` / `server_side` 在新 API 里落成 `unknown`
- 项目必须先建成 **草稿**（`is_draft: true`）并带上 `initial_versions: []`，否则报 `Project submitted for review with no initial versions`
- 上传版本用 `POST /v2/version`，同样是 multipart：`data` + 任意文件字段（在 `file_parts` 里列名）
- 提交审核：作者不能直接把状态改成 `approved`，要先补齐校验项（`environment` 是必填项），再 `PATCH /v2/project/{id}` 把状态设为 **`processing`**

