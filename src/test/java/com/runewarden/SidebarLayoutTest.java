/* SPDX-License-Identifier: GPL-3.0-only */
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
                RunewardenPanel host = new RunewardenPanel(fixtures.get(RunewardenGuides.class),
                    fixtures.get(RunewardenSightTool.class), fixtures.get(WaveExportTool.class));
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
                    for (String title : new String[]{"Guides", "Visibility", "Wave references"})
                    { host.selectModule(title); layout(wrapped); assertLayout(title, host); }
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
        Map<String, JComponent> modules = new LinkedHashMap<>();
        modules.put("Goals", f.get(GoalPlannerTool.class).createPanel());
        modules.put("Slayer", f.get(SlayerCoachTool.class).createPanel());
        modules.put("Boss academy", f.get(EncounterPracticeTool.class).createPanel());
        modules.put("Farm runs", f.get(FarmRunsTool.class).createPanel());
        modules.put("Run loot", f.get(RunLootTool.class).createPanel());
        modules.put("Tile markers", f.get(TileMarkersTool.class).createPanel());
        modules.put("Bank & XP", f.get(BankedExperience.class).createPanel());
        modules.put("Bank supplies", f.get(GuideBankFilter.class).createPanel());
        modules.put("Skilling", f.get(SkillingCoach.class).createPanel());
        modules.put("Timers", f.get(HarvestClockTool.class).createPanel());
        modules.put("Dashboard", f.get(HarvestClockTool.class).createDashboardPanel());
        modules.put("Item database", f.get(ItemKnowledgeTool.class).createPanel());
        modules.put("Max hit", f.get(CombatCalculatorTool.class).createPanel());
        modules.put("HUD", f.get(HudSettingsTool.class).createPanel());
        modules.put("Rewards", f.get(RewardSettingsTool.class).createPanel());
        modules.put("Music unlocks", f.get(MusicUnlockTool.class).createPanel());
        modules.put("Combat achievements", f.get(CombatAchievementsTool.class).createPanel());
        modules.put("Minigames", f.get(MinigameCoach.class).createPanel());
        modules.putAll(expandedModules(f));
        return modules;
    }

    static Map<String,JComponent> expandedModules(Fixture f)
    {
        Map<String,JComponent> result=new LinkedHashMap<>();
        result.put("Command center",f.get(CommandCenterTool.class).createPanel());
        result.put("Find a plan",f.get(OpportunityPlannerTool.class).createPanel());
        result.put("Plans",f.get(WorkflowPlansTool.class).createPanel());
        result.put("Activity hub",f.get(ActivityHubTool.class).createPanel());
        result.put("Supplies",f.get(SupplyAwarenessTool.class).createPanel());
        result.put("Loadouts",f.get(LoadoutReadinessTool.class).createPanel());
        result.put("Effects & supplies",f.get(FieldCoach.class).createPanel());
        result.put("Recovery",f.get(DeathRecoveryTool.class).createPanel());
        result.put("Ground loot",f.get(GroundLootTool.class).createPanel());
        result.put("Account progress",f.get(AccountProgressTool.class).createPanel());
        result.put("Achievement diaries",f.get(DiaryPlannerTool.class).createPanel());
        result.put("Clues & STASH",f.get(ClueCoachTool.class).createPanel());
        result.put("Travel",f.get(GuideNavigation.class).createPanel());
        result.put("World hopper",f.get(WorldHopperPanel.class).createPanel());
        result.put("DPS comparison",f.get(DpsComparisonTool.class).createPanel());
        result.put("Training session",f.get(TrainingProgressTool.class).createPanel());
        result.put("My routines",f.get(RoutinePlannerTool.class).createPanel());
        result.put("Search",f.get(DiscoverTool.class).createPanel());
        result.put("Backup & recovery",f.get(RecoveryTool.class).createPanel());
        result.put("Modules & diagnostics",f.get(ModuleRuntime.class).createPanel());return result;
    }

    /** Real local catalogues and controls, mocked game services; no game login or HTTP requests. */
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
