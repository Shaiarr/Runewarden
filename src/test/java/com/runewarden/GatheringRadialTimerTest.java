/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.timetracking.SummaryState;
import org.junit.Test;
import static org.junit.Assert.*;

public class GatheringRadialTimerTest
{
    private SkillingCoach.Node node(int ticks,boolean respawn)
    {return new SkillingCoach.Node(new WorldPoint(3200,3200,0),0,1,1,80,respawn?"Rock":"Yew",1000,ticks,respawn);}
    @Test public void onlySupportedNodeDurationsHaveRingsAndLifetimeExpiryIsNotADepletionClaim()
    {
        SkillingCoach.Node life=node(190,false),respawn=node(14,true),random=node(0,false);
        assertTrue(NodeTimerOverlay.hasRadial(life,1000,RunewardenConfig.TimerDisplay.BOTH));
        assertEquals(1,RadialTimer.fraction(1000,life.started,life.endsAt()),0);
        assertEquals(.5,RadialTimer.fraction(58000,life.started,life.endsAt()),1e-9);
        assertFalse(NodeTimerOverlay.hasRadial(life,115000,RunewardenConfig.TimerDisplay.RADIAL));
        assertEquals("Next log may deplete",life.label(115000));
        assertTrue(NodeTimerOverlay.hasRadial(respawn,1000,RunewardenConfig.TimerDisplay.RADIAL));
        assertFalse(NodeTimerOverlay.hasRadial(random,1000,RunewardenConfig.TimerDisplay.RADIAL));
        assertEquals("Until depleted",random.label(1000));
        assertFalse(NodeTimerOverlay.hasRadial(life,1000,RunewardenConfig.TimerDisplay.TEXT));
    }
    @Test public void radialAndBothAreAboveNodeVisualsAndUnknownNodesKeepReadableText() throws Exception
    {
        BufferedImage image=new BufferedImage(900,240,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try
        {
            g.setColor(new Color(28,30,35));g.fillRect(0,0,900,240);
            int column=0;for(RunewardenConfig.TimerDisplay mode:RunewardenConfig.TimerDisplay.values())
            {
                g.setColor(Color.WHITE);g.drawString(mode.name(),column*300+120,20);
                Dimension life=NodeTimerOverlay.paintNode(g,node(190,false),40000,mode,column*300+150,90);
                Dimension respawn=NodeTimerOverlay.paintNode(g,node(14,true),5000,mode,column*300+150,160);
                Dimension unknown=NodeTimerOverlay.paintNode(g,node(0,false),5000,mode,column*300+150,210);
                assertEquals(23,unknown.height);
                if(mode==RunewardenConfig.TimerDisplay.RADIAL){assertEquals(59,life.height);assertEquals(59,respawn.height);}
                if(mode==RunewardenConfig.TimerDisplay.BOTH)assertEquals(29,life.height);
                column++;
            }
        }
        finally{g.dispose();}
        File file=new File("build/reports/gathering-radial-modes.png");file.getParentFile().mkdirs();ImageIO.write(image,"png",file);
    }
    private HarvestClockTool.Reading reading(SummaryState state,long completion)
    {return new HarvestClockTool.Reading(true,false,"",List.of(new HarvestClockTool.Harvest("herb","Herb",state,completion,true)));}
    @Test public void harvestWindowUsesFirstObservedDeadlineAndNeverPretendsToKnowPlantingTime()
    {
        HarvestTimerWindows windows=new HarvestTimerWindows();HarvestClockTool.Harvest first=windows.observe(reading(SummaryState.IN_PROGRESS,200),100).harvests.get(0);
        assertEquals(100,first.windowStart);assertEquals(1,first.remainingFraction(100),0);assertTrue(first.estimated);
        HarvestClockTool.Harvest later=windows.observe(reading(SummaryState.IN_PROGRESS,200),125).harvests.get(0);
        assertEquals(100,later.windowStart);assertEquals(.75,later.remainingFraction(125),0);
        assertEquals(.5,later.remainingFraction(150),0);assertEquals(0,later.remainingFraction(250),0);
        HarvestClockTool.Harvest next=windows.observe(reading(SummaryState.IN_PROGRESS,400),150).harvests.get(0);
        assertEquals(150,next.windowStart);assertEquals(1,next.remainingFraction(150),0);
    }
    @Test public void unknownEmptyAndNewProfileCannotReuseOldWindow()
    {
        HarvestTimerWindows windows=new HarvestTimerWindows();windows.observe(reading(SummaryState.IN_PROGRESS,200),100);
        assertFalse(windows.observe(reading(SummaryState.UNKNOWN,200),125).harvests.get(0).hasWindow());
        HarvestClockTool.Harvest seen=windows.observe(reading(SummaryState.IN_PROGRESS,200),150).harvests.get(0);assertEquals(150,seen.windowStart);
        windows.clear();seen=windows.observe(reading(SummaryState.IN_PROGRESS,200),175).harvests.get(0);assertEquals(175,seen.windowStart);
        assertFalse(windows.observe(reading(SummaryState.EMPTY,0),180).harvests.get(0).hasWindow());
        assertFalse(windows.observe(reading(SummaryState.IN_PROGRESS,-1),180).harvests.get(0).hasWindow());
        assertFalse(windows.observe(reading(SummaryState.COMPLETED,0),180).harvests.get(0).hasWindow());
    }
    @Test public void compactLabelsRoundUpAndDoNotClaimReadyEarly()
    {
        assertEquals("2m",HarvestClockOverlay.compactCountdown(61));assertEquals("2h",HarvestClockOverlay.compactCountdown(3601));
        assertEquals("2d",HarvestClockOverlay.compactCountdown(86401));assertEquals("1s",HarvestClockOverlay.compactCountdown(1));
        assertEquals("0s",HarvestClockOverlay.compactCountdown(-1));
    }
}
