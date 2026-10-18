/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import org.junit.Test;
import static org.junit.Assert.*;

public class MinigameHomesTest
{
    @Test public void chatAssignmentConfirmsCustomerAndTier()
    {
        MinigameHomes homes=new MinigameHomes();homes.observe("<col=ff0000>Expert Contract: Go see Bob in north-east Varrock, opposite the church.</col>",true);
        assertEquals("Bob",homes.assigned().name);assertEquals(4,homes.tier());assertEquals("training:homes-expert",homes.guideId());
        assertTrue(homes.facts().stream().anyMatch(f->f.value.equals("16–17 mahogany planks")));
    }
    @Test public void nativeReminderRecoversExistingContractWithMultilineTags()
    {
        MinigameHomes homes=new MinigameHomes();homes.observe("You're currently on an Adept Contract.<br>Go see Jess, upstairs of the building south of the church in East Ardougne. You can get another job once you have furnished her home.",false);
        assertEquals("Jess",homes.assigned().name);assertEquals(3,homes.tier());assertTrue(homes.assigned().upstairs);
    }
    @Test public void requestingTierDoesNotConfirmAnUnassignedCustomer()
    {
        MinigameHomes homes=new MinigameHomes();homes.observe("Could I have a Novice contract please?",false);assertNull(homes.assigned());assertEquals(2,homes.tier());
        homes.observe("Please could you go see Barbara, south of Hosidius, near the mill for us? You can get another job once you have furnished her home.",false);
        assertEquals("Barbara",homes.assigned().name);assertEquals(2,homes.tier());
    }
    @Test public void arbitraryPlayerChatAndUnrecognisedCustomerCannotSetAssignment()
    {
        MinigameHomes homes=new MinigameHomes();homes.observe("Go see Bob in north-east Varrock.",true);assertNull(homes.assigned());
        homes.observe("Expert Contract: Go see Runewarden in Lumbridge.",true);assertNull(homes.assigned());
        homes.observe("Expert Contract: Go see Bob in Varrock.",false);assertNull(homes.assigned());
    }
    @Test public void completionClearsAssignmentAndDoesNotLeakToNextProfile()
    {
        MinigameHomes homes=new MinigameHomes();homes.observe("Expert Contract: Go see Bob in Varrock.",true);assertNotNull(homes.assigned());
        homes.observe("You have completed 1,000 contracts with a total of 4,000 points.",true);assertNull(homes.assigned());assertEquals(0,homes.tier());
        homes.restore(new MinigameHomes.State("Jess",3));assertEquals("Jess",homes.assigned().name);homes.clear();assertNull(homes.assigned());
    }
    @Test public void persistedStateIsBoundedAndCopied()
    {
        assertFalse(MinigameHomes.validState(new MinigameHomes.State("Unknown",4)));assertFalse(MinigameHomes.validState(new MinigameHomes.State("Bob",5)));
        MinigameHomes homes=new MinigameHomes();MinigameHomes.State state=new MinigameHomes.State("Sarah",2);homes.restore(state);state.customer="Bob";assertEquals("Sarah",homes.assigned().name);
        MinigameHomes.State copy=homes.state();copy.customer="Bob";assertEquals("Sarah",homes.assigned().name);
    }
    @Test public void dirtyFlagOnlyChangesWithConfirmedState()
    {
        MinigameHomes homes=new MinigameHomes();assertFalse(homes.takeDirty());homes.observe("Expert Contract: Go see Bob in Varrock.",true);assertTrue(homes.takeDirty());assertFalse(homes.takeDirty());
        homes.observe("Expert Contract: Go see Bob in Varrock.",true);assertFalse(homes.takeDirty());
    }
}
