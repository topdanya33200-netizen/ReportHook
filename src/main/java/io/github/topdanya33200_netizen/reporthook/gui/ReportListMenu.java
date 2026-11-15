package io.github.topdanya33200_netizen.reporthook.gui;

import io.github.topdanya33200_netizen.reporthook.ReportHookPlugin;
import io.github.topdanya33200_netizen.reporthook.config.Messages;
import io.github.topdanya33200_netizen.reporthook.model.Report;
import io.github.topdanya33200_netizen.reporthook.model.ReportCounts;
import io.github.topdanya33200_netizen.reporthook.model.ReportFilter;
import io.github.topdanya33200_netizen.reporthook.model.ReportPage;
import io.github.topdanya33200_netizen.reporthook.model.ReportStatus;
import java.util.List;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Staff menu opened by {@code /reports}: one player head per report, newest first.
 *
 * <ul>
 *   <li>Left click: teleport to the reported player</li>
 *   <li>Right click: claim, unclaim or reopen</li>
 *   <li>Shift + left: resolve, Shift + right: dismiss</li>
 * </ul>
 */
public final class ReportListMenu extends Menu {

    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS = 45;
    private static final int FILTER = 47;
    private static final int SUMMARY = 49;
    private static final int REFRESH = 51;
    private static final int NEXT = 53;

    private final ReportHookPlugin plugin;
    private final Player viewer;
    private final ReportFilter filter;
    private final int page;

    /** Loads the page in the background and opens the menu when the data is ready. */
    public static void open(ReportHookPlugin plugin, Player viewer, ReportFilter filter, int page) {
        plugin.reports().page(filter, page, PAGE_SIZE).whenComplete((result, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not load reports for the menu", error);
                plugin.tasks().entity(viewer, () -> plugin.messages().send(viewer, "staff.error"));
                return;
            }
            if (result.reports().isEmpty() && result.page() > 0) {
                // The page emptied, e.g. its last report was just closed: show the last one that exists
                open(plugin, viewer, filter, result.pages() - 1);
                return;
            }
            plugin.tasks().entity(viewer, () -> new ReportListMenu(plugin, viewer, filter, result).open(viewer));
        });
    }

    private ReportListMenu(ReportHookPlugin plugin, Player viewer, ReportFilter filter, ReportPage result) {
        super(6, plugin.messages().render("gui.list.title",
                Placeholder.component("filter", plugin.messages().render("filter." + filter.key())),
                Placeholder.unparsed("page", Integer.toString(result.page() + 1)),
                Placeholder.unparsed("pages", Integer.toString(result.pages()))));
        this.plugin = plugin;
        this.viewer = viewer;
        this.filter = filter;
        this.page = result.page();
        render(result);
    }

    private void render(ReportPage result) {
        Messages messages = plugin.messages();
        List<Report> reports = result.reports();
        for (int i = 0; i < reports.size() && i < PAGE_SIZE; i++) {
            Report report = reports.get(i);
            TagResolver placeholders = plugin.reports().placeholders(report);
            String loreKey = report.status().isActive() ? "gui.list.lore-active" : "gui.list.lore-closed";
            ItemStack item = ItemBuilder.head(report.targetId())
                    .name(messages.render("gui.list.item-name", placeholders))
                    .lore(messages.lines(loreKey, placeholders))
                    .build();
            slot(i, item, event -> handle(report, event.getClick()));
        }
        if (reports.isEmpty()) {
            slot(22, ItemBuilder.of(Material.BARRIER).name(messages.render("gui.list.empty")).build());
        }

        ItemStack filler = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(Component.text(" ")).build();
        for (int slot = PREVIOUS; slot <= NEXT; slot++) {
            slot(slot, filler);
        }
        if (result.hasPrevious()) {
            slot(PREVIOUS, ItemBuilder.of(Material.ARROW).name(messages.render("gui.list.previous")).build(),
                    event -> open(plugin, viewer, filter, page - 1));
        }
        slot(FILTER, ItemBuilder.of(Material.HOPPER)
                        .name(messages.render("gui.list.filter",
                                Placeholder.component("filter", messages.render("filter." + filter.key()))))
                        .lore(messages.lines("gui.list.filter-lore",
                                Placeholder.component("next", messages.render("filter." + filter.next().key()))))
                        .build(),
                event -> open(plugin, viewer, filter.next(), 0));

        ReportCounts counts = plugin.reports().counts();
        slot(SUMMARY, ItemBuilder.of(Material.BOOK)
                .name(messages.render("gui.list.summary"))
                .lore(messages.lines("gui.list.summary-lore",
                        Placeholder.unparsed("open", Integer.toString(counts.open())),
                        Placeholder.unparsed("claimed", Integer.toString(counts.claimed())),
                        Placeholder.unparsed("resolved", Integer.toString(counts.resolved())),
                        Placeholder.unparsed("dismissed", Integer.toString(counts.dismissed())),
                        Placeholder.unparsed("total", Integer.toString(counts.total()))))
                .build());
        slot(REFRESH, ItemBuilder.of(Material.SUNFLOWER).name(messages.render("gui.list.refresh")).build(),
                event -> open(plugin, viewer, filter, page));
        if (result.hasNext()) {
            slot(NEXT, ItemBuilder.of(Material.ARROW).name(messages.render("gui.list.next")).build(),
                    event -> open(plugin, viewer, filter, page + 1));
        }
    }

    private void handle(Report report, ClickType click) {
        ReportStatus status = report.status();
        switch (click) {
            case LEFT -> {
                plugin.tasks().entity(viewer, viewer::closeInventory);
                plugin.reports().teleport(viewer, report.id());
            }
            // Open -> claimed, claimed -> open again, resolved/dismissed -> reopened
            case RIGHT -> change(report, status == ReportStatus.OPEN ? ReportStatus.CLAIMED : ReportStatus.OPEN);
            case SHIFT_LEFT -> {
                if (status.isActive()) {
                    change(report, ReportStatus.RESOLVED);
                }
            }
            case SHIFT_RIGHT -> {
                if (status.isActive()) {
                    change(report, ReportStatus.DISMISSED);
                }
            }
            default -> {
                // Other click types do nothing
            }
        }
    }

    private void change(Report report, ReportStatus next) {
        plugin.reports().changeStatus(viewer, report.id(), next, null).thenRun(() ->
                plugin.tasks().entity(viewer, () -> {
                    if (isViewing(viewer)) {
                        open(plugin, viewer, filter, page);
                    }
                }));
    }
}
