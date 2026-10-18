/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import net.runelite.api.Point;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VarlamoreDoorCardTest
{
    private static final HudPreferences.Style STYLE=HudPreferences.Style.defaults(90);

    @Test public void visibleDoorsKeepTheWholeCardAboveTheDoorAndInsideTheCanvas()
    {
        for(double scale:new double[]{0.6,1.0,1.8})
            for(Point anchor:new Point[]{new Point(400,400),new Point(3,400),new Point(797,400)})
            {
                Rectangle card=VarlamoreThievingSceneOverlay.doorCardBounds(anchor,800,600,scale);
                assertNotNull("A visible door with ample vertical room must get a card",card);
                assertTrue("The complete scaled card must fit the native canvas",new Rectangle(0,0,800,600).contains(card));
                assertTrue("The card must leave a visible leader line above the actual door",card.getMaxY()<anchor.getY());
            }
    }

    @Test public void largerHudScaleProducesALargerCardWhileKeepingTheAnchorIndependent()
    {
        Point anchor=new Point(420,420);
        Rectangle small=VarlamoreThievingSceneOverlay.doorCardBounds(anchor,900,600,0.7);
        Rectangle large=VarlamoreThievingSceneOverlay.doorCardBounds(anchor,900,600,1.5);
        assertNotNull(small);assertNotNull(large);
        assertTrue(large.width>small.width);assertTrue(large.height>small.height);
        assertTrue(large.getMaxY()<anchor.getY());
        assertEquals(420,anchor.getX());assertEquals(420,anchor.getY());
    }

    @Test public void unprojectableOffscreenAndTooCrampedDoorsDoNotInventAFloatingCard()
    {
        assertNull(VarlamoreThievingSceneOverlay.doorCardBounds(null,800,600,1));
        for(Point anchor:new Point[]{new Point(-10000,-10000),new Point(-1,300),new Point(800,300),new Point(400,-1),new Point(400,600),new Point(400,12)})
            assertNull("No card for an anchor beyond the native canvas or with no room above it",VarlamoreThievingSceneOverlay.doorCardBounds(anchor,800,600,1));
        assertNull(VarlamoreThievingSceneOverlay.doorCardBounds(new Point(30,30),60,60,1));
        assertNull(VarlamoreThievingSceneOverlay.doorCardBounds(new Point(0,0),0,0,1));
    }

    @Test public void actualPaintConnectsTheCardToItsDoorInsteadOfStoppingAtTheLabel()
    {
        Point anchor=new Point(210,260);BufferedImage image=new BufferedImage(420,320,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();
        try {paint(graphics,anchor,420,320,1,"Lavinia",ownerTimer(),100_000);}
        finally {graphics.dispose();}
        Rectangle card=VarlamoreThievingSceneOverlay.doorCardBounds(anchor,420,320,1);
        assertTrue("The actual projected door endpoint must be marked",paintedNear(image,anchor.getX(),anchor.getY(),3));
        assertTrue("A leader must cross the empty gap between the floating card and door",paintedNear(image,anchor.getX(),(int)((card.getMaxY()+anchor.getY())/2),3));
        assertTrue("The card must contain visible native text",brightPixels(image,card)>35);
        assertEquals("The label must not become a tall opaque panel below the door",0,image.getRGB(anchor.getX(),anchor.getY()+12));
    }

    @Test public void edgeCardsAndTheirLeadersRespectTheProvidedNativeCanvas()
    {
        for(Point anchor:new Point[]{new Point(1,230),new Point(318,230)})
        {
            BufferedImage image=new BufferedImage(400,340,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
            try {paint(graphics,anchor,320,260,1,"Victor",null,100_000);}
            finally {graphics.dispose();}
            assertTrue("A visible edge door still gets its endpoint",paintedNear(image,anchor.getX(),anchor.getY(),3));
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
                if(x>=320||y>=260)assertEquals("Painting may not spill onto adjacent native UI",0,image.getRGB(x,y));
        }
    }

    @Test public void drawingTheDoorCardPreservesTheOtherSceneOverlaysGraphicsState()
    {
        BufferedImage image=new BufferedImage(600,400,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
        try
        {
            graphics.setColor(Color.MAGENTA);graphics.setStroke(new java.awt.BasicStroke(3));
            AffineTransform before=graphics.getTransform();java.awt.Stroke stroke=graphics.getStroke();
            paint(graphics,new Point(300,300),600,400,1,"Caius",ownerTimer(),100_000);
            assertEquals(Color.MAGENTA,graphics.getColor());assertEquals(stroke,graphics.getStroke());assertEquals(before,graphics.getTransform());
        }
        finally {graphics.dispose();}
    }

    @Test @SuppressWarnings("unchecked") public void hiddenHudSuppressesTheFloatingCardAndItsCompactFallbackBadge()
    {
        net.runelite.api.Client client=mock(net.runelite.api.Client.class);
        net.runelite.api.Player player=mock(net.runelite.api.Player.class);
        net.runelite.api.WorldView view=mock(net.runelite.api.WorldView.class);
        net.runelite.client.config.ConfigManager config=mock(net.runelite.client.config.ConfigManager.class);
        when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);when(client.getWorld()).thenReturn(301);
        when(client.getLocalPlayer()).thenReturn(player);when(client.getTopLevelWorldView()).thenReturn(view);
        when(client.getCanvasWidth()).thenReturn(500);when(client.getCanvasHeight()).thenReturn(330);
        when(player.getWorldLocation()).thenReturn(new net.runelite.api.coords.WorldPoint(1690,3105,0));
        when(config.getRSProfileKey()).thenReturn("door-visibility-test");
        net.runelite.api.IndexedObjectSet<net.runelite.api.NPC> npcs=mock(net.runelite.api.IndexedObjectSet.class);
        when(npcs.iterator()).thenAnswer(ignored->java.util.Collections.emptyIterator());doReturn(npcs).when(view).npcs();
        String[] encoded={""};RunewardenConfig hudConfig=new RunewardenConfig(){@Override public String hudWidgetSettings(){return encoded[0];}};
        VarlamoreThievingCoach coach=new VarlamoreThievingCoach(client,config,new RunewardenDataStore(config,new com.google.gson.Gson()),hudConfig,()->100_000L);
        coach.start();net.runelite.api.WallObject door=mock(net.runelite.api.WallObject.class);
        when(door.getId()).thenReturn(51998);when(door.getWorldView()).thenReturn(view);
        when(door.getWorldLocation()).thenReturn(VarlamoreThievingState.House.LAVINIA.door);
        when(door.getCanvasTextLocation(any(),anyString(),anyInt())).thenReturn(new Point(250,75));
        net.runelite.api.events.WallObjectSpawned spawned=new net.runelite.api.events.WallObjectSpawned();spawned.setWallObject(door);coach.onWallObjectSpawned(spawned);coach.onGameTick();
        assertEquals(VarlamoreThievingState.House.LAVINIA,coach.reading().availableHouse);
        HudStyle.context(false,false,0,0);
        try
        {
            for(int anchorY:new int[]{12,260})for(HudPreferences.Visibility visibility:new HudPreferences.Visibility[]{HudPreferences.Visibility.COMBAT,HudPreferences.Visibility.ALWAYS})
            {
                when(door.getCanvasLocation()).thenReturn(new Point(250,anchorY));
                encoded[0]=HudPreferences.encode(java.util.Map.of("thieving",new HudPreferences.Style(100,12,90,HudStyle.GOLD,HudStyle.WHITE,new Color(12,13,16),visibility,HudPreferences.Frame.ROUNDED,HudPreferences.Values.CURRENT_MAX)));
                BufferedImage image=new BufferedImage(500,330,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
                try {coach.scene().render(graphics);}finally {graphics.dispose();}
                int visiblePixels=brightPixels(image,new Rectangle(0,0,500,330));
                if(visibility==HudPreferences.Visibility.COMBAT)assertEquals("A hidden card may not reappear as its fallback badge",0,visiblePixels);
                else assertTrue("The same native door must render when the chosen HUD visibility allows it",visiblePixels>35);
            }
        }
        finally {coach.stop();HudStyle.context(false,false,0,0);}
    }

    @Test public void rendersLabelledDoorCardPreviewWithKnownAndUnknownOwnerTiming() throws Exception
    {
        BufferedImage image=new BufferedImage(900,520,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();
        try
        {
            graphics.setColor(new Color(16,18,22));graphics.fillRect(0,0,image.getWidth(),image.getHeight());
            graphics.setColor(HudStyle.GOLD);graphics.setFont(HudStyle.label(20));graphics.drawString("RUNEWARDEN · ACCESSIBLE HOUSE",30,37);
            graphics.setColor(HudStyle.MUTED);graphics.setFont(HudStyle.body(12));graphics.drawString("VISUAL QA FIXTURE · mocked projected door anchors",30,61);
            drawDoor(graphics,240,300);drawDoor(graphics,650,420);
            paint(graphics,new Point(240,300),900,520,1,"Lavinia",ownerTimer(),100_000);
            paint(graphics,new Point(650,420),900,520,1.35,"Victor",null,100_000);
            graphics.setColor(HudStyle.MUTED);graphics.setFont(HudStyle.body(12));
            graphics.drawString("Known owner-return window · 100% HUD scale",65,345);
            graphics.drawString("Departure not observed · 135% HUD scale",510,464);
        }
        finally {graphics.dispose();}
        File file=new File("build/previews/varlamore-door-card.png");assertTrue(file.getParentFile().isDirectory()||file.getParentFile().mkdirs());
        assertTrue(ImageIO.write(image,"png",file));
    }

    private static void paint(Graphics2D graphics,Point anchor,int width,int height,double scale,String house,VarlamoreThievingState.Window timer,long now)
    {VarlamoreThievingSceneOverlay.paintDoorCard(graphics,anchor,width,height,scale,house,"ACCESSIBLE",timer,now,STYLE);}
    private static VarlamoreThievingState.Window ownerTimer()
    {return new VarlamoreThievingState.Window("Owner return",0,180_000,190_000);}
    private static boolean paintedNear(BufferedImage image,int x,int y,int radius)
    {
        for(int py=Math.max(0,y-radius);py<=Math.min(image.getHeight()-1,y+radius);py++)
            for(int px=Math.max(0,x-radius);px<=Math.min(image.getWidth()-1,x+radius);px++)if((image.getRGB(px,py)>>>24)!=0)return true;
        return false;
    }
    private static int brightPixels(BufferedImage image,Rectangle area)
    {
        int count=0;for(int y=area.y;y<area.y+area.height;y++)for(int x=area.x;x<area.x+area.width;x++)
        {int pixel=image.getRGB(x,y);if((pixel>>>24)>0&&((pixel>>16)&255)>150&&((pixel>>8)&255)>140&&(pixel&255)>100)count++;}return count;
    }
    private static void drawDoor(Graphics2D graphics,int x,int y)
    {
        graphics.setColor(new Color(55,49,39));graphics.fillRect(x-19,y-8,38,45);
        graphics.setColor(new Color(113,94,60));graphics.drawRect(x-19,y-8,38,45);
    }
}
