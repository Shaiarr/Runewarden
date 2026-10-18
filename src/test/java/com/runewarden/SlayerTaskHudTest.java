/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SlayerTaskHudTest
{
    @Test public void nativeTaskReportsKillProgressAndClearsOnCompletion()
    {
        Fixture f=new Fixture();f.start();SlayerTaskHud.Reading first=f.tool.reading();
        assertEquals("Gargoyles",first.name);assertEquals(120,first.remaining);assertEquals(150,first.initial);
        assertEquals(88,first.points);assertEquals(25,first.streak);assertEquals(4147,first.iconId);assertTrue(first.context);
        f.varps.put(VarPlayerID.SLAYER_COUNT,119);f.tick();assertEquals(119,f.tool.reading().remaining);
        f.varps.put(VarPlayerID.SLAYER_COUNT,0);f.tick();assertFalse(f.tool.reading().active());
        verify(f.config,never()).setRSProfileConfiguration(anyString(),anyString(),any());
    }
    @Test public void completionRewardIsAnObservedIncreaseWithinABoundedWindow()
    {
        Fixture f=new Fixture();f.start();f.varps.put(VarPlayerID.SLAYER_COUNT,0);f.tick();assertEquals(-1,f.tool.lastCompletionPoints());
        f.bits.put(VarbitID.SLAYER_POINTS,103);f.tick();assertEquals(15,f.tool.lastCompletionPoints());
        f.profile.set("second");assertEquals(-1,f.tool.lastCompletionPoints());
        Fixture expired=new Fixture();expired.start();expired.varps.put(VarPlayerID.SLAYER_COUNT,0);expired.tick();for(int i=0;i<6;i++)expired.tick();
        expired.bits.put(VarbitID.SLAYER_POINTS,500);expired.tick();assertEquals(-1,expired.tool.lastCompletionPoints());
    }
    @Test public void taskChangesAndUnavailableOrMalformedDbRowsNeverRetainTheOldTask()
    {
        Fixture f=new Fixture();f.start();f.name="Banshees";f.tick();assertEquals("Banshees",f.tool.reading().name);assertTrue(f.tool.reading().itemNote.contains("Earmuffs"));
        f.unknown=true;f.tick();assertFalse(f.tool.reading().active());f.unknown=false;f.badName=true;f.tick();assertFalse(f.tool.reading().active());
    }
    @Test public void characterConfigAndLogoutSwitchesHideImmediatelyAndWarmUpAgain()
    {
        Fixture f=new Fixture();f.tool.start();for(int i=0;i<3;i++){f.tick();assertFalse(f.tool.reading().active());}f.tick();assertTrue(f.tool.reading().active());
        f.profile.set("second");assertFalse(f.tool.reading().active());f.tick();assertFalse(f.tool.reading().active());for(int i=0;i<3;i++)f.tick();assertTrue(f.tool.reading().active());
        f.configId.incrementAndGet();assertFalse(f.tool.reading().active());f.tick();assertFalse(f.tool.reading().active());for(int i=0;i<3;i++)f.tick();assertTrue(f.tool.reading().active());
        f.state.set(GameState.LOGIN_SCREEN);assertFalse(f.tool.reading().active());f.tick();f.state.set(GameState.LOGGED_IN);f.tick();assertFalse(f.tool.reading().active());
        f.tool.stop();assertFalse(f.tool.reading().active());
    }
    @Test public void appropriateNormalWildernessAndMortimerStreaksAreSelected()
    {
        Fixture f=new Fixture();f.bits.put(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED,17);f.varps.put(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED,9);f.start();
        assertEquals("Task streak",f.tool.reading().streakLabel);f.bits.put(VarbitID.SLAYER_MASTER,7);f.tick();assertEquals(17,f.tool.reading().streak);assertEquals("Wilderness streak",f.tool.reading().streakLabel);
        f.bits.put(VarbitID.SLAYER_MASTER,10);f.tick();assertEquals(9,f.tool.reading().streak);assertEquals("Mortimer streak",f.tool.reading().streakLabel);
    }
    @Test public void nativeModifierAndBossSublistResolveThroughPublicDbTables()
    {
        Fixture f=new Fixture();f.bits.put(VarbitID.SLAYER_MODIFIER_ID,2);f.bits.put(VarbitID.SLAYER_MODIFIER_VALUE,20);f.start();assertEquals(170,f.tool.reading().initial);
        f.bits.put(VarbitID.SLAYER_MODIFIER_NEGATIVE,1);f.tick();assertEquals(130,f.tool.reading().initial);
        f.varps.put(VarPlayerID.SLAYER_TARGET,98);f.bits.put(VarbitID.SLAYER_TARGET_BOSSID,35);f.name="The Abyssal Sire";f.tick();assertEquals("The Abyssal Sire",f.tool.reading().name);assertEquals(13262,f.tool.reading().iconId);
        verify(f.client).getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,0,35);
    }
    @Test public void taskContextRequiresNearbyActionableAliasAndKnownAssignedArea()
    {
        Fixture f=new Fixture();f.start();assertTrue(f.tool.reading().context);
        when(f.npc.getWorldLocation()).thenReturn(new WorldPoint(3400,3550,1));f.tick();assertFalse(f.tool.reading().context);
        when(f.npc.getWorldLocation()).thenReturn(new WorldPoint(3402,3550,0));when(f.npcType.getActions()).thenReturn(new String[]{"Talk-to"});f.tick();assertFalse(f.tool.reading().context);
        when(f.npcType.getActions()).thenReturn(new String[]{"Attack"});f.name="Black demons";when(f.npcType.getName()).thenReturn("Demonic gorilla");f.tick();assertTrue(f.tool.reading().context);
        f.varps.put(VarPlayerID.SLAYER_AREA,1);f.area="Unknown dungeon";f.tick();assertFalse(f.tool.reading().context);assertEquals("Unknown dungeon",f.tool.reading().location);
        f.area="Slayer Tower";f.location=new WorldPoint(3420,3550,0);when(f.npc.getWorldLocation()).thenAnswer(call->f.location);f.tick();assertTrue(f.tool.reading().context);
        when(f.world.isInstance()).thenReturn(true);f.tick();assertFalse(f.tool.reading().context);
    }
    @Test public void bossTaskArticleIsOptionalForNpcNamesWithoutAcceptingPartialWords()
    {
        Fixture f=new Fixture();f.name="The Abyssal Sire";
        f.varps.put(VarPlayerID.SLAYER_TARGET,98);f.bits.put(VarbitID.SLAYER_TARGET_BOSSID,35);
        when(f.npcType.getName()).thenReturn("Abyssal Sire");f.start();
        assertEquals("The Abyssal Sire",f.tool.reading().name);assertEquals(13262,f.tool.reading().iconId);
        assertTrue(f.tool.reading().context);
        when(f.npcType.getName()).thenReturn("Abyssal Sireling");f.tick();assertFalse(f.tool.reading().context);
        when(f.npcType.getName()).thenReturn("Sire");f.tick();assertFalse(f.tool.reading().context);
        when(f.npcType.getName()).thenReturn("The Abyssal Sire");f.tick();assertTrue(f.tool.reading().context);
    }
    @Test public void geometryBoundariesAndUnknownAreasFailConservatively()
    {
        assertTrue(SlayerTaskHud.areaMatches("Catacombs of Kourend",new WorldPoint(1600,10000,0)));
        assertFalse(SlayerTaskHud.areaMatches("Catacombs of Kourend",new WorldPoint(1600,10000,1)));
        assertFalse(SlayerTaskHud.areaMatches("Catacombs of Kourend",new WorldPoint(1600,3550,0)));
        assertTrue(SlayerTaskHud.areaMatches("Slayer Tower",new WorldPoint(3420,3550,2)));
        assertFalse(SlayerTaskHud.areaMatches("Unrecorded cave",new WorldPoint(3420,3550,2)));
    }
    @Test public void overlayShowsNativeIconOnlyInContextAndFitsItsReportedBounds()
    {
        Fixture f=new Fixture();f.start();RunewardenConfig config=mock(RunewardenConfig.class);ItemManager items=mock(ItemManager.class);
        when(config.slayerTaskHud()).thenReturn(true);when(config.hudOpacity()).thenReturn(85);
        when(items.getImage(4147)).thenReturn(new net.runelite.client.util.AsyncBufferedImage(
            mock(net.runelite.client.callback.ClientThread.class),30,28,BufferedImage.TYPE_INT_ARGB));
        SlayerTaskOverlay overlay=new SlayerTaskOverlay(f.tool,config,items);BufferedImage image=new BufferedImage(500,500,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try
        {
            java.awt.Dimension size=overlay.render(g);assertNotNull(size);assertEquals(268,size.width);verify(items).getImage(4147);
            for(int y=0;y<500;y++)for(int x=0;x<500;x++)if(x>=size.width||y>=size.height)assertEquals(0,image.getRGB(x,y));
            when(f.npcType.getName()).thenReturn("Cow");f.tick();assertNull(overlay.render(g));
        }
        finally{g.dispose();}
    }
    @Test public void nativeAssignmentAreasAreBoundedCachedAndFilteredToTheAssignedArea() throws Exception
    {
        Fixture f=new Fixture();f.varps.put(VarPlayerID.SLAYER_AREA,1);
        when(f.client.getDBRowsByValue(DBTableID.SlayerMasterTask.ID,DBTableID.SlayerMasterTask.COL_MASTER_ID,0,0)).thenReturn(List.of(4000));
        when(f.client.getDBTableField(4000,DBTableID.SlayerMasterTask.COL_TASK,0)).thenReturn(new Object[]{1000});
        when(f.client.getDBTableField(4000,DBTableID.SlayerMasterTask.COL_AREAS,0)).thenReturn(new Object[]{5000});
        when(f.client.getDBTableField(4000,DBTableID.SlayerMasterTask.COL_AREAS,1)).thenReturn(new Object[]{0});
        when(f.client.getDBTableField(5000,DBTableID.SlayerArea.COL_AREA_ID,0)).thenReturn(new Object[]{1});
        when(f.client.getDBTableField(5000,DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER,0)).thenReturn(new Object[]{"Slayer Tower"});
        when(f.client.getDBTableField(5000,DBTableID.SlayerArea.COL_AREA_HINT,0)).thenReturn(new Object[]{"North of Canifis"});
        f.start();for(int i=0;i<10;i++)f.tick();
        verify(f.client,times(1)).getDBRowsByValue(DBTableID.SlayerMasterTask.ID,DBTableID.SlayerMasterTask.COL_MASTER_ID,0,0);
        AtomicReference<JPanel> panel=new AtomicReference<>();SwingUtilities.invokeAndWait(()->panel.set(f.tool.createPanel()));
        JPanel body=(JPanel)panel.get().getComponent(1),card=(JPanel)body.getComponent(1);
        assertTrue(((GuideText)card.getComponent(3)).getText().contains("North of Canifis"));
    }
    @Test public void areaHintsRetryAfterNativeRowsFinishLoading() throws Exception
    {
        Fixture f=new Fixture();
        when(f.client.getDBRowsByValue(DBTableID.SlayerMasterTask.ID,DBTableID.SlayerMasterTask.COL_MASTER_ID,0,0)).thenReturn(null,List.of(4000));
        when(f.client.getDBTableField(4000,DBTableID.SlayerMasterTask.COL_TASK,0)).thenReturn(new Object[]{1000});
        when(f.client.getDBTableField(4000,DBTableID.SlayerMasterTask.COL_AREAS,0)).thenReturn(new Object[]{5000});
        when(f.client.getDBTableField(4000,DBTableID.SlayerMasterTask.COL_AREAS,1)).thenReturn(new Object[]{0});
        when(f.client.getDBTableField(5000,DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER,0)).thenReturn(new Object[]{"Slayer Tower"});
        when(f.client.getDBTableField(5000,DBTableID.SlayerArea.COL_AREA_HINT,0)).thenReturn(new Object[]{"North of Canifis"});
        f.start();for(int i=0;i<8;i++)f.tick();
        verify(f.client,times(2)).getDBRowsByValue(DBTableID.SlayerMasterTask.ID,DBTableID.SlayerMasterTask.COL_MASTER_ID,0,0);
        AtomicReference<JPanel> panel=new AtomicReference<>();SwingUtilities.invokeAndWait(()->panel.set(f.tool.createPanel()));
        JPanel body=(JPanel)panel.get().getComponent(1),card=(JPanel)body.getComponent(1);
        assertTrue(((GuideText)card.getComponent(3)).getText().contains("North of Canifis"));
    }
    @Test public void sidebarKeepsComponentsStableAsTaskProgressChanges() throws Exception
    {
        Fixture f=new Fixture();f.start();AtomicReference<JPanel> panel=new AtomicReference<>();SwingUtilities.invokeAndWait(()->panel.set(f.tool.createPanel()));
        JPanel body=(JPanel)panel.get().getComponent(1);Component task=body.getComponent(1);Component count=((JPanel)task).getComponent(1);
        f.varps.put(VarPlayerID.SLAYER_COUNT,119);f.tick();SwingUtilities.invokeAndWait(()->{});
        assertSame(task,body.getComponent(1));assertSame(count,((JPanel)task).getComponent(1));assertTrue(((GuideText)count).getText().contains("119"));
    }
    private static final class Fixture
    {
        final Client client=mock(Client.class);final ConfigManager config=mock(ConfigManager.class);
        final Player player=mock(Player.class);final NPC npc=mock(NPC.class);final NPCComposition npcType=mock(NPCComposition.class);final WorldView world=mock(WorldView.class);
        final AtomicReference<String> profile=new AtomicReference<>("first");final AtomicReference<GameState> state=new AtomicReference<>(GameState.LOGGED_IN);final AtomicLong configId=new AtomicLong(1);
        final Map<Integer,Integer> varps=new HashMap<>(),bits=new HashMap<>();String name="Gargoyles",area="Slayer Tower";boolean unknown,badName;WorldPoint location=new WorldPoint(3400,3550,0);
        final SlayerTaskHud tool;
        Fixture()
        {
            when(client.getGameState()).thenAnswer(call->state.get());when(client.getLocalPlayer()).thenReturn(player);when(client.getTopLevelWorldView()).thenReturn(world);
            when(player.getWorldLocation()).thenAnswer(call->location);when(npc.getWorldLocation()).thenReturn(new WorldPoint(3402,3550,0));when(client.getNpcs()).thenReturn(List.of(npc));
            when(npc.getTransformedComposition()).thenReturn(npcType);when(npcType.getName()).thenReturn("Gargoyle");when(npcType.getActions()).thenReturn(new String[]{"Attack"});
            when(config.getRSProfileKey()).thenAnswer(call->profile.get());ConfigProfile selected=mock(ConfigProfile.class);when(selected.getId()).thenAnswer(call->configId.get());when(config.getProfile()).thenReturn(selected);
            varps.put(VarPlayerID.SLAYER_COUNT,120);varps.put(VarPlayerID.SLAYER_COUNT_ORIGINAL,150);varps.put(VarPlayerID.SLAYER_TARGET,42);
            bits.put(VarbitID.SLAYER_POINTS,88);bits.put(VarbitID.SLAYER_TASKS_COMPLETED,25);
            when(client.getVarpValue(anyInt())).thenAnswer(call->varps.getOrDefault(call.getArgument(0),0));when(client.getVarbitValue(anyInt())).thenAnswer(call->bits.getOrDefault(call.getArgument(0),0));
            when(client.getDBRowsByValue(anyInt(),anyInt(),eq(0),any())).thenAnswer(call->unknown?List.of():List.of((Integer)call.getArgument(0)==DBTableID.SlayerArea.ID?2000:(Integer)call.getArgument(0)==DBTableID.SlayerTaskSublist.ID?3000:1000));
            when(client.getDBTableField(anyInt(),anyInt(),eq(0))).thenAnswer(call->new Object[]{(Integer)call.getArgument(0)==3000?1000:(Integer)call.getArgument(0)==2000?area:badName?123:name});
            tool=new SlayerTaskHud(client,config,new Gson());
        }
        void start(){tool.start();for(int i=0;i<4;i++)tick();}
        void tick(){tool.onGameTick();}
    }
}
