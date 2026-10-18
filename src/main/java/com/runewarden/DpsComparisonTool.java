/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/** Searchable targets, ranked saved equipment and editable character-specific combat experiments. */
@Singleton
final class DpsComparisonTool
{
    static final String CURRENT = "current";
    static final class Setup
    {
        final String id, name, ownership, style, spell, dart;
        final LoadoutPlan plan;
        final DpsCalculator.Result result;
        final DpsWorkbenchPlan.Purchase purchase;
        final List<DpsReferenceCatalog.Mode> modes;
        final boolean sandbox, enabled, special;
        Setup(LoadoutPlan plan, DpsCalculator.Result result, DpsWorkbenchPlan.Purchase purchase,
            List<DpsReferenceCatalog.Mode> modes, DpsWorkbenchPlan.Choice choice, boolean sandbox)
        {
            this.plan = plan; id = plan.id; name = plan.name; this.result = result; this.purchase = purchase;
            ownership = purchase.label(); this.modes = Collections.unmodifiableList(new ArrayList<>(modes));
            style = choice == null ? null : choice.style; spell = choice == null ? null : choice.spell;
            dart = choice == null ? null : choice.dart; special = choice != null && choice.special; enabled = choice == null || choice.enabled; this.sandbox = sandbox;
        }
    }
    static final class Reading
    {
        final String target, comparisonName, comparisonStatus, status;
        final DpsCalculator.Result current, comparison;
        final List<LoadoutPlan> plans;
        final List<Setup> setups, ranking;
        final int weaponId;
        final DpsReferenceCatalog.Target targetFacts;
        final Integer hpOverride, defenceOverride, magicOverride;
        final long budget;
        final DpsTargetScenario.Inputs scenario;
        Reading(String target, DpsCalculator.Result current, DpsCalculator.Result comparison,
            String comparisonName, String comparisonStatus, List<LoadoutPlan> plans, int weaponId,
            List<Setup> setups, List<Setup> ranking, DpsReferenceCatalog.Target facts, DpsWorkbenchPlan.Saved state, String status)
        {
            this.target = target; this.current = current; this.comparison = comparison;
            this.comparisonName = comparisonName; this.comparisonStatus = comparisonStatus;
            this.plans = Collections.unmodifiableList(new ArrayList<>(plans)); this.weaponId = weaponId;
            this.setups = Collections.unmodifiableList(new ArrayList<>(setups)); this.ranking = Collections.unmodifiableList(new ArrayList<>(ranking));
            targetFacts = facts; hpOverride = state.hp; defenceOverride = state.defence; magicOverride = state.magic;
            budget = state.budget; scenario = state.scenario; this.status = status;
        }
        static Reading empty(String reason)
        { return new Reading("No target", DpsCalculator.Result.unknown(reason), null, "", "", List.of(), -1,
            List.of(), List.of(), null, new DpsWorkbenchPlan.Saved(), ""); }
    }
    private enum TaskState
    {
        UNKNOWN("Unknown", null), NO("No task bonus", false), YES("On assigned task", true);
        final String label; final Boolean applies;
        TaskState(String label, Boolean applies) { this.label = label; this.applies = applies; }
        @Override public String toString() { return label; }
    }
    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager profiles;
    private final ItemManager items;
    private final CombatCalculatorTool combat;
    private final DpsReferenceCatalog references;
    private final CombatSpellCatalog spells;
    private final ItemKnowledgeCatalog itemNames;
    private final RunewardenDataStore data;
    private final Supplier<OwnershipLedger.View> ownership;
    private final Supplier<List<LoadoutPlan>> presets;
    private final Supplier<LoadoutPlan> preferredLoadout;
    private volatile boolean started, holdTarget;
    private volatile long epoch;
    private volatile String selectedId, profile;
    private volatile TaskState task = TaskState.UNKNOWN;
    private volatile Reading reading = Reading.empty("Choose a target or attack a monster");
    private DpsWorkbenchPlan.Saved state = new DpsWorkbenchPlan.Saved();
    private int lockedTarget = -1;
    private volatile long lastFingerprint = Long.MIN_VALUE, renderedEpoch = -1;
    private JPanel panel, body, targetResults, itemResults, targetBody;
    private JLabel dps;
    private GuideText targetText, currentFacts, currentStatus, statusText;
    private JTextField targetQuery, itemQuery;
    private JCheckBox keepTarget;
    private boolean refreshing;
    private int rankPage;
    private String status = "";
    private final Map<String, Boolean> disclosures = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> drafts = new java.util.concurrent.ConcurrentHashMap<>();
    private IntConsumer openItem = ignored -> { };
    private Runnable openLoadouts = () -> { };

    @Inject DpsComparisonTool(Client client, ClientThread clientThread, ConfigManager profiles, ItemManager items,
        CombatCalculatorTool combat, DpsReferenceCatalog references, OwnershipLedger ownership, LoadoutReadinessTool loadouts,
        CombatSpellCatalog spells, ItemKnowledgeCatalog itemNames, RunewardenDataStore data)
    { this(client, clientThread, profiles, items, combat, references, ownership::snapshot, loadouts::presets, loadouts::selected, spells, itemNames, data); }

    DpsComparisonTool(Client client, ClientThread clientThread, ConfigManager profiles, ItemManager items,
        CombatCalculatorTool combat, DpsReferenceCatalog references, Supplier<OwnershipLedger.View> ownership,
        Supplier<List<LoadoutPlan>> presets, Supplier<LoadoutPlan> preferredLoadout)
    { this(client, clientThread, profiles, items, combat, references, ownership, presets, preferredLoadout, null, null, null); }

    DpsComparisonTool(Client client, ClientThread clientThread, ConfigManager profiles, ItemManager items,
        CombatCalculatorTool combat, DpsReferenceCatalog references, Supplier<OwnershipLedger.View> ownership,
        Supplier<List<LoadoutPlan>> presets, Supplier<LoadoutPlan> preferredLoadout, CombatSpellCatalog spells,
        ItemKnowledgeCatalog itemNames, RunewardenDataStore data)
    {
        this.client = client; this.clientThread = clientThread; this.profiles = profiles; this.items = items;
        this.combat = combat; this.references = references; this.ownership = ownership; this.presets = presets;
        this.preferredLoadout = preferredLoadout; this.spells = spells; this.itemNames = itemNames; this.data = data;
    }
    void start() { started = true; clear(); }
    void stop() { started = false; clear(); }
    void clear()
    {
        ++epoch; profile = null; lockedTarget = -1; holdTarget = false; selectedId = null; task = TaskState.UNKNOWN;
        state = new DpsWorkbenchPlan.Saved(); rankPage = 0; status = ""; disclosures.clear(); drafts.clear();
        lastFingerprint = Long.MIN_VALUE; reading = Reading.empty("Choose a target or attack a monster"); publish();
    }
    /** User-triggered boss preparation link; conflicting variants stay a visible choice. */
    void prepareTarget(String name)
    {
        String query=name==null?"":name.substring(0,Math.min(120,name.length()));long ticket=epoch;
        SwingUtilities.invokeLater(()->{if(!started||ticket!=epoch)return;createPanel();targetQuery.setText(query);disclosures.put("target",true);targetBody.setVisible(true);searchTargets();panel.revalidate();});
    }
    void setOpenItemAction(IntConsumer action) { openItem = action == null ? ignored -> { } : action; }
    void setOpenLoadoutsAction(Runnable action) { openLoadouts = action == null ? () -> { } : action; }
    Reading reading()
    { return started && profile != null && profile.equals(profiles.getRSProfileKey()) && client.getGameState() == GameState.LOGGED_IN
        ? reading : Reading.empty("Log in to read the current character's combat state"); }
    void onGameTick()
    {
        if (!started || !ensureProfile()) return;
        if (panel != null && panel.isShowing()) refreshNow();
    }
    private boolean ensureProfile()
    {
        String account = profiles.getRSProfileKey();
        if (!started || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null || account == null || account.isEmpty())
        { if (profile != null) clear(); return false; }
        if (!account.equals(profile))
        {
            clear(); profile = account;
            if (data != null) state = data.read("runewarden", profile, DpsWorkbenchPlan.KEY, 1,
                DpsWorkbenchPlan.Saved.class, DpsWorkbenchPlan::valid, DpsWorkbenchPlan.Saved::new);
            selectedId = state.selected; holdTarget = state.target > 0; lockedTarget = state.target;
        }
        return true;
    }
    /** Client-thread refresh. Ranking modes skip the expensive TTK recurrence; the selected card gets exact TTK. */
    void refreshNow()
    {
        if (!ensureProfile()) return;
        int nativeTarget = targetId();
        if (nativeTarget > 0) lockedTarget = nativeTarget;
        int targetId = state.target > 0 ? state.target : nativeTarget > 0 ? nativeTarget : holdTarget ? lockedTarget : -1;
        DpsReferenceCatalog.Target base = references.variant(targetId, state.target > 0 ? state.targetVersion : null);
        DpsReferenceCatalog.Target target = DpsReferenceCatalog.scenario(base, state.hp, state.defence, state.magic);
        try { target = DpsTargetScenario.apply(target, state.scenario); }
        catch (IllegalArgumentException invalid) { if (target != null) target.unsupportedReason = "Saved target scenario needs correction: " + invalid.getMessage(); }
        ItemContainer container = CurrentMaxHit.worn(client); Item[] equipment = container == null ? null : container.getItems();
        CurrentMaxHit.Inputs inputs = combat.inputs(); OwnershipLedger.View observed = ownership.get();
        if (observed == null || !observed.active || !profile.equals(observed.profile)) observed = null;
        List<LoadoutPlan> plans = new ArrayList<>();
        if (observed != null) plans.addAll(presets.get());
        plans.addAll(state.builds);
        if (selectedId == null || plans.stream().noneMatch(plan -> plan.id.equals(selectedId)) && !CURRENT.equals(selectedId))
        {
            LoadoutPlan preferred = preferredLoadout.get();
            selectedId = preferred != null && plans.contains(preferred) ? preferred.id : plans.isEmpty() ? CURRENT : plans.get(0).id;
        }
        long fingerprint = fingerprint(targetId, equipment, inputs, observed, plans);
        if (fingerprint == lastFingerprint) return;
        lastFingerprint = fingerprint;
        int weaponId = itemAt(equipment, EquipmentInventorySlot.WEAPON.getSlotIdx());
        DpsReferenceCatalog.Mode observedMode = references.observedMode(references.weapon(weaponId),
            client.getVarpValue(VarPlayerID.COM_MODE), client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE) == 1);
        String targetName = base == null ? targetId > 0 ? "NPC " + targetId : "No target" : targetLabel(target);
        DpsCalculator.Result current;
        if (target == null) current = DpsCalculator.Result.unknown(targetId > 0 ? references.unavailable(targetId) : "Choose a target above");
        else if (equipment == null) current = DpsCalculator.Result.unknown("Worn equipment is unavailable");
        else if (weaponId <= 0 && client.getLocalPlayer().getPlayerComposition() != null
            && client.getLocalPlayer().getPlayerComposition().getEquipmentId(net.runelite.api.kit.KitType.WEAPON) > 0)
            current = DpsCalculator.Result.unknown("Worn equipment is updating");
        else current = DpsCalculator.evaluatePlanned(client, items, references, target, equipment, observedMode, inputs, task.applies, false, null, false);
        List<Setup> setups = new ArrayList<>(), ranked = new ArrayList<>();
        LoadoutPlan worn = DpsWorkbenchPlan.capture(CURRENT, "Current equipment", equipment);
        DpsWorkbenchPlan.Choice wornChoice = choice(CURRENT);
        DpsCalculator.Result wornResult = wornChoice != null && (wornChoice.style != null || wornChoice.spell != null || wornChoice.dart != null || wornChoice.special)
            ? evaluate(worn, target, inputs, wornChoice, observedMode) : current;
        Setup currentSetup = new Setup(worn, CURRENT.equals(selectedId) && target != null ? wornResult.withTtk(target.hitpoints == null ? 1 : target.hitpoints) : wornResult,
            DpsWorkbenchPlan.purchase(worn, observed, items, state.budget), modes(worn, inputs, wornChoice), wornChoice, false);
        setups.add(currentSetup); ranked.add(currentSetup);
        int active = 1;
        for (LoadoutPlan plan : plans)
        {
            DpsWorkbenchPlan.Choice choice = choice(plan.id);
            boolean enabled = choice == null ? active < DpsWorkbenchPlan.MAX_ACTIVE : choice.enabled && active < DpsWorkbenchPlan.MAX_ACTIVE;
            DpsCalculator.Result result = DpsCalculator.Result.unknown(enabled ? "Choose a supported target" : "Excluded from this comparison");
            if (enabled)
            {
                active++;
                String invalid = DpsWorkbenchPlan.exactEquipment(plan);
                result = invalid == null ? evaluate(plan, target, inputs, choice, null) : DpsCalculator.Result.unknown(invalid);
                if (plan.id.equals(selectedId) && target != null && target.hitpoints != null) result = result.withTtk(target.hitpoints);
            }
            DpsWorkbenchPlan.Choice shown = copyChoice(choice, plan.id); shown.enabled = enabled;
            Setup setup = new Setup(plan, result, DpsWorkbenchPlan.purchase(plan, observed, items, state.budget), modes(plan, inputs, choice), shown, state.builds.contains(plan));
            setups.add(setup); if (enabled) ranked.add(setup);
        }
        ranked.sort(Comparator.comparing((Setup setup) -> !setup.result.supported)
            .thenComparing((Setup setup) -> setup.result.supported ? -setup.result.dps : 0.0).thenComparing(setup -> setup.name));
        Setup selected = null; for (Setup setup : setups) if (setup.id.equals(selectedId)) selected = setup;
        DpsCalculator.Result comparison = selected == null || CURRENT.equals(selected.id) ? null : selected.result;
        String comparisonStatus = selected == null ? "Create a sandbox set or open Loadouts" : purchaseFacts(selected.purchase);
        if (selected != null && comparison != null && comparison.supported) comparisonStatus += "\nUses current levels/prayers; best supported mode unless explicitly selected";
        if (current.supported && target != null && target.hitpoints != null && CURRENT.equals(selectedId)) current = current.withTtk(target.hitpoints);
        reading = new Reading(targetName, current, comparison, selected == null || CURRENT.equals(selected.id) ? "" : selected.name,
            comparisonStatus, plans, weaponId, setups, ranked, target, state, status); publish();
    }
    private DpsCalculator.Result evaluate(LoadoutPlan plan, DpsReferenceCatalog.Target target, CurrentMaxHit.Inputs inputs,
        DpsWorkbenchPlan.Choice choice, DpsReferenceCatalog.Mode observedMode)
    {
        if (target == null) return DpsCalculator.Result.unknown("Choose a target above");
        CombatSpellCatalog.Spell spell = choice != null && choice.spell != null && spells != null ? spells.named(choice.spell) : inputs == null ? null : inputs.spell;
        if (choice != null && choice.spell != null && spell == null) return DpsCalculator.Result.unknown("Selected spell is unavailable");
        boolean worn = CURRENT.equals(plan.id);
        Integer darts = choice != null && choice.dart != null ? CombatCalculatorTool.Dart.named(choice.dart).strength
            : inputs == null || inputs.observedDart && !worn ? null : inputs.dartStrength;
        boolean observedDart = worn && inputs != null && inputs.observedDart && (choice == null || choice.dart == null);
        CurrentMaxHit.Inputs planned = new CurrentMaxHit.Inputs(spell, darts, choice != null && choice.spell != null,
            observedDart, inputs == null ? null : inputs.target, false, false);
        Integer book = choice != null && choice.spell != null && spell != null ? "standard".equals(spell.spellbook) ? 0 : "ancient".equals(spell.spellbook) ? 1 : "arceuus".equals(spell.spellbook) ? 3 : null : null;
        DpsCalculator.Result result = null;
        for (DpsReferenceCatalog.Mode mode : modes(plan, inputs, choice))
        {
            if (choice != null && choice.style != null && !choice.style.equals(mode.label())) continue;
            DpsCalculator.Result attempt = DpsCalculator.evaluateAttack(client, items, references, target, equipment(plan), mode, planned, task.applies, !worn, book, false, choice != null && choice.special);
            if (result == null || attempt.supported && (!result.supported || attempt.dps > result.dps)) result = attempt;
        }
        return result == null ? DpsCalculator.Result.unknown("Selected style is unavailable for this weapon") : result;
    }
    private List<DpsReferenceCatalog.Mode> modes(LoadoutPlan plan, CurrentMaxHit.Inputs inputs, DpsWorkbenchPlan.Choice choice)
    {
        int id = plan.equipment().getOrDefault(3, -1); DpsReferenceCatalog.Weapon weapon = references.weapon(id);
        boolean knownSpell = choice != null && choice.spell != null || inputs != null && inputs.spell != null;
        List<DpsReferenceCatalog.Mode> modes = new ArrayList<>(references.comparisonModes(weapon, knownSpell));
        ItemComposition definition = id > 0 ? items.getItemComposition(id) : null;
        CombatSpellCatalog.Spell selectedSpell = choice != null && choice.spell != null && spells != null ? spells.named(choice.spell) : inputs == null ? null : inputs.spell;
        if (knownSpell && selectedSpell != null && "standard".equals(selectedSpell.spellbook) && definition != null && definition.getName() != null && definition.getName().toLowerCase(Locale.ROOT).contains("harmonised nightmare staff"))
            modes.add(new DpsReferenceCatalog.Mode("Spell", "magic", "Autocast"));
        return modes;
    }
    private int targetId()
    {
        Actor actor = client.getLocalPlayer().getInteracting(); if (!(actor instanceof NPC)) return -1;
        NPC npc = (NPC) actor; NPCComposition transformed = npc.getTransformedComposition(); return transformed == null ? npc.getId() : transformed.getId();
    }
    private long fingerprint(int target, Item[] equipment, CurrentMaxHit.Inputs inputs, OwnershipLedger.View observed, List<LoadoutPlan> plans)
    {
        long hash = Objects.hash(target, task, holdTarget, selectedId, state.hp, state.defence, state.magic, state.targetVersion, state.scenario, state.budget, state.choices,
            observed == null ? null : observed.storages, plans, client.getVarpValue(VarPlayerID.COM_MODE),
            client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE), client.getVarpValue(VarPlayerID.SOULREAPER_STACKS),
            client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO), client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT), client.getVarbitValue(VarbitID.SPELLBOOK));
        if (equipment != null) for (Item item : equipment)
        { hash = hash * 31 + (item == null ? 0 : Objects.hash(item.getId(), item.getQuantity())); if (item != null && item.getId() > 0) hash = hash * 31 + equipmentFactsHash(item.getId()); }
        for (LoadoutPlan plan : plans) for (Integer id : plan.equipment().values()) hash = hash * 31 + equipmentFactsHash(id);
        for (Skill skill : new Skill[] {Skill.ATTACK, Skill.STRENGTH, Skill.RANGED, Skill.MAGIC}) hash = hash * 31 + Objects.hash(client.getRealSkillLevel(skill), client.getBoostedSkillLevel(skill));
        for (Prayer prayer : Prayer.values()) hash = hash * 31 + (client.isPrayerActive(prayer) ? 1 : 0);
        if (inputs != null) hash = hash * 31 + Objects.hash(inputs.dartStrength, inputs.observedDart, inputs.spell == null ? null : inputs.spell.name, inputs.manualSpell, inputs.tombsOfAmascut);
        return hash;
    }
    private int equipmentFactsHash(int id)
    { ItemComposition definition = items.getItemComposition(id); return Objects.hash(items.getItemStats(id), definition == null ? null : definition.getName(), items.getItemPrice(id)); }
    static String validateOwned(LoadoutPlan plan, OwnershipLedger.View observed)
    {
        String invalid = DpsWorkbenchPlan.exactEquipment(plan); if (invalid != null) return invalid;
        if (observed == null || !observed.active) return "Ownership is unavailable for the current character";
        if (plan.spellbook != null && !plan.spellbook.equals(observed.spellbook)) return "Saved spellbook does not match the observed current spellbook";
        for (LoadoutPlan.Requirement requirement : plan.requirements)
        {
            if (requirement.location != LoadoutPlan.Location.EQUIPMENT) continue;
            long quantity = 0;
            for (Map.Entry<OwnershipLedger.Storage, OwnershipLedger.Observation> storage : observed.storages.entrySet())
                if (storage.getValue().currentSession && (storage.getKey() == OwnershipLedger.Storage.INVENTORY || storage.getKey() == OwnershipLedger.Storage.EQUIPMENT || storage.getKey() == OwnershipLedger.Storage.BANK)) quantity += storage.getValue().quantity(requirement.firstId());
            if (quantity < requirement.quantity) return "Required equipment is not observed in this session's bank, inventory or worn items";
        }
        return null;
    }
    static Item[] equipment(LoadoutPlan plan)
    {
        Item[] result = new Item[14];
        for (LoadoutPlan.Requirement requirement : plan.requirements)
            if (requirement.location == LoadoutPlan.Location.EQUIPMENT && requirement.slot >= 0 && requirement.alternatives.size() == 1)
                result[requirement.slot] = new Item(requirement.firstId(), (int) requirement.quantity);
        return result;
    }
    private static int itemAt(Item[] equipment, int slot)
    { return equipment == null || slot >= equipment.length || equipment[slot] == null || equipment[slot].getQuantity() <= 0 ? -1 : equipment[slot].getId(); }
    private DpsWorkbenchPlan.Choice choice(String id)
    { for (DpsWorkbenchPlan.Choice choice : state.choices) if (choice.id.equals(id)) return choice; return null; }
    private static DpsWorkbenchPlan.Choice copyChoice(DpsWorkbenchPlan.Choice source, String id)
    { DpsWorkbenchPlan.Choice result = new DpsWorkbenchPlan.Choice(id); if (source != null) { result.style = source.style; result.spell = source.spell; result.dart = source.dart; result.special = source.special; result.enabled = source.enabled; } return result; }
    private DpsWorkbenchPlan.Choice editableChoice(String id)
    { DpsWorkbenchPlan.Choice choice = choice(id); if (choice == null) { choice = new DpsWorkbenchPlan.Choice(id); state.choices.add(choice); } return choice; }
    void selectTargetNow(int id) { selectTargetNow(id, null); }
    void selectTargetNow(int id, String version)
    {
        if (!ensureProfile()) return;
        if (id > 0 && references.variant(id, version) == null) { status = "Choose a catalogued exact target variant"; return; }
        state.target = id; state.targetVersion = id > 0 ? version : null; state.scenario = null; drafts.keySet().removeIf(key -> key.startsWith("drain_") || key.equals("phaseHp")); state.hp = null; state.defence = null; state.magic = null; drafts.remove("scenario_hp"); drafts.remove("scenario_defence"); drafts.remove("scenario_magic"); holdTarget = id > 0; rankPage = 0; changed("Target selected");
    }
    void targetScenarioNow(Integer hp, Integer defence, Integer magic)
    {
        if (!ensureProfile()) return;
        DpsReferenceCatalog.scenario(reading.targetFacts, hp, defence, magic);
        if (hp != null && (hp < 1 || hp > 50000) || defence != null && (defence < 0 || defence > 2000) || magic != null && (magic < 0 || magic > 2000)) throw new IllegalArgumentException("Target values are out of range");
        state.hp = hp; state.defence = defence; state.magic = magic; drafts.remove("scenario_hp"); drafts.remove("scenario_defence"); drafts.remove("scenario_magic"); changed(hp == null && defence == null && magic == null ? "Wiki base stats restored" : "Target scenario applied");
    }
    void effectsScenarioNow(DpsTargetScenario.Inputs inputs)
    {
        if (!ensureProfile()) return;
        DpsReferenceCatalog.Target base = reading.targetFacts == null ? null : references.variant(reading.targetFacts.id, state.targetVersion);
        DpsTargetScenario.apply(DpsReferenceCatalog.scenario(base,state.hp,state.defence,state.magic),inputs);
        if (!DpsTargetScenario.valid(inputs)) throw new IllegalArgumentException("Invalid target scenario");
        state.scenario=inputs; changed(inputs==null?"Defence and phase scenario cleared":"Manual defence and phase scenario applied");
    }
    void specialNow(String id, boolean value)
    { if (!ensureProfile()) return; editableChoice(id).special=value; changed(value?"Special attack comparison selected":"Normal attack comparison selected"); }
    void selectSetupNow(String id)
    { if (!ensureProfile()) return; selectedId = id; state.selected = id; changed(""); }
    String createSandboxNow(String name)
    {
        if (!ensureProfile()) return null;
        if (state.builds.size() >= DpsWorkbenchPlan.MAX_BUILDS) { changed("12 sandbox sets saved"); return null; }
        Setup selected = selectedSetup();
        LoadoutPlan source = selected == null ? DpsWorkbenchPlan.capture(CURRENT, "Current equipment", CurrentMaxHit.worn(client) == null ? null : CurrentMaxHit.worn(client).getItems()) : selected.plan;
        if (DpsWorkbenchPlan.exactEquipment(source) != null) { changed("Select an exact equipment set to duplicate"); return null; }
        String id = "sandbox_" + UUID.randomUUID().toString();
        LoadoutPlan build = new LoadoutPlan(id, name == null || name.trim().isEmpty() ? "Sandbox " + (state.builds.size() + 1) : name, source.requirements, source.spellbook);
        state.builds.add(build); selectedId = id; state.selected = id;
        DpsWorkbenchPlan.Choice copy = copyChoice(choice(source.id), id); copy.enabled = activeCount() < DpsWorkbenchPlan.MAX_ACTIVE; state.choices.add(copy);
        changed("Sandbox equipment saved"); return id;
    }
    void replaceSandboxNow(String id, int slot, int itemId, int quantity)
    {
        if (!ensureProfile()) return;
        for (int i = 0; i < state.builds.size(); i++) if (state.builds.get(i).id.equals(id))
        {
            if (itemId > 0)
            {
                ItemStats stats = items.getItemStats(itemId); ItemEquipmentStats equipment = stats == null ? null : stats.getEquipment();
                if (slot != 13 && quantity != 1) throw new IllegalArgumentException("Only ammunition can use a quantity above one");
                if (stats == null || !stats.isEquipable() || equipment == null || equipment.getSlot() != slot) throw new IllegalArgumentException("Item does not match this equipment slot");
            }
            if (slot == 3 && itemId < 0) throw new IllegalArgumentException("A comparison set needs a weapon");
            state.builds.set(i, DpsWorkbenchPlan.replace(state.builds.get(i), slot, itemId, quantity, null));
            DpsWorkbenchPlan.Choice choice = choice(id); if (slot == 3 && choice != null) choice.style = null;
            changed("Sandbox equipment updated"); return;
        }
    }
    void choiceNow(String id, String style, String spell, String dart)
    {
        if (!ensureProfile()) return;
        DpsWorkbenchPlan.Choice choice = editableChoice(id); choice.style = style; choice.spell = spell; choice.dart = dart;
        changed("Comparison inputs updated");
    }
    void budgetNow(long value)
    { if (!ensureProfile()) return; if (value < 0 || value > 1_000_000_000_000L) throw new IllegalArgumentException("Budget is out of range"); state.budget = value; changed("Purchase budget updated"); }
    private int activeCount()
    { int count = 1; for (Setup setup : reading.setups) if (!CURRENT.equals(setup.id) && setup.enabled) count++; return count; }
    private Setup selectedSetup()
    { for (Setup setup : reading.setups) if (setup.id.equals(selectedId)) return setup; return null; }
    private void changed(String message)
    {
        status = message;
        if (data != null && profile != null && !data.write("runewarden", profile, DpsWorkbenchPlan.KEY, 1, state)) status = "Combat changes could not be saved · open Recovery";
        lastFingerprint = Long.MIN_VALUE; refreshNow();
    }
    private void edit(Runnable action)
    {
        long ticket = epoch; String owner = profile;
        clientThread.invokeLater(() ->
        {
            if (!started || ticket != epoch || owner == null || !owner.equals(profile) || !owner.equals(profiles.getRSProfileKey())) return;
            try { action.run(); } catch (IllegalArgumentException invalid) { status = invalid.getMessage(); lastFingerprint = Long.MIN_VALUE; refreshNow(); }
        });
    }

    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column(); panel.setName("runewarden.dps");
        JPanel headline = SidebarUi.section("COMBAT WORKBENCH"); panel.add(headline);
        dps = new JLabel("—"); dps.setName("normalDpsValue"); dps.setForeground(SidebarUi.GOLD); dps.setFont(GuideText.FONT.deriveFont(Font.BOLD, 40f));
        dps.setAlignmentX(Component.LEFT_ALIGNMENT); dps.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56)); headline.add(dps);
        targetText = SidebarUi.heading("No target"); headline.add(targetText);
        currentFacts = SidebarUi.label(""); headline.add(currentFacts); currentStatus = SidebarUi.label(""); headline.add(currentStatus);
        headline.add(SidebarUi.button("Inspect equipped weapon", () -> { if (reading.weaponId > 0) openItem.accept(reading.weaponId); }));
        JPanel target = SidebarUi.column(); targetBody=target; targetQuery = new JTextField(); targetQuery.setName("dpsTargetSearch"); target.add(SidebarUi.field("Monster or exact NPC ID", targetQuery));
        targetResults = SidebarUi.column(); target.add(targetResults); delayed(targetQuery, this::searchTargets);
        target.add(SidebarUi.button("Use interacting monster", () -> edit(() -> selectTargetNow(-1))));
        keepTarget = new JCheckBox("Keep last interacting target"); keepTarget.setOpaque(false); keepTarget.setForeground(SidebarUi.TEXT);
        keepTarget.addActionListener(event -> { if (!refreshing) { boolean keep = keepTarget.isSelected(); edit(() -> { holdTarget = keep; changed(""); }); } }); target.add(keepTarget);
        panel.add(disclosure("target", "Choose target", target));
        body = SidebarUi.column(); panel.add(body); statusText = SidebarUi.label(""); panel.add(statusText);
        panel.addComponentListener(new ComponentAdapter() { @Override public void componentShown(ComponentEvent event) { invalidate(); } });
        render(); invalidate(); return panel;
    }
    private void searchTargets()
    {
        targetResults.removeAll(); String query = targetQuery.getText().trim();
        if (!query.isEmpty())
        {
            List<DpsReferenceCatalog.Target> found = references.searchVariants(query); int count = 0;
            for (DpsReferenceCatalog.Target target : found)
            { if (count++ >= 8) break; targetResults.add(SidebarUi.wrappedButton(targetLabel(target) + " · #" + target.id, () -> edit(() -> selectTargetNow(target.id, target.version == null ? "" : target.version)))); }
            if (found.size() > 8) targetResults.add(SidebarUi.label(found.size() + " variants · refine search"));
            if (found.isEmpty()) targetResults.add(SidebarUi.label("No exact target variants"));
        }
        targetResults.revalidate(); targetResults.repaint();
    }
    private void invalidate()
    { lastFingerprint = Long.MIN_VALUE; long ticket = epoch; clientThread.invokeLater(() -> { if (started && ticket == epoch) refreshNow(); }); }
    private void publish()
    {
        long ticket = epoch; String owner = profile;
        if (panel != null) SwingUtilities.invokeLater(() -> { if (ticket == epoch && (owner == null || owner.equals(profiles.getRSProfileKey()))) render(); });
    }
    private void render()
    {
        if (panel == null) return;
        Reading current = reading; refreshing = true;
        try
        {
            targetText.setText(current.target); dps.setText(current.current.supported ? format(current.current.dps, 2) : "—");
            currentFacts.setText(current.current.supported ? "Current attack · " + current.current.mode + "\n" + compact(current.current) : "");
            currentStatus.setText(current.current.supported ? "" : current.current.reason); keepTarget.setSelected(holdTarget);
            body.removeAll(); buildRanking(current); buildSelected(current); buildScenario(current); buildManagement(current);
            statusText.setText(current.status); renderedEpoch = epoch; panel.revalidate(); panel.repaint();
        }
        finally { refreshing = false; }
    }
    private void buildRanking(Reading current)
    {
        JPanel ranking = SidebarUi.section("SETUP RANKING"); body.add(ranking);
        int pages = Math.max(1, (current.ranking.size() + 3) / 4); rankPage = Math.min(rankPage, pages - 1);
        for (int i = rankPage * 4; i < Math.min(current.ranking.size(), rankPage * 4 + 4); i++)
        {
            Setup setup = current.ranking.get(i);
            JPanel row = SidebarUi.column();
            JButton button = SidebarUi.wrappedButton((i + 1) + ". " + setup.name, () -> edit(() -> selectSetupNow(setup.id)));
            button.setName("dpsRank_" + setup.id); row.add(button);
            String summary = setup.result.supported ? format(setup.result.dps, 2) + (setup.result.specialEnergy == null ? " DPS · " : " burst DPS · ") + format(setup.result.accuracy * 100, 1) + "% accuracy\nMax " + setup.result.maxHit : "Unrated";
            summary += " · " + (setup.purchase.observedOwned ? "Observed owned" : "Theory equipment");
            if (setup.purchase.overBudget) summary += "\nAbove purchase budget";
            row.add(SidebarUi.label(summary)); ranking.add(row);
        }
        if (pages > 1)
        {
            JButton previous = SidebarUi.button("‹", () -> { rankPage = Math.max(0, rankPage - 1); render(); });
            JButton next = SidebarUi.button("›", () -> { rankPage = Math.min(pages - 1, rankPage + 1); render(); });
            JLabel page = new JLabel((rankPage + 1) + " / " + pages); page.setForeground(SidebarUi.TEXT); page.setHorizontalAlignment(JLabel.CENTER);
            ranking.add(SidebarUi.row(previous, page, next));
        }
        if (current.ranking.isEmpty()) ranking.add(SidebarUi.label("Choose a target and equipment"));
    }
    private void buildSelected(Reading current)
    {
        Setup selected = null; for (Setup setup : current.setups) if (setup.id.equals(selectedId)) selected = setup;
        if (selected == null) return; final Setup setup = selected;
        JPanel card = SidebarUi.section(setup.name); body.add(card);
        card.add(SidebarUi.label(setup.result.supported ? facts(setup.result) : setup.result.reason));
        if (setup.result.supported && setup.result.specialEnergy == null && current.current.supported && current.current.dps > 0 && !CURRENT.equals(setup.id))
            card.add(SidebarUi.heading(String.format(Locale.ROOT, "%+.1f%% vs current attack", (setup.result.dps / current.current.dps - 1) * 100)));
        card.add(SidebarUi.label(purchaseFacts(setup.purchase)));
        JPanel inputs = SidebarUi.column();
        JComboBox<String> styles = combo(); styles.addItem("Best supported mode");
        for (DpsReferenceCatalog.Mode mode : setup.modes) styles.addItem(mode.label());
        if (setup.style != null) styles.setSelectedItem(setup.style);
        styles.addActionListener(event -> { if (!refreshing) { String style = styles.getSelectedIndex() == 0 ? null : (String) styles.getSelectedItem(); edit(() -> choiceNow(setup.id, style, setup.spell, setup.dart)); } });
        inputs.add(SidebarUi.field("Attack style", styles));
        JCheckBox special = new JCheckBox("Compare one special attack", setup.special); special.setFont(GuideText.FONT); special.setForeground(SidebarUi.TEXT); special.setOpaque(false);
        special.addActionListener(event -> { if(!refreshing){ boolean enabled=special.isSelected();edit(()->specialNow(setup.id,enabled));} }); inputs.add(special);
        if (spells != null)
        {
            JComboBox<Object> spell = combo(); spell.addItem("Current spell / powered attack"); for (CombatSpellCatalog.Spell value : spells.spells()) spell.addItem(value);
            if (setup.spell != null) spell.setSelectedItem(spells.named(setup.spell));
            spell.addActionListener(event -> { if (!refreshing) { Object value = spell.getSelectedItem(); String name = value instanceof CombatSpellCatalog.Spell ? ((CombatSpellCatalog.Spell) value).name : null; edit(() -> choiceNow(setup.id, name == null ? setup.style : "Spell · magic · Manual Cast", name, setup.dart)); } });
            inputs.add(SidebarUi.field("Planned spell", spell));
        }
        JComboBox<CombatCalculatorTool.Dart> dart = combo(); for (CombatCalculatorTool.Dart value : CombatCalculatorTool.Dart.values()) dart.addItem(value);
        dart.setSelectedItem(CombatCalculatorTool.Dart.named(setup.dart));
        dart.addActionListener(event -> { if (!refreshing) { CombatCalculatorTool.Dart value = (CombatCalculatorTool.Dart) dart.getSelectedItem(); edit(() -> choiceNow(setup.id, setup.style, setup.spell, value == CombatCalculatorTool.Dart.UNKNOWN ? null : value.label)); } });
        inputs.add(SidebarUi.field("Blowpipe darts", dart));
        JComboBox<TaskState> tasks = combo(); for (TaskState value : TaskState.values()) tasks.addItem(value); tasks.setSelectedItem(task);
        tasks.addActionListener(event -> { if (!refreshing) { TaskState value = (TaskState) tasks.getSelectedItem(); edit(() -> { task = value; changed("Slayer context updated"); }); } }); inputs.add(SidebarUi.field("Slayer task bonus", tasks));
        if (setup.result.supported && !setup.result.mechanics.isEmpty()) inputs.add(SidebarUi.label(String.join("\n", setup.result.mechanics)));
        card.add(disclosure("inputs", "Style, spell & context", inputs));
        JPanel equipment = SidebarUi.column();
        for (Map.Entry<Integer, Integer> entry : setup.plan.equipment().entrySet())
        {
            int id = entry.getValue(); String name = itemName(id);
            equipment.add(SidebarUi.wrappedButton(slotName(entry.getKey()) + " · " + name, () -> openItem.accept(id)));
        }
        card.add(disclosure("equipment", "Equipment & sources", equipment));
        if (setup.sandbox) card.add(disclosure("sandbox", "Edit sandbox equipment", sandboxEditor(setup)));
    }
    private JPanel sandboxEditor(Setup setup)
    {
        JPanel edit = SidebarUi.column(); JTextField name = draft("name_" + setup.id, setup.name); edit.add(SidebarUi.field("Set name", name));
        edit.add(SidebarUi.button("Rename set", () -> { String value = name.getText(); edit(() -> { for (int i = 0; i < state.builds.size(); i++) { LoadoutPlan old = state.builds.get(i); if (old.id.equals(setup.id)) state.builds.set(i, new LoadoutPlan(old.id, value, old.requirements, old.spellbook)); } changed("Set renamed"); }); }));
        JComboBox<Integer> slots = combo(); for (int slot : new int[] {0, 1, 2, 3, 4, 5, 7, 9, 10, 12, 13}) slots.addItem(slot);
        slots.setRenderer(new javax.swing.DefaultListCellRenderer() { @Override public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus)
            { return super.getListCellRendererComponent(list, value instanceof Integer ? slotName((Integer) value) : "", index, selected, focus); } });
        edit.add(SidebarUi.field("Equipment slot", slots)); JTextField quantity = draft("quantity_" + setup.id, "1"); edit.add(SidebarUi.field("Quantity", quantity));
        itemQuery = draft("item_" + setup.id, ""); edit.add(SidebarUi.field("Find replacement item / ID", itemQuery)); itemResults = SidebarUi.column(); edit.add(itemResults);
        JTextField localQuery = itemQuery; JPanel localResults = itemResults;
        delayed(localQuery, () ->
        {
            localResults.removeAll(); String query = localQuery.getText().trim(); List<ItemKnowledgeCatalog.ItemRef> found = itemNames == null ? List.of() : itemNames.search(query);
            int count = 0; if (!query.isEmpty()) for (ItemKnowledgeCatalog.ItemRef item : found)
            {
                if (count++ >= 6) break;
                localResults.add(SidebarUi.wrappedButton(item.name + " · #" + item.id, () -> { int slot = (Integer) slots.getSelectedItem(); String amount = quantity.getText(); edit(() -> replaceSandboxNow(setup.id, slot, item.id, positive(amount))); }));
            }
            if (!query.isEmpty() && query.matches("[1-9][0-9]{0,5}"))
            { int id = Integer.parseInt(query); localResults.add(SidebarUi.button("Use item #" + id, () -> { int slot = (Integer) slots.getSelectedItem(); String amount = quantity.getText(); edit(() -> replaceSandboxNow(setup.id, slot, id, positive(amount))); })); }
            localResults.revalidate(); localResults.repaint();
        });
        edit.add(SidebarUi.button("Clear selected slot", () -> { int slot = (Integer) slots.getSelectedItem(); edit(() -> replaceSandboxNow(setup.id, slot, -1, 1)); }));
        edit.add(SidebarUi.button("Delete sandbox set", () -> edit(() -> { state.builds.removeIf(build -> build.id.equals(setup.id)); state.choices.removeIf(value -> value.id.equals(setup.id)); selectedId = CURRENT; state.selected = CURRENT; changed("Sandbox removed"); })));
        return edit;
    }
    private void buildScenario(Reading current)
    {
        JPanel scenario = SidebarUi.column();
        if (current.targetFacts != null)
        {
            JTextField hp = draft("scenario_hp", current.hpOverride == null ? "" : current.hpOverride.toString());
            JTextField defence = draft("scenario_defence", current.defenceOverride == null ? "" : current.defenceOverride.toString());
            JTextField magic = draft("scenario_magic", current.magicOverride == null ? "" : current.magicOverride.toString());
            DpsReferenceCatalog.Target original = references.variant(current.targetFacts.id, state.targetVersion);
            if(original!=null)scenario.add(SidebarUi.label("Wiki base · HP " + original.hitpoints + " · Defence " + original.defenceLevel + " · Magic " + original.magicLevel));
            scenario.add(SidebarUi.field("Scenario HP · blank = base", hp)); scenario.add(SidebarUi.field("Scenario Defence level", defence)); scenario.add(SidebarUi.field("Scenario Magic level", magic));
            scenario.add(SidebarUi.button("Apply scenario", () -> { String a = hp.getText(), b = defence.getText(), c = magic.getText(); edit(() -> targetScenarioNow(optional(a), optional(b), optional(c))); }));
            scenario.add(SidebarUi.button("Restore Wiki base", () -> edit(() -> targetScenarioNow(null, null, null))));
            scenario.add(SidebarUi.label("Manual scenario values · current levels and active prayers. Full-HP TTK includes misses and overkill; encounter movement adds time."));
        }
        if(current.targetFacts!=null)scenario.add(defenceScenario(current));
        body.add(disclosure("scenario", "Target & model", scenario));
    }
    private JPanel defenceScenario(Reading current)
    {
        JPanel form=SidebarUi.column(); DpsTargetScenario.Inputs saved=current.scenario==null?new DpsTargetScenario.Inputs():current.scenario;
        JComboBox<String> phase=combo(); phase.addItem("Base encounter");
        if("Araxxor".equals(current.targetFacts.name)){phase.addItem("Araxxor normal");phase.addItem("Araxxor enraged");}
        if("Vardorvis".equals(current.targetFacts.name))phase.addItem("Vardorvis HP snapshot");
        if(saved.phase!=null)phase.setSelectedItem(saved.phase); form.add(SidebarUi.field("Encounter phase",phase));
        JTextField phaseHp=draft("phaseHp",saved.phaseHp==null?"":saved.phaseHp.toString());
        if("Vardorvis".equals(current.targetFacts.name))form.add(SidebarUi.field("Vardorvis current HP",phaseHp));
        JComboBox<String> curse=combo();curse.addItem("No curse");curse.addItem("Vulnerability");curse.addItem("Accursed sceptre");curse.setSelectedIndex(saved.accursed?2:saved.vulnerability?1:0);form.add(SidebarUi.field("Applied curse",curse));
        JTextField maul=draft("drain_maul",Integer.toString(saved.maul)),dwh=draft("drain_dwh",Integer.toString(saved.warhammer)),bgs=draft("drain_bgs",Integer.toString(saved.bgs));
        form.add(SidebarUi.field("Successful Elder maul specials",maul));form.add(SidebarUi.field("Successful Dragon warhammer specials",dwh));form.add(SidebarUi.field("Bandos godsword damage",bgs));
        JPanel extra=SidebarUi.column();
        JTextField arc=draft("drain_arc",Integer.toString(saved.arclight)),ember=draft("drain_ember",Integer.toString(saved.emberlight)),tonal=draft("drain_tonal",Integer.toString(saved.tonalztic)),seercull=draft("drain_seercull",Integer.toString(saved.seercull)),ayak=draft("drain_ayak",Integer.toString(saved.ayak));
        extra.add(SidebarUi.field("Successful Arclight specials",arc));extra.add(SidebarUi.field("Successful Emberlight specials",ember));extra.add(SidebarUi.field("Successful Tonalztics hits",tonal));extra.add(SidebarUi.field("Seercull damage",seercull));extra.add(SidebarUi.field("Eye of Ayak reduction",ayak));
        form.add(disclosure("otherDrains","Other defence reductions",extra));
        form.add(SidebarUi.button("Apply phase & successful drains",()->{
            String phaseName=phase.getSelectedIndex()==0?null:(String)phase.getSelectedItem();int curseIndex=curse.getSelectedIndex();
            DpsTargetScenario.Inputs next=new DpsTargetScenario.Inputs();
            try{next.phase=phaseName;next.phaseHp=optional(phaseHp.getText());next.maul=nonnegative(maul.getText());next.warhammer=nonnegative(dwh.getText());next.bgs=nonnegative(bgs.getText());next.arclight=nonnegative(arc.getText());next.emberlight=nonnegative(ember.getText());next.tonalztic=nonnegative(tonal.getText());next.seercull=nonnegative(seercull.getText());next.ayak=nonnegative(ayak.getText());next.vulnerability=curseIndex==1;next.accursed=curseIndex==2;}
            catch(IllegalArgumentException invalid){statusText.setText(invalid.getMessage());return;}
            edit(()->effectsScenarioNow(next));
        }));
        form.add(SidebarUi.button("Clear phase & drains",()->{drafts.keySet().removeIf(k->k.startsWith("drain_")||k.equals("phaseHp"));edit(()->effectsScenarioNow(null));}));
        return disclosure("drains","Phase & defence reductions",form);
    }
    private static int nonnegative(String value){Integer n=optional(value);if(n==null)return 0;if(n<0)throw new IllegalArgumentException("Enter zero or a positive drain value");return n;}
    private void buildManagement(Reading current)
    {
        JPanel manage = SidebarUi.column(); JTextField name = draft("new_sandbox", ""); manage.add(SidebarUi.field("New sandbox name", name));
        manage.add(SidebarUi.button("Duplicate selected equipment", () -> { String value = name.getText(); edit(() -> createSandboxNow(value)); }));
        manage.add(SidebarUi.button("Open saved loadouts", () -> openLoadouts.run()));
        JTextField budget = draft("budget", current.budget == 0 ? "" : Long.toString(current.budget)); manage.add(SidebarUi.field("Purchase budget · gp", budget));
        manage.add(SidebarUi.button("Set budget", () -> { String value = budget.getText().trim().replace(",", ""); edit(() -> budgetNow(value.isEmpty() ? 0 : Long.parseLong(value))); }));
        for (Setup setup : current.setups)
        {
            if (CURRENT.equals(setup.id)) continue;
            manage.add(SidebarUi.heading(setup.name)); JCheckBox include = new JCheckBox("Include in ranking", setup.enabled); include.setFont(GuideText.FONT); include.setForeground(SidebarUi.TEXT); include.setOpaque(false);
            include.addActionListener(event -> { if (!refreshing) { boolean enabled = include.isSelected(); edit(() -> { if (enabled && activeCount() >= DpsWorkbenchPlan.MAX_ACTIVE) { changed("Compare up to 8 sets at once"); return; } editableChoice(setup.id).enabled = enabled; changed("Comparison sets updated"); }); } });
            manage.add(include); manage.add(SidebarUi.wrappedButton("Inspect " + setup.name, () -> edit(() -> selectSetupNow(setup.id))));
        }
        body.add(disclosure("management", "Sets & budget", manage));
    }
    private JPanel disclosure(String key, String title, JPanel content)
    {
        JPanel wrapper = SidebarUi.column(); boolean open = Boolean.TRUE.equals(disclosures.get(key)); content.setVisible(open);
        JButton toggle = SidebarUi.button((open ? "▾ " : "▸ ") + title, () -> { });
        toggle.addActionListener(event -> { boolean visible = !content.isVisible(); disclosures.put(key, visible); content.setVisible(visible); toggle.setText((visible ? "▾ " : "▸ ") + title); wrapper.revalidate(); wrapper.repaint(); });
        wrapper.add(toggle); wrapper.add(content); return wrapper;
    }
    private JTextField draft(String key, String initial)
    {
        JTextField field = new JTextField(drafts.getOrDefault(key, initial));
        field.getDocument().addDocumentListener(new DocumentListener() { private void keep() { drafts.put(key, field.getText()); } public void insertUpdate(DocumentEvent event) { keep(); } public void removeUpdate(DocumentEvent event) { keep(); } public void changedUpdate(DocumentEvent event) { keep(); } });
        return field;
    }
    private String itemName(int id)
    { if (itemNames != null) { ItemKnowledgeCatalog.ItemRef item = itemNames.item(id); if (item != null) return item.name; } return "Item #" + id; }
    private static String purchaseFacts(DpsWorkbenchPlan.Purchase purchase)
    {
        String result = purchase.label();
        if (!purchase.unobserved.isEmpty()) result += "\nUnobserved " + purchase.unobserved.size() + " item types · priced portion " + gp(purchase.unobservedCost) + " gp";
        if (purchase.pricedTotal > 0) result += "\nEquipment priced portion " + gp(purchase.pricedTotal) + " gp";
        if (purchase.unknownPrices > 0) result += "\n" + purchase.unknownPrices + " unpriced / untradeable item types";
        if (purchase.overBudget) result += "\nAbove purchase budget";
        return result;
    }
    private static String targetLabel(DpsReferenceCatalog.Target target)
    { return target.name + (target.version == null || target.version.isEmpty() ? "" : " · " + target.version) + " · " + target.hitpoints + " HP"; }
    private static String compact(DpsCalculator.Result result)
    { return "Accuracy " + format(result.accuracy * 100, 1) + "% · " + result.attackTicks + " ticks\nMax " + result.maxHit + " · Expected hit " + format(result.expectedHit, 2); }
    private static String facts(DpsCalculator.Result result)
    {
        return (result.specialEnergy == null ? "DPS " : "Burst DPS ") + format(result.dps, 2) + "\n" + result.mode + "\n" + compact(result)
            + "\nAttack cycle " + format(result.attackTicks * .6, 1) + "s"
            + (result.specialEnergy != null ? "\nBurst maximum " + result.totalMaxHit + "\nSpecial energy " + result.specialEnergy + "%\nDamage per 100% energy " + format(result.expectedHit*100/result.specialEnergy,2)
                : result.snapshotOnly ? "\nPhase snapshot" : "\nFull-HP TTK " + (result.ttkSeconds == null ? "∞" : format(result.ttkSeconds, 1) + "s"));
    }
    private static <T> JComboBox<T> combo()
    { JComboBox<T> value = new JComboBox<>(); value.setFont(GuideText.FONT); value.setForeground(SidebarUi.TEXT); value.setBackground(SidebarUi.CARD); value.setMaximumRowCount(8); value.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30)); return value; }
    private static void delayed(JTextField field, Runnable action)
    {
        javax.swing.Timer delay = new javax.swing.Timer(180, event -> action.run()); delay.setRepeats(false);
        field.getDocument().addDocumentListener(new DocumentListener() { public void insertUpdate(DocumentEvent event) { delay.restart(); } public void removeUpdate(DocumentEvent event) { delay.restart(); } public void changedUpdate(DocumentEvent event) { delay.restart(); } });
    }
    private static Integer optional(String text)
    { if (text == null || text.trim().isEmpty()) return null; try { return Integer.valueOf(text.trim()); } catch (NumberFormatException invalid) { throw new IllegalArgumentException("Enter whole-number target values"); } }
    private static int positive(String text)
    { Integer value = optional(text); if (value == null || value <= 0) throw new IllegalArgumentException("Enter a positive item quantity"); return value; }
    private static String slotName(int slot)
    { switch (slot) { case 0: return "Head"; case 1: return "Cape"; case 2: return "Neck"; case 3: return "Weapon"; case 4: return "Body"; case 5: return "Shield"; case 7: return "Legs"; case 9: return "Hands"; case 10: return "Feet"; case 12: return "Ring"; case 13: return "Ammo"; default: return "Slot " + slot; } }
    private static String gp(long value) { return String.format(Locale.ROOT, "%,d", value); }
    private static String format(double value, int places) { return String.format(Locale.ROOT, "%." + places + "f", value); }
}
