/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

public class SlayerPlanningToolTest
{
    @Test public void nativeRowsProvideWeightsAmountsStatRequirementsAreasAndUnlockText()
    {
        Fixture fixture = new Fixture(); SlayerPlanningTool.NativeTask task = fixture.tool.readTask(100);
        assertNotNull(task); assertEquals("Abyssal demons", task.name); assertEquals(8, task.weight); assertEquals(120, task.minimum); assertEquals(170, task.maximum);
        assertEquals(85, task.all.get(0).level); assertEquals(Skill.SLAYER, task.all.get(0).skill);
        assertEquals("Catacombs of Kourend", task.areas.get(0).name); assertEquals(7, task.areas.get(0).id);
        assertEquals("Bigger and Badder · Unlock superior encounters", task.unlock);
    }
    @Test public void missingNativeIdentityDoesNotBecomeAGuessedTask()
    {
        Fixture fixture = new Fixture(); when(fixture.client.getDBTableField(200, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)).thenReturn(null);
        assertNull(fixture.tool.readTask(100));
    }
    @Test public void actualBlockStateDoesNotChangeWhenSavingPersonalSkipPreference() throws Exception
    {
        Fixture fixture = new Fixture(); fixture.bits.put(VarbitID.SLAYER_BLOCKED_VANNAKA_1, 38); fixture.tick(6);
        SlayerPlanningTool.Snapshot state = fixture.tool.reading(); assertTrue(state.blocksObserved); assertTrue(state.blocked.contains(38));
        SlayerPlanningTool.Rule rule = new SlayerPlanningTool.Rule(); rule.decision = "SKIP"; rule.note = "Prefer fast cannon tasks today";
        assertTrue(fixture.tool.saveRule(state, "Abyssal demons", rule)); assertEquals("SKIP", fixture.tool.rule("Abyssal demons").decision);
        assertTrue(fixture.tool.reading().blocked.contains(38));
        assertFalse(mockingDetails(fixture.client).getInvocations().stream().anyMatch(call -> call.getMethod().getName().startsWith("set")));
    }
    @Test public void staleAccountOrMasterCannotOverwriteTheNextPlan() throws Exception
    {
        Fixture fixture = new Fixture(); fixture.tick(6); SlayerPlanningTool.Snapshot first = fixture.tool.reading();
        SlayerPlanningTool.Rule rule = new SlayerPlanningTool.Rule(); rule.decision = "BLOCK";
        fixture.profile.set("rsprofile.next"); assertFalse(fixture.tool.saveRule(first, "Abyssal demons", rule)); fixture.tick(6);
        assertEquals("DO", fixture.tool.rule("Abyssal demons").decision);
        SlayerPlanningTool.Snapshot second = fixture.tool.reading(); fixture.master.set(8); fixture.tick(6);
        assertFalse(fixture.tool.saveRule(second, "Abyssal demons", rule));
    }
    @Test public void personalPreferencesPersistPerMasterWithoutNativeInputs() throws Exception
    {
        Fixture fixture = new Fixture(); fixture.tick(6); SlayerPlanningTool.Rule rule = new SlayerPlanningTool.Rule(); rule.decision = "PREFER"; rule.effort = "Relaxed"; rule.note = "Use my dragon hunter crossbow";
        assertTrue(fixture.tool.saveRule(fixture.tool.reading(), "Steel dragons", rule));
        fixture.tool.clear(); fixture.tick(6); assertEquals("PREFER", fixture.tool.rule("Steel dragons").decision); assertEquals("Relaxed", fixture.tool.rule("Steel dragons").effort);
        fixture.master.set(8); fixture.tick(6); assertEquals("DO", fixture.tool.rule("Steel dragons").decision);
    }
    @Test public void unknownMasterAndInvalidRulesRemainUnknownOrRejected()
    {
        assertNull(SlayerPlanningTool.blockSlots(9)); assertNull(SlayerPlanningTool.blockSlots(99));
        SlayerPlanningTool.Plan plan = new SlayerPlanningTool.Plan(); SlayerPlanningTool.Rule rule = new SlayerPlanningTool.Rule(); rule.decision = null; plan.masters.put(3, Map.of("test", rule)); assertFalse(SlayerPlanningTool.valid(plan));
        rule.decision = "DO"; rule.note = "<script>"; assertFalse(SlayerPlanningTool.valid(plan));
    }
    @Test public void storageStatusUsesNativeUnlockAndMasterAndClearsImmediatelyOnProfileChange()
    {
        Fixture fixture = new Fixture(); fixture.bits.put(VarbitID.SLAYER_UNLOCK_STORAGE,1); fixture.bits.put(VarbitID.SLAYER_MASTER_STORED,8);
        fixture.nativeField(DBTableID.SlayerUnlock.Row.SLAYER_REWARDS_UNLOCK_STORAGE,DBTableID.SlayerUnlock.COL_COST,500); fixture.tick(6);
        assertTrue(fixture.tool.reading().storageUnlocked); assertEquals(8,fixture.tool.reading().storedMaster); assertEquals(Integer.valueOf(500),fixture.tool.reading().storageUnlockCost);
        fixture.profile.set("rsprofile.other"); assertFalse(fixture.tool.reading().storageUnlocked); assertEquals(0,fixture.tool.reading().storedMaster);
    }
    private static final class Fixture
    {
        final Gson gson = new Gson(); final Client client = mock(Client.class); final ConfigManager config = mock(ConfigManager.class);
        final AtomicReference<String> profile = new AtomicReference<>("rsprofile.slayer"); final AtomicInteger master = new AtomicInteger(3);
        final Map<Integer, Integer> bits = new HashMap<>(); final Map<String, String> data = new HashMap<>(); final SlayerPlanningTool tool;
        Fixture()
        {
            when(config.getRSProfileKey()).thenAnswer(call -> profile.get()); when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99); when(client.getVarbitValue(anyInt())).thenAnswer(call -> bits.getOrDefault(call.getArgument(0), 0)); when(client.getVarbitValue(VarbitID.SLAYER_MASTER)).thenAnswer(call -> master.get());
            when(config.getConfiguration(anyString(), anyString(), anyString())).thenAnswer(call -> data.get(call.getArgument(0) + ":" + call.getArgument(1) + ":" + call.getArgument(2)));
            doAnswer(call -> { data.put(call.getArgument(0) + ":" + call.getArgument(1) + ":" + call.getArgument(2), call.getArgument(3)); return null; }).when(config).setConfiguration(anyString(), anyString(), anyString(), anyString());
            when(client.getDBRowsByValue(eq(DBTableID.SlayerMasterTask.ID), eq(DBTableID.SlayerMasterTask.COL_MASTER_ID), eq(0), any())).thenReturn(List.of(100));
            nativeField(100, DBTableID.SlayerMasterTask.COL_TASK, 200); nativeField(100, DBTableID.SlayerMasterTask.COL_WEIGHT, 8); nativeField(100, DBTableID.SlayerMasterTask.COL_MIN_AMOUNT, 120); nativeField(100, DBTableID.SlayerMasterTask.COL_MAX_AMOUNT, 170);
            nativeField(200, DBTableID.SlayerTask.COL_NAME_UPPERCASE, "Abyssal demons"); nativeField(200, DBTableID.SlayerTask.COL_ID, 38); nativeField(200, DBTableID.SlayerTask.COL_MIN_COMLEVEL, 85);
            when(client.getDBTableField(200, DBTableID.SlayerTask.COL_MIN_STAT_REQUIREMENT_ALL, 0)).thenReturn(new Object[]{85, Skill.SLAYER.ordinal()});
            nativeField(100, DBTableID.SlayerMasterTask.COL_AREAS, 300); nativeField(300, DBTableID.SlayerArea.COL_AREA_ID, 7); nativeField(300, DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, "Catacombs of Kourend"); nativeField(300, DBTableID.SlayerArea.COL_AREA_HINT, "Use the central statue entrance");
            nativeField(100, DBTableID.SlayerMasterTask.COL_TASK_UNLOCK, 400); nativeField(400, DBTableID.SlayerUnlock.COL_NAME, "Bigger and Badder"); nativeField(400, DBTableID.SlayerUnlock.COL_DESCRIPTION, "Unlock superior encounters");
            tool = new SlayerPlanningTool(client, config, new RunewardenDataStore(config, gson), new GuideCatalog(gson), new SlayerTaskCatalog(gson)); tool.start(); visible();
        }
        private void nativeField(int row, int column, Object value) { when(client.getDBTableField(row, column, 0)).thenReturn(new Object[]{value}); }
        private void visible() { try { Field visible = SlayerPlanningTool.class.getDeclaredField("visible"); visible.setAccessible(true); visible.set(tool, true); } catch (ReflectiveOperationException error) { throw new AssertionError(error); } }
        void tick(int count) { visible(); for (int index = 0; index < count; index++) tool.onGameTick(); }
    }
}
