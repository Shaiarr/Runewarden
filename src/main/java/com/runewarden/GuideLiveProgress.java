/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable observed objective, never an inference from a saved manual checklist position. */
final class GuideLiveProgress
{
    private GuideLiveProgress() { }
    static final class Reading
    {
        static final Reading UNKNOWN=new Reading(null,null,null,"","","",false,Collections.emptySet());
        final String profile,guideId,stepId,objectiveKey,title,body;
        final boolean terminal;
        final Set<String> confirmedStepIds;
        Reading(String profile,String guideId,String stepId,String objectiveKey,String title,String body,
            boolean terminal,Set<String> confirmedStepIds)
        {
            this.profile=profile;this.guideId=guideId;this.stepId=stepId;
            this.objectiveKey=objectiveKey==null?"":objectiveKey;this.title=title==null?"":title;this.body=body==null?"":body;
            this.terminal=terminal;this.confirmedStepIds=Collections.unmodifiableSet(new LinkedHashSet<>(confirmedStepIds==null?Collections.emptySet():confirmedStepIds));
        }
        boolean known(){return guideId!=null&&(!objectiveKey.isEmpty()||terminal);}
    }
}
