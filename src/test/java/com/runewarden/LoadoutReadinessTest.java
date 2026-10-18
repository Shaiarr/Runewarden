/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import net.runelite.api.Item;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;
import static org.junit.Assert.*;

public class LoadoutReadinessTest
{
    private static LoadoutPlan.Requirement need(int id, long count, LoadoutPlan.Location where, int slot)
    { return new LoadoutPlan.Requirement(Collections.singleton(id), count, where, slot); }
    @Test public void exactEquipmentSlotAndInventoryQuantitiesMustMatch()
    {
        OwnershipLedgerTest.Fixture fixture = new OwnershipLedgerTest.Fixture();
        Item[] worn = new Item[14]; Arrays.fill(worn, new Item(-1, 0)); worn[3] = new Item(4151, 1);
        fixture.container(InventoryID.WORN, worn); fixture.container(InventoryID.INV, new Item[]{new Item(385, 8)}); fixture.ledger.onGameTick();
        LoadoutPlan plan = new LoadoutPlan("one", "Boss", Arrays.asList(need(4151, 1, LoadoutPlan.Location.EQUIPMENT, 3), need(385, 10, LoadoutPlan.Location.INVENTORY, -1)), 0);
        LoadoutReadiness check = LoadoutReadiness.check(plan, fixture.ledger.snapshot());
        assertFalse(check.ready); assertTrue(check.lines.get(0).ready()); assertEquals(2, check.lines.get(1).missing);
        LoadoutPlan wrongSlot = new LoadoutPlan("one", "Boss", Collections.singletonList(need(4151, 1, LoadoutPlan.Location.EQUIPMENT, 5)), 0);
        assertFalse(LoadoutReadiness.check(wrongSlot, fixture.ledger.snapshot()).ready);
    }
    @Test public void bankSuppliesNeverCountAsCarriedAndUnknownBankNeverMeansZeroOwned()
    {
        OwnershipLedgerTest.Fixture fixture = new OwnershipLedgerTest.Fixture(); fixture.ledger.onGameTick();
        LoadoutPlan plan = new LoadoutPlan("one", "Supplies", Collections.singletonList(need(385, 10, LoadoutPlan.Location.INVENTORY, -1)), null);
        LoadoutReadiness first = LoadoutReadiness.check(plan, fixture.ledger.snapshot()); assertFalse(first.lines.get(0).bankKnown);
        fixture.bank(new Item[]{new Item(385, 100)}); fixture.ledger.onGameTick();
        LoadoutReadiness second = LoadoutReadiness.check(plan, fixture.ledger.snapshot());
        assertEquals(100, second.lines.get(0).banked); assertEquals(10, second.lines.get(0).missing); assertFalse(second.ready);
    }
    @Test public void alternativesAreExplicitAndRepeatedRequirementsCannotReuseOneStack()
    {
        OwnershipLedgerTest.Fixture fixture = new OwnershipLedgerTest.Fixture(); fixture.container(InventoryID.INV, new Item[]{new Item(385, 8), new Item(379, 2)}); fixture.ledger.onGameTick();
        LoadoutPlan.Requirement food = new LoadoutPlan.Requirement(new LinkedHashSet<>(Arrays.asList(385, 379)), 10, LoadoutPlan.Location.INVENTORY, -1);
        LoadoutPlan plan = new LoadoutPlan("one", "Supplies", Arrays.asList(food, need(385, 1, LoadoutPlan.Location.INVENTORY, -1)), null);
        LoadoutReadiness check = LoadoutReadiness.check(plan, fixture.ledger.snapshot()); assertTrue(check.lines.get(0).ready()); assertEquals(1, check.lines.get(1).missing);
    }
    @Test public void spellbookMismatchPreventsDepartureReady()
    {
        OwnershipLedgerTest.Fixture fixture = new OwnershipLedgerTest.Fixture(); fixture.ledger.onGameTick();
        LoadoutPlan plan = new LoadoutPlan("one", "Ancients", Collections.emptyList(), 1);
        assertFalse(LoadoutReadiness.check(plan, fixture.ledger.snapshot()).ready);
        assertTrue(LoadoutReadiness.check(new LoadoutPlan("two", "Any", Collections.emptyList(), null), fixture.ledger.snapshot()).ready);
    }
    @Test public void modelRejectsUnboundedQuantitiesNullIdsAndDuplicateSlots()
    {
        reject(() -> need(385, 0, LoadoutPlan.Location.INVENTORY, -1));
        reject(() -> need(385, Long.MAX_VALUE, LoadoutPlan.Location.INVENTORY, -1));
        reject(() -> need(-1, 1, LoadoutPlan.Location.INVENTORY, -1));
        reject(() -> new LoadoutPlan("one", "Double", Arrays.asList(need(4151, 1, LoadoutPlan.Location.EQUIPMENT, 3), need(4152, 1, LoadoutPlan.Location.EQUIPMENT, 3)), null));
    }
    @Test public void candidateEquipmentOmitsAlternativesRatherThanGuessing()
    {
        LoadoutPlan.Requirement alternative = new LoadoutPlan.Requirement(new LinkedHashSet<>(Arrays.asList(4151, 4152)), 1, LoadoutPlan.Location.EQUIPMENT, 3);
        LoadoutPlan plan = new LoadoutPlan("one", "Candidates", Arrays.asList(alternative, need(1163, 1, LoadoutPlan.Location.EQUIPMENT, 0)), null);
        assertFalse(plan.equipment().containsKey(3)); assertEquals(Integer.valueOf(1163), plan.equipment().get(0));
    }
    @Test public void unknownMandatoryChecksCannotProduceReadyEvenWithAllItemsPacked()
    {
        OwnershipLedgerTest.Fixture f = new OwnershipLedgerTest.Fixture(); f.ledger.onGameTick();
        LoadoutPlan.DepartureCheck charge = new LoadoutPlan.DepartureCheck("charges","Weapon charged",LoadoutPlan.CheckKind.CHARGES,java.util.Set.of(4151),100,null);
        LoadoutPlan plan = new LoadoutPlan("one","Boss",java.util.List.of(),null,java.util.List.of(charge));
        LoadoutReadiness result = LoadoutReadiness.check(plan,f.ledger.snapshot());
        assertTrue(result.itemsReady); assertFalse(result.ready); assertEquals(LoadoutReadiness.CheckState.UNKNOWN,result.checks.get(0).state);
    }
    @Test public void minimumChargePoolsCountEncodedCarriedVariantsAndNeverBankStock()
    {
        OwnershipLedgerTest.Fixture f = new OwnershipLedgerTest.Fixture();
        f.container(InventoryID.INV,new Item[]{new Item(1712,1),new Item(1710,2)});f.container(InventoryID.WORN,new Item[0]);f.bank(new Item[]{new Item(1712,100)});f.ledger.onGameTick();
        LoadoutPlan.DepartureCheck charge = new LoadoutPlan.DepartureCheck("glory","Glory teleports",LoadoutPlan.CheckKind.CHARGES,java.util.Set.of(1712,1710),10,null);
        LoadoutPlan plan = new LoadoutPlan("one","Boss",java.util.List.of(),null,java.util.List.of(charge));
        LoadoutReadiness.CheckFacts facts = new LoadoutReadiness.CheckFacts(java.util.Map.of(),java.util.Map.of(1712,4,1710,3),null,null,null);
        LoadoutReadiness result = LoadoutReadiness.check(plan,f.ledger.snapshot(),facts);
        assertTrue(result.ready);assertEquals(10,result.checks.get(0).observed);
        f.container(InventoryID.INV,new Item[]{new Item(1710,1)});f.ledger.onGameTick();
        result=LoadoutReadiness.check(plan,f.ledger.snapshot(),facts);assertFalse(result.ready);assertEquals(3,result.checks.get(0).observed);assertEquals(LoadoutReadiness.CheckState.MISSING,result.checks.get(0).state);
    }
    @Test public void effectFactsDistinguishInactiveFromDisabledOrUnknown()
    {
        OwnershipLedgerTest.Fixture f = new OwnershipLedgerTest.Fixture();f.ledger.onGameTick();
        LoadoutPlan.DepartureCheck effect = new LoadoutPlan.DepartureCheck("fire","Antifire active",LoadoutPlan.CheckKind.EFFECT,java.util.Set.of(),0,"antifire");
        LoadoutPlan plan = new LoadoutPlan("one","Dragon",java.util.List.of(),null,java.util.List.of(effect));
        assertEquals(LoadoutReadiness.CheckState.UNKNOWN,LoadoutReadiness.check(plan,f.ledger.snapshot()).checks.get(0).state);
        LoadoutReadiness.CheckFacts inactive = new LoadoutReadiness.CheckFacts(null,null,java.util.Set.of(),java.util.Set.of("antifire"),null);
        assertEquals(LoadoutReadiness.CheckState.MISSING,LoadoutReadiness.check(plan,f.ledger.snapshot(),inactive).checks.get(0).state);
        LoadoutReadiness.CheckFacts active = new LoadoutReadiness.CheckFacts(null,null,java.util.Set.of("antifire"),java.util.Set.of("antifire"),null);
        assertTrue(LoadoutReadiness.check(plan,f.ledger.snapshot(),active).ready);
    }
    @Test public void manualChecksNeedExplicitConfirmationAndDoNotBorrowOtherCheckIds()
    {
        OwnershipLedgerTest.Fixture f = new OwnershipLedgerTest.Fixture();f.ledger.onGameTick();
        LoadoutPlan.DepartureCheck manual = new LoadoutPlan.DepartureCheck("blowpipe","Check blowpipe load",LoadoutPlan.CheckKind.MANUAL,null,0,null);
        LoadoutPlan plan = new LoadoutPlan("one","Boss",java.util.List.of(),null,java.util.List.of(manual));
        assertFalse(LoadoutReadiness.check(plan,f.ledger.snapshot(),new LoadoutReadiness.CheckFacts(null,null,null,null,java.util.Set.of("other"))).ready);
        assertTrue(LoadoutReadiness.check(plan,f.ledger.snapshot(),new LoadoutReadiness.CheckFacts(null,null,null,null,java.util.Set.of("blowpipe"))).ready);
    }
    @Test public void malformedAndDuplicateDepartureChecksAreRejectedAndLegacyPlansUpgradeCleanly()
    {
        reject(() -> new LoadoutPlan.DepartureCheck("x","Bad",LoadoutPlan.CheckKind.CHARGES,java.util.Set.of(),1,null));
        reject(() -> new LoadoutPlan.DepartureCheck("x","Bad",LoadoutPlan.CheckKind.EFFECT,java.util.Set.of(),1,"antifire"));
        LoadoutPlan.DepartureCheck manual = new LoadoutPlan.DepartureCheck("x","Manual",LoadoutPlan.CheckKind.MANUAL,null,0,null);
        reject(() -> new LoadoutPlan("one","Bad",java.util.List.of(),null,java.util.List.of(manual,manual)));
        LoadoutPlan old = new com.google.gson.Gson().fromJson("{\"id\":\"old\",\"name\":\"Old\",\"requirements\":[]}",LoadoutPlan.class);
        assertTrue(old.checked().checks().isEmpty());
    }
    private static void reject(Runnable work) { try { work.run(); fail("Rejected input expected"); } catch (IllegalArgumentException expected) { } }
}
