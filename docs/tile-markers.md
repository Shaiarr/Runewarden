# Tile markers

Tile markers has its own sidebar module and dashboard shortcut. Categories lead
to activities and packs. Fight Caves uses a separate rotation page followed by
the numbered wave packs. Back returns to the previous level; returning from pack
details retains the pack-list search, page and selection. The category page also
has a back button to Command dashboard.

Fight Caves rotations are static reference groups. They expose no wait timer or
entry control. Inferno and Colosseum opening packs expose only the same explicit
Load markers button as other packs. There is no automatic entry restoration.
See wave-entry.md for the 0.7.3 feature comparison.

Opening a category, activity or pack detail does not replace the active markers.
Load markers explicitly replaces Runewarden's selected pack. Clear markers and
Show markers are available throughout the browser. Personal RuneLite ground
markers are unchanged. Guide marker packs are static references; marker selection requires an explicit
player action. Numbered-marker filters only change the manually selected pack.

The previous Guides panel populated a 1,009-entry marker combo (including the
empty option). The new browser indexes bundled data on a background worker and
limits the pack-list model to 12 fixed-height rows per page. It never creates a
combo containing the marker library. A stopped plugin rejects pending worker
results. The renderer caches selection facts and scene positions, invalidating
them when the scene or instance template changes instead of rescanning instance
chunks for every tile on every frame. These remove observed expensive paths;
live in-game verification remains separate from headless regression checks.
