# Reward soundboard

Six original bundled sounds fit the Runewarden reward colours: a soft white chime,
rising green chime, bright blue chime, purple arcane sting and gold Warden fanfare,
plus a gentle quiet tick. Any preset can be assigned to any tier. Silent is also
available. The notification settings UI owns tier enablement, preset and volume.

`RewardSoundboard.play(SoundPreset, int volume)` accepts 0–100. `stop()` cancels pending
playback and closes the active clip on the worker. `availability()` returns UNKNOWN
until first use, AVAILABLE after successful playback or UNAVAILABLE if no output is
available. A missing/headless mixer is handled without breaking the notification or
game client; audio can retry after a 30-second cooldown.

All resource reading, decoding, mixer access, playback and closure run on the injected
RuneLite scheduler. Calls from the game or Swing thread only replace one pending request.
There is one active clip and one pending request, so a reward/event storm cannot create
overlapping fanfares or an unbounded playback queue. A newer request replaces the pending
one and stops the previous clip before starting. Plugin shutdown must call stop().

The cache is an EnumMap containing at most the six fixed decoded PCM resources. Each
resource is limited to three seconds / 132,300 PCM bytes; the bundled set is much smaller.
Volume is applied to 16-bit PCM samples in software, so output devices without a native
MASTER_GAIN control still honour the volume setting. No game sound volume is modified.

The assets are mono 22,050 Hz signed 16-bit WAV, 180–1,450 ms long. There are no runtime
downloads, user file paths or third-party audio assets. Rebuild them deterministically
with `python3 tools/generate-reward-sounds.py`. The WAVs and original arrangements are
CC0; the generator and Java source remain GPL-3.0-only. Attribution is included in
`licenses/reward-sounds-CC0.txt`.

Presets expose id(), displayName(), durationMillis(), resourcePath(), fromId(String)
and a friendly toString(). Stable IDs are white-chime, green-chime, blue-chime,
purple-sting, gold-fanfare, quiet-tick and silent. Unknown saved IDs fall back to
WHITE_CHIME. This soundboard does not decide whether an item is rare or announce events.
