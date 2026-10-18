/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SlayerCoachToolTest
{
    private static SlayerTaskCatalog tasks;
    private static MobKnowledgeCatalog monsters;
    private static GuideCatalog guides;
    private Client client;
    private ConfigManager config;
    private ItemManager items;
    private SlayerCoachTool tool;

    @BeforeClass public static void catalog()
    {
        Gson gson = new Gson();
        tasks = new SlayerTaskCatalog(gson);
        monsters = new MobKnowledgeCatalog(gson);
        guides = new GuideCatalog(gson);
    }

    @Before public void prepare()
    {
        client = mock(Client.class); config = mock(ConfigManager.class); items = mock(ItemManager.class);
        when(config.getRSProfileKey()).thenReturn("character-a");
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(10);
        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(50);
        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL)).thenReturn(100);
        when(client.getVarbitValue(VarbitID.SLAYER_POINTS)).thenReturn(250);
        when(client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED)).thenReturn(20);
        when(client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, 10)).thenReturn(Collections.singletonList(200));
        when(client.getDBTableField(200, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)).thenReturn(new Object[]{"Bloodveld"});
        tool = new SlayerCoachTool(client, config, items, tasks, monsters, guides);
        tool.start();
    }

    @Test public void readsLiveAssignmentWithoutNativeSlayerAndCachesIdentity()
    {
        tool.onGameTick(); tool.onGameTick();
        SlayerCoachTool.State state = tool.reading();
        assertEquals("Bloodveld", state.name);
        assertEquals(50, state.remaining); assertEquals(100, state.initial);
        assertEquals(250, state.points); assertEquals(20, state.streak);
        verify(client, times(1)).getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, 10);
        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(49);
        tool.onGameTick(); assertEquals(49, tool.reading().remaining);
        assertFalse(tool.reading().monsters.isEmpty());
    }

    @Test public void resolvesBossSublistAndAssignedLocationFromPublicGameTables()
    {
        when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(98);
        when(client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID)).thenReturn(4);
        when(client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID, DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, 4)).thenReturn(Collections.singletonList(201));
        when(client.getDBTableField(201, DBTableID.SlayerTaskSublist.COL_TASK, 0)).thenReturn(new Object[]{202});
        when(client.getDBTableField(202, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)).thenReturn(new Object[]{"Sarachnis"});
        when(client.getVarpValue(VarPlayerID.SLAYER_AREA)).thenReturn(3);
        when(client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, 3)).thenReturn(Collections.singletonList(300));
        when(client.getDBTableField(300, DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0)).thenReturn(new Object[]{"Forthos Dungeon"});
        tool.onGameTick();
        assertEquals("Sarachnis", tool.reading().name); assertEquals("Forthos Dungeon", tool.reading().location);
    }

    @Test public void appliesCurrentTaskAmountModifiersAndCorrectStreakTrack()
    {
        when(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID)).thenReturn(2);
        when(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE)).thenReturn(15);
        when(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.SLAYER_MASTER)).thenReturn(7);
        when(client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED)).thenReturn(12);
        tool.onGameTick();
        assertEquals(85, tool.reading().initial); assertEquals(12, tool.reading().streak);
        when(client.getVarbitValue(VarbitID.SLAYER_MASTER)).thenReturn(10);
        when(client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED)).thenReturn(7);
        tool.onGameTick(); assertEquals(7, tool.reading().streak);
    }

    @Test public void accountChangeLogoutAndTaskCompletionClearOldReference()
    {
        tool.onGameTick();
        when(config.getRSProfileKey()).thenReturn("character-b");
        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(0);
        tool.onGameTick(); assertEquals("No active assignment", tool.reading().name);
        assertTrue(tool.reading().monsters.isEmpty());
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        tool.onGameTick(); assertEquals(0, tool.reading().points); assertEquals(0, tool.reading().remaining);
    }

    @Test public void missingDbDataDoesNotReuseLastAssignmentsName()
    {
        tool.onGameTick();
        when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(55);
        when(client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, 55)).thenReturn(Collections.emptyList());
        tool.onGameTick();
        assertEquals("Assignment data loading", tool.reading().name);
        assertTrue(tool.reading().monsters.isEmpty());
        assertEquals(50, tool.reading().remaining);
    }

    @Test public void equipmentChecksDistinguishCarriedProtectionFromWornProtection()
    {
        when(client.getDBTableField(200, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)).thenReturn(new Object[]{"Aberrant spectres"});
        ItemContainer inventory = mock(ItemContainer.class);
        when(inventory.getItems()).thenReturn(new Item[]{new Item(4168, 1)});
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
        tool.onGameTick();
        assertFalse(tool.reading().gear.get(0).ready);
        assertEquals("In inventory · equip", tool.reading().gear.get(0).status);
        ItemContainer worn = mock(ItemContainer.class);
        when(worn.getItems()).thenReturn(new Item[]{new Item(11865, 1)});
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(worn);
        ItemComposition helmet = mock(ItemComposition.class);
        when(helmet.getName()).thenReturn("Slayer helmet (i)"); when(items.getItemComposition(11865)).thenReturn(helmet);
        tool.onGameTick(); assertTrue(tool.reading().gear.get(0).ready);
    }

    @Test public void pinnedTaskAliasesContainBossAlternativesWithoutSubstringFalseMatches()
    {
        assertEquals(152, tasks.taskCount());
        assertTrue(tasks.task("Hellhounds").hints().contains("Cerberus"));
        assertTrue(tasks.task("Cows").hints().contains("Brutus"));
        MobKnowledgeCatalog catalog = new MobKnowledgeCatalog(new Gson());
        java.util.List<MobKnowledgeCatalog.Entry> goblins = catalog.matching(tasks.task("Goblins").hints());
        assertFalse(goblins.isEmpty());
        assertFalse(goblins.stream().anyMatch(monster -> monster.name.equals("Hobgoblin")));
        assertTrue(catalog.matching(tasks.task("Hellhounds").hints()).stream().anyMatch(monster -> monster.name.equals("Cerberus")));
    }

    @Test public void stoppedToolDoesNotReadClientState()
    {
        tool.stop(); clearInvocations(client); tool.onGameTick(); verifyNoInteractions(client);
    }

    @Test public void narrowRuneLiteSidebarUsesFullWidthAndCollapsedReferenceDetails() throws Exception
    {
        when(client.getDBTableField(200, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)).thenReturn(new Object[]{"Metal Dragons"});
        tool.onGameTick();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            javax.swing.LookAndFeel previous = javax.swing.UIManager.getLookAndFeel();
            try
            {
                assertTrue(net.runelite.client.ui.laf.RuneLiteLAF.setup());
                javax.swing.JPanel panel = tool.createPanel();
                panel.setSize(206, 1800);
                for (int pass = 0; pass < 3; pass++) layout(panel);
                assertNarrowLayout(panel);
                javax.swing.JButton details = buttons(panel).stream().filter(button -> "More combat details".equals(button.getText())).findFirst().get();
                assertFalse(texts(panel).stream().anyMatch(text -> text.getText().startsWith("Magic defence bonus:")));
                details.doClick();
                assertEquals("Hide combat details", details.getText());
                for (int pass = 0; pass < 3; pass++) layout(panel);
                assertTrue(texts(panel).stream().anyMatch(text -> text.getText().startsWith("Magic defence bonus:")));
                assertNarrowLayout(panel);
            }
            catch (Exception error) { throw new AssertionError(error); }
            finally
            {
                try { javax.swing.UIManager.setLookAndFeel(previous); }
                catch (Exception error) { throw new AssertionError(error); }
            }
        });
    }


    @Test public void sharedActivityCountsAssignmentCreditsRatherThanClaimingNpcKills()
    {
        tool.onGameTick(); assertEquals(0, tool.activityObservation().observedCredits);
        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(48); tool.onGameTick();
        assertEquals(2, tool.activityObservation().observedCredits);
        when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(0);
        when(client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED)).thenReturn(21);
        when(client.getVarbitValue(VarbitID.SLAYER_POINTS)).thenReturn(265);tool.onGameTick();
        assertEquals(50, tool.activityObservation().observedCredits);assertEquals(1, tool.activityObservation().completedTasks);assertEquals(15, tool.activityObservation().pointsGained);
    }
    @Test public void cancelledAssignmentDoesNotInventRemainingCreditsOrCompletion()
    {
        tool.onGameTick();when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(0);tool.onGameTick();
        assertEquals(0,tool.activityObservation().observedCredits);assertEquals(0,tool.activityObservation().completedTasks);
    }
    @Test public void sharedActivitySnapshotCannotLeakThePreviousProfile()
    {
        tool.onGameTick();when(config.getRSProfileKey()).thenReturn("character-b");
        assertNull(tool.activityObservation().profile);tool.onGameTick();assertEquals(0,tool.activityObservation().observedCredits);
    }

    private static void layout(java.awt.Container container)
    {
        container.invalidate();
        container.doLayout();
        for (java.awt.Component component : container.getComponents())
            if (component instanceof java.awt.Container && component.isVisible()) layout((java.awt.Container) component);
    }

    private static void assertNarrowLayout(java.awt.Container container)
    {
        for (java.awt.Component component : container.getComponents())
        {
            if (!component.isVisible()) continue;
            if (component instanceof GuideText || component instanceof javax.swing.JButton
                || component instanceof javax.swing.JComboBox || component instanceof javax.swing.JProgressBar)
            {
                java.awt.Insets insets = container.getInsets();
                int available = container.getWidth() - insets.left - insets.right;
                assertEquals("Left edge of " + component.getClass().getSimpleName(), insets.left, component.getX());
                assertTrue("Full width for " + component.getClass().getSimpleName() + ": " + component.getBounds(), component.getWidth() >= available - 1);
                assertTrue("Vertical clipping of " + component.getClass().getSimpleName() + ": " + component.getBounds(),
                    component.getY() + component.getHeight() <= container.getHeight() - insets.bottom);
                if (component instanceof javax.swing.JButton)
                {
                    javax.swing.JButton button = (javax.swing.JButton) component;
                    int textWidth = button.getFontMetrics(button.getFont()).stringWidth(button.getText());
                    assertTrue("Button text clipped: " + button.getText(), textWidth + button.getInsets().left + button.getInsets().right <= button.getWidth());
                }
            }
            if (component instanceof java.awt.Container && !(component instanceof javax.swing.JComboBox)) assertNarrowLayout((java.awt.Container) component);
        }
    }

    private static java.util.List<javax.swing.JButton> buttons(java.awt.Container root)
    {
        java.util.List<javax.swing.JButton> result = new java.util.ArrayList<>();
        for (java.awt.Component child : root.getComponents())
        {
            if (child instanceof javax.swing.JButton) result.add((javax.swing.JButton) child);
            if (child instanceof java.awt.Container) result.addAll(buttons((java.awt.Container) child));
        }
        return result;
    }

    private static java.util.List<GuideText> texts(java.awt.Container root)
    {
        java.util.List<GuideText> result = new java.util.ArrayList<>();
        for (java.awt.Component child : root.getComponents())
        {
            if (!child.isVisible()) continue;
            if (child instanceof GuideText) result.add((GuideText) child);
            if (child instanceof java.awt.Container) result.addAll(texts((java.awt.Container) child));
        }
        return result;
    }
}
