/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native summaries expose deadlines, not planting timestamps. Rings count down the observed window. */
final class HarvestTimerWindows
{
    private static final class Window
    {
        final long start,end;
        Window(long start,long end){this.start=start;this.end=end;}
    }
    private final Map<String,Window> windows=new HashMap<>();
    void clear(){windows.clear();}
    HarvestClockTool.Reading observe(HarvestClockTool.Reading incoming,long now)
    {
        Map<String,Window> next=new HashMap<>();List<HarvestClockTool.Harvest> harvests=new ArrayList<>();
        for(HarvestClockTool.Harvest harvest:incoming.harvests)
        {
            Window previous=windows.get(harvest.id),window=null;
            if(harvest.pending(now))
                window=previous!=null&&previous.end==harvest.completion?previous:new Window(now,harvest.completion);
            else if(previous!=null&&harvest.observed()&&previous.end==harvest.completion)window=previous;
            if(window!=null){next.put(harvest.id,window);harvest=harvest.withWindow(window.start);}
            harvests.add(harvest);
        }
        windows.clear();windows.putAll(next);
        return new HarvestClockTool.Reading(incoming.active,incoming.canEnable,incoming.message,harvests,incoming.contract);
    }
}
