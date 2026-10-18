/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

/** Practice clock; manual results and verified owner-loot outcomes share the same journal. */
final class EncounterPracticeSession
{
    static final class Attempt
    {
        String outcome;
        long durationMillis;
        String note;
        String source = "Manual";
        int phase = -1, prepared, contextVersion = 1;
        String focus = "";
        Attempt(String outcome, long durationMillis, String note)
        { this.outcome = outcome; this.durationMillis = durationMillis; this.note = note; }
    }
    private final LongSupplier clock;
    private final List<Attempt> attempts = new ArrayList<>();
    private long began;
    private long accumulated;
    private boolean active;
    private boolean paused;
    private int phase = -1, prepared;
    private String focus = "";
    EncounterPracticeSession(LongSupplier clock) { this.clock = clock; }
    void restore(List<Attempt> saved)
    {
        if (saved == null) return;
        for (Attempt attempt : saved)
            if (attempt != null && attempt.durationMillis >= 0 && attempt.durationMillis <= 7L*86400000 && validOutcome(attempt.outcome))
                attempts.add(copy(attempt));
        trim();
    }
    static Attempt copy(Attempt saved)
    {
        Attempt copy=new Attempt(saved.outcome,saved.durationMillis,boundedNote(saved.note));copy.source="Observed".equals(saved.source)?"Observed":"Manual";
        if(saved.contextVersion==1){copy.phase=Math.max(-1,Math.min(1000,saved.phase));copy.prepared=Math.max(0,Math.min(4,saved.prepared));copy.focus=boundedFocus(saved.focus);}return copy;
    }
    static String boundedFocus(String value){return value==null?"":value.substring(0,Math.min(120,value.length()));}
    static boolean validOutcome(String value){return "Clear".equals(value)||"Retry".equals(value)||"Stopped".equals(value);}
    void restorePaused(long millis) { restorePaused(millis,-1,"",0); }
    void restorePaused(long millis,int phase,String focus,int prepared)
    { if(millis>0) { this.phase=Math.max(-1,Math.min(1000,phase));this.focus=boundedFocus(focus);this.prepared=Math.max(0,Math.min(4,prepared));accumulated=Math.min(millis,7L*86400000);active=true;paused=true; } }
    int phase(){return phase;} String focus(){return focus;} int prepared(){return prepared;}
    void start() { start(-1,"",0); }
    void start(int phase, String focus, int prepared)
    { this.phase=Math.max(-1,phase);this.focus=boundedFocus(focus);this.prepared=Math.max(0,Math.min(4,prepared));accumulated = 0; began = clock.getAsLong(); active = true; paused = false; }
    void pause()
    {
        if (!active || paused) return;
        accumulated = elapsed(); paused = true;
    }
    void resume() { if (active && paused) { began = clock.getAsLong(); paused = false; } }
    Attempt finish(String outcome, String note)
    {
        if (!active) return null;
        if (!validOutcome(outcome)) throw new IllegalArgumentException("Choose Clear, Retry or Stopped");
        Attempt attempt = new Attempt(outcome, Math.min(7L*86400000,elapsed()), boundedNote(note));
        attempt.phase=phase;attempt.prepared=prepared;attempt.focus=focus;
        attempts.add(attempt); trim(); active = false; paused = false; accumulated = 0;
        return attempt;
    }
    long elapsed() { return Math.max(0, accumulated + (active && !paused ? Math.max(0, clock.getAsLong() - began) : 0)); }
    boolean active() { return active; }
    boolean paused() { return paused; }
    List<Attempt> attempts() { List<Attempt> result=new ArrayList<>();for(Attempt attempt:attempts)result.add(copy(attempt));return Collections.unmodifiableList(result); }
    private void trim() { while (attempts.size() > 20) attempts.remove(0); }
    static String boundedNote(String text) { return text == null ? "" : text.substring(0, Math.min(400, text.length())); }
}
