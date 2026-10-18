# Plugin Hub review revision

Submission: https://github.com/runelite/plugin-hub/pull/18857

The first submitted source commit was
`e35f17404452ef9655c7caed71040748b755b6e0`. Its Hub build passed. The automated
scanner requested RuneLite's `AudioPlayer` instead of direct Java Sound calls,
and the PR received a GPL licensing objection recommending BSD-2.

This revision changes the original project code and original checklist
arrangements to BSD-2-Clause, published by Shai-arr. Third-party code, reference
data, copyright notices and the CC0 reward sounds retain their own terms.
It does not retroactively remove the license rights granted by older commits.

Reward audio now uses the injected native audio service. Pending requests are
coalesced and cancelled on stop; an already submitted short sound finishes
naturally because the public audio service provides no stop handle. The focused
source and production-JAR guards reject direct Java Sound ownership. These
checks are regression coverage for the observed failure, not full Hub-scan parity.

The small signed-roll probability model and manual defence-drain operations are
independently expressed from mathematical/gameplay rules; see
`combat-probability-spec.md`. Their numerical reference tables retain the source
and data notices documented in `licenses/dps-reference-attribution.txt` inside
the plugin resources.

Submit the updated marker through the existing PR. Human review, live
acceptance and Plugin Hub approval remain separate from successful compilation
and automated checks.
