/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
public class FarmPatchPlannerTest
{
    @Test public void pinnedSnapshotHasAll107NativePatchesAndExactHerbSeeds()
    {
        FarmPatchCatalog catalog = new FarmPatchCatalog(new Gson()); assertEquals(107, catalog.patches().size());
        assertTrue(catalog.forGuide("training:herb-runs").size() >= 10); assertTrue(catalog.forGuide("training:allotment-runs").size() >= 15);
        assertTrue(catalog.seeds("HERB").stream().anyMatch(seed -> seed.id == 5295 && seed.name.equals("Ranarr seed")));
        assertTrue(catalog.seeds("TREE").stream().anyMatch(seed -> seed.name.equals("Magic sapling")));
        for (FarmPatchCatalog.Patch patch : catalog.patches()) { assertTrue(patch.varbit > 0); assertNotNull(patch.sentRegions); assertFalse(patch.sentRegions.isEmpty()); }
    }
    @Test public void catherbyAndFossilIslandRespectNativeTransmissionBounds()
    {
        FarmPatchCatalog catalog = new FarmPatchCatalog(new Gson());
        FarmPatchCatalog.Patch herb = catalog.forGuide("training:herb-runs").stream().filter(patch -> patch.region.equals("Catherby")).findFirst().get();
        assertTrue(herb.sentAt(new WorldPoint(2825, 3445, 0))); assertFalse(herb.sentAt(new WorldPoint(2825, 3445, 1)));
        FarmPatchCatalog.Patch hardwood = catalog.forGuide("training:hardwood-runs").stream().filter(patch -> patch.region.equals("Fossil Island")).findFirst().get();
        assertFalse(hardwood.sentAt(new WorldPoint(3753, 3869, 0))); assertFalse(hardwood.sentAt(new WorldPoint(3729, 3831, 0)));
    }
    @Test public void remotePatchesRemainUnknownAndRegionEntryWaitsForSettledNativeState()
    {
        Fixture fixture = new Fixture(); fixture.planner.start(); fixture.tick(2); assertTrue(fixture.planner.readings().isEmpty());
        fixture.tick(4); assertFalse(fixture.planner.readings().isEmpty());
        for (FarmPatchPlanner.PatchReading reading : fixture.planner.readings().values()) assertTrue(reading.patch.sentAt(fixture.location.get()));
        assertTrue(fixture.planner.readings().size() < fixture.catalog.patches().size());
    }
    @Test public void growthDeadlineIsOnlyDueToCheckAndNeverManufacturesHarvestableState()
    {
        FarmPatchCatalog catalog = new FarmPatchCatalog(new Gson()); FarmPatchCatalog.Patch herb = catalog.forGuide("training:herb-runs").get(0);
        FarmPatchCatalog.State state = new FarmPatchCatalog.State(); state.crop = "RANARR"; state.state = "GROWING"; state.stages = 5; state.stage = 0; state.tickMinutes = 20;
        FarmPatchPlanner.PatchReading reading = new FarmPatchPlanner.PatchReading(herb, state, 100_000L, true);
        assertTrue(reading.status(100).contains("1h 20m")); assertEquals("Due to check · estimated", reading.status(5000)); assertEquals("GROWING", reading.state.state);
    }
    @Test public void restartRetainsLastSeenButAccountSwitchHidesItImmediately()
    {
        Fixture fixture = new Fixture(); fixture.planner.start(); fixture.tick(6); assertFalse(fixture.planner.readings().isEmpty());
        fixture.planner.stop(); fixture.location.set(new WorldPoint(3200, 3200, 0)); fixture.planner.start(); fixture.tick(1);
        assertFalse(fixture.planner.readings().isEmpty()); for (FarmPatchPlanner.PatchReading reading : fixture.planner.readings().values()) assertFalse(reading.currentSession);
        fixture.nativeState.profile.set("rsprofile.other"); assertTrue(fixture.planner.readings().isEmpty()); fixture.tick(1); assertTrue(fixture.planner.readings().isEmpty());
    }

    @Test public void queuedOldProfileEditsAndStaleRunViewsAreDiscarded()
    {
        Fixture f=new Fixture();f.planner.start();f.tick(6);FarmPatchPlanner.RunReading view=f.planner.currentRun();
        List<Runnable> queued=new ArrayList<>();doAnswer(call->{queued.add(call.getArgument(0));return null;}).when(f.thread).invokeLater(any(Runnable.class));
        f.planner.withView(view,()->f.planner.setCompost(0));f.nativeState.profile.set("rsprofile.other");f.tick(1);
        queued.forEach(Runnable::run);assertEquals(21483,f.planner.currentRun().compostItemId);
        assertFalse(f.nativeState.settings.containsKey("runewarden|rsprofile.other|"+FarmPatchPlanner.KEY));
    }
    @Test public void corruptSavedRoutesCannotImportRemoteIdsOrResumeRuns()
    {
        Fixture f=new Fixture();FarmPatchPlanner.Saved saved=new FarmPatchPlanner.Saved();FarmPatchPlanner.Route route=new FarmPatchPlanner.Route();
        route.stops.add("not-a-native-patch");route.active=true;saved.routes.put("herb",route);
        f.nativeState.settings.put("runewarden|rsprofile.characterA|"+FarmPatchPlanner.KEY,new Gson().toJson(saved));
        f.planner.start();f.tick(1);assertFalse(f.planner.currentRun().active);
        assertFalse(f.planner.currentRun().stops.stream().anyMatch(s->s.id.equals("not-a-native-patch")));
        assertTrue(f.planner.currentRun().status.contains("could not be read"));
    }
    @Test public void newSeedFactsKeepNativeSaplingsAndUnknownQuantitiesExplicit()
    {
        FarmPatchCatalog c=new FarmPatchCatalog(new Gson());assertEquals("Palm sapling",c.seed(5502).name);
        assertEquals(Integer.valueOf(1),c.seed(5502).quantity);assertEquals(Integer.valueOf(68),c.seed(5502).level);
        assertEquals(6055,c.iconId(c.state("HERB",0)));assertNull(c.seed(31545).quantity);assertEquals(5502,c.iconId(c.state("FRUIT_TREE",200)));
    }
    static final class NativeState
    {
        final Client client=mock(Client.class);final ConfigManager config=mock(ConfigManager.class);
        final AtomicReference<String> profile=new AtomicReference<>("rsprofile.characterA");final Map<String,String> settings=new HashMap<>();
        NativeState()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
            when(config.getRSProfileKey()).thenAnswer(call->profile.get());
            when(config.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(call->settings.get(call.getArgument(0)+"|"+call.getArgument(1)+"|"+call.getArgument(2)));
            doAnswer(call->{settings.put(call.getArgument(0)+"|"+call.getArgument(1)+"|"+call.getArgument(2),call.getArgument(3));return null;})
                .when(config).setConfiguration(anyString(),anyString(),anyString(),anyString());
        }
    }
    static final class Fixture
    {
        final NativeState nativeState=new NativeState();final ClientThread thread=mock(ClientThread.class);
        final AtomicReference<WorldPoint> location=new AtomicReference<>();final FarmPatchCatalog catalog=new FarmPatchCatalog(new Gson());final FarmPatchPlanner planner;
        Fixture()
        {
            FarmPatchCatalog.Patch patch=catalog.forGuide("herb").stream().filter(p->p.region.equals("Ardougne")).findFirst().get();
            location.set(new WorldPoint((patch.regionId>>8)*64+32,(patch.regionId&255)*64+32,0));
            when(nativeState.client.getLocalPlayer().getWorldLocation()).thenAnswer(call->location.get());
            doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
            planner=new FarmPatchPlanner(nativeState.client,thread,nativeState.config,new Gson(),catalog);
        }
        void tick(int count){for(int i=0;i<count;i++)planner.onGameTick();}
    }
}
