/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.SoundEffectID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;

/** Captures native tick/context facts and keeps presentation settings on the current character. */
@Singleton
final class HudSession
{
    private final Client client;
    private final RunewardenConfig config;
    private final ConfigManager manager;
    private final HudSettingsTool settings;
    private boolean started;
    private String profile, lastLayout;
    private int tick;

    HudSession(Client client, RunewardenConfig config, ConfigManager manager) { this(client, config, manager, null); }
    @Inject HudSession(Client client, RunewardenConfig config, ConfigManager manager, HudSettingsTool settings)
    {
        this.client = client; this.config = config; this.manager = manager; this.settings = settings;
    }
    void start() { started = true; profile = null; lastLayout = null; tick = 0; }
    void stop() { started = false; profile = null; tick = 0; HudStyle.context(false, false, 0, 0); }
    void onGameTick()
    {
        if (!started) return;
        Player player = client.getLocalPlayer();
        if (client.getGameState() != GameState.LOGGED_IN || player == null)
        {
            HudStyle.context(false, false, 0, 0);
            return;
        }
        String current = manager.getRSProfileKey();
        if (current != null && !current.equals(profile))
        {
            profile = current;
            String saved = manager.getRSProfileConfiguration(RunewardenConfig.GROUP, "hudCharacterLayout");
            // First use inherits the existing layout. Subsequent character switches start with its own defaults.
            if (saved == null) saved = lastLayout == null ? config.hudWidgetSettings() : "";
            try { HudPreferences.decode(saved); }
            catch (IllegalArgumentException ignored) { saved = ""; }
            lastLayout = saved == null ? "" : saved;
            manager.setConfiguration(RunewardenConfig.GROUP, "hudWidgetSettings", lastLayout);
            manager.setRSProfileConfiguration(RunewardenConfig.GROUP, "hudCharacterLayout", lastLayout);
        }
        String layout = config.hudWidgetSettings();
        if (profile != null && profile.equals(current) && layout != null && !layout.equals(lastLayout))
        {
            manager.setRSProfileConfiguration(RunewardenConfig.GROUP, "hudCharacterLayout", layout);
            lastLayout = layout;
        }
        if (settings != null) settings.profileTick(current);
        Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS);
        boolean combat = HudStyle.inCombat(player);
        HudStyle.context(combat, bank != null && !bank.isHidden(), System.nanoTime(), tick++);
        if (config.resourceHud() && config.tickDisplay() && config.tickSound()
            && HudStyle.visible(HudStyle.settings(config, "resources"))
            && tick % HudPreferences.clamp(config.tickCycle(), 1, 12) == 0)
            client.playSoundEffect(SoundEffectID.GE_INCREMENT_PLOP, 25);
    }
}
