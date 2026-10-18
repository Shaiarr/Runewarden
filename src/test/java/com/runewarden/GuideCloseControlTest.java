/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.input.MouseManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GuideCloseControlTest
{
    @Test public void visibleCloseControlConsumesOnlyItsOwnClickAndBalancesItsNativeListener()
    {
        Client client=mock(Client.class);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        MouseManager mouse=mock(MouseManager.class);GuideCoachOverlay overlay=new GuideCoachOverlay(client,new RunewardenConfig(){},mouse);
        AtomicInteger closed=new AtomicInteger();overlay.setCloseAction(()->{closed.incrementAndGet();overlay.clear();});
        overlay.start();overlay.start();verify(mouse).registerMouseListener(overlay);
        Graphics2D graphics=new BufferedImage(500,500,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            overlay.getBounds().setLocation(40,30);overlay.select("Training","Current task","Collect the preform",3,8,2);
            Dimension size=overlay.render(graphics);int x=40+200,y=30+size.height-14;
            MouseEvent control=click(x,y,InputEvent.CTRL_DOWN_MASK);overlay.mousePressed(control);assertFalse(control.isConsumed());assertEquals(0,closed.get());
            MouseEvent alt=click(x,y,InputEvent.ALT_DOWN_MASK);overlay.mousePressed(alt);assertFalse(alt.isConsumed());
            MouseEvent outside=click(1,1,0);overlay.mousePressed(outside);assertFalse(outside.isConsumed());
            MouseEvent own=click(x,y,0);overlay.mousePressed(own);assertTrue(own.isConsumed());assertEquals(1,closed.get());assertNull(overlay.render(graphics));
            MouseEvent release=new MouseEvent(new Canvas(),MouseEvent.MOUSE_RELEASED,0,0,x,y,1,false,MouseEvent.BUTTON1);overlay.mouseReleased(release);assertTrue(release.isConsumed());
            MouseEvent clicked=new MouseEvent(new Canvas(),MouseEvent.MOUSE_CLICKED,0,0,x,y,1,false,MouseEvent.BUTTON1);overlay.mouseClicked(clicked);assertTrue(clicked.isConsumed());
            MouseEvent stale=click(x,y,0);overlay.mousePressed(stale);assertFalse(stale.isConsumed());assertEquals(1,closed.get());
        }
        finally{graphics.dispose();overlay.stop();overlay.stop();}
        verify(mouse).unregisterMouseListener(overlay);
    }
    private static MouseEvent click(int x,int y,int modifiers)
    {return new MouseEvent(new Canvas(),MouseEvent.MOUSE_PRESSED,0,modifiers,x,y,1,false,MouseEvent.BUTTON1);}
}
