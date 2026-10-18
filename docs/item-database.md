# Item database

The Item database is a dedicated, paged sidebar browser. Item identity, uses, requirements, training methods and source cards are distinct full-width sections. Source fields occupy compact separate labeled rows; the Method details and Source details toggles keep ingredient notes and provenance collapsed by default. Long item and guide buttons wrap to the actual RuneLite viewport width. Search by item name or exact
item ID, select an item, and use Back to return to the search page. Search results are
limited to 20 visible rows; source cards use pages of 5. The previous giant marker
picker failure must not be repeated by constructing thousands of Swing controls.

The shared bundled item-name snapshot provides 12,218 named entries immediately without a
network request. When logged in, native item definitions are indexed in batches of
96 per game tick while the database is visible; no native definition scan runs before it is opened or while it is hidden. Newly released items therefore join the same local search during use. Explicit guide-item clicks still resolve their selected item directly. Native
item names, icons, membership, stackability, GE estimate, high-alchemy value and
available inventory actions are copied from RuneLite on the client thread.

Skilling facts reuse the resource hover catalog: action-specific XP, level requirements,
ingredient quantities and uses. Red requirements reflect the current player's real
skill levels. All account-dependent level observations clear on profile changes. Hidden detail pages do not repaint on level changes; reopening renders the latest observation.

Source cards show actual source table fields: drops/rewards, gathering sources, shops,
training info, creation requirements and products. Wielding and tool-use requirements are kept separate, and full-width quest unlock rows are retained. Quantity and rarity remain as the Wiki states them, including
ranges, conditional rates and 'Varies'; no rates are inferred. Common resources and gear
have 1,577 bundled source, product and requirement cards across 23 items, with pinned Wiki revisions. Live source retrieval is **off by default**. The native `Live Wiki item sources` setting and the item-panel `Allow live Wiki sources` checkbox disclose the third-party IP warning before opting in. Only after that opt-in does selecting an item request
that item's rendered OSRS Wiki API article, asynchronously. The request sends the selected item title and the network IP address to `oldschool.runescape.wiki`; no account name, inventory, equipment, bank, goals or other account observations are sent. Bundled source, training and requirement cards remain available without this permission. Turning the setting off cancels the active call, invalidates queued requests and stale UI callbacks, prevents cache delivery, and restores bundled details. There is one request
in flight, a 1.5-second pause between responses, maxlag/HTTP 429/503 backoff, a bounded
response size and a six-hour memory cache capped at both 128 items and 8 million estimated retained characters (about 16 MB of character payload). Least recently used responses are evicted by weight. A larger individual lookup can display every source without entering the cache. Repeated selections coalesce requests.
The injected RuneLite OkHttp client, Gson and scheduler are reused; no new HTTP client
is created, and search never polls the Wiki. Failed live requests preserve bundled data.

Item cards list linked guides that use the exact item, including section supplies and
recommended items. Source cards also open matching bundled boss/activity guides. Normalized guide titles are indexed off-thread once, and a bounded 256-source match cache avoids repeated catalog scans during sidebar rendering. Wiki
article, individual source article and exact source revision links are available.

The extractor recognises source tables and short explicit acquisition statements.
Unusual templates and articles without a supported acquisition statement can still have
partial coverage; the full article link remains available. Bundled names include
historical variants; search collapses identical names, while exact IDs remain accessible.
Sources and native item facts are account-independent; plugin stop cancels active requests.

Integration: start(), stop(), onGameTick(), clearProfile(), createPanel(), onConfigChanged(String key),
setOpenGuideAction(Consumer<String>) and openItem(int). Guide item click handlers can call
openItem and navigate to the Item database module. Item source data carries its own Wiki
CC BY-NC-SA 3.0 attribution in licenses/item-sources-CC-BY-NC-SA.txt.

## 0.8 acquisition coverage and parsing

The offline acquisition index now covers **4,538 exact item IDs** and **22,926 additional
cards**: 15,468 direct drop/reward records, 5,350 shop records and 2,108 equipment
requirement records. These supplement the 23 detailed revision-pinned pages. Provenance
is the existing Wiki acquisition export dated 7 October 2026 and its exact-ID equipment
snapshot; individual equipment definitions may have older update dates. Snapshot cards
are not presented as a live revision. The reproducible import script retains SHA-256
input digests, raw explicit rarities and conditions. Export-derived probabilities,
assumed rarities, shared-table expansion, derived rarity classes and shop price estimates
are excluded. Direct records are broad coverage, not a claim of every possible source.

Only the 335 KB identity/coverage index is read at catalog startup. Full source cards are
split into 64 shards. Selecting an item loads its shard on the injected worker executor;
at most four shards remain in the memory LRU. Rendering reads cached data only. Lifecycle
and selection epochs prevent an earlier selection's shard or live response replacing the
current item. Live requests begin after the selected offline lookup completes, preserving
source merging and the existing default-off consent, cancellation and backoff guarantees.

The rendered-Wiki parser now reads a structural table grid instead of matching closing
`</table>` tags. Rowspans, colspans, nested decorative tables, icon columns, seller headers,
creation tables, reward/spawn tables and short explicit acquisition prose are supported.
Source actions select the relevant named cell's article rather than an earlier image link.
Historical, negative and trivia acquisition statements are excluded. Short prose facts
retain their source article and revision; the parser does not infer missing probabilities.
Skill requirements in equipment/creation cards use the observed character levels for
missing-level colour, as training requirement cards already do.

Rebuild acquisition data with `tools/build-item-acquisition.py --drops <npc-drops.json>
--shops <shops-items-by-shop.json> --items <items-complete.json>`. The import has no runtime
network dependency. Source-export regression fixtures retain their upstream attribution
under `tools/fixtures/item-acquisition`.
