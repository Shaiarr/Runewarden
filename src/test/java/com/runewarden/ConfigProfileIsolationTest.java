/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.NPCComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.LootManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ConfigProfileIsolationTest
{
    @Test public void nativeConfigSwitchRejectsOldWritesBeforeItsEventAndDiscardDoesNotCheckpoint() throws Exception
    {
        Harness h=new Harness(1);try
        {
            h.loot(10);RunLootTool.Reading old=h.loot.reading();h.loot.setManualCost(old.profile,old.epoch,5);
            String lootSave=h.f.settings.get(h.key("runLootCheckpointV1"));String sessionSave=h.f.settings.get(ActivitySessionTest.key("runewardenactivity",old.profile,"checkpointV1"));
            h.loot(7);h.configId.set(2);assertFalse(h.loot.reading().active);assertFalse(h.f.session.snapshot().current());assertNull(h.f.session.currentRecord());
            h.loot.setManualCost(old.profile,old.epoch,999);h.loot.newRun(old.profile,old.epoch);h.loot.finishRun(old.profile,old.epoch);assertFalse(h.f.session.begin());h.f.session.pause();
            h.f.store.reload();h.loot.onConfigProfileChanged();h.f.session.onConfigProfileChanged();
            assertEquals(lootSave,h.f.settings.get(h.key("runLootCheckpointV1")));assertEquals(sessionSave,h.f.settings.get(ActivitySessionTest.key("runewardenactivity",old.profile,"checkpointV1")));
            h.f.session.onGameTick();h.loot.onGameTick();assertEquals(1000,h.loot.reading().value);assertEquals(5,h.loot.reading().manualCost);assertTrue(h.loot.reading().paused);
            h.loot.setManualCost(old.profile,old.epoch,999);assertEquals(5,h.loot.reading().manualCost);
        }finally{h.stop();}
    }
    @Test public void sameCharacterLoadsTheActiveConfigurationsPersistedRunInsteadOfRetainedTotals() throws Exception
    {
        Harness old=new Harness(1),active=new Harness(2);try
        {
            old.loot(10);RunLootTool.Reading first=old.loot.reading();old.loot.setManualCost(first.profile,first.epoch,1);
            active.loot(3);RunLootTool.Reading saved=active.loot.reading();active.loot.setManualCost(saved.profile,saved.epoch,7);active.f.session.pause();
            Map<String,String> selectedSaves=new HashMap<>(active.f.settings);old.loot(9);old.configId.set(2);
            // ConfigManager owns the active persistence store. Simulate replacement before ProfileChanged.
            old.f.settings.clear();old.f.settings.putAll(selectedSaves);old.f.store.reload();old.f.session.onConfigProfileChanged();old.loot.onConfigProfileChanged();
            old.f.session.onGameTick();old.loot.onGameTick();
            assertEquals(300,old.loot.reading().value);assertEquals(3,old.loot.reading().items.get(0).quantity);assertEquals(7,old.loot.reading().manualCost);assertTrue(old.loot.reading().paused);
            assertEquals(active.f.session.snapshot().runId,old.f.session.snapshot().runId);
            old.loot.setManualCost(first.profile,first.epoch,10000);assertEquals(7,old.loot.reading().manualCost);
        }finally{old.stop();active.stop();}
    }
    @Test public void queuedRoutineAddCannotCrossConfigProfilesForTheSameCharacter() throws Exception
    {
        Harness h=new Harness(1);RoutinePlannerTool routines=new RoutinePlannerTool(h.f.config,h.f.store);try
        {
            String address=ActivitySessionTest.key(RoutinePlannerTool.GROUP,h.f.profile.get(),RoutinePlannerTool.KEY);
            h.f.settings.put(address,plan("Old routine"));routines.start();routines.onGameTick();AtomicReference<JButton> staleAdd=new AtomicReference<>();
            SwingUtilities.invokeAndWait(()->{
                JPanel panel=routines.createPanel();((JComboBox<?>)components(panel).stream().filter(c->c instanceof JComboBox).findFirst().get()).setSelectedIndex(2);
                ((JTextField)components(panel).stream().filter(c->c instanceof JTextField).findFirst().get()).setText("Wrong profile routine");
                staleAdd.set((JButton)components(panel).stream().filter(c->c instanceof JButton&&"Add routine".equals(((JButton)c).getText())).findFirst().get());
            });
            h.configId.set(2);String replacement=plan("New routine");h.f.settings.put(address,replacement);
            SwingUtilities.invokeAndWait(()->staleAdd.get().doClick());assertEquals(replacement,h.f.settings.get(address));
            h.f.store.reload();routines.onConfigProfileChanged();routines.onGameTick();
            SwingUtilities.invokeAndWait(()->staleAdd.get().doClick());assertEquals(replacement,h.f.settings.get(address));
        }finally{routines.stop();h.stop();}
    }
    private static String plan(String name){return "{\"version\":1,\"routines\":[{\"id\":\"custom\",\"label\":\""+name+"\",\"cadence\":\"DAILY\",\"completed\":0}],\"hiddenClaims\":[]}";}
    private static List<Component> components(Container parent){List<Component> result=new ArrayList<>();for(Component c:parent.getComponents()){result.add(c);if(c instanceof Container)result.addAll(components((Container)c));}return result;}
    private static final class Harness
    {
        final ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();final AtomicLong configId;final RunLootTool loot;
        Harness(long id)
        {
            configId=new AtomicLong(id);ConfigProfile profile=mock(ConfigProfile.class);when(profile.getId()).thenAnswer(call->configId.get());when(f.config.getProfile()).thenReturn(profile);
            f.session.onConfigProfileChanged();f.session.onGameTick();ItemManager items=mock(ItemManager.class);when(items.canonicalize(anyInt())).thenAnswer(call->call.getArgument(0));when(items.getItemPrice(anyInt())).thenReturn(100L);
            loot=new RunLootTool(f.client,mock(ClientThread.class),f.config,items,mock(LootManager.class),new Gson(),f.store,f.session,f.now::get);loot.start();loot.onGameTick();
        }
        String key(String key){return ActivitySessionTest.key(RunewardenConfig.GROUP,f.profile.get(),key);}
        void loot(int quantity){NPCComposition npc=mock(NPCComposition.class);when(npc.getName()).thenReturn("Goblin");loot.onServerNpcLoot(new ServerNpcLoot(npc,Collections.singletonList(new ItemStack(440,quantity))));}
        void stop() throws Exception{loot.stop();f.session.stop();SwingUtilities.invokeAndWait(()->{});}
    }
}
