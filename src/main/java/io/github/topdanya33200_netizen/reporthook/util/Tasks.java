package io.github.topdanya33200_netizen.reporthook.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Thin wrapper over Paper's region-aware schedulers, so the plugin runs on both Paper and Folia.
 *
 * <p>Anything touching an entity runs on that entity's scheduler; storage and network work run
 * on their own threads inside the services.</p>
 */
public final class Tasks {

    private final Plugin plugin;

    public Tasks(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Runs on the thread that owns {@code entity}. Silently skipped if the entity is gone. */
    public void entity(Entity entity, Runnable task) {
        entity(entity, task, null);
    }

    /** Like {@link #entity(Entity, Runnable)}, but runs {@code retired} if the entity was removed first. */
    public void entity(Entity entity, Runnable task, @Nullable Runnable retired) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (entity.getScheduler().run(plugin, scheduled -> task.run(), retired) == null && retired != null) {
            retired.run();
        }
    }

    public void entityLater(Entity entity, Runnable task, long delayTicks) {
        if (plugin.isEnabled()) {
            entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), null, Math.max(1, delayTicks));
        }
    }

    /** Runs on the global region (the main thread on Paper). */
    public void global(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        }
    }

    public ScheduledTask asyncTimer(Runnable task, long delay, long period, TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduled -> task.run(), delay, period, unit);
    }

    public void cancelAll() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
