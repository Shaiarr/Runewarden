# Music unlocks

The Music unlocks sidebar combines the live native music collection with sourced
unlock locations and access requirements. Missing, unlocked and all-track views
share a track/location search and region filter. Eight tracks appear per page.
Each detail view shows the unlock instructions, location, source article and all
available source-route alternatives, with a link to the relevant bundled quest
guide where one exists.

Nearby stops lists up to eight missing tracks with sourced entry coordinates and
observed requirements. It checks listed quest completions, real skill levels and
items carried or worn. Unknown item families and unobserved quest state remain
unknown. Tracks that unlock during quests are left to their quest guide. Sorting
uses coordinate distance on the same plane; this is a suggested visit order, not
a path through walls, transport or instances. Source notes and quest progress
still matter. Selecting a music track never plays music or sends game input.

## Live state and notifications

The pinned RuneLite 1.13.2 public `DBTableID.Music` table supplies native display
names, hints, hidden flags and the `COL_VARIABLE` bank/bit tuple. Hidden native
rows are excluded. Native definitions are matched to local facts by normalised
name. A new native track can still show its native hint even when it is absent
from the bundled snapshot. A Wiki-only track without a native definition, an
unreadable variable or an unsupported future bank remains **Unknown**, rather
than being counted as locked. This is a collection count, not a music-cape
eligibility calculation.

The variable tuple is decoded with the explicit public
`VarPlayerID.MUSICMULTI_1` through `MUSICMULTI_27` constants. The automatic-unlock
case follows the native `music_isunlocked` bank/bit rule. Bit 31 is decoded with
an unsigned shift. No assumptions are made about adjacent varp IDs.

After a five-tick login/profile warmup, the native index reads at most 32 rows per
game tick with an additional 1.5 ms soft budget. A loading row is attempted at
most five times. The index is capped at 4,096 native rows and retained across
account changes. Indexing continues while the sidebar is hidden so new unlock
notifications do not depend on opening the tracker. Afterwards, hidden tracking
compares only 27 music-bank integers per tick and rebuilds the immutable unlock
snapshot only when a bank or definition changes.

The unlock callback fires only for an observed **locked → unlocked** transition
in the same logged-in profile. Initial native definitions, historical login
progress, profile switches and reconnect baselines are silent. The plugin's
shared reward presentation owns the popup and sound preferences.

Visible access checks run once every ten game ticks. Hidden views do not read
quests, skills or item containers for route checks and do not rebuild Swing rows.
Search is debounced by 180 ms; render requests are coalesced. All native API reads
stay on the client thread and all queued UI updates run on Swing's event thread.
The module makes no network requests during play.

## Snapshot and attribution

The bundled snapshot was retrieved on **16 October 2026** and contains:

| Facts | Count |
| --- | ---: |
| Listed music tracks with Wiki unlock instructions | 853 |
| Tracks with a sourced entry coordinate | 719 |
| Source route alternatives | 735 |

Wiki facts were expanded from the [Music table](https://oldschool.runescape.wiki/w/Music)
using the public MediaWiki `expandtemplates` API. The Music page revision is
15341564 and the music-table module revision is 15168435. Bucket-backed table
contents are dynamic: the page revision alone does not freeze their rows. The
expanded snapshot SHA-256 is
`16783c03307af84b6abb99cb26591bb09e96216e7dfa51314224592a56f57a29`.
Each record retains its exact track article URL. Wiki markup was removed from
the factual unlock instructions.

Entry coordinates, region labels and structured route requirements come from
[The-DejaQ/music-tracker at 069ccb5d64644a923da31450ce2b2dcae2769ea9](https://github.com/The-DejaQ/music-tracker/tree/069ccb5d64644a923da31450ce2b2dcae2769ea9),
the commit pinned by the RuneLite Plugin Hub entry when retrieved. Only route
metadata was imported. Navigation and game-interaction code were not imported.
Source quest gates are conservatively checked as completed; quest-unlock tracks
use the quest-guide flow because a song may unlock during the quest.

The dataset records retrieval date, source revision/commit and hash in
`music-unlocks.json`. Wiki facts retain their CC BY-NC-SA 3.0 attribution; route
metadata retains its BSD 2-Clause notice. Both notices are bundled in
`com/runewarden/licenses/`. No audio files are included. Java implementation is
GPL-3.0-only.

## Validation

`MusicUnlockTest` checks the full dataset and source links, native row-zero and
high-bit decoding, banks 26/27, unknown variables, bounded retries and indexing,
hidden tracking, profile/reconnect notification suppression, route requirements
and nearby bounds. List and detail controls are laid out at 206 pixels and in an
actual 242-pixel RuneLite wrapped panel, with wrapped text height and control
bounds checked. These checks do not replace a logged-in visual test of native
music unlocks in the dev client.
