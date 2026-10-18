/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name = "Runewarden", description = "Live HUDs, gathering timers, harvest runs and session reports",
    tags = {"hud", "timers", "farming", "thieving", "loot", "ticks"})
public final class RunewardenPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private ClientToolbar toolbar;
    @Inject private OverlayManager overlayManager;
    @Inject private RunewardenConfig config;
    @Inject private RunewardenDataStore data;
    @Inject private HudSettingsTool settings;
    @Inject private HudSession hud;
    @Inject private RunewardenResourceOverlay resources;
    @Inject private CommandDashboardTool dashboard;
    @Inject private InventoryPreviewOverlay inventory;
    @Inject private SkillingCoach gathering;
    @Inject private SkillingCoachOverlay outfit;
    @Inject private NodeTimerOverlay nodes;
    @Inject private ActionProgressTool actions;
    @Inject private VarlamoreThievingCoach thieving;
    @Inject private FieldCoach effects;
    @Inject private HarvestClockTool harvest;
    @Inject private FarmRunsTool farms;
    @Inject private FarmPatchOverlay patches;
    @Inject private BirdHouseSceneOverlay birdHouses;
    @Inject private FarmRunOverlay farmRun;
    @Inject private SlayerTaskHud slayer;
    @Inject private SlayerTaskOverlay slayerOverlay;
    @Inject private HarvestNavigation travel;
    @Inject private PohTravelCapabilities poh;
    @Inject private SharedActivitySession session;
    @Inject private RunLootTool loot;
    @Inject private RoutinePlannerTool routines;
    @Inject private ResourceTooltipOverlay tooltips;
    @Inject private HoverCardOverlay hover;
    private final List<Overlay> overlays = new ArrayList<>();
    private volatile RunewardenPanel panel;
    private volatile boolean running;
    private volatile long epoch;
    private NavigationButton navigation;

    @Provides RunewardenConfig provideConfig(ConfigManager manager)
    { return manager.getConfig(RunewardenConfig.class); }

    @Override protected void startUp()
    {
        running = true;
        long ticket = ++epoch;
        data.reload();
        register("resources", resources, resources::setHudLocked);
        register("dashboard", dashboard.overlay(), dashboard::setHudLocked);
        register("inventory", inventory, inventory::setHudLocked);
        register("outfit", outfit, outfit::setHudLocked);
        register("harvest", harvest.overlay(), harvest::setHudLocked);
        register("harvest-run", farmRun, farmRun::setHudLocked);
        register("slayer-task", slayerOverlay, slayerOverlay::setHudLocked);
        register("action-progress", actions.overlay(), actions::setHudLocked);
        register("thieving", thieving.hud(), thieving::setHudLocked);
        register("farm-patches", patches, locked -> {});
        register("node-timers", nodes, locked -> {});
        FieldStatusOverlay effectHud = (FieldStatusOverlay) effects.overlays().get(0);
        register("field-effects", effectHud, effectHud::setHudLocked);
        overlays.add(thieving.scene());
        overlays.add(thieving.minimap());
        overlays.add(birdHouses);
        overlays.add(effects.overlays().get(1));
        overlays.add(tooltips); overlays.add(hover);
        settings.prepareForOverlayRegistration();
        overlays.forEach(overlayManager::add);
        farms.setNavigation(travel);
        farms.setOpenAction(() -> open("Harvest runs"));
        farmRun.setOpenAction(() -> open("Harvest runs"));
        thieving.setNavigation(travel);
        harvest.setOpenAction(() -> open("Timers"));
        harvest.setRunGroupSupport(FarmRunsTool::supportsGroup);
        harvest.setOpenRunAction(group -> { if (farms.openGroup(group)) open("Harvest runs"); });
        clientThread.invokeLater(() ->
        {
            if (!running || ticket != epoch) return;
            session.start(); loot.start(); routines.start(); slayer.start();
            gathering.startUp(); actions.start(); thieving.start(); effects.start();
            harvest.start(); farms.start(); poh.start(); travel.start();
            dashboard.start(); hud.start(); settings.start();
        });
        SwingUtilities.invokeLater(() ->
        {
            if (!running || ticket != epoch) return;
            panel = createPanel();
            navigation = NavigationButton.builder().tooltip("Runewarden").icon(icon()).priority(7).panel(panel).build();
            toolbar.addNavigation(navigation);
        });
    }

    private void register(String key, Overlay overlay, java.util.function.Consumer<Boolean> lock)
    { settings.registerWidget(key, overlay, lock); overlays.add(overlay); }

    RunewardenPanel createPanel()
    {
        RunewardenPanel result = new RunewardenPanel();
        JPanel overview = SidebarUi.column();
        overview.add(farms.createSummaryPanel()); overview.add(harvest.createDashboardPanel());
        result.addModule("Overview", overview);
        result.addModule("Harvest runs", farms.createPanel());
        JPanel timers = SidebarUi.column();
        timers.add(harvest.createPanel()); timers.add(effects.createPanel());
        result.addModule("Timers", timers);
        JPanel skilling = SidebarUi.column();
        skilling.add(gathering.createPanel()); skilling.add(actions.createPanel());
        result.addModule("Gathering", skilling);
        result.addModule("Varlamore", thieving.createPanel());
        result.addModule("Session loot", loot.createPanel());
        result.addModule("Slayer", slayer.createPanel());
        result.addModule("Routines & dailies", routines.createPanel());
        result.addModule("HUD settings", settings.createPanel());
        result.selectModule("Overview");
        return result;
    }

    private void open(String page)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (running && panel != null && navigation != null)
            { panel.selectModule(page); toolbar.openPanel(navigation); }
        });
    }

    @Override protected void shutDown()
    {
        running = false; epoch++;
        settings.stop();
        clientThread.invokeLater(() ->
        {
            travel.stop(); poh.stop(); farms.stop(); harvest.stop();
            actions.stop(); thieving.stop(); effects.stop(); gathering.shutDown();
            loot.stop(); session.stop(); routines.stop(); slayer.stop();
            dashboard.stop(); inventory.clear(); hud.stop(); hover.clear();
        });
        overlays.forEach(overlayManager::remove); overlays.clear();
        NavigationButton previous = navigation;
        navigation = null; panel = null;
        SwingUtilities.invokeLater(() -> { if (previous != null) toolbar.removeNavigation(previous); });
    }

    @Subscribe public void onGameTick(GameTick event)
    {
        if (!running) return;
        session.onGameTick(); hud.onGameTick(); inventory.onGameTick(); dashboard.onGameTick();
        gathering.onGameTick(event); actions.onGameTick(); thieving.onGameTick(); effects.onGameTick();
        poh.onGameTick(); travel.onGameTick(); farms.onGameTick(); harvest.onGameTick();
        loot.onGameTick(); routines.onGameTick(); slayer.onGameTick();
    }
    @Subscribe public void onPostClientTick(PostClientTick event)
    { if (running) actions.onClientTick(); }
    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (!running) return;
        gathering.onGameStateChanged(event); thieving.onGameStateChanged(event); effects.onGameStateChanged(event);
        travel.onGameStateChanged(event.getGameState()); actions.clear();
        if (event.getGameState() != GameState.LOGGED_IN)
        { inventory.clear(); dashboard.clear(); hover.clear(); session.suspend(); loot.suspend(); slayer.clear(); }
    }
    @Subscribe public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
    {
        if (!running) return;
        data.reload(); farms.clear(); harvest.clear(); actions.clear(); thieving.clear(); effects.clear();
        loot.clear(); session.clear(); routines.clear(); slayer.clear(); dashboard.clear(); inventory.clear(); hover.clear();
        travel.onGameStateChanged(GameState.LOGIN_SCREEN);
        if (panel != null) panel.resetNavigation();
    }
    @Subscribe public void onProfileChanged(ProfileChanged event)
    {
        if (!running) return;
        long ticket = epoch;
        clientThread.invokeLater(() ->
        {
            if (!running || epoch != ticket) return;
            data.reload(); session.onConfigProfileChanged(); loot.onConfigProfileChanged(); routines.onConfigProfileChanged(); slayer.clear();
            farms.clear(); harvest.clear(); actions.start(); thieving.clear(); thieving.reloadPreferences(); effects.start();
            GameStateChanged reset = new GameStateChanged(); reset.setGameState(GameState.LOGIN_SCREEN);
            gathering.onGameStateChanged(reset);
            dashboard.clear(); inventory.clear(); hover.clear();
            travel.onGameStateChanged(GameState.LOGIN_SCREEN);
            poh.stop(); poh.start(); hud.stop(); hud.start(); settings.onProfileChanged();
            if (panel != null) panel.resetNavigation();
        });
    }
    @Subscribe public void onConfigChanged(ConfigChanged event)
    {
        if (!running) return;
        if (RunewardenConfig.GROUP.equals(event.getGroup())) settings.onConfigChanged();
        if (VarlamoreThievingCoach.GROUP.equals(event.getGroup())) thieving.reloadPreferences();
        effects.onConfigChanged(event);
    }
    @Subscribe public void onItemContainerChanged(ItemContainerChanged event)
    { if (running) actions.onItemContainerChanged(event); }
    @Subscribe public void onAnimationChanged(AnimationChanged event)
    { if (running) { gathering.onAnimationChanged(event); actions.onAnimationChanged(event); } }
    @Subscribe public void onStatChanged(StatChanged event)
    { if (running) { gathering.onStatChanged(event); actions.onStatChanged(event); } }
    @Subscribe public void onActorDeath(ActorDeath event)
    { if (running) gathering.onActorDeath(event); }
    @Subscribe public void onScriptPreFired(ScriptPreFired event)
    { if (running) gathering.onScriptPreFired(event); }
    @Subscribe public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!running) return;
        gathering.onMenuOptionClicked(event); actions.onMenuOptionClicked(event);
        thieving.onMenuOptionClicked(event); farms.onMenuOptionClicked(event); travel.onMenuOptionClicked(event);
    }
    @Subscribe public void onChatMessage(ChatMessage event)
    { if (running) { thieving.onChatMessage(event); farms.onChatMessage(event); } }
    @Subscribe public void onGameObjectSpawned(GameObjectSpawned event)
    { if (running) { gathering.onGameObjectSpawned(event); thieving.onGameObjectSpawned(event); } }
    @Subscribe public void onGameObjectDespawned(GameObjectDespawned event)
    { if (running) { gathering.onGameObjectDespawned(event); thieving.onGameObjectDespawned(event); } }
    @Subscribe public void onWallObjectSpawned(WallObjectSpawned event)
    { if (running) thieving.onWallObjectSpawned(event); }
    @Subscribe public void onWallObjectDespawned(WallObjectDespawned event)
    { if (running) thieving.onWallObjectDespawned(event); }
    @Subscribe public void onNpcDespawned(NpcDespawned event)
    { if (running) thieving.onNpcDespawned(event); }
    @Subscribe public void onPluginMessage(PluginMessage event)
    { travel.onPluginMessage(event); }
    @Subscribe public void onServerNpcLoot(ServerNpcLoot event)
    { if (running) loot.onServerNpcLoot(event); }
    @Subscribe public void onPlayerLootReceived(PlayerLootReceived event)
    { if (running) loot.onPlayerLootReceived(event); }
    @Subscribe public void onLootReceived(LootReceived event)
    { if (running) loot.onLootReceived(event); }

    static BufferedImage icon()
    {
        BufferedImage result = new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try
        {
            g.setColor(new Color(17,17,20)); g.fillRoundRect(1,1,30,30,7,7);
            g.setColor(new Color(224,190,113)); g.drawRoundRect(1,1,29,29,7,7);
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18)); g.drawString("R",9,23);
        }
        finally { g.dispose(); }
        return result;
    }
}
