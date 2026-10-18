# Runewarden 0.9.2

This development update addresses the live progress, guide and routing failures reported after 0.9.1. The previous tests did not cover native cached animations and idle events, guide datasets without completion rules, queued reopening after End run, or the absent routing companion.

## Behavior

- The dev client separately compiles and loads Shortest Path 1.21.3 at official Hub marker commit `37abff3a3345185fc11fccecae04d8170de25a11`. Unchanged upstream source/resources and the BSD notice are retained under `vendor/shortest-path`; their hashes are pinned. The main Runewarden Plugin Hub JAR does not contain those classes.
- Guide selection and active-step changes route through the public `path` protocol. Native matching quest NPC/object positions take precedence when available; sourced world coordinates are the fallback. A correlated `getTarget/currentTarget` reply confirms destination acceptance. This is separate from the pathfinder's eventual reachability result. Missing receivers, disabled plugins, blocked travel and timeouts remain visible as such.
- Guide dismissal clears only the matching guide-owned destination. A manually replaced route is preserved. Automatic routes are not inferred for boss/wave standing instructions or unsupported instances.
- Action bars use native classic/Maya durations or existing sourced action cycles. Idle animation events no longer discard a known cast/action cooldown. Client-tick sampling detects short animation loops. Unknown timings remain unknown.
- The selected guide HUD has a labelled step-progress bar. Native minigame tasks move the actual selected authored method instruction; exact linked training guides focus their method after attributed work. Position is distinct from a completed-checklist count.
- Completed quests, confirmed final steps and reached explicit run XP targets close their guide. Subjective long-term milestones still require a completion observation or manual confirmation.
- The global End run button immediately dismisses the guide and activity coaching, invalidates queued opens and routes, and finishes the run with account/run guards. Automatic detection cannot restart the same dismissed activity through a temporary bank/scene gap. Explicit starts and different activities can begin new runs. Completed-run dismissal is retained through restart.
- Every sidebar header shows the running Runewarden version. Launch scripts read generated explicit release classpaths instead of `lib/*`, preventing older leftover JARs from shadowing a new build.

## Verification

`automated-evidence-0.9.2.json` records the completed checks. Regression coverage includes actual upstream Shortest Path code, the collision map, public EventBus messages, asynchronous client-thread initialization, path generation, ownership-safe clearing, actual native Foundry stages, rendered progress pixels, native idle events, and global End run controls.

The release checks compare delivered JAR classes/resources to the clean tested build, verify the companion source pin and its complete resources/license, retain the 48 pinned native dependencies and 316 pinned Windows runtime files, and compare the source archive to the committed tree.

Windows execution and in-game testing on the user's account remain unverified. Actual Plugin Hub review remains pending. The recorded capability audit continues to distinguish partial and missing specialist features.
