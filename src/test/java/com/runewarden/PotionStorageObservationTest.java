/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import net.runelite.api.Item;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PotionStorageObservationTest
{
    private static Widget[] row(int itemId,String doses)
    {Widget[] widgets=new Widget[5];widgets[0]=mock(Widget.class);when(widgets[0].getItemId()).thenReturn(itemId);widgets[1]=mock(Widget.class);when(widgets[1].getText()).thenReturn(doses);return widgets;}
    @Test public void nativeRowsUseTheDoseLabelAndOneDoseIdentityInsteadOfWithdrawIconQuantity()
    {Item[] items=PotionStorageObservation.read(row(2434,"<col=ffcc00>12,345 doses</col>"));assertNotNull(items);assertEquals(1,items.length);assertEquals(143,items[0].getId());assertEquals(12345,items[0].getQuantity());}
    @Test public void unreadableOrAbbreviatedNativeAmountsStayUnknown()
    {assertNull(PotionStorageObservation.read(row(2434,"12K")));assertNull(PotionStorageObservation.read(row(2434,"")));assertNull(PotionStorageObservation.read(row(2434,"2,147,483,648")));assertNull(PotionStorageObservation.read(row(2434,null)));}
    @Test public void zeroNativeDosesCanRepresentAnObservedEmptyStore()
    {assertEquals(0,PotionStorageObservation.read(row(2434,"0")).length);}
    @Test public void missingOrUnrecognisedWidgetTreesAreNeverClaimedEmpty()
    {assertNull(PotionStorageObservation.read(null));assertNull(PotionStorageObservation.read(new Widget[0]));assertNull(PotionStorageObservation.read(row(4151,"100")));Widget[] widgets=row(2434,"4");widgets[1]=null;assertNull(PotionStorageObservation.read(widgets));}
}
