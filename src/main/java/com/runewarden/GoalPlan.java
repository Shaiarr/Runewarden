/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Deque;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;

/** Typed, account-bound personal goals. The client's observed XP remains authoritative. */
final class GoalPlan
{
    static final String KEY = "personalGoalsV1";
    static final int MAX_GOALS = 100;
    static final int MAX_XP = 200_000_000;
    String id;
    String kind;
    String skill;
    String questGuideId;
    String methodId;
    String intensity = "Normal";
    int targetXp;
    int startXp;
    int milestoneInterval = 5;
    long customXpPerHour;
    long gpBudget;
    double weeklyHours;
    long achievedAt;
    boolean archived;
    String referenceKey;
    String label;
    long targetAmount;
    List<Integer> itemIds = new ArrayList<>();

    GoalPlan copy()
    {
        GoalPlan result = new GoalPlan();
        result.id = id; result.kind = kind; result.skill = skill; result.questGuideId = questGuideId;
        result.methodId = methodId; result.intensity = intensity; result.targetXp = targetXp;
        result.startXp = startXp; result.milestoneInterval = milestoneInterval;
        result.customXpPerHour = customXpPerHour; result.gpBudget = gpBudget; result.weeklyHours = weeklyHours;
        result.achievedAt = achievedAt; result.archived = archived;
        result.referenceKey = referenceKey; result.label = label; result.targetAmount = targetAmount;
        result.itemIds = new ArrayList<>(itemIds == null ? Collections.emptyList() : itemIds);
        return result;
    }

    static GoalPlan skill(Skill skill, int targetXp, int startXp)
    {
        GoalPlan result = new GoalPlan();
        result.id = UUID.randomUUID().toString(); result.kind = "skill";
        result.skill = skill.name(); result.targetXp = targetXp; result.startXp = startXp;
        if (!valid(result)) throw new IllegalArgumentException("Invalid skill goal");
        return result;
    }

    static GoalPlan quest(String guideId)
    {
        GoalPlan result = new GoalPlan();
        result.id = UUID.randomUUID().toString(); result.kind = "quest"; result.questGuideId = guideId;
        if (!valid(result)) throw new IllegalArgumentException("Invalid quest goal");
        return result;
    }

    static GoalPlan progress(String kind, String reference, long target, String label, List<Integer> itemIds)
    {
        GoalPlan result = new GoalPlan(); result.id = UUID.randomUUID().toString(); result.kind = kind;
        result.referenceKey = reference; result.targetAmount = target; result.label = label;
        result.itemIds = new ArrayList<>(itemIds == null ? Collections.emptyList() : itemIds);
        if (!valid(result)) throw new IllegalArgumentException("Invalid progression goal");
        return result;
    }
    boolean progressGoal() { return !"skill".equals(kind) && !"quest".equals(kind); }
    boolean volatileGoal() { return "currency".equals(kind) || "item".equals(kind) || "set".equals(kind); }
    String semanticKey()
    { return kind + ":" + ("skill".equals(kind) ? skill + ":" + targetXp : "quest".equals(kind) ? questGuideId : referenceKey + ":" + targetAmount + ":" + itemIds); }

    Skill skill() { try { return Skill.valueOf(skill); } catch (RuntimeException ignored) { return null; } }
    int remaining(int observedXp) { return Math.max(0, targetXp - Math.max(0, observedXp)); }
    int percent(int observedXp)
    {
        if (observedXp >= targetXp) return 100;
        long span = Math.max(1, (long) targetXp - startXp);
        return (int) Math.max(0, Math.min(100, ((long) observedXp - startXp) * 100 / span));
    }

    /** Every checkpoint is an OSRS XP threshold, with the final exact XP target included. */
    List<Integer> milestones()
    {
        if (!"skill".equals(kind)) return Collections.emptyList();
        List<Integer> result = new ArrayList<>();
        int initial = Experience.getLevelForXp(startXp), end = Experience.getLevelForXp(targetXp);
        for (int level = ((initial / milestoneInterval) + 1) * milestoneInterval;
            level <= Math.min(99, end); level += milestoneInterval)
        {
            int xp = Experience.getXpForLevel(level);
            if (xp > startXp && xp < targetXp) result.add(xp);
        }
        result.add(targetXp);
        return Collections.unmodifiableList(result);
    }

    private static boolean valid(GoalPlan goal)
    {
        if (goal == null || goal.kind == null || goal.id == null || goal.id.isEmpty() || goal.id.length() > 100
            || goal.customXpPerHour < 0 || goal.customXpPerHour > MAX_XP || goal.gpBudget < 0
            || !Double.isFinite(goal.weeklyHours) || goal.weeklyHours < 0 || goal.weeklyHours > 168
            || goal.milestoneInterval < 1 || goal.milestoneInterval > 25) return false;
        if ("quest".equals(goal.kind)) return goal.questGuideId != null && goal.questGuideId.startsWith("quest:")
            && goal.questGuideId.length() <= 250;
        if (List.of("item", "set", "collection", "kc", "diary", "ca", "music", "metric", "currency", "unlock").contains(goal.kind))
        {
            if (goal.referenceKey == null || goal.referenceKey.isEmpty() || goal.referenceKey.length() > 200
                || goal.label == null || goal.label.isEmpty() || goal.label.length() > 200 || goal.label.matches(".*[<>\\r\\n].*")
                || goal.targetAmount < 1 || goal.targetAmount > Integer.MAX_VALUE || goal.itemIds == null || goal.itemIds.size() > 28) return false;
            for (Integer id : goal.itemIds) if (id == null || id <= 0 || id > 1_000_000) return false;
            if (("item".equals(goal.kind) || "set".equals(goal.kind)) && goal.itemIds.isEmpty()) return false;
            if (List.of("collection", "ca", "music").contains(goal.kind))
                try { if (Integer.parseInt(goal.referenceKey) < ("collection".equals(goal.kind) ? 1 : 0)) return false; } catch (NumberFormatException error) { return false; }
            return true;
        }
        return "skill".equals(goal.kind) && goal.skill() != null && goal.skill() != Skill.OVERALL
            && goal.startXp >= 0 && goal.startXp <= MAX_XP && goal.targetXp > 0 && goal.targetXp <= MAX_XP;
    }

    static String share(Gson gson, List<GoalPlan> goals)
    {
        if (goals == null || goals.size() > MAX_GOALS) throw new IllegalArgumentException("At most 100 goals can be shared.");
        Data data = new Data();
        for (GoalPlan item : goals)
        {
            if (!valid(item)) throw new IllegalArgumentException("Invalid goal in plan.");
            GoalPlan clean = item.copy(); clean.id = UUID.randomUUID().toString(); clean.startXp = 0; clean.achievedAt = 0; clean.archived = false;
            data.goals.add(clean);
        }
        return "RWPLAN1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(gson.toJson(data).getBytes(StandardCharsets.UTF_8));
    }
    static List<GoalPlan> importPlan(Gson gson, String text)
    {
        if (text == null || text.length() > 350_000 || !text.trim().startsWith("RWPLAN1:")) throw new IllegalArgumentException("Paste a Runewarden plan code.");
        try
        {
            String json = new String(Base64.getUrlDecoder().decode(text.trim().substring(8)), StandardCharsets.UTF_8);
            Data parsed = gson.fromJson(json, Data.class);
            if (parsed == null || parsed.version != 1 || parsed.goals == null || parsed.goals.isEmpty() || parsed.goals.size() > MAX_GOALS)
                throw new IllegalArgumentException("Unsupported or empty plan.");
            List<GoalPlan> result = new ArrayList<>(); Set<String> keys = new LinkedHashSet<>();
            for (GoalPlan item : parsed.goals)
            {
                if (!valid(item)) throw new IllegalArgumentException("Plan contains an invalid goal.");
                GoalPlan clean = item.copy(); clean.id = UUID.randomUUID().toString(); clean.startXp = 0; clean.achievedAt = 0; clean.archived = false;
                if (keys.add(clean.semanticKey())) result.add(clean);
            }
            return Collections.unmodifiableList(result);
        }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid or unsupported plan code."); }
    }

    static final class Data { int version = 1; List<GoalPlan> goals = new ArrayList<>(); }
    static List<GoalPlan> decode(Gson gson, String json)
    {
        if (json == null || json.length() > 250_000) return new ArrayList<>();
        try
        {
            Data data = gson.fromJson(json, Data.class);
            List<GoalPlan> result = new ArrayList<>();
            Set<String> ids = new LinkedHashSet<>();
            if (data == null || data.version != 1 || data.goals == null) return result;
            for (GoalPlan goal : data.goals)
            {
                if (!valid(goal) || !ids.add(goal.id)) continue;
                if (!"AFK".equals(goal.intensity) && !"Normal".equals(goal.intensity) && !"Intensive".equals(goal.intensity)) goal.intensity = "Normal";
                if (goal.methodId != null && goal.methodId.length() > 250) goal.methodId = null;
                result.add(goal.copy());
                if (result.size() == MAX_GOALS) break;
            }
            return result;
        }
        catch (RuntimeException ignored) { return new ArrayList<>(); }
    }

    /** Every mutation is guarded against the exact profile and generation displayed on screen. */
    static final class Store
    {
        private final Gson gson;
        private final ConfigManager config;
        private final RunewardenDataStore dataStore;
        private final Deque<List<GoalPlan>> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
        private String profile;
        private long generation;
        private List<GoalPlan> goals = new ArrayList<>();
        Store(Gson gson, ConfigManager config) { this(gson, config, null); }
        Store(Gson gson, ConfigManager config, RunewardenDataStore dataStore) { this.gson = gson; this.config = config; this.dataStore = dataStore; }
        synchronized void clear() { profile = null; generation++; goals = new ArrayList<>(); undo.clear(); redo.clear(); }
        synchronized Session bind(String next)
        {
            if (next == null || next.isEmpty()) { clear(); return session(); }
            if (!next.equals(profile))
            {
                profile = next; generation++;
                undo.clear(); redo.clear();
                if (dataStore == null) goals = decode(gson, config.getConfiguration(RunewardenConfig.GROUP, profile, KEY));
                else
                {
                    Data data = dataStore.read(RunewardenConfig.GROUP, profile, KEY, 1, Data.class, Store::validData, Data::new);
                    goals = copy(data.goals);
                }
            }
            return session();
        }
        synchronized Session session() { return new Session(profile, generation); }
        synchronized boolean current(Session expected)
        {
            return expected != null && profile != null && profile.equals(expected.profile)
                && generation == expected.generation && profile.equals(config.getRSProfileKey());
        }
        synchronized List<GoalPlan> goals()
        {
            List<GoalPlan> result = new ArrayList<>();
            for (GoalPlan goal : goals) result.add(goal.copy());
            return Collections.unmodifiableList(result);
        }
        synchronized boolean put(Session expected, GoalPlan updated)
        { return put(expected, updated, true); }
        synchronized boolean putObserved(Session expected, GoalPlan updated)
        { return put(expected, updated, false); }
        private boolean put(Session expected, GoalPlan updated, boolean remember)
        {
            if (!current(expected) || !writable() || !valid(updated)) return false;
            for (int i = 0; i < goals.size(); i++) if (goals.get(i).id.equals(updated.id))
            { if (remember) checkpoint(); goals.set(i, updated.copy()); save(); return true; }
            if (goals.size() >= MAX_GOALS) return false;
            if (remember) checkpoint(); goals.add(updated.copy()); save(); return true;
        }
        synchronized boolean remove(Session expected, String id)
        {
            if (!current(expected) || !writable()) return false;
            boolean present = goals.stream().anyMatch(goal -> goal.id.equals(id)); if (present) checkpoint();
            boolean changed = goals.removeIf(goal -> goal.id.equals(id));
            if (changed) save();
            return changed;
        }
        synchronized boolean move(Session expected, String id, int direction)
        {
            if (!current(expected) || !writable() || (direction != -1 && direction != 1)) return false;
            for (int i = 0; i < goals.size(); i++) if (goals.get(i).id.equals(id))
            {
                int next = i + direction;
                if (next < 0 || next >= goals.size()) return false;
                checkpoint(); Collections.swap(goals, i, next); save(); return true;
            }
            return false;
        }
        synchronized boolean importGoals(Session expected, List<GoalPlan> incoming)
        {
            if (!current(expected) || !writable() || incoming == null) return false;
            Set<String> keys = new LinkedHashSet<>(); for (GoalPlan goal : goals) keys.add(goal.semanticKey());
            List<GoalPlan> additions = new ArrayList<>();
            for (GoalPlan goal : incoming) { if (!valid(goal)) return false; if (keys.add(goal.semanticKey())) additions.add(goal.copy()); }
            if (goals.size() + additions.size() > MAX_GOALS || additions.isEmpty()) return false;
            checkpoint(); goals.addAll(additions); save(); return true;
        }
        synchronized boolean canUndo() { return !undo.isEmpty(); }
        synchronized boolean canRedo() { return !redo.isEmpty(); }
        synchronized boolean writable() { return profile != null && (dataStore == null || !dataStore.blocked(RunewardenConfig.GROUP, profile, KEY)); }
        synchronized boolean undo(Session expected)
        { if (!current(expected) || !writable() || undo.isEmpty()) return false; redo.addLast(copy(goals)); goals = undo.removeLast(); save(); return true; }
        synchronized boolean redo(Session expected)
        { if (!current(expected) || !writable() || redo.isEmpty()) return false; undo.addLast(copy(goals)); goals = redo.removeLast(); save(); return true; }
        private void checkpoint() { undo.addLast(copy(goals)); while (undo.size() > 20) undo.removeFirst(); redo.clear(); }
        private static List<GoalPlan> copy(List<GoalPlan> source) { List<GoalPlan> result = new ArrayList<>(); for (GoalPlan goal : source) result.add(goal.copy()); return result; }
        private static boolean validData(Data data)
        { if (data == null || data.version != 1 || data.goals == null || data.goals.size() > MAX_GOALS) return false; Set<String> ids = new LinkedHashSet<>(); for (GoalPlan goal : data.goals) if (!valid(goal) || !ids.add(goal.id)) return false; return true; }
        private void save()
        {
            Data data = new Data(); data.goals = goals;
            if (dataStore == null) config.setConfiguration(RunewardenConfig.GROUP, profile, KEY, gson.toJson(data));
            else dataStore.write(RunewardenConfig.GROUP, profile, KEY, 1, data);
        }
    }

    static final class Session
    {
        final String profile; final long generation;
        Session(String profile, long generation) { this.profile = profile; this.generation = generation; }
    }
}
