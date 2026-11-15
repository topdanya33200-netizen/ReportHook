package io.github.topdanya33200_netizen.reporthook.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Routes clicks to {@link Menu}s and stops items from being taken out of or put into them.
 */
public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        // getHolder(false) skips the expensive block state snapshot Paper takes by default
        if (!(top.getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < top.getSize()) {
            menu.click(event);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
