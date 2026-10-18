/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.LinkedHashSet;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/** Stable step IDs persist completion independently of the guide's selected position. */
final class GuideProgress
{
    private GuideProgress() { }

    static String positionKey(String guideId) { return "guide_position_" + encodedId(guideId); }
    static String completedKey(String guideId) { return "guide_completed_" + encodedId(guideId); }

    private static String encodedId(String guideId)
    {
        if (guideId == null || guideId.isEmpty()) throw new IllegalArgumentException("Missing guide ID");
        // RuneLite config keys reject ':' and a leading '$'. URL-safe base64 is stable and collision-free.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(guideId.getBytes(StandardCharsets.UTF_8));
    }

    static Set<String> decode(String stored, List<GuideCatalog.DisplayStep> steps)
    {
        Set<String> valid = new LinkedHashSet<>();
        for (GuideCatalog.DisplayStep step : steps) valid.add(step.step.id);
        Set<String> result = new LinkedHashSet<>();
        if (stored != null)
            for (String id : stored.split("\\n")) if (valid.contains(id)) result.add(id);
        return result;
    }

    static String encode(Set<String> completed) { return String.join("\n", completed); }

    static int next(List<GuideCatalog.DisplayStep> steps, int current, Set<String> completed)
    {
        for (int index = current + 1; index < steps.size(); index++)
            if (!completed.contains(steps.get(index).step.id)) return index;
        for(int index=0;index<current&&index<steps.size();index++)
            if(!completed.contains(steps.get(index).step.id))return index;
        return Math.max(0, Math.min(current, steps.size() - 1));
    }
}
