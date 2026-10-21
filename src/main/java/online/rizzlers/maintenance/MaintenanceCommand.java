package online.rizzlers.maintenance;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MaintenanceCommand implements SimpleCommand {

    public static final String ADMIN_PERMISSION = "rizzlersmaintenance.admin";

    private final ProxyServer server;
    private final MaintenanceManager manager;
    private final Logger logger;

    public MaintenanceCommand(ProxyServer server, MaintenanceManager manager, Logger logger) {
        this.server = server;
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        CommandSource source = invocation.source();
        return source instanceof ConsoleCommandSource || source.hasPermission(ADMIN_PERMISSION);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!hasPermission(invocation)) {
            source.sendMessage(Component.text("You do not have permission to execute this command.", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            sendHelp(source);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on" -> handleOn(source);
            case "off" -> handleOff(source);
            case "status" -> handleStatus(source);
            case "add" -> handleAdd(source, args);
            case "remove" -> handleRemove(source, args);
            case "list" -> handleList(source);
            case "reload" -> handleReload(source);
            default -> sendHelp(source);
        }
    }

    private void handleOn(CommandSource source) {
        if (manager.isEnabled()) {
            source.sendMessage(Component.text("Maintenance mode is already enabled.", NamedTextColor.YELLOW));
            return;
        }

        manager.setEnabled(true);
        try {
            manager.saveConfig();
        } catch (IOException e) {
            logger.error("Failed to save maintenance state to config.properties", e);
            source.sendMessage(Component.text("Warning: Could not save enabled state to disk. Check console logs.", NamedTextColor.RED));
        }

        // Disconnect unauthorized online players, keeping authorized staff connected.
        int disconnectedCount = 0;
        Component kickMsg = manager.getFormattedKickMessage();
        for (Player player : server.getAllPlayers()) {
            boolean bypass = player.hasPermission("rizzlersmaintenance.bypass");
            if (!manager.isAuthorized(player.getUniqueId(), bypass)) {
                player.disconnect(kickMsg);
                disconnectedCount++;
            }
        }

        source.sendMessage(Component.text("RIZZLERS maintenance mode ENABLED. Disconnected " + disconnectedCount + " unauthorized player(s).", NamedTextColor.GREEN));
        logger.info("Maintenance ENABLED by {}. Disconnected {} unauthorized players.", getSourceName(source), disconnectedCount);
    }

    private void handleOff(CommandSource source) {
        if (!manager.isEnabled()) {
            source.sendMessage(Component.text("Maintenance mode is already disabled.", NamedTextColor.YELLOW));
            return;
        }

        manager.setEnabled(false);
        try {
            manager.saveConfig();
        } catch (IOException e) {
            logger.error("Failed to save maintenance state to config.properties", e);
            source.sendMessage(Component.text("Warning: Could not save disabled state to disk. Check console logs.", NamedTextColor.RED));
        }

        source.sendMessage(Component.text("RIZZLERS maintenance mode DISABLED. Network is now open to all players.", NamedTextColor.GREEN));
        logger.info("Maintenance DISABLED by {}.", getSourceName(source));
    }

    private void handleStatus(CommandSource source) {
        boolean on = manager.isEnabled();
        NamedTextColor statusColor = on ? NamedTextColor.RED : NamedTextColor.GREEN;
        String statusText = on ? "ACTIVE (ON)" : "INACTIVE (OFF)";

        source.sendMessage(Component.text("=== RIZZLERS Maintenance Status ===", NamedTextColor.GOLD));
        source.sendMessage(Component.text("Status: ", NamedTextColor.GRAY).append(Component.text(statusText, statusColor)));
        source.sendMessage(Component.text("Allowlisted Staff: ", NamedTextColor.GRAY).append(Component.text(manager.getAllowedEntries().size() + " accounts", NamedTextColor.AQUA)));
        source.sendMessage(Component.text("Connected Players: ", NamedTextColor.GRAY).append(Component.text(server.getPlayerCount() + " online", NamedTextColor.WHITE)));
    }

    private void handleAdd(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /rmaint add <player-or-uuid>", NamedTextColor.RED));
            return;
        }

        String target = args[1].trim();
        UUID uuid;
        String label;

        // Check if an online player matches this username on the proxy
        Optional<Player> onlinePlayer = server.getPlayer(target);
        if (onlinePlayer.isPresent()) {
            Player p = onlinePlayer.get();
            uuid = p.getUniqueId();
            label = p.getUsername();
        } else {
            try {
                uuid = OfflineUuidUtil.parseOrResolve(target);
                label = target;
            } catch (IllegalArgumentException e) {
                source.sendMessage(Component.text("Invalid UUID or username: '" + target + "'. Please enter a valid player name or UUID.", NamedTextColor.RED));
                return;
            }
        }

        try {
            manager.addAllowlist(uuid, label);
            source.sendMessage(Component.text("Successfully added ", NamedTextColor.GREEN)
                    .append(Component.text(label, NamedTextColor.YELLOW))
                    .append(Component.text(" (" + uuid + ") to the maintenance allowlist.", NamedTextColor.GREEN)));
            logger.info("Added {} ({}) to maintenance allowlist by {}", label, uuid, getSourceName(source));
        } catch (IOException e) {
            logger.error("Failed to persist allowlist.txt", e);
            source.sendMessage(Component.text("Error saving allowlist to disk. Check proxy console logs.", NamedTextColor.RED));
        }
    }

    private void handleRemove(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /rmaint remove <player-or-uuid>", NamedTextColor.RED));
            return;
        }

        String target = args[1].trim();
        UUID resolvedUuid = null;

        // First check if target matches a known username in the allowlist
        for (Map.Entry<UUID, String> entry : manager.getAllowedEntries().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(target)) {
                resolvedUuid = entry.getKey();
                break;
            }
        }

        // If not found by label, attempt to parse as UUID or offline player UUID
        if (resolvedUuid == null) {
            try {
                resolvedUuid = OfflineUuidUtil.parseOrResolve(target);
            } catch (IllegalArgumentException ignored) {}
        }

        if (resolvedUuid == null || !manager.isAllowlisted(resolvedUuid)) {
            source.sendMessage(Component.text("No allowlist entry found for: '" + target + "'.", NamedTextColor.RED));
            return;
        }

        try {
            manager.removeAllowlist(resolvedUuid);
            source.sendMessage(Component.text("Successfully removed " + target + " (" + resolvedUuid + ") from maintenance allowlist.", NamedTextColor.GREEN));
            logger.info("Removed {} ({}) from maintenance allowlist by {}", target, resolvedUuid, getSourceName(source));
        } catch (IOException e) {
            logger.error("Failed to update allowlist.txt", e);
            source.sendMessage(Component.text("Error saving allowlist to disk. Check proxy console logs.", NamedTextColor.RED));
        }
    }

    private void handleList(CommandSource source) {
        Map<UUID, String> entries = manager.getAllowedEntries();
        if (entries.isEmpty()) {
            source.sendMessage(Component.text("The maintenance allowlist is currently empty.", NamedTextColor.YELLOW));
            return;
        }

        source.sendMessage(Component.text("=== Maintenance Allowlist (" + entries.size() + ") ===", NamedTextColor.GOLD));
        for (Map.Entry<UUID, String> entry : entries.entrySet()) {
            String label = entry.getValue();
            if (label != null && !label.isEmpty() && !label.equals(entry.getKey().toString())) {
                source.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                        .append(Component.text(label, NamedTextColor.YELLOW))
                        .append(Component.text(" (" + entry.getKey() + ")", NamedTextColor.DARK_GRAY)));
            } else {
                source.sendMessage(Component.text("- " + entry.getKey(), NamedTextColor.GRAY));
            }
        }
    }

    private void handleReload(CommandSource source) {
        manager.reload();
        source.sendMessage(Component.text("Maintenance configuration and allowlist reloaded from disk.", NamedTextColor.GREEN));
        logger.info("Maintenance reloaded by {}", getSourceName(source));
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(Component.text("=== RIZZLERS Maintenance Commands ===", NamedTextColor.GOLD));
        source.sendMessage(Component.text("/rmaint on", NamedTextColor.YELLOW).append(Component.text(" - Enable network maintenance mode", NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/rmaint off", NamedTextColor.YELLOW).append(Component.text(" - Disable network maintenance mode", NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/rmaint status", NamedTextColor.YELLOW).append(Component.text(" - Check maintenance status", NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/rmaint add <player|uuid>", NamedTextColor.YELLOW).append(Component.text(" - Allowlist staff account", NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/rmaint remove <player|uuid>", NamedTextColor.YELLOW).append(Component.text(" - Remove staff account", NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/rmaint list", NamedTextColor.YELLOW).append(Component.text(" - List allowlisted staff", NamedTextColor.GRAY)));
        source.sendMessage(Component.text("/rmaint reload", NamedTextColor.YELLOW).append(Component.text(" - Reload config from disk", NamedTextColor.GRAY)));
    }

    private String getSourceName(CommandSource source) {
        if (source instanceof Player p) {
            return p.getUsername() + " (" + p.getUniqueId() + ")";
        }
        return "Console";
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> list = List.of("on", "off", "status", "add", "remove", "list", "reload");
            return list.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && "remove".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (Map.Entry<UUID, String> entry : manager.getAllowedEntries().entrySet()) {
                if (entry.getValue().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    matches.add(entry.getValue());
                } else if (entry.getKey().toString().startsWith(prefix)) {
                    matches.add(entry.getKey().toString());
                }
            }
            return matches;
        }
        if (args.length == 2 && "add".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return server.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return Collections.emptyList();
    }
}
