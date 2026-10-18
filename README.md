# Runewarden

![Runewarden](icon.png)

Runewarden is a free, local OSRS activity companion for RuneLite. It connects account goals, preparation, guides, bank focus, travel and run tracking through a configurable HUD.

Published by **Shai-arr**.

**0.9.6 is an experimental source release, targeting RuneLite 1.13.2.** This repository contains the plugin and its development checks. It does not include a custom client, launcher, Java runtime or bundled Quest Helper/Shortest Path plugins. Plugin Hub review and logged-in acceptance remain pending.

## What it does

- Connects training plans, banked XP, loadouts and item sources to account goals and tracked activities.
- Provides supported native minigame objectives and explicitly opened guides, with current-step updates and dismissal controls. Boss practice uses written lessons and manually selected marker packs.
- Tracks farming runs, observed crop/compost states, harvest estimates, gathering nodes, action batches and skill progress.
- Shares run loot, Slayer planning, collection/music/Combat Achievement progress, reward notifications and configurable HUD cards.
- Includes a manual World Hopper with search, filters, favourites, native entry checks and optional measured latency HUD.

Hold **Alt** to move supported HUD cards and **Ctrl** to resize them. Settings are saved per character under the **runewarden** configuration namespace. Earlier test settings are not imported automatically. Keep an exported backup if you want to attempt a manual migration; compatibility with older backups is not guaranteed. Unsupported observations and estimates remain distinct from confirmed account state.

Navigation requires **Shortest Path**, installed and enabled separately. Runewarden supplies supported destinations and account-aware options; the external plugin computes the route. Quest status, requirements and ordering remain available here; use the separately installed **Quest Helper** for walkthroughs. This export does not include the former launcher-only Quest Helper handoff adapter.

## Build and check

Use **JDK 17**, **Python 3** and the included Gradle wrapper. Production bytecode targets **Java 11**. Dependency resolution requires access to RuneLite's Maven repository and Maven Central.

```sh
python tools/validate_bundle.py
python tools/validate_hub_release.py
python -m unittest discover -s tools -p 'test_*.py'
./gradlew clean check jar
python tools/check_hub_jar.py
```

On Windows, use `gradlew.bat` instead of `./gradlew` and run Python checks as `python -X utf8 ...`. The production artifact is `build/libs/runewarden-0.9.6.jar`. Building a JAR does not install it through the live Plugin Hub; normal Hub publication requires review.

## Status and evidence

[Plugin-only verification](docs/plugin-only-evidence.json) records checks for this exported repository. Earlier readiness reports, benchmark records and `automated-evidence-0.9.5.json` describe the original development harness: its 1,620-test result must not be attributed to this smaller export. Historical client-package instructions do not describe artifacts included here.

The retained [capability comparison](docs/hub-utility-audit.md) documents implemented, partial and missing specialist features. Runewarden does not replace every plugin. Long-session stability, live interoperability and Hub acceptance remain unverified; no comparative RAM/FPS advantage is established.

## Contribute or publish

Runewarden is community maintained. Contributions and forks are welcome, with no guaranteed maintenance schedule or support response time. Reproducible bug reports and independent testing are welcome. See [CONTRIBUTING](CONTRIBUTING.md), the [release procedure](docs/release-process.md) and [Nederlandse uploadhandleiding](docs/github-upload-nl.md).

Original code is **BSD-2-Clause**; retain [LICENSE](LICENSE) and the bundled third-party source/data notices. Runewarden is an independent community project and is not endorsed by Jagex or RuneLite.
