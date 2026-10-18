/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ClueJugglingToolTest
{
    @Test public void enablingSeedsNativeItemsOnlyOnceAndReplayDoesNotDuplicate()
    {Fixture f=new Fixture();TileItem clue=f.clue(2677,200,true);f.ground.add(clue);f.start();assertEquals(1,f.tool.reading().rows.size());assertEquals(100,f.row().ticks);f.tool.onItemSpawned(new ItemSpawned(f.tile,clue));f.tool.onGameTick();assertEquals(1,f.tool.reading().rows.size());verify(f.scene,times(1)).getTiles();f.stop();}
    @Test public void npcDroppedClueKeepsItsShortNativeDeadline()
    {Fixture f=new Fixture();f.ground.add(f.clue(2677,120,false));f.start();assertEquals(20,f.row().ticks);verify(f.notifier).notify(contains("12s"));f.stop();}
    @Test public void actualPickupRemovesOnlyTheDespawnedIdentity()
    {Fixture f=new Fixture();TileItem one=f.clue(2677,200,true),two=f.clue(2677,300,true);f.ground.add(one);f.ground.add(two);f.start();f.ground.remove(one);f.tool.onItemDespawned(new ItemDespawned(f.tile,one));f.next();assertEquals(1,f.tool.reading().rows.size());assertEquals(199,f.row().ticks);f.stop();}
    @Test public void sameTickFakeDespawnAndRespawnDoesNotRemoveOrResetClue()
    {Fixture f=new Fixture();TileItem old=f.clue(2677,200,true);f.ground.add(old);f.start();long key=f.row().key;f.tool.onItemDespawned(new ItemDespawned(f.tile,old));TileItem fresh=f.clue(2677,200,true);f.ground.clear();f.ground.add(fresh);f.tool.onItemSpawned(new ItemSpawned(f.tile,fresh));f.next();assertEquals(key,f.row().key);assertEquals(99,f.row().ticks);f.stop();}
    @Test public void leavingTheSceneKeepsTheObservedDeadlineRunning()
    {Fixture f=new Fixture();TileItem clue=f.clue(2677,200,true);f.ground.add(clue);f.start();f.playerPoint.set(new WorldPoint(3300,3300,0));f.tool.onItemDespawned(new ItemDespawned(f.tile,clue));f.ground.clear();f.next();assertEquals(99,f.row().ticks);assertFalse(f.row().present);f.tick.set(200);f.tool.onGameTick();assertTrue(f.tool.reading().rows.isEmpty());f.stop();}
    @Test public void loadingPreservesOffSceneTimersAndReseedsOnReturn()
    {Fixture f=new Fixture();f.ground.add(f.clue(2677,200,true));f.start();f.tool.onGameStateChanged(GameState.LOADING);f.ground.clear();f.playerPoint.set(new WorldPoint(3300,3300,0));f.next();assertEquals(99,f.row().ticks);assertFalse(f.row().present);f.stop();}
    @Test public void revisitingAnEmptyNearbyTileClearsAnOldSavedClue()
    {Fixture f=new Fixture();f.ground.add(f.clue(2677,200,true));f.start();f.tool.onGameStateChanged(GameState.LOGIN_SCREEN);f.ground.clear();f.next();assertTrue(f.tool.reading().rows.isEmpty());f.stop();}
    @Test public void cleanLogoutResumesPrivateTimerWithoutCountingOfflineWallTime()
    {Fixture f=new Fixture();f.ground.add(f.clue(2677,500,true));f.start();f.tool.onGameStateChanged(GameState.LOGIN_SCREEN);f.ground.clear();f.playerPoint.set(new WorldPoint(3300,3300,0));f.tick.set(10000);f.tool.onGameTick();assertEquals(400,f.row().ticks);assertEquals(ClueJugglingTracker.Certainty.REMEMBERED,f.row().certainty);verifyNoInteractions(f.notifier);f.stop();}
    @Test public void disablingWhileInGameRestoresUnknownUntilSeenAgain()
    {Fixture f=new Fixture();f.ground.add(f.clue(2677,500,true));f.start();f.tool.stop();f.ground.clear();f.playerPoint.set(new WorldPoint(3300,3300,0));f.tick.set(2000);f.start();assertEquals(-1,f.row().ticks);assertEquals(ClueJugglingTracker.Certainty.UNKNOWN,f.row().certainty);f.stop();}
    @Test public void connectionLossCannotBeUpgradedToCleanByLaterLoginScreen()
    {Fixture f=new Fixture();f.ground.add(f.clue(2677,500,true));f.start();f.tool.onGameStateChanged(GameState.CONNECTION_LOST);f.tool.onGameStateChanged(GameState.LOGIN_SCREEN);f.ground.clear();f.playerPoint.set(new WorldPoint(3300,3300,0));f.next();assertEquals(-1,f.row().ticks);f.stop();}
    @Test public void accountSwitchImmediatelyHidesOldReadingsAndDoesNotCopyTimers()
    {Fixture f=new Fixture();f.ground.add(f.clue(2677,500,true));f.start();f.profile.set("rsprofile-b");assertTrue(f.tool.reading().rows.isEmpty());f.ground.clear();f.next();assertTrue(f.tool.reading().rows.isEmpty());assertFalse(f.saves.containsKey(ClueJugglingTool.GROUP+"|rsprofile-b|"+ClueJugglingTool.TIMERS)&&f.saves.get(ClueJugglingTool.GROUP+"|rsprofile-b|"+ClueJugglingTool.TIMERS).contains("2677"));f.stop();}
    @Test public void noOtherPlayersPrivateClueIsIncluded()
    {Fixture f=new Fixture();TileItem clue=f.clue(2677,200,true);when(clue.getOwnership()).thenReturn(TileItem.OWNERSHIP_OTHER);f.ground.add(clue);f.start();assertTrue(f.tool.reading().rows.isEmpty());f.stop();}
    @Test public void nativeUnknownExpiryIsVisibleButNeverNotified()
    {Fixture f=new Fixture();f.ground.add(f.clue(2677,0,true));f.start();assertEquals(-1,f.row().ticks);f.tick.set(6000);f.tool.onGameTick();assertEquals(-1,f.row().ticks);verifyNoInteractions(f.notifier);f.stop();}
    @Test public void wildcardAndNumericRulesAreBoundedAndTreatOtherRegexSyntaxLiterally()
    {assertTrue(ClueJugglingTool.extraMatches("995, Rune *",995,"Coins"));assertTrue(ClueJugglingTool.extraMatches("995, Rune *",123,"Rune arrow"));assertFalse(ClueJugglingTool.extraMatches("Rune .+",123,"Rune arrow"));assertFalse(ClueJugglingTool.extraMatches("",123,""));assertFalse(ClueJugglingTool.extraMatches("9999999999999999999999",123,"Coins"));}
    @Test public void configuredExtraTimersTrackNativeDeadlineButAreNotPersistedAsPrivateClues()
    {Fixture f=new Fixture();f.saves.put(ClueJugglingTool.GROUP+"|"+ClueJugglingTool.PREFERENCES,"{\"version\":1,\"extra\":\"995\"}");f.tool.reloadPreferences();f.ground.add(f.clue(995,200,true));f.start();assertEquals(ClueJugglingTracker.Tier.EXTRA,f.row().tier);f.tool.onGameStateChanged(GameState.LOGIN_SCREEN);assertFalse(f.saves.get(ClueJugglingTool.GROUP+"|rsprofile-a|"+ClueJugglingTool.TIMERS).contains("995"));f.stop();}
    @Test public void staleSidebarActionsCannotRemoveOrRouteAnotherCharactersTimer() throws Exception
    {Fixture f=new Fixture();f.ground.add(f.clue(2677,200,true));f.start();AtomicInteger routes=new AtomicInteger();f.tool.setRouteAction(point->routes.incrementAndGet());SwingUtilities.invokeAndWait(()->{JPanel p=f.tool.createPanel();JButton route=button(p,"Route"),remove=button(p,"Remove");assertNotNull(route);assertNotNull(remove);f.profile.set("rsprofile-b");route.doClick();remove.doClick();});assertEquals(0,routes.get());f.profile.set("rsprofile-a");assertEquals(1,f.tool.reading().rows.size());f.stop();}
    @Test public void retainedOldCharactersButtonsCannotModifyAFullyBoundNewCharactersReusedKey() throws Exception
    {
        Fixture f=new Fixture();f.ground.add(f.clue(2677,200,true));f.start();AtomicInteger routes=new AtomicInteger();f.tool.setRouteAction(point->routes.incrementAndGet());
        final JButton[] old=new JButton[3];SwingUtilities.invokeAndWait(()->{JPanel p=f.tool.createPanel();old[0]=button(p,"Remove");old[1]=button(p,"Clear tracked timers");old[2]=button(p,"Route");});
        long oldKey=f.row().key;f.profile.set("rsprofile-b");f.ground.clear();f.ground.add(f.clue(2677,300,true));f.next();assertEquals(oldKey,f.row().key);assertEquals(1,f.tool.reading().rows.size());
        SwingUtilities.invokeAndWait(()->{for(JButton button:old){assertNotNull(button);button.doClick();}});
        assertEquals(1,f.tool.reading().rows.size());assertEquals(0,routes.get());assertEquals(199,f.row().ticks);f.stop();
    }
    @Test public void oldButtonsCannotRemoveANewDropAfterManualClearReusesItsKey() throws Exception
    {
        Fixture f=new Fixture();f.ground.add(f.clue(2677,200,true));f.start();JButton[] old=new JButton[1];SwingUtilities.invokeAndWait(()->old[0]=button(f.tool.createPanel(),"Remove"));
        long key=f.row().key;f.tool.clearTimers();TileItem fresh=f.clue(2677,300,true);f.tool.onItemSpawned(new ItemSpawned(f.tile,fresh));f.next();assertEquals(key,f.row().key);
        SwingUtilities.invokeAndWait(()->old[0].doClick());assertEquals(1,f.tool.reading().rows.size());f.stop();
    }
    @Test public void clearAndIndividualRemovePersistWithoutDeletingOtherEntries() throws Exception
    {Fixture f=new Fixture();f.ground.add(f.clue(2677,200,true));f.ground.add(f.clue(2677,300,true));f.start();f.tool.remove(f.row().key);assertEquals(1,f.tool.reading().rows.size());f.tool.clearTimers();assertTrue(f.tool.reading().rows.isEmpty());f.next();assertTrue(f.tool.reading().rows.isEmpty());f.stop();}
    @Test public void combinedAndSeparateHudPaintFromSnapshots() throws Exception
    {Fixture f=new Fixture();f.ground.add(f.clue(2677,200,true));f.ground.add(f.clue(2677,300,true));f.start();BufferedImage image=new BufferedImage(400,250,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{assertNotNull(f.tool.overlay().render(g));f.tool.preferences().combined=true;assertEquals(83,f.tool.overlay().render(g).height);}finally{g.dispose();}f.stop();}
    @Test public void instanceClueDeadlineIsVisibleButNeverSavedAsPersistentWorldLoot()
    {Fixture f=new Fixture();when(f.view.isInstance()).thenReturn(true);f.ground.add(f.clue(2677,200,true));f.start();assertEquals(100,f.row().ticks);f.tool.onGameStateChanged(GameState.LOGIN_SCREEN);assertFalse(f.saves.get(ClueJugglingTool.GROUP+"|rsprofile-a|"+ClueJugglingTool.TIMERS).contains("2677"));f.stop();}
    @Test public void hiddenSidebarDoesNotRebuildAndShowingRendersTheLatestSnapshot() throws Exception
    {
        Fixture f=new Fixture();f.ground.add(f.clue(2677,200,true));f.start();JPanel[] panel=new JPanel[1];JButton[] oldRemove=new JButton[1];
        SwingUtilities.invokeAndWait(()->{panel[0]=f.tool.createPanel();assertFalse(panel[0].isShowing());oldRemove[0]=button(panel[0],"Remove");});
        f.tick.set(125);f.tool.onGameTick();SwingUtilities.invokeAndWait(()->assertSame(oldRemove[0],button(panel[0],"Remove")));assertEquals(75,f.row().ticks);
        SwingUtilities.invokeAndWait(()->{panel[0].addNotify();assertTrue(panel[0].isShowing());});SwingUtilities.invokeAndWait(()->{});
        SwingUtilities.invokeAndWait(()->{assertNotSame(oldRemove[0],button(panel[0],"Remove"));assertTrue(text(panel[0]).contains("45s"));panel[0].removeNotify();});f.stop();
    }
    @Test public void sidebarPagesTheFullLedgerWithAtMostTwelveCards() throws Exception
    {
        Fixture f=new Fixture();for(int i=0;i<40;i++)f.ground.add(f.clue(2677,200+i,true));f.start();
        SwingUtilities.invokeAndWait(()->{JPanel panel=f.tool.createPanel();assertEquals(12,countButtons(panel,"Remove"));assertTrue(text(panel).contains("1–12 of 40"));button(panel,"Next").doClick();assertEquals(12,countButtons(panel,"Remove"));assertTrue(text(panel).contains("13–24 of 40"));button(panel,"Next").doClick();button(panel,"Next").doClick();assertEquals(4,countButtons(panel,"Remove"));assertTrue(text(panel).contains("37–40 of 40"));});f.stop();
    }
    @Test public void invalidPreferencesRemainUntouchedAndUseSafeDefaults()
    {Fixture f=new Fixture();String invalid="{\"version\":1,\"notifySeconds\":-1,\"extra\":\"995\"}";f.saves.put(ClueJugglingTool.GROUP+"|"+ClueJugglingTool.PREFERENCES,invalid);f.tool.reloadPreferences();assertEquals(30,f.tool.preferences().notifySeconds);assertEquals(invalid,f.saves.get(ClueJugglingTool.GROUP+"|"+ClueJugglingTool.PREFERENCES));f.stop();}
    static final class Fixture
    {
        final Client client=mock(Client.class);final ClientThread thread=mock(ClientThread.class);final ConfigManager config=mock(ConfigManager.class);final ItemManager items=mock(ItemManager.class);final Notifier notifier=mock(Notifier.class);
        final Scene scene=mock(Scene.class);final WorldView view=mock(WorldView.class);final Tile tile=mock(Tile.class);final Player player=mock(Player.class);
        final AtomicInteger tick=new AtomicInteger(100);final AtomicReference<String> profile=new AtomicReference<>("rsprofile-a");final AtomicReference<WorldPoint> playerPoint=new AtomicReference<>(new WorldPoint(3200,3200,0));
        final List<TileItem> ground=new ArrayList<>();final Map<String,String> saves=new HashMap<>();final ClueJugglingTool tool;
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getTickCount()).thenAnswer(c->tick.get());when(client.getWorld()).thenReturn(301);when(client.getLocalPlayer()).thenReturn(player);when(player.getWorldLocation()).thenAnswer(c->playerPoint.get());
            when(config.getRSProfileKey()).thenAnswer(c->profile.get());when(client.getTopLevelWorldView()).thenReturn(view);when(view.getScene()).thenReturn(scene);when(scene.getTiles()).thenReturn(new Tile[][][]{{{tile}}});when(tile.getGroundItems()).thenReturn(ground);when(tile.getWorldLocation()).thenReturn(new WorldPoint(3200,3200,0));
            when(config.getConfiguration(anyString(),anyString())).thenAnswer(c->saves.get(c.getArgument(0)+"|"+c.getArgument(1)));
            when(config.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(c->saves.get(c.getArgument(0)+"|"+c.getArgument(1)+"|"+c.getArgument(2)));
            doAnswer(c->{saves.put(c.getArgument(0)+"|"+c.getArgument(1),(String)c.getArgument(2));return null;}).when(config).setConfiguration(anyString(),anyString(),any());
            doAnswer(c->{saves.put(c.getArgument(0)+"|"+c.getArgument(1)+"|"+c.getArgument(2),(String)c.getArgument(3));return null;}).when(config).setConfiguration(anyString(),anyString(),anyString(),any());
            doAnswer(c->{((Runnable)c.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
            when(items.getItemComposition(anyInt())).thenAnswer(c->{ItemComposition d=mock(ItemComposition.class);when(d.getMembersName()).thenReturn((int)c.getArgument(0)==995?"Coins":"Clue scroll (easy)");return d;});
            RunewardenConfig hudConfig=mock(RunewardenConfig.class);when(hudConfig.hudWidgetSettings()).thenReturn("");when(hudConfig.hudOpacity()).thenReturn(85);
            tool=new ClueJugglingTool(client,thread,config,items,new RunewardenDataStore(config,new Gson()),notifier,hudConfig);
        }
        TileItem clue(int id,int deadline,boolean privateItem){TileItem item=mock(TileItem.class);when(item.getId()).thenReturn(id);when(item.getQuantity()).thenReturn(1);when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_SELF);when(item.isPrivate()).thenReturn(privateItem);when(item.getDespawnTime()).thenReturn(deadline);return item;}
        void start(){tool.start();tool.onGameTick();}
        void next(){tick.incrementAndGet();tool.onGameTick();}
        ClueJugglingTracker.Row row(){return tool.reading().rows.get(0);}
        void stop(){tool.stop();}
    }
    private static int countButtons(Container container,String text)
    {int total=0;for(Component child:container.getComponents()){if(child instanceof JButton&&text.equals(((JButton)child).getText()))total++;if(child instanceof Container)total+=countButtons((Container)child,text);}return total;}
    private static String text(Container container)
    {StringBuilder result=new StringBuilder();for(Component child:container.getComponents()){if(child instanceof javax.swing.text.JTextComponent)result.append(((javax.swing.text.JTextComponent)child).getText());if(child instanceof Container)result.append(text((Container)child));}return result.toString();}
    private static JButton button(Container container,String text)
    {for(Component child:container.getComponents()){if(child instanceof JButton&&text.equals(((JButton)child).getText()))return (JButton)child;if(child instanceof Container){JButton found=button((Container)child,text);if(found!=null)return found;}}return null;}
}
