/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class RadialTimerTest
{
    @Test public void nativeAndWallClockIntervalsClampAndUnknownIntervalsStayUnknown()
    {
        assertEquals(1d,RadialTimer.fraction(99,100,110),0d);
        assertEquals(.5d,RadialTimer.fraction(105,100,110),0d);
        assertEquals(0d,RadialTimer.fraction(111,100,110),0d);
        assertTrue(Double.isNaN(RadialTimer.fraction(100,100,100)));
        assertTrue(Double.isNaN(RadialTimer.fraction(100,110,100)));
        assertEquals(.5d,RadialTimer.fraction(1_800_000_005_000L,1_800_000_000_000L,1_800_000_010_000L),0d);
    }
    @Test public void drawingAClockCannotChangeTheNextHudElementsGraphicsState()
    {
        BufferedImage image=new BufferedImage(80,80,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try
        {
            BasicStroke stroke=new BasicStroke(7);g.setStroke(stroke);g.setColor(Color.MAGENTA);
            RadialTimer.paint(g,40,40,18,.5,Color.YELLOW,"4s",true);
            assertEquals(Color.MAGENTA,g.getColor());assertEquals(stroke,g.getStroke());
            assertNotEquals(0,image.getRGB(40,40));
            assertEquals(0,image.getRGB(0,0));
        }
        finally{g.dispose();}
    }
    @Test public void anUnknownDeadlineDoesNotDrawAFakeCountdown()
    {
        BufferedImage image=new BufferedImage(40,40,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try{RadialTimer.paint(g,20,20,12,Double.NaN,Color.YELLOW,"?",false);assertEquals(0,image.getRGB(20,20));}
        finally{g.dispose();}
    }
}
