/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SailingCoachTasksTest
{
    Client client;SailingCoachTasks tasks;final int[] values=new int[15];AtomicInteger tick=new AtomicInteger();
    @Before public void setup(){client=mock(Client.class);tasks=new SailingCoachTasks();when(client.getVarbitValue(anyInt())).thenAnswer(e->{int i=(int)e.getArgument(0)-19574;return i>=0&&i<15?values[i]:0;});when(client.getTickCount()).thenAnswer(e->tick.get());}
    static void courier(Client client,int id,int row,int from,int to,int amount,int item)
    {
        when(client.getDBRowsByValue(DBTableID.PortTask.ID,DBTableID.PortTask.COL_TASK_ID,0,id)).thenReturn(List.of(row));
        when(client.getDBTableField(row,DBTableID.PortTask.COL_TASK_TYPE,0)).thenReturn(new Object[]{0});when(client.getDBTableField(row,DBTableID.PortTask.COL_CARGO_PORT,0)).thenReturn(new Object[]{from});when(client.getDBTableField(row,DBTableID.PortTask.COL_ENDING_PORT,0)).thenReturn(new Object[]{to});
        when(client.getDBTableField(row,DBTableID.PortTask.COL_CARGO,1)).thenReturn(new Object[]{amount});when(client.getDBTableField(row,DBTableID.PortTask.COL_CARGO,0)).thenReturn(new Object[]{item});when(client.getDBTableField(row,DBTableID.PortTask.COL_NAME,0)).thenReturn(new Object[]{"Supplies"});
    }
    @Test public void nativePickupDeliveryAndLedgerStatesChooseTheRightPort()
    {
        courier(client,8,808,8587,8597,10,30000);SailingCoachTasks.Task pickup=SailingCoachTasks.decode(client,0,8,3,0);assertNotNull(pickup);assertEquals("Port Sarim",pickup.nextPort().name);assertEquals("Collect 7 cargo at Port Sarim",pickup.objective());
        SailingCoachTasks.Task deliver=SailingCoachTasks.decode(client,0,8,10,4);assertEquals("Port Khazard",deliver.nextPort().name);assertEquals("Deliver 6 cargo to Port Khazard",deliver.objective());assertEquals("Finish at Port Khazard's ledger",SailingCoachTasks.decode(client,0,8,10,10).objective());
    }
    @Test public void allFiveSlotsAreReadAndAnUnchangedSnapshotAvoidsRepeatedNativeDbReads()
    {
        for(int i=0;i<5;i++){values[i*3]=i+1;courier(client,i+1,800+i,8587,8597,10,30000+i);}tasks.observe(client);assertEquals(5,tasks.tasks().size());clearInvocations(client);tick.incrementAndGet();tasks.observe(client);verify(client,never()).getDBRowsByValue(anyInt(),anyInt(),anyInt(),anyInt());
        values[1]=10;tasks.observe(client);assertEquals("Port Khazard",tasks.tasks().get(0).nextPort().name);
    }
    @Test public void taskRemovalAndAccountResetRemovePreviousDataImmediately()
    {
        values[0]=8;courier(client,8,808,8587,8597,10,30000);tasks.observe(client);assertEquals(1,tasks.tasks().size());values[0]=0;tasks.observe(client);assertTrue(tasks.tasks().isEmpty());values[0]=8;tasks.observe(client);tasks.clear();assertTrue(tasks.tasks().isEmpty());
    }
    @Test public void unavailableRowsRetryOnABoundedCadenceThenRecover()
    {
        values[0]=8;tasks.observe(client);assertEquals(1,tasks.unsupported());courier(client,8,808,8587,8597,10,30000);tick.set(9);tasks.observe(client);assertTrue(tasks.tasks().isEmpty());tick.set(10);tasks.observe(client);assertEquals(1,tasks.tasks().size());assertEquals(0,tasks.unsupported());
    }
    @Test public void bountyAmbiguityMissingFieldsAndUnknownPortsRemainUnresolved()
    {
        courier(client,8,808,8587,8597,10,30000);when(client.getDBTableField(808,DBTableID.PortTask.COL_TASK_TYPE,0)).thenReturn(new Object[]{1});assertNull(SailingCoachTasks.decode(client,0,8,0,0));
        when(client.getDBTableField(808,DBTableID.PortTask.COL_TASK_TYPE,0)).thenReturn(new Object[]{0});when(client.getDBTableField(808,DBTableID.PortTask.COL_CARGO_PORT,0)).thenReturn(new Object[]{123});assertNull(SailingCoachTasks.decode(client,0,8,0,0));
        courier(client,8,808,8587,8597,10,30000);when(client.getDBTableField(808,DBTableID.PortTask.COL_CARGO,1)).thenReturn(new Object[]{});assertNull(SailingCoachTasks.decode(client,0,8,0,0));
        when(client.getDBRowsByValue(DBTableID.PortTask.ID,DBTableID.PortTask.COL_TASK_ID,0,8)).thenReturn(List.of(808,809));assertNull(SailingCoachTasks.decode(client,0,8,0,0));
    }
    @Test public void inconsistentCountersAndMalformedNativeTypesDoNotCreateAnObjective()
    {
        courier(client,8,808,8587,8597,10,30000);assertNull(SailingCoachTasks.decode(client,0,8,11,0));assertNull(SailingCoachTasks.decode(client,0,8,5,6));assertNull(SailingCoachTasks.decode(client,0,8,-1,0));
        when(client.getDBTableField(808,DBTableID.PortTask.COL_TASK_TYPE,0)).thenReturn(new Object[]{"delivery"});assertNull(SailingCoachTasks.decode(client,0,8,0,0));
    }
    @Test public void nativeDatabaseLookupFailureDegradesToUnresolved()
    {
        when(client.getDBRowsByValue(DBTableID.PortTask.ID,DBTableID.PortTask.COL_TASK_ID,0,8)).thenThrow(new IllegalArgumentException("Not available"));assertNull(SailingCoachTasks.decode(client,0,8,0,0));
    }
}
