/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(RunewardenConfig.GROUP)
public interface RunewardenConfig extends Config
{
    enum InventoryMode { CLOSED_TAB, ALWAYS, COMBAT }
    enum TickDisplay { PULSE, BAR, DOTS, NUMBER }
    enum TimerDisplay { TEXT, RADIAL, BOTH }
    String GROUP = "runewarden";

    @ConfigItem(keyName = "hudCompact", name = "Compact HUD", position = 0,
        description = "Use compact spacing and prioritise ready harvests and the next timer.")
    default boolean hudCompact() { return true; }

    @ConfigItem(keyName = "hudLocked", name = "Lock HUD positions and sizes", position = 11,
        description = "Lock Runewarden card movement and Ctrl-drag resizing.")
    default boolean hudLocked() { return false; }

    @Range(min = 40, max = 100)
    @ConfigItem(keyName = "hudOpacity", name = "HUD opacity", position = 1,
        description = "Background opacity for the Runewarden HUD, in percent.")
    default int hudOpacity() { return 85; }

    @ConfigItem(keyName = "resourceHud", name = "Resource HUD", position = 2,
        description = "Show horizontal health, prayer, special attack and run-energy bars.")
    default boolean resourceHud() { return true; }

    @ConfigItem(keyName = "dashboardHud", name = "Character dashboard", position = 3,
        description = "Show a movable clock, world, supported base max hit and worn-equipment block.")
    default boolean dashboardHud() { return true; }

    @ConfigItem(keyName = "dashboardEquipment", name = "Worn equipment", position = 4,
        description = "Include the worn-equipment grid in the character dashboard.")
    default boolean dashboardEquipment() { return true; }

    @ConfigItem(keyName = "clockHud", name = "Dashboard clock", position = 5,
        description = "Include the clock in the character dashboard.")
    default boolean clockHud() { return true; }

    @ConfigItem(keyName = "clockUtc", name = "Use UTC clock", position = 6,
        description = "Use UTC instead of your computer's local time.")
    default boolean clockUtc() { return false; }

    @ConfigItem(keyName = "harvestHud", name = "Harvest reminder HUD", position = 8,
        description = "Show selected farming and bird-house timers from Time Tracking.")
    default boolean harvestHud() { return true; }

    @ConfigItem(keyName = "farmRunHud", name = "Active harvest run", position = 37,
        description = "Show the current location and observed progress while a harvest run is active.")
    default boolean farmRunHud() { return true; }

    @ConfigItem(keyName = "slayerTaskHud", name = "Slayer task card", position = 38,
        description = "Show current Slayer task details at a recognized task location.")
    default boolean slayerTaskHud() { return true; }

    @ConfigItem(keyName = "nodeTimers", name = "Node timers", position = 9,
        description = "Show supported depletion estimates and observed respawn timers above nodes.")
    default boolean nodeTimers() { return true; }

    @ConfigItem(keyName = "nodeTimerDisplay", name = "Node countdown style", position = 34,
        description = "Choose text, radial countdowns or both above observed skilling nodes.")
    default TimerDisplay nodeTimerDisplay() { return TimerDisplay.BOTH; }

    @ConfigItem(keyName = "harvestTimerDisplay", name = "Harvest countdown style", position = 35,
        description = "Choose text, radial countdowns or both for selected harvest timers.")
    default TimerDisplay harvestTimerDisplay() { return TimerDisplay.BOTH; }

    @ConfigItem(keyName = "effectTimerDisplay", name = "Effect countdown style", position = 36,
        description = "Choose text, radial countdowns or both for observed buffs and cooldowns.")
    default TimerDisplay effectTimerDisplay() { return TimerDisplay.BOTH; }

    @ConfigItem(keyName = "skillingOutfitReminder", name = "Skilling outfit reminder", position = 10,
        description = "Show missing outfit pieces for the skill you are training.")
    default boolean skillingOutfitReminder() { return true; }

    @ConfigItem(keyName = "resourceTooltips", name = "Skilling resource hover", position = 12,
        description = "Show uses, action XP and sources when hovering skilling resources in inventory or bank.")
    default boolean resourceTooltips() { return true; }

    @ConfigItem(keyName = "inventoryPreview", name = "Inventory preview", position = 18,
        description = "Show an actual 28-slot inventory preview while using other game tabs.")
    default boolean inventoryPreview() { return true; }

    @ConfigItem(keyName = "inventoryPreviewMode", name = "Inventory visibility", position = 19,
        description = "Show the preview only while the inventory is closed, always, or during combat.")
    default InventoryMode inventoryPreviewMode() { return InventoryMode.CLOSED_TAB; }

    @ConfigItem(keyName = "inventoryHideBank", name = "Hide inventory at bank", position = 20,
        description = "Hide the inventory preview while the bank is open.")
    default boolean inventoryHideBank() { return true; }

    @ConfigItem(keyName = "inventoryCounts", name = "Inventory quantities", position = 21,
        description = "Show stack counts on inventory preview icons.")
    default boolean inventoryCounts() { return true; }

    @ConfigItem(keyName = "tickDisplay", name = "Tick indicator", position = 22,
        description = "Show a visual server-tick heartbeat beside the resource bars.")
    default boolean tickDisplay() { return true; }

    @ConfigItem(keyName = "tickDisplayMode", name = "Tick appearance", position = 23,
        description = "Choose a pulse, sweeping bar, dots or numbered cycle.")
    default TickDisplay tickDisplayMode() { return TickDisplay.PULSE; }

    @Range(min = 1, max = 12)
    @ConfigItem(keyName = "tickCycle", name = "Tick cycle", position = 24,
        description = "Number of ticks in the visual cycle; this does not predict an action timer.")
    default int tickCycle() { return 4; }

    @ConfigItem(keyName = "tickSound", name = "Tick sound", position = 25,
        description = "Play a quiet native sound once per selected tick cycle.")
    default boolean tickSound() { return false; }

    @Range(min = 1, max = 100)
    @ConfigItem(keyName = "healthAlert", name = "Low health threshold", position = 26,
        description = "Pulse health below this percentage.")
    default int healthAlert() { return 25; }

    @Range(min = 1, max = 100)
    @ConfigItem(keyName = "prayerAlert", name = "Low prayer threshold", position = 27,
        description = "Pulse prayer below this percentage.")
    default int prayerAlert() { return 15; }

    @ConfigItem(keyName = "hudWidgetSettings", name = "HUD layout data", hidden = true,
        description = "Runewarden presentation settings managed by the HUD editor.")
    default String hudWidgetSettings() { return ""; }

    @ConfigItem(keyName = "clickTrainingCue", name = "Click training cue", position = 29,
        description = "Flash CLICK on each received game tick for rhythm practice.")
    default boolean clickTrainingCue() { return true; }
}
