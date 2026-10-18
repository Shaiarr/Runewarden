/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Skill;
import net.runelite.api.QuestState;

/** Local training scenarios. Variable activity estimates never enter the deterministic bank allocation. */
@Singleton
public final class ActivityXpCatalog
{
    public static final class Rate
    {
        public int minLevel = 1, maxLevel = 99;
        public double minXpPerHour, maxXpPerHour;
        public boolean scalesWithLevel;
        public double low(int level) { return minXpPerHour * (scalesWithLevel ? level / 99.0 : 1); }
        public double high(int level) { return maxXpPerHour * (scalesWithLevel ? level / 99.0 : 1); }
    }

    public static final class Unlock
    {
        public String kind, key, label; public int value; public boolean started;
        /** 1 met, -1 missing, 0 not observed. Textual unlocks are never guessed from levels. */
        public int state(Map<Skill, Integer> levels, Map<String, QuestState> quests, int combat)
        {
            if ("skill".equals(kind))
            {
                Skill skill = GoalCatalog.skill(key); Integer level = skill == null ? null : levels.get(skill);
                return level == null ? 0 : level >= value ? 1 : -1;
            }
            if ("quest".equals(kind))
            {
                QuestState state = quests.get(GoalCatalog.questKey(key));
                return state == null ? 0 : state == QuestState.FINISHED || started && state == QuestState.IN_PROGRESS ? 1 : -1;
            }
            return "metric".equals(kind) && "Combat".equals(key) && combat > 0 ? combat >= value ? 1 : -1 : 0;
        }
    }

    public static final class Method
    {
        public String id, name, guideId, intensity = "Normal", kind = "time", source, note = "";
        public Skill skill;
        public int level = 1;
        public boolean explicitPlan;
        public String attention = "", rateBasis = "", actionUnit = "actions", recipeId;
        public double xpPerAction;
        public List<Unlock> unlocks = new ArrayList<>();
        public List<String> preparation = new ArrayList<>(), loop = new ArrayList<>(), milestones = new ArrayList<>(), rateConditions = new ArrayList<>();
        public double minXpPerHour, maxXpPerHour;
        public List<Rate> rates = new ArrayList<>();
        public List<String> requirements = new ArrayList<>();
        public int plankId;
        public double planksPerUnit, barsPerUnit, xpPerUnit;
        public String lowerMetal, upperMetal;
        public int lowerBars, upperBars;
        public Rate ratesAt(int playerLevel)
        {
            for (Rate rate : rates) if (playerLevel >= rate.minLevel && playerLevel <= rate.maxLevel) return rate;
            return null;
        }
        @Override public String toString() { return name; }
    }

    public static final class Material { public int itemId, bars; public String name; }
    public static final class Metal { public String name; public List<Material> items; }
    public static final class Herb { public String name, paste; public int amount; public List<Integer> itemIds; }
    public static final class Potion
    {
        public String code, name; public int level; public double xp;
        @Override public String toString() { return name + " (" + code + ")"; }
    }
    private static final class File
    { List<Method> methods; List<Metal> metals; List<Herb> herbs; List<Potion> potions; }
    private final Map<String, Method> methods = new LinkedHashMap<>();
    private final Map<Skill, List<Method>> skillIndex = new EnumMap<>(Skill.class);
    private List<Method> orderedMethods;
    private List<Method> activityMethods;
    private final File data;

    @Inject public ActivityXpCatalog(Gson gson, GuideCatalog guides)
    {
        data = gson.fromJson(new InputStreamReader(ActivityXpCatalog.class.getResourceAsStream(
            "/com/runewarden/activity-xp-methods.json"), StandardCharsets.UTF_8), File.class);
        if (data == null || data.methods == null) throw new IllegalStateException("Missing activity XP methods");
        for (Method method : data.methods)
        {
            if (method.id == null || method.name == null || method.skill == null || method.level < 1
                || method.level > 99 || methods.put(method.id, method) != null)
                throw new IllegalStateException("Invalid activity XP method");
            GuideCatalog.Entry guide = guides.entry(method.guideId);
            if (guide == null) throw new IllegalStateException("Missing activity guide: " + method.id);
            if (guide.requirements != null) for (String requirement : guide.requirements)
                if (!method.requirements.contains(requirement)) method.requirements.add(requirement);
            if (guide.requirementDetails != null) for (JsonObject requirement : guide.requirementDetails)
            {
                Unlock unlock = gson.fromJson(requirement, Unlock.class);
                if (unlock.kind != null && method.unlocks.stream().noneMatch(existing -> java.util.Objects.equals(existing.kind, unlock.kind) && java.util.Objects.equals(existing.key, unlock.key))) method.unlocks.add(unlock);
            }
            if (method.explicitPlan && (method.preparation.isEmpty() || method.loop.isEmpty() || method.milestones.isEmpty() || method.source == null))
                throw new IllegalStateException("Incomplete training plan: " + method.id);
            for (Rate rate : method.rates)
            {
                if (!Double.isFinite(rate.minXpPerHour) || !Double.isFinite(rate.maxXpPerHour) || rate.minXpPerHour <= 0 || rate.maxXpPerHour < rate.minXpPerHour || rate.minLevel < method.level || rate.maxLevel < rate.minLevel || rate.maxLevel > 99)
                    throw new IllegalStateException("Invalid activity XP rate");
                method.minXpPerHour = method.minXpPerHour == 0 ? rate.minXpPerHour : Math.min(method.minXpPerHour, rate.minXpPerHour);
                method.maxXpPerHour = Math.max(method.maxXpPerHour, rate.maxXpPerHour);
            }
        }
        for (String category : new String[]{"training", "minigame"})
            for (GuideCatalog.Entry guide : guides.search("", category))
            {
                if (methods.containsKey(guide.id)) continue;
                Skill skill;
                // RuneLite's deprecated Skill.OVERALL is null, not an enum member.
                try { skill = guide.skill == null ? null : Skill.valueOf("runecrafting".equalsIgnoreCase(guide.skill) ? "RUNECRAFT" : guide.skill.toUpperCase(Locale.ROOT)); }
                catch (RuntimeException error) { continue; }
                Method method = new Method(); method.id = guide.id; method.guideId = guide.id;
                method.name = guide.title; method.skill = skill; method.source = guide.source;
                method.note = guide.subtitle == null ? "" : guide.subtitle;
                if (guide.requirementDetails != null) for (JsonObject requirement : guide.requirementDetails) method.unlocks.add(gson.fromJson(requirement, Unlock.class));
                String detail = method.note.toLowerCase(Locale.ROOT);
                method.intensity = detail.contains("afk") || detail.contains("relaxed") ? "AFK"
                    : detail.contains("intensive") || detail.contains("tick") ? "Intensive" : "Normal";
                if (guide.requirements != null) method.requirements.addAll(guide.requirements);
                if (guide.requirementDetails != null) for (JsonObject requirement : guide.requirementDetails)
                    if (requirement.has("kind") && "skill".equals(requirement.get("kind").getAsString())
                        && requirement.has("key") && skill != null && (skill.name().equalsIgnoreCase(requirement.get("key").getAsString())
                            || (skill == Skill.RUNECRAFT && "runecrafting".equalsIgnoreCase(requirement.get("key").getAsString())))
                        && requirement.has("value")) method.level = Math.max(method.level, requirement.get("value").getAsInt());
                methods.put(method.id, method);
            }
        List<Method> ordered = new ArrayList<>(methods.values());
        ordered.sort(Comparator.comparingInt((Method m) -> m.level).thenComparing(m -> m.name));
        orderedMethods = Collections.unmodifiableList(ordered);
        List<Method> untyped = new ArrayList<>();
        for (Method method : ordered)
        {
            if (method.skill == null) untyped.add(method);
            else skillIndex.computeIfAbsent(method.skill, ignored -> new ArrayList<>()).add(method);
        }
        activityMethods = Collections.unmodifiableList(untyped);
        skillIndex.replaceAll((skill, entries) -> Collections.unmodifiableList(entries));
    }

    /** Explicit plans only; guide-only browser entries are deliberately excluded from coverage. */
    public List<Method> plans(Skill skill)
    {
        List<Method> result = new ArrayList<>(); for (Method method : methods(skill)) if (method.explicitPlan) result.add(method);
        return Collections.unmodifiableList(result);
    }
    public Method find(String id) { return methods.get(id); }
    public List<Metal> metals() { return Collections.unmodifiableList(data.metals); }
    public List<Herb> herbs() { return Collections.unmodifiableList(data.herbs); }
    public List<Potion> potions() { return Collections.unmodifiableList(data.potions); }
    public List<Method> activities() { return activityMethods; }
    public List<Method> methods(Skill skill)
    {
        return skill == null ? orderedMethods : skillIndex.getOrDefault(skill, Collections.emptyList());
    }
}
