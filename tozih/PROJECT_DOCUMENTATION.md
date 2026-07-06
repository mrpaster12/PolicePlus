# 📋 Police-Plus Plugin — Full Project Documentation

## 🎯 Overview

**Police-Plus** is a comprehensive police, jail, and handcuff system plugin for Minecraft servers (Spigot/Paper 1.17+). Written in **Java 21** and built with **Maven**.

### Core Features:
- **Wanted System** — Multi-level wanted/bounty tracking
- **Jail System** — TIME-based and BLOCKS (mine labor)-based prisons with cuboid regions
- **Handcuff System** — Physics-based elastic dragging with slowness, velocity pull, and emergency teleport
- **Police GUI** — Graphical interface for viewing wanted players
- **Compass Tracking** — Track suspects with distance display and boss bar
- **Bounty System** — Place bounties on players
- **Salary System** — Rank-based automatic salary payments
- **Stats & Logging** — Track arrests, jails, and all police activity
- **Multilingual** — English and Persian (Farsi)
- **PlaceholderAPI** support

---

## 🗺️ Project Structure

```
police-plus/
├── pom.xml                          ← Maven build file (dependencies, version, plugins)
├── README.md                        ← English project documentation
├── tozih/                           ← Documentation folder
│   └── PROJECT_DOCUMENTATION.md     ← This file
├── src/
│   └── main/
│       ├── resources/               ← Resource files (configs, languages)
│       │   ├── plugin.yml           ← Plugin metadata (name, commands, permissions)
│       │   ├── config.yml           ← Main plugin configuration
│       │   └── languages/           ← Translation files (en.yml, fa.yml)
│       └── java/
│           └── com/
│               └── policeplus/      ← Main source code
│                   ├── PolicePlus.java           ← Main plugin class (entry point)
│                   ├── commands/                 ← Command classes
│                   │   ├── BountyCommand.java
│                   │   ├── CompassCommand.java
│                   │   ├── CuffCommand.java
│                   │   ├── HandcuffCommand.java
│                   │   ├── JailCommand.java
│                   │   ├── LogCommand.java
│                   │   ├── PoliceCommand.java
│                   │   ├── PoliceTabCompleter.java
│                   │   ├── RankCommand.java
│                   │   ├── SalaryCommand.java
│                   │   ├── StatsCommand.java
│                   │   └── WantedCommand.java
│                   ├── gui/
│                   │   └── PoliceGUI.java        ← Police GUI (wanted players list)
│                   ├── listeners/
│                   │   ├── CompassListener.java
│                   │   ├── HandcuffListener.java
│                   │   ├── JailListener.java
│                   │   └── PlayerListener.java
│                   └── managers/
│                       ├── BountyManager.java
│                       ├── CompassManager.java
│                       ├── ConfigManager.java
│                       ├── DisplayManager.java
│                       ├── HandcuffManager.java
│                       ├── JailManager.java
│                       ├── LanguageManager.java
│                       ├── LogManager.java
│                       ├── PlaceholderHook.java
│                       ├── RankManager.java
│                       ├── SalaryManager.java
│                       ├── StatsManager.java
│                       └── WantedManager.java
└── target/
    └── police-plus-2.0.0.jar        ← Built JAR (ready to install)
```

---

## 🏗️ Architecture

### Design Pattern: Manager-Based Architecture

The plugin uses a **Singleton** pattern for each Manager. The main class `PolicePlus` (extending `JavaPlugin`) serves as the entry point and coordinator.

### Startup Sequence:

```
1. PolicePlus.onEnable()
   ├── 2. initializeManagers()        ← Instantiate 12 managers (Singletons)
   │       ├── ConfigManager           ← Load configuration
   │       ├── LanguageManager         ← Load language files
   │       ├── WantedManager           ← Wanted system
   │       ├── JailManager             ← Jail system
   │       ├── CompassManager          ← Compass tracking
   │       ├── DisplayManager          ← Display (actionbar, bossbar, tablist)
   │       ├── HandcuffManager         ← Handcuff system
   │       ├── RankManager             ← Police ranks
   │       ├── LogManager              ← Activity logging
   │       ├── BountyManager           ← Bounty system
   │       ├── SalaryManager           ← Salary system
   │       └── StatsManager            ← Player statistics
   ├── 3. loadConfigurations()        ← Load config.yml and language files
   ├── 4. handcuffManager.loadConfig()← Load handcuff settings (after ConfigManager is ready)
   ├── 5. registerCommands()          ← Register all commands
   ├── 6. registerListeners()         ← Register all event listeners
   ├── 7. startSystems()              ← Start background tasks (timers, drag task)
   ├── 8. cleanupResidualEffects()    ← Clean up leftover potion effects from reloads
   └── 9. registerPlaceholderAPI()    ← Connect to PlaceholderAPI (optional)
```

---

## 📦 Dependencies

| Dependency | Version | Type | Description |
|-----------|---------|------|-------------|
| **Spigot API** | 1.20.4-R0.1-SNAPSHOT | provided | Core Minecraft server API |
| **PlaceholderAPI** | 2.11.6 | provided | Variable placeholder system (optional) |
| **VaultAPI** | 1.7.1 | provided | Economy system (optional) |

---

## ⚙️ Configuration (config.yml)

The `config.yml` contains all configurable settings with bilingual comments (English + Persian):

### Sections:
- **language** — Plugin language (`en` or `fa`)
- **wanted** — Wanted system settings (max level, auto-decay, reset on death)
- **jail** — Jail settings (type: TIME/BLOCKS, durations, distances, allowed blocks)
- **compass** — Compass tracking settings (update interval, max distance)
- **handcuff** — Handcuff settings (name, lore, color, max time, apply blindness)
- **display** — Display settings (stars/number mode, tablist, below-name)
- **bounty** — Bounty system settings
- **logs** — Logging settings
- **economy** — Economy rewards on arrest (reward per wanted level)

### Key Handcuff Settings:
| Setting | Default | Description |
|---------|---------|-------------|
| `handcuff.name` | `"cuff"` | Display name of handcuff item |
| `handcuff.max_time` | `300` | Maximum cuff duration (seconds) |
| `handcuff.accept_plain_item` | `false` | Accept plain items without PDC tag |
| `handcuff.apply_blindness` | `false` | Apply blindness effect to cuffed player |

### Key Economy Settings:
| Setting | Default | Description |
|---------|---------|-------------|
| `economy.enabled` | `true` | Enable economy rewards |
| `economy.reward_per_wanted_level` | `150.0` | Money paid to cop per wanted level of jailed player |

---

## 🎮 Commands

### `/wanted` (alias: `/w`) — Wanted System
**Permission:** `policeplus.wanted.self` (self-view) / `policeplus.wanted` (admin)

| Subcommand | Description |
|-----------|-------------|
| *(none)* | View your own wanted level |
| `set <player> <level>` | Set a player's wanted level |
| `list` | List all wanted players |
| `jail <player>` | Jail a wanted player |
| `unjail <player>` | Release a player from jail |
| `reload` | Reload configuration |
| `gui` | Open police GUI |

### `/jail` (alias: `/j`) — Jail Management
**Permission:** `policeplus.wanted`

| Subcommand | Description |
|-----------|-------------|
| `create <name>` | Create a new TIME jail at your location |
| `createmine <name>` | Create a new BLOCKS (mine) jail |
| `delete <name>` | Delete a jail |
| `spawn` | Set the global release spawn point |
| `list` | List all jails |

### `/police` (alias: `/p`) — Police Commands
**Permission:** `policeplus.police`

| Subcommand | Description |
|-----------|-------------|
| *(none)* | Open police GUI |
| `jail` | Jail nearest player |
| `arrest <player>` | Arrest a specific player |
| `reload` | Reload plugin configuration |

### `/compass` (alias: `/c`) — Compass Tracking
**Permission:** `policeplus.compass`

| Usage | Description |
|-------|-------------|
| `/compass <player>` | Start tracking a player |

### `/cuffe` — Handcuff
**Permission:** `policeplus.handcuff.cuff`

| Usage | Description |
|-------|-------------|
| `/cuffe <player>` | Handcuff a player |
| `/cuffe give <player> [amount]` | Give handcuff items |
| `/cuffe` | Handcuff nearest player (≤3 blocks) |

### `/uncuffe` — Remove Handcuffs
**Permission:** `policeplus.handcuff.uncuff`

| Usage | Description |
|-------|-------------|
| `/uncuffe <player>` | Remove handcuffs from a player |

### Other Commands:
- `/rank` — Manage police ranks (permission: `policeplus.admin`)
- `/bounty` — Bounty system (permission: `policeplus.wanted`)
- `/salary` — Salary management (permission: `policeplus.admin`)
- `/stats` — View player statistics (permission: `policeplus.wanted.self`)
- `/log` — View activity logs (permission: `policeplus.admin`)

---

## 🔐 Permissions

### Core Permissions:
| Permission | Default | Description |
|-----------|---------|-------------|
| `policeplus.wanted.self` | true | View own wanted level |
| `policeplus.wanted` | op | Manage wanted system |
| `policeplus.police` | op | Access police commands |
| `policeplus.compass` | op | Use compass tracking |
| `policeplus.bypass` | op | Bypass jail/handcuff restrictions |
| `policeplus.admin` | op | Full admin access |

### Handcuff Permissions:
| Permission | Default | Description |
|-----------|---------|-------------|
| `policeplus.handcuff.cuff` | op | Handcuff players |
| `policeplus.handcuff.uncuff` | op | Remove handcuffs |
| `policeplus.handcuff.give` | op | Give handcuff items |

---

## 🎯 Drag System (Elastic Dragging)

The handcuff dragging system uses **slowness + velocity + teleport** mechanics (no entities or leashes):

### How It Works:
1. **When handcuffed**: Suspect receives Slowness IV effect (walks very slowly but can still move)
2. **Movement restriction**: Suspect cannot walk beyond **5 blocks** from the cop (elastic leash)
3. **Every 2 ticks** (0.1s), the system checks distance:
   - **< 4.5 blocks**: Do nothing (suspect is close enough)
   - **4.5–8.0 blocks**: Smooth velocity pull toward cop (`velocity × 0.35`)
   - **> 8.0 blocks**: Emergency teleport 1.5 blocks behind cop (preserving yaw/pitch)
4. **When uncuffed**: Slowness effect removed immediately

### Constants:
| Constant | Value | Description |
|----------|-------|-------------|
| `LEASH_RADIUS` | 5.0 | Maximum walking distance from cop |
| `VELOCITY_DISTANCE` | 4.5 | Distance to start velocity pull |
| `TELEPORT_DISTANCE` | 8.0 | Distance for emergency teleport |
| `VELOCITY_STRENGTH` | 0.35 | Velocity multiplier |
| `SLOWNESS_AMPLIFIER` | 4 | Slowness effect level |

---

## 🔧 Jail System

### Two Jail Types:
1. **TIME** — Player is jailed for a set duration (minutes)
2. **BLOCKS** — Player must mine a set number of blocks to be released

### Mine-Jail Cuboid Regions:
- Admins can set `pos1` and `pos2` to define a 3D cuboid mine region
- The region is filled with COBBLESTONE when a player is jailed
- Mining allowed blocks (COBBLESTONE, STONE by default) counts toward release
- Blocks auto-regenerate after 3 seconds (only if the location is still AIR)
- Non-jailed players cannot place or break blocks inside mine regions
- OPs in Creative mode can edit mine regions freely

---

## 🏗️ Build

### Prerequisites:
- **Java JDK 21** or higher
- **Maven 3.6+**

### Build Command:
```bash
mvn clean package -DskipTests
```

### Output:
```
target/police-plus-2.0.0.jar
```

### Installation:
1. Copy `police-plus-2.0.0.jar` to your server's `plugins/` folder
2. Restart the server
3. Configuration files are generated in `plugins/PolicePlus/`
4. Edit settings and use `/police reload` to apply changes

---

## 📊 Technical Summary

| Feature | Value |
|---------|-------|
| **Language** | Java 21 |
| **Build System** | Maven |
| **API Version** | Spigot 1.20.4 |
| **Minimum Server** | 1.17+ |
| **Architecture** | Manager-Based (Singleton) |
| **Total Classes** | 25+ |
| **Commands** | 11 |
| **Listeners** | 4 |
| **Managers** | 12 |
| **Languages** | English (en), Persian (fa) |
| **Storage** | YAML files |
| **PlaceholderAPI** | ✅ Supported |
| **Vault Economy** | ✅ Supported |
| **JAR Version** | 2.0.0 |