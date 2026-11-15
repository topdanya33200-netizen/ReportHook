package io.github.topdanya33200_netizen.reporthook.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * A chest menu with a click action per slot. The menu is its own {@link InventoryHolder},
 * which is how {@link MenuListener} recognises it without comparing titles.
 */
public abstract class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    // The holder must be passed to createInventory; subclasses only fill slots after this returns
    @SuppressWarnings("this-escape")
    protected Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    protected void slot(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        actions.remove(slot);
    }

    protected void slot(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    void click(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** Whether {@code player} still has this exact menu open. */
    protected boolean isViewing(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder(false) == this;
    }
}
