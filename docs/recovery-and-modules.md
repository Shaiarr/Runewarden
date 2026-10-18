# Recovery, backups and module lifecycle

RunewardenDataStore guards versioned personal plans, observations, checkpoints and
preferences. Valid reads retain a last-good payload. Invalid JSON, rejected
schemas or future versions preserve the original configuration and block writes.
The Recovery screen offers explicit last-good restore or reset, retaining the
rejected copy. Up to 48 recovery addresses and 6 million payload characters are
retained; old metadata is evicted when necessary, while original settings remain
in their own location. Oversized payloads are not duplicated.

Backup & recovery exports only Runewarden groups, shared settings and the active
RuneScape profile. JSON import is data-only, bounded and character-bound. The multi-character recovery journal remains local and is excluded from exports; current-character originals remain included. Preview
shows shared/character setting counts. Restore merges reviewed keys and restarts
the plugin; undo restores previous values and removes keys added by that restore.
Only Runewarden's own nine HUD cards are included in layout exports and per-character
position restore. Their public preferred locations and snap positions are applied
with RuneLite's public `OverlayManager.resetOverlay` and `saveOverlay` APIs. The
plugin does not write to RuneLite's configuration group or inspect private origin
fields. Free positions are saved as absolute canvas coordinates. Older layout
codes using a right, centre or widget-relative native origin retain any snap
position but reset their unsupported free anchor; use RuneLite's normal Alt-drag
to place that card again.

Modules & diagnostics performs actual service start/stop and skips suspended
tick work. The sidebar shows an enable action for suspended modules. Shared
ownership/account facts are core observations, rather than independent feature
modules that can silently stale active consumers. A module tick exception
suspends that module and records only the exception class in the report.

Client-thread initialization is registered with the module lifecycle before it
runs. A saved-disabled module stays stopped, and queued initialization or enable
actions from an older plugin instance cannot revive a replacement instance.
Stopped bank filters also reject external preparation focus and native filter
callbacks until enabled again.

Measurements use System.nanoTime and a bounded 1,200-sample per-module window.
p95 describes that window; max describes the current enabled-plugin session.
The plugin does not collect JVM heap measurements. No relative performance
claim follows from the timing values. The support report contains no character name,
world coordinates, container contents, exception text or saved setting values.

The harvest dashboard consumes public, read-only Time Tracking services only
when that native plugin is already active. If it is disabled, the dashboard asks
the player to enable Time Tracking in RuneLite's own plugin settings. Runewarden
does not enable, start, stop or alter the settings of that plugin.
