/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Additional observed activities. Native state is read afresh; no timers or scene positions are inferred. */
final class MinigameExtraActivities
{
    private static final Set<Integer> PYRAMID_RESOURCES = ids(
        ObjectID.NTK_URN_TYPE1_MULTI_1, ObjectID.NTK_URN_TYPE1_MULTI_2, ObjectID.NTK_URN_TYPE1_MULTI_3,
        ObjectID.NTK_URN_TYPE1_MULTI_4, ObjectID.NTK_URN_TYPE1_MULTI_5, ObjectID.NTK_URN_TYPE2_MULTI_6,
        ObjectID.NTK_URN_TYPE2_MULTI_7, ObjectID.NTK_URN_TYPE2_MULTI_8, ObjectID.NTK_URN_TYPE2_MULTI_9,
        ObjectID.NTK_URN_TYPE2_MULTI_10, ObjectID.NTK_URN_TYPE3_MULTI_11, ObjectID.NTK_URN_TYPE3_MULTI_12,
        ObjectID.NTK_URN_TYPE3_MULTI_13, ObjectID.NTK_URN_TYPE3_MULTI_14, ObjectID.NTK_URN_TYPE3_MULTI_15,
        ObjectID.NTK_GOLDEN_CHEST_MULTI, ObjectID.NTK_SARCOPHAGUS_MULTI);
    private static final Set<Integer> PYRAMID_TRAP = ids(ObjectID.NTK_SPEARTRAP_INMOTION);
    private static final Set<Integer> ALCH_RESOURCES = ids(
        ObjectID.MAGICTRAINING_ALCHEM_CUPBOARD0, ObjectID.MAGICTRAINING_ALCHEM_CUPBOARD0_OPEN,
        ObjectID.MAGICTRAINING_ALCHEM_CUPBOARD1, ObjectID.MAGICTRAINING_ALCHEM_CUPBOARD1_OPEN,
        ObjectID.MAGICTRAINING_ALCHEM_CUPBOARD2, ObjectID.MAGICTRAINING_ALCHEM_CUPBOARD2_OPEN,
        ObjectID.MAGICTRAINING_ALCHEM_CUPBOARD3, ObjectID.MAGICTRAINING_ALCHEM_CUPBOARD3_OPEN,
        ObjectID.MAGICTRAINING_ALCHEM_CUPBOARD4, ObjectID.MAGICTRAINING_ALCHEM_CUPBOARD4_OPEN,
        ObjectID.MAGICTRAINING_ALCHEM_CUPBOARD5, ObjectID.MAGICTRAINING_ALCHEM_CUPBOARD5_OPEN);
    private static final Set<Integer> ENCHANT_RESOURCES = ids(
        ObjectID.MAGICTRAINING_ENCHAN_SHAPEPILE1, ObjectID.MAGICTRAINING_ENCHAN_SHAPEPILE2,
        ObjectID.MAGICTRAINING_ENCHAN_SHAPEPILE3, ObjectID.MAGICTRAINING_ENCHAN_SHAPEPILE4,
        ObjectID.MAGICTRAINING_ENCHA_HOLE);
    private static final Set<Integer> GRAVE_RESOURCES = ids(
        ObjectID.MAGICTRAINING_BONES_PILE1, ObjectID.MAGICTRAINING_BONES_PILE2,
        ObjectID.MAGICTRAINING_BONES_PILE3, ObjectID.MAGICTRAINING_BONES_PILE4);
    private static final Set<Integer> TELE_RESOURCES = ids(ObjectID.MAGICTRAINING_MINI_STATUE_PITFALL);
    private static final Set<Integer> VOLCANIC_RESOURCES = ids(
        ObjectID.FOSSIL_MINING_START_EXIT, ObjectID.FOSSIL_MINING_BOULDER_ATTACHED,
        ObjectID.FOSSIL_MINING_BOULDER_GIANT, ObjectID.FOSSIL_MINING_BOULDER_LARGE,
        ObjectID.FOSSIL_MINING_BOULDER_MEDIUM, ObjectID.FOSSIL_MINING_BOULDER_SMALL,
        ObjectID.FOSSIL_VOLCANO_CHAMBER, ObjectID.FOSSIL_VOLCANO_CHAMBER_BLOCKED);
    private static final Set<Integer> BLAST_RESOURCES = ids(
        ObjectID.BLAST_MINING_WALL_01, ObjectID.BLAST_MINING_WALL_02,
        ObjectID.BLAST_MINING_WALL_CHISELED_01, ObjectID.BLAST_MINING_WALL_CHISELED_02,
        ObjectID.BLAST_MINING_WALL_POT_01, ObjectID.BLAST_MINING_WALL_POT_02,
        ObjectID.BLAST_MINING_WALL_DESTROYED_01, ObjectID.BLAST_MINING_WALL_DESTROYED_02,
        ObjectID.BLAST_MINING_HOPPER, ObjectID.BLAST_MINING_SACK_DEPOSIT);
    private static final Set<Integer> TRAWLER_RESOURCES = ids(ObjectID.TRAWLERNET_L, ObjectID.TRAWLERNET_R);
    private static final Set<Integer> ALL_IDS;

    static
    {
        LinkedHashSet<Integer> all = new LinkedHashSet<>();
        for (Set<Integer> resources : Arrays.asList(PYRAMID_RESOURCES, PYRAMID_TRAP, ALCH_RESOURCES, ENCHANT_RESOURCES,
            GRAVE_RESOURCES, TELE_RESOURCES, VOLCANIC_RESOURCES, BLAST_RESOURCES, TRAWLER_RESOURCES))
            all.addAll(resources);
        ALL_IDS = Collections.unmodifiableSet(all);
    }

    private MinigameExtraActivities() { }

    static MinigameActivityCatalog.Snapshot detect(Client client)
    {
        if (client == null || client.getGameState() != GameState.LOGGED_IN) return MinigameActivityCatalog.Snapshot.EMPTY;
        Player player = client.getLocalPlayer();
        WorldPoint location = player == null ? null : player.getWorldLocation();
        if (location == null) return MinigameActivityCatalog.Snapshot.EMPTY;

        // RuneLite explicitly distinguishes this shared region from KBD by plane and the entire map-region array.
        if (location.getPlane() > 0 && Arrays.equals(client.getMapRegions(), new int[] {9033})) return nightmare(client);
        if (location.getRegionID() == 7749 && client.getVarbitValue(VarbitID.NTK_PLAYER_TIMER_COUNT) > 0) return pyramid(client);
        if (location.getRegionID() == 13462 && location.getPlane() >= 0 && location.getPlane() <= 2)
            return mageTraining(client, location.getPlane());
        if (visible(client, InterfaceID.MagictrainingTele.UNIVERSE)) return mageTraining(client, 3);
        if (visible(client, InterfaceID.FossilVolcanicMine.UNIVERSE)) return volcanic(client);
        if (visible(client, InterfaceID.LovakengjBlastMiningHud.UNIVERSE)) return blast(client);
        if (visible(client, InterfaceID.TrawlerOverlay.UNIVERSE)) return trawler(client);
        return MinigameActivityCatalog.Snapshot.EMPTY;
    }

    static List<MinigameActivityCatalog.Snapshot> supportedActivities()
    {
        return List.of(
            snapshot(MinigameActivityCatalog.Activity.NIGHTMARE_ZONE, "Nightmare Zone", "training:nightmare-zone-absorptions",
                List.of(), "Manage the dream", "Follow your selected dream method and monitor health, prayer and supplies.", Set.of()),
            snapshot(MinigameActivityCatalog.Activity.PYRAMID_PLUNDER, "Pyramid Plunder", "training:pyramid-plunder",
                List.of(), "Work through the rooms", "Pass the spear trap, then move toward your planned looting room.", PYRAMID_RESOURCES),
            snapshot(MinigameActivityCatalog.Activity.MAGE_TRAINING_ARENA, "Mage Training Arena", "minigame:mage-training-arena",
                List.of(), "Train in the selected room", "Use the room's spell and check your reward point goals.", ALCH_RESOURCES),
            snapshot(MinigameActivityCatalog.Activity.VOLCANIC_MINE, "Volcanic Mine", "training:volcanic-mine",
                List.of(), "Mine and maintain the chambers", "Follow your team's vent role and leave before the native game timer expires.", VOLCANIC_RESOURCES),
            snapshot(MinigameActivityCatalog.Activity.BLAST_MINE, "Blast Mine", "training:blast-mine",
                List.of(), "Complete a blast circuit", "Excavate, load and light dynamite; move clear and collect the blasted ore.", BLAST_RESOURCES),
            snapshot(MinigameActivityCatalog.Activity.FISHING_TRAWLER, "Fishing Trawler", "minigame:fishing-trawler",
                List.of(), "Contribute on your deck", "Bail water, patch leaks, repair nets or handle the tentacle as needed.", TRAWLER_RESOURCES));
    }

    static Set<Integer> allHighlightObjectIds() { return ALL_IDS; }

    private static MinigameActivityCatalog.Snapshot nightmare(Client client)
    {
        List<MinigameActivityCatalog.Fact> facts = new ArrayList<>();
        int points = client.getVarbitValue(VarbitID.NZONE_CURRENTPOINTS);
        int absorption = client.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS);
        int banked = client.getVarpValue(VarPlayerID.NZONE_REWARDPOINTS);
        if (points >= 0) facts.add(fact("Dream points", Integer.toString(points)));
        if (banked >= 0) facts.add(fact("Banked points", Integer.toString(banked)));
        if (absorption >= 0 && absorption <= 1000) facts.add(fact("Absorption", Integer.toString(absorption)));
        boolean activeAbsorption = absorption > 0 && absorption <= 1000;
        return snapshot(MinigameActivityCatalog.Activity.NIGHTMARE_ZONE, "Nightmare Zone",
            activeAbsorption ? "training:nightmare-zone-absorptions" : null, facts,
            activeAbsorption ? "Maintain your dream supplies" : "Check your protection",
            activeAbsorption ? "Continue your chosen training style. Monitor health and refill absorption before your planned threshold."
                : "No absorption is active. Manage prayer, food or the supplies required by your chosen dream method.", Set.of());
    }

    private static MinigameActivityCatalog.Snapshot pyramid(Client client)
    {
        List<MinigameActivityCatalog.Fact> facts = new ArrayList<>();
        int room = client.getVarbitValue(VarbitID.NTK_ROOM_NUMBER);
        int required = client.getVarbitValue(VarbitID.NTK_THIEVING_REQUIRED);
        if (room >= 1 && room <= 8) facts.add(fact("Room", Integer.toString(room)));
        if (required >= 21 && required <= 91) facts.add(fact("Room requirement", required + " Thieving"));
        addText(facts, client, "Timer", InterfaceID.NtkOverlay.TIMER_TEXT1);
        int trapState = client.getVarbitValue(VarbitID.NTK_TRAP_ACTIVE);
        boolean trap = trapState == 1;
        if (trapState == 0 || trapState == 1) facts.add(fact("Spear trap", trap ? "Not yet passed" : "Passed"));
        return snapshot(MinigameActivityCatalog.Activity.PYRAMID_PLUNDER, "Pyramid Plunder", "training:pyramid-plunder", facts,
            trap ? "Pass the spear trap" : "Loot or move to the next room",
            trap ? "Pass the entrance spear trap before searching containers or trying the room's exits."
                : "Move through lower rooms toward your planned looting room, then search urns while watching the native timer.",
            trap ? PYRAMID_TRAP : PYRAMID_RESOURCES);
    }

    private static MinigameActivityCatalog.Snapshot mageTraining(Client client, int room)
    {
        List<MinigameActivityCatalog.Fact> facts = new ArrayList<>();
        String roomName, title, instruction;
        Set<Integer> resources;
        switch (room)
        {
            case 0:
                roomName = "Enchanting Chamber"; resources = ENCHANT_RESOURCES;
                title = "Enchant and deposit shapes";
                instruction = "Collect shapes, cast an enchantment spell and deposit the resulting orbs in the hole. Check the current bonus shape.";
                addText(facts, client, "Points", InterfaceID.MagictrainingEncha.MAGICTRAIN_ENCHA_PTS);
                addText(facts, client, "Bonus", InterfaceID.MagictrainingEncha.MAGICTRAIN_ENCH_BONUSTXT);
                break;
            case 1:
                roomName = "Creature Graveyard"; resources = GRAVE_RESOURCES;
                title = "Convert bones and manage health";
                instruction = "Collect bones and turn them into food. Eat when needed, then deposit fruit for points.";
                addText(facts, client, "Points", InterfaceID.MagictrainingGrave.MAGICTRAIN_GRAVE_PTS);
                break;
            case 2:
                roomName = "Alchemist's Playground"; resources = ALCH_RESOURCES;
                String best = alchemyBest(client);
                title = best.isEmpty() ? "Check the current alchemy values" : "Alchemise " + best;
                instruction = best.isEmpty() ? "Find the item worth 30 coins in the room's current table, alchemise it and deposit coins."
                    : "The native table currently values " + best + " at 30 coins. Find it in the cupboards, alchemise it and deposit coins.";
                addText(facts, client, "Points", InterfaceID.MagictrainingAlchem.MAGICTRAIN_ALCHEM_PTS);
                if (!best.isEmpty()) facts.add(fact("30-coin item", best));
                break;
            default:
                roomName = "Telekinetic Theatre"; resources = TELE_RESOURCES;
                title = "Move the maze guardian";
                instruction = "Stand on the side you want to pull toward and cast Telekinetic Grab on the guardian. Guide it to the finish tile.";
                addText(facts, client, "Points", InterfaceID.MagictrainingTele.MAGICTRAIN_TELE_PTS);
                addText(facts, client, "Maze", InterfaceID.MagictrainingTele.MAGICTRAIN_MAZE_NUMBER);
                break;
        }
        facts.add(0, fact("Room", roomName));
        return snapshot(MinigameActivityCatalog.Activity.MAGE_TRAINING_ARENA, "Mage Training Arena", "minigame:mage-training-arena",
            facts, title, instruction, resources);
    }

    private static String alchemyBest(Client client)
    {
        String best = "";
        for (int i = 0; i < 5; ++i)
        {
            String name = text(client, InterfaceID.MAGICTRAINING_ALCHEM << 16 | 7 + i);
            String coins = text(client, InterfaceID.MAGICTRAINING_ALCHEM << 16 | 12 + i);
            if (name.isEmpty() || !coins.matches("\\d{1,3}")) return "";
            if (Integer.parseInt(coins) == 30)
            {
                if (!best.isEmpty()) return "";
                best = name;
            }
        }
        return best;
    }

    private static MinigameActivityCatalog.Snapshot volcanic(Client client)
    {
        List<MinigameActivityCatalog.Fact> facts = new ArrayList<>();
        addText(facts, client, "Time", InterfaceID.FossilVolcanicMine.TIME);
        addText(facts, client, "Points", InterfaceID.FossilVolcanicMine.POINTS);
        addText(facts, client, "Stability", InterfaceID.FossilVolcanicMine.STABILITY);
        addText(facts, client, "Players", InterfaceID.FossilVolcanicMine.PLAYERS);
        addText(facts, client, "Vent A", InterfaceID.FossilVolcanicMine.VENT_A);
        addText(facts, client, "Vent B", InterfaceID.FossilVolcanicMine.VENT_B);
        addText(facts, client, "Vent C", InterfaceID.FossilVolcanicMine.VENT_C);
        addText(facts, client, "Chamber A", InterfaceID.FossilVolcanicMine.CHAMBER_A);
        addText(facts, client, "Chamber B", InterfaceID.FossilVolcanicMine.CHAMBER_B);
        addText(facts, client, "Chamber C", InterfaceID.FossilVolcanicMine.CHAMBER_C);
        return snapshot(MinigameActivityCatalog.Activity.VOLCANIC_MINE, "Volcanic Mine", "training:volcanic-mine", facts,
            "Mine and maintain the chambers", "Follow your assigned vent role. Mine the moving boulder, maintain a clear route and leave before the native timer expires.",
            VOLCANIC_RESOURCES);
    }

    private static MinigameActivityCatalog.Snapshot blast(Client client)
    {
        List<MinigameActivityCatalog.Fact> facts = new ArrayList<>();
        int[] vars = {VarbitID.LOVAKENGJ_ORE_COAL_BIGGER, VarbitID.LOVAKENGJ_ORE_GOLD_BIGGER,
            VarbitID.LOVAKENGJ_ORE_MITHRIL_BIGGER, VarbitID.LOVAKENGJ_ORE_ADAMANTITE_BIGGER, VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER};
        String[] labels = {"Coal", "Gold", "Mithril", "Adamantite", "Runite"};
        for (int i = 0; i < vars.length; ++i)
        {
            int amount = client.getVarbitValue(vars[i]);
            if (amount >= 0) facts.add(fact(labels[i] + " stored", Integer.toString(amount)));
        }
        return snapshot(MinigameActivityCatalog.Activity.BLAST_MINE, "Blast Mine", "training:blast-mine", facts,
            "Excavate, blast and collect", "Chisel hard rock, load dynamite and light it. Move clear of the blast, collect blasted ore promptly and deposit it in the sack.",
            BLAST_RESOURCES);
    }

    private static MinigameActivityCatalog.Snapshot trawler(Client client)
    {
        List<MinigameActivityCatalog.Fact> facts = new ArrayList<>();
        addText(facts, client, "Time", InterfaceID.TrawlerOverlay.TIME);
        addText(facts, client, "Water", InterfaceID.TrawlerOverlay.WATER);
        addText(facts, client, "Activity", InterfaceID.TrawlerOverlay.ACTIVITY);
        addText(facts, client, "Net", InterfaceID.TrawlerOverlay.NET);
        addText(facts, client, "Catch", InterfaceID.TrawlerOverlay.CATCH);
        return snapshot(MinigameActivityCatalog.Activity.FISHING_TRAWLER, "Fishing Trawler", "minigame:fishing-trawler", facts,
            "Contribute on your deck", "Bail water and patch leaks below deck, or repair nets and handle the tentacle above. Watch the native contribution and trip status.",
            TRAWLER_RESOURCES);
    }

    private static MinigameActivityCatalog.Snapshot snapshot(MinigameActivityCatalog.Activity activity, String title, String guide,
        List<MinigameActivityCatalog.Fact> facts, String step, String instruction, Set<Integer> resources)
    {
        return new MinigameActivityCatalog.Snapshot(activity, title, guide, facts,
            List.of(new MinigameActivityCatalog.Step(step, instruction)), resources, List.of(), true);
    }
    private static MinigameActivityCatalog.Fact fact(String label, String value)
    { return new MinigameActivityCatalog.Fact(label, value); }
    private static Set<Integer> ids(Integer... ids)
    { return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(ids))); }
    private static boolean visible(Client client, int widgetId)
    { Widget widget = client.getWidget(widgetId); return widget != null && !widget.isHidden(); }
    private static void addText(List<MinigameActivityCatalog.Fact> facts, Client client, String label, int widgetId)
    { String value = text(client, widgetId); if (!value.isEmpty()) facts.add(fact(label, value)); }
    private static String text(Client client, int widgetId)
    {
        Widget widget = client.getWidget(widgetId);
        if (widget == null || widget.isHidden() || widget.getText() == null) return "";
        String value = Text.removeTags(widget.getText()).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return value.substring(0, Math.min(value.length(), 100));
    }
}
