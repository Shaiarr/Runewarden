/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.laf.RuneLiteLAF;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CombatAchievementsToolTest
{
    private static Gson gson;
    private static CombatAchievementCatalog catalog;
    private static GuideCatalog guides;
    private Client client; private ConfigManager config; private CombatAchievementsTool tool;
    private final Map<Integer, Integer> bits = new HashMap<>();
    private final List<CombatAchievementCatalog.Task> popups = new ArrayList<>();
    private int nativePoints;

    @BeforeClass public static void load() { gson = new Gson(); catalog = new CombatAchievementCatalog(gson); guides = new GuideCatalog(gson); }
    @Before public void prepare()
    {
        client = mock(Client.class); config = mock(ConfigManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(config.getRSProfileKey()).thenReturn("character-a");
        when(client.getVarpValue(anyInt())).thenAnswer(invocation -> bits.getOrDefault(invocation.getArgument(0), 0));
        when(client.getVarbitValue(VarbitID.CA_POINTS)).thenAnswer(invocation -> nativePoints);
        when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
        tool = new CombatAchievementsTool(client, config, gson, catalog, guides);
        tool.setCompletionAction(popups::add); tool.start();
    }
    private void ticks(int count) { for (int index = 0; index < count; index++) tool.onGameTick(); }
    private void finish(int taskId)
    {
        int varp = CombatAchievementCatalog.COMPLETION_VARPS[taskId / 32];
        bits.put(varp, bits.getOrDefault(varp, 0) | (1 << (taskId % 32)));
        nativePoints += catalog.find(taskId).points;
    }

    @Test public void historicalLoginBitsEstablishBaselineWithoutCelebration()
    {
        finish(0); finish(654); ticks(11);
        assertTrue(tool.reading().notificationBaseline); assertEquals(2, tool.reading().finished.size());
        assertEquals(nativePoints, tool.reading().points); assertTrue(popups.isEmpty());
        ticks(10); assertTrue(popups.isEmpty());
    }
    @Test public void newKnownCompletionsEmitOnceAfterConsistentPointsAndSameProfile()
    {
        ticks(11); finish(0); ticks(5);
        assertEquals(1, popups.size()); assertEquals("Noxious Foe", popups.get(0).name);
        ticks(10); assertEquals(1, popups.size());
        finish(654); ticks(5); assertEquals(2, popups.size());
        assertEquals(654, popups.get(1).id);
    }
    @Test public void delayedInitialTransmitsAndPointMismatchDoNotGenerateOldPopups()
    {
        nativePoints = 1; ticks(11); assertFalse(tool.reading().notificationBaseline);
        finish(0); nativePoints = 1; ticks(15);
        assertTrue(tool.reading().notificationBaseline); assertTrue(popups.isEmpty());
        int varp = CombatAchievementCatalog.COMPLETION_VARPS[28 / 32]; bits.put(varp, bits.get(varp) | (1 << 28));
        ticks(5); assertTrue(popups.isEmpty()); nativePoints = 2; ticks(5); assertEquals(1, popups.size());
    }
    @Test public void accountChangesAndLogoutDiscardOldNotificationBaseline()
    {
        ticks(11); when(config.getRSProfileKey()).thenReturn("character-b"); finish(0); ticks(15);
        assertTrue(popups.isEmpty()); assertEquals("character-b", tool.reading().profile);
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); tool.onGameTick();
        assertFalse(tool.reading().observed()); assertTrue(tool.queued().isEmpty());
    }
    @Test public void pointRegressionRecalibratesInsteadOfCelebratingReloadedBits()
    {
        finish(0); ticks(11); bits.clear(); nativePoints = 0; ticks(5);
        assertFalse(tool.reading().notificationBaseline); finish(0); ticks(15); assertTrue(popups.isEmpty());
    }
    @Test public void explicitQueuePersistsAndStaleButtonsCannotMutateTheNextCharacter() throws Exception
    {
        ticks(11); AtomicReference<JButton> captured = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = tool.createPanel(); JButton queue = findButton(panel, "Queue next five"); captured.set(queue); queue.doClick();
        });
        assertEquals(5, tool.queued().size());
        verify(config, atLeastOnce()).setConfiguration(eq(RunewardenConfig.GROUP), eq("character-a"), eq(CombatAchievementsTool.PLAN_KEY), anyString());
        when(config.getRSProfileKey()).thenReturn("character-b"); ticks(1);
        SwingUtilities.invokeAndWait(() -> captured.get().doClick()); assertTrue(tool.queued().isEmpty());
        verify(config, never()).setConfiguration(eq(RunewardenConfig.GROUP), eq("character-b"), eq(CombatAchievementsTool.PLAN_KEY), anyString());
    }
    @Test public void savedQueueIsBoundedAndPreservesValidIdsAndTarget() throws Exception
    {
        when(config.getConfiguration(RunewardenConfig.GROUP, "character-a", CombatAchievementsTool.PLAN_KEY)).thenReturn("{\"target\":\"Hard\",\"queued\":[0,0,654,99999,-1]}");
        ticks(1); assertEquals(List.of(0, 654), tool.queued());
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = tool.createPanel(); JComboBox<?> target = (JComboBox<?>) descendants(panel).stream().filter(component -> "runewarden.ca.target".equals(component.getName())).findFirst().get();
            assertEquals("Hard", target.getSelectedItem());
        });
    }
    @Test public void nativeNarrowLayoutCreatesOnlyOnePagedViewAndNoHorizontalClipping() throws Exception
    {
        ticks(11);
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.LookAndFeel previous = javax.swing.UIManager.getLookAndFeel();
            try
            {
                assertTrue(RuneLiteLAF.setup()); JPanel panel = tool.createPanel(); panel.setSize(206, 2400);
                for (int pass = 0; pass < 4; pass++) layout(panel);
                int methods = 0;
                for (Component component : descendants(panel))
                {
                    if (component instanceof JButton && "Method".equals(((JButton) component).getText())) methods++;
                    if (component instanceof JButton && component.isVisible() && component.getWidth() > 0)
                    {
                        JButton button = (JButton) component;
                        int required = button.getFontMetrics(button.getFont()).stringWidth(button.getText()) + button.getInsets().left + button.getInsets().right;
                        assertTrue(button.getText() + " clipped at " + button.getWidth(), required <= button.getWidth());
                    }
                }
                assertEquals(6, methods); findButton(panel, "Method").doClick();
                assertNotNull(findButton(panel, "Back")); assertNotNull(findButton(panel, "Task Wiki"));
            }
            catch (Exception error) { throw new AssertionError(error); }
            finally { try { javax.swing.UIManager.setLookAndFeel(previous); } catch (Exception error) { throw new AssertionError(error); } }
        });
    }
    private static JButton findButton(Container root, String label)
    { return (JButton) descendants(root).stream().filter(component -> component instanceof JButton && label.equals(((JButton) component).getText())).findFirst().get(); }
    private static List<Component> descendants(Container root)
    { List<Component> result = new ArrayList<>(); for (Component component : root.getComponents()) { result.add(component); if (component instanceof Container) result.addAll(descendants((Container) component)); } return result; }
    private static void layout(Container root)
    { root.invalidate(); root.doLayout(); for (Component component : root.getComponents()) if (component instanceof Container && component.isVisible()) layout((Container) component); }
}
