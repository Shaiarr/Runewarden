package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GuardedActivityPreferencesTest
{
    @Test public void futureMinigamePreferencesCannotBeOverwrittenByCheckboxEdits() throws Exception
    {
        Fixture f = new Fixture(); String future = "{\"version\":2,\"autoHud\":false,\"futureSetting\":17}";
        f.saved.put(MinigameCoach.GROUP + "/" + MinigameCoach.KEY, future);
        MinigameCoach coach = new MinigameCoach(mock(Client.class), new MinigameActivityCatalog(), f.config, new Gson(), new RunewardenConfig() {}, f.store);
        coach.start(); SwingUtilities.invokeAndWait(() -> ((JCheckBox) named(coach.createPanel(), "minigameAutoHud")).doClick());
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(future, f.saved.get(MinigameCoach.GROUP + "/" + MinigameCoach.KEY)); assertTrue(coach.preferences().autoHud);
        assertTrue(f.store.blocked(MinigameCoach.GROUP, null, MinigameCoach.KEY)); coach.stop();
    }
    @Test public void malformedMinigamePreferencesRetainLastGoodUntilRecovery() throws Exception
    {
        Fixture f = new Fixture(); f.saved.put(MinigameCoach.GROUP + "/" + MinigameCoach.KEY, "{\"autoGuide\":true}");
        MinigameCoach coach = new MinigameCoach(mock(Client.class), new MinigameActivityCatalog(), f.config, new Gson(), new RunewardenConfig() {}, f.store);
        coach.start(); assertTrue(coach.preferences().autoGuide);
        f.saved.put(MinigameCoach.GROUP + "/" + MinigameCoach.KEY, "{broken"); coach.reloadPreferences();
        assertTrue(coach.preferences().autoGuide); assertEquals("{broken", f.saved.get(MinigameCoach.GROUP + "/" + MinigameCoach.KEY));
        assertTrue(f.store.recover(new RunewardenDataStore.Address(MinigameCoach.GROUP, null, MinigameCoach.KEY))); coach.stop();
    }
    @Test public void futureGroundPreferencesCannotBeOverwrittenByDisplayChanges() throws Exception
    {
        Fixture f = new Fixture(); String future = "{\"version\":2,\"targets\":{\"946\":\"Knife\"}}";
        f.saved.put(GroundLootTool.GROUP + "/" + GroundLootTool.KEY, future);
        GroundLootTool tool = new GroundLootTool(mock(Client.class), f.config, mock(ItemManager.class), new Gson(), f.store);
        SwingUtilities.invokeAndWait(() -> ((JCheckBox) named(tool.createPanel(), "groundLootEnabled")).doClick());
        assertEquals(future, f.saved.get(GroundLootTool.GROUP + "/" + GroundLootTool.KEY)); assertTrue(tool.preferences().enabled);
        assertTrue(f.store.blocked(GroundLootTool.GROUP, null, GroundLootTool.KEY)); tool.stop();
    }
    @Test public void malformedGroundPreferencesRetainKnownTargetsWithoutDestroyingOriginal()
    {
        Fixture f = new Fixture(); f.saved.put(GroundLootTool.GROUP + "/" + GroundLootTool.KEY, "{\"targets\":{\"946\":\"Knife\"}}");
        GroundLootTool tool = new GroundLootTool(mock(Client.class), f.config, mock(ItemManager.class), new Gson(), f.store);
        assertTrue(tool.preferences().targets.containsKey(946)); f.saved.put(GroundLootTool.GROUP + "/" + GroundLootTool.KEY, "{broken"); tool.reloadPreferences();
        assertTrue(tool.preferences().targets.containsKey(946)); assertEquals("{broken", f.saved.get(GroundLootTool.GROUP + "/" + GroundLootTool.KEY)); tool.stop();
    }
    private static Component named(Container root, String name)
    {
        for (Component child : root.getComponents())
        { if (name.equals(child.getName())) return child; if (child instanceof Container) { Component found = named((Container) child, name); if (found != null) return found; } }
        return null;
    }
    private static final class Fixture
    {
        final ConfigManager config = mock(ConfigManager.class); final Map<String, String> saved = new HashMap<>(); final RunewardenDataStore store;
        Fixture()
        {
            when(config.getConfiguration(anyString(), anyString())).thenAnswer(call -> saved.get(call.getArgument(0) + "/" + call.getArgument(1)));
            doAnswer(call -> { saved.put(call.getArgument(0) + "/" + call.getArgument(1), String.valueOf((Object) call.getArgument(2))); return null; })
                .when(config).setConfiguration(anyString(), anyString(), any());
            doAnswer(call -> { saved.remove(call.getArgument(0) + "/" + call.getArgument(1)); return null; })
                .when(config).unsetConfiguration(anyString(), anyString());
            store = new RunewardenDataStore(config, new Gson());
        }
    }
}
