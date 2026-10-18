/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.input.KeyManager;
import net.runelite.http.api.worlds.WorldResult;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises actual worker retirement and reuse without DNS, sockets or native game state. */
public class WorldHopperWorkerLifecycleTest
{
    @Test public void stoppingRetiresTheActualIdleWorkerAndRestartCanExecuteFreshWork() throws Exception
    {
        Client client=mock(Client.class);ClientThread clientThread=mock(ClientThread.class);ConfigManager store=mock(ConfigManager.class);
        WorldService service=mock(WorldService.class);KeyManager keys=mock(KeyManager.class);
        Queue<Runnable> clientQueue=new ConcurrentLinkedQueue<>();AtomicBoolean onClient=new AtomicBoolean();
        Thread clientOwner=Thread.currentThread();
        AtomicReference<Thread> created=new AtomicReference<>();AtomicReference<CountDownLatch> enteredRead=new AtomicReference<>(new CountDownLatch(1));
        AtomicInteger reads=new AtomicInteger();AtomicLong clock=new AtomicLong(100000);
        ThreadPoolExecutor executor=new ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),action->{
            Thread thread=new Thread(action,"world-hopper-lifecycle-test");thread.setDaemon(true);created.set(thread);return thread;});
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getWorld()).thenReturn(301);
        when(client.isClientThread()).thenAnswer(i->Thread.currentThread()==clientOwner&&onClient.get());when(client.getWorldType()).thenReturn(EnumSet.noneOf(net.runelite.api.WorldType.class));
        when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);when(client.getTotalLevel()).thenReturn(2000);
        when(store.getRSProfileKey()).thenReturn("rsprofile-worker");
        WorldResult worlds=new WorldResult();worlds.setWorlds(List.of(WorldHopperToolTest.world(301,500),WorldHopperToolTest.world(302,600)));
        when(service.getWorlds()).thenAnswer(i->{assertNotSame("The service read must run on the worker",clientOwner,Thread.currentThread());reads.incrementAndGet();enteredRead.get().countDown();return worlds;});
        doAnswer(i->{clientQueue.add(i.getArgument(0));return null;}).when(clientThread).invokeLater(any(Runnable.class));
        WorldHopperTool tool=new WorldHopperTool(client,clientThread,store,service,keys,new RunewardenConfig(){},executor,
            world->{throw new AssertionError("An invisible module must not open a ping connection");},clock::get);
        try
        {
            tool.start();drainClient(clientQueue,onClient);
            assertTrue("The finite initial world read did not execute",enteredRead.get().await(2,TimeUnit.SECONDS));
            // A worker barrier proves the source task and its finally block have completed.
            executor.submit(()->{}).get(2,TimeUnit.SECONDS);drainClient(clientQueue,onClient);
            assertEquals(2,tool.snapshot().rows.size());Thread first=created.get();assertNotNull(first);assertTrue(first.isAlive());
            tool.stop();first.join(2000);
            assertFalse("Stopping must release the idle worker and its thread-local graph promptly",first.isAlive());
            assertFalse("Stopping retains the same bounded executor for a safe restart",executor.isShutdown());

            enteredRead.set(new CountDownLatch(1));clock.addAndGet(4000);tool.start();drainClient(clientQueue,onClient);
            assertTrue("Restart must execute a fresh source read on the retained executor",enteredRead.get().await(2,TimeUnit.SECONDS));
            executor.submit(()->{}).get(2,TimeUnit.SECONDS);drainClient(clientQueue,onClient);
            Thread second=created.get();assertNotSame("Restart must not retain the retired thread",first,second);
            assertTrue(second.isAlive());assertEquals(2,reads.get());assertEquals(2,tool.snapshot().rows.size());
            tool.stop();second.join(2000);assertFalse("The restarted idle worker must also retire",second.isAlive());
        }
        finally{tool.stop();executor.shutdownNow();assertTrue("The test must release its real executor",executor.awaitTermination(2,TimeUnit.SECONDS));}
    }
    private static void drainClient(Queue<Runnable> queue,AtomicBoolean onClient)
    {
        Runnable action;int bounded=0;
        while((action=queue.poll())!=null)
        {
            assertTrue("Unexpected unbounded client scheduling",++bounded<100);onClient.set(true);
            try{action.run();}finally{onClient.set(false);}
        }
    }
}
