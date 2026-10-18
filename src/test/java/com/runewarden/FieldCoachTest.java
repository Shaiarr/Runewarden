package com.runewarden;

import com.google.inject.Guice;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.itemstats.ItemStatChanges;
import net.runelite.client.plugins.itemstats.StatsChanges;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FieldCoachTest
{
    @Test public void radialWindowsResetOnlyOnRefreshAndPreserveUncertainty()
    {
        FieldCoach.NativeClock clock = new FieldCoach.NativeClock();
        clock.observe(5, 30, 100); assertEquals(150, clock.observedWindowTicks);
        FieldCoach.Range next = clock.observe(4, 30, 115);
        assertEquals(150, clock.observedWindowTicks); assertEquals(120, next.highTicks);
        clock.observe(10, 30, 116); assertEquals(300, clock.observedWindowTicks);
        clock.observe(0, 30, 117); assertEquals(0, clock.observedWindowTicks);
        FieldCoach.Range unknown = clock.observe(3, 30, 118);
        assertEquals(90, clock.observedWindowTicks); assertTrue(unknown.synchronizedPhase);
    }
    @Test public void effectDisplayModesChangeRenderedRowHeight()
    {
        Client client = mock(Client.class); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        ConfigManager configs=mock(ConfigManager.class);when(configs.getRSProfileKey()).thenReturn("alice");
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));when(client.getVarbitValue(VarbitID.ANTIFIRE_POTION)).thenReturn(5);
        FieldCoach coach=tool(client,configs,mock(Notifier.class));coach.start();coach.onGameTick();
        java.awt.Graphics2D g = new java.awt.image.BufferedImage(400, 400, java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            FieldStatusOverlay text = new FieldStatusOverlay(client, new RunewardenConfig() {
                @Override public TimerDisplay effectTimerDisplay() { return TimerDisplay.TEXT; }
            }, coach);
            FieldStatusOverlay radial = new FieldStatusOverlay(client, new RunewardenConfig() {
                @Override public TimerDisplay effectTimerDisplay() { return TimerDisplay.BOTH; }
            }, coach);
            assertEquals(49, text.render(g).height); assertEquals(59, radial.render(g).height);
        }
        finally { g.dispose(); }
    }
    @Test public void initialCoarseNativeBucketsRemainRangesUntilObservedChange()
    {
        FieldCoach.NativeClock clock = new FieldCoach.NativeClock();
        FieldCoach.Range first = clock.observe(5, 30, 100);
        assertEquals(120, first.lowTicks); assertEquals(150, first.highTicks); assertFalse(first.synchronizedPhase);
        assertEquals("1:12–1:30", first.text());
        assertFalse(clock.observe(5, 30, 101).synchronizedPhase);
        FieldCoach.Range next = clock.observe(4, 30, 115);
        assertTrue(next.synchronizedPhase); assertEquals(120, next.highTicks);
        assertEquals(115, clock.observe(4, 30, 120).highTicks);
    }
    @Test public void baselineZeroThenActivationSynchronizesAndRefreshResetsWarning()
    {
        FieldCoach.NativeClock clock = new FieldCoach.NativeClock(); clock.observe(0, 30, 1);
        assertTrue(clock.observe(10, 30, 2).synchronizedPhase);
        clock.warned = true; clock.observe(11, 30, 3); assertFalse(clock.warned);
        assertEquals(0, clock.observe(0, 30, 4).highTicks);
    }
    @Test public void stalledNativeValueNeverBecomesAClaimedExpiredEffect()
    {
        FieldCoach.NativeClock clock = new FieldCoach.NativeClock(); clock.observe(0, 10, 1); clock.observe(2, 10, 2);
        FieldCoach.Range late = clock.observe(2, 10, 100);
        assertFalse(late.synchronizedPhase); assertEquals(10, late.lowTicks); assertEquals(20, late.highTicks);
    }
    @Test public void tickValuesHaveNoInitialCoarseRange()
    {
        FieldCoach.Range range = new FieldCoach.NativeClock().observe(123, 1, 100);
        assertEquals(range.lowTicks, range.highTicks); assertEquals("1:14", range.text());
    }
    @Test public void sourceMappedEffectsSuppressCombinedDuplicatesAndClearOnHop()
    {
        Client client = mock(Client.class); ConfigManager config = mock(ConfigManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(config.getRSProfileKey()).thenReturn("alice");
        when(client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME)).thenReturn(400);
        when(client.getVarbitValue(VarbitID.DIVINEATTACK_POTION_TIME)).thenReturn(400);
        when(client.getVarbitValue(VarbitID.DIVINESTRENGTH_POTION_TIME)).thenReturn(400);
        when(client.getVarbitValue(VarbitID.DIVINEDEFENCE_POTION_TIME)).thenReturn(400);
        when(client.getVarbitValue(VarbitID.STAMINA_DURATION)).thenReturn(20); // inactive native stamina flag
        when(client.getRealSkillLevel(Skill.ATTACK)).thenReturn(99); when(client.getBoostedSkillLevel(Skill.ATTACK)).thenReturn(118);
        FieldCoach tool = tool(client, config, mock(Notifier.class)); tool.start(); tool.onGameTick();
        assertEquals(1, tool.snapshot().effects.size()); assertEquals("Divine combat", tool.snapshot().effects.get(0).label);
        assertEquals("118/99 (+19)", tool.snapshot().boosts.get(0).value);
        GameStateChanged hop = new GameStateChanged(); hop.setGameState(GameState.HOPPING); tool.onGameStateChanged(hop);
        assertTrue(tool.snapshot().effects.isEmpty()); assertTrue(tool.snapshot().boosts.isEmpty());
    }
    @Test public void poisonAndVenomImmunityAreNotConflated()
    {
        Client client = mock(Client.class); ConfigManager config = mock(ConfigManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(config.getRSProfileKey()).thenReturn("alice"); when(client.getVarpValue(VarPlayerID.POISON)).thenReturn(-43);
        FieldCoach tool = tool(client, config, mock(Notifier.class)); tool.start(); tool.onGameTick();
        assertEquals("Venom immunity", tool.snapshot().effects.get(0).label);
        when(client.getVarpValue(VarPlayerID.POISON)).thenReturn(-5); tool.onGameTick();
        assertEquals("Poison immunity", tool.snapshot().effects.get(0).label);
        when(client.getVarpValue(VarPlayerID.POISON)).thenReturn(1_000_000); tool.onGameTick();
        assertEquals("Venom", tool.snapshot().effects.get(0).label);
    }
    @Test public void accountSwitchRebaselinesTimerWithoutHistoricalAlerts()
    {
        Client client = mock(Client.class); ConfigManager config = mock(ConfigManager.class); Notifier notifier = mock(Notifier.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(config.getRSProfileKey()).thenReturn("alice"); when(config.getConfiguration(FieldCoach.GROUP, "alerts")).thenReturn("true");
        AtomicInteger tick = new AtomicInteger(10); when(client.getTickCount()).thenAnswer(call -> tick.get());
        when(client.getVarbitValue(VarbitID.ANTIFIRE_POTION)).thenReturn(1);
        FieldCoach tool = tool(client, config, notifier); tool.start(); tool.onGameTick();
        tick.set(100); when(config.getRSProfileKey()).thenReturn("bob"); tool.onGameTick();
        assertTrue(tool.snapshot().effects.get(0).value.contains("–")); verifyNoInteractions(notifier);
    }
    @Test public void nativeSharkShowsActualHealAndWaste()
    {
        Client client = mock(Client.class); when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(94);
        ItemStatChanges nativeEffects = Guice.createInjector().getInstance(ItemStatChanges.class);
        StatsChanges changes = nativeEffects.get(net.runelite.api.ItemID.SHARK).calculate(client);
        List<String> lines = ConsumablePreviewOverlay.describe("Shark", changes);
        assertEquals(2, lines.size()); assertEquals("Hitpoints +5 → 99 · wasted 15", lines.get(1));
    }
    @Test public void nativeBrewPreviewRetainsNegativeCombatEffects()
    {
        Client client = mock(Client.class);
        for (Skill skill : Skill.values()) { when(client.getRealSkillLevel(skill)).thenReturn(99); when(client.getBoostedSkillLevel(skill)).thenReturn(99); }
        ItemStatChanges effects = Guice.createInjector().getInstance(ItemStatChanges.class);
        List<String> lines = ConsumablePreviewOverlay.describe("Saradomin brew(4)", effects.get(net.runelite.api.ItemID.SARADOMIN_BREW4).calculate(client));
        assertTrue(lines.toString(), lines.stream().anyMatch(line -> line.startsWith("Attack -")));
        assertTrue(lines.toString(), lines.stream().anyMatch(line -> line.startsWith("Hitpoints +")));
    }
    @Test public void currentEffectKeysDistinguishInactiveFromUnknownAndReadinessCanTrustDisabledState()
    {
        Client client=mock(Client.class);ConfigManager config=mock(ConfigManager.class);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));when(config.getRSProfileKey()).thenReturn("rsprofile.one");
        FieldCoach field=tool(client,config,mock(Notifier.class));field.start();assertTrue(field.knownEffectKeys().isEmpty());field.onGameTick();assertTrue(field.knownEffectKeys().contains("antifire"));assertTrue(field.activeEffectKeys().isEmpty());
        when(client.getVarbitValue(VarbitID.ANTIFIRE_POTION)).thenReturn(10);field.onGameTick();assertTrue(field.activeEffectKeys().contains("antifire"));
        when(config.getConfiguration(FieldCoach.GROUP,"effects")).thenReturn("false");net.runelite.client.events.ConfigChanged changed=new net.runelite.client.events.ConfigChanged();changed.setGroup(FieldCoach.GROUP);field.onConfigChanged(changed);
        assertTrue(field.knownEffectKeys().isEmpty());assertTrue(field.activeEffectKeys().isEmpty());assertTrue(field.snapshot().effects.isEmpty());
    }
    @Test public void combinedDivineEffectsKeepNativeComponentEvidenceWhileSuppressingDuplicateHudRows()
    {
        Client client=mock(Client.class);ConfigManager config=mock(ConfigManager.class);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));when(config.getRSProfileKey()).thenReturn("rsprofile.one");
        when(client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME)).thenReturn(300);when(client.getVarbitValue(VarbitID.DIVINEATTACK_POTION_TIME)).thenReturn(300);when(client.getVarbitValue(VarbitID.DIVINESTRENGTH_POTION_TIME)).thenReturn(300);
        FieldCoach field=tool(client,config,mock(Notifier.class));field.start();field.onGameTick();assertEquals(1,field.snapshot().effects.size());assertTrue(field.activeEffectKeys().containsAll(java.util.Set.of("divine-combat","divine-attack","divine-strength")));
    }
    @Test public void profileChangesImmediatelyHideEffectFactsBeforeTheNextTickAndCallbacksStayReadOnly()
    {
        Client client=mock(Client.class);ConfigManager config=mock(ConfigManager.class);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));when(config.getRSProfileKey()).thenReturn("rsprofile.one");when(client.getVarbitValue(VarbitID.ANTIFIRE_POTION)).thenReturn(10);
        FieldCoach field=tool(client,config,mock(Notifier.class));field.start();field.onGameTick();assertFalse(field.snapshot().effects.isEmpty());when(config.getRSProfileKey()).thenReturn("rsprofile.two");clearInvocations(client);
        assertTrue(field.snapshot().effects.isEmpty());assertTrue(field.activeEffectKeys().isEmpty());assertTrue(field.knownEffectKeys().isEmpty());verifyNoInteractions(client);
    }
    @Test public void supplyRowsJoinTheHudOnlyWhenEffectsAreEnabledAndNeverBecomeEffectEvidence()
    {
        Client client=mock(Client.class);ConfigManager config=mock(ConfigManager.class);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));when(config.getRSProfileKey()).thenReturn("rsprofile.one");
        FieldCoach field=tool(client,config,mock(Notifier.class));field.setAdditionalRows(()->java.util.List.of(new FieldCoach.Row("Super restore","1 doses",true)));field.start();field.onGameTick();assertEquals("Super restore",field.snapshot().effects.get(0).label);assertTrue(field.activeEffectKeys().isEmpty());
        when(config.getConfiguration(FieldCoach.GROUP,"effects")).thenReturn("false");net.runelite.client.events.ConfigChanged changed=new net.runelite.client.events.ConfigChanged();changed.setGroup(FieldCoach.GROUP);field.onConfigChanged(changed);field.onGameTick();assertTrue(field.snapshot().effects.isEmpty());
    }
    @Test public void fieldHudSnapshotNeverPollsNativeStateOnTheSwingThread() throws Exception
    {
        Client client=mock(Client.class);ConfigManager config=mock(ConfigManager.class);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));when(config.getRSProfileKey()).thenReturn("rsprofile.one");
        FieldCoach field=tool(client,config,mock(Notifier.class));field.start();field.onGameTick();clearInvocations(client);
        javax.swing.SwingUtilities.invokeAndWait(()->{field.createPanel();field.snapshot();field.knownEffectKeys();field.activeEffectKeys();});verifyNoInteractions(client);
    }
    private static FieldCoach tool(Client client, ConfigManager config, Notifier notifier)
    { return new FieldCoach(client, config, new RunewardenConfig() {}, mock(ItemManager.class), mock(ItemStatChanges.class), new HoverCardOverlay(client), notifier); }
}
