/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Dimension;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;

/** One activity snapshot drives the minigame card, scene references and sidebar. */
@Singleton
final class MinigameCoach
{
    static final String GROUP = "runewardenminigames", KEY = "preferencesV1";
    static final class Preferences
    {
        int version = 1;
        boolean autoHud = true, autoGuide, highlights = true, labels = true;
        int scalePercent = 100;
        boolean npcHighlights=true,numberedTargets=true,compactHud;
        int fragmentTarget=100,warmthThreshold=25,volcanicExitSeconds=60;
        String volcanicRole="Team overview";
        boolean winterFletch=true,temporossCook=true;
        int temporossFishBatch=1;
        Set<String> disabledActivities = new LinkedHashSet<>();
        Preferences copy()
        {
            Preferences p = new Preferences(); p.autoHud = autoHud; p.autoGuide = autoGuide;
            p.highlights = highlights; p.labels = labels; p.scalePercent = scalePercent;
            p.disabledActivities = new LinkedHashSet<>(disabledActivities);
            p.npcHighlights=npcHighlights;p.numberedTargets=numberedTargets;p.compactHud=compactHud;
            p.fragmentTarget=fragmentTarget;p.warmthThreshold=warmthThreshold;p.volcanicExitSeconds=volcanicExitSeconds;
            p.volcanicRole=volcanicRole;p.winterFletch=winterFletch;p.temporossCook=temporossCook;p.temporossFishBatch=temporossFishBatch;return p;
        }
    }
    private final Client client;
    private final MinigameActivityCatalog catalog;
    private final ConfigManager configManager;
    private final Gson gson;
    private final RunewardenDataStore store;
    private final RunewardenConfig hudConfig;
    private final Supplier<MinigameActivityCatalog.Snapshot> detector;
    private final MinigameHudOverlay hud;
    private final MinigameSceneOverlay scene;
    private final MinigameFieldEvents fieldEvents;
    @Inject private net.runelite.client.game.ItemManager fieldItemManager;
    MinigameFieldEvents fieldEvents(){return fieldEvents;}
    net.runelite.client.game.ItemManager fieldItemManager(){return fieldItemManager;}
    private final MinigameGuideSession guideSession = new MinigameGuideSession();
    private volatile Preferences preferences = new Preferences();
    private volatile MinigameActivityCatalog.Snapshot reading = MinigameActivityCatalog.Snapshot.EMPTY;
    private volatile String readingProfile;
    private final MinigameCountdowns countdowns = new MinigameCountdowns();
    private volatile boolean started;
    private volatile long epoch;
    private volatile Supplier<String> selectedGuide = () -> null;
    private volatile Consumer<String> selectGuide = ignored -> {};
    private volatile Consumer<String> openGuideAction;
    private volatile boolean guideBridgeReady;
    private volatile Runnable resumeGuideAction=()->{};
    private volatile Consumer<String> closeGuideAction=ignored->{};
    private volatile Consumer<MinigameActivityCatalog.Snapshot> activityExitAction=ignored->{};
    private final MinigameActivityExit activityExit=new MinigameActivityExit();
    void setActivityExitAction(Consumer<MinigameActivityCatalog.Snapshot> action){activityExitAction=action==null?ignored->{}:action;}
    private boolean lessonDismissed;
    void setResumeGuideAction(Runnable action){resumeGuideAction=action==null?()->{}:action;}
    void setCloseGuideAction(Consumer<String> action){closeGuideAction=action==null?ignored->{}:action;}
    private JPanel panel;
    private GuideText activity, facts, stepTitle, stepBody;
    private JButton openGuide,resumeCoaching;
    private JCheckBox autoHud, highlights, labels;
    private JSlider scale;
    private final Map<MinigameActivityCatalog.Activity, JCheckBox> activityChoices = new EnumMap<>(MinigameActivityCatalog.Activity.class);
    private boolean refreshing;
    private String lastUiKey;
    private final MinigameRunProgress progress=new MinigameRunProgress();
    private final MinigameRunProgress.Learning learning=new MinigameRunProgress.Learning();
    private JComboBox<MinigameLessonCatalog.Lesson> lessonChoice;
    private JCheckBox followLesson,npcHighlights,numberedTargets,compactHud;
    private GuideText lessonTitle,lessonBody,lessonStatus,runStatus;
    private JPanel learningPanel;
    private JLabel lessonCounter;
    private JButton lessonComplete,lessonBack,lessonNext,lessonUndo,lessonReset;
    private JSpinner fragmentTarget,warmthThreshold,volcanicExitSeconds,temporossFishBatch;
    private JComboBox<String> volcanicRole;
    private JCheckBox winterFletch,temporossCook;
    private String accountProfile;
    private volatile Consumer<net.runelite.api.coords.WorldPoint> routeAction;
    private JButton homeRoute;
    void setRouteAction(Consumer<net.runelite.api.coords.WorldPoint> action){routeAction=action;}
    boolean canRouteHome(net.runelite.api.coords.WorldPoint point)
    {return started&&Objects.equals(accountProfile,configManager.getRSProfileKey())&&reading().activity==MinigameActivityCatalog.Activity.MAHOGANY_HOMES&&point!=null&&point.equals(catalog.homeDestination());}
    void onChatMessage(net.runelite.api.events.ChatMessage event)
    {
        if(!started||event==null||event.getType()!=net.runelite.api.ChatMessageType.GAMEMESSAGE||client.getGameState()!=GameState.LOGGED_IN)return;
        scopeAccount();fieldEvents.message(event.getType(),event.getMessage());catalog.onHomeMessage(event.getMessage());saveHomeAssignment();
    }
    private void scopeAccount()
    {
        String profile=configManager.getRSProfileKey();
        if(Objects.equals(accountProfile,profile))return;
        accountProfile=profile;catalog.clearAccount();activityExit.clear();
        if(profile!=null)catalog.restoreHome(store.read(GROUP,profile,"homeAssignmentV1",1,MinigameHomes.State.class,MinigameHomes::validState,MinigameHomes.State::new));
    }
    private void saveHomeAssignment()
    {
        if(accountProfile!=null&&Objects.equals(accountProfile,configManager.getRSProfileKey())&&catalog.homeDirty())
            store.write(GROUP,accountProfile,"homeAssignmentV1",1,catalog.homeState());
    }
    private volatile boolean followObservedLesson=true;
    MinigameRunProgress.View sessionView(){MinigameRunProgress.View view=progress.view();return started&&view.profile!=null&&view.profile.equals(configManager.getRSProfileKey())?view:MinigameRunProgress.View.EMPTY;}

    @Inject MinigameCoach(Client client, MinigameActivityCatalog catalog, ConfigManager configManager,
        Gson gson, RunewardenConfig hudConfig, RunewardenDataStore store)
    { this(client, catalog, configManager, gson, hudConfig, () -> catalog.detect(client), store); }

    MinigameCoach(Client client, MinigameActivityCatalog catalog, ConfigManager configManager,
        Gson gson, RunewardenConfig hudConfig)
    { this(client, catalog, configManager, gson, hudConfig, () -> catalog.detect(client), new RunewardenDataStore(configManager, gson)); }

    MinigameCoach(Client client, MinigameActivityCatalog catalog, ConfigManager configManager,
        Gson gson, RunewardenConfig hudConfig, Supplier<MinigameActivityCatalog.Snapshot> detector)
    { this(client, catalog, configManager, gson, hudConfig, detector, new RunewardenDataStore(configManager, gson)); }

    MinigameCoach(Client client, MinigameActivityCatalog catalog, ConfigManager configManager,
        Gson gson, RunewardenConfig hudConfig, Supplier<MinigameActivityCatalog.Snapshot> detector, RunewardenDataStore store)
    {
        this.client = client; this.catalog = catalog; this.configManager = configManager; this.gson = gson; this.hudConfig = hudConfig;
        this.store = store;
        this.detector = detector;
        fieldEvents=new MinigameFieldEvents(client,configManager,store);
        hud = new MinigameHudOverlay(client, this, hudConfig); scene = new MinigameSceneOverlay(client, this, catalog);
    }
    void start() { started = true;fieldEvents.setEnabled(true); epoch++; reloadPreferences(); }
    void stop() { started = false;fieldEvents.setEnabled(false); clear(); }
    void clear()
    {
        epoch++; accountProfile=null;catalog.clearAccount();activityExit.clear(); reading = MinigameActivityCatalog.Snapshot.EMPTY; readingProfile = null; guidanceDismissed=false;lessonDismissed=false;dismissedActivity=null;countdowns.clear();fieldEvents.clear(); progress.clear();learning.clear();scene.clear(); catalog.clearScene(); lastUiKey = null;
        SwingUtilities.invokeLater(() -> { guideSession.forget(); refreshPanel(); });
    }
    void onSceneChanged() { fieldEvents.clear();scene.clear(); catalog.clearScene(); }
    Overlay hud() { return hud; }
    void setHudLocked(boolean locked) { hud.setHudLocked(locked); }
    private volatile boolean guidanceDismissed;
    private volatile MinigameActivityCatalog.Activity dismissedActivity;
    boolean guidanceDismissed(){return guidanceDismissed;}
    void dismissGuide()
    {
        guidanceDismissed=true;dismissedActivity=reading.activity;scene.hide();
        long expected=++epoch;String owner=configManager.getRSProfileKey();
        Runnable update=()->{if(started&&epoch==expected&&Objects.equals(owner,configManager.getRSProfileKey())){guideSession.dismiss();refreshPanel();}};
        if(SwingUtilities.isEventDispatchThread())update.run();else SwingUtilities.invokeLater(update);
    }
    void resumeGuide()
    {
        guidanceDismissed=false;lessonDismissed=false;dismissedActivity=null;resumeGuideAction.run();long expected=++epoch;String owner=configManager.getRSProfileKey();
        Runnable update=()->{if(started&&expected==epoch&&Objects.equals(owner,configManager.getRSProfileKey())){guideSession.forget();refreshPanel();}};
        if(SwingUtilities.isEventDispatchThread())update.run();else SwingUtilities.invokeLater(update);
    }
    Overlay scene() { return scene; }
    MinigameActivityCatalog.Snapshot reading()
    { return Objects.equals(readingProfile, configManager.getRSProfileKey()) ? reading : MinigameActivityCatalog.Snapshot.EMPTY; }
    java.util.List<MinigameCountdowns.Clock> countdowns()
    { return started && Objects.equals(readingProfile, configManager.getRSProfileKey()) ? countdowns.reading() : java.util.List.of(); }
    Preferences preferences() { return preferences; }
    boolean started() { return started; }

    void setGuideActions(Supplier<String> selected, Consumer<String> select)
    {
        selectedGuide = selected == null ? () -> null : selected;
        selectGuide = select == null ? ignored -> {} : select;
        guideBridgeReady = selected != null && select != null;
    }
    void setOpenGuideAction(Consumer<String> open) { openGuideAction = open; if (panel != null) SwingUtilities.invokeLater(this::refreshPanel); }
    void onGameTick()
    {
        if (!started) return;
        Preferences p = preferences;
        boolean visiblePanel = panel != null && panel.isShowing();
        if (!p.autoHud && !p.highlights && !p.labels && !visiblePanel && !guideNeedsObservation())
        {
            if (reading.activity != null) publish(MinigameActivityCatalog.Snapshot.EMPTY);
            scene.clear();
            return;
        }
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        { if(client.getGameState()==GameState.LOGIN_SCREEN||client.getGameState()==GameState.HOPPING){if(reading.activity!=null)clear();}else scene.hide(); return; }
        scene.ensureSeeded();
        scopeAccount();
        catalog.setOptions(p.fragmentTarget,p.warmthThreshold,p.volcanicExitSeconds,p.volcanicRole,p.winterFletch,p.temporossCook,p.temporossFishBatch);
        MinigameActivityCatalog.Snapshot next = detector.get();
        saveHomeAssignment();
        if (next == null) next = MinigameActivityCatalog.Snapshot.EMPTY;
        if (next.activity != null && p.disabledActivities.contains(next.activity.name())) next = MinigameActivityCatalog.Snapshot.EMPTY;
        next = MinigameReleasePolicy.releaseSnapshot(next);
        next=catalog.annotateGuideProgress(fieldEvents.enrich(next));
        publish(next);
        hud.setHudLocked(hudConfig.hudLocked());
        if (MinigameReleasePolicy.allowsAutomaticGuidance(next.activity) && (p.highlights || p.labels)) scene.refresh(next);
        else scene.hide();
    }
    /** Client-thread read for an explicitly opened guide; does not start overlays or auto-guide actions. */
    void refreshForGuide()
    {
        if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null)
        {reading=MinigameActivityCatalog.Snapshot.EMPTY;readingProfile=configManager.getRSProfileKey();return;}
        scopeAccount();Preferences p=preferences;
        catalog.setOptions(p.fragmentTarget,p.warmthThreshold,p.volcanicExitSeconds,p.volcanicRole,p.winterFletch,p.temporossCook,p.temporossFishBatch);
        MinigameActivityCatalog.Snapshot fresh=detector.get();
        if(fresh==null)fresh=MinigameActivityCatalog.Snapshot.EMPTY;
        // Explicit reading works while the automatic minigame presentation module is disabled.
        observeExit(fresh);
        reading=MinigameReleasePolicy.releaseSnapshot(fresh);readingProfile=configManager.getRSProfileKey();
    }
    boolean isGuide(String id)
    {
        if(id==null)return false;
        if(id.startsWith("training:homes-")||id.startsWith("training:blast-")||id.equals("training:mage-training-arena"))return true;
        for(MinigameActivityCatalog.Snapshot activity:catalog.supportedActivities())if(id.equals(activity.guideId))return true;
        return false;
    }
    private void observeExit(MinigameActivityCatalog.Snapshot next)
    {MinigameActivityCatalog.Snapshot left=activityExit.observe(next,client);if(left!=null)activityExitAction.accept(left);}
    private boolean guideNeedsObservation(){return isGuide(selectedGuide.get());}

    private void publish(MinigameActivityCatalog.Snapshot next)
    {
        MinigameActivityCatalog.Snapshot before = reading;
        observeExit(next);
        boolean restartGuidance=guidanceDismissed&&next.activity!=null&&next.activity!=dismissedActivity;
        if(restartGuidance){guidanceDismissed=false;dismissedActivity=null;}
        reading = next;
        readingProfile = configManager.getRSProfileKey();
        countdowns.observe(next);
        progress.observe(next,configManager.getRSProfileKey());
        if(next.activity==null&&before.activity!=null)learning.clear();
        if(followObservedLesson&&MinigameReleasePolicy.allowsAutomaticGuidance(next.activity))learning.follow(next);
        // Opening a guide is an explicit sidebar action. Native activity changes never open one.
        if (panel != null && panel.isShowing())
        {
            String key = uiKey(next)+"|"+learning.key()+"|"+(progress.view().observedTicks/10);
            if (!key.equals(lastUiKey)) { lastUiKey = key; SwingUtilities.invokeLater(this::refreshPanel); }
        }
    }
    void onGameObjectSpawned(GameObject object)
    {
        if (!started || !observing()) return;
        fieldEvents.observe(object,true);catalog.onGameObjectSpawned(object); scene.observe(object);
    }
    void onGameObjectDespawned(GameObject object)
    {
        if (!started) return;
        fieldEvents.remove(object);catalog.onGameObjectDespawned(object); scene.remove(object);
    }
    void onNpcSpawned(net.runelite.api.events.NpcSpawned event){if(started&&observing()&&event!=null)fieldEvents.npcSpawned(event.getNpc());}
    void onNpcDespawned(net.runelite.api.events.NpcDespawned event){if(event!=null)fieldEvents.npcDespawned(event.getNpc());}
    private boolean observing()
    {
        Preferences p = preferences;
        return p.autoHud || p.autoGuide || p.highlights || p.labels || panel != null && panel.isShowing();
    }
    void reloadPreferences()
    {
        Preferences next = store.read(GROUP, null, KEY, 1, Preferences.class, MinigameCoach::validPreferences, Preferences::new);
        validate(next); preferences = next;fieldEvents.reload();
        if (panel != null) SwingUtilities.invokeLater(this::refreshPanel);
    }
    private void update(Consumer<Preferences> action)
    {
        if (!started || refreshing) return;
        Preferences before = preferences, next = before.copy(); action.accept(next);
        validate(next);
        if (!store.write(GROUP, null, KEY, 1, next)) { SwingUtilities.invokeLater(this::refreshPanel); return; }
        preferences = next;
    }
    private static void validate(Preferences preferences)
    {
        preferences.scalePercent = Math.max(70, Math.min(150, preferences.scalePercent));
        preferences.fragmentTarget=Math.max(0,Math.min(500,preferences.fragmentTarget));
        preferences.warmthThreshold=Math.max(5,Math.min(75,preferences.warmthThreshold));
        preferences.volcanicExitSeconds=Math.max(15,Math.min(180,preferences.volcanicExitSeconds));
        if(preferences.volcanicRole==null||!MinigameNativeObjectives.VOLCANIC_ROLES.contains(preferences.volcanicRole))preferences.volcanicRole="Team overview";
        preferences.temporossFishBatch=Math.max(1,Math.min(25,preferences.temporossFishBatch));
        Set<String> valid = new LinkedHashSet<>();
        for (MinigameActivityCatalog.Activity activity : MinigameActivityCatalog.Activity.values())
            if (preferences.disabledActivities != null && preferences.disabledActivities.contains(activity.name())) valid.add(activity.name());
        preferences.disabledActivities = valid;
    }
    private static boolean validPreferences(Preferences p)
    {
        if (p == null || p.version != 1 || p.scalePercent < 70 || p.scalePercent > 150 || p.disabledActivities == null
            || p.disabledActivities.size() > MinigameActivityCatalog.Activity.values().length
            || p.temporossFishBatch<1||p.temporossFishBatch>25||p.volcanicRole==null||!MinigameNativeObjectives.VOLCANIC_ROLES.contains(p.volcanicRole)
            || p.fragmentTarget<0||p.fragmentTarget>500||p.warmthThreshold<5||p.warmthThreshold>75||p.volcanicExitSeconds<15||p.volcanicExitSeconds>180) return false;
        for (String name : p.disabledActivities)
        { try { MinigameActivityCatalog.Activity.valueOf(name); } catch (RuntimeException invalid) { return false; } }
        return true;
    }
    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column(); panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 8, 0));
        panel.add(SidebarUi.heading("MINIGAME COACH"));
        autoHud = choice("Automatic minigame HUD", "minigameAutoHud", () -> update(p -> p.autoHud = autoHud.isSelected()));
        highlights = choice("Resource highlights", "minigameHighlights", () -> update(p -> p.highlights = highlights.isSelected()));
        labels = choice("Object & tile labels", "minigameLabels", () -> update(p -> p.labels = labels.isSelected()));
        npcHighlights=choice("NPC references","minigameNpcHighlights",()->update(p->p.npcHighlights=npcHighlights.isSelected()));
        numberedTargets=choice("Number target references","minigameNumberedTargets",()->update(p->p.numberedTargets=numberedTargets.isSelected()));
        compactHud=choice("Compact HUD","minigameCompactHud",()->update(p->p.compactHud=compactHud.isSelected()));
        GuideText size = SidebarUi.label("HUD scale · 100%"); panel.add(size);
        scale = new JSlider(70, 150, preferences.scalePercent); scale.setName("minigameHudScale"); scale.setOpaque(false);
        scale.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30)); panel.add(scale);
        scale.addChangeListener(event ->
        {
            size.setText("HUD scale · " + scale.getValue() + "%");
            if (!scale.getValueIsAdjusting()) update(p -> p.scalePercent = scale.getValue());
        });
        JPanel current = SidebarUi.section("CURRENT ACTIVITY"); panel.add(current);
        activity = SidebarUi.label("No active minigame"); activity.setFont(GuideText.FONT.deriveFont(java.awt.Font.BOLD)); current.add(activity);
        facts = SidebarUi.label(""); current.add(facts);
        stepTitle = SidebarUi.heading(""); current.add(stepTitle);
        stepBody = SidebarUi.label("");stepBody.setName("minigameCurrentInstruction");current.add(stepBody);
        openGuide = SidebarUi.button("Open current guide", () ->
        {
            MinigameActivityCatalog.Snapshot selected = reading();
            Consumer<String> open = openGuideAction;
            if (started && selected.activity != null && selected.guideId != null && (open != null || guideBridgeReady))
            { if (open != null) open.accept(selected.guideId); else selectGuide.accept(selected.guideId); }
        });
        openGuide.setName("openMinigameGuide"); current.add(openGuide);
        homeRoute=SidebarUi.button("Route to customer",()->
        {Consumer<net.runelite.api.coords.WorldPoint> action=routeAction;net.runelite.api.coords.WorldPoint destination=catalog.homeDestination();
            if(started&&Objects.equals(accountProfile,configManager.getRSProfileKey())&&reading().activity==MinigameActivityCatalog.Activity.MAHOGANY_HOMES&&destination!=null&&action!=null)action.accept(destination);});
        homeRoute.setName("minigameHomeRoute");current.add(homeRoute);
        runStatus=SidebarUi.label("");current.add(runStatus);
        resumeCoaching=SidebarUi.button("Resume coaching",()->{if(started)resumeGuide();});
        resumeCoaching.setName("minigameResumeCoach");current.add(resumeCoaching);
        createLearningPanel();
        JPanel support = SidebarUi.section("ACTIVITY SETTINGS"); panel.add(support);
        JPanel activities = SidebarUi.column(); activities.setVisible(false);
        JButton expand = SidebarUi.button("Show activities", () -> {});
        expand.setName("minigameActivitySettings");
        expand.addActionListener(event ->
        {
            activities.setVisible(!activities.isVisible());
            expand.setText(activities.isVisible() ? "Hide activities" : "Show activities");
            support.invalidate(); panel.invalidate(); panel.revalidate(); panel.repaint();
        });
        support.add(expand); support.add(activities);
        fragmentTarget=spinner(activities,"Guardian fragment batch","minigameFragmentTarget",0,500,10,p->p.fragmentTarget=((Number)fragmentTarget.getValue()).intValue());
        warmthThreshold=spinner(activities,"Warmth reminder at %","minigameWarmthThreshold",5,75,5,p->p.warmthThreshold=((Number)warmthThreshold.getValue()).intValue());
        volcanicExitSeconds=spinner(activities,"Mine exit reminder · sec","minigameVolcanicExit",15,180,15,p->p.volcanicExitSeconds=((Number)volcanicExitSeconds.getValue()).intValue());
        activities.add(SidebarUi.label("Volcanic Mine role"));
        volcanicRole=new JComboBox<>(MinigameNativeObjectives.VOLCANIC_ROLES.toArray(new String[0]));
        volcanicRole.setName("minigameVolcanicRole");volcanicRole.setFont(GuideText.FONT);volcanicRole.setBackground(SidebarUi.CARD);volcanicRole.setForeground(SidebarUi.TEXT);volcanicRole.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));
        volcanicRole.addActionListener(e->update(p->p.volcanicRole=(String)volcanicRole.getSelectedItem()));activities.add(volcanicRole);
        winterFletch=checkBox("Fletch Wintertodt roots","minigameWinterFletch",()->update(p->p.winterFletch=winterFletch.isSelected()));activities.add(winterFletch);
        temporossCook=checkBox("Cook Tempoross fish","minigameTemporossCook",()->update(p->p.temporossCook=temporossCook.isSelected()));activities.add(temporossCook);
        temporossFishBatch=spinner(activities,"Tempoross fish batch","minigameTemporossBatch",1,25,1,p->p.temporossFishBatch=((Number)temporossFishBatch.getValue()).intValue());
        activities.add(fieldEvents.controls());
        for (MinigameActivityCatalog.Snapshot definition : catalog.supportedActivities())
        {
            activities.add(SidebarUi.label(definition.title + " · " + (definition.liveSupported ? "Observed HUD" : "Guide")));
            JCheckBox enabled = checkBox("Use this coach", "minigameEnabled" + definition.activity.name(), () -> {});
            enabled.addActionListener(event -> update(p ->
            {
                if (enabled.isSelected()) p.disabledActivities.remove(definition.activity.name());
                else p.disabledActivities.add(definition.activity.name());
            }));
            activityChoices.put(definition.activity, enabled); activities.add(enabled);
        }
        panel.addHierarchyListener(event -> { if (panel.isShowing()) refreshPanel(); });
        refreshPanel(); return panel;
    }
    private JCheckBox choice(String title, String key, Runnable action)
    {
        JCheckBox box = checkBox(title, key, action); panel.add(box); return box;
    }
    private JCheckBox checkBox(String title, String key, Runnable action)
    {
        JCheckBox box = new JCheckBox(title); box.setName(key); box.setFont(GuideText.FONT);
        box.setForeground(SidebarUi.TEXT); box.setOpaque(false); box.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        box.addActionListener(event -> action.run()); return box;
    }
    private void refreshPanel()
    {
        if (panel == null) return;
        refreshing = true;
        try
        {
            Preferences p = preferences;
            autoHud.setSelected(p.autoHud); highlights.setSelected(p.highlights); labels.setSelected(p.labels);
            npcHighlights.setSelected(p.npcHighlights);numberedTargets.setSelected(p.numberedTargets);compactHud.setSelected(p.compactHud);
            volcanicRole.setSelectedItem(p.volcanicRole);volcanicRole.setEnabled(started);
            winterFletch.setSelected(p.winterFletch);winterFletch.setEnabled(started);temporossCook.setSelected(p.temporossCook);temporossCook.setEnabled(started);
            temporossFishBatch.setValue(p.temporossFishBatch);temporossFishBatch.setEnabled(started);
            fragmentTarget.setValue(p.fragmentTarget);warmthThreshold.setValue(p.warmthThreshold);volcanicExitSeconds.setValue(p.volcanicExitSeconds);
            fragmentTarget.setEnabled(started);warmthThreshold.setEnabled(started);volcanicExitSeconds.setEnabled(started);
            for (JCheckBox box : new JCheckBox[] {autoHud, highlights, labels,npcHighlights,numberedTargets,compactHud}) box.setEnabled(started);
            scale.setValue(p.scalePercent); scale.setEnabled(started);
            for (Map.Entry<MinigameActivityCatalog.Activity, JCheckBox> entry : activityChoices.entrySet())
            { entry.getValue().setSelected(!p.disabledActivities.contains(entry.getKey().name())); entry.getValue().setEnabled(started); }
            MinigameActivityCatalog.Snapshot current = reading();
            activity.setText(current.activity == null ? "No active minigame" : current.title);
            facts.setText(factsText(current));
            homeRoute.setVisible(!guidanceDismissed&&current.activity==MinigameActivityCatalog.Activity.MAHOGANY_HOMES&&catalog.homeDestination()!=null);
            homeRoute.setEnabled(started&&routeAction!=null&&Objects.equals(accountProfile,configManager.getRSProfileKey()));
            boolean step = !guidanceDismissed&&MinigameReleasePolicy.allowsAutomaticGuidance(current.activity) && !current.steps.isEmpty();
            stepTitle.setText(step ? "NEXT · " + current.steps.get(0).title : "");
            stepBody.setText(step ? current.steps.get(0).instruction : "");
            stepTitle.setVisible(step);stepBody.setVisible(step);openGuide.setVisible(!guidanceDismissed);
            openGuide.setEnabled(started && current.activity != null && current.guideId != null && (guideBridgeReady || openGuideAction != null));
            resumeCoaching.setVisible(guidanceDismissed||lessonDismissed);resumeCoaching.setEnabled(started);
            MinigameRunProgress.View run=progress.view();
            runStatus.setText(guidanceDismissed?"Run ended":run.active()?(MinigameReleasePolicy.nativeFactsOnly(current.activity)
                ? "This visit · native facts" : "This visit · "+run.phaseChanges+" objective changes"):"");
            learningPanel.setVisible(!guidanceDismissed&&!lessonDismissed);
            if(guidanceDismissed)
            {
                lessonTitle.setText("");lessonBody.setText("");lessonStatus.setText("");lessonCounter.setText("");
                lessonComplete.setEnabled(false);lessonBack.setEnabled(false);lessonNext.setEnabled(false);lessonUndo.setEnabled(false);lessonReset.setEnabled(false);
            }
            else refreshLearning();
            panel.revalidate(); panel.repaint();
        }
        finally { refreshing = false; }
    }
    private JSpinner spinner(JPanel parent,String label,String name,int min,int max,int step,Consumer<Preferences> change)
    {
        parent.add(SidebarUi.label(label));JSpinner control=new JSpinner(new SpinnerNumberModel(min,min,max,step));
        control.setName(name);control.setFont(GuideText.FONT);control.setBackground(SidebarUi.CARD);control.setForeground(SidebarUi.TEXT);
        ((JSpinner.DefaultEditor)control.getEditor()).getTextField().setBackground(SidebarUi.CARD);((JSpinner.DefaultEditor)control.getEditor()).getTextField().setForeground(SidebarUi.TEXT);control.getAccessibleContext().setAccessibleName(label);control.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));
        control.addChangeListener(e->update(change));parent.add(control);return control;
    }
    private void createLearningPanel()
    {
        JPanel section=SidebarUi.section("OPTIONAL PRACTICE PLAN");learningPanel=section;section.setName("minigameLearningPlan");panel.add(section);
        lessonChoice=new JComboBox<>(MinigameLessonCatalog.all().toArray(new MinigameLessonCatalog.Lesson[0]));
        lessonChoice.setName("minigameLessonChoice");lessonChoice.setFont(GuideText.FONT);lessonChoice.setBackground(SidebarUi.CARD);lessonChoice.setForeground(SidebarUi.TEXT);lessonChoice.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));
        lessonChoice.getAccessibleContext().setAccessibleName("Minigame learning plan");section.add(lessonChoice);
        followLesson=checkBox("Follow current activity","minigameFollowLesson",()->
        {if(refreshing)return;followObservedLesson=followLesson.isSelected();MinigameActivityCatalog.Snapshot current=reading();
            if(followObservedLesson&&MinigameReleasePolicy.allowsAutomaticGuidance(current.activity))learning.follow(current);refreshPanel();});section.add(followLesson);
        lessonChoice.addActionListener(e->{if(refreshing)return;MinigameLessonCatalog.Lesson next=(MinigameLessonCatalog.Lesson)lessonChoice.getSelectedItem();
            followObservedLesson=false;learning.select(next);refreshPanel();});
        lessonCounter=new JLabel();lessonCounter.setFont(GuideText.FONT);lessonCounter.setForeground(SidebarUi.GOLD);section.add(lessonCounter);
        lessonTitle=SidebarUi.heading("");section.add(lessonTitle);lessonBody=SidebarUi.label("");section.add(lessonBody);lessonStatus=SidebarUi.label("");section.add(lessonStatus);
        lessonComplete=SidebarUi.button("Complete & next",()->{if(!started)return;followObservedLesson=false;if(learning.completeAndAdvance()&&learning.done())closeLearningGuide();else refreshPanel();});
        lessonComplete.setName("minigameLessonComplete");section.add(lessonComplete);
        JButton closeLesson=SidebarUi.button("Close guide",this::closeLearningGuide);closeLesson.setName("minigameLessonClose");section.add(closeLesson);
        JPanel paging=new JPanel(new java.awt.GridLayout(1,2,6,0));paging.setOpaque(false);paging.setMaximumSize(new Dimension(Integer.MAX_VALUE,32));
        lessonBack=SidebarUi.button("Back",()->{followObservedLesson=false;learning.back();refreshPanel();});lessonBack.setName("minigameLessonBack");paging.add(lessonBack);
        lessonNext=SidebarUi.button("Next",()->{followObservedLesson=false;learning.next();refreshPanel();});lessonNext.setName("minigameLessonNext");paging.add(lessonNext);section.add(paging);
        lessonUndo=SidebarUi.button("Undo completion",()->{followObservedLesson=false;learning.undo();refreshPanel();});lessonUndo.setName("minigameLessonUndo");section.add(lessonUndo);
        lessonReset=SidebarUi.button("Restart this plan",()->{MinigameLessonCatalog.Lesson selected=learning.lesson();learning.clear();learning.select(selected);followObservedLesson=false;refreshPanel();});
        lessonReset.setName("minigameLessonReset");section.add(lessonReset);
        if(learning.lesson()==null)learning.select(MinigameLessonCatalog.get(MinigameActivityCatalog.Activity.GOTR));
    }
    private void closeLearningGuide()
    {
        MinigameLessonCatalog.Lesson lesson=learning.lesson();if(lesson==null)return;
        MinigameActivityCatalog.Snapshot current=reading();String guide=null;
        if(current.activity==lesson.activity)
        {guide=current.guideId;dismissGuide();}
        else for(MinigameActivityCatalog.Snapshot definition:catalog.supportedActivities())
            if(definition.activity==lesson.activity){guide=definition.guideId;break;}
        lessonDismissed=true;
        if(guide!=null)closeGuideAction.accept(guide);
        refreshPanel();
    }

    private void refreshLearning()
    {
        MinigameLessonCatalog.Lesson lesson=learning.lesson();
        if(lesson==null){lesson=MinigameLessonCatalog.get(MinigameActivityCatalog.Activity.GOTR);learning.select(lesson);}
        lessonChoice.setSelectedItem(lesson);lessonChoice.setEnabled(started);
        followLesson.setSelected(followObservedLesson&&!MinigameReleasePolicy.nativeFactsOnly(reading().activity));
        followLesson.setEnabled(started&&!MinigameReleasePolicy.nativeFactsOnly(reading().activity));
        int index=learning.index(),size=lesson.milestones.size();boolean done=index>=size;
        MinigameActivityCatalog.Snapshot current=reading();
        boolean live=followObservedLesson&&current.activity==lesson.activity&&!current.terminal&&!current.objectiveKey.isEmpty()&&!current.steps.isEmpty();
        lessonCounter.setText(live?"LIVE TASK":done?"PLAN COMPLETE":("STEP "+(index+1)+" / "+size));
        lessonTitle.setText(live?current.steps.get(0).title:done?"Ready for the next run":lesson.milestones.get(index).title);
        lessonBody.setText(live?current.steps.get(0).instruction:done?"Restart this plan for another practice run or choose the next activity.":lesson.milestones.get(index).instruction);
        lessonStatus.setText(learning.completed()+" / "+size+" checked off");
        lessonComplete.setEnabled(started&&!done);lessonBack.setEnabled(started&&index>0);lessonNext.setEnabled(started&&!done);lessonUndo.setEnabled(started&&learning.canUndo());lessonReset.setEnabled(started);
    }
    static String factsText(MinigameActivityCatalog.Snapshot snapshot)
    {
        StringBuilder facts = new StringBuilder();
        for (MinigameActivityCatalog.Fact fact : snapshot.facts)
        {
            if (facts.length() > 0) facts.append('\n');
            facts.append(fact.label).append(" · ").append(fact.value);
        }
        return facts.toString();
    }
    private static String uiKey(MinigameActivityCatalog.Snapshot snapshot)
    {
        StringBuilder key = new StringBuilder().append(snapshot.activity).append('|').append(factsText(snapshot));
        if (!snapshot.steps.isEmpty()) key.append('|').append(snapshot.steps.get(0).title).append('|').append(snapshot.steps.get(0).instruction);
        return key.toString();
    }
}
