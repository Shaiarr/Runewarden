/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/** Evaluates only explicit data rules on RuneLite's client thread. */
final class GuideCompletion
{
    private GuideCompletion() { }

    static boolean supportsAutomatic(GuideCatalog.Entry entry)
    {
        return entry != null && ("training".equals(entry.category)
            || "minigame".equals(entry.category));
    }

    /** Follow the authored method matching an observed native objective, without inventing checklist completion. */
    static int activityStep(GuideCatalog.Entry entry, MinigameActivityCatalog.Snapshot activity)
    {
        if(entry==null||activity==null||activity.activity==null||!matchesActivity(entry,activity)
            ||activity.steps.isEmpty()||!MinigameReleasePolicy.allowsAutomaticGuidance(activity.activity))return -1;
        if(!entry.id.equals(activity.guideId)&&activity.activity==MinigameActivityCatalog.Activity.MAGE_TRAINING_ARENA)
        {
            java.util.List<GuideCatalog.DisplayStep> alias=entry.flattened();
            for(int index=0;index<alias.size();index++)if("method:1".equals(alias.get(index).step.id))return index;
            return -1;
        }
        if(activity.guideStepId!=null)
        {
            java.util.List<GuideCatalog.DisplayStep> exact=entry.flattened();
            for(int index=0;index<exact.size();index++)if(activity.guideStepId.equals(exact.get(index).step.id))return index;
            return -1;
        }
        String title=activity.steps.get(0).title.toLowerCase(Locale.ROOT),id;
        switch(activity.activity)
        {
            case WINTERTODT:id=contains(title,"chop","fletch")?"method:1":contains(title,"warmth","potion","pyromancer")?"method:3":"method:2";break;
            case GOTR:id=contains(title,"gather","mine","fragment","craft guardian essence")?"method:1":"method:2";break;
            case TEMPOROSS:id=contains(title,"spirit")?"method:3":contains(title,"catch","cook")?"method:1":"method:2";break;
            case GIANTS_FOUNDRY:id=contains(title,"prepare","commission")?"prepare:1":contains(title,"mould")?"prepare:2":contains(title,"collect","pour")?"method:1":contains(title,"return")?"method:3":"method:2";break;
            case MIXOLOGY:id=contains(title,"prepare","paste","hopper")?"method:1":"method:2";break;
            case MAHOGANY_HOMES:id=contains(title,"customer","speak","tea")?"method:2":contains(title,"contract","agent")?"prepare:2":"method:1";break;
            case PEST_CONTROL:id="method:2";break;
            case BLAST_FURNACE:id="method:2";break;
            case TITHE_FARM:id=contains(title,"harvest","deposit")?"method:2":"method:1";break;
            case BARBARIAN_ASSAULT:id=contains(title,"queen","omega")?"method:4":contains(title,"dispenser","supplies")?"method:1":contains(title,"wait for yours","call and")?"method:2":"method:3";break;
            case NIGHTMARE_ZONE:id=contains(title,"protection","absorption","health")?"method:2":"method:3";break;
            case PYRAMID_PLUNDER:id="method:2";break;
            case MAGE_TRAINING_ARENA:id=contains(title,"alchem","enchant")?"method:3":contains(title,"bones","graveyard","health")?"method:4":"method:2";break;
            case VOLCANIC_MINE:id=contains(title,"exit","leave")?"method:3":contains(title,"vent","chamber")?"method:1":"method:2";break;
            case BLAST_MINE:id=contains(title,"chisel","excavat","load")?"method:1":contains(title,"operator")?"method:3":"method:2";break;
            case FISHING_TRAWLER:id=contains(title,"contribution","finish")?"method:3":"method:2";break;
            case DRIFT_NET:id=contains(title,"empty","replacement")?"method:3":contains(title,"set net")?"method:1":"method:2";break;
            case TROUBLE_BREWING:id=contains(title,"process","deposit","cut")?"method:3":"method:2";break;
            case SOUL_WARS:id=contains(title,"supplies","restock")?"method:1":contains(title,"fragment","offer")?"method:3":contains(title,"avatar")?"method:4":"method:2";break;
            case CASTLE_WARS:id=contains(title,"bandage","supplies")?"method:2":contains(title,"return","capture","standard")?"method:3":"method:4";break;
            default:return -1;
        }
        java.util.List<GuideCatalog.DisplayStep> steps=entry.flattened();
        for(int index=0;index<steps.size();index++)if(id.equals(steps.get(index).step.id))return index;
        return -1;
    }

    static boolean matchesActivity(GuideCatalog.Entry entry,MinigameActivityCatalog.Snapshot activity)
    {
        if(entry==null||activity==null||activity.activity==null)return false;
        return entry.id.equals(activity.guideId)||activity.activity==MinigameActivityCatalog.Activity.MAGE_TRAINING_ARENA
            &&"training:mage-training-arena".equals(entry.id)&&"minigame:mage-training-arena".equals(activity.guideId);
    }

    /** Leaving an activity also closes its selected variant when no current recipe can be resolved. */
    static boolean belongsToActivity(GuideCatalog.Entry entry,MinigameActivityCatalog.Snapshot activity)
    {
        if(matchesActivity(entry,activity))return true;
        if(entry==null||entry.id==null||activity==null)return false;
        if(activity.activity==MinigameActivityCatalog.Activity.MAHOGANY_HOMES)return entry.id.startsWith("training:homes-");
        return activity.activity==MinigameActivityCatalog.Activity.BLAST_FURNACE
            &&java.util.Set.of("training:blast-steel","training:blast-mithril","training:blast-adamant","training:blast-rune","training:blast-gold").contains(entry.id);
    }

    private static boolean contains(String value,String... words)
    {for(String word:words)if(value.contains(word))return true;return false;}

    static boolean observed(Client client, GuideCatalog.Entry entry, GuideCatalog.Step step)
    {
        if (!supportsAutomatic(entry) || step == null || step.completion == null
            || client.getGameState() != GameState.LOGGED_IN) return false;
        GuideCatalog.CompletionRule rule = step.completion;
        if ("inventory".equals(rule.kind))
        {
            if (rule.itemId <= 0 || rule.quantity <= 0) return false;
            ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
            if (inventory == null) return false;
            long quantity = 0;
            for (Item item : inventory.getItems())
                if (item != null && item.getId() == rule.itemId) quantity += item.getQuantity();
            return quantity >= rule.quantity;
        }
        if ("quest".equals(rule.kind))
        {
            for (Quest quest : Quest.values())
                if (quest.getName().equals(rule.quest)) return quest.getState(client) == QuestState.FINISHED;
        }
        if ("level".equals(rule.kind) && rule.level >= 1 && rule.level <= 99 && rule.skill != null)
        {
            try
            {
                Skill skill = Skill.valueOf("runecrafting".equals(rule.skill) ? "RUNECRAFT" : rule.skill.toUpperCase(Locale.ROOT));
                return client.getRealSkillLevel(skill) >= rule.level;
            }
            catch (IllegalArgumentException ignored) { return false; }
        }
        return false;
    }
}
