# Death and recovery

`DeathRecoveryTool` records an authoritative local-player `ActorDeath` only after
a stable logged-in, living-player baseline. NPC deaths, initial login, repeated
delivery and reconnect initialization do not create history. Up to 20 records
are kept per native RuneScape profile through `RunewardenDataStore`, with a
four-record history page in the sidebar. Corrupt and future-version settings
remain protected by the shared recovery service.

A death record contains its observed location, world, instance flag and the
recent inventory/equipment snapshot labelled **Carried before death**. These
items are not claimed to have been lost, because protection, safe deaths,
minigame rules and storage rules differ. The **Mark recovered** action is an
explicit player confirmation. Emptying a currently observed storage does not
silently mark unrelated death records recovered. World-location routing is an
explicit request; instance deaths require returning through their entrance and
do not produce a path to meaningless dynamic instance coordinates.

Recovery is established separately from actual public retrieval interfaces:

| Interface | Observed items | Ownership ledger publication |
| --- | --- | --- |
| `GravestoneGeneric` (672) | `InventoryID.GRAVESTONE` (525), while interface visible | `GRAVESTONE` |
| `DeathOffice` (669) | `InventoryID.DEATH_PERMANENT` (636), while interface visible | `DEATH_OFFICE` |
| `GravestoneRetrieval` (602) | Actual dynamic item children of its public items widget | Kept as generic item retrieval; no assumed boss/grave mapping |

Native fee widget text is shown verbatim after removing display tags. There is
no guessed fee, timer, loss prediction or mapping of every instance to a death
provider. A hidden cached container does not establish a new recovery
observation. Closed recovery interfaces retain a dated **last seen** record;
grave and Death's Office ownership observations stay distinct in the shared
ledger. Item details open through the supplied item-database callback.

The module never retrieves, equips, buys, teleports or performs another game
action. It reads native observations and presents manual controls. No web calls,
other-plugin settings or private plugin state are used.

Integration: start/stop/clear/onGameTick; `onActorDeath(ActorDeath)`,
`onGameStateChanged(GameStateChanged)`, `onItemContainerChanged(int)` and owned
`onConfigChanged(ConfigChanged)`. Set explicit route and item callbacks. Tick
the shared OwnershipLedger before this module so matching native observations
can be published. There is no separate death overlay in this implementation.

Pinned primary API references: RuneLite 1.13.2 `gameval/InventoryID.java`,
`gameval/InterfaceID.java`, `events/ActorDeath.java`, `widgets/Widget.java` and
the public native inventory-container APIs. The broader storage-reference
design also follows the separately licensed public project
<https://github.com/Thource/dude-wheres-my-stuff> without copying its code.

Tests cover login warmup, player-only observations, repeat suppression,
per-character persistence, reconnect baselines, visible versus hidden native
storage, separate grave/office contents, native fee text, generic retrieval,
empty-storage ambiguity and protected future-version history. Live in-game
recovery timing and individual boss providers remain unverified.

## Return kit and return-to-activity workflow (0.7)

Each recorded death can now carry a separate **Return kit**. The user chooses an existing saved loadout in the shared Loadouts module and explicitly clicks **Use selected loadout** on that death. The exact checked plan is copied into the death record and persists independently of later edits or selection changes. A pre-death inventory/equipment snapshot never becomes a return kit automatically and remains labelled **Carried before death**.

The return-kit card pages every copied requirement in groups of six and shows native item icons, current item/spellbook readiness and a **Bank missing kit items** action. Bank demand is calculated from the exact copied requirement alternatives and quantities, consuming physical carried quantities only once. Items already in inventory/equipment or an observed usable pouch/quiver are not requested from the bank a second time merely because they need equipping or moving to a different slot. Last-seen bank/death storage quantities never count as carried. Wrong-slot gear still appears as requiring packing/equipping in the stricter loadout readiness display.

Departure checks copied with a kit are left for review after death. In particular, an earlier manual confirmation, antifire assumption, unverified charge state or active-effect assumption is not carried over as automatic readiness. Native charge/effect facts are not fabricated. A return kit can be cleared or replaced explicitly without acknowledging the death.

A death captures the active guide ID supplied by the shared activity integration. **Return to activity guide** opens that guide only after a manual, profile-guarded click. It does not claim that the items have been recovered and performs no game input. **Choose return loadout** opens the shared loadout editor; explicitly applying it again refreshes the copied return kit.

Recovery type remains a separate observation. **Link open recovery** associates the chosen death with the kind of a currently visible native Gravestone, Death's Office or generic item-retrieval interface. The association is labelled **linked by you**, because an interface alone does not prove which historical death produced its contents. The interface is rechecked when the queued click executes. Hidden/stale containers cannot establish recovery type. Generic item retrieval is not relabelled as a particular boss deathbank. Deathpiles and unsupported provider-specific recovery types remain unverified; no universal fee, grave timer or item-loss prediction has been added.

A new death advances the module's generation so queued actions from an older view cannot silently attach a kit, link recovery, route or resume after a second death. Profile changes and stop/restart also invalidate queued actions. Kit bindings and recovery links roll back their in-memory change if guarded persistence fails. Existing version-one death histories load with no invented kit, activity guide or recovery type.

Additional root callbacks:

- `setSelectedLoadoutSupplier(Supplier<LoadoutPlan>)`
- `setActivityGuideSupplier(Supplier<String>)`
- `setBankFocusAction(Consumer<Set<Integer>>)`
- `setOpenLoadoutsAction(Runnable)`
- `setResumeGuideAction(Consumer<String>)`

The existing six-argument injection constructor and lifecycle/event hooks remain compatible. The shared ownership ledger is ticked before death recovery. Kit readiness snapshots are rebuilt on the client thread every three ticks when a kit exists; Swing rendering reads those snapshots and captured local names. Tests cover explicit kit choice, exact-copy restart persistence, missing-only bank focus, wrong-slot carried gear, stale profiles, queued actions across a second death/restart, visible-interface linking, post-death manual-check uncertainty, long-kit paging and the explicit activity-guide callback. The populated return-kit sidebar is rendered at 206 pixels with no Client API reads on Swing.
