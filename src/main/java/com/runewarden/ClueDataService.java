/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.ClueScrollService;
import net.runelite.client.plugins.cluescrolls.clues.*;
import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;

/** Snapshot adapter over public native clue models. All client reads occur on the game thread. */
@Singleton
final class ClueDataService
{
    static final String GROUP="runewardenclues", STASH_KEY="stashSeenV1";
    private static final int MAX_JSON=64000;
    private final Client client;
    private final PluginManager plugins;
    private final OwnershipLedger ownership;
    private final ConfigManager config;
    private final Gson gson;
    private final RunewardenDataStore store;
    private final ClueAccessIndex access;
    private final ItemRequirement spade,light;
    private final Map<String,StashReference> references=new LinkedHashMap<>();
    private final Map<String,StashState> stash=new LinkedHashMap<>();
    private final Set<Integer> knownItemIds=new LinkedHashSet<>();
    private volatile String profile;
    private int warmup;
    private boolean chartWasOpen;
    private ClueScroll lastClue;
    private int lastRequirementCount=-1;
    private List<Map<Integer,Integer>> requirementGroups=Collections.emptyList();
    private volatile Snapshot reading=Snapshot.empty("Log in to follow a clue");

    static final class Need
    {
        final String name, state; final int itemId;
        final boolean fulfilled, carried, bankObserved, bankHas;
        Need(String name,String state,int itemId,boolean fulfilled,boolean carried,boolean bankObserved,boolean bankHas)
        { this.name=name; this.state=state; this.itemId=itemId; this.fulfilled=fulfilled; this.carried=carried;
          this.bankObserved=bankObserved; this.bankHas=bankHas; }
    }
    static final class StashReference
    {
        String key,title,chartText; List<Integer> itemIds;
        STASHUnit unit() { try { return STASHUnit.valueOf(key); } catch (IllegalArgumentException ex) { return null; } }
    }
    private static final class ReferenceFile { List<StashReference> units; }
    private static final class ToolFile { List<Integer> spades,lights; }
    static final class StashState
    {
        Boolean built,filled; long observedAt;
        StashState() { }
        StashState(Boolean built,Boolean filled,long at) { this.built=built; this.filled=filled; observedAt=at; }
        String label()
        { return Boolean.TRUE.equals(filled) ? "Filled · last observed" : Boolean.FALSE.equals(built) ? "Not built"
            : Boolean.TRUE.equals(built) && Boolean.FALSE.equals(filled) ? "Built · empty when observed"
            : Boolean.TRUE.equals(built) ? "Built · contents unknown" : "Not observed"; }
    }
    private static final class SavedStash { int version=1; Map<String,StashState> units; }
    static final class Snapshot
    {
        final String profile,key,title,instruction,status,enemy;
        final List<String> details;
        final List<Need> needs;
        final List<WorldPoint> destinations;
        final List<Map<Integer,Integer>> requirements;
        final STASHUnit stash;
        final boolean nativeEnabled;
        final List<ClueAccessIndex.Access> access;
        final String accessStatus;
        Snapshot(String profile,String key,String title,String instruction,String status,String enemy,List<String> details,
            List<Need> needs,List<WorldPoint> destinations,List<Map<Integer,Integer>> requirements,STASHUnit stash,boolean nativeEnabled)
        { this(profile,key,title,instruction,status,enemy,details,needs,destinations,requirements,stash,nativeEnabled,Collections.emptyList(),""); }
        Snapshot(String profile,String key,String title,String instruction,String status,String enemy,List<String> details,
            List<Need> needs,List<WorldPoint> destinations,List<Map<Integer,Integer>> requirements,STASHUnit stash,boolean nativeEnabled,
            List<ClueAccessIndex.Access> access,String accessStatus)
        {
            this.profile=profile; this.key=key; this.title=title; this.instruction=instruction; this.status=status; this.enemy=enemy;
            this.details=Collections.unmodifiableList(new ArrayList<>(details)); this.needs=Collections.unmodifiableList(new ArrayList<>(needs));
            this.destinations=Collections.unmodifiableList(new ArrayList<>(destinations)); this.requirements=Collections.unmodifiableList(new ArrayList<>(requirements));
            this.stash=stash; this.nativeEnabled=nativeEnabled;
            this.access=Collections.unmodifiableList(new ArrayList<>(access)); this.accessStatus=accessStatus;
        }
        static Snapshot empty(String status)
        { return new Snapshot(null,"","Treasure Trails","",status,"",Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),null,false); }
    }

    ClueDataService(Client client,PluginManager plugins,OwnershipLedger ownership,ConfigManager config,Gson gson)
    { this(client,plugins,ownership,config,gson,new ClueAccessIndex(client,gson),new RunewardenDataStore(config,gson)); }
    @Inject ClueDataService(Client client,PluginManager plugins,OwnershipLedger ownership,ConfigManager config,Gson gson,ClueAccessIndex access,RunewardenDataStore store)
    {
        this.client=client; this.plugins=plugins; this.ownership=ownership; this.config=config; this.gson=gson;
        this.access=access;
        this.store=store;
        ItemRequirement spadeTools=new net.runelite.client.plugins.cluescrolls.clues.item.SingleItemRequirement(net.runelite.api.gameval.ItemID.SPADE),lightTools=null;
        try (InputStreamReader input=new InputStreamReader(ClueDataService.class.getResourceAsStream("/com/runewarden/clue-tools.json"),StandardCharsets.UTF_8))
        {
            ToolFile tools=gson.fromJson(input,ToolFile.class);
            if (tools!=null) { ItemRequirement found=tool("Spade",tools.spades); if (found!=null) spadeTools=found; lightTools=tool("Light source",tools.lights); }
        }
        catch (Exception ignored) { }
        spade=spadeTools; light=lightTools;
        try (InputStreamReader input=new InputStreamReader(ClueDataService.class.getResourceAsStream("/com/runewarden/clue-stash-reference.json"),StandardCharsets.UTF_8))
        {
            ReferenceFile data=gson.fromJson(input,ReferenceFile.class);
            if (data!=null && data.units!=null) for (StashReference row : data.units)
                if (row!=null && row.key!=null && row.unit()!=null) references.put(row.key,row);
        }
        catch (Exception ignored) { /* Native clue coaching remains available without optional chart aliases. */ }
        for (List<Integer> ids : GuideItemResolver.nameIndex().values()) knownItemIds.addAll(ids);
    }

    void clear()
    { profile=null; warmup=0; chartWasOpen=false; stash.clear(); lastClue=null; requirementGroups=Collections.emptyList(); reading=Snapshot.empty("Log in to follow a clue"); }
    Snapshot snapshot() { return reading; }
    boolean bankOpen()
    {Widget bank=client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.ITEMS);return client.getGameState()==GameState.LOGGED_IN&&bank!=null&&!bank.isHidden();}
    String activeProfile() { return profile; }
    /** Game-thread guard also rejects an account switch before the next tick refresh. */
    boolean isCurrentProfile(String expected)
    { return expected!=null && Objects.equals(expected,profile) && client.getGameState()==GameState.LOGGED_IN
        && client.getLocalPlayer()!=null && Objects.equals(expected,config.getRSProfileKey()); }
    List<StashReference> stashes() { return Collections.unmodifiableList(new ArrayList<>(references.values())); }
    StashState stashState(STASHUnit unit)
    {
        StashState state=unit==null ? null : stash.get(unit.name());
        return state==null ? new StashState() : new StashState(state.built,state.filled,state.observedAt);
    }

    void onGameTick()
    {
        String account=client.getGameState()==GameState.LOGGED_IN && client.getLocalPlayer()!=null ? config.getRSProfileKey() : null;
        if (!Objects.equals(account,profile)) { clear(); profile=account; if (account!=null) load(); }
        if (account==null || ++warmup<3) return;
        try { access.onGameTick(); } catch (RuntimeException ignored) { /* Native DB metadata can be unavailable during loading. */ }
        observeChart();
        ClueScrollPlugin nativePlugin=null;
        for (Plugin plugin : plugins.getPlugins()) if (plugin instanceof ClueScrollPlugin && plugins.isPluginEnabled(plugin))
        { nativePlugin=(ClueScrollPlugin)plugin; break; }
        if (nativePlugin==null)
        { reading=Snapshot.empty("Enable RuneLite’s Clue Scroll plugin"); lastClue=null; return; }
        // The optional native plugin publishes this same one-method service to hard dependants.
        // A method adapter avoids forcing the core plugin on or accessing a child injector.
        ClueScrollService service=nativePlugin::getClue;
        ClueScroll clue=service.getClue();
        if (clue==null)
        { reading=new Snapshot(profile,"","Treasure Trails","","Read a clue scroll to see its current step","",Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),null,true); lastClue=null; return; }
        try { reading=describe(clue,nativePlugin); }
        catch (RuntimeException ignored)
        { reading=new Snapshot(profile,"","Treasure Trails","","Waiting for the current clue observation","",Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),null,true); lastClue=null; }
    }

    private Snapshot describe(ClueScroll clue,ClueScrollPlugin plugin)
    {
        List<String> details=new ArrayList<>(); String title="Current clue", instruction="Follow the native clue marker",nativeText="";
        ItemRequirement[] requirements=new ItemRequirement[0]; boolean equip=false; STASHUnit unit=null;
        if (clue instanceof EmoteClue)
        {
            EmoteClue emote=(EmoteClue)clue; title=emote.getLocationName(); instruction=emote.getFirstEmote().getName();
            if (emote.getSecondEmote()!=null) instruction+=" → "+emote.getSecondEmote().getName();
            details.add(emote.getText()); requirements=emote.getItemRequirements(); equip=true; unit=emote.getStashUnit();
            nativeText=emote.getText();
        }
        else if (clue instanceof FaloTheBardClue)
        { FaloTheBardClue falo=(FaloTheBardClue)clue; title="Falo the Bard"; instruction="Show Falo the required item"; details.add(falo.getText()); nativeText=falo.getText(); requirements=falo.getItemRequirements(); }
        else if (clue instanceof SkillChallengeClue)
        {
            SkillChallengeClue skill=(SkillChallengeClue)clue; title="Skill challenge"; instruction=skill.isChallengeCompleted() ? skill.getReturnText() : skill.getChallenge();
            requirements=skill.getItemRequirements(); equip=skill.isRequireEquip();
            nativeText=skill.getRawChallenge();
        }
        else if (clue instanceof CrypticClue)
        { CrypticClue cryptic=(CrypticClue)clue; title="Cryptic clue"; instruction=cryptic.getSolution(plugin); details.add(cryptic.getText()); nativeText=cryptic.getText(); if (cryptic.getAnswer()!=null) details.add("Answer · "+cryptic.getAnswer()); }
        else if (clue instanceof AnagramClue)
        {
            AnagramClue anagram=(AnagramClue)clue; title="Anagram clue"; instruction="Speak to "+anagram.getNpcProvider().apply(plugin); details.add(anagram.getArea());
            nativeText=anagram.getTextProvider()==null ? "" : anagram.getTextProvider().apply(plugin);
            if (anagram.getQuestion()!=null) details.add(anagram.getQuestion()); if (anagram.getAnswerProvider()!=null) details.add("Answer · "+anagram.getAnswerProvider().apply(plugin));
        }
        else if (clue instanceof CipherClue)
        { CipherClue cipher=(CipherClue)clue; title="Cipher clue"; nativeText=cipher.getText(); instruction="Speak to "+String.join(", ",cipher.getNpcs(plugin)); details.add(cipher.getAreaProvider().apply(plugin)); if (cipher.getAnswer()!=null) details.add("Answer · "+cipher.getAnswer()); }
        else if (clue instanceof MapClue) { title="Map clue"; instruction=((MapClue)clue).getDescription(); }
        else if (clue instanceof CoordinateClue) { title="Coordinate clue"; instruction="Dig at the marked destination"; }
        else if (clue instanceof HotColdClue) { title="Hot / cold clue"; instruction=((HotColdClue)clue).getSolution(); details.add("Possible locations follow the native device readings"); }
        else if (clue instanceof ThreeStepCrypticClue)
        {
            title="Three-step clue"; instruction="Complete the remaining clue parts";
            for (Map.Entry<CrypticClue,Boolean> entry : ((ThreeStepCrypticClue)clue).getClueSteps())
                details.add((Boolean.TRUE.equals(entry.getValue()) ? "✓ " : "○ ")+entry.getKey().getSolution(plugin));
        }
        List<Need> needs=new ArrayList<>(); Item[] inventory=items(InventoryID.INV), equipment=items(InventoryID.WORN);
        List<ItemRequirement> all=new ArrayList<>(Arrays.asList(requirements));
        int spadeIndex=-1,lightIndex=-1;
        if (clue.isRequiresSpade()) { spadeIndex=all.size(); all.add(spade); }
        boolean firePitLit=clue.getFirePitVarbitId()!=-1 && client.getVarbitValue(clue.getFirePitVarbitId())==1;
        if (clue.isRequiresLight() && !firePitLit && light!=null) { lightIndex=all.size(); all.add(light); }
        if (clue.isRequiresLight() && firePitLit) details.add("Fire pit unlocked · carried light source not required");
        OwnershipLedger.View owned=ownership.snapshot();
        OwnershipLedger.Observation bank=owned.active && Objects.equals(owned.profile,profile) ? owned.storages.get(OwnershipLedger.Storage.BANK) : null;
        for (int i=0;i<all.size();i++) needs.add(need(all.get(i),equip && i<requirements.length,
            i==spadeIndex || i<requirements.length && !equip,inventory,equipment,bank));
        List<WorldPoint> destinations=new ArrayList<>();
        if (clue instanceof LocationsClueScroll)
        {
            WorldPoint[] points=((LocationsClueScroll)clue).getLocations(plugin);
            if (points!=null) for (WorldPoint point : points) if (GuideNavigation.valid(point) && !destinations.contains(point) && destinations.size()<12) destinations.add(point);
        }
        if (destinations.isEmpty() && clue instanceof LocationClueScroll)
        { WorldPoint point=((LocationClueScroll)clue).getLocation(plugin); if (GuideNavigation.valid(point)) destinations.add(point); }
        if (clue instanceof FaloTheBardClue) destinations.add(new WorldPoint(2689,3550,0));
        if (clue!=lastClue || lastRequirementCount!=all.size()) { requirementGroups=groups(all); lastClue=clue; lastRequirementCount=all.size(); }
        if (unit!=null) observeBuilt(unit);
        String key=clue.getClass().getSimpleName()+Arrays.toString(clue.getConfigKeys())+destinations;
        String enemy=clue.getEnemy()==null ? "" : clue.getEnemy().getText();
        return new Snapshot(profile,key,GuideNavigation.safe(title),GuideNavigation.safe(instruction),"",enemy,clean(details),needs,destinations,requirementGroups,unit,true,
            access.access(nativeText),!access.ready() ? "Loading native access requirements" : !access.matched(nativeText) ? "Access requirements not indexed for this clue" : "");
    }

    private Need need(ItemRequirement requirement,boolean equip,boolean inventoryOnly,Item[] inventory,Item[] equipment,OwnershipLedger.Observation bank)
    {
        Item[] carried=concat(equipment,inventory); boolean worn=requirement.fulfilledBy(equipment);
        boolean present=requirement.fulfilledBy(inventoryOnly ? inventory : carried); boolean fulfilled=equip ? worn : present;
        Item[] bankItems=bank==null ? new Item[0] : from(bank.quantities); boolean bankHas=bank!=null && requirement.fulfilledBy(concat(carried,bankItems));
        int itemId=-1;
        for (Item item : concat(carried,bankItems)) if (item.getId()>0 && requirement.fulfilledBy(item.getId())) { itemId=item.getId(); break; }
        String name=GuideNavigation.safe(requirement.getCollectiveName(client));
        if (itemId<0) for (int candidate : GuideItemResolver.resolveName(name)) if (requirement.fulfilledBy(candidate)) { itemId=candidate; break; }
        String state=fulfilled ? equip ? "Equipped" : "Carried" : present ? "Equip before the emote" : inventoryOnly && worn ? "Move to inventory" : bankHas ? bank.currentSession ? "In observed bank" : "In saved bank snapshot"
            : bank==null ? "Missing carried · bank unknown" : "Missing from observed supplies";
        return new Need(name,state,itemId,fulfilled,present,bank!=null,bankHas);
    }
    private static ItemRequirement tool(String name,List<Integer> ids)
    {
        if (ids==null || ids.isEmpty() || ids.size()>100) return null;
        List<ItemRequirement> requirements=new ArrayList<>();
        for (Integer id : ids) if (id!=null && id>0) requirements.add(new net.runelite.client.plugins.cluescrolls.clues.item.SingleItemRequirement(id));
        return new net.runelite.client.plugins.cluescrolls.clues.item.AnyRequirementCollection(name,requirements.toArray(new ItemRequirement[0]));
    }

    /** Only encode requirements proven satisfiable by one item alternative; composite sets stay in the preparation list. */
    List<Map<Integer,Integer>> groups(List<ItemRequirement> requirements)
    {
        List<Map<Integer,Integer>> result=new ArrayList<>();
        for (ItemRequirement requirement : requirements)
        {
            Map<Integer,Integer> alternatives=new LinkedHashMap<>();
            for (int id : knownItemIds)
                if (id>0 && requirement.fulfilledBy(id) && requirement.fulfilledBy(new Item[]{new Item(id,1)}))
                { alternatives.put(id,1); if (alternatives.size()>256) break; }
            if (!alternatives.isEmpty() && alternatives.size()<=256) result.add(Collections.unmodifiableMap(alternatives));
        }
        return Collections.unmodifiableList(result);
    }

    private Item[] items(int inventory) { ItemContainer container=client.getItemContainer(inventory); return container==null ? new Item[0] : container.getItems(); }
    private static Item[] concat(Item[] first,Item[] second)
    { Item[] result=Arrays.copyOf(first,first.length+second.length); System.arraycopy(second,0,result,first.length,second.length); return result; }
    private static Item[] from(Map<Integer,Long> counts)
    { List<Item> result=new ArrayList<>(); counts.forEach((id,n) -> { if (id>0 && n>0) result.add(new Item(id,(int)Math.min(Integer.MAX_VALUE,n))); }); return result.toArray(new Item[0]); }
    private static List<String> clean(List<String> rows)
    { List<String> result=new ArrayList<>(); for (String row : rows) if (row!=null && !row.isEmpty() && result.size()<20) result.add(GuideNavigation.safe(row)); return result; }

    void observeBuilt(STASHUnit unit)
    {
        if (profile==null || client.getGameState()!=GameState.LOGGED_IN) return;
        try
        {
            client.runScript(ScriptID.WATSON_STASH_UNIT_CHECK,unit.getObjectId(),0,0,0);
            int[] values=client.getIntStack(); if (values==null || values.length<1 || (values[0]!=0 && values[0]!=1)) return;
            StashState old=stash.get(unit.name()); boolean built=values[0]==1;
            stash.put(unit.name(),new StashState(built,!built ? Boolean.FALSE : old==null ? null : old.filled,old==null ? 0 : old.observedAt));
        }
        catch (RuntimeException ignored) { /* No default booleans are inferred when the native script is unavailable. */ }
    }

    private void observeChart()
    {
        Widget chart=client.getWidget(493,2);
        boolean open=chart!=null && !chart.isHidden();
        if (!open) { chartWasOpen=false; return; }
        if (chartWasOpen) return; boolean changed=false;
        for (int child : new int[]{4,6,8,10,12,14})
        {
            Widget column=client.getWidget(493,child); if (column==null || column.getChildren()==null) continue;
            String key=null; int checks=0;
            for (Widget widget : column.getChildren())
            {
                if (widget==null || widget.isHidden()) continue;
                if (widget.getType()==WidgetType.TEXT)
                {
                    if (key!=null) { stash.put(key,new StashState(checks>=1,checks>=2,System.currentTimeMillis())); changed=true; }
                    key=chartKey(widget.getText(),child); checks=0;
                }
                else if (widget.getType()==WidgetType.GRAPHIC && widget.getSpriteId()==SpriteID.STASH_UNITS_GREEN_CHECK_MARK && key!=null) checks++;
            }
            if (key!=null) { stash.put(key,new StashState(checks>=1,checks>=2,System.currentTimeMillis())); changed=true; }
        }
        if (changed) { chartWasOpen=true; save(); }
    }

    String chartKey(String text,int child)
    {
        String normalized=normalize(text);
        if (child==14 && normalized.equals("warriorsguildbank")) normalized+="master";
        for (StashReference reference : references.values())
            if (normalize(reference.chartText).equals(normalized)) return reference.key;
        return null;
    }
    static String normalize(String text)
    { return GuideNavigation.safe(text).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]",""); }
    private void load()
    {
        try
        {
            SavedStash saved=store.read(GROUP,profile,STASH_KEY,1,SavedStash.class,this::validSaved,SavedStash::new);
            if (saved==null || saved.units==null) return;
            saved.units.forEach((key,value) -> { if (references.containsKey(key) && value!=null && value.observedAt>0) stash.put(key,value); });
        }
        catch (RuntimeException ignored) { stash.clear(); }
    }
    private void save()
    {
        if (profile==null || !Objects.equals(profile,config.getRSProfileKey())) return;
        SavedStash saved=new SavedStash(); saved.units=new LinkedHashMap<>(stash);
        if (gson.toJson(saved).length()<=MAX_JSON) store.write(GROUP,profile,STASH_KEY,1,saved);
    }
    private boolean validSaved(SavedStash saved)
    {
        if (saved==null || saved.version!=1 || saved.units==null || saved.units.size()>200) return false;
        long latest=System.currentTimeMillis()+60_000;
        for (Map.Entry<String,StashState> row : saved.units.entrySet())
            if (!references.containsKey(row.getKey()) || row.getValue()==null || row.getValue().observedAt<0 || row.getValue().observedAt>latest
                || Boolean.TRUE.equals(row.getValue().filled) && !Boolean.TRUE.equals(row.getValue().built)) return false;
        return true;
    }
}
