package online.rizzlers.maintenance;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MaintenanceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceTest.class);

    @TempDir
    Path tempDir;

    private MaintenanceManager manager;

    @BeforeEach
    void setUp() {
        manager = new MaintenanceManager(tempDir, LOGGER);
        manager.load();
    }

    @Test
    @DisplayName("1. Maintenance disabled: normal players can connect")
    void testMaintenanceDisabledAllowsNormalPlayers() {
        manager.setEnabled(false);
        UUID randomPlayer = UUID.randomUUID();

        assertTrue(manager.isAuthorized(randomPlayer, false),
                "When maintenance is disabled, normal players must always be authorized to connect.");
    }

    @Test
    @DisplayName("2. Maintenance enabled: unauthorized players are rejected")
    void testMaintenanceEnabledRejectsUnauthorized() {
        manager.setEnabled(true);
        UUID randomPlayer = UUID.randomUUID();

        assertFalse(manager.isAuthorized(randomPlayer, false),
                "When maintenance is enabled, unauthorized players must be rejected.");
    }

    @Test
    @DisplayName("3. Maintenance enabled: allowlisted UUIDs can connect")
    void testMaintenanceEnabledAllowsAllowlistedUuids() throws IOException {
        manager.setEnabled(true);
        UUID staffUuid = UUID.randomUUID();

        manager.addAllowlist(staffUuid, "StaffMember");

        assertTrue(manager.isAuthorized(staffUuid, false),
                "When maintenance is enabled, allowlisted UUIDs must be permitted to connect.");
    }

    @Test
    @DisplayName("4. Offline-mode UUIDs are handled consistently")
    void testOfflineModeUuidResolutionConsistency() {
        String username = "Alex";
        UUID offlineUuid1 = OfflineUuidUtil.getOfflineUuid(username);
        UUID offlineUuid2 = OfflineUuidUtil.getOfflineUuid(username);
        UUID resolvedUuid = OfflineUuidUtil.parseOrResolve(username);

        assertEquals(offlineUuid1, offlineUuid2, "Offline UUID generation must be deterministic.");
        assertEquals(offlineUuid1, resolvedUuid, "parseOrResolve for username must match offline UUID.");

        // Check against standard Mojang offline UUID generation (MD5 of "OfflinePlayer:" + name)
        UUID expected = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(expected, offlineUuid1);
    }

    @Test
    @DisplayName("5. State persists after a restart")
    void testStatePersistenceAcrossRestart() throws IOException {
        manager.setEnabled(true);
        manager.setKickMessage("<red>Custom Maintenance Notice</red>");
        UUID staffUuid = UUID.randomUUID();
        manager.addAllowlist(staffUuid, "AdminUser");
        manager.saveConfig();

        // Simulate proxy restart with a fresh manager reading the same data directory
        MaintenanceManager reloadedManager = new MaintenanceManager(tempDir, LOGGER);
        reloadedManager.load();

        assertTrue(reloadedManager.isEnabled(), "Enabled state must persist after restart.");
        assertEquals("<red>Custom Maintenance Notice</red>", reloadedManager.getKickMessage(), "Custom kick message must persist.");
        assertTrue(reloadedManager.isAllowlisted(staffUuid), "Allowlisted UUIDs must persist after restart.");
    }

    @Test
    @DisplayName("6. Adding and removing allowlist entries works")
    void testAddAndRemoveAllowlistEntries() throws IOException {
        UUID testUuid = UUID.randomUUID();

        assertFalse(manager.isAllowlisted(testUuid));
        boolean added = manager.addAllowlist(testUuid, "Moderator");
        assertTrue(added, "addAllowlist should return true for a new entry.");
        assertTrue(manager.isAllowlisted(testUuid));

        boolean removed = manager.removeAllowlist(testUuid);
        assertTrue(removed, "removeAllowlist should return true when removing an existing entry.");
        assertFalse(manager.isAllowlisted(testUuid));
    }

    @Test
    @DisplayName("7. Invalid UUIDs and malformed configuration are handled safely")
    void testInvalidUuidsAndMalformedConfigurationHandledSafely() throws IOException {
        // Write corrupted configuration and corrupted allowlist
        Path configFile = tempDir.resolve("config.properties");
        Files.writeString(configFile, "invalid-line-without-equals\nenabled=invalid_boolean\n");

        Path allowlistFile = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlistFile, "not-a-valid-uuid-and-not-valid-username-!!@@##$$\n\n# Comment\n123e4567-e89b-12d3-a456-426614174000 # ValidAdmin\n");

        MaintenanceManager robustManager = new MaintenanceManager(tempDir, LOGGER);
        assertDoesNotThrow(robustManager::load, "Loading malformed files must never throw an unhandled exception.");

        // Valid entry was still loaded safely
        UUID validUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertTrue(robustManager.isAllowlisted(validUuid), "Valid entry must be loaded despite malformed lines.");
    }

    @Test
    @DisplayName("8. Console and unauthorized command access are handled correctly")
    void testConsoleAndUnauthorizedCommandAccess() {
        ProxyServer mockServer = (ProxyServer) Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (p, m, a) -> null
        );

        MaintenanceCommand cmd = new MaintenanceCommand(mockServer, manager, LOGGER);

        // 1. Console command source
        ConsoleCommandSource console = (ConsoleCommandSource) Proxy.newProxyInstance(
                ConsoleCommandSource.class.getClassLoader(),
                new Class<?>[]{ConsoleCommandSource.class},
                (p, m, a) -> {
                    if ("hasPermission".equals(m.getName())) return true;
                    return null;
                }
        );
        SimpleCommand.Invocation consoleInvocation = createInvocation(console);
        assertTrue(cmd.hasPermission(consoleInvocation), "Console must always have permission.");

        // 2. Unauthorized normal player
        Player unprivilegedPlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (p, m, a) -> {
                    if ("hasPermission".equals(m.getName())) return false;
                    return null;
                }
        );
        SimpleCommand.Invocation unprivilegedInvocation = createInvocation(unprivilegedPlayer);
        assertFalse(cmd.hasPermission(unprivilegedInvocation), "Normal players without permission must be denied.");

        // 3. Authorized admin player with rizzlersmaintenance.admin permission
        Player adminPlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (p, m, a) -> {
                    if ("hasPermission".equals(m.getName())) {
                        return MaintenanceCommand.ADMIN_PERMISSION.equals(a[0]);
                    }
                    return null;
                }
        );
        SimpleCommand.Invocation adminInvocation = createInvocation(adminPlayer);
        assertTrue(cmd.hasPermission(adminInvocation), "Admin players with permission must be allowed.");
    }

    private SimpleCommand.Invocation createInvocation(CommandSource source) {
        return new SimpleCommand.Invocation() {
            @Override
            public CommandSource source() {
                return source;
            }

            @Override
            public String[] arguments() {
                return new String[0];
            }

            @Override
            public String alias() {
                return "rmaint";
            }
        };
    }
}
