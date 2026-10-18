# Official Plugin Hub standard build reference

These exact template files were retrieved from RuneLite's public plugin-hub-tooling
repository at the commit in provenance.json. Their hashes are recorded there.
They are a review reference, not this repository's active Gradle configuration.
The Hub replaces build.gradle/settings.gradle for build=standard and supplies
its selected RuneLite dependency version.

For the recorded local check, only src/main was copied to a temporary project,
these two templates were used unchanged and a separate Gradle initialization
script constrained compileOnly net.runelite:client to 1.13.2. This checks native
standard compilation against that API, not a live Hub CI run or review decision.
The production source and resources match this repository byte for byte.

Primary documentation: https://github.com/runelite/plugin-hub#build-type
