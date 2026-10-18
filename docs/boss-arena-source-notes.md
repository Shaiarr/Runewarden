# Boss arena lifecycle sources

Tombs of Amascut membership follows the public native observations in the approved
Plugin Hub source pin retained in `docs/hub-source-snapshot/hub-markers.json.gz`:
`LlemonDuck/tombs-of-amascut`, commit
`831c39b9d60f40965d221fe5ac6b4d19d61c97f4`.

- [RaidStateTracker.java](https://github.com/LlemonDuck/tombs-of-amascut/blob/831c39b9d60f40965d221fe5ac6b4d19d61c97f4/src/main/java/com/duckblade/osrs/toa/util/RaidStateTracker.java)
  observes a known source room or visible native widget `481:40`. It waits three
  valid ticks without either observation before declaring a raid exit, to allow
  room transitions. The entrance lobby source region is `13454`.
- [RaidRoom.java](https://github.com/LlemonDuck/tombs-of-amascut/blob/831c39b9d60f40965d221fe5ac6b4d19d61c97f4/src/main/java/com/duckblade/osrs/toa/util/RaidRoom.java)
  defines Nexus `14160`, Crondis puzzle `15698`, Zebak `15700`, Scabaras puzzle
  `14162`, Kephri `14164`, Apmeken puzzle `15186`, Ba-Ba `15188`, Het puzzle
  `14674`, Akkha `14676`, Wardens `15184`/`15696` and reward tomb `14672`.

- `RaidStateTracker.java`: SHA-256 `62b730de60938c81209a07dc48a0ab7df88b217e6aee113c042bf800ebc95925`.
- `RaidRoom.java`: SHA-256 `f70919654fd095af714adaebdd9c46c88d1762eb22c6be4e471b9503f02221c4`.

These facts were read at the exact approved commit. Runewarden does not infer a
Tombs party-status constant, import a solver or advance boss instructions.
Individual ToA boss guides use the boss room regions, rather than the locations
of optional puzzle marker packs.

RuneLite API 1.13.2 documents native ToB varbit `6440` as `1` in party,
`2` inside/spectating and `3` dead spectating (`Varbits.THEATRE_OF_BLOOD`;
current game-value name `VarbitID.TOB_CLIENT_PARTYSTATUS`). Sotetseg arms only
after its source arena is observed, then uses this membership contract to retain
the lesson through an internal maze-template change. Leaving the native ToB run
or entering another boss room from the attributed Theatre marker packs closes
the lesson (including Xarpus). A corridor doorway before the next sourced room
is not separately certified.

Other boss guides use their attributed static source packs, observed native NPC
identity and finite native collision chambers. Open outdoor components do not
establish a physical doorway boundary and fall back to their observed source
region/plane. These observations are lifecycle checks, not evidence that every
boss arena has been verified in a live client.
