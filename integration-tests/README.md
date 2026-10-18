# Optional Shortest Path contract tests

The normal `./gradlew test` task tests Runewarden without downloading or building another Plugin Hub plugin. Its identity-only `src/test/java/shortestpath/ShortestPathPlugin.java` fixture provides the expected discovery class name. It does not calculate paths and is excluded from the production JAR.

Two retained suites require the real, separately supplied Shortest Path implementation:

- `HarvestShortestPathContractTest`: three contracts exercise native walking/tablet selection, a declared house plus carried Construction cape to Catherby, and preservation of a manually replaced route during farm cleanup.
- `PohTravelCapabilitiesTest`: seven contracts cover native declarations, observed house facilities, relevant inventory revisions, character isolation and the actual upstream portal enum/serialization.

Run them separately with the recorded unmodified companion JAR:

```sh
./gradlew companionIntegrationTest -PshortestPathJar=/absolute/path/shortest-path-1.21.3.jar
```

The target is Shortest Path 1.21.3, upstream commit `37abff3a3345185fc11fccecae04d8170de25a11`, with RuneLite 1.13.2. The supplied JAR must contain its native resources, including `/collision-map.zip`. Newer revisions require a fresh compatibility assessment.

The `companionIntegration` source set uses the real JAR and production output, without the normal identity fixture. `ShortestPathLifecycle` is a test helper for the upstream protected start/stop methods; it neither replaces nor modifies the native pathfinder. These are synthetic contracts, not logged-in travel or Hub approval evidence. The retired full guide and Quest Helper launcher suites are outside this HUD tree.
