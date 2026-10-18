/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class HudPositionLifecycleTest
{
    @Test public void lockedCardsKeepTheirNativeLocationsAcrossPluginRestart()
    {
        Fixture f = new Fixture();
        f.tool.prepareForOverlayRegistration(); f.registerCards(); f.tool.start();
        f.putLocations("111:222"); f.tool.profileTick(f.profile[0]);
        String saved = f.saved.get(f.profile[0]);
        f.tool.stop();
        f.tool.prepareForOverlayRegistration();
        for (Overlay overlay : f.owned.values()) assertTrue(overlay.isMovable());
        f.registerCards(); f.tool.start(); f.tool.profileTick(f.profile[0]);
        assertEquals(saved, f.saved.get(f.profile[0]));
        for (Overlay overlay : f.owned.values())
        {
            assertFalse(overlay.isMovable());
            assertEquals("111:222", f.global.get(overlay.getName() + "_preferredLocation"));
        }
        f.tool.stop();
    }

    @Test public void restartingSameCharacterAppliesRestoredPositionBackup()
    {
        Fixture f = new Fixture();
        f.tool.prepareForOverlayRegistration(); f.tool.start();
        f.putLocations("111:222"); f.tool.profileTick(f.profile[0]); f.tool.stop();
        f.putLocations("333:444");
        String restored = HudLayoutPositions.capture(f.manager, f.owned);
        f.saved.put(f.profile[0], restored);
        // Backup restore changes character settings, not RuneLite's global positions.
        f.putLocations("111:222");
        f.tool.prepareForOverlayRegistration(); f.registerCards(); f.tool.start();
        f.tool.profileTick(f.profile[0]);
        assertEquals(restored, f.saved.get(f.profile[0]));
        for (Overlay overlay : f.owned.values())
            assertEquals("333:444", f.global.get(overlay.getName() + "_preferredLocation"));
        f.tool.stop();
    }

    @Test public void restartingOnNewCharacterDoesNotInheritPreviousCharactersPositions()
    {
        Fixture f = new Fixture(); f.tool.start(); f.putLocations("111:222");
        f.tool.profileTick(f.profile[0]); String first = f.saved.get(f.profile[0]); f.tool.stop();
        f.profile[0] = "rsprofileB";
        f.tool.prepareForOverlayRegistration(); f.tool.start(); f.tool.profileTick(f.profile[0]);
        assertEquals("RWP1|", f.saved.get("rsprofileB"));
        assertEquals(first, f.saved.get("rsprofileA"));
        for (Overlay overlay : f.owned.values())
            assertNull(f.global.get(overlay.getName() + "_preferredLocation"));
        f.tool.stop();
    }

    @Test public void independentlyRestartedHarvestAndDashboardKeepLockedLocations()
    {
        Fixture f = new Fixture();
        try
        {
            f.harvest.start(); f.dashboard.start();
            f.harvest.setHudLocked(true); f.dashboard.setHudLocked(true);
            f.putLocations("111:222");
            f.harvest.stop(); f.dashboard.stop(); f.harvest.start(); f.dashboard.start();
            for (Overlay overlay : new Overlay[] { f.harvest.overlay(), f.dashboard.overlay() })
            {
                assertFalse(overlay.isMovable());
                assertEquals("111:222", f.global.get(overlay.getName() + "_preferredLocation"));
            }
        }
        finally { f.harvest.stop(); f.dashboard.stop(); }
    }

    private static final class EffectsCard extends Overlay
    { void lock(boolean locked) { setMovable(!locked); } @Override public Dimension render(Graphics2D graphics) { return null; } }
    private static final class TrainingCard extends Overlay
    { void lock(boolean locked) { setMovable(!locked); } @Override public Dimension render(Graphics2D graphics) { return null; } }

    private static final class Fixture
    {
        final ConfigManager manager = mock(ConfigManager.class);
        final OverlayManager overlays = mock(OverlayManager.class);
        final Map<String, String> global = new LinkedHashMap<>(), saved = new LinkedHashMap<>();
        final String[] profile = {"rsprofileA"};
        final RunewardenConfig config = new RunewardenConfig() { @Override public boolean hudLocked() { return true; } };
        final Client client = mock(Client.class);
        final ItemManager items = mock(ItemManager.class);
        final RunewardenResourceOverlay resources = new RunewardenResourceOverlay(client, config, mock(SpriteManager.class));
        final GuideCoachOverlay guide = new GuideCoachOverlay(client, config);
        final SkillingCoachOverlay skilling = new SkillingCoachOverlay(client, config,
            new SkillingCoach(client, items, manager, config), items);
        final CommandDashboardTool dashboard = new CommandDashboardTool(client, items, overlays, config);
        final HarvestClockTool harvest = new HarvestClockTool(client, manager, mock(PluginManager.class), overlays, config);
        final WaveEntryOverlay entry = new WaveEntryOverlay(client, new WaveEntryCoach(client,
            new GuideCatalog(new com.google.gson.Gson()), new GuideMarkerOverlay(client), config,
            Runnable::run, System::nanoTime), config);
        final InventoryPreviewOverlay inventory = new InventoryPreviewOverlay(client, config, items);
        final EffectsCard effects = new EffectsCard();
        final TrainingCard training = new TrainingCard();
        final Map<String, Overlay> owned = new LinkedHashMap<>();
        final HudSettingsTool tool = new HudSettingsTool(manager, config, overlays, resources, guide, skilling,
            dashboard, harvest, entry, inventory);

        Fixture()
        {
            owned.put("resources", resources); owned.put("dashboard", dashboard.overlay()); owned.put("guide", guide);
            owned.put("harvest", harvest.overlay()); owned.put("outfit", skilling); owned.put("entrance", entry);
            owned.put("inventory", inventory); owned.put("field-effects", effects); owned.put("training", training);
            effects.lock(false); training.lock(false);
            tool.registerWidget("field-effects", effects, effects::lock);
            tool.registerWidget("training", training, training::lock);
            when(manager.getRSProfileKey()).thenAnswer(call -> profile[0]);
            when(manager.getConfiguration(eq("runelite"), anyString())).thenAnswer(call -> global.get(call.getArgument(1)));
            doAnswer(call -> { global.put(call.getArgument(1), call.getArgument(2).toString()); return null; })
                .when(manager).setConfiguration(eq("runelite"), anyString(), any());
            doAnswer(call -> { global.remove(call.getArgument(1)); return null; })
                .when(manager).unsetConfiguration(eq("runelite"), anyString());
            when(manager.getRSProfileConfiguration(RunewardenConfig.GROUP, "hudCharacterPositions"))
                .thenAnswer(call -> saved.get(profile[0]));
            doAnswer(call -> { saved.put(profile[0], call.getArgument(2)); return null; })
                .when(manager).setRSProfileConfiguration(eq(RunewardenConfig.GROUP), eq("hudCharacterPositions"), any());
            doAnswer(call ->
            {
                Overlay overlay = call.getArgument(0);
                overlay.setPreferredLocation(null); overlay.setPreferredPosition(null); overlay.setPreferredSize(null);
                for (String suffix : new String[] {"_preferredLocation", "_preferredPosition", "_origin", "_originX", "_originY"})
                    global.remove(overlay.getName() + suffix);
                return null;
            }).when(overlays).resetOverlay(any(Overlay.class));
            doAnswer(call ->
            {
                Overlay overlay = call.getArgument(0); Point location = overlay.getPreferredLocation();
                if (location != null)
                    global.put(overlay.getName() + "_preferredLocation", location.x + ":" + location.y);
                else global.remove(overlay.getName() + "_preferredLocation");
                return null;
            }).when(overlays).saveOverlay(any(Overlay.class));
            when(overlays.remove(any(Overlay.class))).thenReturn(true);
            when(overlays.add(any(Overlay.class))).thenAnswer(call ->
            {
                Overlay overlay = call.getArgument(0);
                // Pinned RuneLite OverlayManager.loadOverlay drops a stored location for non-movable overlays.
                if (!overlay.isMovable())
                    for (String suffix : new String[] {"_preferredLocation", "_origin", "_originX", "_originY"})
                        global.remove(overlay.getName() + suffix);
                String savedLocation = global.get(overlay.getName() + "_preferredLocation");
                if (savedLocation != null)
                {
                    String[] point = savedLocation.split(":");
                    overlay.setPreferredLocation(new Point(Integer.parseInt(point[0]), Integer.parseInt(point[1])));
                    overlay.getBounds().setLocation(overlay.getPreferredLocation());
                }
                return true;
            });
        }
        void registerCards() { owned.values().forEach(overlays::add); }
        void putLocations(String location)
        {
            for (Overlay overlay : owned.values())
            {
                global.put(overlay.getName() + "_preferredLocation", location);
                global.put(overlay.getName() + "_origin", "MANUAL");
                global.put(overlay.getName() + "_originX", "LEFT");
                global.put(overlay.getName() + "_originY", "TOP");
                String[] point = location.split(":");
                overlay.setPreferredLocation(new Point(Integer.parseInt(point[0]), Integer.parseInt(point[1])));
                overlay.getBounds().setLocation(overlay.getPreferredLocation());
            }
        }
    }
}
