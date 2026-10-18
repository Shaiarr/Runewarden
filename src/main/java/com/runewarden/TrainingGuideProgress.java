/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;

/** Client-thread guide state from exact recipe inputs/outputs and native account state. */
@Singleton
final class TrainingGuideProgress
{
    private final Client client;private final ConfigManager config;private final GuideCatalog guides;
    private final SharedActivitySession session;
    private final Map<String,BankedExperienceCalculator.Recipe> recipes=new LinkedHashMap<>();
    private Supplier<ActionProgressTool.Snapshot> action=ActionProgressTool.Snapshot::empty;
    @Inject TrainingGuideProgress(Client client,ConfigManager config,GuideCatalog guides,ActivityXpCatalog methods,SharedActivitySession session)
    {
        this.client=client;this.config=config;this.guides=guides;this.session=session;
        Map<String,BankedExperienceCalculator.Recipe> byId=new LinkedHashMap<>();for(BankedExperienceCalculator.Recipe recipe:BankedExperienceCalculator.recipes())byId.put(recipe.id,recipe);
        for(ActivityXpCatalog.Method method:methods.methods(null))
        {BankedExperienceCalculator.Recipe recipe=byId.get(method.recipeId);if(recipe!=null)recipes.putIfAbsent(method.guideId,recipe);}
    }
    void setActionSupplier(Supplier<ActionProgressTool.Snapshot> supplier){action=supplier==null?ActionProgressTool.Snapshot::empty:supplier;}
    GuideLiveProgress.Reading read(String guideId)
    {
        if(client.getGameState()!=GameState.LOGGED_IN || client.getLocalPlayer()==null)return GuideLiveProgress.Reading.UNKNOWN;
        String profile=config.getRSProfileKey();GuideCatalog.Entry guide=guideId==null?null:guides.entry(guideId);
        if(profile==null||guide==null||!"training".equals(guide.category))return GuideLiveProgress.Reading.UNKNOWN;
        SharedActivitySession.Snapshot run=session.snapshot();
        if(profile.equals(run.profile)&&run.context!=null&&guideId.equals(run.context.guideId)&&run.context.skill!=null&&run.context.targetXp>0
            &&client.getSkillExperience(run.context.skill)>=run.context.targetXp)
            return reading(profile,guideId,null,"target:"+run.runId+":"+run.context.targetXp,"Target complete","Your selected XP target is reached.",true,Collections.emptySet());
        if("training:farming-contracts".equals(guideId))return contract(profile,guideId);
        if("training:bird-house-runs".equals(guideId))return birdHouses(profile,guideId);
        BankedExperienceCalculator.Recipe recipe=recipes.get(guideId);if(recipe==null)return GuideLiveProgress.Reading.UNKNOWN;
        String readyStep=readyStep(recipe,guideId);if(readyStep==null)return GuideLiveProgress.Reading.UNKNOWN;
        ItemContainer container=client.getItemContainer(InventoryID.INV);if(container==null)return GuideLiveProgress.Reading.UNKNOWN;
        Map<Integer,Long> inventory=new LinkedHashMap<>();for(Item item:container.getItems())if(item!=null&&item.getId()>0&&item.getQuantity()>0)inventory.merge(item.getId(),(long)item.getQuantity(),Long::sum);
        boolean inputs=BankedExperienceCalculator.availableActions(recipe,inventory)>0&&tools(recipe,inventory);
        ActionProgressTool.Snapshot observed=action.get();long now=System.currentTimeMillis();
        boolean working=observed!=null&&profile.equals(observed.profile)&&(observed.batchItemId==recipe.icon&&observed.completed>0&&now-observed.observedAt>=0&&now-observed.observedAt<=30_000
            ||observed.cycle()&&observed.cycleEnd>now&&observed.cycleSkill==recipe.skill&&observed.cycleItemId==recipe.icon);
        if(recipe.id.startsWith("fletching:")&&recipe.id.endsWith("_longbow_u"))
        {
            BankedExperienceCalculator.Recipe stringing=findRecipe(recipe.id.substring(0,recipe.id.length()-2));
            if(stringing!=null&&BankedExperienceCalculator.availableActions(stringing,inventory)>0)
                return reading(profile,guideId,"method:3","recipe:"+stringing.id+":string","String the bows","Unstrung bows and bow strings are already in your inventory.",false,Collections.emptySet());
            if(stringing!=null&&!inputs)for(BankedExperienceCalculator.Ingredient output:stringing.products)
                if(inventory.getOrDefault(output.itemId,0L)>=output.quantity)
                    return reading(profile,guideId,"method:3","recipe:"+stringing.id+":output","Finished bows in inventory","Bank the finished bows and restock for the next batch.",false,Collections.emptySet());
        }
        Set<String> confirmed=new LinkedHashSet<>();
        if(inputs)
        {
            if("method:2".equals(readyStep))confirmed.add("method:1");
            String step=working&&recipe.skill==Skill.FIREMAKING?"method:2":readyStep;
            return reading(profile,guideId,step,"recipe:"+recipe.id+":inputs:"+step,working?"Continue the current batch":"Supplies ready",
                working?"Continue the current batch; its inputs are already loaded.":"Your recipe inputs are already in your inventory. Continue with the method.",false,confirmed);
        }
        if(!recipe.products.isEmpty())for(BankedExperienceCalculator.Ingredient output:recipe.products)
            if(inventory.getOrDefault(output.itemId,0L)>=output.quantity)
            {
                String step=outputStep(recipe);
                return reading(profile,guideId,step,"recipe:"+recipe.id+":output","Finished items in inventory",
                    "You already carry "+LoadoutReadinessTool.itemName(output.itemId)+". Bank or use the output and restock for the next batch.",false,Collections.emptySet());
            }
        return GuideLiveProgress.Reading.UNKNOWN;
    }
    private static String readyStep(BankedExperienceCalculator.Recipe recipe,String guide)
    {
        if(recipe.skill==Skill.HERBLORE)return "method:2";
        if(recipe.skill==Skill.CRAFTING)
        {
            String id=recipe.id.substring("crafting:".length());
            if(id.equals("bow_string"))return "method:1";
            if(id.equals("lantern_lens")||id.equals("unpowered_orb")||id.equals("light_orb")||id.equals("sapphire")||id.equals("emerald")||id.equals("ruby")||id.equals("diamond")||id.equals("dragonstone")
                ||id.endsWith("_bracelet")||id.endsWith("_battlestaff")||id.endsWith("_dhide_body"))return "method:2";
        }
        if(recipe.skill==Skill.COOKING)return "training:karambwan-1tick".equals(guide)?"method:1":"method:2";
        if(recipe.skill==Skill.FLETCHING&&(recipe.id.endsWith("_longbow_u")||recipe.id.endsWith("_dart")||recipe.id.endsWith(":headless_arrow")))return "method:1";
        return recipe.skill==Skill.FIREMAKING?"method:1":null;
    }
    private static String outputStep(BankedExperienceCalculator.Recipe recipe)
    {
        if(recipe.skill==Skill.COOKING&&!Set.of("cooking:jug_of_wine","cooking:cooked_karambwan","cooking:shrimps").contains(recipe.id))return "method:2";
        if(recipe.skill==Skill.FLETCHING&&recipe.id.endsWith("_longbow_u"))return "method:2";
        if(recipe.skill==Skill.CRAFTING&&Set.of("crafting:sapphire","crafting:emerald","crafting:ruby","crafting:diamond","crafting:dragonstone").contains(recipe.id))return "method:2";
        return "method:3";
    }
    private static boolean tools(BankedExperienceCalculator.Recipe recipe,Map<Integer,Long> inventory)
    {
        String id=recipe.id;
        if(id.endsWith("_bracelet"))return has(inventory,ItemID.JEWL_BRACELET_MOULD);
        if(Set.of("crafting:lantern_lens","crafting:unpowered_orb","crafting:light_orb").contains(id))return has(inventory,ItemID.GLASSBLOWINGPIPE);
        if(Set.of("crafting:sapphire","crafting:emerald","crafting:ruby","crafting:diamond","crafting:dragonstone").contains(id))return has(inventory,ItemID.CHISEL);
        if(id.endsWith("_dhide_body"))return has(inventory,ItemID.NEEDLE)&&has(inventory,ItemID.THREAD);
        if(id.endsWith("_longbow_u"))return has(inventory,ItemID.KNIFE)||has(inventory,ItemID.FLETCHING_KNIFE);
        return recipe.skill!=Skill.FIREMAKING||has(inventory,ItemID.TINDERBOX);
    }
    private static boolean has(Map<Integer,Long> inventory,int item){return inventory.getOrDefault(item,0L)>0;}
    private static BankedExperienceCalculator.Recipe findRecipe(String id){for(BankedExperienceCalculator.Recipe recipe:BankedExperienceCalculator.recipes())if(id.equals(recipe.id))return recipe;return null;}
    private GuideLiveProgress.Reading contract(String profile,String guide)
    {
        int done=client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_COMPLETE),type=client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_TYPE);
        return done>0?reading(profile,guide,"method:3","contract:claim:"+type,"Contract ready to claim","The requested crop is complete. Speak to Jane to claim the seed pack.",false,Collections.emptySet())
            :type>0?reading(profile,guide,"method:2","contract:grow:"+type,"Contract already assigned","Grow and harvest the crop in your assigned contract.",false,Collections.emptySet())
            :reading(profile,guide,"method:1","contract:request","Request a contract","Speak to Jane for the next eligible farming contract.",false,Collections.emptySet());
    }
    private GuideLiveProgress.Reading birdHouses(String profile,String guide)
    {
        WorldPoint location=client.getLocalPlayer().getWorldLocation();
        if(location==null||location.getPlane()!=0||!Set.of(14650,14651,14652,14906,14907,15162,15163).contains(location.getRegionID()))return GuideLiveProgress.Reading.UNKNOWN;
        int[] varps={VarPlayerID.BIRDHOUSE_TRANSMIT_A,VarPlayerID.BIRDHOUSE_TRANSMIT_B,VarPlayerID.BIRDHOUSE_TRANSMIT_C,VarPlayerID.BIRDHOUSE_TRANSMIT_D};
        int filled=0,built=0;boolean ready=false,timed=true;StringBuilder key=new StringBuilder("birdhouses:");long now=System.currentTimeMillis()/1000;
        for(int varp:varps)
        {
            int value=client.getVarpValue(varp);if(value<0||value>27)return GuideLiveProgress.Reading.UNKNOWN;key.append(value).append(':');
            if(value>0&&value%3==0)
            {
                filled++;String stored=config.getConfiguration(TimeTrackingConfig.CONFIG_GROUP,profile,TimeTrackingConfig.BIRD_HOUSE+"."+varp);
                try{String[] parts=stored==null?new String[0]:stored.split(":");long at=parts.length==2?Long.parseLong(parts[1]):0;
                    if(parts.length!=2||Integer.parseInt(parts[0])!=value||at<=0||at>now)timed=false;else if(at+50*60<=now)ready=true;}
                catch(NumberFormatException invalid){timed=false;}
            }
            else if(value>0)built++;
        }
        String step=ready?"method:1":built>0||filled<4?"method:2":timed?"method:3":"method:1";
        String body=ready?"A previously filled bird house is ready. Empty ready houses before replacing them.":built>0?"A replacement house is already built. Fill it with suitable seeds.":filled<4?filled+" / 4 houses are filled. Place and seed the remaining houses.":timed?"All four bird houses are filled. Bank the nests and return after their catching interval.":"All four bird houses are filled. Check which houses are ready to empty.";
        return reading(profile,guide,step,key+step,"Bird house run",body,false,Collections.emptySet());
    }
    private static GuideLiveProgress.Reading reading(String profile,String guide,String step,String key,String title,String body,boolean terminal,Set<String> completed)
    {return new GuideLiveProgress.Reading(profile,guide,step,key,title,body,terminal,completed);}
}
