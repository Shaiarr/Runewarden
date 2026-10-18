/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import org.junit.Test;
import static org.junit.Assert.*;

public class ModuleNavigationTest
{
    @Test public void followingAnItemAndReturningPreservesTheGuideJourney()
    {
        ModuleNavigation navigation = new ModuleNavigation();
        navigation.visit("Guides"); navigation.visit("Item database"); navigation.visit("Loadouts");
        assertEquals("Item database", navigation.back());
        assertEquals("Guides", navigation.back()); assertNull(navigation.back());
        assertEquals("Item database", navigation.forward());
        navigation.visit("Farm runs"); assertFalse(navigation.canForward());
        assertEquals("Item database", navigation.back());
    }

    @Test public void duplicateVisitsAndLongSessionsKeepHistoryBounded()
    {
        ModuleNavigation navigation = new ModuleNavigation();
        navigation.visit("Guides"); navigation.visit("Guides"); navigation.visit(null);
        assertEquals(1, navigation.size());
        for (int i = 0; i < 1000; i++) navigation.visit("Module " + i);
        assertEquals(32, navigation.size());
        assertEquals("Module 999", navigation.current());
        for (int i = 0; i < 31; i++) assertNotNull(navigation.back());
        assertNull(navigation.back());
    }

    @Test public void accountResetCannotFollowAnOldHistory()
    {
        ModuleNavigation navigation = new ModuleNavigation();
        navigation.visit("Guides"); navigation.visit("Recovery"); navigation.back();
        navigation.reset("Guides"); assertFalse(navigation.canBack()); assertFalse(navigation.canForward());
        assertEquals("Guides", navigation.current());
    }
}
