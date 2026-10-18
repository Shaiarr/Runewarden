/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MinigameGuideSessionTest
{
    @Test public void exitRestoresExactlyTheGuideThatWasOpenBeforeAutomaticEntry()
    {
        String[] selected = {"quest-a"}; List<String> actions = new ArrayList<>();
        MinigameGuideSession session = new MinigameGuideSession();
        session.transition("wintertodt", true, () -> selected[0], id -> { selected[0] = id; actions.add(id); });
        assertEquals("wintertodt", selected[0]);
        session.transition(null, false, () -> selected[0], id -> { selected[0] = id; actions.add(id); });
        assertEquals("quest-a", selected[0]); assertEquals(Arrays.asList("wintertodt", "quest-a"), actions);
    }
    @Test public void aManualGuideChoiceAlwaysWinsOnExit()
    {
        String[] selected = {"quest-a"}; MinigameGuideSession session = new MinigameGuideSession();
        session.transition("wintertodt", true, () -> selected[0], id -> selected[0] = id);
        selected[0] = "manual-quest";
        session.transition(null, false, () -> selected[0], id -> selected[0] = id);
        assertEquals("manual-quest", selected[0]);
    }
    @Test public void directActivitySwitchKeepsTheOriginalGuideWithoutAnIntermediateReload()
    {
        String[] selected = {"quest-a"}; List<String> actions = new ArrayList<>();
        MinigameGuideSession session = new MinigameGuideSession();
        java.util.function.Consumer<String> select = id -> { selected[0] = id; actions.add(id); };
        session.transition("wintertodt", true, () -> selected[0], select);
        session.transition("gotr", true, () -> selected[0], select);
        assertEquals(Arrays.asList("wintertodt", "gotr"), actions);
        session.transition(null, false, () -> selected[0], select);
        assertEquals("quest-a", selected[0]);
    }
    @Test public void existingMatchingGuideAndDisabledFollowDoNotAcquireSelectionOwnership()
    {
        String[] selected = {"wintertodt"}; List<String> actions = new ArrayList<>();
        MinigameGuideSession session = new MinigameGuideSession();
        session.transition("wintertodt", true, () -> selected[0], actions::add);
        session.transition(null, false, () -> selected[0], actions::add);
        session.transition("gotr", false, () -> selected[0], actions::add);
        assertTrue(actions.isEmpty());
    }
    @Test public void accountResetForgetsOwnershipAndNeverRestoresAnOldAccountGuide()
    {
        String[] selected = {null}; MinigameGuideSession session = new MinigameGuideSession();
        session.transition("wintertodt", true, () -> selected[0], id -> selected[0] = id);
        session.forget(); selected[0] = "new-account-guide";
        session.transition(null, false, () -> selected[0], id -> selected[0] = id);
        assertEquals("new-account-guide", selected[0]);
    }
    @Test public void aManualChoiceSurvivesSubsequentNativeMethodChanges()
    {
        String[] selected = {"quest-a"}; MinigameGuideSession session = new MinigameGuideSession();
        session.transition("blast-gold", true, () -> selected[0], id -> selected[0] = id);
        selected[0] = "manual-quest";
        session.transition("blast-steel", true, () -> selected[0], id -> selected[0] = id);
        session.transition(null, true, true, () -> selected[0], id -> selected[0] = id);
        session.transition("blast-mithril", true, () -> selected[0], id -> selected[0] = id);
        assertEquals("manual-quest", selected[0]);
        session.transition(null, false, () -> selected[0], id -> selected[0] = id);
        assertEquals("manual-quest", selected[0]);
    }
}
