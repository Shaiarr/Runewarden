/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.LinkBrowser;

/** Diary preparation, real completion and sourced task instructions in a bounded sidebar. */
@Singleton
final class DiaryPlannerTool
{
    private final DiaryCatalog catalog; private final AccountProgressTool account; private final ConfigManager config;
    private JPanel panel, body; private JComboBox<String> region, tier; private JCheckBox incomplete;
    private String selected; private int page; private boolean started;
    private AccountProgressTool.Snapshot rendered;
    private volatile boolean visible;
    private Consumer<GoalPlan> addGoal = ignored -> { };
    private Consumer<String> openGuide = ignored -> { };
    private Consumer<WorldPoint> route = ignored -> { };
    private java.util.function.IntConsumer openItem = ignored -> { };

    @Inject DiaryPlannerTool(DiaryCatalog catalog, AccountProgressTool account, ConfigManager config)
    { this.catalog = catalog; this.account = account; this.config = config; }
    private Consumer<java.util.Set<Integer>> bankFocus = ignored -> { };
    void setBankFocusAction(Consumer<java.util.Set<Integer>> action) { bankFocus = action == null ? ignored -> { } : action; }
    void openDiary(String id) { if (catalog.find(id) != null) SwingUtilities.invokeLater(() -> { selected = id; page = 0; if (panel != null) render(); }); }
    void setGoalAction(Consumer<GoalPlan> action) { addGoal = action == null ? ignored -> { } : action; }
    void setOpenGuideAction(Consumer<String> action) { openGuide = action == null ? ignored -> { } : action; }
    void setRouteAction(Consumer<WorldPoint> action) { route = action == null ? ignored -> { } : action; }
    void setOpenItemAction(java.util.function.IntConsumer action) { openItem = action == null ? ignored -> { } : action; }
    void start() { started = true; clear(); }
    void stop() { started = false; clear(); }
    void clear() { selected = null; page = 0; rendered = null; SwingUtilities.invokeLater(() -> { if (panel != null) render(); }); }
    void onGameTick()
    {
        if (!started || !visible) return;
        AccountProgressTool.Snapshot latest = account.reading();
        if (rendered == latest) return;
        SwingUtilities.invokeLater(() -> { if (account.reading() == latest && panel.isShowing()) render(); });
    }
    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column(); panel.setName("achievement-diaries");
        panel.addHierarchyListener(event -> { visible = panel.isShowing(); if (visible) render(); });
        JPanel filters = SidebarUi.section("Achievement diaries");
        List<String> regions = new ArrayList<>(); regions.add("All regions"); regions.addAll(catalog.regions());
        region = new JComboBox<>(regions.toArray(new String[0])); region.setFont(GuideText.FONT);
        region.addActionListener(event -> { selected = null; page = 0; render(); }); filters.add(region);
        tier = new JComboBox<>(new String[]{"All tiers", "Easy", "Medium", "Hard", "Elite"}); tier.setFont(GuideText.FONT);
        tier.addActionListener(event -> { selected = null; page = 0; render(); }); filters.add(tier);
        incomplete = new JCheckBox("Incomplete tasks only", true); incomplete.setFont(GuideText.FONT); incomplete.setOpaque(false); incomplete.setForeground(SidebarUi.TEXT);
        incomplete.addActionListener(event -> { page = 0; render(); }); filters.add(incomplete); panel.add(filters);
        body = SidebarUi.column(); panel.add(body); render(); return panel;
    }
    private void render()
    {
        if (body == null) return;
        body.removeAll(); AccountProgressTool.Snapshot state = account.reading(); rendered = state;
        if (selected != null)
        {
            DiaryCatalog.Diary diary = catalog.find(selected);
            if (diary != null) { detail(diary, state); finish(); return; }
            selected = null;
        }
        List<DiaryCatalog.Diary> found = new ArrayList<>();
        for (DiaryCatalog.Diary diary : catalog.diaries())
            if ((region.getSelectedIndex() == 0 || diary.region.equals(region.getSelectedItem())) && (tier.getSelectedIndex() == 0 || diary.tier.equals(tier.getSelectedItem()))
                && (!incomplete.isSelected() || !Long.valueOf((long) diary.tasks.size()).equals(state.value("diary:" + diary.id)))) found.add(diary);
        found.sort(java.util.Comparator.comparingInt((DiaryCatalog.Diary diary) -> DiaryCatalog.TIERS.indexOf(diary.tier)).thenComparing(diary -> diary.region));
        int pages = Math.max(1, (found.size() + 5) / 6); page = Math.max(0, Math.min(page, pages - 1));
        if (found.isEmpty()) body.add(SidebarUi.label("No matching diaries"));
        for (int index = page * 6; index < Math.min(found.size(), page * 6 + 6); index++)
        {
            DiaryCatalog.Diary diary = found.get(index); JPanel card = SidebarUi.section(diary.name);
            card.add(SidebarUi.label(progress(diary, state)));
            int ready = 0, blocked = 0;
            for (DiaryCatalog.Task task : diary.tasks) if (!Long.valueOf(1).equals(state.value("task:" + task.id)))
            { int access = taskRequirementState(task.requirements); if (access > 0) ready++; if (access < 0) blocked++; }
            card.add(SidebarUi.label(ready + " prepared · " + blocked + " need unlocks"));
            card.add(SidebarUi.button("Tasks and preparation", () -> { selected = diary.id; page = 0; render(); })); body.add(card);
        }
        navigation(pages); finish();
    }
    private void detail(DiaryCatalog.Diary diary, AccountProgressTool.Snapshot state)
    {
        body.add(SidebarUi.button("‹ All diaries", () -> { selected = null; page = 0; render(); }));
        JPanel overview = SidebarUi.section(diary.name); overview.add(SidebarUi.label(progress(diary, state)));
        Long claimed = state.value("claimed:" + diary.id); overview.add(SidebarUi.label(claimed == null ? "Reward claim unknown" : claimed > 0 ? "Reward claimed" : "Reward not yet claimed"));
        overview.add(SidebarUi.button("Track diary goal", () -> withAccount(state, () -> addGoal.accept(GoalPlan.progress("diary", diary.id, diary.tasks.size(), diary.name, Collections.emptyList())))));
        overview.add(SidebarUi.button("Wiki diary ↗", () -> LinkBrowser.browse(diary.wiki)));
        JPanel rewards = SidebarUi.column(); rewards.setVisible(false);
        for (String reward : diary.rewards) rewards.add(SidebarUi.label(reward));
        overview.add(SidebarUi.button("Rewards ▸", () -> { rewards.setVisible(!rewards.isVisible()); overview.revalidate(); })); overview.add(rewards); body.add(overview);
        List<DiaryCatalog.Task> found = new ArrayList<>();
        for (DiaryCatalog.Task task : diary.tasks) if (!incomplete.isSelected() || !Long.valueOf(1).equals(state.value("task:" + task.id))) found.add(task);
        int pages = Math.max(1, (found.size() + 4) / 5); page = Math.max(0, Math.min(page, pages - 1));
        for (int index = page * 5; index < Math.min(found.size(), page * 5 + 5); index++)
        {
            DiaryCatalog.Task task = found.get(index); Long done = state.value("task:" + task.id);
            JPanel card = SidebarUi.section((done == null ? "? " : done > 0 ? "✓ " : "○ ") + task.title);
            if (task.variants == null || task.variants.isEmpty()) for (DiaryCatalog.Step step : task.steps) card.add(SidebarUi.label(step.text));
            else
            {
                JPanel routes = SidebarUi.column(); routes.setVisible(false); int variantIndex = 0;
                for (DiaryCatalog.Variant variant : task.variants)
                {
                    routes.add(SidebarUi.heading("Route " + ++variantIndex));
                    for (DiaryCatalog.Step step : variant.steps) routes.add(SidebarUi.label(step.text));
                    for (DiaryCatalog.Requirement requirement : variant.requirements) addRequirement(routes, requirement, state);
                    java.util.Set<Integer> supplies = itemTargets(variant.requirements);
                    if (!supplies.isEmpty()) routes.add(SidebarUi.button("Find route supplies in bank", () -> withAccount(state, () -> bankFocus.accept(supplies))));
                    for (DiaryCatalog.Step step : variant.steps) if (step.location != null && step.location.length == 3)
                    { WorldPoint start = new WorldPoint(step.location[0], step.location[1], step.location[2]); routes.add(SidebarUi.button("Route to this start", () -> withAccount(state, () -> route.accept(start)))); break; }
                }
                card.add(SidebarUi.button("Choose route and supplies ▸", () -> { routes.setVisible(!routes.isVisible()); card.revalidate(); })); card.add(routes);
            }
            JPanel preparation = SidebarUi.column(); preparation.setVisible(false);
            for (DiaryCatalog.Requirement requirement : task.requirements) addRequirement(preparation, requirement, state);
            if (!task.requirements.isEmpty()) { card.add(SidebarUi.button("Preparation ▸", () -> { preparation.setVisible(!preparation.isVisible()); card.revalidate(); })); card.add(preparation); }
            java.util.Set<Integer> supplies = itemTargets(task.requirements);
            if (!supplies.isEmpty()) card.add(SidebarUi.button("Find task supplies in bank", () -> withAccount(state, () -> bankFocus.accept(supplies))));
            WorldPoint target = null;
            for (DiaryCatalog.Step step : task.steps) if (step.location != null && step.location.length == 3) { target = new WorldPoint(step.location[0], step.location[1], step.location[2]); break; }
            if (target != null && (task.variants == null || task.variants.isEmpty())) { WorldPoint stop = target; card.add(SidebarUi.button("Route to starting stop", () -> withAccount(state, () -> route.accept(stop)))); }
            if (task.steps.isEmpty()) card.add(SidebarUi.button("Source instructions ↗", () -> LinkBrowser.browse(diary.source)));
            body.add(card);
        }
        if (found.isEmpty()) body.add(SidebarUi.label("All tasks complete")); navigation(pages);
    }
    int taskRequirementState(List<DiaryCatalog.Requirement> requirements)
    { if (requirements == null || requirements.isEmpty()) return 0; boolean unknown = false; for (DiaryCatalog.Requirement requirement : requirements) { int state = account.requirementState(requirement); if (state < 0) return -1; unknown |= state == 0; } return unknown ? 0 : 1; }
    static java.util.Set<Integer> itemTargets(List<DiaryCatalog.Requirement> requirements)
    {
        java.util.Set<Integer> result = new java.util.LinkedHashSet<>();
        if (requirements != null) for (DiaryCatalog.Requirement requirement : requirements)
        {
            if ("item".equals(requirement.kind)) { if (requirement.itemId > 0) result.add(requirement.itemId); else result.addAll(GuideItemResolver.resolveName(requirement.label)); }
            else if ("all".equals(requirement.kind)) result.addAll(itemTargets(requirement.children));
        }
        return Collections.unmodifiableSet(result);
    }
    private void addRequirement(JPanel container, DiaryCatalog.Requirement requirement, AccountProgressTool.Snapshot state)
    {
        int value = account.requirementState(requirement);
        container.add(SidebarUi.label((value > 0 ? "✓ " : value < 0 ? "✕ " : "? ") + requirement.label
            + (requirement.boostable ? " · boostable" : "") + ("item".equals(requirement.kind) && requirement.quantityKnown ? " × " + requirement.value : "")));
        if ("item".equals(requirement.kind) && requirement.itemId > 0)
            container.add(SidebarUi.button("Item and sources", () -> withAccount(state, () -> openItem.accept(requirement.itemId))));
        if ("quest".equals(requirement.kind)) container.add(SidebarUi.button("Quest Helper", () -> withAccount(state, () -> openGuide.accept("quest:" + requirement.key))));
        if (requirement.children != null) for (DiaryCatalog.Requirement child : requirement.children) addRequirement(container, child, state);
    }
    private static String progress(DiaryCatalog.Diary diary, AccountProgressTool.Snapshot state)
    { Long amount = state.value("diary:" + diary.id); return (amount == null ? "?" : amount) + " / " + diary.tasks.size() + " tasks"; }
    private void withAccount(AccountProgressTool.Snapshot expected, Runnable action)
    { if (expected.profile != null && expected.profile.equals(config.getRSProfileKey()) && account.reading().generation == expected.generation) action.run(); }
    private void navigation(int pages)
    { if (pages > 1) { javax.swing.JLabel count = new javax.swing.JLabel((page + 1) + " / " + pages, javax.swing.SwingConstants.CENTER); count.setFont(GuideText.FONT); count.setForeground(SidebarUi.TEXT); body.add(SidebarUi.row(SidebarUi.button("‹", () -> { page--; render(); }), count, SidebarUi.button("›", () -> { page++; render(); }))); } }
    private void finish() { body.revalidate(); body.repaint(); }
}
