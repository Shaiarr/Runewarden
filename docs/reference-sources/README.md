# Retained upstream reference sources

These are the exact upstream source files referenced by Runewarden's shipped
travel and POH data. The original `vendor/...` paths remain in the data to
preserve its provenance. In this plugin-only repository those paths resolve
beneath `docs/reference-sources/` for source-line verification.

Only referenced files, their upstream license, and the pinned revision metadata
are retained. They are documentation and test evidence: Gradle does not compile,
package, load, or install them. Runewarden does not bundle either upstream plugin.
Players install optional companion plugins separately through the Plugin Hub.

- Shortest Path 1.21.3: commit
  `37abff3a3345185fc11fccecae04d8170de25a11`.
- Quest Helper 4.18.0: commit
  `5f791937a99a676f23c02aa48c4e4d5b50e7edad`.

The retained copies are byte-identical to these revisions; line numbers in the
data have not been rewritten. Original copyright and license notices remain in
place. `GuideTravelCatalogTest.everyCoordinateMatchesItsExactRetainedPrimarySourceLine`
checks the exact referenced line and coordinates against these files.
