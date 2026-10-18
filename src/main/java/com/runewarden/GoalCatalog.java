/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Experience;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/** Progression rules reuse sourced method and guide requirements, including uncertain unlocks. */
@Singleton
final class GoalCatalog
{
    private final GuideCatalog guides;
    private final ActivityXpCatalog activities;
    private final AccountProgressTool progress;
    @Inject GoalCatalog(GuideCatalog guides, ActivityXpCatalog activities, AccountProgressTool progress)
    { this.guides = guides; this.activities = activities; this.progress = progress; }
    GoalCatalog(GuideCatalog guides, ActivityXpCatalog activities) { this(guides, activities, null); }
    AccountProgressTool progressTool() { return progress; }
    Long progress(GoalPlan goal) { return progress == null ? null : progress.progress(goal); }
    /** Exact sourced supply candidates, including alternatives; never a personal completion claim. */
    Set<Integer> goalSupplyTargets(GoalPlan goal)
    {
        if(goal==null||goal.archived||goal.achievedAt>0)return Collections.emptySet();Set<Integer> ids=new LinkedHashSet<>();
        if(List.of("item","set","unlock").contains(goal.kind))ids.addAll(goal.itemIds);
        else if("collection".equals(goal.kind))ids.add(Integer.parseInt(goal.referenceKey));
        if(progress!=null)ids.addAll(progress.goalSupplyTargets(goal));
        String guideId="skill".equals(goal.kind)?method(goal.methodId)==null?null:method(goal.methodId).guideId:goalGuide(goal);
        GuideCatalog.Entry entry=guide(guideId);if(entry!=null&&entry.items!=null)for(JsonObject item:entry.items)ids.addAll(GuideItemResolver.resolveAll(item));
        return Collections.unmodifiableSet(ids);
    }
    List<Requirement> requirements(GoalPlan goal, Facts facts)
    {
        if (!goal.progressGoal()) return "skill".equals(goal.kind) ? requirements(method(goal.methodId), facts) : requirements(guide(goal.questGuideId), facts);
        if (progress == null) return Collections.emptyList();
        if ("ca".equals(goal.kind))
        {
            List<Requirement> result = new ArrayList<>();
            for (CombatAchievementCatalog.Requirement source : progress.combatRequirements(Integer.parseInt(goal.referenceKey))) result.add(combatRequirement(source, facts));
            return result;
        }
        if ("music".equals(goal.kind))
        {
            MusicUnlockCatalog.Track track = progress.musicTrack(Integer.parseInt(goal.referenceKey));
            if (track == null || track.quest == null) return Collections.emptyList();
            GuideCatalog.Entry quest = progress.musicQuestGuide(Integer.parseInt(goal.referenceKey)); if (quest == null) return Collections.emptyList();
            List<Requirement> result = new ArrayList<>(requirements(quest, facts));
            result.add(new Requirement(quest.title, facts.quest(quest.title) == null ? 0 : facts.finished(quest) ? 1 : -1, null, 0, quest.id)); return result;
        }
        if (!"diary".equals(goal.kind)) return Collections.emptyList();
        DiaryCatalog.Diary diary = progress.diaryCatalog().find(goal.referenceKey);
        if (diary == null) return Collections.emptyList();
        List<Requirement> result = new ArrayList<>();
        for (DiaryCatalog.Requirement source : diary.requirements) result.add(diaryRequirement(source));
        Map<String, Requirement> unique = new LinkedHashMap<>();
        for (DiaryCatalog.Task task : diary.tasks)
            for (DiaryCatalog.Requirement source : task.requirements) { Requirement converted = diaryRequirement(source); unique.putIfAbsent(converted.label, converted); }
        result.addAll(unique.values()); return result;
    }
    private Requirement combatRequirement(CombatAchievementCatalog.Requirement source, Facts facts)
    {
        Skill skill = "skill".equals(source.kind) ? skill(source.key) : null;
        if (skill != null) return new Requirement(source.value + " " + skill.getName() + (source.boostable ? " · boostable" : ""), facts.level(skill) >= source.value ? 1 : -1, skill, source.value, null);
        if ("quest".equals(source.kind)) { GuideCatalog.Entry quest = questGuide(source.key); QuestState state = facts.quest(source.key); return new Requirement(source.key, state == null ? 0 : state == QuestState.FINISHED || source.started && state == QuestState.IN_PROGRESS ? 1 : -1, null, 0, source.started || quest == null ? null : quest.id); }
        if ("choice".equals(source.kind) && source.children != null) { boolean unknown = false; for (CombatAchievementCatalog.Requirement child : source.children) { Requirement option = combatRequirement(child, facts); if (option.state > 0) return new Requirement(source.label, 1, null, 0, null); unknown |= option.state == 0; } return new Requirement(source.label, unknown ? 0 : -1, null, 0, null); }
        return new Requirement(source.label == null ? source.key : source.label, 0, null, 0, null);
    }
    List<String> benefits(GoalPlan goal, Facts facts)
    {
        if (!"skill".equals(goal.kind)) return benefits(goal);
        List<ActivityXpCatalog.Method> unlocked = new ArrayList<>();
        int end = Math.min(99, Experience.getLevelForXp(goal.targetXp));
        for (ActivityXpCatalog.Method method : activities.methods(goal.skill())) if (method.level > facts.level(goal.skill()) && method.level <= end) unlocked.add(method);
        unlocked.sort(java.util.Comparator.comparingInt((ActivityXpCatalog.Method method) -> method.level).thenComparing(method -> method.name));
        List<String> result = new ArrayList<>();
        for (int index = 0; index < Math.min(12, unlocked.size()); index++) { ActivityXpCatalog.Method method = unlocked.get(index); result.add("Level " + method.level + " · " + method.name); }
        if (unlocked.size() > 12) result.add((unlocked.size() - 12) + " more training options");
        return Collections.unmodifiableList(result);
    }
    List<String> benefits(GoalPlan goal) { return progress == null ? Collections.emptyList() : progress.benefits(goal); }
    String goalGuide(GoalPlan goal) { return goal.progressGoal() && progress != null ? progress.goalGuide(goal) : goal.questGuideId; }
    private Requirement diaryRequirement(DiaryCatalog.Requirement source)
    {
        Skill skill = "skill".equals(source.kind) ? skill(source.key) : null;
        GuideCatalog.Entry quest = "quest".equals(source.kind) ? questGuide(source.key) : null;
        return new Requirement(source.label + (source.boostable ? " · boostable" : ""), progress.requirementState(source), skill,
            skill == null ? 0 : source.value, quest == null || source.started ? null : quest.id);
    }

    List<ActivityXpCatalog.Method> methods(Skill skill, String intensity)
    {
        List<ActivityXpCatalog.Method> result = new ArrayList<>();
        for (ActivityXpCatalog.Method method : activities.methods(skill))
            if (intensity == null || intensity.equals(intensity(method.intensity))) result.add(method);
        result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }
    ActivityXpCatalog.Method method(String id) { return id == null ? null : activities.find(id); }
    GuideCatalog.Entry guide(String id) { return id == null ? null : guides.entry(id); }
    List<GuideCatalog.Entry> quests() { return guides.search("", "quest"); }
    private GuideCatalog.Entry questGuide(String title)
    {
        GuideCatalog.Entry direct = guide("quest:" + title);
        if (direct != null) return direct;
        String key = questKey(title);
        for (GuideCatalog.Entry entry : quests()) if (questKey(entry.title).equals(key)) return entry;
        return null;
    }
    static String questKey(String title)
    {
        String key = QuestOrder.normalize(title);
        // Wiki subquest paths and the RuneLite quest API name the same RFD quests differently.
        return key.startsWith("recipefordisasterfreeing")
            ? "recipefordisaster" + key.substring("recipefordisasterfreeing".length()) : key;
    }

    Set<String> observedQuestKeys(List<GoalPlan> goals)
    {
        Set<String> keys = new LinkedHashSet<>(), seen = new LinkedHashSet<>();
        for (GoalPlan goal : goals)
        {
            if ("diary".equals(goal.kind) && progress != null)
            {
                DiaryCatalog.Diary diary = progress.diaryCatalog().find(goal.referenceKey);
                if (diary != null) { diaryQuestKeys(diary.requirements, keys, seen); for (DiaryCatalog.Task task : diary.tasks) diaryQuestKeys(task.requirements, keys, seen); }
                continue;
            }
            if (progress != null && "ca".equals(goal.kind))
            { for (CombatAchievementCatalog.Requirement requirement : progress.combatRequirements(Integer.parseInt(goal.referenceKey))) combatQuestKeys(requirement, keys, seen); continue; }
            if (progress != null && "music".equals(goal.kind))
            { GuideCatalog.Entry quest = progress.musicQuestGuide(Integer.parseInt(goal.referenceKey)); if (quest != null) { keys.add(questKey(quest.title)); collectQuestKeys(quest, keys, seen, 0); } continue; }
            GuideCatalog.Entry entry;
            if ("quest".equals(goal.kind)) entry = guide(goal.questGuideId);
            else { ActivityXpCatalog.Method method = method(goal.methodId); entry = method == null ? null : guide(method.guideId); }
            collectQuestKeys(entry, keys, seen, 0);
        }
        return keys;
    }
    private void combatQuestKeys(CombatAchievementCatalog.Requirement source, Set<String> keys, Set<String> seen)
    { if ("quest".equals(source.kind)) { keys.add(questKey(source.key)); collectQuestKeys(questGuide(source.key), keys, seen, 0); } if (source.children != null) for (CombatAchievementCatalog.Requirement child : source.children) combatQuestKeys(child, keys, seen); }
    private void diaryQuestKeys(List<DiaryCatalog.Requirement> requirements, Set<String> keys, Set<String> seen)
    {
        if (requirements == null) return;
        for (DiaryCatalog.Requirement requirement : requirements)
        {
            if ("quest".equals(requirement.kind)) { keys.add(questKey(requirement.key)); collectQuestKeys(questGuide(requirement.key), keys, seen, 0); }
            diaryQuestKeys(requirement.children, keys, seen);
        }
    }
    private void collectQuestKeys(GuideCatalog.Entry entry, Set<String> keys, Set<String> seen, int depth)
    {
        if (entry == null || depth > 16 || !seen.add(entry.id)) return;
        if ("quest".equals(entry.category)) keys.add(questKey(entry.title));
        if (entry.requirementDetails != null)
            for (JsonObject item : entry.requirementDetails) collectQuestRequirement(item, keys, seen, depth);
    }
    private void collectQuestRequirement(JsonObject item, Set<String> keys, Set<String> seen, int depth)
    {
        if (item.has("anyOf") && item.get("anyOf").isJsonArray())
            for (JsonElement option : item.getAsJsonArray("anyOf"))
                if (option.isJsonObject()) collectQuestRequirement(option.getAsJsonObject(), keys, seen, depth);
        if ("quest".equals(text(item, "kind")))
        {
            String title = text(item, "key"); keys.add(questKey(title));
            collectQuestKeys(questGuide(title), keys, seen, depth + 1);
        }
    }

    static String intensity(String value)
    {
        if (value == null) return "Normal";
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("afk")) return "AFK";
        if (lower.contains("intens") || lower.contains("sweaty")) return "Intensive";
        return "Normal";
    }
    static Skill skill(String value)
    {
        if (value == null) return null;
        try { return Skill.valueOf("runecrafting".equalsIgnoreCase(value) ? "RUNECRAFT" : value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    static final class Facts
    {
        final Map<Skill, Integer> xp, levels;
        final Map<String, QuestState> quests;
        final int combat;
        Facts(Map<Skill, Integer> xp, Map<Skill, Integer> levels, Map<String, QuestState> quests, int combat)
        {
            Map<Skill, Integer> copyXp = new EnumMap<>(Skill.class), copyLevels = new EnumMap<>(Skill.class);
            copyXp.putAll(xp); copyLevels.putAll(levels);
            this.xp = Collections.unmodifiableMap(copyXp);
            this.levels = Collections.unmodifiableMap(copyLevels);
            this.quests = Collections.unmodifiableMap(new LinkedHashMap<>(quests)); this.combat = combat;
        }
        int xp(Skill skill) { return xp.getOrDefault(skill, 0); }
        int level(Skill skill) { return levels.getOrDefault(skill, 0); }
        QuestState quest(String title) { return quests.get(questKey(title)); }
        boolean finished(GuideCatalog.Entry entry) { return entry != null && quest(entry.title) == QuestState.FINISHED; }
    }

    static final class Requirement
    {
        final String label;
        final int state;
        final Skill skill;
        final int level;
        final String questGuideId;
        Requirement(String label, int state, Skill skill, int level, String questGuideId)
        { this.label = label; this.state = state; this.skill = skill; this.level = level; this.questGuideId = questGuideId; }
        String display() { return (state > 0 ? "✓ " : state < 0 ? "✕ " : "? ") + label; }
    }

    List<Requirement> requirements(GuideCatalog.Entry entry, Facts facts)
    {
        if (entry == null) return Collections.emptyList();
        List<Requirement> result = new ArrayList<>();
        if (entry.requirementDetails != null && !entry.requirementDetails.isEmpty())
            for (JsonObject item : entry.requirementDetails) result.add(requirement(item, facts));
        else if (entry.requirements != null)
            for (String label : entry.requirements) result.add(new Requirement(label, 0, null, 0, null));
        Map<String, Requirement> unique = new LinkedHashMap<>();
        for (Requirement requirement : result) unique.putIfAbsent(requirement.label.toLowerCase(Locale.ROOT), requirement);
        return new ArrayList<>(unique.values());
    }

    List<Requirement> requirements(ActivityXpCatalog.Method method, Facts facts)
    {
        if (method == null) return Collections.emptyList();
        List<Requirement> result = new ArrayList<>();
        if (method.level > 1)
            result.add(new Requirement(method.level + " " + method.skill.getName(),
                facts.level(method.skill) >= method.level ? 1 : -1, method.skill, method.level, null));
        GuideCatalog.Entry entry = guide(method.guideId);
        result.addAll(requirements(entry, facts));
        for (ActivityXpCatalog.Unlock unlock : method.unlocks)
        {
            String label=unlock.label==null?unlock.key:unlock.label;if(label==null)continue;
            Skill skill="skill".equals(unlock.kind)?skill(unlock.key):null;
            GuideCatalog.Entry quest="quest".equals(unlock.kind)?questGuide(unlock.key):null;
            Requirement requirement=new Requirement(label,unlock.state(facts.levels,facts.quests,facts.combat),skill,unlock.value,
                quest==null||unlock.started?null:quest.id);
            result.removeIf(existing->existing.label.equalsIgnoreCase(label));result.add(requirement);
        }
        // Curated activity variants can add textual restrictions beyond their base guide.
        // These remain explicitly unverified rather than becoming guessed client unlocks.
        if (method.requirements != null) for (String label : method.requirements)
            if (result.stream().noneMatch(item -> item.label.equalsIgnoreCase(label)))
                result.add(new Requirement(label, 0, null, 0, null));
        return result;
    }

    private Requirement requirement(JsonObject item, Facts facts)
    {
        if (item == null) return new Requirement("Unspecified unlock", 0, null, 0, null);
        String label = text(item, "label"), kind = text(item, "kind"), key = text(item, "key");
        if (label.isEmpty()) label = key.isEmpty() ? "Unspecified unlock" : key;
        if (item.has("ironmanOnly") && item.get("ironmanOnly").getAsBoolean())
            return new Requirement(label, 0, null, 0, null);
        if (item.has("anyOf") && item.get("anyOf").isJsonArray())
        {
            boolean unknown = false;
            for (JsonElement child : item.getAsJsonArray("anyOf")) if (child.isJsonObject())
            {
                Requirement option = requirement(child.getAsJsonObject(), facts);
                if (option.state > 0) return new Requirement(label, 1, null, 0, null);
                unknown |= option.state == 0;
            }
            // A choice of prerequisite routes never silently queues every alternative.
            return new Requirement(label, unknown ? 0 : -1, null, 0, null);
        }
        if ("skill".equals(kind))
        {
            Skill skill = skill(key); int level = number(item, "value", 1);
            if (skill != null && level >= 1 && level <= 99)
                return new Requirement(label, facts.level(skill) >= level ? 1 : -1, skill, level, null);
        }
        if ("quest".equals(kind))
        {
            QuestState observed = facts.quest(key);
            boolean started = item.has("started") && item.get("started").getAsBoolean();
            int state = observed == null ? 0 : observed == QuestState.FINISHED || (started && observed == QuestState.IN_PROGRESS) ? 1 : -1;
            GuideCatalog.Entry linked = questGuide(key);
            return new Requirement(label, state, null, 0, started || linked == null ? null : linked.id);
        }
        if ("metric".equals(kind) && "Combat".equals(key) && facts.combat > 0)
            return new Requirement(label, facts.combat >= number(item, "value", Integer.MAX_VALUE) ? 1 : -1, null, 0, null);
        return new Requirement(label, 0, null, 0, null);
    }

    /** Missing typed prerequisites, in dependency order. Unknown facts and alternative choices stay manual. */
    List<GoalPlan> unlockGoals(List<Requirement> requirements, Facts facts, List<GoalPlan> existing)
    {
        LinkedHashMap<String, GoalPlan> result = new LinkedHashMap<>();
        collect(requirements, facts, existing, result, new LinkedHashSet<>(), 0);
        return new ArrayList<>(result.values());
    }
    private void collect(List<Requirement> requirements, Facts facts, List<GoalPlan> existing,
        Map<String, GoalPlan> result, Set<String> visiting, int depth)
    {
        if (depth > 16) return;
        for (Requirement requirement : requirements)
        {
            if (requirement.state >= 0) continue;
            if (requirement.skill != null)
            {
                int xp = Experience.getXpForLevel(requirement.level);
                boolean planned = existing.stream().anyMatch(goal -> !goal.archived && goal.skill() == requirement.skill && goal.targetXp >= xp);
                String key = "skill:" + requirement.skill;
                GoalPlan queued = result.get(key);
                if (!planned && (queued == null || queued.targetXp < xp))
                    result.put(key, GoalPlan.skill(requirement.skill, xp, facts.xp(requirement.skill)));
            }
            else if (requirement.questGuideId != null && visiting.add(requirement.questGuideId))
            {
                GuideCatalog.Entry entry = guide(requirement.questGuideId);
                boolean planned = existing.stream().anyMatch(goal -> !goal.archived && requirement.questGuideId.equals(goal.questGuideId));
                if (!planned && entry != null)
                {
                    collect(requirements(entry, facts), facts, existing, result, visiting, depth + 1);
                    result.put(requirement.questGuideId, GoalPlan.quest(requirement.questGuideId));
                }
                visiting.remove(requirement.questGuideId);
            }
        }
    }
    private static String text(JsonObject item, String key)
    { return item.has(key) && item.get(key).isJsonPrimitive() ? item.get(key).getAsString() : ""; }
    private static int number(JsonObject item, String key, int fallback)
    { try { return item.has(key) ? item.get(key).getAsInt() : fallback; } catch (RuntimeException ignored) { return fallback; } }
}
