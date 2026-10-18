/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.runelite.client.config.ConfigItem;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Timeout;
import org.junit.Test;
import static org.junit.Assert.*;

/** No sockets or live Wiki requests: the service is exercised through its real queue and fake calls. */
public class ItemSourceConsentTest
{
    private static final class Consent implements RunewardenConfig
    {
        boolean enabled;
        @Override public boolean wikiItemSources() { return enabled; }
    }

    private static final class Clock extends AbstractExecutorService implements ScheduledExecutorService
    {
        final Queue<Runnable> work = new ArrayDeque<>();
        boolean closed;
        void drain() { int budget = 100; while (!work.isEmpty()) { assertTrue(--budget > 0); work.remove().run(); } }
        @Override public void execute(Runnable command) { work.add(command); }
        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
        { work.add(command); return new Ticket(); }
        @Override public <V> ScheduledFuture<V> schedule(Callable<V> command, long delay, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public void shutdown() { closed = true; }
        @Override public List<Runnable> shutdownNow() { closed = true; return Collections.emptyList(); }
        @Override public boolean isShutdown() { return closed; }
        @Override public boolean isTerminated() { return closed; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return closed; }
    }

    private static final class Ticket implements ScheduledFuture<Object>
    {
        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(Delayed other) { return 0; }
        @Override public boolean cancel(boolean interrupt) { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return false; }
        @Override public Object get() { return null; }
        @Override public Object get(long timeout, TimeUnit unit) { return null; }
    }

    private static final class Http extends OkHttpClient
    {
        final List<FakeCall> calls = new ArrayList<>();
        Runnable beforeCall = () -> { };
        Runnable duringCall = () -> { };
        @Override public Call newCall(Request request)
        { FakeCall call = new FakeCall(request, () -> duringCall.run()); calls.add(call); beforeCall.run(); return call; }
    }

    private static final class FakeCall implements Call
    {
        final Request request;
        final Runnable duringCall;
        boolean executed, cancelled;
        FakeCall(Request request, Runnable duringCall) { this.request = request; this.duringCall = duringCall; }
        @Override public Request request() { return request; }
        @Override public Response execute() throws IOException
        {
            executed = true; duringCall.run();
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"),
                    "{\"parse\":{\"title\":\"Yew logs\",\"revid\":123,\"text\":\"\"}}")) .build();
        }
        @Override public void enqueue(Callback callback) { throw new UnsupportedOperationException(); }
        @Override public void cancel() { cancelled = true; }
        @Override public boolean isExecuted() { return executed; }
        @Override public boolean isCanceled() { return cancelled; }
        @Override public Timeout timeout() { return Timeout.NONE; }
        @Override public Call clone() { return new FakeCall(request, duringCall); }
    }

    @Test public void defaultIsOffAndNativeSettingDisplaysExactThirdPartyWarning() throws Exception
    {
        assertFalse(new RunewardenConfig() { }.wikiItemSources());
        ConfigItem setting = RunewardenConfig.class.getMethod("wikiItemSources").getAnnotation(ConfigItem.class);
        assertEquals("This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers", setting.warning());
        assertTrue(setting.description().contains("item title"));
        Http http = new Http(); Clock clock = new Clock();
        ItemSourceService service = new ItemSourceService(http, new Gson(), clock);
        List<ItemSourceService.Lookup> results = new ArrayList<>(); service.request("Yew logs", results::add); clock.drain();
        assertTrue(http.calls.isEmpty()); assertTrue(clock.work.isEmpty());
        assertEquals("Live Wiki sources are off.", results.get(0).error);
    }

    @Test public void explicitConsentFetchesOnlySelectedItemTitleWithVersionedUserAgent()
    {
        Consent config = new Consent(); config.enabled = true; Http http = new Http(); Clock clock = new Clock();
        ItemSourceService service = new ItemSourceService(http, new Gson(), clock, config);
        List<ItemSourceService.Lookup> results = new ArrayList<>(); service.request("Yew logs", results::add);
        assertTrue(http.calls.isEmpty()); clock.drain(); assertEquals(1, http.calls.size());
        Request request = http.calls.get(0).request(); assertEquals("oldschool.runescape.wiki", request.url().host());
        assertEquals("Yew logs", request.url().queryParameter("page"));
        assertEquals(new HashSet<>(Arrays.asList("action", "page", "prop", "format", "formatversion", "redirects", "disablelimitreport", "disableeditsection", "maxlag")), request.url().queryParameterNames());
        assertTrue(request.header("User-Agent").startsWith("Runewarden/" + RunewardenVersion.VERSION));
        assertEquals(1, results.size()); assertNull(results.get(0).error);
    }

    @Test public void revokingBeforeScheduledWorkPreventsRequestConstructionAndDropsQueue()
    {
        Consent config = new Consent(); config.enabled = true; Http http = new Http(); Clock clock = new Clock();
        ItemSourceService service = new ItemSourceService(http, new Gson(), clock, config);
        List<ItemSourceService.Lookup> results = new ArrayList<>();
        service.request("Yew logs", results::add); service.request("Coins", results::add);
        config.enabled = false; service.onConsentChanged(); clock.drain();
        assertTrue(http.calls.isEmpty()); assertTrue(results.isEmpty());
    }

    @Test public void revokingDuringCallCancelsItAndDiscardsEvenACompleteResponse()
    {
        Consent config = new Consent(); config.enabled = true; Http http = new Http(); Clock clock = new Clock();
        ItemSourceService service = new ItemSourceService(http, new Gson(), clock, config);
        http.duringCall = () -> { config.enabled = false; service.onConsentChanged(); };
        List<ItemSourceService.Lookup> results = new ArrayList<>();
        service.request("Yew logs", results::add); service.request("Coins", results::add); clock.drain();
        assertEquals(1, http.calls.size()); assertTrue(http.calls.get(0).isCanceled()); assertTrue(results.isEmpty());
    }

    @Test public void disabledConsentCannotReadCachedLiveResultAndReenableIsExplicit()
    {
        Consent config = new Consent(); config.enabled = true; Http http = new Http(); Clock clock = new Clock();
        ItemSourceService service = new ItemSourceService(http, new Gson(), clock, config);
        List<ItemSourceService.Lookup> results = new ArrayList<>(); service.request("Yew logs", results::add); clock.drain();
        assertEquals(123, results.get(0).revision); results.clear();
        config.enabled = false; service.onConsentChanged(); service.request("Yew logs", results::add); clock.drain();
        assertEquals(1, results.size()); assertNotNull(results.get(0).error); assertEquals(0, results.get(0).revision);
        assertEquals(1, http.calls.size()); results.clear();
        config.enabled = true; service.request("Yew logs", results::add); clock.drain();
        assertEquals(1, results.size()); assertEquals(123, results.get(0).revision); assertEquals(1, http.calls.size());
    }

    @Test public void consentIsRecheckedAtDispatchEvenWithoutConfigChangeNotification()
    {
        Consent config = new Consent(); config.enabled = true; Http http = new Http(); Clock clock = new Clock();
        ItemSourceService service = new ItemSourceService(http, new Gson(), clock, config);
        List<ItemSourceService.Lookup> results = new ArrayList<>(); service.request("Yew logs", results::add);
        config.enabled = false; clock.drain();
        assertTrue(http.calls.isEmpty()); assertTrue(results.isEmpty());
    }

    @Test public void revokingAtCallConstructionPreventsExecutionAndDoesNotStallLaterOptIn()
    {
        Consent config = new Consent(); config.enabled = true; Http http = new Http(); Clock clock = new Clock();
        ItemSourceService service = new ItemSourceService(http, new Gson(), clock, config);
        http.beforeCall = () -> config.enabled = false;
        List<ItemSourceService.Lookup> results = new ArrayList<>(); service.request("Yew logs", results::add); clock.drain();
        assertEquals(1, http.calls.size()); assertTrue(http.calls.get(0).isCanceled());
        assertFalse(http.calls.get(0).isExecuted()); assertTrue(results.isEmpty());
        config.enabled = true; http.beforeCall = () -> { };
        service.request("Yew logs", results::add); clock.drain();
        assertEquals(2, http.calls.size()); assertEquals(1, results.size()); assertNull(results.get(0).error);
    }
}
