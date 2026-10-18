/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Dimension;
import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.LinkBrowser;

/** A paged item browser; each item connects observed native details, training facts, sources and guides. */
@Singleton
public final class ItemKnowledgeTool
{
    private static final Color BACKGROUND = new Color(18, 19, 23), CARD = new Color(31, 32, 36);
    private static final Color TEXT = new Color(242, 240, 233), GOLD = new Color(232, 194, 113);
    private static final int PAGE_SIZE = 20, SOURCE_PAGE_SIZE = 5, ACTION_PAGE_SIZE = 4;
    private final Client client;
    private final ClientThread clientThread;
    private final ItemManager items;
    private final ItemKnowledgeCatalog catalog;
    private final SkillingResourceCatalog resources;
    private final ItemSourceService sourceService;
    private final ScheduledExecutorService executor;
    private final RunewardenConfig config;
    private final ConfigManager configManager;
    private volatile boolean running;
    private volatile boolean databaseOpened, databaseVisible;
    private volatile long epoch;
    private volatile int requestedNative = -1;
    private volatile Map<Skill, Integer> levels = Collections.emptyMap();
    private volatile NativeFacts nativeFacts;
    private int nativeCursor = 1;
    private JPanel panel, content;
    private JTextField search;
    private Timer searchTimer;
    private List<ItemKnowledgeCatalog.ItemRef> results = Collections.emptyList();
    private int resultPage, sourcePage, actionPage, guidePage, requirementPage, selectedId = -1;
    private boolean showMethodDetails, showProvenance;
    private String sourceKind = "All";
    private ItemSourceService.Lookup lookup;
    private String lookupStatus = "";
    private Consumer<String> openGuide = ignored -> { };
    private volatile long searchEpoch;
    private final AtomicLong sourceEpoch = new AtomicLong();

    private static final class NativeFacts
    {
        final int id, alch;
        final long ge;
        final String name, actions;
        final boolean members, stackable, tradeable;
        NativeFacts(int id, ItemComposition definition, long ge)
        {
            this.id = id; this.name = definition.getMembersName(); this.ge = ge;
            this.alch = definition.getHaPrice(); this.members = definition.isMembers();
            this.stackable = definition.isStackable(); this.tradeable = definition.isTradeable();
            List<String> operations = new ArrayList<>();
            if (definition.getInventoryActions() != null) for (String action : definition.getInventoryActions())
                if (action != null && !action.equalsIgnoreCase("Drop")) operations.add(action);
            actions = String.join(" / ", operations);
        }
    }

    ItemKnowledgeTool(Client client, ClientThread clientThread, ItemManager items,
        ItemKnowledgeCatalog catalog, SkillingResourceCatalog resources, ItemSourceService sourceService,
        ScheduledExecutorService executor)
    { this(client, clientThread, items, catalog, resources, sourceService, executor, new RunewardenConfig() { }, null); }

    @Inject ItemKnowledgeTool(Client client, ClientThread clientThread, ItemManager items,
        ItemKnowledgeCatalog catalog, SkillingResourceCatalog resources, ItemSourceService sourceService,
        ScheduledExecutorService executor, RunewardenConfig config, ConfigManager configManager)
    {
        this.client = client; this.clientThread = clientThread; this.items = items; this.catalog = catalog;
        this.resources = resources; this.sourceService = sourceService; this.executor = executor;
        this.config = config; this.configManager = configManager;
    }

    public void setOpenGuideAction(Consumer<String> action) { openGuide = action == null ? ignored -> { } : action; }

    /** Called for native configuration changes, including revocation while logged out or hidden. */
    public void onConfigChanged(String key)
    {
        if (!"wikiItemSources".equals(key)) return;
        sourceService.onConsentChanged();
        long expected = epoch, request = sourceEpoch.incrementAndGet();
        SwingUtilities.invokeLater(() -> {
            if (!running || epoch != expected || sourceEpoch.get() != request || selectedId <= 0) return;
            select(selectedId);
        });
    }

    public void start()
    {
        running = true; long expected = ++epoch;
        executor.execute(() -> {
            catalog.load();
            SwingUtilities.invokeLater(() -> {
                if (!running || epoch != expected) return;
                if (selectedId > 0 && databaseOpened) select(selectedId); else if (databaseVisible) searchItems();
            });
        });
    }

    public void stop()
    {
        running = false; ++epoch; ++searchEpoch; sourceEpoch.incrementAndGet(); sourceService.stop();
        databaseVisible = databaseOpened = false;
        requestedNative = -1; nativeFacts = null; levels = Collections.emptyMap();
        if (searchTimer != null) SwingUtilities.invokeLater(() -> searchTimer.stop());
    }

    /** Item knowledge is account-independent; only player skill levels and native observations are cleared. */
    public void clearProfile()
    {
        levels = Collections.emptyMap(); nativeFacts = null;
        if (databaseVisible) SwingUtilities.invokeLater(() -> { if (databaseVisible && selectedId > 0) renderDetail(); });
    }

    public void onGameTick()
    {
        if (!running || client.getGameState() != GameState.LOGGED_IN) return;
        EnumMap<Skill, Integer> observed = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) if (skill != Skill.OVERALL) observed.put(skill, client.getRealSkillLevel(skill));
        boolean levelChanged = !observed.equals(levels);
        levels = Collections.unmodifiableMap(observed);
        if (levelChanged && databaseVisible)
            SwingUtilities.invokeLater(() -> { if (running && databaseVisible && selectedId > 0) renderDetail(); });
        // Only visible database use opts into native indexing; never warm all definitions on login.
        if (databaseOpened && databaseVisible && catalog.loaded())
        {
            int count = client.getItemCount(), end = Math.min(count, nativeCursor + 96);
            for (; nativeCursor < end; nativeCursor++)
            {
                ItemComposition definition = items.getItemComposition(nativeCursor);
                if (definition != null && definition.getNote() == -1 && definition.getPlaceholderTemplateId() == -1)
                    catalog.addNative(nativeCursor, definition.getMembersName());
            }
        }
        int wanted = requestedNative;
        if (wanted > 0) readNative(wanted);
    }

    public JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = vertical(BACKGROUND); panel.setName("runewarden.items.root");
        panel.add(text("Item database", true));
        panel.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0)
                onVisibilityChanged(panel.isShowing());
        });
        search = new JTextField(); style(search); search.setToolTipText("Item name or exact item ID");
        search.setName("runewarden.items.search");
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30)); panel.add(SidebarUi.field("Search items", search));
        searchTimer = new Timer(180, e -> { selectedId = -1; searchItems(); }); searchTimer.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { searchTimer.restart(); }
            public void removeUpdate(DocumentEvent e) { searchTimer.restart(); }
            public void changedUpdate(DocumentEvent e) { searchTimer.restart(); }
        });
        search.addActionListener(e -> { searchTimer.stop(); selectedId = -1; searchItems(); });
        content = vertical(BACKGROUND); panel.add(content);
        if (!catalog.loaded()) content.add(text("Loading item index…", false));
        else if (selectedId > 0) renderDetail(); else searchItems();
        return panel;
    }

    public void openItem(int itemId)
    {
        if (itemId <= 0) return;
        SwingUtilities.invokeLater(() -> { databaseOpened = true; selectedId = itemId; if (catalog.loaded()) select(itemId); });
    }

    /** Called by Swing hierarchy changes; kept separate to verify lifecycle without a native window. */
    void onVisibilityChanged(boolean visible)
    {
        databaseVisible = visible;
        if (!visible) return;
        databaseOpened = true;
        if (!catalog.loaded()) return;
        if (selectedId > 0)
        {
            if (nativeFacts == null) requestedNative = selectedId;
            renderDetail();
        }
        else if (results.isEmpty()) searchItems();
        else renderResults();
    }

    private void searchItems()
    {
        if (!databaseVisible || !catalog.loaded()) return;
        String query = search == null ? "" : search.getText();
        long expected = ++searchEpoch, lifecycle = epoch;
        executor.execute(() -> {
            List<ItemKnowledgeCatalog.ItemRef> found = catalog.search(query);
            SwingUtilities.invokeLater(() -> {
                if (!running || expected != searchEpoch || lifecycle != epoch || selectedId > 0) return;
                results = found; resultPage = 0; renderResults();
            });
        });
    }

    private void renderResults()
    {
        if (content == null || !databaseVisible) return;
        content.removeAll();
        content.add(text(number(results.size()) + " matching items", false));
        content.add(muted(number(catalog.sourceItemCount()) + " items with offline source / requirement cards"));
        int end = Math.min(results.size(), (resultPage + 1) * PAGE_SIZE);
        for (int i = resultPage * PAGE_SIZE; i < end; i++)
        {
            ItemKnowledgeCatalog.ItemRef item = results.get(i);
            JButton row = button(item.name, () -> select(item.id)); row.setHorizontalAlignment(JButton.LEFT);
            items.getImage(item.id).addTo(row); content.add(row);
        }
        if (results.isEmpty()) content.add(text("No matching items.", false));
        content.add(pager(resultPage, results.size(), PAGE_SIZE,
            () -> { --resultPage; renderResults(); }, () -> { ++resultPage; renderResults(); }));
        refresh();
    }

    private void select(int itemId)
    {
        ++searchEpoch; sourceEpoch.incrementAndGet(); selectedId = itemId; sourcePage = actionPage = guidePage = requirementPage = 0; sourceKind = "All";
        nativeFacts = null; requestedNative = itemId;
        lookup = catalog.bundled(itemId);
        lookupStatus = lookup == null ? (config.wikiItemSources() ? "Fetching item sources…" : "No bundled source tables for this item.")
            : "Bundled Wiki sources · " + lookup.retrieved;
        renderDetail();
        clientThread.invokeLater(() -> { if (running && requestedNative == itemId && client.getGameState() == GameState.LOGGED_IN) readNative(itemId); });
        ItemKnowledgeCatalog.ItemRef item = catalog.item(itemId);
        long lifecycle=epoch, selection=sourceEpoch.get();
        executor.execute(() -> {
            ItemSourceService.Lookup offline=catalog.loadBundled(itemId);
            SwingUtilities.invokeLater(() -> {
                if(!running||epoch!=lifecycle||sourceEpoch.get()!=selection||selectedId!=itemId)return;
                lookup=offline;lookupStatus=offline==null?"No bundled sources for this item.":"Bundled Wiki sources · "+offline.retrieved;
                renderDetail();
                if(item!=null&&config.wikiItemSources())loadSources(item.name,itemId);
            });
        });
    }

    private void readNative(int itemId)
    {
        ItemComposition definition = items.getItemComposition(itemId);
        if (definition == null) return;
        int canonical = items.canonicalize(itemId);
        ItemComposition original = items.getItemComposition(canonical);
        if (original == null) return;
        NativeFacts next = new NativeFacts(canonical, original, items.getItemPrice(canonical));
        catalog.addNative(canonical, next.name); nativeFacts = next; requestedNative = -1;
        long expected = epoch;
        SwingUtilities.invokeLater(() -> {
            if (!running || epoch != expected || selectedId != itemId) return;
            if (canonical != itemId) { select(canonical); return; }
            renderDetail();
        });
    }

    private void loadSources(String name, int id)
    {
        if (!running || !config.wikiItemSources()) return;
        long expected = epoch, request = sourceEpoch.get();
        sourceService.request(name, response -> SwingUtilities.invokeLater(() -> {
            if (!running || epoch != expected || sourceEpoch.get() != request || selectedId != id || !config.wikiItemSources()) return;
            if (response.error == null)
            {
                ItemSourceService.Lookup pinned = catalog.bundled(id);
                ItemSourceService.Lookup displayed = new ItemSourceService.Lookup(response);
                if (pinned != null && pinned.sources != null) for (ItemSourceService.Source source : pinned.sources)
                    if (source.curated && displayed.sources.stream().noneMatch(live -> live.kind.equals(source.kind) && live.name.equals(source.name)))
                        displayed.sources.add(source);
                lookup = displayed; lookupStatus = "Wiki sources · revision " + response.revision; sourcePage = 0;
            }
            else lookupStatus = response.error;
            renderDetail();
        }));
    }

    private void renderDetail()
    {
        if (content == null || selectedId <= 0 || !databaseVisible) return;
        content.removeAll();
        content.add(button("‹ Back to items", () -> { selectedId = -1; renderResults(); }));
        ItemKnowledgeCatalog.ItemRef item = catalog.item(selectedId);
        NativeFacts facts = nativeFacts != null && nativeFacts.id == selectedId ? nativeFacts : null;
        String name = facts != null ? facts.name : item != null ? item.name : "Item " + selectedId;

        JPanel identity = section(null, "runewarden.items.identity");
        JPanel heading = fullRow();
        JLabel icon = new JLabel(); items.getImage(selectedId).addTo(icon);
        icon.setPreferredSize(new Dimension(36, 36)); heading.add(icon, BorderLayout.WEST);
        heading.add(text(name, true), BorderLayout.CENTER); identity.add(heading);
        identity.add(text("ID " + selectedId, false));
        if (facts != null)
        {
            identity.add(text((facts.members ? "Members" : "Free-to-play") + " · "
                + (facts.tradeable ? "Tradeable" : "Untradeable") + (facts.stackable ? " · Stackable" : ""), false));
            if (facts.ge > 0) identity.add(fact("GE estimate", number(facts.ge) + " gp"));
            if (facts.alch > 0) identity.add(fact("High alchemy", number(facts.alch) + " gp"));
            if (!facts.actions.isEmpty()) identity.add(fact("Actions", facts.actions));
        }
        identity.add(button("Open Wiki article ↗", () -> LinkBrowser.browse(ItemSourceService.wiki(name))));
        content.add(identity);

        SkillingResourceCatalog.Resource resource = resources.find(selectedId);
        if (resource != null && (resource.use != null || resource.source != null))
        {
            JPanel uses = section("Uses", "runewarden.items.uses");
            if (resource.use != null) uses.add(text(resource.use, false));
            if (resource.source != null) uses.add(fact("Gather / obtain", resource.source));
            content.add(uses);
        }
        renderRequirements();
        if (resource != null && !resource.actions().isEmpty()) renderTraining(resource);
        renderSources();
        renderRelatedGuides();
        refresh();
    }

    private void renderRequirements()
    {
        List<ItemSourceService.Source> requirements = new ArrayList<>();
        if (lookup != null && lookup.sources != null) for (ItemSourceService.Source source : lookup.sources)
            if (isRequirement(source)) requirements.add(source);
        if (requirements.isEmpty()) return;
        JPanel section = section("Requirements", "runewarden.items.requirements");
        int pages = Math.max(1, (requirements.size() + 3) / 4);
        requirementPage = Math.min(requirementPage, pages - 1);
        int end = Math.min(requirements.size(), (requirementPage + 1) * 4);
        for (int i = requirementPage * 4; i < end; i++)
        {
            ItemSourceService.Source source = requirements.get(i);
            section.add(text(source.name, true));
            if (source.kind.startsWith("Creation")) section.add(muted("For creation"));
            addRequirementFields(section, source);
            addGuideLinks(section, source.name);
        }
        if (requirements.size() > 4) section.add(pager(requirementPage, requirements.size(), 4,
            () -> { --requirementPage; renderDetail(); }, () -> { ++requirementPage; renderDetail(); }));
        content.add(section);
    }

    private void addRequirementFields(JPanel target, ItemSourceService.Source source)
    {
        for(String[] field:sourceFields(source.details))
        {
            String value=field[0].isEmpty()?field[1]:field[0]+": "+field[1];
            GuideText line=text(value,false);
            for(Map.Entry<Skill,Integer> observed:levels.entrySet())
            {
                String name=observed.getKey().name().replace('_',' ');
                java.util.regex.Matcher requirement=java.util.regex.Pattern.compile("(?i)\\b"+name+"\\s+([0-9]{1,2})\\b").matcher(value);
                int needed=requirement.find()?Integer.parseInt(requirement.group(1)):-1;
                if(needed<0&&source.name.equalsIgnoreCase(name)&&field[0].equalsIgnoreCase("Level"))
                {java.util.regex.Matcher level=java.util.regex.Pattern.compile("^[0-9]{1,2}").matcher(field[1]);if(level.find())needed=Integer.parseInt(level.group());}
                if(needed>observed.getValue())line.setForeground(new Color(255,153,128));
            }
            target.add(line);
        }
    }

    private void renderTraining(SkillingResourceCatalog.Resource resource)
    {
        JPanel training = section("Training methods", "runewarden.items.training");
        JCheckBox details = check("Method details", showMethodDetails);
        details.addActionListener(event -> { showMethodDetails = details.isSelected(); renderDetail(); });
        training.add(details);
        List<SkillingResourceCatalog.Action> actions = resource.actions();
        actionPage = Math.min(actionPage, Math.max(0, (actions.size() - 1) / ACTION_PAGE_SIZE));
        int end = Math.min(actions.size(), (actionPage + 1) * ACTION_PAGE_SIZE);
        for (int i = actionPage * ACTION_PAGE_SIZE; i < end; i++)
        {
            SkillingResourceCatalog.Action action = actions.get(i);
            training.add(text(action.name, true));
            GuideText required = text(SkillingResourceCatalog.title(action.skill.name()) + " " + action.level, false);
            if (levels.containsKey(action.skill) && levels.get(action.skill) < action.level)
                required.setForeground(new Color(255, 153, 128));
            training.add(required);
            training.add(fact("Experience", SkillingResourceCatalog.number(action.xp) + " XP / action"));
            if (showMethodDetails)
            {
                if (!action.ingredients.isEmpty()) training.add(fact("Ingredients", action.ingredients));
                if (action.note != null && !action.note.isEmpty()) training.add(text(action.note, false));
            }
        }
        if (actions.size() > ACTION_PAGE_SIZE) training.add(pager(actionPage, actions.size(), ACTION_PAGE_SIZE,
            () -> { --actionPage; renderDetail(); }, () -> { ++actionPage; renderDetail(); }));
        if (showMethodDetails && resource.note != null) training.add(text(resource.note, false));
        content.add(training);
    }

    private void renderSources()
    {
        content.add(text("Sources & products", true));
        JPanel network = section("Live Wiki sources", "runewarden.items.network-consent");
        network.add(text(RunewardenConfig.THIRD_PARTY_WARNING, false));
        network.add(muted("Optional requests to oldschool.runescape.wiki send only the selected item title and your IP address. Bundled sources work offline."));
        JCheckBox live = check("Allow live Wiki sources", config.wikiItemSources());
        live.setName("runewarden.items.wiki-opt-in");
        live.setEnabled(configManager != null);
        live.addActionListener(event -> {
            configManager.setConfiguration(RunewardenConfig.GROUP, "wikiItemSources", live.isSelected());
            onConfigChanged("wikiItemSources");
        });
        network.add(live); content.add(network);
        List<ItemSourceService.Source> sources = new ArrayList<>();
        Set<String> kinds = new LinkedHashSet<>(); kinds.add("All");
        if (lookup != null && lookup.sources != null) for (ItemSourceService.Source source : lookup.sources)
        {
            if (isRequirement(source)) continue;
            kinds.add(source.kind);
        }
        if (!kinds.contains(sourceKind)) sourceKind = "All";
        if (lookup != null && lookup.sources != null) for (ItemSourceService.Source source : lookup.sources)
            if (!isRequirement(source) && (sourceKind.equals("All") || sourceKind.equals(source.kind))) sources.add(source);
        JComboBox<String> category = new JComboBox<>(kinds.toArray(new String[0])); style(category);
        category.setName("runewarden.items.source-filter");
        category.setSelectedItem(sourceKind); category.setMaximumRowCount(10);
        category.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        category.addActionListener(event -> { sourceKind = (String) category.getSelectedItem(); sourcePage = 0; renderDetail(); });
        if (kinds.size() > 1) content.add(SidebarUi.field("Source type", category));
        sourcePage = Math.min(sourcePage, Math.max(0, (sources.size() - 1) / SOURCE_PAGE_SIZE));
        int end = Math.min(sources.size(), (sourcePage + 1) * SOURCE_PAGE_SIZE);
        for (int i = sourcePage * SOURCE_PAGE_SIZE; i < end; i++)
        {
            ItemSourceService.Source source = sources.get(i);
            JPanel card = section(source.name, "runewarden.items.source-card");
            card.add(muted(source.kind));
            addSourceFields(card, source.details);
            addGuideLinks(card, source.name);
            if (source.wiki != null && source.wiki.startsWith(ItemSourceService.HOST + "/w/"))
                card.add(button("Open source ↗", () -> LinkBrowser.browse(source.wiki)));
            content.add(card);
        }
        if (lookup != null && sources.isEmpty()) content.add(text("No matching source cards for this item.", false));
        else if (lookup == null && !lookupStatus.isEmpty()) content.add(text(lookupStatus, false));
        if (sources.size() > SOURCE_PAGE_SIZE) content.add(pager(sourcePage, sources.size(), SOURCE_PAGE_SIZE,
            () -> { --sourcePage; renderDetail(); }, () -> { ++sourcePage; renderDetail(); }));
        JCheckBox provenance = check("Source details", showProvenance);
        provenance.addActionListener(event -> { showProvenance = provenance.isSelected(); renderDetail(); });
        content.add(provenance);
        if (showProvenance)
        {
            content.add(muted(lookupStatus));
            if (lookup != null && lookup.wiki != null)
                content.add(button(lookup.revision > 0 ? "Open source revision ↗" : "Open source article ↗", () -> LinkBrowser.browse(lookup.wiki)));
        }
    }

    private void renderRelatedGuides()
    {
        List<GuideCatalog.Entry> related = catalog.related(selectedId);
        if (related.isEmpty()) return;
        JPanel guides = section("Guides using this item", "runewarden.items.guides");
        int end = Math.min(related.size(), (guidePage + 1) * 8);
        for (int i = guidePage * 8; i < end; i++)
        {
            GuideCatalog.Entry guide = related.get(i);
            guides.add(button(("quest".equals(guide.category)?"Quest Helper: ":"")+guide.title, () -> openGuide.accept(guide.id)));
        }
        if (related.size() > 8) guides.add(pager(guidePage, related.size(), 8,
            () -> { --guidePage; renderDetail(); }, () -> { ++guidePage; renderDetail(); }));
        content.add(guides);
    }

    private void addGuideLinks(JPanel target, String name)
    {
        for (GuideCatalog.Entry guide : catalog.sourceGuides(name))
            target.add(button(("quest".equals(guide.category) ? "Quest Helper: " : "Guide: ") + guide.title, () -> openGuide.accept(guide.id)));
    }

    private static boolean isRequirement(ItemSourceService.Source source)
    { return source.kind.equals("Requirements") || source.kind.equals("Creation requirements"); }

    static List<String[]> sourceFields(String details)
    {
        List<String[]> fields = new ArrayList<>();
        if (details == null || details.trim().isEmpty()) return fields;
        for (String field : details.split("\\s+·\\s+"))
        {
            int colon = field.indexOf(':');
            fields.add(colon > 0 ? new String[]{field.substring(0, colon).trim(), field.substring(colon + 1).trim()}
                : new String[]{"", field.trim()});
        }
        return fields;
    }

    private static void addSourceFields(JPanel target, String details)
    {
        for (String[] field : sourceFields(details))
            target.add(field[0].isEmpty() ? text(field[1], false) : fact(field[0], field[1]));
    }

    private static JPanel pager(int page, int count, int size, Runnable previous, Runnable next)
    {
        JPanel row = fullRow();
        JButton back = SidebarUi.button("‹", previous), forward = SidebarUi.button("›", next);
        back.setPreferredSize(new Dimension(30, 30)); forward.setPreferredSize(new Dimension(30, 30));
        back.setEnabled(page > 0); forward.setEnabled((page + 1) * size < count);
        back.setToolTipText("Previous page"); forward.setToolTipText("Next page");
        row.add(back, BorderLayout.WEST); row.add(forward, BorderLayout.EAST);
        JLabel label = new JLabel((page + 1) + " / " + Math.max(1, (count + size - 1) / size), JLabel.CENTER);
        label.setForeground(GOLD); label.setFont(GuideText.FONT); row.add(label, BorderLayout.CENTER);
        row.setName("runewarden.items.pager"); return row;
    }
    private void refresh() { content.revalidate(); content.repaint(); }
    private static String number(long value) { return String.format(java.util.Locale.ROOT, "%,d", value); }
    private static GuideText text(String value, boolean heading)
    { return heading ? SidebarUi.heading(value) : SidebarUi.label(value); }
    private static GuideText muted(String value)
    { GuideText text = text(value, false); text.setForeground(SidebarUi.MUTED); return text; }
    private static JPanel vertical(Color color)
    {
        JPanel panel = SidebarUi.column(); panel.setOpaque(true); panel.setBackground(color);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0)); return panel;
    }
    private static JPanel section(String title, String name)
    {
        JPanel section = SidebarUi.section(title); section.setName(name);
        return section;
    }
    private static JPanel fact(String label, String value)
    {
        JPanel field = SidebarUi.column();
        field.add(text(label + ": " + value, false));
        field.setName("runewarden.items.fact"); return field;
    }
    private static JPanel fullRow()
    {
        JPanel row = new JPanel(new BorderLayout(6, 0))
        {
            @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        row.setOpaque(false); row.setAlignmentX(Component.LEFT_ALIGNMENT); return row;
    }
    private static JCheckBox check(String title, boolean selected)
    {
        JCheckBox checkbox = new JCheckBox(title, selected); style(checkbox); checkbox.setOpaque(false);
        checkbox.setAlignmentX(Component.LEFT_ALIGNMENT); checkbox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return checkbox;
    }
    private static JButton button(String title, Runnable action)
    {
        JButton button = new WrappedButton(title); style(button); button.setFocusPainted(false);
        button.setToolTipText(title); button.setHorizontalAlignment(JButton.LEFT);
        button.setMargin(new Insets(6, 6, 6, 6)); button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(event -> action.run()); return button;
    }
    private static void style(javax.swing.JComponent component)
    { component.setBackground(CARD); component.setForeground(TEXT); component.setFont(GuideText.FONT); }

    /** Fits RuneLite's real viewport and grows vertically for long item/guide names. */
    private static final class WrappedButton extends JButton
    {
        WrappedButton(String title)
        {
            super("<html>" + title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</html>");
            getAccessibleContext().setAccessibleName(title);
        }
        @Override public Dimension getPreferredSize()
        {
            Container parent = getParent();
            int width = parent == null || parent.getWidth() <= 0 ? 190
                : Math.max(60, parent.getWidth() - parent.getInsets().left - parent.getInsets().right);
            Insets insets = getInsets();
            int iconWidth = getIcon() == null ? 0 : getIcon().getIconWidth() + getIconTextGap();
            int iconHeight = getIcon() == null ? 0 : getIcon().getIconHeight();
            View view = (View) getClientProperty(BasicHTML.propertyKey);
            int textHeight = getFontMetrics(getFont()).getHeight();
            if (view != null)
            {
                view.setSize(Math.max(30, width - insets.left - insets.right - iconWidth), 0);
                textHeight = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
            }
            return new Dimension(width, Math.max(iconHeight, textHeight) + insets.top + insets.bottom);
        }
        @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    }
}
