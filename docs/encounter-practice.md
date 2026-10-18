# Boss Academy practice

Boss Academy is a local rehearsal board over the bundled guide library:
117 boss phase guides, 17 wave activities, and 13 equipment/supply guides.
Its category browser has Back navigation, search, and ten-entry pages.

Choose an encounter and phase, read its static movement/mechanic notes, and use
**Use phase guide & markers** to activate that exact bundled section in the guide
coach. The existing guide selection replaces only this plugin's guide markers;
it does not edit personal Ground Markers. Wave reference markers are opening or
reference positions from the selected guide, not a solution for live spawn state.

Players can mark phases practised or for revisit, start/pause/resume an attempt,
and record Clear, Retry, or Stopped. Exact matching native owner-loot events can record an observed Clear for the active selected encounter; local-player death records an observed Retry. Unmatched or raid-wide results remain manual. The recent journal
retains twenty attempts per encounter, with duration and up to 400 characters of
notes. Each outcome retains its manual/observed provenance. It saves locally under the current RuneScape profile and checks profile
identity again before deferred writes. Switching accounts clears the in-memory
board and reloads the new character's journal. Unfinished timers checkpoint and reopen paused, preserving recorded active time without counting offline time.

Phase notes, linked supplies when available, and an explicit Wiki source button
connect to the existing guide/item modules. The tool does not read NPC animations,
select prayers, predict attacks, change combat actions, or solve live wave states.


## Prepare, attempt, review and recover

Every encounter now retains a personal practice goal and four manual readiness
checks: equipment/required items, prayer/spellbook, supplies/charges and exit or
recovery route. Its preparation card links Loadouts, the shared combat target
search and the selected guide's bank requirements. These checkboxes record the
player's acknowledgement; they never certify unobserved item charges or unlocks.

Starting an attempt snapshots the selected phase, preparation count and practice
goal into that attempt. Review shows completed-clear rate, best clear and median
clear time. Voluntarily stopped attempts are counted separately and do not lower
the clear rate. Retry outcomes expose the death/recovery module handoff; reviewing
and preparing the next attempt clears readiness checks while retaining the
practice goal. Native loot/death events affect only unpaused attempts, so an
older paused rehearsal cannot receive the outcome of a later fight.

Existing journals migrate without losing attempts. Preparation arrays, text and
context are bounded, and history readers receive copies rather than mutable
session entries. Manual context survives normal character-specific persistence;
no new external service or game-input path is introduced.
