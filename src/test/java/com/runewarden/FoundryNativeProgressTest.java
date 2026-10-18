/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Runs the native adapter pipeline, including metadata-preserving objective enrichment. */
public class FoundryNativeProgressTest
{
    @Test public void openingAfterAllTwentyEightMetalsWereLoadedSelectsTheMouldWithoutRepeatingAlloyLoading()
    {
        Fixture f=new Fixture();f.commission();f.metals(14,14);MinigameActivityCatalog.Snapshot state=f.read();
        assertEquals("Set the mould",state.steps.get(0).title);assertTrue(state.steps.get(0).instruction.contains("28-metal alloy is loaded"));
        assertEquals("prepare:2",state.guideStepId);assertEquals(Set.of("prepare:1"),state.confirmedStepIds);assertFalse(state.terminal);
        GuideCatalog.Entry guide=new GuideCatalog(new Gson()).entry("training:giants-foundry");
        assertEquals("prepare:2",guide.flattened().get(GuideCompletion.activityStep(guide,state)).step.id);
    }
    @Test public void partialAlloysUseTheRemainingNativeCountAndSetMouldsAreNotRequestedAgain()
    {
        Fixture f=new Fixture();f.commission();f.bits.put(VarbitID.GIANTS_FOUNDRY_MOULD_STATE,1);f.metals(7,7);
        MinigameActivityCatalog.Snapshot state=f.read();assertEquals("Load the remaining alloy",state.steps.get(0).title);assertTrue(state.steps.get(0).instruction.contains("14 more metal units"));
        assertEquals("method:1",state.guideStepId);assertTrue(state.confirmedStepIds.contains("prepare:2"));
        f.metals(14,14);state=f.read();assertEquals("Pour the sword mould",state.steps.get(0).title);assertEquals(Set.of(44776),state.highlightObjectIds);
        assertTrue(state.confirmedStepIds.containsAll(Set.of("prepare:1","prepare:2")));assertFalse(state.confirmedStepIds.contains("method:1"));
    }
    @Test public void aPouredSwordIsCollectedBeforeItsVisibleToolGaugeIsUsed()
    {
        Fixture f=new Fixture();f.commission();f.bits.put(VarbitID.GIANTS_FOUNDRY_MOULD_STATE,2);f.hammerGauge();
        MinigameActivityCatalog.Snapshot state=f.read();assertEquals("Collect the preform",state.steps.get(0).title);assertEquals("method:1",state.guideStepId);
        assertFalse(state.confirmedStepIds.contains("method:1"));
    }
    @Test public void equippedPreformSurvivesTheRealMouldStateResetToZeroAndSelectsTheCurrentHeatAndTool()
    {
        Fixture f=new Fixture();f.commission();f.equipPreform();f.hammerGauge();f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION,100);
        f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_TEMPERATURE,400);MinigameActivityCatalog.Snapshot heat=f.read();
        assertEquals("Heat the preform",heat.steps.get(0).title);assertEquals("method:2",heat.guideStepId);assertEquals(Set.of(44631),heat.highlightObjectIds);
        assertTrue(heat.confirmedStepIds.containsAll(Set.of("prepare:1","prepare:2","method:1")));
        f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_TEMPERATURE,830);assertEquals("Work at the trip hammer",f.read().steps.get(0).title);
        f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_TEMPERATURE,990);assertEquals("Cool the preform",f.read().steps.get(0).title);
        assertEquals(0,(int)f.bits.getOrDefault(VarbitID.GIANTS_FOUNDRY_MOULD_STATE,0));
    }
    @Test public void storedPreformsAreRetrievedAndNeverTreatedAsFreshAlloyPreparation()
    {
        Fixture f=new Fixture();f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_STORED,1);f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION,500);
        MinigameActivityCatalog.Snapshot stored=f.read();assertEquals("Retrieve your stored preform",stored.steps.get(0).title);assertEquals(Set.of(44778),stored.highlightObjectIds);
        assertEquals("method:2",stored.guideStepId);f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION,1000);
        assertEquals("method:3",f.read().guideStepId);assertFalse(f.read().terminal);
    }
    @Test public void finishedGaugeAndSwordRemovalDoNotClaimCompletionWithoutTheNativeReward()
    {
        Fixture f=new Fixture();f.commission();f.equipPreform();f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION,1000);
        MinigameActivityCatalog.Snapshot handin=f.read();assertEquals("Return the sword",handin.steps.get(0).title);assertEquals("method:3",handin.guideStepId);assertFalse(handin.terminal);
        assertTrue(handin.confirmedStepIds.contains("method:2"));assertFalse(handin.confirmedStepIds.contains("method:3"));
        f.removePreform();f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION,0);assertFalse(f.read().terminal);
        assertEquals("Confirm the commission reward",f.read().steps.get(0).title);
        f.points(100);MinigameActivityCatalog.Snapshot reward=f.read();assertTrue(reward.terminal);assertEquals("Commission rewarded",reward.steps.get(0).title);
        assertTrue(reward.confirmedStepIds.containsAll(Set.of("method:3","milestone:1")));assertFalse(reward.confirmedStepIds.contains("milestone:2"));assertFalse(reward.confirmedStepIds.contains("milestone:3"));
    }
    @Test public void rewardBeforeItemRemovalWaitsForTheActualHandInAndNewAlloyClearsTheTerminalLatch()
    {
        Fixture f=new Fixture();f.equipPreform();f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION,1000);f.read();
        f.points(120);assertFalse(f.read().terminal);f.removePreform();f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION,0);assertTrue(f.read().terminal);
        assertTrue(f.read().terminal);f.commission();f.metals(14,14);assertFalse(f.read().terminal);assertEquals("Set the mould",f.read().steps.get(0).title);
    }
    @Test public void profileOrActivityResetDropsPendingRewardEvidence()
    {
        Fixture f=new Fixture();f.equipPreform();f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION,1000);f.read();f.catalog.clearAccount();
        f.removePreform();f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION,0);f.points(150);assertFalse(f.read().terminal);
        f.equipPreform();f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION,1000);f.read();when(f.hud.isHidden()).thenReturn(true);assertNull(f.catalog.detect(f.client).activity);
        f.removePreform();f.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION,0);f.points(300);when(f.hud.isHidden()).thenReturn(false);assertFalse(f.read().terminal);
    }
    static final class Fixture
    {
        final Client client=mock(Client.class);final Player player=mock(Player.class);final Widget hud=mock(Widget.class);
        final Map<Integer,Integer> bits=new HashMap<>(),vars=new HashMap<>();final Map<Integer,Widget> widgets=new HashMap<>();
        final ItemContainer worn=mock(ItemContainer.class),inventory=mock(ItemContainer.class);final MinigameActivityCatalog catalog=new MinigameActivityCatalog();
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);when(player.getWorldLocation()).thenReturn(new WorldPoint(3366,11494,0));
            when(client.getVarbitValue(anyInt())).thenAnswer(call->bits.getOrDefault(call.getArgument(0),0));when(client.getVarpValue(anyInt())).thenAnswer(call->vars.getOrDefault(call.getArgument(0),0));
            when(client.getRealSkillLevel(Skill.SMITHING)).thenReturn(99);when(client.getWidget(anyInt(),anyInt())).thenAnswer(call->widgets.get(((int)call.getArgument(0)<<16)|(int)call.getArgument(1)));
            widgets.put(754<<16|51,hud);when(worn.getItems()).thenReturn(new Item[0]);when(inventory.getItems()).thenReturn(new Item[0]);
            when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
        }
        void commission(){bits.put(VarbitID.GIANTS_FOUNDRY_COMMISSION_WORD_1,4);bits.put(VarbitID.GIANTS_FOUNDRY_COMMISSION_WORD_2,5);}
        void metals(int steel,int mithril){bits.put(VarbitID.GIANTS_FOUNDRY_STEEL_COUNT,steel);bits.put(VarbitID.GIANTS_FOUNDRY_MITHRIL_COUNT,mithril);}
        void equipPreform(){when(worn.getItems()).thenReturn(new Item[]{new Item(ItemID.GIANTS_FOUNDRY_PREFORM,1)});}
        void removePreform(){when(worn.getItems()).thenReturn(new Item[0]);}
        void points(int value){vars.put(VarPlayerID.GIANTS_FOUNDRY_REWARD_SHOP_POINTS,value);}
        void hammerGauge()
        {
            Widget bar=mock(Widget.class),hammer=mock(Widget.class),gauge=mock(Widget.class),medium=mock(Widget.class);when(hammer.getSpriteId()).thenReturn(4442);when(bar.getChildren()).thenReturn(new Widget[]{hammer});
            when(gauge.getWidth()).thenReturn(300);when(medium.getWidth()).thenReturn(60);widgets.put(754<<16|75,bar);widgets.put(754<<16|9,gauge);widgets.put(754<<16|20,medium);
        }
        MinigameActivityCatalog.Snapshot read(){return catalog.detect(client);}
    }
}
