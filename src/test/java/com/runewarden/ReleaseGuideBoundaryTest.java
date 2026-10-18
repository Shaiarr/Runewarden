/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

/** Generic checklist evidence remains distinct from manually selected boss/wave phases. */
public class ReleaseGuideBoundaryTest
{
    @Test public void bossAndWaveGuidesStayReadableButCannotAutoAdvance()
    {
        GuideCatalog catalog = new GuideCatalog(new Gson());
        int manual = 0;
        for (GuideCatalog.Entry entry : catalog.search("", ""))
        {
            if (!"boss".equals(entry.category) && !"waves".equals(entry.category)) continue;
            manual++;
            assertFalse(entry.id, GuideCompletion.supportsAutomatic(entry));
            assertSame("The written guide remains available", entry, catalog.entry(entry.id));
        }
        assertTrue("The bundled boss/wave guides were exercised", manual > 0);
        assertFalse(GuideCompletion.supportsAutomatic(null));
    }

    @Test public void questWalkthroughFollowingIsRemovedWhileSkillAndMinigamesRemainAvailable()
    {
        GuideCatalog catalog = new GuideCatalog(new Gson());
        assertFalse(GuideCompletion.supportsAutomatic(catalog.entry("quest:Cook's Assistant")));
        GuideCatalog.Entry training = new GuideCatalog.Entry();
        training.id = "training:ordinary-mining"; training.title = "Ordinary Mining"; training.category = "training";
        assertTrue(GuideCompletion.supportsAutomatic(training));
        training.id = "training:Nightmare Zone melee"; training.title = "Melee training";
        assertTrue(GuideCompletion.supportsAutomatic(training));
        training.category = "minigame";
        training.id = "minigame:barbarian-assault";
        assertTrue(GuideCompletion.supportsAutomatic(training));
        training.id = "minigame:volcanic-mine";
        assertTrue(GuideCompletion.supportsAutomatic(training));
    }
}
