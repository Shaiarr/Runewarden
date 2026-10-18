/* SPDX-License-Identifier: GPL-3.0-only
 * Standalone validation tool. Not part of the plugin/launcher source sets.
 * See docs/performance.md for the runtime classpath and measurement limits.
 */
package com.runewarden;

import com.google.gson.Gson;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

public final class RunewardenCatalogProbe
{
    private static final int WARMUP = 5_000, CYCLES = 100_000;
    private static final int SOURCE_GUIDE_LIMIT = 256, SOURCE_LIMIT = 128;
    private static final long CHARACTER_LIMIT = 8_000_000L;
    private static volatile Object retained;
    private static volatile long checksum;

    private static void require(boolean condition, String label)
    { if (!condition) throw new AssertionError(label); }

    /** Approximate Java live heap, not RSS, allocation rate, or a guarantee of GC completion. */
    private static long heap() throws InterruptedException
    {
        for (int i = 0; i < 3; i++)
        { System.gc(); System.runFinalization(); Thread.sleep(50); }
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(Object owner, String name) throws ReflectiveOperationException
    {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true);
        return (Map<K, V>) field.get(owner);
    }

    /** The measured static reference graph; native indexing and account services are excluded. */
    private static final class Catalogs
    {
        final Gson gson;
        final GuideCatalog guides;
        final ActivityXpCatalog activities;
        final SkillingResourceCatalog resources;
        final MobKnowledgeCatalog mobs;
        final ItemKnowledgeCatalog items;
        final SlayerTaskCatalog slayer;
        final SlayerMethodCatalog slayerMethods;
        final SailingXpScenarios sailing;
        final SupplyAwarenessTool supplyReference;
        final Map<Integer,Integer> itemCharges;
        final List<Integer> chargeIds;
        final List<MinigameLessonCatalog.Lesson> minigameLessons;
        final List<SailingXpScenarios.Trial> sailingRanks;
        final List<SailingXpScenarios.Courier> courierTasks;
        final List<SlayerMethodCatalog.Method> dustMethods;
        final MusicUnlockCatalog music;
        final CombatAchievementCatalog achievements;
        final RarityCatalog rarity;
        final DiaryCatalog diaries;
        final DpsReferenceCatalog dps;
        final FarmPatchCatalog farming;
        final CombatSpellCatalog spells;
        final MinigameActivityCatalog minigames;
        final TileMarkerIndex markers;
        final GoalCatalog goals;
        final QuestOrder questOrder;
        final Map<String, List<Integer>> names;
        final List<ActivityXpCatalog.Method> methods, smithing;
        final List<BankedExperienceCalculator.Recipe> recipes;
        final List<GuideCatalog.Entry> allGuides, sourceGuides;
        final List<FarmPatchCatalog.Patch> patches;
        final List<MusicUnlockCatalog.Track> tracks;
        final List<MinigameActivityCatalog.Snapshot> minigameDefinitions;
        final List<DiaryCatalog.Diary> diaryDefinitions;
        final List<CombatAchievementCatalog.Task> achievementDefinitions;
        final List<CombatSpellCatalog.Spell> spellDefinitions;
        final List<TileMarkerIndex.Category> categories;
        final Set<String> supplyIds;
        final int[] targetIds;
        final int rarityItem;

        Catalogs(Gson gson) throws ReflectiveOperationException
        {
            this.gson = gson;
            guides = new GuideCatalog(gson); guides.load();
            questOrder = guides.questOrder();
            activities = new ActivityXpCatalog(gson, guides);
            resources = new SkillingResourceCatalog(gson);
            mobs = new MobKnowledgeCatalog(gson);
            items = new ItemKnowledgeCatalog(gson, guides); items.load();
            slayer = new SlayerTaskCatalog(gson);
            slayerMethods = new SlayerMethodCatalog(gson); dustMethods = slayerMethods.forTask("Dust devils");
            sailing = new SailingXpScenarios(gson); courierTasks = sailing.couriers();
            sailingRanks = new ArrayList<>();
            for (String id : new String[]{"training:tempor-tantrum", "training:advanced-barracuda", "activity:sailing-gwenith-glide"})
                sailingRanks.addAll(sailing.trials(id));
            minigameLessons = MinigameLessonCatalog.all();
            // Constructor-only production reference load: no native Client, service mocks, lifecycle or observations.
            // SupplyAwarenessTool's constructor parses its sourced charge table without touching its service dependencies.
            supplyReference = new SupplyAwarenessTool(null, null, null, null, null, null, null, gson);
            itemCharges = supplyReference.supportedItemCharges(); chargeIds = new ArrayList<>(itemCharges.keySet());
            music = new MusicUnlockCatalog(gson, guides);
            achievements = new CombatAchievementCatalog(gson);
            rarity = new RarityCatalog(gson);
            diaries = new DiaryCatalog(gson);
            dps = new DpsReferenceCatalog(gson);
            farming = new FarmPatchCatalog(gson, resources);
            spells = new CombatSpellCatalog(gson);
            minigames = new MinigameActivityCatalog();
            markers = new TileMarkerIndex(guides);
            goals = new GoalCatalog(guides, activities); // Static resolver; no character observations.
            names = GuideItemResolver.nameIndex();
            methods = activities.methods(null); smithing = activities.methods(Skill.SMITHING);
            recipes = BankedExperienceCalculator.recipes();
            allGuides = guides.search("", ""); sourceGuides = items.sourceGuides("Vorkath");
            patches = farming.patches(); tracks = music.tracks();
            minigameDefinitions = minigames.supportedActivities(); diaryDefinitions = diaries.diaries();
            achievementDefinitions = achievements.tasks(); spellDefinitions = spells.spells();
            categories = markers.categories(); supplyIds = GuideSupplyPlanner.auditedIds();
            targetIds = RunewardenCatalogProbe.<Integer, Object>map(dps, "targets").keySet()
                .stream().mapToInt(Integer::intValue).sorted().toArray();
            rarityItem = rarity.itemId("Dragon warhammer");
            // Force the remaining code-defined reference tables into the measured retained set.
            require(!SkillingOutfits.OUTFITS.isEmpty() && !GuideBankFamilies.DYNAMIC.isEmpty(), "outfit/supply tables");
            require(!NodeTimerRules.TREE_RESPAWNS.isEmpty() && !NodeTimerRules.ROCK_RESPAWNS.isEmpty()
                && !NodeTimerRules.MINING.isEmpty() && !NodeTimerRules.CHOPPING.isEmpty()
                && !NodeTimerRules.PLANTED_TREES.isEmpty(), "node reference tables");
            require(!allGuides.isEmpty() && !methods.isEmpty() && !recipes.isEmpty() && !patches.isEmpty()
                && !tracks.isEmpty() && !spellDefinitions.isEmpty() && targetIds.length > 0 && rarityItem > 0,
                "catalogs contain actual reference data");
            require(!dustMethods.isEmpty() && !sailingRanks.isEmpty() && !courierTasks.isEmpty()
                && !minigameLessons.isEmpty() && !itemCharges.isEmpty(), "expanded reference data");
        }

        long warm(int cycles)
        {
            long sum = 0;
            for (int i = 0; i < cycles; i++)
            {
                guides.load(); items.load();
                require(names == GuideItemResolver.nameIndex(), "item-name map reconstructed");
                require(methods == activities.methods(null) && smithing == activities.methods(Skill.SMITHING), "method lists reconstructed");
                GuideCatalog.Entry guide = allGuides.get(i % allGuides.size());
                ActivityXpCatalog.Method method = methods.get(i % methods.size());
                FarmPatchCatalog.Patch patch = patches.get(i % patches.size());
                DiaryCatalog.Diary diary = diaryDefinitions.get(i % diaryDefinitions.size());
                CombatAchievementCatalog.Task achievement = achievementDefinitions.get(i % achievementDefinitions.size());
                CombatSpellCatalog.Spell spell = spellDefinitions.get(i % spellDefinitions.size());
                require(guide == guides.entry(guide.id) && guide == goals.guide(guide.id), "guide identity");
                require(method == activities.find(method.id) && method == goals.method(method.id), "training method identity");
                require(sourceGuides == items.sourceGuides("Vorkath"), "source-guide cache identity");
                require(patch == farming.patch(patch.id) && farming.seeds("HERB") == farming.seeds("HERB"), "farm reference identity");
                require(diary == diaries.find(diary.id) && diary.tasks.get(0) == diaries.task(diary.tasks.get(0).id), "diary identity");
                require(achievement == achievements.find(achievement.id), "achievement identity");
                require(spell == spells.named(spell.name), "spell identity");
                require(tracks == music.tracks() && minigameDefinitions == minigames.supportedActivities(), "music/minigame list identity");
                require(categories == markers.categories(), "marker category list identity");
                require(questOrder == guides.questOrder(), "quest-order metadata identity");
                require(recipes == BankedExperienceCalculator.recipes(), "recipe list identity");
                MinigameLessonCatalog.Lesson lesson = minigameLessons.get(i % minigameLessons.size());
                SailingXpScenarios.Trial rank = sailingRanks.get(i % sailingRanks.size());
                require(lesson == MinigameLessonCatalog.get(lesson.activity), "minigame lesson identity");
                require(rank == sailing.trials(rank.methodId).get(i % 4), "Sailing trial identity");
                require(courierTasks.get(i % courierTasks.size()) == sailing.couriers().get(i % courierTasks.size()), "courier task identity");
                require(dustMethods.get(0) == slayerMethods.forTask("Dust devils").get(0), "Slayer method identity");
                require(itemCharges == supplyReference.supportedItemCharges(), "charge reference map reconstructed");
                sum += lesson.milestones.get(i % lesson.milestones.size()).instruction.length();
                sum += rank.experience(i % 10, (i & 1) == 0);
                sum += courierTasks.get(i % courierTasks.size()).experience(i % 5);
                sum += itemCharges.get(chargeIds.get(i % chargeIds.size()));
                sum += dps.target(targetIds[i % targetIds.length]).id;
                sum += dps.modes(dps.weapon(-1)).size();
                sum += markers.groups(categories.get(i % categories.size())).size();
                sum += recipes.get(i % recipes.size()).level;
                sum += rarity.forItem(rarityItem).oddsText.length(); // Intentionally allocates a result.
                sum += mobs.describe(8061, "Vorkath").lines.size();
                sum += resources.find(1515).actions().size();
                sum += slayer.task("Gargoyles").name.length();
                sum += questOrder.rank("Cook's Assistant");
                sum += GuideSupplyPlanner.metadata("training:wintertodt") == null ? 0 : 1;
                sum += NodeTimerRules.treeLifetimeTicks("Yew");
                sum += SkillingOutfits.OUTFITS.get(Skill.MINING).pieces.size();
            }
            checksum = sum; return sum;
        }

        Map<String, Object> counts() throws ReflectiveOperationException
        {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("guides", allGuides.size()); values.put("markerPacks", guides.markerPacks().size());
            values.put("trainingMethods", methods.size()); values.put("bankedXpRecipes", recipes.size());
            values.put("resourceItems", resources.size()); values.put("monsterVariants", mobs.variantCount());
            values.put("monsterNativeIds", mobs.npcCount()); values.put("itemIdNames", items.size());
            values.put("resolverNames", names.size()); values.put("slayerTasks", slayer.taskCount());
            values.put("slayerMethodOptions", slayerMethods.size());
            values.put("sailingTrialRankScenarios", sailingRanks.size()); values.put("sailingExactCourierTasks", courierTasks.size());
            values.put("minigameLessons", minigameLessons.size());
            values.put("minigameLessonMilestones", minigameLessons.stream().mapToInt(lesson -> lesson.milestones.size()).sum());
            values.put("itemChargeReferenceIds", itemCharges.size());
            values.put("itemChargeKinds", RunewardenCatalogProbe.<Integer,SupplyAwarenessTool.Charge>map(supplyReference, "charges")
                .values().stream().map(charge -> charge.type).distinct().count());
            values.put("musicFacts", tracks.size()); values.put("combatAchievements", achievementDefinitions.size());
            values.put("rarityItems", rarity.size()); values.put("diaries", diaryDefinitions.size());
            values.put("diaryTasks", diaries.taskCount()); values.put("dpsTargets", dps.targetCount());
            values.put("dpsWeaponIdsIncludingAliases", map(dps, "weapons").size());
            values.put("farmProtectionModels",farming.payments().size());values.put("farmExactPayments",farming.payments().stream().filter(value->value.available).count());
            values.put("farmPatches", patches.size()); values.put("farmCrops", map(farming, "crops").size());
            values.put("farmStateTypes", map(farming, "states").size());
            values.put("combatSpells", spellDefinitions.size()); values.put("minigameContexts", minigameDefinitions.size());
            values.put("markerCategories", categories.size()); values.put("supplyPolicies", supplyIds.size());
            values.put("questOrderRows", questOrder.rowCount()); values.put("questMetadataRows", guides.search("", "quest").size());
            values.put("outfits", SkillingOutfits.OUTFITS.size()); values.put("dynamicSupplyFamilies", GuideBankFamilies.DYNAMIC.size());
            return values;
        }
    }

    private static ItemSourceService.Lookup lookup(int id, String details)
    {
        ItemSourceService.Lookup value = new ItemSourceService.Lookup(); value.itemId = id;
        value.title = "Synthetic item " + id; value.wiki = "https://example.invalid/" + id;
        value.sources = Collections.singletonList(new ItemSourceService.Source("synthetic", "Source " + id, details, value.wiki));
        return value;
    }

    private static void bounds(ItemKnowledgeCatalog items, ItemSourceService.LookupCache cache) throws ReflectiveOperationException
    {
        require(map(items, "sourceGuideCache").size() <= SOURCE_GUIDE_LIMIT, "source-guide cache exceeds bound");
        require(cache.size() <= SOURCE_LIMIT, "item-source cache exceeds entry bound");
        require(cache.characters() <= CHARACTER_LIMIT, "item-source cache exceeds character bound");
    }

    /** Sequential deterministic cache exercise; it does not issue or simulate HTTP requests. */
    private static Map<String, Object> stress(Catalogs catalogs) throws Exception
    {
        ItemSourceService.LookupCache cache = new ItemSourceService.LookupCache(SOURCE_LIMIT, CHARACTER_LIMIT);
        retained = new Object[]{catalogs, cache};
        String payload = "synthetic-data-".repeat(128);
        for (int i = 0; i < 512; i++)
        { catalogs.items.sourceGuides("probe initial " + i); cache.put("initial-" + i, lookup(i, payload + i), Long.MAX_VALUE); }
        require(cache.size() == SOURCE_LIMIT && map(catalogs.items, "sourceGuideCache").size() == SOURCE_GUIDE_LIMIT,
            "initial entry pressure did not exercise eviction");
        long before = heap();
        List<Map<String, Object>> checkpoints = new ArrayList<>();
        long start = System.nanoTime(), gcNanos = 0;
        int maxEntries = 0, maxGuides = 0; long maxCharacters = 0;
        for (int i = 1; i <= CYCLES; i++)
        {
            String key = "cycle-" + i;
            ItemSourceService.Lookup value = lookup(i, payload + i);
            cache.put(key, value, (long) i + 256);
            require(cache.get(key, i) == value, "newest cache result not retained");
            require(cache.get("cycle-" + (i - 256), i) == null, "old cache result survived eviction/expiry");
            if ((i & 15) == 0) catalogs.items.sourceGuides("probe distinct source " + i);
            bounds(catalogs.items, cache);
            maxEntries = Math.max(maxEntries, cache.size()); maxCharacters = Math.max(maxCharacters, cache.characters());
            maxGuides = Math.max(maxGuides, map(catalogs.items, "sourceGuideCache").size());
            if (i == 25_000 || i == 50_000 || i == CYCLES)
            {
                long gcStart = System.nanoTime(); long used = heap(); gcNanos += System.nanoTime() - gcStart;
                Map<String, Object> point = new LinkedHashMap<>(); point.put("cycles", i);
                point.put("usedHeapBytes", used); point.put("sourceGuideEntries", map(catalogs.items, "sourceGuideCache").size());
                point.put("itemSourceEntries", cache.size()); point.put("itemSourceCharacterUnits", cache.characters());
                checkpoints.add(point);
            }
        }
        double elapsed = (System.nanoTime() - start - gcNanos) / 1_000_000.0;
        long after = ((Number) checkpoints.get(checkpoints.size() - 1).get("usedHeapBytes")).longValue();
        // Exercise character pressure separately so the entry cap cannot mask that constraint.
        for (int i = 0; i < 256; i++)
        { cache.put("large-" + i, lookup(i, "x".repeat(70_000) + i), Long.MAX_VALUE); bounds(catalogs.items, cache); }
        int characterPressureEntries = cache.size(); long characterPressureUnits = cache.characters();
        require(characterPressureEntries < SOURCE_LIMIT && characterPressureUnits > 7_000_000, "character eviction not exercised");
        ItemSourceService.Lookup tooLarge = lookup(-1, "x".repeat((int) CHARACTER_LIMIT + 1));
        int countBefore = cache.size(); long charactersBefore = cache.characters();
        cache.put("oversized", tooLarge, Long.MAX_VALUE);
        require(cache.get("oversized", 0) == null && cache.size() == countBefore && cache.characters() == charactersBefore,
            "oversized result retained or disturbed cache accounting");
        ItemSourceService.Lookup expiring = lookup(-2, "short-lived"); cache.put("expiry-check", expiring, 10);
        require(cache.get("expiry-check", 9) == expiring && cache.get("expiry-check", 10) == null, "expiry boundary");
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("cycles", CYCLES); output.put("distinctSourceGuideMissQueries", 512 + CYCLES / 16);
        output.put("elapsedMillisExcludingCheckpointGc", elapsed); output.put("retainedHeapBeforeBytes", before);
        output.put("retainedHeapAfterBytes", after); output.put("retainedHeapDeltaBytes", after - before);
        output.put("checkpoints", checkpoints); output.put("maximumSourceGuideEntries", maxGuides);
        output.put("maximumItemSourceEntriesDuringSmallRows", maxEntries); output.put("maximumSmallRowCharacterUnits", maxCharacters);
        output.put("characterPressureEntriesAfter256LargeRows", characterPressureEntries);
        output.put("characterPressureUnitsAfter256LargeRows", characterPressureUnits);
        output.put("entryAndCharacterBoundsPassed", true); output.put("oversizedAndExpiryChecksPassed", true);
        return output;
    }

    /** Optional newer APIs are invoked only by this external harness, allowing the same class on the pristine baseline. */
    private static Map<String,Object> offlineSources(Catalogs catalogs) throws Exception
    {
        Map<String,Object> output=new LinkedHashMap<>();
        java.lang.reflect.Method load,loaded;
        try
        {load=ItemKnowledgeCatalog.class.getDeclaredMethod("loadBundled",int.class);loaded=ItemKnowledgeCatalog.class.getDeclaredMethod("loadedSourceShards");load.setAccessible(true);loaded.setAccessible(true);}
        catch(NoSuchMethodException baseline)
        {output.put("supported",false);output.put("reason","Baseline has no lazy offline acquisition shards");output.put("measured",false);return output;}
        Map<Integer,Object> index=map(catalogs.items,"acquisitionIndex");
        int initial=((Number)loaded.invoke(catalogs.items)).intValue();require(initial==0,"offline shards eagerly loaded by cold/warm reference workload");
        Map<Integer,Integer> representatives=new java.util.TreeMap<>();
        for(Integer id:new java.util.TreeSet<>(index.keySet()))representatives.putIfAbsent(id%64,id);
        require(representatives.size()==64,"offline acquisition shard coverage");
        long before=heap(),start=System.nanoTime();int max=0,selections=0;long cardVisits=0;
        ItemSourceService.Lookup last=null;int lastId=-1;
        for(int round=0;round<2;round++)for(Integer id:representatives.values())
        {
            last=(ItemSourceService.Lookup)load.invoke(catalogs.items,id);lastId=id;selections++;
            require(last!=null&&last.itemId==id&&!last.sources.isEmpty(),"selected offline item has no source cards");
            cardVisits+=last.sources.size();int count=((Number)loaded.invoke(catalogs.items)).intValue();max=Math.max(max,count);require(count<=4,"offline shard cache exceeds bound");
        }
        require(load.invoke(catalogs.items,lastId)==last,"cached selected item reconstructed");
        double elapsed=(System.nanoTime()-start)/1_000_000.0;long after=heap();
        Map<Integer,Map<Integer,ItemSourceService.Lookup>> retainedBuckets=map(catalogs.items,"acquisitionBuckets");
        int retainedItems=0,retainedCards=0;long characterUnits=0;
        for(Map<Integer,ItemSourceService.Lookup> bucket:retainedBuckets.values())for(Map.Entry<Integer,ItemSourceService.Lookup> entry:bucket.entrySet())
        {retainedItems++;retainedCards+=entry.getValue().sources.size();characterUnits+=ItemSourceService.LookupCache.estimate(entry.getKey().toString(),entry.getValue());}
        output.put("supported",true);output.put("measured",true);output.put("scope","Separate sequential worker-equivalent selected-item shard loads; no Swing, native client, concurrency or HTTP");
        output.put("indexedItemIds",index.size());output.put("initialShardsDuringColdAndWarmCatalogPhases",initial);output.put("distinctShardsVisited",representatives.size());output.put("itemSelections",selections);
        output.put("selectedCardVisits",cardVisits);output.put("elapsedMillis",elapsed);output.put("retainedHeapBeforeBytes",before);output.put("retainedHeapAfterBytes",after);output.put("retainedHeapDeltaBytes",after-before);
        output.put("maximumCachedShards",max);output.put("retainedShards",retainedBuckets.size());output.put("retainedSourceItems",retainedItems);output.put("retainedSourceCards",retainedCards);output.put("estimatedRetainedCharacterUnits",characterUnits);
        output.put("shardBoundsPassed",true);output.put("cachedLookupIdentityPassed",true);return output;
    }

    public static void main(String[] args) throws Exception
    {
        Gson gson = new Gson(); Map<String, Object> output = new LinkedHashMap<>();
        String version=null;
        for(String argument:args)
        {if(argument.startsWith("--version="))version=argument.substring("--version=".length());else throw new IllegalArgumentException("Unknown probe argument: "+argument);}
        require(version!=null&&version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9._-]+)?"),"Pass the measured build version with --version=<version>");
        output.put("version",version);output.put("versionSource","explicit measured-build argument");output.put("measuredOn",java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString());
        output.put("scope", "Standalone local knowledge catalogs and constructor-only supported-charge reference; optional offline shard browsing measured separately; no native Client, login, panels, GPU or network");
        output.put("java", System.getProperty("java.runtime.version")); output.put("vm", System.getProperty("java.vm.name"));
        output.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        output.put("vmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
        output.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        long baseline = heap(), start = System.nanoTime();
        Catalogs catalogs = new Catalogs(gson); retained = catalogs;
        double coldMillis = (System.nanoTime() - start) / 1_000_000.0; long loaded = heap();
        output.put("baselineUsedHeapBytes", baseline); output.put("loadedUsedHeapBytes", loaded);
        output.put("loadedMinusBaselineBytes", loaded - baseline); output.put("coldCatalogLoadMillis", coldMillis);
        output.put("catalogCounts", catalogs.counts());
        catalogs.warm(WARMUP); long warmBefore = heap(); start = System.nanoTime();
        catalogs.warm(CYCLES); double warmMillis = (System.nanoTime() - start) / 1_000_000.0; long warmAfter = heap();
        output.put("warmupCycles", WARMUP); output.put("warmLookupCycles", CYCLES);
        output.put("warmLookupTotalMillis", warmMillis); output.put("warmRetainedHeapBeforeBytes", warmBefore);
        output.put("warmRetainedHeapAfterBytes", warmAfter); output.put("warmRetainedHeapDeltaBytes", warmAfter - warmBefore);
        output.put("sharedReferenceIdentityChecksPassed", true); output.put("checksum", checksum);
        output.put("syntheticCacheCycle", stress(catalogs));
        output.put("offlineAcquisitionShardCycle",offlineSources(catalogs));
        output.put("comparisonAgainstOtherPluginsMeasured", false); output.put("inGamePerformanceMeasured", false);
        output.put("liveProcessRssMeasured", false); output.put("liveFrameLatencyMeasured", false);
        output.put("notes", "Approximate post-System.gc Java heap includes class/Gson/JIT metadata and harness fixtures; retained delta is not a leak proof or allocation-rate metric. Cache character units are not bytes. Cold/warm catalog measurements exclude lazy offline acquisition shard bodies; those have a separate optional selected-item phase. The charge service is constructed only to load its production reference table with null unused native dependencies; no service lifecycle is started. Some query APIs intentionally allocate result wrappers/lists while returning shared stored entries. Timings are synchronous synthetic catalog work, not game ticks, frame latency or FPS.");
        require(retained != null, "retention anchor"); System.out.println(gson.toJson(output));
    }
}
