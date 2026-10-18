/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SlayerFullWorkflowTest
{
    @Test public void selectedCreatureAndSuppliesProduceImmutableProfileBoundStartContext() throws Exception
    {
        Fixture f=new Fixture();f.tick();SlayerPlanningTool.Snapshot state=f.tool.reading();SlayerPlanningTool.Rule rule=f.rule();rule.areaConfirmed=true;
        assertTrue(f.tool.saveRule(state,"Abyssal demons",rule));SlayerPlanningTool.Prepared prepared=f.tool.prepare(state,state.tasks.get(0));
        assertNotNull(prepared);assertEquals("Catacombs of Kourend",prepared.area);assertEquals(140,prepared.remaining);assertEquals(Set.of(rule.npcId),prepared.npcIds);assertEquals(Set.of(560,555),prepared.itemIds);
        try{prepared.itemIds.add(1);fail();}catch(UnsupportedOperationException expected){}
        f.profile.set("rsprofile.other");assertNull(f.tool.prepare(state,state.tasks.get(0)));
    }
    @Test public void aBossAlternativeNeedsAnExplicitAssignedAreaConfirmation() throws Exception
    {
        Fixture f=new Fixture();f.tick();SlayerPlanningTool.Snapshot state=f.tool.reading();SlayerPlanningTool.Rule rule=f.rule();
        assertTrue(f.tool.saveRule(state,"Abyssal demons",rule));assertNull(f.tool.prepare(state,state.tasks.get(0)));
        rule.areaConfirmed=true;assertTrue(f.tool.saveRule(state,"Abyssal demons",rule));assertNotNull(f.tool.prepare(state,state.tasks.get(0)));
        f.tool.setAssignment(SlayerCoachTool.State.empty("No active assignment"));assertNull(f.tool.prepare(state,state.tasks.get(0)));
    }
    @Test public void unrelatedCreatureIdsCannotStartAnotherTask() throws Exception
    {
        Fixture f=new Fixture();f.tick();SlayerPlanningTool.Snapshot state=f.tool.reading();SlayerPlanningTool.Rule rule=f.rule();rule.npcId=999999;rule.areaConfirmed=true;
        assertTrue(f.tool.saveRule(state,"Abyssal demons",rule));assertNull(f.tool.prepare(state,state.tasks.get(0)));
    }
    @Test public void ratesAndRepeatedZeroPointRewardsPersistWithoutChangingOtherCharacters() throws Exception
    {
        Fixture f=new Fixture();f.tick();SlayerPlanningTool.Rule rule=f.rule();rule.killsPerHour=175;rule.xpKnown=true;rule.xpPerKill=150;rule.gpKnown=true;rule.netGpPerKill=-120;rule.plannedCount=150;
        assertTrue(f.tool.saveRule(f.tool.reading(),"Abyssal demons",rule));SlayerPlanningTool.Settings settings=new SlayerPlanningTool.Settings();settings.rewards=new int[]{0,0,0,0,0,0};settings.combatChecking="DISABLED";assertTrue(f.tool.saveSettings(f.tool.reading(),settings));
        f.tool.clear();f.tick();assertEquals(175,f.tool.rule("Abyssal demons").killsPerHour,0);assertArrayEquals(new int[]{0,0,0,0,0,0},f.tool.settings().rewards);
        f.profile.set("rsprofile.other");f.tick();assertEquals(0,f.tool.rule("Abyssal demons").killsPerHour,0);assertNull(f.tool.settings().rewards);
    }
    @Test public void populatedEditorPagesFitTheNativeSidebarAndDoNotReadClientOnSwing() throws Exception
    {
        Fixture f=new Fixture();f.tick();SlayerPlanningTool.Rule rule=f.rule();rule.areaConfirmed=true;assertTrue(f.tool.saveRule(f.tool.reading(),"Abyssal demons",rule));
        SwingUtilities.invokeAndWait(()->{
            try{
                net.runelite.client.ui.laf.RuneLiteLAF.setup();JPanel panel=f.tool.createPanel();set(f.tool,"selected","abyssaldemons");
                Method render=SlayerPlanningTool.class.getDeclaredMethod("render");render.setAccessible(true);
                for(int page=0;page<5;page++){
                    clearInvocations(f.client); if (page >= 3) { set(f.tool,"selected",null); Field modeField=SlayerPlanningTool.class.getDeclaredField("mode");modeField.setAccessible(true);((javax.swing.JComboBox<?>)modeField.get(f.tool)).setSelectedIndex(page == 3 ? 1 : 4); } else set(f.tool,"detailPage",page);render.invoke(f.tool);panel.setSize(206,2400);for(int pass=0;pass<3;pass++)layout(panel);panel.setSize(206,panel.getPreferredSize().height);for(int pass=0;pass<3;pass++)layout(panel);assertFits(panel);verifyNoInteractions(f.client);
                    BufferedImage image=new BufferedImage(206,panel.getHeight(),BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D graphics=image.createGraphics();panel.printAll(graphics);graphics.dispose();File output=new File("build/reports/slayer-planning-page-"+page+"-206px.png");output.getParentFile().mkdirs();ImageIO.write(image,"png",output);
                }
            }catch(Exception error){throw new AssertionError(error);}
        });
    }
    @Test public void changingMethodClearsTheActiveRateButRetainsComparableSamples() throws Exception
    {
        Fixture f=new Fixture();f.tick();SlayerPlanningTool.Rule rule=f.rule();rule.method="old-melee";rule.killsPerHour=200;rule.xpKnown=true;rule.xpPerKill=150;
        SlayerPlanningTool.Measurement sample=new SlayerPlanningTool.Measurement();sample.method=rule.method;sample.credits=20;sample.minutes=6;rule.measurements.add(sample);
        assertTrue(f.tool.saveRule(f.tool.reading(),"Abyssal demons",rule));
        SwingUtilities.invokeAndWait(()->{try { JPanel panel=f.tool.createPanel();set(f.tool,"selected","abyssaldemons");set(f.tool,"detailPage",1);Method render=SlayerPlanningTool.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(f.tool); findButton(panel,"Use this method").doClick(); }catch(Exception error){throw new AssertionError(error);}});
        SlayerPlanningTool.Rule changed=f.tool.rule("Abyssal demons");assertEquals(0,changed.killsPerHour,0);assertFalse(changed.xpKnown);assertEquals(1,changed.measurements.size());assertEquals("old-melee",changed.measurements.get(0).method);
    }
    private static javax.swing.JButton findButton(Container parent,String text)
    { for(Component child:parent.getComponents()){if(child instanceof javax.swing.JButton && text.equals(((javax.swing.JButton)child).getText()))return(javax.swing.JButton)child;if(child instanceof Container){javax.swing.JButton found=findButton((Container)child,text);if(found!=null)return found;}}return null; }
    private static void layout(Container parent){parent.invalidate();parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container&&child.isVisible())layout((Container)child);}
    private static void assertFits(Container parent)
    {
        for(Component child:parent.getComponents())if(child.isVisible()){
            assertTrue(child.getClass().getName()+" horizontal clipping: "+child.getBounds()+" in "+parent.getWidth(),child.getX()+child.getWidth()<=parent.getWidth()-parent.getInsets().right+1);
            assertTrue(child.getClass().getName()+" vertical clipping",child.getY()+child.getHeight()<=parent.getHeight()-parent.getInsets().bottom+1);
            if(child instanceof Container && !(child instanceof javax.swing.JComboBox) && !(child instanceof javax.swing.JTextField))assertFits((Container)child);
        }
    }
    private static void set(Object target,String name,Object value)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    private static final class Fixture
    {
        final Gson gson=new Gson();final Client client=mock(Client.class);final ConfigManager config=mock(ConfigManager.class);final AtomicReference<String> profile=new AtomicReference<>("rsprofile.slayer");final Map<String,String> data=new HashMap<>();
        final SlayerTaskCatalog tasks=new SlayerTaskCatalog(gson);final MobKnowledgeCatalog mobs=new MobKnowledgeCatalog(gson);final SlayerPlanningTool tool;
        Fixture(){
            when(config.getRSProfileKey()).thenAnswer(call->profile.get());when(client.getGameState()).thenReturn(GameState.LOGGED_IN);Player player=mock(Player.class);when(client.getLocalPlayer()).thenReturn(player);when(player.getCombatLevel()).thenReturn(126);
            when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);when(client.getVarbitValue(VarbitID.SLAYER_MASTER)).thenReturn(5);
            when(config.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(call->data.get(call.getArgument(0)+":"+call.getArgument(1)+":"+call.getArgument(2)));
            doAnswer(call->{data.put(call.getArgument(0)+":"+call.getArgument(1)+":"+call.getArgument(2),call.getArgument(3));return null;}).when(config).setConfiguration(anyString(),anyString(),anyString(),anyString());
            when(client.getDBRowsByValue(DBTableID.SlayerMasterTask.ID,DBTableID.SlayerMasterTask.COL_MASTER_ID,0,5)).thenReturn(List.of(100));
            nativeField(100,DBTableID.SlayerMasterTask.COL_TASK,200);nativeField(100,DBTableID.SlayerMasterTask.COL_WEIGHT,8);nativeField(100,DBTableID.SlayerMasterTask.COL_MIN_AMOUNT,120);nativeField(100,DBTableID.SlayerMasterTask.COL_MAX_AMOUNT,170);nativeField(200,DBTableID.SlayerTask.COL_NAME_UPPERCASE,"Abyssal demons");nativeField(200,DBTableID.SlayerTask.COL_ID,38);
            GuideCatalog guides=new GuideCatalog(gson); ItemKnowledgeCatalog itemKnowledge = new ItemKnowledgeCatalog(gson,guides); itemKnowledge.load(); tool=new SlayerPlanningTool(client,config,new RunewardenDataStore(config,gson),guides,tasks,mobs,new SlayerMethodCatalog(gson),null,itemKnowledge);tool.start();
        }
        SlayerPlanningTool.Rule rule(){SlayerPlanningTool.Rule rule=new SlayerPlanningTool.Rule();MobKnowledgeCatalog.Entry creature=mobs.matching(tasks.task("Abyssal demons").hints()).get(0);rule.npcId=creature.id;rule.creature=creature.name;rule.area="Another area";rule.access="YES";rule.supplies=List.of(560,555);return rule;}
        void nativeField(int row,int column,Object value){when(client.getDBTableField(row,column,0)).thenReturn(new Object[]{value});}
        void tick()throws Exception{set(tool,"visible",true);for(int index=0;index<7;index++)tool.onGameTick();tool.setAssignment(new SlayerCoachTool.State("Abyssal demons","Catacombs of Kourend",140,160,250,20,5,List.of(),List.of()));SwingUtilities.invokeAndWait(()->{});}
    }
}
