# Runewarden 0.9.6 release candidate

This release completes the Runewarden rename. The Plugin Hub entrypoint is
`com.runewarden.RunewardenPlugin`; production Java and bundled resources live
under `com/runewarden`. Configuration annotations, source tools, data formats,
tests, metadata and package verification use the same naming.

The primary configuration namespace is `runewarden`. Previous development
settings are not automatically copied into it. Keep an existing backup if you
need to migrate settings manually; old backup compatibility is not promised.
Other already-named Runewarden module groups remain independently scoped.

The public GitHub export contains one plugin, its sources, reference data,
licenses, documentation and verification tooling. It contains no native client,
Java runtime, custom launcher or installed companion plugin. Navigation uses a
separately installed/enabled Shortest Path. Quest walkthroughs use external
Quest Helper; the former development-launcher adapter is not included here.

Current plugin-only results are in `plugin-only-evidence.json`; older verification
records and source hashes are historical and do not certify this renamed source.
The native full development harness has a separate automated result. Neither
record proves logged-in behavior, Windows execution, comparative memory use or
Plugin Hub acceptance. The checks in `live-acceptance.json` remain open until
actual evidence is recorded. Source publication and submitting the exact public
commit for Hub review remain separate from review acceptance.

Use `release-process.md` and `github-upload-nl.md` to publish the plugin sources
and prepare the exact commit marker. Do not reuse a commit SHA from an earlier
source tree or package.
