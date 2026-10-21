# RizzlersMaintenance (Velocity)

High-performance, network-wide maintenance mode plugin with persistent UUID allowlisting designed specifically for the **RIZZLERS** Minecraft network on **Velocity 4.3.0**.

---

## Key Features

- **Network-Wide Protection**: Enforces maintenance mode at the Velocity proxy ingress layer. Rejects unauthorized players before they touch LimboAuth, Lobby, Survival, or any other backend server.
- **Offline/Cracked Support**: Fully supports offline-mode UUID generation (`OfflinePlayer:<name>`) and online UUIDs. Seamlessly compatible with Velocity offline setups and LimboAuth.
- **Dual Access Control**:
  - Console can always manage maintenance without risk of lockout.
  - Dedicated proxy permission `rizzlersmaintenance.admin` allows authorized staff in-game management.
  - Proxy permission `rizzlersmaintenance.bypass` allows automatic bypass without needing an allowlist entry.
  - Explicit UUID-based allowlist ensures security regardless of backend OP or offline UUID changes.
- **Persistent Storage**:
  - Separate `config.properties` for plugin configuration (enabled state, MiniMessage kick message).
  - Separate `allowlist.txt` for authorized staff UUIDs with optional username comments.
  - Reloading never overwrites or wipes allowlist entries.
- **Non-Disruptive Staff Experience**:
  - Enabling maintenance mode disconnects unauthorized players immediately, while keeping online authorized staff connected.
  - Tab-completion support for subcommands, online players, and allowlisted staff.

---

## Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/rmaint on` | Enables network-wide maintenance mode | `rizzlersmaintenance.admin` / Console |
| `/rmaint off` | Disables maintenance mode, opening network to all | `rizzlersmaintenance.admin` / Console |
| `/rmaint status` | Displays active maintenance status & allowlist count | `rizzlersmaintenance.admin` / Console |
| `/rmaint add <player\|uuid>` | Adds a staff account (by username or UUID) | `rizzlersmaintenance.admin` / Console |
| `/rmaint remove <player\|uuid>` | Removes a staff account from the allowlist | `rizzlersmaintenance.admin` / Console |
| `/rmaint list` | Lists all allowlisted staff accounts | `rizzlersmaintenance.admin` / Console |
| `/rmaint reload` | Reloads configuration and allowlist from disk | `rizzlersmaintenance.admin` / Console |

---

## Configuration Files

The plugin stores data in `plugins/rizzlersmaintenance/`:

### `config.properties`
```properties
# RIZZLERS Velocity Maintenance Mode Configuration
# enabled: true or false
enabled=false
# MiniMessage format. Use \n for newlines in properties file.
kick-message=<red><bold>RIZZLERS NETWORK IS UNDER MAINTENANCE</bold></red>\n<gray>Our team is currently performing scheduled updates. Please check back later!</gray>
```

### `allowlist.txt`
```text
# RIZZLERS Maintenance Allowlist
# Each line contains one authorized staff UUID, optionally followed by their username for reference.
# Example:
e9734e9e-5f91-450e-9404-51781e695be2 # Alex
```

---

## Installation & Deployment (Plugged.host / Pelican)

> [!IMPORTANT]
> Install this plugin **ONLY on your Velocity proxy**.
> Do **NOT** install it on backend servers (Lobby or Survival).

1. **Back up Velocity**: Create a backup or snapshot of your Velocity proxy server in the control panel.
2. **Access Panel**: Open your Velocity proxy server in the **Plugged.host / Pelican** control panel.
3. **Open Files**: Navigate to the `plugins/` directory.
4. **Upload JAR**: Upload `RizzlersMaintenance-1.0.0.jar` into the `plugins/` directory.
5. **Restart Proxy**: Restart the Velocity proxy from the control panel.
6. **Add Trusted Staff**:
   From the Velocity console, run:
   ```text
   rmaint add <YourStaffUsernameOrUUID>
   ```
7. **Test Maintenance**:
   - Turn maintenance ON: `/rmaint on`
   - Test connecting with an unauthorized account: confirm they are rejected with the kick message and cannot reach Lobby or Survival.
   - Test connecting with your authorized staff account: confirm you connect normally.
   - Turn maintenance OFF: `/rmaint off`

---

## Rollback Instructions

If you need to roll back:
1. Stop the Velocity proxy.
2. Delete `plugins/RizzlersMaintenance-1.0.0.jar` and `plugins/rizzlersmaintenance/` directory.
3. Restart the Velocity proxy.

---

## Building from Source

This project compiles on GitHub Actions with Temurin JDK 25 and Velocity API 4.3.0:

```bash
mvn clean verify package
```

All 8 automated JUnit 5 test suites execute during `mvn clean verify`. Output JAR: `target/RizzlersMaintenance-1.0.0.jar`.
