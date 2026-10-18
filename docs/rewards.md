# Reward celebrations

The **Rewards** sidebar configures collection-log, rare-drop, music-unlock and combat-achievement celebrations. Each category and the master switch can be toggled independently. Settings are global presentation preferences, rather than character progress. The five-tier soundboard shows White, Green, Blue, Purple and Gold rows; opening a row reveals its sound toggle, bundled preset, volume, **Listen** preview and **Popup** preview. Only one sound row expands at a time.

Observed rewards enter a bounded 20-entry queue. Collection chat and the native notification fallback are deduplicated; the same item drop and log unlock can share one celebration when their observed ticks correlate. A genuinely later drop is retained separately. Character changes discard queued account observations and stale callbacks. Existing login counters do not replay an account's reward history. Explicit generic popup previews remain available before login and while normal celebrations are disabled.

**Appearance** controls popup duration (2–20 seconds), scale (60–160%) and animation. Defaults are six seconds, 100% scale and animation enabled. Every tier starts with its matching original bundled sound at 65% volume. Silent and gentle-tick choices are available. Audio runs through the dedicated soundboard service; absent audio output does not block the client. Popup demos are explicitly labelled previews and do not record a game reward.

## Separate rarity metrics

Collection rarity defaults to **Ownership** when a source reference covers that item. **Drop chance** is a separate optional collection metric and the source of ordinary rare-drop tiers. Ownership prevalence and the probability of a particular reward are not interchangeable. Both labels can appear on a notification, with their source/context retained. Unknown rarity remains White with **Unrated** rather than an invented percentage or chance. Coverage and source dates belong to the reference dataset; ownership is not presented as a census of every player.

The default tier boundaries are editable under **Rarity thresholds**:

| Tier | Ownership reference | Source drop denominator |
| --- | --- | --- |
| White | Above 25% | Below 50 |
| Green | Above 10%, up to 25% | 50 to below 200 |
| Blue | Above 2%, up to 10% | 200 to below 1,000 |
| Purple | Above 0.25%, up to 2% | 1,000 to below 5,000 |
| Gold | At most 0.25% | At least 5,000 |

Chance fields express **1 in N**; ownership fields express percentages. Chance denominators must increase from Green to Gold, while ownership ceilings must decrease. Source restrictions remain relevant: a probability conditional on receiving a purple reward is not silently reinterpreted as a per-raid chance. Music and combat achievements identify their own event types and context; their presentation is not an ownership or loot-probability measurement.

**Rare-drop filters** defaults to Blue as the minimum tier and zero as the stack-value floor. A drop must satisfy both its minimum tier **and** the configured value floor. Collection-log, music and combat-achievement toggles remain independent of these drop filters. A supplied item price is an estimate; an unknown price is not replaced with a fabricated value.

## Storage and rendering

Preferences use RuneLite's global `runewardenrewards/preferencesV1` configuration with the injected Gson. JSON input is bounded to 32 KiB; enum sounds are restricted to bundled preset IDs, volumes to 0–100%, and numeric/threshold ranges are validated. No account credentials or progress are stored in this settings payload. Renderers read a cached snapshot without per-frame configuration reads or JSON parsing. Runtime settings are separate from reward observation, account guards and reference-data provenance.

The sidebar uses the shared width-aware layout, with advanced filters, appearance and thresholds collapsed initially. It is regression-checked at a 206-pixel content width; no giant always-open settings form is required.
