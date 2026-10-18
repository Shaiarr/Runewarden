/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BankMemoryTest
{
    private static BankMemorySnapshot snapshot(String id,BankMemorySnapshot.Item... items){Map<Integer,BankMemorySnapshot.Item> rows=new LinkedHashMap<>();for(BankMemorySnapshot.Item item:items)rows.put(item.id,item);return new BankMemorySnapshot(id,id,200,100,rows);}
    private static BankMemorySnapshot.Item item(int id,long qty,long price){return new BankMemorySnapshot.Item(id,"Item "+id,qty,price);}
    @Test public void quantitiesAndHistoricalPriceMovementAreCalculatedSeparately()
    {
        BankMemoryDiff diff=new BankMemoryDiff(snapshot("before",item(440,10,5),item(385,3,200)),snapshot("after",item(440,15,7),item(946,1,2)));
        assertEquals(1,diff.added);assertEquals(1,diff.removed);assertEquals(1,diff.changed);assertEquals(-563,diff.quantityValue);assertEquals(-543,diff.valueDelta);
        assertTrue(diff.changes.stream().anyMatch(c->c.id==440&&c.delta==5&&c.quantityValue==35&&c.valueDelta==55));
    }
    @Test public void priceOnlyDifferencesRemainDistinctFromQuantityChangesAndEqualSnapshotsHaveNoRows()
    {
        BankMemorySnapshot a=snapshot("a",item(440,10,5)),b=snapshot("b",item(440,10,7));BankMemoryDiff diff=new BankMemoryDiff(a,b);assertEquals(0,diff.quantityValue);assertEquals(20,diff.valueDelta);assertEquals(0,diff.changed);assertTrue(diff.changes.get(0).priceOnly());assertTrue(new BankMemoryDiff(a,a).changes.isEmpty());
    }
    @Test public void unknownPricesStayUnpricedAndLargeValuesNeverOverflowIntoNegativeWealth()
    {
        BankMemorySnapshot a=snapshot("a",item(440,10,0));assertEquals(0,a.value());assertEquals(1,a.unpriced());BankMemoryDiff diff=new BankMemoryDiff(snapshot("empty"),a);assertEquals(1,diff.unpriced);assertEquals(0,diff.quantityValue);
        BankMemorySnapshot huge=snapshot("huge",item(440,BankMemorySnapshot.MAX_QUANTITY,BankMemorySnapshot.MAX_PRICE));assertEquals(Long.MAX_VALUE,huge.value());assertTrue(huge.saturated());assertEquals(-Long.MAX_VALUE,new BankMemoryDiff(huge,snapshot("empty")).quantityValue);
    }
    @Test public void csvRetainsExactSignedQuantitiesAndEscapesFormulaLikeNamesAndQuotes()
    {
        BankMemorySnapshot.Item named=new BankMemorySnapshot.Item(440,"=HYPERLINK(\"x\")",5,10);String csv=new BankMemoryDiff(snapshot("before",item(440,7,10)),snapshot("after",named)).csv();assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""));assertTrue(csv.contains(",7,5,-2,10,10,-20,-20"));
    }
    @Test public void malformedSnapshotKeysNamesAndUnboundedAmountsAreRejected()
    {
        reject(()->new BankMemorySnapshot("a","",200,100,Collections.emptyMap()));reject(()->new BankMemorySnapshot("a","<b></b>",200,100,Collections.emptyMap()));reject(()->new BankMemorySnapshot("a","a",100,200,Collections.emptyMap()));reject(()->new BankMemorySnapshot.Item(1,"x",0,1));reject(()->new BankMemorySnapshot.Item(1,"x",1,-1));Map<Integer,BankMemorySnapshot.Item> wrong=Collections.singletonMap(2,item(1,1,1));reject(()->new BankMemorySnapshot("a","a",200,100,wrong));
    }
    private static final class Fixture
    {
        final OwnershipLedgerTest.Fixture nativeState=new OwnershipLedgerTest.Fixture();final ClientThread thread=mock(ClientThread.class);final ItemManager items=mock(ItemManager.class);final Queue<Runnable> pending=new ArrayDeque<>();final BankMemoryTool tool;
        Fixture()
        {
            doAnswer(call->{pending.add(call.getArgument(0));return null;}).when(thread).invokeLater(any(Runnable.class));when(items.canonicalize(anyInt())).thenAnswer(call->call.getArgument(0));when(items.getItemPrice(anyInt())).thenReturn(100L);
            when(items.getItemComposition(anyInt())).thenAnswer(call->{ItemComposition d=mock(ItemComposition.class);when(d.getMembersName()).thenReturn("A long readable bank item name "+call.getArgument(0));return d;});Gson gson=new Gson();tool=new BankMemoryTool(nativeState.client,thread,nativeState.config,gson,items,nativeState.ledger,new RunewardenDataStore(nativeState.config,gson));tool.start();tick();
        }
        void tick(){nativeState.ledger.onGameTick();tool.onGameTick();}
        void bank(Item... items){nativeState.bank(items);tick();}
        void drain(){int count=0;while(!pending.isEmpty()){assertTrue(++count<100);pending.remove().run();}}
        void capture(String name)throws Exception{tool.capture(name);drain();flush();}
        void flush()throws Exception{SwingUtilities.invokeAndWait(()->{});}
        void stop()throws Exception{tool.stop();drain();flush();}
        String address(String profile){return BankMemoryTool.GROUP+"|"+profile+"|"+BankMemoryTool.KEY;}
    }
    @Test public void closedOrUnobservedBankCannotBecomeAnEmptySnapshot() throws Exception
    {
        Fixture f=new Fixture();assertFalse(f.tool.bankAvailable());f.capture("Missing bank");assertTrue(f.tool.snapshots().isEmpty());verifyNoInteractions(f.items);f.stop();
    }
    @Test public void anActuallyObservedEmptyOpenBankIsAValidNamedSnapshot() throws Exception
    {
        Fixture f=new Fixture();f.bank();assertTrue(f.tool.bankAvailable());f.capture("Empty bank");assertEquals(1,f.tool.snapshots().size());assertTrue(f.tool.snapshots().get(0).items.isEmpty());f.stop();
    }
    @Test public void nativeOpenBankCapturesProduceNamedPersistentQuantityDifferences() throws Exception
    {
        Fixture f=new Fixture();f.bank(new Item(440,10),new Item(385,2));f.capture("Before trip");f.bank(new Item(440,15),new Item(946,1));f.capture("After trip");assertEquals(2,f.tool.snapshots().size());assertEquals(1,f.tool.difference().added);assertEquals(1,f.tool.difference().removed);assertEquals(1,f.tool.difference().changed);assertTrue(f.nativeState.settings.containsKey(f.address("rsprofile.characterA")));assertEquals(15,f.tool.snapshots().get(1).items.get(440).quantity);f.stop();
    }
    @Test public void historySurvivesRestartButAClosedBankStaysUnavailableAndRequiresNoItemReads() throws Exception
    {
        Fixture f=new Fixture();f.bank(new Item(440,10));f.capture("Known bank");f.tool.stop();f.nativeState.bankWidget=null;clearInvocations(f.items);f.tool.start();f.tick();assertEquals(1,f.tool.snapshots().size());assertFalse(f.tool.bankAvailable());verifyNoInteractions(f.items);f.capture("Cannot capture closed bank");assertEquals(1,f.tool.snapshots().size());f.stop();
    }
    @Test public void queuedCapturesAndExistingHistoryNeverCrossCharacterProfiles() throws Exception
    {
        Fixture f=new Fixture();f.bank(new Item(440,10));f.capture("Character A");f.tool.capture("Queued A");f.nativeState.profile.set("rsprofile.characterB");assertTrue(f.tool.snapshots().isEmpty());f.nativeState.bankWidget=null;f.tick();f.drain();assertTrue(f.tool.snapshots().isEmpty());assertFalse(f.nativeState.settings.containsKey(f.address("rsprofile.characterB")));f.stop();
    }
    @Test public void snapshotsCanBeRenamedAndRemovedWithoutChangingOtherSavedBanks() throws Exception
    {
        Fixture f=new Fixture();f.bank(new Item(440,10));f.capture("One");f.capture("Two");String first=f.tool.snapshots().get(0).id,second=f.tool.snapshots().get(1).id;f.tool.rename(first,"Renamed");f.drain();assertEquals("Renamed",f.tool.snapshots().get(0).name);f.tool.delete(second);f.drain();assertEquals(1,f.tool.snapshots().size());assertEquals(first,f.tool.snapshots().get(0).id);f.stop();
    }
    @Test public void explicitOpenBankComparisonClearsItsLiveSideWhenTheBankCloses() throws Exception
    {
        Fixture f=new Fixture();f.bank(new Item(440,10));f.capture("Before");f.bank(new Item(440,15));f.tool.compareCurrent();f.drain();assertEquals(5,f.tool.difference().changes.get(0).delta);assertEquals(1,f.tool.snapshots().size());f.nativeState.bankWidget=null;f.tick();assertNull(f.tool.difference());assertEquals(1,f.tool.snapshots().size());f.stop();
    }
    @Test public void eightSnapshotBoundAndFutureSchemaProtectionPreventSilentHistoryLoss() throws Exception
    {
        Fixture f=new Fixture();f.bank(new Item(440,10));for(int i=0;i<9;i++)f.capture("Run "+i);assertEquals(8,f.tool.snapshots().size());f.stop();
        Fixture future=new Fixture();future.tool.stop();String original="{\"version\":99,\"snapshots\":[]}";future.nativeState.settings.put(future.address("rsprofile.characterA"),original);future.tool.start();future.bank(new Item(440,10));future.capture("Blocked future data");assertTrue(future.tool.snapshots().isEmpty());assertEquals(original,future.nativeState.settings.get(future.address("rsprofile.characterA")));future.stop();
    }
    @Test public void snapshotJsonAndDiffCsvExportsAreLocalAndNeverIncludeTheProfileIdentifier() throws Exception
    {
        Fixture f=new Fixture();f.bank(new Item(440,10));f.capture("Before");f.bank(new Item(440,15));f.capture("After");String id=f.tool.snapshots().get(0).id,json=f.tool.exportSnapshotJson(id),csv=f.tool.exportDiffCsv(),itemsCsv=f.tool.exportSnapshotCsv(id),itemsTsv=f.tool.exportSnapshotTsv(id);assertTrue(json.contains("Before"));assertTrue(csv.contains("quantity_delta"));assertTrue(itemsCsv.startsWith("item_id,item_name,quantity,unit_ge,total_ge\r\n"));assertTrue(itemsCsv.contains("440,\"A long readable bank item name 440\",10,100,1000\r\n"));assertTrue(itemsTsv.contains("440\t\"A long readable bank item name 440\"\t10\t100\t1000\r\n"));assertFalse(json.contains("rsprofile"));assertFalse(csv.contains("rsprofile"));assertFalse(itemsCsv.contains("rsprofile"));assertFalse(itemsTsv.contains("rsprofile"));assertEquals("",f.tool.exportSnapshotCsv("missing"));f.stop();assertEquals("",f.tool.exportDiffCsv());assertEquals("",f.tool.exportSnapshotCsv(id));assertEquals("",f.tool.exportSnapshotTsv(id));
    }
    @Test public void oneSavedBankHasSearchableContentsEvenWithoutAnyQuantityChanges() throws Exception
    {
        Fixture f=new Fixture();f.bank(new Item(440,10),new Item(385,2));f.capture("Only bank");assertTrue(f.tool.difference().changes.isEmpty());SwingUtilities.invokeAndWait(()->{JPanel panel=f.tool.createPanel();assertEquals(2,buttons(panel,"Item details"));JTextField search=named(panel,"bankMemorySearch",JTextField.class);JComboBox<?> view=named(panel,"bankMemoryViewMode",JComboBox.class);search.setText("440");view.setSelectedItem("Changes");view.setSelectedItem("Bank contents");assertEquals(1,buttons(panel,"Item details"));search.setText("not present");view.setSelectedItem("Changes");view.setSelectedItem("Bank contents");assertEquals(0,buttons(panel,"Item details"));});f.stop();
    }
    @Test public void priceOnlyChangesAreHiddenUntilExplicitlyIncludedInTheChangesView() throws Exception
    {
        Fixture f=new Fixture();f.bank(new Item(440,10));f.capture("Before");when(f.items.getItemPrice(440)).thenReturn(120L);f.capture("After");assertTrue(f.tool.difference().changes.get(0).priceOnly());SwingUtilities.invokeAndWait(()->{JPanel panel=f.tool.createPanel();named(panel,"bankMemoryViewMode",JComboBox.class).setSelectedItem("Changes");assertEquals(0,buttons(panel,"Item details"));JCheckBox include=checkBox(panel,"Include price-only changes");assertNotNull(include);include.doClick();assertEquals(1,buttons(panel,"Item details"));assertNotNull(button(panel,"Export diff CSV…"));});f.stop();
    }
    @Test public void unchangedNativeTicksNeverRebuildCapturesOrPollItemDefinitions() throws Exception
    {
        Fixture f=new Fixture();f.bank(new Item(440,10));f.capture("One");BankMemoryDiff diff=f.tool.difference();clearInvocations(f.items);for(int i=0;i<10;i++)f.tick();assertSame(diff,f.tool.difference());verifyNoInteractions(f.items);f.stop();
    }
    @Test public void historicalItemButtonsRejectAnOldProfileBeforeOpeningTheDatabase() throws Exception
    {
        Fixture f=new Fixture();AtomicInteger opened=new AtomicInteger();f.tool.setOpenItemAction(opened::set);f.bank(new Item(440,10));f.capture("Before");f.bank(new Item(440,15));f.capture("After");SwingUtilities.invokeAndWait(()->{JButton button=button(f.tool.createPanel(),"Item details");assertNotNull(button);f.nativeState.profile.set("rsprofile.characterB");button.doClick();});f.drain();f.flush();assertEquals(0,opened.get());f.stop();
    }
    @Test public void actual206PixelSidebarWrapsNamedHistoryAndPagedChanges() throws Exception
    {
        Fixture f=new Fixture();f.bank(new Item(440,10));f.capture("A deliberately long snapshot name before a very long skilling session");Item[] after=new Item[16];for(int i=0;i<16;i++)after[i]=new Item(1000+i,i+1);f.bank(after);f.capture("A deliberately long snapshot name after a very long skilling session");SwingUtilities.invokeAndWait(()->{JPanel panel=f.tool.createPanel();SidebarUi.install(panel);for(int i=0;i<8;i++){panel.setSize(206,panel.getPreferredSize().height);layout(panel);}assertFits(panel);assertNotNull(button(panel,"Next"));Graphics2D g=new BufferedImage(206,panel.getHeight(),BufferedImage.TYPE_INT_ARGB).createGraphics();try{panel.paint(g);}finally{g.dispose();}});f.stop();
    }
    private static void reject(Runnable action){try{action.run();fail("Invalid snapshot accepted");}catch(IllegalArgumentException expected){}}
    private static JButton button(Container root,String text){for(Component c:root.getComponents()){if(c instanceof JButton&&text.equals(((JButton)c).getText()))return (JButton)c;if(c instanceof Container){JButton b=button((Container)c,text);if(b!=null)return b;}}return null;}
    private static int buttons(Container root,String text){int count=0;for(Component c:root.getComponents()){if(c instanceof JButton&&text.equals(((JButton)c).getText()))count++;if(c instanceof Container)count+=buttons((Container)c,text);}return count;}
    private static JCheckBox checkBox(Container root,String text){for(Component c:root.getComponents()){if(c instanceof JCheckBox&&text.equals(((JCheckBox)c).getText()))return (JCheckBox)c;if(c instanceof Container){JCheckBox found=checkBox((Container)c,text);if(found!=null)return found;}}return null;}
    private static <T extends Component> T named(Container root,String name,Class<T> type){for(Component c:root.getComponents()){if(type.isInstance(c)&&name.equals(c.getName()))return type.cast(c);if(c instanceof Container){T found=named((Container)c,name,type);if(found!=null)return found;}}return null;}
    private static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container)layout((Container)c);}
    private static void assertFits(Container root){for(Component c:root.getComponents()){if(!c.isVisible())continue;assertTrue(c.getClass()+" right edge",c.getX()+c.getWidth()<=root.getWidth());if(c instanceof GuideText)assertTrue("clipped text",c.getHeight()>=c.getPreferredSize().height);if(c instanceof Container)assertFits((Container)c);}}
}
