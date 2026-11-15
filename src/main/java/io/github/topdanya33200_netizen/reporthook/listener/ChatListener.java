package io.github.topdanya33200_netizen.reporthook.listener;

import io.github.topdanya33200_netizen.reporthook.ReportHookPlugin;
import io.github.topdanya33200_netizen.reporthook.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Keeps the recent chat of every player so reports can include it as evidence.
 */
public final class ChatListener implements Listener {

    private final ReportHookPlugin plugin;

    public ChatListener(ReportHookPlugin plugin) {
        this.plugin = plugin;
    }

    // MONITOR + ignoreCancelled: only messages other players actually saw are recorded
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.settings().chatContext().enabled()) {
            return;
        }
        String message = Text.clean(PlainTextComponentSerializer.plainText().serialize(event.message()));
        if (!message.isEmpty()) {
            plugin.chatHistory().record(event.getPlayer().getUniqueId(), message);
        }
    }
}
