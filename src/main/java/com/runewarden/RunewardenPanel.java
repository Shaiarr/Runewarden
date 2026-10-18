package com.runewarden;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

final class RunewardenPanel extends PluginPanel
{
    private static final Color GOLD = new Color(224, 190, 113);
    private static final Color BACKGROUND = new Color(20, 20, 21);
    private ModuleRuntime runtime;
    private final java.util.Map<String,JPanel> moduleFrames=new java.util.LinkedHashMap<>();
    private final JComboBox<String> modules = new JComboBox<>();
    private final ModuleNavigation history = new ModuleNavigation();
    private final java.util.Map<String,Integer> scrollPositions = new java.util.LinkedHashMap<>();
    private JButton back, forward, home;
    private Runnable endRunAction=()->{};
    private boolean traversing;
    private String shownModule;
    private long navigationEpoch;
    private final JPanel cards = new JPanel(new CardLayout())
    {
        @Override public Dimension getPreferredSize()
        {
            for (Component child : getComponents())
                if (child.isVisible()) return new Dimension(200, child.getPreferredSize().height);
            return new Dimension(200, 0);
        }
    };

    RunewardenPanel(RunewardenGuides guides, RunewardenSightTool sightTool, WaveExportTool waveExport)
    {
        setLayout(new BorderLayout(0, 10));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 8, 8, 8));
        getWrappedPanel().setBackground(BACKGROUND);
        if (getScrollPane() != null)
        {
            getScrollPane().setBorder(null);
            getScrollPane().setBackground(BACKGROUND);
            getScrollPane().getViewport().setBackground(BACKGROUND);
            Component view = getScrollPane().getViewport().getView();
            if (view != null) view.setBackground(BACKGROUND);
            getScrollPane().getVerticalScrollBar().setUnitIncrement(24);
        }
        JLabel title = new JLabel("RUNEWARDEN");
        title.setForeground(GOLD);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        JLabel version = new JLabel(RunewardenVersion.VERSION);
        version.setName("runewarden.build.version");
        version.setForeground(GOLD);
        version.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        version.setToolTipText("Running Runewarden build " + RunewardenVersion.VERSION);
        JPanel branding = new JPanel(new BorderLayout(6, 0));
        branding.setOpaque(false);
        branding.add(title, BorderLayout.WEST);
        branding.add(version, BorderLayout.EAST);
        JPanel navigation = new JPanel(new BorderLayout(0, 10));
        navigation.setOpaque(false);
        navigation.add(branding, BorderLayout.NORTH);
        modules.setMaximumRowCount(12);
        modules.setName("runewarden.module.selector");
        modules.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        modules.setPreferredSize(new Dimension(200, 32));
        modules.getAccessibleContext().setAccessibleName("Runewarden module");
        modules.addActionListener(event ->
        {
            Object selected = modules.getSelectedItem();
            if (selected == null) return;
            String target = selected.toString();
            if (shownModule != null && getScrollPane() != null)
                scrollPositions.put(shownModule, getScrollPane().getVerticalScrollBar().getValue());
            shownModule = target;
            if (!traversing) history.visit(target);
            ((CardLayout) cards.getLayout()).show(cards, target);
            refreshNavigation();
            long ticket = ++navigationEpoch;
            SwingUtilities.invokeLater(() -> {
                if (ticket == navigationEpoch && target.equals(shownModule) && getScrollPane() != null)
                    getScrollPane().getVerticalScrollBar().setValue(scrollPositions.getOrDefault(target, 0));
            });
            cards.revalidate();
            revalidate();
            repaint();
        });
        navigation.add(modules, BorderLayout.CENTER);
        back = SidebarUi.button("← Back", () -> traverse(false));
        forward = SidebarUi.button("Next →", () -> traverse(true));
        home = SidebarUi.button("Home", () -> selectModule("Command dashboard"));
        back.setName("runewarden.navigation.back");
        forward.setName("runewarden.navigation.forward");
        home.setName("runewarden.navigation.home");
        back.getAccessibleContext().setAccessibleName("Previous module");
        forward.getAccessibleContext().setAccessibleName("Next module in history");
        home.getAccessibleContext().setAccessibleName("Command dashboard");
        JPanel controls=SidebarUi.column();
        controls.add(SidebarUi.row(back, home, forward));
        JButton endRun=SidebarUi.button("End run",()->endRunAction.run());
        endRun.setName("runewarden.activity.end");
        endRun.setToolTipText("Finish the current run and dismiss its guide and route");
        controls.add(endRun);
        navigation.add(controls, BorderLayout.SOUTH);
        refreshNavigation();
        add(navigation, BorderLayout.NORTH);
        cards.setName("runewarden.module.cards");
        cards.setBackground(BACKGROUND);
        add(cards, BorderLayout.CENTER);
        addModule("Guides", guides.createPanel());
        addModule("Visibility", sightTool.createPanel());
        addModule("Wave references", waveExport.createPanel());
    }

    void setEndRunAction(Runnable action){endRunAction=action==null?()->{}:action;}

    void addModule(String title, JComponent module)
    {
        addModule(title, module, modules.getItemCount());
    }

    void addModule(String title, JComponent module, int index)
    {
        readable(module);
        SidebarUi.install(module);
        JPanel frame=new JPanel(new CardLayout());frame.setOpaque(false);frame.add(module,"content");
        JPanel paused=SidebarUi.section(title);paused.add(SidebarUi.label("Module paused"));paused.add(SidebarUi.button("Enable module",()->{if(runtime!=null)runtime.enable(title);}));frame.add(paused,"paused");moduleFrames.put(title,frame);
        cards.add(frame, title);
        modules.insertItemAt(title, index);
        refreshNavigation();
    }

    void selectModule(String title)
    {
        if (SwingUtilities.isEventDispatchThread()) modules.setSelectedItem(title);
        else SwingUtilities.invokeLater(() -> modules.setSelectedItem(title));
    }

    private void traverse(boolean next)
    {
        String target = next ? history.forward() : history.back();
        if (target == null) return;
        traversing = true;
        try { modules.setSelectedItem(target); }
        finally { traversing = false; refreshNavigation(); }
    }

    private void refreshNavigation()
    {
        if (back != null) back.setEnabled(history.canBack());
        if (forward != null) forward.setEnabled(history.canForward());
        if (home != null) home.setEnabled(moduleFrames.containsKey("Command dashboard"));
    }

    void resetNavigation()
    {
        Runnable reset = () -> { navigationEpoch++; scrollPositions.clear(); history.reset(shownModule); refreshNavigation(); };
        if (SwingUtilities.isEventDispatchThread()) reset.run(); else SwingUtilities.invokeLater(reset);
    }

    void setRuntime(ModuleRuntime runtime){this.runtime=runtime;refreshModules();}
    void refreshModules()
    { if(runtime==null)return;for(java.util.Map.Entry<String,JPanel> entry:moduleFrames.entrySet())((CardLayout)entry.getValue().getLayout()).show(entry.getValue(),runtime.enabled(entry.getKey())?"content":"paused");cards.revalidate();cards.repaint(); }

    private static void readable(Component component)
    {
        Font font = component.getFont();
        component.setFont(new Font(Font.SANS_SERIF, font == null ? Font.PLAIN : font.getStyle(),
            font == null ? 13 : Math.max(13, font.getSize())));
        if (component instanceof Container)
            for (Component child : ((Container) component).getComponents()) readable(child);
    }
}
