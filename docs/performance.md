# Performance and shared data

Runewarden shares knowledge catalogs between its modules. This reduces repeated
parsing and repeated work within this plugin. It has **not** been measured against
a collection of separate RuneLite plugins, and no relative RAM, CPU or frame-rate
claim is made.

## Shared catalogs

`GuideCatalog`, `ActivityXpCatalog`, `SkillingResourceCatalog`,
`MobKnowledgeCatalog`, `ItemKnowledgeCatalog`, `SlayerTaskCatalog`,
`MusicUnlockCatalog`, `CombatAchievementCatalog`, `RarityCatalog`, `DiaryCatalog`,
`DpsReferenceCatalog`, `FarmPatchCatalog`, `CombatSpellCatalog`, `GoalCatalog`,
`SailingXpScenarios`, `SlayerMethodCatalog` and `MinigameActivityCatalog` are
injected singletons. `MinigameLessonCatalog` retains one static lesson graph. Consumers retain references to the same catalog objects rather than
building a private copy for each sidebar or overlay. Production deserialisation
reuses RuneLite's injected Gson where an object mapper is needed. Static recipe
and item-name tables use JsonParser without creating additional Gson instances.

| Data | Reuse |
| --- | --- |
| Guides and marker packs | One loaded GuideCatalog; individual guide and pack lookups return the stored objects. |
| Guide and farm arrivals | One immutable guide-arrival catalogue indexes 94 sourced points for 112 guide IDs. Farming uses a separate fixed table of 60 native farm-area marker sites, plus observed anchors and user pins. These facts do not establish an arrival point for every guide or exact plot. |
| Item names | One immutable `GuideItemResolver.nameIndex()` map, reused by item lookup and guide supply resolution. The item database builds its own lightweight item-ID/name index from that map, not another JSON parse. |
| Training methods | One ActivityXpCatalog; all-method and per-skill lists are sorted once and reused by the calculator and Goals. |
| Resource uses and XP | One SkillingResourceCatalog, shared by hover cards and the item database. |
| Monster knowledge | One MobKnowledgeCatalog with precomputed ID/name cards, used by hover details, combat information and Slayer. |
| Item-to-guide links | Built once by ItemKnowledgeCatalog. Source-name links use pre-normalised guide titles and a 256-entry access-order cache. |
| Slayer task rules | One local task/gear-reference catalog; native task identity is refreshed when the task, area or boss key changes. |
| Music unlocks | One 853-track factual catalog plus a native metadata index retained across profiles; live unlock snapshots are separate account observations. |
| Combat Achievements and rarity | Separate shared immutable references for the 655 achievement tasks and sourced rarity metrics; character observations and display preferences do not duplicate their datasets. |
| Diaries, combat and farming | Shared diary/task, exact-ID combat, spell and patch/state reference tables; live completion, equipment and patch observations remain separate. |
| Minigame and progression references | One set of activity definitions and twenty static ordered lessons with 122 learning milestones, a marker index referring to existing packs, shared quest metadata and order, supply policies and banked-XP recipes. |
| Finite Sailing rewards and Slayer methods | One parsed Sailing scenario graph with twelve rank rewards and 438 exact courier tasks; one 32-option Slayer-method catalog. Query lists may be fresh snapshots while their factual entry objects stay shared. |
| ID-encoded supplies | One supported-charge map from the pinned 647-item RuneLite reference, reused by supply/departure checks. Account amounts and thresholds remain separate from this reference. |

Static knowledge is account independent. Account observations and local saved
plans are kept separately, with profile/epoch checks on delayed callbacks so that
a callback for an old character cannot update the current character's interface.

## Work bounds

| Area | Current bound or schedule |
| --- | --- |
| Item-name search | Debounced by 180 ms, performed on the shared executor; 20 search results are rendered per page. Older search results are discarded using a generation check. |
| Native item definitions | At most 96 definitions per game tick, and only while the item database has been opened and is visible. There is no full-definition scan on login. Explicitly opening an item can read that one definition. |
| Item detail rows | Five source cards, four training-action rows, four requirement entries and eight related-guide rows per page. |
| Offline acquisition sources | The 335,552-byte index holds 4,538 identities/coverage entries. Source bodies are worker-loaded from 64 shards; at most four shards remain in the access-order cache. Selection and lifecycle epochs suppress stale deliveries. |
| Live item-source requests | Only selected items with explicit, default-off Wiki consent request sources; at most one request is in flight and eight distinct requests may be pending. Requests use the shared HTTP client and executor, with backoff. |
| Source lookup cache | At most 128 cached lookup entries and 8,000,000 estimated character units (stored strings plus fixed per-entry/per-row overhead), evicted in access order. Oversized results are displayed without caching, so source rows are not truncated to fit. Successful entries expire after six hours; error entries have a shorter expiry. The response reader rejects more than 4,000,000 characters. Character accounting is not a byte-exact heap measurement. |
| Tile marker browser | Twelve packs per category page, with category/back navigation. |
| Tile marker overlay | Only the selected pack/subset is prepared. Static colours, labels and world points are cached; scene/instance coordinate resolution is cached until selection or scene inputs change. Camera projection still runs while drawing. |
| Banked XP and activity calculator | Account snapshots continue to update, but hidden views do not reconstruct Swing rows. Showing a view renders its latest snapshot. Visible refresh requests are coalesced. |
| Run loot | Item detail storage is limited to 2,048 distinct IDs; additional items retain aggregate quantity/value. New source names are grouped into Other sources after the source threshold. Live pages show twelve item rows and eight source rows. |
| Run loot heartbeat | Immutable sorted loot/source data and saved history are reused while only elapsed time changes. The one-second display refresh skips hidden panels and rebuilds rows only after a data/page/session revision. |
| Earned-loot history | At most 50 saved loot summaries, five displayed per page, with bounded saved JSON and source fields. |
| Unified activity journal | At most 100 saved run records, additionally constrained by a conservative 900,000-character history estimate; six records per history page. The atomic current/history snapshot has bounded per-run outcomes, milestones and sources. Checkpoints occur every fifty game ticks and on lifecycle boundaries. |
| AFK/action supervision | At most 32 recent XP drops; selected-recipe item/XP evidence has a three-tick correlation window. Stable attention causes alert once per episode after the configured delay; bank, travel, pause and offline states suppress ordinary idle alerts. |
| Action progress icons | At most 64 cached native image entries, populated from client events. Unavailable images retry at most once per second; rendering uses the captured image rather than loading definitions. |
| Personal goals | At most 100 stored goals, eight cards per page and milestone components created only when expanded. Hidden goal panels skip reconstruction; observations run once every five game ticks after login warmup. Quest reads are limited to the dependency keys needed by saved goals. |
| Encounter practice | Ten guide rows per page, a 180 ms search debounce and a 500 ms timer that updates the clock label. Saved journals are limited to 1,000,000 characters and sanitised to 192 recognised encounters, twenty recent attempts per encounter and 400-character notes. |
| Slayer | The task identity and matching monster list are retained until their identity key changes; gear checks inspect the small current equipment/inventory containers. |
| Music native metadata | At most 32 DB rows per tick with a 1.5 ms soft budget, at most five attempts per loading row and a 4,096-row index cap. Initial indexing continues while hidden so unlock notifications work without opening the module; completed metadata is reused. |
| Music live state | Twenty-seven integer varp comparisons per tick after login warmup. State maps rebuild only when banks or metadata change. Quest/skill/carried-item route checks run once per ten ticks while visible; hidden views skip those reads and Swing reconstruction. Search is debounced 180 ms, lists show eight tracks per page and nearby suggestions stop at eight. |
| Native Slayer planning | At most eight rows per visible game tick, with a 1.5 ms outer soft budget; master task-row lists are capped at 400. Missing rows are retried after fifty indexing ticks. |
| Clue access metadata | At most 24 pending native DB rows per game tick, with a 2,000-row pending cap and at most 32 requirement IDs per indexed clue. |
| Minigame scene observations | At most 512 relevant object identities; scene clear discards the observed set. |
| Ground loot | At most 4,096 observed ground-item identities and 160 retained display rows, with up to 128 manual item targets. Scene/profile changes clear observations. |
| Death records | At most twenty saved records and 64 item entries per captured container/retrieval observation. |
| Ownership and account facts | Ownership observations reject containers exceeding 2,500 slots and saved JSON over 600,000 characters. Account progress keeps at most 3,000 collection entries and 250 kill-count references. |
| In-client module timing | A fixed 1,200-duration ring per registered module; no unbounded timing history. |

The regular game tick and overlay hooks still run where they are needed for
current character observations, timers and hovered-target information. Hidden
sidebar suppression does not turn off tracking that must remain current. Native
API reads remain on the client thread; searchable indexes, source HTTP and source
parsing use background work. UI changes are delivered on Swing's event thread.

## Catalog probe

`tools/RunewardenCatalogProbe.java` is a standalone tool outside the production
source sets. It loads the selected static knowledge graph without a native
`Client`: non-quest guides, quest metadata/order, activity XP, banked-XP recipes, item names,
item knowledge/sources, skilling resources, monster knowledge, Slayer references,
music facts, Combat Achievements, rarity, diaries, DPS targets/weapons, combat
spells, farming patches/states/crops, minigame definitions, the marker browsing
index and the static goal/supply resolvers. The graph also includes finite
Sailing rank/courier rewards, ordered minigame lessons and Slayer-method options.
Outfit and node reference tables are initialized. The production
`SupplyAwarenessTool` constructor is called only to parse its supported-charge
reference, with its unused native/service dependencies null. No service lifecycle,
client method, account observation or mock client is used for that constructor-only
reference load. Its charge entry and immutable supported map are retained exactly
as in production. The quest branch/stage/hint graph and walkthrough payload were
removed in 0.9.3; the current probe retains only quest metadata and ordering.
The current item knowledge initialization loads the acquisition index and the existing detailed
pinned pages; it deliberately leaves the 64 lazy acquisition shard bodies unread.
The tool keeps its measured objects and cache under a strong
retention anchor.

The measurements below are historical 0.8.0 results from the earlier probe and
graph. They do not measure the metadata-only 0.9.3 graph or establish the memory
cost of its separately loaded Quest Helper companion.

After 5,000 warmup cycles, 100,000 measured warm cycles exercise repeated load
calls, varied guide/method/diary/task/patch/target/spell lookups, rarity results,
resource/mob/Slayer lookups, finite Sailing rewards, minigame lesson milestones,
charge-ID lookups and stored-reference identity assertions. Rarity results and
some catalog query wrappers/result lists intentionally allocate; assertions check
the stored entry objects and only those containers designed to be shared. This is a mixed synchronous workload, not 100,000
individual lookups or a measurement of one game tick.

A separate deterministic 100,000-cycle cache exercise inserts new synthetic item
sources, reads current entries, verifies old entries are absent and adds a
distinct source-guide miss every sixteen cycles. It checks both cache bounds on
each cycle, and requests GC at 25,000, 50,000 and 100,000 cycles. A following
256-insert large-row phase exercises the character limit independently of the
entry limit. Oversized-result rejection and the exact expiry boundary are also
asserted. These phases call the real local cache implementations without issuing
HTTP requests. Synthetic fixture strings and harness bookkeeping contribute to
measured heap and elapsed time.

### Reproduce

Use JDK 17 and the pinned project's resolved dependencies. From the project root,
this temporary Gradle init script prints the same main-output/launcher-runtime
classpath used by the developer client. It adds no production task or code:

```sh
cat > /tmp/runewarden-probe.init.gradle <<'GRADLE'
allprojects {
    afterEvaluate {
        tasks.register('runewardenProbeClasspath') {
            dependsOn tasks.named('classes')
            doLast { println sourceSets.launcher.runtimeClasspath.asPath }
        }
    }
}
GRADLE
RUNEWARDEN_PROBE_CP=$(./gradlew -q -I /tmp/runewarden-probe.init.gradle runewardenProbeClasspath)
mkdir -p build/catalog-probe
javac -cp "$RUNEWARDEN_PROBE_CP" -d build/catalog-probe tools/RunewardenCatalogProbe.java
java -Xms32m -Xmx256m -Djava.awt.headless=true \
  -cp "build/catalog-probe:$RUNEWARDEN_PROBE_CP" \
  com.runewarden.RunewardenCatalogProbe --version=0.9.3 > build/catalog-probe/result.json
```

Pass the actual measured build version with `--version=<version>`; it is required
and is recorded as an explicit build argument, rather than inferred from the tool
source. The example runs the current graph; reproducing the historical results
requires the probe source and graph from the measured release. To compare releases, compile the same chosen tool once against the pristine
baseline main classes, then run it with each build's main classes/resources and
the same pinned dependency JARs. The optional lazy-source APIs use reflection only
in this standalone external harness, allowing the same tool class to run on
0.7.3, where those APIs do not exist. No reflection or JVM heap API is added to
the plugin.

The process exits unsuccessfully if a sharing or cache-bound assertion fails.
The JSON contains the runtime arguments, catalog counts, exact heap readings,
phase timings, cache checkpoints and assertion results. It is deliberately kept
outside the plugin and launcher JARs. On Windows, use the platform classpath
separator and equivalent shell commands.

### Historical local comparison: 0.7.3 and 0.8.0

Two standalone processes on **17 October 2026** used the same compiled harness,
JDK and RuneLite **1.13.2** dependency JARs: one used the pristine **0.7.3** main
classes/resources, the other **0.8.0** main classes/resources. Both
passed all shared-reference and cache assertions. The exact 0.8.0 result and
embedded baseline are in `catalog-probe-result.json`.

Both ran OpenJDK 64-Bit Server VM **17.0.20+8-1-24.04-Ubuntu**, Linux amd64,
with `-Xms32m -Xmx256m -Djava.awt.headless=true` and a maximum heap of
268,435,456 bytes. The table reports one final run per release. The final 0.8.0 process ran
concurrently with the full Gradle test suite. Host scheduling,
JIT, GC and other concurrent work are uncontrolled; these are observations, not
statistically established timing regressions or speed claims. The retained graph
also contains more content in 0.8.0, so this is not equal-data microbenchmarking.

| Observation | Pristine 0.7.3 | Measured 0.8.0 |
| --- | ---: | ---: |
| Approximate baseline used Java heap | 3,238,464 B | 3,259,800 B |
| Approximate used heap after retaining the measured graph | 44,793,360 B | 54,563,328 B |
| Catalog load delta, including class/Gson metadata and harness references | 41,554,896 B (39.63 MiB) | 51,303,528 B (48.93 MiB) |
| Cold catalog initialization | 2,526.83 ms | 16,168.27 ms |
| 100,000 mixed warm cycles after 5,000 warmup cycles | 3,402.88 ms | 7,596.64 ms |
| Warm-cycle post-GC retained-heap delta | +12,688 B | +11,688 B |
| 100,000 synthetic cache cycles, excluding checkpoint GC | 269.69 ms | 398.17 ms |
| Synthetic-cycle post-GC retained-heap delta | +3,632 B | +3,632 B |
| Maximum source-guide / small-row item-source cache entries | 256 / 128 | 256 / 128 |
| Large-row cache after 256 inserts | 113 entries; 7,939,719 character units | 113 entries; 7,939,719 character units |
| Entry/character bounds, oversized rejection and expiry boundary | Passed | Passed |

| 0.8.0 synthetic cache checkpoint | Approximate used Java heap | Source-guide entries | Item-source entries | Estimated source character units |
| --- | ---: | ---: | ---: | ---: |
| 25,000 cycles | 55,122,816 B | 256 | 128 | 280,960 |
| 50,000 cycles | 55,123,304 B | 256 | 128 | 280,960 |
| 100,000 cycles | 55,123,736 B | 256 | 128 | 280,966 |

### Historical offline acquisition shard exercise

The 0.8.0 cold and warm catalog phases loaded **zero acquisition shards**. The
4,538-item index is counted in the catalog graph; the additional source bodies
are not included in those cold/warm heap or timing measurements.

After the synthetic-cache phases, a separate sequential exercise made
**128 selected-item loads across all 64 shards**, using one representative
item per shard twice. It called the real worker-side source-loading method, checked
every returned item and source list, asserted the four-shard cap after each selection,
and checked that repeated access to the final cached item returned the same object.
The baseline reports this phase as unsupported, rather than pretending to measure it.

| 0.8.0 offline-shard observation | Result |
| --- | ---: |
| Selected-item loads / distinct shard files visited | 128 / 64 |
| Total selected-card visits | 2,638 |
| Synchronous selection workload | 279.07 ms |
| Maximum / final retained shards | 4 / 4 |
| Final retained source items / cards | 281 / 1,228 |
| Final estimated retained source character units | 283,618 |
| Additional approximate post-GC heap across this phase | +382,192 B |
| Shard bounds and cached lookup identity | Passed |

This is worker-equivalent synchronous parsing and cache access. Swing dispatch,
UI selection timing, concurrent selection/revocation, native indexing and HTTP
are not exercised by this phase. Its heap delta includes lazy Gson/reflection
metadata and harness effects, and its starting heap retains the earlier large-row
synthetic cache. It is not a byte-exact attribution to acquisition cards.

### Historical retained reference counts

| Reference | Entries in 0.8.0 |
| --- | ---: |
| Guides / marker packs | 783 / 1,008 |
| Training methods / banked-XP recipes / skilling-resource items | 377 / 210 / 292 |
| Monster variants / mapped native NPC IDs | 2,860 / 2,817 |
| Item-ID/name index / shared resolver names | 16,885 / 12,218 |
| Slayer task references / authored method options | 152 / 32 |
| Music facts / Combat Achievements / rarity items | 853 / 655 / 1,717 |
| Diaries / diary tasks | 48 / 492 |
| Exact-ID DPS targets / weapon IDs including aliases / spells | 2,795 / 1,569 / 50 |
| Farming patches / crops / state types | 107 / 95 / 23 |
| Crop protection models / exact gardener payments | 49 / 33 |
| Minigame native contexts / ordered lessons / learning milestones | 20 / 20 / 122 |
| Sailing rank reward scenarios / exact courier tasks | 12 / 438 |
| Supported charge-reference IDs / charge categories | 647 / 11 |
| Available marker categories / supply policies | 6 / 783 |
| Quest-order rows / quest-stage rules / outfits / dynamic supply families | 238 / 191 / 11 / 30 |

The baseline had 773 guides, 347 training methods and twelve Slayer method options.
A count is not a claim that every entry has equally deep interactive coaching.

### Scope and remaining live validation

This probe does **not** create a native RuneLite client or log in. Native
music/Slayer/clue DB indexing, clue/STASH adapters, owned-item/account snapshots,
game scenes, active timers, running activity/loot/AFK journals, Swing panels,
overlays, notifications, sound decoding and HTTP requests are outside the measured
set. The inert charge constructor does not exercise live supply scanning, thresholds
or alert handling. Full client process RSS and live frame latency were not measured. The synthetic cache test validates local
entry/character accounting and sequential expiry/eviction; it does not exercise
request concurrency, HTTP response parsing or elapsed real-world sessions.

Heap readings are approximate used Java heap after requested `System.gc`, not a
client memory budget, total process RSS or allocation rate. Values include loaded
classes, Gson metadata, JIT/runtime effects and harness fixtures; GC completion
and exact retained ownership are not guaranteed. Cache character units are an
accounting estimate, not bytes. Small positive or negative post-GC deltas do not
prove a leak or zero allocation. These single-run timings include assertion and
harness work and are not a statistical microbenchmark, FPS result, Windows
measurement or comparison with other plugins.

For a live assessment, **Modules & diagnostics** now records actual wrapped
module tick durations with `System.nanoTime`, a bounded 1,200-sample window, p95
for that window and a maximum for the plugin session. The in-client support report
does not collect JVM heap measurements. The counters exclude overlay rendering,
Swing work, other event hooks and background HTTP work. The standalone
catalog probe above is separate from the plugin's production code. See
[recovery-and-modules.md](recovery-and-modules.md).

A logged-in profile is still needed to measure native indexing, crowded-scene
frame time, sidebar interactions, long sessions and the enabled overlay
combination. No live-game or relative RAM/CPU/FPS result is claimed here.
