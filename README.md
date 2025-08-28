# Police-Plus (Police + Handcuff)

A comprehensive Police/Wanted/Jail system with a built‑in handcuff mechanic for Minecraft servers (Spigot/Paper 1.17+). Fully localized (English and Persian), GUI-driven, and optimized for practicality.

## Features
- Wanted system with levels and admin controls
- Jail system with movement restrictions and action bar timer
- Physical handcuff item and cuff/uncuff commands
- Police GUI to view and interact with wanted players
- Compass tracking with distance and boss bar support
- Language system with seamless fallbacks (en/fa)
- PlaceholderAPI support

## Requirements
- Spigot/Paper 1.17+ (tested on Paper 1.20.4)
- Java 17+
- Optional: PlaceholderAPI

## Installation
1) Download the jar from releases or build locally.
2) Place `police-plus-1.0.0.jar` into your server `plugins` folder.
3) Restart the server.
4) Edit `plugins/PolicePlugin/config.yml` and `plugins/PolicePlugin/languages/*.yml` as needed.

## Configuration Highlights
`config.yml` excerpt:

```yaml
language: "en" # or "fa"

wanted:
  max_wanted_level: 5

jail:
  jail_time_per_wanted: 5
  arrest_distance: 10

handcuff:
  name: "cuff"
  lore: "cuff"
  name_color: "&a"
  name_bold: true
  max_time: 300
  accept_plain_item: true
```

Notes:
- Handcuff material is fixed to BLAZE_ROD (not configurable). Change only name/lore, then run `/wanted reload` and give yourself a new handcuff with `/cuffe give <player> [amount]`.
- Set `accept_plain_item: false` if you only want the signed/marked handcuff item to work.

## Commands
Wanted (/wanted):
- `/wanted` — show your wanted level
- `/wanted set <player> [level]`
- `/wanted list`
- `/wanted jail <player>`
- `/wanted unjail <player>`
- `/wanted reload`
- `/wanted help`
- `/wanted gui`

Jail (/jail):
- `/jail create <name> <id>`
- `/jail delete <name>`
- `/jail spawn`
- `/jail list`
- `/jail help`

Police (/police):
- `/police` — open Police GUI
- `/police jail`
- `/police arrest <player>`

Handcuff:
- `/cuffe give <player> [amount]`
- `/cuffe <player>` or `/cuffe` (nearest player ≤ 3 blocks)
- `/uncuffe <player>`

## Permissions
Core:
- `policeplugin.wanted.self` (default: true)
- `policeplugin.wanted` (default: op)
- `policeplugin.police` (default: op)
- `policeplugin.compass` (default: op)
- `policeplugin.bypass` (default: op)
- `policeplugin.admin` (default: op)

Handcuff:
- `policeplugin.handcuff.cuff` (default: op)
- `policeplugin.handcuff.uncuff` (default: op)
- `policeplugin.handcuff.give` (default: op)
- `policeplugin.handcuff.uncuff_any` (default: op)

## Language System
- Select language with `language: en|fa` in `config.yml`.
- The plugin loads all defaults from bundled `en.yml` and overlays your selected language; missing keys are written back to disk to keep files in sync.
- If your server language files are outdated, delete `plugins/PolicePlugin/languages/` and restart to get fresh copies.

## Building
Requires Maven and JDK 17+.

```bash
mvn clean package -DskipTests
```

Jar will be in `target/police-plus-1.0.0.jar`.

## Troubleshooting
- Handcuffs not updating after config change: run `/wanted reload` and give new items.
- Can’t jail: check distance, arrest ownership, and that jails exist.
- Language key missing: remove `plugins/PolicePlugin/languages` and restart.

## License
Proprietary. All rights reserved unless stated otherwise by the author.


