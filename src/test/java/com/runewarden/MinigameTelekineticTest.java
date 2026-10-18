/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class MinigameTelekineticTest
{
    private final MinigameTelekinetic.Bounds bounds=new MinigameTelekinetic.Bounds(0,0,4,4,0);
    private WorldPoint at(int x,int y){return new WorldPoint(x,y,0);}
    @Test public void solverUsesSlidingPullsRatherThanWalkingTiles()
    {
        assertEquals(List.of(MinigameTelekinetic.Pull.NORTH,MinigameTelekinetic.Pull.EAST),MinigameTelekinetic.solve(at(0,0),at(4,4),bounds,(x,y,dx,dy)->true));
        assertTrue(MinigameTelekinetic.solve(at(0,0),at(2,2),bounds,(x,y,dx,dy)->true).isEmpty());
    }
    @Test public void internalWallCreatesActualLandingAndShortestPath()
    {
        assertEquals(List.of(MinigameTelekinetic.Pull.NORTH,MinigameTelekinetic.Pull.EAST),
            MinigameTelekinetic.solve(at(1,1),at(4,2),bounds,(x,y,dx,dy)->!(x+dx==1&&y+dy==3)));
    }
    @Test public void enclosedGuardianProducesNoFabricatedRoute()
    {
        assertTrue(MinigameTelekinetic.solve(at(1,1),at(4,4),bounds,(x,y,dx,dy)->false).isEmpty());
    }
    @Test public void solvedInvalidOrCrossPlaneMazeProducesNoPulls()
    {
        assertTrue(MinigameTelekinetic.solve(at(2,2),at(2,2),bounds,(x,y,dx,dy)->true).isEmpty());
        assertTrue(MinigameTelekinetic.solve(at(1,1),new WorldPoint(4,4,1),bounds,(x,y,dx,dy)->true).isEmpty());
        assertTrue(MinigameTelekinetic.solve(at(1,1),at(4,4),new MinigameTelekinetic.Bounds(0,0,10000,4,0),(x,y,dx,dy)->true).isEmpty());
    }
    @Test public void standingMarkerIsNearestValidTileAlongChosenSide()
    {
        assertEquals(at(3,4),MinigameTelekinetic.standingTile(bounds,MinigameTelekinetic.Pull.NORTH,at(5,1)));
        assertEquals(at(0,1),MinigameTelekinetic.standingTile(bounds,MinigameTelekinetic.Pull.WEST,at(1,1)));
    }
    @Test public void cornerDoesNotCountAsCorrectCastingSide()
    {
        assertTrue(MinigameTelekinetic.sameSide(bounds,MinigameTelekinetic.Pull.NORTH,at(2,4)));
        assertFalse(MinigameTelekinetic.sameSide(bounds,MinigameTelekinetic.Pull.NORTH,at(4,4)));
        assertFalse(MinigameTelekinetic.sameSide(bounds,MinigameTelekinetic.Pull.WEST,new WorldPoint(0,2,1)));
    }
}
