package io.github.topdanya33200_netizen.reporthook.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.topdanya33200_netizen.reporthook.Permissions;
import io.github.topdanya33200_netizen.reporthook.ReportHookPlugin;
import io.github.topdanya33200_netizen.reporthook.gui.ReportListMenu;
import io.github.topdanya33200_netizen.reporthook.model.ReportFilter;
import io.github.topdanya33200_netizen.reporthook.model.ReportStatus;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * {@code /reports} and its staff subcommands.
 */
public final class ReportsCommand {

    private final ReportHookPlugin plugin;

    public ReportsCommand(ReportHookPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("reports")
                .requires(source -> source.getSender().hasPermission(Permissions.STAFF))
                .executes(this::openMenu)
                .then(Commands.literal("list")
                        .executes(context -> list(context, ReportFilter.ACTIVE.key(), 1))
                        .then(Commands.argument("filter", StringArgumentType.word())
                                .suggests(this::suggestFilters)
                                .executes(context -> list(context, StringArgumentType.getString(context, "filter"), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> list(context, StringArgumentType.getString(context, "filter"),
                                                IntegerArgumentType.getInteger(context, "page"))))))
                .then(byId("info", (sender, id) -> plugin.reports().sendInfo(sender, id)))
                .then(byId("claim", (sender, id) -> plugin.reports().changeStatus(sender, id, ReportStatus.CLAIMED, null)))
                .then(byId("unclaim", (sender, id) -> plugin.reports().changeStatus(sender, id, ReportStatus.OPEN, null)))
                .then(byId("reopen", (sender, id) -> plugin.reports().changeStatus(sender, id, ReportStatus.OPEN, null)))
                .then(withNote("resolve", ReportStatus.RESOLVED))
                .then(withNote("dismiss", ReportStatus.DISMISSED))
                .then(byId("tp", this::teleport))
                .then(Commands.literal("history")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(this::suggestPlayers)
                                .executes(context -> {
                                    plugin.reports().sendHistory(context.getSource().getSender(),
                                            StringArgumentType.getString(context, "player"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission(Permissions.ADMIN))
                        .executes(this::reload))
                .build();
    }

    @FunctionalInterface
    private interface IdAction {
        void run(CommandSender sender, long id);
    }

    private LiteralArgumentBuilder<CommandSourceStack> byId(String name, IdAction action) {
        return Commands.literal(name)
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            action.run(context.getSource().getSender(), IntegerArgumentType.getInteger(context, "id"));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> withNote(String name, ReportStatus status) {
        return Commands.literal(name)
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(context -> change(context, status, null))
                        .then(Commands.argument("note", StringArgumentType.greedyString())
                                .executes(context -> change(context, status, StringArgumentType.getString(context, "note")))));
    }

    private int change(CommandContext<CommandSourceStack> context, ReportStatus status, @Nullable String note) {
        plugin.reports().changeStatus(context.getSource().getSender(), IntegerArgumentType.getInteger(context, "id"), status, note);
        return Command.SINGLE_SUCCESS;
    }

    private int openMenu(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (sender instanceof Player player) {
            ReportListMenu.open(plugin, player, ReportFilter.ACTIVE, 0);
        } else {
            plugin.reports().sendList(sender, ReportFilter.ACTIVE, 1);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> context, String rawFilter, int page) {
        CommandSender sender = context.getSource().getSender();
        Optional<ReportFilter> filter = ReportFilter.parse(rawFilter);
        if (filter.isEmpty()) {
            plugin.messages().send(sender, "staff.unknown-filter", Placeholder.unparsed("filter", rawFilter));
            return Command.SINGLE_SUCCESS;
        }
        plugin.reports().sendList(sender, filter.get(), page);
        return Command.SINGLE_SUCCESS;
    }

    private void teleport(CommandSender sender, long id) {
        if (sender instanceof Player player) {
            plugin.reports().teleport(player, id);
        } else {
            plugin.messages().send(sender, "report.only-players");
        }
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        try {
            plugin.reload();
            plugin.messages().send(sender, "staff.reloaded");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Reloading ReportHook failed", exception);
            plugin.messages().send(sender, "staff.reload-failed");
        }
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestFilters(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String typed = builder.getRemainingLowerCase();
        for (ReportFilter filter : ReportFilter.values()) {
            if (filter.key().startsWith(typed)) {
                builder.suggest(filter.key());
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String typed = builder.getRemainingLowerCase();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(online.getName());
            }
        }
        return builder.buildFuture();
    }
}
