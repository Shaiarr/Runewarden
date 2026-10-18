/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/** Public native identifiers and strict native Check-message parsing. */
final class ContainerHudRules
{
    static final int CAPACITY=28,CHECK_GROUP=193,CHECK_COMPONENT=2;
    static final Set<Integer> BARRELS=Set.of(ItemID.FISH_BARREL_CLOSED,ItemID.FISH_BARREL_OPEN,ItemID.FISH_SACK_BARREL_CLOSED,ItemID.FISH_SACK_BARREL_OPEN);
    static final Set<Integer> OPEN_BARRELS=Set.of(ItemID.FISH_BARREL_OPEN,ItemID.FISH_SACK_BARREL_OPEN);
    enum Plank
    {
        PLAIN("Plain",ItemID.WOODPLANK,VarbitID.PLANK_SACK_PLAIN),OAK("Oak",ItemID.PLANK_OAK,VarbitID.PLANK_SACK_OAK),
        TEAK("Teak",ItemID.PLANK_TEAK,VarbitID.PLANK_SACK_TEAK),MAHOGANY("Mahogany",ItemID.PLANK_MAHOGANY,VarbitID.PLANK_SACK_MAHOGANY),
        CAMPHOR("Camphor",ItemID.PLANK_CAMPHOR,VarbitID.PLANK_SACK_CAMPHOR),IRONWOOD("Ironwood",ItemID.PLANK_IRONWOOD,VarbitID.PLANK_SACK_IRONWOOD),
        ROSEWOOD("Rosewood",ItemID.PLANK_ROSEWOOD,VarbitID.PLANK_SACK_ROSEWOOD);
        final String title;final int item,varbit;Plank(String title,int item,int varbit){this.title=title;this.item=item;this.varbit=varbit;}
    }
    static final Map<String,Integer> FISH;
    static
    {
        Map<String,Integer> fish=new LinkedHashMap<>();
        fish.put("shrimp",ItemID.RAW_SHRIMP);fish.put("shrimps",ItemID.RAW_SHRIMP);fish.put("sardine",ItemID.RAW_SARDINE);
        fish.put("herring",ItemID.RAW_HERRING);fish.put("anchovies",ItemID.RAW_ANCHOVIES);fish.put("mackerel",ItemID.RAW_MACKEREL);
        fish.put("trout",ItemID.RAW_TROUT);fish.put("cod",ItemID.RAW_COD);fish.put("pike",ItemID.RAW_PIKE);fish.put("slimy swamp eel",ItemID.MORT_SLIMEY_EEL);
        fish.put("salmon",ItemID.RAW_SALMON);fish.put("tuna",ItemID.RAW_TUNA);fish.put("rainbow fish",ItemID.HUNTING_RAW_FISH_SPECIAL);fish.put("cave eel",ItemID.RAW_CAVE_EEL);
        fish.put("lobster",ItemID.RAW_LOBSTER);fish.put("bass",ItemID.RAW_BASS);fish.put("leaping trout",ItemID.BRUT_SPAWNING_TROUT);fish.put("swordfish",ItemID.RAW_SWORDFISH);
        fish.put("lava eel",ItemID.RAW_LAVA_EEL);fish.put("leaping salmon",ItemID.BRUT_SPAWNING_SALMON);fish.put("monkfish",ItemID.RAW_MONKFISH);fish.put("Karambwan",ItemID.TBWT_RAW_KARAMBWAN);
        fish.put("leaping sturgeon",ItemID.BRUT_STURGEON);fish.put("shark",ItemID.RAW_SHARK);fish.put("infernal eel",ItemID.INFERNAL_EEL);fish.put("anglerfish",ItemID.RAW_ANGLERFISH);
        fish.put("dark crab",ItemID.RAW_DARK_CRAB);fish.put("sacred eel",ItemID.SNAKEBOSS_EEL);fish.put("swordtip squid",ItemID.RAW_SWORDTIP_SQUID);fish.put("jumbo squid",ItemID.RAW_JUMBO_SQUID);
        FISH=Collections.unmodifiableMap(fish);
    }
    static boolean rawFish(int id)
    {return FISH.containsValue(id)||id==ItemID.RAW_SEATURTLE||id==ItemID.RAW_MANTARAY||id==ItemID.AERIAL_FISHING_BLUEGILL||id==ItemID.AERIAL_FISHING_COMMON_TENCH||id==ItemID.AERIAL_FISHING_MOTTLED_EEL||id==ItemID.AERIAL_FISHING_GREATER_SIREN;}
    private static final Pattern ENTRY=Pattern.compile("([0-9]{1,2}) x ([A-Za-z][A-Za-z '\\-]*)(?:,\\s*|$)");
    static int checkedFish(String text)
    {
        if(text==null||text.length()>4096)return -1;
        text=text.replace("<br>"," ").replaceAll("<[^>]*>","").trim();
        if("The barrel is empty.".equals(text))return 0;
        String prefix="The barrel contains: ";if(!text.startsWith(prefix))return -1;
        String contents=text.substring(prefix.length()).trim();if(contents.endsWith("."))contents=contents.substring(0,contents.length()-1);
        Matcher matcher=ENTRY.matcher(contents);int end=0,total=0;
        while(matcher.find()){if(matcher.start()!=end)return -1;int count=Integer.parseInt(matcher.group(1));if(count<1||count>CAPACITY)return -1;total+=count;end=matcher.end();}
        return end==contents.length()&&end>0&&total<=CAPACITY?total:-1;
    }
    private static final Pattern CAUGHT=Pattern.compile("^You catch (?:an?|some)(?: raw)? ([a-zA-Z ]+)[.!]?(?: It hardens as you handle it with your ice gloves\\.)?$");
    static boolean caughtFish(String text)
    {if(text==null)return false;Matcher matcher=CAUGHT.matcher(text);return matcher.matches()&&FISH.containsKey(matcher.group(1));}
    static boolean bonusCatch(String text)
    {return "Rada's blessing enabled you to catch an extra fish.".equals(text)||"The spirit flakes enabled you to catch an extra fish.".equals(text)||"Your cormorant returns with its catch.".equals(text);}
    static int totalPlanks(int[] counts)
    {if(counts==null||counts.length!=Plank.values().length)return -1;int total=0;for(int count:counts){if(count<0||count>CAPACITY)return -1;total+=count;}return total<=CAPACITY?total:-1;}
}
