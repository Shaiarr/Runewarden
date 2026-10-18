/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/** Hover facts for the original inventory/bank items; it does not alter item menus or clicks. */
@Singleton
public final class ResourceTooltipOverlay extends WidgetItemOverlay
{
    private final Client client;
    private final RunewardenConfig config;
    private final ItemManager itemManager;
    private final TooltipManager tooltips;
    private final SkillingResourceCatalog catalog;
    private final HoverCardOverlay hoverCards;
    @Inject ResourceTooltipOverlay(Client client, RunewardenConfig config, ItemManager itemManager, TooltipManager tooltips,
        SkillingResourceCatalog catalog, HoverCardOverlay hoverCards)
    {
        this.client = client; this.config = config; this.itemManager = itemManager; this.tooltips = tooltips; this.catalog = catalog;
        this.hoverCards = hoverCards;
        showOnInventory(); showOnBank();
    }

    ResourceTooltipOverlay(Client client, RunewardenConfig config, ItemManager itemManager, TooltipManager tooltips, SkillingResourceCatalog catalog)
    { this(client, config, itemManager, tooltips, catalog, null); }

    @Override public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem item)
    {
        if (!config.resourceTooltips() || client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen()
            || item == null || itemId <= 0 || item.getQuantity() <= 0 || item.getDraggingCanvasBounds() != null) return;
        Widget widget = item.getWidget();
        if (widget == null || widget.isHidden()) return;
        Widget parent = widget.getParent();
        Rectangle parentBounds = parent == null ? widget.getBounds() : parent.getBounds();
        if (!hovered(item.getCanvasBounds(), parentBounds, client.getMouseCanvasPosition())) return;
        SkillingResourceCatalog.Resource resource = catalog.find(itemManager.canonicalize(itemId));
        if (resource == null) return;
        String content = SkillingResourceCatalog.tooltip(resource, client.isKeyPressed(KeyCode.KC_SHIFT), client::getRealSkillLevel,
            Math.max(6, Math.min(18, (client.getCanvasHeight() - 64) / 16)));
        if (hoverCards != null) hoverCards.submitItemHtml(itemId, content);
        else tooltips.add(new Tooltip(content)); // Compatibility for older standalone fixtures; DI always supplies the top-layer card.
    }

    static boolean hovered(Rectangle item, Rectangle visibleParent, Point mouse)
    {
        return item != null && mouse != null && item.contains(mouse.getX(), mouse.getY())
            && (visibleParent == null || visibleParent.contains(mouse.getX(), mouse.getY()));
    }
}
