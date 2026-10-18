/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** EDT-only ownership of one automatically selected guide. A manual choice always wins. */
final class MinigameGuideSession
{
    private String previous, automatic;
    private boolean manualOverride;

    /** Re-check at dispatch time; a persisted follow preference cannot bypass the release policy. */
    void transitionReleased(MinigameActivityCatalog.Snapshot snapshot, boolean follow,
        Supplier<String> selected, Consumer<String> select)
    {
        transition(snapshot.guideId, follow && MinigameReleasePolicy.allowsAutomaticGuidance(snapshot.activity),
            snapshot.activity != null, selected, select);
    }

    void transition(String nextGuide, boolean follow, Supplier<String> selected, Consumer<String> select)
    { transition(nextGuide, follow, nextGuide != null, selected, select); }

    void transition(String nextGuide, boolean follow, boolean activityActive, Supplier<String> selected, Consumer<String> select)
    {
        String current = selected.get();
        boolean owned = automatic != null && Objects.equals(automatic, current);
        if (!follow || nextGuide == null || nextGuide.isEmpty())
        {
            if (owned) select.accept(previous);
            boolean preserveManualChoice = follow && activityActive && (manualOverride || automatic != null && !owned);
            forget(); manualOverride = preserveManualChoice; return;
        }
        if (manualOverride) return;
        if (automatic != null && !owned)
        {
            previous = null; automatic = null; manualOverride = true; return;
        }
        if (Objects.equals(current, nextGuide))
        {
            if (!owned) manualOverride = true;
            return;
        }
        String restore = owned ? previous : current;
        previous = restore; automatic = nextGuide; select.accept(nextGuide);
    }
    void forget() { previous = null; automatic = null; manualOverride = false; }
    void dismiss(){previous=null;automatic=null;manualOverride=true;}
}
