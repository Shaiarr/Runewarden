# Reward soundboard

Six original bundled sounds fit the Runewarden reward colours: a soft white chime,
rising green chime, bright blue chime, purple arcane sting and gold Warden fanfare,
plus a gentle quiet tick. Any preset can be assigned to any tier. Silent is also
available. The notification settings UI owns tier enablement, preset and volume.

`RewardSoundboard.play(SoundPreset, int volume)` accepts 0–100. Playback uses the
injected RuneLite `AudioPlayer`; Runewarden does not access Java Sound devices or
native clips directly. `stop()` and mute cancel pending requests. The public player
has no stop handle, so audio already submitted finishes naturally, within its
maximum 1.45-second duration. `availability()` returns UNKNOWN until first use,
AVAILABLE after a successful API handoff or UNAVAILABLE after an API failure.
This is not a physical audibility check. Failures allow retry after 30 seconds.

Resource reads, bounded WAV validation and audio handoff run on the injected
RuneLite scheduler. RuneLite owns native playback and cleanup. Calls from the game
or Swing thread replace one pending request. Handoffs are separated by the known
preset duration plus a 250 ms guard; an event storm retains only the newest waiting
sound instead of building a queue or interrupting current audio. Plugin shutdown
calls stop() to clear pending requests.

The cache is an EnumMap containing at most the six fixed decoded PCM resources. Each
resource is limited to three seconds / 132,300 PCM bytes; the bundled set is much smaller.
Volume is applied to 16-bit PCM samples in software, so output devices without a native
MASTER_GAIN control still honour the volume setting. No game sound volume is modified.

The assets are mono 22,050 Hz signed 16-bit WAV, 180–1,450 ms long. There are no runtime
downloads, user file paths or third-party audio assets. Rebuild them deterministically
with `python3 tools/generate-reward-sounds.py`. The WAVs and original arrangements are
CC0; the generator and Java source remain BSD-2-Clause. Attribution is included in
`licenses/reward-sounds-CC0.txt`.

Presets expose id(), displayName(), durationMillis(), resourcePath(), fromId(String)
and a friendly toString(). Stable IDs are white-chime, green-chime, blue-chime,
purple-sting, gold-fanfare, quiet-tick and silent. Unknown saved IDs fall back to
WHITE_CHIME. This soundboard does not decide whether an item is rare or announce events.
