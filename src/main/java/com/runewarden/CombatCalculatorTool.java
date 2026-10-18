/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
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
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.util.Text;

/** Live worn-equipment calculator. Hidden inputs are learned only from explicit game observations. */
@Singleton
final class CombatCalculatorTool
{
    enum Dart
    {
        UNKNOWN("Automatic", null), BRONZE("Bronze", 1), IRON("Iron", 3), STEEL("Steel", 4),
        BLACK("Black", 6), MITHRIL("Mithril", 7), ADAMANT("Adamant", 10), RUNE("Rune", 14),
        AMETHYST("Amethyst", 28), DRAGON("Dragon", 35);
        final String label;
        final Integer strength;
        Dart(String label, Integer strength) { this.label = label; this.strength = strength; }
        @Override public String toString() { return label; }
        static Dart named(String text)
        {
            if (text == null) return UNKNOWN;
            String clean = text.toLowerCase(Locale.ROOT).replaceAll("\\s+darts?$", "").trim();
            for (Dart dart : values()) if (dart.label.equalsIgnoreCase(clean)) return dart;
            return UNKNOWN;
        }
    }
    private enum TombsMode
    {
        AUTO("Automatic"), OUTSIDE("Outside ToA"), INSIDE("Inside ToA");
        final String text;
        TombsMode(String text) { this.text = text; }
        @Override public String toString() { return text; }
    }
    private static final Pattern DART_CHECK = Pattern.compile("(?i)^Darts:\\s*(Bronze|Iron|Steel|Black|Mithril|Adamant|Rune|Amethyst|Dragon) darts?\\s*x\\s*[0-9,]+[.\\s].*");
    // OSRS Wiki DPS calculator constants.ts, verified 2026-10-16. Exact target IDs, no name inference.
    private static final Set<Integer> TOMBS_TARGETS = Set.of(11719, 11721, 11724, 11725, 11726,
        11730, 11732, 11733, 11750, 11751, 11752, 11753, 11754, 11755, 11756, 11757, 11758,
        11761, 11762, 11763, 11764, 11778, 11779, 11780, 11789, 11790, 11791, 11792, 11793,
        11794, 11795, 11796, 11797, 11798, 11799);
    private final Client client;
    private final ClientThread clientThread;
    private final ItemManager items;
    private final MobKnowledgeCatalog monsters;
    private final CombatSpellCatalog spells;
    private final ConfigManager profiles;
    private String profile;
    private volatile CombatSpellCatalog.Spell selectedSpell;
    private volatile int spellWeapon = -1;
    private volatile Dart selectedDart = Dart.UNKNOWN;
    private volatile TombsMode tombsMode = TombsMode.AUTO;
    private volatile boolean started;
    private volatile long generation;
    private int lastWeapon = -1;
    private int checkedWeapon = -1;
    private int checkTick = -100;
    private Dart observedDart = Dart.UNKNOWN;
    private JPanel panel, missing, advanced, spellField, dartField, tombsField;
    private JLabel value;
    private GuideText mode, weaponText, equipmentText, prayerText, status, calculation;
    private javax.swing.JButton advancedButton, missingInputButton;
    private JComboBox<Object> spellChoice;
    private JComboBox<Dart> dartChoice;
    private JComboBox<TombsMode> tombsChoice;
    private boolean advancedOpen;
    private boolean synchronizing;
    private String lastText;

    @Inject CombatCalculatorTool(Client client, ClientThread clientThread, ItemManager items,
        MobKnowledgeCatalog monsters, CombatSpellCatalog spells, ConfigManager profiles)
    {
        this.client = client; this.clientThread = clientThread; this.items = items;
        this.monsters = monsters; this.spells = spells; this.profiles = profiles;
    }
    void start() { generation++; started = true; }
    void stop() { started = false; clear(); }
    void clear()
    {
        generation++; selectedSpell = null; spellWeapon = -1; selectedDart = Dart.UNKNOWN;
        tombsMode = TombsMode.AUTO; lastText = null; profile = null;
        lastWeapon = -1; checkedWeapon = -1; observedDart = Dart.UNKNOWN;
        final long ticket = generation;
        SwingUtilities.invokeLater(() ->
        {
            if (ticket != generation) return;
            synchronizing = true;
            if (spellChoice != null) spellChoice.setSelectedIndex(0);
            if (dartChoice != null) dartChoice.setSelectedIndex(0);
            if (tombsChoice != null) tombsChoice.setSelectedIndex(0);
            synchronizing = false;
            if (value != null) { value.setText("—"); weaponText.setText("Equipment unavailable"); equipmentText.setText(""); prayerText.setText(""); status.setText(""); missing.setVisible(false); setAdvanced(false); }
        });
    }
    void onGameTick()
    {
        if (!started) return;
        checkProfile();
        if (panel != null && panel.isShowing()) refresh();
    }
    private void checkProfile()
    {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        String current = profiles.getRSProfileKey();
        if (current != null && !current.equals(profile)) { clear(); profile = current; }
    }

    /** Client-thread only. The dashboard uses the same live calculation as the sidebar. */
    CurrentMaxHit.Reading read()
    { return CurrentMaxHit.read(client, items, inputs()); }

    /** Current public/explicit calculator inputs, shared with the normal-DPS comparison. */
    CurrentMaxHit.Inputs inputs()
    {
        checkProfile();
        Weapon weapon = weapon();
        if (weapon.id != lastWeapon)
        {
            observedDart = Dart.UNKNOWN; checkedWeapon = -1; lastWeapon = weapon.id;
            if (spellWeapon != weapon.id) selectedSpell = null;
        }
        MobKnowledgeCatalog.Entry target = null;
        int targetId = -1;
        Actor actor = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getInteracting();
        if (actor instanceof NPC)
        {
            NPC npc = (NPC) actor;
            NPCComposition transformed = npc.getTransformedComposition();
            targetId = transformed == null ? npc.getId() : transformed.getId();
            target = monsters.exact(targetId);
        }
        CombatSpellCatalog.Spell spell = selectedSpell;
        if (spell == null && client.getGameState() == GameState.LOGGED_IN)
        {
            int spellObject = client.getVarpValue(VarPlayerID.AUTOCAST_SPELL_OBJ);
            spell = spells.fromSpellObject(spellObject);
            if (spell == null && spellObject > 0)
            {
                ItemComposition definition = items.getItemComposition(spellObject);
                if (definition != null) spell = spells.named(definition.getName());
            }
        }
        boolean inTombs = tombsMode == TombsMode.INSIDE || tombsMode == TombsMode.AUTO && isTombsTarget(targetId);
        boolean isShadow = weapon.lower().contains("tumeken's shadow");
        Dart dart = selectedDart != Dart.UNKNOWN ? selectedDart : observedDart;
        return new CurrentMaxHit.Inputs(spell, dart.strength,
            selectedSpell != null && spellWeapon == weapon.id, isShadow && inTombs, target,
            selectedDart == Dart.UNKNOWN && observedDart != Dart.UNKNOWN, tombsMode == TombsMode.AUTO);
    }
    static boolean isTombsTarget(int npcId) { return TOMBS_TARGETS.contains(npcId); }

    /** A Check must target the equipped item; inventory copies never establish the worn pipe's load. */
    void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!started || event == null) return;
        String option = Text.removeTags(event.getMenuOption() == null ? "" : event.getMenuOption());
        checkProfile(); Weapon weapon = weapon();
        if (!weapon.lower().contains("blowpipe")) return;
        int group = event.getParam1() >>> 16;
        boolean wornInterface = group == InterfaceID.WORNITEMS || group == InterfaceID.EQUIPMENT || group == InterfaceID.EQUIPMENT_SIDE;
        if ("Check".equalsIgnoreCase(option) && event.getItemId() == weapon.id && wornInterface)
        { checkedWeapon = weapon.id; lastWeapon = weapon.id; checkTick = client.getTickCount(); }
        else if ((event.getItemId() == weapon.id || Text.removeTags(event.getMenuTarget() == null ? "" : event.getMenuTarget()).toLowerCase(Locale.ROOT).contains("blowpipe"))
            && ("Unload".equalsIgnoreCase(option) || "Use".equalsIgnoreCase(option)))
        { observedDart = Dart.UNKNOWN; checkedWeapon = -1; }
    }
    void onChatMessage(ChatMessage event)
    {
        if (!started || event == null || !(event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM)) return;
        String text = Text.removeTags(event.getMessage() == null ? "" : event.getMessage());
        Weapon weapon = weapon();
        if (checkedWeapon == weapon.id && weapon.lower().contains("blowpipe") && client.getTickCount() - checkTick >= 0 && client.getTickCount() - checkTick <= 5)
        {
            Dart detected = dartFromCheck(text);
            if (detected != Dart.UNKNOWN || text.startsWith("Darts: None"))
            { observedDart = detected; lastWeapon = weapon.id; checkedWeapon = -1; queueRefresh(); return; }
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("blowpipe") && (lower.contains("load") || lower.contains("run out")))
        { observedDart = Dart.UNKNOWN; checkedWeapon = -1; queueRefresh(); }
    }
    static Dart dartFromCheck(String message)
    {
        Matcher match = DART_CHECK.matcher(Text.removeTags(message == null ? "" : message));
        return match.matches() ? Dart.named(match.group(1)) : Dart.UNKNOWN;
    }

    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column(); panel.setName("runewarden.maxhit");
        JPanel result = SidebarUi.section("MAX HIT");
        value = new JLabel("—"); value.setName("maxHitValue"); value.setForeground(SidebarUi.GOLD); value.setFont(GuideText.FONT.deriveFont(Font.BOLD, 40f));
        result.add(value); mode = SidebarUi.label("Live equipment"); result.add(mode); panel.add(result);
        JPanel equipment = SidebarUi.section("EQUIPPED");
        weaponText = SidebarUi.heading("Equipment unavailable"); weaponText.setName("maxHitWeapon"); equipment.add(weaponText);
        equipmentText = SidebarUi.label(""); equipment.add(equipmentText);
        prayerText = SidebarUi.label(""); equipment.add(prayerText); panel.add(equipment);
        missing = SidebarUi.section(null); status = SidebarUi.label(""); missing.add(status);
        missingInputButton = SidebarUi.button("Set missing input", () -> setAdvanced(true)); missing.add(missingInputButton); missing.setVisible(false); panel.add(missing);
        advancedButton = SidebarUi.button("Calculation details ▸", () -> setAdvanced(!advancedOpen)); panel.add(advancedButton);
        advanced = SidebarUi.section("CALCULATION"); calculation = SidebarUi.label(""); advanced.add(calculation);
        spellChoice = new JComboBox<>(); spellChoice.addItem("Automatic");
        for (CombatSpellCatalog.Spell spell : spells.spells()) spellChoice.addItem(spell);
        spellChoice.setName("maxHitSpell"); style(spellChoice);
        spellChoice.addActionListener(event ->
        {
            if (synchronizing) return;
            Object selected = spellChoice.getSelectedItem(); selectedSpell = selected instanceof CombatSpellCatalog.Spell ? (CombatSpellCatalog.Spell) selected : null;
            clientThread.invokeLater(() -> { spellWeapon = weapon().id; if (started) refresh(); });
        });
        spellField = SidebarUi.field("Spell override", spellChoice); advanced.add(spellField);
        dartChoice = new JComboBox<>(Dart.values()); dartChoice.setName("maxHitDart"); style(dartChoice);
        dartChoice.addActionListener(event -> { if (!synchronizing) { selectedDart = (Dart) dartChoice.getSelectedItem(); queueRefresh(); } });
        dartField = SidebarUi.field("Loaded dart override", dartChoice); advanced.add(dartField);
        tombsChoice = new JComboBox<>(TombsMode.values()); tombsChoice.setName("maxHitToa"); style(tombsChoice);
        tombsChoice.addActionListener(event -> { if (!synchronizing) { tombsMode = (TombsMode) tombsChoice.getSelectedItem(); queueRefresh(); } });
        tombsField = SidebarUi.field("Shadow bonus", tombsChoice); advanced.add(tombsField);
        advanced.add(SidebarUi.button("Use automatic inputs", () ->
        {
            synchronizing = true; selectedSpell = null; spellWeapon = -1; selectedDart = Dart.UNKNOWN; tombsMode = TombsMode.AUTO;
            spellChoice.setSelectedIndex(0); dartChoice.setSelectedIndex(0); tombsChoice.setSelectedIndex(0); synchronizing = false; queueRefresh();
        }));
        advanced.setVisible(false); panel.add(advanced);
        panel.addComponentListener(new ComponentAdapter() { @Override public void componentShown(ComponentEvent event) { queueRefresh(); } });
        queueRefresh(); return panel;
    }
    private void setAdvanced(boolean open)
    { advancedOpen = open; advanced.setVisible(open); advancedButton.setText(open ? "Hide details ▾" : "Calculation details ▸"); panel.revalidate(); panel.repaint(); }
    private void queueRefresh()
    { final long ticket = generation; clientThread.invokeLater(() -> { if (started && ticket == generation && panel != null) refresh(); }); }
    private void refresh()
    {
        CurrentMaxHit.Reading reading = read();
        Weapon weapon = weapon(); String style = CurrentMaxHit.attackStyle(client);
        boolean casting = style != null && (style.contains("casting") || client.getVarpValue(VarPlayerID.COM_MODE) >= 4 && "defensive".equals(style));
        boolean magic = CurrentMaxHit.poweredBase(weapon.lower(), Math.max(0, client.getBoostedSkillLevel(Skill.MAGIC))) != null || casting || selectedSpell != null;
        boolean ranged = !magic && style != null && (style.equals("ranging") || style.equals("longrange"));
        Skill skill = magic ? Skill.MAGIC : ranged ? Skill.RANGED : Skill.STRENGTH;
        int strength = 0, rangedStrength = 0, magicDamage = 0, count = 0;
        ItemContainer worn = CurrentMaxHit.worn(client);
        boolean selfAmmo = weapon.lower().contains("blowpipe") || weapon.lower().contains("crystal bow") || weapon.lower().contains("faerdhinen")
            || weapon.lower().contains("dart") || weapon.lower().contains("knife") || weapon.lower().contains("knives") || weapon.lower().contains("thrownaxe");
        if (worn != null && worn.getItems() != null)
            for (int slot = 0; slot < worn.getItems().length; slot++)
            {
                Item item = worn.getItems()[slot];
                if (item == null || item.getId() < 0 || item.getQuantity() <= 0) continue;
                count++; ItemStats stats = items.getItemStats(item.getId()); ItemEquipmentStats bonuses = stats == null ? null : stats.getEquipment();
                if (bonuses != null) { strength += bonuses.getStr(); if (!selfAmmo || slot != EquipmentInventorySlot.AMMO.getSlotIdx()) rangedStrength += bonuses.getRstr(); magicDamage += Math.round(bonuses.getMdmg() * 10); }
            }
        Dart loaded = selectedDart == Dart.UNKNOWN ? observedDart : selectedDart;
        if (weapon.lower().contains("blowpipe") && loaded.strength != null) rangedStrength += loaded.strength;
        String styleLabel = ranged && "ranging".equals(style) ? client.getVarpValue(VarPlayerID.COM_MODE) == 0 ? "Accurate" : "Rapid" : title(style);
        String equipment = styleLabel + "\n" + title(skill.getName()) + " " + client.getBoostedSkillLevel(skill) + "/" + client.getRealSkillLevel(skill)
            + "\n" + (magic ? "Magic damage +" + String.format(Locale.ROOT, "%.1f", magicDamage / 10.0) + "%"
                : (ranged ? "Ranged" : "Melee") + " strength +" + (ranged ? rangedStrength : strength))
            + "\n" + count + (count == 1 ? " worn item" : " worn items");
        String prayer = prayerDescription(magic, ranged);
        String caption = scopeLabel(reading);
        String missingText = reading.maxHit == null ? reading.reason : "";
        boolean blowpipe = weapon.lower().contains("blowpipe"); boolean shadow = weapon.lower().contains("tumeken's shadow");
        String key = reading.text() + caption + weapon.name + equipment + prayer + missingText + reading.reason + casting + blowpipe + shadow;
        if (key.equals(lastText)) return;
        lastText = key; final long ticket = generation;
        SwingUtilities.invokeLater(() ->
        {
            if (!started || ticket != generation || value == null) return;
            value.setText(reading.text()); mode.setText(caption); weaponText.setText(weapon.name);
            equipmentText.setText(equipment); prayerText.setText(prayer);
            missing.setVisible(reading.maxHit == null); status.setText(missingText);
            missingInputButton.setVisible(reading.maxHit == null && (blowpipe || casting)); calculation.setText(reading.reason);
            spellField.setVisible(magic); dartField.setVisible(blowpipe); tombsField.setVisible(shadow);
            panel.revalidate(); panel.repaint();
        });
    }
    private String prayerDescription(boolean magic, boolean ranged)
    {
        Prayer[] candidates = magic ? new Prayer[] {Prayer.AUGURY, Prayer.MYSTIC_VIGOUR, Prayer.MYSTIC_MIGHT, Prayer.MYSTIC_LORE}
            : ranged ? new Prayer[] {Prayer.RIGOUR, Prayer.DEADEYE, Prayer.EAGLE_EYE, Prayer.HAWK_EYE, Prayer.SHARP_EYE}
            : new Prayer[] {Prayer.PIETY, Prayer.CHIVALRY, Prayer.ULTIMATE_STRENGTH, Prayer.SUPERHUMAN_STRENGTH, Prayer.BURST_OF_STRENGTH};
        for (Prayer prayer : candidates) if (client.isPrayerActive(prayer)) return "Prayer · " + title(prayer.name().replace('_', ' '));
        return "Damage prayer · None";
    }
    private static String scopeLabel(CurrentMaxHit.Reading reading)
    {
        if (reading.maxHit == null) return "No attack estimate";
        if (reading.scope.equals("REF TBOW")) return "Twisted bow · Target reference";
        if (reading.scope.equals("BASE FIRST HIT")) return "Scythe · First hit";
        if (reading.scope.startsWith("INPUT")) return "Manual override · " + (reading.scope.endsWith("MAGIC") ? "Magic" : "Ranged");
        return "Normal " + (reading.scope.endsWith("MAGIC") ? "magic" : reading.scope.endsWith("RANGED") ? "ranged" : "melee") + " hit";
    }
    private static void style(JComboBox<?> choice)
    { choice.setFont(GuideText.FONT); choice.setForeground(SidebarUi.TEXT); choice.setBackground(SidebarUi.CARD); choice.setMaximumRowCount(8); }
    private static String title(String text)
    {
        if (text == null || text.isEmpty()) return "Style unavailable";
        String lower = text.toLowerCase(Locale.ROOT); return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
    private static final class Weapon
    {
        final int id; final String name;
        Weapon(int id, String name) { this.id = id; this.name = name; }
        String lower() { return name.toLowerCase(Locale.ROOT); }
    }
    private Weapon weapon()
    {
        ItemContainer worn = CurrentMaxHit.worn(client);
        Item[] gear = worn == null ? null : worn.getItems();
        int slot = EquipmentInventorySlot.WEAPON.getSlotIdx();
        Item weapon = gear == null || gear.length <= slot ? null : gear[slot];
        if (weapon == null || weapon.getId() < 0 || weapon.getQuantity() <= 0) return new Weapon(-1, "Unarmed");
        ItemComposition definition = items.getItemComposition(weapon.getId());
        return new Weapon(weapon.getId(), definition == null || definition.getName() == null ? "Weapon " + weapon.getId() : definition.getName());
    }
}
