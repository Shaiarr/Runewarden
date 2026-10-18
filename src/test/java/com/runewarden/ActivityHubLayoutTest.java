/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class ActivityHubLayoutTest
{
    @Test public void populatedUniversalRunAndHistoryStayReadableAt206pxWithoutNativeReadsOnSwing() throws Exception
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.MINIGAME,"winter","Wintertodt with a long name and several secondary skill rewards","minigame:wintertodt",null,null,0));
        String id=f.session.snapshot().runId;f.session.observeXp(id,Skill.FIREMAKING,18240);f.session.observeXp(id,Skill.WOODCUTTING,890);f.session.observeXp(id,Skill.FLETCHING,680);
        f.session.reportLoot(id,128_000,12_500,4,1,Map.of("Wintertodt supply crate",6L));f.session.observeOutcome("rounds","Completed observed rounds",6);
        f.session.reachMilestone("level","Firemaking level 75 reached","Goals");f.tick(12);f.session.finish();f.session.startActivity(ActivityHubTest.context(SharedActivitySession.Kind.FARMING,"route"));
        ActivityHubTool hub=new ActivityHubTool(f.client,mock(ClientThread.class),f.config,f.session);hub.start();clearInvocations(f.client);
        SwingUtilities.invokeAndWait(()->{
            JPanel panel=hub.createPanel();panel.setSize(206,7000);layout(panel);
            for(Component c:children(panel))if(c instanceof JButton&&((JButton)c).getText().contains("Wintertodt")){((JButton)c).doClick();break;}
            for(Component c:children(panel))if(c instanceof JButton&&((JButton)c).getText().contains("Run breakdown")){((JButton)c).doClick();break;}
            layout(panel);for(Component c:children(panel))if(c.isVisible()&&c.getParent().getWidth()>0&&(c instanceof JButton||c instanceof GuideText||c instanceof JCheckBox))
            {assertTrue(c.getClass()+" starts inside",c.getX()>=0);assertTrue(c.getClass()+" stays inside",c.getX()+c.getWidth()<=c.getParent().getWidth());}
            int height=Math.min(2400,panel.getPreferredSize().height);panel.setSize(206,height);layout(panel);BufferedImage image=new BufferedImage(206,height,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();panel.paint(graphics);graphics.dispose();
            try{Path target=Path.of("build/reports/activity-hub-206px.png");Files.createDirectories(target.getParent());javax.imageio.ImageIO.write(image,"png",target.toFile());}catch(Exception failure){throw new AssertionError(failure);}
        });
        verifyNoInteractions(f.client);
    }
    private static List<Component> children(Container root){List<Component> result=new ArrayList<>();for(Component c:root.getComponents()){result.add(c);if(c instanceof Container)result.addAll(children((Container)c));}return result;}
    private static void layout(Container root){for(int i=0;i<5;i++)deep(root);}
    private static void deep(Container root){root.invalidate();root.doLayout();for(Component c:root.getComponents())if(c instanceof Container)deep((Container)c);}
}
