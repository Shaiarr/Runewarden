/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.MenuEntry;
import net.runelite.api.MenuAction;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WorldListLoad;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.events.WorldsFetch;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.util.HotkeyListener;
import javax.swing.SwingUtilities;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native client actions and delayed work, using no real executor or network. */
public class WorldHopperToolTest
{
    @Test public void openingObservingAndPingingNeverHopWithoutAnExplicitWorldChoice()
    {
        Fixture f=new Fixture();f.show();f.tool.refreshWorlds();f.clientTasks();f.tool.refreshPings();f.workerTasks();f.clientTasks();
        for(int i=0;i<5;i++)f.tick();
        verify(f.client,never()).createWorld();verify(f.client,never()).openWorldHopper();
        verify(f.client,never()).hopToWorld(any());verify(f.client,never()).changeWorld(any());
    }
    @Test public void explicitLoggedInHopRunsOnClientThreadAndUsesTheNativeSwitcher()
    {
        Fixture f=new Fixture();f.show();f.tool.selectWorld(302);
        verify(f.client,never()).createWorld();f.clientTasks();verify(f.client).createWorld();
        verify(f.client,never()).hopToWorld(any());f.tick();verify(f.client).openWorldHopper();
        when(f.client.getWidget(InterfaceID.Worldswitcher.BUTTONS)).thenReturn(mock(Widget.class));
        f.tick();verify(f.client).hopToWorld(f.nativeWorld);f.tick();verify(f.client,times(1)).hopToWorld(any());
        verify(f.nativeWorld).setId(302);verify(f.nativeWorld).setAddress("world302.runescape.com");
        assertTrue("All native writes must be made from the queued client task or GameTick",f.writesOnClient.get());
    }
    @Test public void loginScreenChoiceChangesWorldDirectlyAndDoesNotOpenTheInGameSwitcher()
    {
        Fixture f=new Fixture();f.state.set(GameState.LOGIN_SCREEN);f.show();f.tool.selectWorld(302);f.clientTasks();
        verify(f.client).changeWorld(f.nativeWorld);verify(f.client,never()).hopToWorld(any());verify(f.client,never()).openWorldHopper();
    }
    @Test public void nativeSwitcherRetriesAreBoundedAndDoNotTurnIntoAnAutomaticHopLoop()
    {
        Fixture f=new Fixture();f.show();f.tool.selectWorld(302);f.clientTasks();
        for(int i=0;i<9;i++)f.tick();
        verify(f.client,times(3)).openWorldHopper();verify(f.client,never()).hopToWorld(any());
    }
    @Test public void eligibilityRejectsCurrentOfflineFullAndMembersOnlyWorlds()
    {
        Fixture f=new Fixture(List.of(world(301,500),world(302,-1),world(303,2000),world(305,400,WorldType.MEMBERS),world(306,1949)));
        f.show();
        for(int id:List.of(301,302,303,305))
        {assertFalse("World "+id+" must be blocked",f.row(id).eligible);f.tool.selectWorld(id);f.clientTasks();}
        assertTrue(f.row(306).eligible);verify(f.client,never()).createWorld();assertNull(f.tool.snapshot().confirmation);
    }
    @Test public void restrictedTypeFlagsStayManualAndPreserveTheNativeWorldTypes()
    {
        for(WorldType type:List.of(WorldType.LEGACY_ONLY,WorldType.EOC_ONLY))
        {
            Fixture f=new Fixture(List.of(world(301,500),world(302,400,type)));f.show();
            f.tool.cycle(true);f.clientTasks();verify(f.client,never()).createWorld();assertNull(f.tool.snapshot().confirmation);
            assertTrue("The native client decides actual entry restrictions",f.row(302).eligible);
            assertFalse(f.row(302).safe);f.tool.selectWorld(302);f.clientTasks();assertNotNull(f.tool.snapshot().confirmation);
            verify(f.client,never()).createWorld();f.tool.confirmHop(f.tool.snapshot().confirmation.token);f.clientTasks();
            verify(f.nativeWorld).setTypes(EnumSet.of(net.runelite.api.WorldType.valueOf(type.name())));
            when(f.client.getWidget(InterfaceID.Worldswitcher.BUTTONS)).thenReturn(mock(Widget.class));f.tick();verify(f.client).hopToWorld(f.nativeWorld);
        }
    }
    @Test public void membershipAndTotalLevelUseTheAccountAndDestinationRules()
    {
        World freeTotal=world(302,500,"500 skill total",WorldType.SKILL_TOTAL);
        World membersTotal=world(303,500,"1000 skill total",WorldType.MEMBERS,WorldType.SKILL_TOTAL);
        Fixture f=new Fixture(List.of(world(301,500),freeTotal,membersTotal));
        when(f.client.getTotalLevel()).thenReturn(2000);when(f.client.getRealSkillLevel(any(Skill.class))).thenReturn(1);f.tick();f.show();
        assertFalse("A high members-inclusive total cannot unlock a free total-level world",f.row(302).eligible);
        assertFalse("A free account cannot use a members world",f.row(303).eligible);
        when(f.client.getVarpValue(1780)).thenReturn(30);f.tick();assertTrue(f.row(303).eligible);
        when(f.client.getRealSkillLevel(any(Skill.class))).thenReturn(99);f.tick();assertTrue(f.row(302).eligible);
    }
    @Test public void malformedTotalRequirementIsBlockedRatherThanAssumedUnlocked()
    {
        Fixture f=new Fixture(List.of(world(301,500),world(302,500,"Unknown requirement",WorldType.SKILL_TOTAL)));f.show();
        assertFalse(f.row(302).eligible);f.tool.selectWorld(302);f.clientTasks();verify(f.client,never()).createWorld();
    }
    @Test public void dangerousWorldRequiresItsOwnExplicitConfirmationToken()
    {
        Fixture f=new Fixture(List.of(world(301,500),world(302,500,WorldType.PVP),world(303,500,WorldType.HIGH_RISK)));f.show();
        f.tool.selectWorld(302);f.clientTasks();assertNotNull(f.tool.snapshot().confirmation);long first=f.tool.snapshot().confirmation.token;
        assertFalse(f.row(302).safe);verify(f.client,never()).createWorld();
        f.tool.confirmHop(first+1);f.clientTasks();verify(f.client,never()).createWorld();
        f.tool.selectWorld(303);f.clientTasks();assertNotNull(f.tool.snapshot().confirmation);long second=f.tool.snapshot().confirmation.token;
        f.tool.confirmHop(first);f.clientTasks();verify(f.client,never()).createWorld();
        f.tool.confirmHop(second);f.clientTasks();verify(f.nativeWorld).setId(303);
        verify(f.client,never()).runScript(anyInt(),any());
    }
    @Test public void confirmationCannotOutliveItsAccountOrFreshWorldEligibility()
    {
        Fixture f=new Fixture(List.of(world(301,500),world(302,500,WorldType.PVP)));f.show();
        f.tool.selectWorld(302);f.clientTasks();long token=f.tool.snapshot().confirmation.token;
        f.profile.set("rsprofile-b");f.tool.confirmHop(token);f.clientTasks();verify(f.client,never()).createWorld();
        f.profile.set("rsprofile-a");f.tick();f.tool.selectWorld(302);f.clientTasks();assertNotNull(f.tool.snapshot().confirmation);
        token=f.tool.snapshot().confirmation.token;f.population(302,-1);
        f.tool.confirmHop(token);f.clientTasks();verify(f.client,never()).createWorld();
    }
    @Test public void queuedManualChoiceRechecksLatestGameStateAndPopulation()
    {
        Fixture f=new Fixture();f.show();f.tool.selectWorld(302);f.state.set(GameState.HOPPING);f.clientTasks();verify(f.client,never()).createWorld();
        f.state.set(GameState.LOGGED_IN);f.tick();f.tool.selectWorld(302);f.population(302,2000);f.clientTasks();
        verify(f.client,never()).createWorld();
    }
    @Test public void aNewWorldsFetchSupersedesAnOlderNativePopulationOverride()
    {
        Fixture f=new Fixture();f.show();
        for(int latest:List.of(-1,2000))
        {
            f.population(302,400);assertTrue(f.row(302).eligible);
            WorldResult newer=new WorldResult();newer.setWorlds(List.of(world(301,500),world(302,latest),world(303,700)));
            f.tool.onWorldsFetch(new WorldsFetch(newer));f.clientTasks();
            assertEquals("The newer fetched population must replace the older native list",latest,f.row(302).population);
            assertFalse(f.row(302).eligible);f.tool.selectWorld(302);f.clientTasks();
        }
        verify(f.client,never()).createWorld();
    }
    @Test public void observedPreviousWorldAndFavouritesDoNotProduceAutomaticClientActions()
    {
        Fixture f=new Fixture();f.show();f.tool.toggleFavourite(302);f.clientTasks();assertTrue(f.row(302).favourite);
        f.tool.toggleFavourite(302);f.clientTasks();assertFalse(f.row(302).favourite);
        f.current.set(302);f.tick();assertEquals(301,f.tool.snapshot().lastWorld);verify(f.client,never()).createWorld();
        f.tool.previousWorld();f.clientTasks();verify(f.nativeWorld).setId(301);
    }
    @Test public void favouritesAreRestoredOnlyForTheirOwnCharacter()
    {
        Fixture f=new Fixture();f.show();f.tool.toggleFavourite(302);f.clientTasks();assertTrue(f.row(302).favourite);
        f.profile.set("rsprofile-b");f.tick();assertFalse(f.row(302).favourite);
        f.profile.set("rsprofile-a");f.tick();assertTrue(f.row(302).favourite);
        verify(f.client,never()).createWorld();
    }
    @Test public void quickHopWrapsAndSkipsNearFullAndDifferentDangerTypes()
    {
        Fixture f=new Fixture(List.of(world(301,500),world(302,1950),world(303,300,WorldType.PVP),world(304,600)));f.show();
        assertTrue("Native direct selection may still use a near-full world",f.row(302).eligible);
        f.tool.cycle(true);f.clientTasks();verify(f.nativeWorld).setId(304);assertNull(f.tool.snapshot().confirmation);
        f.tool.cancelHop();f.clientTasks();f.tool.cycle(false);f.clientTasks();verify(f.nativeWorld,times(2)).setId(304);
    }
    @Test public void hotkeysRegisterOnceAndTheirManualCallbackUsesTheSameQueuedHopFlow()
    {
        Fixture f=new Fixture();f.show();ArgumentCaptor<KeyListener> listeners=ArgumentCaptor.forClass(KeyListener.class);
        verify(f.keys,times(2)).registerKeyListener(listeners.capture());f.tool.start();verify(f.keys,times(2)).registerKeyListener(any());
        ((HotkeyListener)listeners.getAllValues().get(1)).hotkeyPressed();verify(f.client,never()).createWorld();f.clientTasks();verify(f.nativeWorld).setId(302);
        f.tool.stop();for(KeyListener listener:listeners.getAllValues())verify(f.keys).unregisterKeyListener(listener);
    }
    @Test public void aFriendsNativeMenuEntryOnlyHopsWhenItsOwnOnClickIsInvoked()
    {
        Fixture f=new Fixture();f.show();FriendsChatManager friends=mock(FriendsChatManager.class);FriendsChatMember friend=mock(FriendsChatMember.class);
        when(f.client.getFriendsChatManager()).thenReturn(friends);when(friends.findByName("A friend")).thenReturn(friend);when(friend.getWorld()).thenReturn(302);
        MenuEntry original=mock(MenuEntry.class);when(original.getOption()).thenReturn("Message");when(original.getTarget()).thenReturn("A friend");when(original.getParam1()).thenReturn(InterfaceID.FRIENDS<<16);
        when(f.client.getMenuEntries()).thenReturn(new MenuEntry[0]);MenuEntry added=mock(MenuEntry.class);when(f.client.createMenuEntry(-2)).thenReturn(added);
        when(added.setOption(anyString())).thenReturn(added);when(added.setTarget(anyString())).thenReturn(added);when(added.setType(any(MenuAction.class))).thenReturn(added);when(added.getTarget()).thenReturn("A friend");
        AtomicReference<Consumer<MenuEntry>> click=new AtomicReference<>();doAnswer(i->{click.set(i.getArgument(0));return added;}).when(added).onClick(any());
        f.tool.onMenuEntryAdded(new MenuEntryAdded(original));assertNotNull(click.get());verify(f.client,never()).createWorld();
        click.get().accept(added);f.clientTasks();verify(f.nativeWorld).setId(302);
    }
    @Test public void nativeBlockedMessageCancelsThePendingSwitcherRequest()
    {
        Fixture f=new Fixture();f.show();f.tool.selectWorld(302);f.clientTasks();
        ChatMessage blocked=new ChatMessage();blocked.setType(ChatMessageType.GAMEMESSAGE);blocked.setMessage("Please finish what you're doing before using the World Switcher.");f.tool.onChatMessage(blocked);
        when(f.client.getWidget(InterfaceID.Worldswitcher.BUTTONS)).thenReturn(mock(Widget.class));f.tick();
        verify(f.client,never()).hopToWorld(any());verify(f.client,never()).openWorldHopper();
    }
    @Test public void requestingARiskConfirmationCancelsAnOlderUnexecutedSafeHop()
    {
        Fixture f=new Fixture(List.of(world(301,500),world(302,500),world(303,500,WorldType.PVP)));f.show();
        f.tool.selectWorld(302);f.clientTasks();f.tool.selectWorld(303);f.clientTasks();assertNotNull(f.tool.snapshot().confirmation);
        when(f.client.getWidget(InterfaceID.Worldswitcher.BUTTONS)).thenReturn(mock(Widget.class));f.tick();verify(f.client,never()).hopToWorld(any());
    }
    @Test public void aPendingTargetWhoseWorldTypeChangesNeedsANewExplicitWarning()
    {
        Fixture f=new Fixture();f.show();f.tool.selectWorld(302);f.clientTasks();
        WorldResult pvp=new WorldResult();pvp.setWorlds(List.of(world(301,500),world(302,600,WorldType.PVP),world(303,700)));
        f.tool.onWorldsFetch(new WorldsFetch(pvp));f.clientTasks();when(f.client.getWidget(InterfaceID.Worldswitcher.BUTTONS)).thenReturn(mock(Widget.class));
        f.tick();verify(f.client,never()).hopToWorld(any());
        f.tool.selectWorld(302);f.clientTasks();assertNotNull(f.tool.snapshot().confirmation);long first=f.tool.snapshot().confirmation.token;
        f.tool.confirmHop(first);f.clientTasks();
        WorldResult highRisk=new WorldResult();highRisk.setWorlds(List.of(world(301,500),world(302,600,WorldType.PVP,WorldType.HIGH_RISK),world(303,700)));
        f.tool.onWorldsFetch(new WorldsFetch(highRisk));f.clientTasks();f.tick();verify(f.client,never()).hopToWorld(any());
        f.tool.selectWorld(302);f.clientTasks();assertNotNull(f.tool.snapshot().confirmation);
        assertNotEquals(first,f.tool.snapshot().confirmation.token);assertTrue(f.tool.snapshot().confirmation.message.contains("HIGH RISK"));
    }
    @Test public void hiddenModuleDoesNotFetchOrSchedulePingWorkOnItsOwn()
    {
        Fixture f=new Fixture();for(int i=0;i<10;i++)f.tick();
        assertEquals(0,f.worker.pending());verify(f.service,never()).refresh();assertEquals(0,f.pings.get());
        f.tool.setVisible(false);f.tool.setPingDemand(false);f.tool.refreshPings();f.clientTasks();
        assertEquals(0,f.worker.pending());
    }
    @Test public void repeatedPingRequestsHaveOneWorkerAndResultsCannotPublishAfterStop()
    {
        Fixture f=new Fixture();f.show();f.tool.refreshPings();f.tool.refreshPings();f.clientTasks();
        assertEquals("Ping sweeps must be single-flight",1,f.worker.pending());
        f.workerTasks();f.tool.stop();f.clientTasks();assertTrue(f.tool.snapshot().rows.isEmpty());assertFalse(f.tool.snapshot().pingRunning);
    }
    @Test public void aProfileChangeDiscardsLatePingResultsWithoutHoppingOrLeakingThePreviousList()
    {
        Fixture f=new Fixture();f.show();f.tool.refreshPings();f.clientTasks();f.workerTasks();
        f.profile.set("rsprofile-b");f.clientTasks();assertNotEquals("rsprofile-a",f.tool.snapshot().profile);
        assertFalse(f.tool.snapshot().pingRunning);verify(f.client,never()).createWorld();
    }
    @Test public void aMeasuredCurrentWorldPingRendersWithoutSchedulingWorkFromPaint()
    {
        RunewardenConfig display=new RunewardenConfig(){@Override public boolean worldHopDisplayPing(){return true;}};
        Fixture f=new Fixture(display);f.tick();f.workerTasks();f.clientTasks();assertTrue(f.tool.snapshot().currentPing>=0);
        WorldHopperPingOverlay overlay=new WorldHopperPingOverlay(f.tool,display);int probes=f.pings.get();
        Graphics2D graphics=new BufferedImage(240,110,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try{for(int i=0;i<10;i++)assertEquals(new Dimension(190,56),overlay.render(graphics));}finally{graphics.dispose();}
        assertEquals(probes,f.pings.get());assertEquals(0,f.worker.pending());
        f.state.set(GameState.LOGIN_SCREEN);GameStateChanged logout=new GameStateChanged();logout.setGameState(GameState.LOGIN_SCREEN);f.tool.onGameStateChanged(logout);
        Graphics2D loggedOut=new BufferedImage(240,110,BufferedImage.TYPE_INT_ARGB).createGraphics();try{assertNull(overlay.render(loggedOut));}finally{loggedOut.dispose();}
    }
    @Test public void leavingAndReturningToTheSameWorldRejectsThePreviousConnectionProbe()
    {
        Fixture f=new Fixture(new RunewardenConfig(){@Override public boolean worldHopDisplayPing(){return true;}});
        f.tick();assertEquals(1,f.worker.pending());f.workerTasks();
        f.current.set(302);f.tickWithoutClientTasks();f.current.set(301);f.tickWithoutClientTasks();f.clientTasks();
        assertTrue("A delayed probe from the previous visit must not become the new connection's latency",f.tool.snapshot().currentPing<0);
        assertTrue(f.tool.snapshot().currentLoss<0);
        // The cancelled B probe keeps its one-flight slot until the worker dequeues it.
        f.workerTasks();f.clientTasks();
        f.tool.refreshPings();f.clientTasks();f.workerTasks();f.clientTasks();assertTrue("A fresh probe still publishes normally",f.tool.snapshot().currentPing>=0);
    }

    static World world(int id,int players,WorldType...types){return world(id,players,"-",types);}
    static World world(int id,int players,String activity,WorldType...types)
    {return World.builder().id(id).players(players).address("world"+id+".runescape.com").activity(activity).region(WorldRegion.UNITED_KINGDOM).types(types.length==0?EnumSet.noneOf(WorldType.class):EnumSet.copyOf(List.of(types))).build();}

    static final class Fixture
    {
        final Client client=mock(Client.class);final ClientThread thread=mock(ClientThread.class);final ConfigManager config=mock(ConfigManager.class);
        final WorldService service=mock(WorldService.class);final KeyManager keys=mock(KeyManager.class);final WorldResult worlds=new WorldResult();
        final net.runelite.api.World nativeWorld=mock(net.runelite.api.World.class);final Worker worker=new Worker();final Queue<Runnable> queued=new ArrayDeque<>();
        final AtomicReference<String> profile=new AtomicReference<>("rsprofile-a");final AtomicReference<GameState> state=new AtomicReference<>(GameState.LOGGED_IN);
        final AtomicInteger current=new AtomicInteger(301),pings=new AtomicInteger(),nativeId=new AtomicInteger();final AtomicLong clock=new AtomicLong(100000);final AtomicBoolean onClient=new AtomicBoolean(),writesOnClient=new AtomicBoolean(true);
        final Map<String,String> saved=new HashMap<>();
        final WorldHopperTool tool;
        Fixture(){this(List.of(world(301,500),world(302,600),world(303,700)));}
        Fixture(RunewardenConfig options){this(List.of(world(301,500),world(302,600),world(303,700)),options);}
        Fixture(List<World> list){this(list,new RunewardenConfig(){});}
        Fixture(List<World> list,RunewardenConfig options)
        {
            worlds.setWorlds(list);when(service.getWorlds()).thenAnswer(i->{assertFalse("The initial service read can block and must not run on the client thread",onClient.get());assertFalse(SwingUtilities.isEventDispatchThread());return worlds;});when(config.getRSProfileKey()).thenAnswer(i->profile.get());
            when(config.getConfiguration(anyString(),nullable(String.class),anyString())).thenAnswer(i->saved.get(i.getArgument(0)+"|"+i.getArgument(1)+"|"+i.getArgument(2)));
            doAnswer(i->{saved.put(i.getArgument(0)+"|"+i.getArgument(1)+"|"+i.getArgument(2),String.valueOf((Object)i.getArgument(3)));return null;}).when(config).setConfiguration(anyString(),nullable(String.class),anyString(),anyString());
            doAnswer(i->{saved.put(i.getArgument(0)+"|"+i.getArgument(1)+"|"+i.getArgument(2),String.valueOf((Object)i.getArgument(3)));return null;}).when(config).setConfiguration(anyString(),nullable(String.class),anyString(),any(Boolean.class));
            when(client.getGameState()).thenAnswer(i->state.get());when(client.getWorld()).thenAnswer(i->current.get());when(client.isClientThread()).thenAnswer(i->onClient.get());
            when(client.getLocalPlayer()).thenReturn(mock(Player.class));when(client.getWorldType()).thenReturn(EnumSet.noneOf(net.runelite.api.WorldType.class));
            when(client.getTotalLevel()).thenReturn(2000);when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
            when(client.createWorld()).thenAnswer(i->{writesOnClient.compareAndSet(true,onClient.get());return nativeWorld;});
            doAnswer(i->{nativeId.set(i.getArgument(0));return null;}).when(nativeWorld).setId(anyInt());when(nativeWorld.getId()).thenAnswer(i->nativeId.get());
            doAnswer(i->{writesOnClient.compareAndSet(true,onClient.get());return null;}).when(client).openWorldHopper();
            doAnswer(i->{writesOnClient.compareAndSet(true,onClient.get());return null;}).when(client).hopToWorld(any());
            doAnswer(i->{writesOnClient.compareAndSet(true,onClient.get());return null;}).when(client).changeWorld(any());
            doAnswer(i->{queued.add(i.getArgument(0));return null;}).when(thread).invoke(any(Runnable.class));
            doAnswer(i->{queued.add(i.getArgument(0));return null;}).when(thread).invokeLater(any(Runnable.class));
            doAnswer(i->{BooleanSupplier action=i.getArgument(0);queued.add(()->action.getAsBoolean());return null;}).when(thread).invoke(any(BooleanSupplier.class));
            doAnswer(i->{BooleanSupplier action=i.getArgument(0);queued.add(()->action.getAsBoolean());return null;}).when(thread).invokeLater(any(BooleanSupplier.class));
            tool=new WorldHopperTool(client,thread,config,service,keys,options,worker,w->{pings.incrementAndGet();return w.getId()-250;},clock::get);
            tool.start();clientTasks();workerTasks();clientTasks();
        }
        void show(){tool.setVisible(true);clientTasks();}
        void tick(){tickWithoutClientTasks();clientTasks();}
        void tickWithoutClientTasks(){clock.addAndGet(600);onClient.set(true);try{tool.onGameTick();}finally{onClient.set(false);}}
        void clientTasks(){int bound=0;while(!queued.isEmpty()){assertTrue("Unbounded client task loop",++bound<100);onClient.set(true);try{queued.remove().run();}finally{onClient.set(false);}}}
        void workerTasks(){worker.runAll();}
        void population(int id,int amount){net.runelite.api.World world=mock(net.runelite.api.World.class);when(world.getId()).thenReturn(id);when(world.getPlayerCount()).thenReturn(amount);tool.onWorldListLoad(new WorldListLoad(new net.runelite.api.World[]{world}));}
        WorldHopperTool.WorldRow row(int id){return tool.snapshot().rows.stream().filter(r->r.id==id).findFirst().orElseThrow(()->new AssertionError("Missing world "+id+" in "+tool.snapshot().rows));}
    }
    private static final class Worker extends AbstractExecutorService
    {
        final Queue<Runnable> work=new ArrayDeque<>();boolean shutdown;
        int pending(){return work.size();}
        void runAll(){int bound=0;while(!work.isEmpty()){assertTrue("Unbounded ping work loop",++bound<50);work.remove().run();}}
        @Override public void execute(Runnable action){if(shutdown)throw new java.util.concurrent.RejectedExecutionException();work.add(action);}
        @Override public void shutdown(){shutdown=true;}
        @Override public List<Runnable> shutdownNow(){shutdown=true;List<Runnable> result=new ArrayList<>(work);work.clear();return result;}
        @Override public boolean isShutdown(){return shutdown;}
        @Override public boolean isTerminated(){return shutdown&&work.isEmpty();}
        @Override public boolean awaitTermination(long timeout,TimeUnit unit){return isTerminated();}
    }
}
