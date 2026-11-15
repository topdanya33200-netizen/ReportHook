package io.github.topdanya33200_netizen.reporthook.listener;

import io.github.topdanya33200_netizen.reporthook.Permissions;
import io.github.topdanya33200_netizen.reporthook.ReportHookPlugin;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Reminds staff about open reports when they join and drops chat history on quit.
 */
public final class ConnectionListener implements Listener {

    /** Three seconds, so the reminder is not buried under join messages. */
    private static final long REMINDER_DELAY_TICKS = 60L;

    private final ReportHookPlugin plugin;

    public ConnectionListener(ReportHookPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.settings().notifications().joinReminder() || !player.hasPermission(Permissions.NOTIFY)) {
            return;
        }
        plugin.tasks().entityLater(player, () -> {
            int open = plugin.reports().counts().open();
            if (open > 0) {
                plugin.messages().send(player, "staff.join-reminder", Placeholder.unparsed("count", Integer.toString(open)));
            }
        }, REMINDER_DELAY_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.chatHistory().forget(event.getPlayer().getUniqueId());
    }
}
