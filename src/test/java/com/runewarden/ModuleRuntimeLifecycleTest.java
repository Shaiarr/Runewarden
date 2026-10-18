package com.runewarden;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ModuleRuntimeLifecycleTest
{
    @Test public void savedDisabledClientModuleNeverStartsFromQueuedInitialization()
    {
        Fixture f=new Fixture();AtomicInteger starts=new AtomicInteger(),ticks=new AtomicInteger();
        when(f.config.getConfiguration(ModuleRuntime.GROUP,"bankxp")).thenReturn("false");
        f.runtime.registerDeferred("Bank & XP",starts::incrementAndGet,()->{});
        f.queued.remove(0).run();f.runtime.tick("Bank & XP",ticks::incrementAndGet);
        assertFalse(f.runtime.enabled("Bank & XP"));assertEquals(0,starts.get());assertEquals(0,ticks.get());
    }
    @Test public void queuedInitializationHonorsDisableAndCannotReviveAnOldLifecycle()
    {
        Fixture f=new Fixture();AtomicInteger starts=new AtomicInteger();
        f.runtime.registerDeferred("Skilling coach",starts::incrementAndGet,()->{});
        when(f.config.getConfiguration(ModuleRuntime.GROUP,"skillingcoach")).thenReturn("false");
        f.runtime.changed();f.queued.remove(0).run();assertEquals(0,starts.get());
        f.runtime.registerDeferred("Test",starts::incrementAndGet,()->{});
        f.runtime.clear();f.queued.remove(0).run();assertEquals(0,starts.get());
    }
    @Test public void earlyEnableStartsOnlyOnceAndOldEnableCannotAffectReplacement()
    {
        Fixture f=new Fixture();AtomicInteger starts=new AtomicInteger();
        f.runtime.registerDeferred("Test",starts::incrementAndGet,()->{});
        f.runtime.enable("Test");f.queued.remove(1).run();f.queued.remove(0).run();assertEquals(1,starts.get());
        f.runtime.enable("Test");f.runtime.clear();
        AtomicInteger replacementStarts=new AtomicInteger();
        f.runtime.registerDeferred("Test",replacementStarts::incrementAndGet,()->{});
        f.queued.remove(0).run();assertEquals(0,replacementStarts.get());
        f.queued.remove(0).run();assertEquals(1,replacementStarts.get());
    }
    @Test public void queuedOldToggleCannotStopServiceAfterRestart() throws Exception
    {
        Fixture f = new Fixture(); AtomicInteger oldStops = new AtomicInteger(), newStops = new AtomicInteger();
        f.runtime.registerStarted("Test", () -> { }, oldStops::incrementAndGet);
        SwingUtilities.invokeAndWait(() -> toggle(f.runtime.createPanel(), "Test").doClick());
        assertEquals(1, f.queued.size()); f.runtime.clear(); f.runtime.registerStarted("Test", () -> { }, newStops::incrementAndGet);
        f.queued.remove(0).run(); assertTrue(f.runtime.enabled("Test")); assertEquals(0, oldStops.get()); assertEquals(0, newStops.get());
        verify(f.config, never()).setConfiguration(eq(ModuleRuntime.GROUP), eq("test"), eq(false));
    }
    @Test public void replacingModuleIdentityAlsoRejectsOldToggleWithoutFullClear() throws Exception
    {
        Fixture f = new Fixture(); AtomicInteger oldStops = new AtomicInteger(), newStops = new AtomicInteger();
        f.runtime.registerStarted("Test", () -> { }, oldStops::incrementAndGet);
        SwingUtilities.invokeAndWait(() -> toggle(f.runtime.createPanel(), "Test").doClick());
        f.runtime.registerStarted("Test", () -> { }, newStops::incrementAndGet); f.queued.remove(0).run();
        assertTrue(f.runtime.enabled("Test")); assertEquals(0, oldStops.get()); assertEquals(0, newStops.get());
    }
    @Test public void queuedActionUsesTheClickedChoiceInsteadOfLaterCheckboxState() throws Exception
    {
        Fixture f = new Fixture(); AtomicInteger stops = new AtomicInteger(); f.runtime.registerStarted("Test", () -> { }, stops::incrementAndGet);
        SwingUtilities.invokeAndWait(() -> { JCheckBox box = toggle(f.runtime.createPanel(), "Test"); box.doClick(); box.setSelected(true); });
        f.queued.remove(0).run(); assertFalse(f.runtime.enabled("Test")); assertEquals(1, stops.get());
        verify(f.config).setConfiguration(ModuleRuntime.GROUP, "test", false);
    }
    private static JCheckBox toggle(Container root, String title)
    {
        for (Component child : root.getComponents())
        { if (child instanceof JCheckBox && title.equals(((JCheckBox) child).getText())) return (JCheckBox) child;
            if (child instanceof Container) { JCheckBox found = toggle((Container) child, title); if (found != null) return found; } }
        return null;
    }
    private static final class Fixture
    {
        final ConfigManager config = mock(ConfigManager.class); final ClientThread thread = mock(ClientThread.class);
        final List<Runnable> queued = new ArrayList<>(); final ModuleRuntime runtime;
        Fixture()
        { doAnswer(call -> { queued.add(call.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class)); runtime = new ModuleRuntime(config, thread); }
    }
}
