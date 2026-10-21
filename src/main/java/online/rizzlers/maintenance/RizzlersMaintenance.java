package online.rizzlers.maintenance;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "rizzlersmaintenance",
        name = "RizzlersMaintenance",
        version = "1.0.0",
        description = "Network-wide maintenance mode with UUID allowlist for Velocity proxy on RIZZLERS network.",
        authors = {"RIZZLERS"}
)
public final class RizzlersMaintenance {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final MaintenanceManager manager;

    @Inject
    public RizzlersMaintenance(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.manager = new MaintenanceManager(dataDirectory, logger);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        manager.load();

        CommandManager commandManager = server.getCommandManager();
        CommandMeta meta = commandManager.metaBuilder("rmaint")
                .aliases("maintenance", "rizzlersmaintenance")
                .plugin(this)
                .build();
        commandManager.register(meta, new MaintenanceCommand(server, manager, logger));

        logger.info("==================================================");
        logger.info("RizzlersMaintenance v1.0.0 initialized successfully.");
        logger.info("Global Maintenance is currently: {}", manager.isEnabled() ? "ENABLED (ON)" : "DISABLED (OFF)");
        logger.info("Allowlisted staff accounts loaded: {}", manager.getAllowedEntries().size());
        logger.info("==================================================");
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onLogin(LoginEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        boolean bypass = player.hasPermission("rizzlersmaintenance.bypass");

        if (manager.isAuthorized(player.getUniqueId(), bypass)) {
            logger.info("Allowing staff player {} ({}) during maintenance", player.getUsername(), player.getUniqueId());
            return;
        }

        logger.info("Rejecting player {} ({}) - network maintenance is active", player.getUsername(), player.getUniqueId());
        event.setResult(ResultedEvent.ComponentResult.denied(manager.getFormattedKickMessage()));
    }

    public MaintenanceManager getManager() {
        return manager;
    }
}
