package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RecoveryBoundaryTest
{
    private static final String GROUP = "runewardenfieldtest", ALICE = "rsprofile.alice", BOB = "rsprofile.bob", KEY = "personalV1";
    static final class Value { int version = 1, count = 7; }
    @Test public void characterBackupExcludesCrossProfileRecoveryJournalEvenFromDynamicGroups()
    {
        Fixture f = new Fixture(); String journal = "{\"profile\":\"rsprofile.bob\",\"good\":\"private inventory\"}";
        when(f.config.getConfigurationKeys("runewarden")).thenReturn(Arrays.asList(RunewardenDataStore.GROUP + "." + RunewardenDataStore.KEY, "runewardenfield.effects"));
        when(f.config.getConfigurationKeys(RunewardenDataStore.GROUP + ".")).thenReturn(List.of(RunewardenDataStore.GROUP + "." + RunewardenDataStore.KEY));
        when(f.config.getConfigurationKeys("runewardenfield.")).thenReturn(List.of("runewardenfield.effects"));
        f.settings.put(address(RunewardenDataStore.GROUP, null, RunewardenDataStore.KEY), journal); f.settings.put(address("runewardenfield", null, "effects"), "true");
        String backup = f.tool.exportBackup(); assertTrue(backup.contains("runewardenfield"));
        assertFalse(backup.contains("rsprofile.bob")); assertFalse(backup.contains("private inventory")); assertFalse(backup.contains(RunewardenDataStore.GROUP));
    }
    @Test public void importingRecoveryJournalIsRejected()
    {
        try { RecoveryTool.parse("{\"settings\":[{\"group\":\"runewardenrecovery\",\"key\":\"safeSavesV1\",\"value\":\"{}\"}]}", new Gson()); fail(); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void staleRecoveryCardAndQueuedRecoveryCannotMutateAnotherCharacter() throws Exception
    {
        Fixture f = new Fixture(); f.corruptAlice(); List<Runnable> actions = new ArrayList<>(); f.tool.setApplyAction(actions::add);
        AtomicReference<JButton> recover = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { recover.set(button(f.tool.createPanel(), "Recover last valid save")); assertNotNull(recover.get()); recover.get().doClick(); });
        assertEquals(1, actions.size()); f.profile.set(BOB); actions.remove(0).run();
        assertEquals("corrupt-original", f.settings.get(address(GROUP, ALICE, KEY))); assertTrue(f.store.blocked(GROUP, ALICE, KEY));
        SwingUtilities.invokeAndWait(() -> recover.get().doClick()); assertTrue(actions.isEmpty());
    }
    @Test public void problemsDiscoveredAfterPanelCreationAppearOnRefresh() throws Exception
    {
        Fixture f = new Fixture(); AtomicReference<JPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { panel.set(f.tool.createPanel()); panel.get().addNotify(); });
        try
        {
            assertTrue(panel.get().isShowing()); assertNull(button(panel.get(), "Recover last valid save"));
            f.corruptAlice(); f.tool.refresh(); SwingUtilities.invokeAndWait(() -> { });
            assertNotNull(button(panel.get(), "Recover last valid save"));
        }
        finally { SwingUtilities.invokeAndWait(() -> panel.get().removeNotify()); }
    }
    @Test public void accountSwitchAfterRestoreWasQueuedCannotWriteEitherCharacter()
    {
        Fixture f = new Fixture(); List<Runnable> actions = new ArrayList<>(); f.tool.setApplyAction(actions::add);
        f.tool.previewImport("{\"profile\":\"rsprofile.alice\",\"settings\":[{\"group\":\"runewardenfieldtest\",\"key\":\"personalV1\",\"value\":\"reviewed\",\"character\":true}]}");
        f.tool.restorePreview(); assertEquals(1, actions.size()); f.profile.set(BOB); actions.remove(0).run();
        assertNull(f.settings.get(address(GROUP, ALICE, KEY))); assertNull(f.settings.get(address(GROUP, BOB, KEY)));
    }
    private static JButton button(Container root, String title)
    {
        for (Component child : root.getComponents())
        { if (child instanceof JButton && title.equals(((JButton) child).getText())) return (JButton) child;
            if (child instanceof Container) { JButton found = button((Container) child, title); if (found != null) return found; } }
        return null;
    }
    private static String address(String group, String profile, String key) { return group + "/" + profile + "/" + key; }
    private static final class Fixture
    {
        final ConfigManager config = mock(ConfigManager.class); final Map<String, String> settings = new HashMap<>();
        final AtomicReference<String> profile = new AtomicReference<>(ALICE); final RunewardenDataStore store; final RecoveryTool tool;
        Fixture()
        {
            when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
            when(config.getConfiguration(anyString(), anyString())).thenAnswer(call -> settings.get(address(call.getArgument(0), null, call.getArgument(1))));
            when(config.getConfiguration(anyString(), nullable(String.class), anyString())).thenAnswer(call -> settings.get(address(call.getArgument(0), call.getArgument(1), call.getArgument(2))));
            doAnswer(call -> { settings.put(address(call.getArgument(0), null, call.getArgument(1)), String.valueOf((Object) call.getArgument(2))); return null; })
                .when(config).setConfiguration(anyString(), anyString(), any());
            doAnswer(call -> { settings.put(address(call.getArgument(0), call.getArgument(1), call.getArgument(2)), String.valueOf((Object) call.getArgument(3))); return null; })
                .when(config).setConfiguration(anyString(), nullable(String.class), anyString(), any());
            store = new RunewardenDataStore(config, new Gson()); tool = new RecoveryTool(config, new Gson(), store);
        }
        void corruptAlice()
        {
            store.write(GROUP, ALICE, KEY, 1, new Value()); settings.put(address(GROUP, ALICE, KEY), "corrupt-original");
            store.read(GROUP, ALICE, KEY, 1, Value.class, v -> v.count >= 0, Value::new);
        }
    }
}
