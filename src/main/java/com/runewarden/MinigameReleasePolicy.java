/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

/**
 * Presentation eligibility for the twenty authored native activity readers.
 * These readers expose inventory, interface and observed resource/objective state.
 * Boss attack prediction, prayer sequences and computed player-standing positions
 * are not capabilities of this catalog; an activity name is not a reason to hide it.
 */
final class MinigameReleasePolicy
{
    private MinigameReleasePolicy() { }

    static boolean allowsAutomaticGuidance(MinigameActivityCatalog.Activity activity)
    { return activity != null; }

    static boolean nativeFactsOnly(MinigameActivityCatalog.Activity activity)
    { return activity != null && !allowsAutomaticGuidance(activity); }

    static MinigameActivityCatalog.Snapshot releaseSnapshot(MinigameActivityCatalog.Snapshot snapshot)
    {
        if (snapshot == null || snapshot.activity == null) return MinigameActivityCatalog.Snapshot.EMPTY;
        return snapshot;
    }
}
