/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Animation;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GroundLootBeamsTest
{
    private static List<RuneLiteObject> nativeAssets(GroundLootToolTest.Fixture f)
    {
        ModelData data=mock(ModelData.class);Model model=mock(Model.class);Animation animation=mock(Animation.class);
        when(f.client.loadModelData(43330)).thenReturn(data);when(data.cloneColors()).thenReturn(data);when(data.recolor(anyShort(),anyShort())).thenReturn(data);
        when(data.light(anyInt(),anyInt(),anyInt(),anyInt(),anyInt())).thenReturn(model);
        when(f.client.loadModel(eq(5809),any(short[].class),any(short[].class))).thenReturn(model);when(f.client.loadAnimation(anyInt())).thenReturn(animation);
        List<RuneLiteObject> created=new ArrayList<>();when(f.client.createRuneLiteObject()).thenAnswer(call->{RuneLiteObject object=mock(RuneLiteObject.class);created.add(object);return object;});return created;
    }
    @Test public void exactGpBoundariesUseWhiteGreenBluePurpleAndGoldWithoutPretendingTheyAreRarity()
    {
        GroundLootTool.Preferences p=new GroundLootTool.Preferences();
        assertNull(GroundLootTier.value(24999,p));assertEquals(GroundLootTier.WHITE,GroundLootTier.value(25000,p));assertEquals(GroundLootTier.GREEN,GroundLootTier.value(100000,p));assertEquals(GroundLootTier.BLUE,GroundLootTier.value(1000000,p));assertEquals(GroundLootTier.PURPLE,GroundLootTier.value(10000000,p));assertEquals(GroundLootTier.GOLD,GroundLootTier.value(100000000,p));assertEquals(GroundLootTier.GOLD,GroundLootTier.value(Long.MAX_VALUE,p));
    }
    @Test public void nativeBeamPersistsAcrossTicksAndQuantityTierChangesReuseItsSceneObject() throws Exception
    {
        GroundLootToolTest.Fixture f=new GroundLootToolTest.Fixture(null);List<RuneLiteObject> objects=nativeAssets(f);when(f.view.getId()).thenReturn(7);
        TileItem item=f.item(440,3,TileItem.OWNERSHIP_SELF,300,150);f.ground.add(item);f.tool.start();f.tool.onGameTick();f.tool.onGameTick();
        assertEquals(1,objects.size());assertEquals(1,f.tool.activeBeams());verify(objects.get(0)).setLocation(f.tile.getLocalLocation(),0);verify(objects.get(0)).setWorldView(7);verify(objects.get(0)).setShouldLoop(true);
        f.tool.onItemQuantityChanged(new ItemQuantityChanged(item,f.tile,3,20));f.tool.onGameTick();
        assertEquals(1,objects.size());verify(objects.get(0),times(2)).setModel(any(Model.class));
        f.tool.onItemDespawned(new ItemDespawned(f.tile,item));f.tool.onGameTick();assertEquals(0,f.tool.activeBeams());verify(objects.get(0)).setActive(false);f.stop();
    }
    @Test public void nativeBeamsHaveTheirOwnFloorIndependentOfWhichLabelsAreShown() throws Exception
    {
        GroundLootToolTest.Fixture f=new GroundLootToolTest.Fixture("{\"valueFloor\":1000000}");List<RuneLiteObject> objects=nativeAssets(f);f.ground.add(f.item(440,3,1,200,150));f.tool.start();f.tool.onGameTick();
        assertEquals(0,f.tool.reading().total);assertEquals(1,f.tool.reading().activeBeams);assertEquals(1,objects.size());f.stop();
    }
    @Test public void guideAndManualTargetsCanBeamWithoutAValueButTheirTogglesAreRespected() throws Exception
    {
        GroundLootToolTest.Fixture f=new GroundLootToolTest.Fixture("{\"minimumBeamTier\":\"GOLD\",\"targets\":{\"946\":\"Knife\"}}");List<RuneLiteObject> objects=nativeAssets(f);when(f.items.getItemPrice(anyInt())).thenReturn(0L);f.ground.add(f.item(946,1,1,200,150));f.tool.start();f.tool.onGameTick();assertEquals(1,objects.size());
        f.saved.set("{\"minimumBeamTier\":\"GOLD\",\"manualBeams\":false,\"targets\":{\"946\":\"Knife\"}}");f.tool.reloadPreferences();f.tool.onGameTick();assertEquals(0,f.tool.activeBeams());assertTrue(f.tool.reading().entries.get(0).manualTarget);f.stop();
    }
    @Test public void aTileUsesItsStrongestTierAndBeamsHaveAnIndependentHardBound() throws Exception
    {
        GroundLootToolTest.Fixture f=new GroundLootToolTest.Fixture("{\"beamLimit\":3}");List<RuneLiteObject> objects=nativeAssets(f);f.ground.add(f.item(440,3,1,200,0));f.tool.start();f.tool.onGameTick();
        for(int i=0;i<8;i++)
        {
            Tile tile=mock(Tile.class);LocalPoint local=new LocalPoint(192+i*128,64,f.view);when(tile.getWorldLocation()).thenReturn(new WorldPoint(3201+i,3200,0));when(tile.getLocalLocation()).thenReturn(local);when(tile.getItemLayer()).thenReturn(f.layer);
            f.tool.onItemSpawned(new ItemSpawned(tile,f.item(1000+i,100+i,1,300,0)));
        }
        f.tool.onGameTick();assertEquals(3,f.tool.reading().beamCandidates);assertEquals(3,f.tool.activeBeams());assertEquals(4,objects.size()); // Original weaker spot retired; three stronger spots created.
        f.tool.clearScene();assertEquals(0,f.tool.activeBeams());for(RuneLiteObject object:objects)verify(object).setActive(false);f.stop();
    }
    @Test public void changingStyleUsesThePinnedLightModelAndNeverCreatesANewHttpOrPluginService() throws Exception
    {
        GroundLootToolTest.Fixture f=new GroundLootToolTest.Fixture("{\"beamStyle\":\"LIGHT\"}");List<RuneLiteObject> objects=nativeAssets(f);f.ground.add(f.item(440,3,1,200,0));f.tool.start();f.tool.onGameTick();
        assertEquals(1,objects.size());verify(f.client).loadModel(eq(5809),any(short[].class),any(short[].class));verify(f.client,never()).loadModelData(anyInt());f.stop();
    }
    @Test public void missingAssetsRetryWithABoundedTickDelayAndCreateNoEmptySceneObject() throws Exception
    {
        GroundLootToolTest.Fixture f=new GroundLootToolTest.Fixture(null);f.ground.add(f.item(440,3,1,200,0));f.tool.start();for(int i=100;i<105;i++){f.tick.set(i);f.tool.onGameTick();}
        verify(f.client,times(1)).loadModelData(43330);verify(f.client,never()).createRuneLiteObject();f.tick.set(105);f.tool.onGameTick();verify(f.client,times(2)).loadModelData(43330);f.stop();
    }
    @Test public void disablingProfileChangeAndStopRemoveOnlyOwnedNativeObjects() throws Exception
    {
        GroundLootToolTest.Fixture f=new GroundLootToolTest.Fixture(null);List<RuneLiteObject> objects=nativeAssets(f);f.ground.add(f.item(440,3,1,200,0));f.tool.start();f.tool.onGameTick();
        f.saved.set("{\"beams\":false}");f.tool.reloadPreferences();f.tool.onGameTick();assertEquals(0,f.tool.activeBeams());verify(objects.get(0)).setActive(false);
        f.saved.set("{}");f.tool.reloadPreferences();f.tool.onGameTick();assertEquals(1,f.tool.activeBeams());f.profile.set("character-b");assertTrue(f.tool.reading().entries.isEmpty());f.ground.clear();f.tool.onGameTick();assertEquals(0,f.tool.activeBeams());verify(objects.get(1)).setActive(false);f.stop();
    }
    @Test public void deferredOldObjectCleanupCannotDeactivateTheNextSessionObjects() throws Exception
    {
        GroundLootToolTest.Fixture f=new GroundLootToolTest.Fixture(null);List<RuneLiteObject> objects=nativeAssets(f);ClientThread thread=mock(ClientThread.class);List<Runnable> cleanup=new ArrayList<>();doAnswer(call->{cleanup.add(call.getArgument(0));return null;}).when(thread).invoke(any(Runnable.class));
        GroundLootBeams beams=new GroundLootBeams(f.client,thread);f.ground.add(f.item(440,3,1,200,0));f.tool.start();f.tool.onGameTick();List<GroundLootBeams.Candidate> wanted=GroundLootBeams.select(f.tool.reading().entries,f.tool.preferences());
        beams.sync(wanted,f.tool.preferences(),100);RuneLiteObject first=objects.get(1);beams.clear();assertEquals(1,cleanup.size());beams.sync(wanted,f.tool.preferences(),101);RuneLiteObject second=objects.get(2);cleanup.get(0).run();verify(first).setActive(false);verify(second,never()).setActive(false);beams.clear();cleanup.get(1).run();f.stop();
    }
    @Test public void radialDespawnAndPublicTransitionsUseActualNativeDeadlines() throws Exception
    {
        GroundLootToolTest.Fixture f=new GroundLootToolTest.Fixture(null);TileItem item=f.item(440,3,1,200,150);f.ground.add(item);f.tool.start();f.tool.onGameTick();GroundLootTool.Entry entry=f.tool.reading().entries.get(0);
        List<GroundLootOverlay.Deadline> deadlines=GroundLootOverlay.deadlines(entry,125,Color.WHITE);assertEquals(2,deadlines.size());assertEquals(.75,deadlines.get(0).fraction,0.00001);assertEquals(.5,deadlines.get(1).fraction,0.00001);assertEquals("45s",deadlines.get(0).label);assertEquals("15s",deadlines.get(1).label);
        BufferedImage image=new BufferedImage(120,80,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();try{java.awt.Font font=graphics.getFont();assertEquals(2,GroundLootOverlay.paintTimers(graphics,entry,125,80,30,Color.WHITE));assertEquals(font,graphics.getFont());}finally{graphics.dispose();}assertNotEquals(0,image.getRGB(80,19));
        when(item.isPrivate()).thenReturn(true);f.tool.onGameTick();assertEquals(1,GroundLootOverlay.deadlines(f.tool.reading().entries.get(0),125,Color.WHITE).size());f.stop();
    }
    @Test public void sameTileKeepsItsHighestValueTierAndGuideTargetsHaveExplicitIndependentPriority() throws Exception
    {
        GroundLootToolTest.Fixture f=new GroundLootToolTest.Fixture("{\"valueFloor\":0}");f.ground.add(f.item(440,3,1,300,0));f.ground.add(f.item(946,100,1,300,0));f.tool.start();f.tool.onGameTick();
        List<GroundLootBeams.Candidate> candidates=GroundLootBeams.select(f.tool.reading().entries,f.tool.preferences());assertEquals(1,candidates.size());assertEquals(GroundLootTier.BLUE,candidates.get(0).tier);
        f.tool.setTargetItems(()->Collections.singleton(440));f.tool.onGameTick();candidates=GroundLootBeams.select(f.tool.reading().entries,f.tool.preferences());assertEquals(GroundLootTier.GOLD,candidates.get(0).tier);f.stop();
    }
    @Test public void sidebarThresholdEditsRejectInvalidBandsAndOnlyWriteOwnedPreferences() throws Exception
    {
        GroundLootToolTest.Fixture f=new GroundLootToolTest.Fixture(null);f.tool.start();
        javax.swing.SwingUtilities.invokeAndWait(()->{
            javax.swing.JPanel panel=f.tool.createPanel();javax.swing.JSpinner white=(javax.swing.JSpinner)find(panel,"groundLootWhiteFloor");assertNotNull(white);
            white.setValue(1000000L);assertEquals(25000,f.tool.preferences().whiteFloor);white.setValue(50000L);assertEquals(50000,f.tool.preferences().whiteFloor);
            javax.swing.JCheckBox beam=(javax.swing.JCheckBox)find(panel,"groundLootBeams");assertTrue(beam.isSelected());beam.doClick();assertFalse(f.tool.preferences().beams);
            javax.swing.JComboBox<?> tier=(javax.swing.JComboBox<?>)find(panel,"groundLootTargetBeamTier");tier.setSelectedItem(GroundLootTier.PURPLE);assertEquals(GroundLootTier.PURPLE,f.tool.preferences().targetBeamTier);
        });
        verify(f.config,atLeastOnce()).setConfiguration(eq(GroundLootTool.GROUP),eq(GroundLootTool.KEY),anyString());verify(f.config,never()).setConfiguration(eq("grounditems"),anyString(),anyString());f.stop();
    }
    @Test public void malformedBeamPreferencesAreRecoveredWithoutSilentlyRewritingTheirSavedJson() throws Exception
    {
        GroundLootToolTest.Fixture f=new GroundLootToolTest.Fixture("{\"beamLimit\":999,\"whiteFloor\":1000000,\"greenFloor\":1}");assertEquals(32,f.tool.preferences().beamLimit);assertEquals(25000,f.tool.preferences().whiteFloor);assertTrue(f.saved.get().contains("999"));verify(f.config,never()).setConfiguration(eq(GroundLootTool.GROUP),eq(GroundLootTool.KEY),anyString());f.stop();
    }
    private static java.awt.Component find(java.awt.Container root,String name)
    {for(java.awt.Component child:root.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof java.awt.Container){java.awt.Component found=find((java.awt.Container)child,name);if(found!=null)return found;}}return null;}
    @Test public void unknownExpiredAndPermanentlyPrivateDeadlinesNeverGetGuessedRadials() throws Exception
    {
        GroundLootToolTest.Fixture f=new GroundLootToolTest.Fixture(null);TileItem item=f.item(440,3,1,0,0);f.ground.add(item);f.tool.start();f.tool.onGameTick();assertTrue(GroundLootOverlay.deadlines(f.tool.reading().entries.get(0),100,Color.WHITE).isEmpty());
        when(item.getVisibleTime()).thenReturn(120);when(item.isPrivate()).thenReturn(true);f.tool.onGameTick();assertTrue(GroundLootOverlay.deadlines(f.tool.reading().entries.get(0),100,Color.WHITE).isEmpty());f.stop();
    }
}
