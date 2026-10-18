# Varlamore live thieving coach

The dedicated Thieving coach module recognises wealthy-citizen pickpocketing and the three burgled houses in Civitas illa Fortis. Its movable black, white and gold HUD shares a profile/world-bound snapshot with the scene highlights and sidebar. It reads native state and the player's own actions; it performs no game input and never changes another plugin's configuration or the native hint arrow.

## Current coverage

- Citizens: known wealthy-citizen IDs 13302–13305 are highlighted. A citizen is labelled distracted only while actually interacting with one of the three native distraction urchins, IDs 13306–13308. Interaction with another player or an unrelated NPC does not qualify.
- Distractions: a witnessed native start message enables an estimated current-distraction ring and the Wiki's 84–99 second next-cycle window. Current distraction is an upper bound of 24 seconds; the separate citizen article describes approximately 20 seconds. The dashed ring represents this uncertainty. Enabling the plugin mid-distraction shows the actual distracted target and “remaining unknown”; it does not restart a full timer.
- House entry: actual locked/open doors at each verified house entrance determine availability. House-key and valuables counts come from the inventory. A locked door first encountered on entry has no inferred departure time. Only a witnessed open-to-locked transition starts the estimated 180–190 second owner-return window.
- Homeowners: the three owners are highlighted from current native positions. The native return-warning message immediately prioritises the escape window. An owner currently close to the entrance is also shown; no future path is calculated.
- Furniture: the three active searchable chest/wardrobe/jewellery-case forms are highlighted inside the current house. A bonus highlight follows only the game's existing coordinate arrow when it actually points at one of those objects. A witnessed native shine message can add the approximate seven-second bonus ring. An unrelated/missing arrow does not identify a bonus target.
- Container activity: the player's own Search/Steal click identifies a loaded furniture object. The estimated 50–60 second container window begins only after subsequently receiving valuables, not on an unfulfilled click or while walking. The native empty-container message stops the timer and prompts a different object. Clicking the same active object does not renew the observed interval.
- Escape: actual native window objects are marked, including Victor's window just outside the strict interior rectangle. Known window morphs are resolved through public object definitions.
- Value: current carried valuables are valued at Oriana's fixed 55 gp each, or 65 gp at native Colosseum glory 8,000+. The calculation uses long arithmetic and is separate from live GE prices.

The Sidebar opens either existing wealthy-citizen or stealing-valuables training guide on request. HUD, dashed circular timers, citizen/owner/door/window/furniture/bonus highlights, labels, compact density and scale are independently configurable. Preferences use the guarded Runewarden data store; live timers are never persisted across profiles or worlds.

## Native fields and evidence limits

RuneLite 1.13.2 names varbits 9928–9932 as house-entry, loot-progress, last-checked, caught-tracker and special-location fields. Their integer observations are available through the coach's diagnostic reader. The implementation does **not** invent an enum mapping or turn undocumented integer values into an owner deadline. Actual NPC interaction, resolved door forms, the existing native bonus arrow and native messages drive the live coach, as they do in the referenced Hub helper.

Leaving the supported city/plane, scene reload, logout, hopping, profile change or module shutdown clears observation state. Snapshot readers also check the current profile and world before rendering, so an account switch hides the previous account immediately. A bounded cache holds at most 192 relevant objects; the scene is seeded once per view/base/plane and maintained by object events. Native NPC traversal is bounded to 2,048 entries and retains at most 64 relevant NPCs. Renderers read snapshots rather than scanning scene tiles or resolving definitions.

An elapsed timing window is not represented as proof that its event happened. Missed starts, changed scenes, conflicting door forms and unavailable native information degrade to unknown. This implementation does not predict exact random timings, choose the next random house or claim complete parity with every third-party thieving notification feature. In-game verification of native message visibility, menu options and scene reloads remains part of release acceptance.

## Sources and attribution

- Current official Plugin Hub marker `plugins/varlamore-house-thieving`, retrieved 2026-10-17: `sololegends/runelite-varlamore-house-thieving` commit **ed8c58e1e9dbc73508187c1d88000c61115aee6e**. House bounds, NPC/door/furniture identifiers, native interaction/hint-arrow conventions and fixed-value threshold are adapted from this MIT-licensed source.
- [Pinned Hub source](https://github.com/sololegends/runelite-varlamore-house-thieving/tree/ed8c58e1e9dbc73508187c1d88000c61115aee6e).
- [OSRS Wiki: Stealing valuables](https://oldschool.runescape.wiki/w/Stealing_valuables), raw wikitext retrieved 2026-10-17: timing ranges, bonus window and fixed trade-in values.
- [OSRS Wiki: Wealthy citizen](https://oldschool.runescape.wiki/w/Wealthy_citizen), raw wikitext retrieved 2026-10-17: citizen identifiers and distraction behavior. API revision metadata could not be fetched, so this reference is retrieval-dated rather than falsely revision-pinned.
- [RuneLite native identities](https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-api/src/main/java/net/runelite/api/gameval), version **1.13.2**: `NpcID`, inherited `ObjectID1`, `VarbitID` and `VarPlayerID`.

The full upstream MIT notice ships in the plugin JAR as `com/runewarden/licenses/varlamore-thieving-MIT.txt`. Wiki-derived timing facts retain the attribution in `varlamore-thieving-Wiki.txt`.
