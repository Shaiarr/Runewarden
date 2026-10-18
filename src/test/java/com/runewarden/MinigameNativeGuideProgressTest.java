/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.List;
import java.util.Set;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MinigameNativeGuideProgressTest
{
    @Test public void openingDuringRiftUsesActualCarriedEssenceThenStonesWithoutMarkingManualMilestones()
    {
        Fixture f=new Fixture();f.visible(746,2);f.items(26879);
        MinigameActivityCatalog.Snapshot essence=f.catalog.detect(f.client);
        assertEquals("gotr:craft-stones",essence.objectiveKey);assertEquals("method:2",essence.guideStepId);
        assertEquals("Use an active altar",essence.steps.get(0).title);assertTrue(essence.confirmedStepIds.isEmpty());
        f.items(26880);MinigameActivityCatalog.Snapshot stones=f.catalog.detect(f.client);
        assertEquals("gotr:empower-guardian",stones.objectiveKey);assertFalse(stones.terminal);
        when(f.client.getVarbitValue(VarbitID.GOTR_PLAYER_HAS_FINISHED_GAME)).thenReturn(1);
        assertTrue(f.catalog.detect(f.client).terminal);
    }
    @Test public void nativeCompletedGameCounterNeedsAWitnessedBaselineAndIsNotAnExperienceTarget()
    {
        Fixture f=new Fixture();f.visible(396,2);f.items(20696);
        when(f.client.getVarpValue(VarPlayerID.TOTAL_WINTERTODT_KILLS)).thenReturn(42);
        MinigameActivityCatalog.Snapshot first=f.catalog.detect(f.client);
        assertEquals("wintertodt:feed-brazier",first.objectiveKey);assertFalse(first.terminal);
        when(f.client.getVarpValue(VarPlayerID.TOTAL_WINTERTODT_KILLS)).thenReturn(43);
        MinigameActivityCatalog.Snapshot done=f.catalog.detect(f.client);assertTrue(done.terminal);assertTrue(done.confirmedStepIds.isEmpty());
        f.catalog.clearAccount();assertFalse("An old lifetime kill count is not a newly completed round",f.catalog.detect(f.client).terminal);
    }
    @Test public void blastFurnaceCollectionAndDriftNetCyclesNeverInventAnEndOfTraining()
    {
        Fixture f=new Fixture();f.visible(474,0);f.items(444);
        when(f.client.getVarbitValue(947)).thenReturn(5);
        MinigameActivityCatalog.Snapshot bars=f.catalog.detect(f.client);assertEquals("blast_furnace:collect-bars",bars.objectiveKey);assertFalse(bars.terminal);
        when(f.client.getVarbitValue(947)).thenReturn(0);
        MinigameActivityCatalog.Snapshot ore=f.catalog.detect(f.client);assertEquals("blast_furnace:load-ore",ore.objectiveKey);assertFalse(ore.terminal);
        MinigameGuideProgress adapter=new MinigameGuideProgress();
        MinigameActivityCatalog.Snapshot net=adapter.annotate(snapshot(MinigameActivityCatalog.Activity.DRIFT_NET,"training:drift-net","Empty net 1"));
        assertEquals("drift_net:empty-net",net.objectiveKey);assertFalse(net.terminal);assertTrue(net.confirmedStepIds.isEmpty());
    }
    @Test public void reloadedHomesFurnitureStateWinsOverAMissingObjectSceneAndOnlyARealRewardMessageFinishes()
    {
        Fixture f=new Fixture();when(f.player.getWorldLocation()).thenReturn(new WorldPoint(3235,3384,0));
        f.catalog.onHomeMessage("Expert Contract: Go see Sarah in Varrock.");
        when(f.client.getVarbitValue(10554)).thenReturn(1);
        MinigameActivityCatalog.Snapshot repair=f.catalog.detect(f.client);
        assertEquals("training:homes-expert",repair.guideId);assertEquals("mahogany_homes:repair-furniture",repair.objectiveKey);
        when(f.client.getVarbitValue(10554)).thenReturn(3);assertEquals("mahogany_homes:remove-furniture",f.catalog.detect(f.client).objectiveKey);
        when(f.client.getVarbitValue(10554)).thenReturn(8);assertEquals("mahogany_homes:speak-to-customer",f.catalog.detect(f.client).objectiveKey);
        assertFalse(f.catalog.detect(f.client).terminal);
        f.catalog.onHomeMessage("You have completed 12 contracts with a total of 30 points.");assertTrue(f.catalog.detect(f.client).terminal);
    }
    @Test public void resultPanelsAreCurrentNativeCompletionEvidenceButUnknownTimersAndTextAreNot()
    {
        Fixture f=new Fixture();f.visible(435,3);MinigameActivityCatalog.Snapshot result=f.catalog.detect(f.client);
        assertEquals(MinigameActivityCatalog.Activity.SOUL_WARS,result.activity);assertTrue(result.terminal);
        MinigameActivityCatalog.Snapshot unknown=snapshot(MinigameActivityCatalog.Activity.FISHING_TRAWLER,"minigame:fishing-trawler","Round complete in a guide sentence");
        MinigameActivityCatalog.Snapshot mapped=new MinigameGuideProgress().annotate(unknown);
        assertFalse(mapped.terminal);assertEquals("",mapped.objectiveKey);
    }
    @Test public void explicitProgressSurvivesAllPublicSnapshotPresentationTransforms()
    {
        Fixture f=new Fixture();MinigameActivityCatalog.Snapshot original=snapshot(MinigameActivityCatalog.Activity.GIANTS_FOUNDRY,"training:giants-foundry","Return the sword")
            .withGuideProgress("method:3","giants_foundry:hand-in",true,Set.of("prepare:1"));
        MinigameActivityCatalog.Snapshot objectives=MinigameNativeObjectives.enrich(original,f.client,Set.of(),MinigameNativeObjectives.Options.DEFAULT);
        MinigameFieldEvents fields=new MinigameFieldEvents(f.client,mock(net.runelite.client.config.ConfigManager.class),new RunewardenDataStore(mock(net.runelite.client.config.ConfigManager.class),new Gson()));
        MinigameActivityCatalog.Snapshot released=MinigameReleasePolicy.releaseSnapshot(fields.enrich(objectives));
        assertEquals(original.guideStepId,released.guideStepId);assertEquals(original.objectiveKey,released.objectiveKey);assertTrue(released.terminal);assertEquals(original.confirmedStepIds,released.confirmedStepIds);
    }
    @Test public void typedObjectivesBindExistingAuthoredStepsAndMageTrainingAliasUsesItsOwnLayout()
    {
        MinigameGuideProgress adapter=new MinigameGuideProgress();GuideCatalog guides=new GuideCatalog(new Gson());
        String[][] values={{"WINTERTODT","training:wintertodt","Chop bruma roots"},{"GOTR","training:guardians-rift","Craft guardian essence"},
            {"TEMPOROSS","training:tempoross","Fish the spirit pool"},{"MIXOLOGY","training:mastering-mixology","Deliver the finished order"},
            {"MAHOGANY_HOMES","training:homes-expert","Build the replacement"},{"PEST_CONTROL","training:pest-control-experience","Defend the Void Knight"},
            {"BLAST_FURNACE","training:blast-gold","Collect your bars"},{"TITHE_FARM","training:tithe-farm","Water dry plants"},
            {"BARBARIAN_ASSAULT","minigame:barbarian-assault","Send the call and wait for yours"},{"NIGHTMARE_ZONE","training:nightmare-zone-absorptions","Maintain your dream supplies"},
            {"PYRAMID_PLUNDER","training:pyramid-plunder","Pass the spear trap"},{"MAGE_TRAINING_ARENA","minigame:mage-training-arena","Enchant and deposit shapes"},
            {"VOLCANIC_MINE","training:volcanic-mine","Return to the exit"},{"BLAST_MINE","training:blast-mine","Load dynamite"},
            {"FISHING_TRAWLER","minigame:fishing-trawler","Bail rising water"},{"DRIFT_NET","training:drift-net","Herd fish shoals"},
            {"TROUBLE_BREWING","minigame:trouble-brewing","Cut logs into bark"},{"SOUL_WARS","minigame:soul-wars","Take fragments to the obelisk"},
            {"CASTLE_WARS","minigame:castle-wars","Return the enemy standard"}};
        for(String[] value:values)
        {
            MinigameActivityCatalog.Snapshot mapped=adapter.annotate(snapshot(MinigameActivityCatalog.Activity.valueOf(value[0]),value[1],value[2]));
            assertFalse(value[0],mapped.objectiveKey.isEmpty());assertNotNull(mapped.guideStepId);
            assertTrue(value[0],GuideCompletion.activityStep(guides.entry(value[1]),mapped)>=0);
        }
        MinigameActivityCatalog.Snapshot mage=adapter.annotate(snapshot(MinigameActivityCatalog.Activity.MAGE_TRAINING_ARENA,"minigame:mage-training-arena","Convert bones and manage health"));
        GuideCatalog.Entry alias=guides.entry("training:mage-training-arena");assertTrue(GuideCompletion.matchesActivity(alias,mage));
        assertEquals("method:1",alias.flattened().get(GuideCompletion.activityStep(alias,mage)).step.id);
    }
    static MinigameActivityCatalog.Snapshot snapshot(MinigameActivityCatalog.Activity activity,String guide,String title)
    {return new MinigameActivityCatalog.Snapshot(activity,activity.toString(),guide,List.of(),List.of(new MinigameActivityCatalog.Step(title,"Current native instruction")),Set.of(),List.of(),true);}
    private static final class Fixture
    {
        final Client client=mock(Client.class);final Player player=mock(Player.class);final MinigameActivityCatalog catalog=new MinigameActivityCatalog();
        Fixture(){when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);when(player.getWorldLocation()).thenReturn(new WorldPoint(3200,3200,0));}
        void visible(int group,int child){net.runelite.api.widgets.Widget widget=mock(net.runelite.api.widgets.Widget.class);when(client.getWidget(group,child)).thenReturn(widget);when(client.getWidget((group<<16)|child)).thenReturn(widget);}
        void items(int id){ItemContainer container=mock(ItemContainer.class);when(container.getItems()).thenReturn(new Item[]{new Item(id,5)});when(client.getItemContainer(93)).thenReturn(container);}
    }
}
