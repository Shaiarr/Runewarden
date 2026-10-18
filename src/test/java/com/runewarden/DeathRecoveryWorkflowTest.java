/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Item;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DeathRecoveryWorkflowTest
{
    @Test public void carryingAnItemBeforeDeathDoesNotDeclareItLostOrMakeItAReturnKit() throws Exception
    {
        Fixture f=new Fixture();f.selected.set(kit());f.death();
        DeathRecoveryTool.DeathView death=f.tool().snapshot().deaths.get(0);assertNull(death.kit);assertNull(death.recoveryKind);assertEquals(2,death.carried.size());
        f.click("Use selected loadout");f.run();death=f.tool().snapshot().deaths.get(0);assertNotNull(death.kit);assertEquals("Return safely",death.kit.plan.name);
        assertEquals(Set.of(8007),death.kit.missingItemIds);assertFalse(death.kit.plan.requirements.stream().anyMatch(line->line.alternatives.contains(4151)));
    }
    @Test public void returnKitIsACopyAndSurvivesRestartIndependentlyOfSelectedLoadout() throws Exception
    {
        Fixture f=new Fixture();f.selected.set(kit());f.death();f.click("Use selected loadout");f.run();f.selected.set(new LoadoutPlan("other","Other plan",List.of(),null));
        f.tool().stop();f.base.warm();assertEquals("Return safely",f.tool().snapshot().deaths.get(0).kit.plan.name);
        assertEquals(Set.of(8007),f.tool().snapshot().deaths.get(0).kit.missingItemIds);
        try{f.tool().snapshot().deaths.get(0).kit.missingItemIds.add(2);fail();}catch(UnsupportedOperationException expected){}
    }
    @Test public void bankFocusOnlyIncludesReturnItemsNotAlreadyPhysicallyCarried() throws Exception
    {
        Fixture f=new Fixture();f.selected.set(kit());f.death();f.click("Use selected loadout");f.run();f.click("Bank missing kit items");f.run();assertEquals(Set.of(8007),f.bank.get());
        f.base.container(InventoryID.INV,new Item(385,12),new Item(8007,1));f.base.tick();f.click("Bank missing kit items");f.run();assertEquals(Set.of(),f.bank.get());
    }
    @Test public void gearInInventoryNeedsEquippingButDoesNotCreateFalseBankDemand() throws Exception
    {
        Fixture f=new Fixture();f.base.container(InventoryID.INV,new Item(4151,1));f.base.container(InventoryID.WORN);f.selected.set(new LoadoutPlan("wear","Wear existing weapon",List.of(new LoadoutPlan.Requirement(Set.of(4151),1,LoadoutPlan.Location.EQUIPMENT,3)),null));
        f.death();f.click("Use selected loadout");f.run();DeathRecoveryTool.KitView kit=f.tool().snapshot().deaths.get(0).kit;
        assertTrue(kit.missingItemIds.isEmpty());assertFalse(kit.readiness.itemsReady);
    }
    @Test public void queuedReturnKitActionCannotAffectAnotherCharacter() throws Exception
    {
        Fixture f=new Fixture();f.selected.set(kit());f.death();f.click("Use selected loadout");f.base.profile.set("rsprofile.bob");f.base.tick();f.run();assertTrue(f.tool().snapshot().deaths.isEmpty());
        f.base.profile.set("rsprofile.alice");f.base.tick();assertNull(f.tool().snapshot().deaths.get(0).kit);
    }
    @Test public void aSecondDeathInvalidatesQueuedActionsFromThePreviousView() throws Exception
    {
        Fixture f=new Fixture();f.selected.set(kit());f.death();f.click("Use selected loadout");f.base.tick();f.base.tick();f.tool().onActorDeath(new ActorDeath(f.base.player));f.run();
        assertEquals(2,f.tool().snapshot().deaths.size());assertTrue(f.tool().snapshot().deaths.stream().allMatch(death->death.kit==null));
    }
    @Test public void stopAndRestartInvalidateQueuedActionsEvenForTheSameProfile() throws Exception
    {
        Fixture f=new Fixture();f.selected.set(kit());f.death();f.click("Use selected loadout");f.tool().stop();f.base.warm();f.run();assertNull(f.tool().snapshot().deaths.get(0).kit);
    }
    @Test public void recoveryKindRequiresAnExplicitLinkToTheStillOpenNativeInterface() throws Exception
    {
        Fixture f=new Fixture();f.death();Widget grave=f.base.show(InterfaceID.GravestoneGeneric.UNIVERSE);f.base.container(InventoryID.GRAVESTONE,new Item(4151,1));f.base.tick();
        assertNull(f.tool().snapshot().deaths.get(0).recoveryKind);f.click("Link open recovery");when(grave.isHidden()).thenReturn(true);f.run();assertNull(f.tool().snapshot().deaths.get(0).recoveryKind);
        when(grave.isHidden()).thenReturn(false);f.base.tick();f.click("Link open recovery");f.run();assertEquals("Gravestone",f.tool().snapshot().deaths.get(0).recoveryKind);
    }
    @Test public void manualDepartureConfirmationsDoNotSurviveAsAutomaticPostDeathReadiness() throws Exception
    {
        Fixture f=new Fixture();LoadoutPlan plan=new LoadoutPlan("checks","Return checks",List.of(),null,List.of(new LoadoutPlan.DepartureCheck("antifire","Check antifire",LoadoutPlan.CheckKind.MANUAL,Set.of(),0,null)));
        f.selected.set(plan);f.death();f.click("Use selected loadout");f.run();DeathRecoveryTool.KitView kit=f.tool().snapshot().deaths.get(0).kit;
        assertEquals(LoadoutReadiness.CheckState.UNKNOWN,kit.readiness.checks.get(0).state);assertFalse(kit.readiness.ready);
    }
    @Test public void capturedActivityGuideReturnsOnlyAfterAnExplicitGuardedClick() throws Exception
    {
        Fixture f=new Fixture();f.tool().setActivityGuideSupplier(()->"boss-sarachnis");AtomicReference<String> resumed=new AtomicReference<>();f.tool().setResumeGuideAction(resumed::set);f.death();assertEquals("boss-sarachnis",f.tool().snapshot().deaths.get(0).guideId);assertNull(resumed.get());
        f.click("Return to activity guide");f.run();assertEquals("boss-sarachnis",resumed.get());
    }
    @Test public void populatedReturnKitFitsTheNativeSidebarWithoutSwingClientReads() throws Exception
    {
        Fixture f=new Fixture();f.selected.set(kit());f.death();f.click("Use selected loadout");f.run();
        SwingUtilities.invokeAndWait(()->{try{
            net.runelite.client.ui.laf.RuneLiteLAF.setup();clearInvocations(f.base.client);JPanel panel=f.tool().createPanel();panel.setSize(206,2400);for(int pass=0;pass<3;pass++)layout(panel);panel.setSize(206,panel.getPreferredSize().height);for(int pass=0;pass<3;pass++)layout(panel);assertFits(panel);verifyNoInteractions(f.base.client);
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(206,panel.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D graphics=image.createGraphics();panel.printAll(graphics);graphics.dispose();java.io.File output=new java.io.File("build/reports/death-return-kit-206px.png");output.getParentFile().mkdirs();javax.imageio.ImageIO.write(image,"png",output);
        }catch(Exception error){throw new AssertionError(error);}});
    }
    private static void layout(Container root){root.invalidate();root.doLayout();for(Component child:root.getComponents())if(child instanceof Container&&child.isVisible())layout((Container)child);}
    private static void assertFits(Container root){for(Component child:root.getComponents())if(child.isVisible()){
        assertTrue("Horizontal clipping "+child.getClass().getName()+" "+child.getBounds(),child.getX()+child.getWidth()<=root.getWidth()-root.getInsets().right+1);
        assertTrue("Vertical clipping "+child.getClass().getName(),child.getY()+child.getHeight()<=root.getHeight()-root.getInsets().bottom+1);
        if(child instanceof JButton){JButton button=(JButton)child;assertTrue("Clipped button "+button.getText(),button.getFontMetrics(button.getFont()).stringWidth(button.getText())+button.getInsets().left+button.getInsets().right<=button.getWidth());}
        if(child instanceof Container)assertFits((Container)child);
    }}
    @Test public void longReturnKitsProvideDirectPagingForEveryCopiedRequirement() throws Exception
    {
        Fixture f=new Fixture();List<LoadoutPlan.Requirement> lines=new ArrayList<>();for(int id=100;id<108;id++)lines.add(new LoadoutPlan.Requirement(Set.of(id),1,LoadoutPlan.Location.INVENTORY,-1));f.selected.set(new LoadoutPlan("longkit","Long return kit",lines,null));f.death();f.click("Use selected loadout");f.run();
        SwingUtilities.invokeAndWait(()->{JPanel panel=f.tool().createPanel();JButton next=f.buttons(panel).stream().filter(button->"›".equals(button.getText())).findFirst().orElseThrow(()->new AssertionError("Missing kit pagination"));next.doClick();List<String> visible=new ArrayList<>();collectText(panel,visible);assertTrue(visible.stream().anyMatch(text->text.contains("Item 106")));assertTrue(visible.stream().anyMatch(text->text.contains("Item 107")));});
    }
    private static void collectText(Container root,List<String> result){for(Component child:root.getComponents()){if(child instanceof GuideText)result.add(((GuideText)child).getText());if(child instanceof Container)collectText((Container)child,result);}}
    @Test public void oldVersionOneHistoryStillLoadsWithNoInventedKitOrRecoveryType()
    {
        DeathRecoveryToolTest.Fixture f=new DeathRecoveryToolTest.Fixture();f.settings.put(DeathRecoveryToolTest.Fixture.key(DeathRecoveryTool.GROUP,"rsprofile.alice",DeathRecoveryTool.KEY),"{\"version\":1,\"deaths\":[{\"at\":1,\"x\":3200,\"y\":3200,\"plane\":0,\"world\":301,\"carried\":[]}]}");f.warm();assertEquals(1,f.tool.snapshot().deaths.size());assertNull(f.tool.snapshot().deaths.get(0).kit);assertNull(f.tool.snapshot().deaths.get(0).recoveryKind);
    }
    private static LoadoutPlan kit(){return new LoadoutPlan("return","Return safely",List.of(new LoadoutPlan.Requirement(Set.of(385),3,LoadoutPlan.Location.INVENTORY,-1),new LoadoutPlan.Requirement(Set.of(8007),1,LoadoutPlan.Location.INVENTORY,-1)),null);}
    private static final class Fixture
    {
        final DeathRecoveryToolTest.Fixture base=new DeathRecoveryToolTest.Fixture();final Deque<Runnable> queue=new ArrayDeque<>();final AtomicReference<LoadoutPlan> selected=new AtomicReference<>();final AtomicReference<Set<Integer>> bank=new AtomicReference<>();
        Fixture()throws Exception{Field field=DeathRecoveryTool.class.getDeclaredField("clientThread");field.setAccessible(true);ClientThread thread=(ClientThread)field.get(base.tool);doAnswer(call->{queue.addLast(call.getArgument(0));return null;}).when(thread).invokeLater(any(Runnable.class));base.tool.setSelectedLoadoutSupplier(selected::get);base.tool.setBankFocusAction(bank::set);}
        DeathRecoveryTool tool(){return base.tool;}
        void death(){base.warm();tool().onActorDeath(new ActorDeath(base.player));}
        void click(String name)throws Exception{SwingUtilities.invokeAndWait(()->{JPanel panel=tool().createPanel();JButton button=buttons(panel).stream().filter(value->name.equals(value.getText())).findFirst().orElseThrow(()->new AssertionError("Missing button "+name));button.doClick();});}
        void run()throws Exception{while(!queue.isEmpty())queue.removeFirst().run();SwingUtilities.invokeAndWait(()->{});}
        List<JButton> buttons(Container root){List<JButton> result=new ArrayList<>();for(Component child:root.getComponents()){if(child instanceof JButton)result.add((JButton)child);if(child instanceof Container)result.addAll(buttons((Container)child));}return result;}
    }
}
