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
    String THIRD_PARTY_WARNING = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers";

    @ConfigItem(keyName = "wikiItemSources", name = "Live Wiki item sources", position = 33,
        description = "Fetch sources from oldschool.runescape.wiki for the item you select. Sends the selected item title and your IP address; bundled sources remain available with this disabled.",
        warning = THIRD_PARTY_WARNING)
    default boolean wikiItemSources() { return false; }

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

    @ConfigItem(keyName = "guideHud", name = "Guide coach HUD", position = 7,
        description = "Show the current locally selected guide step on screen.")
    default boolean guideHud() { return true; }

    @ConfigItem(keyName = "harvestHud", name = "Harvest reminder HUD", position = 8,
        description = "Show selected farming and bird-house timers from Time Tracking.")
    default boolean harvestHud() { return true; }

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

    @ConfigItem(keyName = "minigameTimerDisplay", name = "Activity countdown style", position = 38,
        description = "Choose native clock text, radial countdowns or both in activity HUDs.")
    default TimerDisplay minigameTimerDisplay() { return TimerDisplay.BOTH; }

    @ConfigItem(keyName = "contextActions", name = "Contextual Runewarden menus", position = 37,
        description = "Add Runewarden references and preparation to right-click menus while holding Shift.")
    default boolean contextActions() { return true; }

    @ConfigItem(keyName = "skillingOutfitReminder", name = "Skilling outfit reminder", position = 10,
        description = "Show missing outfit pieces for the skill you are training.")
    default boolean skillingOutfitReminder() { return true; }

    @ConfigItem(keyName = "resourceTooltips", name = "Skilling resource hover", position = 12,
        description = "Show uses, action XP and sources when hovering skilling resources in inventory or bank.")
    default boolean resourceTooltips() { return true; }

    @ConfigItem(keyName = "hoverNpcSight", name = "Mob hover sight", position = 13,
        description = "Check line of sight in both directions for the NPC under the mouse.")
    default boolean hoverNpcSight() { return true; }

    @ConfigItem(keyName = "hoverNpcSightAlways", name = "Always show mob hover", position = 32,
        description = "Show mob sight and combat cards without holding Shift. Otherwise hold Shift while hovering.")
    default boolean hoverNpcSightAlways() { return false; }

    @ConfigItem(keyName = "waveEntryCoach", name = "Fight Caves entrance", position = 14,
        description = "Show the Fight Caves rotation countdown and first-wave NPC spawn origins on entry.")
    default boolean waveEntryCoach() { return true; }

    @Range(min = 1, max = 15)
    @ConfigItem(keyName = "fightCavesTargetRotation", name = "Fight Caves target rotation", position = 15,
        description = "The rotation to wait for outside the Fight Caves.")
    default int fightCavesTargetRotation() { return 1; }

    @ConfigItem(keyName = "infernoOpeningOnEntry", name = "Inferno opening on entry", position = 16, hidden = true,
        description = "Restore the opening pack selected in Tile markers when entering the Inferno.")
    default boolean infernoOpeningOnEntry() { return false; }

    @ConfigItem(keyName = "colosseumOpeningOnEntry", name = "Colosseum opening on entry", position = 17, hidden = true,
        description = "Restore the opening pack selected in Tile markers when entering the Colosseum.")
    default boolean colosseumOpeningOnEntry() { return false; }

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

    @ConfigItem(keyName = "hoverMobFacts", name = "Mob combat reference", position = 28,
        description = "Show usual attack styles, protection reference and weaknesses on mob hover.")
    default boolean hoverMobFacts() { return true; }

    @ConfigItem(keyName = "clickTrainingCue", name = "Click training cue", position = 29,
        description = "Flash CLICK on each received game tick for rhythm practice.")
    default boolean clickTrainingCue() { return true; }
    @ConfigItem(keyName="trainingPauseKey",name="Training start / pause key",position=30,
        description="Start the prepared training session, or pause/resume its timer.")
    default net.runelite.client.config.Keybind trainingPauseKey(){return net.runelite.client.config.Keybind.NOT_SET;}
    @ConfigItem(keyName="trainingRestartKey",name="Training restart key",position=31,
        description="Start a fresh run for the prepared activity.")
    default net.runelite.client.config.Keybind trainingRestartKey(){return net.runelite.client.config.Keybind.NOT_SET;}

    @ConfigItem(keyName="worldHopPreviousKey",name="World hopper: previous key",position=32,
        description="Manually hop to the previous eligible world using the world hopper filters.")
    default net.runelite.client.config.Keybind worldHopPreviousKey(){return net.runelite.client.config.Keybind.NOT_SET;}

    @ConfigItem(keyName="worldHopNextKey",name="World hopper: next key",position=33,
        description="Manually hop to the next eligible world using the world hopper filters.")
    default net.runelite.client.config.Keybind worldHopNextKey(){return net.runelite.client.config.Keybind.NOT_SET;}

    @ConfigItem(keyName="worldHopOutOfDanger",name="World hopper: safe quick hop",position=34,
        description="Exclude PvP and high-risk worlds when using the next/previous world controls.")
    default boolean worldHopOutOfDanger(){return true;}

    @ConfigItem(keyName="worldHopMessage",name="World hopper: chat messages",position=35,
        description="Show the chosen world and failed world-switch attempts in chat.")
    default boolean worldHopMessage(){return true;}

    @ConfigItem(keyName="worldHopMenu",name="World hopper: Hop-to menu",position=36,
        description="Add Hop-to (Runewarden) to online friends and clan members.")
    default boolean worldHopMenu(){return true;}

    @ConfigItem(keyName="worldHopPing",name="World hopper: world pings",position=37,
        description="Measure world latency while the world hopper is open.")
    default boolean worldHopPing(){return true;}

    @ConfigItem(keyName="worldHopDisplayPing",name="World hopper: ping HUD",position=38,
        description="Show the current world's measured latency in a movable Runewarden HUD.")
    default boolean worldHopDisplayPing(){return false;}
}
