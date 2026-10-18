/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Additional native activity readers. No lobby, future opponent state or inferred route. */
final class MinigameMoreActivities
{
    private static final Set<Integer> DRIFT_FRAMES = ids(ObjectID.FOSSIL_DRIFT_NET1_MULTI, ObjectID.FOSSIL_DRIFT_NET2_MULTI);
    private static final Set<Integer> BREW_TREES = ids(ObjectID.BREW_SCRAPEY_TREE);
    private static final Set<Integer> ALL_IDS;
    static
    {
        Set<Integer> ids = new LinkedHashSet<>(DRIFT_FRAMES); ids.addAll(BREW_TREES);
        ids.add(ObjectID.FOSSIL_MERMAID_DRIFTNETS); ids.add(ObjectID.SOUL_WARS_COUNTER_BANDAGES_NEUTRAL);
        ids.addAll(ids(ObjectID.CASTLEWARS_SARADOMIN_BANNER, ObjectID.CASTLEWARS_ZAMORAK_BANNER,
            ObjectID.CASTLEWARS_SARADOMIN_BANNER_STAND, ObjectID.CASTLEWARS_ZAMORAK_BANNER_STAND));
        ALL_IDS = Collections.unmodifiableSet(ids);
    }
    private MinigameMoreActivities() { }
    static Set<Integer> allHighlightObjectIds() { return ALL_IDS; }
    static List<MinigameActivityCatalog.Snapshot> supportedActivities()
    {
        return List.of(
            snapshot(MinigameActivityCatalog.Activity.DRIFT_NET, "Drift net fishing", "training:drift-net", List.of(), "Set and herd", "Set nets, herd shoals, and empty full nets.", DRIFT_FRAMES),
            snapshot(MinigameActivityCatalog.Activity.TROUBLE_BREWING, "Trouble Brewing", "minigame:trouble-brewing", List.of(), "Supply the still", "Gather, process and deposit ingredients for your crew.", BREW_TREES),
            snapshot(MinigameActivityCatalog.Activity.SOUL_WARS, "Soul Wars", "minigame:soul-wars", List.of(), "Support your team", "Capture the Soul Obelisk and contribute fragments while maintaining activity.", Set.of()),
            snapshot(MinigameActivityCatalog.Activity.CASTLE_WARS, "Castle Wars", "minigame:castle-wars", List.of(), "Capture the standard", "Take the opposing standard to your own stand while keeping your standard secure.", Set.of()));
    }
    static MinigameActivityCatalog.Snapshot detect(Client client)
    {
        if (client == null || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
            return MinigameActivityCatalog.Snapshot.EMPTY;
        if (visible(client, InterfaceID.SoulWarsGame.CONTENT_MAIN)) return soulWars(client);
        if (visible(client, InterfaceID.BrewOverlay.CONTENT_TOP)) return brewing(client);
        boolean sara = visible(client, InterfaceID.CastlewarsStatusOverlaySaradomin.HUD);
        boolean zam = visible(client, InterfaceID.CastlewarsStatusOverlayZamorak.HUD);
        if (sara != zam) return castleWars(client, sara);
        if (driftArea(client)) return drift(client);
        return MinigameActivityCatalog.Snapshot.EMPTY;
    }
    private static boolean driftArea(Client client)
    {
        if (!client.isInInstancedRegion()) return false;
        Player player = client.getLocalPlayer(); LocalPoint local = player.getLocalLocation(); if (local == null) return false;
        WorldView view = client.getWorldView(local.getWorldView());
        if (view == null || view.getPlane() < 0 || view.getPlane() > 3 || !view.isInstance()) return false;
        int[][][] chunks = view.getInstanceTemplateChunks();
        int x = local.getSceneX() / 8, y = local.getSceneY() / 8, plane = view.getPlane();
        if (chunks == null || plane >= chunks.length || chunks[plane] == null || x < 0 || x >= chunks[plane].length
            || chunks[plane][x] == null || y < 0 || y >= chunks[plane][x].length || chunks[plane][x][y] == -1) return false;
        WorldPoint template = WorldPoint.fromLocalInstance(client, local);
        return template != null && template.getRegionID() == 15008;
    }
    private static MinigameActivityCatalog.Snapshot drift(Client client)
    {
        int one = client.getVarbitValue(VarbitID.FOSSIL_DRIFT_NET1), two = client.getVarbitValue(VarbitID.FOSSIL_DRIFT_NET2);
        List<MinigameActivityCatalog.Fact> facts = new ArrayList<>();
        facts.add(fact("Net 1", netState(one) + " · " + Math.max(0, client.getVarbitValue(VarbitID.FOSSIL_DRIFT_NET1_CATCH)) + " caught"));
        facts.add(fact("Net 2", netState(two) + " · " + Math.max(0, client.getVarbitValue(VarbitID.FOSSIL_DRIFT_NET2_CATCH)) + " caught"));
        int replacement = count(client, InventoryID.INV, ItemID.FOSSIL_DRIFT_NET); facts.add(fact("Replacement nets", Integer.toString(replacement)));
        if (one == 3 || two == 3)
        {
            int chosen = one == 3 ? 1 : 2;
            return snapshot(MinigameActivityCatalog.Activity.DRIFT_NET, "Drift net fishing", "training:drift-net", facts,
                "Empty net " + chosen, "Empty the full net, then install a replacement to continue herding.",
                ids(chosen == 1 ? ObjectID.FOSSIL_DRIFT_NET1_MULTI : ObjectID.FOSSIL_DRIFT_NET2_MULTI));
        }
        if (one == 0 || two == 0)
        {
            int chosen = one == 0 ? 1 : 2;
            return snapshot(MinigameActivityCatalog.Activity.DRIFT_NET, "Drift net fishing", "training:drift-net", facts,
                replacement > 0 ? "Set net " + chosen : "Get replacement drift nets",
                replacement > 0 ? "Install a drift net on the empty frame, then herd nearby shoals toward it."
                    : "Get drift nets from Annette or your stored supplies before returning to the empty frame.",
                replacement > 0 ? ids(chosen == 1 ? ObjectID.FOSSIL_DRIFT_NET1_MULTI : ObjectID.FOSSIL_DRIFT_NET2_MULTI) : ids(ObjectID.FOSSIL_MERMAID_DRIFTNETS));
        }
        return snapshot(MinigameActivityCatalog.Activity.DRIFT_NET, "Drift net fishing", "training:drift-net", facts,
            one > 3 || one < 0 || two > 3 || two < 0 ? "Check your nets" : "Herd fish shoals",
            "Prod nearby shoals toward a set net. Empty a net when its native state becomes full.", DRIFT_FRAMES);
    }
    private static String netState(int value)
    { return value == 0 ? "Unset" : value == 1 ? "Set" : value == 2 ? "Catching" : value == 3 ? "Full" : "Unknown"; }
    private static MinigameActivityCatalog.Snapshot brewing(Client client)
    {
        List<MinigameActivityCatalog.Fact> facts = new ArrayList<>();
        addText(facts, client, "Round", InterfaceID.BrewOverlay.BREW_TIME_DISPLAY);
        addText(facts, client, "Water", InterfaceID.BrewOverlay.BUCKET_COUNT);
        addText(facts, client, "Bark", InterfaceID.BrewOverlay.BARK_COUNT);
        addText(facts, client, "Sweetgrubs", InterfaceID.BrewOverlay.SWEETGRUB_COUNT);
        addText(facts, client, "Bitternuts", InterfaceID.BrewOverlay.BITTERNUT_COUNT);
        addText(facts, client, "Red score", InterfaceID.BrewOverlay.RED_SCORE); addText(facts, client, "Blue score", InterfaceID.BrewOverlay.BLUE_SCORE);
        int logs = count(client, InventoryID.INV, ItemID.BREW_SCRAPEY_LOGS);
        int ingredients = count(client, InventoryID.INV, ItemID.BREW_SCRAPEY_BARK, ItemID.BREW_SWEETGRUBS,
            ItemID.BREW_BITTERNUT, ItemID.BREW_BOWL_RED, ItemID.BREW_BOWL_BLUE, ItemID.BUCKET_WATER);
        facts.add(fact("Carried logs", Integer.toString(logs))); facts.add(fact("Carried ingredients", Integer.toString(ingredients)));
        if (ingredients > 0)
            return snapshot(MinigameActivityCatalog.Activity.TROUBLE_BREWING, "Trouble Brewing", "minigame:trouble-brewing", facts,
                "Deposit your ingredients", "Take the ingredients to your crew's matching hoppers. Deposit bark, grubs, bitternuts, coloured water or ordinary water in the correct hopper.", Set.of());
        if (logs > 0)
            return snapshot(MinigameActivityCatalog.Activity.TROUBLE_BREWING, "Trouble Brewing", "minigame:trouble-brewing", facts,
                count(client, InventoryID.INV, ItemID.KNIFE) > 0 ? "Cut logs into bark" : "Collect a knife",
                "Use a knife on scrapey tree logs, then deposit the bark into your crew's bark hopper.", Set.of());
        return snapshot(MinigameActivityCatalog.Activity.TROUBLE_BREWING, "Trouble Brewing", "minigame:trouble-brewing", facts,
            "Gather scrapey tree logs", "Chop a scrapey tree, cut the logs into bark with a knife, then deposit the bark for your crew.", BREW_TREES);
    }
    private static MinigameActivityCatalog.Snapshot soulWars(Client client)
    {
        List<MinigameActivityCatalog.Fact> facts = new ArrayList<>();
        addText(facts, client, "Round", InterfaceID.SoulWarsGame.TIME_LEFT);
        addText(facts, client, "Blue avatar HP", InterfaceID.SoulWarsGame.BLUE_HEALTH); addText(facts, client, "Red avatar HP", InterfaceID.SoulWarsGame.RED_HEALTH);
        addText(facts, client, "Blue avatar strength", InterfaceID.SoulWarsGame.BLUE_STRENGTH); addText(facts, client, "Red avatar strength", InterfaceID.SoulWarsGame.RED_STRENGTH);
        facts.add(fact("Activity", Integer.toString(Math.max(0, client.getVarbitValue(VarbitID.SOUL_WARS_ACTIVITY_VALUE)))));
        int fragments = count(client, InventoryID.INV, ItemID.SOUL_FRAGMENT); facts.add(fact("Carried fragments", Integer.toString(fragments)));
        if (fragments > 0)
            return snapshot(MinigameActivityCatalog.Activity.SOUL_WARS, "Soul Wars", "minigame:soul-wars", facts,
                "Take fragments to the obelisk", "Take your soul fragments to the Soul Obelisk. Sacrifice them once your team controls it to weaken the enemy avatar.", Set.of());
        if (count(client, InventoryID.INV, ItemID.SOUL_WARS_BANDAGES) == 0)
            return snapshot(MinigameActivityCatalog.Activity.SOUL_WARS, "Soul Wars", "minigame:soul-wars", facts,
                "Restock at your base", "Collect bandages and your chosen supplies at your base before joining your team at the Soul Obelisk.", ids(ObjectID.SOUL_WARS_COUNTER_BANDAGES_NEUTRAL));
        return snapshot(MinigameActivityCatalog.Activity.SOUL_WARS, "Soul Wars", "minigame:soul-wars", facts,
            "Capture and contribute", "Help your team capture the Soul Obelisk. Collect fragments and offer them while your team controls it; keep contributing to maintain activity.", Set.of());
    }
    private static MinigameActivityCatalog.Snapshot castleWars(Client client, boolean saradomin)
    {
        List<MinigameActivityCatalog.Fact> facts = new ArrayList<>(); facts.add(fact("Team HUD", saradomin ? "Saradomin" : "Zamorak"));
        addText(facts, client, "Round", saradomin ? InterfaceID.CastlewarsStatusOverlaySaradomin.TIMER : InterfaceID.CastlewarsStatusOverlayZamorak.TIMER);
        addText(facts, client, "Saradomin score", saradomin ? InterfaceID.CastlewarsStatusOverlaySaradomin.SARADOMINSCORE : InterfaceID.CastlewarsStatusOverlayZamorak.SARADOMINSCORE);
        addText(facts, client, "Zamorak score", saradomin ? InterfaceID.CastlewarsStatusOverlaySaradomin.ZAMORAKSCORE : InterfaceID.CastlewarsStatusOverlayZamorak.ZAMORAKSCORE);
        addText(facts, client, "Saradomin flag", saradomin ? InterfaceID.CastlewarsStatusOverlaySaradomin.SARADOMINFLAG : InterfaceID.CastlewarsStatusOverlayZamorak.SARADOMINFLAG);
        addText(facts, client, "Zamorak flag", saradomin ? InterfaceID.CastlewarsStatusOverlaySaradomin.ZAMORAKFLAG : InterfaceID.CastlewarsStatusOverlayZamorak.ZAMORAKFLAG);
        int ownFlag = saradomin ? ItemID.CASTLEWARS_SARADOMIN_BANNER : ItemID.CASTLEWARS_ZAMORAK_BANNER;
        int enemyFlag = saradomin ? ItemID.CASTLEWARS_ZAMORAK_BANNER : ItemID.CASTLEWARS_SARADOMIN_BANNER;
        if (count(client, InventoryID.INV, enemyFlag) + count(client, InventoryID.WORN, enemyFlag) > 0)
            return snapshot(MinigameActivityCatalog.Activity.CASTLE_WARS, "Castle Wars", "minigame:castle-wars", facts,
                "Return the enemy standard", "Carry the enemy standard to your own standard stand to score while your team protects the return route.",
                ids(saradomin ? ObjectID.CASTLEWARS_SARADOMIN_BANNER_STAND : ObjectID.CASTLEWARS_ZAMORAK_BANNER_STAND));
        if (count(client, InventoryID.INV, ownFlag) + count(client, InventoryID.WORN, ownFlag) > 0)
            return snapshot(MinigameActivityCatalog.Activity.CASTLE_WARS, "Castle Wars", "minigame:castle-wars", facts,
                "Protect your standard", "You are carrying your own standard. Keep it protected outside your castle so the opposing team cannot capture it.", Set.of());
        if (count(client, InventoryID.INV, ItemID.CASTLEWARS_BANDAGES) == 0)
            return snapshot(MinigameActivityCatalog.Activity.CASTLE_WARS, "Castle Wars", "minigame:castle-wars", facts,
                "Collect bandages", "Collect bandages and the tools for your role from your base's supply area, then join your team.", Set.of());
        return snapshot(MinigameActivityCatalog.Activity.CASTLE_WARS, "Castle Wars", "minigame:castle-wars", facts,
            "Attack or defend with your team", "Follow the displayed flag status. Take an available enemy standard and return it to your stand, or protect your team's standard.",
            ids(saradomin ? ObjectID.CASTLEWARS_ZAMORAK_BANNER : ObjectID.CASTLEWARS_SARADOMIN_BANNER));
    }
    private static int count(Client client, int containerId, int... ids)
    {
        ItemContainer container = client.getItemContainer(containerId); if (container == null || container.getItems() == null) return 0;
        long result = 0;
        for (Item item : container.getItems()) if (item != null && item.getQuantity() > 0)
            for (int id : ids) if (item.getId() == id) { result += item.getQuantity(); break; }
        return (int) Math.min(Integer.MAX_VALUE, result);
    }
    private static MinigameActivityCatalog.Snapshot snapshot(MinigameActivityCatalog.Activity activity, String title, String guide,
        List<MinigameActivityCatalog.Fact> facts, String next, String instruction, Set<Integer> targets)
    { return new MinigameActivityCatalog.Snapshot(activity, title, guide, facts, List.of(new MinigameActivityCatalog.Step(next, instruction)), targets, List.of(), true); }
    private static MinigameActivityCatalog.Fact fact(String name, String value) { return new MinigameActivityCatalog.Fact(name, value); }
    private static Set<Integer> ids(Integer... ids) { return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(ids))); }
    private static boolean visible(Client client, int id) { Widget widget = client.getWidget(id); return widget != null && !widget.isHidden(); }
    private static void addText(List<MinigameActivityCatalog.Fact> facts, Client client, String label, int id)
    {
        Widget widget = client.getWidget(id); if (widget == null || widget.isHidden() || widget.getText() == null) return;
        String text = Text.removeTags(widget.getText()).replaceAll("\\s+", " ").trim();
        if (!text.isEmpty()) facts.add(fact(label, text.substring(0, Math.min(100, text.length()))));
    }
}
