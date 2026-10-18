/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.EnumComposition;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ParamID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SupplyAwarenessToolTest
{
    @Test public void pinned647ExactItemStatesIncludePotionsAndTeleportsButNoInventedWeaponCharges()
    {
        Fixture f = new Fixture(); Map<Integer,Integer> facts = f.tool.supportedItemCharges();
        assertEquals(647,facts.size()); assertEquals(Integer.valueOf(4),facts.get(3024)); assertEquals(Integer.valueOf(1),facts.get(3030));
        assertEquals(Integer.valueOf(4),facts.get(1712)); assertEquals(Integer.valueOf(3),facts.get(1710));
        assertFalse(facts.containsKey(12926)); assertFalse(facts.containsKey(22323)); assertFalse(facts.containsKey(28955));
        try { facts.put(12926,1000); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void mixedDoseVariantsPoolForTheHudButKeepExactIdTotalsForReadiness()
    {
        Fixture f = new Fixture(); f.inventory(new Item(3024,2),new Item(3026,1)); f.tick();
        SupplyAwarenessTool.Row row = f.kind("POTION"); assertEquals(11,row.amount); assertEquals("Super restore",row.name); assertEquals("doses",row.unit);
        assertEquals(Set.of(3024,3026),row.variants); assertFalse(row.low());
        assertEquals(Long.valueOf(8),f.tool.chargeTotals().get(3024)); assertEquals(Long.valueOf(3),f.tool.chargeTotals().get(3026));
        assertTrue(f.tool.hudRows().isEmpty());
    }
    @Test public void bankStockNeverCountsAsCurrentlyAvailableCharges()
    {
        Fixture f = new Fixture(); f.inventory(new Item(1710,1)); f.nativeState.bank(new Item[]{new Item(1712,100)}); f.tick();
        assertEquals(3,f.kind("TELEPORT").amount); assertEquals("teleports",f.kind("TELEPORT").unit);
        assertFalse(f.tool.chargeTotals().containsKey(1712));
    }
    @Test public void stackableThrownWeaponsTakePrecedenceAndBlessingsNeverMasqueradeAsAmmo()
    {
        Fixture f = new Fixture(); f.item(892,"Rune arrow",true); f.item(12934,"Zulrah's scales",true); f.item(11889,"Unholy blessing",false);
        f.worn(13,11889,1); f.tick(); assertFalse(f.tool.rows().stream().anyMatch(row -> "AMMUNITION".equals(row.kind)));
        f.worn(13,892,250);f.tick();assertEquals(250,f.kind("AMMUNITION").amount);
        f.item(806,"Bronze dart",true);f.worn(3,806,500);f.tick();assertEquals(806,f.kind("AMMUNITION").itemId);assertEquals(500,f.kind("AMMUNITION").amount);
    }
    @Test public void quiverAndPouchUseObservedNativeSourcesAndDisappearWhenUnavailable()
    {
        Fixture f = new Fixture(); f.item(28955,"Blessed dizana's quiver",false); f.item(892,"Rune arrow",true); f.item(563,"Law rune",true);
        when(f.items.getItemComposition(28955).getIntValue(ParamID.QUIVER_AMMO_AVAILABLE)).thenReturn(1);
        f.worn(1,28955,1);when(f.nativeState.client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO)).thenReturn(892);
        when(f.nativeState.client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT)).thenReturn(30);
        // The ledger fixture's constructor deliberately has no ItemManager; supply an actual observed cape/quiver view through a second ledger below.
        Gson gson = new Gson(); OwnershipLedger ledger = new OwnershipLedger(f.nativeState.client,f.nativeState.config,gson,f.nativeState.clock::get,new RunewardenDataStore(f.nativeState.config,gson),f.items);
        ledger.start();SupplyAwarenessTool tool = new SupplyAwarenessTool(f.nativeState.client,f.thread,f.nativeState.config,new RunewardenDataStore(f.nativeState.config,gson),ledger,f.items,f.notifier,gson);tool.start();
        f.inventory(new Item(ItemID.BH_RUNE_POUCH,1));EnumComposition runes=mock(EnumComposition.class);when(runes.getIntValue(1)).thenReturn(563);when(f.nativeState.client.getEnum(net.runelite.api.EnumID.RUNEPOUCH_RUNE)).thenReturn(runes);
        when(f.nativeState.client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_1)).thenReturn(1);when(f.nativeState.client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_1)).thenReturn(75);
        ledger.onGameTick();tool.onGameTick();assertTrue(tool.rows().stream().anyMatch(row -> row.kind.equals("QUIVER")&&row.amount==30));assertTrue(tool.rows().stream().anyMatch(row -> row.kind.equals("RUNE_POUCH")&&row.amount==75));
        f.worn(1,-1,0);f.inventory();ledger.onGameTick();tool.onGameTick();assertTrue(tool.rows().stream().noneMatch(row -> row.kind.equals("QUIVER")||row.kind.equals("RUNE_POUCH")));
    }
    @Test public void hiddenBlowpipeAndPoweredStaffLoadsStayAbsent()
    {
        Fixture f=new Fixture();f.item(12926,"Toxic blowpipe",false);f.item(22323,"Sanguinesti staff",false);f.inventory(new Item(12926,1),new Item(22323,1));f.tick();
        assertTrue(f.tool.chargeTotals().isEmpty());assertTrue(f.tool.rows().isEmpty());
    }
    @Test public void familyAlertsDoNotRepeatForEveryConsumedDoseAndRearmAfterRefilling()
    {
        Fixture f = new Fixture(); f.inventory(new Item(3024,2));f.tick();SupplyAwarenessTool.Preferences p = new SupplyAwarenessTool.Preferences();p.notifications=true;f.tool.preferencesNow(p);
        for(int i=0;i<5;i++)f.tick();verifyNoInteractions(f.notifier);
        f.inventory(new Item(3030,2));f.tick();verify(f.notifier,times(1)).notify(anyString());
        f.inventory(new Item(3028,1));f.tick();verify(f.notifier,times(1)).notify(anyString());
        f.inventory(new Item(3024,2));f.tick();f.inventory(new Item(3030,1));f.tick();verify(f.notifier,times(2)).notify(anyString());
    }
    @Test public void bankingSuppressesAlertsAndARefilledPotionPoolIsNotLow()
    {
        Fixture f = new Fixture();f.inventory(new Item(3024,2));f.tick();SupplyAwarenessTool.Preferences p=new SupplyAwarenessTool.Preferences();p.notifications=true;f.tool.preferencesNow(p);
        for(int i=0;i<5;i++)f.tick();f.nativeState.bank(new Item[0]);f.inventory(new Item(3030,1));f.tick();verifyNoInteractions(f.notifier);
        f.inventory(new Item(3030,1),new Item(3024,2));f.tick();f.nativeState.bankWidget=null;f.tick();verifyNoInteractions(f.notifier);
        assertEquals(9,f.kind("POTION").amount);
    }
    @Test public void exhaustedObservedConsumablesRemainZeroUntilDismissedRatherThanVanishing()
    {
        Fixture f = new Fixture();f.inventory(new Item(3024,1));f.tick();f.inventory();f.tick();
        assertEquals(0,f.kind("POTION").amount);assertTrue(f.kind("POTION").low());assertFalse(f.tool.chargeTotals().containsKey(3024));
        assertEquals("0 doses",f.tool.hudRows().get(0).value);
    }
    @Test public void unknownCarriedContainersNeverBecomeAnInventedZeroSupply()
    {
        Fixture f = new Fixture();f.inventory(new Item(3024,1));f.tick();f.nativeState.ledger.clear();f.nativeState.containers.remove(InventoryID.INV);f.nativeState.ledger.onGameTick();f.tool.onGameTick();
        assertTrue(f.tool.rows().isEmpty());assertTrue(f.tool.chargeTotals().isEmpty());
    }
    @Test public void profileSwitchAndDisabledModuleImmediatelyHideRowsAndDoNotLeakPreviousDepletion()
    {
        Fixture f = new Fixture();f.inventory(new Item(3030,1));f.tick();assertFalse(f.tool.hudRows().isEmpty());
        f.nativeState.profile.set("rsprofile.characterB");assertTrue(f.tool.rows().isEmpty());assertTrue(f.tool.chargeTotals().isEmpty());assertTrue(f.tool.hudRows().isEmpty());
        f.inventory();f.tick();assertTrue(f.tool.rows().isEmpty());f.tool.stop();clearInvocations(f.nativeState.client);f.tool.onGameTick();assertTrue(f.tool.rows().isEmpty());verifyNoInteractions(f.nativeState.client);
        assertEquals(647,f.tool.supportedItemCharges().size());
    }
    @Test public void readonlySidebarAndHudDoNotPollTheNativeClient() throws Exception
    {
        Fixture f = new Fixture();f.inventory(new Item(3030,1));f.tick();clearInvocations(f.nativeState.client);
        SwingUtilities.invokeAndWait(f.tool::createPanel);f.tool.rows();f.tool.chargeTotals();f.tool.hudRows();verifyNoInteractions(f.nativeState.client);
    }
    @Test public void futurePreferencesArePreservedAndCannotBeSilentlyOverwritten()
    {
        Fixture f = new Fixture();String key="runewarden|rsprofile.characterA|"+SupplyAwarenessTool.KEY;String raw="{\"version\":99}";f.nativeState.settings.put(key,raw);f.tick();f.tool.preferencesNow(new SupplyAwarenessTool.Preferences());assertEquals(raw,f.nativeState.settings.get(key));
    }
    @Test public void oldCharacterItemButtonsCannotOpenNewCharacterActions() throws Exception
    {
        Fixture f = new Fixture();f.inventory(new Item(3030,1));f.tick();AtomicReference<JPanel> panel = new AtomicReference<>();AtomicReference<Integer> opened = new AtomicReference<>();f.tool.setOpenItemAction(opened::set);
        SwingUtilities.invokeAndWait(() -> { panel.set(f.tool.createPanel());button(panel.get(),"Item sources").doClick(); });assertFalse(f.callbacks.isEmpty());
        f.nativeState.profile.set("rsprofile.characterB");f.inventory();f.tick();for(Runnable action:new ArrayList<>(f.callbacks))action.run();assertNull(opened.get());
    }
    @Test public void explicitItemThresholdOverridesAndPreferenceBoundsAreValidated()
    {
        Fixture f = new Fixture();f.inventory(new Item(3030,1));f.tick();SupplyAwarenessTool.Preferences p=new SupplyAwarenessTool.Preferences();p.thresholds.put(3030,0);f.tool.preferencesNow(p);assertFalse(f.kind("POTION").low());
        p.ammo=-1;assertFalse(SupplyAwarenessTool.valid(p));p.ammo=100;p.thresholds.put(-1,1);assertFalse(SupplyAwarenessTool.valid(p));
    }
    @Test public void loadingAnItemNameCannotCreateAPhantomEmptyFamily()
    {
        Fixture f=new Fixture();when(f.items.getItemComposition(3030)).thenReturn(null);f.inventory(new Item(3030,1));f.tick();assertEquals(1,f.tool.rows().size());
        f.item(3030,"Super restore(1)",false);f.tick();assertEquals(1,f.tool.rows().size());assertEquals("Super restore",f.kind("POTION").name);assertEquals(1,f.kind("POTION").amount);
    }
    private static JButton button(Container root,String text)
    {for(Component child:root.getComponents()){if(child instanceof JButton&&text.equals(((JButton)child).getText()))return (JButton)child;if(child instanceof Container){JButton found=button((Container)child,text);if(found!=null)return found;}}return null;}
    static final class Fixture
    {
        final OwnershipLedgerTest.Fixture nativeState = new OwnershipLedgerTest.Fixture();
        final ClientThread thread=mock(ClientThread.class);final ItemManager items=mock(ItemManager.class);final Notifier notifier=mock(Notifier.class);
        final List<Runnable> callbacks=new ArrayList<>();final Item[] worn=new Item[14];final SupplyAwarenessTool tool;
        Fixture()
        {
            Arrays.fill(worn,new Item(-1,0));nativeState.container(InventoryID.WORN,worn);nativeState.container(InventoryID.INV,new Item[0]);
            item(3024,"Super restore(4)",false);item(3026,"Super restore(3)",false);item(3028,"Super restore(2)",false);item(3030,"Super restore(1)",false);item(1712,"Amulet of glory(4)",false);item(1710,"Amulet of glory(3)",false);
            doAnswer(call->{callbacks.add(call.getArgument(0));return null;}).when(thread).invokeLater(any(Runnable.class));
            Gson gson=new Gson();tool=new SupplyAwarenessTool(nativeState.client,thread,nativeState.config,new RunewardenDataStore(nativeState.config,gson),nativeState.ledger,items,notifier,gson);tool.start();
        }
        void item(int id,String name,boolean stackable)
        {ItemComposition definition=mock(ItemComposition.class);when(definition.getName()).thenReturn(name);when(definition.isStackable()).thenReturn(stackable);when(items.getItemComposition(id)).thenReturn(definition);}
        void inventory(Item... items){nativeState.container(InventoryID.INV,items);}
        void worn(int slot,int id,int quantity){worn[slot]=new Item(id,quantity);nativeState.container(InventoryID.WORN,worn);}
        void tick(){nativeState.ledger.onGameTick();tool.onGameTick();}
        SupplyAwarenessTool.Row kind(String kind){for(SupplyAwarenessTool.Row row:tool.rows())if(row.kind.equals(kind))return row;fail("No row: "+kind);return null;}
    }
}
