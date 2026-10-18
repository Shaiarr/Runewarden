# Runewarden 0.9.1

This development update fixes progress visibility and guide/run lifecycle problems on the 0.9.0 feature baseline.

## Fixed behavior

- The training HUD now renders an XP progress bar toward the explicit run target, or toward the next level when no target is set.
- Action progress accepts native animation definitions beyond the previous short list. A fresh animation event starts a fresh bar rather than inheriting elapsed frames from the previous action. Source-backed cast/recipe cycles and confirmed batch estimates retain their existing evidence rules.
- Confirmed guide steps advance to the next unfinished instruction, including restored progress. Completing the final step or observing a finished quest closes the guide and its owned overlays.
- Matching selected minigame guides display native current tasks. These facts do not mark unseen static checklist steps complete.
- Reaching an explicit XP target finishes the run. End run immediately dismisses guides, guide markers, automatic minigame HUD/scene coaching and guide bank focus, even if no run is active or a previous run is already finished.
- Queued updates cannot resurrect dismissed guides. A new run reopens its linked guide; repeatable training guides can restart after completion, including progress loaded after a client restart.

## Verification and remaining acceptance

The automated evidence is recorded in `automated-evidence-0.9.1.json`. Regression tests render actual overlay classes and exercise confirmed completion, native task changes, all-run dismissal, stale callbacks and repeatable guide loading. The full Java suite, data fingerprints, mechanical Hub checks and delivered archive fidelity are checked separately.

The [0.9.0 capability comparison](hub-utility-audit.md) continues to record partial and missing specialist features. This update does not claim full Plugin Hub parity. Real Windows execution, in-game acceptance and actual Plugin Hub review remain unverified in `live-acceptance.json`.
