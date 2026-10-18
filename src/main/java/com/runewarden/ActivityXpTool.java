/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.QuestState;
import net.runelite.client.config.ConfigManager;
import java.util.LinkedHashMap;

/** Training browser and separately labelled activity scenarios driven by local account observations. */
@Singleton
public final class ActivityXpTool
{
    private static final Color BG = new Color(20, 22, 27), CARD = new Color(31, 34, 41), GOLD = new Color(241, 208, 135);
    private final ActivityXpCatalog catalog;
    private final SharedActivitySession activitySession;
    private final SailingXpScenarios sailing;
    private final ConfigManager config; private final RunewardenDataStore dataStore;
    private String paceProfile; private PacePreferences savedPaces = new PacePreferences();
    private volatile Map<String,QuestState> quests=Collections.emptyMap(); private volatile int combat;
    private static final class PacePreferences
    {
        int version=1; Map<String,Double> rates=new LinkedHashMap<>();
        boolean valid(){return version==1&&rates!=null&&rates.size()<=600&&rates.entrySet().stream().allMatch(e->e.getKey()!=null&&e.getKey().length()<=120&&e.getValue()!=null&&Double.isFinite(e.getValue())&&e.getValue()>0&&e.getValue()<=1_000_000_000);}
    }
    private JComboBox<SailingXpScenarios.Trial> sailingRank;
    private JComboBox<SailingXpScenarios.Courier> courier;
    private JCheckBox firstRank;
    private JButton measuredPace;
    private volatile Map<Integer, Long> bank = Collections.emptyMap();
    private volatile Map<Skill, Integer> xp = Collections.emptyMap(), levels = Collections.emptyMap();
    private volatile boolean observed;
    private Consumer<String> openGuide = id -> { };
    private Runnable showTraining = () -> { };
    private JComboBox<Skill> skill;
    private JComboBox<String> intensity;
    private JComboBox<ActivityXpCatalog.Method> method;
    private JComboBox<ActivityXpCatalog.Potion> potion;
    private JTextField search, target, rate, actionRate, quality, rounds;
    private JCheckBox outfit;
    private JPanel result, extra, root, methodInfo, pace;
    private JButton infoToggle, guideButton, startButton;
    private boolean changing;
    private String pendingMethod;
    private volatile boolean visible;
    private final AtomicBoolean refreshQueued = new AtomicBoolean();

    public ActivityXpTool(ActivityXpCatalog catalog) { this(catalog,null,null); }
    public ActivityXpTool(ActivityXpCatalog catalog,SharedActivitySession activitySession){this(catalog,activitySession,null);}
    public ActivityXpTool(ActivityXpCatalog catalog,SharedActivitySession activitySession,SailingXpScenarios sailing)
    {this(catalog,activitySession,sailing,null,null);}
    @Inject public ActivityXpTool(ActivityXpCatalog catalog,SharedActivitySession activitySession,SailingXpScenarios sailing,ConfigManager config,RunewardenDataStore dataStore)
    {this.catalog=catalog;this.activitySession=activitySession;this.sailing=sailing;this.config=config;this.dataStore=dataStore;}
    /** Same observed account facts as goals; null clears previous-profile prerequisite state. */
    public void updateAccountFacts(GoalCatalog.Facts facts)
    {
        quests=facts==null?Collections.emptyMap():facts.quests;combat=facts==null?0:facts.combat;
        if(facts==null){xp=levels=Collections.emptyMap();observed=false;bank=Collections.emptyMap();}
        else {levels=facts.levels;xp=facts.xp;}
        if(visible&&refreshQueued.compareAndSet(false,true))SwingUtilities.invokeLater(()->{refreshQueued.set(false);if(visible)render();});
    }
    public void setOpenGuideAction(Consumer<String> action) { openGuide = action == null ? id -> { } : action; }
    public void setShowTrainingAction(Runnable action)
    {
        showTraining = action == null ? () -> { } : action;
        if (pendingMethod != null) showTraining.run();
    }

    public void openMethod(String id)
    {
        ActivityXpCatalog.Method selected = catalog.find(id);
        if (selected == null) return;
        Runnable open = () -> {
            pendingMethod = id; showTraining.run();
            if (root == null) return;
            changing = true; skill.setSelectedItem(selected.skill); intensity.setSelectedIndex(0); search.setText(""); changing = false;
            reload(); method.setSelectedItem(selected);
        };
        if (SwingUtilities.isEventDispatchThread()) open.run(); else SwingUtilities.invokeLater(open);
    }

    public void updateSnapshot(Map<Integer, Long> items, Map<Skill, Integer> experience,
        Map<Skill, Integer> playerLevels, boolean hasBank)
    {
        bank = items; xp = experience; levels = playerLevels; observed = hasBank;
        if (visible && refreshQueued.compareAndSet(false, true)) SwingUtilities.invokeLater(() -> {
            refreshQueued.set(false); if (visible) render();
        });
    }

    public JPanel createPanel()
    {
        root = column(BG); root.setName("banked-xp.training");
        root.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0)
            { visible = root.isShowing(); if (visible) render(); }
        });
        JPanel chooser = SidebarUi.section("Training method");
        List<Skill> skills = new ArrayList<>();
        for (Skill value : Skill.values()) if (!catalog.methods(value).isEmpty()) skills.add(value);
        if (!catalog.activities().isEmpty()) skills.add(null);
        skill = new JComboBox<>(skills.toArray(new Skill[0])); style(skill); skill.setName("training-xp.skill"); chooser.add(skill);
        skill.setRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus)
            {
                super.getListCellRendererComponent(list, value == null ? "Activities" : ((Skill) value).getName(), index, selected, focus);
                setForeground(Color.WHITE); setBackground(selected ? new Color(65, 58, 41) : CARD); setFont(GuideText.FONT); return this;
            }
        });
        search = field(""); search.setName("training-xp.search"); search.setToolTipText("Search methods and minigames");
        chooser.add(SidebarUi.field("Find a method", search));
        intensity = new JComboBox<>(new String[]{"All intensities", "AFK", "Normal", "Intensive"}); style(intensity); chooser.add(intensity);
        method = new JComboBox<>(); style(method); method.setMaximumRowCount(10); method.setName("training-xp.method"); chooser.add(method);
        target = field("99"); target.setName("training-xp.target");
        chooser.add(SidebarUi.field("Target level", target)); root.add(chooser);
        extra = SidebarUi.column(); root.add(extra);
        pace = SidebarUi.section("Personal pace"); rate = field(""); rate.setName("training-xp.rate");
        rate.setToolTipText("Use your observed XP per hour instead of the reference rate");
        pace.add(SidebarUi.field("Your XP / hour", rate));
        actionRate=field("");actionRate.setName("training-xp.action-rate");pace.add(SidebarUi.field("Successful actions / hour",actionRate));
        pace.add(SidebarUi.button("Save this method's pace",this::savePace));pace.setVisible(false);
        JButton paceToggle = SidebarUi.button("Set your pace", () -> { pace.setVisible(!pace.isVisible()); root.revalidate(); });
        root.add(paceToggle); root.add(pace);
        measuredPace=SidebarUi.button("Use my measured pace",()->{ActivityXpCatalog.Method chosen=selected();double measured=measuredRate(chosen);if(measured>0){pace.setVisible(true);rate.setText(Long.toString(Math.round(measured)));render();}});root.add(measuredPace);
        result = SidebarUi.section(null); result.setName("training-xp.estimate"); root.add(result);
        guideButton = SidebarUi.button("Open guide", () -> { ActivityXpCatalog.Method selected = selected(); if (selected != null) openGuide.accept(selected.guideId); });
        root.add(guideButton);
        startButton=SidebarUi.button("Start this training method", () -> {
            ActivityXpCatalog.Method chosen=selected();if(chosen==null||TrainingMethodPlan.blocked(TrainingMethodPlan.requirements(chosen,levels,quests,combat)))return;
            prepareActivity(); if (activitySession != null) activitySession.startSelected();
        }); root.add(startButton);
        methodInfo = SidebarUi.section(null); methodInfo.setVisible(false);
        infoToggle = SidebarUi.button("Preparation & unlocks", () -> { methodInfo.setVisible(!methodInfo.isVisible()); root.revalidate(); });
        root.add(infoToggle); root.add(methodInfo);
        skill.addActionListener(e -> { if (!changing) reload(); }); intensity.addActionListener(e -> { if (!changing) reload(); });
        watch(search, () -> { if (!changing) reload(); }); watch(target, this::render); watch(rate, this::render);watch(actionRate,this::render);
        method.addActionListener(e -> { if (!changing) { prepareActivity(); buildExtra(); } });
        reload(); if (pendingMethod != null) openMethod(pendingMethod);
        return root;
    }

    private ActivityXpCatalog.Method selected() { return method == null ? null : (ActivityXpCatalog.Method) method.getSelectedItem(); }

    private void prepareActivity()
    {
        ActivityXpCatalog.Method method=selected();if(activitySession==null||method==null)return;
        int goal=99;try{goal=Math.max(1,Math.min(99,Integer.parseInt(target.getText().trim())));}catch(NumberFormatException ignored){}
        activitySession.select(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,method.id,method.name,
            method.guideId,method.id,method.skill,Experience.getXpForLevel(goal)));
    }

    private void reload()
    {
        if (root == null) return;
        changing = true;
        ActivityXpCatalog.Method previous = selected(); method.removeAllItems();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        Skill selectedSkill = (Skill) skill.getSelectedItem();
        for (ActivityXpCatalog.Method candidate : selectedSkill == null ? catalog.activities() : catalog.methods(selectedSkill))
            if ((intensity.getSelectedIndex() == 0 || candidate.intensity.equals(intensity.getSelectedItem()))
                && (candidate.name + " " + candidate.note).toLowerCase(Locale.ROOT).contains(query)) method.addItem(candidate);
        if (previous != null) for (int i = 0; i < method.getItemCount(); i++)
            if (method.getItemAt(i).id.equals(previous.id)) { method.setSelectedIndex(i); break; }
        rate.setText(""); changing = false; buildExtra();
    }

    private void buildExtra()
    {
        changing = true; loadPaces();ActivityXpCatalog.Method paceMethod=selected();
        Double saved=paceMethod==null?null:savedPaces.rates.get(paceMethod.id);rate.setText(saved==null?"":number(saved));actionRate.setText(""); changing = false;
        extra.removeAll(); quality = null; rounds = null; outfit = null; potion = null;sailingRank=null;courier=null;firstRank=null;
        ActivityXpCatalog.Method selected = selected();
        if (selected != null && !"time".equals(selected.kind))
        {
            JPanel options = SidebarUi.section("Method options");
            if ("foundry".equals(selected.kind))
            { quality = field(""); quality.setToolTipText("Final sword quality: 0–199"); options.add(SidebarUi.field("Sword quality", quality)); watch(quality, this::render); }
            if ("homes".equals(selected.kind))
            { outfit = new JCheckBox("Carpenter outfit"); style(outfit); outfit.setToolTipText("Full carpenter outfit: +2.5% XP"); options.add(outfit); outfit.addActionListener(e -> render()); }
            if ("mixology".equals(selected.kind))
            { potion = new JComboBox<>(catalog.potions().toArray(new ActivityXpCatalog.Potion[0])); style(potion); options.add(SidebarUi.field("Potion", potion)); potion.addActionListener(e -> render()); }
            if ("rift".equals(selected.kind))
            { rounds = field("10"); rounds.setToolTipText("Completed rounds with at least 300 energy"); options.add(SidebarUi.field("Successful rounds", rounds)); watch(rounds, this::render); }
            extra.add(options);
        }
        if(selected!=null&&sailing!=null)
        {
            List<SailingXpScenarios.Trial> trials=sailing.trials(selected.id);
            if(!trials.isEmpty())
            {JPanel scenario=SidebarUi.section("Trial reward scenario");sailingRank=new JComboBox<>(trials.toArray(new SailingXpScenarios.Trial[0]));style(sailingRank);scenario.add(SidebarUi.field("Rank",sailingRank));
                rounds=field("10");scenario.add(SidebarUi.field("Completed trials",rounds));watch(rounds,this::render);
                firstRank=new JCheckBox("First rank bonus");style(firstRank);scenario.add(firstRank);sailingRank.addActionListener(e->render());firstRank.addActionListener(e->render());extra.add(scenario);}
            else if("training:sailing-courier".equals(selected.id))
            {JPanel scenario=SidebarUi.section("Courier reward scenario");List<SailingXpScenarios.Courier> available=new ArrayList<>();int observedLevel=levels.getOrDefault(selected.skill,1);
                for(SailingXpScenarios.Courier task:sailing.couriers())if(task.level<=observedLevel)available.add(task);
                courier=new JComboBox<>(available.toArray(new SailingXpScenarios.Courier[0]));style(courier);courier.setMaximumRowCount(8);scenario.add(SidebarUi.field("Exact task",courier));
                JTextField taskSearch=field("");scenario.add(SidebarUi.field("Find port or cargo",taskSearch));watch(taskSearch,()->{String query=taskSearch.getText().trim().toLowerCase(Locale.ROOT);courier.removeAllItems();for(SailingXpScenarios.Courier task:available)if((task.noticeBoard+" "+task.cargoLocation+" "+task.destination+" "+task.cargo).toLowerCase(Locale.ROOT).contains(query))courier.addItem(task);render();});
                rounds=field("10");scenario.add(SidebarUi.field("Completed deliveries",rounds));watch(rounds,this::render);courier.addActionListener(e->render());extra.add(scenario);}
        }
        render(); extra.revalidate(); extra.repaint();
    }

    private void render()
    {
        if (result == null || changing) return;
        if(config!=null&&!java.util.Objects.equals(paceProfile,config.getRSProfileKey()))
        {loadPaces();ActivityXpCatalog.Method paceMethod=selected();Double saved=paceMethod==null?null:savedPaces.rates.get(paceMethod.id);changing=true;rate.setText(saved==null?"":number(saved));actionRate.setText("");changing=false;}
        result.removeAll(); methodInfo.removeAll(); ActivityXpCatalog.Method selected = selected();
        guideButton.setEnabled(selected != null);
        if (selected != null)
        {
            List<TrainingMethodPlan.Requirement> requirements=TrainingMethodPlan.requirements(selected,levels,quests,combat);
            for(TrainingMethodPlan.Requirement requirement:requirements)methodInfo.add(label(requirement.display(),requirement.state<0));
            startButton.setEnabled(!TrainingMethodPlan.blocked(requirements));
            for(String preparation:selected.preparation)methodInfo.add(label(preparation,false));
            if(!selected.attention.isEmpty())methodInfo.add(label(selected.attention,false));
            if(!selected.loop.isEmpty()){JPanel steps=SidebarUi.column();int number=0;for(String step:selected.loop)steps.add(label((++number)+". "+step,false));methodInfo.add(SidebarUi.disclosure("Training loop",steps));}
            if(!selected.milestones.isEmpty()){JPanel goals=SidebarUi.column();for(String milestone:selected.milestones)goals.add(label("○ "+milestone,false));methodInfo.add(SidebarUi.disclosure("Method milestones",goals));}
            for(String condition:selected.rateConditions)methodInfo.add(label(condition,false));
            if(!selected.rateBasis.isEmpty())methodInfo.add(label("Reference · "+selected.rateBasis,false));
            if (selected.note != null && !selected.note.isEmpty()) methodInfo.add(label(selected.note, false));
            methodInfo.setToolTipText(selected.source);
        }
        infoToggle.setVisible(selected != null && methodInfo.getComponentCount() > 0);
        if (selected == null) { result.add(label("No matching methods.", false)); return; }
        result.add(label(selected.name, true));
        int missing=0;for(TrainingMethodPlan.Requirement requirement:TrainingMethodPlan.requirements(selected,levels,quests,combat))
            if(requirement.state<0){if(missing++<3)result.add(label(requirement.display(),true));}
        if(missing>3)result.add(label((missing-3)+" more missing unlocks",false));
        double measured=measuredRate(selected);if(measuredPace!=null){measuredPace.setEnabled(measured>0);measuredPace.setVisible(activitySession!=null);}
        if(measured>0)result.add(label(number(measured)+" measured XP/hour",false));
        if (selected.skill == null)
        {
            result.add(label("Activity · " + selected.intensity, true));
            result.add(label("Choose your role in the guide.", false));
            result.revalidate(); result.repaint(); return;
        }
        int level = Math.max(1, levels.getOrDefault(selected.skill, 1));
        long current = xp.getOrDefault(selected.skill, 0);
        result.add(label(selected.skill.getName() + " " + level + " · " + selected.intensity, false));
        if (level < selected.level) result.add(label("Requires level " + selected.level, true));
        try
        {
            int targetLevel = Integer.parseInt(target.getText().trim());
            if (targetLevel < 1 || targetLevel > 99) throw new IllegalArgumentException("Target level must be 1–99.");
            long targetXp = Experience.getXpForLevel(targetLevel), remaining = Math.max(0, targetXp - current);
            result.add(label(number(remaining) + " XP to level " + targetLevel, true));
            ActivityXpCatalog.Rate reference=selected.ratesAt(level);Double personal=null;
            if(!rate.getText().trim().isEmpty())personal=Double.parseDouble(rate.getText().replace(",", "").trim());
            else if(!actionRate.getText().trim().isEmpty())
            {double actions=Double.parseDouble(actionRate.getText().replace(",", "").trim());if(!Double.isFinite(actions)||actions<=0||selected.xpPerAction<=0)throw new IllegalArgumentException("Choose a method with exact XP per action.");personal=actions*selected.xpPerAction;}
            if("oneoff".equals(selected.kind))result.add(label("One-off rewards · follow unfinished tasks",false));
            else if(level<selected.level)result.add(label("Unlock this method before estimating its pace",false));
            else
            {
                ActivityXpCalculator.Projection projection=ActivityXpCalculator.project(selected,current,targetXp,personal);
                if(personal==null&&reference!=null)result.add(label(number(reference.low(level))+"–"+number(reference.high(level))+" reference XP/hour",false));
                if(projection.complete())result.add(label(String.format(Locale.ROOT,(personal==null?"Reference plan: ":"Your pace: ")+"%.1f–%.1f hours",projection.fastestHours,projection.slowestHours),true));
                else result.add(label("Set your pace for "+number(projection.unpricedXp)+" XP without a reference band",false));
                if(selected.xpPerAction>0){result.add(label(String.format(Locale.ROOT,"%,.2f",selected.xpPerAction)+" XP / "+selected.actionUnit,false));result.add(label(number(ActivityXpCalculator.actionsFor(remaining,selected.xpPerAction))+" "+selected.actionUnit+" to target",false));}
            }
            List<TrainingMethodPlan.Goalpost> goalposts=TrainingMethodPlan.goalposts(catalog,selected.skill,current,targetLevel,selected.intensity);
            if(!goalposts.isEmpty())
            {
                TrainingMethodPlan.Goalpost next=goalposts.get(0);result.add(label("Next goalpost · level "+next.level+" · "+number(next.remainingXp)+" XP",true));
                JPanel path=SidebarUi.column();for(TrainingMethodPlan.Goalpost goalpost:goalposts){path.add(label("Level "+goalpost.level+" · "+number(goalpost.remainingXp)+" XP",true));for(String unlock:goalpost.unlocks)path.add(label("Unlock · "+unlock,false));}
                result.add(SidebarUi.disclosure("Path to level "+targetLevel,path));
            }
            if (level >= selected.level)
            {
                if(sailingRank!=null&&sailingRank.getSelectedItem()!=null)
                {SailingXpScenarios.Trial trial=(SailingXpScenarios.Trial)sailingRank.getSelectedItem();long completed=Long.parseLong(rounds.getText().replace(",", "").trim());
                    result.add(label(number(trial.experience(completed,firstRank.isSelected()))+" planned Sailing XP",true));
                    result.add(label(number(trial.completionXp)+" finish + "+number(trial.supplyXp)+" required supplies per trial",false));
                    methodInfo.add(label("Rank rewards · "+trial.source,false));}
                if(courier!=null&&courier.getSelectedItem()!=null)
                {SailingXpScenarios.Courier task=(SailingXpScenarios.Courier)courier.getSelectedItem();long completed=Long.parseLong(rounds.getText().replace(",", "").trim());
                    result.add(label(number(task.experience(completed))+" planned Sailing XP",true));result.add(label("Collect "+task.quantity+" × "+task.cargo+" at "+task.cargoLocation+"; deliver to "+task.destination,false));
                    methodInfo.add(label("Exact task base reward · "+sailing.courierSource(),false));}

                ActivityXpCalculator.Estimate estimate = null;
                if ("rift".equals(selected.kind)) estimate = ActivityXpCalculator.rift(level, Long.parseLong(rounds.getText().replace(",", "").trim()));
                else if (!"time".equals(selected.kind) && !"oneoff".equals(selected.kind) && !observed) result.add(label("Bank snapshot unavailable", false));
                else if ("foundry".equals(selected.kind))
                {
                    Map<String, Long> bars = ActivityXpCalculator.metalBars(catalog.metals(), bank);
                    result.add(label(number(bars.get(selected.lowerMetal)) + " " + selected.lowerMetal + " + "
                        + number(bars.get(selected.upperMetal)) + " " + selected.upperMetal + " bar equivalents", false));
                    if (quality.getText().trim().isEmpty()) result.add(label("Sword quality needed", false));
                    else estimate = ActivityXpCalculator.foundry(selected, catalog.metals(), bank, level, Integer.parseInt(quality.getText().trim()));
                }
                else if ("homes".equals(selected.kind)) estimate = ActivityXpCalculator.homes(selected, bank, level, outfit.isSelected());
                else if ("mixology".equals(selected.kind))
                {
                    ActivityXpCatalog.Potion recipe = (ActivityXpCatalog.Potion) potion.getSelectedItem();
                    if (recipe.level > level) result.add(label("Potion requires Herblore " + recipe.level, true));
                    estimate = ActivityXpCalculator.mixology(recipe, catalog.herbs(), bank, level);
                }
                if (estimate != null)
                {
                    result.add(label("rift".equals(selected.kind) ? "Round scenario" : "Separate bank scenario", true));
                    String unit = "foundry".equals(selected.kind) ? "swords" : "homes".equals(selected.kind) ? "average contracts"
                        : "mixology".equals(selected.kind) ? "requested potions" : "rounds";
                    result.add(label(number(estimate.units) + " " + unit + " · " + number(estimate.xp) + " XP", true));
                    if (estimate.units > 0 && estimate.xp > 0)
                        result.add(label(number(Math.ceil(remaining / (estimate.xp / estimate.units))) + " " + unit + " for target", false));
                    for (Map.Entry<String, Long> supply : estimate.supplies.entrySet()) result.add(label(supply.getKey() + ": " + number(supply.getValue()), false));
                    methodInfo.add(label(estimate.basis, false));
                }
            }
        }
        catch (IllegalArgumentException | ArithmeticException error)
        { result.add(label(!(error instanceof NumberFormatException) && error.getMessage() != null && error.getMessage().length() < 90
            ? error.getMessage() : "Enter valid numeric inputs.", false)); }
        result.setToolTipText(selected.source);
        result.revalidate(); result.repaint(); root.revalidate();
    }

    private void loadPaces()
    {
        if(config==null||dataStore==null)return;String profile=config.getRSProfileKey();
        if(java.util.Objects.equals(paceProfile,profile))return;paceProfile=profile;
        savedPaces=profile==null?new PacePreferences():dataStore.read("runewardentraining",profile,"methodPacesV1",1,PacePreferences.class,PacePreferences::valid,PacePreferences::new);
    }
    private void savePace()
    {
        if(config!=null&&!java.util.Objects.equals(paceProfile,config.getRSProfileKey())){render();return;}
        loadPaces();ActivityXpCatalog.Method chosen=selected();if(chosen==null)return;
        try
        {
            String text=rate.getText().replace(",", "").trim();Double value=text.isEmpty()?null:Double.parseDouble(text);
            if(value==null&&!actionRate.getText().trim().isEmpty()&&chosen.xpPerAction>0)value=Double.parseDouble(actionRate.getText().replace(",", "").trim())*chosen.xpPerAction;
            if(value!=null&&(!Double.isFinite(value)||value<=0||value>1_000_000_000))throw new IllegalArgumentException();
            if(value==null)savedPaces.rates.remove(chosen.id);else savedPaces.rates.put(chosen.id,value);
            if(dataStore!=null&&paceProfile!=null)dataStore.write("runewardentraining",paceProfile,"methodPacesV1",1,savedPaces);
            render();
        }
        catch(IllegalArgumentException error){result.add(label("Enter a positive pace before saving.",false));result.revalidate();}
    }
    double measuredRate(ActivityXpCatalog.Method method)
    {
        if(activitySession==null||method==null||method.skill==null)return 0;
        List<SharedActivitySession.RunRecord> runs=new ArrayList<>(activitySession.history());SharedActivitySession.RunRecord current=activitySession.currentRecord();
        if(current!=null&&current.state!=SharedActivitySession.State.FINISHED)runs.add(current);long xp=0,time=0;
        for(SharedActivitySession.RunRecord run:runs)
            if(run.activeMillis>=60_000&&(method.id.equals(run.context.methodId)||method.id.equals(run.context.id)))
            {long gain=run.context.skill==method.skill?run.trainingXp:0;if(gain>0){xp+=gain;time+=run.activeMillis;}}
        return time==0?0:xp*3_600_000d/time;
    }
    private static void watch(JTextField field, Runnable callback)
    { field.getDocument().addDocumentListener(new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent e) { callback.run(); }
        @Override public void removeUpdate(DocumentEvent e) { callback.run(); }
        @Override public void changedUpdate(DocumentEvent e) { callback.run(); }
    }); }
    private static JPanel column(Color background)
    { JPanel panel = SidebarUi.column(); panel.setOpaque(true); panel.setBackground(background); return panel; }
    private static JTextField field(String value) { JTextField field = new JTextField(value); style(field); return field; }
    private static GuideText label(String text, boolean gold)
    { return gold ? SidebarUi.heading(text) : SidebarUi.label(text); }
    private static void style(javax.swing.JComponent component)
    {
        component.setBackground(CARD); component.setForeground(Color.WHITE); component.setFont(GuideText.FONT); component.setAlignmentX(0);
        component.setMinimumSize(new java.awt.Dimension(0, 30)); component.setPreferredSize(new java.awt.Dimension(184, 30));
        component.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 30));
        if (component instanceof JComboBox) ((JComboBox<?>) component).setRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus)
            { super.getListCellRendererComponent(list, value, index, selected, focus); setForeground(Color.WHITE); setBackground(selected ? new Color(65, 58, 41) : CARD); setFont(GuideText.FONT); return this; }
        });
        if (component instanceof JTextField) ((JTextField) component).setCaretColor(GOLD);
    }
    private static String number(double value) { return String.format(Locale.ROOT, "%,.0f", value); }
}
