# Fight Caves entrance and manual wave references

Version 0.7.3 restores Fight Caves entrance preparation after direct comparison
with the current Hub Fight Caves Spawn Predictor. The 0.7.2 blanket removal was
unsupported. The upstream descriptor pins Damen's
[8c5a34d4c88407cda7a6c60d8014018163d97974](https://github.com/damencs/spawn-predictor/tree/8c5a34d4c88407cda7a6c60d8014018163d97974)
implementation. The existing BSD-2-Clause attribution is retained.

Near the Fight Caves entrance, the movable HUD reads the native server minute
and second, waits for calibration, and displays the current rotation and the
countdown to the player's chosen rotation. Boundary or stale readings suppress
entry confirmation. The target rotation is configurable from Tile markers and
native RuneLite configuration; the entrance card has its own HUD style/lock.

On a freshly confirmed lobby-to-instance transition, the matching wave-one
NPC spawn-origin pack is loaded once. Only the fifteen bundled Fight Caves
wave-one packs with spawn labels and the correct region are eligible. They
indicate NPC spawns, not player standing positions. Manually clearing or changing
markers inside the run is respected; it does not reload markers every tick.
Login, hopping, connection loss and stale calibration cannot reuse an old entry.

Inferno and Colosseum opening packs describe player references and remain
manually loaded in Tile markers. No reactive standing solution, next attack,
prayer sequence or live arena-state export is added. The Wave references panel
contains ordinary, manually opened Wiki strategies. Written boss/wave guides,
phase selection and manual numbered packs remain available.

See [release-review-0.7.3.md](release-review-0.7.3.md) for the feature comparison
and the remaining external submission status.
