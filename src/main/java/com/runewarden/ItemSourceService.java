/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Selected-item requests only, with one request in flight, a bounded memory cache and API backoff. */
@Singleton
public final class ItemSourceService
{
    static final String HOST = "https://oldschool.runescape.wiki";
    private static final Pattern TABLE = Pattern.compile("(?is)<table\\b([^>]*)>(.*?)</table>");
    private static final Pattern ROW = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
    private static final Pattern CELL = Pattern.compile("(?is)<t[dh]\\b([^>]*)>(.*?)</t[dh]>");
    private static final Pattern LINK = Pattern.compile("(?is)<a\\b[^>]*href=[\"'](/w/[^\"'#]+)[\"']");
    static final class Bundle { List<Lookup> items; }

    public static final class Source
    {
        String kind;
        String name;
        String details;
        String wiki;
        boolean curated;
        Source() { }
        Source(String kind, String name, String details, String wiki)
        { this.kind = kind; this.name = name; this.details = details; this.wiki = wiki; }
    }

    public static final class Lookup
    {
        int itemId;
        String title;
        String wiki;
        long revision;
        String retrieved;
        List<Source> sources = new ArrayList<>();
        String error;
        Lookup() { }
        Lookup(Lookup other)
        {
            itemId = other.itemId; title = other.title; wiki = other.wiki; revision = other.revision;
            retrieved = other.retrieved; error = other.error; sources = new ArrayList<>(other.sources);
        }
        static Lookup failure(String reason) { Lookup lookup = new Lookup(); lookup.error = reason; return lookup; }
    }

    private static final class Cached
    {
        final Lookup value; final long expires, characters;
        Cached(Lookup value, long expires, long characters)
        { this.value = value; this.expires = expires; this.characters = characters; }
    }

    /** Limits retained strings and row overhead as well as entry count; caller holds the service lock. */
    static final class LookupCache
    {
        private final Map<String, Cached> entries = new LinkedHashMap<>(32, 0.75f, true);
        private final int entryLimit;
        private final long characterLimit;
        private long characters;
        LookupCache(int entryLimit, long characterLimit)
        { this.entryLimit = entryLimit; this.characterLimit = characterLimit; }
        Lookup get(String key, long now)
        {
            Cached cached = entries.get(key);
            if (cached == null) return null;
            if (cached.expires <= now) { remove(key); return null; }
            return cached.value;
        }
        void put(String key, Lookup value, long expires)
        {
            remove(key);
            long weight = estimate(key, value);
            // The current item can display every row even if retaining it would exceed the cache budget.
            if (weight > characterLimit) return;
            entries.put(key, new Cached(value, expires, weight)); characters += weight;
            while (entries.size() > entryLimit || characters > characterLimit)
                remove(entries.keySet().iterator().next());
        }
        private void remove(String key)
        { Cached previous = entries.remove(key); if (previous != null) characters -= previous.characters; }
        int size() { return entries.size(); }
        long characters() { return characters; }
        static long estimate(String key, Lookup value)
        {
            long size = 96L + length(key) + length(value.title) + length(value.wiki)
                + length(value.retrieved) + length(value.error);
            if (value.sources != null) for (Source source : value.sources)
                size += 64L + length(source.kind) + length(source.name) + length(source.details) + length(source.wiki);
            return size;
        }
        private static int length(String value) { return value == null ? 0 : value.length(); }
    }
    private static final class Pending
    {
        final String title; final List<Consumer<Lookup>> callbacks = new ArrayList<>();
        Pending(String title, Consumer<Lookup> callback) { this.title = title; callbacks.add(callback); }
    }

    private final OkHttpClient http;
    private final Gson gson;
    private final ScheduledExecutorService executor;
    private final RunewardenConfig config;
    private final LookupCache cache = new LookupCache(128, 8_000_000L);
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private final Deque<Pending> queue = new ArrayDeque<>();
    private volatile Call activeCall;
    private boolean processing;
    private long nextAllowed, epoch;

    ItemSourceService(OkHttpClient http, Gson gson, ScheduledExecutorService executor)
    { this(http, gson, executor, new RunewardenConfig() { }); }

    @Inject ItemSourceService(OkHttpClient http, Gson gson, ScheduledExecutorService executor, RunewardenConfig config)
    { this.http = http; this.gson = gson; this.executor = executor; this.config = config; }

    /** Revocation cancels the current call and invalidates queued work before any UI refresh. */
    synchronized void onConsentChanged()
    { if (!config.wikiItemSources()) stop(); }

    synchronized void stop()
    {
        ++epoch; queue.clear(); pending.clear(); processing = false;
        Call call = activeCall; activeCall = null; if (call != null) call.cancel();
    }

    void request(String title, Consumer<Lookup> callback)
    {
        Lookup cached = null;
        synchronized (this)
        {
            if (!config.wikiItemSources())
            {
                stop();
                cached = Lookup.failure("Live Wiki sources are off.");
            }
            else
            {
                String key = title.toLowerCase(Locale.ROOT);
                Lookup entry = cache.get(key, System.currentTimeMillis());
                if (entry != null) cached = entry;
                else
                {
                    Pending existing = pending.get(key);
                    if (existing != null) { existing.callbacks.add(callback); return; }
                    if (pending.size() >= 8) cached = Lookup.failure("Sources busy; select the item again shortly.");
                    else
                    {
                        Pending request = new Pending(title, callback);
                        pending.put(key, request); queue.add(request); schedule(); return;
                    }
                }
            }
        }
        callback.accept(cached);
    }

    private synchronized void schedule()
    {
        if (!config.wikiItemSources()) { stop(); return; }
        if (processing || queue.isEmpty()) return;
        processing = true;
        long expected = epoch;
        executor.schedule(() -> fetch(expected), Math.max(0, nextAllowed - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
    }

    private void fetch(long expected)
    {
        Pending request;
        synchronized (this)
        {
            if (expected != epoch) return;
            if (!config.wikiItemSources()) { stop(); return; }
            request = queue.poll();
            if (request == null) { processing = false; return; }
        }
        Lookup result;
        long backoff = 1500;
        try
        {
            HttpUrl url = HttpUrl.parse(HOST + "/api.php").newBuilder()
                .addQueryParameter("action", "parse").addQueryParameter("page", request.title)
                .addQueryParameter("prop", "text|revid").addQueryParameter("format", "json")
                .addQueryParameter("formatversion", "2").addQueryParameter("redirects", "1")
                .addQueryParameter("disablelimitreport", "1").addQueryParameter("disableeditsection", "1")
                .addQueryParameter("maxlag", "5").build();
            Request query = new Request.Builder().url(url)
                .header("User-Agent", "Runewarden/" + RunewardenVersion.VERSION + " (RuneLite plugin; opt-in item source lookup)")
                .header("Accept", "application/json").build();
            Call call = http.newCall(query);
            synchronized (this)
            {
                if (expected != epoch) { call.cancel(); return; }
                if (!config.wikiItemSources()) { call.cancel(); stop(); return; }
                activeCall = call;
            }
            try (Response response = call.execute())
            {
                if (response.code() == 429 || response.code() == 503)
                {
                    backoff = retryAfter(response.header("Retry-After"));
                    result = Lookup.failure("Wiki is busy. Sources can be retried shortly.");
                }
                else if (!response.isSuccessful() || response.body() == null)
                    result = Lookup.failure("Item sources unavailable (HTTP " + response.code() + ").");
                else
                {
                    JsonObject data = gson.fromJson(readBounded(response.body().charStream()), JsonObject.class);
                    if (data == null || !data.has("parse"))
                    {
                        if (data != null && data.has("error")
                            && data.getAsJsonObject("error").toString().contains("maxlag")) backoff = 30_000;
                        result = Lookup.failure("No item article found for this name.");
                    }
                    else
                    {
                        JsonObject parsed = data.getAsJsonObject("parse");
                        result = parse(parsed.get("title").getAsString(), parsed.get("revid").getAsLong(),
                            parsed.get("text").getAsString());
                    }
                }
            }
        }
        catch (IOException | RuntimeException error) { result = Lookup.failure("Item sources are temporarily unavailable."); }
        List<Consumer<Lookup>> callbacks;
        synchronized (this)
        {
            if (expected != epoch) return;
            if (!config.wikiItemSources()) { stop(); return; }
            activeCall = null;
            pending.remove(request.title.toLowerCase(Locale.ROOT));
            cache.put(request.title.toLowerCase(Locale.ROOT), result,
                System.currentTimeMillis() + (result.error == null ? TimeUnit.HOURS.toMillis(6) : Math.max(30_000, backoff)));
            callbacks = new ArrayList<>(request.callbacks);
            nextAllowed = System.currentTimeMillis() + backoff; processing = false; schedule();
        }
        for (Consumer<Lookup> callback : callbacks)
        {
            synchronized (this)
            { if (expected != epoch || !config.wikiItemSources()) return; }
            callback.accept(result);
        }
    }

    private static long retryAfter(String value)
    {
        try { return Math.max(30_000, Math.min(600_000, Long.parseLong(value) * 1000)); }
        catch (RuntimeException ignored) { return 60_000; }
    }

    private static String readBounded(Reader reader) throws IOException
    {
        StringBuilder text = new StringBuilder(); char[] buffer = new char[8192]; int count;
        while ((count = reader.read(buffer)) != -1)
        {
            if (text.length() + count > 4_000_000) throw new IOException("Item page is too large");
            text.append(buffer, 0, count);
        }
        return text.toString();
    }

    /** Extracts source, shop, gathering and production facts, never article prose or inferred drop rates. */
    static Lookup parse(String title, long revision, String html)
    {
        Lookup result = new Lookup(); result.title = title; result.revision = revision;
        result.wiki = wiki(title) + "?oldid=" + revision;
        result.retrieved = java.time.Instant.now().toString();
        addEquipmentRequirements(result, html);
        ItemSourceHtmlParser.append(result, html);
        return result;
    }

    private static void addEquipmentRequirements(Lookup result, String html)
    {
        String skills = "Attack|Defence|Strength|Ranged|Magic|Prayer|Mining|Woodcutting|Fishing|Hunter|Slayer";
        int firstHeading = html.indexOf("<h2");
        String lead = firstHeading < 0 ? html : html.substring(0, firstHeading);
        Matcher paragraphs = Pattern.compile("(?is)<p\\b[^>]*>(.*?)</p>").matcher(lead);
        Set<String> seen = new LinkedHashSet<>();
        while (paragraphs.find())
        {
            String paragraph = plain(paragraphs.group(1));
            if (!paragraph.toLowerCase(Locale.ROOT).contains("requir")) continue;
            Matcher namedFirst = Pattern.compile("(?i)(" + skills + ") level of ([0-9]{1,2}) to (wield|equip|wear|mine|use)").matcher(paragraph);
            while (namedFirst.find()) addRequirement(result, seen, namedFirst.group(3), namedFirst.group(1) + " " + namedFirst.group(2));
            Matcher levelFirst = Pattern.compile("(?i)([0-9]{1,2}) (" + skills + ")(?: and (?:(?:level )?([0-9]{1,2}) )?(" + skills + "))? to (wield|equip|wear|mine|use)").matcher(paragraph);
            while (levelFirst.find())
            {
                String details = levelFirst.group(2) + " " + levelFirst.group(1);
                if (levelFirst.group(4) != null) details += " · " + levelFirst.group(4) + " "
                    + (levelFirst.group(3) == null ? levelFirst.group(1) : levelFirst.group(3));
                addRequirement(result, seen, levelFirst.group(5), details);
            }
        }
    }

    private static void addRequirement(Lookup result, Set<String> seen, String action, String details)
    {
        if (seen.add(action + details)) result.sources.add(new Source("Requirements",
            Character.toUpperCase(action.charAt(0)) + action.substring(1), details, result.wiki));
    }

    private static int namedIndex(List<String> headers, String name)
    { for (int i = 0; i < headers.size(); i++) if (headers.get(i).equalsIgnoreCase(name)) return i; return -1; }

    private static String headingBefore(String html, int end)
    {
        Matcher heading = Pattern.compile("(?is)<h[23]\\b[^>]*>(.*?)</h[23]>").matcher(html.substring(0, end));
        String last = ""; while (heading.find()) last = plain(heading.group(1)); return last;
    }

    static String plain(String html)
    {
        StringBuilder value = new StringBuilder();
        try
        {
            new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback()
            {
                @Override public void handleText(char[] text, int position) { value.append(text).append(' '); }
            }, true);
        }
        catch (IOException ignored) { }
        return value.toString().replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    static String wiki(String title)
    { return HOST + "/w/" + java.net.URLEncoder.encode(title.replace(' ', '_'), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"); }
}
