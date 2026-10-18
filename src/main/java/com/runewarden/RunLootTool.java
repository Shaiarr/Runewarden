/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.LootManager;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;

/** Local, account-isolated run totals from RuneLite's observed loot events. */
@Singleton
final class RunLootTool
{
    private static final Color GOLD = new Color(224, 190, 113);
    private static final Color WHITE = new Color(243, 240, 232);
    private static final Color BACKGROUND = new Color(17, 18, 21);
    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager config;
    private final ItemManager items;
    private final RunewardenDataStore store;
    private final SharedActivitySession activitySession;
    private final LongSupplier clock;
    private static final String CHECKPOINT_KEY = "runLootCheckpointV1";
    private String activityRunId,activityTitle;
    private int checkpointTicks;
    private long manualCost;
    // Injecting RuneLite's independent service ensures its event producer is available even
    // with the native Loot Tracker plugin disabled. Never starts or configures that plugin.
    @SuppressWarnings("unused") private final LootManager lootManager;
    private final Map<Integer, LootItem> totals = new LinkedHashMap<>();
    private static final int ITEM_LIMIT = 2048;
    private long overflowQuantity, overflowValue;
    private int overflowUnpriced;
    private boolean lootDirty = true;
    private long lootRevision;
    private Reading cached = Reading.empty();
    private final Map<String, Long> sources = new LinkedHashMap<>();
    private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private volatile Reading reading = Reading.empty();
    private volatile boolean started;
    private String profile;
    private volatile Long configProfileId;
    private volatile boolean configBound;
    private long epoch;
    private boolean paused;
    private long runStarted;
    private long pausedAt;
    private long pausedMillis;
    private boolean suspended;
    private long suspendedAt;
    private int seenTick = -1;
    private JPanel panel;
    private JPanel rows;
    private JLabel summary;
    private JLabel status;
    private JLabel unpriced;
    private JButton pause;
    private JButton fresh;
    private JButton reset;
    private JButton applyCost;
    private JTextField costInput;
    private GuideText analytics, costStatus;
    private String costProfile;
    private long costEpoch = -1;
    private JPanel lootNavigation;
    private JButton previousLoot, nextLoot;
    private JLabel lootPageLabel;
    private int lootPage;
    private long renderedLoot = -1, renderedEpoch = -1;
    private int renderedLootPage = -1;
    private boolean renderedActive;
    private Timer timer;

    RunLootTool(Client client, ClientThread clientThread, ConfigManager config,
        ItemManager items, LootManager lootManager)
    { this(client, clientThread, config, items, lootManager, null); }

    RunLootTool(Client client, ClientThread clientThread, ConfigManager config,
        ItemManager items, LootManager lootManager, Gson gson)
    { this(client,clientThread,config,items,lootManager,gson,null,null); }

    @Inject RunLootTool(Client client, ClientThread clientThread, ConfigManager config,
        ItemManager items, LootManager lootManager, Gson gson, RunewardenDataStore store, SharedActivitySession activitySession)
    { this(client,clientThread,config,items,lootManager,gson,store,activitySession,System::currentTimeMillis); }

    RunLootTool(Client client, ClientThread clientThread, ConfigManager config,
        ItemManager items, LootManager lootManager, Gson gson, RunewardenDataStore store, SharedActivitySession activitySession,
        LongSupplier clock)
    {
        this.client = client; this.clientThread = clientThread; this.config = config;
        this.items = items; this.lootManager = lootManager; this.store=store;this.activitySession=activitySession;this.clock=clock;
    }

    void start()
    {
        started = true;
        clear();
        SwingUtilities.invokeLater(() -> {
            if (!started) return;
            if (timer != null) timer.stop();
            timer = new Timer(1000, event -> refreshPanel());
            timer.start();
        });
    }

    void stop()
    {
        started = false;
        clear();
        SwingUtilities.invokeLater(() -> { if (timer != null) timer.stop(); timer = null; });
    }

    /** Destructive lifecycle reset for start/stop, or after a different account is observed. */
    void clear()
    {
        checkpoint();
        discard();
    }

    /** The native config has already switched; never save the previous owner here. */
    void onConfigProfileChanged() { discard(); }

    private void discard()
    {
        configBound=false;configProfileId=null;
        profile = null; epoch++; paused = false; clearLoot();
        runStarted = 0; pausedAt = 0; pausedMillis = 0; seenTick = -1;
        suspended = false; suspendedAt = 0; manualCost = 0;
        cached = Reading.empty();
        reading = Reading.empty();
        activityRunId=activityTitle=null;checkpointTicks=0;
        SwingUtilities.invokeLater(this::refreshPanel);
    }

    /** Hide immediately on HOPPING/CONNECTION_LOST/LOGIN_SCREEN. Same-profile runs resume. */
    void suspend()
    {
        if (profile != null && !suspended)
        {
            checkpoint();
            epoch++; suspended = true; suspendedAt = clock.getAsLong();
        }
        reading = Reading.empty();
        SwingUtilities.invokeLater(this::refreshPanel);
    }

    void onGameTick()
    {
        if (!started) return;
        if (!configCurrent()) discard();
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            suspend();
            return;
        }
        String current = config.getRSProfileKey();
        if (current == null || current.isEmpty()) { suspend(); return; }
        if (!current.equals(profile))
        {
            clear(); profile = current; configProfileId=nativeConfigProfileId();configBound=true;runStarted = clock.getAsLong(); restoreCheckpoint();
            if(activitySession!=null)
            {
                SharedActivitySession.Snapshot session=activitySession.snapshot();
                if(paused&&Objects.equals(profile,session.profile)&&Objects.equals(activityRunId,session.runId))activitySession.pause();
                if(Objects.equals(profile,session.profile)&&!session.current())
                {
                    activitySession.begin();if(paused)activitySession.pause();
                    session=activitySession.snapshot();activityRunId=session.runId;activityTitle=session.context==null?null:session.context.title;
                }
            }
            publish();
        }
        if (suspended)
        {
            if (!paused) pausedMillis += Math.max(0, clock.getAsLong() - suspendedAt);
            suspended = false; suspendedAt = 0;
        }
        if (client.getTickCount() != seenTick) { seen.clear(); seenTick = client.getTickCount(); }
        syncActivity();
        if(++checkpointTicks>=50){checkpointTicks=0;checkpoint();}
        publish();
    }

    void onServerNpcLoot(ServerNpcLoot event)
    {
        if (event != null && event.getComposition() != null)
            record(event, "NPC · " + clean(event.getComposition().getName()), event.getItems(), 1);
    }

    void onPlayerLootReceived(PlayerLootReceived event)
    {
        if (event != null && event.getPlayer() != null)
            record(event, "Player · " + clean(event.getPlayer().getName()), event.getItems(), 1);
    }

    void onLootReceived(LootReceived event)
    {
        if (event == null || event.getType() == null) return;
        // Native Loot Tracker mirrors ServerNpcLoot/PlayerLootReceived. Those are already
        // handled independently above. Only consume its chest/minigame/pickpocket output.
        if (event.getType() == LootRecordType.NPC || event.getType() == LootRecordType.PLAYER) return;
        record(event, clean(event.getName()), event.getItems(), Math.max(1, event.getAmount()));
    }

    private boolean accountCurrent(String expected, long expectedEpoch)
    {
        return started && configCurrent() && !suspended && expected != null && expectedEpoch == epoch && expected.equals(profile)
            && expected.equals(config.getRSProfileKey()) && client.getGameState() == GameState.LOGGED_IN
            && client.getLocalPlayer() != null;
    }

    private void record(Object identity, String source, Collection<ItemStack> stacks, int amount)
    {
        if (!accountCurrent(profile, epoch) || stacks == null || stacks.isEmpty()) return;
        // Shared controls can run between ticks. Apply their exact run boundary before
        // assigning a drop, including pause/resume and a newly started activity.
        syncActivity();
        if (paused) { publish(); return; }
        if (client.getTickCount() != seenTick) { seen.clear(); seenTick = client.getTickCount(); }
        if (!seen.add(identity)) return;
        boolean added = false;
        for (ItemStack stack : stacks)
        {
            if (stack == null || stack.getId() < 0 || stack.getQuantity() <= 0) continue;
            int id = items.canonicalize(stack.getId());
            ItemComposition definition = items.getItemComposition(id);
            String name = definition == null ? "Item " + id : clean(definition.getName());
            long price = id == 995 ? 1 : Math.max(0, items.getItemPrice(id));
            LootItem before = totals.get(id);
            if (before == null && totals.size() >= ITEM_LIMIT)
            {
                overflowQuantity = add(overflowQuantity, stack.getQuantity());
                overflowValue = add(overflowValue, multiply(stack.getQuantity(), price));
                if (price == 0 && overflowUnpriced < Integer.MAX_VALUE) overflowUnpriced++;
                added = true; continue;
            }
            long quantity = add(before == null ? 0 : before.quantity, stack.getQuantity());
            BufferedImage image = items.getImage(id);
            totals.put(id, new LootItem(id, name, quantity, price, image));
            added = true;
        }
        if (added)
        {
            if (!sources.containsKey(source) && sources.size() >= 256) source = "Other sources";
            sources.put(source, add(sources.getOrDefault(source, 0L), amount)); lootDirty = true; lootRevision++;
        }
        publish();
    }

    private void publish()
    {
        if (lootDirty)
        {
            List<LootItem> values = new ArrayList<>(totals.values());
            if (overflowQuantity > 0) values.add(new LootItem(overflowQuantity, overflowValue, overflowUnpriced));
            values.sort(Comparator.comparingLong(LootItem::value).reversed().thenComparing(item -> item.name));
            Map<String, Long> sourceValues = new LinkedHashMap<>(sources);
            cached = new Reading(profile != null && !suspended, profile, epoch, paused, elapsed(clock.getAsLong()),
                values, sourceValues, manualCost);
            cached.lootRevision = lootRevision;
            lootDirty = false;
        }
        else cached = new Reading(cached, profile != null && !suspended, profile, epoch, paused,
            elapsed(clock.getAsLong()), manualCost);
        reading = cached;
        if(activitySession!=null&&activityRunId!=null)activitySession.reportLoot(activityRunId,cached.value,manualCost,cached.items.size(),cached.unpricedItems,cached.sources);
    }

    private long elapsed(long now)
    {
        long until = paused ? pausedAt : suspended ? suspendedAt : now;
        return runStarted == 0 ? 0 : Math.max(0, until - runStarted - pausedMillis);
    }

    Reading reading()
    {
        Reading current = reading;
        return !configCurrent() || current.active && !current.profile.equals(config.getRSProfileKey()) ? Reading.empty() : current;
    }

    /** Client-thread operations, guarded by the exact session captured by the visible button. */
    void setPaused(String expected, long expectedEpoch, boolean value)
    {
        if (!accountCurrent(expected, expectedEpoch) || paused == value) return;
        updatePaused(value); publish();
        if(activitySession!=null){if(value)activitySession.pause();else if(activitySession.snapshot().state==SharedActivitySession.State.FINISHED||!activitySession.snapshot().current())activitySession.begin();else activitySession.resume();}
        checkpoint();
    }

    void newRun(String expected, long expectedEpoch)
    {
        if (!accountCurrent(expected, expectedEpoch)) return;
        if(activitySession!=null)
        {activitySession.resetCurrent();activityRunId=activitySession.snapshot().runId;activityTitle=activitySession.snapshot().context==null?null:activitySession.snapshot().context.title;}
        resetRun(false);checkpoint();
    }

    /** Stop earning while preserving the current report. */
    void finishRun(String expected, long expectedEpoch)
    {
        if (!accountCurrent(expected, expectedEpoch)) return;
        if(!paused){paused=true;pausedAt=clock.getAsLong();}
        if(activitySession!=null)activitySession.finish();publish();checkpoint();
    }

    void setManualCost(String expected, long expectedEpoch, long value)
    {
        if (!accountCurrent(expected, expectedEpoch) || value < 0) return;
        manualCost = value; publish();checkpoint();
    }

    private void resetRun(boolean pauseAfter)
    {
        epoch++; clearLoot(); paused = false;
        manualCost = 0; runStarted = clock.getAsLong(); pausedAt = 0; pausedMillis = 0;
        if (pauseAfter) { paused = true; pausedAt = runStarted; }
        publish();
    }

    private void syncActivity()
    {
        if(activitySession==null)return;
        SharedActivitySession.Snapshot activity=activitySession.snapshot();
        if(activity.profile==null||!activity.profile.equals(profile)||activity.runId==null)return;
        if(!activity.runId.equals(activityRunId))
        {
            resetRun(!activity.running());activityRunId=activity.runId;
            activityTitle=activity.context==null?null:activity.context.title;checkpoint();
        }
        boolean shouldPause=!activity.running();
        if(paused!=shouldPause) updatePaused(shouldPause);
    }

    private void updatePaused(boolean value)
    {
        long now = clock.getAsLong();
        if (value) pausedAt = now;
        else { pausedMillis += Math.max(0, now - pausedAt); pausedAt = 0; }
        paused = value;
    }

    private void clearLoot()
    {
        totals.clear(); sources.clear(); seen.clear();
        overflowQuantity = overflowValue = 0; overflowUnpriced = 0;
        lootDirty = true; lootRevision++;
    }
    private static final class CheckpointItem { int id;String name;long quantity,price; }
    private static final class Checkpoint
    {
        int version=1,unpriced;long elapsed,cost,overflowQuantity,overflowValue;
        String activityRunId,activityTitle;
        List<CheckpointItem> items=new ArrayList<>();Map<String,Long> sources=new LinkedHashMap<>();
        boolean valid()
        {
            if(version!=1||elapsed<0||elapsed>31_536_000_000L||cost<0||overflowQuantity<0||overflowValue<0||unpriced<0
                ||items==null||items.size()>ITEM_LIMIT||sources==null||sources.size()>257
                ||activityRunId!=null&&activityRunId.length()>100||activityTitle!=null&&activityTitle.length()>180)return false;
            java.util.HashSet<Integer> ids=new java.util.HashSet<>();
            for(CheckpointItem item:items)if(item==null||item.id<=0||!ids.add(item.id)||item.quantity<=0||item.price<0||item.name==null||item.name.length()>250)return false;
            for(Map.Entry<String,Long> source:sources.entrySet())if(source.getKey()==null||source.getKey().length()>250||source.getValue()==null||source.getValue()<0)return false;
            return true;
        }
    }
    private void checkpoint()
    {
        if(!configCurrent()||store==null||profile==null||runStarted==0)return;
        Checkpoint saved=new Checkpoint();saved.elapsed=elapsed(clock.getAsLong());saved.cost=manualCost;
        saved.activityRunId=activityRunId;saved.activityTitle=activityTitle;saved.overflowQuantity=overflowQuantity;saved.overflowValue=overflowValue;saved.unpriced=overflowUnpriced;
        for(LootItem item:totals.values())
        {CheckpointItem row=new CheckpointItem();row.id=item.id;row.name=item.name.length()>250?item.name.substring(0,250):item.name;row.quantity=item.quantity;row.price=item.unitPrice;saved.items.add(row);}
        for(Map.Entry<String,Long> source:sources.entrySet())
        {String name=source.getKey().length()>250?source.getKey().substring(0,250):source.getKey();saved.sources.merge(name,source.getValue(),RunLootTool::add);}
        store.write(RunewardenConfig.GROUP,profile,CHECKPOINT_KEY,1,saved);
    }
    private Long nativeConfigProfileId(){ConfigProfile selected=config.getProfile();return selected==null?null:selected.getId();}
    private boolean configCurrent(){return !configBound||Objects.equals(configProfileId,nativeConfigProfileId());}
    private void restoreCheckpoint()
    {
        if(store==null||profile==null)return;
        Checkpoint saved=store.read(RunewardenConfig.GROUP,profile,CHECKPOINT_KEY,1,Checkpoint.class,Checkpoint::valid,()->null);
        if(saved==null)return;
        for(CheckpointItem row:saved.items)totals.put(row.id,new LootItem(row.id,row.name,row.quantity,row.price,items.getImage(row.id)));
        sources.putAll(saved.sources);manualCost=saved.cost;overflowQuantity=saved.overflowQuantity;overflowValue=saved.overflowValue;overflowUnpriced=saved.unpriced;
        activityRunId=saved.activityRunId;activityTitle=saved.activityTitle;
        long now=clock.getAsLong();runStarted=now-saved.elapsed;paused=true;pausedAt=now;pausedMillis=0;lootDirty=true;lootRevision++;
    }

    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column();
        panel.addHierarchyListener(event ->
        {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && panel.isShowing()) refreshPanel(true);
        });
        JPanel header = SidebarUi.section("Loot this session");
        summary = label("0 GP", GOLD); summary.setFont(summary.getFont().deriveFont(Font.BOLD, 20f));
        summary.setToolTipText("Estimated gross loot using RuneLite item prices.");
        status = label("Log in to start a run.", WHITE); unpriced = label("", WHITE);
        analytics = wrap("", WHITE); header.add(summary); header.add(status); header.add(analytics); header.add(unpriced);
        panel.add(header);
        fresh = uiButton("Reset"); pause = uiButton("Pause"); reset = uiButton("Clear loot");
        JButton costsToggle = uiButton("Costs ▸");
        fresh.setToolTipText("Reset this session and start tracking.");
        fresh.addActionListener(event -> action(false, false)); pause.addActionListener(event -> action(true, false));
        reset.addActionListener(event -> action(false, true));
        panel.add(SidebarUi.row(pause, fresh)); panel.add(costsToggle);
        JPanel costs = SidebarUi.section("Supplies and fees");
        costInput = new JTextField("0"); costInput.setBackground(new Color(35, 34, 30)); costInput.setForeground(WHITE); costInput.setFont(GuideText.FONT);
        costInput.setToolTipText("Total estimated food, potion, ammunition, rune, charge and entry-fee spending.");
        applyCost = uiButton("Apply"); costs.add(SidebarUi.field("Total GP spent", costInput)); costs.add(SidebarUi.row(applyCost, reset));
        costStatus = wrap("", WHITE); costs.add(costStatus); costs.setVisible(false); panel.add(costs);
        costsToggle.addActionListener(event -> { costs.setVisible(!costs.isVisible()); costsToggle.setText(costs.isVisible() ? "Costs ▾" : "Costs ▸"); panel.revalidate(); });
        applyCost.addActionListener(event ->
        {
            Reading captured = reading(); if (!captured.active) return;
            try
            {
                long entered = Long.parseLong(costInput.getText().trim().replace(",", ""));
                if (entered < 0) throw new NumberFormatException();
                clientThread.invoke(() -> { setManualCost(captured.profile, captured.epoch, entered); SwingUtilities.invokeLater(this::refreshPanel); });
                costStatus.setText("");
            }
            catch (NumberFormatException invalid) { costStatus.setText("Enter a non-negative GP total."); }
        });
        JPanel drops = SidebarUi.section("Drops"); panel.add(drops);
        lootNavigation = SidebarUi.row();
        previousLoot = uiButton("‹"); nextLoot = uiButton("›"); lootPageLabel = label("", GOLD);
        previousLoot.addActionListener(event -> { lootPage--; refreshPanel(true); }); nextLoot.addActionListener(event -> { lootPage++; refreshPanel(true); });
        lootNavigation.add(previousLoot); lootNavigation.add(lootPageLabel); lootNavigation.add(nextLoot); drops.add(lootNavigation);
        rows = SidebarUi.column(); drops.add(rows);
        SidebarUi.install(panel); refreshPanel(true); return panel;
    }

    private static JButton uiButton(String title)
    {
        JButton button = SidebarUi.button(title, () -> {});
        button.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 30)); return button;
    }

    private void action(boolean pauseAction, boolean clearOnly)
    {
        Reading captured = reading();
        if (!captured.active) return;
        clientThread.invoke(() -> {
            if (!accountCurrent(captured.profile, captured.epoch)) return;
            if (pauseAction) setPaused(captured.profile, captured.epoch, !captured.paused);
            else if (clearOnly)
            {
                clearLoot(); epoch++; publish();checkpoint();
            }
            else newRun(captured.profile, captured.epoch);
            SwingUtilities.invokeLater(this::refreshPanel);
        });
    }

    private void refreshPanel()
    { refreshPanel(false); }

    private void refreshPanel(boolean force)
    {
        if (panel == null || (!force && !panel.isShowing())) return;
        Reading current = reading();
        summary.setText(String.format(java.util.Locale.ROOT, "%,d GP", current.value));
        unpriced.setText(current.unpricedItems + (current.unpricedItems == 1 ? " unpriced item" : " unpriced items"));
        unpriced.setVisible(current.unpricedItems > 0);
        long seconds = current.elapsedMillis / 1000;
        status.setText(!current.active ? "Log in to start a run." :
            (current.paused ? "Paused" : "Tracking") + String.format(java.util.Locale.ROOT, " · %d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60));
        summary.setToolTipText("Estimated gross loot using RuneLite item prices.");
        String grossRate = current.elapsedMillis >= 60_000 ? String.format(java.util.Locale.ROOT, "%,d", current.gpPerHour) : "—";
        String netRate = current.elapsedMillis >= 60_000 ? String.format(java.util.Locale.ROOT, "%,d", current.netPerHour) : "—";
        analytics.setText(String.format(java.util.Locale.ROOT, "Gross/hour %s GP\nCosts %,d GP\nNet %,d GP\nNet/hour %s GP",
            grossRate, current.manualCost, current.netValue, netRate));
        analytics.setToolTipText("Hourly rates appear after one active minute. Net subtracts your entered expense total.");
        fresh.setEnabled(current.active); pause.setEnabled(current.active); reset.setEnabled(current.active);
        applyCost.setEnabled(current.active); costInput.setEnabled(current.active);
        if (!java.util.Objects.equals(costProfile, current.profile) || costEpoch != current.epoch)
        { costProfile = current.profile; costEpoch = current.epoch; costInput.setText(Long.toString(current.manualCost)); lootPage = 0; costStatus.setText(""); }
        pause.setText(current.paused ? "Resume" : "Pause");
        int pages = Math.max(1, Math.max((current.items.size() + 11) / 12, (current.sources.size() + 7) / 8));
        lootPage = Math.max(0, Math.min(lootPage, pages - 1));
        boolean sessionChanged = renderedEpoch != current.epoch || renderedActive != current.active;
        boolean rebuildLoot = force || sessionChanged || renderedLoot != current.lootRevision || renderedLootPage != lootPage;
        if (rebuildLoot)
        {
            rows.removeAll();
            List<Map.Entry<String, Long>> sourceRows = new ArrayList<>(current.sources.entrySet());
            for (int i = lootPage * 8; i < Math.min(sourceRows.size(), lootPage * 8 + 8); i++)
            {
                Map.Entry<String, Long> source = sourceRows.get(i);
                rows.add(wrap(source.getKey() + " × " + source.getValue(), GOLD));
            }
            for (int i = lootPage * 12; i < Math.min(current.items.size(), lootPage * 12 + 12); i++)
            {
                LootItem item = current.items.get(i);
                JPanel row = new JPanel(new BorderLayout(7, 0)); row.setOpaque(false);
                row.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
                if (item.image != null) row.add(new JLabel(new ImageIcon(item.image)), BorderLayout.WEST);
                GuideText text = wrap(String.format(java.util.Locale.ROOT, "%s × %,d\n%s", item.name, item.quantity,
                    item.unitPrice > 0 || item.aggregate ? String.format(java.util.Locale.ROOT, "%,d gp", item.value()) : "Unpriced"), WHITE);
                row.add(text, BorderLayout.CENTER); rows.add(row);
            }
            if (current.items.isEmpty()) rows.add(wrap(current.active ? "No loot yet" : "", WHITE));
            lootNavigation.setVisible(pages > 1); lootPageLabel.setText((lootPage + 1) + " / " + pages);
            previousLoot.setEnabled(lootPage > 0); nextLoot.setEnabled(lootPage + 1 < pages);
            renderedLoot = current.lootRevision; renderedLootPage = lootPage;
            rows.revalidate(); rows.repaint();
        }
        renderedEpoch = current.epoch; renderedActive = current.active;
    }

    private static JLabel label(String text, Color color) { JLabel label = new JLabel(text); label.setForeground(color); label.setFont(GuideText.FONT); return label; }
    private static GuideText wrap(String text, Color color)
    {
        GuideText result = SidebarUi.label(text); result.setForeground(color); return result;
    }
    private static String clean(String text) { return text == null || text.isEmpty() ? "Unknown source" : Text.removeTags(text); }
    static long add(long first, long second) { return Long.MAX_VALUE - first < second ? Long.MAX_VALUE : first + second; }
    static long multiply(long quantity, long price) { return price > 0 && quantity > Long.MAX_VALUE / price ? Long.MAX_VALUE : quantity * price; }
    static long perHour(long value, long elapsedMillis)
    { return elapsedMillis <= 0 ? 0 : Math.round(value * 3_600_000.0 / elapsedMillis); }
    static final class LootItem
    {
        final int id; final String name; final long quantity; final long unitPrice; final BufferedImage image;
        final boolean aggregate;
        final long aggregateValue;
        final int aggregateUnpriced;
        LootItem(int id, String name, long quantity, long unitPrice, BufferedImage image)
        {
            this.id = id; this.name = name; this.quantity = quantity; this.unitPrice = unitPrice; this.image = image;
            aggregate = false; aggregateValue = 0; aggregateUnpriced = 0;
        }
        LootItem(long quantity, long value, int unpriced)
        {
            id = -1; name = "Additional loot (item limit)"; this.quantity = quantity; unitPrice = 0; image = null;
            aggregate = true; aggregateValue = value; aggregateUnpriced = unpriced;
        }
        long value() { return aggregate ? aggregateValue : multiply(quantity, unitPrice); }
    }

    static final class Reading
    {
        final boolean active; final String profile; final long epoch; final boolean paused; final long elapsedMillis;
        final List<LootItem> items; final Map<String, Long> sources; final long value; final int unpricedItems;
        final long manualCost, netValue, gpPerHour, netPerHour;
        long lootRevision;
        Reading(boolean active, String profile, long epoch, boolean paused, long elapsedMillis,
            List<LootItem> items, Map<String, Long> sources)
        { this(active, profile, epoch, paused, elapsedMillis, items, sources, 0); }
        Reading(boolean active, String profile, long epoch, boolean paused, long elapsedMillis,
            List<LootItem> items, Map<String, Long> sources, long manualCost)
        {
            this.active = active; this.profile = profile; this.epoch = epoch; this.paused = paused; this.elapsedMillis = elapsedMillis;
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
            long total = 0; int unpriced = 0;
            for (LootItem item : items)
            {
                total = add(total, item.value());
                if (item.aggregate) unpriced = (int) Math.min(Integer.MAX_VALUE, (long) unpriced + item.aggregateUnpriced);
                else if (item.unitPrice == 0) unpriced++;
            }
            this.value = total; this.unpricedItems = unpriced;
            this.manualCost = Math.max(0, manualCost); this.netValue = total - this.manualCost;
            this.gpPerHour = perHour(total, elapsedMillis); this.netPerHour = perHour(netValue, elapsedMillis);
        }
        /** Reuses immutable data on heartbeat ticks; no sort, source copy or item scan. */
        private Reading(Reading data, boolean active, String profile, long epoch, boolean paused, long elapsedMillis, long manualCost)
        {
            this.active = active; this.profile = profile; this.epoch = epoch; this.paused = paused; this.elapsedMillis = elapsedMillis;
            items = data.items; sources = data.sources; value = data.value; unpricedItems = data.unpricedItems;
            this.manualCost = Math.max(0, manualCost); netValue = value - this.manualCost;
            gpPerHour = perHour(value, elapsedMillis); netPerHour = perHour(netValue, elapsedMillis);
            lootRevision = data.lootRevision;
        }
        static Reading empty() { return new Reading(false, null, 0, false, 0, Collections.emptyList(), Collections.emptyMap()); }
    }
}
