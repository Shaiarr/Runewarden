/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SkillIconManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SkillProgressToolTest
{
    @Test public void everyNativeSkillHasActualXpLevelsAndANextLevelGoalIncludingSailing()
    {
        Fixture f=new Fixture();f.start();assertEquals(Skill.values().length,f.tool.reading().skills.size());assertEquals(Experience.getXpForLevel(50),f.row(Skill.AGILITY).xp);assertEquals(50,f.row(Skill.AGILITY).level);assertEquals(Experience.getXpForLevel(51),f.row(Skill.AGILITY).target);assertNotNull(f.row(Skill.SAILING));assertEquals(0,f.row(Skill.AGILITY).gained);assertEquals(-1,f.row(Skill.AGILITY).eta);
    }
    @Test public void positiveNativeGainsUpdateSessionAndHudWhileBoostOnlyChangesDoNot()
    {
        Fixture f=new Fixture();f.start();f.gain(Skill.MINING,500);assertEquals(500,f.row(Skill.MINING).gained);assertEquals(Skill.MINING,f.tool.reading().hud.get(0).skill);
        f.tool.onStatChanged(new StatChanged(Skill.MINING,f.xp.get(Skill.MINING),1,8));assertEquals(500,f.row(Skill.MINING).gained);assertEquals(8,f.row(Skill.MINING).boosted);
        for(int i=0;i<10;i++)f.tick();assertTrue(f.row(Skill.MINING).rate>0);assertTrue(f.row(Skill.MINING).eta>=0);
    }
    @Test public void aLevelGoalAndItsStartPointSurviveAProfileRoundTrip() throws Exception
    {
        Fixture f=new Fixture();f.start();JPanel panel=f.panel();SwingUtilities.invokeAndWait(()->{((JSpinner)field(panel,"Target level (up to virtual 126)")).setValue(75);button(panel,"Set level goal").doClick();});f.callbacks();assertEquals(Experience.getXpForLevel(75),f.row(Skill.AGILITY).target);assertTrue(f.row(Skill.AGILITY).explicit);assertEquals(0,f.row(Skill.AGILITY).fraction(),0);
        f.profile.set("rsprofile.b");assertTrue(f.tool.reading().skills.isEmpty());f.tick();assertFalse(f.row(Skill.AGILITY).explicit);f.profile.set("rsprofile.a");f.tick();assertEquals(Experience.getXpForLevel(75),f.row(Skill.AGILITY).target);
    }
    @Test public void clearingAGoalRestoresTheNativeNextLevelTarget() throws Exception
    {
        Fixture f=new Fixture();f.start();JPanel panel=f.panel();SwingUtilities.invokeAndWait(()->button(panel,"Set level goal").doClick());f.callbacks();assertTrue(f.row(Skill.AGILITY).explicit);SwingUtilities.invokeAndWait(()->button(panel,"Clear goal · use next level").doClick());f.callbacks();assertFalse(f.row(Skill.AGILITY).explicit);assertEquals(Experience.getXpForLevel(51),f.row(Skill.AGILITY).target);
    }
    @Test public void customXpGoalsAcceptCommasAndRejectValuesBeyondTheActualSkillCap() throws Exception
    {
        Fixture f=new Fixture();f.start();JPanel panel=f.panel();SwingUtilities.invokeAndWait(()->{((JTextField)field(panel,"Or target XP")).setText("200,000,000");button(panel,"Set XP goal").doClick();});f.callbacks();assertEquals(200000000,f.row(Skill.AGILITY).target);
        SwingUtilities.invokeAndWait(()->{((JTextField)field(panel,"Or target XP")).setText("200000001");button(panel,"Set XP goal").doClick();});f.callbacks();assertEquals(200000000,f.row(Skill.AGILITY).target);
    }
    @Test public void idleClockAndOfflineGainsAreNotTreatedAsNewTrainingXp()
    {
        Fixture f=new Fixture();f.start();f.gain(Skill.AGILITY,120);for(int i=0;i<110;i++)f.tick();long rate=f.row(Skill.AGILITY).rate;assertTrue(f.row(Skill.AGILITY).paused);for(int i=0;i<20;i++)f.tick();assertEquals(rate,f.row(Skill.AGILITY).rate);
        GameStateChanged state=new GameStateChanged();state.setGameState(GameState.HOPPING);f.tool.onGameStateChanged(state);f.xp.put(Skill.AGILITY,f.xp.get(Skill.AGILITY)+10000);f.tick();assertEquals(120,f.row(Skill.AGILITY).gained);assertTrue(f.row(Skill.AGILITY).paused);
    }
    @Test public void manualPauseAndResetDoNotAlterTheNativeXpObservation() throws Exception
    {
        Fixture f=new Fixture();f.start();f.gain(Skill.AGILITY,120);JPanel panel=f.panel();SwingUtilities.invokeAndWait(()->button(panel,"Pause / resume tracking").doClick());f.callbacks();f.gain(Skill.AGILITY,500);assertEquals(120,f.row(Skill.AGILITY).gained);assertEquals(Experience.getXpForLevel(50)+620,f.row(Skill.AGILITY).xp);
        SwingUtilities.invokeAndWait(()->button(panel,"Reset this skill session").doClick());f.callbacks();assertEquals(0,f.row(Skill.AGILITY).gained);assertEquals(Experience.getXpForLevel(50)+620,f.row(Skill.AGILITY).xp);
    }
    @Test public void latestTrainingOnlyChangesHudSelectionAndPreservesAnUnsubmittedGoalDraft() throws Exception
    {
        Fixture f=new Fixture();f.start();JPanel panel=f.panel();SwingUtilities.invokeAndWait(()->((JTextField)field(panel,"Or target XP")).setText("123456"));f.gain(Skill.RANGED,1000);SwingUtilities.invokeAndWait(()->{assertEquals(Skill.AGILITY,((JComboBox<?>)field(panel,"Skill")).getSelectedItem());assertEquals("123456",((JTextField)field(panel,"Or target XP")).getText());});assertEquals(Skill.RANGED,f.tool.reading().hud.get(0).skill);
    }
    @Test public void nativeSkillTabEntryOpensTheSelectedGoalWithoutReplacingTheNativeGuide() throws Exception
    {
        Fixture f=new Fixture();f.start();AtomicInteger opens=new AtomicInteger();f.tool.setOpenAction(opens::incrementAndGet);MenuEntry original=mock(MenuEntry.class);when(original.getOption()).thenReturn("View <col=ff981f>Attack</col> guide");when(original.getParam1()).thenReturn(InterfaceID.STATS<<16|4);
        f.tool.onMenuEntryAdded(new MenuEntryAdded(original));verify(f.entry).setOption("Runewarden goal");verify(f.entry).setType(MenuAction.RUNELITE);assertNotNull(f.menuClick.get());f.menuClick.get().accept(f.entry);SwingUtilities.invokeAndWait(()->{});assertEquals(1,opens.get());assertEquals(Skill.ATTACK,f.tool.reading().hud.get(0).skill);verify(original,never()).setOption(anyString());
    }
    @Test public void unrelatedMenusAndStaleCharacterCallbacksCannotOpenOrMutateGoals() throws Exception
    {
        Fixture f=new Fixture();f.start();MenuEntry other=mock(MenuEntry.class);when(other.getOption()).thenReturn("View <col=ff981f>Attack</col> guide");when(other.getParam1()).thenReturn(InterfaceID.STATS+1<<16);f.tool.onMenuEntryAdded(new MenuEntryAdded(other));verify(f.client,never()).createMenuEntry(anyInt());
        when(other.getParam1()).thenReturn(InterfaceID.STATS<<16);f.tool.onMenuEntryAdded(new MenuEntryAdded(other));AtomicInteger opens=new AtomicInteger();f.tool.setOpenAction(opens::incrementAndGet);f.profile.set("rsprofile.other");f.menuClick.get().accept(f.entry);SwingUtilities.invokeAndWait(()->{});assertEquals(0,opens.get());assertTrue(f.tool.reading().skills.isEmpty());
    }
    @Test public void futureGoalDataIsRetainedAndUnknownSkillGoalsAreRejected() throws Exception
    {
        Fixture f=new Fixture();String key=SkillProgressTool.GROUP+"/rsprofile.a/"+SkillProgressTool.KEY;String future="{\"version\":99,\"goals\":{}}";f.saved.put(key,future);f.start();JPanel panel=f.panel();SwingUtilities.invokeAndWait(()->button(panel,"Set level goal").doClick());f.callbacks();assertEquals(future,f.saved.get(key));
        SkillProgressTool.Saved invalid=new SkillProgressTool.Saved();invalid.goals.put("NOT_A_SKILL",new SkillProgressTool.Goal(100,0));assertFalse(invalid.valid());
    }
    @Test public void nativeIconHudAndAllVisibleControlsFitTheNarrowSidebar() throws Exception
    {
        Fixture f=new Fixture();f.start();BufferedImage image=new BufferedImage(500,500,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{java.awt.Dimension size=f.tool.hud().render(g);assertNotNull(size);assertEquals(268,size.width);assertTrue(size.height<200);verify(f.icons,atLeastOnce()).getSkillImage(Skill.AGILITY,true);}finally{g.dispose();}
        JPanel panel=f.panel();SwingUtilities.invokeAndWait(()->{panel.setSize(206,1600);layout(panel);assertCheckboxesFit(panel);});
        f.tool.stop();assertTrue(f.tool.reading().skills.isEmpty());
    }
    @Test public void stoppedQueuedCallbacksCannotRestoreAnOldCharacterView()
    {Fixture f=new Fixture();f.tool.start();f.tool.stop();f.callbacks();f.tool.onGameTick();assertTrue(f.tool.reading().skills.isEmpty());}
    @Test public void everySkillTabBindingMatchesItsActualNativeComponent() throws Exception
    {java.util.Set<Integer> unique=new java.util.HashSet<>();for(Skill skill:Skill.values()){int component=SkillTabProgressOverlay.component(skill);assertEquals(InterfaceID.Stats.class.getField(skill.name()).getInt(null),component);assertTrue(unique.add(component));}}
    @Test public void skillTabDecorationUsesVisibleNativeBoundsAndDisplaysTheMeasuredGoalTooltip()
    {
        Fixture f=new Fixture();f.start();f.gain(Skill.AGILITY,1000);net.runelite.api.widgets.Widget widget=mock(net.runelite.api.widgets.Widget.class);when(widget.getBounds()).thenReturn(new java.awt.Rectangle(10,10,60,30));when(f.client.getWidget(InterfaceID.Stats.AGILITY)).thenReturn(widget);when(f.client.getMouseCanvasPosition()).thenReturn(new Point(20,20));
        BufferedImage image=new BufferedImage(100,100,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{f.tool.skillTab().render(g);assertNotEquals(0,image.getRGB(14,37));}finally{g.dispose();}
        org.mockito.ArgumentCaptor<net.runelite.client.ui.overlay.tooltip.Tooltip> tooltip=org.mockito.ArgumentCaptor.forClass(net.runelite.client.ui.overlay.tooltip.Tooltip.class);verify(f.tooltips).add(tooltip.capture());assertTrue(tooltip.getValue().getText().contains("Agility · Level 50"));assertTrue(tooltip.getValue().getText().contains("Goal: Level 51"));
        when(widget.isHidden()).thenReturn(true);clearInvocations(f.tooltips);image=new BufferedImage(100,100,BufferedImage.TYPE_INT_ARGB);g=image.createGraphics();try{f.tool.skillTab().render(g);assertEquals(0,image.getRGB(14,37));verifyNoInteractions(f.tooltips);}finally{g.dispose();}
    }
    @Test public void disabledSkillTabDecorationsDoNotReadOrModifyNativeWidgets()
    {
        Fixture f=new Fixture();f.start();f.tool.preferences().tabBars=false;f.tool.preferences().tabTooltips=false;Graphics2D g=new BufferedImage(100,100,BufferedImage.TYPE_INT_ARGB).createGraphics();try{f.tool.skillTab().render(g);verify(f.client,never()).getWidget(anyInt());}finally{g.dispose();}
    }
    private static Component field(Container parent,String name)
    {for(Component child:parent.getComponents()){if(child instanceof javax.swing.JComponent&&name.equals(((javax.swing.JComponent)child).getAccessibleContext().getAccessibleName())&&!(child instanceof GuideText))return child;if(child instanceof Container){Component nested=field((Container)child,name);if(nested!=null)return nested;}}return null;}
    private static JButton button(Container parent,String text)
    {for(Component child:parent.getComponents()){if(child instanceof JButton&&text.equals(((JButton)child).getText()))return (JButton)child;if(child instanceof Container){JButton nested=button((Container)child,text);if(nested!=null)return nested;}}return null;}
    private static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container)layout((Container)child);}
    private static void assertCheckboxesFit(Container parent){for(Component child:parent.getComponents()){if(child instanceof JCheckBox)assertTrue(((JCheckBox)child).getPreferredSize().width<=206);if(child instanceof Container)assertCheckboxesFit((Container)child);}}
    static final class Fixture
    {
        final Client client=mock(Client.class);final ClientThread thread=mock(ClientThread.class);final ConfigManager config=mock(ConfigManager.class);final SkillIconManager icons=mock(SkillIconManager.class);final net.runelite.client.ui.overlay.tooltip.TooltipManager tooltips=mock(net.runelite.client.ui.overlay.tooltip.TooltipManager.class);final AtomicReference<String> profile=new AtomicReference<>("rsprofile.a");final AtomicInteger tick=new AtomicInteger(10);final EnumMap<Skill,Integer> xp=new EnumMap<>(Skill.class);final List<Runnable> callbacks=new ArrayList<>();final Map<String,String> saved=new HashMap<>();final MenuEntry entry=mock(MenuEntry.class,RETURNS_SELF);final AtomicReference<Consumer<MenuEntry>> menuClick=new AtomicReference<>();final SkillProgressTool tool;
        @SuppressWarnings("unchecked") Fixture()
        {
            for(Skill skill:Skill.values())xp.put(skill,skill==Skill.AGILITY?Experience.getXpForLevel(50):0);
            when(config.getRSProfileKey()).thenAnswer(call->profile.get());when(config.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(call->saved.get(call.getArgument(0)+"/"+call.getArgument(1)+"/"+call.getArgument(2)));when(config.getConfiguration(anyString(),anyString())).thenAnswer(call->saved.get(call.getArgument(0)+"/"+call.getArgument(1)));
            doAnswer(call->{saved.put(call.getArgument(0)+"/"+call.getArgument(1)+"/"+call.getArgument(2),String.valueOf((Object)call.getArgument(3)));return null;}).when(config).setConfiguration(anyString(),anyString(),anyString(),any());doAnswer(call->{saved.put(call.getArgument(0)+"/"+call.getArgument(1),String.valueOf((Object)call.getArgument(2)));return null;}).when(config).setConfiguration(anyString(),anyString(),any());
            doAnswer(call->{callbacks.add(call.getArgument(0));return null;}).when(thread).invoke(any(Runnable.class));when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));when(client.getTickCount()).thenAnswer(call->tick.get());when(client.getSkillExperience(any(Skill.class))).thenAnswer(call->xp.get(call.getArgument(0)));when(client.getRealSkillLevel(any(Skill.class))).thenAnswer(call->Math.min(99,Experience.getLevelForXp(xp.get(call.getArgument(0)))));when(client.getBoostedSkillLevel(any(Skill.class))).thenAnswer(call->Math.min(99,Experience.getLevelForXp(xp.get(call.getArgument(0)))));
            when(client.createMenuEntry(-1)).thenReturn(entry);doAnswer(call->{menuClick.set(call.getArgument(0));return entry;}).when(entry).onClick(any(Consumer.class));
            when(icons.getSkillImage(any(Skill.class),eq(true))).thenReturn(new BufferedImage(12,12,BufferedImage.TYPE_INT_ARGB));tool=new SkillProgressTool(client,thread,config,new RunewardenDataStore(config,new Gson()),icons,new RunewardenConfig(){},tooltips);
        }
        void start(){tool.start();tool.onGameTick();}
        void tick(){tick.incrementAndGet();tool.onGameTick();}
        void gain(Skill skill,int amount){xp.put(skill,xp.get(skill)+amount);tool.onStatChanged(new StatChanged(skill,xp.get(skill),Math.min(99,Experience.getLevelForXp(xp.get(skill))),Math.min(99,Experience.getLevelForXp(xp.get(skill)))));}
        SkillProgressTool.Row row(Skill skill){return tool.reading().skills.get(skill);}
        JPanel panel() throws Exception{AtomicReference<JPanel> result=new AtomicReference<>();SwingUtilities.invokeAndWait(()->result.set(tool.createPanel()));SwingUtilities.invokeAndWait(()->{});return result.get();}
        void callbacks(){List<Runnable> pending=new ArrayList<>(callbacks);callbacks.clear();for(Runnable callback:pending)callback.run();}
    }
}
