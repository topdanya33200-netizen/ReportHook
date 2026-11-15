package io.github.topdanya33200_netizen.reporthook.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.topdanya33200_netizen.reporthook.Permissions;
import io.github.topdanya33200_netizen.reporthook.ReportHookPlugin;
import io.github.topdanya33200_netizen.reporthook.config.Messages;
import io.github.topdanya33200_netizen.reporthook.config.ReasonPreset;
import io.github.topdanya33200_netizen.reporthook.gui.ReasonMenu;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * {@code /report <player> [reason]}. Without a reason the reason menu opens.
 */
public final class ReportCommand {

    private final ReportHookPlugin plugin;

    public ReportCommand(ReportHookPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("report")
                .requires(source -> source.getSender().hasPermission(Permissions.REPORT))
                .executes(context -> {
                    plugin.messages().send(context.getSource().getSender(), "report.usage");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(this::suggestPlayers)
                        .executes(context -> report(context, null))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .suggests(this::suggestReasons)
                                .executes(context -> report(context, StringArgumentType.getString(context, "reason")))))
                .build();
    }

    private int report(CommandContext<CommandSourceStack> context, @Nullable String reason) {
        CommandSender sender = context.getSource().getSender();
        Messages messages = plugin.messages();
        if (!(sender instanceof Player reporter)) {
            messages.send(sender, "report.only-players");
            return Command.SINGLE_SUCCESS;
        }
        String name = StringArgumentType.getString(context, "player");
        Player target = Bukkit.getPlayerExact(name);
        // Vanished players are treated as offline, so /report can't be used to find them
        if (target == null || !reporter.canSee(target)) {
            messages.send(reporter, "report.player-not-found", Placeholder.unparsed("name", name));
            return Command.SINGLE_SUCCESS;
        }
        if (reason != null) {
            plugin.reports().submit(reporter, target, reason);
        } else if (plugin.reports().canReport(reporter, target)) {
            plugin.tasks().entity(reporter, () -> new ReasonMenu(plugin, reporter, target).open(reporter));
        }
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSender sender = context.getSource().getSender();
        String typed = builder.getRemainingLowerCase();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player self && (self.equals(online) || !self.canSee(online))) {
                continue;
            }
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(online.getName());
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestReasons(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String typed = builder.getRemainingLowerCase();
        for (ReasonPreset reason : plugin.settings().reasons()) {
            String name = reason.plainName();
            if (name.toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }
}
