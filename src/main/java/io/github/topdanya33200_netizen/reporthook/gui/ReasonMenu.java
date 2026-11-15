package io.github.topdanya33200_netizen.reporthook.gui;

import io.github.topdanya33200_netizen.reporthook.ReportHookPlugin;
import io.github.topdanya33200_netizen.reporthook.config.Messages;
import io.github.topdanya33200_netizen.reporthook.config.ReasonPreset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Opened by {@code /report <player>}: the reporter picks one of the configured reasons.
 */
public final class ReasonMenu extends Menu {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    public ReasonMenu(ReportHookPlugin plugin, Player reporter, Player target) {
        super(rows(plugin.settings().reasons().size()),
                plugin.messages().render("gui.reasons.title", Placeholder.unparsed("target", target.getName())));
        Messages messages = plugin.messages();
        List<ReasonPreset> reasons = plugin.settings().reasons();
        int rows = getInventory().getSize() / 9;

        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(Component.text(" ")).build();
        for (int slot = 0; slot < rows * 9; slot++) {
            slot(slot, filler);
        }

        UUID targetId = target.getUniqueId();
        String targetName = target.getName();
        List<Integer> slots = contentSlots(rows, reasons.size());
        for (int i = 0; i < slots.size(); i++) {
            ReasonPreset reason = reasons.get(i);
            List<Component> lore = new ArrayList<>();
            for (String line : reason.description()) {
                lore.add(MINI.deserialize(line));
            }
            lore.addAll(messages.lines("gui.reasons.click", Placeholder.unparsed("target", targetName)));
            ItemStack icon = ItemBuilder.of(ItemBuilder.material(reason.icon(), Material.PAPER))
                    .name(MINI.deserialize(reason.name()))
                    .lore(lore)
                    .build();
            slot(slots.get(i), icon, event -> plugin.tasks().entity(reporter, () -> {
                // Closing an inventory inside its own click event is unsafe, so this runs a tick later
                reporter.closeInventory();
                Player current = Bukkit.getPlayer(targetId);
                if (current == null || !reporter.canSee(current)) {
                    messages.send(reporter, "report.player-not-found", Placeholder.unparsed("name", targetName));
                    return;
                }
                plugin.reports().submit(reporter, current, reason.plainName());
            }));
        }
    }

    /** A border around up to four rows of seven reasons. */
    static int rows(int reasons) {
        return Math.clamp(2 + (reasons + 6) / 7, 3, 6);
    }

    /** Slots inside the border; a single row of reasons is centred. */
    static List<Integer> contentSlots(int rows, int reasons) {
        List<Integer> slots = new ArrayList<>();
        if (reasons <= 7) {
            int start = 9 + 1 + (7 - reasons) / 2;
            for (int i = 0; i < reasons; i++) {
                slots.add(start + i);
            }
            return slots;
        }
        for (int row = 1; row < rows - 1 && slots.size() < reasons; row++) {
            for (int column = 1; column < 8 && slots.size() < reasons; column++) {
                slots.add(row * 9 + column);
            }
        }
        return slots;
    }
}
