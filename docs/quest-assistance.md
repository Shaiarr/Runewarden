# Quest Helper handoff

Runewarden’s quest walkthroughs, quest-stage readers, puzzle/branch solvers and quest interaction overlays were removed in 0.9.3. The obsolete generated assistance/frontier datasets and their generation scripts are removed as well.

The catalogue keeps 238 quest metadata rows for account prerequisites, goal tracking, the sourced optimal order and completion filters. These rows contain no walkthrough steps, step-specific supplies or tile-marker packs. Their names, source links and requirements remain attributed to their original sources.

Every quest reference opens the real Quest Helper companion through its public API when the development-client adapter is available. Runewarden’s main plugin has no binary dependency on Quest Helper and does not emit a made-up plugin message or change another plugin’s configuration. An unavailable companion is shown as unavailable; no removed walkthrough is substituted.

The development client includes the unchanged Quest Helper sources pinned to approved commit `5f791937a99a676f23c02aa48c4e4d5b50e7edad`. The launcher adapter selects an exact named helper and opens its sidebar. Ambiguous faction choices stay with Quest Helper’s own chooser.

Training, minigame, boss and wave guides remain Runewarden features. Their own markers, bank focus, completion handling and travel integration are independent of Quest Helper.
