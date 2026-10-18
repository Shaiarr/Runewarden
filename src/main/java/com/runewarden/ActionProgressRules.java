/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import net.runelite.api.Animation;
import net.runelite.api.Skill;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.skillcalculator.skills.WoodcuttingAction;
import net.runelite.client.plugins.skillcalculator.skills.MiningAction;
import java.util.Set;

/** Current local animations; an animation duration is deliberately not a universal action cooldown. */
final class ActionProgressRules
{
    static final String ALCHEMY_SOURCE="https://oldschool.runescape.wiki/w/High_Level_Alchemy?oldid=15362543";
    static final long HIGH_ALCHEMY_MILLIS=5*600L;
    static final String ACTION_LENGTH_SOURCE="https://oldschool.runescape.wiki/w/Game_tick/Action_lengths?oldid=15319478";
    private static final Set<Integer> GEMS=Set.of(AnimationID.HUMAN_DRAGONSTONECUTTING,AnimationID.HUMAN_DIAMONDCUTTING,AnimationID.HUMAN_RUBYCUTTING,AnimationID.HUMAN_SAPPHIRECUTTING,AnimationID.HUMAN_EMERALDCUTTING,AnimationID.HUMAN_OPALCUTTING,AnimationID.HUMAN_JADECUTTING,AnimationID.HUMAN_REDTOPAZCUTTING);
    private static final Set<Integer> STRINGING=Set.of(AnimationID.STRINGING_SHORTBOW,AnimationID.STRINGING_OAK_SHORTBOW,AnimationID.STRINGING_WILLOW_SHORTBOW,AnimationID.STRINGING_MAPLE_SHORTBOW,AnimationID.STRINGING_YEW_SHORTBOW,AnimationID.STRINGING_MAGIC_SHORTBOW,AnimationID.STRINGING_LONGBOW,AnimationID.STRINGING_OAK_LONGBOW,AnimationID.STRINGING_WILLOW_LONGBOW,AnimationID.STRINGING_MAPLE_LONGBOW,AnimationID.STRINGING_YEW_LONGBOW,AnimationID.STRINGING_MAGIC_LONGBOW);
    private static final Set<String> GLASS=Set.of("crafting:beer_glass","crafting:empty_candle_lantern","crafting:empty_oil_lamp","crafting:vial","crafting:fishbowl","crafting:unpowered_orb","crafting:lantern_lens","crafting:light_orb");
    private static final Map<Integer,String> LABELS;
    static
    {
        Map<Integer,String> labels=new LinkedHashMap<>();
        labels.put(AnimationID.HUMAN_CASTHIGHLVLALCHEMY,"High Level Alchemy");
        labels.put(AnimationID.HUMAN_HERBING_VIAL,"Mixing potions");labels.put(AnimationID.HUMAN_HERBING_GRIND,"Grinding ingredients");
        labels.put(AnimationID.HUMAN_GLASSBLOWING,"Glassblowing");labels.put(AnimationID.HUMAN_LEATHER_CRAFTING,"Crafting leather");
        labels.put(AnimationID.HUMAN_FLETCHING,"Fletching");labels.put(AnimationID.HUMAN_FLETCHING_HUNTINGBOLTS,"Fletching bolts");
        labels.put(AnimationID.HUMAN_COOKING,"Cooking");labels.put(AnimationID.HUMAN_FIRECOOKING,"Cooking on a fire");
        labels.put(AnimationID.HUMAN_SMITHING,"Smithing");labels.put(AnimationID.BRUT_HUMAN_SMITHING,"Smithing");
        labels.put(AnimationID.HUMAN_FISHING_CASTING,"Fishing");labels.put(AnimationID.HUMAN_FISH_ONSPOT,"Fishing");
        labels.put(AnimationID.HUMAN_FISHING_CASTING_PEARL_OILY,"Fishing");labels.put(AnimationID.HUMAN_FISH_ONSPOT_PEARL_OILY,"Fishing");
        labels.put(AnimationID.HUMAN_PRAY,"Offering remains");labels.put(AnimationID.HUMAN_PRAY_LOOP,"Offering remains");
        labels.put(AnimationID.HUMAN_RUNECRAFT,"Runecrafting");
        labels.put(AnimationID.HUMAN_POH_BUILD,"Construction");labels.put(AnimationID.HUMAN_POH_BUILD_FLOOR,"Construction");labels.put(AnimationID.HUMAN_POH_BUILD_WALL,"Construction");labels.put(AnimationID.HUMAN_POH_BUILD_DWH,"Construction");
        labels.put(AnimationID.HUMAN_WOODCRAFTING_HAMMER,"Crafting");labels.put(AnimationID.HUMAN_WOODCRAFTING_KNIFE,"Crafting");
        labels.put(AnimationID.HUMAN_BATTLESTAFF_CRAFTING,"Attaching battlestaves");labels.put(AnimationID.HUMAN_FURNACE,"Furnace work");
        labels.put(AnimationID.HUMAN_CASTLOWLVLALCHEMY,"Low Level Alchemy");labels.put(AnimationID.HUMAN_CASTSUPERHEATITEM,"Superheat Item");
        for(int gem:GEMS)labels.put(gem,"Cutting gems");for(int stringing:STRINGING)labels.put(stringing,"Stringing bows");
        LABELS=Collections.unmodifiableMap(labels);
    }
    static String label(int animation)
    {
        if(NodeTimerRules.CHOPPING.contains(animation))return "Woodcutting";
        if(NodeTimerRules.MINING.contains(animation))return "Mining";
        return LABELS.get(animation);
    }
    static int spellSprite(int animation)
    {
        if(animation==AnimationID.HUMAN_CASTHIGHLVLALCHEMY)return SpriteID.Magicon.HIGH_LEVEL_ALCHEMY;
        if(animation==AnimationID.HUMAN_CASTLOWLVLALCHEMY)return SpriteID.Magicon.LOW_LEVEL_ALCHEMY;
        if(animation==AnimationID.HUMAN_CASTSUPERHEATITEM)return SpriteID.Magicon.SUPERHEAT_ITEM;
        return 0;
    }
    static Skill skill(int animation)
    {
        if(NodeTimerRules.CHOPPING.contains(animation))return Skill.WOODCUTTING;
        if(NodeTimerRules.MINING.contains(animation))return Skill.MINING;
        if(spellSprite(animation)>0)return Skill.MAGIC;
        if(GEMS.contains(animation)||animation==AnimationID.HUMAN_GLASSBLOWING||animation==AnimationID.HUMAN_LEATHER_CRAFTING||animation==AnimationID.HUMAN_WOODCRAFTING_HAMMER||animation==AnimationID.HUMAN_WOODCRAFTING_KNIFE||animation==AnimationID.HUMAN_BATTLESTAFF_CRAFTING)return Skill.CRAFTING;
        if(STRINGING.contains(animation)||animation==AnimationID.HUMAN_FLETCHING||animation==AnimationID.HUMAN_FLETCHING_HUNTINGBOLTS)return Skill.FLETCHING;
        if(animation==AnimationID.HUMAN_HERBING_VIAL||animation==AnimationID.HUMAN_HERBING_GRIND)return Skill.HERBLORE;
        if(animation==AnimationID.HUMAN_COOKING||animation==AnimationID.HUMAN_FIRECOOKING)return Skill.COOKING;
        if(animation==AnimationID.HUMAN_SMITHING||animation==AnimationID.BRUT_HUMAN_SMITHING)return Skill.SMITHING;
        if(animation==AnimationID.HUMAN_FISHING_CASTING||animation==AnimationID.HUMAN_FISH_ONSPOT||animation==AnimationID.HUMAN_FISHING_CASTING_PEARL_OILY||animation==AnimationID.HUMAN_FISH_ONSPOT_PEARL_OILY)return Skill.FISHING;
        if(animation==AnimationID.HUMAN_PRAY||animation==AnimationID.HUMAN_PRAY_LOOP)return Skill.PRAYER;
        if(animation==AnimationID.HUMAN_RUNECRAFT)return Skill.RUNECRAFT;
        if(animation==AnimationID.HUMAN_POH_BUILD||animation==AnimationID.HUMAN_POH_BUILD_FLOOR||animation==AnimationID.HUMAN_POH_BUILD_WALL||animation==AnimationID.HUMAN_POH_BUILD_DWH)return Skill.CONSTRUCTION;
        // A furnace is shared by smithing, jewellery and glass: no assumed skill.
        return null;
    }
    /** Candidate products. Reused hand-work sequences still require native item evidence. */
    static int animationItem(int animation)
    {
        switch(animation)
        {
            case AnimationID.HUMAN_OPALCUTTING:return ItemID.OPAL;
            case AnimationID.HUMAN_JADECUTTING:return ItemID.JADE;
            case AnimationID.HUMAN_REDTOPAZCUTTING:return ItemID.RED_TOPAZ;
            case AnimationID.HUMAN_SAPPHIRECUTTING:return ItemID.SAPPHIRE;
            case AnimationID.HUMAN_EMERALDCUTTING:return ItemID.EMERALD;
            case AnimationID.HUMAN_RUBYCUTTING:return ItemID.RUBY;
            case AnimationID.HUMAN_DIAMONDCUTTING:return ItemID.DIAMOND;
            case AnimationID.HUMAN_DRAGONSTONECUTTING:return ItemID.DRAGONSTONE;
            case AnimationID.STRINGING_SHORTBOW:return ItemID.SHORTBOW;
            case AnimationID.STRINGING_OAK_SHORTBOW:return ItemID.OAK_SHORTBOW;
            case AnimationID.STRINGING_WILLOW_SHORTBOW:return ItemID.WILLOW_SHORTBOW;
            case AnimationID.STRINGING_MAPLE_SHORTBOW:return ItemID.MAPLE_SHORTBOW;
            case AnimationID.STRINGING_YEW_SHORTBOW:return ItemID.YEW_SHORTBOW;
            case AnimationID.STRINGING_MAGIC_SHORTBOW:return ItemID.MAGIC_SHORTBOW;
            case AnimationID.STRINGING_LONGBOW:return ItemID.LONGBOW;
            case AnimationID.STRINGING_OAK_LONGBOW:return ItemID.OAK_LONGBOW;
            case AnimationID.STRINGING_WILLOW_LONGBOW:return ItemID.WILLOW_LONGBOW;
            case AnimationID.STRINGING_MAPLE_LONGBOW:return ItemID.MAPLE_LONGBOW;
            case AnimationID.STRINGING_YEW_LONGBOW:return ItemID.YEW_LONGBOW;
            case AnimationID.STRINGING_MAGIC_LONGBOW:return ItemID.MAGIC_LONGBOW;
            default:return 0;
        }
    }
    static Skill resourceSkill(String option)
    {return "Chop down".equalsIgnoreCase(option)||"Chop".equalsIgnoreCase(option)?Skill.WOODCUTTING:"Mine".equalsIgnoreCase(option)?Skill.MINING:null;}
    static int resourceItem(Skill skill,String target)
    {
        String name=normal(target);
        if(skill==Skill.WOODCUTTING)
        {
            if("tree".equals(name)||"evergreen".equals(name)||"dead tree".equals(name))return WoodcuttingAction.LOGS.getItemId();
            for(WoodcuttingAction action:WoodcuttingAction.values())
            {
                String type=normal(action.name()).replaceFirst(" logs$","").replaceFirst(" tree$","");
                if(name.equals(type)||name.equals(type+" tree"))return action.getItemId();
            }
        }
        if(skill==Skill.MINING)for(MiningAction action:MiningAction.values())
        {
            String type=normal(action.name()).replaceFirst(" ore$","");
            if(name.equals(type)||name.equals(type+" rocks")||name.equals(type+" rock"))return action.getItemId();
        }
        return 0;
    }
    static BankedExperienceCalculator.Recipe menuRecipe(String option,int clickedItem)
    {
        if(clickedItem<=0||option==null)return null;String verb=normal(option);
        Skill skill=verb.startsWith("clean")||verb.startsWith("mix")?Skill.HERBLORE:verb.startsWith("cook")?Skill.COOKING:verb.startsWith("fletch")||verb.startsWith("string")?Skill.FLETCHING:null;
        boolean supported=skill!=null||verb.startsWith("make")||verb.startsWith("craft")||verb.startsWith("cut")||verb.startsWith("smelt")||verb.startsWith("smith");
        if(!supported)return null;
        BankedExperienceCalculator.Recipe match=null;
        for(BankedExperienceCalculator.Recipe recipe:BankedExperienceCalculator.recipes())
        {
            if(skill!=null&&skill!=recipe.skill)continue;
            boolean exact=recipe.icon==clickedItem;
            if(!exact&&(verb.startsWith("clean")||verb.startsWith("cook")))exact=recipe.ingredients.size()==1&&recipe.ingredients.get(0).itemId==clickedItem;
            if(exact){if(match!=null)return null;match=recipe;}
        }
        return match;
    }
    static boolean cleaning(BankedExperienceCalculator.Recipe recipe)
    {return recipe!=null&&recipe.skill==Skill.HERBLORE&&recipe.name.startsWith("Clean ")&&recipe.ingredients.size()==1&&!recipe.products.isEmpty();}
    static boolean handWork(int animation)
    {return GEMS.contains(animation)||STRINGING.contains(animation)||animation==AnimationID.HUMAN_FLETCHING||animation==AnimationID.HUMAN_FLETCHING_HUNTINGBOLTS||animation==AnimationID.HUMAN_HERBING_VIAL||animation==AnimationID.HUMAN_HERBING_GRIND;}
    static boolean ambiguousItem(int animation){return GEMS.contains(animation);}
    private static String normal(String text)
    {return text==null?"":text.toLowerCase(Locale.ROOT).replace('_',' ').replaceAll("\\s*\\([^)]*\\)","").trim();}
    static long spellCycle(int animation)
    {
        if(animation==AnimationID.HUMAN_CASTHIGHLVLALCHEMY)return HIGH_ALCHEMY_MILLIS;
        if(animation==AnimationID.HUMAN_CASTLOWLVLALCHEMY||animation==AnimationID.HUMAN_CASTSUPERHEATITEM)return 3*600L;
        return 0;
    }
    /** Normal-mode source cycles only. Special tools and variable/instant operations are excluded. */
    static long recipeCycle(BankedExperienceCalculator.Recipe recipe,int animation,Set<Integer> carried,boolean first)
    {
        if(recipe==null)return 0;String id=recipe.id;
        if(id.startsWith("herblore:")&&id.endsWith("_3")&&animation==AnimationID.HUMAN_HERBING_VIAL)return 2*600L;
        if(gemAnimation(id)==animation&&!carried.contains(net.runelite.api.gameval.ItemID.JEWELLERS_CHISEL))return 2*600L;
        if(id.startsWith("crafting:")&&id.contains("battlestaff")&&animation==AnimationID.HUMAN_BATTLESTAFF_CRAFTING)return 2*600L;
        if(id.startsWith("crafting:")&&id.endsWith("_dhide_body")&&animation==AnimationID.HUMAN_LEATHER_CRAFTING)return 3*600L;
        if(GLASS.contains(id)&&animation==AnimationID.HUMAN_GLASSBLOWING)return 3*600L;
        if(id.startsWith("crafting:")&&(id.endsWith("_bracelet")||id.endsWith("_necklace")||id.endsWith("_amulet")||id.endsWith("_ring"))&&animation==AnimationID.HUMAN_FURNACE)return 3*600L;
        if(id.startsWith("fletching:")&&id.endsWith("bow_u")&&animation==AnimationID.HUMAN_FLETCHING)return (carried.contains(net.runelite.api.gameval.ItemID.FLETCHING_KNIFE)&&!first?2:3)*600L;
        if(id.startsWith("fletching:")&&id.endsWith("bow")&&STRINGING.contains(animation))return 2*600L;
        if(id.startsWith("cooking:")&&!id.contains("karambwan")&&!id.contains("wine")&&(animation==AnimationID.HUMAN_COOKING||animation==AnimationID.HUMAN_FIRECOOKING))return (first?3:4)*600L;
        return 0;
    }
    private static int gemAnimation(String recipe)
    {
        switch(recipe)
        {
            case "crafting:sapphire":return AnimationID.HUMAN_SAPPHIRECUTTING;
            case "crafting:emerald":return AnimationID.HUMAN_EMERALDCUTTING;
            case "crafting:ruby":return AnimationID.HUMAN_RUBYCUTTING;
            case "crafting:diamond":return AnimationID.HUMAN_DIAMONDCUTTING;
            case "crafting:dragonstone":return AnimationID.HUMAN_DRAGONSTONECUTTING;
            default:return -1;
        }
    }
    static boolean nonCombat(Skill skill)
    {return skill!=null&&skill!=Skill.ATTACK&&skill!=Skill.STRENGTH&&skill!=Skill.DEFENCE&&skill!=Skill.HITPOINTS&&skill!=Skill.RANGED&&skill!=Skill.SLAYER;}
    static final class Timing
    {
        final long durationMillis;private final int[] frames;
        Timing(long duration,int[] frames){durationMillis=duration;this.frames=frames;}
        long elapsedAtFrame(int frame)
        {
            if(frame<=0)return 0;
            if(frames==null)return Math.min(durationMillis,frame*20L);
            long sum=0;for(int i=0;i<Math.min(frame,frames.length);i++)sum+=frames[i]*20L;
            return Math.min(durationMillis,sum);
        }
    }
    static Timing timing(Animation animation)
    {
        if(animation==null)return null;int[] frames=animation.getFrameLengths();long cycles=0;
        if(frames!=null&&frames.length>0&&frames.length<=4096)
        {frames=frames.clone();for(int frame:frames){if(frame<0)return null;cycles+=frame;}}
        // Cached (Maya) sequences have no classic frame-length array. The API defines
        // getNumFrames() as their duration in client ticks; getDuration() may be absent.
        else{frames=null;cycles=animation.isMayaAnim()?animation.getNumFrames():animation.getDuration();}
        long duration=cycles*20L;
        return duration>0&&duration<=120_000?new Timing(duration,frames):null;
    }
}
