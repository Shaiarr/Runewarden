/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** A read-only 28-slot mirror. It has no menu actions or item interaction handlers. */
@Singleton
final class InventoryPreviewOverlay extends Overlay
{
    private final Client client;
    private final RunewardenConfig config;
    private final ItemManager items;
    private volatile Slot[] slots = new Slot[28];
    private volatile boolean loggedIn;
    private volatile boolean inventoryOpen;
    private volatile boolean combat;
    private boolean populated;
    static final class Slot
    {
        final int id, quantity;
        final BufferedImage image;
        Slot(int id, int quantity, BufferedImage image) { this.id = id; this.quantity = quantity; this.image = image; }
    }
    @Inject InventoryPreviewOverlay(Client client, RunewardenConfig config, ItemManager items)
    {
        this.client = client; this.config = config; this.items = items;
        setPosition(OverlayPosition.BOTTOM_RIGHT); setLayer(OverlayLayer.ABOVE_WIDGETS);
        setMovable(true); setResizable(false);
    }
    void setHudLocked(boolean locked) { setMovable(!locked); }
    void clear() { loggedIn = false; slots = new Slot[28]; populated = false; }
    void onGameTick()
    {
        if (!config.inventoryPreview()) { if (loggedIn) clear(); return; }
        loggedIn = client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
        if (!loggedIn) { if (populated) { slots = new Slot[28]; populated = false; } return; }
        Widget inventory = client.getWidget(InterfaceID.Inventory.ITEMS);
        inventoryOpen = inventory != null && !inventory.isHidden();
        combat = HudStyle.inCombat(client.getLocalPlayer());
        if (!shouldShow(config.inventoryPreviewMode(), inventoryOpen, combat)
            || config.inventoryHideBank() && HudStyle.bankOpen()
            || !HudStyle.visible(HudStyle.settings(config, "inventory")))
        {
            if (populated) { slots = new Slot[28]; populated = false; }
            return;
        }
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        Slot[] next = new Slot[28];
        if (container != null)
        {
            Item[] actual = container.getItems();
            for (int i = 0; i < Math.min(28, actual.length); i++)
            {
                Item item = actual[i];
                if (item != null && item.getId() >= 0 && item.getQuantity() > 0)
                    next[i] = new Slot(item.getId(), item.getQuantity(), items.getImage(item.getId()));
            }
        }
        slots = next;
        populated = true;
    }
    static boolean shouldShow(RunewardenConfig.InventoryMode mode, boolean open, boolean inCombat)
    {
        return mode == RunewardenConfig.InventoryMode.ALWAYS
            || mode == RunewardenConfig.InventoryMode.COMBAT && inCombat
            || mode == RunewardenConfig.InventoryMode.CLOSED_TAB && !open;
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        HudPreferences.Style style = HudStyle.settings(config, "inventory");
        if (!config.inventoryPreview() || !loggedIn || client.getGameState() != GameState.LOGGED_IN
            || !HudStyle.visible(style) || config.inventoryHideBank() && HudStyle.bankOpen()
            || !shouldShow(config.inventoryPreviewMode(), inventoryOpen, combat)) return null;
        Slot[] reading = slots;
        int width = 176, height = 272;
        Graphics2D g = HudStyle.canvas(graphics, style);
        try
        {
            HudStyle.panel(g, width, height, style);
            g.setFont(HudStyle.label(style, 11)); g.setColor(style.accent); g.drawString("INVENTORY", 10, 19);
            for (int i = 0; i < 28; i++)
            {
                int x = 9 + i % 4 * 40, y = 28 + i / 4 * 34;
                g.setColor(new Color(255, 255, 255, 13)); g.fillRoundRect(x, y, 38, 32, 3, 3);
                Slot slot = reading[i];
                if (slot == null) continue;
                if (slot.image != null) g.drawImage(slot.image, x + (38 - slot.image.getWidth()) / 2,
                    y + (32 - slot.image.getHeight()) / 2, null);
                if (config.inventoryCounts() && slot.quantity > 1)
                {
                    g.setFont(HudStyle.label(10));
                    String count = quantity(slot.quantity);
                    g.setColor(Color.BLACK); g.drawString(count, x + 2, y + 11);
                    g.setColor(style.text); g.drawString(count, x + 1, y + 10);
                }
            }
            return HudStyle.dimension(style, width, height);
        }
        finally { g.dispose(); }
    }
    static String quantity(int number)
    {
        return number >= 10_000_000 ? number / 1_000_000 + "M" : number >= 100_000 ? number / 1000 + "K" : Integer.toString(number);
    }
}
