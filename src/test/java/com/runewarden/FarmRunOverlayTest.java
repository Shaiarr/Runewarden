/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import net.runelite.api.GameState;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FarmRunOverlayTest
{
    @Test public void runHudAppearsOnStartAndImmediatelyVanishesOnStopCompletionOrAccountChange()
    {
        FarmRunNavigationTest.Fixture f=new FarmRunNavigationTest.Fixture();FarmRunOverlay overlay=overlay(f);
        assertNull(render(overlay));f.base.planner.startSelectedRun();assertNotNull(render(overlay));
        f.base.planner.stopActiveRun();assertNull(render(overlay));
        f.base.planner.startSelectedRun();assertNotNull(render(overlay));f.base.planner.checkNext(f.first.id,false);assertNotNull(render(overlay));
        f.base.planner.checkNext(f.second.id,false);assertNull(render(overlay));
        f.base.planner.startSelectedRun();assertNotNull(render(overlay));f.base.nativeState.profile.set("rsprofile.other");assertNull(render(overlay));
    }
    @Test public void logoutAndTheIndividualHudToggleHideTheRunWithoutAnExtraTick()
    {
        FarmRunNavigationTest.Fixture f=new FarmRunNavigationTest.Fixture();f.base.planner.startSelectedRun();FarmRunOverlay overlay=overlay(f);
        assertNotNull(render(overlay));when(f.base.nativeState.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);assertNull(render(overlay));
        when(f.base.nativeState.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        assertNull(render(new FarmRunOverlay(f.base.planner,new RunewardenConfig(){@Override public boolean farmRunHud(){return false;}},null)));
    }
    @Test public void selectedNativeCropIconActuallyReachesTheMovableRunHud()
    {
        FarmRunNavigationTest.Fixture f=new FarmRunNavigationTest.Fixture();f.base.planner.startSelectedRun();ItemManager items=mock(ItemManager.class);
        int item=f.base.planner.currentRun().seedItemId;
        AsyncBufferedImage icon=new AsyncBufferedImage(f.base.thread,32,32,BufferedImage.TYPE_INT_ARGB);Graphics2D iconGraphics=icon.createGraphics();
        iconGraphics.setColor(Color.MAGENTA);iconGraphics.fillRect(0,0,32,32);iconGraphics.dispose();when(items.getImage(item)).thenReturn(icon);
        FarmRunOverlay overlay=new FarmRunOverlay(f.base.planner,new RunewardenConfig(){@Override public boolean farmRunHud(){return true;}},items);
        BufferedImage image=new BufferedImage(500,400,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();Dimension size;
        try {size=overlay.render(graphics);}finally {graphics.dispose();}
        verify(items).getImage(item);assertNotNull(size);assertEquals(Color.MAGENTA.getRGB(),image.getRGB(25,25));
        assertEquals("Open harvest run",overlay.getMenuEntries().get(0).getOption());
        assertTrue(overlay.isMovable());overlay.setHudLocked(true);assertFalse(overlay.isMovable());overlay.setHudLocked(false);assertTrue(overlay.isMovable());
    }
    @Test public void longLocationAndStatusTextStayInsideTheScaledCardAndProgressActuallyFills()
    {
        FarmPatchPlanner.RunReading run=longRun();HudPreferences.Style style=HudPreferences.Style.defaults(90).withScale(170);
        BufferedImage image=new BufferedImage(600,400,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();Dimension size;
        try {size=FarmRunOverlay.paintRun(graphics,run,160_000,style,null);}finally {graphics.dispose();}
        assertTrue(size.width>246);assertTrue(size.height>142);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
            if(x>=size.width||y>=size.height)assertEquals("No long line may spill onto the game outside the card",0,image.getRGB(x,y));
        int filled=image.getRGB((int)(50*1.7),(int)(114*1.7)),unfilled=image.getRGB((int)(210*1.7),(int)(114*1.7));
        assertNotEquals("Observed one-of-two completion must visibly fill half of the bar",filled,unfilled);
    }
    @Test public void elapsedTimeChangesOnlyTheFooterAndRenderingPreservesOtherOverlayGraphics()
    {
        FarmPatchPlanner.RunReading run=longRun();BufferedImage first=paint(run,100_000),later=paint(run,160_000);int changed=0;
        for(int y=0;y<142;y++)for(int x=0;x<246;x++)if(first.getRGB(x,y)!=later.getRGB(x,y))
        {changed++;assertTrue("The changing elapsed clock belongs in the footer",y>=120);}
        assertTrue("The elapsed clock must advance without fabricating an ETA",changed>0);
        Graphics2D graphics=first.createGraphics();try
        {
            graphics.setColor(Color.BLUE);java.awt.geom.AffineTransform transform=graphics.getTransform();
            FarmRunOverlay.paintRun(graphics,run,160_000,HudPreferences.Style.defaults(90),null);
            assertEquals(Color.BLUE,graphics.getColor());assertEquals(transform,graphics.getTransform());
        }
        finally {graphics.dispose();}
    }
    @Test public void rendersLabelledRunHudPreview() throws Exception
    {
        BufferedImage image=new BufferedImage(520,270,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();
        try
        {
            graphics.setColor(new Color(16,18,22));graphics.fillRect(0,0,520,270);graphics.setColor(HudStyle.GOLD);graphics.setFont(HudStyle.label(17));graphics.drawString("RUNEWARDEN · HARVEST RUN",20,28);
            graphics.setFont(HudStyle.body(11));graphics.setColor(HudStyle.MUTED);graphics.drawString("VISUAL QA FIXTURE · mocked run checklist",20,49);
            graphics.translate(20,70);FarmRunOverlay.paintRun(graphics,longRun(),160_000,HudPreferences.Style.defaults(90),null);
        }
        finally {graphics.dispose();}
        File output=new File("build/previews/harvest-run.png");assertTrue(output.getParentFile().isDirectory()||output.getParentFile().mkdirs());assertTrue(ImageIO.write(image,"png",output));
    }
    private static FarmRunOverlay overlay(FarmRunNavigationTest.Fixture fixture)
    {return new FarmRunOverlay(fixture.base.planner,new RunewardenConfig(){@Override public boolean farmRunHud(){return true;}},null);}
    private static Dimension render(FarmRunOverlay overlay)
    {
        BufferedImage image=new BufferedImage(500,400,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
        try {return overlay.render(graphics);}finally {graphics.dispose();}
    }
    private static BufferedImage paint(FarmPatchPlanner.RunReading run,long now)
    {
        BufferedImage image=new BufferedImage(246,142,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
        try {FarmRunOverlay.paintRun(graphics,run,now,HudPreferences.Style.defaults(90),null);}finally {graphics.dispose();}return image;
    }
    private static FarmPatchPlanner.RunReading longRun()
    {
        FarmPatchPlanner.Route route=new FarmPatchPlanner.Route();route.active=true;route.seedItemId=22195;route.startedAt=100_000;route.stops.addAll(List.of("first","next"));route.completed.add("first");
        String label="Fossil Island · Verdant Valley north-eastern birdhouse with a deliberately long native location description";
        return new FarmPatchPlanner.RunReading("test",1,new FarmPatchPlanner.RunType("birdhouse","Birdhouse run","BIRDHOUSE",22195),route,"next",label,"Run active","",
            List.of(new FarmPatchPlanner.StopReading("next",label,"Refill observed · this deliberately long status must remain readable within the card","",true,false,null)),List.of());
    }
}
