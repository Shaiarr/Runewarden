/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AccountProgressToolTest
{
    @Test public void nativeTaskBitsAndRewardClaimAreIndependent()
    {
        Fixture fixture = new Fixture(); DiaryCatalog.Diary diary = fixture.diaries.find("ardougne_easy");
        DiaryCatalog.Task first = diary.tasks.get(0); fixture.varps.put(first.observation.variable, 1 << first.observation.bit);
        fixture.varbits.put(diary.claimedVariable, 1); fixture.tick(8);
        assertEquals(Long.valueOf(1), fixture.account.reading().value("diary:" + diary.id));
        assertEquals(Long.valueOf(1), fixture.account.reading().value("claimed:" + diary.id));
        assertEquals(Long.valueOf(1), fixture.account.progress(GoalPlan.progress("diary", diary.id, diary.tasks.size(), diary.name, List.of())));
    }
    @Test public void onlyPositiveCollectionTransmitFactsEstablishObtainedItems()
    {
        Fixture fixture = new Fixture(); fixture.tick(8);
        ItemContainer container = mock(ItemContainer.class); when(container.getItems()).thenReturn(new Item[] { new Item(4151, 3), new Item(11286, 0), new Item(-1, 0) });
        fixture.account.onItemContainerChanged(new ItemContainerChanged(InventoryID.COLLECTION_TRANSMIT, container)); fixture.tick(1);
        assertEquals(Long.valueOf(3), fixture.account.progress(GoalPlan.progress("collection", "4151", 1, "Whip", List.of())));
        assertNull(fixture.account.progress(GoalPlan.progress("collection", "11286", 1, "Visage", List.of())));
        assertEquals(1, fixture.account.reading().collection.size());
    }
    @Test public void publicMessagesCannotSpoofCollectionOrKillCounts()
    {
        Fixture fixture = new Fixture(); fixture.tick(8);
        fixture.account.onChatMessage(chat(ChatMessageType.PUBLICCHAT, "Your Brutus kill count is: 1,000."));
        fixture.account.onChatMessage(chat(ChatMessageType.PUBLICCHAT, "New item added to your collection log: Abyssal whip")); fixture.tick(1);
        assertNull(fixture.account.progress(GoalPlan.progress("kc", "Brutus", 1000, "Brutus", List.of())));
        assertTrue(fixture.account.reading().collection.isEmpty());
        fixture.account.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "Your Brutus kill count is: <col=ff0000>1,000</col>.")); fixture.tick(1);
        assertEquals(Long.valueOf(1000), fixture.account.progress(GoalPlan.progress("kc", "Brutus", 1000, "Brutus", List.of())));
    }
    @Test public void unobservedKillCountAndOwnershipDoNotBecomeZero()
    {
        Fixture fixture = new Fixture(); fixture.tick(8);
        assertNull(fixture.account.progress(GoalPlan.progress("kc", "TzKal-Zuk", 1, "Zuk", List.of())));
        assertNull(fixture.account.progress(GoalPlan.progress("item", "4151", 1, "Whip", List.of(4151))));
        assertNull(fixture.account.reading().value("metric:music-tracks"));
        assertNull(fixture.account.progress(GoalPlan.progress("ca","0",1,"Noxious Foe",List.of())));
    }
    @Test public void accountSwitchImmediatelyHidesPreviousFactsAndReloadsOnlyOwnObservations()
    {
        Fixture fixture = new Fixture(); fixture.tick(8);
        fixture.account.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "Your Brutus kill count is: 123.")); fixture.tick(1);
        assertEquals(Long.valueOf(123), fixture.account.progress(GoalPlan.progress("kc", "Brutus", 1000, "Brutus", List.of())));
        fixture.profile.set("rsprofile.b"); assertNull(fixture.account.reading().profile); fixture.tick(8);
        assertNull(fixture.account.progress(GoalPlan.progress("kc", "Brutus", 1000, "Brutus", List.of())));
        fixture.profile.set("rsprofile.a"); fixture.tick(8);
        assertEquals(Long.valueOf(123), fixture.account.progress(GoalPlan.progress("kc", "Brutus", 1000, "Brutus", List.of())));
    }
    @Test public void spendableBalancesAndPermanentUnlocksUseNamedNativeVariables()
    {
        Fixture fixture = new Fixture(); fixture.varbits.put(VarbitID.PRAYER_RIGOUR_UNLOCKED, 1); fixture.varps.put(VarPlayerID.GIANTS_FOUNDRY_REWARD_SHOP_POINTS, 4321); fixture.tick(8);
        assertEquals(Long.valueOf(1), fixture.account.progress(GoalPlan.progress("unlock", "rigour", 1, "Rigour", List.of())));
        assertEquals(Long.valueOf(4321), fixture.account.progress(GoalPlan.progress("currency", "foundry-points", 5000, "Foundry", List.of())));
        fixture.varps.put(VarPlayerID.GIANTS_FOUNDRY_REWARD_SHOP_POINTS, 100); fixture.tick(5);
        assertEquals(Long.valueOf(100), fixture.account.progress(GoalPlan.progress("currency", "foundry-points", 5000, "Foundry", List.of())));
    }
    @Test public void nativePopupOnlyCollectionModeIsObservedWithoutReplayingStaleText()
    {
        Fixture fixture = new Fixture(); fixture.tick(8);
        when(fixture.client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE)).thenReturn("Collection log");
        when(fixture.client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN)).thenReturn("New item: <col=ffff00>Abyssal whip</col>");
        fixture.account.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_DELAY)); fixture.tick(1); assertTrue(fixture.account.reading().collection.isEmpty());
        fixture.account.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_START)); fixture.account.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_DELAY)); fixture.tick(1);
        assertEquals(Long.valueOf(1), fixture.account.reading().collection.get(4151));
    }
    @Test public void diarySidebarBoundsItsTaskCardsAndUsesNativeWidth() throws Exception
    {
        Fixture fixture = new Fixture(); fixture.tick(8); DiaryPlannerTool diary = new DiaryPlannerTool(fixture.diaries, fixture.account, fixture.config);
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            net.runelite.client.ui.laf.RuneLiteLAF.setup(); javax.swing.JPanel panel = diary.createPanel(); panel.setSize(206, 2500); layout(panel); panel.setSize(206, panel.getPreferredSize().height); layout(panel);
            assertTrue(panel.getHeight() < 1800);
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(206, panel.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
            java.io.File file = new java.io.File("build/reports/diaries-206px.png"); file.getParentFile().mkdirs();
            try { javax.imageio.ImageIO.write(image, "png", file); } catch (java.io.IOException error) { throw new AssertionError(error); }
        });
    }
    @Test public void boostableDiarySkillsUseObservedBoostsButUnboostableTasksUseBase() {
        Fixture fixture = new Fixture(); when(fixture.client.getRealSkillLevel(Skill.MINING)).thenReturn(70);
        when(fixture.client.getBoostedSkillLevel(Skill.MINING)).thenReturn(75); fixture.tick(8);
        DiaryCatalog.Requirement need = new DiaryCatalog.Requirement(); need.kind = "skill"; need.key = "MINING"; need.value = 75; need.boostable = true;
        assertEquals(1, fixture.account.requirementState(need)); need.boostable = false; assertEquals(-1, fixture.account.requirementState(need));
    }
    @Test public void accountOpportunitiesPrioritizeRealUnclaimedRewardsAndKeepTargetsDetached() {
        Fixture fixture = new Fixture(); DiaryCatalog.Diary diary = fixture.diaries.find("ardougne_easy");
        for (DiaryCatalog.Task task : diary.tasks) if ("varpBit".equals(task.observation.kind)) fixture.varps.merge(task.observation.variable, 1 << task.observation.bit, (a,b) -> a | b);
        fixture.tick(11); java.util.List<AccountProgressTool.Opportunity> opportunities = fixture.account.opportunities();
        AccountProgressTool.Opportunity reward = opportunities.stream().filter(value -> value.id.equals("claim:ardougne_easy")).findFirst().get();
        assertEquals(0, reward.priority); assertNull(reward.goal());
        AccountProgressTool.Opportunity unlock = opportunities.stream().filter(value -> value.id.equals("unlock:rigour")).findFirst().get();
        GoalPlan target = unlock.goal(); target.label = "tampered"; assertEquals("Rigour", unlock.goal().label); assertFalse(unlock.itemTargets.isEmpty());
        try { opportunities.clear(); fail(); } catch (UnsupportedOperationException expected) { }
        fixture.profile.set("rsprofile.b"); assertTrue(fixture.account.opportunities().isEmpty());
    }
    @Test public void goalsWithEmptyExplicitItemsResolveValidatedCaDiaryAndMusicSupplies() {
        Fixture fixture = new Fixture(); int row = 900001;
        when(fixture.client.getDBTableRows(net.runelite.api.gameval.DBTableID.Music.ID)).thenReturn(List.of(row));
        when(fixture.client.getDBTableField(row,net.runelite.api.gameval.DBTableID.Music.COL_DISPLAYNAME,0)).thenReturn(new Object[]{"Cave of the Goblins"});
        when(fixture.client.getDBTableField(row,net.runelite.api.gameval.DBTableID.Music.COL_HIDDEN,0)).thenReturn(new Object[]{0});
        when(fixture.client.getDBTableField(row,net.runelite.api.gameval.DBTableID.Music.COL_VARIABLE,0)).thenReturn(new Object[]{1,0});
        fixture.tick(11);
        GoalCatalog catalog = new GoalCatalog(fixture.guides,new ActivityXpCatalog(fixture.gson,fixture.guides),fixture.account);
        GoalPlan ca = GoalPlan.progress("ca","0",1,"Noxious Foe",List.of());
        GoalPlan diary = GoalPlan.progress("diary","ardougne_easy",12,"Ardougne easy",List.of());
        GoalPlan music = GoalPlan.progress("music",Integer.toString(row),1,"Cave of the Goblins",List.of());
        assertTrue(catalog.goalSupplyTargets(ca).contains(4168)); // Verified nose peg, not the source's incidental NPC link.
        assertTrue(catalog.goalSupplyTargets(diary).contains(686)); // Native rusty sword requirement.
        assertTrue(catalog.goalSupplyTargets(music).contains(954)); // Exact sourced rope route.
        assertFalse(catalog.goalSupplyTargets(ca).contains(4144)); // Aberrant Spectre link is not an item requirement.
        net.runelite.client.callback.ClientThread thread=mock(net.runelite.client.callback.ClientThread.class);
        doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
        GoalPlan.Data goals=new GoalPlan.Data();goals.goals=List.of(ca,diary,music);fixture.data.put("runewarden:rsprofile.a:"+GoalPlan.KEY,fixture.gson.toJson(goals));
        GoalPlannerTool planner=new GoalPlannerTool(fixture.client,thread,fixture.config,fixture.gson,catalog,new RunewardenDataStore(fixture.config,fixture.gson));planner.start();for(int tick=0;tick<10;tick++)planner.onGameTick();
        assertTrue(planner.goalItemTargets().containsAll(List.of(4168,686,954)));
        ca.archived=true;diary.achievedAt=1;music.achievedAt=1;assertTrue(catalog.goalSupplyTargets(ca).isEmpty());assertTrue(catalog.goalSupplyTargets(diary).isEmpty());
        fixture.data.put("runewarden:rsprofile.a:"+GoalPlan.KEY,fixture.gson.toJson(goals));planner.stop();planner.start();for(int tick=0;tick<10;tick++)planner.onGameTick();assertTrue(planner.goalItemTargets().isEmpty());
        fixture.profile.set("rsprofile.b");assertTrue(planner.goalItemTargets().isEmpty());planner.stop();
        assertTrue(catalog.goalSupplyTargets(GoalPlan.progress("currency","foundry-points",1000,"Points",List.of())).isEmpty());
    }
    private static void layout(java.awt.Container parent)
    { parent.doLayout(); for (java.awt.Component child : parent.getComponents()) if (child instanceof java.awt.Container) layout((java.awt.Container) child); }
    private static ChatMessage chat(ChatMessageType type, String text)
    { ChatMessage event = new ChatMessage(); event.setType(type); event.setMessage(text); return event; }
    private static final class Fixture
    {
        final Gson gson = new Gson(); final Client client = mock(Client.class); final ConfigManager config = mock(ConfigManager.class);
        final AtomicReference<String> profile = new AtomicReference<>("rsprofile.a"); final Map<Integer, Integer> varps = new HashMap<>(), varbits = new HashMap<>();
        final Map<String, String> data = new HashMap<>(); final DiaryCatalog diaries = new DiaryCatalog(gson);
        final AccountProgressTool account; final MusicUnlockTool music; final CombatAchievementsTool combat; final MusicUnlockCatalog tracks; final GuideCatalog guides;
        Fixture()
        {
            when(config.getRSProfileKey()).thenAnswer(call -> profile.get()); when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
            when(client.getVarpValue(anyInt())).thenAnswer(call -> varps.getOrDefault(call.getArgument(0), 0)); when(client.getVarbitValue(anyInt())).thenAnswer(call -> varbits.getOrDefault(call.getArgument(0), 0));
            when(config.getConfiguration(anyString(), anyString(), anyString())).thenAnswer(call -> data.get(call.getArgument(0) + ":" + call.getArgument(1) + ":" + call.getArgument(2)));
            doAnswer(call -> { data.put(call.getArgument(0) + ":" + call.getArgument(1) + ":" + call.getArgument(2), call.getArgument(3)); return null; }).when(config).setConfiguration(anyString(), anyString(), anyString(), anyString());
            guides = new GuideCatalog(gson); tracks = new MusicUnlockCatalog(gson, guides); CombatAchievementCatalog tasks = new CombatAchievementCatalog(gson);
            music = new MusicUnlockTool(client, config, tracks); combat = new CombatAchievementsTool(client, config, gson, tasks, guides);
            RunewardenDataStore store = new RunewardenDataStore(config, gson); OwnershipLedger ownership = new OwnershipLedger(client, config, gson, () -> 1L, store);
            account = new AccountProgressTool(client, config, gson, diaries, new RarityCatalog(gson), music, combat, ownership, store, tracks, tasks);
            music.start(); combat.start(); account.start();
        }
        void tick(int count) { for (int index = 0; index < count; index++) { music.onGameTick(); combat.onGameTick(); account.onGameTick(); } }
    }
}
