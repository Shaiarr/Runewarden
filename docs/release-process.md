# Release procedure

Runewarden 0.9.6 is an experimental plugin source release. Publishing the source does not require prior Plugin Hub acceptance and does not make the plugin available in the live client's Hub. A local build or mechanical API check is not RuneLite/Jagex approval.

Published by **Shai-arr**. Original Runewarden code is **GPL-3.0-only**; third-party code and datasets retain their own licenses, notices and provenance. Runewarden is community maintained. Contributions and forks are welcome, with no guaranteed maintenance schedule or support response time.

1. Run the checks in `CONTRIBUTING.md` on the exact proposed source. Inspect sidebar rendering and the production JAR. Record this plugin-only export's results in `docs/plugin-only-evidence.json`; do not reuse the historical full-harness test counts.
2. Record actual live checks in `docs/live-acceptance.json`, including version, date, scenario and evidence. Account/profile transitions, full activity journeys, long sessions and interoperability remain unverified until tested. Open checks may accompany an explicitly experimental source release; do not relabel them as passes.
3. Keep `build.gradle`, `runelite-plugin.properties` and `RunewardenVersion.java` versions equal. Preserve `build=standard`. Update the changelog, source notices and dataset fingerprints. This export packages production Java/resources only; external Quest Helper and Shortest Path are not bundled. The entry point is `com.runewarden.RunewardenPlugin` and the configuration namespace is `runewarden`. Earlier test settings are not automatically migrated; preserve exported backups for a possible manual migration without claiming compatibility.
4. Commit and push the reviewed source to a dedicated **public GitHub repository**. From its clean checkout run:

   ```sh
   python tools/prepare_hub_submission.py https://github.com/OWNER/REPOSITORY
   ```

   The tool writes `build/hub-submission/plugins/runewarden` and `build/hub-submission/pull-request.md`. It does not publish or send anything. Inspect the review text and confirm that the marker contains the real public repository URL and the exact **40-character commit SHA** already available there:

   ```text
   repository=https://github.com/OWNER/REPOSITORY.git
   commit=THE_EXACT_40_CHARACTER_COMMIT_SHA
   ```

5. Fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub) and open a pull request adding that marker as **plugins/runewarden**. The plugin source stays in your dedicated repository. Follow the official repository's current submission instructions. Its standard builder controls the Hub build configuration and packages `src/main`; a successful local custom build is not a substitute for that build or review.
6. Address concrete reviewer findings. Source changes require a new public commit and an updated marker. A documented feature precedent supports investigation of that behavior; it does not certify the complete plugin. Record the actual Hub decision when it exists.

The marker identifies one immutable source revision, not a branch or version label. Never invent its SHA or copy one from the old development repository. Keep corresponding source and third-party notices available with any distributed plugin binary.

`release_gate.py` prevents a public 1.x claim while required acceptance gates remain open. Experimental source publication, submitting for review and acceptance into the live Hub are separate steps. Earlier readiness/evidence and client-package documents are retained as historical records; use the current plugin-only evidence for this repository.
