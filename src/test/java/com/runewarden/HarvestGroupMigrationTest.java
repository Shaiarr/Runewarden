/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Existing timer choices survive splitting native tabs into independently selected crops. */
public class HarvestGroupMigrationTest
{
    @Test public void oldTreeFruitAndSpecialSelectionsExpandIntoTheirActualChildGroupsOnce()
    {
        Fixture f=new Fixture();f.put("account-a",HarvestSelections.LEGACY_KEY,"[\"tree\",\"fruit-tree\",\"special\"]");
        f.choices.bind("account-a");
        Set<String> expected=Set.of("tree","hardwood","spirit-tree","redwood", "fruit-tree","calquat","celastrus","crystal-tree",
            "hespori","seaweed","cactus","mushroom","belladonna","anima","compost","big-compost","coral");
        assertEquals(expected,f.selected());assertFalse(f.selected().contains("special"));
        assertEquals(1,f.writes.size());assertNotNull(f.get("account-a",HarvestSelections.KEY));
        assertEquals("Legacy data remains available to older versions","[\"tree\",\"fruit-tree\",\"special\"]",f.get("account-a",HarvestSelections.LEGACY_KEY));
        f.choices.clear();f.choices.bind("account-a");
        assertEquals(expected,f.selected());assertEquals("V2 must not be rewritten on every login",1,f.writes.size());
    }

    @Test public void editingOneMigratedChildDoesNotReexpandItOnTheNextLogin()
    {
        Fixture f=new Fixture();f.put("account-a",HarvestSelections.LEGACY_KEY,"[\"fruit-tree\"]");
        HarvestSelections.Session session=f.choices.bind("account-a");
        assertEquals(Set.of("fruit-tree","calquat","celastrus","crystal-tree"),f.selected());
        assertTrue(f.choices.toggle(session,"calquat",false,List.of()));
        assertEquals(Set.of("fruit-tree","celastrus","crystal-tree"),f.selected());
        String saved=f.get("account-a",HarvestSelections.KEY);int writes=f.writes.size();
        f.choices.clear();f.choices.bind("account-a");
        assertEquals(Set.of("fruit-tree","celastrus","crystal-tree"),f.selected());
        assertEquals(saved,f.get("account-a",HarvestSelections.KEY));assertEquals(writes,f.writes.size());
    }

    @Test public void explicitEmptyV2AndLegacyEmptyDoNotReenableObservedTimers()
    {
        List<HarvestClockTool.Harvest> observed=List.of(harvest("hespori"),harvest("tree"),harvest("bird-house"));
        Fixture current=new Fixture();current.put("account-a",HarvestSelections.LEGACY_KEY,"[\"tree\",\"special\"]");
        current.put("account-a",HarvestSelections.KEY,"[]");current.choices.bind("account-a");
        assertTrue(current.choices.selected(observed).isEmpty());assertTrue(current.writes.isEmpty());
        Fixture old=new Fixture();old.put("account-a",HarvestSelections.LEGACY_KEY,"[]");old.choices.bind("account-a");
        assertTrue(old.choices.selected(observed).isEmpty());assertEquals("[]",old.get("account-a",HarvestSelections.KEY));
        assertEquals(1,old.writes.size());
    }

    @Test public void herbAndBirdhouseChoicesSurviveWhileUnknownLegacyIdsAreDiscarded()
    {
        Fixture f=new Fixture();f.put("account-a",HarvestSelections.LEGACY_KEY,
            "[\"herb\",\"bird-house\",\"bogus\",\"calquat\",null,42,{\"tree\":true}]");
        f.choices.bind("account-a");
        assertEquals(Set.of("herb","bird-house"),f.selected());
        assertFalse(f.get("account-a",HarvestSelections.KEY).contains("bogus"));
        assertFalse("A new group could not have been selected in V1",f.selected().contains("calquat"));
    }

    @Test public void delayedOldAccountCallbacksCannotOverwriteEitherMigratedSelection()
    {
        Fixture f=new Fixture();f.put("account-a",HarvestSelections.LEGACY_KEY,"[\"tree\"]");
        f.put("account-b",HarvestSelections.LEGACY_KEY,"[\"herb\",\"bird-house\"]");
        HarvestSelections.Session old=f.choices.bind("account-a");String first=f.get("account-a",HarvestSelections.KEY);
        f.profile.set("account-b");
        assertFalse(f.choices.toggle(old,"redwood",false,List.of()));
        f.choices.bind("account-b");String second=f.get("account-b",HarvestSelections.KEY);int writes=f.writes.size();
        assertFalse(f.choices.toggle(old,"hespori",true,List.of()));
        assertEquals(Set.of("herb","bird-house"),f.selected());
        assertEquals(first,f.get("account-a",HarvestSelections.KEY));assertEquals(second,f.get("account-b",HarvestSelections.KEY));
        assertEquals(writes,f.writes.size());
        f.profile.set("account-a");f.choices.bind("account-a");
        assertEquals(Set.of("tree","hardwood","spirit-tree","redwood"),f.selected());
        assertFalse("Rebinding an account does not revive its stale callback",f.choices.toggle(old,"tree",false,List.of()));
    }

    @Test public void splitChildrenRemainIndependentAndV2IsNeverTreatedAsAnAggregate()
    {
        Fixture f=new Fixture();f.put("account-a",HarvestSelections.LEGACY_KEY,"[\"tree\",\"fruit-tree\",\"special\"]");
        f.put("account-a",HarvestSelections.KEY,"[\"calquat\",\"hespori\",\"redwood\"]");
        HarvestSelections.Session session=f.choices.bind("account-a");
        assertEquals(Set.of("calquat","hespori","redwood"),f.selected());assertTrue(f.writes.isEmpty());
        assertTrue(f.choices.toggle(session,"calquat",false,List.of()));
        assertEquals(Set.of("hespori","redwood"),f.selected());
        assertTrue(f.choices.toggle(session,"tree",true,List.of()));
        assertEquals("Choosing ordinary trees must not choose hardwood or spirit trees",Set.of("tree","hespori","redwood"),f.selected());
        assertTrue(f.choices.toggle(session,"fruit-tree",true,List.of()));
        assertEquals(Set.of("tree","fruit-tree","hespori","redwood"),f.selected());
    }

    @Test public void firstTimeAccountsStillFollowObservedDefaultsUntilTheyChooseTimers()
    {
        Fixture f=new Fixture();f.choices.bind("account-a");
        assertEquals(Set.of("hespori","bird-house"),f.choices.selected(List.of(harvest("hespori"),harvest("bird-house"))));
        assertEquals(Set.of("calquat"),f.choices.selected(List.of(harvest("calquat"))));
        assertTrue("No explicit choice means no V2 migration write",f.writes.isEmpty());
    }

    private static HarvestClockTool.Harvest harvest(String id)
    {return new HarvestClockTool.Harvest(id,id,SummaryState.COMPLETED,0,true);}

    private static final class Fixture
    {
        final ConfigManager config=mock(ConfigManager.class);
        final AtomicReference<String> profile=new AtomicReference<>("account-a");
        final Map<String,String> values=new HashMap<>();final List<String> writes=new ArrayList<>();
        final HarvestSelections choices;
        Fixture()
        {
            when(config.getRSProfileKey()).thenAnswer(call->profile.get());
            when(config.getConfiguration(eq(RunewardenConfig.GROUP),anyString(),anyString()))
                .thenAnswer(call->get(call.getArgument(1),call.getArgument(2)));
            doAnswer(call->{String owner=call.getArgument(1),key=call.getArgument(2),value=call.getArgument(3);
                put(owner,key,value);writes.add(owner+":"+key);return null;})
                .when(config).setConfiguration(eq(RunewardenConfig.GROUP),anyString(),anyString(),anyString());
            choices=new HarvestSelections(config);
        }
        void put(String owner,String key,String value){values.put(owner+":"+key,value);}
        String get(String owner,String key){return values.get(owner+":"+key);}
        Set<String> selected(){return choices.selected(List.of());}
    }
}
