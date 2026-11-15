package io.github.topdanya33200_netizen.reporthook.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Small fluent helper for menu items.
 */
public final class ItemBuilder {

    private final ItemStack item;

    private ItemBuilder(ItemStack item) {
        this.item = item;
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    /** A player head. The client fetches the skin itself, so this never blocks the server. */
    public static ItemBuilder head(UUID owner) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner)));
        return new ItemBuilder(head);
    }

    /** Looks up a material by name, falling back when it is unknown or not an item. */
    public static Material material(String name, Material fallback) {
        Material material = Material.matchMaterial(name);
        return material != null && material.isItem() && !material.isAir() ? material : fallback;
    }

    public ItemBuilder name(Component name) {
        Component display = withoutItalics(name);
        item.editMeta(meta -> meta.displayName(display));
        return this;
    }

    public ItemBuilder lore(List<Component> lines) {
        List<Component> lore = new ArrayList<>(lines.size());
        for (Component line : lines) {
            lore.add(withoutItalics(line));
        }
        item.editMeta(meta -> meta.lore(lore));
        return this;
    }

    public ItemStack build() {
        return item;
    }

    /** Item names and lore are italic unless told otherwise. */
    private static Component withoutItalics(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
