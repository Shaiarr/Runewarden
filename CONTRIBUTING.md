# Contributing to Runewarden HUD

Published by **Shai-arr**. Contributions, forks and independent testing are welcome. This community release has no guaranteed maintenance schedule or support response time. Keep changes focused on the HUD's documented scope and explain the observed behavior, relevant verification and remaining limits.

Use JDK 17 and Python 3. Production classes target Java 11. Preserve BSD-2-Clause and all third-party source/data notices. Keep character observations separate from immutable references, retain profile/generation guards, and keep network/resource loading out of overlay rendering.

## Plugin checks

```sh
python -m pip install -r tools/requirements.txt
python tools/validate_bundle.py
python tools/validate_hub_release.py
python -m unittest discover -s tools -p 'test_*.py'
./gradlew clean check jar
python tools/check_hub_jar.py
```

On Windows use `gradlew.bat` and `python -X utf8`. Inspect actual rendered HUD/sidebar evidence when changing layout. Test logout, profile changes, loading scenes, disable/re-enable and stretched/scaled display behavior before claiming those live cases passed. Use the current HUD release's results; the larger prototype's test counts are historical.

New factual data needs its source revision, license and accurate support boundary. Inspect changes before regenerating fingerprints with `python tools/validate_bundle.py --write`. Unknown or stale native states must not become invented timers, item identities, max hits or completion.

## Local test package

The launcher is a separate source set and never enters the production Hub JAR. It only registers Runewarden and an optional external Shortest Path through the public development bootstrap, then starts native RuneLite. It must not activate foreign plugins, rewrite their configuration or contain a guide engine.

Build a portable test package:

```sh
./gradlew devTestZip
```

To include the recorded unchanged Shortest Path companion:

```sh
./gradlew devTestZip -PshortestPathJar=/path/to/shortest-path-1.21.3.jar
```

To bundle the pinned Windows Temurin runtime, also supply `-PruntimeDir=/path/to/pinned-jre` and `-PruntimeSourceDir=/path/to/matching-runtime-sources`. These inputs are checked against the retained manifests. `devTestZip` then also produces the separate matching Java source ZIP; distribute both together. The runtime's full legal directory and notices remain intact. No runtime or companion is downloaded by the running plugin.

`launcherJar`, `prepareTestPackage` and `verifyTestPackage` are available separately. Verification checks the actual release classpath, file hashes and native resources without starting gameplay. It does not establish Windows execution, logged-in behavior or Hub approval.

## Behavioral boundaries

Read supported native observations and display HUD information. Do not add gameplay input/packet automation, reactive prayer/standing solvers, live arena exports, private reflective access, executable downloads or automatic foreign-plugin configuration. Tick teaching remains a generic metronome, independent of enemies and their attacks. Resource sounds use RuneLite's injected AudioPlayer rather than direct Java Sound ownership.

Report bugs with the RuneLite version, module, reproduction steps, expected and actual result. Remove credentials and personal account/chat details from attached diagnostics. Performance claims need comparable live measurements.

See [the release procedure](docs/release-process.md) for publication and review.
