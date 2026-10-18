/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import net.runelite.api.Animation;
import net.runelite.api.Skill;
import static net.runelite.api.gameval.AnimationID.*;
import net.runelite.api.gameval.SpriteID;
import static net.runelite.api.gameval.ItemID.*;
import net.runelite.client.plugins.skillcalculator.skills.WoodcuttingAction;
import net.runelite.client.plugins.skillcalculator.skills.MiningAction;
import java.util.Set;

/** Current local animations; an animation duration is deliberately not a universal action cooldown. */
final class ActionProgressRules
{
    static final String ALCHEMY_SOURCE="https://oldschool.runescape.wiki/w/High_Level_Alchemy?oldid=15362543";
    static final long HIGH_ALCHEMY_MILLIS=5*600L;
    static final String ACTION_LENGTH_SOURCE="https://oldschool.runescape.wiki/w/Game_tick/Action_lengths?oldid=15319478";
    private static final Set<String> GLASS=Set.of("crafting:beer_glass","crafting:empty_candle_lantern","crafting:empty_oil_lamp","crafting:vial","crafting:fishbowl","crafting:unpowered_orb","crafting:lantern_lens","crafting:light_orb");

    private static final class Action
    {
        final String label;
        final Skill skill;
        final int product;
        Action(String label, Skill skill, int product)
        { this.label = label; this.skill = skill; this.product = product; }
    }

    private static final Map<Integer, Action> ACTIONS;
    static
    {
        Map<Integer, Action> actions = new LinkedHashMap<>();
        add(actions, "High Level Alchemy", Skill.MAGIC, HUMAN_CASTHIGHLVLALCHEMY);
        add(actions, "Mixing potions", Skill.HERBLORE, HUMAN_HERBING_VIAL);
        add(actions, "Grinding ingredients", Skill.HERBLORE, HUMAN_HERBING_GRIND);
        add(actions, "Glassblowing", Skill.CRAFTING, HUMAN_GLASSBLOWING);
        add(actions, "Crafting leather", Skill.CRAFTING, HUMAN_LEATHER_CRAFTING);
        add(actions, "Fletching", Skill.FLETCHING, HUMAN_FLETCHING);
        add(actions, "Fletching bolts", Skill.FLETCHING, HUMAN_FLETCHING_HUNTINGBOLTS);
        add(actions, "Cooking", Skill.COOKING, HUMAN_COOKING);
        add(actions, "Cooking on a fire", Skill.COOKING, HUMAN_FIRECOOKING);
        add(actions, "Smithing", Skill.SMITHING, HUMAN_SMITHING, BRUT_HUMAN_SMITHING);
        add(actions, "Fishing", Skill.FISHING, HUMAN_FISHING_CASTING, HUMAN_FISH_ONSPOT,
            HUMAN_FISHING_CASTING_PEARL_OILY, HUMAN_FISH_ONSPOT_PEARL_OILY);
        add(actions, "Offering remains", Skill.PRAYER, HUMAN_PRAY, HUMAN_PRAY_LOOP);
        add(actions, "Runecrafting", Skill.RUNECRAFT, HUMAN_RUNECRAFT);
        add(actions, "Construction", Skill.CONSTRUCTION, HUMAN_POH_BUILD, HUMAN_POH_BUILD_FLOOR,
            HUMAN_POH_BUILD_WALL, HUMAN_POH_BUILD_DWH);
        add(actions, "Crafting", Skill.CRAFTING, HUMAN_WOODCRAFTING_HAMMER, HUMAN_WOODCRAFTING_KNIFE);
        add(actions, "Attaching battlestaves", Skill.CRAFTING, HUMAN_BATTLESTAFF_CRAFTING);
        // A furnace is shared by smithing, jewellery and glass: no assumed skill.
        add(actions, "Furnace work", null, HUMAN_FURNACE);
        add(actions, "Low Level Alchemy", Skill.MAGIC, HUMAN_CASTLOWLVLALCHEMY);
        add(actions, "Superheat Item", Skill.MAGIC, HUMAN_CASTSUPERHEATITEM);
        gem(actions, HUMAN_OPALCUTTING, OPAL);
        gem(actions, HUMAN_JADECUTTING, JADE);
        gem(actions, HUMAN_REDTOPAZCUTTING, RED_TOPAZ);
        gem(actions, HUMAN_SAPPHIRECUTTING, SAPPHIRE);
        gem(actions, HUMAN_EMERALDCUTTING, EMERALD);
        gem(actions, HUMAN_RUBYCUTTING, RUBY);
        gem(actions, HUMAN_DIAMONDCUTTING, DIAMOND);
        gem(actions, HUMAN_DRAGONSTONECUTTING, DRAGONSTONE);
        bow(actions, STRINGING_SHORTBOW, SHORTBOW);
        bow(actions, STRINGING_OAK_SHORTBOW, OAK_SHORTBOW);
        bow(actions, STRINGING_WILLOW_SHORTBOW, WILLOW_SHORTBOW);
        bow(actions, STRINGING_MAPLE_SHORTBOW, MAPLE_SHORTBOW);
        bow(actions, STRINGING_YEW_SHORTBOW, YEW_SHORTBOW);
        bow(actions, STRINGING_MAGIC_SHORTBOW, MAGIC_SHORTBOW);
        bow(actions, STRINGING_LONGBOW, LONGBOW);
        bow(actions, STRINGING_OAK_LONGBOW, OAK_LONGBOW);
        bow(actions, STRINGING_WILLOW_LONGBOW, WILLOW_LONGBOW);
        bow(actions, STRINGING_MAPLE_LONGBOW, MAPLE_LONGBOW);
        bow(actions, STRINGING_YEW_LONGBOW, YEW_LONGBOW);
        bow(actions, STRINGING_MAGIC_LONGBOW, MAGIC_LONGBOW);
        ACTIONS = Collections.unmodifiableMap(actions);
    }

    private static void add(Map<Integer, Action> actions, String label, Skill skill, int... animations)
    {
        Action action = new Action(label, skill, 0);
        for (int animation : animations) actions.put(animation, action);
    }

    private static void gem(Map<Integer, Action> actions, int animation, int product)
    { actions.put(animation, new Action("Cutting gems", Skill.CRAFTING, product)); }

    private static void bow(Map<Integer, Action> actions, int animation, int product)
    { actions.put(animation, new Action("Stringing bows", Skill.FLETCHING, product)); }

    static String label(int animation)
    {
        Skill skill = gatheringSkill(animation);
        if (skill == Skill.WOODCUTTING) return "Woodcutting";
        if (skill == Skill.MINING) return "Mining";
        Action action = ACTIONS.get(animation);
        return action == null ? null : action.label;
    }

    static int spellSprite(int animation)
    {
        if(animation==HUMAN_CASTHIGHLVLALCHEMY)return SpriteID.Magicon.HIGH_LEVEL_ALCHEMY;
        if(animation==HUMAN_CASTLOWLVLALCHEMY)return SpriteID.Magicon.LOW_LEVEL_ALCHEMY;
        if(animation==HUMAN_CASTSUPERHEATITEM)return SpriteID.Magicon.SUPERHEAT_ITEM;
        return 0;
    }

    private static Skill gatheringSkill(int animation)
    {
        if (NodeTimerRules.CHOPPING.contains(animation)) return Skill.WOODCUTTING;
        if (NodeTimerRules.MINING.contains(animation)) return Skill.MINING;
        return null;
    }

    static Skill skill(int animation)
    {
        Skill gathering = gatheringSkill(animation);
        Action action = ACTIONS.get(animation);
        return gathering != null ? gathering : action == null ? null : action.skill;
    }

    /** Candidate products. Reused hand-work sequences still require native item evidence. */
    static int animationItem(int animation)
    {
        Action action = ACTIONS.get(animation);
        return action == null ? 0 : action.product;
    }

    private static boolean stringing(int animation)
    {
        Action action = ACTIONS.get(animation);
        return action != null && action.product > 0 && action.skill == Skill.FLETCHING;
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
    static ActionRecipeCatalog.Recipe menuRecipe(String option,int clickedItem)
    {
        if(clickedItem<=0||option==null)return null;String verb=normal(option);
        Skill skill=verb.startsWith("clean")||verb.startsWith("mix")?Skill.HERBLORE:verb.startsWith("cook")?Skill.COOKING:verb.startsWith("fletch")||verb.startsWith("string")?Skill.FLETCHING:null;
        boolean supported=skill!=null||verb.startsWith("make")||verb.startsWith("craft")||verb.startsWith("cut")||verb.startsWith("smelt")||verb.startsWith("smith");
        if(!supported)return null;
        ActionRecipeCatalog.Recipe match=null;
        for(ActionRecipeCatalog.Recipe recipe:ActionRecipeCatalog.recipes())
        {
            if(skill!=null&&skill!=recipe.skill)continue;
            boolean exact=recipe.icon==clickedItem;
            if(!exact&&(verb.startsWith("clean")||verb.startsWith("cook")))exact=recipe.ingredients.size()==1&&recipe.ingredients.get(0).itemId==clickedItem;
            if(exact){if(match!=null)return null;match=recipe;}
        }
        return match;
    }
    static boolean cleaning(ActionRecipeCatalog.Recipe recipe)
    {return recipe!=null&&recipe.skill==Skill.HERBLORE&&recipe.name.startsWith("Clean ")&&recipe.ingredients.size()==1&&!recipe.products.isEmpty();}
    static boolean handWork(int animation)
    {
        Action action = ACTIONS.get(animation);
        return action != null && (action.product > 0 || action.skill == Skill.FLETCHING || action.skill == Skill.HERBLORE);
    }
    static boolean ambiguousItem(int animation)
    {
        Action action = ACTIONS.get(animation);
        return action != null && action.product > 0 && action.skill == Skill.CRAFTING;
    }
    private static String normal(String text)
    {return text==null?"":text.toLowerCase(Locale.ROOT).replace('_',' ').replaceAll("\\s*\\([^)]*\\)","").trim();}
    static long spellCycle(int animation)
    {
        if(animation==HUMAN_CASTHIGHLVLALCHEMY)return HIGH_ALCHEMY_MILLIS;
        if(animation==HUMAN_CASTLOWLVLALCHEMY||animation==HUMAN_CASTSUPERHEATITEM)return 3*600L;
        return 0;
    }
    /** Normal-mode source cycles only. Special tools and variable/instant operations are excluded. */
    static long recipeCycle(ActionRecipeCatalog.Recipe recipe,int animation,Set<Integer> carried,boolean first)
    {
        if(recipe==null)return 0;String id=recipe.id;
        if(id.startsWith("herblore:")&&id.endsWith("_3")&&animation==HUMAN_HERBING_VIAL)return 2*600L;
        if(gemAnimation(id)==animation&&!carried.contains(JEWELLERS_CHISEL))return 2*600L;
        if(id.startsWith("crafting:")&&id.contains("battlestaff")&&animation==HUMAN_BATTLESTAFF_CRAFTING)return 2*600L;
        if(id.startsWith("crafting:")&&id.endsWith("_dhide_body")&&animation==HUMAN_LEATHER_CRAFTING)return 3*600L;
        if(GLASS.contains(id)&&animation==HUMAN_GLASSBLOWING)return 3*600L;
        if(id.startsWith("crafting:")&&(id.endsWith("_bracelet")||id.endsWith("_necklace")||id.endsWith("_amulet")||id.endsWith("_ring"))&&animation==HUMAN_FURNACE)return 3*600L;
        if(id.startsWith("fletching:")&&id.endsWith("bow_u")&&animation==HUMAN_FLETCHING)return (carried.contains(FLETCHING_KNIFE)&&!first?2:3)*600L;
        if(id.startsWith("fletching:")&&id.endsWith("bow")&&stringing(animation))return 2*600L;
        if(id.startsWith("cooking:")&&!id.contains("karambwan")&&!id.contains("wine")&&(animation==HUMAN_COOKING||animation==HUMAN_FIRECOOKING))return (first?3:4)*600L;
        return 0;
    }
    private static int gemAnimation(String recipe)
    {
        switch(recipe)
        {
            case "crafting:sapphire":return HUMAN_SAPPHIRECUTTING;
            case "crafting:emerald":return HUMAN_EMERALDCUTTING;
            case "crafting:ruby":return HUMAN_RUBYCUTTING;
            case "crafting:diamond":return HUMAN_DIAMONDCUTTING;
            case "crafting:dragonstone":return HUMAN_DRAGONSTONECUTTING;
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
