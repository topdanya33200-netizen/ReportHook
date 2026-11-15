package io.github.topdanya33200_netizen.reporthook.service;

import com.google.gson.JsonObject;
import io.github.topdanya33200_netizen.reporthook.Permissions;
import io.github.topdanya33200_netizen.reporthook.ReportHookPlugin;
import io.github.topdanya33200_netizen.reporthook.config.Messages;
import io.github.topdanya33200_netizen.reporthook.config.Settings;
import io.github.topdanya33200_netizen.reporthook.discord.DiscordSettings;
import io.github.topdanya33200_netizen.reporthook.discord.DiscordWebhookClient;
import io.github.topdanya33200_netizen.reporthook.discord.ReportEmbedFactory;
import io.github.topdanya33200_netizen.reporthook.discord.WebhookException;
import io.github.topdanya33200_netizen.reporthook.discord.WebhookUrl;
import io.github.topdanya33200_netizen.reporthook.model.ChatLine;
import io.github.topdanya33200_netizen.reporthook.model.NewReport;
import io.github.topdanya33200_netizen.reporthook.model.Report;
import io.github.topdanya33200_netizen.reporthook.model.ReportCounts;
import io.github.topdanya33200_netizen.reporthook.model.ReportFilter;
import io.github.topdanya33200_netizen.reporthook.model.ReportLocation;
import io.github.topdanya33200_netizen.reporthook.model.ReportPage;
import io.github.topdanya33200_netizen.reporthook.model.ReportStatus;
import io.github.topdanya33200_netizen.reporthook.model.TransitionResult;
import io.github.topdanya33200_netizen.reporthook.storage.ReportRepository;
import io.github.topdanya33200_netizen.reporthook.util.Text;
import io.github.topdanya33200_netizen.reporthook.util.TimeFormat;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Everything that happens to a report: filing, status changes, staff notifications and
 * Discord sync.
 *
 * <p>Database calls run one after another on a dedicated thread, so the server thread never
 * waits for disk. Results travel back to players through their own schedulers.</p>
 */
public final class ReportService {

    private static final int HISTORY_LIMIT = 10;
    private static final int LIST_PAGE_SIZE = 8;
    private static final int NOTE_LIMIT = 500;

    private final ReportHookPlugin plugin;
    private final ReportRepository repository;
    private final DiscordWebhookClient discord;
    private final ChatHistory chatHistory;
    private final CooldownTracker cooldowns;
    private final ExecutorService database;
    private final AtomicReference<ReportCounts> counts = new AtomicReference<>(ReportCounts.EMPTY);
    private final List<ScheduledTask> timers = new ArrayList<>();
    private final ZoneId zone = ZoneId.systemDefault();
    private final DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone);

    public ReportService(ReportHookPlugin plugin, ReportRepository repository, DiscordWebhookClient discord,
                         ChatHistory chatHistory, CooldownTracker cooldowns) {
        this.plugin = plugin;
        this.repository = repository;
        this.discord = discord;
        this.chatHistory = chatHistory;
        this.cooldowns = cooldowns;
        this.database = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ReportHook-Database");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        refreshCounts();
        timers.add(plugin.tasks().asyncTimer(this::refreshCounts, 2, 2, TimeUnit.MINUTES));
        timers.add(plugin.tasks().asyncTimer(this::maintenance, 1, 360, TimeUnit.MINUTES));
    }

    /** Stops the timers and waits briefly for pending database writes. */
    public void shutdown() {
        timers.forEach(ScheduledTask::cancel);
        timers.clear();
        database.shutdown();
        try {
            if (!database.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Pending database work did not finish in time");
                database.shutdownNow();
            }
        } catch (InterruptedException exception) {
            database.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public ReportCounts counts() {
        return counts.get();
    }

    // ---------------------------------------------------------------- filing reports

    /**
     * Checks whether {@code reporter} may report {@code target} right now and tells them if not.
     * Used before opening the reason menu; does not start the cooldown.
     */
    public boolean canReport(Player reporter, Player target) {
        Messages messages = plugin.messages();
        if (reporter.getUniqueId().equals(target.getUniqueId())) {
            messages.send(reporter, "report.self");
            return false;
        }
        if (target.hasPermission(Permissions.EXEMPT)) {
            messages.send(reporter, "report.exempt");
            return false;
        }
        if (!reporter.hasPermission(Permissions.BYPASS_COOLDOWN)) {
            long remaining = cooldowns.remaining(reporter.getUniqueId());
            if (remaining > 0) {
                sendCooldown(reporter, remaining);
                return false;
            }
        }
        return true;
    }

    public void submit(Player reporter, Player target, String rawReason) {
        Settings settings = plugin.settings();
        Messages messages = plugin.messages();
        String reason = Text.clean(rawReason);
        if (reason.isEmpty()) {
            messages.send(reporter, "report.usage");
            return;
        }
        if (reason.length() > settings.maxReasonLength()) {
            messages.send(reporter, "report.too-long", Placeholder.unparsed("max", Integer.toString(settings.maxReasonLength())));
            return;
        }
        if (!canReport(reporter, target)) {
            return;
        }
        boolean bypass = reporter.hasPermission(Permissions.BYPASS_COOLDOWN);
        if (!bypass) {
            long remaining = cooldowns.tryAcquire(reporter.getUniqueId());
            if (remaining > 0) {
                sendCooldown(reporter, remaining);
                return;
            }
        }

        UUID reporterId = reporter.getUniqueId();
        String reporterName = reporter.getName();
        UUID targetId = target.getUniqueId();
        String targetName = target.getName();
        List<ChatLine> chat = settings.chatContext().enabled() ? chatHistory.snapshot(targetId) : List.of();
        Instant now = Instant.now();
        Function<@Nullable ReportLocation, NewReport> draft = location -> new NewReport(
                reporterId, reporterName, targetId, targetName, reason, settings.serverName(), location, chat, now);

        // On Folia an entity's position may only be read on the thread that owns it
        plugin.tasks().entity(target,
                () -> persist(reporter, draft.apply(locationOf(target)), bypass),
                () -> persist(reporter, draft.apply(null), bypass));
    }

    private void persist(Player reporter, NewReport draft, boolean bypass) {
        query(() -> repository.hasActiveReport(draft.reporterId(), draft.targetId())
                ? Optional.<Report>empty()
                : Optional.of(repository.insert(draft)))
                .whenComplete((saved, error) -> {
                    Messages messages = plugin.messages();
                    if (error != null) {
                        plugin.getLogger().log(Level.SEVERE, "Could not save a report against " + draft.targetName(), error);
                        if (!bypass) {
                            cooldowns.release(draft.reporterId());
                        }
                        plugin.tasks().entity(reporter, () -> messages.send(reporter, "report.failed"));
                        return;
                    }
                    if (saved.isEmpty()) {
                        if (!bypass) {
                            cooldowns.release(draft.reporterId());
                        }
                        plugin.tasks().entity(reporter, () -> messages.send(reporter, "report.duplicate",
                                Placeholder.unparsed("target", draft.targetName())));
                        return;
                    }
                    Report report = saved.get();
                    plugin.getLogger().info("Report #" + report.id() + ": " + report.reporterName() + " reported "
                            + report.targetName() + " (" + report.reason() + ")");
                    plugin.tasks().entity(reporter, () -> messages.send(reporter, "report.sent", placeholders(report)));
                    notifyStaff(report);
                    publishCreated(report);
                    refreshCounts();
                });
    }

    private void sendCooldown(Player reporter, long remainingMillis) {
        Messages messages = plugin.messages();
        String time = TimeFormat.duration(Duration.ofMillis(remainingMillis), messages.timeUnits());
        messages.send(reporter, "report.cooldown", Placeholder.unparsed("time", time));
    }

    private static ReportLocation locationOf(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        return new ReportLocation(world == null ? "unknown" : world.getName(), location.getX(), location.getY(), location.getZ());
    }

    // ---------------------------------------------------------------- staff actions

    /**
     * Moves a report to {@code next}. The returned future completes after the actor was told
     * the outcome, which lets the menu refresh itself.
     */
    public CompletableFuture<TransitionResult> changeStatus(CommandSender actor, long id, ReportStatus next, @Nullable String rawNote) {
        UUID actorId = actor instanceof Player player ? player.getUniqueId() : null;
        String actorName = actor instanceof Player player ? player.getName() : plugin.messages().plain("console-name");
        String note = rawNote == null ? null : Text.clean(rawNote);
        String storedNote = note == null || note.isEmpty() ? null : note.substring(0, Math.min(note.length(), NOTE_LIMIT));

        CompletableFuture<TransitionResult> result =
                query(() -> repository.transition(id, next, actorId, actorName, storedNote, Instant.now()));
        return result.whenComplete((outcome, error) -> {
            Messages messages = plugin.messages();
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not update report #" + id, error);
                tell(actor, audience -> messages.send(audience, "staff.error"));
                return;
            }
            switch (outcome) {
                case TransitionResult.NotFound notFound -> tell(actor, audience ->
                        messages.send(audience, "staff.not-found", Placeholder.unparsed("id", Long.toString(notFound.id()))));
                case TransitionResult.Rejected rejected -> {
                    Report current = rejected.current();
                    String key = current.status() == ReportStatus.CLAIMED && next == ReportStatus.CLAIMED
                            ? "staff.already-claimed"
                            : "staff.invalid-transition";
                    tell(actor, audience -> messages.send(audience, key, Map.of("id", Long.toString(current.id())),
                            placeholders(current), Placeholder.component("next", messages.status(next))));
                }
                case TransitionResult.Changed changed -> {
                    Report report = changed.report();
                    tell(actor, audience -> messages.send(audience, "staff.status-changed", placeholders(report)));
                    broadcastStatus(actor, actorName, report);
                    notifyReporter(report);
                    publishUpdated(report);
                    refreshCounts();
                }
            }
        });
    }

    /** Teleports staff to the reported player, or to where they stood if they went offline. */
    public void teleport(Player staff, long id) {
        query(() -> repository.find(id)).whenComplete((found, error) -> {
            Messages messages = plugin.messages();
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load report #" + id, error);
                tell(staff, audience -> messages.send(audience, "staff.error"));
                return;
            }
            if (found.isEmpty()) {
                tell(staff, audience -> messages.send(audience, "staff.not-found", Placeholder.unparsed("id", Long.toString(id))));
                return;
            }
            Report report = found.get();
            // Player lookups happen on server threads, never on the database thread
            plugin.tasks().entity(staff, () -> {
                Player target = Bukkit.getPlayer(report.targetId());
                if (target != null) {
                    plugin.tasks().entity(target, () -> {
                        Location destination = target.getLocation();
                        plugin.tasks().entity(staff, () -> staff.teleportAsync(destination).thenAccept(success -> {
                            if (success) {
                                messages.send(staff, "staff.teleported", placeholders(report));
                            }
                        }));
                    });
                    return;
                }
                ReportLocation last = report.location();
                World world = last == null ? null : Bukkit.getWorld(last.world());
                if (last == null || world == null) {
                    messages.send(staff, "staff.no-location", placeholders(report));
                    return;
                }
                staff.teleportAsync(new Location(world, last.x(), last.y(), last.z())).thenAccept(success -> {
                    if (success) {
                        messages.send(staff, "staff.teleported-last-known", placeholders(report));
                    }
                });
            });
        });
    }

    public void sendInfo(CommandSender viewer, long id) {
        query(() -> repository.find(id)).whenComplete((found, error) -> {
            Messages messages = plugin.messages();
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load report #" + id, error);
                tell(viewer, audience -> messages.send(audience, "staff.error"));
                return;
            }
            if (found.isEmpty()) {
                tell(viewer, audience -> messages.send(audience, "staff.not-found", Placeholder.unparsed("id", Long.toString(id))));
                return;
            }
            Report report = found.get();
            // Player names are plain [A-Za-z0-9_.], so they are safe inside <click> arguments
            Map<String, String> literals = Map.of("id", Long.toString(report.id()), "target", report.targetName());
            tell(viewer, audience -> {
                messages.send(audience, "staff.info", literals, placeholders(report));
                for (ChatLine line : report.chatContext()) {
                    messages.send(audience, "staff.info-chat",
                            Placeholder.unparsed("time", clock.format(line.time())),
                            Placeholder.unparsed("message", line.message()));
                }
            });
        });
    }

    public void sendHistory(CommandSender viewer, String name) {
        Messages messages = plugin.messages();
        OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(name);
        if (player == null) {
            messages.send(viewer, "staff.unknown-player", Placeholder.unparsed("name", name));
            return;
        }
        UUID targetId = player.getUniqueId();
        String displayName = player.getName() == null ? name : player.getName();
        query(() -> repository.findByTarget(targetId, HISTORY_LIMIT)).whenComplete((reports, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load the report history of " + displayName, error);
                tell(viewer, audience -> messages.send(audience, "staff.error"));
                return;
            }
            tell(viewer, audience -> {
                if (reports.isEmpty()) {
                    messages.send(audience, "staff.history.empty", Placeholder.unparsed("name", displayName));
                    return;
                }
                messages.send(audience, "staff.history.header", Placeholder.unparsed("name", displayName),
                        Placeholder.unparsed("count", Integer.toString(reports.size())));
                for (Report report : reports) {
                    messages.send(audience, "staff.history.entry", Map.of("id", Long.toString(report.id())), placeholders(report));
                }
            });
        });
    }

    /** Chat version of the staff menu, also usable from the console. {@code page} is 1-based. */
    public void sendList(CommandSender viewer, ReportFilter filter, int page) {
        page(filter, page - 1, LIST_PAGE_SIZE).whenComplete((result, error) -> {
            Messages messages = plugin.messages();
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not list reports", error);
                tell(viewer, audience -> messages.send(audience, "staff.error"));
                return;
            }
            tell(viewer, audience -> {
                TagResolver filterName = Placeholder.component("filter", messages.render("filter." + filter.key()));
                if (result.reports().isEmpty()) {
                    messages.send(audience, "staff.list.empty", filterName);
                    return;
                }
                messages.send(audience, "staff.list.header", filterName,
                        Placeholder.unparsed("page", Integer.toString(result.page() + 1)),
                        Placeholder.unparsed("pages", Integer.toString(result.pages())),
                        Placeholder.unparsed("total", Integer.toString(result.total())));
                for (Report report : result.reports()) {
                    messages.send(audience, "staff.list.entry", Map.of("id", Long.toString(report.id())), placeholders(report));
                }
                if (result.pages() > 1) {
                    messages.send(audience, "staff.list.footer", Map.of(
                            "filter", filter.key(),
                            "prev", Integer.toString(Math.max(1, result.page())),
                            "next", Integer.toString(Math.min(result.pages(), result.page() + 2))));
                }
            });
        });
    }

    /** {@code page} is 0-based. */
    public CompletableFuture<ReportPage> page(ReportFilter filter, int page, int pageSize) {
        int safePage = Math.max(0, page);
        return query(() -> new ReportPage(
                repository.list(filter.statuses(), pageSize, safePage * pageSize),
                repository.count(filter.statuses()),
                safePage,
                pageSize));
    }

    /** MiniMessage placeholders describing a report. Player-written values are never parsed. */
    public TagResolver placeholders(Report report) {
        Messages messages = plugin.messages();
        TimeFormat.Units units = messages.timeUnits();
        Instant now = Instant.now();
        ReportLocation location = report.location();
        String none = messages.plain("none");
        String handler = report.handlerName();
        String note = report.note();
        boolean online = Bukkit.getPlayer(report.targetId()) != null;
        return TagResolver.resolver(
                Placeholder.unparsed("id", Long.toString(report.id())),
                Placeholder.unparsed("target", report.targetName()),
                Placeholder.unparsed("reporter", report.reporterName()),
                Placeholder.unparsed("reason", report.reason()),
                Placeholder.unparsed("server", report.serverName()),
                Placeholder.component("status", messages.status(report.status())),
                Placeholder.unparsed("handler", handler == null ? none : handler),
                Placeholder.unparsed("note", note == null ? none : note),
                Placeholder.unparsed("age", TimeFormat.since(report.createdAt(), now, units)),
                Placeholder.unparsed("updated", TimeFormat.since(report.updatedAt(), now, units)),
                Placeholder.unparsed("location", location == null ? none
                        : location.world() + " " + location.blockX() + ", " + location.blockY() + ", " + location.blockZ()),
                Placeholder.unparsed("chat-count", Integer.toString(report.chatContext().size())),
                Placeholder.component("online", messages.render(online ? "online" : "offline")));
    }

    // ---------------------------------------------------------------- notifications

    private void notifyStaff(Report report) {
        Settings settings = plugin.settings();
        if (!settings.notifications().inGame()) {
            return;
        }
        Sound sound = settings.notifications().sound();
        forEachStaff(null,
                () -> plugin.messages().render("staff.new-report", Map.of("id", Long.toString(report.id())), placeholders(report)),
                (staff, message) -> {
                    staff.sendMessage(message);
                    if (sound != null) {
                        staff.playSound(sound);
                    }
                });
    }

    private void broadcastStatus(CommandSender actor, String actorName, Report report) {
        if (!plugin.settings().notifications().inGame()) {
            return;
        }
        forEachStaff(actor instanceof Player player ? player.getUniqueId() : null,
                () -> plugin.messages().render("staff.status-broadcast", placeholders(report), Placeholder.unparsed("actor", actorName)),
                Audience::sendMessage);
    }

    private void notifyReporter(Report report) {
        if (!plugin.settings().notifications().notifyReporter() || report.status().isActive()) {
            return;
        }
        plugin.tasks().global(() -> {
            Player reporter = Bukkit.getPlayer(report.reporterId());
            if (reporter != null) {
                plugin.tasks().entity(reporter, () -> plugin.messages().send(reporter, "report.reviewed", placeholders(report)));
            }
        });
    }

    /**
     * Builds a message once on the global thread and delivers it to every online staff member
     * on that player's own thread.
     */
    private void forEachStaff(@Nullable UUID except, Supplier<Component> message, BiConsumer<Player, Component> action) {
        plugin.tasks().global(() -> {
            Component rendered = null;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission(Permissions.NOTIFY) && !player.getUniqueId().equals(except)) {
                    if (rendered == null) {
                        rendered = message.get();
                    }
                    Component finalMessage = rendered;
                    plugin.tasks().entity(player, () -> action.accept(player, finalMessage));
                }
            }
        });
    }

    /** Runs {@code action} on the viewer's thread: their own scheduler for players, the global one otherwise. */
    private void tell(CommandSender viewer, Consumer<Audience> action) {
        if (viewer instanceof Player player) {
            plugin.tasks().entity(player, () -> action.accept(player));
        } else {
            plugin.tasks().global(() -> action.accept(viewer));
        }
    }

    // ---------------------------------------------------------------- discord

    private ReportEmbedFactory embeds() {
        Settings settings = plugin.settings();
        return new ReportEmbedFactory(settings.discord(), plugin.messages().discordLabels(), settings.serverName(), zone);
    }

    private void publishCreated(Report report) {
        DiscordSettings settings = plugin.settings().discord();
        WebhookUrl webhook = settings.webhook();
        if (!settings.active() || webhook == null) {
            return;
        }
        ReportEmbedFactory embeds = embeds();
        post(webhook, report, embeds.created(report), embeds.threadName(report));
    }

    private void publishUpdated(Report report) {
        DiscordSettings settings = plugin.settings().discord();
        WebhookUrl webhook = settings.webhook();
        if (!settings.active() || webhook == null || !settings.updateOnStatusChange()) {
            return;
        }
        ReportEmbedFactory embeds = embeds();
        String messageId = report.discordMessageId();
        if (messageId == null) {
            // Never reached Discord (sync was off or Discord was down): post it now, without the role ping
            post(webhook, report, embeds.updated(report), embeds.threadName(report));
            return;
        }
        String threadId = settings.usesThreads()
                ? (report.discordChannelId() != null ? report.discordChannelId() : webhook.threadId())
                : null;
        discord.edit(webhook, messageId, threadId, embeds.updated(report)).exceptionally(error -> {
            if (unwrap(error) instanceof WebhookException webhookError && webhookError.status() == 404) {
                // The message was deleted in Discord: post a fresh one instead
                post(webhook, report, embeds.updated(report), embeds.threadName(report));
            } else {
                warnDiscord("update report #" + report.id(), webhook, error);
            }
            return null;
        });
    }

    private void post(WebhookUrl webhook, Report report, JsonObject payload, @Nullable String threadName) {
        discord.post(webhook, payload, threadName)
                .thenCompose(sent -> query(() -> {
                    repository.setDiscordMessage(report.id(), sent.messageId(), sent.channelId());
                    return sent;
                }))
                .exceptionally(error -> {
                    warnDiscord("send report #" + report.id(), webhook, error);
                    return null;
                });
    }

    private void warnDiscord(String action, WebhookUrl webhook, Throwable error) {
        plugin.getLogger().warning("Could not " + action + " to Discord (" + webhook.masked() + "): " + unwrap(error).getMessage());
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    // ---------------------------------------------------------------- housekeeping

    public void refreshCounts() {
        query(repository::countByStatus).whenComplete((byStatus, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not count reports", error);
                return;
            }
            counts.set(ReportCounts.of(byStatus));
        });
    }

    private void maintenance() {
        cooldowns.purgeExpired();
        int days = plugin.settings().retentionDays();
        if (days <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        query(() -> repository.purgeClosedBefore(cutoff)).whenComplete((removed, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not remove old reports", error);
            } else if (removed > 0) {
                plugin.getLogger().info("Removed " + removed + " closed reports older than " + days + " days");
                refreshCounts();
            }
        });
    }

    @FunctionalInterface
    private interface DatabaseCall<T> {
        T call() throws Exception;
    }

    private <T> CompletableFuture<T> query(DatabaseCall<T> call) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            database.execute(() -> {
                try {
                    future.complete(call.call());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (RejectedExecutionException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }
}
