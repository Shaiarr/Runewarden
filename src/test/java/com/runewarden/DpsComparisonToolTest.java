/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DpsComparisonToolTest
{
    @Test public void currentNativeEquipmentAndSelectedObservedOwnedSetProduceAComparison()
    {
        Fixture f = new Fixture(); f.tool.refreshNow(); DpsComparisonTool.Reading reading = f.tool.reading();
        assertTrue(reading.current.reason, reading.current.supported); assertTrue(reading.target.contains("Vorkath"));
        assertNotNull(reading.comparison); assertTrue(reading.comparison.reason, reading.comparison.supported);
        assertEquals("Owned dragon scimitar", reading.comparisonName); assertEquals(1, reading.plans.size());
        assertTrue(reading.comparisonStatus.contains("best supported mode"));
    }

    @Test public void staleStorageIsTheoryEquipmentAndUnresolvedAlternativesStayUnrated()
    {
        Fixture f = new Fixture(); f.owned.set(f.view("a", false)); f.tool.refreshNow();
        assertTrue(f.tool.reading().current.supported); assertTrue(f.tool.reading().comparison.supported);
        assertTrue(f.tool.reading().comparisonStatus.contains("Ownership incomplete"));
        LoadoutPlan alternatives = new LoadoutPlan("unresolved", "Alternative weapon", List.of(
            new LoadoutPlan.Requirement(Set.of(4151, 4587), 1, LoadoutPlan.Location.EQUIPMENT, 3)), null);
        assertTrue(DpsComparisonTool.validateOwned(alternatives, f.view("a", true)).contains("alternatives"));
    }

    @Test public void changingProfileOrLoggingOutCannotExposeTheOldSavedSetOrKeptTarget()
    {
        Fixture f = new Fixture(); f.tool.refreshNow(); assertEquals("Owned dragon scimitar", f.tool.reading().comparisonName);
        f.profile.set("b"); assertTrue(f.tool.reading().plans.isEmpty());
        when(f.player.getInteracting()).thenReturn(null); f.tool.refreshNow();
        assertEquals("No target", f.tool.reading().target); assertTrue(f.tool.reading().plans.isEmpty());
        assertEquals("", f.tool.reading().comparisonName); assertNull(f.tool.reading().comparison);
        when(f.base.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); f.tool.refreshNow();
        assertFalse(f.tool.reading().current.supported); assertTrue(f.tool.reading().plans.isEmpty());
    }

    @Test public void delayedOldGenerationRefreshCannotReviveAStoppedOrPreviousCharacterPanel() throws Exception
    {
        Fixture f = new Fixture(); SwingUtilities.invokeAndWait(f.tool::createPanel);
        assertFalse(f.callbacks.isEmpty()); f.tool.stop();
        for (Runnable callback : new ArrayList<>(f.callbacks)) callback.run();
        assertFalse(f.tool.reading().current.supported); assertTrue(f.tool.reading().plans.isEmpty());
    }

    @Test public void candidateMetadataLoadingIsRetriedAndTheNativePanelUsesTheFullSidebarWidth() throws Exception
    {
        Fixture f = new Fixture(); ItemStats expected = f.base.items.getItemStats(4587);
        when(f.base.items.getItemStats(4587)).thenReturn(null); f.tool.refreshNow(); assertFalse(f.tool.reading().comparison.supported);
        when(f.base.items.getItemStats(4587)).thenReturn(expected); f.tool.refreshNow(); assertTrue(f.tool.reading().comparison.supported);
        AtomicReference<JPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { JPanel value = f.tool.createPanel(); value.setSize(206, 1800); layout(value); panel.set(value); });
        JLabel number = find(panel.get(), "normalDpsValue"); assertNotNull(number);
        assertTrue(number.getFont().getSize() >= 28); assertTrue(number.getWidth() >= 150);
    }

    private static void layout(Container container)
    { container.doLayout(); for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child); }
    private static JLabel find(Container container, String name)
    {
        for (Component child : container.getComponents())
        { if (child instanceof JLabel && name.equals(child.getName())) return (JLabel) child; if (child instanceof Container) { JLabel found = find((Container) child, name); if (found != null) return found; } }
        return null;
    }
    private static final class Fixture
    {
        final DpsCalculatorTest.Fixture base = new DpsCalculatorTest.Fixture();
        final ConfigManager config = mock(ConfigManager.class); final ClientThread thread = mock(ClientThread.class);
        final Player player = mock(Player.class); final AtomicReference<String> profile = new AtomicReference<>("a");
        final AtomicReference<OwnershipLedger.View> owned = new AtomicReference<>();
        final List<Runnable> callbacks = new ArrayList<>(); final DpsComparisonTool tool;
        Fixture()
        {
            base.weapon(4151, "Abyssal whip", 82, 0, 82, 4);
            ItemComposition scimitar = mock(ItemComposition.class); when(scimitar.getName()).thenReturn("Dragon scimitar");
            when(base.items.getItemComposition(4587)).thenReturn(scimitar);
            when(base.items.getItemStats(4587)).thenReturn(new ItemStats(true, 0, 0,
                ItemEquipmentStats.builder().slot(3).aslash(67).astab(8).str(66).aspeed(4).build()));
            when(base.client.getLocalPlayer()).thenReturn(player); when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
            NPC npc = mock(NPC.class); NPCComposition composition = mock(NPCComposition.class);
            when(composition.getId()).thenReturn(8059); when(npc.getTransformedComposition()).thenReturn(composition); when(player.getInteracting()).thenReturn(npc);
            ItemContainer worn = mock(ItemContainer.class); when(worn.getItems()).thenReturn(base.gear);
            when(base.client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN)).thenReturn(worn);
            LoadoutPlan plan = new LoadoutPlan("owned", "Owned dragon scimitar", List.of(
                new LoadoutPlan.Requirement(Set.of(4587), 1, LoadoutPlan.Location.EQUIPMENT, 3)), null);
            owned.set(view("a", true));
            doAnswer(call -> { callbacks.add(call.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
            Gson gson = new Gson(); CombatCalculatorTool combat = new CombatCalculatorTool(base.client, thread, base.items,
                new MobKnowledgeCatalog(gson), new CombatSpellCatalog(gson), config); combat.start();
            tool = new DpsComparisonTool(base.client, thread, config, base.items, combat, base.references,
                owned::get, () -> List.of(plan), () -> plan); tool.start();
        }
        OwnershipLedger.View view(String character, boolean currentBank)
        {
            Map<OwnershipLedger.Storage, OwnershipLedger.Observation> observations = new EnumMap<>(OwnershipLedger.Storage.class);
            observations.put(OwnershipLedger.Storage.EQUIPMENT, new OwnershipLedger.Observation(Map.of(3, new OwnershipLedger.SlotItem(4151, 1)), 1, true));
            observations.put(OwnershipLedger.Storage.BANK, new OwnershipLedger.Observation(Map.of(2, new OwnershipLedger.SlotItem(4587, 1)), 1, currentBank));
            return new OwnershipLedger.View(character, observations, 0, Collections.emptyMap(), false, false, Collections.emptyMap());
        }
    }
}
