/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.LinkBrowser;

/** Guide selection follows native progress and maintains its owned coaching and travel overlays. */
@Singleton
public final class RunewardenGuides
{
    private static final Color GOLD = new Color(220, 185, 90);
    private static final Color BACKGROUND = new Color(21, 22, 24);
    private static final String CONFIG_GROUP = "runewarden";
    private static final String[] CATEGORY_LABELS = {"All guides", "Quest Helper", "Training", "Minigames", "Bosses", "Wave guides"};
    private static final String[] CATEGORIES = {"", "quest", "training", "minigame", "boss", "waves"};
    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager configManager;
    private final GuideCatalog catalog;
    private final GuideNavigation navigation;
    private final BossGuideLifecycle bossArena;
    private final GuideMarkerOverlay markerOverlay;
    private final GuideCoachOverlay coachOverlay;
    private final OverlayManager overlayManager;
    private final Map<String, Integer> progress = new HashMap<>();
    private final Map<String, Set<String>> completed = new HashMap<>();
    private final ItemManager itemManager;
    private final QuestOrder questOrder;
    private volatile java.util.function.Function<String, Boolean> questHelperAction = id -> false;
    void setQuestHelperAction(java.util.function.Function<String, Boolean> action)
    { questHelperAction = action == null ? id -> false : action; }
    private java.util.function.IntConsumer openItemAction = id -> {};
    private volatile Set<String> finishedQuests = Collections.emptySet();
    private volatile Map<String, Integer> questUnlocks = Collections.emptyMap();
    private volatile Selection selection = new Selection(null, null, 0);
    private volatile long accountEpoch;
    private volatile boolean completionQueued;
    private volatile boolean progressInitialized,progressLoaded;
    private volatile boolean dismissed;
    /** Invalidates queued opens and paints when a run ends or the account changes. */
    private final java.util.concurrent.atomic.AtomicLong selectionRequests = new java.util.concurrent.atomic.AtomicLong();
    private volatile java.util.function.Consumer<String> completionAction=ignored->{};
    void setCompletionAction(java.util.function.Consumer<String> action){completionAction=action==null?ignored->{}:action;}
    private volatile java.util.function.Consumer<String> nativeCompletionAction=ignored->{};
    private volatile Runnable dismissAction=()->{},explicitOpenAction=()->{},refreshProgressAction=()->{};
    private volatile boolean automaticSuppressed;
    private java.util.function.Function<String,GuideLiveProgress.Reading> progressSupplier=id->GuideLiveProgress.Reading.UNKNOWN;
    private volatile GuideLiveProgress.Reading liveProgress=GuideLiveProgress.Reading.UNKNOWN;
    private volatile boolean liveProgressFromActivity;
    void setNativeCompletionAction(java.util.function.Consumer<String> action){nativeCompletionAction=action==null?ignored->{}:action;}
    void setDismissAction(Runnable action){dismissAction=action==null?()->{}:action;}
    void setExplicitOpenAction(Runnable action){explicitOpenAction=action==null?()->{}:action;}
    void setRefreshProgressAction(Runnable action){refreshProgressAction=action==null?()->{}:action;}
    void setProgressSupplier(java.util.function.Function<String,GuideLiveProgress.Reading> supplier)
    {progressSupplier=supplier==null?id->GuideLiveProgress.Reading.UNKNOWN:supplier;}
    void resumeAutomaticGuidance(){automaticSuppressed=false;}
    private java.util.function.BooleanSupplier automaticTravelAllowed=()->true;
    void setAutomaticTravelAllowed(java.util.function.BooleanSupplier allowed)
    { automaticTravelAllowed=allowed==null?()->true:allowed; }
    private java.util.function.Supplier<MinigameActivityCatalog.Snapshot> activitySupplier=()->MinigameActivityCatalog.Snapshot.EMPTY;
    private volatile MinigameActivityCatalog.Snapshot liveActivity=MinigameActivityCatalog.Snapshot.EMPTY;
    private volatile String liveActivityKey="";
    private JPanel liveActivityPanel;private GuideText liveActivityText;
    void setActivitySupplier(java.util.function.Supplier<MinigameActivityCatalog.Snapshot> supplier){activitySupplier=supplier==null?()->MinigameActivityCatalog.Snapshot.EMPTY:supplier;}
    private java.util.function.Supplier<TrainingProgressTool.Reading> trainingSupplier=()->null;
    private volatile String trainingFocusKey="";
    void setTrainingSupplier(java.util.function.Supplier<TrainingProgressTool.Reading> supplier)
    {trainingSupplier=supplier==null?()->null:supplier;}
    private static final class Selection
    {
        final GuideCatalog.Entry entry;
        final GuideCatalog.Step step;
        final int epoch;
        Selection(GuideCatalog.Entry entry, GuideCatalog.Step step, int epoch)
        { this.entry = entry; this.step = step; this.epoch = epoch; }
    }
    private JPanel panel;
    private JPanel detail;
    private JTextField search;
    private JComboBox<String> categories;
    private JPanel questControls;
    private JComboBox<String> questSort;
    private JCheckBox hideCompletedQuests;
    private GuideText nextQuestLabel;
    private JButton openNextQuest;
    private GuideCatalog.Entry nextQuest;
    private JList<GuideCatalog.Entry> results;
    private DefaultListModel<GuideCatalog.Entry> resultModel;
    private GuideText resultCount;
    private GuideText requirementLabel;
    private GuideText stepLabel;
    private JPanel stepItems;
    private GuideText stepCount;
    private GuideText status;
    private JComboBox<String> steps;
    private JComboBox<String> sections;
    private final List<Integer> sectionStarts = new ArrayList<>();
    private JButton previousSection;
    private JButton nextSection;
    private JButton previous;
    private JButton next;
    private GuideText routeStatus;
    private volatile String routeStatusText = "";
    private JButton complete;
    private JButton undoComplete;
    private volatile GuideCatalog.Entry selected;
    private List<GuideCatalog.DisplayStep> selectedSteps = Collections.emptyList();
    private int stepIndex;
    private int ticks;
    private boolean rebuilding;
    private boolean rebuildingResults;
    private boolean started;
    private volatile boolean requirementsDirty;
    private int selectionEpoch;
    private long progressAccountEpoch;
    private GuideCatalog.Step routedStep;
    private GuideCatalog.Entry routedEntry;
    private volatile boolean routeAvailable, routePending, routeSuppressed;
    private final java.util.concurrent.atomic.AtomicBoolean routeQueued = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile long routingGeneration = -1, routingManualRevision;

    public RunewardenGuides(Client client, ClientThread clientThread, ConfigManager configManager,
        GuideCatalog catalog, GuideNavigation navigation, GuideMarkerOverlay markerOverlay, GuideCoachOverlay coachOverlay,
        OverlayManager overlayManager)
    {
        this(client, clientThread, configManager, catalog, navigation, markerOverlay, coachOverlay, overlayManager, null);
    }

    @Inject public RunewardenGuides(Client client, ClientThread clientThread, ConfigManager configManager,
        GuideCatalog catalog, GuideNavigation navigation, GuideMarkerOverlay markerOverlay, GuideCoachOverlay coachOverlay,
        OverlayManager overlayManager, ItemManager itemManager)
    {
        this.client = client;
        this.bossArena = new BossGuideLifecycle(client, catalog);
        this.clientThread = clientThread;
        this.configManager = configManager;
        this.catalog = catalog;
        this.questOrder = catalog.questOrder();
        this.navigation = navigation;
        this.markerOverlay = markerOverlay;
        this.coachOverlay = coachOverlay;
        this.overlayManager = overlayManager;
        this.itemManager = itemManager;
        this.coachOverlay.setCloseAction(this::closeGuide);
    }

    public JComponent createPanel()
    {
        if (panel != null) return panel;
        catalog.load();
        panel = vertical();
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        GuideText heading = text("GUIDE DECK");
        heading.setForeground(GOLD);
        panel.add(heading);
        panel.add(button("Close guide", this::closeGuide));
        categories = new JComboBox<>(CATEGORY_LABELS);
        categories.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        categories.addActionListener(event -> refreshResults());
        panel.add(categories);
        search = new JTextField();
        search.setToolTipText("Search quests, minigames, training or bosses");
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        search.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent event) { refreshResults(); }
            public void removeUpdate(DocumentEvent event) { refreshResults(); }
            public void changedUpdate(DocumentEvent event) { refreshResults(); }
        });
        panel.add(search);
        questControls = vertical();
        questSort = new JComboBox<>(new String[] { "Optimal order", "Name" });
        questSort.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        questSort.addActionListener(event -> refreshResults());
        questControls.add(questSort);
        hideCompletedQuests = new JCheckBox("Hide completed quests", true);
        hideCompletedQuests.setBackground(BACKGROUND);
        hideCompletedQuests.setForeground(Color.WHITE);
        hideCompletedQuests.addActionListener(event -> refreshResults());
        questControls.add(hideCompletedQuests);
        nextQuestLabel = text("");
        nextQuestLabel.setForeground(GOLD);
        questControls.add(nextQuestLabel);
        openNextQuest = button("Open next in Quest Helper", () -> { if (nextQuest != null) selectGuide(nextQuest); });
        questControls.add(openNextQuest);
        questControls.add(button("Wiki quest order", () -> LinkBrowser.browse(questOrder.source())));
        panel.add(questControls);
        resultCount = text("");
        panel.add(resultCount);
        resultModel = new DefaultListModel<>();
        results = new JList<GuideCatalog.Entry>(resultModel)
        {
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
        };
        results.addComponentListener(new java.awt.event.ComponentAdapter()
        {
            @Override public void componentResized(java.awt.event.ComponentEvent event)
            {
                results.setFixedCellWidth(Math.max(60, results.getWidth()));
                results.revalidate();
            }
        });
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.addListSelectionListener(event -> {
            if (!rebuildingResults && !event.getValueIsAdjusting() && results.getSelectedValue() != null)
                selectGuide(results.getSelectedValue());
        });
        JScrollPane list = new JScrollPane(results);
        results.setFont(GuideText.FONT);
        results.setCellRenderer((listView, value, index, isSelected, focus) -> {
            boolean questView = categories.getSelectedIndex() == 1 && questSort.getSelectedIndex() == 0;
            String prefix = value != null && questView ? (questOrder.ranked(value) ? questOrder.rank(value) + " · " : "Unranked · ") : "";
            GuideText label = text(value == null ? "" : prefix + value.title + (finishedQuests.contains(value.id) ? " ✓" : ""));
            label.setOpaque(true);
            label.setBackground(isSelected ? new Color(66, 54, 29) : BACKGROUND);
            label.setForeground(Color.WHITE);
            label.setBorder(BorderFactory.createEmptyBorder(7, 6, 7, 6));
            label.setWrapWidth(Math.max(80, listView.getWidth() - 4));
            return label;
        });
        list.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        list.setMinimumSize(new Dimension(0, 80));
        list.setPreferredSize(new Dimension(190, 155));
        list.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        panel.add(list);
        detail = vertical();
        panel.add(detail);
        panel.add(Box.createVerticalStrut(10));
        status = text("Choose a guide");
        panel.add(status);
        refreshResults();
        return panel;
    }

    public void start()
    {
        catalog.load();
        if (!started) { overlayManager.add(markerOverlay); overlayManager.add(coachOverlay); coachOverlay.start(); started = true; }
    }

    public void stop()
    {
        selectionRequests.incrementAndGet();dismissed=true;bossArena.reset();
        clearOwnedRoute();
        liveActivity=MinigameActivityCatalog.Snapshot.EMPTY;liveProgress=GuideLiveProgress.Reading.UNKNOWN;liveProgressFromActivity=false;liveActivityKey="";trainingFocusKey="";
        accountEpoch++;automaticSuppressed=false;
        markerOverlay.select(null);
        coachOverlay.clear();
        if (started) { overlayManager.remove(markerOverlay); overlayManager.remove(coachOverlay); coachOverlay.stop(); started = false; }
        selected = null;
        finishedQuests = Collections.emptySet();
        questUnlocks = Collections.emptyMap();
        progress.clear();
        completed.clear();
        selection = new Selection(null, null, ++selectionEpoch);
    }

    public void onGameStateChanged(GameState state)
    {
        if (state == GameState.LOGIN_SCREEN)
        {
            onProfileChanged();
            requirementsDirty = true;
        }
        if (state == GameState.LOGGED_IN) requirementsDirty = true;
    }

    /** Called when RuneLite changes the active character profile, including while logged in. */
    void onProfileChanged()
    {
        long request=selectionRequests.incrementAndGet();dismissed=true;bossArena.reset();
        clearOwnedRoute();
        liveActivity=MinigameActivityCatalog.Snapshot.EMPTY;liveProgress=GuideLiveProgress.Reading.UNKNOWN;liveProgressFromActivity=false;liveActivityKey="";trainingFocusKey="";
        accountEpoch++;automaticSuppressed=false;
        finishedQuests = Collections.emptySet();
        questUnlocks = Collections.emptyMap();
        selection = new Selection(null, null, 0);
        markerOverlay.select(null);coachOverlay.clear();
        SwingUtilities.invokeLater(() -> {
            if(request!=selectionRequests.get())return;
            progressAccountEpoch=accountEpoch;
            progress.clear();
            completed.clear();
            selected = null;
            if (results != null) results.clearSelection();
            selection = new Selection(null, null, ++selectionEpoch);
                if (detail != null) { detail.removeAll(); detail.revalidate(); detail.repaint(); }
            markerOverlay.select(null);
            coachOverlay.clear();
                refreshResults();
        });
        requirementsDirty = true;
    }

    public void onGameTick()
    {
        checkObservedCompletion();
        refreshLiveActivity();
        refreshTrainingFocus();
        if (bossArena.leftArena(selectedGuide())) { clearSelection(); return; }
        boolean available=navigation.available();
        if(routingManualRevision!=navigation.manualRevision())
        { routingManualRevision=navigation.manualRevision();routeSuppressed=true;routePending=false; }
        if(routingGeneration!=navigation.generation())
        { routingGeneration=navigation.generation();if(!routeSuppressed)routePending=true; }
        Selection selectedStep=selection;
        if(!dismissed&&selectedStep.entry!=null&&!routeSuppressed&&guideRoutingReady()
            &&(routePending||available&&!routeAvailable&&navigation.hasTravelDestination(selectedStep.entry,selectedStep.step)))routeSelectedStep();
        routeAvailable=available;
        refreshRouteStatus();
        if (requirementsDirty || ++ticks % 16 == 0)
        {
            requirementsDirty = false;
            refreshQuestFacts();
            refreshRequirements();
        }
    }

    void onActorDeath(net.runelite.api.events.ActorDeath event)
    {
        if (event != null && event.getActor() == client.getLocalPlayer() && client.getLocalPlayer() != null
            && BossGuideLifecycle.isBoss(selectedGuide())) clearSelection();
    }

    private void refreshResults()
    {
        if (resultModel == null || search == null) return;
        String category = CATEGORIES[Math.max(0, categories.getSelectedIndex())];
        List<GuideCatalog.Entry> filtered = new ArrayList<>(catalog.search(search.getText(), category));
        filtered.removeIf(entry -> "markers".equals(entry.category) || (!"quest".equals(category) && "quest".equals(entry.category)));
        boolean quests = "quest".equals(category);
        if (questControls != null) questControls.setVisible(quests);
        if (quests)
        {
            filtered = questOrder.filter(filtered, questSort.getSelectedIndex() == 0,
                hideCompletedQuests.isSelected(), finishedQuests);
            List<GuideCatalog.Entry> route = questOrder.filter(catalog.search("", "quest"), true, true, finishedQuests);
            nextQuest = null;
            for (GuideCatalog.Entry entry : route)
                if (questOrder.ranked(entry) && questUnlocks.getOrDefault(entry.id, 0) > 0) { nextQuest = entry; break; }
            boolean available = nextQuest != null;
            if (!available) for (GuideCatalog.Entry entry : route)
                if (questOrder.ranked(entry)) { nextQuest = entry; break; }
            nextQuestLabel.setText(nextQuest == null ? "Route complete" : (available ? "Next available · " : "Next in route · ") + nextQuest.title);
            openNextQuest.setEnabled(nextQuest != null);
        }
        GuideCatalog.Entry keep = selected;
        rebuildingResults = true;
        resultModel.clear();
        for (GuideCatalog.Entry entry : filtered) resultModel.addElement(entry);
        if (keep != null && filtered.contains(keep)) results.setSelectedValue(keep, false);
        rebuildingResults = false;
        resultCount.setText(filtered.size() + (quests ? (filtered.size() == 1 ? " quest · Quest Helper" : " quests · Quest Helper") : filtered.size() == 1 ? " guide" : " guides"));
    }

    /** RuneLite quest scripts and skill values are read only on the client thread. */
    private void refreshQuestFacts()
    {
        long account = accountEpoch;
        Set<String> done = new java.util.HashSet<>();
        Map<String, Integer> unlocks = new HashMap<>();
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            Map<String, QuestState> states = new HashMap<>();
            Set<Integer> doneRanks = new java.util.HashSet<>();
            Set<Integer> observedRanks = new java.util.HashSet<>();
            for (Quest quest : Quest.values())
            {
                try
                {
                    QuestState state = quest.getState(client);
                    states.put(QuestOrder.normalize(quest.getName()), state);
                    int rank = questOrder.rank(quest.getName());
                    if (rank != Integer.MAX_VALUE && state != null)
                    {
                        observedRanks.add(rank);
                        if (state == QuestState.FINISHED) doneRanks.add(rank);
                    }
                }
                catch (RuntimeException ignored) { /* Unavailable client facts stay unknown. */ }
            }
            for (GuideCatalog.Entry entry : catalog.search("", "quest"))
            {
                if (states.get(QuestOrder.normalize(entry.title)) == QuestState.FINISHED
                    || doneRanks.contains(questOrder.rank(entry))) done.add(entry.id);
                int ready = 1;
                if (entry.requirementDetails != null)
                    for (JsonObject requirement : entry.requirementDetails)
                    {
                        int state;
                        try { state = requirementState(requirement); }
                        catch (RuntimeException ignored) { state = 0; }
                        if (state < 0) { ready = -1; break; }
                        if (state == 0) ready = 0;
                    }
                // Text-only prerequisites have not been verified against live client facts.
                if ((entry.requirementDetails == null || entry.requirementDetails.isEmpty())
                    && entry.requirements != null && !entry.requirements.isEmpty()) ready = 0;
                if (!states.containsKey(QuestOrder.normalize(entry.title)) && !observedRanks.contains(questOrder.rank(entry))) ready = 0;
                unlocks.put(entry.id, ready);
            }
        }
        SwingUtilities.invokeLater(() -> {
            if (account != accountEpoch) return;
            if (!done.equals(finishedQuests) || !unlocks.equals(questUnlocks))
            {
                finishedQuests = Collections.unmodifiableSet(done);
                questUnlocks = Collections.unmodifiableMap(unlocks);
                refreshResults();
            }
        });
    }

    void openGuide(String id)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            queueSelection(() -> openGuide(id));
            return;
        }
        if (detail == null) createPanel();
        GuideCatalog.Entry entry = catalog.entry(id);
        if (entry != null) selectGuide(entry);
    }

    /** Automatic entries never release a user's explicit close latch. */
    void openAutomaticGuide(String id)
    {
        if(automaticSuppressed)return;
        if(!SwingUtilities.isEventDispatchThread()){queueSelection(()->openAutomaticGuide(id));return;}
        if(automaticSuppressed)return;
        if(detail==null)createPanel();GuideCatalog.Entry entry=catalog.entry(id);
        if(entry!=null)selectGuide(entry,false,true);
    }

    void closeGuide()
    {
        automaticSuppressed=true;clearSelection();dismissAction.run();
    }

    /** Called only after the native activity producer positively observes leaving its context. */
    void closeActivityGuide(MinigameActivityCatalog.Snapshot previous)
    {
        Selection current=selection;
        if(!dismissed&&current.entry!=null&&GuideCompletion.belongsToActivity(current.entry,previous))closeGuide();
    }

    /** Clears the active guide and its owned overlays without discarding saved guide progress. */
    void clearSelection()
    {
        long request=selectionRequests.incrementAndGet();
        dismissed=true;routePending=false;routeSuppressed=true;bossArena.reset();
        liveActivity=MinigameActivityCatalog.Snapshot.EMPTY;liveProgress=GuideLiveProgress.Reading.UNKNOWN;liveProgressFromActivity=false;liveActivityKey="";trainingFocusKey="";
        markerOverlay.select(null);coachOverlay.clear();
        clearOwnedRoute();
        if (!SwingUtilities.isEventDispatchThread())
        {
            long account=accountEpoch;
            SwingUtilities.invokeLater(()->{if(account==accountEpoch&&request==selectionRequests.get())clearSelectionUi();});
            return;
        }
        clearSelectionUi();
    }

    private void clearSelectionUi()
    {
        selected = null;routedEntry=null;routedStep=null;
        selectedSteps = Collections.emptyList();
        selection = new Selection(null, null, ++selectionEpoch);
        if (results != null) results.clearSelection();
        if (detail != null) { detail.removeAll(); detail.revalidate(); detail.repaint(); }
        if (status != null) status.setText("Choose a guide");
        markerOverlay.select(null);
        coachOverlay.clear();
    }

    private void queueSelection(Runnable open)
    {
        long request=selectionRequests.incrementAndGet(),account=accountEpoch;
        SwingUtilities.invokeLater(()->{if(account==accountEpoch&&request==selectionRequests.get())open.run();});
    }

    private void clearOwnedRoute()
    {
        long request=selectionRequests.get();
        clientThread.invokeLater(()->{if(request==selectionRequests.get())navigation.clearOwned("guide");});
    }

    void setOpenItemAction(java.util.function.IntConsumer action)
    { openItemAction = action == null ? id -> {} : action; }

    void openForRun(String id)
    {
        if(!SwingUtilities.isEventDispatchThread()){queueSelection(()->openForRun(id));return;}
        GuideCatalog.Entry entry=catalog.entry(id);
        boolean repeat=entry!=null&&!"quest".equals(entry.category)&&!entry.flattened().isEmpty()
            &&completed.getOrDefault(id,Collections.emptySet()).size()==entry.flattened().size();
        if(repeat){completed.put(id,new java.util.LinkedHashSet<>());progress.put(id,0);}
        if(entry==null)return;
        if(detail==null)createPanel();
        selectGuide(entry,true);if(repeat)saveCompletion();
    }

    void openSection(String id, int sectionIndex)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            queueSelection(() -> openSection(id, sectionIndex));
            return;
        }
        openGuide(id);
        if (selected != null && selected.id.equals(id) && sectionIndex >= 0 && sectionIndex < sectionStarts.size())
            setStep(sectionStarts.get(sectionIndex));
    }

    private void selectGuide(GuideCatalog.Entry entry)
    {selectGuide(entry,false,false);}

    private void selectGuide(GuideCatalog.Entry entry,boolean forRun) {selectGuide(entry,forRun,false);}

    private void selectGuide(GuideCatalog.Entry entry,boolean forRun,boolean automatic)
    {
        if (entry == null) return;
        if(automatic&&automaticSuppressed)return;
        if(!automatic){resumeAutomaticGuidance();explicitOpenAction.run();}
        if ("quest".equals(entry.category))
        {
            clearSelection();
            boolean opened = Boolean.TRUE.equals(questHelperAction.apply(entry.id));
            if (status != null) status.setText(opened ? "Opened in Quest Helper · " + entry.title : "Quest Helper is unavailable. Enable it from RuneLite’s Plugin Hub.");
            return;
        }
        selectionRequests.incrementAndGet();
        if(progressAccountEpoch!=accountEpoch)
        {
            progress.clear();completed.clear();
            progressAccountEpoch=accountEpoch;
        }
        dismissed=false;
        liveActivityKey="";trainingFocusKey="";routedEntry=null;routedStep=null;routePending=false;routeSuppressed=false;routeStatusText="";
        progressInitialized=false;progressLoaded=progress.containsKey(entry.id);
        routingGeneration=navigation.generation();routingManualRevision=navigation.manualRevision();
        selected = entry;
        int epoch = ++selectionEpoch;
        selectedSteps = entry.flattened();
        liveActivity=activitySupplier.get();if(liveActivity==null)liveActivity=MinigameActivityCatalog.Snapshot.EMPTY;
        liveProgress=activityProgress(entry,liveActivity);liveProgressFromActivity=liveProgress.known();
        int observedIndex=progressIndex(entry,liveProgress);
        stepIndex = observedIndex>=0?observedIndex:Math.min(progress.getOrDefault(entry.id, 0), Math.max(0, selectedSteps.size() - 1));
        detail.removeAll();
        detail.add(Box.createVerticalStrut(10));
        GuideText title = text(html(entry.title));
        title.setForeground(GOLD);
        title.setFont(GuideText.FONT.deriveFont(Font.BOLD, 15f));
        detail.add(title);
        detail.add(text(html(entry.subtitle)));
        liveActivityPanel=SidebarUi.section("CURRENT TASK");liveActivityText=text("");liveActivityPanel.add(liveActivityText);liveActivityPanel.setVisible(false);detail.add(liveActivityPanel);
        requirementLabel = text(requirementHtml(entry));
        detail.add(requirementLabel);
        if (entry.optionalUnlocks != null && !entry.optionalUnlocks.isEmpty())
            detail.add(text(html("Optional: " + String.join(" · ", entry.optionalUnlocks))));
        addItems("Required items", entry.items);
        addItems("Recommended items", entry.recommended);
        sections = new JComboBox<>();
        sectionStarts.clear();
        GuideCatalog.Section last = null;
        for (int i = 0; i < selectedSteps.size(); i++)
        {
            GuideCatalog.Section section = selectedSteps.get(i).group;
            if (section != last)
            {
                sections.addItem(section.title);
                sectionStarts.add(i);
                last = section;
            }
        }
        sections.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        sections.setPreferredSize(new Dimension(230, 28));
        sections.setMinimumSize(new Dimension(0, 28));
        sections.addActionListener(event -> {
            if (!rebuilding && sections.getSelectedIndex() >= 0)
                setStep(sectionStarts.get(sections.getSelectedIndex()));
        });
        detail.add(sections);
        JPanel sectionPaging = new JPanel(new GridLayout(1, 2, 4, 0));
        sectionPaging.setBackground(BACKGROUND);
        sectionPaging.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        boolean waves = entry.sections.stream().anyMatch(section -> section.title != null && section.title.startsWith("Wave "));
        previousSection = button(waves ? "← Wave" : "← Section", () -> changeSection(-1));
        nextSection = button(waves ? "Wave →" : "Section →", () -> changeSection(1));
        sectionPaging.add(previousSection);
        sectionPaging.add(nextSection);
        detail.add(sectionPaging);
        steps = new JComboBox<>();
        List<String> options = new ArrayList<>();
        for (int i = 0; i < selectedSteps.size(); i++) options.add((i + 1) + " · " + selectedSteps.get(i).section);
        steps.setModel(new DefaultComboBoxModel<>(options.toArray(new String[0])));
        steps.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        steps.setPreferredSize(new Dimension(230, 28));
        steps.setMinimumSize(new Dimension(0, 28));
        sections.setFont(GuideText.FONT);
        steps.setFont(GuideText.FONT);
        steps.setSelectedIndex(selectedSteps.isEmpty() ? -1 : stepIndex);
        steps.addActionListener(event -> { if (!rebuilding && steps.getSelectedIndex() >= 0) setStep(steps.getSelectedIndex()); });
        detail.add(steps);
        stepCount = text("");
        detail.add(stepCount);
        stepLabel = text("");
        detail.add(stepLabel);
        stepItems = vertical();
        detail.add(stepItems);
        JPanel paging = new JPanel(new GridLayout(1, 2, 4, 0));
        paging.setBackground(BACKGROUND);
        paging.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        previous = button("Previous", () -> setStep(stepIndex - 1));
        next = button("Next step", () -> setStep(stepIndex + 1));
        paging.add(previous);
        paging.add(next);
        detail.add(paging);
        complete = button("Complete step →", this::completeCurrentStep);
        complete.setForeground(GOLD);
        detail.add(complete);
        undoComplete = button("Mark step incomplete", this::undoCurrentCompletion);
        detail.add(undoComplete);
        routeStatus=text("");routeStatus.setForeground(GOLD);detail.add(routeStatus);
        detail.add(button("Clear route", () -> {
            routeSuppressed=true;routePending=false;
            clientThread.invokeLater(()->navigation.clearOwned("guide"));refreshRouteStatus();
        }));
        detail.add(button("Open Wiki guide", () -> { if (entry.source != null) LinkBrowser.browse(entry.source); }));
        renderStep();
        requestCurrentProgress(entry,epoch);
        loadProgress(entry, epoch, forRun);
        requirementsDirty = true;
        detail.revalidate();
        detail.repaint();
    }

    long selectionGeneration() { return selectionRequests.get(); }
    GuideCatalog.Entry selectedGuide() { return dismissed?null:selection.entry; }
    GuideCatalog.Step currentStep() { return dismissed?null:selection.step; }
    List<JsonObject> currentStepItems() { return Collections.emptyList(); }

    private void loadProgress(GuideCatalog.Entry entry, int epoch,boolean forRun)
    {
        if (progressLoaded) return;
        long account = accountEpoch, request=selectionRequests.get();
        clientThread.invokeLater(() -> {
            if (dismissed||request!=selectionRequests.get()||account != accountEpoch || client.getGameState() != GameState.LOGGED_IN) return;
            Integer saved = configManager.getRSProfileConfiguration(CONFIG_GROUP, GuideProgress.positionKey(entry.id), Integer.class);
            String done = configManager.getRSProfileConfiguration(CONFIG_GROUP, GuideProgress.completedKey(entry.id), String.class);
            SwingUtilities.invokeLater(() -> {
                if (dismissed||request!=selectionRequests.get()||account != accountEpoch || selected != entry || selectionEpoch != epoch || progressLoaded) return;
                progressLoaded=true;
                Set<String> restored=GuideProgress.decode(done,selectedSteps);
                restored.addAll(completed.getOrDefault(entry.id,Collections.emptySet()));completed.put(entry.id,restored);
                if(selectedSteps.isEmpty()){renderStep();return;}
                GuideLiveProgress.Reading nativeProgress=liveProgress;
                if(matchesProgress(entry,nativeProgress)&&nativeProgress.terminal){finishSelectedGuide(true);return;}
                int observedIndex=progressIndex(entry,nativeProgress);
                if(observedIndex>=0)
                {
                    if(forRun&&restored.size()==selectedSteps.size()){restored.clear();saveCompletion();}
                    setStep(observedIndex);return;
                }
                if(restored.size()==selectedSteps.size())
                {
                    if(forRun&&!"quest".equals(entry.category)){restored.clear();setStep(0);saveCompletion();return;}
                    finishSelectedGuide();return;
                }
                int position=Math.max(0,Math.min(selectedSteps.size()-1,saved==null?0:saved));
                if(position>=0&&restored.contains(selectedSteps.get(position).step.id))position=GuideProgress.next(selectedSteps,position,restored);
                setStep(position);
            });
        });
    }

    private void saveCompletion()
    {
        if (selected == null) return;
        String key = selected.id;
        String done = GuideProgress.encode(completed.getOrDefault(key, Collections.emptySet()));
        long account = accountEpoch;
        clientThread.invokeLater(() -> {
            if (account == accountEpoch && client.getGameState() == GameState.LOGGED_IN)
                configManager.setRSProfileConfiguration(CONFIG_GROUP, GuideProgress.completedKey(key), done);
        });
    }

    private void completeCurrentStep()
    {
        if (dismissed||selected == null || selectedSteps.isEmpty()) return;
        Set<String> done = completed.computeIfAbsent(selected.id, key -> new java.util.LinkedHashSet<>());
        done.add(selectedSteps.get(stepIndex).step.id);
        saveCompletion();
        boolean nativeObjective=matchesProgress(selected,liveProgress);
        if(!nativeObjective&&done.size()==selectedSteps.size()||stepIndex==selectedSteps.size()-1){finishSelectedGuide();return;}
        int nextIndex = nativeObjective?Math.min(stepIndex+1,selectedSteps.size()-1):GuideProgress.next(selectedSteps, stepIndex, done);
        setStep(nextIndex);
        status.setText("Step completed ✓");
        detail.revalidate();
        detail.repaint();
    }

    private void finishSelectedGuide(){finishSelectedGuide(false);}
    private void finishSelectedGuide(boolean observedRound)
    {
        if(dismissed||selected==null)return;
        String id=selected.id,title=selected.title;
        closeGuide();
        if(status!=null)status.setText("Completed · "+title);
        if(observedRound)nativeCompletionAction.accept(id);else completionAction.accept(id);
    }

    private void undoCurrentCompletion()
    {
        if (selected == null || selectedSteps.isEmpty()) return;
        completed.computeIfAbsent(selected.id, key -> new java.util.LinkedHashSet<>()).remove(selectedSteps.get(stepIndex).step.id);
        saveCompletion();
        renderStep();
    }

    private void checkObservedCompletion()
    {
        Selection observed = selection;
        if (dismissed || completionQueued || observed.entry == null || observed.step == null
            || "quest".equals(observed.entry.category) || !GuideCompletion.supportsAutomatic(observed.entry)
            || !GuideCompletion.observed(client, observed.entry, observed.step)) return;
        long account = accountEpoch;
        completionQueued = true;
        SwingUtilities.invokeLater(() -> {
            try
            {
                Selection current = selection;
                if (dismissed || account != accountEpoch || current.entry != observed.entry || current.step != observed.step
                    || current.epoch != observed.epoch || selected == null) return;
                if (!completed.getOrDefault(selected.id, Collections.emptySet()).contains(observed.step.id)) completeCurrentStep();
            }
            finally { completionQueued = false; }
        });
    }

    private void requestCurrentProgress(GuideCatalog.Entry entry,int epoch)
    {
        long account=accountEpoch,request=selectionRequests.get();
        clientThread.invokeLater(()->{
            if(dismissed||account!=accountEpoch||request!=selectionRequests.get()||selection.entry!=entry||selection.epoch!=epoch)return;
            refreshProgressAction.run();progressInitialized=true;refreshLiveActivity();refreshTrainingFocus();
        });
    }

    private GuideLiveProgress.Reading activityProgress(GuideCatalog.Entry entry,MinigameActivityCatalog.Snapshot activity)
    {
        if(entry==null||activity==null||!GuideCompletion.matchesActivity(entry,activity)||!MinigameReleasePolicy.allowsAutomaticGuidance(activity.activity))return GuideLiveProgress.Reading.UNKNOWN;
        String stepId=entry.id.equals(activity.guideId)?activity.guideStepId:null;
        if(stepId==null||stepId.isEmpty())
        {
            int index=GuideCompletion.activityStep(entry,activity);List<GuideCatalog.DisplayStep> authored=entry.flattened();
            if(index>=0&&index<authored.size())stepId=authored.get(index).step.id;
        }
        String title=activity.steps.isEmpty()?"":activity.steps.get(0).title;
        String body=activity.steps.isEmpty()?"":activity.steps.get(0).instruction;
        String key=activity.objectiveKey==null||activity.objectiveKey.isEmpty()?title+"|"+body:activity.objectiveKey;
        if(key.equals("|")&&!activity.terminal)return GuideLiveProgress.Reading.UNKNOWN;
        return new GuideLiveProgress.Reading(configManager.getRSProfileKey(),entry.id,stepId,key,title,body,activity.terminal,activity.confirmedStepIds);
    }

    private boolean matchesProgress(GuideCatalog.Entry entry,GuideLiveProgress.Reading reading)
    {
        return entry!=null&&reading!=null&&reading.known()&&entry.id.equals(reading.guideId)
            &&java.util.Objects.equals(reading.profile,configManager.getRSProfileKey());
    }
    private int progressIndex(GuideCatalog.Entry entry,GuideLiveProgress.Reading reading)
    {
        if(!matchesProgress(entry,reading)||reading.stepId==null)return -1;
        List<GuideCatalog.DisplayStep> authored=entry.flattened();
        for(int index=0;index<authored.size();index++)if(reading.stepId.equals(authored.get(index).step.id))return index;
        return -1;
    }

    /** Native captures happen on the client thread; Swing consumes only this immutable reading. */
    private void refreshLiveActivity()
    {
        Selection observed=selection;if(dismissed||observed.entry==null)return;
        MinigameActivityCatalog.Snapshot activity=activitySupplier.get();if(activity==null)activity=MinigameActivityCatalog.Snapshot.EMPTY;
        MinigameActivityCatalog.Snapshot previousActivity=liveActivity;GuideLiveProgress.Reading previousProgress=liveProgress;
        GuideLiveProgress.Reading reading=activityProgress(observed.entry,activity);boolean nativeObjective=reading.known();
        // A hidden activity widget is not evidence that its current commission restarted.
        // The activity producer positively closes this selection on a real departure.
        if(!nativeObjective&&activity.activity==null&&liveProgressFromActivity
            &&matchesProgress(observed.entry,previousProgress)&&GuideCompletion.matchesActivity(observed.entry,previousActivity))
        {activity=previousActivity;reading=previousProgress;nativeObjective=true;}
        liveActivity=activity;
        if(!reading.known())
        {
            GuideLiveProgress.Reading other=progressSupplier.apply(observed.entry.id);
            reading=matchesProgress(observed.entry,other)?other:GuideLiveProgress.Reading.UNKNOWN;
        }
        liveProgress=reading;liveProgressFromActivity=nativeObjective;
        String key=observed.entry.id+"|"+reading.objectiveKey+"|"+reading.stepId+"|"+reading.terminal+"|"+reading.title+"|"+reading.body+"|"+reading.confirmedStepIds;
        if(key.equals(liveActivityKey))return;liveActivityKey=key;long account=accountEpoch,request=selectionRequests.get();
        GuideLiveProgress.Reading captured=reading;
        SwingUtilities.invokeLater(()->{
            if(dismissed||account!=accountEpoch||request!=selectionRequests.get()||selection.entry!=observed.entry||selection.epoch!=observed.epoch||!key.equals(liveActivityKey))return;
            if(matchesProgress(observed.entry,captured)&&captured.terminal){finishSelectedGuide(true);return;}
            int active=progressIndex(observed.entry,captured);
            if(active>=0&&active!=stepIndex)setStep(active);else renderStep();
        });
    }
    private static boolean liveMatches(GuideCatalog.Entry entry,MinigameActivityCatalog.Snapshot activity)
    {return entry!=null&&activity!=null&&GuideCompletion.matchesActivity(entry,activity)&&MinigameReleasePolicy.allowsAutomaticGuidance(activity.activity)&&!activity.steps.isEmpty();}

    private void refreshTrainingFocus()
    {
        Selection observed=selection;
        if(dismissed||observed.entry==null||!"training".equals(observed.entry.category)||matchesProgress(observed.entry,liveProgress)||liveMatches(observed.entry,liveActivity))return;
        TrainingProgressTool.Reading reading=trainingSupplier.get();
        if(reading==null||reading.session==null||!reading.session.running()||reading.session.context==null
            ||!observed.entry.id.equals(reading.session.context.guideId)||reading.session.context.methodId==null||reading.session.context.methodId.isEmpty()
            ||reading.session.profile==null||!reading.session.profile.equals(configManager.getRSProfileKey())||reading.session.runId==null
            ||reading.trainingXp<=0||reading.actions<=0)return;
        String key=observed.entry.id+"|"+reading.session.runId;
        if(key.equals(trainingFocusKey))return;
        int method=-1;List<GuideCatalog.DisplayStep> authored=observed.entry.flattened();
        for(int index=0;index<authored.size();index++)if("method".equals(authored.get(index).group.id)){method=index;break;}
        if(method<0)return;
        trainingFocusKey=key;int active=method;long account=accountEpoch,request=selectionRequests.get();
        SwingUtilities.invokeLater(()->{
            if(!dismissed&&account==accountEpoch&&request==selectionRequests.get()&&selection.entry==observed.entry
                &&!liveMatches(observed.entry,liveActivity))setStep(active);
        });
    }

    private void setStep(int index)
    {
        if (dismissed||selected == null || selectedSteps.isEmpty()) return;
        stepIndex = Math.max(0, Math.min(selectedSteps.size() - 1, index));
        progress.put(selected.id, stepIndex);
        // Store only our own per-character manual position. Never alter Quest Helper or Ground Markers config.
        String guideKey = selected.id;
        int position = stepIndex;
        long account = accountEpoch;
        clientThread.invokeLater(() -> {
            if (account == accountEpoch && client.getGameState() == GameState.LOGGED_IN)
                configManager.setRSProfileConfiguration(CONFIG_GROUP, GuideProgress.positionKey(guideKey), position);
        });
        rebuilding = true;
        steps.setSelectedIndex(stepIndex);
        rebuilding = false;
        renderStep();
    }

    private void changeSection(int direction)
    {
        int index = sections.getSelectedIndex() + direction;
        if (index >= 0 && index < sectionStarts.size()) setStep(sectionStarts.get(index));
    }

    private void renderStep()
    {
        if(dismissed||selected==null)return;
        if (selectedSteps.isEmpty()) { stepLabel.setText("No checklist steps"); return; }
        GuideCatalog.DisplayStep display = selectedSteps.get(stepIndex);
        GuideCatalog.Step step = display.step;
        selection = new Selection(selected, step, selectionEpoch);
        StringBuilder body = new StringBuilder();
        if (display.alternative) body.append("Alternative route\n");
        body.append(step.text);
        if ("waves".equals(selected.category) || selected.id.startsWith("encounter:"))
        {
            for (GuideCatalog.Step cue : display.group.steps)
                if (cue != step) body.append("\n\n").append(cue.text);
        }
        List<String> dialogue = step.dialogue;
        if (dialogue != null && !dialogue.isEmpty()) body.append("\n\nDialogue\n").append(String.join(" → ", dialogue));
        if (step.notes != null) for (String note : step.notes) body.append("\n\n").append(note);
        GuideLiveProgress.Reading objective=liveProgress;
        boolean known=matchesProgress(selected,objective)&&!objective.terminal&&!objective.body.isEmpty();
        boolean currentObjective=known&&(objective.stepId==null||objective.stepId.isEmpty()||objective.stepId.equals(step.id));
        String nativeBody=objective.title.isEmpty()?objective.body:objective.title+"\n"+objective.body;
        stepLabel.setText(currentObjective?nativeBody:body.toString());
        stepItems.removeAll();
        addItems(stepItems, "For this step", currentStepItems());
        stepItems.revalidate();
        stepItems.repaint();
        String coachText = step.text;
        if ("waves".equals(selected.category) && display.group.steps.get(0) == step && display.group.steps.size() > 1)
        {
            coachText = display.group.steps.get(1).text;
            if (display.group.steps.size() > 2) coachText += " " + display.group.steps.get(2).text;
        }
        else if(!trainingFocusKey.isEmpty()&&"method".equals(display.group.id)&&!known&&!liveMatches(selected,liveActivity))
        {
            StringBuilder method=new StringBuilder();
            for(GuideCatalog.Step instruction:display.group.steps){if(method.length()>0)method.append(" ");method.append(instruction.text);}
            coachText=method.toString();
        }
        else if (step.text.startsWith("Position: "))
        {
            for (GuideCatalog.Step cue : display.group.steps)
                if (cue.text.startsWith("Movement cue: ")) { coachText += " " + cue.text; break; }
        }
        int sectionIndex = 0;
        for (int i = 0; i < sectionStarts.size(); i++) if (sectionStarts.get(i) <= stepIndex) sectionIndex = i;
        boolean phaseGuide = "waves".equals(selected.category) || selected.id.startsWith("encounter:");
        Set<String> displayedCompletion=known?objective.confirmedStepIds:completed.getOrDefault(selected.id,Collections.emptySet());
        int completedCount=0;for(GuideCatalog.DisplayStep item:selectedSteps)if(displayedCompletion.contains(item.step.id))completedCount++;
        int completedPhases=0;
        if(phaseGuide)for(int group=0;group<sectionStarts.size();group++)
        {
            int from=sectionStarts.get(group),to=group+1<sectionStarts.size()?sectionStarts.get(group+1):selectedSteps.size();boolean all=true;
            for(int item=from;item<to;item++)all&=displayedCompletion.contains(selectedSteps.get(item).step.id);
            if(all)completedPhases++;
        }
        coachOverlay.select(selected.title,currentObjective?"Current task":display.section,currentObjective?nativeBody:coachText,
            phaseGuide?sectionIndex+1:stepIndex+1,phaseGuide?sectionStarts.size():selectedSteps.size(),phaseGuide?completedPhases:completedCount);
        if(liveActivityPanel!=null)liveActivityPanel.setVisible(known&&!currentObjective);
        if(known&&liveActivityText!=null)liveActivityText.setText(nativeBody);
        boolean done = !currentObjective&&displayedCompletion.contains(step.id);
        stepCount.setText((done ? "✓ " : "") + (stepIndex + 1) + " / " + selectedSteps.size() + " · " + display.section);
        complete.setText(done ? "Completed ✓" : "Complete step →");
        complete.setEnabled(!done);
        undoComplete.setVisible(done);
        stepLabel.setFont(GuideText.FONT.deriveFont(14f));
        rebuilding = true;
        sections.setSelectedIndex(sectionIndex);
        rebuilding = false;
        previousSection.setEnabled(sectionIndex > 0);
        nextSection.setEnabled(sectionIndex + 1 < sectionStarts.size());
        String markerPackId = display.group.markerPackId != null ? display.group.markerPackId : selected.markerPackId;
        // Every manual wave/section choice replaces our overlay. Personal Ground Markers are untouched.
        GuideCatalog.MarkerPack pack = markerPackId == null ? null : catalog.pack(markerPackId);
        markerOverlay.select(pack, display.group.markerNumbers);
        previous.setEnabled(stepIndex > 0);
        next.setEnabled(stepIndex + 1 < selectedSteps.size());
        boolean travelStep = navigation.hasTravelDestination(selected, step);
        if(routeStatus!=null)routeStatus.setVisible(travelStep&&guideTravelAllowed(selected));
        boolean changedRouteStep=routedEntry!=selected||routedStep!=step;
        if(changedRouteStep)
        {
            routedEntry=selected;routedStep=step;routeSuppressed=false;routePending=true;
            routingManualRevision=navigation.manualRevision();routingGeneration=navigation.generation();routeStatusText="";
        }
        if(changedRouteStep||routePending)routeSelectedStep();
    }

    static boolean isTravelStep(GuideCatalog.Entry entry, GuideCatalog.Step step)
    {
        return entry!=null&&step!=null&&step.location!=null
            &&("training".equals(entry.category)||"minigame".equals(entry.category));
    }

    private boolean guideRoutingReady(){return progressInitialized&&progressLoaded&&automaticTravelAllowed.getAsBoolean();}
    private boolean guideTravelAllowed(GuideCatalog.Entry entry)
    {return guideRoutingReady()&&!GuideCompletion.matchesActivity(entry,liveActivity);}

    private void routeSelectedStep()
    {
        if(routeSuppressed||!guideRoutingReady()||!routeQueued.compareAndSet(false,true))return;
        Selection expected=selection;
        long account=accountEpoch,request=selectionRequests.get(),manual=navigation.manualRevision();
        clientThread.invokeLater(()->{
            try
            {
                Selection current=selection;
                if(dismissed||routeSuppressed||!guideRoutingReady()||account!=accountEpoch||request!=selectionRequests.get()
                    ||current.entry!=expected.entry||current.step!=expected.step)return;
                if(manual!=navigation.manualRevision()) {routeSuppressed=true;routePending=false;return;}
                if(!guideTravelAllowed(current.entry)) {navigation.clearOwned("guide");routePending=false;return;}
                routeAvailable=navigation.available();
                boolean sent=navigation.routeStep(expected.entry,expected.step);
                routingGeneration=navigation.generation();
                routePending=navigation.hasTravelDestination(expected.entry,expected.step)&&!sent;
                refreshRouteStatus();
            }
            finally { routeQueued.set(false); }
        });
    }

    private void refreshRouteStatus()
    {
        Selection expected=selection;
        if(dismissed||expected.entry==null||expected.step==null)return;
        String message=routeSuppressed?"Route released":navigation.snapshot().status;
        if(message.equals(routeStatusText))return;routeStatusText=message;
        long account=accountEpoch,request=selectionRequests.get();
        SwingUtilities.invokeLater(()->{
            if(!dismissed&&account==accountEpoch&&request==selectionRequests.get()
                &&selection.entry==expected.entry&&selection.step==expected.step&&routeStatus!=null)
                routeStatus.setText(message);
        });
    }

    private void refreshRequirements()
    {
        GuideCatalog.Entry entry = selected;
        if (entry == null || requirementLabel == null) return;
        String body;
        if (client.getGameState() != GameState.LOGGED_IN) body = requirementHtml(entry);
        else if (entry.requirementDetails == null || entry.requirementDetails.isEmpty()) body = requirementHtml(entry);
        else
        {
            StringBuilder text = new StringBuilder("Unlocks");
            for (JsonObject requirement : entry.requirementDetails)
            {
                int state = requirementState(requirement);
                text.append("\n").append(state > 0 ? "✓ " : state < 0 ? "✕ Needed: " : "? Check: ").append(string(requirement, "label"));
            }
            body = text.toString();
        }
        String html = body;
        SwingUtilities.invokeLater(() -> {
            if (selected == entry && requirementLabel != null) requirementLabel.setText(html);
        });
    }

    private int requirementState(JsonObject requirement)
    {
        if (requirement.has("anyOf") && requirement.get("anyOf").isJsonArray())
        {
            boolean unknown = false;
            for (JsonElement child : requirement.getAsJsonArray("anyOf"))
            {
                if (!child.isJsonObject()) continue;
                int state = requirementState(child.getAsJsonObject());
                if (state > 0) return 1;
                unknown |= state == 0;
            }
            return unknown ? 0 : -1;
        }
        String kind = string(requirement, "kind"), key = string(requirement, "key");
        if (requirement.has("ironmanOnly") && requirement.get("ironmanOnly").getAsBoolean()) return 0;
        if ("skill".equals(kind))
        {
            try
            {
                Skill skill = Skill.valueOf("runecrafting".equals(key) ? "RUNECRAFT" : key.toUpperCase(Locale.ROOT));
                int needed = requirement.has("value") ? requirement.get("value").getAsInt() : 1;
                return client.getRealSkillLevel(skill) >= needed ? 1 : -1;
            }
            catch (IllegalArgumentException ignored) { return 0; }
        }
        if ("quest".equals(kind))
        {
            for (Quest quest : Quest.values())
            {
                if (quest.getName().equals(key))
                {
                    QuestState state = quest.getState(client);
                    boolean started = requirement.has("started") && requirement.get("started").getAsBoolean();
                    return state == QuestState.FINISHED || (started && state == QuestState.IN_PROGRESS) ? 1 : -1;
                }
            }
        }
        if ("metric".equals(kind) && "Combat".equals(key) && client.getLocalPlayer() != null && requirement.has("value"))
            return client.getLocalPlayer().getCombatLevel() >= requirement.get("value").getAsInt() ? 1 : -1;
        return 0;
    }

    private static String requirementHtml(GuideCatalog.Entry entry)
    {
        if (entry.requirements == null || entry.requirements.isEmpty()) return "";
        return html("Unlocks to check: " + String.join(" · ", entry.requirements));
    }

    private void addItems(String title, List<JsonObject> items)
    { addItems(detail, title, items); }

    private void addItems(JPanel destination, String title, List<JsonObject> items)
    {
        if (items == null || items.isEmpty()) return;
        GuideText heading = text(title);
        heading.setFont(GuideText.FONT.deriveFont(Font.BOLD));
        heading.setForeground(GOLD);
        destination.add(heading);
        for (JsonObject item : items)
        {
            JPanel row = new JPanel(new BorderLayout(6, 0))
            {
                @Override public Dimension getMaximumSize()
                { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
            };
            row.setBackground(new Color(29, 30, 33));
            row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(105, 88, 44)),
                BorderFactory.createEmptyBorder(4, 5, 4, 5)));
            String quantity = item.has("quantity") ? item.get("quantity").getAsString() : "1";
            StringBuilder description = new StringBuilder(quantity).append(" × ").append(string(item, "name"));
            if (item.has("notes") && item.get("notes").isJsonArray())
                for (JsonElement note : item.getAsJsonArray("notes"))
                    if (note.isJsonPrimitive()) description.append("\n").append(note.getAsString());
            GuideText label = text(description.toString());
            label.setToolTipText(description.toString());
            row.add(label, BorderLayout.CENTER);
            int itemId = GuideItemResolver.resolve(item);
            if (itemId > 0)
            {
                java.awt.event.MouseAdapter open = new java.awt.event.MouseAdapter()
                {
                    @Override public void mouseClicked(java.awt.event.MouseEvent event) { openItemAction.accept(itemId); }
                };
                row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                row.addMouseListener(open);
                label.addMouseListener(open);
            }
            if (itemManager != null && itemId > 0)
            {
                JLabel icon = new JLabel();
                icon.setVerticalAlignment(JLabel.TOP);
                icon.setPreferredSize(new Dimension(36, 32));
                itemManager.getImage(itemId).addTo(icon);
                icon.addMouseListener(new java.awt.event.MouseAdapter()
                {
                    @Override public void mouseClicked(java.awt.event.MouseEvent event) { openItemAction.accept(itemId); }
                });
                row.add(icon, BorderLayout.WEST);
            }
            row.setMinimumSize(new Dimension(0, 25));
            destination.add(row);
            destination.add(Box.createVerticalStrut(3));
        }
    }

    private static String string(JsonObject object, String field)
    {
        if (object == null || !object.has(field) || !object.get(field).isJsonPrimitive()) return "";
        return object.get(field).getAsString();
    }

    static String escape(String value)
    {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String html(String value) { return value == null ? "" : value; }
    private static GuideText text(String value) { return new GuideText(value); }
    private static JPanel vertical()
    {
        JPanel result = new JPanel()
        {
            @Override protected void addImpl(Component component, Object constraints, int index)
            {
                if (component instanceof JComponent)
                    ((JComponent) component).setAlignmentX(JComponent.LEFT_ALIGNMENT);
                super.addImpl(component, constraints, index);
            }
        };
        result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));
        result.setBackground(BACKGROUND);
        result.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        result.setMinimumSize(new Dimension(0, 0));
        return result;
    }
    private static JButton button(String title, Runnable action)
    {
        JButton button = new JButton(title);
        button.setFont(GuideText.FONT);
        button.setMargin(new java.awt.Insets(4, 3, 4, 3));
        button.setToolTipText(title);
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(43, 43, 45));
        button.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        button.addActionListener(event -> action.run());
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        button.setMinimumSize(new Dimension(0, 30));
        return button;
    }
}
