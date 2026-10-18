/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Current facts and explicit inventory/object states refine the ordinary activity loop. */
final class MinigameNativeObjectives
{
    static final List<String> VOLCANIC_ROLES=List.of("Team overview","Vent A","Vent B","Vent C","Boulder miner");
    static final class Options
    {
        final int fragmentTarget, warmthThreshold, volcanicExitSeconds,temporossFishBatch;
        final String volcanicRole;final boolean winterFletch,temporossCook;
        Options(int fragmentTarget,int warmthThreshold,int volcanicExitSeconds)
        {this(fragmentTarget,warmthThreshold,volcanicExitSeconds,"Team overview",true,true,1);}
        Options(int fragments,int warmth,int exit,String role,boolean fletch,boolean cook,int batch)
        {this.fragmentTarget=fragments;this.warmthThreshold=warmth;this.volcanicExitSeconds=exit;
            volcanicRole=role!=null&&VOLCANIC_ROLES.contains(role)?role:"Team overview";winterFletch=fletch;temporossCook=cook;temporossFishBatch=Math.max(1,Math.min(25,batch));}
        static final Options DEFAULT=new Options(0,25,60);
    }
    static final Set<Integer> EXTRA_OBJECTS=Set.of(
        ObjectID.BARBASSAULT_TRAP,ObjectID.BARBASSAULT_TRAP_1USE,ObjectID.BARBASSAULT_TRAP_BROKEN,
        ObjectID.BARBASSAULT_EGG_HOPPER,ObjectID.BARBASSAULT_CRATER_LAVA,ObjectID.BARBASSAULT_CRATER_LAVA_BIG,
        ObjectID.BARBASSAULT_PETRIFIED_MUSHROOM,ObjectID.BARBASSAULT_NATURAL_SPRING,
        ObjectID.BARBASSAULT_CRATER_POISION,ObjectID.BARBASSAULT_CRATER_POISION_BIG,
        ObjectID.BARASSAULT_VENDING_MACHINE_HEALER,ObjectID.BARASSAULT_VENDING_MACHINE_DEFENDER,ObjectID.BARASSAULT_VENDING_MACHINE_ATTACKER,
        ObjectID.TITHE_SACK_OF_FRUIT_EMPTY,ObjectID.TITHE_SACK_OF_FRUIT,
        ObjectID.BLAST_MINING_WALL_01,ObjectID.BLAST_MINING_WALL_02,
        ObjectID.BLAST_MINING_WALL_CHISELED_01,ObjectID.BLAST_MINING_WALL_CHISELED_02,
        ObjectID.BLAST_MINING_WALL_POT_01,ObjectID.BLAST_MINING_WALL_POT_02,
        ObjectID.BLAST_MINING_WALL_DESTROYED_01,ObjectID.BLAST_MINING_WALL_DESTROYED_02);
    private MinigameNativeObjectives() { }
    static MinigameActivityCatalog.Snapshot enrich(MinigameActivityCatalog.Snapshot original,Client client,Set<Integer> nearby,Options options)
    {
        if(original.activity==null)return original;
        Builder b=new Builder(original); List<NPC> npcs=original.activity==MinigameActivityCatalog.Activity.WINTERTODT||original.activity==MinigameActivityCatalog.Activity.PEST_CONTROL?npcs(client):List.of();
        switch(original.activity)
        {
            case WINTERTODT:winter(b,client,npcs,options);break;
            case GOTR:rift(b,client,options);break;
            case TEMPOROSS:tempoross(b,client,options);break;
            case BARBARIAN_ASSAULT:assault(b,client,nearby);break;
            case PEST_CONTROL:pest(b,client,npcs);break;
            case VOLCANIC_MINE:volcanic(b,options);break;
            case BLAST_MINE:blast(b,client,nearby);break;
            case TITHE_FARM:tithe(b,client);break;
            case PYRAMID_PLUNDER:pyramid(b,client);break;
            case FISHING_TRAWLER:trawler(b);break;
            case DRIFT_NET:if(b.title().contains("Herd"))b.npcs.add(NpcID.FOSSIL_FISH_SHOAL);break;
            default:break;
        }
        return b.build();
    }
    private static void winter(Builder b,Client client,List<NPC> npcs,Options options)
    {
        int roots=count(client,ItemID.WINT_BRUMA_ROOT),kindling=count(client,ItemID.WINT_BRUMA_KINDLING);
        b.fact("Carried fuel",roots+" roots · "+kindling+" kindling");
        Integer warmth=percent(b.value("Warmth"));
        if(warmth!=null&&warmth<=options.warmthThreshold)
        {
            boolean potion=count(client,ItemID.WINT_POTION4,ItemID.WINT_POTION3,ItemID.WINT_POTION2,ItemID.WINT_POTION1)>0;
            b.current("Restore warmth",potion?"Your warmth is at or below your chosen threshold. Drink your rejuvenation potion before continuing the fuel loop."
                :"Your warmth is at or below your chosen threshold. Restore it with your prepared food or rejuvenation potion before continuing.",Set.of());
            return;
        }
        if(count(client,ItemID.WINT_POTION4,ItemID.WINT_POTION3,ItemID.WINT_POTION2,ItemID.WINT_POTION1)>0)
            for(NPC npc:npcs)if(npc.getId()==NpcID.WINT_WIZARD_DOWN&&near(client,npc,5))
            {b.current("Revive the pyromancer","Use your rejuvenation potion on the incapacitated pyromancer so the brazier can keep burning.",Set.of());b.npcs.add(NpcID.WINT_WIZARD_DOWN);return;}
        if(!options.winterFletch&&roots>0&&kindling==0&&b.title().equals("Fletch or feed bruma roots"))
            b.current("Feed bruma roots","Feed your roots directly to a lit brazier, then gather the next batch.",ids(29314));
    }
    private static void rift(Builder b,Client client,Options options)
    {
        int fragments=count(client,26878),essence=count(client,26879),stones=count(client,26880,26881,26941,30887);
        b.fact("Carried",fragments+" fragments · "+essence+" essence · "+stones+" stones");
        if(options.fragmentTarget>0)b.fact("Fragment target",Integer.toString(options.fragmentTarget));
        if(fragments>0&&fragments<options.fragmentTarget&&essence==0&&stones==0)
        {
            Set<Integer> targets=new LinkedHashSet<>(ids(43717,43718));
            if(client.getRealSkillLevel(Skill.AGILITY)>=56)targets.add(43719);
            net.runelite.api.coords.WorldPoint p=client.getLocalPlayer().getWorldLocation();
            if(p!=null&&p.getX()>3587&&p.getX()<3594&&p.getY()>9496&&p.getY()<9516)targets=ids(43720);
            b.current("Mine your fragment batch","Gather "+(options.fragmentTarget-fragments)+" more fragments to reach your chosen batch, then use the workbench. You can change the batch target in Activity settings.",targets);
        }
        int elemental=number(b.value("Elemental energy")),catalytic=number(b.value("Catalytic energy"));
        if(elemental>=0&&catalytic>=0)b.fact("Reward balance",elemental==catalytic?"Balanced":elemental<catalytic?"Elemental contribution is lower":"Catalytic contribution is lower");
    }
    private static void tempoross(Builder b,Client client,Options options)
    {
        b.fact("Carried fish",count(client,ItemID.TEMPOROSS_RAW_HARPOONFISH)+" raw · "+count(client,ItemID.TEMPOROSS_HARPOONFISH,ItemID.TEMPOROSS_CRYSTALLISED_HARPOONFISH)+" cooked");
        String title=b.title().toLowerCase(Locale.ROOT);
        int raw=count(client,ItemID.TEMPOROSS_RAW_HARPOONFISH),cooked=count(client,ItemID.TEMPOROSS_HARPOONFISH,ItemID.TEMPOROSS_CRYSTALLISED_HARPOONFISH);
        b.fact("Fish batch",Integer.toString(options.temporossFishBatch));
        boolean ordinary=title.contains("catch")||title.contains("cook")||title.contains("load");
        if(ordinary&&raw>0&&cooked==0)
        {
            if(raw<options.temporossFishBatch&&emptySlots(client)>0)
                b.current("Catch harpoonfish", "Catch "+(options.temporossFishBatch-raw)+" more fish for your chosen batch, then "+(options.temporossCook?"cook and load it.":"load it raw."),Set.of());
            else if(!options.temporossCook)b.current("Load raw harpoonfish","Your raw-fish batch is ready. Load the ammunition crate, then resume fishing.",ids(40968,40969,40970,40971,40972,40973,40974,40975,40976,40977,40978,40979));
            title=b.title().toLowerCase(Locale.ROOT);
        }
        if(title.contains("spirit"))b.npcs.add(NpcID.TEMPOROSS_P2_FISHINGSPOT);
        else if(title.contains("catch"))b.npcs.addAll(ids(NpcID.TEMPOROSS_HARPOONFISH_FISHINGSPOT_NORTH,NpcID.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SOUTH,NpcID.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SPECIAL));
        else if(title.contains("load"))b.npcs.addAll(ids(NpcID.TEMPOROSS_NPC_CRATE_AMMUNITION_1,NpcID.TEMPOROSS_NPC_CRATE_AMMUNITION_2,NpcID.TEMPOROSS_NPC_CRATE_AMMUNITION_3,NpcID.TEMPOROSS_NPC_CRATE_AMMUNITION_4));
    }
    private static void assault(Builder b,Client client,Set<Integer> nearby)
    {
        String role=b.value("Role"),call=b.value("Heard call"),send=b.value("Call to send");
        String sendText=knownCall(send)?"Call "+send+" with your horn. ":"Check your horn for the partner call. ";
        if(!knownCall(call))
        {b.current("Send the call and wait for yours",sendText+"Wait for a valid received call before choosing style, eggs or food.",Set.of());return;}
        if("Attacker".equals(role))
        {
            b.current("Attack using "+call,sendText+"Use the received "+call+" attack instruction against penance fighters and rangers. Update your style, spell or ammunition whenever the native call changes.",Set.of());
            b.npcs.addAll(range(NpcID.BARBASSAULT_PEN_FIGHTER_LV1,NpcID.BARBASSAULT_PEN_FIGHTER_LV9));
            b.npcs.addAll(range(NpcID.BARBASSAULT_PEN_RANGER_LV1,NpcID.BARBASSAULT_PEN_RANGER_LV9));
        }
        else if("Collector".equals(role))
        {
            b.fact("Eggs carried",Integer.toString(count(client,ItemID.BARBASSAULT_EGG_01,ItemID.BARBASSAULT_EGG_02,ItemID.BARBASSAULT_EGG_03)));
            b.current("Collect "+call,sendText+"Pick up only the called "+call+" and load collected eggs into the hopper. Re-read the call before picking up another colour.",ids(ObjectID.BARBASSAULT_EGG_HOPPER));
        }
        else if("Defender".equals(role))
        {
            boolean broken=nearby.contains(ObjectID.BARBASSAULT_TRAP_BROKEN);
            b.current(broken?"Repair the runner trap":"Bait runners with "+call,sendText+(broken?"Repair the broken trap using the defender repair supplies, then continue the bait line."
                :"Drop the called "+call+" to lure runners toward a working trap. Keep the bait line supplied and repair a broken trap."),
                broken?ids(ObjectID.BARBASSAULT_TRAP_BROKEN):ids(ObjectID.BARBASSAULT_TRAP,ObjectID.BARBASSAULT_TRAP_1USE));
            b.npcs.addAll(range(NpcID.BARBASSAULT_PEN_RUNNER_LV1,NpcID.BARBASSAULT_PEN_RUNNER_LV9));
        }
        else if("Healer".equals(role))
        {
            b.fact("Poison food",Integer.toString(count(client,ItemID.BARBASSAULT_POISION_01,ItemID.BARBASSAULT_POISION_02,ItemID.BARBASSAULT_POISION_03)));
            for(int index=0;index<4;index++)
            {
                String name=text(client,488,17+index*4),hp=text(client,488,18+index*4);
                if(!name.isEmpty()&&!hp.isEmpty())b.fact("Teammate "+(index+1),name+" · "+hp);
            }
            if(client.getItemContainer(InventoryID.INV)!=null&&count(client,ItemID.BARBASSAULT_POISION_01,ItemID.BARBASSAULT_POISION_02,ItemID.BARBASSAULT_POISION_03)==0)
                b.current("Restock healer supplies",sendText+"Collect poison food from the healer item machine, then use the called "+call+" on penance healers. Refill your healing vial at the healer spring when needed.",ids(ObjectID.BARASSAULT_VENDING_MACHINE_HEALER,ObjectID.BARBASSAULT_NATURAL_SPRING));
            else
            {b.current("Poison with "+call,sendText+"Use the called "+call+" on penance healers. Monitor the team health display and refill your healing vial for teammates.",Set.of());
                b.npcs.addAll(range(NpcID.BARBASSAULT_PEN_HEALER_LV1,NpcID.BARBASSAULT_PEN_HEALER_LV9));}
        }
        // The native wave value establishes the queen context; the egg's exact carried form establishes the hand-off.
        if(number(b.value("Wave").replaceAll("(?i)wave", "").trim())==10)
        {
            if(count(client,ItemID.BARBASSAULT_EGG_HEATED_POISON_SPIKES)>0)
                b.current("Collector".equals(role)?"Load and fire the omega egg":"Hand off the omega egg",sendText+("Collector".equals(role)?"Load the completed omega egg into the cannon, then fire it at the queen."
                    :"Give the completed omega egg to the Collector so it can be loaded into the cannon."),"Collector".equals(role)?ids(ObjectID.BARBASSAULT_EGG_HOPPER):Set.of());
            else if(count(client,ItemID.BARBASSAULT_EGG_POISON_SPIKES)>0)
                b.current("Defender".equals(role)?"Heat the spiked egg":"Pass the spiked egg to the Defender",sendText+("Defender".equals(role)?"Heat the spiked poisoned egg at the lava, then give the omega egg to the Collector."
                    :"Pass the spiked poisoned egg to the Defender, who heats it at the lava."),"Defender".equals(role)?ids(ObjectID.BARBASSAULT_CRATER_LAVA,ObjectID.BARBASSAULT_CRATER_LAVA_BIG):Set.of());
            else if(count(client,ItemID.BARBASSAULT_EGG_POISON)>0)
                b.current("Attacker".equals(role)?"Add spikes to the poisoned egg":"Pass the poisoned egg to the Attacker",sendText+("Attacker".equals(role)?"Add spikes from the petrified mushroom, then pass the spiked egg to the Defender."
                    :"Pass the poisoned egg to the Attacker, who adds mushroom spikes."),"Attacker".equals(role)?ids(ObjectID.BARBASSAULT_PETRIFIED_MUSHROOM):Set.of());
            else if(count(client,ItemID.BARBASSAULT_EGG_04)>0)
                b.current("Healer".equals(role)?"Poison the queen egg":"Pass the yellow egg to the Healer",sendText+("Healer".equals(role)?"Poison the yellow egg at the poison pool, then pass the poisoned egg to the Attacker."
                    :"Pass the yellow egg to the Healer to begin the poison, spike and heat hand-off."),"Healer".equals(role)?ids(ObjectID.BARBASSAULT_CRATER_POISION,ObjectID.BARBASSAULT_CRATER_POISION_BIG):Set.of());
        }
    }
    private static void pest(Builder b,Client client,List<NPC> npcs)
    {
        String[] colours={"Purple","Blue","Yellow","Red"};
        int[] hp={InterfaceID.PestStatusOverlay.PEST_STATUS_PORTTXT1,InterfaceID.PestStatusOverlay.PEST_STATUS_PORTTXT2,
            InterfaceID.PestStatusOverlay.PEST_STATUS_PORTTXT3,InterfaceID.PestStatusOverlay.PEST_STATUS_PORTTXT4};
        int selected=-1,nearest=Integer.MAX_VALUE; Set<Integer> open=new LinkedHashSet<>();boolean spinner=false;
        for(int i=0;i<4;i++)
        {
            String health=text(client,hp[i]>>>16,hp[i]&65535);boolean active=false,shielded=false;int distance=Integer.MAX_VALUE;
            for(NPC npc:npcs)
            {
                int id=npc.getId();
                if(id==NpcID.PEST_PORTAL_1_ACTIVE+i||id==NpcID.PEST_PORTAL_1_ACTIVE_BT1+i)
                {active=true;if(npc.getWorldLocation()!=null&&client.getLocalPlayer().getWorldLocation()!=null)distance=Math.min(distance,npc.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation()));}
                if(id==NpcID.PEST_PORTAL_SHIELD_1_ACTIVE+i||id==NpcID.PEST_PORTAL_SHIELD_1_ACTIVE_BT1+i)shielded=true;
            }
            boolean dead="0".equals(health);
            b.fact(colours[i]+" portal",dead?"Destroyed":active&&!shielded?"Open"+(health.isEmpty()?"":" · "+health+" HP"):shielded?"Shielded"+(health.isEmpty()?"":" · "+health+" HP"):health.isEmpty()?"Not observed":health+" HP · shield not observed");
            if(active&&!shielded&&!dead)
            {open.add(NpcID.PEST_PORTAL_1_ACTIVE+i);open.add(NpcID.PEST_PORTAL_1_ACTIVE_BT1+i);if(distance<nearest){selected=i;nearest=distance;}}
        }
        for(NPC npc:npcs)if(npc.getId()>=NpcID.PEST_SPINNER_1&&npc.getId()<=NpcID.PEST_SPINNER_5&&near(client,npc,10))spinner=true;
        if(spinner)
        {b.current("Clear nearby spinners","A spinner is nearby. Stop it healing the portal your group is attacking, then resume portal damage. Keep the Void Knight protected.",Set.of());b.npcs.addAll(range(NpcID.PEST_SPINNER_1,NpcID.PEST_SPINNER_5));}
        else if(selected>=0)
        {b.current("Attack the "+colours[selected].toLowerCase(Locale.ROOT)+" portal","This nearby portal is observed in its unshielded form. Attack it with your prepared style and clear any spinners healing it.",Set.of());b.npcs.addAll(open);}
        else b.npcs.addAll(range(NpcID.PEST_SPINNER_1,NpcID.PEST_SPINNER_5));
    }
    private static void volcanic(Builder b,Options options)
    {
        Integer remaining=clockSeconds(b.value("Time"));
        if(remaining!=null&&remaining<=options.volcanicExitSeconds)
        {b.current("Return to the exit","The native clock is at your chosen exit reminder. Leave the mining loop and return to the exit before time runs out.",ids(ObjectID.FOSSIL_MINING_START_EXIT));return;}
        b.fact("Chosen role",options.volcanicRole);
        String[] assigned=options.volcanicRole.startsWith("Vent ")?new String[]{options.volcanicRole}:options.volcanicRole.equals("Boulder miner")?new String[0]:new String[]{"Vent A","Vent B","Vent C"};
        for(String label:assigned)
        {
            String value=b.value(label);
            if(!value.isEmpty()&&(value.contains("?")||value.equals("-")))
            {b.current("Check "+(options.volcanicRole.startsWith("Vent ")?options.volcanicRole:"the assigned vents"),"Your role has an unknown native vent reading. Check your vent and tell your team its pressure before changing chambers.",Set.of());return;}
        }
        if(options.volcanicRole.startsWith("Vent "))
        {
            String letter=options.volcanicRole.substring(5),pressure=b.value(options.volcanicRole),chamber=b.value("Chamber "+letter);
            b.fact("Role reading",options.volcanicRole+" · "+pressure+" · chamber "+(chamber.isEmpty()?"not observed":chamber));
            b.current("Maintain "+options.volcanicRole+" and mine","Your assigned reading is "+pressure+". Communicate it, maintain your assigned chamber with the team and mine the boulder between checks.",ids(ObjectID.FOSSIL_VOLCANO_CHAMBER,ObjectID.FOSSIL_VOLCANO_CHAMBER_BLOCKED,ObjectID.FOSSIL_MINING_BOULDER_ATTACHED,ObjectID.FOSSIL_MINING_BOULDER_GIANT,ObjectID.FOSSIL_MINING_BOULDER_LARGE,ObjectID.FOSSIL_MINING_BOULDER_MEDIUM,ObjectID.FOSSIL_MINING_BOULDER_SMALL));return;
        }
        b.current(options.volcanicRole.equals("Boulder miner")?"Mine the boulder":"Mine and watch your vent role","Mine the current boulder. Recheck your assigned vent and chamber whenever its native reading changes, and maintain a route back to the exit.",ids(ObjectID.FOSSIL_MINING_BOULDER_ATTACHED,ObjectID.FOSSIL_MINING_BOULDER_GIANT,ObjectID.FOSSIL_MINING_BOULDER_LARGE,ObjectID.FOSSIL_MINING_BOULDER_MEDIUM,ObjectID.FOSSIL_MINING_BOULDER_SMALL));
    }
    private static void blast(Builder b,Client client,Set<Integer> nearby)
    {
        b.fact("Dynamite carried",Integer.toString(count(client,ItemID.LOVAKENGJ_DYNAMITE_POT)));
        if(has(nearby,ObjectID.BLAST_MINING_WALL_DESTROYED_01,ObjectID.BLAST_MINING_WALL_DESTROYED_02))
            b.current("Collect blasted ore","Collect the blasted ore promptly, then deposit it in the sack before continuing the circuit.",ids(ObjectID.BLAST_MINING_WALL_DESTROYED_01,ObjectID.BLAST_MINING_WALL_DESTROYED_02,ObjectID.BLAST_MINING_SACK_DEPOSIT));
        else if(has(nearby,ObjectID.BLAST_MINING_WALL_POT_01,ObjectID.BLAST_MINING_WALL_POT_02))
            b.current("Light and retreat","Light a loaded dynamite pot, then move clear of the blast before preparing the next rock.",ids(ObjectID.BLAST_MINING_WALL_POT_01,ObjectID.BLAST_MINING_WALL_POT_02));
        else if(has(nearby,ObjectID.BLAST_MINING_WALL_CHISELED_01,ObjectID.BLAST_MINING_WALL_CHISELED_02))
            b.current("Load dynamite","Place dynamite in the excavated rock, then prepare to light it and retreat.",ids(ObjectID.BLAST_MINING_WALL_CHISELED_01,ObjectID.BLAST_MINING_WALL_CHISELED_02));
        else b.current("Excavate a rock","Chisel a hard-rock face on your selected circuit before loading a dynamite pot.",ids(ObjectID.BLAST_MINING_WALL_01,ObjectID.BLAST_MINING_WALL_02));
    }
    private static void tithe(Builder b,Client client)
    {
        int fruit=count(client,ItemID.HOSIDIUS_TITHE_FRUIT_A,ItemID.HOSIDIUS_TITHE_FRUIT_B,ItemID.HOSIDIUS_TITHE_FRUIT_C);b.fact("Fruit carried",Integer.toString(fruit));
        if(fruit>0&&b.title().equals("Plant and water"))b.current("Deposit your harvested fruit","Take your harvested fruit to the sack, refill watering cans and prepare the next planting circuit.",ids(ObjectID.TITHE_SACK_OF_FRUIT_EMPTY,ObjectID.TITHE_SACK_OF_FRUIT));
    }
    private static void pyramid(Builder b,Client client)
    {
        int level=client.getRealSkillLevel(Skill.THIEVING);if(level<21)return;
        int highest=Math.min(8,1+(level-21)/10);b.fact("Highest eligible room",Integer.toString(highest));
        int room=number(b.value("Room"));
        if(room>0&&room<highest&&!b.title().toLowerCase(Locale.ROOT).contains("trap"))
            b.current("Advance toward room "+highest,"You can enter room "+highest+" at your real Thieving level. Search the correct exit while following your chosen looting plan and native timer.",Set.of());
    }
    private static void trawler(Builder b)
    {
        Integer water=percent(b.value("Water"));
        if(water!=null&&water>=50)b.current("Bail rising water","The native water display is at least half full. Help bail and repair leaks in the hold while teammates maintain the nets.",Set.of());
    }
    static Integer percent(String text)
    {
        if(text==null)return null;java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?<![0-9])([0-9]{1,3})\\s*%").matcher(text);
        if(!m.find())return null;int result=Integer.parseInt(m.group(1));return result<=100?result:null;
    }
    static Integer clockSeconds(String text)
    {
        if(text==null)return null;java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?<![0-9])([0-9]{1,2}):([0-5][0-9])(?![0-9])").matcher(text);
        return m.find()?Integer.parseInt(m.group(1))*60+Integer.parseInt(m.group(2)):null;
    }
    private static boolean knownCall(String text){return text!=null&&!text.isEmpty()&&!text.contains("---")&&!text.equals("?")&&!text.equalsIgnoreCase("None")&&!text.equalsIgnoreCase("Unknown");}
    private static int number(String text){try{return text.matches("[0-9]{1,9}")?Integer.parseInt(text):-1;}catch(RuntimeException e){return -1;}}
    private static boolean has(Set<Integer> set,int... values){for(int value:values)if(set.contains(value))return true;return false;}
    private static boolean near(Client client,NPC npc,int tiles)
    {return npc.getWorldLocation()!=null&&client.getLocalPlayer()!=null&&client.getLocalPlayer().getWorldLocation()!=null&&client.getLocalPlayer().getWorldLocation().distanceTo(npc.getWorldLocation())<=tiles;}
    private static List<NPC> npcs(Client client)
    {
        WorldView view=client.getTopLevelWorldView();if(view==null||view.npcs()==null)return List.of();
        List<NPC> list=new ArrayList<>();int inspected=0;for(NPC npc:view.npcs()){if(++inspected>2048)break;if(npc!=null)list.add(npc);}return list;
    }
    private static int emptySlots(Client client)
    {ItemContainer c=client.getItemContainer(InventoryID.INV);if(c==null||c.getItems()==null)return 0;int used=0;for(Item item:c.getItems())if(item!=null&&item.getId()>0&&item.getQuantity()>0)used++;return Math.max(0,28-used);}
    private static int count(Client client,int... ids)
    {
        ItemContainer container=client.getItemContainer(InventoryID.INV);if(container==null||container.getItems()==null)return 0;
        long count=0;Item[] items=container.getItems();for(int i=0;i<Math.min(28,items.length);i++)if(items[i]!=null)for(int id:ids)if(items[i].getId()==id){count+=Math.max(0,items[i].getQuantity());break;}return (int)Math.min(Integer.MAX_VALUE,count);
    }
    private static String text(Client client,int group,int child)
    {Widget widget=client.getWidget(group,child);if(widget==null||widget.isHidden()||widget.getText()==null)return "";String s=net.runelite.client.util.Text.removeTags(widget.getText()).replaceAll("\\s+"," ").trim();return s.substring(0,Math.min(100,s.length()));}
    private static Set<Integer> ids(Integer... values){return new LinkedHashSet<>(java.util.Arrays.asList(values));}
    private static Set<Integer> range(int from,int to){Set<Integer> values=new LinkedHashSet<>();for(int i=from;i<=to;i++)values.add(i);return values;}
    private static final class Builder
    {
        final MinigameActivityCatalog.Snapshot original;
        final Map<String,String> facts=new LinkedHashMap<>();final Set<Integer> npcs=new LinkedHashSet<>();
        MinigameActivityCatalog.Step current;Set<Integer> objects;boolean changed;
        Builder(MinigameActivityCatalog.Snapshot source){original=source;for(MinigameActivityCatalog.Fact fact:source.facts)facts.put(fact.label,fact.value);current=source.steps.isEmpty()?null:source.steps.get(0);objects=source.highlightObjectIds;npcs.addAll(source.highlightNpcIds);}
        void fact(String label,String value){facts.put(label,value);}
        String value(String label){return facts.getOrDefault(label,"");}
        String title(){return current==null?"":current.title;}
        void current(String title,String body,Set<Integer> objects){current=new MinigameActivityCatalog.Step(title,body);this.objects=objects;changed=true;}
        MinigameActivityCatalog.Snapshot build()
        {
            List<MinigameActivityCatalog.Fact> items=new ArrayList<>();facts.forEach((key,value)->items.add(new MinigameActivityCatalog.Fact(key,value)));
            return new MinigameActivityCatalog.Snapshot(original.activity,original.title,original.guideId,items,current==null?List.of():List.of(current),objects,changed?List.of():original.referenceTiles,original.liveSupported,npcs).preserveGuideProgress(original);
        }
    }
}
