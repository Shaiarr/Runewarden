# Runewarden 0.8.1

This corrective development release keeps the 0.8.0 feature scope and review
boundaries described in `release-readiness-0.8.0.md`.

Foundry scene labels now resolve native object variants before reading their
names. Java null, the literal `null`, blank and tag-only names use a meaningful
station fallback, such as Mould jig. Numbering remains optional and unchanged.

The sidebar jump was reproduced as queued Swing caret visibility work from
display-only labels. `NEVER_UPDATE` did not stop those existing callbacks. The
original isolated case moved the viewport from 0 to 824; the corrected component
stayed at 0 through the same queued events. Display labels cannot scroll their
parent. Editable inputs only scroll to their caret while visible and focused;
their selection, typing and update policy remain intact. Module navigation still
restores the reader's prior position.

Regression coverage includes delayed labels added below the current viewport,
background input updates, module switching, editable selections and the actual
Foundry render-label path. Current automated evidence is recorded in
`automated-evidence-0.8.1.json`. Live Windows/gameplay acceptance and Hub approval
remain separate unverified gates.
