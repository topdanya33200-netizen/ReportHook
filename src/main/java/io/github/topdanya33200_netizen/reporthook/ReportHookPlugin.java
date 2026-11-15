package io.github.topdanya33200_netizen.reporthook;

import io.github.topdanya33200_netizen.reporthook.command.ReportCommand;
import io.github.topdanya33200_netizen.reporthook.command.ReportsCommand;
import io.github.topdanya33200_netizen.reporthook.config.Messages;
import io.github.topdanya33200_netizen.reporthook.config.Settings;
import io.github.topdanya33200_netizen.reporthook.discord.DiscordSettings;
import io.github.topdanya33200_netizen.reporthook.discord.DiscordWebhookClient;
import io.github.topdanya33200_netizen.reporthook.discord.WebhookUrl;
import io.github.topdanya33200_netizen.reporthook.gui.MenuListener;
import io.github.topdanya33200_netizen.reporthook.hook.ReportHookExpansion;
import io.github.topdanya33200_netizen.reporthook.listener.ChatListener;
import io.github.topdanya33200_netizen.reporthook.listener.ConnectionListener;
import io.github.topdanya33200_netizen.reporthook.service.ChatHistory;
import io.github.topdanya33200_netizen.reporthook.service.CooldownTracker;
import io.github.topdanya33200_netizen.reporthook.service.ReportService;
import io.github.topdanya33200_netizen.reporthook.storage.SqliteReportRepository;
import io.github.topdanya33200_netizen.reporthook.util.Tasks;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Entry point: wires configuration, storage, Discord and the commands together.
 */
public final class ReportHookPlugin extends JavaPlugin {

    private volatile Settings settings;
    private volatile Messages messages;
    private Tasks tasks;
    private ChatHistory chatHistory;
    private CooldownTracker cooldowns;
    private SqliteReportRepository repository;
    private DiscordWebhookClient discord;
    private ReportService reports;
    private @Nullable ReportHookExpansion expansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages.saveBundled(this);
        loadConfiguration();

        tasks = new Tasks(this);
        chatHistory = new ChatHistory();
        cooldowns = new CooldownTracker();
        applyRuntimeSettings();

        repository = new SqliteReportRepository(getDataFolder().toPath().resolve("reports.db"));
        try {
            repository.open();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Could not open the report database, disabling ReportHook", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        discord = new DiscordWebhookClient(userAgent());
        reports = new ReportService(this, repository, discord, chatHistory, cooldowns);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new MenuListener(), this);
        pluginManager.registerEvents(new ChatListener(this), this);
        pluginManager.registerEvents(new ConnectionListener(this), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(new ReportCommand(this).node(), "Report a player to the staff team", List.of());
            commands.register(new ReportsCommand(this).node(), "Review and manage player reports", List.of("reportlist"));
        });

        hookPlaceholderApi();
        reports.start();
        logDiscordState();
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        if (reports != null) {
            reports.shutdown();
        }
        if (discord != null) {
            discord.close();
        }
        if (repository != null) {
            repository.close();
        }
        if (tasks != null) {
            tasks.cancelAll();
        }
    }

    /** Re-reads config.yml and the language file; used by {@code /reports reload}. */
    public void reload() {
        reloadConfig();
        Messages.saveBundled(this);
        loadConfiguration();
        applyRuntimeSettings();
        hookPlaceholderApi();
        logDiscordState();
    }

    private void loadConfiguration() {
        Settings loaded = Settings.load(getConfig(), getLogger());
        this.messages = Messages.load(this, loaded.language());
        this.settings = loaded;
    }

    private void applyRuntimeSettings() {
        cooldowns.cooldown(settings.cooldown());
        chatHistory.configure(settings.chatContext().messages(), settings.chatContext().maxAge());
    }

    private void hookPlaceholderApi() {
        boolean wanted = settings.placeholderApi() && getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (wanted && expansion == null) {
            ReportHookExpansion created = new ReportHookExpansion(this);
            if (created.register()) {
                expansion = created;
            }
        } else if (!wanted && expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }

    private void logDiscordState() {
        DiscordSettings discordSettings = settings.discord();
        WebhookUrl webhook = discordSettings.webhook();
        if (discordSettings.active() && webhook != null) {
            getLogger().info("Discord sync enabled (" + webhook.masked() + ")");
        } else {
            getLogger().info("Discord sync disabled");
        }
    }

    private String userAgent() {
        return "ReportHook/" + getPluginMeta().getVersion() + " (+https://github.com/topdanya33200-netizen/ReportHook)";
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Tasks tasks() {
        return tasks;
    }

    public ReportService reports() {
        return reports;
    }

    public ChatHistory chatHistory() {
        return chatHistory;
    }
}
