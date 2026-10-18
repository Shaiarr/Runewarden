/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Player;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MusicUnlockTest
{
    private static GuideCatalog guides;
    @BeforeClass public static void guides() { guides = new GuideCatalog(new Gson()); }
    private static MusicUnlockCatalog catalog() { return new MusicUnlockCatalog(new Gson(), guides); }

    @Test public void completeBundledMusicFactsHaveUniqueNamesSourcesAndRoutes()
    {
        MusicUnlockCatalog catalog = catalog(); assertEquals(853, catalog.bundledCount());
        HashSet<String> names = new HashSet<>(); int located = 0;
        for (MusicUnlockCatalog.Track track : catalog.tracks())
        {
            assertTrue(names.add(MusicUnlockCatalog.normalize(track.name))); assertFalse(track.nativeDefinition);
            assertTrue(track.source.startsWith("https://oldschool.runescape.wiki/"));
            assertNotNull(track.unlock); assertFalse(track.unlock.trim().isEmpty());
            if (track.routes.stream().anyMatch(route -> route.point() != null)) located++;
        }
        assertEquals(719, located); assertFalse(catalog.neededQuests().isEmpty());
        assertNotNull(catalog.guide("DRUIDIC_RITUAL"));
    }

    @Test public void unlockBitsUseNativeBanksIncludingHighBitAndNewBanks()
    {
        int[] banks = new int[27]; banks[0] = Integer.MIN_VALUE; banks[25] = 4; banks[26] = 1;
        assertEquals(1, MusicUnlockCatalog.unlocked(track(0, 1, 31), banks));
        assertEquals(0, MusicUnlockCatalog.unlocked(track(1, 1, 0), banks));
        assertEquals(1, MusicUnlockCatalog.unlocked(track(2, 26, 2), banks));
        assertEquals(1, MusicUnlockCatalog.unlocked(track(3, 27, 0), banks));
        assertEquals(1, MusicUnlockCatalog.unlocked(track(4, 0, -1), banks));
        assertEquals(-1, MusicUnlockCatalog.unlocked(track(5, 28, -1), banks));
        assertEquals(-1, MusicUnlockCatalog.unlocked(track(6, 1, 32), banks));
        assertEquals(-1, MusicUnlockCatalog.unlocked(track(7, Integer.MIN_VALUE, -1), banks));
        assertEquals(-1, MusicUnlockCatalog.unlocked(track(-1, 1, 0), banks));
    }

    @Test public void publicNativeIndexIsBoundedAndMergesFactsWithoutDuplicateNames()
    {
        MusicUnlockCatalog catalog = catalog(); Client client = nativeClient(); List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) rows.add(i); when(client.getDBTableRows(DBTableID.Music.ID)).thenReturn(rows);
        when(client.getDBTableField(anyInt(), eq(DBTableID.Music.COL_DISPLAYNAME), eq(0))).thenAnswer(call ->
            new Object[]{call.<Integer>getArgument(0) == 0 ? "Adventure" : "Future track " + call.<Integer>getArgument(0)});
        when(client.getDBTableField(eq(99), eq(DBTableID.Music.COL_HIDDEN), eq(0))).thenReturn(new Object[]{1});
        catalog.readNativeChunk(client); assertTrue(catalog.indexedRows() <= 32); assertFalse(catalog.nativeComplete());
        for (int i = 0; i < 200 && !catalog.nativeComplete(); i++) catalog.readNativeChunk(client);
        assertTrue(catalog.nativeComplete()); assertEquals(100, catalog.indexedRows());
        assertEquals(1, catalog.tracks().stream().filter(track -> "Adventure".equals(track.name)).count());
        MusicUnlockCatalog.Track adventure = catalog.tracks().stream().filter(track -> "Adventure".equals(track.name)).findFirst().get();
        assertTrue(adventure.nativeDefinition); assertEquals(0, adventure.id); assertFalse(adventure.unlock.isEmpty());
        assertFalse(catalog.tracks().stream().anyMatch(track -> "Future track 99".equals(track.name)));
        MusicUnlockCatalog.Track future = catalog.tracks().stream().filter(track -> "Future track 1".equals(track.name)).findFirst().get();
        assertEquals("Native unlock hint", future.unlock); assertTrue(future.source.contains("Special:Search"));
        int calls = mockingDetails(client).getInvocations().size(); catalog.readNativeChunk(client);
        assertEquals("Completed metadata performs no more client reads", calls, mockingDetails(client).getInvocations().size());
    }

    @Test public void missingNativeVariablesRemainUnknownAndUnavailableRowsStopRetrying()
    {
        Client client = nativeClient(); when(client.getDBTableRows(DBTableID.Music.ID)).thenReturn(Arrays.asList(10, 11));
        when(client.getDBTableField(eq(10), eq(DBTableID.Music.COL_VARIABLE), eq(0))).thenReturn(null);
        when(client.getDBTableField(eq(11), eq(DBTableID.Music.COL_DISPLAYNAME), eq(0))).thenReturn(null);
        MusicUnlockCatalog catalog = catalog();
        for (int i = 0; i < 20 && !catalog.nativeComplete(); i++) catalog.readNativeChunk(client);
        assertTrue(catalog.nativeComplete()); assertEquals(2, catalog.indexedRows());
        MusicUnlockCatalog.Track known = catalog.tracks().stream().filter(track -> track.id == 10).findFirst().get();
        assertEquals(-1, MusicUnlockCatalog.unlocked(known, new int[27]));
        verify(client, times(5)).getDBTableField(11, DBTableID.Music.COL_DISPLAYNAME, 0);
    }

    @Test public void hiddenTrackerBuildsBaselineAndCelebratesOnlyNewUnlocks()
    {
        Fixture fixture = new Fixture(0); fixture.tick(7);
        assertTrue(fixture.catalog.nativeComplete()); assertTrue(fixture.events.isEmpty());
        verify(fixture.client, never()).getItemContainer(any(InventoryID.class));
        verify(fixture.client, never()).getRealSkillLevel(any(Skill.class));
        fixture.bits.set(1); fixture.tick(1); assertEquals(1, fixture.events.size()); assertEquals("Adventure", fixture.events.get(0).name);
        fixture.tick(5); assertEquals(1, fixture.events.size());
        fixture.profile.set("account-B"); fixture.tick(7); assertEquals(1, fixture.events.size());
        fixture.tool.clear(); fixture.tick(7); assertEquals(1, fixture.events.size());
        fixture.tool.stop(); fixture.bits.set(0); fixture.tick(1); fixture.bits.set(1); fixture.tick(1);
        assertEquals(1, fixture.events.size());
    }

    @Test public void initialUnlockedTracksAndReconnectProgressNeverCelebrate()
    {
        Fixture fixture = new Fixture(1); fixture.tick(7); assertTrue(fixture.events.isEmpty());
        fixture.bits.set(0); fixture.tick(1); when(fixture.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); fixture.tick(1);
        fixture.bits.set(1); when(fixture.client.getGameState()).thenReturn(GameState.LOGGED_IN); fixture.tick(7);
        assertTrue(fixture.events.isEmpty());
    }

    @Test public void routesCheckSourcedQuestLevelsAndCarriedItemsWithoutGuessing()
    {
        MusicUnlockCatalog catalog = catalog(); MusicUnlockCatalog.Route route = new MusicUnlockCatalog.Route(); route.quest = "DRUIDIC_RITUAL";
        MusicUnlockCatalog.Level level = new MusicUnlockCatalog.Level(); level.skill = "Woodcutting"; level.level = 30; route.levels.add(level);
        MusicUnlockCatalog.RequiredItem item = new MusicUnlockCatalog.RequiredItem(); item.id = 952; item.quantity = 1; item.name = "Spade"; route.items.add(item);
        Map<String, QuestState> quests = new HashMap<>(); Map<Skill, Integer> levels = new EnumMap<>(Skill.class); Map<Integer, Integer> carried = new HashMap<>();
        assertTrue(catalog.access(route, quests, levels, carried, false).unknown);
        quests.put(route.quest, QuestState.IN_PROGRESS); levels.put(Skill.WOODCUTTING, 29);
        assertTrue(catalog.access(route, quests, levels, carried, true).blocked);
        quests.put(route.quest, QuestState.FINISHED); levels.put(Skill.WOODCUTTING, 30); carried.put(952, 1);
        assertTrue(catalog.access(route, quests, levels, carried, true).checked());
        item.id = 0; item.name = "PICKAXES";
        assertTrue("An unresolved item family is never treated as owned", catalog.access(route, quests, levels, carried, true).unknown);
    }

    @Test public void nearbyStopsAreBoundedOrderedAndRequireObservedAccess()
    {
        MusicUnlockCatalog catalog = catalog(); List<MusicUnlockCatalog.Track> tracks = new ArrayList<>(); Map<Integer, Integer> states = new HashMap<>();
        for (int i = 0; i < 12; i++)
        {
            MusicUnlockCatalog.Fact fact = new MusicUnlockCatalog.Fact(); fact.name = "Test " + i; fact.kind = i == 0 ? "QUEST" : "NORMAL";
            MusicUnlockCatalog.Route route = new MusicUnlockCatalog.Route(); route.x = 3200 + i; route.y = 3200; route.plane = 0; fact.routes.add(route);
            tracks.add(new MusicUnlockCatalog.Track(i, fact.name, "", 1, i, false, fact)); states.put(i, 0);
        }
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        assertTrue(catalog.nearby(tracks, states, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), player, false).isEmpty());
        List<MusicUnlockCatalog.Stop> stops = catalog.nearby(tracks, states, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), player, true);
        assertEquals(8, stops.size()); assertEquals(1, stops.get(0).distance); assertEquals(8, stops.get(7).distance);
        assertTrue(stops.stream().noneMatch(stop -> stop.track.id == 0));
    }

    @Test public void musicListAndDetailFit206PixelsAndActualRuneLiteWrapper() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            MusicUnlockCatalog catalog = catalog(); MusicUnlockTool tool = new MusicUnlockTool(mock(Client.class), mock(ConfigManager.class), catalog);
            JPanel panel = tool.createPanel(); JComboBox<?> mode = named(panel, "music-unlocks.mode", JComboBox.class); mode.setSelectedIndex(2);
            String longName = catalog.tracks().stream().max(java.util.Comparator.comparingInt(track -> track.name.length())).get().name;
            named(panel, "music-unlocks.search", JTextField.class).setText(longName); mode.setSelectedIndex(2);
            panel.setSize(206, 18000); layout(panel); assertFits(panel);
            JButton details = descendants(panel).stream().filter(c -> c instanceof JButton).map(c -> (JButton) c)
                .filter(button -> "Unlock details".equals(button.getText())).findFirst().get(); details.doClick(); layout(panel); assertFits(panel);
            assertTrue(descendants(panel).stream().anyMatch(c -> c instanceof GuideText && longName.equals(((GuideText) c).getText())));
            PluginPanel host = new PluginPanel() { }; host.setLayout(new java.awt.BorderLayout()); host.setBorder(javax.swing.BorderFactory.createEmptyBorder(8,8,8,8)); host.add(panel);
            JComponent wrapped = host.getWrappedPanel(); wrapped.setSize(242, 18000); layout(wrapped); assertFits(panel); assertTrue(panel.getWidth() >= 190);
            tool.stop();
        });
    }

    private static MusicUnlockCatalog.Track track(int id, int bank, int bit)
    { return new MusicUnlockCatalog.Track(id, "Test", "", bank, bit, false, null); }
    private static Client nativeClient()
    {
        Client client = mock(Client.class);
        when(client.getDBTableRows(DBTableID.Music.ID)).thenReturn(Collections.singletonList(10000));
        when(client.getDBTableField(anyInt(), anyInt(), eq(0))).thenAnswer(call -> {
            int column = call.getArgument(1);
            if (column == DBTableID.Music.COL_DISPLAYNAME) return new Object[]{"Adventure"};
            if (column == DBTableID.Music.COL_UNLOCKHINT) return new Object[]{"Native unlock hint"};
            if (column == DBTableID.Music.COL_VARIABLE) return new Object[]{1, 0};
            return new Object[]{0};
        }); return client;
    }
    private static final class Fixture
    {
        final Client client = nativeClient(); final ConfigManager config = mock(ConfigManager.class);
        final AtomicInteger bits; final AtomicReference<String> profile = new AtomicReference<>("account-A");
        final MusicUnlockCatalog catalog = catalog(); final MusicUnlockTool tool; final List<MusicUnlockCatalog.Track> events = new ArrayList<>();
        Fixture(int initial)
        {
            bits = new AtomicInteger(initial); when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(client.getVarpValue(VarPlayerID.MUSICMULTI_1)).thenAnswer(call -> bits.get());
            tool = new MusicUnlockTool(client, config, catalog); tool.setUnlockAction(events::add); tool.start();
        }
        void tick(int count) { for (int i = 0; i < count; i++) tool.onGameTick(); }
    }
    private static <T> T named(Container root, String name, Class<T> type)
    { for (Component child : descendants(root)) if (name.equals(child.getName())) return type.cast(child); throw new AssertionError(name); }
    private static List<Component> descendants(Container root)
    {
        List<Component> result = new ArrayList<>(); for (Component child : root.getComponents())
        { result.add(child); if (child instanceof Container) result.addAll(descendants((Container) child)); } return result;
    }
    private static void layout(Container root) { for (int i = 0; i < 8; i++) deepLayout(root); }
    private static void deepLayout(Container root)
    { root.invalidate(); root.doLayout(); for (Component child : root.getComponents()) if (child instanceof Container) deepLayout((Container) child); }
    private static void assertFits(Container root)
    {
        for (Component child : descendants(root))
        {
            if (!(child instanceof JButton || child instanceof JComboBox || child instanceof JTextField || child instanceof GuideText)) continue;
            boolean visible = true; for (Component parent = child; parent != root; parent = parent.getParent())
                if (parent == null || !parent.isVisible()) { visible = false; break; }
            if (!visible || child.getParent().getWidth() <= 0) continue;
            assertTrue(child.getClass().getSimpleName() + " clipped left", child.getX() >= 0);
            assertTrue(child.getClass().getSimpleName() + " exceeds parent", child.getX() + child.getWidth() <= child.getParent().getWidth());
            if (child instanceof GuideText)
            {
                assertTrue("Text keeps usable width", child.getWidth() >= child.getParent().getWidth() - 22);
                assertTrue("Wrapped text keeps full height", child.getHeight() >= child.getPreferredSize().height);
            }
        }
    }
}
