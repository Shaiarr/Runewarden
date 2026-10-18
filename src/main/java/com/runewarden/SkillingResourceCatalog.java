/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Skill;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.plugins.skillcalculator.skills.FishingAction;
import net.runelite.client.plugins.skillcalculator.skills.CookingAction;
import net.runelite.client.plugins.skillcalculator.skills.ItemSkillAction;
import net.runelite.client.plugins.skillcalculator.skills.MiningAction;
import net.runelite.client.plugins.skillcalculator.skills.WoodcuttingAction;

/** Local resource facts. XP belongs to a named action, never to an item without context. */
@Singleton
public final class SkillingResourceCatalog
{
    public static final class Action
    {
        final String name;
        final Skill skill;
        final int level;
        final double xp;
        final int quantity;
        final String note;
        final String source;
        final String ingredients;
        Action(String name, Skill skill, int level, double xp, int quantity, String note, String source, String ingredients)
        {
            this.name = name; this.skill = skill; this.level = level; this.xp = xp;
            this.quantity = quantity; this.note = note; this.source = source; this.ingredients = ingredients;
        }
        String summary()
        {
            return title(skill.name()) + " " + level + " · " + name + ": " + number(xp)
                + " XP / action" + (quantity > 1 ? " (uses " + quantity + ")" : "");
        }
    }

    private static final class FarmingStage
    { String name; int level; double xp; String note; }

    public static final class Resource
    {
        int itemId;
        String name;
        String source;
        String use;
        String note;
        String wiki;
        String farmingSource;
        List<FarmingStage> farmingActions;
        private final List<Action> actions = new ArrayList<>();
        public List<Action> actions() { return Collections.unmodifiableList(actions); }
    }

    private final Map<Integer, Resource> resources;
    @Inject SkillingResourceCatalog(Gson gson) { resources = load(gson); }
    public Resource find(int canonicalItemId) { return resources.get(canonicalItemId); }
    public int size() { return resources.size(); }

    private static Map<Integer, Resource> load(Gson gson)
    {
        Map<Integer, Resource> result = new LinkedHashMap<>();
        List<Resource> facts = gson.fromJson(new InputStreamReader(
            SkillingResourceCatalog.class.getResourceAsStream("/com/runewarden/skilling-resource-details.json"),
            StandardCharsets.UTF_8), new TypeToken<List<Resource>>() {}.getType());
        for (Resource fact : facts)
        {
            result.put(fact.itemId, fact);
            if (fact.farmingActions != null) for (FarmingStage stage : fact.farmingActions)
                fact.actions.add(new Action(stage.name, Skill.FARMING, stage.level, stage.xp, 1,
                    stage.note, fact.farmingSource, ""));
        }
        for (ActionRecipeCatalog.Recipe recipe : ActionRecipeCatalog.recipes())
        {
            StringBuilder ingredients = new StringBuilder();
            for (ActionRecipeCatalog.Ingredient ingredient : recipe.ingredients)
            {
                if (ingredients.length() > 0) ingredients.append(" + ");
                ingredients.append(ingredient.quantity).append(" × ").append(ingredient.name);
            }
            for (ActionRecipeCatalog.Ingredient ingredient : recipe.ingredients)
            {
                Resource resource = result.computeIfAbsent(ingredient.itemId, id -> resource(id, ingredient.name));
                resource.actions.add(new Action(recipe.name, recipe.skill, recipe.level, recipe.xp,
                    ingredient.quantity, recipe.note, recipe.source, ingredients.toString()));
            }
        }
        for (WoodcuttingAction action : WoodcuttingAction.values())
            addGathering(result, action, Skill.WOODCUTTING, "Chop", "Woodcutting: chop the corresponding tree.");
        for (MiningAction action : MiningAction.values())
            addGathering(result, action, Skill.MINING, "Mine", "Mining: mine the corresponding rocks or deposit.");
        for (FishingAction action : FishingAction.values())
        {
            addGathering(result, action, Skill.FISHING, "Catch", "Fishing: catch at the corresponding fishing spot.");
            Resource resource = result.get(action.getItemId());
            if (resource.actions.stream().noneMatch(a -> a.skill == Skill.COOKING))
            {
                String name = action.name().replaceFirst("^RAW_", "");
                try
                {
                    CookingAction cooking = CookingAction.valueOf(name);
                    resource.actions.add(new Action("Prepare " + title(action.name()), Skill.COOKING,
                        cooking.getLevel(), cooking.getXp(), 1,
                        "XP for successful preparation; method and unlocks depend on the fish.",
                        "RuneLite 1.13.2 CookingAction." + cooking.name(), "1 × " + resource.name));
                }
                catch (IllegalArgumentException ignored) { /* Not every catch is cooked. */ }
            }
            switch (action.name())
            {
                case "MINNOW": resource.use = "Trade 40 minnows to Kylie Minnow for one raw shark."; break;
                case "INFERNAL_EEL": resource.use = "Crack with a hammer for Tokkul and possible onyx bolt tips or lava scale shards."; break;
                case "SACRED_EEL": resource.use = "Dissect with a knife to obtain Zulrah's scales."; break;
                case "LEAPING_TROUT": case "LEAPING_SALMON": case "LEAPING_STURGEON":
                    resource.use = "Cut with a knife for fish offcuts and possible roe or caviar."; break;
                case "RAW_KARAMBWANJI": resource.use = "Bait for catching karambwan."; break;
                default: break;
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Resource resource(int id, String name)
    { Resource resource = new Resource(); resource.itemId = id; resource.name = name; return resource; }

    private static void addGathering(Map<Integer, Resource> resources, ItemSkillAction action,
        Skill skill, String verb, String source)
    {
        String actionName = title(((Enum<?>) action).name());
        Resource resource = resources.computeIfAbsent(action.getItemId(), id -> resource(id, actionName));
        if (resource.source == null) resource.source = source;
        resource.actions.add(0, new Action(verb + " " + actionName, skill, action.getLevel(), action.getXp(), 1,
            "Base XP per successful gathering action; equipment and other bonuses are separate.",
            "RuneLite 1.13.2 " + action.getClass().getSimpleName(), ""));
    }

    static String title(String text)
    {
        String lower = text.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
    static String number(double value)
    { return value == Math.rint(value) ? String.format(Locale.ROOT, "%,.0f", value) : String.format(Locale.ROOT, "%,.1f", value); }

    /** A compact default; Shift reveals named ingredient and requirement details for more actions. */
    public static String tooltip(Resource resource, boolean expanded, java.util.function.ToIntFunction<Skill> levels)
    { return tooltip(resource, expanded, levels, 18); }

    static String tooltip(Resource resource, boolean expanded, java.util.function.ToIntFunction<Skill> levels, int maxRows)
    {
        if (resource == null) return null;
        StringBuilder text = new StringBuilder("<col=e0be71>" + safe(resource.name) + "</col>");
        if (resource.use != null) line(text, "Use: " + resource.use);
        if (resource.source != null) line(text, "Source: " + resource.source);
        int limit = expanded ? 6 : 4;
        int count = 0;
        for (Action action : resource.actions)
        {
            if (count++ >= limit) break;
            boolean locked = levels.applyAsInt(action.skill) < action.level;
            line(text, (locked ? "<col=ff9a86>" : "<col=eeeeee>") + safe((locked ? "Requires " : "") + action.summary()) + "</col>", true);
            if (expanded)
            {
                if (!action.ingredients.isEmpty()) line(text, action.ingredients);
                if (action.note != null && !action.note.isEmpty()) line(text, action.note);
            }
        }
        if (resource.actions.size() > limit)
            line(text, "+ " + (resource.actions.size() - limit) + " other actions" + (expanded ? "" : " · hold Shift for details"));
        else if (!expanded && !resource.actions.isEmpty()) line(text, "Hold Shift for ingredients and method details.");
        if (resource.note != null) line(text, resource.note);
        String[] rows = text.toString().split("<br>");
        if (rows.length <= maxRows) return text.toString();
        StringBuilder bounded = new StringBuilder();
        for (int i = 0; i < Math.max(1, maxRows - 1); i++)
        { if (i > 0) bounded.append("<br>"); bounded.append(rows[i]); }
        return bounded.append("<br><col=e0be71>More methods in Banked experience</col>").toString();
    }

    private static void line(StringBuilder text, String value) { line(text, value, false); }
    private static void line(StringBuilder text, String value, boolean tagged)
    {
        // TooltipManager supports game colour tags and <br>; wrap plain facts into readable short rows.
        String colour = "";
        if (tagged)
        {
            int end = value.indexOf('>');
            colour = value.substring(0, end + 1);
            value = value.substring(end + 1).replace("</col>", "");
        }
        StringBuilder row = new StringBuilder();
        for (String word : safe(value).split(" "))
        {
            if (row.length() + word.length() > 62 && row.length() > 0)
            { text.append("<br>").append(colour).append(row).append(colour.isEmpty() ? "" : "</col>"); row.setLength(0); }
            if (row.length() > 0) row.append(' ');
            row.append(word);
        }
        if (row.length() > 0) text.append("<br>").append(colour).append(row).append(colour.isEmpty() ? "" : "</col>");
    }
    private static String safe(String text) { return text.replace('<', '[').replace('>', ']'); }
}
