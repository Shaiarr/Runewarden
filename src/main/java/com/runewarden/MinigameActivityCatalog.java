/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

/**
 * Reads public minigame interfaces and scene evidence on the client thread.
 * Object observations are supplied by scene entry/events; this class never scans a scene.
 * Reference tiles use template-world coordinates, including in instances.
 */
@Singleton
public final class MinigameActivityCatalog
{
    public enum Activity
    {
        WINTERTODT, GOTR, TEMPOROSS, GIANTS_FOUNDRY, MIXOLOGY, MAHOGANY_HOMES,
        PEST_CONTROL, BLAST_FURNACE, TITHE_FARM, BARBARIAN_ASSAULT,
        NIGHTMARE_ZONE, PYRAMID_PLUNDER, MAGE_TRAINING_ARENA, VOLCANIC_MINE,
        BLAST_MINE, FISHING_TRAWLER, DRIFT_NET, TROUBLE_BREWING, SOUL_WARS, CASTLE_WARS
    }

    public static final class Fact
    {
        public final String label, value;
        public Fact(String label, String value) { this.label = label; this.value = value; }
    }

    public static final class Step
    {
        public final String title, instruction;
        public Step(String title, String instruction) { this.title = title; this.instruction = instruction; }
    }

    public static final class Snapshot
    {
        public static final Snapshot EMPTY = new Snapshot(null, "", null, Collections.emptyList(),
            Collections.emptyList(), Collections.emptySet(), Collections.emptyList(), false);
        public final Activity activity;
        public final String title, guideId;
        public final List<Fact> facts;
        public final List<Step> steps;
        /** Current suggested targets; the complete evidence union is allHighlightObjectIds(). */
        public final Set<Integer> highlightObjectIds;
        /** Actual native NPC forms relevant to the current objective. */
        public final Set<Integer> highlightNpcIds;
        /** Fixed template-world references; renderers must resolve instanced copies. */
        public final List<WorldPoint> referenceTiles;
        public final boolean liveSupported;
        /** Explicit source-backed progress, independent of manual checklist navigation. */
        public final String guideStepId, objectiveKey;
        public final boolean terminal;
        public final Set<String> confirmedStepIds;

        public Snapshot(Activity activity, String title, String guideId, List<Fact> facts,
            List<Step> steps, Set<Integer> highlightObjectIds, List<WorldPoint> referenceTiles,
            boolean liveSupported)
        { this(activity,title,guideId,facts,steps,highlightObjectIds,referenceTiles,liveSupported,Collections.emptySet()); }
        public Snapshot(Activity activity,String title,String guideId,List<Fact> facts,List<Step> steps,
            Set<Integer> highlightObjectIds,List<WorldPoint> referenceTiles,boolean liveSupported,Set<Integer> highlightNpcIds)
        { this(activity,title,guideId,facts,steps,highlightObjectIds,referenceTiles,liveSupported,highlightNpcIds,null,"",false,Collections.emptySet()); }
        private Snapshot(Activity activity,String title,String guideId,List<Fact> facts,List<Step> steps,
            Set<Integer> highlightObjectIds,List<WorldPoint> referenceTiles,boolean liveSupported,Set<Integer> highlightNpcIds,
            String guideStepId,String objectiveKey,boolean terminal,Set<String> confirmedStepIds)
        {
            this.guideStepId=guideStepId;this.objectiveKey=objectiveKey==null?"":objectiveKey;this.terminal=terminal;
            this.confirmedStepIds=Collections.unmodifiableSet(new LinkedHashSet<>(confirmedStepIds));
            this.activity = activity; this.title = title; this.guideId = guideId;
            this.facts = Collections.unmodifiableList(new ArrayList<>(facts));
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
            this.highlightObjectIds = Collections.unmodifiableSet(new LinkedHashSet<>(highlightObjectIds));
            this.highlightNpcIds = Collections.unmodifiableSet(new LinkedHashSet<>(highlightNpcIds));
            this.referenceTiles = Collections.unmodifiableList(new ArrayList<>(referenceTiles));
            this.liveSupported = liveSupported;
        }
        public Snapshot withGuideProgress(String stepId,String key,boolean done,Set<String> confirmed)
        {return new Snapshot(activity,title,guideId,facts,steps,highlightObjectIds,referenceTiles,liveSupported,highlightNpcIds,
            stepId,key,done,confirmed==null?Collections.emptySet():confirmed);}
        Snapshot preserveGuideProgress(Snapshot source)
        {return withGuideProgress(source.guideStepId,source.objectiveKey,source.terminal,source.confirmedStepIds);}
    }

    private static final int MAX_OBJECTS = 512;
    private static final Set<Integer> WINTER_OBJECTS = ids(29311, 29312, 29313, 29314);
    private static final Set<Integer> GOTR_OBJECTS = ids(43754, 43732, 43696, 43729, 43722, 43723, 43715, 43716, 43717, 43718, 43719, 43720,
        43701, 43702, 43703, 43704, 43705, 43706, 43707, 43708, 43709, 43710, 43711, 43712,
        43736, 43737, 43739, 43740, 43741, 43742, 43743);
    private static final Set<Integer> TEMPOROSS_OBJECTS = ids(37582,41006,41007,40964, 40965, 40966, 40967,
        40968, 40969, 40970, 40971, 40972, 40973, 40974, 40975, 40976, 40977, 40978, 40979,
        40996, 40997, 40998, 40999, 41000, 41001, 41002, 41003, 41004, 41010, 41011, 41012,
        41233, 41234, 41235, 41236, 41238, 41239, 41240, 41241, 41352, 41353, 41354, 41355);
    private static final Set<Integer> FOUNDRY_OBJECTS = ids(44619, 44620, 44621, 44622, 44623,
        44624, 44625, 44626, 44627, 44628, 44629, 44630, 44631, 44632, 44637, 44638,
        44776, 44777, 44778);
    private static final Set<Integer> MIXOLOGY_OBJECTS = mixologyObjects();
    private static final Set<Integer> BLAST_OBJECTS = ids(9100, 9092, 9093, 9095, 9096);
    private static final Set<Integer> TITHE_OBJECTS = range(27383, 27416);
    private static final int[][] HOME_OBJECTS = {
        {39981,39989,39997,40002,40007,40011,40083,40156,40164,40171,40296,40297},
        {39982,39990,39998,40008,40084,40089,40095,40157,40165,40172,40287,40293},
        {39983,39991,39999,40003,40012,40085,40090,40096,40158,40166,40173,40290},
        {39984,39992,40000,40086,40091,40097,40159,40167,40174,40288,40291,40294},
        {39985,39993,40004,40009,40013,40087,40092,40160,40168,40175,40286,40298},
        {39986,39994,40001,40005,40010,40014,40088,40093,40098,40161,40169,40176},
        {39987,39995,40006,40015,40094,40099,40162,40170,40177,40292,40295},
        {39988,39996,40163,40289,40299}
    };
    private static final Set<Integer> HOME_ALL = homeObjects();
    private static final Set<Integer> ALL_OBJECTS = allObjects();
    private final Map<GameObject, Observed> observed = new IdentityHashMap<>();
    private String lastBlastGuide;
    private final MinigameHomes homeAssignment=new MinigameHomes();
    private final MinigameFoundry.CommissionProgress foundryCommission=new MinigameFoundry.CommissionProgress();
    private final MinigameTelekinetic telekinetic=new MinigameTelekinetic();
    private final MinigameGuideProgress guideProgress=new MinigameGuideProgress();
    MinigameHomes.State homeState(){return homeAssignment.state();}
    void restoreHome(MinigameHomes.State state){homeAssignment.restore(state);}
    boolean homeDirty(){return homeAssignment.takeDirty();}
    void clearAccount(){homeAssignment.clear();telekinetic.clear();guideProgress.clear();foundryCommission.clear();}
    void onHomeMessage(String message){guideProgress.onMessage(message,homeAssignment.guideId());homeAssignment.observe(message,true);}
    WorldPoint homeDestination(){MinigameHomes.Customer customer=homeAssignment.assigned();return customer==null?null:customer.destination;}
    private volatile MinigameNativeObjectives.Options options=MinigameNativeObjectives.Options.DEFAULT;
    void setOptions(int fragmentTarget,int warmthThreshold,int exitSeconds)
    { options=new MinigameNativeObjectives.Options(fragmentTarget,warmthThreshold,exitSeconds); }
    void setOptions(int fragments,int warmth,int exit,String role,boolean fletch,boolean cook,int fishBatch)
    {options=new MinigameNativeObjectives.Options(fragments,warmth,exit,role,fletch,cook,fishBatch);}

    @Inject public MinigameActivityCatalog() { }

    public void clearScene() { observed.clear(); lastBlastGuide = null; telekinetic.clear(); }
    public void onGameObjectSpawned(GameObject object)
    {
        if (object == null || !ALL_OBJECTS.contains(object.getId())) return;
        if (observed.size() >= MAX_OBJECTS && !observed.containsKey(object)) return;
        WorldPoint location = object.getWorldLocation();
        if (location != null) observed.put(object, new Observed(object.getId(), location));
    }
    public void onGameObjectDespawned(GameObject object) { observed.remove(object); }
    public Set<Integer> allHighlightObjectIds() { return ALL_OBJECTS; }

    public List<Snapshot> supportedActivities() { return SUPPORTED; }

    public Snapshot detect(Client client)
    {
        if(client!=null&&client.getGameState()==GameState.LOGGED_IN&&client.getLocalPlayer()!=null)homeAssignment.observeDialog(client);
        Snapshot terminal = guideProgress.terminal(client);
        if (terminal != null) return terminal;
        Snapshot snapshot = detectCurrent(client);
        if (snapshot.activity != Activity.BLAST_FURNACE) lastBlastGuide = null;
        if (snapshot.activity != Activity.GIANTS_FOUNDRY) foundryCommission.clear();
        if(snapshot.activity==null)return guideProgress.observe(snapshot,client);
        snapshot=MinigameNativeObjectives.enrich(snapshot,client,nearbyIds(client,40),options);
        return guideProgress.observe(telekinetic.enrich(snapshot,client),client);
    }

    Snapshot annotateGuideProgress(Snapshot snapshot){return guideProgress.annotate(snapshot);}

    private Set<Integer> nearbyIds(Client client,int distance)
    {
        Set<Integer> result=new LinkedHashSet<>();WorldPoint player=client.getLocalPlayer().getWorldLocation();
        if(player!=null)for(Observed object:observed.values())if(player.distanceTo(object.location)<=distance)result.add(object.id);
        return result;
    }

    private Snapshot detectCurrent(Client client)
    {
        if (client == null || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
            return Snapshot.EMPTY;
        if (visible(client, 396, 2)) return wintertodt(client);
        if (visible(client, 746, 2)) return gotr(client);
        if (visible(client, 437, 2)) return tempoross(client);
        if (visible(client, 754, 51) || nearby(client, ids(44621), 24)) return foundry(client);
        if (visible(client, 882, 2)) return mixology(client);
        Snapshot home = homes(client);
        if (home.activity != null) return home;
        if (visible(client, 408, 14)) return pestControl(client);
        if (visible(client, 474, 0) || nearby(client, BLAST_OBJECTS, 16)) return blastFurnace(client);
        if (client.isInInstancedRegion() && nearby(client, TITHE_OBJECTS, 40)) return tithe(client);
        if (client.isInInstancedRegion())
            for (int group = 485; group <= 488; group++)
                if (visible(client, group, 5)) return barbarianAssault(client, group);
        Snapshot extra = MinigameExtraActivities.detect(client);
        if(extra.activity!=null)return extra;
        Snapshot more=MinigameMoreActivities.detect(client);
        return more.activity!=null?more:homeAssignment.travel(client);
    }

    private Snapshot wintertodt(Client client)
    {
        List<Fact> facts = facts(client, 396, "Warmth", 15, "Energy", 21, "Points", 6, "Round", 2);
        if (nearby(client, ids(29313), 4))
            return snapshot(Activity.WINTERTODT, facts, "Repair the brazier", "Use a hammer to repair the broken brazier before feeding it.", ids(29313));
        if (nearby(client, ids(29312), 4))
            return snapshot(Activity.WINTERTODT, facts, "Light the brazier", "Use a tinderbox to light the unlit brazier before feeding roots or kindling.", ids(29312));
        if (count(client, 20696) > 0)
            return snapshot(Activity.WINTERTODT, facts, "Feed your kindling", "Feed the lit brazier; keep your warmth above zero and eat when needed.", ids(29314));
        if (count(client, 20695) > 0)
            return snapshot(Activity.WINTERTODT, facts, "Fletch or feed bruma roots", "Fletch roots into kindling for more points, then feed a lit brazier.", ids(29314));
        return snapshot(Activity.WINTERTODT, facts, "Chop bruma roots", "Gather roots from the bruma tree, then fletch and feed a lit brazier.", ids(29311));
    }

    private Snapshot gotr(Client client)
    {
        List<Fact> facts = facts(client, 746, "Round", 3, "Portal", 5, "Portal position", 28);
        facts.add(new Fact("Elemental energy", String.valueOf(client.getVarbitValue(13686))));
        facts.add(new Fact("Catalytic energy", String.valueOf(client.getVarbitValue(13685))));
        if (count(client, 26880, 26881, 26941, 30887) > 0)
            return snapshot(Activity.GOTR, facts, "Empower the Great Guardian", "Take your guardian stones to the Great Guardian in the centre.", Collections.emptySet());
        if (count(client, 26879) > 0)
            return snapshot(Activity.GOTR, facts, "Use an active altar", "Enter one of the active altar rifts shown by the native elemental and catalytic displays; craft your guardian essence.", Collections.emptySet());
        if (count(client, 26878) > 0)
            return snapshot(Activity.GOTR, facts, "Craft guardian essence", "Use the workbench to turn guardian fragments into essence, then enter an active altar rift.", ids(43754));
        return snapshot(Activity.GOTR, facts, "Gather guardian fragments", "Mine the guardian remains available on your side of the arena. Large remains require the 56 Agility shortcut; huge remains require the currently active portal.", guardianRemains(client));
    }

    private Set<Integer> guardianRemains(Client client)
    {
        // Tier-1 large model IDs are ordinary remains, not the Agility-gated tier-2 node.
        Set<Integer> result = new LinkedHashSet<>(ids(43717,43718));
        if (client.getRealSkillLevel(net.runelite.api.Skill.AGILITY) >= 56) result.add(43719);
        WorldPoint p = client.getLocalPlayer().getWorldLocation();
        // The huge-remains shelf is reached through a portal; nearby across the wall is insufficient.
        if (p != null && p.getX() > 3587 && p.getX() < 3594 && p.getY() > 9496 && p.getY() < 9516)
            return ids(43720);
        return result;
    }

    private Snapshot tempoross(Client client)
    {
        List<Fact> facts = facts(client, 437, "Energy", 19, "Essence", 21, "Storm", 23, "Points", 25);
        if (client.getVarbitValue(11895) > 0)
            return snapshot(Activity.TEMPOROSS, facts, "Remain tethered", "Stay secured while the wave passes; resume fishing once it is safe to untether.", Collections.emptySet());
        String energy = widgetText(client, 437, 19);
        if (zeroPercent(energy))
            return snapshot(Activity.TEMPOROSS, facts, "Fish the spirit pool", "Tempoross has no energy: use the spirit pool to reduce its essence.", Collections.emptySet());
        if (count(client, 25565, 25566) > 0)
            return snapshot(Activity.TEMPOROSS, facts, "Load cooked harpoonfish", "Deposit cooked harpoonfish into your ship's ammunition crate.", ids(40968,40969,40970,40971,40972,40973,40974,40975,40976,40977,40978,40979));
        if (count(client, 25564) > 0)
            return snapshot(Activity.TEMPOROSS, facts, "Cook harpoonfish", "Cook your raw harpoonfish at the shrine before loading the ammunition crate.", ids(41236));
        return snapshot(Activity.TEMPOROSS, facts, "Catch harpoonfish", "Fish the harpoonfish shoals. Keep a rope and water available for the encounter's hazards.", Collections.emptySet());
    }

    private Snapshot foundry(Client client)
    {
        int progress=client.getVarbitValue(net.runelite.api.gameval.VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION),tool=foundryTool(client,progress);
        return MinigameFoundry.read(client,foundryCommission,tool,foundryHeatBand(client,tool));
    }

    private static final String[] POTION_NAMES = {"Mammoth might mix", "Mystic mana amalgam", "Marley's moonlight", "Alco-augmentator", "Azure aura mix", "Aqualux amalgam", "Liplack liquor", "Megalite liquid", "Anti-leech lotion", "Mixalot"};
    private static final String[] RECIPES = {"Mox + Mox + Mox", "Mox + Mox + Aga", "Mox + Mox + Lye", "Aga + Aga + Aga", "Aga + Aga + Mox", "Aga + Aga + Lye", "Lye + Lye + Lye", "Lye + Lye + Mox", "Lye + Lye + Aga", "Mox + Aga + Lye"};
    private static final int[] UNFINISHED = {30011,30012,30013,30014,30016,30015,30017,30019,30018,30020};
    private static final int[] FINISHED = {30021,30022,30023,30024,30026,30025,30027,30029,30028,30030};
    private static final String[] MODIFIERS = {"Agitator", "Retort", "Alembic"};

    private Snapshot mixology(Client client)
    {
        List<Fact> facts = new ArrayList<>();
        facts.add(new Fact("Paste", "Mox " + client.getVarbitValue(11431) + " · Aga " + client.getVarbitValue(11432) + " · Lye " + client.getVarbitValue(11433)));
        int selected = -1, modifier = -1;
        for (int i = 0; i < 3; i++)
        {
            int potion = client.getVarbitValue(11315 + i * 2) - 1, mod = client.getVarbitValue(11316 + i * 2) - 1;
            if (potion < 0 || potion >= POTION_NAMES.length || mod < 0 || mod >= MODIFIERS.length) continue;
            facts.add(new Fact("Order " + (i + 1), POTION_NAMES[potion] + " · " + MODIFIERS[mod]));
            if (selected < 0) { selected = potion; modifier = mod; }
            if (count(client, FINISHED[potion]) > 0)
                return snapshot(Activity.MIXOLOGY, facts, "Deliver the finished order", "Place " + POTION_NAMES[potion] + " on the conveyor belt to submit the order.", ids(54917), point(1394,9331));
            if (count(client, UNFINISHED[potion]) > 0) { selected = potion; modifier = mod; break; }
        }
        if (selected >= 0 && count(client, UNFINISHED[selected]) > 0)
        {
            int station = modifier == 0 ? 55390 : modifier == 1 ? 55389 : 55391;
            WorldPoint tile = modifier == 0 ? point(1393,9329) : modifier == 1 ? point(1397,9325) : point(1391,9325);
            return snapshot(Activity.MIXOLOGY, facts, "Use the " + MODIFIERS[modifier].toLowerCase(java.util.Locale.ROOT), "Process " + POTION_NAMES[selected] + " at the station requested by the current order.", ids(station), tile);
        }
        if (selected >= 0)
            return snapshot(Activity.MIXOLOGY, facts, "Mix " + POTION_NAMES[selected], RECIPES[selected] + ". Pull the matching levers, then collect the unfinished potion from the mixing vessel.", ids(54867,54868,54869,55395), point(1394,9326));
        return snapshot(Activity.MIXOLOGY, facts, "Prepare the mixing station", "Keep Mox, Aga and Lye paste in the hopper, then follow a displayed potion order.", ids(54903), point(1394,9322));
    }

    private Snapshot homes(Client client)
    {
        WorldPoint location = client.getLocalPlayer().getWorldLocation();
        MinigameHomes.Customer confirmed=homeAssignment.assigned();
        if ((client.getVarpValue(2747)<=0||!nearby(client,HOME_ALL,16)) && (confirmed==null||!confirmed.contains(location))) return Snapshot.EMPTY;
        Home house = null;
        for (Home home : HOMES) if (home.contains(location)) { house = home; break; }
        if (house == null) return Snapshot.EMPTY;
        MinigameHomes.Customer assigned=homeAssignment.assigned();
        if(assigned!=null&&!assigned.name.equals(house.name))return Snapshot.EMPTY;
        Set<Integer> targets = new LinkedHashSet<>();
        int remaining = 0, first = 0, builtTier = 0;
        int[] states = new int[8];
        for (int i = 0; i < HOME_OBJECTS.length; i++)
        {
            states[i] = client.getVarbitValue(10554 + i);
            if (states[i] >= 5 && states[i] <= 8) builtTier = Math.max(builtTier,states[i] - 4);
        }
        // 2747 contains furniture states, not an assigned customer. Prove an actual task
        // in this house from the resolved native object's currently available action.
        for (Observed object : observed.values())
        {
            if (!house.contains(object.location) || location.distanceTo(object.location) > 16) continue;
            int slot = homeSlot(object.id);
            if (slot < 0) continue;
            int state = states[slot];
            String action = state == 1 ? "Repair" : state == 3 ? "Remove" : state == 4 ? "Build" : null;
            if (action == null || !hasAction(client,object.id,action)) continue;
            if (targets.add(object.id)) { remaining++; if (first == 0) first = state; }
        }
        if (targets.isEmpty())
        {
            int unfinished=0,unfinishedState=0;
            for(int state:states)if(state==1||state==3||state==4){unfinished++;if(unfinishedState==0)unfinishedState=state;}
            if(assigned==null)return Snapshot.EMPTY;
            if(unfinished==0)
            {
                boolean built=false;for(int state:states)if(state>=5&&state<=8)built=true;
                if(built)return homeAssignment.travel(client);
                return withGuide(snapshot(Activity.MAHOGANY_HOMES,homeAssignment.facts(),"Check the assigned furniture",
                    "Speak to your assigned customer and continue the contract furniture. The current furniture state is not available yet.",Collections.emptySet()),homeAssignment.guideId());
            }
            String pending=unfinishedState==1?"Repair the furniture":unfinishedState==3?"Remove damaged furniture":"Build the replacement";
            List<Fact> pendingFacts=new ArrayList<>(homeAssignment.facts());
            pendingFacts.add(new Fact("Unfinished furniture",Integer.toString(unfinished)));
            return withGuide(snapshot(Activity.MAHOGANY_HOMES,pendingFacts,pending,
                "Your assigned contract still has unfinished furniture. Continue the remaining repairs in this house.",Collections.emptySet()),homeAssignment.guideId());
        }
        List<Fact> facts = new ArrayList<>(homeAssignment.facts()); facts.add(new Fact("House", house.name));
        facts.add(new Fact("Nearby actionable furniture", String.valueOf(remaining)));
        String guide = homeAssignment.guideId()!=null?homeAssignment.guideId():builtTier == 0 ? null : "training:homes-" + new String[]{"beginner","novice","adept","expert"}[builtTier - 1];
        return withGuide(snapshot(Activity.MAHOGANY_HOMES, facts, first == 1 ? "Repair the furniture" : first == 3 ? "Remove damaged furniture" : "Build the replacement",
            "Work through the highlighted contract furniture using the planks and other materials required by your contract tier.", targets),guide);
    }

    private static int homeSlot(int objectId)
    {
        for (int slot=0;slot<HOME_OBJECTS.length;slot++)
            for (int id : HOME_OBJECTS[slot]) if (id == objectId) return slot;
        return -1;
    }
    private static boolean hasAction(Client client,int objectId,String action)
    {
        ObjectComposition definition=client.getObjectDefinition(objectId);
        if (definition != null && definition.getImpostorIds() != null) definition=definition.getImpostor();
        if (definition == null || definition.getActions() == null) return false;
        for (String candidate : definition.getActions()) if (action.equalsIgnoreCase(candidate)) return true;
        return false;
    }

    private Snapshot pestControl(Client client)
    {
        List<Fact> facts = facts(client, 408, "Time", 5, "Void Knight", 6, "Damage", 7);
        facts.add(new Fact("Activity", String.valueOf(client.getVarbitValue(5662))));
        return snapshot(Activity.PEST_CONTROL, facts, "Defend the Void Knight", "Attack an unshielded portal when the native shield disappears. Clear nearby enemies and protect the Void Knight.", Collections.emptySet());
    }

    private Snapshot blastFurnace(Client client)
    {
        int bars = 0;
        for (int bit = 941; bit <= 948; bit++) bars += Math.max(0, client.getVarbitValue(bit));
        List<Fact> facts = new ArrayList<>(); facts.add(new Fact("Bars in dispenser", String.valueOf(bars)));
        facts.add(new Fact("Coal stored", String.valueOf(client.getVarbitValue(949))));
        if (bars > 0)
            return withGuide(snapshot(Activity.BLAST_FURNACE, facts, "Collect your bars", client.getVarbitValue(936) > 0
                ? "Cool the hot bars with water or wear ice gloves, then collect them from the dispenser."
                : "Collect your cooled bars from the dispenser before the next batch.", ids(9092,9093,9095,9096)),blastGuide(client));
        return withGuide(snapshot(Activity.BLAST_FURNACE, facts, "Load the conveyor belt", "Take your batch of ore to the conveyor belt; supply coal when your chosen bar requires it.", ids(9100)),blastGuide(client));
    }

    private static final Set<Integer> TITHE_DRY = ids(27384,27387,27390,27395,27398,27401,27406,27409,27412);
    private static final Set<Integer> TITHE_GROWN = ids(27393,27404,27415);
    private static final Set<Integer> TITHE_DEAD = ids(27386,27389,27392,27394,27397,27400,27403,27405,27408,27411,27414,27416);

    private Snapshot tithe(Client client)
    {
        int dry = observedCount(client, TITHE_DRY, 40), grown = observedCount(client, TITHE_GROWN, 40), dead = observedCount(client, TITHE_DEAD, 40);
        List<Fact> facts = new ArrayList<>(); facts.add(new Fact("Round score", String.valueOf(client.getVarbitValue(4900))));
        facts.add(new Fact("Nearby plants", dry + " dry · " + grown + " grown · " + dead + " dead"));
        if (dry > 0) return snapshot(Activity.TITHE_FARM, facts, "Water dry plants", "Use your watering can on the dry plants before they die.", TITHE_DRY);
        if (grown > 0) return snapshot(Activity.TITHE_FARM, facts, "Harvest mature fruit", "Harvest the grown plants, then deposit the fruit in the sack.", TITHE_GROWN);
        if (dead > 0) return snapshot(Activity.TITHE_FARM, facts, "Clear dead plants", "Clear dead plants so the patches can be replanted.", TITHE_DEAD);
        return snapshot(Activity.TITHE_FARM, facts, "Plant and water", "Plant your selected seed in an empty patch, then water it immediately.", ids(27383));
    }

    private Snapshot barbarianAssault(Client client, int group)
    {
        String role = group == 485 ? "Attacker" : group == 486 ? "Collector" : group == 487 ? "Defender" : "Healer";
        boolean attacker = group == 485;
        List<Fact> facts = new ArrayList<>(); facts.add(new Fact("Role", role));
        addWidgetFact(facts, client, "Wave", group, 5);
        addWidgetFact(facts, client, "Heard call", group, attacker ? 8 : 7);
        addWidgetFact(facts, client, "Call to send", group, attacker ? 10 : 9);
        return snapshot(Activity.BARBARIAN_ASSAULT, facts, "Call and follow your role", "Use your horn to send the current call to your partner, then follow your received call for the " + role.toLowerCase(java.util.Locale.ROOT) + " role.", Collections.emptySet());
    }

    private static final List<Snapshot> DEFINITIONS = definitions();
    private static final List<Snapshot> SUPPORTED = supportedDefinitions();
    private static List<Snapshot> supportedDefinitions()
    { List<Snapshot> list = new ArrayList<>(DEFINITIONS); list.addAll(MinigameExtraActivities.supportedActivities()); list.addAll(MinigameMoreActivities.supportedActivities()); return Collections.unmodifiableList(list); }
    private static List<Snapshot> definitions()
    {
        List<Snapshot> list = new ArrayList<>();
        list.add(definition(Activity.WINTERTODT, "Wintertodt", "training:wintertodt"));
        list.add(definition(Activity.GOTR, "Guardians of the Rift", "training:guardians-rift"));
        list.add(definition(Activity.TEMPOROSS, "Tempoross", "training:tempoross"));
        list.add(definition(Activity.GIANTS_FOUNDRY, "Giants' Foundry", "training:giants-foundry"));
        list.add(definition(Activity.MIXOLOGY, "Mastering Mixology", "training:mastering-mixology"));
        list.add(definition(Activity.MAHOGANY_HOMES, "Mahogany Homes", null));
        list.add(definition(Activity.PEST_CONTROL, "Pest Control", "training:pest-control-experience"));
        list.add(definition(Activity.BLAST_FURNACE, "Blast Furnace", null));
        list.add(definition(Activity.TITHE_FARM, "Tithe Farm", "training:tithe-farm"));
        list.add(definition(Activity.BARBARIAN_ASSAULT, "Barbarian Assault", "minigame:barbarian-assault"));
        return Collections.unmodifiableList(list);
    }
    private static Snapshot withGuide(Snapshot snapshot,String guide)
    { return new Snapshot(snapshot.activity,snapshot.title,guide,snapshot.facts,snapshot.steps,snapshot.highlightObjectIds,snapshot.referenceTiles,snapshot.liveSupported,snapshot.highlightNpcIds).preserveGuideProgress(snapshot); }
    private String blastGuide(Client client)
    {
        Set<String> candidates = new LinkedHashSet<>();
        String[] methods = {"steel","mithril","adamant","rune","gold"};
        int[] oreIds = {0,447,449,451,444}, barIds = {2353,2359,2361,2363,2357};
        for (int i = 0; i < methods.length; i++)
            if (client.getVarbitValue(943+i) > 0 || count(client,barIds[i]) > 0 || oreIds[i] != 0 && count(client,oreIds[i]) > 0)
                candidates.add("training:blast-"+methods[i]);
        // Iron ore alone cannot establish iron versus steel. Mixed native batches remain unselected.
        if (candidates.size() == 1) lastBlastGuide = candidates.iterator().next();
        return lastBlastGuide;
    }

    private static Snapshot definition(Activity activity, String title, String guideId)
    { return new Snapshot(activity,title,guideId,Collections.emptyList(),Collections.emptyList(),Collections.emptySet(),Collections.emptyList(),true); }
    private static Snapshot snapshot(Activity activity, List<Fact> facts, String title, String instruction, Set<Integer> objects, WorldPoint... tiles)
    {
        Snapshot definition = DEFINITIONS.stream().filter(value -> value.activity == activity).findFirst().orElseThrow(IllegalArgumentException::new);
        return new Snapshot(activity,definition.title,definition.guideId,facts,Collections.singletonList(new Step(title,instruction)),objects,Arrays.asList(tiles),true);
    }
    private static Set<Integer> ids(int... ids)
    { Set<Integer> result = new LinkedHashSet<>(); for (int id : ids) result.add(id); return Collections.unmodifiableSet(result); }
    private static Set<Integer> range(int from, int to)
    { Set<Integer> result = new LinkedHashSet<>(); for (int id = from; id <= to; id++) result.add(id); return Collections.unmodifiableSet(result); }
    private static Set<Integer> mixologyObjects()
    { Set<Integer> result = new LinkedHashSet<>(range(54867,54904)); result.add(54917); result.addAll(range(54918,54928)); result.addAll(ids(55389,55390,55391,55395)); return Collections.unmodifiableSet(result); }
    private static Set<Integer> homeObjects()
    { Set<Integer> result = new LinkedHashSet<>(); for (int[] objects : HOME_OBJECTS) for (int id : objects) result.add(id); return Collections.unmodifiableSet(result); }
    private static Set<Integer> allObjects()
    {
        Set<Integer> result = new LinkedHashSet<>();
        for (Set<Integer> set : Arrays.asList(WINTER_OBJECTS,GOTR_OBJECTS,TEMPOROSS_OBJECTS,FOUNDRY_OBJECTS,MIXOLOGY_OBJECTS,BLAST_OBJECTS,TITHE_OBJECTS,HOME_ALL)) result.addAll(set);
        result.addAll(MinigameNativeObjectives.EXTRA_OBJECTS);
        result.addAll(MinigameExtraActivities.allHighlightObjectIds());
        result.addAll(MinigameMoreActivities.allHighlightObjectIds());
        return Collections.unmodifiableSet(result);
    }
    private boolean nearby(Client client, Set<Integer> ids, int distance) { return observedCount(client,ids,distance) > 0; }
    private int observedCount(Client client, Set<Integer> ids, int distance)
    {
        WorldPoint player = client.getLocalPlayer().getWorldLocation(); if (player == null) return 0;
        int count = 0;
        for (Observed object : observed.values()) if (ids.contains(object.id) && player.distanceTo(object.location) <= distance) count++;
        return count;
    }
    private static final class Observed
    { final int id; final WorldPoint location; Observed(int id,WorldPoint location) { this.id=id; this.location=location; } }
    private static boolean visible(Client client, int group, int child)
    { Widget widget = client.getWidget(group,child); return widget != null && !widget.isHidden(); }
    private static String widgetText(Client client, int group, int child)
    {
        Widget widget = client.getWidget(group,child); if (widget == null || widget.isHidden()) return "";
        String text = widget.getText(); if (text == null) return "";
        if (text.length() > 600) text = text.substring(0,600);
        return text.replace("<br>", " ").replaceAll("<[^>]*>", "").trim();
    }
    private static List<Fact> facts(Client client, int group, Object... fields)
    { List<Fact> facts = new ArrayList<>(); for (int i=0;i<fields.length;i+=2) addWidgetFact(facts,client,(String)fields[i],group,(Integer)fields[i+1]); return facts; }
    private static void addWidgetFact(List<Fact> facts, Client client, String label, int group, int child)
    { String text=widgetText(client,group,child); if (!text.isEmpty()) facts.add(new Fact(label,text)); }
    private static int count(Client client, int... ids)
    {
        ItemContainer container = client.getItemContainer(net.runelite.api.gameval.InventoryID.INV);
        if (container == null || container.getItems() == null) return 0;
        long result=0; Item[] items=container.getItems();
        for (int i=0;i<Math.min(items.length,28);i++) if (items[i] != null)
            for (int id : ids) if (items[i].getId() == id) { result += Math.max(0,items[i].getQuantity()); break; }
        return (int)Math.min(Integer.MAX_VALUE,result);
    }
    private static String percent1000(int value) { return String.format(java.util.Locale.ROOT,"%.1f%%", Math.max(0,Math.min(1000,value))/10.0); }
    private static boolean zeroPercent(String text) { return text.matches("(?i).*?(?<![0-9])0\\s*%.*"); }
    // Verified against EasyGiantsFoundryState.getCurrentStage() at 9324c7f55cba4c0fc5245a7bc85f3db521a8cceb.
    private static int foundryTool(Client client,int progress)
    {
        Widget bar=client.getWidget(754,75); if (bar==null || bar.getChildren()==null) return 0;
        List<Integer> tools=new ArrayList<>(); Widget[] children=bar.getChildren();
        for(int i=0;i<Math.min(64,children.length);i++) if(children[i]!=null)
        { int sprite=children[i].getSpriteId(); if(sprite==4442)tools.add(44619);else if(sprite==4443)tools.add(44620);else if(sprite==4444)tools.add(44621); }
        if(tools.isEmpty())return 0; int index=Math.min(tools.size()-1,Math.max(0,progress)*tools.size()/1000); return tools.get(index);
    }
    private static int[] foundryHeatBand(Client client,int tool)
    {
        if(tool!=44619&&tool!=44620&&tool!=44621)return null;
        Widget gauge=client.getWidget(754,9),medium=client.getWidget(754,20);
        if(gauge==null||medium==null||gauge.getWidth()<=0||medium.getWidth()<=0||medium.getWidth()>gauge.getWidth())return null;
        double centre=tool==44619?5.0/6:tool==44620?3.0/6:1.0/6;
        double half=(double)medium.getWidth()/gauge.getWidth()/2;
        return new int[]{(int)Math.ceil((centre-half)*1000),(int)Math.floor((centre+half)*1000)};
    }
    private static WorldPoint point(int x,int y) { return new WorldPoint(x,y,0); }
    private static final class Home
    {
        final String name; final int x,y,width,height;
        Home(String name,int x,int y,int width,int height){this.name=name;this.x=x;this.y=y;this.width=width;this.height=height;}
        boolean contains(WorldPoint p){return p!=null&&p.getX()>=x&&p.getX()<x+width&&p.getY()>=y&&p.getY()<y+height&&p.getPlane()<=1;}
    }
    private static final Home[] HOMES={new Home("Jess",2611,3290,14,7),new Home("Noella",2652,3317,15,8),new Home("Ross",2609,3313,11,9),
        new Home("Larry",3033,3360,10,9),new Home("Norman",3034,3341,8,8),new Home("Tau",3043,3340,10,11),
        new Home("Barbara",1746,3531,10,11),new Home("Leela",1781,3589,9,8),new Home("Mariah",1762,3618,10,7),
        new Home("Bob",3234,3482,10,10),new Home("Jeff",3235,3445,10,12),new Home("Sarah",3232,3381,8,7)};
}
