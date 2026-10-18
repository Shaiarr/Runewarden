# Contributing to Runewarden

Published by **Shai-arr**. Runewarden is community maintained. Contributions and forks are welcome, with no guaranteed maintenance schedule or support response time. Independent testing is welcome. Propose a focused change with its purpose, relevant evidence and any remaining limitations.

Use JDK 17 and Python 3. Production classes target Java 11; RuneLite and dependency lockfiles are pinned. This repository builds the plugin, without a launcher, client distribution or bundled companion plugins. Original code is GPL-3.0-only. Preserve third-party notices and data licenses; attribution alone does not permit redistribution of unrelated copyrighted text or sounds.

Run these checks before proposing a change:

```sh
python tools/validate_bundle.py
python tools/validate_hub_release.py
python -m unittest discover -s tools -p 'test_*.py'
./gradlew clean check jar
python tools/check_hub_jar.py
python tools/release_gate.py
```

On Windows, use `gradlew.bat`. The release gate reports pending live/review checks for experimental versions; it does not approve the plugin. Version 0.9.6 uses the `com.runewarden` package and `runewarden` configuration namespace. Earlier test settings are not automatically migrated; do not promise old-backup compatibility without a tested migration. Current export results belong in `docs/plugin-only-evidence.json`. Historical harness reports must retain their original scope and counts.

New datasets need the original URL, pinned revision or commit, license, reproducible builder and accurate support boundary. After inspecting changed data, regenerate fingerprints with `python tools/validate_bundle.py --write`. Unknown account observations and unsupported formulas must stay unknown. Separate account state from immutable references, preserve profile/epoch guards, and keep network requests out of rendering and native game hooks.

Use focused regression tests for concrete failures and meaningful integrated workflows. Inspect generated sidebar PNGs when changing layout. Test login, logout, profile changes, suspension and re-entry before claiming a live workflow passed. Mocked or headless tests remain labelled as such.

Plugins may show guides and supported native observations. Do not add movement/combat input automation, packet actions, reactive future-attack/prayer/standing solvers, live arena exports, private reflective access, automatic foreign-plugin activation or executable downloads. The manual World Hopper alone owns native world-change calls: explicit player intent, fresh account/target checks and native confirmations must precede execution. It must never choose emergency or activity-triggered hops.

Optional route summaries may read fixed public getters on a completed Shortest Path result; do not access private members or retain its graph. The explicit **Use my POH for routing** action may apply its documented whitelist of native house settings with profile ownership checks. Ordinary routing must not rewrite them. Preserve the exact documented Hub precedents and review new behavior on its own merits; a similar plugin's existence is not approval.

Report reproducible bugs with the client version, module, steps, expected and actual result. Remove names, chat, credentials and other personal account details from screenshots and diagnostics. Performance claims need comparable measured sessions; the standalone catalogue probe does not establish in-game performance.

See [the release procedure](docs/release-process.md) for source publication and Hub submission.
