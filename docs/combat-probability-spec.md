<!-- SPDX-License-Identifier: BSD-2-Clause -->
# Combat roll probability model

`CombatRollProbability` is independently expressed from the probability model
below. The source references associated with the combat catalogue provide
gameplay facts (roll conventions, percentages, NPC IDs, floors and immunities),
not imported implementation. No external combat-calculator implementation was
used to write this helper or its exhaustive enumeration tests.

A raw bound `x` becomes `min(0, x + 2)` when negative and remains `x` otherwise.
Its roll is uniform over the integers from `min(0, x)` through `max(0, x)`,
inclusive, after normalization. An attack hits only when it strictly exceeds
the defence roll; ties miss.

Let the attack support be `[aLow, aHigh]`, with `N` values. At defence `d`, the
number of attacks that miss is `F(d) = clamp(d - aLow + 1, 0, N)`. For `n`
independent attacks against one shared defence roll, the conditional chance to
hit is `1 - (F(d) / N)^n`. Normal accuracy uses `n = 1`; Fang uses `n = 2`.
Average this chance over the uniform defence support.

The implementation divides the defence support into certain hits, an overlapping
interval, and certain misses. In the overlap, write `r = aHigh - d` for the
number of winning attack values. The conditional chances are `r/N` and
`2r/N - r²/N²`. If the overlap contains `m` consecutive values of `r` beginning
at `u`, their mean is `u + (m - 1)/2` and their variance is `(m² - 1)/12`.
These are the closed-form first and second power sums. Their normalized moments
give each interval's contribution in constant time. Summing hit mass directly
preserves very small chances without subtracting a value close to one.

The `DpsCalculator.fangAccuracy` adapter retains the calculator's existing signed
roll policy: when raw attack is positive and raw defence is below `-2`, it uses
normal accuracy. Thus the adapter returns `0.75` for `(1, -3)`, while the generic
two-attack model returns `0.875`. This compatibility rule remains pending
separate verification of the game's native signed-roll mechanics; enumeration
alone establishes the probability model, rather than those native conventions.

Confliction uses the normal chance `p` and two-roll chance `q`. Its accuracy is
`q / (q + 1 - p)`, expressed as `1 / (1 + (1 - p)/q)` when `q > 0`, and zero
when `q = 0`.

`DpsTargetScenario` keeps defence-drain ordering explicit. Small arithmetic
helpers distinguish retaining a fraction, subtracting a fraction of the current
level, and subtracting a fixed amount. A sorted NPC-ID table holds fixed floors
and drain immunity as gameplay facts; immunity uses the selected scenario's
starting Defence. Phase guards and unsupported-overflow handling remain part of
the scenario boundary.
