/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.laf.RuneLiteLAF;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Runs the real native PluginPanel wrapper; large isolated panels hid the original alignment bug. */
public class SidebarLayoutTest
{
    @Test public void everyModuleUsesTheNativeSidebarWidthWithoutCompressedWrappedText() throws Exception
    {
        Fixture fixtures = new Fixture();
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.LookAndFeel previous = UIManager.getLookAndFeel();
            try
            {
                assertTrue(RuneLiteLAF.setup());
                RunewardenPanel host = new RunewardenPanel();
                Map<String,JComponent> modules=allModules(fixtures);
                for (Map.Entry<String, JComponent> entry : modules.entrySet()) host.addModule(entry.getKey(), entry.getValue());
                JComponent wrapped = host.getWrappedPanel();
                for (int width : new int[]{225, 242, 300})
                {
                    wrapped.setSize(width, 780);
                    for (Map.Entry<String, JComponent> entry : modules.entrySet())
                    {
                        host.selectModule(entry.getKey()); layout(wrapped);
                        assertTrue(entry.getKey() + " lost sidebar width", entry.getValue().getWidth() >= 180);
                        assertLayout(entry.getKey(), entry.getValue());
                        if (width == 242) render(entry.getKey(), wrapped);
                    }
                }
            }
            catch (Exception error) { throw new RuntimeException(error); }
            finally { try { UIManager.setLookAndFeel(previous); } catch (Exception ignored) { } }
        });
    }

    @Test public void dynamicFieldsKeepWrappedHeightAndProgrammaticTextDoesNotScroll() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            JPanel root = SidebarUi.column();
            JPanel section = SidebarUi.section("Current equipment"); root.add(section);
            GuideText value = SidebarUi.label("A long equipment name with several words and a description that must wrap over multiple complete lines.");
            section.add(SidebarUi.field("Weapon", value));
            root.setSize(206, root.getPreferredSize().height); layout(root);
            root.setSize(206, root.getPreferredSize().height); layout(root);
            assertTrue(value.getHeight() >= value.getPreferredSize().height);
            assertEquals(javax.swing.text.DefaultCaret.NEVER_UPDATE,
                ((javax.swing.text.DefaultCaret) value.getCaret()).getUpdatePolicy());
        });
    }

    private static void assertLayout(String name, Container root)
    {
        for (Component child : descendants(root))
        {
            if (!(child instanceof GuideText || child instanceof javax.swing.JComboBox || child instanceof javax.swing.JTextField)) continue;
            boolean visible = true;
            for (Component parent = child; parent != root; parent = parent.getParent())
                if (parent == null || !parent.isVisible()) { visible = false; break; }
            if (!visible || child.getParent().getWidth() <= 0 || child.getWidth() <= 0) continue;
            assertTrue(name + ": content escapes left edge", child.getX() >= 0);
            assertTrue(name + ": content escapes right edge", child.getX() + child.getWidth() <= child.getParent().getWidth());
            if (child instanceof GuideText && !(child.getParent() instanceof javax.swing.CellRendererPane))
            {
                assertTrue(name + ": narrow text column: " + ((GuideText) child).getText(), child.getWidth() >= Math.min(150, child.getParent().getWidth() - 20));
                assertTrue(name + ": clipped text height: " + ((GuideText) child).getText(), child.getHeight() >= child.getPreferredSize().height - 1);
            }
        }
    }
    private static void render(String title, JComponent wrapped) throws Exception
    {
        java.io.File dir = new java.io.File("build/reports/sidebar"); dir.mkdirs();
        BufferedImage image = new BufferedImage(wrapped.getWidth(), wrapped.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics(); wrapped.paint(graphics); graphics.dispose();
        ImageIO.write(image, "png", new java.io.File(dir, title.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-") + ".png"));
    }
    private static List<Component> descendants(Container root)
    {
        List<Component> result = new ArrayList<>();
        for (Component child : root.getComponents()) { result.add(child); if (child instanceof Container) result.addAll(descendants((Container) child)); }
        return result;
    }
    private static void layout(Container root)
    { for (int pass = 0; pass < 12; pass++) deepLayout(root); }
    private static void deepLayout(Container root)
    { root.doLayout(); for (Component child : root.getComponents()) if (child instanceof Container) deepLayout((Container) child); }

    static Map<String,JComponent> allModules(Fixture f)
    {
        Map<String,JComponent> modules=new LinkedHashMap<>();
        JPanel overview=SidebarUi.column();
        overview.add(f.get(FarmRunsTool.class).createSummaryPanel());
        overview.add(f.get(HarvestClockTool.class).createDashboardPanel());
        modules.put("Overview",overview);
        modules.put("Harvest runs",f.get(FarmRunsTool.class).createPanel());
        JPanel timers=SidebarUi.column();
        timers.add(f.get(HarvestClockTool.class).createPanel());
        timers.add(f.get(FieldCoach.class).createPanel());
        modules.put("Timers",timers);
        JPanel gathering=SidebarUi.column();
        gathering.add(f.get(SkillingCoach.class).createPanel());
        gathering.add(f.get(ActionProgressTool.class).createPanel());
        modules.put("Gathering",gathering);
        modules.put("Varlamore",f.get(VarlamoreThievingCoach.class).createPanel());
        modules.put("Session loot",f.get(RunLootTool.class).createPanel());
        modules.put("Slayer",f.get(SlayerTaskHud.class).createPanel());
        modules.put("Routines & dailies",f.get(RoutinePlannerTool.class).createPanel());
        modules.put("HUD settings",f.get(HudSettingsTool.class).createPanel());
        return modules;
    }

    /** Real retained controls with mocked game services; fixture data is not a live account reading. */
    static final class Fixture
    {
        private final Map<Class<?>, Object> values = new HashMap<>();
        Fixture()
        {
            values.put(Gson.class, new Gson());
            Client client = mock(Client.class); when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); values.put(Client.class, client);
            values.put(ClientThread.class, mock(ClientThread.class));
            values.put(RunewardenConfig.class, mock(RunewardenConfig.class, CALLS_REAL_METHODS));
        }
        @SuppressWarnings("unchecked") <T> T get(Class<T> type)
        {
            if (values.containsKey(type)) return (T) values.get(type);
            Object value;
            if (!type.getName().startsWith("com.runewarden.")) value = mock(type);
            else
            {
                Constructor<?> chosen = null;
                for (Constructor<?> constructor : type.getDeclaredConstructors())
                    if (constructor.isAnnotationPresent(javax.inject.Inject.class)) { chosen = constructor; break; }
                if (chosen == null) chosen = type.getDeclaredConstructors()[0];
                chosen.setAccessible(true); Class<?>[] types = chosen.getParameterTypes(); Object[] args = new Object[types.length];
                for (int i = 0; i < types.length; i++) args[i] = get(types[i]);
                try { value = chosen.newInstance(args); }
                catch (ReflectiveOperationException error) { throw new AssertionError("Unable to construct UI fixture " + type, error); }
            }
            values.put(type, value);
            if (type.getName().startsWith("com.runewarden."))
                for (java.lang.reflect.Method method : type.getDeclaredMethods())
                    if (method.isAnnotationPresent(javax.inject.Inject.class))
                    {
                        Class<?>[] types = method.getParameterTypes(); Object[] args = new Object[types.length];
                        for (int i = 0; i < types.length; i++) args[i] = get(types[i]);
                        try { method.setAccessible(true); method.invoke(value, args); }
                        catch (ReflectiveOperationException error) { throw new AssertionError("Unable to inject UI fixture " + type, error); }
                    }
            return (T) value;
        }
    }
}
