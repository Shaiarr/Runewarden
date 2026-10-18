/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/** Validated, recoverable configuration belonging exclusively to Runewarden. */
@Singleton
public final class RunewardenDataStore
{
    static final String GROUP = "runewardenrecovery", KEY = "safeSavesV1";
    static final int MAX_PAYLOAD = 1_500_000, MAX_TOTAL = 6_000_000, MAX_ENTRIES = 48;
    public static final class Address
    {
        public final String group, profile, key;
        public Address(String group, String profile, String key)
        {
            if (!owned(group) || GROUP.equals(group) || key == null || key.isEmpty() || key.length() > 250 || key.indexOf('\n') >= 0)
                throw new IllegalArgumentException("Invalid Runewarden setting");
            if (profile != null && (!profile.startsWith("rsprofile") || profile.length() > 180 || profile.indexOf('\n') >= 0)) throw new IllegalArgumentException("Invalid profile");
            this.group = group; this.profile = profile; this.key = key;
        }
        String id() { return group + "\n" + (profile == null ? "" : profile) + "\n" + key; }
    }
    public static final class Problem
    {
        public final Address address;
        public final String reason;
        public final long observedAt;
        public final boolean recoveryAvailable;
        Problem(Entry entry) { address = new Address(entry.group,entry.profile,entry.key); reason = entry.reason;
            observedAt = entry.at; recoveryAvailable = entry.good != null; }
    }
    private static final class Entry
    {
        String group,profile,key,good,rejected,reason;
        long at;
        int supportedVersion;
        boolean blocked;
    }
    private static final class Saves { int version = 1; List<Entry> entries = new ArrayList<>(); }
    private final ConfigManager config;
    private final Gson gson;
    private final Map<String,Entry> entries = new LinkedHashMap<>();
    private boolean loaded;
    private int paused;

    @Inject public RunewardenDataStore(ConfigManager config, Gson gson) { this.config=config; this.gson=gson; }
    public static boolean owned(String group)
    { return group != null && group.startsWith("runewarden") && group.matches("[a-z0-9]{1,60}"); }

    public synchronized <T> T read(String group, String profile, String key, int supportedVersion,
        Class<T> type, Predicate<T> valid, Supplier<T> fallback)
    {
        Address address = new Address(group,profile,key); ensureLoaded();
        String raw = get(group,profile,key);
        if (raw == null) return fallback.get();
        try
        {
            JsonElement json = checked(raw,supportedVersion);
            T value = gson.fromJson(json,type);
            if (value == null || valid != null && !valid.test(value)) throw new IllegalArgumentException("Unsupported saved data");
            Entry before = entries.get(address.id());
            if (before == null || !before.blocked) remember(address,raw,supportedVersion);
            return value;
        }
        catch (RuntimeException invalid)
        {
            reject(address,raw,supportedVersion,reason(invalid));
            Entry entry=entries.get(address.id());
            if (entry != null && entry.good != null)
                try
                {
                    T recovered=gson.fromJson(checked(entry.good,supportedVersion),type);
                    if (recovered != null && (valid == null || valid.test(recovered))) return recovered;
                }
                catch (RuntimeException ignored) { }
            return fallback.get();
        }
    }

    /** Keeps future/invalid payloads in place until an explicit recovery or reset. */
    public synchronized boolean write(String group, String profile, String key, int version, Object value)
    {
        Address address=new Address(group,profile,key); ensureLoaded();
        if (paused > 0 || value == null) return false;
        Entry existing=entries.get(address.id());
        if (existing != null && existing.blocked) return false;
        String current=get(group,profile,key);
        if (current != null)
            try { checked(current,version); }
            catch (RuntimeException invalid) { reject(address,current,version,reason(invalid)); return false; }
        JsonElement tree=gson.toJsonTree(value);
        if (tree == null || tree.isJsonNull()) return false;
        if (tree.isJsonObject() && !tree.getAsJsonObject().has("version")) tree.getAsJsonObject().addProperty("version",version);
        String raw=gson.toJson(tree);
        try { checked(raw,version); } catch (RuntimeException invalid) { return false; }
        if (Objects.equals(raw,current)) return true;
        remember(address,raw,version);
        put(group,profile,key,raw); return true;
    }

    public synchronized boolean blocked(String group,String profile,String key)
    { ensureLoaded(); Entry entry=entries.get(new Address(group,profile,key).id()); return entry != null && entry.blocked; }

    public synchronized List<Problem> problems(String profile)
    {
        ensureLoaded(); List<Problem> result=new ArrayList<>();
        for (Entry entry:entries.values())
            if (entry.blocked && (entry.profile == null || Objects.equals(profile,entry.profile))) result.add(new Problem(entry));
        return Collections.unmodifiableList(result);
    }

    /** Called only from the explicit recovery control. Rejected data remains retained. */
    public synchronized boolean recover(Address address)
    {
        ensureLoaded(); Entry entry=entries.get(address.id()); if (entry == null || entry.good == null) return false;
        try { checked(entry.good,entry.supportedVersion); } catch (RuntimeException invalid) { return false; }
        put(address.group,address.profile,address.key,entry.good);
        entry.blocked=false; entry.reason=null; save(); return true;
    }
    /** Explicit reset preserves the rejected payload in the recovery record. */
    public synchronized void reset(Address address)
    {
        ensureLoaded(); Entry entry=entries.get(address.id());
        if (entry != null) { entry.blocked=false; entry.reason=null; entry.good=null; }
        remove(address.group,address.profile,address.key); save();
    }
    public synchronized void pauseWrites(Runnable action)
    { paused++; try { action.run(); } finally { paused--; } }

    public synchronized void reload() { entries.clear();loaded=false;ensureLoaded(); }

    private String get(String group,String profile,String key) { return profile==null?config.getConfiguration(group,key):config.getConfiguration(group,profile,key); }
    private void put(String group,String profile,String key,String value) { if(profile==null)config.setConfiguration(group,key,value);else config.setConfiguration(group,profile,key,value); }
    private void remove(String group,String profile,String key) { if(profile==null)config.unsetConfiguration(group,key);else config.unsetConfiguration(group,profile,key); }

    static void validateJson(String raw,int max)
    {
        if(raw==null||raw.length()>max)throw new IllegalArgumentException("Saved data exceeds the supported size");
        int depth=0;boolean quoted=false,escaped=false;
        for(int i=0;i<raw.length();i++)
        {
            char ch=raw.charAt(i);
            if(quoted){if(escaped)escaped=false;else if(ch=='\\')escaped=true;else if(ch=='"')quoted=false;continue;}
            if(ch=='"')quoted=true;
            else if(ch=='{'||ch=='['){if(++depth>48)throw new IllegalArgumentException("Saved data is nested too deeply");}
            else if(ch=='}'||ch==']')depth--;
        }
    }

    private static JsonElement checked(String raw,int supportedVersion)
    {
        validateJson(raw,MAX_PAYLOAD);
        JsonElement json=new JsonParser().parse(raw);
        if (json == null || json.isJsonNull()) throw new IllegalArgumentException("Saved data is empty");
        if (json.isJsonObject())
        {
            JsonObject object=json.getAsJsonObject(); JsonElement version=object.get("version");
            if (version != null && (!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
                || version.getAsDouble() != version.getAsInt() || version.getAsInt() < 1 || version.getAsInt() > supportedVersion))
                throw new IllegalArgumentException("Saved by an unsupported version");
        }
        return json;
    }
    private static String reason(RuntimeException error)
    { return error instanceof IllegalArgumentException && error.getMessage() != null && error.getMessage().startsWith("Saved")
        ? error.getMessage() : "Saved data could not be read"; }
    private Entry entry(Address address)
    {
        Entry result=entries.get(address.id());
        if (result == null)
        { result=new Entry(); result.group=address.group;result.profile=address.profile;result.key=address.key;entries.put(address.id(),result); }
        return result;
    }
    private void remember(Address address,String raw,int version)
    {
        Entry entry=entry(address); if (Objects.equals(entry.good,raw)) return;
        entry.good=raw;entry.supportedVersion=version;entry.at=System.currentTimeMillis(); save();
    }
    private void reject(Address address,String raw,int version,String reason)
    {
        Entry entry=entry(address); boolean changed=!entry.blocked || !Objects.equals(entry.reason,reason);
        entry.blocked=true;entry.reason=reason;entry.supportedVersion=version;entry.at=System.currentTimeMillis();
        // Oversize originals remain untouched in their original configuration location.
        if (raw != null && raw.length() <= MAX_PAYLOAD && !Objects.equals(entry.rejected,raw)) {entry.rejected=raw;changed=true;}
        if (changed) save();
    }
    private void ensureLoaded()
    {
        if (loaded) return;loaded=true;String raw=config.getConfiguration(GROUP,KEY);
        if (raw == null || raw.length() > MAX_TOTAL + 1_000_000) return;
        try
        {
            Saves saved=gson.fromJson(raw,Saves.class);if(saved==null||saved.version!=1||saved.entries==null)return;
            int size=0;
            for (Entry entry:saved.entries)
            {
                if(entries.size()>=MAX_ENTRIES)break;
                if(entry==null||!owned(entry.group)||GROUP.equals(entry.group)||entry.key==null||entry.key.length()>250)continue;
                final Address address;
                try {address=new Address(entry.group,entry.profile,entry.key);}catch(IllegalArgumentException invalidEntry){continue;}
                if(entry.good!=null&&entry.good.length()>MAX_PAYLOAD)entry.good=null;
                if(entry.rejected!=null&&entry.rejected.length()>MAX_PAYLOAD)entry.rejected=null;
                size+=length(entry.good)+length(entry.rejected);if(size>MAX_TOTAL)break;
                if(entry.reason!=null&&entry.reason.length()>180)entry.reason=entry.reason.substring(0,180);
                entries.put(address.id(),entry);
            }
        }
        catch(RuntimeException invalid) { entries.clear(); }
    }
    private void save()
    {
        // Prefer retaining blocked originals; evict old non-problem backups first.
        while (entries.size()>MAX_ENTRIES || size()>MAX_TOTAL)
        {
            String victim=null;
            for(Map.Entry<String,Entry> row:entries.entrySet()) if(!row.getValue().blocked){victim=row.getKey();break;}
            if(victim==null)victim=entries.keySet().iterator().next();
            entries.remove(victim);
        }
        Saves saved=new Saves(); int remaining=MAX_TOTAL;
        for(Entry entry:entries.values())
        {
            if(saved.entries.size()>=MAX_ENTRIES)break;
            int bytes=length(entry.good)+length(entry.rejected);
            if(bytes>remaining)continue;
            remaining-=bytes;saved.entries.add(entry);
        }
        config.setConfiguration(GROUP,KEY,gson.toJson(saved));
    }
    private int size(){int result=0;for(Entry entry:entries.values())result+=length(entry.good)+length(entry.rejected);return result;}
    private static int length(String value){return value==null?0:value.length();}
}
