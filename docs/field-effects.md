# Effects and consumable previews

`FieldCoach` publishes an immutable, account-scoped snapshot for the movable
`FieldStatusOverlay`. The card uses Runewarden's black/white/gold `HudStyle`, with
warnings distinguished by text and colour. The sidebar is a wrapping column and
only refreshes visible content. A hidden card performs no render-time timer reads.

The observed timer families are antifire, super antifire, stamina, eight divine
potion variants, NMZ/raid/Deadman overloads, smelling salts, Menaphite remedy,
goading, prayer regeneration, heart cooldown and surge cooldown, plus separate
poison and venom immunity. Poison/venom are marked active when the public poison
varp reports them. Combined divine effects suppress the matching component rows.
Current boosted/drained skill levels appear separately; a natural-stat decay
countdown is deliberately absent because the initial phase has not been observed.

Timer multipliers come from the public pinned RuneLite 1.13.2
`timersandbuffs/TimersAndBuffsPlugin.java`: antifire/poison buckets 30 ticks,
super antifire 20, stamina/heart/surge 10, overload/salts/remedy 25, goading 6,
prayer regeneration 12 and divine values one tick. A coarse native value first
seen during login is displayed as a remaining-time **range**. An observed change
establishes its tick phase. A native value which stops updating does not become
a falsely precise expiry. Reconnect, profile changes and logout discard phase.
Antivenom's public cutoff is -38, with the venom-immune portion distinct from
the subsequent poison-immune portion. Durations are not a guarantee of complete
protection against a particular dragon or attack; target requirements belong in
the activity/loadout readiness model.

Expiry notifications are off by default and configurable in the owned
`runewardenfield` group. They require an observed synchronized phase and a stable
login baseline; refreshing an effect resets its single-warning latch. The native
Timers & Buffs, Item Stats and other plugins are never reconfigured.

`ConsumablePreviewOverlay` looks up the actual, uncanonicalized inventory/bank
item ID in the native `ItemStatChanges` service. Its native `Effect.calculate`
result supplies relative change, resulting level and theoretical restoration.
The card labels positive restoration lost to the native cap as wasted, retains
negative brew effects, and explicitly says **per use**. It does not multiply a
four-dose potion into a fabricated whole-item forecast. Unsupported items show no
invented effect. Content uses the shared always-on-top hover card and original
widget clipping checks. No HTTP requests or game actions are involved.

Integration: start/stop/clear/onGameTick; forward game-state and owned-config
changes; register both entries in `FieldCoach.overlays()`. The public varbit hook
is a compatibility no-op because native values are sampled once on game ticks.
The HUD style key is `field-effects`.

Primary references:

- <https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/timersandbuffs>
- <https://github.com/runelite/runelite/wiki/Timers-%26-Buffs>
- <https://github.com/runelite/runelite/wiki/Boosts-Information>
- <https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/itemstats>

Tests exercise initial native uncertainty, activation/refresh/deactivation,
stalled observations, combined divine suppression, poison/venom separation and
profile rebaselining. Native ItemStatChanges calculations verify a shark healing
five with fifteen wasted and the negative combat changes of a Saradomin brew.
Headless checks do not establish live gameplay or Plugin Hub approval.

## Shared supply awareness and departure evidence

`SupplyAwarenessTool` reads the shared current-session inventory/equipment ledger,
observed rune pouch and currently available quiver. Its bundled reference contains
**647 exact item-ID states** from RuneLite 1.13.2 `ItemWithCharge`, with retained
BSD-2-Clause attribution and a reproducible source-archive generator. The source
URL and pinned RuneLite version are recorded in `item-charge-reference.json`.
There is no runtime HTTP scrape or hidden blowpipe/staff/tome charge guess.

The sidebar/HUD pools dose or charge variants with the same verified native base
name, while `chargeTotals()` retains exact IDs for Loadout readiness. Mixed
four-dose/one-dose potion stock therefore reports the total doses. Unit labels
separate doses, teleports, water, basket/sack contents, bellows air, sprays and
imps. Ammunition follows the public pinned `AmmoPlugin` rule: a stackable thrown
weapon takes precedence, then stackable ammo-slot contents; nonstackable blessings
are not ammunition. Quiver/pouch rows require their native availability evidence.

Global thresholds cover ammunition, pouch runes, potion doses and other supported
uses; persisted per-item overrides are bounded. Low-supply rows join the movable
Field HUD when effects are enabled. Optional notifications are deduplicated by
native item family, suppressed while banking, and rearmed after supplies rise
above their threshold. They wait through the initial login observation ticks.
Previously observed consumables may stay visible at zero only when current
inventory **and** equipment are known; `Clear empty entries` removes those reminders.
Unobserved containers never become a zero. Login/profile clear resets tracking,
zero reminders, warning state and visible rows. The sidebar pages eight rows and
keeps its threshold controls intact across data refreshes.

`activeEffectKeys()` and `knownEffectKeys()` expose native effect observations for
mandatory departure checks. Before observing the current character, after a
profile switch, or while effects are disabled, known keys are empty: readiness
reports unknown rather than inactive. A native component timer remains available
as evidence even when a matching combined divine potion hides its duplicate HUD
row. Heart/surge selections are labelled as cooldowns. Added supply warning rows
are display data and never become proof that a potion effect is active.

All supply/Field sidebar and HUD snapshot accessors are read-only and do not poll
the native client from Swing. Item/source actions and saved threshold changes
carry profile/generation guards onto the client thread. Tests cover native source
facts, mixed doses, carried/banked distinction, thrown ammo/blessings, quiver/pouch,
hidden-charge exclusions, alerts/rearming/bank suppression, zero/unknown states,
profile/disabled boundaries, future saves and stale UI callbacks.
