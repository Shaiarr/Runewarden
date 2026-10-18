/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Component;
import java.awt.Container;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CommandCenterToolTest
{
    @Test public void disabledCommandCenterStopsCollectingAndExposesNoPriorCards() throws Exception
    {
        ConfigManager config=mock(ConfigManager.class);when(config.getRSProfileKey()).thenReturn("rsprofile-a");AtomicInteger reads=new AtomicInteger();
        CommandCenterTool tool=new CommandCenterTool(()->{reads.incrementAndGet();return reading("rsprofile-a");},config);
        tool.onGameTick();assertEquals(0,reads.get());tool.start();tool.onGameTick();assertEquals(1,reads.get());assertEquals(2,tool.cards().size());tool.stop();tool.onGameTick();assertEquals(1,reads.get());assertTrue(tool.cards().isEmpty());
    }
    @Test public void aProfileChangeDuringCollectionCannotPublishMixedAccountCards()
    {
        ConfigManager config=mock(ConfigManager.class);AtomicReference<String> owner=new AtomicReference<>("rsprofile-a");when(config.getRSProfileKey()).thenAnswer(e->owner.get());
        CommandCenterTool tool=new CommandCenterTool(()->{owner.set("rsprofile-b");return reading("rsprofile-a");},config);tool.start();tool.onGameTick();assertTrue(tool.cards().isEmpty());
    }
    @Test public void oldButtonsCannotOpenNewCharactersContextOrSurviveRestart() throws Exception
    {
        ConfigManager config=mock(ConfigManager.class);AtomicReference<String> owner=new AtomicReference<>("rsprofile-a");when(config.getRSProfileKey()).thenAnswer(e->owner.get());AtomicInteger opens=new AtomicInteger();
        CommandCenterTool tool=new CommandCenterTool(()->reading(owner.get()),config);tool.setActions(s->opens.incrementAndGet(),o->opens.incrementAndGet());tool.start();tool.onGameTick();
        JButton[] old={null};SwingUtilities.invokeAndWait(()->{JPanel panel=tool.createPanel();old[0]=button(panel,"Open Achievement diaries");assertNotNull(old[0]);});
        owner.set("rsprofile-b");SwingUtilities.invokeAndWait(()->old[0].doClick(0));assertEquals(0,opens.get());
        owner.set("rsprofile-a");tool.stop();tool.start();tool.onGameTick();SwingUtilities.invokeAndWait(()->old[0].doClick(0));assertEquals(0,opens.get());
    }
    @Test public void populatedCardsAndLongModuleButtonsFit206Pixels() throws Exception
    {
        ConfigManager config=mock(ConfigManager.class);when(config.getRSProfileKey()).thenReturn("rsprofile-a");CommandCenterTool tool=new CommandCenterTool(()->reading("rsprofile-a"),config);tool.start();tool.onGameTick();
        SwingUtilities.invokeAndWait(()->{JPanel panel=tool.createPanel();for(int i=0;i<5;i++){panel.setSize(206,panel.getPreferredSize().height);layout(panel);}bounds(panel);});tool.stop();
    }
    @Test public void readinessDistinguishesPositiveChecklistAndUnobservedOrWrongSpellbook()
    {
        OwnershipLedger.View normal=new OwnershipLedger.View("rsprofile-a",Map.of(),0,Map.of(),false,false,Map.of());
        LoadoutPlan items=new LoadoutPlan("items","Items",List.of(),null);
        assertEquals("✓ Items ready",CommandCenterTool.readinessDetail(items,LoadoutReadiness.check(items,normal)));
        LoadoutPlan ancient=new LoadoutPlan("ancient","Ancients",List.of(),1);
        assertEquals("Change spellbook",CommandCenterTool.readinessDetail(ancient,LoadoutReadiness.check(ancient,normal)));
        OwnershipLedger.View unknown=new OwnershipLedger.View("rsprofile-a",Map.of(),null,Map.of(),false,false,Map.of());
        assertEquals("Spellbook not observed",CommandCenterTool.readinessDetail(ancient,LoadoutReadiness.check(ancient,unknown)));
        LoadoutPlan checklist=new LoadoutPlan("check","Checks",List.of(),null,List.of(new LoadoutPlan.DepartureCheck("shield","Shield charged",LoadoutPlan.CheckKind.MANUAL,Set.of(),0,null)));
        LoadoutReadiness.CheckFacts confirmed=new LoadoutReadiness.CheckFacts(Map.of(),Map.of(),Set.of(),Set.of(),Set.of("shield"));
        assertEquals("✓ Checklist ready",CommandCenterTool.readinessDetail(checklist,LoadoutReadiness.check(checklist,normal,confirmed)));
    }
    private static CommandCenterTool.Reading reading(String profile)
    {return new CommandCenterTool.Reading(profile,List.of(new CommandCenterTool.Card("Long named quest preparation opportunity","A detailed account-specific preparation description that wraps at the narrow RuneLite sidebar width","Achievement diaries"),new CommandCenterTool.Card("Ready for the run","✓ Checklist ready","Loadouts")),List.of());}
    private static JButton button(Container parent,String text){for(Component c:parent.getComponents()){if(c instanceof JButton&&((JButton)c).getText().contains(text))return (JButton)c;if(c instanceof Container){JButton b=button((Container)c,text);if(b!=null)return b;}}return null;}
    private static void layout(Container parent){parent.doLayout();for(Component c:parent.getComponents())if(c instanceof Container)layout((Container)c);}
    private static void bounds(Container parent){for(Component c:parent.getComponents()){if(!c.isVisible())continue;assertTrue(c.getX()+c.getWidth()<=parent.getWidth());assertTrue(c.getY()+c.getHeight()<=parent.getHeight());if(c instanceof Container)bounds((Container)c);}}
}
