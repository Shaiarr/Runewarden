# Optional Shortest Path contract tests

The normal `./gradlew test` task tests Runewarden without downloading, building,
or redistributing another Plugin Hub plugin. Its identity-only
`src/test/java/shortestpath/ShortestPathPlugin.java` fixture lets the public
plugin-discovery and message-ownership tests use the expected class name. That
fixture does not calculate paths and is never packaged in Runewarden's JAR.

This directory preserves the two suites that need the real upstream Shortest
Path implementation:

- `GuideShortestPathContractTest`: four tests use the real EventBus, upstream
  collision map, pathfinder, transport selection, overlays, lifecycle, and
  route cleanup.
- `PohTravelCapabilitiesTest`: seven tests include an exact contract check
  against the upstream portal enum and serialization format.

Run these separately with an externally supplied, unmodified compatible JAR:

```sh
./gradlew companionIntegrationTest -PshortestPathJar=/absolute/path/shortest-path-1.21.3.jar
```

The preserved contract was validated against Shortest Path 1.21.3, upstream
commit `37abff3a3345185fc11fccecae04d8170de25a11`, with RuneLite 1.13.2.
Use that revision for reproducing its results; newer upstream revisions may
require the contract tests to be updated. The supplied JAR must include its
resources, including `/collision-map.zip`.

The `companionIntegration` source set compiles and runs with the real JAR and
production output, without the normal identity fixture. The route suite also asserts
that `shortestpath.ShortestPathPlugin` was loaded from a JAR, so the normal
identity-only fixture cannot silently stand in for the upstream implementation.
`ShortestPathLifecycle` is a test helper that accesses the upstream protected
startup and shutdown methods; it does not replace the upstream plugin.

Two dev-launcher-only suites are intentionally absent from this plugin-only
repository: `RoutingCompanionStartupTest` and `QuestHelperLauncherBridgeTest`.
Their subjects belonged to the separate local dev launcher and are not part of
the Runewarden plugin being submitted to the Plugin Hub. Their six tests are
not counted as plugin-only test coverage.
