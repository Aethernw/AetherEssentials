# AetherEssentials

![AetherEssentials](AetherEssentials.png)

Official essentials plugin for Aethernw, built for Paper 1.20+ servers.

A high-performance essentials plugin with MySQL-backed warps, spawn management, item repair, hunger restore and a complete teleport request (TPA) system. All database and I/O operations run asynchronously so the server thread is never blocked.

## Features

- Virtual crafting table and Ender Chest access
- MySQL-backed warp system with optional Redis cache layer
- Spawn point management
- Item repair (single item or full inventory)
- Hunger and saturation restore
- TPA system with 30-second request timeout, accept, deny and cancel
- All player messages stored in `messages.yml` and fully customizable
- Asynchronous database and Redis operations, zero main-thread blocking
- Async teleportation via Paper's `teleportAsync` (no chunk loading on the main thread)

## Requirements

- Paper 1.20.4 or newer
- Java 17 or newer
- MySQL server (required for warp storage)
- Redis (optional, enables warp caching)

## Installation

1. Place `AetherEssentials-1.0.0.jar` into the `plugins` folder.
2. Start the server once, then stop it.
3. Edit `config.yml` and fill in your MySQL credentials under the `database:` section.
4. Optionally set `redis.enabled: true` and configure Redis under the `redis:` section.
5. Restart the server. Database tables are created automatically on first run.

## Commands

| Command | Description |
|---|---|
| `/craft` | Opens a virtual crafting table |
| `/enderchest [player]` | Opens an Ender Chest inventory |
| `/setwarp <name>` | Creates a new warp at your location |
| `/warp <name>` | Teleports to a warp |
| `/warps` | Lists all warps |
| `/delwarp <name>` | Deletes a warp |
| `/spawn` | Teleports to the spawn point |
| `/setspawn` | Sets the spawn point |
| `/repair [all]` | Repairs the item in hand, or the full inventory with `all` |
| `/feed [player]` | Restores hunger and saturation for yourself or another player |
| `/tpa <player>` | Sends a teleport request |
| `/tpaccept [player]` | Accepts a teleport request |
| `/tpadeny [player]` | Denies a teleport request |
| `/tpacancel` | Cancels all outgoing teleport requests |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `aether.command.craft` | true | Use `/craft` |
| `aether.command.enderchest` | true | Use `/enderchest` on yourself |
| `aether.command.enderchest.others` | op | Open other players' Ender Chests |
| `aether.command.setwarp` | op | Create warps |
| `aether.command.warp` | true | Teleport to warps |
| `aether.command.warps` | true | View the warp list |
| `aether.command.delwarp` | op | Delete warps |
| `aether.command.spawn` | true | Teleport to spawn |
| `aether.command.setspawn` | op | Set the spawn point |
| `aether.command.repair` | true | Repair the held item |
| `aether.command.repair.all` | op | Repair the full inventory |
| `aether.command.feed` | true | Feed yourself |
| `aether.command.feed.others` | op | Feed other players |
| `aether.command.tpa` | true | Use all TPA commands |
| `aether.*` | op | All AetherEssentials permissions |

## Configuration

### config.yml

Contains only infrastructure settings: MySQL connection, optional Redis and the spawn location. The spawn point is stored using Bukkit's native `Location` serialization under the `spawn:` section.

### messages.yml

All player-facing messages live in this file and use `&` color codes (for example `&a`, `&c`). Default messages are in Turkish; edit the file to translate or customize them. No restart required after editing if you keep the file structure valid, otherwise restart the server.

## How It Works

```
AetherEssentials (main class)
 - ConfigManager    loads config.yml and messages.yml, handles spawn location and message lookup
 - DatabaseManager  HikariCP pool, every MySQL query runs on an async thread
 - RedisManager     optional Jedis cache layer, fully skipped when disabled
 - WarpManager      in-memory warp cache (ConcurrentHashMap), write-through to MySQL and Redis
 - TpaManager       in-memory TPA requests with a 1-second expiry sweep
 - WarpLocation     plain POJO carrying warp data between layers
```

- All commands are registered on the main class and dispatched through a single switch. Each command is guarded twice: the `permission:` field in `plugin.yml` and an explicit `hasPermission` check in code.
- Warps are loaded from MySQL at startup, kept in memory, and persisted asynchronously on every change.
- Redis is a pure cache. When disabled, the plugin simply skips it and MySQL remains the source of truth.
- TPA requests are keyed by target UUID so each player has one pending request at a time. Expired requests are swept every second and the sender is notified.

## Build

```bash
mvn package
```

The shaded jar (HikariCP and Jedis relocated under `net.aethernw.essentials.libs`) is output to `target/AetherEssentials-1.0.0.jar`.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file.
