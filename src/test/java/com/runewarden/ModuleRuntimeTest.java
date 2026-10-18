package com.runewarden;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class ModuleRuntimeTest
{
    @Test public void disabledModuleActuallyStopsAndSkipsWorkUntilEnabled()
    {ConfigManager config=mock(ConfigManager.class);when(config.getConfiguration(ModuleRuntime.GROUP,"test")).thenReturn("false");ModuleRuntime runtime=new ModuleRuntime(config,mock(ClientThread.class));AtomicInteger starts=new AtomicInteger(),stops=new AtomicInteger(),ticks=new AtomicInteger();runtime.registerStarted("Test",starts::incrementAndGet,stops::incrementAndGet);assertEquals(1,stops.get());runtime.tick("Test",ticks::incrementAndGet);assertEquals(0,ticks.get());when(config.getConfiguration(ModuleRuntime.GROUP,"test")).thenReturn("true");runtime.changed();assertEquals(1,starts.get());runtime.tick("Test",ticks::incrementAndGet);assertEquals(1,ticks.get());}
    @Test public void failingModuleSuspendsWithoutRepeatedTickExceptionsAndReportIsBounded()
    {ConfigManager config=mock(ConfigManager.class);ModuleRuntime runtime=new ModuleRuntime(config,mock(ClientThread.class));AtomicInteger stops=new AtomicInteger();runtime.registerStarted("Test",()->{},stops::incrementAndGet);runtime.tick("Test",()->{throw new IllegalStateException("private character inventory details");});assertFalse(runtime.enabled("Test"));runtime.tick("Test",()->fail("suspended"));String report=runtime.report();assertTrue(report.contains("IllegalStateException"));assertFalse(report.contains("private character"));assertEquals(1,stops.get());assertTrue(report.contains("samples=1"));}
    @Test public void measurementsRetainOnlyTheLatestTwelveHundredSamples()
    {ModuleRuntime runtime=new ModuleRuntime(mock(ConfigManager.class),mock(ClientThread.class));runtime.registerStarted("Test",()->{},()->{});for(int i=0;i<1500;i++)runtime.tick("Test",()->{});assertTrue(runtime.report().contains("samples=1200"));assertTrue(runtime.report().contains("p95="));}
}
