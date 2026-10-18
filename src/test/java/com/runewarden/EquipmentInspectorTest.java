/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.WorldView;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class EquipmentInspectorTest
{
    static final class Fixture
    {
        final Client client=mock(Client.class);final ClientThread thread=mock(ClientThread.class);final ConfigManager config=mock(ConfigManager.class);final ItemManager items=mock(ItemManager.class);
        final Player player=mock(Player.class);final PlayerComposition composition=mock(PlayerComposition.class);final WorldView view=mock(WorldView.class);
        final IndexedObjectSet<Player> players=mock(IndexedObjectSet.class);
        final AtomicReference<String> profile=new AtomicReference<>("profile-a"),saved=new AtomicReference<>();final Queue<Runnable> pending=new ArrayDeque<>();
        final Map<KitType,Integer> worn=new EnumMap<>(KitType.class);final EquipmentInspectorTool tool;
        Fixture(String preferences)
        {
            saved.set(preferences);doAnswer(call->{pending.add(call.getArgument(0));return null;}).when(thread).invokeLater(any(Runnable.class));
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);when(client.getTopLevelWorldView()).thenReturn(view);when(client.getWorldView(0)).thenReturn(view);when(client.getTickCount()).thenReturn(100);
            when(config.getRSProfileKey()).thenAnswer(call->profile.get());when(config.getConfiguration(EquipmentInspectorTool.GROUP,EquipmentInspectorTool.KEY)).thenAnswer(call->saved.get());
            when(player.getId()).thenReturn(7);when(player.getWorldView()).thenReturn(view);when(player.getName()).thenReturn("<col=ff0000>Cathy Wampus</col>");when(player.getCombatLevel()).thenReturn(126);when(player.getPlayerComposition()).thenReturn(composition);
            doReturn(players).when(view).players();when(players.byIndex(7)).thenReturn(player);when(composition.getTransformedNpcId()).thenReturn(-1);
            when(composition.getEquipmentId(any(KitType.class))).thenAnswer(call->worn.getOrDefault(call.getArgument(0),-1));when(composition.getKitId(any(KitType.class))).thenReturn(-1);
            when(items.canonicalize(anyInt())).thenAnswer(call->call.getArgument(0));when(items.getItemPrice(anyInt())).thenReturn(1000L);
            when(items.getItemComposition(anyInt())).thenAnswer(call->{ItemComposition definition=mock(ItemComposition.class);when(definition.getMembersName()).thenReturn("A deliberately long readable visible equipment item "+call.getArgument(0));return definition;});
            when(items.getImage(anyInt())).thenAnswer(call->{AsyncBufferedImage icon=new AsyncBufferedImage(thread,36,32,BufferedImage.TYPE_INT_ARGB);icon.loaded();return icon;});
            Gson gson=new Gson();tool=new EquipmentInspectorTool(client,thread,config,items,gson,new RunewardenDataStore(config,gson),new RunewardenConfig(){});tool.start();
        }
        void drain(){int count=0;while(!pending.isEmpty()){assertTrue(++count<100);pending.remove().run();}}
        void select()throws Exception{tool.select(player);drain();flush();}
        void flush()throws Exception{SwingUtilities.invokeAndWait(()->{});}
        void stop()throws Exception{tool.stop();drain();flush();}
    }
    @Test public void selectingOnlyReadsPlayerCompositionAndDefinitionsAfterClientThreadDispatch() throws Exception
    {
        Fixture f=new Fixture(null);f.worn.put(KitType.HEAD,1163);f.worn.put(KitType.WEAPON,4151);clearInvocations(f.player,f.items);f.tool.select(f.player);verifyNoInteractions(f.player,f.items);f.drain();
        EquipmentInspectorTool.Snapshot s=f.tool.reading();assertEquals("Cathy Wampus",s.name);assertEquals(126,s.combat);assertEquals(2,s.pieces.size());assertEquals(2000,s.value);verify(f.client,never()).getItemContainer(anyInt());verify(f.client,never()).getRealSkillLevel(any());f.stop();
    }
    @Test public void ringAmmoAndAppearanceKitsNeverTurnIntoInventedItemIds() throws Exception
    {
        Fixture f=new Fixture(null);f.worn.put(KitType.WEAPON,4151);when(f.composition.getKitId(KitType.HEAD)).thenReturn(5);f.select();
        assertTrue(f.tool.reading().appearance.get(KitType.HEAD));assertNull(f.tool.reading().piece(EquipmentInspectorTool.Slot.RING));assertNull(f.tool.reading().piece(EquipmentInspectorTool.Slot.AMMO));
        SwingUtilities.invokeAndWait(()->{JPanel panel=f.tool.createPanel();JButton ring=(JButton)find(panel,"equipmentInspector.RING"),ammo=(JButton)find(panel,"equipmentInspector.AMMO");assertFalse(ring.isEnabled());assertFalse(ammo.isEnabled());assertTrue(ring.getToolTipText().contains("Not visible"));assertTrue(((JButton)find(panel,"equipmentInspector.HEAD")).getToolTipText().contains("Appearance"));});f.stop();
    }
    @Test public void unchangedGearReusesTheSnapshotAndDefinitionIconsInsteadOfRebuildingEveryTick() throws Exception
    {
        Fixture f=new Fixture(null);f.worn.put(KitType.WEAPON,4151);f.select();EquipmentInspectorTool.Snapshot first=f.tool.reading();clearInvocations(f.items);for(int i=0;i<5;i++)f.tool.onGameTick();assertSame(first,f.tool.reading());verifyNoInteractions(f.items);f.stop();
    }
    @Test public void liveEquipmentChangesUpdatePublicPiecesButUnpricedItemsAreNotValuedByGuesswork() throws Exception
    {
        Fixture f=new Fixture(null);f.worn.put(KitType.WEAPON,4151);when(f.items.getItemPrice(1163)).thenReturn(0L);f.select();f.worn.put(KitType.HEAD,1163);f.tool.onGameTick();assertEquals(2,f.tool.reading().pieces.size());assertEquals(1000,f.tool.reading().value);assertEquals(1,f.tool.reading().unpriced);f.stop();
    }
    @Test public void pausedSnapshotsRequireManualRefreshWhileThePlayerStillHasToBeLoaded() throws Exception
    {
        Fixture f=new Fixture("{\"live\":false}");f.worn.put(KitType.WEAPON,4151);f.select();f.worn.put(KitType.WEAPON,11802);f.tool.onGameTick();assertEquals(4151,f.tool.reading().pieces.get(KitType.WEAPON).id);
        f.tool.refreshSelected();f.drain();assertEquals(11802,f.tool.reading().pieces.get(KitType.WEAPON).id);when(f.players.byIndex(7)).thenReturn(null);f.tool.onGameTick();assertFalse(f.tool.reading().available());f.stop();
    }
    @Test public void anotherProfileCannotAcceptAQueuedPlayerSelectionOrReadThePreviousSnapshot() throws Exception
    {
        Fixture f=new Fixture(null);f.worn.put(KitType.WEAPON,4151);f.tool.select(f.player);f.profile.set("profile-b");f.drain();assertFalse(f.tool.reading().available());f.profile.set("profile-a");f.select();assertTrue(f.tool.reading().available());f.profile.set("profile-b");assertFalse(f.tool.reading().available());f.tool.onGameTick();assertFalse(f.tool.reading().available());f.stop();
    }
    @Test public void worldViewReplacementAndReusedPlayerIndexesInvalidateTheSelection() throws Exception
    {
        Fixture f=new Fixture(null);f.worn.put(KitType.WEAPON,4151);f.select();when(f.player.getWorldView()).thenReturn(mock(WorldView.class));f.tool.onGameTick();assertFalse(f.tool.reading().available());
        when(f.player.getWorldView()).thenReturn(f.view);f.select();when(f.players.byIndex(7)).thenReturn(mock(Player.class));f.tool.onGameTick();assertFalse(f.tool.reading().available());f.stop();
    }
    @Test public void npcTransformsDoNotPretendToExposeWornItems() throws Exception
    {
        Fixture f=new Fixture(null);f.worn.put(KitType.WEAPON,4151);when(f.composition.getTransformedNpcId()).thenReturn(123);f.select();assertTrue(f.tool.reading().transformed);assertTrue(f.tool.reading().pieces.isEmpty());verify(f.items,never()).getItemComposition(anyInt());f.stop();
    }
    @Test public void staleSidebarButtonsCannotOpenAnOldPlayersGearAfterTheProfileChanges() throws Exception
    {
        Fixture f=new Fixture(null);AtomicInteger opened=new AtomicInteger();f.tool.setOpenItemAction(opened::set);f.worn.put(KitType.WEAPON,4151);f.select();SwingUtilities.invokeAndWait(()->{JButton weapon=(JButton)find(f.tool.createPanel(),"equipmentInspector.WEAPON");assertNotNull(weapon);f.profile.set("profile-b");weapon.doClick();});f.drain();f.flush();assertEquals(0,opened.get());f.stop();
    }
    @Test public void selectingAndClickingTheActualVisibleIconOpensThatExactItem() throws Exception
    {
        Fixture f=new Fixture(null);AtomicInteger opened=new AtomicInteger(),module=new AtomicInteger();f.tool.setOpenItemAction(opened::set);f.tool.setOpenModuleAction(module::incrementAndGet);f.worn.put(KitType.WEAPON,4151);f.select();assertEquals(1,module.get());
        SwingUtilities.invokeAndWait(()->{JButton weapon=(JButton)find(f.tool.createPanel(),"equipmentInspector.WEAPON");assertNotNull(weapon.getIcon());weapon.doClick();});f.drain();f.flush();assertEquals(4151,opened.get());f.stop();
    }
    @Test public void inspectYourselfUsesTheSameBoundedNativeSelectionPath() throws Exception
    {
        Fixture f=new Fixture(null);f.worn.put(KitType.WEAPON,4151);f.tool.inspectSelf();f.drain();assertEquals("Cathy Wampus",f.tool.reading().name);f.stop();
    }
    @Test public void optionalHudUsesSnapshotIconsAndNoNativeActorOrDefinitionReadsWhilePainting() throws Exception
    {
        Fixture f=new Fixture("{\"hud\":true}");f.worn.put(KitType.WEAPON,4151);f.select();clearInvocations(f.client,f.items,f.player,f.composition);BufferedImage image=new BufferedImage(200,260,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();try{assertEquals(new Dimension(154,226),f.tool.overlay().render(graphics));}finally{graphics.dispose();}verifyNoInteractions(f.client,f.items,f.player,f.composition);
        f.tool.setHudLocked(true);assertFalse(f.tool.overlay().isMovable());f.tool.setHudLocked(false);assertTrue(f.tool.overlay().isMovable());f.stop();
    }
    @Test public void theActual206PixelSidebarFitsItsIconGridWrappedNamesAndOptions() throws Exception
    {
        Fixture f=new Fixture(null);for(EquipmentInspectorTool.Slot slot:EquipmentInspectorTool.Slot.values())if(slot.kit!=null)f.worn.put(slot.kit,1000+slot.ordinal());f.select();SwingUtilities.invokeAndWait(()->{JPanel panel=f.tool.createPanel();SidebarUi.install(panel);for(String label:new String[]{"▸ Visible item list","▸ Inspector options"}){JButton button=button(panel,label);assertNotNull(button);button.doClick();}for(int i=0;i<8;i++){panel.setSize(206,panel.getPreferredSize().height);layout(panel);}assertFits(panel);Graphics2D g=new BufferedImage(206,panel.getHeight(),BufferedImage.TYPE_INT_ARGB).createGraphics();try{panel.paint(g);}finally{g.dispose();}});f.stop();
    }
    @Test public void inspectorOptionsPersistPresentationOnlyAndStopClearsThePlayerIdentity() throws Exception
    {
        Fixture f=new Fixture(null);f.worn.put(KitType.WEAPON,4151);f.select();SwingUtilities.invokeAndWait(()->{javax.swing.JCheckBox hud=(javax.swing.JCheckBox)find(f.tool.createPanel(),"equipmentInspectorHud");hud.doClick();});org.mockito.ArgumentCaptor<String> stored=org.mockito.ArgumentCaptor.forClass(String.class);verify(f.config).setConfiguration(eq(EquipmentInspectorTool.GROUP),eq(EquipmentInspectorTool.KEY),stored.capture());assertFalse(stored.getValue().contains("Cathy"));assertFalse(stored.getValue().contains("4151"));f.stop();assertFalse(f.tool.reading().available());
    }
    private static Component find(Container root,String name){for(Component child:root.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container){Component found=find((Container)child,name);if(found!=null)return found;}}return null;}
    private static JButton button(Container root,String title){for(Component child:root.getComponents()){if(child instanceof JButton&&title.equals(((JButton)child).getText()))return (JButton)child;if(child instanceof Container){JButton found=button((Container)child,title);if(found!=null)return found;}}return null;}
    private static void layout(Container root){root.doLayout();for(Component child:root.getComponents())if(child instanceof Container)layout((Container)child);}
    private static void assertFits(Container root){for(Component child:root.getComponents()){if(!child.isVisible())continue;assertTrue(child.getClass()+" right edge",child.getX()+child.getWidth()<=root.getWidth());if(child instanceof GuideText)assertTrue("clipped text",child.getHeight()>=child.getPreferredSize().height);if(child instanceof Container)assertFits((Container)child);}}
}
