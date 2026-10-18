/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerIdentityTest
{
    @Test public void nameFormattingDoesNotCreateAnAccountSwitch()
    {
        assertEquals("name here", PlayerIdentity.normalizedName(" Name_Here\u00a0"));
        assertEquals(PlayerIdentity.normalizedName("Cathy_Wampus"), PlayerIdentity.normalizedName("  CATHY  WAMPUS "));
        assertEquals("", PlayerIdentity.normalizedName(null));
        assertNotEquals(PlayerIdentity.normalizedName("Alice"), PlayerIdentity.normalizedName("Bob"));
    }
}
