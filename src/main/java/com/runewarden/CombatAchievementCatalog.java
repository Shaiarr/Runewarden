/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.VarPlayerID;

/** Complete revision-pinned Wiki reference. Task IDs are native packed completion-bit indices. */
@Singleton
final class CombatAchievementCatalog
{
    static final List<String> DIFFICULTIES = List.of("Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster");
    static final int[] COMPLETION_VARPS = {
        VarPlayerID.CA_TASK_COMPLETED_0, VarPlayerID.CA_TASK_COMPLETED_1, VarPlayerID.CA_TASK_COMPLETED_2,
        VarPlayerID.CA_TASK_COMPLETED_3, VarPlayerID.CA_TASK_COMPLETED_4, VarPlayerID.CA_TASK_COMPLETED_5,
        VarPlayerID.CA_TASK_COMPLETED_6, VarPlayerID.CA_TASK_COMPLETED_7, VarPlayerID.CA_TASK_COMPLETED_8,
        VarPlayerID.CA_TASK_COMPLETED_9, VarPlayerID.CA_TASK_COMPLETED_10, VarPlayerID.CA_TASK_COMPLETED_11,
        VarPlayerID.CA_TASK_COMPLETED_12, VarPlayerID.CA_TASK_COMPLETED_13, VarPlayerID.CA_TASK_COMPLETED_14,
        VarPlayerID.CA_TASK_COMPLETED_15, VarPlayerID.CA_TASK_COMPLETED_16, VarPlayerID.CA_TASK_COMPLETED_17,
        VarPlayerID.CA_TASK_COMPLETED_18, VarPlayerID.CA_TASK_COMPLETED_19, VarPlayerID.CA_TASK_COMPLETED_20
    };

    static final class Requirement { String kind, key, label; int value; boolean boostable, started, baseLevelOnly; List<Requirement> children; }
    static final class ItemReference { String name; int itemId; }
    static final class Task
    {
        int id, points, revision;
        String name, difficulty, boss, type, description, source, guideId;
        List<String> context, method;
        List<Requirement> requirements;
        List<ItemReference> items;
        @Override public String toString() { return name; }
    }
    private static final class Data { String retrieved, overviewSource; Map<String, Integer> rewardThresholds; List<Task> tasks; }
    private final Data data;
    private final Map<Integer, Task> byId = new LinkedHashMap<>();
    private final List<String> bosses, types;

    @Inject CombatAchievementCatalog(Gson gson)
    {
        try (InputStream input = CombatAchievementCatalog.class.getResourceAsStream("/com/runewarden/combat-achievements.json"))
        {
            if (input == null) throw new IllegalStateException("Missing combat achievement reference");
            data = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Data.class);
        }
        catch (IOException error) { throw new IllegalStateException("Could not read combat achievements", error); }
        if (data == null || data.tasks == null || data.rewardThresholds == null) throw new IllegalStateException("Invalid combat achievement reference");
        java.util.TreeSet<String> bossNames = new java.util.TreeSet<>(), typeNames = new java.util.TreeSet<>();
        for (Task task : data.tasks)
        {
            int tier = DIFFICULTIES.indexOf(task.difficulty);
            if (task.id < 0 || task.id >= COMPLETION_VARPS.length * 32 || tier < 0 || task.points != tier + 1
                || task.name == null || task.source == null || byId.put(task.id, task) != null)
                throw new IllegalStateException("Invalid combat achievement task");
            if (task.context == null) task.context = Collections.emptyList();
            if (task.method == null) task.method = Collections.emptyList();
            if (task.requirements == null) task.requirements = Collections.emptyList();
            if (task.items == null) task.items = Collections.emptyList();
            bossNames.add(task.boss); typeNames.add(task.type);
        }
        bosses = Collections.unmodifiableList(new ArrayList<>(bossNames)); types = Collections.unmodifiableList(new ArrayList<>(typeNames));
    }

    /** Combine task-specific facts with the exact linked encounter's typed access rules. */
    List<Requirement> requirements(Task task, GuideCatalog guides)
    {
        if (task == null) return Collections.emptyList();
        Map<String, Requirement> result = new LinkedHashMap<>();
        for (Requirement requirement : task.requirements) result.put(requirementKey(requirement), requirement);
        GuideCatalog.Entry guide = task.guideId == null ? null : guides.entry(task.guideId);
        if (guide != null && guide.requirementDetails != null) for (com.google.gson.JsonObject fact : guide.requirementDetails)
        { Requirement requirement = requirement(fact); if (requirement != null) result.putIfAbsent(requirementKey(requirement), requirement); }
        if (guide != null && guide.requirements != null) for (String text : guide.requirements)
        { Requirement requirement = encounterRequirement(text); result.putIfAbsent(requirementKey(requirement), requirement); }
        return Collections.unmodifiableList(new ArrayList<>(result.values()));
    }
    private static Requirement encounterRequirement(String text)
    {
        Requirement result = new Requirement(); result.kind = "access"; result.key = text; result.label = text; result.value = 1;
        java.util.regex.Matcher skill = java.util.regex.Pattern.compile("^([1-9][0-9]?) (Attack|Strength|Defence|Ranged|Prayer|Magic|Runecraft|Runecrafting|Construction|Hitpoints|Agility|Herblore|Thieving|Crafting|Fletching|Slayer|Hunter|Mining|Smithing|Fishing|Cooking|Firemaking|Woodcutting|Farming|Sailing)$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        if (skill.matches()) { result.kind = "skill"; result.value = Integer.parseInt(skill.group(1)); result.key = skill.group(2); result.baseLevelOnly = true; return result; }
        if (!text.startsWith("Complete ") || text.startsWith("Complete or ")) return result;
        String title = text.substring(9).replaceFirst(" for repeat kills$", "").replaceFirst(" miniquest$", "");
        String normalized = GoalCatalog.questKey(title);
        for (net.runelite.api.Quest quest : net.runelite.api.Quest.values())
        {
            String key = GoalCatalog.questKey(quest.getName());
            if (key.equals(normalized) || normalized.equals("deserttreasureii") && key.equals("deserttreasureiithefallenempire"))
            { result.kind = "quest"; result.key = quest.getName(); return result; }
        }
        return result;
    }
    private static String requirementKey(Requirement value) { return value.kind + ":" + value.key + ":" + value.value + ":" + value.started; }
    private static Requirement requirement(com.google.gson.JsonObject fact)
    {
        if (fact.has("ironmanOnly") && fact.get("ironmanOnly").getAsBoolean()) return null;
        Requirement result = new Requirement();
        result.kind = fact.has("kind") ? fact.get("kind").getAsString() : "unknown";
        result.key = fact.has("key") ? fact.get("key").getAsString() : "";
        result.label = fact.has("label") ? fact.get("label").getAsString() : result.key;
        result.value = fact.has("value") && fact.get("value").isJsonPrimitive() ? fact.get("value").getAsInt() : 1;
        result.boostable = fact.has("boostable") && fact.get("boostable").getAsBoolean();
        result.started = fact.has("started") && fact.get("started").getAsBoolean();
        if (fact.has("anyOf") && fact.get("anyOf").isJsonArray())
        { result.kind = "choice"; result.key = result.label; result.children = new ArrayList<>(); for (com.google.gson.JsonElement child : fact.getAsJsonArray("anyOf")) if (child.isJsonObject()) { Requirement converted = requirement(child.getAsJsonObject()); if (converted != null) result.children.add(converted); } }
        return result;
    }
    Task find(int id) { return byId.get(id); }
    List<Task> tasks() { return Collections.unmodifiableList(data.tasks); }
    List<String> bosses() { return bosses; }
    List<String> types() { return types; }
    int threshold(String difficulty) { return data.rewardThresholds.getOrDefault(difficulty, 0); }
    String source() { return data.overviewSource; }
    int totalPoints() { return data.tasks.stream().mapToInt(task -> task.points).sum(); }
    int points(Set<Integer> completed) { int result = 0; for (int id : completed) { Task task = find(id); if (task != null) result += task.points; } return result; }
    String nextTier(int points)
    { for (String tier : DIFFICULTIES) if (points < threshold(tier)) return tier; return "Grandmaster"; }

    List<Task> filter(Set<Integer> completed, String difficulty, String boss, String type, String search, boolean incomplete)
    {
        String needle = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        List<Task> result = new ArrayList<>();
        for (Task task : data.tasks)
            if ((!incomplete || !completed.contains(task.id)) && (difficulty == null || "All tiers".equals(difficulty) || task.difficulty.equals(difficulty))
                && (boss == null || "All bosses".equals(boss) || task.boss.equals(boss))
                && (type == null || "All types".equals(type) || task.type.equals(type))
                && (task.name + " " + task.boss + " " + task.description).toLowerCase(java.util.Locale.ROOT).contains(needle)) result.add(task);
        result.sort(Comparator.comparingInt((Task task) -> task.points).thenComparingInt(task -> typeRank(task.type)).thenComparing(task -> task.boss).thenComparing(task -> task.name));
        return result;
    }

    private static int typeRank(String type)
    { return "Kill Count".equals(type) ? 0 : "Mechanical".equals(type) ? 1 : "Restriction".equals(type) ? 2 : "Perfection".equals(type) ? 3 : "Stamina".equals(type) ? 4 : 5; }

    static boolean complete(int[] values, int taskId)
    { return taskId >= 0 && taskId / 32 < values.length && ((values[taskId / 32] >>> (taskId % 32)) & 1) != 0; }
}
