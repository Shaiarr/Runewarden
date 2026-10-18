/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.LinkBrowser;

/** Phase rehearsal, verified local loot/death outcomes and a recoverable character journal. */
@Singleton
final class EncounterPracticeTool
{
    static final int PAGE_SIZE = 10;
    private static final String KEY = "encounterPracticeV1";
    static final int MAX_SAVED_CHARS = 1_000_000;
    private static final Color GOLD = new Color(234, 201, 124), WHITE = new Color(240, 238, 232), CARD = new Color(31, 30, 27);
    static final class Record
    {
        List<EncounterPracticeSession.Attempt> attempts = new ArrayList<>();
        Set<Integer> practised = new HashSet<>();
        String note = "";
        long unfinishedMillis;
        int unfinishedPhase=-1, unfinishedPrepared;
        String unfinishedFocus="";
        EncounterPreparation preparation = new EncounterPreparation();
    }
    static final class Journal { Map<String, Record> encounters = new HashMap<>(); }
    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager config;
    private final GuideCatalog catalog;
    private final Gson gson;
    private RunewardenDataStore data;
    private volatile boolean started;
    private volatile long epoch;
    private volatile String account;
    private Journal journal = new Journal();
    private final Map<String, EncounterPracticeSession> sessions = new HashMap<>();
    private JPanel panel, body;
    private GuideCatalog.Entry selected;
    private String category;
    private String query = "";
    private int page, section, checkpointTicks;
    private JTextArea note;
    private JLabel clock;
    private Timer repaintClock;
    private BiConsumer<String, Integer> openSection = (id, index) -> {};
    private java.util.function.Consumer<GuideCatalog.Entry> startActivity=entry->{};
    private java.util.function.BiConsumer<GuideCatalog.Entry,String> activityOutcome=(entry,outcome)->{};
    void setActivityActions(java.util.function.Consumer<GuideCatalog.Entry> start,java.util.function.BiConsumer<GuideCatalog.Entry,String> outcome)
    {startActivity=start==null?entry->{}:start;activityOutcome=outcome==null?(entry,result)->{}:outcome;}
    private IntConsumer openItem = id -> {};
    private Runnable openLoadouts=()->{},openCombat=()->{},openRecovery=()->{};
    private java.util.function.Consumer<String> combatTarget=ignored->{};
    void setCombatTargetAction(java.util.function.Consumer<String> action){combatTarget=action==null?ignored->{}:action;}
    void setPreparationActions(Runnable loadouts,Runnable combat,Runnable recovery)
    {openLoadouts=loadouts==null?()->{}:loadouts;openCombat=combat==null?()->{}:combat;openRecovery=recovery==null?()->{}:recovery;}
    EncounterPracticeTool(Client client, ClientThread clientThread, ConfigManager config, GuideCatalog catalog, Gson gson)
    { this.client = client; this.clientThread = clientThread; this.config = config; this.catalog = catalog; this.gson = gson; }
    @Inject EncounterPracticeTool(Client client, ClientThread clientThread, ConfigManager config, GuideCatalog catalog, Gson gson, RunewardenDataStore data)
    { this(client,clientThread,config,catalog,gson);this.data=data; }
    void onServerNpcLoot(ServerNpcLoot event)
    {
        if(!started||account==null||!account.equals(config.getRSProfileKey())||event==null||event.getComposition()==null)return;
        String name=event.getComposition().getName();long ticket=epoch;
        onEdt(()->{if(started&&ticket==epoch)for(Map.Entry<String,EncounterPracticeSession> row:sessions.entrySet())
        {GuideCatalog.Entry entry=catalog.entry(row.getKey());if(row.getValue().active()&&!row.getValue().paused()&&matchesLoot(entry,name))observedFinish(row.getKey(),"Clear");}});
    }
    static boolean matchesLoot(GuideCatalog.Entry entry,String name)
    {return entry!=null&&entry.id.startsWith("encounter:")&&name!=null&&entry.title.equalsIgnoreCase(name.trim());}
    void onActorDeath(ActorDeath event)
    {
        if(!started||account==null||!account.equals(config.getRSProfileKey())||event.getActor()!=client.getLocalPlayer()||client.getLocalPlayer()==null)return;long ticket=epoch;
        onEdt(()->{if(started&&ticket==epoch)for(Map.Entry<String,EncounterPracticeSession> row:sessions.entrySet())if(row.getValue().active()&&!row.getValue().paused())observedFinish(row.getKey(),"Retry");});
    }
    void onGameStateChanged(GameStateChanged event)
    {
        if(!started||event.getGameState()==GameState.LOGGED_IN)return;
        final String owner=account;
        onEdt(()->{for(Map.Entry<String,EncounterPracticeSession> row:sessions.entrySet())
        {row.getValue().pause();Record record=journal.encounters.get(row.getKey());if(record!=null){if(row.getValue().active())checkpointContext(record,row.getValue());else record.unfinishedMillis=0;}}
        if(owner!=null&&data!=null)data.write(RunewardenConfig.GROUP,owner,KEY,1,journal);});
    }
    private void observedFinish(String id,String outcome)
    {
        EncounterPracticeSession session=sessions.get(id);if(session==null||!session.active())return;
        Record record=journal.encounters.get(id);if(record==null)return;
        EncounterPracticeSession.Attempt attempt=session.finish(outcome,record.note);if(attempt!=null)attempt.source="Observed";
        record.attempts=new ArrayList<>(session.attempts());record.unfinishedMillis=0;preparation(record).finish(outcome);save();activityOutcome.accept(catalog.entry(id),"Observed "+outcome);render();
    }
    void setOpenSectionAction(BiConsumer<String, Integer> action) { openSection = action == null ? (id, index) -> {} : action; }
    void setOpenItemAction(IntConsumer action) { openItem = action == null ? id -> {} : action; }
    void start() { started = true; epoch++; onEdt(this::render); }
    void stop() { checkpoint();started = false; clear(); }
    void clear()
    {
        epoch++; account = null;
        onEdt(() -> { journal = new Journal(); sessions.clear(); selected = null; category = null; if (repaintClock != null) repaintClock.stop(); render(); });
    }
    void onGameTick()
    {
        if (!started || client.getGameState() != GameState.LOGGED_IN) return;
        String current = config.getRSProfileKey();
        if(current!=null&&current.equals(account)){if(++checkpointTicks>=50){checkpointTicks=0;checkpoint();}return;}
        if(current==null)return;
        account = current; final long ticket = ++epoch;
        String raw = config.getRSProfileConfiguration(RunewardenConfig.GROUP, KEY);
        final Journal restored = data==null?loadJournal(raw,gson,catalog):data.read(RunewardenConfig.GROUP,current,KEY,1,Journal.class,
            value->value.encounters!=null,Journal::new);
        final Journal clean=loadJournal(gson.toJson(restored),gson,catalog);
        onEdt(() -> { if (started && ticket == epoch) { journal = clean; sessions.clear(); selected = null; render(); } });
    }
    private void checkpoint()
    {
        final String owner=account;if(owner==null||data==null)return;
        onEdt(()->{for(Map.Entry<String,EncounterPracticeSession> row:sessions.entrySet())
        {Record record=journal.encounters.get(row.getKey());if(record!=null&&row.getValue().active())checkpointContext(record,row.getValue());}
        data.write(RunewardenConfig.GROUP,owner,KEY,1,journal);});
    }
    static Journal loadJournal(String raw, Gson gson, GuideCatalog catalog)
    {
        Journal clean = new Journal();
        if (raw == null || raw.length() > MAX_SAVED_CHARS) return clean;
        final Journal loaded;
        try { loaded = gson.fromJson(raw, Journal.class); }
        catch (RuntimeException invalid) { return clean; }
        if (loaded == null || loaded.encounters == null) return clean;
        for (Map.Entry<String, Record> row : loaded.encounters.entrySet())
        {
            if (clean.encounters.size() >= 192) break;
            GuideCatalog.Entry entry = catalog.entry(row.getKey());
            Record saved = row.getValue();
            if (entry == null || saved == null || entry.sections == null
                || !("boss".equals(entry.category) || "waves".equals(entry.category))) continue;
            Record record = new Record();
            record.note = EncounterPracticeSession.boundedNote(saved.note);
            record.preparation = EncounterPreparation.clean(saved.preparation);
            record.unfinishedMillis=Math.max(0,Math.min(7L*86400000,saved.unfinishedMillis));
            record.unfinishedPhase=saved.unfinishedPhase>=0&&saved.unfinishedPhase<entry.sections.size()?saved.unfinishedPhase:-1;
            record.unfinishedPrepared=Math.max(0,Math.min(4,saved.unfinishedPrepared));record.unfinishedFocus=EncounterPracticeSession.boundedFocus(saved.unfinishedFocus);
            if (saved.practised != null)
                for (Integer index : saved.practised)
                    if (index != null && index >= 0 && index < entry.sections.size()) record.practised.add(index);
            if (saved.attempts != null)
            {
                int first = Math.max(0, saved.attempts.size() - 20);
                for (int i = first; i < saved.attempts.size(); i++)
                {
                    EncounterPracticeSession.Attempt attempt = saved.attempts.get(i);
                    if (attempt == null || attempt.durationMillis < 0
                        || !("Clear".equals(attempt.outcome) || "Retry".equals(attempt.outcome) || "Stopped".equals(attempt.outcome))) continue;
                    record.attempts.add(EncounterPracticeSession.copy(attempt));
                }
            }
            clean.encounters.put(entry.id, record);
        }
        return clean;
    }

    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = vertical(); panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        panel.add(label("BOSS ACADEMY")); body = vertical(); panel.add(body);
        repaintClock = new Timer(500, event -> updateClock());
        render(); return panel;
    }
    private void render()
    {
        if (body == null) return;
        body.removeAll(); clock = null; note = null;
        if (repaintClock != null) repaintClock.stop();
        if (selected != null) renderPractice();
        else if (category != null) renderBrowser();
        else
        {
            body.add(button("Boss encounters", () -> browse("encounter")));
            body.add(button("Wave activities", () -> browse("waves")));
            body.add(button("Equipment guides", () -> browse("boss")));
        }
        body.revalidate(); body.repaint();
    }
    private void browse(String next) { category = next; query = ""; page = 0; render(); }
    private void renderBrowser()
    {
        body.add(button("‹ Categories", () -> { category = null; render(); }));
        JTextField search = new JTextField(query); search.setFont(GuideText.FONT); search.setForeground(WHITE); search.setBackground(CARD);
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30)); search.setName("practiceSearch"); body.add(search);
        JPanel results = vertical(); body.add(results);
        Runnable populate = () ->
        {
            results.removeAll(); List<GuideCatalog.Entry> entries = entries(catalog, category, query);
            int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE); page = Math.min(page, pages - 1);
            for (int i = page * PAGE_SIZE; i < Math.min(entries.size(), (page + 1) * PAGE_SIZE); i++)
            {
                GuideCatalog.Entry entry = entries.get(i);
                results.add(button(entry.title, () -> { selected = entry; section = 0; render(); }));
            }
            results.add(label((page + 1) + " / " + pages + " · " + entries.size() + " guides"));
            if (page > 0) results.add(button("Previous page", () -> { page--; render(); }));
            if (page + 1 < pages) results.add(button("Next page", () -> { page++; render(); }));
            results.revalidate(); results.repaint();
        };
        Timer debounce = new Timer(180, event -> { query = search.getText(); page = 0; populate.run(); }); debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener()
        { public void insertUpdate(DocumentEvent e) { debounce.restart(); } public void removeUpdate(DocumentEvent e) { debounce.restart(); } public void changedUpdate(DocumentEvent e) { debounce.restart(); } });
        populate.run();
    }
    static List<GuideCatalog.Entry> entries(GuideCatalog catalog, String category, String query)
    {
        List<GuideCatalog.Entry> result = new ArrayList<>();
        for (GuideCatalog.Entry entry : catalog.search(query, "boss"))
            if (entry.id.startsWith(category + ":")) result.add(entry);
        return result;
    }
    private Record record()
    {
        Record record = journal.encounters.computeIfAbsent(selected.id, ignored -> new Record());
        if (record.attempts == null) record.attempts = new ArrayList<>();
        if (record.practised == null) record.practised = new HashSet<>();
        if (record.preparation == null) record.preparation = new EncounterPreparation();
        return record;
    }
    private EncounterPracticeSession session()
    {
        return sessions.computeIfAbsent(selected.id, ignored -> { EncounterPracticeSession session = new EncounterPracticeSession(() -> System.nanoTime() / 1_000_000); session.restore(record().attempts);session.restorePaused(record().unfinishedMillis,record().unfinishedPhase,record().unfinishedFocus,record().unfinishedPrepared); return session; });
    }
    private void renderPractice()
    {
        body.add(button("‹ Guides", () -> { storeNote(); selected = null; render(); }));
        body.add(label(selected.title));
        if (selected.sections == null || selected.sections.isEmpty()) { body.add(new GuideText("No phase guide available")); return; }
        section = Math.max(0, Math.min(section, selected.sections.size() - 1));
        JComboBox<String> phases = new JComboBox<>();
        for (int i = 0; i < selected.sections.size(); i++) phases.addItem((i + 1) + " · " + selected.sections.get(i).title);
        phases.setSelectedIndex(section); phases.setMaximumRowCount(10); phases.setFont(GuideText.FONT); phases.setBackground(CARD); phases.setForeground(WHITE);
        phases.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        phases.addActionListener(event -> { storeNote(); section = phases.getSelectedIndex(); render(); }); body.add(phases);
        GuideCatalog.Section current = selected.sections.get(section);
        JPanel guide = SidebarUi.section("PHASE GUIDE");
        if (current.steps != null) for (GuideCatalog.Step step : current.steps) if (step.text != null && !step.text.isEmpty()) guide.add(new GuideText(step.text));
        guide.add(button("Use phase guide & markers", () -> openSection.accept(selected.id, section)));
        JPanel navigation = SidebarUi.row();
        if (section > 0) navigation.add(button("Previous", () -> { storeNote(); section--; render(); }));
        if (section + 1 < selected.sections.size()) navigation.add(button("Next", () -> { storeNote(); section++; render(); }));
        guide.add(navigation); body.add(guide);
        Record record = record();
        renderPreparation(record);
        JPanel practice = SidebarUi.section("PRACTICE");
        practice.add(button(record.practised.contains(section) ? "✓ Practised" : "Mark phase practised", () ->
        { if (!record.practised.add(section)) record.practised.remove(section); save(); render(); }));
        clock = label(""); practice.add(clock); updateClock();
        EncounterPracticeSession session = session();
        if (!session.active()) practice.add(button("Start attempt", () -> { for(EncounterPracticeSession other:sessions.values())if(other!=session)other.pause();storeNote();session.start(section,preparation(record()).focus,preparation(record()).readyCount());checkpointContext(record(),session);save();startActivity.accept(selected);render(); }));
        else
        {
            practice.add(button(session.paused() ? "Resume timer" : "Pause timer", () -> { if (session.paused()) session.resume(); else session.pause();checkpointContext(record(),session);save();render(); }));
            practice.add(SidebarUi.row(button("Clear", () -> finish("Clear")), button("Retry", () -> finish("Retry"))));
            practice.add(button("End attempt", () -> finish("Stopped")));
        }
        body.add(practice);
        JPanel notes = SidebarUi.section(null);
        note = new JTextArea(record.note == null ? "" : record.note, 3, 16);
        note.setLineWrap(true); note.setWrapStyleWord(true); note.setFont(GuideText.FONT); note.setBackground(CARD); note.setForeground(WHITE);
        note.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82)); notes.add(note);
        notes.add(button("Save notes", this::storeNote));
        body.add(SidebarUi.disclosure("Practice notes", notes));
        List<EncounterPracticeSession.Attempt> attempts = session.attempts();
        JPanel history = SidebarUi.column();
        for (int i = attempts.size() - 1; i >= Math.max(0, attempts.size() - 5); i--)
        {
            EncounterPracticeSession.Attempt attempt = attempts.get(i);
            history.add(new GuideText(attempt.outcome + " · " + duration(attempt.durationMillis) + " · " + ("Observed".equals(attempt.source)?"Observed":"Manual")
                + (attempt.focus==null||attempt.focus.isEmpty()?"":"\nFocus: " + attempt.focus)
                + (attempt.phase<0?"":"\nPhase " + (attempt.phase+1) + " · Preparation " + attempt.prepared + "/4")
                + (attempt.note == null || attempt.note.isEmpty() ? "" : "\n" + attempt.note)));
        }
        if (!attempts.isEmpty())
        {
            EncounterPracticeSummary summary=EncounterPracticeSummary.of(attempts);
            JPanel review=SidebarUi.section("ATTEMPT REVIEW");
            review.add(new GuideText(summary.clears + "/" + summary.completed + " clears · " + String.format(java.util.Locale.ROOT,"%.0f%%",summary.clearRate*100)
                + (summary.bestClearMillis==null?"":"\nBest clear " + duration(summary.bestClearMillis))
                + (summary.medianClearMillis==null?"":" · Median " + duration(summary.medianClearMillis))));
            if(preparation(record).recoveryPending){review.add(new GuideText("Recovery pending"));review.add(button("Open death recovery",openRecovery));}
            review.add(button("Review complete · prepare next attempt",()->{preparation(record).resetForNext();save();render();}));
            body.add(review);body.add(SidebarUi.disclosure("Recent attempts · " + attempts.size(), history));
        }
        if (selected.source != null && selected.source.startsWith("https://oldschool.runescape.wiki/"))
            body.add(button("Open Wiki source", () -> LinkBrowser.browse(selected.source)));
        addItems(current.items);
        if (repaintClock != null && started) repaintClock.start();
    }
    private static void checkpointContext(Record record,EncounterPracticeSession session)
    {if(record==null||session==null)return;record.unfinishedMillis=Math.max(1,session.elapsed());record.unfinishedPhase=session.phase();record.unfinishedFocus=session.focus();record.unfinishedPrepared=session.prepared();}
    private static EncounterPreparation preparation(Record record)
    {if(record.preparation==null)record.preparation=new EncounterPreparation();return record.preparation;}
    private void renderPreparation(Record record)
    {
        EncounterPreparation prep=preparation(record);JPanel content=SidebarUi.column();
        JTextField focus=new JTextField(prep.focus);focus.setFont(GuideText.FONT);focus.setForeground(WHITE);focus.setBackground(CARD);
        content.add(SidebarUi.field("This attempt's practice goal",focus));
        content.add(button("Save practice goal",()->{prep.focus=EncounterPracticeSession.boundedFocus(focus.getText());save();}));
        String[] checks={"Equipment & required items checked","Prayer & spellbook ready","Supplies & charges checked","Exit & recovery route checked"};
        for(int i=0;i<checks.length;i++)
        {
            final int index=i;javax.swing.JCheckBox check=new javax.swing.JCheckBox(checks[i],prep.ready[i]);check.setOpaque(false);check.setForeground(WHITE);check.setFont(GuideText.FONT);
            check.addActionListener(event->{prep.ready[index]=check.isSelected();save();});content.add(check);
        }
        content.add(button("Check loadout & supplies",openLoadouts));content.add(button("Compare combat setups",()->{combatTarget.accept(selected.title);openCombat.run();}));
        content.add(button("Use this guide's bank requirements",()->openSection.accept(selected.id,section)));
        if(prep.recoveryPending)content.add(button("Recovery checklist",openRecovery));
        body.add(SidebarUi.disclosure("Prepare attempt · " + prep.readyCount() + "/4",content));
    }
    private void addItems(List<JsonObject> items)
    {
        if (items == null) return;
        int shown = 0;
        for (JsonObject item : items)
        {
            if (++shown > 8) break;
            if (!item.has("id") || !item.get("id").isJsonPrimitive()) continue;
            int id; try { id = item.get("id").getAsInt(); } catch (RuntimeException ignored) { continue; }
            if (id <= 0) continue;
            String title = item.has("name") ? item.get("name").getAsString() : "Item " + id;
            body.add(button(title, () -> openItem.accept(id)));
        }
    }
    private void updateClock()
    { if (clock != null && selected != null) { EncounterPracticeSession session = session(); clock.setText((session.active() ? session.paused() ? "Paused · " : "Attempt · " : "Timer · ") + duration(session.elapsed())); } }
    private void finish(String outcome)
    {
        String text = note == null ? "" : note.getText(); session().finish(outcome, text); record().attempts = new ArrayList<>(session().attempts());
        record().note = EncounterPracticeSession.boundedNote(text);record().unfinishedMillis=0;preparation(record()).finish(outcome); save();activityOutcome.accept(selected,outcome); render();
    }
    private void storeNote() { if (selected != null && note != null) { record().note = EncounterPracticeSession.boundedNote(note.getText()); save(); } }
    private void save()
    {
        if (!started || account == null) return;
        for(Map.Entry<String,EncounterPracticeSession> row:sessions.entrySet())if(row.getValue().active())checkpointContext(journal.encounters.get(row.getKey()),row.getValue());
        String serialized = gson.toJson(journal); String owner = account; long ticket = epoch;
        clientThread.invokeLater(() -> { if (started && ticket == epoch && owner.equals(config.getRSProfileKey())) {if(data!=null)data.write(RunewardenConfig.GROUP,owner,KEY,1,gson.fromJson(serialized,Journal.class));else config.setRSProfileConfiguration(RunewardenConfig.GROUP, KEY, serialized);} });
    }
    static String duration(long millis) { long seconds = Math.max(0, millis / 1000); return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60); }
    private static JPanel vertical() { return SidebarUi.column(); }
    private static JLabel label(String text) { JLabel label = new JLabel(text); label.setFont(GuideText.FONT); label.setForeground(GOLD); label.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0)); return label; }
    private JButton button(String text, Runnable action)
    {
        long ticket=epoch;String owner=account;
        return SidebarUi.wrappedButton(text,()->{if(started&&ticket==epoch&&(owner==null||owner.equals(config.getRSProfileKey())))action.run();});
    }
    private static void onEdt(Runnable action) { if (SwingUtilities.isEventDispatchThread()) action.run(); else SwingUtilities.invokeLater(action); }
}
