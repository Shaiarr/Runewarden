/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.image.BufferedImage;
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class FarmPatchHudTest
{
    @Test public void serverMessageFormsMatchThePinnedCoreTrackerAndRejectUnrelatedCompostChat()
    {
        assertEquals("ULTRACOMPOST",FarmCompostEvidence.parse("You treat the herb patch with ultracompost."));
        assertEquals("SUPERCOMPOST",FarmCompostEvidence.parse("The herb patch has been treated with supercompost."));
        assertEquals("COMPOST",FarmCompostEvidence.parse("This patch has already been treated with compost."));
        assertEquals("ULTRACOMPOST",FarmCompostEvidence.parse("This is a herb patch. The soil has been treated with ultracompost. The plants are growing."));
        assertNull(FarmCompostEvidence.parse("You put some compost into the bucket."));assertNull(FarmCompostEvidence.parse("Please use ultracompost."));
        for(String message:List.of("You treat the herb patch with ultracompost.","The herb patch has been treated with supercompost.","This patch has already been treated with compost.","This is a herb patch. The soil has been treated with ultracompost. The plants are growing.","You put some compost into the bucket."))
            assertEquals(nativeCompost(message),FarmCompostEvidence.parse(message));
    }
    @Test public void planningAndClickingNeverManufactureObservedCompost()
    { Fixture f=new Fixture();assertNull(f.reading().observedCompost);f.click();assertNull(f.reading().observedCompost);assertEquals(4774,f.patch.varbit); }
    @Test public void confirmedNearbyServerResponseAttachesOnlyToTheExactPatchVarbit()
    { Fixture f=new Fixture();f.click();f.message(ChatMessageType.GAMEMESSAGE,"You treat the herb patch with ultracompost.");assertEquals("ULTRACOMPOST",f.reading().observedCompost);assertTrue(f.reading().compostObservedAt>0); }
    @Test public void playerChatAndUnmatchedServerMessagesCannotClaimCompost()
    { Fixture f=new Fixture();f.message(ChatMessageType.GAMEMESSAGE,"You treat the herb patch with ultracompost.");assertNull(f.reading().observedCompost);f.click();f.message(ChatMessageType.PUBLICCHAT,"You treat the herb patch with ultracompost.");assertNull(f.reading().observedCompost); }
    @Test public void pendingEvidenceCannotCrossACharacterOrSceneChange()
    {
        Fixture f=new Fixture();f.click();f.base.nativeState.profile.set("rsprofile.other");f.message(ChatMessageType.GAMEMESSAGE,"You treat the herb patch with ultracompost.");assertTrue(f.base.planner.readings().isEmpty());
        Fixture other=new Fixture();other.click();when(other.view.getBaseX()).thenReturn(0);other.message(ChatMessageType.GAMEMESSAGE,"You treat the herb patch with ultracompost.");assertNull(other.reading().observedCompost);
    }
    @Test public void distantPlayerCannotConfirmAnotherPatchThroughOldPendingAction()
    { Fixture f=new Fixture();f.click();f.base.location.set(new net.runelite.api.coords.WorldPoint(f.base.location.get().getX()+12,f.base.location.get().getY(),0));f.message(ChatMessageType.SPAM,"You treat the herb patch with supercompost.");assertNull(f.reading().observedCompost); }
    @Test public void revisitingInANewSessionDoesNotAssertOldCompostAppliesToTheCurrentCrop()
    { Fixture f=new Fixture();f.click();f.message(ChatMessageType.SPAM,"You treat the herb patch with supercompost.");assertEquals("SUPERCOMPOST",f.reading().observedCompost);f.base.planner.stop();f.base.planner.start();f.base.tick(7);assertNull(f.reading().observedCompost); }
    @Test public void growthRangeStaysEstimatedAndDueIsNotHarvestable()
    {
        Fixture f=new Fixture();FarmPatchCatalog.State state=new FarmPatchCatalog.State();state.crop="RANARR";state.state="GROWING";state.stage=0;state.stages=5;state.tickMinutes=20;
        FarmPatchPlanner.PatchReading reading=new FarmPatchPlanner.PatchReading(f.patch,state,100_000,100_000,true);
        assertEquals("~1h–1h 20m",FarmPatchOverlay.timerLabel(reading,100));assertEquals("Due to check · estimated",FarmPatchOverlay.timerLabel(reading,5000));assertEquals("GROWING",state.state);
    }
    @Test public void oldCompostDoesNotCarryIntoANewCropButFreshPreplantEvidenceSurvives()
    {
        FarmPatchCatalog.State before=new FarmPatchCatalog.State(),next=new FarmPatchCatalog.State();before.crop="RANARR";before.state="HARVESTABLE";next.crop="RANARR";next.state="GROWING";
        assertTrue(FarmPatchPlanner.clearsCompost(before,next,1000,500));assertFalse(FarmPatchPlanner.clearsCompost(before,next,1000,1500));
    }
    @Test public void sceneScanDeduplicatesLargePatchObjectsAndRejectsInstances()
    {
        Fixture f=new Fixture();f.tiles[0][33][32]=f.tiles[0][32][32];clearInvocations(f.base.nativeState.client);
        FarmPatchScene.Snapshot found=FarmPatchScene.scan(f.base.nativeState.client,f.view,f.base.location.get(),List.of(f.patch));assertEquals(1,found.anchors.size());verify(f.base.nativeState.client,times(1)).getObjectDefinition(1001);
        when(f.view.isInstance()).thenReturn(true);assertTrue(FarmPatchScene.scan(f.base.nativeState.client,f.view,f.base.location.get(),List.of(f.patch)).anchors.isEmpty());assertFalse(found.matches(f.view));
    }
    @Test public void overlayDrawsAboveThePatchAndImmediatelyHidesOnProfileChange()
    {
        Fixture f=new Fixture();FarmPatchOverlay overlay=new FarmPatchOverlay(f.base.nativeState.client,f.base.planner,mock(net.runelite.client.game.ItemManager.class));BufferedImage image=new BufferedImage(400,400,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D graphics=image.createGraphics();overlay.render(graphics);graphics.dispose();assertNotEquals(0,image.getRGB(200,150));assertEquals(0,image.getRGB(200,220));
        f.base.nativeState.profile.set("rsprofile.other");BufferedImage blank=new BufferedImage(400,400,BufferedImage.TYPE_INT_ARGB);graphics=blank.createGraphics();overlay.render(graphics);graphics.dispose();assertEquals(0,blank.getRGB(200,150));
    }
    @Test public void palmAndCurryCardsRenderTheirOwnNativeSaplingIconsInTheCountdownRing()
    {
        for(int[] crop:new int[][]{{200,net.runelite.api.gameval.ItemID.PLANTPOT_PALM_SAPLING},{99,net.runelite.api.gameval.ItemID.PLANTPOT_CURRY_SAPLING}})
        {
            Fixture f=new Fixture("training:fruit-tree-runs","Brimhaven",crop[0]);
            net.runelite.client.game.ItemManager items=mock(net.runelite.client.game.ItemManager.class);
            net.runelite.client.util.AsyncBufferedImage icon=new net.runelite.client.util.AsyncBufferedImage(f.base.thread,32,32,BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D iconGraphics=icon.createGraphics();iconGraphics.setColor(java.awt.Color.MAGENTA);iconGraphics.fillRect(0,0,32,32);iconGraphics.dispose();
            when(items.getImage(crop[1])).thenReturn(icon);
            FarmPatchOverlay overlay=new FarmPatchOverlay(f.base.nativeState.client,f.base.planner,items);BufferedImage image=new BufferedImage(400,400,BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics=image.createGraphics();overlay.render(graphics);graphics.dispose();
            verify(items).getImage(crop[1]);int iconPixels=0;
            for(int y=100;y<200;y++)for(int x=0;x<400;x++)if(image.getRGB(x,y)==java.awt.Color.MAGENTA.getRGB())iconPixels++;
            assertTrue("Native crop icon must actually reach the scene canvas",iconPixels>=600);
            assertEquals(crop[1],f.base.planner.cropIconId(f.reading().state));
            RunewardenConfig config=mock(RunewardenConfig.class);
            when(config.hudWidgetSettings()).thenReturn(HudPreferences.encode(java.util.Collections.singletonMap("farm-patches",HudPreferences.Style.defaults(85).withScale(150))));
            overlay.setHudConfiguration(config);BufferedImage resized=new BufferedImage(400,400,BufferedImage.TYPE_INT_ARGB);
            graphics=resized.createGraphics();overlay.render(graphics);graphics.dispose();int resizedIconPixels=0;
            for(int y=70;y<200;y++)for(int x=0;x<400;x++)if(resized.getRGB(x,y)==java.awt.Color.MAGENTA.getRGB())resizedIconPixels++;
            assertTrue("Scene card scaling must resize the native icon with its text and ring",resizedIconPixels>=1400);
            assertEquals("Card remains above its native patch anchor",0,resized.getRGB(200,220));
        }
    }
    @Test public void plannerReadsNativeAccountCycleOffsetAndUsesCurrentGrowthStage()
    {
        Fixture f=new Fixture("training:fruit-tree-runs","Brimhaven",204);
        when(f.base.nativeState.config.getConfiguration("timetracking","rsprofile.characterA","farmTickOffsetPrecision",int.class)).thenReturn(160);
        when(f.base.nativeState.config.getConfiguration("timetracking","rsprofile.characterA","farmTickOffset",int.class)).thenReturn(37);
        f.base.tick(5);FarmPatchPlanner.PatchReading reading=f.reading();
        assertEquals(4,reading.state.stage);assertTrue(reading.growthClockKnown());assertEquals(reading.earliestGrowthSecond(),reading.latestGrowthSecond());
        long observed=reading.observedAt/1000,cycle=160*60,offset=37*60;
        long expected=observed+offset-Math.floorMod(observed+offset,cycle)-offset+2*cycle;
        assertEquals(expected,reading.latestGrowthSecond());
        assertTrue(reading.latestGrowthSecond()-observed<=320*60);
        assertFalse(FarmPatchOverlay.timerLabel(reading,observed).contains("960"));
        verify(f.base.nativeState.config,never()).setConfiguration(eq("timetracking"),anyString(),anyString(),anyString());
    }
    static final class Fixture
    {
        final FarmPatchPlannerTest.Fixture base=new FarmPatchPlannerTest.Fixture();final FarmPatchCatalog.Patch patch;
        final WorldView view=mock(WorldView.class);final Scene scene=mock(Scene.class);final Tile[][][] tiles=new Tile[1][64][64];final GameObject object=mock(GameObject.class);
        Fixture(){this("training:herb-runs","Ardougne",0);}
        Fixture(String guide,String region,int value)
        {
            patch=base.catalog.forGuide(guide).stream().filter(p->p.region.equals(region)).findFirst().get();
            base.location.set(new net.runelite.api.coords.WorldPoint((patch.regionId>>8)*64+32,(patch.regionId&255)*64+32,0));
            when(base.nativeState.client.getVarbitValue(patch.varbit)).thenReturn(value);
            when(view.getScene()).thenReturn(scene);when(scene.getTiles()).thenReturn(tiles);when(view.getBaseX()).thenReturn(base.location.get().getX()-32);when(view.getBaseY()).thenReturn(base.location.get().getY()-32);
            when(base.nativeState.client.getTopLevelWorldView()).thenReturn(view);when(base.nativeState.client.getCanvasWidth()).thenReturn(400);when(base.nativeState.client.getCanvasHeight()).thenReturn(400);
            when(object.getId()).thenReturn(1001);when(object.getWorldView()).thenReturn(view);when(object.sizeX()).thenReturn(3);when(object.sizeY()).thenReturn(3);when(object.getCanvasTextLocation(any(),anyString(),anyInt())).thenReturn(new net.runelite.api.Point(200,200));
            ObjectComposition definition=mock(ObjectComposition.class);when(definition.getVarbitId()).thenReturn(patch.varbit);when(base.nativeState.client.getObjectDefinition(1001)).thenReturn(definition);
            tiles[0][32][32]=mock(Tile.class);when(tiles[0][32][32].getGameObjects()).thenReturn(new GameObject[]{object});base.planner.start();base.tick(7);
        }
        FarmPatchPlanner.PatchReading reading(){return base.planner.readings().get(patch.id);}
        void click(){MenuEntry entry=mock(MenuEntry.class);when(entry.getType()).thenReturn(MenuAction.GAME_OBJECT_FIRST_OPTION);when(entry.getOption()).thenReturn("Inspect");when(entry.getIdentifier()).thenReturn(1001);when(entry.getParam0()).thenReturn(32);when(entry.getParam1()).thenReturn(32);base.planner.onMenuOptionClicked(new MenuOptionClicked(entry));}
        void message(ChatMessageType type,String text){base.planner.onChatMessage(new ChatMessage(null,type,"",text,"",0));}
    }
    private static String nativeCompost(String text)
    {
        try { java.lang.reflect.Method method=net.runelite.client.plugins.timetracking.farming.CompostTracker.class.getDeclaredMethod("determineCompostUsed",String.class);method.setAccessible(true);Object result=method.invoke(null,text);return result==null?null:result.toString(); }
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
}
