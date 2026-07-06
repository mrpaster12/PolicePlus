# PolicePlus

**The ultimate law and order system for your Minecraft server** — wanted levels, physical handcuffs, dynamic jails, and a full mining-labor punishment system, all in one plugin.

Built for **Spigot / Paper**, compiled for compatibility from **Minecraft 1.13 through 1.21.x** in a single JAR. Fully bilingual (English / Persian) and Vault-integrated.

📦 **[Download on SpigotMC](https://www.spigotmc.org/resources/police-plus-advanced-wanted-jail-handcuff-system.128404/)** &nbsp;|&nbsp; 🎥 **[Tutorials on YouTube](https://www.youtube.com/@mr.paster8904/videos)**

## Features

- **Wanted System** — Multi-level wanted tracking with custom reasons (`/police wanted add`), configurable auto-decay over time, and full police immunity for staff.
- **Advanced Handcuffs** — Custom handcuff item secured with a PersistentDataContainer tag (blocks plain Blaze Rod exploits). Innocent players (wanted level 0) can never be cuffed.
- **Elastic Dragging** — Suspects are smoothly pulled toward the arresting cop within a 5-block radius, with camera-preserving emergency teleport if the cop moves too fast.
- **Interactive Police GUI** — A guided, multi-step jailing flow: pick duration → pick jail type → pick a matching cell, all from a clickable inventory menu.
- **Mine-Jail & Labor System** — Time-based jails *or* mining-labor jails where prisoners must break a set number of blocks to earn release. Auto-regenerating cuboid regions, WorldGuard/GriefPrevention/Towny bypass, and full anti-grief protection for non-prisoners.
- **Compass Tracking** — Track a wanted suspect's distance and direction with a boss bar.
- **Vault Economy Rewards** — Cops get paid automatically on a successful arrest, scaled by the suspect's wanted level.
- **Bilingual & Hot-Reloadable** — Full English (`en.yml`) and Persian (`fa.yml`) translations, zero hardcoded strings, instant `/police reload`.
- **PlaceholderAPI Support** — Wanted level, jail status, and rank placeholders for scoreboards/tab lists.

## Requirements

- Spigot or Paper, **Minecraft 1.13 to 1.21.x**
- Java 8 or higher (the plugin is compiled for Java 8 bytecode so the same JAR runs on every supported server version)
- Optional: **Vault** + an economy plugin (e.g. EssentialsX) for arrest payouts
- Optional: **PlaceholderAPI**
- Optional: **WorldGuard** / **GriefPrevention** — mine-jail regions are designed to bypass these safely for assigned prisoners only

## Installation

1. Download `PolicePlus.jar` from [Releases](../../releases) or build it yourself (see below).
2. Drop it into your server's `plugins/` folder.
3. Restart the server completely to generate the config folder.
4. Edit `plugins/PolicePlus/config.yml` (language, wanted levels, jail settings, economy rewards).
5. Run `/police reload` to apply changes without restarting.

## Commands

**Master permission:** `policeplus.police` — grants a cop full access to GUI, cuffing, jailing, tracking, and alerts.

| Command | Description | Permission |
|---|---|---|
| `/police` | Open the Police GUI | `policeplus.police` |
| `/police wanted add <player> <level> [reason]` | Add wanted level | `policeplus.police` |
| `/police wanted remove <player>` | Remove wanted level | `policeplus.police` |
| `/police wanted list` | List all wanted players | `policeplus.police` |
| `/cuffe <player>` | Handcuff a player | `policeplus.police` |
| `/cuffe give <player> [amount]` | Give handcuff items | `policeplus.police` |
| `/uncuffe <player>` | Remove handcuffs | `policeplus.police` |
| `/compass <player>` | Track a wanted player | `policeplus.police` |
| `/jail create <name>` | Create a time-based jail | `policeplus.admin` |
| `/jail createmine <name>` | Create a mining-labor jail | `policeplus.admin` |
| `/jail setpos1 <name>` / `setpos2 <name>` | Define the 3D mine cuboid | `policeplus.admin` |
| `/jail spawn` | Set the global release point | `policeplus.admin` |
| `/jail list` | List all jails | `policeplus.admin` |
| `/police debugjails` | Debug jail data in memory | `policeplus.admin` |
| `/police reload` | Reload config and language files | `policeplus.admin` |

**Other permissions:**
- `policeplus.bypass` — bypass jail/handcuff restrictions (for admins/testing)
- `policeplus.wanted` — view your own wanted level (default: `true`)

## Configuration Highlights

```yaml
language: "en" # en = English, fa = Persian

wanted:
  max_wanted_level: 5
  wanted_on_kill: true
  wanted_level_per_kill: 1
  auto_remove: true
  remove_interval_minutes: 30

jail:
  type: "TIME" # TIME or BLOCKS
  jail_time_per_wanted: 5
  blocks_per_wanted: 100
  allowed_blocks:
    - "COBBLESTONE"
    - "STONE"
  arrest_distance: 10

handcuff:
  name: "cuff"
  max_time: 300
  apply_blindness: false

economy:
  enabled: true
  reward_per_wanted_level: 150.0
```

See `config.yml` for the full list of options (display modes, bounty settings, log rotation, and more).

## Building From Source

Requires **JDK 8+** and **Maven**.

```bash
mvn clean package -DskipTests
```

The compiled JAR will be at `target/police-plus-2.1.0.jar`.

> **Why Java 8?** The plugin targets Java 8 bytecode specifically so a single JAR works unmodified across every supported Minecraft version (1.13 → 1.21.x), regardless of which Java version the server itself runs.

## Troubleshooting

- **Handcuff not working after a config change** — run `/police reload` and give yourself a fresh handcuff item; existing items in inventories don't retroactively update.
- **Can't jail a player** — check `arrest_distance` in config, confirm the target actually has a wanted level, and make sure at least one jail has been created (`/jail list`).
- **Blocks placed inside a mine-jail disappear** — only the assigned prisoner can place/break blocks inside an active mine-jail region; this is intentional anti-grief protection.
- **Language file out of date after an update** — delete `plugins/PolicePlus/languages/` and restart; missing keys are regenerated automatically from the bundled defaults.

## Contributing

Issues and pull requests are welcome. Please open an issue describing the bug or feature before submitting a large PR.

## License

Proprietary — all rights reserved unless otherwise stated by the author. Contact the author before redistributing or forking for commercial use.

---

Maintained by [mrpaster](https://www.spigotmc.org/resources/authors/mrpaster.2114537/). Report issues on GitHub or via the Spigot resource page.
