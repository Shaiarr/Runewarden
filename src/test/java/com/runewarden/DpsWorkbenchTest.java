/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DpsWorkbenchTest
{
    @Test public void targetSearchEnablesRankedSavedEquipmentWhileOutsideCombat()
    {
        Fixture f = new Fixture(); f.tool.refreshNow(); assertFalse(f.tool.reading().current.supported);
        f.tool.selectTargetNow(8059); DpsComparisonTool.Reading result = f.tool.reading();
        assertTrue(result.current.reason, result.current.supported); assertEquals(3, result.ranking.size());
        assertEquals(2, result.plans.size());
        assertTrue(result.ranking.get(0).result.dps >= result.ranking.get(1).result.dps);
        assertTrue(result.ranking.get(1).result.dps >= result.ranking.get(2).result.dps);
        assertTrue(result.target.contains("Vorkath"));
        verify(f.player, atLeastOnce()).getInteracting();
    }
    @Test public void unownedGearCanBeExploredAndBudgetLabelsNeverClaimReadiness()
    {
        Fixture f = new Fixture(); f.tool.selectTargetNow(8059); f.tool.selectSetupNow("lance"); f.tool.budgetNow(100);
        DpsComparisonTool.Setup lance = selected(f.tool.reading(), "lance");
        assertTrue(lance.result.reason, lance.result.supported); assertFalse(lance.purchase.observedOwned);
        assertTrue(lance.purchase.ownershipComplete); assertEquals(Long.valueOf(1), lance.purchase.unobserved.get(22978));
        assertEquals(40_000_000L, lance.purchase.unobservedCost); assertTrue(lance.purchase.overBudget);
        assertFalse(f.tool.reading().comparisonStatus.contains("ready"));
    }
    @Test public void staleBankDoesNotBecomeProofThatAnItemIsAbsent()
    {
        Fixture f = new Fixture(); f.observed.set(f.ownership(false)); f.tool.selectTargetNow(8059);
        DpsComparisonTool.Setup lance = selected(f.tool.reading(), "lance");
        assertTrue(lance.result.supported); assertFalse(lance.purchase.ownershipComplete);
        assertEquals("Ownership incomplete · unobserved items", lance.purchase.label());
    }
    @Test public void sandboxItemEditsAreSeparateFromSavedSetsAndActualEquipment()
    {
        Fixture f = new Fixture(); f.tool.selectTargetNow(8059); f.tool.selectSetupNow("scimitar");
        String sandbox = f.tool.createSandboxNow("My dragon setup"); assertNotNull(sandbox);
        f.tool.replaceSandboxNow(sandbox, 3, 22978, 1);
        DpsComparisonTool.Setup saved = selected(f.tool.reading(), "scimitar"), copied = selected(f.tool.reading(), sandbox);
        assertEquals(Integer.valueOf(4587), saved.plan.equipment().get(3)); assertEquals(Integer.valueOf(22978), copied.plan.equipment().get(3));
        assertTrue(copied.sandbox); assertFalse(saved.sandbox); assertEquals(4151, f.base.gear[3].getId());
    }
    @Test public void explicitStyleAndSpellArePerSetAndDoNotChangeTheLiveStance()
    {
        Fixture f = new Fixture(); f.tool.selectTargetNow(8059); f.tool.selectSetupNow("scimitar");
        String sandbox = f.tool.createSandboxNow("Magic experiment"); f.tool.replaceSandboxNow(sandbox, 3, 1381, 1);
        f.tool.choiceNow(sandbox, "Spell · magic · Manual Cast", "Ice Barrage", null);
        DpsComparisonTool.Setup spell = selected(f.tool.reading(), sandbox);
        assertTrue(spell.result.reason, spell.result.supported); assertTrue(spell.result.mode.contains("Manual Cast"));
        assertTrue(spell.result.mechanics.stream().anyMatch(value -> value.contains("Planned ancient")));
        assertTrue(f.tool.reading().current.mode.contains("slash"));
        assertNull(selected(f.tool.reading(), "scimitar").spell);
    }
    @Test public void scenarioChangesAreManualAndRestoreTheExactPinnedBase()
    {
        Fixture f = new Fixture(); f.tool.selectTargetNow(8059); double baseline = f.tool.reading().current.dps;
        f.tool.targetScenarioNow(100, 0, 0); DpsComparisonTool.Reading scenario = f.tool.reading();
        assertEquals(Integer.valueOf(100), scenario.targetFacts.hitpoints); assertTrue(scenario.current.dps > baseline);
        f.tool.selectSetupNow("scimitar"); assertNotNull(f.tool.reading().comparison.ttkSeconds);
        f.tool.targetScenarioNow(null, null, null); assertEquals(baseline, f.tool.reading().current.dps, 1e-12);
        assertEquals(Integer.valueOf(750), f.base.references.target(8059).hitpoints);
        f.tool.selectTargetNow(11789); f.tool.targetScenarioNow(100, 0, 0); assertFalse(f.tool.reading().current.supported);
    }
    @Test public void sandboxAndTargetChoicesSurviveRestartOnlyForTheirOwningCharacter()
    {
        Fixture f = new Fixture(); f.tool.selectTargetNow(8059); String id = f.tool.createSandboxNow("Persistent set"); f.tool.budgetNow(1000);
        f.tool.stop(); f.tool.start(); f.tool.refreshNow();
        assertTrue(f.tool.reading().target.contains("Vorkath")); assertNotNull(selected(f.tool.reading(), id)); assertEquals(1000, f.tool.reading().budget);
        f.profile.set("rsprofile.test.other"); f.observed.set(f.ownership(true));
        assertTrue(f.tool.reading().setups.isEmpty()); f.tool.refreshNow();
        assertEquals("No target", f.tool.reading().target); assertFalse(f.tool.reading().plans.stream().anyMatch(plan -> plan.id.equals(id)));
        assertEquals(0, f.tool.reading().budget);
    }
    @Test public void staleQueuedRankButtonsCannotWriteToTheNextProfile() throws Exception
    {
        Fixture f = new Fixture(); f.tool.selectTargetNow(8059); AtomicReference<JPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { panel.set(f.tool.createPanel()); JButton rank = findButton(panel.get(), "dpsRank_lance"); assertNotNull(rank); rank.doClick(); });
        int callbacks = f.callbacks.size(); assertTrue(callbacks > 0); String before = f.saved.get("runewarden|rsprofile.test.one|dpsWorkbenchV1");
        f.profile.set("rsprofile.test.other"); f.observed.set(f.ownership(true)); f.tool.refreshNow();
        for (Runnable action : new ArrayList<>(f.callbacks)) action.run();
        assertEquals("No target", f.tool.reading().target); assertEquals(before, f.saved.get("runewarden|rsprofile.test.one|dpsWorkbenchV1"));
        assertNull(f.saved.get("runewarden|rsprofile.test.other|dpsWorkbenchV1"));
    }
    @Test public void corruptFutureCombatPlansArePreservedAndNeverOverwritten()
    {
        Fixture f = new Fixture(); String raw = "{\"version\":99,\"builds\":[]}";
        f.saved.put("runewarden|rsprofile.test.one|dpsWorkbenchV1", raw); f.tool.start(); f.tool.refreshNow(); f.tool.selectTargetNow(8059);
        assertEquals(raw, f.saved.get("runewarden|rsprofile.test.one|dpsWorkbenchV1"));
        assertTrue(f.tool.reading().status.contains("Recovery"));
    }
    @Test public void activeRankingAndSandboxStorageHaveExplicitBounds()
    {
        Fixture f = new Fixture(); f.tool.selectTargetNow(8059);
        for (int i=0;i<12;i++) assertNotNull(f.tool.createSandboxNow("Experiment " + i));
        assertNull(f.tool.createSandboxNow("Too many")); assertEquals(8, f.tool.reading().ranking.size());
        assertEquals(14, f.tool.reading().plans.size());
    }
    @Test public void exactTtkIsComputedForTheSelectedSetWithoutRepeatingItForEveryRankingMode()
    {
        Fixture f = new Fixture(); f.tool.selectTargetNow(8059); f.tool.selectSetupNow("scimitar");
        assertNotNull(selected(f.tool.reading(), "scimitar").result.ttkSeconds);
        assertNull(selected(f.tool.reading(), "lance").result.ttkSeconds);
        assertTrue(selected(f.tool.reading(), "lance").result.dps > 0);
    }
    @Test public void validatorsRejectUnboundedOrAmbiguousSavedInputs()
    {
        DpsWorkbenchPlan.Saved state = new DpsWorkbenchPlan.Saved(); assertTrue(DpsWorkbenchPlan.valid(state));
        state.hp = 0; assertFalse(DpsWorkbenchPlan.valid(state)); state.hp = null;
        state.builds.add(new LoadoutPlan("ambiguous", "Ambiguous", List.of(new LoadoutPlan.Requirement(Set.of(4151,4587),1,LoadoutPlan.Location.EQUIPMENT,3)),null));
        assertFalse(DpsWorkbenchPlan.valid(state)); state.builds.clear();
        DpsWorkbenchPlan.Choice choice = new DpsWorkbenchPlan.Choice("current"); choice.spell = "x\n"; state.choices.add(choice); assertFalse(DpsWorkbenchPlan.valid(state));
    }
    @Test public void invalidSlotReplacementAndTargetNumbersCannotEnterAComparison()
    {
        Fixture f = new Fixture(); f.tool.selectTargetNow(8059); String id = f.tool.createSandboxNow("Validate edits");
        try { f.tool.replaceSandboxNow(id, 0, 22978, 1); fail(); } catch (IllegalArgumentException expected) { }
        try { f.tool.targetScenarioNow(-1, null, null); fail(); } catch (IllegalArgumentException expected) { }
        assertEquals(Integer.valueOf(4587), selected(f.tool.reading(), id).plan.equipment().get(3));
        assertEquals(Integer.valueOf(750), f.tool.reading().targetFacts.hitpoints);
    }
    @Test public void sidebarRenderingAndSearchControlsDoNotReadTheNativeClient() throws Exception
    {
        Fixture f = new Fixture(); f.tool.selectTargetNow(8059); clearInvocations(f.base.client);
        SwingUtilities.invokeAndWait(f.tool::createPanel);
        verifyNoInteractions(f.base.client);
    }
    @Test public void unsavedSandboxNamesSurviveLiveInputRefreshes() throws Exception
    {
        Fixture f = new Fixture(); f.tool.selectTargetNow(8059); AtomicReference<JPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { panel.set(f.tool.createPanel()); findField(panel.get(), "New sandbox name").setText("Unfinished experiment"); });
        f.tool.budgetNow(5000); SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> assertEquals("Unfinished experiment",findField(panel.get(), "New sandbox name").getText()));
    }
    private static javax.swing.JTextField findField(Container container, String name)
    {
        for (Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JTextField && name.equals(((javax.swing.JTextField)child).getAccessibleContext().getAccessibleName())) return (javax.swing.JTextField)child;
            if (child instanceof Container) { javax.swing.JTextField found = findField((Container) child,name); if (found != null) return found; }
        }
        return null;
    }
    private static DpsComparisonTool.Setup selected(DpsComparisonTool.Reading reading, String id)
    { for (DpsComparisonTool.Setup setup : reading.setups) if (setup.id.equals(id)) return setup; fail("Missing " + id); return null; }
    private static JButton findButton(Container container, String name)
    {
        for (Component child : container.getComponents())
        { if (child instanceof JButton && name.equals(child.getName())) return (JButton) child; if (child instanceof Container) { JButton found = findButton((Container) child, name); if (found != null) return found; } } return null;
    }
    static final class Fixture
    {
        final DpsCalculatorTest.Fixture base = new DpsCalculatorTest.Fixture();
        final ConfigManager config = mock(ConfigManager.class); final ClientThread thread = mock(ClientThread.class);
        final Player player = mock(Player.class); final AtomicReference<String> profile = new AtomicReference<>("rsprofile.test.one");
        final AtomicReference<OwnershipLedger.View> observed = new AtomicReference<>();
        final Map<String,String> saved = new HashMap<>(); final List<Runnable> callbacks = new ArrayList<>(); final DpsComparisonTool tool;
        Fixture()
        {
            base.weapon(4151, "Abyssal whip", 82, 0, 82, 4);
            item(4587,"Dragon scimitar",ItemEquipmentStats.builder().slot(3).aslash(67).astab(8).str(66).aspeed(4).build(),60_000);
            item(22978,"Dragon hunter lance",ItemEquipmentStats.builder().slot(3).aslash(65).astab(85).str(70).aspeed(4).build(),40_000_000);
            item(1381,"Staff of air",ItemEquipmentStats.builder().slot(3).amagic(20).aspeed(5).build(),1500);
            when(base.client.getLocalPlayer()).thenReturn(player); when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
            when(config.getConfiguration(anyString(),anyString())).thenAnswer(call -> saved.get(call.getArgument(0)+"|"+call.getArgument(1)));
            when(config.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(call -> saved.get(call.getArgument(0)+"|"+call.getArgument(1)+"|"+call.getArgument(2)));
            doAnswer(call -> { saved.put(call.getArgument(0)+"|"+call.getArgument(1),call.getArgument(2)); return null; }).when(config).setConfiguration(anyString(),anyString(),any());
            doAnswer(call -> { saved.put(call.getArgument(0)+"|"+call.getArgument(1)+"|"+call.getArgument(2),call.getArgument(3)); return null; }).when(config).setConfiguration(anyString(),anyString(),anyString(),any());
            ItemContainer worn = mock(ItemContainer.class); when(worn.getItems()).thenAnswer(call -> base.gear);
            when(base.client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN)).thenReturn(worn);
            LoadoutPlan scimitar = new LoadoutPlan("scimitar","Dragon scimitar set",List.of(new LoadoutPlan.Requirement(Set.of(4587),1,LoadoutPlan.Location.EQUIPMENT,3)),null);
            LoadoutPlan lance = new LoadoutPlan("lance","Dragon hunter lance set",List.of(new LoadoutPlan.Requirement(Set.of(22978),1,LoadoutPlan.Location.EQUIPMENT,3)),null);
            observed.set(ownership(true));
            doAnswer(call -> { callbacks.add(call.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
            Gson gson = new Gson(); CombatSpellCatalog spells = new CombatSpellCatalog(gson);
            CombatCalculatorTool combat = new CombatCalculatorTool(base.client,thread,base.items,new MobKnowledgeCatalog(gson),spells,config);combat.start();
            tool = new DpsComparisonTool(base.client,thread,config,base.items,combat,base.references,observed::get,()->List.of(scimitar,lance),()->scimitar,spells,null,new RunewardenDataStore(config,gson));tool.start();
        }
        void item(int id,String name,ItemEquipmentStats stats,long price)
        {
            ItemComposition definition=mock(ItemComposition.class);when(definition.getName()).thenReturn(name);when(definition.isTradeable()).thenReturn(true);
            when(base.items.getItemComposition(id)).thenReturn(definition);when(base.items.getItemStats(id)).thenReturn(new ItemStats(true,0,0,stats));when(base.items.getItemPrice(id)).thenReturn(price);
        }
        OwnershipLedger.View ownership(boolean currentBank)
        {
            Map<OwnershipLedger.Storage,OwnershipLedger.Observation> observations=new EnumMap<>(OwnershipLedger.Storage.class);
            observations.put(OwnershipLedger.Storage.EQUIPMENT,new OwnershipLedger.Observation(Map.of(3,new OwnershipLedger.SlotItem(4151,1)),1,true));
            observations.put(OwnershipLedger.Storage.INVENTORY,new OwnershipLedger.Observation(Map.of(),1,true));
            observations.put(OwnershipLedger.Storage.BANK,new OwnershipLedger.Observation(Map.of(1,new OwnershipLedger.SlotItem(4587,1)),1,currentBank));
            return new OwnershipLedger.View(profile.get(),observations,0,Map.of(),false,false,Map.of());
        }
    }
}
