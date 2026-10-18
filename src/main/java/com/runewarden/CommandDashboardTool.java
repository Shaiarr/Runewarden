/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayManager;

/** One dashboard snapshot; equipment is the current worn container, never inventory or cached bank data. */
@Singleton
final class CommandDashboardTool
{
    private final Client client;
    private final ItemManager itemManager;
    private final OverlayManager overlays;
    private final CommandDashboardOverlay overlay;
    private final RunewardenConfig config;
    private volatile boolean started;
    private volatile Dashboard reading = Dashboard.empty();

    @Inject CommandDashboardTool(Client client, ItemManager itemManager, OverlayManager overlays, RunewardenConfig config)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.overlays = overlays;
        this.config = config;
        this.overlay = new CommandDashboardOverlay(this, config);
    }

    net.runelite.client.ui.overlay.Overlay overlay() { return overlay; }
    void setHudLocked(boolean locked) { overlay.setHudLocked(locked); }

    void start()
    {
        if (started) return;
        started = true;
        clear();
        // Native add() otherwise deletes the location of a previously locked card.
        boolean movable = overlay.isMovable();
        overlay.setHudLocked(false);
        try { overlays.add(overlay); }
        finally { overlay.setHudLocked(!movable); }
    }

    void stop()
    {
        started = false;
        clear();
        overlays.remove(overlay);
    }

    void clear() { if (reading.loggedIn) reading = Dashboard.empty(); }
    boolean started() { return started; }
    Dashboard reading() { return reading; }

    /** Client thread only. */
    void onGameTick()
    {
        if (!started) return;
        if (!config.dashboardHud() || !HudStyle.visible(HudStyle.settings(config, "dashboard")))
        {
            clear();
            return;
        }
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            clear();
            return;
        }
        List<WornSlot> equipment = new ArrayList<>();
        if (config.dashboardEquipment())
        {
            ItemContainer worn = client.getItemContainer(InventoryID.WORN);
            for (SlotPosition position : LAYOUT)
            {
                Item item = worn == null ? null : worn.getItem(position.slot.getSlotIdx());
                int id = item == null ? -1 : item.getId();
                int quantity = item == null ? 0 : item.getQuantity();
                String name = position.label;
                BufferedImage image = null;
                if (id >= 0 && quantity > 0)
                {
                    ItemComposition composition = itemManager.getItemComposition(id);
                    if (composition != null && composition.getName() != null) name = composition.getName();
                    image = itemManager.getImage(id, quantity, quantity > 1);
                }
                equipment.add(new WornSlot(position, id, Math.max(0, quantity), name, image));
            }
        }
        EnumSet<WorldType> types = client.getWorldType();
        CurrentMaxHit.Reading maxHit = CurrentMaxHit.read(client, itemManager);
        reading = new Dashboard(true, client.getWorld(), worldLabel(types), equipment,
            new MaxHitReading(maxHit.maxHit, maxHit.maxHit == null ? maxHit.reason : maxHit.scope));
    }

    static String worldLabel(EnumSet<WorldType> types)
    {
        if (types == null) return "World status unavailable";
        if (types.contains(WorldType.DEADMAN)) return "Deadman";
        if (types.contains(WorldType.PVP)) return types.contains(WorldType.HIGH_RISK) ? "High-risk PvP" : "PvP world";
        if (types.contains(WorldType.BETA_WORLD) || types.contains(WorldType.NOSAVE_MODE)) return "Beta world";
        if (types.contains(WorldType.SEASONAL)) return "Seasonal world";
        if (types.contains(WorldType.QUEST_SPEEDRUNNING)) return "Quest speedrunning";
        if (types.contains(WorldType.SKILL_TOTAL)) return "Skill total · " + (types.contains(WorldType.MEMBERS) ? "Members" : "Free world");
        return types.contains(WorldType.MEMBERS) ? "Members world" : "Free world";
    }

    static final class SlotPosition
    {
        final EquipmentInventorySlot slot;
        final String label;
        final int column;
        final int row;
        SlotPosition(EquipmentInventorySlot slot, String label, int column, int row)
        {
            this.slot = slot;
            this.label = label;
            this.column = column;
            this.row = row;
        }
    }

    // RuneLite's 14-index worn container includes ARMS/HAIR/JAW appearance indices. The 11 real item slots
    // retain their native worn-equipment arrangement and raw indices; appearance indices are not item slots.
    static final List<SlotPosition> LAYOUT = Collections.unmodifiableList(java.util.Arrays.asList(
        new SlotPosition(EquipmentInventorySlot.HEAD, "Head", 1, 0),
        new SlotPosition(EquipmentInventorySlot.CAPE, "Cape", 0, 1),
        new SlotPosition(EquipmentInventorySlot.AMULET, "Neck", 1, 1),
        new SlotPosition(EquipmentInventorySlot.AMMO, "Ammo", 2, 1),
        new SlotPosition(EquipmentInventorySlot.WEAPON, "Weapon", 0, 2),
        new SlotPosition(EquipmentInventorySlot.BODY, "Body", 1, 2),
        new SlotPosition(EquipmentInventorySlot.SHIELD, "Shield", 2, 2),
        new SlotPosition(EquipmentInventorySlot.LEGS, "Legs", 1, 3),
        new SlotPosition(EquipmentInventorySlot.GLOVES, "Hands", 0, 4),
        new SlotPosition(EquipmentInventorySlot.BOOTS, "Feet", 1, 4),
        new SlotPosition(EquipmentInventorySlot.RING, "Ring", 2, 4)));

    static final class WornSlot
    {
        final SlotPosition position;
        final int id;
        final int quantity;
        final String name;
        final BufferedImage image;
        WornSlot(SlotPosition position, int id, int quantity, String name, BufferedImage image)
        {
            this.position = position;
            this.id = id;
            this.quantity = quantity;
            this.name = name;
            this.image = image;
        }
        boolean equipped() { return id >= 0 && quantity > 0; }
    }

    static final class MaxHitReading
    {
        final Integer value;
        final String scope;
        MaxHitReading(Integer value, String scope)
        {
            this.value = value;
            this.scope = scope;
        }
        static MaxHitReading unavailable(String scope) { return new MaxHitReading(null, scope); }
    }

    static final class Dashboard
    {
        final boolean loggedIn;
        final int world;
        final String worldDescription;
        final List<WornSlot> equipment;
        final MaxHitReading maxHit;
        Dashboard(boolean loggedIn, int world, String worldDescription, List<WornSlot> equipment, MaxHitReading maxHit)
        {
            this.loggedIn = loggedIn;
            this.world = world;
            this.worldDescription = worldDescription;
            this.equipment = Collections.unmodifiableList(new ArrayList<>(equipment));
            this.maxHit = maxHit;
        }
        static Dashboard empty()
        {
            return new Dashboard(false, 0, "Log in to connect", Collections.emptyList(), MaxHitReading.unavailable("Log in to calculate"));
        }
    }
}
