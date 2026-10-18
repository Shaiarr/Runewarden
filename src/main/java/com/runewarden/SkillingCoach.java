/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Font;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.ObjectID;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/** Observed gathering timers and a quiet, contextual outfit check. No game input. */
@Singleton
public final class SkillingCoach
{
    static final class Node
    {
        final WorldPoint point;
        final int worldView, sizeX, sizeY, height, ticks;
        final String name;
        final long started;
        final boolean respawn;
        boolean motherlode;
        Node(WorldPoint point, int worldView, int sizeX, int sizeY, int height, String name, long started, int ticks, boolean respawn)
        {
            this.point = point; this.worldView = worldView; this.sizeX = sizeX; this.sizeY = sizeY;
            this.height = height; this.name = name; this.started = started; this.ticks = ticks; this.respawn = respawn;
        }
        boolean hasCountdown(long now) { return ticks > 0 && (respawn || NodeTimerRules.remainingMillis(started,ticks,now)>0); }
        long endsAt() { return started + ticks * 600L; }
        String countdown(long now) { return NodeTimerRules.countdown(NodeTimerRules.remainingMillis(started,ticks,now)); }
        String label(long now)
        {
            if (ticks == 0) { return "Until depleted"; }
            long left = NodeTimerRules.remainingMillis(started, ticks, now);
            if (respawn) { return "Respawn " + NodeTimerRules.countdown(left); }
            return left == 0 ? "Next log may deplete" : "Life ~ " + NodeTimerRules.countdown(left);
        }
    }
    static final class Reminder
    {
        final Skill skill;
        final String outfit;
        final List<SkillingOutfits.Piece> missing;
        Reminder(Skill skill, String outfit, List<SkillingOutfits.Piece> missing)
        { this.skill = skill; this.outfit = outfit; this.missing = missing; }
    }
    private final Client client;
    private final ItemManager itemManager;
    private final ConfigManager configManager;
    private final RunewardenConfig config;
    private final Map<WorldPoint, Node> respawns = new LinkedHashMap<>();
    private final Map<Skill, Integer> previousXp = new EnumMap<>(Skill.class);
    private final Set<Skill> dismissed = ConcurrentHashMap.newKeySet();
    private TileObject target;
    private Skill targetSkill;
    private volatile Skill activeSkill;
    private Node activeNode;
    private volatile List<Node> nodeSnapshot = Collections.emptyList();
    private long lastActivity, lastPickpocket, lastGatherAnimation;
    private volatile Reminder reminder;
    private GuideText panelStatus;
    private volatile String lastPanelMessage;

    @Inject SkillingCoach(Client client, ItemManager itemManager, ConfigManager configManager, RunewardenConfig config)
    { this.client = client; this.itemManager = itemManager; this.configManager = configManager; this.config = config; }

    public void startUp() { clearScene(); initialiseXp(); dismissed.clear(); }
    public void shutDown() { clearScene(); previousXp.clear(); dismissed.clear(); }
    private void initialiseXp()
    {
        previousXp.clear();
        for (Skill skill : Skill.values()) { if (skill != Skill.OVERALL) { previousXp.put(skill, client.getSkillExperience(skill)); } }
    }
    private void clearScene()
    {
        respawns.clear(); target = null; targetSkill = null; activeNode = null; activeSkill = null;
        nodeSnapshot = Collections.emptyList();
        reminder = null; lastActivity = 0; lastPickpocket = 0; lastGatherAnimation = 0;
    }
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN) { clearScene(); }
        if (event.getGameState() == GameState.LOGGED_IN) { initialiseXp(); }
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) { dismissed.clear(); }
    }
    public void onActorDeath(ActorDeath event)
    {
        if (!enabled()) { clearScene(); return; }
        if (event.getActor() == client.getLocalPlayer()) { clearScene(); }
    }

    private boolean enabled() { return config.nodeTimers() || config.skillingOutfitReminder(); }

    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!enabled()) return;
        String action = event.getMenuOption() == null ? "" : event.getMenuOption().toLowerCase(Locale.ROOT);
        if (action.equals("pickpocket")) { lastPickpocket = System.currentTimeMillis(); }
        if (!config.nodeTimers()) return;
        Skill skill = action.equals("chop down") || action.equals("chop") ? Skill.WOODCUTTING
            : action.equals("mine") ? Skill.MINING : null;
        if (skill == null) { return; }
        Player player = client.getLocalPlayer();
        if (player == null || player.getWorldView() == null) { return; }
        WorldView view = player.getWorldView();
        TileObject object = findTarget(view, view.getPlane(), event.getParam0(), event.getParam1(), event.getId());
        if (object != null)
        {
            if (target == null || !target.getWorldLocation().equals(object.getWorldLocation())) { activeNode = null; }
            target = object; targetSkill = skill; publishNodes();
        }
    }
    public void onAnimationChanged(AnimationChanged event)
    {
        if (!enabled()) return;
        Player player = client.getLocalPlayer();
        if (player == null || event.getActor() != player) { return; }
        int animation = player.getAnimation();
        Skill skill = NodeTimerRules.CHOPPING.contains(animation) ? Skill.WOODCUTTING
            : NodeTimerRules.MINING.contains(animation) ? Skill.MINING : null;
        if (skill != null)
        {
            activeSkill = skill; lastActivity = System.currentTimeMillis(); lastGatherAnimation = lastActivity;
            if (config.nodeTimers() && skill == Skill.MINING && targetSkill == skill && target != null && activeNode == null)
            { activeNode = nodeFor(target, 0, false); publishNodes(); }
        }
    }
    public void onStatChanged(StatChanged event)
    {
        Integer old = previousXp.put(event.getSkill(), event.getXp());
        if (!enabled() || old == null || event.getXp() <= old || client.getGameState() != GameState.LOGGED_IN) { return; }
        long now = System.currentTimeMillis();
        if (!SkillingOutfits.OUTFITS.containsKey(event.getSkill())) { return; }
        if (event.getSkill() == Skill.THIEVING && now - lastPickpocket > 90_000L) { return; }
        activeSkill = event.getSkill(); lastActivity = now;
        if (config.nodeTimers() && event.getSkill() == Skill.WOODCUTTING && targetSkill == Skill.WOODCUTTING && target != null
            && activeNode == null && now - lastGatherAnimation < 6_000L)
        {
            ObjectComposition definition = client.getObjectDefinition(target.getId());
            String name = definition == null ? "" : definition.getName();
            activeNode = nodeFor(target, NodeTimerRules.PLANTED_TREES.contains(target.getId()) ? 0 : NodeTimerRules.treeLifetimeTicks(name), false); publishNodes();
        }
    }
    public void onGameTick(GameTick event)
    {
        if (!enabled()) { clearScene(); showPanelStatus("Skilling coach disabled."); return; }
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null || client.getLocalPlayer().isDead())
        { clearScene(); return; }
        long now = System.currentTimeMillis();
        if (!config.nodeTimers()) { respawns.clear(); target = null; targetSkill = null; activeNode = null; }
        else respawns.values().removeIf(n -> NodeTimerRules.remainingMillis(n.started, n.ticks, now) == 0);
        Player player = client.getLocalPlayer();
        int anim = player.getAnimation();
        if (NodeTimerRules.CHOPPING.contains(anim) || NodeTimerRules.MINING.contains(anim))
        { lastGatherAnimation = now; lastActivity = now; }
        if (now - lastGatherAnimation > 6_000L) { activeNode = null; }
        if (target != null && (target.getWorldView() != player.getWorldView()
            || target.getWorldLocation().distanceTo(player.getWorldLocation()) > 16)) { target = null; activeNode = null; }
        publishNodes();
        reminder = !config.skillingOutfitReminder() || now - lastActivity > 90_000L ? null : readReminder();
        if (panelStatus != null && client.getTickCount() % 5 == 0)
        {
            String message = !config.skillingOutfitReminder() ? "Outfit reminders disabled."
                : activeSkill == null ? "Start skilling to check your outfit."
                : reminder == null ? activeSkill.getName() + ": outfit ready or dismissed."
                : activeSkill.getName() + ": " + reminder.missing.size() + " outfit pieces missing.";
            showPanelStatus(message);
        }
    }
    private void showPanelStatus(String message)
    {
        if (panelStatus == null || message.equals(lastPanelMessage)) return;
        lastPanelMessage = message;
        SwingUtilities.invokeLater(() ->
        {
            if (message.equals(lastPanelMessage)) panelStatus.setText(message);
        });
    }
    public void onScriptPreFired(ScriptPreFired event)
    {
        if (!config.nodeTimers()) return;
        if (event.getScriptId() != ScriptID.ADD_OVERLAYTIMER_LOC || event.getScriptEvent() == null || client.getGameState() != GameState.LOGGED_IN) { return; }
        Object[] args = event.getScriptEvent().getArguments();
        if (args == null || args.length < 6 || !(args[1] instanceof Integer) || !(args[2] instanceof Integer)
            || !(args[5] instanceof Integer)) { return; }
        int id = (Integer) args[2], ticks = (Integer) args[5];
        boolean tree = NodeTimerRules.TREE_RESPAWNS.contains(id), rock = NodeTimerRules.ROCK_RESPAWNS.contains(id);
        if ((!tree && !rock) || ticks <= 0 || ticks > 10_000) { return; }
        Player player = client.getLocalPlayer();
        if (player == null || player.getWorldView() == null) { return; }
        WorldView view = player.getWorldView();
        WorldPoint point = WorldPoint.fromCoord((Integer) args[1]);
        if (point.getPlane() != view.getPlane() || LocalPoint.fromWorld(view, point) == null) { return; }
        GameObject object = findObject(view, point, -1);
        Node node = new Node(point, view.getId(), object == null ? 1 : object.sizeX(), object == null ? 1 : object.sizeY(),
            rock ? 35 : 80, tree ? "Tree" : "Rock", System.currentTimeMillis(), ticks, true);
        node.motherlode = id == ObjectID.MOTHERLODE_DEPLETED_SINGLE || id == ObjectID.MOTHERLODE_DEPLETED_LEFT
            || id == ObjectID.MOTHERLODE_DEPLETED_MIDDLE || id == ObjectID.MOTHERLODE_DEPLETED_RIGHT;
        respawns.put(point, node);
        if (respawns.size() > 128) { respawns.remove(respawns.keySet().iterator().next()); }
        if (activeNode != null && activeNode.point.equals(point)) { activeNode = null; }
        publishNodes();
    }
    public void onGameObjectDespawned(GameObjectDespawned event)
    { if (config.nodeTimers() && event.getGameObject() == target) { target = null; targetSkill = null; activeNode = null; publishNodes(); } }
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        if (!config.nodeTimers()) return;
        GameObject object = event.getGameObject();
        if (!respawns.containsKey(object.getWorldLocation())) { return; }
        ObjectComposition def = client.getObjectDefinition(object.getId());
        if (def == null || def.getActions() == null) { return; }
        for (String action : def.getActions())
        {
            if (action != null && (action.equalsIgnoreCase("Chop down") || action.equalsIgnoreCase("Mine")))
            { respawns.remove(object.getWorldLocation()); publishNodes(); break; }
        }
    }
    private Node nodeFor(TileObject object, int ticks, boolean respawn)
    {
        ObjectComposition def = client.getObjectDefinition(object.getId());
        return new Node(object.getWorldLocation(), object.getWorldView().getId(), object instanceof GameObject ? ((GameObject) object).sizeX() : 1,
            object instanceof GameObject ? ((GameObject) object).sizeY() : 1, 110, def == null ? "Node" : def.getName(), System.currentTimeMillis(), ticks, respawn);
    }
    private Reminder readReminder()
    {
        if (activeSkill == null || dismissed.contains(activeSkill)) { return null; }
        SkillingOutfits.Outfit outfit = SkillingOutfits.OUTFITS.get(activeSkill);
        if (outfit == null) { return null; }
        List<String> names = new ArrayList<>();
        ItemContainer equipped = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipped == null) { return null; }
        for (Item item : equipped.getItems())
        { if (item != null && item.getId() > 0) { names.add(itemManager.getItemComposition(item.getId()).getName()); } }
        Set<Integer> benefits = new HashSet<>();
        for (SkillingOutfits.Piece piece : outfit.pieces)
        { if (piece.benefitVarbit >= 0 && client.getVarbitValue(piece.benefitVarbit) > 0) { benefits.add(piece.benefitVarbit); } }
        List<SkillingOutfits.Piece> missing = SkillingOutfits.missing(outfit, names, benefits);
        return missing.isEmpty() ? null : new Reminder(activeSkill, outfit.name, missing);
    }
    List<Node> nodes()
    {
        return config.nodeTimers() ? nodeSnapshot : Collections.emptyList();
    }
    private void publishNodes()
    {
        List<Node> nodes = new ArrayList<>(respawns.values());
        if(activeNode != null) nodes.add(activeNode);
        nodeSnapshot = Collections.unmodifiableList(nodes);
    }
    Reminder reminder() { return config.skillingOutfitReminder() ? reminder : null; }
    public void dismissOutfitReminder()
    { if (activeSkill != null) { dismissed.add(activeSkill); reminder = null; } }
    private TileObject findTarget(WorldView view, int plane, int x, int y, int id)
    {
        GameObject object = findObject(view, plane, x, y, id);
        if (object != null) { return object; }
        Tile[][][] tiles = view.getScene().getTiles();
        if (plane < 0 || plane >= tiles.length || x < 0 || x >= tiles[plane].length || y < 0 || y >= tiles[plane][x].length) { return null; }
        Tile tile = tiles[plane][x][y];
        if (tile == null) { return null; }
        if (tile.getWallObject() != null && tile.getWallObject().getId() == id) { return tile.getWallObject(); }
        if (tile.getDecorativeObject() != null && tile.getDecorativeObject().getId() == id) { return tile.getDecorativeObject(); }
        if (tile.getGroundObject() != null && tile.getGroundObject().getId() == id) { return tile.getGroundObject(); }
        return null;
    }
    private GameObject findObject(WorldView view, WorldPoint point, int id)
    {
        LocalPoint local = LocalPoint.fromWorld(view, point);
        return local == null ? null : findObject(view, point.getPlane(), local.getSceneX(), local.getSceneY(), id);
    }
    private GameObject findObject(WorldView view, int plane, int x, int y, int id)
    {
        Tile[][][] tiles = view.getScene().getTiles();
        if (plane < 0 || plane >= tiles.length || x < 0 || x >= tiles[plane].length || y < 0 || y >= tiles[plane][x].length) { return null; }
        Tile tile = tiles[plane][x][y];
        if (tile == null) { return null; }
        for (GameObject object : tile.getGameObjects()) { if (object != null && (id == -1 || object.getId() == id)) { return object; } }
        return null;
    }
    public JPanel createPanel()
    {
        JPanel panel = SidebarUi.column();
        JPanel options = SidebarUi.section("SKILLING HUD");
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        JCheckBox timers = new JCheckBox("Node timers", config.nodeTimers());
        JCheckBox outfits = new JCheckBox("Outfit reminders", config.skillingOutfitReminder());
        for (JCheckBox box : List.of(timers, outfits)) { box.setOpaque(false); box.setForeground(Color.WHITE); box.setFont(font); options.add(box); }
        timers.addActionListener(e -> configManager.setConfiguration("runewarden", "nodeTimers", timers.isSelected()));
        outfits.addActionListener(e -> configManager.setConfiguration("runewarden", "skillingOutfitReminder", outfits.isSelected()));
        JComboBox<RunewardenConfig.TimerDisplay> display=new JComboBox<>(RunewardenConfig.TimerDisplay.values());
        display.setName("skilling.node-timer-display");display.setSelectedItem(NodeTimerOverlay.display(config.nodeTimerDisplay()));
        display.addActionListener(event->configManager.setConfiguration(RunewardenConfig.GROUP,"nodeTimerDisplay",display.getSelectedItem()));
        options.add(SidebarUi.field("Node countdown",display));
        panel.add(options);
        JPanel current = SidebarUi.section("CURRENT SESSION");
        panelStatus = SidebarUi.label("Start skilling to check your outfit.");
        current.add(panelStatus);
        JButton dismiss = SidebarUi.button("Dismiss outfit reminder", this::dismissOutfitReminder);
        current.add(dismiss); panel.add(current);
        return panel;
    }
}
