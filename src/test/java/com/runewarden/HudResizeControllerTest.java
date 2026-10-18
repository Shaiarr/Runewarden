/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.Overlay;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class HudResizeControllerTest
{
    @Test public void nativeMouseManagerConsumesResizeBeforeTheGameAndRestoresNormalInput()
    {
        MouseManager nativeMouse=com.google.inject.Guice.createInjector(new com.google.inject.AbstractModule()
        {
            @Override protected void configure()
            {bind(net.runelite.client.config.RuneLiteConfig.class).toInstance(new net.runelite.client.config.RuneLiteConfig() {});}
        }).getInstance(MouseManager.class);
        Fixture f=new Fixture(nativeMouse);
        try
        {
            MouseEvent down=f.event(MouseEvent.MOUSE_PRESSED,363,140,InputEvent.CTRL_DOWN_MASK,MouseEvent.BUTTON1);
            nativeMouse.processMousePressed(down);assertTrue(down.isConsumed());
            MouseEvent move=f.event(MouseEvent.MOUSE_DRAGGED,495,140,InputEvent.CTRL_DOWN_MASK|InputEvent.BUTTON1_DOWN_MASK,MouseEvent.NOBUTTON);
            nativeMouse.processMouseDragged(move);assertTrue(move.isConsumed());
            MouseEvent up=f.event(MouseEvent.MOUSE_RELEASED,495,140,0,MouseEvent.BUTTON1);
            nativeMouse.processMouseReleased(up);assertTrue(up.isConsumed());
            MouseEvent click=f.event(MouseEvent.MOUSE_CLICKED,495,140,0,MouseEvent.BUTTON1);
            nativeMouse.processMouseClicked(click);assertTrue(click.isConsumed());
            assertEquals(150,HudPreferences.decode(f.layout).get("guide").scale);
            MouseEvent normal=f.event(MouseEvent.MOUSE_PRESSED,200,200,0,MouseEvent.BUTTON1);
            nativeMouse.processMousePressed(normal);assertFalse(normal.isConsumed());
        }
        finally {f.controller.stop();}
    }

    @Test public void nativeStretchedModeCoordinatesReachResizeWithoutASecondConversion()
    {
        MouseManager nativeMouse=com.google.inject.Guice.createInjector(new com.google.inject.AbstractModule()
        {
            @Override protected void configure()
            {bind(net.runelite.client.config.RuneLiteConfig.class).toInstance(new net.runelite.client.config.RuneLiteConfig() {});}
        }).getInstance(MouseManager.class);
        Fixture f=new Fixture(nativeMouse);
        when(f.client.getStretchedDimensions()).thenReturn(new Dimension(1600,1200));
        when(f.client.getRealDimensions()).thenReturn(new Dimension(800,600));
        nativeMouse.registerMouseListener(0,new net.runelite.client.plugins.stretchedmode.TranslateMouseListener(f.client));
        try
        {
            assertTrue(nativeMouse.processMousePressed(f.event(MouseEvent.MOUSE_PRESSED,726,280,InputEvent.CTRL_DOWN_MASK,MouseEvent.BUTTON1)).isConsumed());
            assertTrue(nativeMouse.processMouseDragged(f.event(MouseEvent.MOUSE_DRAGGED,990,280,InputEvent.CTRL_DOWN_MASK|InputEvent.BUTTON1_DOWN_MASK,MouseEvent.NOBUTTON)).isConsumed());
            assertTrue(nativeMouse.processMouseReleased(f.event(MouseEvent.MOUSE_RELEASED,990,280,0,MouseEvent.BUTTON1)).isConsumed());
            assertEquals(150,HudPreferences.decode(f.layout).get("guide").scale);
        }
        finally {f.controller.stop();}
    }

    @Test public void controlEdgeDragChangesActualHudPaintAndBoundsBeforeSavingOnce() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            BufferedImage before = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
            Dimension original = f.paint(before);
            f.card.getBounds().setBounds(100,100,original.width,original.height);
            SwingUtilities.invokeAndWait(() ->
            {
                MouseEvent down=f.event(MouseEvent.MOUSE_PRESSED,363,140,InputEvent.CTRL_DOWN_MASK,MouseEvent.BUTTON1);
                f.controller.mousePressed(down);assertTrue(down.isConsumed());
                MouseEvent move=f.event(MouseEvent.MOUSE_DRAGGED,495,140,InputEvent.CTRL_DOWN_MASK|InputEvent.BUTTON1_DOWN_MASK,MouseEvent.NOBUTTON);
                f.controller.mouseDragged(move);assertTrue(move.isConsumed());
                assertEquals(150,HudStyle.settings(f.config,"guide").scale);
                verify(f.manager,never()).setConfiguration(anyString(),anyString(),any());
                BufferedImage during=new BufferedImage(600,400,BufferedImage.TYPE_INT_ARGB);
                Dimension enlarged=f.paint(during);
                assertEquals(396,enlarged.width);assertEquals((int)Math.ceil(original.height*1.5),enlarged.height);
                assertEquals(0,before.getRGB(300,35));assertNotEquals(0,during.getRGB(300,35));
                MouseEvent up=f.event(MouseEvent.MOUSE_RELEASED,495,140,0,MouseEvent.BUTTON1);
                f.controller.mouseReleased(up);assertTrue(up.isConsumed());
                assertEquals(150,HudPreferences.decode(f.layout).get("guide").scale);
                assertEquals(f.layout,f.profiles.get("A"));
                assertEquals(enlarged,f.paint(new BufferedImage(600,400,BufferedImage.TYPE_INT_ARGB)));
                MouseEvent click=f.event(MouseEvent.MOUSE_CLICKED,495,140,0,MouseEvent.BUTTON1);
                f.controller.mouseClicked(click);assertTrue(click.isConsumed());
            });
            verify(f.manager,times(1)).setConfiguration(eq(RunewardenConfig.GROUP),eq("hudWidgetSettings"),any());
            verify(f.manager,times(1)).setRSProfileConfiguration(eq(RunewardenConfig.GROUP),eq("hudCharacterLayout"),any());
        }
        finally { f.controller.stop(); }
    }

    @Test public void altMovesAndOrdinaryNativeClicksRemainUntouchedWhileControlShowsResizeCursor()
    {
        Fixture f=new Fixture();
        try
        {
            for(int modifiers:new int[]{0,InputEvent.ALT_DOWN_MASK,InputEvent.CTRL_DOWN_MASK|InputEvent.ALT_DOWN_MASK})
            {
                MouseEvent down=f.event(MouseEvent.MOUSE_PRESSED,363,140,modifiers,MouseEvent.BUTTON1);
                f.controller.mousePressed(down);assertFalse(down.isConsumed());
            }
            MouseEvent center=f.event(MouseEvent.MOUSE_PRESSED,200,140,InputEvent.CTRL_DOWN_MASK,MouseEvent.BUTTON1);
            f.controller.mousePressed(center);assertFalse(center.isConsumed());
            f.controller.mouseMoved(f.event(MouseEvent.MOUSE_MOVED,363,140,InputEvent.CTRL_DOWN_MASK,MouseEvent.NOBUTTON));
            verify(f.ui).setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            f.controller.keyReleased(new KeyEvent(f.canvas,KeyEvent.KEY_RELEASED,0,0,KeyEvent.VK_CONTROL,KeyEvent.CHAR_UNDEFINED));
            verify(f.ui,atLeastOnce()).setCursor(Cursor.getDefaultCursor());
            verify(f.mouse).registerMouseListener(f.controller);verify(f.keys).registerKeyListener(f.controller);
            verifyNoInteractions(f.manager);
        }
        finally { f.controller.stop(); }
        verify(f.mouse).unregisterMouseListener(f.controller);verify(f.keys).unregisterKeyListener(f.controller);
    }

    @Test public void scaleIsBoundedAndNorthWestCornerCanShrinkTheSameCard()
    {
        Fixture f=new Fixture();
        try
        {
            f.drag(363,140,2000,140);assertEquals(180,HudPreferences.decode(f.layout).get("guide").scale);
            f.drag(101,101,2000,1000);assertEquals(60,HudPreferences.decode(f.layout).get("guide").scale);
            assertEquals(159,HudStyle.dimension(HudStyle.settings(f.config,"guide"),264,100).width);
        }
        finally { f.controller.stop(); }
    }

    @Test public void lockedHiddenAndNonLoggedInCardsCannotStartResizing()
    {
        Fixture f=new Fixture();
        try
        {
            f.locked=true;assertFalse(f.press(363,140).isConsumed());f.locked=false;
            f.card.getBounds().setSize(0,0);assertFalse(f.press(363,140).isConsumed());
            f.card.getBounds().setBounds(100,100,264,100);
            when(f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);assertFalse(f.press(363,140).isConsumed());
            when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);when(f.client.isMenuOpen()).thenReturn(true);
            assertFalse(f.press(363,140).isConsumed());verifyNoInteractions(f.manager);
        }
        finally { f.controller.stop(); }
    }

    @Test public void profileSwitchCancelsPreviewAndNeverWritesEitherCharactersLayout()
    {
        Fixture f=new Fixture();
        try
        {
            assertTrue(f.press(363,140).isConsumed());
            f.controller.mouseDragged(f.event(MouseEvent.MOUSE_DRAGGED,495,140,InputEvent.CTRL_DOWN_MASK,MouseEvent.NOBUTTON));
            assertEquals(150,HudStyle.settings(f.config,"guide").scale);
            f.profile="B";f.controller.validateProfile();
            assertEquals(100,HudStyle.settings(f.config,"guide").scale);
            MouseEvent up=f.event(MouseEvent.MOUSE_RELEASED,495,140,0,MouseEvent.BUTTON1);
            f.controller.mouseReleased(up);assertTrue(up.isConsumed());
            verify(f.manager,never()).setConfiguration(anyString(),anyString(),any());
            verify(f.manager,never()).setRSProfileConfiguration(anyString(),anyString(),any());
            assertTrue(f.profiles.isEmpty());
        }
        finally { f.controller.stop(); }
    }

    @Test public void enablingHudLockDuringDragCancelsAndConsumesTheGestureWithoutSaving()
    {
        Fixture f=new Fixture();
        try
        {
            f.press(363,140);f.locked=true;
            MouseEvent move=f.event(MouseEvent.MOUSE_DRAGGED,495,140,InputEvent.CTRL_DOWN_MASK,MouseEvent.NOBUTTON);
            f.controller.mouseDragged(move);assertTrue(move.isConsumed());
            MouseEvent up=f.event(MouseEvent.MOUSE_RELEASED,495,140,0,MouseEvent.BUTTON1);
            f.controller.mouseReleased(up);assertTrue(up.isConsumed());
            assertEquals(100,HudStyle.settings(f.config,"guide").scale);
            verify(f.manager,never()).setConfiguration(anyString(),anyString(),any());
        }
        finally { f.controller.stop(); }
    }

    @Test public void farmAndNodeCardsUseOnlyCurrentRenderedRegionsAndKeepIndependentScale()
    {
        Fixture f=new Fixture();
        try
        {
            f.controller.beginScene("farm-patches");f.controller.sceneBounds("farm-patches",new Rectangle(500,200,160,60));
            f.drag(659,230,739,230);
            assertEquals(150,HudPreferences.decode(f.layout).get("farm-patches").scale);
            assertEquals(100,HudStyle.settings(f.config,"node-timers").scale);
            f.controller.beginScene("farm-patches");assertFalse(f.press(659,230).isConsumed());
            f.controller.beginScene("node-timers");f.controller.sceneBounds("node-timers",new Rectangle(500,200,160,60));
            f.nanos+=600_000_000L;assertFalse(f.press(659,230).isConsumed());
            f.controller.beginScene("node-timers");f.controller.sceneBounds("node-timers",new Rectangle(500,200,160,60));
            assertTrue(f.press(659,230).isConsumed());f.controller.focusLost();
            assertEquals(100,HudStyle.settings(f.config,"node-timers").scale);
        }
        finally { f.controller.stop(); }
    }

    private static final class Fixture
    {
        final Client client=mock(Client.class);
        final ConfigManager manager=mock(ConfigManager.class);
        final MouseManager mouse;
        final KeyManager keys=mock(KeyManager.class);
        final ClientUI ui=mock(ClientUI.class);
        final Canvas canvas=new Canvas();
        final Map<String,String> profiles=new LinkedHashMap<>();
        String profile="A",layout="";
        boolean locked;
        long nanos=1;
        final RunewardenConfig config=new RunewardenConfig()
        {
            @Override public String hudWidgetSettings(){return layout;}
            @Override public boolean hudLocked(){return locked;}
        };
        final GuideCoachOverlay card=new GuideCoachOverlay(client,config);
        final HudResizeController controller;
        Fixture() {this(mock(MouseManager.class));}
        Fixture(MouseManager mouse)
        {
            this.mouse=mouse;
            controller=new HudResizeController(client,config,manager,mouse,keys,ui,()->nanos);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(ui.getDefaultCursor()).thenReturn(Cursor.getDefaultCursor());
            when(manager.getRSProfileKey()).thenAnswer(call->profile);
            doAnswer(call->{layout=call.getArgument(2);return null;}).when(manager).setConfiguration(eq(RunewardenConfig.GROUP),eq("hudWidgetSettings"),any());
            doAnswer(call->{profiles.put(profile,call.getArgument(2));return null;}).when(manager).setRSProfileConfiguration(eq(RunewardenConfig.GROUP),eq("hudCharacterLayout"),any());
            card.select("Activity","Current task","Prepare supplies",1,3);card.getBounds().setBounds(100,100,264,100);
            Map<String,Overlay> widgets=new LinkedHashMap<>();widgets.put("guide",card);controller.start(widgets);
        }
        MouseEvent event(int id,int x,int y,int modifiers,int button)
        {return new MouseEvent(canvas,id,1,modifiers,x,y,1,false,button);}
        MouseEvent press(int x,int y)
        {MouseEvent event=event(MouseEvent.MOUSE_PRESSED,x,y,InputEvent.CTRL_DOWN_MASK,MouseEvent.BUTTON1);controller.mousePressed(event);return event;}
        void drag(int x,int y,int targetX,int targetY)
        {
            assertTrue(press(x,y).isConsumed());
            controller.mouseDragged(event(MouseEvent.MOUSE_DRAGGED,targetX,targetY,InputEvent.CTRL_DOWN_MASK|InputEvent.BUTTON1_DOWN_MASK,MouseEvent.NOBUTTON));
            controller.mouseReleased(event(MouseEvent.MOUSE_RELEASED,targetX,targetY,0,MouseEvent.BUTTON1));
        }
        Dimension paint(BufferedImage image)
        {Graphics2D g=image.createGraphics();try{return card.render(g);}finally{g.dispose();}}
    }
}
