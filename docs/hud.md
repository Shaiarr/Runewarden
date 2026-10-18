# Runewarden HUD

Runewarden is a standalone RuneLite command centre. Its HUD reads local client
state and uses the native OSRS item and resource sprites.

Open **HUD** to switch cards on or off. **Lock HUD positions** controls nine
movable cards: resources/ticks, character dashboard, guide, harvest, outfit,
inventory, effects, training and Fight Caves entrance. Unlock and Alt-drag with RuneLite's overlay controls.
**Reset HUD positions** resets only these cards.

Native positions and anchors survive locking, plugin restart and harvest card
module suspension. Reloading a backup reapplies the active character's restored
positions, even if that character was already logged in.

The **Widget editor** applies a separate scale, text size, opacity, accent/text/
background colours, rounded/square/borderless frame and visibility condition to
each card. Resource values can show current/max, current only or percentages.
Colour inputs accept six hexadecimal digits. The preview shows text, colours
and frame before applying. The editor uses one left-aligned, wrapping column
at the sidebar's actual width, including its 206-pixel content area. Text-size changes keep the entire card proportional
so icons, wrapping and displayed overlay bounds stay aligned.

**PvM**, **Skilling** and **AFK** presets can be loaded and overwritten for the
current character. Saved layouts include both presentation and native positions
and anchors of Runewarden's own cards. Copy/Paste layout codes shares that data
through the clipboard, excluding account details and other plugins' settings.
Old style-only codes remain importable. Character switches restore that
character's layout; first use retains an existing layout.

- **Resources:** horizontal health, prayer, special attack and run-energy bars.
  Health/prayer alert percentages are configurable. Native game orbs are unchanged.
- **Ticks:** pulse, bar, dots or number beside the resources, with a configurable
  1–12-tick cycle. Each heartbeat comes from RuneLite's actual `GameTick` event;
  animation shows time elapsed since that event. It is a metronome rather than
  an attack or mechanic prediction. Optional native sound is off by default and
  respects game sound settings. **Click training cue** is on by default and
  briefly flashes the literal **CLICK** at each received tick, then returns to
  the chosen visual. Switch it off for the ordinary pulse/dot or selected style.
  The cue is a training rhythm; it does not inspect prayers, NPC attacks or
  animations and never clicks anything.
- **Inventory:** all 28 slots retain their actual positions and item icons,
  including stack counts. Choose closed-tab, always or combat visibility, and
  whether to hide at the bank. It is a read-only mirror without item actions.
- **Character:** clock, world, supported normal-attack max hit and 11-slot worn
  equipment grid. Clock, UTC and equipment preferences are independent.
- **Guide:** the selected local step and progress. Compact mode limits detail
  to five lines; expanded mode permits ten. Full walkthroughs stay in Guides.
- **Harvest:** observed due groups and the earliest selected upcoming timer,
  with an additional-group count. Unknown observations do not create ready alerts.
- **Outfit:** missing outfit pieces for the skill currently being trained.

Inventory snapshots update on game ticks, and disabled inventory performs no
client/item work. A preview hidden by tab, combat, banking or style visibility
also skips container/image refresh. Layout parsing happens only when its bounded setting changes;
styles and fonts are cached. A hidden dashboard skips its reads, and disabling
the worn grid skips its image/name work. Disabled outfit/node features skip their
respective observations. The HUD performs no HTTP requests. Profiles and
preferences use local RuneLite configuration. Account-dependent cards hide at
logout; inventory snapshots are cleared on profile transitions.

The development bundle still needs live in-game testing and Plugin Hub review.
