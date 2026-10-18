/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.BoxLayout;
import javax.swing.JTextField;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.config.ConfigManager;

/** A temporary ordinary bank search. Bank tabs and item contents are untouched. */
@Singleton
public final class GuideBankFilter
{
    static final String QUERY = "runewarden guide items";
    private static final Pattern LINK = Pattern.compile("\\[\\[([^]#|]+)(?:\\|[^]]*)?]]");
    private static final class Supply { String name; List<Integer> itemIds; String evidence; }
    private static final class Supplies { Map<String, List<Supply>> methods; }
    private static final Supplies SUPPLIES = readSupplies();
    private final Client client;
    private final ClientThread clientThread;
    private final net.runelite.client.plugins.bank.BankSearch bankSearch;
    private final Supplier<GuideCatalog.Entry> selectedGuide;
    private Supplier<List<JsonObject>> stepItems = Collections::emptyList;
    private int stepFingerprint;
    private final ItemManager items;
    private final ConfigManager config;
    private final Gson gson;
    private boolean started;
    private boolean enabled = true;
    private boolean bankWasOpen;
    private boolean active;
    private boolean suspended;
    private String previousQuery = "";
    private int previousInputType;
    private GuideCatalog.Entry selected;
    private Set<Integer> required = Collections.emptySet();
    private Set<Integer> externalTargets;
    private String externalOwner;
    private int unresolved;
    private GuideSupplyPlanner.Plan plan = GuideSupplyPlanner.resolve(null, Collections.emptySet(), 0, GuideSupplyPlanner.GearView.GUIDE);
    private GuideSupplyPlanner.GearView gearView = GuideSupplyPlanner.GearView.GUIDE;
    private JComboBox<GuideSupplyPlanner.GearView> gearChoice;
    private boolean rebuildingGearChoice;
    private JPanel requirementPicker;
    private JTextField choiceSearch;
    private JComboBox<BankChoice> itemChoice;
    private GuideText choiceSummary;
    private GuideText pendingRequirement;
    private final Set<Integer> chosenItems = new LinkedHashSet<>();
    private volatile List<BankChoice> bankChoices = Collections.emptyList();
    private String activeAccount;
    private int bankFingerprint;
    private volatile long uiRevision;
    private int metadataRetries;
    private volatile String status = "Select a guide to filter its bank items.";
    private GuideText statusLabel;

    public GuideBankFilter(Client client, ClientThread clientThread,
        net.runelite.client.plugins.bank.BankSearch bankSearch, RunewardenGuides guides, ItemManager items)
    { this(client, clientThread, bankSearch, guides::selectedGuide, items); stepItems = guides::currentStepItems; }

    public GuideBankFilter(Client client, ClientThread clientThread,
        net.runelite.client.plugins.bank.BankSearch bankSearch, RunewardenGuides guides, ItemManager items, ConfigManager config)
    { this(client, clientThread, bankSearch, guides::selectedGuide, items, config); stepItems = guides::currentStepItems; }

    @Inject public GuideBankFilter(Client client, ClientThread clientThread,
        net.runelite.client.plugins.bank.BankSearch bankSearch, RunewardenGuides guides, ItemManager items, ConfigManager config, Gson gson)
    { this(client, clientThread, bankSearch, guides::selectedGuide, items, config, gson); stepItems = guides::currentStepItems; }

    GuideBankFilter(Client client, ClientThread clientThread,
        net.runelite.client.plugins.bank.BankSearch bankSearch, Supplier<GuideCatalog.Entry> selectedGuide, ItemManager items)
    { this(client, clientThread, bankSearch, selectedGuide, items, null); }

    GuideBankFilter(Client client, ClientThread clientThread,
        net.runelite.client.plugins.bank.BankSearch bankSearch, Supplier<GuideCatalog.Entry> selectedGuide, ItemManager items, ConfigManager config)
    { this(client, clientThread, bankSearch, selectedGuide, items, config, null); }

    GuideBankFilter(Client client, ClientThread clientThread,
        net.runelite.client.plugins.bank.BankSearch bankSearch, Supplier<GuideCatalog.Entry> selectedGuide, ItemManager items, ConfigManager config, Gson gson)
    {
        this.client = client; this.clientThread = clientThread; this.bankSearch = bankSearch;
        this.selectedGuide = selectedGuide; this.items = items; this.config = config; this.gson = gson;
    }

    public JPanel createPanel()
    {
        JPanel panel = SidebarUi.column(); panel.setName("guide-bank-filter");
        JPanel controls = SidebarUi.section("Guide supplies");
        JCheckBox auto = new JCheckBox("Auto filter supplies", enabled); styleControl(auto);
        auto.addActionListener(event -> { boolean value = auto.isSelected(); clientThread.invokeLater(() -> setEnabled(value)); });
        controls.add(auto);
        gearChoice = new JComboBox<>(GuideSupplyPlanner.GearView.values()); styleControl(gearChoice);
        gearChoice.setMaximumRowCount(8);
        gearChoice.addActionListener(event -> {
            if (rebuildingGearChoice) return;
            GuideSupplyPlanner.GearView view = (GuideSupplyPlanner.GearView) gearChoice.getSelectedItem();
            clientThread.invokeLater(() -> setGearView(view));
        });
        controls.add(SidebarUi.field("Equipment view", gearChoice));
        JButton clear = SidebarUi.button("Show full bank", () -> clientThread.invokeLater(() -> { externalTargets = null; release(true); suspended = true; updateStatus(); }));
        clear.setToolTipText("Restore your bank search"); controls.add(clear);
        statusLabel = SidebarUi.label(""); controls.add(statusLabel); panel.add(controls);
        requirementPicker = SidebarUi.section("Missing requirement");
        pendingRequirement = SidebarUi.label(""); requirementPicker.add(pendingRequirement);
        choiceSearch = new JTextField(); styleControl(choiceSearch); choiceSearch.setToolTipText("Search your observed bank");
        requirementPicker.add(SidebarUi.field("Find bank item", choiceSearch));
        itemChoice = new JComboBox<>(); styleControl(itemChoice); itemChoice.setMaximumRowCount(8); requirementPicker.add(itemChoice);
        choiceSearch.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent event) { refreshItemChoices(); }
            public void removeUpdate(DocumentEvent event) { refreshItemChoices(); }
            public void changedUpdate(DocumentEvent event) { refreshItemChoices(); }
        });
        requirementPicker.add(SidebarUi.button("Include item", () -> {
            BankChoice choice = (BankChoice) itemChoice.getSelectedItem();
            if (choice != null) clientThread.invokeLater(() -> addChosenItem(choice.id));
        }));
        requirementPicker.add(SidebarUi.button("Clear choices", () -> clientThread.invokeLater(this::clearChosenItems)));
        choiceSummary = SidebarUi.label(""); requirementPicker.add(choiceSummary); panel.add(requirementPicker);
        updateStatus(); return panel;
    }

    private static void styleControl(javax.swing.JComponent component)
    {
        component.setBackground(new Color(40, 40, 39)); component.setForeground(SidebarUi.TEXT);
        component.setFont(GuideText.FONT); component.setAlignmentX(0);
        component.setMinimumSize(new java.awt.Dimension(0, 30)); component.setPreferredSize(new java.awt.Dimension(178, 30));
        component.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 30));
        if (component instanceof JTextField) ((JTextField) component).setCaretColor(SidebarUi.GOLD);
        if (component instanceof JComboBox) ((JComboBox<?>) component).setRenderer(new DefaultListCellRenderer()
        {
            @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,
                Object value, int index, boolean selected, boolean focus)
            {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setForeground(SidebarUi.TEXT); setBackground(selected ? new Color(65, 58, 41) : new Color(40, 40, 39)); setFont(GuideText.FONT); return this;
            }
        });
    }

    public void start() { started = true; bankWasOpen = false; suspended = false; selected = null; activeAccount = null; bankFingerprint = 0; chosenItems.clear(); externalTargets = null; }
    public void stop() { started = false; release(true); bankWasOpen = false; selected = null; required = Collections.emptySet();
        activeAccount = null; chosenItems.clear(); bankChoices = Collections.emptyList(); bankFingerprint = 0; externalTargets = null; }

    /** Explicit local loadout focus. Call on the client thread; it never changes the selected guide. */
    public void setExternalTargets(Set<Integer> itemIds)
    {setExternalTargets("preparation",itemIds);}

    public void setExternalTargets(String owner,Set<Integer> itemIds)
    {
        if (!started || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) return;
        if(owner==null||owner.length()>100)return;
        if (itemIds == null || itemIds.isEmpty()) { clearExternalTargets(owner); return; }
        if (itemIds.size() > 400) return;
        Set<Integer> canonical = new LinkedHashSet<>();
        for (Integer id : itemIds) if (id != null && id > 0 && id <= 1_000_000)
        { int mapped = items.canonicalize(id); if (mapped > 0) canonical.add(mapped); }
        if (canonical.isEmpty()) return;
        release(true);
        String account = PlayerIdentity.normalizedName(client.getLocalPlayer().getName());
        GuideCatalog.Entry now = selectedGuide.get();
        if (now != null && "quest".equals(now.category)) now = null;
        boolean accountChanged = !Objects.equals(account, activeAccount);
        if (accountChanged) { bankChoices = Collections.emptyList(); bankFingerprint = 0; }
        boolean selectionChanged = accountChanged || now != selected;
        activeAccount = account; selected = now;
        if (selectionChanged) { loadChoices(); metadataRetries = 0; }
        buildRequirements(selected);
        externalTargets = Collections.unmodifiableSet(canonical); externalOwner=owner;suspended = false;
        if (enabled && bankOpen()) activate(); updateStatus();
    }

    public void clearExternalTargets()
    {
        if (externalTargets == null) return;
        release(true); externalTargets = null;externalOwner=null;suspended = false;
        if (enabled && bankOpen()) activate(); updateStatus();
    }
    public void clearExternalTargets(String owner){if(java.util.Objects.equals(owner,externalOwner))clearExternalTargets();}

    /** Release the dismissed guide immediately; a separate explicit supplies focus keeps its ownership. */
    void onGuideDismissed()
    {
        if(!started||selectedGuide.get()!=null)return;
        selected=null;stepFingerprint=0;suspended=false;chosenItems.clear();buildRequirements(null);
        if(externalTargets==null)release(true);
        updateStatus();
    }

    public void setEnabled(boolean value)
    {
        enabled = value; suspended = false;
        if (!enabled) release(true);
        else if (bankOpen()) activate();
        updateStatus();
    }

    void setGearView(GuideSupplyPlanner.GearView view)
    {
        if (view == null || gearView == view) return;
        externalTargets = null;
        gearView = view; buildRequirements(selected);
        persistChoices();
        if (active) bankSearch.layoutBank();
        updateStatus();
    }

    public void onGameTick()
    {
        if (!started) return;
        String account = client.getLocalPlayer() == null ? null : PlayerIdentity.normalizedName(client.getLocalPlayer().getName());
        boolean accountChanged = !Objects.equals(account, activeAccount);
        if (accountChanged) { release(false); externalTargets = null; activeAccount = account; bankChoices = Collections.emptyList(); bankFingerprint = 0; }
        GuideCatalog.Entry now = selectedGuide.get();
        if (now != null && "quest".equals(now.category)) now = null;
        if (now != selected || accountChanged)
        {
            externalTargets = null;
            selected = now; suspended = false; loadChoices(); buildRequirements(now); metadataRetries = 0;
            if (now == null) release(true);
            else if (active) bankSearch.layoutBank();
            else if (bankOpen() && enabled && !suspended) activate();
        }
        int nextStep = currentStepItems().hashCode();
        if (selected != null && nextStep != stepFingerprint)
        { stepFingerprint = nextStep; buildRequirements(selected); if(active) bankSearch.layoutBank(); }
        boolean open = bankOpen();
        if (open && !bankWasOpen) { suspended = false; metadataRetries = 0; if (enabled) activate(); }
        if (open && !active && !suspended && enabled) activate();
        if (!open && bankWasOpen) { release(false); externalTargets = null; suspended = false; }
        if (open) captureBankChoices();
        if (active && plan.hasGearView() && metadataRetries++ < 5) bankSearch.layoutBank();
        // A typed search or another plugin takes ownership immediately. Never fight it.
        if (active && !QUERY.equals(client.getVarcStrValue(VarClientID.MESLAYERINPUT)))
        { active = false; suspended = true; }
        bankWasOpen = open;
        updateStatus();
    }

    public void onGameStateChanged(GameState state)
    {
        if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
        { release(false); externalTargets = null; bankWasOpen = false; suspended = false; bankChoices = Collections.emptyList(); bankFingerprint = 0;
            if (state != GameState.HOPPING) { activeAccount = null; chosenItems.clear(); } }
    }

    public void onScriptCallbackEvent(ScriptCallbackEvent event)
    {
        if (!started || !active || !"bankSearchFilter".equals(event.getEventName())) return;
        int[] ints = client.getIntStack(); Object[] strings = client.getObjectStack();
        int n = client.getIntStackSize(), s = client.getObjectStackSize();
        if (n < 2 || s < 1 || !QUERY.equals(strings[s - 1])) return;
        int id = ints[n - 1];
        int canonical = id > 0 ? items.canonicalize(id) : id;
        boolean matches = canonical > 0 && (externalTargets != null ? externalTargets.contains(canonical)
            : required.contains(canonical) || GuideBankFamilies.matches(plan.families, canonical, items, selected == null ? null : selected.skill));
        ints[n - 2] = matches ? 1 : 0;
    }

    private boolean bankOpen()
    {
        if (client.getGameState() != GameState.LOGGED_IN) return false;
        Widget widget = client.getWidget(InterfaceID.Bankmain.ITEMS);
        return widget != null && !widget.isHidden();
    }

    private void activate()
    {
        if (!started || active || suspended || selected == null && externalTargets == null || !bankOpen()) return;
        int input = client.getVarcIntValue(VarClientID.MESLAYERMODE);
        if (input != InputType.NONE.getType() && input != InputType.SEARCH.getType()) return;
        previousQuery = client.getVarcStrValue(VarClientID.MESLAYERINPUT);
        previousInputType = client.getVarcIntValue(VarClientID.MESLAYERMODE);
        active = true;
        bankSearch.initSearch();
        client.setVarcStrValue(VarClientID.MESLAYERINPUT, QUERY);
        bankSearch.layoutBank();
    }

    private void release(boolean layout)
    {
        boolean ownQuery = active && QUERY.equals(client.getVarcStrValue(VarClientID.MESLAYERINPUT));
        active = false;
        if (!ownQuery) return;
        if (layout && bankOpen())
        {
            bankSearch.reset(true);
            if (previousInputType == InputType.SEARCH.getType()) bankSearch.initSearch();
        }
        client.setVarcStrValue(VarClientID.MESLAYERINPUT, previousQuery == null ? "" : previousQuery);
        // A closed bank must not leave the chatbox in bank-search input mode.
        if (layout && bankOpen()) client.setVarcIntValue(VarClientID.MESLAYERMODE, previousInputType);
        else if (client.getVarcIntValue(VarClientID.MESLAYERMODE) == InputType.SEARCH.getType())
            client.setVarcIntValue(VarClientID.MESLAYERMODE, InputType.NONE.getType());
        if (layout && bankOpen()) bankSearch.layoutBank();
    }

    private void buildRequirements(GuideCatalog.Entry guide)
    {
        Set<Integer> resolved = new LinkedHashSet<>(); unresolved = 0;
        if (guide != null)
        {
            addRequirements(guide.items, resolved);
            addRequirements(currentStepItems(), resolved);
            List<Supply> supplies = SUPPLIES.methods.get(guide.id);
            if (supplies != null) for (Supply supply : supplies) resolved.addAll(supply.itemIds);
            // Training guides carry explicit item links in their preparation steps.
            if ((guide.items == null || guide.items.isEmpty()) && guide.sections != null)
            {
                for (GuideCatalog.Section section : guide.sections)
                {
                    if (section.items != null) addRequirements(section.items, resolved);
                    if (!"prepare".equals(section.id) || section.steps == null) continue;
                    for (GuideCatalog.Step step : section.steps)
                    {
                        if (step.text == null || !step.text.matches("(?is)^(Bring|Pack|Withdraw|Equip|Wear|Take|Have|Buy|Obtain|Prepare)\\b.*")) continue;
                        Matcher matcher = LINK.matcher(step.text);
                        while (matcher.find()) addNamedRequirement(matcher.group(1), resolved);
                    }
                }
                if ("woodcutting".equalsIgnoreCase(guide.skill)) addNamedRequirement("Any axe", resolved);
                if ("mining".equalsIgnoreCase(guide.skill)) addNamedRequirement("Any pickaxe", resolved);
                if (guide.source != null && guide.source.contains("/Uncut_"))
                    addNamedRequirement(guide.source.substring(guide.source.lastIndexOf('/') + 1).replace('_', ' '), resolved);
                addFarmingSupplies(guide.id, resolved);
            }
        }
        plan = GuideSupplyPlanner.resolve(guide, resolved, unresolved, gearView, chosenItems);
        Set<Integer> canonical = new LinkedHashSet<>();
        for (int id : plan.itemIds) { int mapped = items.canonicalize(id); if (mapped > 0) canonical.add(mapped); }
        required = Collections.unmodifiableSet(canonical);
    }

    private List<JsonObject> currentStepItems()
    { List<JsonObject> requirements=stepItems.get();return requirements==null?Collections.emptyList():requirements; }

    private void addNamedRequirement(String name, Set<Integer> resolved)
    {
        JsonObject item = new JsonObject(); item.addProperty("name", name);
        Set<Integer> ids = GuideItemResolver.resolveAll(item);
        if (!ids.isEmpty()) resolved.addAll(ids);
    }

    private void addFarmingSupplies(String id, Set<Integer> resolved)
    {
        String[] seeds;
        switch (id == null ? "" : id)
        {
            case "training:allotment-runs": seeds = new String[]{"Allotment seeds", "Flower seeds", "Any watering can"}; break;
            case "training:herb-runs": seeds = new String[]{"Herb seeds", "Magic secateurs"}; break;
            case "training:tree-runs": seeds = new String[]{"Tree saplings"}; break;
            case "training:fruit-tree-runs": seeds = new String[]{"Fruit tree saplings"}; break;
            case "training:hardwood-runs": seeds = new String[]{"Hardwood saplings"}; break;
            case "training:giant-seaweed": seeds = new String[]{"Seaweed spore"}; break;
            case "training:tithe-farm": seeds = new String[]{"Any watering can"}; break;
            case "training:calquat-runs": seeds = new String[]{"Calquat sapling"}; break;
            case "training:crystal-tree-runs": seeds = new String[]{"Crystal sapling"}; break;
            case "training:celastrus-runs": seeds = new String[]{"Celastrus sapling"}; break;
            case "training:redwood-farming": seeds = new String[]{"Redwood sapling"}; break;
            case "training:farming-contracts": seeds = new String[]{"Allotment seeds", "Flower seeds", "Herb seeds", "Tree saplings", "Fruit tree saplings", "Potato cactus seed", "Cactus seed", "Whiteberry seed", "Poison ivy seed", "Celastrus sapling", "Redwood sapling"}; break;
            default: return;
        }
        for (String seed : seeds) addNamedRequirement(seed, resolved);
        for (String tool : new String[]{"Spade", "Rake", "Seed dibber", "Any compost"}) addNamedRequirement(tool, resolved);
    }

    private void addRequirements(List<JsonObject> list, Set<Integer> resolved)
    {
        if (list == null) return;
        for (JsonObject requirement : list)
        {
            Set<Integer> ids = GuideItemResolver.resolveAll(requirement);
            if (ids.isEmpty()) unresolved++; else resolved.addAll(ids);
        }
    }

    private void updateStatus()
    {
        status = !enabled ? "Guide bank filter off." : externalTargets != null ? active ? "Loadout supplies shown." : "Loadout supplies ready for bank." : selected == null ? "Select a guide to filter its bank items."
            : suspended ? "Full bank / your search. Reopen bank to resume."
            : active && plan.isItemFree() ? "No bank supplies needed for this guide."
            : active ? "Guide supplies shown" + (chosenItems.isEmpty() && !plan.unresolved.isEmpty() ? " · Choose requirement below." : ".")
            : "Ready for your next bank visit.";
        GuideText label = statusLabel;
        if (label != null) SwingUtilities.invokeLater(() -> label.setText(!enabled ? "Filter off" : externalTargets != null ? active ? "Loadout supplies shown" : "Loadout supplies ready" : selected == null ? "No guide selected"
            : suspended ? "Full bank shown" : active ? plan.isItemFree() ? "No supplies needed" : "Guide supplies shown" : "Ready for bank"));
        JComboBox<GuideSupplyPlanner.GearView> choice = gearChoice;
        GuideSupplyPlanner.GearView current = gearView;
        boolean gear = plan.hasGearView();
        boolean showPicker = externalTargets == null && selected != null && !plan.isItemFree() && (!plan.unresolved.isEmpty() || !chosenItems.isEmpty());
        int chosen = chosenItems.size(); long revision = ++uiRevision;
        String pending = plan.unresolved.isEmpty() ? "" : plan.unresolved.get(0)
            + (plan.unresolved.size() > 1 ? " (" + plan.unresolved.size() + " choices)" : "");
        List<String> chosenNames = new ArrayList<>();
        for (int id : chosenItems)
        {
            net.runelite.api.ItemComposition definition = items.getItemComposition(id);
            chosenNames.add(definition == null || definition.getName() == null ? "Item " + id : definition.getName());
        }
        String names = String.join(", ", chosenNames);
        if (choice != null) SwingUtilities.invokeLater(() -> {
            if (revision != uiRevision) return;
            rebuildingGearChoice = true;
            try { choice.setEnabled(gear); if (choice.getSelectedItem() != current) choice.setSelectedItem(current); }
            finally { rebuildingGearChoice = false; }
            requirementPicker.setVisible(showPicker);
            pendingRequirement.setText(pending);
            choiceSummary.setText(chosen == 0 ? "No added items" : chosen + " added items");
            choiceSummary.setToolTipText(names);
        });
    }

    private static final class BankChoice
    {
        final int id; final String name; final int quantity;
        BankChoice(int id, String name, int quantity) { this.id = id; this.name = name; this.quantity = quantity; }
        @Override public String toString() { return name + " × " + quantity; }
    }
    private static final class ChoiceState { Set<Integer> itemIds; GuideSupplyPlanner.GearView view; }

    void addChosenItem(int id)
    {
        if (selected == null || activeAccount == null || id <= 0 || plan.isItemFree()) return;
        chosenItems.add(items.canonicalize(id)); persistChoices(); buildRequirements(selected);
        if (active) bankSearch.layoutBank(); updateStatus();
    }
    void clearChosenItems()
    {
        chosenItems.clear(); persistChoices(); buildRequirements(selected);
        if (active) bankSearch.layoutBank(); updateStatus();
    }
    private String choicesKey()
    {
        return "guideBank_" + Base64.getUrlEncoder().withoutPadding().encodeToString(
            (activeAccount + ":" + selected.id).getBytes(StandardCharsets.UTF_8));
    }
    private void loadChoices()
    {
        chosenItems.clear(); gearView = GuideSupplyPlanner.GearView.GUIDE;
        if (config == null || selected == null || activeAccount == null) return;
        try
        {
            String saved = config.getRSProfileConfiguration(RunewardenConfig.GROUP, choicesKey());
            if (saved == null) return;
            ChoiceState state = gson == null ? readChoice(saved) : gson.fromJson(saved, ChoiceState.class);
            if (state.itemIds != null) for (int id : state.itemIds) if (id > 0) chosenItems.add(id);
            if (state.view != null) gearView = state.view;
        }
        catch (RuntimeException ignored) { chosenItems.clear(); }
    }
    private void persistChoices()
    {
        if (config == null || selected == null || activeAccount == null) return;
        ChoiceState state = new ChoiceState(); state.itemIds = chosenItems; state.view = gearView;
        config.setRSProfileConfiguration(RunewardenConfig.GROUP, choicesKey(), gson == null ? writeChoice(state) : gson.toJson(state));
    }

    private static ChoiceState readChoice(String json)
    {
        JsonObject object = new JsonParser().parse(json).getAsJsonObject();
        ChoiceState state = new ChoiceState(); state.itemIds = new LinkedHashSet<>();
        if (object.has("itemIds") && object.get("itemIds").isJsonArray())
            for (JsonElement id : object.getAsJsonArray("itemIds")) state.itemIds.add(id.getAsInt());
        if (object.has("view") && !object.get("view").isJsonNull())
            state.view = GuideSupplyPlanner.GearView.valueOf(object.get("view").getAsString());
        return state;
    }

    private static String writeChoice(ChoiceState state)
    {
        JsonObject object = new JsonObject(); JsonArray ids = new JsonArray();
        if (state.itemIds != null) for (int id : state.itemIds) ids.add(id);
        object.add("itemIds", ids);
        if (state.view != null) object.addProperty("view", state.view.name());
        return object.toString();
    }

    private static Supplies readSupplies()
    {
        try (InputStream input = GuideBankFilter.class.getResourceAsStream("/com/runewarden/guide-bank-supplies.json"))
        {
            if (input == null) throw new IllegalStateException("Missing guide bank supplies");
            JsonObject methods = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("methods");
            Supplies supplies = new Supplies(); supplies.methods = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> method : methods.entrySet())
            {
                List<Supply> entries = new ArrayList<>();
                for (JsonElement value : method.getValue().getAsJsonArray())
                {
                    JsonObject object = value.getAsJsonObject(); Supply supply = new Supply();
                    supply.name = object.has("name") ? object.get("name").getAsString() : null;
                    supply.evidence = object.has("evidence") ? object.get("evidence").getAsString() : null;
                    supply.itemIds = new ArrayList<>();
                    for (JsonElement id : object.getAsJsonArray("itemIds")) supply.itemIds.add(id.getAsInt());
                    entries.add(supply);
                }
                supplies.methods.put(method.getKey(), Collections.unmodifiableList(entries));
            }
            supplies.methods = Collections.unmodifiableMap(supplies.methods);
            return supplies;
        }
        catch (java.io.IOException error) { throw new IllegalStateException("Could not read guide bank supplies", error); }
    }
    private void captureBankChoices()
    {
        ItemContainer bank = client.getItemContainer(InventoryID.BANK);
        if (bank == null) return;
        int fingerprint = 1;
        for (Item item : bank.getItems()) fingerprint = fingerprint * 31 + item.getId() * 7 + item.getQuantity();
        if (fingerprint == bankFingerprint) return;
        bankFingerprint = fingerprint; List<BankChoice> choices = new ArrayList<>(); Set<Integer> added = new LinkedHashSet<>();
        for (Item item : bank.getItems())
        {
            if (item.getId() <= 0 || item.getQuantity() <= 0) continue;
            int id = items.canonicalize(item.getId()); if (!added.add(id)) continue;
            net.runelite.api.ItemComposition definition = items.getItemComposition(id);
            if (definition != null && definition.getName() != null) choices.add(new BankChoice(id, definition.getName(), item.getQuantity()));
        }
        choices.sort(Comparator.comparing(choice -> choice.name.toLowerCase(java.util.Locale.ROOT)));
        bankChoices = Collections.unmodifiableList(choices); SwingUtilities.invokeLater(this::refreshItemChoices);
    }
    private void refreshItemChoices()
    {
        if (itemChoice == null) return;
        String query = choiceSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
        itemChoice.removeAllItems(); int count = 0;
        for (BankChoice choice : bankChoices)
            if (choice.name.toLowerCase(java.util.Locale.ROOT).contains(query) && count++ < 60) itemChoice.addItem(choice);
        itemChoice.setToolTipText(bankChoices.isEmpty() ? "Open your bank to choose its items" : "Choose the item requested by this guide");
    }
    private static String escape(String text)
    { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    public String status() { return status; }
    /** Current resolved supply IDs for local preparation and relevant-loot highlighting. */
    public Set<Integer> requiredItemIds() { return externalTargets == null ? required : externalTargets; }
    boolean isActive() { return active; }
    Set<Integer> requiredItems() { return required; }
    GuideSupplyPlanner.Plan supplyPlan() { return plan; }
}
