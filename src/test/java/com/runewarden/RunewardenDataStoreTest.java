package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises recovery outcomes and isolation, rather than mirroring serialization internals. */
public class RunewardenDataStoreTest
{
    private static final String GROUP = "runewardentest", ALICE = "rsprofile.alice", BOB = "rsprofile.bob", KEY = "recordsV1";
    static final class Value
    { int version = 1; int count; String text = ""; Value() { } Value(int count) { this.count = count; } }
    static final class Unversioned { int count = 4; }

    @Test public void validRoundTripAndVersionInsertion()
    {
        Fixture f = new Fixture(); assertTrue(f.store.write(GROUP, ALICE, KEY, 1, new Value(7)));
        assertEquals(7, f.read(ALICE).count); assertFalse(f.store.blocked(GROUP, ALICE, KEY));
        assertTrue(f.store.write(GROUP, ALICE, "unversioned", 1, new Unversioned()));
        assertTrue(f.raw(ALICE, "unversioned").contains("\"version\":1"));
    }
    @Test public void malformedPayloadKeepsLastGoodAndBlocksAutomaticReplacement()
    {
        Fixture f = new Fixture(); f.store.write(GROUP, ALICE, KEY, 1, new Value(7)); f.put(ALICE, KEY, "{unclosed");
        assertEquals(7, f.read(ALICE).count); assertEquals("{unclosed", f.raw(ALICE, KEY));
        assertFalse(f.store.write(GROUP, ALICE, KEY, 1, new Value(12)));
        assertTrue(f.store.blocked(GROUP, ALICE, KEY)); assertTrue(f.store.problems(ALICE).get(0).recoveryAvailable);
    }
    @Test public void futureVersionIsNeverDowngraded()
    {
        Fixture f = new Fixture(); String future = "{\"version\":9,\"count\":8}"; f.put(ALICE, KEY, future);
        assertEquals(0, f.read(ALICE).count); assertFalse(f.store.write(GROUP, ALICE, KEY, 1, new Value(3)));
        assertEquals(future, f.raw(ALICE, KEY)); assertFalse(f.store.problems(ALICE).get(0).recoveryAvailable);
    }
    @Test public void schemaInvalidDataAndInvalidVersionTypesAreRejected()
    {
        for (String raw : new String[] {"{\"version\":1,\"count\":-1}", "{\"version\":1.5}", "{\"version\":\"1\"}",
            "{\"version\":true}", "{\"version\":0}", "null"})
        {
            Fixture f = new Fixture(); f.put(ALICE, KEY, raw); assertEquals(raw, 0, f.read(ALICE).count);
            assertTrue(raw, f.store.blocked(GROUP, ALICE, KEY)); assertEquals(raw, f.raw(ALICE, KEY));
        }
    }
    @Test public void sameKeysInDifferentProfilesStayIndependent()
    {
        Fixture f = new Fixture(); f.store.write(GROUP, ALICE, KEY, 1, new Value(7)); f.store.write(GROUP, BOB, KEY, 1, new Value(11));
        f.put(ALICE, KEY, "broken"); assertEquals(7, f.read(ALICE).count); assertEquals(11, f.read(BOB).count);
        assertEquals(1, f.store.problems(ALICE).size()); assertTrue(f.store.problems(BOB).isEmpty());
        assertTrue(f.store.write(GROUP, BOB, KEY, 1, new Value(12))); assertEquals(12, f.read(BOB).count);
    }
    @Test public void explicitRecoveryRestoresGoodAndRetainsRejectedOriginal()
    {
        Fixture f = new Fixture(); f.store.write(GROUP, ALICE, KEY, 1, new Value(7)); f.put(ALICE, KEY, "damaged-original"); f.read(ALICE);
        assertTrue(f.store.recover(new RunewardenDataStore.Address(GROUP, ALICE, KEY)));
        assertEquals(7, f.read(ALICE).count); assertTrue(f.store.problems(ALICE).isEmpty());
        assertTrue(f.settings.get(Fixture.address(RunewardenDataStore.GROUP, null, RunewardenDataStore.KEY)).contains("damaged-original"));
        assertTrue(f.store.write(GROUP, ALICE, KEY, 1, new Value(8)));
    }
    @Test public void explicitResetAllowsNewDataWhileKeepingRejectedCopy()
    {
        Fixture f = new Fixture(); f.put(ALICE, KEY, "damaged-original"); f.read(ALICE);
        f.store.reset(new RunewardenDataStore.Address(GROUP, ALICE, KEY)); assertNull(f.raw(ALICE, KEY));
        assertTrue(f.settings.get(Fixture.address(RunewardenDataStore.GROUP, null, RunewardenDataStore.KEY)).contains("damaged-original"));
        assertTrue(f.store.write(GROUP, ALICE, KEY, 1, new Value(8))); assertEquals(8, f.read(ALICE).count);
    }
    @Test public void recoverySurvivesStoreRestart()
    {
        Fixture f = new Fixture(); f.store.write(GROUP, ALICE, KEY, 1, new Value(7)); f.put(ALICE, KEY, "corrupt");
        RunewardenDataStore restarted = new RunewardenDataStore(f.config, new Gson());
        Value value = restarted.read(GROUP, ALICE, KEY, 1, Value.class, v -> v.count >= 0, Value::new);
        assertEquals(7, value.count); assertTrue(restarted.problems(ALICE).get(0).recoveryAvailable);
    }
    @Test public void nestedPauseRestoresWritesAfterException()
    {
        Fixture f = new Fixture();
        try
        {
            f.store.pauseWrites(() -> { assertFalse(f.store.write(GROUP, ALICE, KEY, 1, new Value(1)));
                f.store.pauseWrites(() -> { assertFalse(f.store.write(GROUP, ALICE, KEY, 1, new Value(2))); }); throw new IllegalStateException("test"); });
            fail("exception expected");
        }
        catch (IllegalStateException expected) { assertEquals("test", expected.getMessage()); }
        assertNull(f.raw(ALICE, KEY)); assertTrue(f.store.write(GROUP, ALICE, KEY, 1, new Value(3)));
    }
    @Test public void oversizedOriginalIsPreservedAndOversizedReplacementRefused()
    {
        Fixture f = new Fixture(); String huge = "x".repeat(RunewardenDataStore.MAX_PAYLOAD + 1); f.put(ALICE, KEY, huge);
        assertEquals(0, f.read(ALICE).count); assertEquals(huge, f.raw(ALICE, KEY));
        assertFalse(f.store.write(GROUP, ALICE, KEY, 1, new Value(3)));
        Value large = new Value(1); large.text = huge; assertFalse(f.store.write(GROUP, BOB, KEY, 1, large)); assertNull(f.raw(BOB, KEY));
    }
    @Test public void foreignPluginAndInvalidProfileAddressesAreRejected()
    {
        for (String group : new String[] {"grounditems", "timers", RunewardenDataStore.GROUP, "runewarden\nforged"})
        {
            try { new RunewardenDataStore.Address(group, ALICE, KEY); fail(group); }
            catch (IllegalArgumentException expected) { }
        }
        try { new RunewardenDataStore.Address(GROUP, "alice", KEY); fail(); } catch (IllegalArgumentException expected) { }
        try { new RunewardenDataStore.Address(GROUP, ALICE, "key\nforged"); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void externallyFixedPayloadStillNeedsExplicitRecoveryOrResetBeforeWriting()
    {
        Fixture f = new Fixture(); f.put(ALICE, KEY, "corrupt"); f.read(ALICE); f.put(ALICE, KEY, "{\"version\":1,\"count\":9}");
        assertEquals(9, f.read(ALICE).count); assertFalse(f.store.write(GROUP, ALICE, KEY, 1, new Value(3)));
    }
    @Test public void oneBadBackupEntryDoesNotDiscardOtherGoodRecoveries()
    {
        Fixture f = new Fixture(); f.store.write(GROUP, ALICE, KEY, 1, new Value(7));
        JsonObject recovery = new JsonParser().parse(f.settings.get(Fixture.address(RunewardenDataStore.GROUP, null, RunewardenDataStore.KEY))).getAsJsonObject();
        JsonArray entries = recovery.getAsJsonArray("entries"); JsonObject invalid = new JsonObject();
        invalid.addProperty("group", GROUP); invalid.addProperty("profile", "not-rsprofile"); invalid.addProperty("key", "bad"); entries.add(invalid);
        f.settings.put(Fixture.address(RunewardenDataStore.GROUP, null, RunewardenDataStore.KEY), recovery.toString()); f.put(ALICE, KEY, "corrupt");
        RunewardenDataStore restarted = new RunewardenDataStore(f.config, new Gson());
        assertEquals(7, restarted.read(GROUP, ALICE, KEY, 1, Value.class, v -> v.count >= 0, Value::new).count);
    }
    @Test public void problemHistoryHasABoundedNumberOfEntries()
    {
        Fixture f = new Fixture();
        for (int i = 0; i < RunewardenDataStore.MAX_ENTRIES + 3; i++)
        { String key = "bad" + i; f.put(ALICE, key, "corrupt"); f.store.read(GROUP, ALICE, key, 1, Value.class, value -> true, Value::new); }
        assertTrue(f.store.problems(ALICE).size() <= RunewardenDataStore.MAX_ENTRIES);
        assertEquals("corrupt", f.raw(ALICE, "bad0"));
    }
    @Test public void deeplyNestedCorruptionIsBoundedBeforeParsingAndPreservesLastGood()
    {
        Fixture f=new Fixture();f.store.write(GROUP,ALICE,KEY,1,new Value(7));String nested="[".repeat(20000)+"0"+"]".repeat(20000);f.put(ALICE,KEY,nested);
        assertEquals(7,f.read(ALICE).count);assertEquals(nested,f.raw(ALICE,KEY));assertTrue(f.store.blocked(GROUP,ALICE,KEY));
        RunewardenDataStore.validateJson("{\"text\":\""+"[".repeat(100)+"\"}",1000);
    }
    static final class Fixture
    {
        final ConfigManager config = mock(ConfigManager.class); final Map<String, String> settings = new HashMap<>();
        final RunewardenDataStore store;
        Fixture()
        {
            when(config.getConfiguration(anyString(), anyString())).thenAnswer(call -> settings.get(address(call.getArgument(0), null, call.getArgument(1))));
            when(config.getConfiguration(anyString(), nullable(String.class), anyString())).thenAnswer(call -> settings.get(address(call.getArgument(0), call.getArgument(1), call.getArgument(2))));
            doAnswer(call -> { settings.put(address(call.getArgument(0), null, call.getArgument(1)), String.valueOf((Object) call.getArgument(2))); return null; })
                .when(config).setConfiguration(anyString(), anyString(), any());
            doAnswer(call -> { settings.put(address(call.getArgument(0), call.getArgument(1), call.getArgument(2)), String.valueOf((Object) call.getArgument(3))); return null; })
                .when(config).setConfiguration(anyString(), nullable(String.class), anyString(), any());
            doAnswer(call -> { settings.remove(address(call.getArgument(0), call.getArgument(1), call.getArgument(2))); return null; })
                .when(config).unsetConfiguration(anyString(), nullable(String.class), anyString());
            store = new RunewardenDataStore(config, new Gson());
        }
        static String address(String group, String profile, String key) { return group + "/" + profile + "/" + key; }
        void put(String profile, String key, String value) { settings.put(address(GROUP, profile, key), value); }
        String raw(String profile, String key) { return settings.get(address(GROUP, profile, key)); }
        Value read(String profile) { return store.read(GROUP, profile, KEY, 1, Value.class, value -> value.count >= 0, Value::new); }
    }
}
