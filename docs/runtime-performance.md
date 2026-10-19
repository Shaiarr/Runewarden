# Runtime work verification — 0.10.6

The baseline is 0.10.5 (`39e11fff698672621fcfd66dc92a63a749a8679d`). These changes remove repeated work while preserving the reduced HUD scope, Java 11 production compatibility and all tracking choices. No dependencies or background worker threads were added.

| Path | Previous work | Current work and guard |
| --- | --- | --- |
| Action HUD | Every client tick constructed a snapshot and temporary icon lookup key | Unchanged payloads reuse the snapshot before icon lookup. Animation sampling and expiry still run each tick; unavailable icons retry once per second. |
| Farming scene | Full loaded-plane object discovery every five game ticks | Discover once, then invalidate on relevant object changes, scene/view/base/plane changes, account changes and loading/hopping events. Crop varbits remain current every settled tick. |
| Effects sidebar | Each visible game tick queued a task and recreated all text components | At most one pending task reads the latest account-scoped snapshot. Existing text components are reused; unchanged text is skipped. |
| Harvest sidebar | Repeated category construction, linear icon/category searches and per-control account checks | Immutable category/icon catalogs, one observation index per changed snapshot and one shared validity check for all 48 controls. Hidden timer refreshes stop; showing a panel refreshes immediately. |

`ActionProgressTest` checks 1,000 idle frames, 100 stable active frames and 500 stable batch frames retain the same snapshot, along with expiry, missing-icon retry and account/context changes. The steady paths allocate no replacement snapshots or icon-key strings. This is an operation-count result, not a measured heap or FPS claim.

`FarmPatchSceneCacheTest` checks 40 static ticks perform zero additional scene tile reads or object-definition lookups while crop varbits update. It also checks relevant replacements, unrelated objects, scene identity/base/plane changes, account changes, empty scenes and loading with no intervening game tick. Distant discovery still covers the full loaded native plane.

`SidebarRefreshPerformanceTest` checks 100 queued effect updates produce one latest-state application, reuse text components and avoid unchanged document writes. It checks all 24 harvest choices on both screens, cross-screen synchronization, stale-account rejection, zero account/config calls across 100 hidden timer callbacks and immediate refresh on showing.

These deterministic checks do not measure logged-in FPS, CPU, retained memory or Windows gameplay. Live profiling and Plugin Hub review remain separate evidence.
