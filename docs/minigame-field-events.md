# GOTR and Tempoross field evidence

`MinigameFieldEvents` extends the existing activity card and scene overlay with native altar identities, resource/supply highlights and bounded estimates for witnessed minigame events. It uses no steering, game input, prayer prediction, menu mutation or other plugin's settings.

## Guardians of the Rift

All twelve altar identities map the native elemental/catalytic rune sprites to their actual guardian objects, rune items and Runecraft requirements. A guardian is highlighted only when its currently displayed rune matches its category and its actual dynamic-object animation is the native active animation (9363). Its overhead icon uses RuneLite's actual rune item image. Insufficient Runecraft is marked in red and in the activity card.

A witnessed rune-sprite transition starts the pinned helper's 33-tick (19.8-second) altar window. A witnessed hidden-to-visible portal transition starts its 43-tick (25.8-second) portal window. Enabling inside an already active altar/portal leaves remaining time unknown. An unchanged sprite never refreshes the window; expired estimates become unknown while actual active-object evidence may still be displayed. These are estimates, not a claim of tick-perfect expiry.

Actual carried guardian essence adds the current active altar to objective highlights. Guardian stones add the Great Guardian NPC. No uncharged cells adds the cell table. A carried overcharged cell plus chisel adds the two essence piles only when a parseable native guardian count confirms a free slot. Existing fragment-batch and contribution-balance guidance remains available.

Still missing: complete talisman bypass overlays, every charged-cell placement variation, quest-unlock coverage, outside-game reward/start timers, threshold desktop notifications, safe-menu convenience parity and certified tick-perfect behavior.

## Tempoross

| Evidence | Window / behavior |
| --- | --- |
| Actual double fishing NPC spawn (10569) | Approximately 24 seconds, with the source's 20–27-second variability stated in the card. A spot already present when enabled has unknown remaining time. |
| Burning fire object 37582 | First witnessed 24-second spread window. After that window, remaining time is unknown; recurring spread cycles are not claimed. |
| Forming cloud 41006 | Witnessed 9.6-second window. |
| Spreading shadow 41007 | Witnessed 1.2-second window. |
| Native incoming-wave game message | Witnessed 7.8-second window. The warning/action and tether timer expire at that boundary even if impact chat is absent. Native hit/safe-wave messages clear them earlier. |
| Native tether bitfield 11895 | Current tethered state colours the marker green. Current damaged mast/totem forms are red while untethered; intact tether points are gold. |
| Carried fish | Raw × 10, cooked × 15, crystallised × 10 damage, calculated with long arithmetic. |

An untethered player receives the incoming-wave lesson only inside the bounded witnessed wave window. This uses a native minigame message and current tether state. Existing native energy/essence/storm, fishing/cooking/loading and spirit-pool guidance remains integrated.

Still missing: recurring fire-cycle estimates, the complete configurable desktop-notification suite, storm-threshold alert controls, outside-instance reward infobox parity, and full phase-history tracking. No exact depletion deadline is claimed for a random double spot.

## Display and lifecycle

Each of double spots, fires, waves/tethers and GOTR timers has an independent OFF / RADIAL / SECONDS / TICKS choice. Rune icons and current event warnings have separate toggles. RADIAL uses the shared Runewarden timer; estimated rings are dashed, text estimates use `~`, and unknown/expired estimates use `?`. Settings belong exclusively to the validated `runewardenminigameevents` namespace. Module shutdown disables its controls.

Actual object observations are bounded to 192, double spots to 64, and native NPC traversal to 2,048. Unknown preexisting spawns never receive invented full timers. Objects/NPCs are identity-based, removed on despawn, scoped to the current top-level view and plane, and cleared on account, world, scene or activity changes. Render rechecks account/world/game-state identity.

## Primary sources

Pinned official Plugin Hub sources retrieved on 17 October 2026:

- [Guardians of the Rift Helper](https://github.com/DatBear/Guardians-of-the-Rift-Helper/tree/cdee9d0d5cf9aa7782bfe8f5c36c20a2b127d11c): `GuardianInfo`, `GuardiansOfTheRiftHelperPlugin` and `GuardiansOfTheRiftHelperOverlay` supply rune/object/sprite mappings, native active animation, inventory conditions and 33/43-tick windows.
- [Tempoross](https://github.com/nmlynch94/runelite-external-plugins/tree/96ddee1178127c982fd94d59fbc6fdc56ab6a308): `TemporossPlugin` and `TemporossOverlay` supply observed object/NPC identities, game messages, tether bitfield, timing constants, double-spot variability and carried-fish damage.

Full BSD notices ship in `com/runewarden/licenses/minigame-field-events-BSD.txt`. Java 11 tests cover the twelve mappings, unknown initial ages, witnessed transitions, current animation/category matching, native supply conditions, random spot provenance, distinct fire stages, missed-impact expiry, tether colours, estimate rounding/expiry, long damage arithmetic, account/world isolation and editable/stopped controls. A logged-in run remains manual validation.
