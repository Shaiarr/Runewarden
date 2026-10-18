/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class TrainingProgressLayoutTest
{
    @Test public void longActivityAndExpandedChecksFitNativeSidebar() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();
            f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"long","Long named training activity with explicit supply requirements and a distant level goal",null,null,Skill.WOODCUTTING,13_034_431));
            f.progress.start();f.tick(1);JPanel panel=f.progress.createPanel();panel.setSize(206,8000);layout(panel);fits(panel);
            for(Component child:children(panel))if(child instanceof JButton&&((JButton)child).getText().contains("AFK checks")){((JButton)child).doClick();break;}
            layout(panel);fits(panel);
            PluginPanel host=new PluginPanel(){};host.setLayout(new java.awt.BorderLayout());host.setBorder(javax.swing.BorderFactory.createEmptyBorder(8,8,8,8));host.add(panel);
            JComponent wrapped=host.getWrappedPanel();wrapped.setSize(242,8000);layout(wrapped);fits(panel);assertTrue(panel.getWidth()>=190);
        });
    }
    @Test public void trainingWidgetUsesRegisteredScaleAndLock() throws Exception
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"test","Woodcutting",null,null,Skill.WOODCUTTING,10000));
        RunewardenConfig config=mock(RunewardenConfig.class);HudPreferences.Style style=new HudPreferences.Style(150,12,75,java.awt.Color.YELLOW,java.awt.Color.WHITE,java.awt.Color.BLACK,HudPreferences.Visibility.ALWAYS,HudPreferences.Frame.SQUARE,HudPreferences.Values.CURRENT_MAX);
        when(config.hudWidgetSettings()).thenReturn(HudPreferences.encode(Collections.singletonMap("training",style)));
        TrainingProgressTool tool=new TrainingProgressTool(f.client,mock(ClientThread.class),f.config,f.store,f.session,config);tool.start();tool.onGameTick();
        BufferedImage image=new BufferedImage(600,600,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
        try{assertEquals(new Dimension(402,306),tool.overlay().render(graphics));tool.setHudLocked(true);assertFalse(tool.overlay().isMovable());tool.setHudLocked(false);assertTrue(tool.overlay().isMovable());}
        finally{graphics.dispose();}
    }
    @Test public void actualXpChangesFillTheRenderedTrainingBar() throws Exception
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();
        f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"bar","Woodcutting",null,null,Skill.WOODCUTTING,2000));f.progress.start();f.tick(1);
        f.xp.set(1500);f.progress.onStatChanged(new net.runelite.api.events.StatChanged(Skill.WOODCUTTING,1500,1,1));f.tick(1);
        assertEquals(0.5,f.progress.reading().progressFraction(),0.00001);
        BufferedImage image=new BufferedImage(400,300,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try{assertNotNull(f.progress.overlay().render(g));assertNotEquals(image.getRGB(30,170),image.getRGB(240,170));}finally{g.dispose();}
        java.io.File output=new java.io.File("build/reports/training-progress-bar.png");output.getParentFile().mkdirs();javax.imageio.ImageIO.write(image,"png",output);
    }
    @Test public void selectedRecipeXpBarUsesItsNativeOutputImageAndDoesNotFetchItDuringRender()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();
        f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"fletching:magic_longbow_u","Carve Magic longbows",null,"fletching:magic_longbow_u",Skill.FLETCHING,2000));
        net.runelite.client.game.ItemManager items=mock(net.runelite.client.game.ItemManager.class);
        net.runelite.client.util.AsyncBufferedImage icon=new net.runelite.client.util.AsyncBufferedImage(mock(ClientThread.class),36,32,BufferedImage.TYPE_INT_ARGB);
        Graphics2D iconGraphics=icon.createGraphics();iconGraphics.setColor(java.awt.Color.CYAN);iconGraphics.fillRect(0,0,36,32);iconGraphics.dispose();icon.loaded();when(items.getImage(70)).thenReturn(icon);
        TrainingProgressTool tool=new TrainingProgressTool(f.client,mock(ClientThread.class),f.config,f.store,f.session,mock(RunewardenConfig.class),null,null,items,null);tool.start();tool.onGameTick();
        assertEquals(70,tool.reading().iconItemId);assertSame(icon,tool.reading().icon);
        BufferedImage image=new BufferedImage(400,300,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try{for(int i=0;i<10;i++)assertNotNull(tool.overlay().render(g));}finally{g.dispose();}
        assertEquals(java.awt.Color.CYAN.getRGB(),image.getRGB(25,36));verify(items,times(1)).getImage(70);
    }
    private static java.util.List<Component> children(Container root)
    {java.util.List<Component> result=new java.util.ArrayList<>();for(Component c:root.getComponents()){result.add(c);if(c instanceof Container)result.addAll(children((Container)c));}return result;}
    private static void layout(Container root){for(int i=0;i<8;i++)deep(root);}
    private static void deep(Container root){root.invalidate();root.doLayout();for(Component c:root.getComponents())if(c instanceof Container)deep((Container)c);}
    private static void fits(Container root)
    {
        for(Component c:children(root))
        {
            boolean visible=true;for(Component p=c;p!=root;p=p.getParent())if(p==null||!p.isVisible()){visible=false;break;}
            if(!visible||c.getParent().getWidth()<=0||!(c instanceof JButton||c instanceof GuideText||c instanceof javax.swing.JSlider))continue;
            assertTrue(c.getClass().getSimpleName()+" starts inside parent",c.getX()>=0);assertTrue(c.getClass().getSimpleName()+" stays inside parent",c.getX()+c.getWidth()<=c.getParent().getWidth());
            if(c instanceof GuideText)assertTrue("Wrapped text keeps full height",c.getHeight()>=c.getPreferredSize().height);
        }
    }
}
