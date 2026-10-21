package online.rizzlers.maintenance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class MaintenanceManager {

    public static final String DEFAULT_KICK_MESSAGE =
            "<red><bold>RIZZLERS NETWORK IS UNDER MAINTENANCE</bold></red>\n<gray>Our team is currently performing scheduled updates. Please check back later!</gray>";

    private final Path dataDirectory;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private volatile String kickMessage = DEFAULT_KICK_MESSAGE;
    private volatile Component cachedKickComponent = miniMessage.deserialize(DEFAULT_KICK_MESSAGE);

    // Map of allowed UUID -> optional username / label for display
    private final ConcurrentHashMap<UUID, String> allowed = new ConcurrentHashMap<>();

    public MaintenanceManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    public synchronized void load() {
        try {
            Files.createDirectories(dataDirectory);
            loadConfig();
            loadAllowlist();
        } catch (IOException e) {
            logger.error("Failed to initialize or load maintenance directory/files at {}", dataDirectory, e);
        }
    }

    public synchronized void reload() {
        logger.info("Reloading maintenance configuration and allowlist from disk...");
        loadConfig();
        loadAllowlist();
        logger.info("Maintenance reload complete: status is {}, {} allowlisted entries.",
                enabled.get() ? "ENABLED" : "DISABLED", allowed.size());
    }

    private void loadConfig() {
        Path configFile = dataDirectory.resolve("config.properties");
        if (!Files.exists(configFile)) {
            try {
                saveConfig();
            } catch (IOException e) {
                logger.error("Could not create default config.properties", e);
            }
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq < 0) continue;
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();

                if ("enabled".equalsIgnoreCase(key)) {
                    this.enabled.set(Boolean.parseBoolean(value));
                } else if ("kick-message".equalsIgnoreCase(key)) {
                    setKickMessage(value.replace("\\n", "\n"));
                }
            }
        } catch (IOException e) {
            logger.error("Failed to read config.properties", e);
        }
    }

    public synchronized void saveConfig() throws IOException {
        Files.createDirectories(dataDirectory);
        Path configFile = dataDirectory.resolve("config.properties");
        try (BufferedWriter writer = Files.newBufferedWriter(configFile)) {
            writer.write("# RIZZLERS Velocity Maintenance Mode Configuration\n");
            writer.write("# enabled: true or false\n");
            writer.write("enabled=" + enabled.get() + "\n");
            writer.write("# MiniMessage format. Use \\n for newlines in properties file.\n");
            writer.write("kick-message=" + kickMessage.replace("\n", "\\n") + "\n");
        }
    }

    private void loadAllowlist() {
        Path allowlistFile = dataDirectory.resolve("allowlist.txt");
        if (!Files.exists(allowlistFile)) {
            try {
                saveAllowlist();
            } catch (IOException e) {
                logger.error("Could not create default allowlist.txt", e);
            }
            return;
        }

        // We load into a fresh map first so that if read succeeds, we don't end up in an inconsistent state
        Map<UUID, String> newAllowed = new ConcurrentHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(allowlistFile)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                // Format: <uuid> [optional username/comment]
                String uuidPart = trimmed;
                String namePart = "";
                int commentIndex = trimmed.indexOf('#');
                if (commentIndex >= 0) {
                    namePart = trimmed.substring(commentIndex + 1).trim();
                    uuidPart = trimmed.substring(0, commentIndex).trim();
                } else {
                    int spaceIndex = trimmed.indexOf(' ');
                    if (spaceIndex >= 0) {
                        namePart = trimmed.substring(spaceIndex + 1).trim();
                        uuidPart = trimmed.substring(0, spaceIndex).trim();
                    }
                }

                if (uuidPart.isEmpty()) continue;

                try {
                    UUID uuid = OfflineUuidUtil.parseOrResolve(uuidPart);
                    newAllowed.put(uuid, namePart.isEmpty() ? uuidPart : namePart);
                } catch (IllegalArgumentException ex) {
                    logger.warn("Ignoring invalid UUID/username at allowlist.txt line {}: '{}'", lineNumber, trimmed);
                }
            }
            allowed.clear();
            allowed.putAll(newAllowed);
        } catch (IOException e) {
            logger.error("Failed to read allowlist.txt", e);
        }
    }

    public synchronized void saveAllowlist() throws IOException {
        Files.createDirectories(dataDirectory);
        Path allowlistFile = dataDirectory.resolve("allowlist.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(allowlistFile)) {
            writer.write("# RIZZLERS Maintenance Allowlist\n");
            writer.write("# Each line contains one authorized staff UUID, optionally followed by their username for reference.\n");
            writer.write("# Example: 123e4567-e89b-12d3-a456-426614174000 # StaffMember\n\n");
            for (Map.Entry<UUID, String> entry : allowed.entrySet()) {
                String label = entry.getValue();
                if (label != null && !label.isEmpty() && !label.equals(entry.getKey().toString())) {
                    writer.write(entry.getKey() + " # " + label + "\n");
                } else {
                    writer.write(entry.getKey() + "\n");
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        this.enabled.set(value);
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public void setKickMessage(String message) {
        this.kickMessage = message != null ? message : DEFAULT_KICK_MESSAGE;
        this.cachedKickComponent = miniMessage.deserialize(this.kickMessage);
    }

    public Component getFormattedKickMessage() {
        return cachedKickComponent;
    }

    public boolean isAuthorized(UUID uuid, boolean hasBypassPermission) {
        if (!enabled.get()) {
            return true;
        }
        if (hasBypassPermission) {
            return true;
        }
        return uuid != null && allowed.containsKey(uuid);
    }

    public boolean addAllowlist(UUID uuid, String username) throws IOException {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        String label = (username != null && !username.isBlank()) ? username : uuid.toString();
        String prev = allowed.put(uuid, label);
        saveAllowlist();
        return prev == null;
    }

    public boolean removeAllowlist(UUID uuid) throws IOException {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        String removed = allowed.remove(uuid);
        if (removed != null) {
            saveAllowlist();
            return true;
        }
        return false;
    }

    public boolean isAllowlisted(UUID uuid) {
        return uuid != null && allowed.containsKey(uuid);
    }

    public Map<UUID, String> getAllowedEntries() {
        return Collections.unmodifiableMap(allowed);
    }

    public Set<UUID> getAllowedUuids() {
        return Collections.unmodifiableSet(allowed.keySet());
    }
}
