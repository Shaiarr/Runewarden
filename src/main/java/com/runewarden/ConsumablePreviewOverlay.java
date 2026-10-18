/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.itemstats.Effect;
import net.runelite.client.plugins.itemstats.ItemStatChanges;
import net.runelite.client.plugins.itemstats.StatChange;
import net.runelite.client.plugins.itemstats.StatsChanges;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Read-only per-use previews from RuneLite's native item-effect formulas. */
public final class ConsumablePreviewOverlay extends WidgetItemOverlay
{
    private final Client client; private final ItemManager items; private final ItemStatChanges stats;
    private final HoverCardOverlay hovers; private final FieldCoach coach;
    ConsumablePreviewOverlay(Client client, ItemManager items, ItemStatChanges stats, HoverCardOverlay hovers, FieldCoach coach)
    { this.client = client; this.items = items; this.stats = stats; this.hovers = hovers; this.coach = coach; showOnInventory(); showOnBank(); }
    void clear() { }
    @Override public void renderItemOverlay(Graphics2D graphics, int id, WidgetItem item)
    {
        if (!coach.previewsEnabled() || client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen()
            || item == null || id <= 0 || item.getQuantity() <= 0 || item.getDraggingCanvasBounds() != null) return;
        Widget widget = item.getWidget(); if (widget == null || widget.isHidden()) return;
        Widget parent = widget.getParent();
        if (!ResourceTooltipOverlay.hovered(item.getCanvasBounds(), parent == null ? widget.getBounds() : parent.getBounds(), client.getMouseCanvasPosition())) return;
        // Do not canonicalize charge/dose variants: each actual item has its own native effect mapping.
        Effect effect = stats.get(id); if (effect == null) return;
        StatsChanges changes = effect.calculate(client); if (changes == null) return;
        List<String> lines = describe(items.getItemComposition(id).getName(), changes);
        if (lines.size() > 1) hovers.submitItemSupplement(id, lines);
    }
    static List<String> describe(String name, StatsChanges changes)
    {
        List<String> lines = new ArrayList<>(); lines.add(name + " · per use");
        if (changes == null || changes.getStatChanges() == null) return lines;
        for (StatChange change : changes.getStatChanges())
        {
            if (change == null || change.getStat() == null) continue;
            int actual = change.getRelative(), potential = change.getTheoretical();
            String row = change.getStat().getName() + " " + (actual >= 0 ? "+" : "") + actual + " → " + change.getAbsolute();
            if (potential > 0 && potential > Math.max(0, actual)) row += " · wasted " + (potential - Math.max(0, actual));
            lines.add(row);
        }
        return lines;
    }
}
