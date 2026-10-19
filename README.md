# Runewarden HUD

![Runewarden](icon.png)

A configurable black, white and gold HUD for Old School RuneScape on RuneLite, published by **Shai-arr**.

**0.10.6 is an experimental HUD release on the `hud-core` branch.** It focuses on current activity information, timers and compact session tracking. It has not been approved for the Plugin Hub; package checks and headless tests do not establish live gameplay or performance.

## HUD features

- Health, prayer, special attack and run energy, plus an optional generic tick teacher with CLICK, dot and audio modes.
- A movable clock/world/worn-equipment dashboard with supported automatic base max hit, and an inventory preview while another game tab is open.
- Actual action icons, progress, quantities and remaining time; supported buffs and cooldowns.
- Supported gathering-node lifetime/respawn timers, outfit reminders and resource hover information.
- Observed farming growth, compost and ready timers, harvest reminders and compact herb/tree/fruit-tree/hardwood/allotment/hop/bush/seaweed/birdhouse run stops with locations and quantities. Floating patch and individual birdhouse cards point to their actual scene objects.
- Independent farming reminders for ordinary trees, fruit trees, hardwood, spirit, redwood, celastrus, calquat, crystal trees and Hespori, with individual timer selections. Mixed native tabs do not supply their combined countdowns.
- Varlamore distraction elapsed/remaining timers, next-cycle countdowns, a floating accessible-house card pointing to the door, and optional automatic Shortest Path routing.
- Session loot with item quantities, native value estimates, sources, duration, rates, pause and reset.
- A Slayer sidebar with native task counts, assigned location, selected target locations, equipment notes, points balance and observed completion rewards. Its optional floating card appears beside matching targets; restricted assignments also require a recognized area match.
- Custom routines and supported native daily-claim reminders.

HUD cards use shared style controls, **Alt-drag** movement and **Ctrl-drag** edge/corner resizing where supported. Scene-anchored cards retain their scene positions. Patch cards, birdhouse cards, observed node timers and Varlamore door labels can appear throughout the loaded scene when their native anchors are on screen. Account observations and preferences remain character-bound; unknown states and estimates stay distinct from confirmed facts.

Farm and Varlamore house routing use **Shortest Path**, installed and enabled separately in a normal RuneLite setup. The test package can include the unchanged optional companion. The plugin displays routes and never performs walking, teleporting or other gameplay inputs.

The broader prototype's quest/minigame/boss guides, Slayer and training planners, DPS and banked-XP calculators, world hopper, reward system and loot beams are outside this release.

## Run the test download

Extract the full Windows x64 test ZIP into a new folder, open **Runewarden** and double-click **Start-Runewarden.bat**. Keep `lib`, argument files and the bundled `runtime` together. **Verify-Runewarden.bat** checks hashes and required package resources.

The package uses unmodified native RuneLite and the pinned Eclipse Temurin 17 runtime. The separate launcher registers the test plugins through RuneLite's public development API; it does not enable or configure foreign plugins. Choose the wanted plugins in RuneLite's normal settings. Jagex accounts use RuneLite's [development-client account setup](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

The portable ZIP requires an installed Java 17. The Windows runtime's matching source collection and complete legal notices accompany its distribution.

## Build

Use **JDK 17**, **Python 3** and the Gradle wrapper. Production bytecode targets **Java 11**.

```sh
python -m pip install -r tools/requirements.txt
python tools/validate_bundle.py
python tools/validate_hub_release.py
python -m unittest discover -s tools -p 'test_*.py'
./gradlew clean check jar
python tools/check_hub_jar.py
```

The Hub artifact is `build/libs/runewarden-0.10.6.jar`. Launcher/client/runtime classes are excluded from it. On Windows use `gradlew.bat` and UTF-8 Python mode (`python -X utf8 ...`). See [CONTRIBUTING](CONTRIBUTING.md) for optional test-package builds and [the release procedure](docs/release-process.md) for review.

Original code is **BSD-2-Clause**; preserve [LICENSE](LICENSE), [NOTICE](NOTICE), [data attributions](DATA-SOURCES.md) and separate third-party terms. A future HUD submission will need its own size/budget assessment and review; the earlier broad submission does not cover this branch. Contributions and independent testing are welcome, without a guaranteed maintenance schedule or support response time. Runewarden is not endorsed by Jagex or RuneLite. Earlier prototype evidence does not certify this reduced HUD release.
