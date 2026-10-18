/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.HierarchyEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

/** In-memory, local-only observed bank calculator. Independent of account upload consent. */
@Singleton
public final class BankedExperience
{
    private static final Color BACKGROUND = new Color(20, 22, 27), CARD = new Color(31, 34, 41);
    private static final Color TEXT = new Color(245, 243, 235), GOLD = new Color(241, 208, 135);
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final Skill[] SKILLS = { Skill.CRAFTING, Skill.FLETCHING, Skill.HERBLORE, Skill.FIREMAKING,
        Skill.COOKING, Skill.SMITHING, Skill.PRAYER, Skill.CONSTRUCTION };
    private final Client client;
    private final ItemManager itemManager;
    private final ActivityXpTool activities;
    private final TrainingProgressTool training;
    private volatile Map<Integer, Long> bank = Collections.emptyMap();
    private volatile Map<Skill, Integer> experience = Collections.emptyMap();
    private volatile Map<Skill, Integer> levels = Collections.emptyMap();
    private volatile Instant observed;
    private String account;
    private boolean wasOpen;
    private JPanel panel, summary, recipesPanel, detail;
    private JComboBox<Skill> skillBox;
    private JComboBox<BankedExperienceCalculator.Recipe> recipeBox;
    private JComboBox<String> targetMode;
    private JTextField target;
    private JCheckBox bankedOnly;
    private JCheckBox productionChains;
    private java.util.function.IntConsumer openItem=id -> { };
    private final Set<String> selected = new HashSet<>();
    private final Set<Skill> initialized = EnumSet.noneOf(Skill.class);
    private boolean rendering;
    private volatile boolean bankPanelVisible;
    private final AtomicBoolean refreshQueued = new AtomicBoolean();

    public BankedExperience(Client client, ItemManager itemManager, ActivityXpTool activities)
    { this(client,itemManager,activities,null); }
    @Inject public BankedExperience(Client client, ItemManager itemManager, ActivityXpTool activities, TrainingProgressTool training)
    { this.client = client; this.itemManager = itemManager; this.activities = activities; this.training=training; }
    public BankedExperience(Client client, ItemManager itemManager) { this(client, itemManager, null); }
    public void setOpenItemAction(java.util.function.IntConsumer action) { openItem=action==null ? id -> { } : action; }

    public JPanel createPanel()
    {
        panel = vertical(BACKGROUND);
        panel.setName("banked-xp.materials");
        panel.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0)
            { bankPanelVisible = panel.isShowing(); if (bankPanelVisible) render(); }
        });
        JPanel plan = vertical(CARD); plan.add(label("Material plan", true));
        skillBox = new JComboBox<>(SKILLS); style(skillBox); skillBox.setName("banked-xp.skill");
        skillBox.addActionListener(e -> { selectDefaults(); render(); }); plan.add(skillBox);
        targetMode = new JComboBox<>(new String[]{"Level", "XP"}); style(targetMode);
        target = new JTextField("99", 7); style(target); target.setName("banked-xp.target");
        targetMode.addActionListener(e -> target.setText(targetMode.getSelectedIndex() == 0 ? "99" : "13034431"));
        target.addActionListener(e -> render());
        JPanel targetRow = new JPanel(new BorderLayout(6, 0)); targetRow.setOpaque(false);
        targetRow.add(targetMode, BorderLayout.CENTER); target.setPreferredSize(new java.awt.Dimension(64, 30));
        targetRow.add(target, BorderLayout.EAST); plan.add(targetRow);
        JButton calculate = new JButton("Update estimate"); style(calculate); calculate.addActionListener(e -> render()); plan.add(calculate);
        productionChains=new JCheckBox("Use production chains",true); style(productionChains);
        productionChains.setName("banked-xp.production-chains"); productionChains.addActionListener(e -> render()); plan.add(productionChains);
        panel.add(plan);
        summary = vertical(CARD); summary.setName("banked-xp.summary"); panel.add(summary);
        JPanel materialHeading = vertical(BACKGROUND); materialHeading.add(label("Use banked materials", true));
        bankedOnly = new JCheckBox("Banked items only", true); style(bankedOnly);
        bankedOnly.addActionListener(e -> render()); materialHeading.add(bankedOnly); panel.add(materialHeading);
        recipesPanel = vertical(BACKGROUND); recipesPanel.setName("banked-xp.recipes"); panel.add(recipesPanel);
        JPanel goalMethod = vertical(CARD); goalMethod.add(label("Finish target with", true));
        recipeBox = new JComboBox<>(); style(recipeBox); recipeBox.setName("banked-xp.finish-method");
        recipeBox.setMaximumRowCount(10); recipeBox.addActionListener(e -> { if (!rendering) renderDetail(); });
        goalMethod.add(recipeBox); detail = vertical(CARD); goalMethod.add(detail); panel.add(goalMethod);
        selectDefaults(); render();
        JPanel materialPage = new JPanel(new BorderLayout())
        {
            @Override public java.awt.Dimension getPreferredSize()
            { return new java.awt.Dimension(200, panel.getPreferredSize().height); }
        };
        materialPage.setOpaque(false); materialPage.add(panel, BorderLayout.CENTER);
        if (activities == null) return materialPage;
        JPanel browser = vertical(BACKGROUND);
        JComboBox<String> mode = new JComboBox<>(new String[]{"Banked materials", "Training methods"}); style(mode);
        mode.setName("banked-xp.view");
        JPanel pages = new JPanel(new CardLayout())
        {
            @Override public java.awt.Dimension getPreferredSize()
            {
                for (java.awt.Component child : getComponents()) if (child.isVisible())
                    return new java.awt.Dimension(190, child.getPreferredSize().height);
                return new java.awt.Dimension(190, 0);
            }
            @Override public java.awt.Dimension getMaximumSize()
            { return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        pages.setName("banked-xp.pages"); pages.setOpaque(false);
        pages.add(materialPage, "bank"); pages.add(activities.createPanel(), "training");
        mode.addActionListener(e -> {
            ((CardLayout) pages.getLayout()).show(pages, mode.getSelectedIndex() == 0 ? "bank" : "training");
            pages.revalidate(); browser.revalidate(); browser.repaint();
        });
        activities.setShowTrainingAction(() -> mode.setSelectedIndex(1)); browser.add(mode); browser.add(pages); return browser;
    }

    public void start() { reset(); }
    public void stop() { reset(); }
    public void reset()
    {
        bank = Collections.emptyMap(); observed = null; wasOpen = false; account = null;
        experience = Collections.emptyMap(); levels = Collections.emptyMap(); refreshPanel();
    }

    public void onGameStateChanged(GameState state)
    {
        if (state == GameState.LOGIN_SCREEN || state == GameState.CONNECTION_LOST) reset();
        else if (state == GameState.HOPPING) wasOpen = false;
    }

    public void onGameTick()
    {
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) return;
        String name = PlayerIdentity.normalizedName(client.getLocalPlayer().getName());
        if (!name.equals(account)) { reset(); account = name; }
        Map<Skill, Integer> nextXp = new EnumMap<>(Skill.class), nextLevels = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values())
            if (skill != Skill.OVERALL)
            { nextXp.put(skill, client.getSkillExperience(skill)); nextLevels.put(skill, client.getRealSkillLevel(skill)); }
        boolean changed = !nextXp.equals(experience) || !nextLevels.equals(levels);
        experience = Collections.unmodifiableMap(nextXp); levels = Collections.unmodifiableMap(nextLevels);
        boolean open = bankOpen();
        if (open && !wasOpen) observeBank();
        else if (changed) refreshPanel();
        wasOpen = open;
    }

    public void onItemContainerChanged(int containerId)
    { if (containerId == InventoryID.BANK && bankOpen()) observeBank(); }

    private boolean bankOpen()
    {
        if (client.getGameState() != GameState.LOGGED_IN) return false;
        Widget widget = client.getWidget(InterfaceID.Bankmain.ITEMS);
        return widget != null && !widget.isHidden();
    }

    private void observeBank()
    {
        ItemContainer container = client.getItemContainer(InventoryID.BANK);
        if (container == null) return;
        Map<Integer, Long> observedItems = new LinkedHashMap<>();
        for (Item item : container.getItems())
        {
            if (item.getId() <= 0 || item.getQuantity() <= 0) continue;
            int id = itemManager.canonicalize(item.getId());
            observedItems.merge(id, (long) item.getQuantity(), Long::sum);
        }
        bank = Collections.unmodifiableMap(observedItems); observed = Instant.now(); refreshPanel();
    }

    private void refreshPanel()
    {
        if (activities != null) activities.updateSnapshot(bank, experience, levels, observed != null);
        if (bankPanelVisible && refreshQueued.compareAndSet(false, true)) SwingUtilities.invokeLater(() -> {
            refreshQueued.set(false); if (bankPanelVisible) render();
        });
    }

    private List<BankedExperienceCalculator.Recipe> skillRecipes()
    {
        Skill skill = (Skill) skillBox.getSelectedItem(); List<BankedExperienceCalculator.Recipe> result = new ArrayList<>();
        for (BankedExperienceCalculator.Recipe recipe : BankedExperienceCalculator.recipes())
            if (recipe.skill == skill) result.add(recipe);
        // Higher XP alternatives receive materials first; checked rows explicitly show each allocation.
        result.sort(Comparator.comparingDouble((BankedExperienceCalculator.Recipe r) -> r.xp).reversed().thenComparing(r -> r.name));
        return result;
    }

    private void selectDefaults()
    {
        if (skillBox == null) return;
        if (!initialized.add((Skill) skillBox.getSelectedItem())) return;
        for (BankedExperienceCalculator.Recipe recipe : skillRecipes()) selected.add(recipe.id);
    }

    private BankedExperienceCalculator.Result calculate()
    {
        List<BankedExperienceCalculator.Recipe> plan=selectedRecipes();
        if (productionChains==null || !productionChains.isSelected()) plan.removeIf(recipe -> recipe.xp<=0);
        return productionChains!=null && productionChains.isSelected()
            ? BankedExperienceCalculator.allocateChained(plan,bank,levels)
            : BankedExperienceCalculator.allocate(plan,bank,levels.getOrDefault((Skill)skillBox.getSelectedItem(),1));
    }
    private List<BankedExperienceCalculator.Recipe> selectedRecipes()
    {
        List<BankedExperienceCalculator.Recipe> plan=new ArrayList<>();
        for (BankedExperienceCalculator.Recipe recipe : skillRecipes()) if (selected.contains(recipe.id)) plan.add(recipe);
        return plan;
    }

    private long goalXp()
    {
        long value = Long.parseLong(target.getText().replace(",", "").trim());
        if (targetMode.getSelectedIndex() == 0)
        {
            if (value < 1 || value > 99) throw new IllegalArgumentException();
            return Experience.getXpForLevel((int) value);
        }
        if (value < 0 || value > 200_000_000) throw new IllegalArgumentException();
        return value;
    }

    private void render()
    {
        if (panel == null || rendering) return;
        rendering = true;
        try
        {
            Skill skill = (Skill) skillBox.getSelectedItem();
            BankedExperienceCalculator.Result result = calculate(); long current = experience.getOrDefault(skill, 0);
            summary.removeAll();
            if (observed == null) summary.add(label("Bank snapshot unavailable", false));
            else
            {
                summary.add(label("Current: " + number(current) + " XP", false));
                summary.add(label("Planned banked: +" + number(result.bankedXp) + " XP", true));
                summary.add(label("Projected: " + number(current + result.bankedXp) + " XP · level " + result.projectedLevel(current), false));
                try { summary.add(label("To target: " + number(Math.max(0, goalXp() - current - result.bankedXp)) + " XP", false)); }
                catch (RuntimeException error) { summary.add(label("Enter level 1–99 or XP 0–200,000,000.", false)); }
                summary.add(label("Bank seen " + DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(observed), false));
                if (result.usesOutputs)
                {
                    JPanel steps=SidebarUi.column(); int step=0;
                    for (BankedExperienceCalculator.Allocation allocation : result.allocations) if (allocation.actions>0)
                        steps.add(label((++step)+". "+allocation.recipe.name+" × "+number(allocation.actions),false));
                    if (step>0) summary.add(SidebarUi.disclosure("Production steps",steps));
                }
            }
            recipesPanel.removeAll(); Map<String, BankedExperienceCalculator.Allocation> allocations = new LinkedHashMap<>();
            for (BankedExperienceCalculator.Allocation allocation : result.allocations) allocations.put(allocation.recipe.id, allocation);
            for (BankedExperienceCalculator.Recipe recipe : skillRecipes())
            {
                BankedExperienceCalculator.Allocation allocation = allocations.get(recipe.id);
                boolean hasPrimary = bank.getOrDefault(recipe.ingredients.get(0).itemId, 0L) > 0
                    || result.remaining.getOrDefault(recipe.ingredients.get(0).itemId,0L)>0 || allocation!=null && allocation.actions>0;
                if (bankedOnly.isSelected() && !hasPrimary) continue;
                JPanel row = vertical(CARD);
                row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
                JCheckBox use = new JCheckBox("Use in plan"); style(use); use.setSelected(selected.contains(recipe.id));
                use.addActionListener(e -> { if (use.isSelected()) selected.add(recipe.id); else selected.remove(recipe.id); render(); });
                JLabel icon = new JLabel(); itemManager.getImage(recipe.icon).addTo(icon);
                JPanel controls = new JPanel(new BorderLayout(6, 0)); controls.setOpaque(false);
                controls.setPreferredSize(new java.awt.Dimension(0, 32));
                controls.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 32));
                controls.add(icon, BorderLayout.WEST); controls.add(use, BorderLayout.CENTER);
                long count = allocation == null ? 0 : allocation.actions;
                String line = recipe.level > levels.getOrDefault(skill, 1) ? "Requires level " + recipe.level
                    : number(count) + " actions · " + number(count * recipe.xp) + " XP";
                GuideText body = label(recipe.name + "\n" + line, false);
                row.add(controls); row.add(body);
                row.setToolTipText(recipe.note);
                recipesPanel.add(row);
            }
            if (recipesPanel.getComponentCount() == 0 && observed != null)
                recipesPanel.add(label("No supported materials in this bank", false));
            BankedExperienceCalculator.Recipe previous = (BankedExperienceCalculator.Recipe) recipeBox.getSelectedItem();
            recipeBox.removeAllItems();
            for (BankedExperienceCalculator.Recipe recipe : skillRecipes()) if (recipe.xp>0) recipeBox.addItem(recipe);
            if (previous != null && previous.skill == skill) recipeBox.setSelectedItem(previous);
            renderDetail(); panel.revalidate(); panel.repaint();
        }
        finally { rendering = false; }
    }

    private void renderDetail()
    {
        detail.removeAll();
        BankedExperienceCalculator.Recipe recipe = (BankedExperienceCalculator.Recipe) recipeBox.getSelectedItem();
        if (recipe == null) return;
        detail.add(label(recipe.name, true));
        detail.add(label("Level " + recipe.level + " · " + number(recipe.xp) + " XP per action", false));
        if(training!=null)detail.add(SidebarUi.button("Train with this recipe",()->{
            long goal=0;try{goal=goalXp();}catch(RuntimeException ignored){}
            training.startRecipe(recipe,goal);
        }));
        for (BankedExperienceCalculator.Ingredient item : recipe.ingredients)
            detail.add(label(item.quantity + " × " + item.name + " · bank " + number(bank.getOrDefault(item.itemId, 0L)), false));
        if (recipe.note != null && !recipe.note.isEmpty()) {
            JPanel notes = vertical(CARD); notes.add(label(recipe.note, false)); notes.setVisible(false);
            JButton show = new JButton("Method details"); style(show);
            show.addActionListener(e -> { notes.setVisible(!notes.isVisible()); detail.revalidate(); });
            detail.add(show); detail.add(notes);
        }
        if (observed != null)
        {
            try
            {
                BankedExperienceCalculator.Result result = calculate();
                long current = experience.getOrDefault(recipe.skill, 0);
                double remainingXp=Math.max(0,goalXp()-current-result.xp(recipe.skill));
                if (recipe.level>levels.getOrDefault(recipe.skill,1)) detail.add(label("Requires "+recipe.level+" "+recipe.skill.getName(),false));
                else
                {
                    BankedExperienceCalculator.ShoppingPlan shopping=BankedExperienceCalculator.shoppingForTarget(recipe,remainingXp,result.remaining,
                        productionChains.isSelected() ? selectedRecipes() : Collections.emptyList(),levels);
                    if (remainingXp==0) detail.add(label("Target covered by this plan",true));
                    else
                    {
                        detail.add(label("Finish with +"+number(shopping.xp(recipe.skill))+" XP",true));
                        JPanel steps=SidebarUi.column(); int step=0;
                        for (BankedExperienceCalculator.Allocation allocation : shopping.steps)
                            steps.add(label((++step)+". "+allocation.recipe.name+" × "+number(allocation.actions),false));
                        detail.add(SidebarUi.disclosure("Remaining steps",steps));
                        detail.add(label("Buy materials",true));
                        for (Map.Entry<Integer,Long> purchase : shopping.purchases.entrySet())
                        {
                            int id=purchase.getKey(); String name=materialName(id);
                            detail.add(label(number(purchase.getValue())+" × "+name,false));
                            JButton item=SidebarUi.button("Item sources",() -> openItem.accept(id));
                            if (itemManager.getImage(id)!=null) itemManager.getImage(id).addTo(item);
                            detail.add(item);
                        }
                        if (shopping.purchases.isEmpty()) detail.add(label("No additional materials",false));
                        if (!shopping.purchases.isEmpty()) detail.add(SidebarUi.button("Copy shopping list",() -> {
                            StringBuilder text=new StringBuilder(); shopping.purchases.forEach((id,count) -> text.append(count).append(" × ").append(materialName(id)).append('\n'));
                            try { java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(text.toString()),null); }
                            catch (IllegalStateException | java.awt.HeadlessException ignored) { }
                        }));
                    }
                }
            }
            catch (ArithmeticException overflow) { detail.add(label("This material quantity exceeds the supported range",false)); }
            catch (IllegalArgumentException invalid) { detail.add(label("Choose a valid XP target and production route",false)); }
        }
        detail.revalidate(); detail.repaint();
    }

    private static String materialName(int id)
    {
        for (BankedExperienceCalculator.Recipe recipe : BankedExperienceCalculator.recipes())
        {
            for (BankedExperienceCalculator.Ingredient item : recipe.ingredients) if (item.itemId==id) return item.name;
            for (BankedExperienceCalculator.Ingredient item : recipe.products) if (item.itemId==id) return item.name;
        }
        return "Item "+id;
    }

    private static JPanel vertical(Color background)
    {
        JPanel panel = SidebarUi.column(); panel.setOpaque(true); panel.setBackground(background);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3)); return panel;
    }

    private static GuideText label(String text, boolean accent)
    {
        GuideText result = accent ? SidebarUi.heading(text) : SidebarUi.label(text); result.setForeground(accent ? GOLD : TEXT);
        if (accent) result.setFont(FONT.deriveFont(Font.BOLD)); return result;
    }
    private static void style(javax.swing.JComponent component)
    {
        component.setBackground(CARD); component.setForeground(TEXT); component.setFont(FONT); component.setAlignmentX(0);
        component.setMinimumSize(new java.awt.Dimension(0, 30));
        component.setPreferredSize(new java.awt.Dimension(184, 30));
        component.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 30));
        if (component instanceof JButton) { ((JButton) component).setFocusPainted(false); ((JButton) component).setMargin(new java.awt.Insets(4, 6, 4, 6)); }
        if (component instanceof JComboBox)
            ((JComboBox<?>) component).setRenderer(new DefaultListCellRenderer()
            {
                @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,
                    Object value, int index, boolean selected, boolean focus)
                {
                    super.getListCellRendererComponent(list, value instanceof Skill ? ((Skill) value).getName() : value, index, selected, focus);
                    setBackground(selected ? new Color(65, 58, 41) : CARD); setForeground(TEXT); setFont(FONT); return this;
                }
            });
        if (component instanceof JTextField)
        {
            JTextField field = (JTextField) component; field.setCaretColor(GOLD);
            field.setSelectionColor(new Color(93, 72, 35)); field.setSelectedTextColor(TEXT);
        }
    }
    private static String number(double value)
    { return String.format(java.util.Locale.ROOT, value == Math.floor(value) ? "%,.0f" : "%,.1f", value); }

    public Instant bankObservedAt() { return observed; }
    Map<Integer, Long> observedItems() { return bank; }
}
