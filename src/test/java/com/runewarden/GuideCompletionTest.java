/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GuideCompletionTest
{
    @Test public void onlyExactObservedInventoryCanCompleteAnAcquisition()
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        GuideCatalog.Entry cooks = new GuideCatalog(new Gson()).entry("training:giants-foundry");
        GuideCatalog.Step egg = new GuideCatalog.Step(); egg.id = "observed-item";
        egg.completion = new GuideCatalog.CompletionRule(); egg.completion.kind = "inventory"; egg.completion.itemId = 1944; egg.completion.quantity = 1;
        assertFalse(GuideCompletion.observed(client, cooks, egg));
        ItemContainer inventory = mock(ItemContainer.class);
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
        when(inventory.getItems()).thenReturn(new Item[] { new Item(1944, 1) });
        assertTrue(GuideCompletion.observed(client, cooks, egg));
        when(inventory.getItems()).thenReturn(new Item[] { new Item(1945, 100) });
        assertFalse("A different or noted item does not prove the step", GuideCompletion.observed(client, cooks, egg));
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        assertFalse(GuideCompletion.observed(client, cooks, egg));
    }

    @Test public void noRuleOrBossRuleCannotAdvanceAndBoostedLevelsDoNotCount()
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        GuideCatalog.Entry entry = new GuideCatalog.Entry();
        entry.category = "training";
        GuideCatalog.Step step = new GuideCatalog.Step();
        step.text = "Talk to an NPC standing beside you.";
        assertFalse(GuideCompletion.observed(client, entry, step));
        step.completion = new GuideCatalog.CompletionRule();
        step.completion.kind = "level";
        step.completion.skill = "mining";
        step.completion.level = 70;
        when(client.getRealSkillLevel(Skill.MINING)).thenReturn(69);
        when(client.getBoostedSkillLevel(Skill.MINING)).thenReturn(75);
        assertFalse(GuideCompletion.observed(client, entry, step));
        when(client.getRealSkillLevel(Skill.MINING)).thenReturn(70);
        assertTrue(GuideCompletion.observed(client, entry, step));
        entry.category = "boss";
        assertFalse("Combat phases remain manually chosen", GuideCompletion.observed(client, entry, step));
        entry.category = "waves";
        assertFalse(GuideCompletion.observed(client, entry, step));
    }

    @Test public void stableIdsSurviveRestartsAndUnknownIdsCannotCompleteNewSteps()
    {
        GuideCatalog.Entry guide = new GuideCatalog(new Gson()).entry("training:giants-foundry");
        Set<String> done = new LinkedHashSet<>(Arrays.asList("prepare:1", "prepare:2", "removed-old-step"));
        Set<String> restored = GuideProgress.decode(GuideProgress.encode(done), guide.flattened());
        assertEquals(2, restored.size());
        assertEquals(2, GuideProgress.next(guide.flattened(), 0, restored));
        done.clear();
        for (GuideCatalog.DisplayStep step : guide.flattened()) done.add(step.step.id);
        assertEquals("Completing final step keeps its completion visible", guide.flattened().size() - 1,
            GuideProgress.next(guide.flattened(), guide.flattened().size() - 1, done));
    }
}
