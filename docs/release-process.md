# HUD release procedure

Runewarden HUD 0.10.6 is an experimental candidate on `hud-core`, published by Shai-arr. Its original code is BSD-2-Clause; third-party data, native libraries and optional Java runtime retain their own licenses. Successful local checks are not RuneLite/Jagex approval.

1. Run the plugin checks in `CONTRIBUTING.md` on the final source. Inspect HUD/sidebar rendering and the production JAR. Record results for this HUD tree, without copying the broader prototype's test totals.
2. Check actual live scenarios: account/profile transitions, scene loading, overlay layout/resize, retained activities, module suspension and interoperability. Record what was tested and keep unverified behavior explicit. No relative RAM/FPS claim follows from a synthetic test or catalogue size.
3. Keep the version in `build.gradle`, `runelite-plugin.properties` and `RunewardenVersion.java` equal. Preserve `build=standard`. The production JAR contains only the HUD plugin's Java/resources.
4. For a test download, build `devTestZip`. An external `shortestPathJar` may include the recorded unchanged routing companion. A supplied pinned `runtimeDir` also requires the matching `runtimeSourceDir`; distribute the resulting Java source ZIP alongside the Windows test ZIP. Retain native runtime legal files, dependency notices, hashes and corresponding plugin source. Run `verifyTestPackage` and inspect the actual archive before distribution.
5. Before considering a new HUD submission, verify the current source/bytecode budget with the applicable Hub tooling and record the HUD-specific results. The former broad prototype submission is separate; it does not automatically receive this branch. Publish reviewed source/artifacts only when authorized. Create the future `plugins/runewarden` marker in the Hub fork using these two lines:

   ```text
   repository=https://github.com/Shaiarr/Runewarden.git
   commit=EXACT_PUBLIC_40_CHARACTER_HUD_COMMIT_SHA
   ```

   Use the exact committed and pushed HUD revision, not an earlier prototype SHA or the branch name.

6. If a future HUD submission is authorized after that assessment, follow the current [official Plugin Hub instructions](https://github.com/runelite/plugin-hub) for its fork, marker and review PR. Do not assume the earlier broad submission is still active or approved. Address concrete build/scanner/reviewer findings and preserve third-party notices. A successful build, a clean scanner and human acceptance are separate results.

The development bootstrap lives outside `src/main` and is excluded from the Hub JAR. Native RuneLite is not modified. The standard Hub builder controls its own build configuration; launcher packaging tasks are local testing tools, not part of that submission build.

The earlier full prototype's readiness, CI and approval state must not be represented as acceptance of 0.10.6. Do not publish a stable-release claim until the relevant live and review evidence exists.
