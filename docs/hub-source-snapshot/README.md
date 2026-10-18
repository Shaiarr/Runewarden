# Retained primary-source metadata

This directory retains the exact signed RuneLite 1.13.2 manifest envelope and the official active-install response retrieved on 17 October 2026, plus the independently pinned official `runelite/plugin-hub` marker tree and text-source hashes. Gzip files have deterministic headers. Decompression changes no signed manifest bytes. The official count response and parsed marker metadata also retain their original stored bytes.

The manifest is verified with RSA/SHA-256 against the certificate extracted from the verified native RuneLite 1.13.2 client. Its SHA-256 fingerprint is pinned in both tools. The independently recorded GitHub marker pins are not a cryptographic binding between a README and the manifest's built JAR.

Rebuild from an extracted source archive:

```sh
python3 tools/fetch-hub-audit-sources.py --output-dir /tmp/runewarden-hub-evidence
python3 tools/build-hub-coverage-audit.py --source-dir /tmp/runewarden-hub-evidence
```

Python 3, OpenSSL and network access to the recorded primary text URLs are required. The fetcher downloads only the 151 pinned READMEs and the additional Java text references used for brief/template READMEs, using at most eight workers. It verifies every content hash, reuses matching local files, and does not download or execute plugin JARs. `--metadata-only` reconstructs and verifies the retained metadata without network access; the full report rebuild still requires the pinned text evidence. Source URLs and expected hashes remain reviewable if a hosting service later removes a pinned source.

The compressed metadata contains the complete inventory, including unavailable and unreviewed plugins. The comparison tier includes every available manifest plugin with at least 50,000 active installs in the recorded official response. These inputs preserve a dated snapshot; rerunning the audit does not claim to refresh current Hub popularity.

The offline source-evidence freshness check is:

```sh
python3 tools/build-hub-coverage-audit.py --check-evidence
```

It compares the report against current production/test hashes, the recorded version, complete popularity-tier membership and retained manifest/count hashes. It does not fetch sources or run the Java tests. Regenerate after source changes.
