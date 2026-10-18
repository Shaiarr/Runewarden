/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Clock values come from native HUD text, never elapsed-time predictions of a mechanic. */
final class MinigameCountdowns
{
    static final class Clock
    {
        final String label;final int seconds,observedWindow;
        Clock(String label,int seconds,int window){this.label=label;this.seconds=seconds;observedWindow=window;}
        double remainingFraction(){return observedWindow<=0?0:Math.min(1d,seconds/(double)observedWindow);}
        String text(){return seconds/60+":"+String.format(java.util.Locale.ROOT,"%02d",seconds%60);}
    }
    private final Map<String,Integer> windows=new HashMap<>(),previous=new HashMap<>();
    private MinigameActivityCatalog.Activity activity;
    private volatile List<Clock> reading=List.of();
    void clear(){activity=null;windows.clear();previous.clear();reading=List.of();}
    List<Clock> reading(){return reading;}
    void observe(MinigameActivityCatalog.Snapshot snapshot)
    {
        if(snapshot==null||snapshot.activity==null){clear();return;}
        if(snapshot.activity!=activity){clear();activity=snapshot.activity;}
        List<Clock> clocks=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(MinigameActivityCatalog.Fact fact:snapshot.facts)
        {
            if(!Set.of("Time","Timer","Round","Portal","Storm").contains(fact.label))continue;
            Integer seconds=MinigameNativeObjectives.clockSeconds(fact.value);
            if(seconds==null||!seen.add(fact.label))continue;
            int window=windows.getOrDefault(fact.label,seconds);
            if(seconds>previous.getOrDefault(fact.label,seconds)+1)window=seconds;
            window=Math.max(window,seconds);windows.put(fact.label,window);previous.put(fact.label,seconds);
            if(clocks.size()<4)clocks.add(new Clock(fact.label,seconds,window));
        }
        windows.keySet().retainAll(seen);previous.keySet().retainAll(seen);reading=List.copyOf(clocks);
    }
}
