/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.LootManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.itemstats.ItemStatChanges;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Builds every actual Runewarden service, with only RuneLite-owned dependencies substituted. */
public class RunewardenPluginWiringTest
{
    @Test public void nativeGuiceModuleConstructsTheCompletePluginGraph() throws Exception
    {
        Fixture f=new Fixture();
        for(java.lang.reflect.Field field:RunewardenPlugin.class.getDeclaredFields())
            if(field.isAnnotationPresent(javax.inject.Inject.class))
            {field.setAccessible(true);assertNotNull(field.getName(),field.get(f.plugin));}
        assertNotNull(f.injector.getInstance(HarvestNavigation.class));
        assertNotNull(f.injector.getInstance(FarmPatchPlanner.class));
        assertNotNull(f.injector.getInstance(ResourceTooltipOverlay.class));
    }

    @Test public void configProfileEventReloadsActionThievingAndEffectPreferences() throws Exception
    {
        Fixture f=new Fixture();ActionProgressTool actions=f.injector.getInstance(ActionProgressTool.class);
        VarlamoreThievingCoach thieving=f.injector.getInstance(VarlamoreThievingCoach.class);
        FieldCoach effects=f.injector.getInstance(FieldCoach.class);
        actions.start();thieving.start();effects.start();
        assertTrue(actions.preferences().enabled);assertTrue(thieving.preferences().hud);assertTrue(effects.enabled());
        ActionProgressTool.Preferences actionPreferences=new ActionProgressTool.Preferences();actionPreferences.enabled=false;
        VarlamoreThievingCoach.Preferences thievingPreferences=new VarlamoreThievingCoach.Preferences();thievingPreferences.hud=false;
        RunewardenDataStore store=f.injector.getInstance(RunewardenDataStore.class);
        assertTrue(store.write("runewardenactionprogress",null,"preferencesV1",1,actionPreferences));
        assertTrue(store.write(VarlamoreThievingCoach.GROUP,null,VarlamoreThievingCoach.KEY,1,thievingPreferences));
        f.raw.put(FieldCoach.GROUP+"::effects","false");
        java.lang.reflect.Field running=RunewardenPlugin.class.getDeclaredField("running");running.setAccessible(true);running.set(f.plugin,true);
        try
        {
            f.plugin.onProfileChanged(new ProfileChanged());
            assertTrue("Profile work must wait for the client thread",actions.preferences().enabled);
            f.drain();SwingUtilities.invokeAndWait(()->{});
            assertFalse(actions.preferences().enabled);assertFalse(thieving.preferences().hud);assertFalse(effects.enabled());
        }
        finally
        {
            running.set(f.plugin,false);actions.stop();thieving.stop();effects.stop();
            f.injector.getInstance(PohTravelCapabilities.class).stop();f.injector.getInstance(HudSession.class).stop();f.drain();
        }
    }

    private static final class Fixture
    {
        final Client client=mock(Client.class);final ClientThread thread=mock(ClientThread.class);
        final ConfigManager configs=mock(ConfigManager.class);final Map<String,String> raw=new HashMap<>();
        final Queue<Runnable> queued=new ArrayDeque<>();final RunewardenPlugin plugin=new RunewardenPlugin();final Injector injector;
        Fixture()
        {
            RunewardenConfig preferences=new RunewardenConfig(){};
            when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
            when(configs.getConfig(RunewardenConfig.class)).thenReturn(preferences);
            when(configs.getRSProfileKey()).thenReturn("rsprofile.graph");ConfigProfile profile=mock(ConfigProfile.class);
            when(profile.getId()).thenReturn(1L);when(configs.getProfile()).thenReturn(profile);
            when(configs.getConfiguration(anyString(),anyString())).thenAnswer(c->raw.get(c.getArgument(0)+"::"+c.getArgument(1)));
            when(configs.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(c->raw.get(c.getArgument(0)+":"+c.getArgument(1)+":"+c.getArgument(2)));
            doAnswer(c->{raw.put(c.getArgument(0)+"::"+c.getArgument(1),String.valueOf((Object)c.getArgument(2)));return null;}).when(configs).setConfiguration(anyString(),anyString(),anyString());
            doAnswer(c->{queued.add(c.getArgument(0));return null;}).when(thread).invokeLater(any(Runnable.class));
            PluginManager plugins=mock(PluginManager.class);when(plugins.getPlugins()).thenReturn(List.of());
            injector=Guice.createInjector(plugin,new AbstractModule()
            {
                @Override protected void configure()
                {
                    bind(Client.class).toInstance(client);bind(ClientThread.class).toInstance(thread);bind(ConfigManager.class).toInstance(configs);
                    bind(Gson.class).toInstance(new Gson());bind(EventBus.class).toInstance(new EventBus());bind(PluginManager.class).toInstance(plugins);
                    bind(ClientToolbar.class).toInstance(mock(ClientToolbar.class));bind(ClientUI.class).toInstance(mock(ClientUI.class));
                    bind(OverlayManager.class).toInstance(mock(OverlayManager.class));bind(Notifier.class).toInstance(mock(Notifier.class));
                    bind(ItemManager.class).toInstance(mock(ItemManager.class));bind(LootManager.class).toInstance(mock(LootManager.class));
                    bind(SpriteManager.class).toInstance(mock(SpriteManager.class));bind(SkillIconManager.class).toInstance(mock(SkillIconManager.class));
                    bind(KeyManager.class).toInstance(mock(KeyManager.class));bind(MouseManager.class).toInstance(mock(MouseManager.class));
                    bind(ItemStatChanges.class).toInstance(mock(ItemStatChanges.class));bind(TooltipManager.class).toInstance(mock(TooltipManager.class));
                }
            });
            injector.injectMembers(plugin);
        }
        void drain(){int count=0;for(Runnable work;(work=queued.poll())!=null;){if(++count>1000)throw new AssertionError("Unbounded client queue");work.run();}}
    }
}
