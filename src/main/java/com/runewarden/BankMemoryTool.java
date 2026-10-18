/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/** Named local bank observations and quantity/value diffs. Never changes or reconstructs the bank. */
@Singleton
final class BankMemoryTool
{
    static final String GROUP="runewardenbankmemory",KEY="snapshotsV1",LIVE="current";
    static final int MAX_SNAPSHOTS=8,MAX_JSON=1_200_000,PAGE_SIZE=8;
    private static final class Saved {int version=1;List<BankMemorySnapshot> snapshots=new ArrayList<>();String before,after;}
    private static final class Facts {final String name;final int canonical;Facts(String name,int canonical){this.name=name;this.canonical=canonical;}}
    private final Client client;private final ClientThread thread;private final ConfigManager config;private final Gson gson;private final ItemManager items;private final OwnershipLedger ledger;private final RunewardenDataStore store;
    private final Map<Integer,Facts> definitions=new LinkedHashMap<Integer,Facts>(256,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<Integer,Facts> eldest){return size()>2500;}};
    private volatile List<BankMemorySnapshot> snapshots=Collections.emptyList();private volatile BankMemorySnapshot liveSnapshot;private volatile BankMemoryDiff difference;
    private volatile String profile,beforeId,afterId,status="";private volatile long epoch;private volatile boolean started,active,bankAvailable;
    private JPanel panel,body;private JComboBox<BankMemorySnapshot> beforeChoice,afterChoice;private JTextField name,search;private JCheckBox priceChanges;private JComboBox<String> viewMode;
    private boolean rendering;private int page;private String renderedProfile;private long renderedEpoch;private Timer searchTimer;
    private IntConsumer openItem=ignored->{};
    @Inject BankMemoryTool(Client client,ClientThread thread,ConfigManager config,Gson gson,ItemManager items,OwnershipLedger ledger,RunewardenDataStore store)
    {this.client=client;this.thread=thread;this.config=config;this.gson=gson;this.items=items;this.ledger=ledger;this.store=store;}
    void start(){started=true;clear();}
    void stop(){started=false;clear();if(searchTimer!=null)SwingUtilities.invokeLater(()->searchTimer.stop());}
    void clear(){++epoch;active=false;profile=null;snapshots=Collections.emptyList();liveSnapshot=null;difference=null;beforeId=afterId=null;bankAvailable=false;status="";definitions.clear();refresh();}
    void setOpenItemAction(IntConsumer action){openItem=action==null?ignored->{}:action;}
    List<BankMemorySnapshot> snapshots(){return valid()?snapshots:Collections.emptyList();}
    BankMemoryDiff difference(){return valid()?difference:null;}
    boolean bankAvailable(){return valid()&&bankAvailable;}
    private boolean valid(){return started&&active&&profile!=null&&profile.equals(config.getRSProfileKey());}
    void onGameTick()
    {
        if(!started)return;
        if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null){if(active||profile!=null)clear();return;}
        String next=config.getRSProfileKey();if(next==null){clear();return;}
        if(!next.equals(profile))
        {
            clear();profile=next;active=true;
            Saved saved=store.read(GROUP,profile,KEY,1,Saved.class,value->validSaved(value)&&gson.toJson(value).length()<=MAX_JSON,Saved::new);
            List<BankMemorySnapshot> safe=new ArrayList<>();for(BankMemorySnapshot snapshot:saved.snapshots)safe.add(snapshot.checked());snapshots=Collections.unmodifiableList(safe);
            beforeId=saved.before;afterId=saved.after;normaliseSelection();recalculate();refresh();
        }
        active=true;OwnershipLedger.View state=ledger.snapshot();OwnershipLedger.Observation bank=state.storages.get(OwnershipLedger.Storage.BANK);
        Widget widget=client.getWidget(InterfaceID.Bankmain.ITEMS);
        boolean available=state.active&&profile.equals(state.profile)&&bank!=null&&bank.currentSession&&widget!=null&&!widget.isHidden();
        if(available!=bankAvailable)
        {bankAvailable=available;if(!available&&LIVE.equals(afterId)){liveSnapshot=null;difference=null;}refresh();}
    }
    private static boolean validSaved(Saved saved)
    {
        if(saved==null||saved.version!=1||saved.snapshots==null||saved.snapshots.size()>MAX_SNAPSHOTS||saved.before!=null&&saved.before.length()>64||saved.after!=null&&saved.after.length()>64)return false;
        java.util.Set<String> ids=new java.util.HashSet<>();try{for(BankMemorySnapshot snapshot:saved.snapshots)if(snapshot==null||LIVE.equals(snapshot.id)||!ids.add(snapshot.checked().id))return false;}catch(RuntimeException bad){return false;}return true;
    }
    void capture(String title)
    {
        mutate(()->{
            if(snapshots.size()>=MAX_SNAPSHOTS){status="Eight snapshots saved · remove one to capture another";return;}
            BankMemorySnapshot captured=captureOpenBank(title,UUID.randomUUID().toString());if(captured==null)return;
            List<BankMemorySnapshot> next=new ArrayList<>(snapshots);next.add(captured);String oldBefore=beforeId,oldAfter=afterId;
            beforeId=snapshots.isEmpty()?captured.id:snapshots.get(snapshots.size()-1).id;afterId=captured.id;
            if(save(next)){snapshots=Collections.unmodifiableList(next);status="Snapshot saved";recalculate();}else{beforeId=oldBefore;afterId=oldAfter;}
        });
    }
    void compareCurrent()
    {mutate(()->{BankMemorySnapshot current=captureOpenBank("Current bank capture",LIVE);if(current==null)return;liveSnapshot=current;afterId=LIVE;normaliseSelection();recalculate();status="Compared with the open bank";});}
    private BankMemorySnapshot captureOpenBank(String title,String id)
    {
        OwnershipLedger.View state=ledger.snapshot();OwnershipLedger.Observation bank=state.storages.get(OwnershipLedger.Storage.BANK);Widget widget=client.getWidget(InterfaceID.Bankmain.ITEMS);
        if(!state.active||!Objects.equals(profile,state.profile)||bank==null||!bank.currentSession||widget==null||widget.isHidden())
        {status="Open your bank before capturing";return null;}
        long now=System.currentTimeMillis();Map<Integer,BankMemorySnapshot.Item> values=new LinkedHashMap<>();
        for(Map.Entry<Integer,Long> entry:bank.quantities.entrySet())
        {
            int itemId=entry.getKey();Facts facts=definitions.get(itemId);
            if(facts==null){ItemComposition definition=items.getItemComposition(itemId);int canonical=items.canonicalize(itemId);if(canonical<=0)canonical=itemId;String text=definition==null||definition.getMembersName()==null?"Item "+itemId:BankMemorySnapshot.clean(definition.getMembersName(),100);facts=new Facts(text,canonical);definitions.put(itemId,facts);}
            long price=Math.max(0,items.getItemPrice(facts.canonical));if(price>BankMemorySnapshot.MAX_PRICE)price=0;
            values.put(itemId,new BankMemorySnapshot.Item(itemId,facts.name,entry.getValue(),price));
        }
        String label=title==null||title.trim().isEmpty()?DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(now)):title.trim();
        return new BankMemorySnapshot(id,label,now,Math.min(now,bank.observedAt),values);
    }
    void rename(String id,String title)
    {mutate(()->{List<BankMemorySnapshot> next=new ArrayList<>();boolean found=false;for(BankMemorySnapshot snapshot:snapshots){if(snapshot.id.equals(id))found=true;next.add(snapshot.id.equals(id)?snapshot.renamed(title):snapshot);}if(!found)return;if(save(next)){snapshots=Collections.unmodifiableList(next);recalculate();status="Snapshot renamed";}});}
    void delete(String id)
    {mutate(()->{List<BankMemorySnapshot> next=new ArrayList<>(snapshots);if(!next.removeIf(snapshot->snapshot.id.equals(id)))return;if(save(next)){snapshots=Collections.unmodifiableList(next);normaliseSelection();recalculate();status="Snapshot removed";}});}
    void selectBefore(String id){mutate(()->{if(find(id)!=null){beforeId=id;recalculate();save(snapshots);}});}
    void selectAfter(String id){mutate(()->{if(find(id)!=null){afterId=id;recalculate();save(snapshots);}});}
    private BankMemorySnapshot find(String id){if(LIVE.equals(id))return liveSnapshot;for(BankMemorySnapshot snapshot:snapshots)if(snapshot.id.equals(id))return snapshot;return null;}
    private void normaliseSelection()
    {if(find(beforeId)==null)beforeId=snapshots.isEmpty()?null:snapshots.get(Math.max(0,snapshots.size()-2)).id;if(find(afterId)==null)afterId=snapshots.isEmpty()?null:snapshots.get(snapshots.size()-1).id;}
    private void recalculate(){BankMemorySnapshot a=find(beforeId),b=find(afterId);difference=a==null||b==null?null:new BankMemoryDiff(a,b);page=0;}
    private boolean save(List<BankMemorySnapshot> next)
    {
        Saved saved=new Saved();saved.snapshots=next;saved.before=LIVE.equals(beforeId)?null:beforeId;saved.after=LIVE.equals(afterId)?null:afterId;
        if(gson.toJson(saved).length()>MAX_JSON){status="Snapshot storage is full · remove an older snapshot";return false;}
        if(!store.write(GROUP,profile,KEY,1,saved)){status="Snapshot save needs recovery in Data settings";return false;}return true;
    }
    private void mutate(Runnable action)
    {
        String owner=panel==null?profile:renderedProfile;long expected=panel==null?epoch:renderedEpoch;
        thread.invokeLater(()->{if(!valid()||expected!=epoch||owner==null||!owner.equals(profile)||client.getGameState()!=GameState.LOGGED_IN)return;try{action.run();}catch(IllegalArgumentException invalid){status=invalid.getMessage();}refresh();});
    }
    String exportSnapshotJson(String id){BankMemorySnapshot snapshot=valid()?find(id):null;return snapshot==null?"":gson.toJson(snapshot);}
    String exportDiffCsv(){BankMemoryDiff diff=difference();return diff==null?"":diff.csv();}
    String exportSnapshotCsv(String id){return exportItems(id,',');}
    String exportSnapshotTsv(String id){return exportItems(id,'\t');}
    private String exportItems(String id,char delimiter)
    {
        BankMemorySnapshot snapshot=valid()?find(id):null;if(snapshot==null)return "";
        StringBuilder text=new StringBuilder("item_id"+delimiter+"item_name"+delimiter+"quantity"+delimiter+"unit_ge"+delimiter+"total_ge\r\n");
        for(BankMemorySnapshot.Item item:snapshot.items.values())text.append(item.id).append(delimiter).append(BankMemoryDiff.csvText(item.name)).append(delimiter).append(item.quantity).append(delimiter).append(item.price).append(delimiter).append(BankMemorySnapshot.multiply(item.quantity,item.price)).append("\r\n");return text.toString();
    }
    JPanel createPanel()
    {
        if(panel!=null)return panel;panel=SidebarUi.column();panel.setName("runewarden.bank-memory");panel.add(SidebarUi.heading("BANK MEMORY"));
        name=new JTextField();name.setName("bankMemoryName");panel.add(SidebarUi.field("Snapshot name",name));panel.add(SidebarUi.button("Capture open bank",()->capture(name.getText())));
        beforeChoice=new JComboBox<>();beforeChoice.setName("bankMemoryBefore");panel.add(SidebarUi.field("Before",beforeChoice));beforeChoice.addActionListener(event->{if(!rendering){BankMemorySnapshot s=(BankMemorySnapshot)beforeChoice.getSelectedItem();if(s!=null)selectBefore(s.id);}});
        afterChoice=new JComboBox<>();afterChoice.setName("bankMemoryAfter");panel.add(SidebarUi.field("After",afterChoice));afterChoice.addActionListener(event->{if(!rendering){BankMemorySnapshot s=(BankMemorySnapshot)afterChoice.getSelectedItem();if(s!=null)selectAfter(s.id);}});
        panel.add(SidebarUi.button("Compare with open bank",this::compareCurrent));
        search=new JTextField();search.setName("bankMemorySearch");panel.add(SidebarUi.field("Find item",search));searchTimer=new Timer(180,event->{page=0;render();});searchTimer.setRepeats(false);search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){searchTimer.restart();}public void removeUpdate(DocumentEvent e){searchTimer.restart();}public void changedUpdate(DocumentEvent e){searchTimer.restart();}});
        viewMode=new JComboBox<>(new String[]{"Bank contents","Changes"});viewMode.setName("bankMemoryViewMode");panel.add(SidebarUi.field("View",viewMode));viewMode.addActionListener(event->{if(!rendering){page=0;render();}});
        priceChanges=new JCheckBox("Include price-only changes");priceChanges.setForeground(SidebarUi.TEXT);priceChanges.setFont(GuideText.FONT);priceChanges.setOpaque(false);priceChanges.addActionListener(event->{page=0;render();});panel.add(priceChanges);
        body=SidebarUi.column();panel.add(body);render();return panel;
    }
    private void refresh(){if(panel==null)return;long expected=epoch;SwingUtilities.invokeLater(()->{if(panel!=null&&expected==epoch)render();});}
    private void render()
    {
        if(body==null||rendering)return;rendering=true;
        try
        {
            renderedProfile=profile;renderedEpoch=epoch;body.removeAll();beforeChoice.removeAllItems();afterChoice.removeAllItems();
            for(BankMemorySnapshot snapshot:snapshots()){beforeChoice.addItem(snapshot);afterChoice.addItem(snapshot);}if(valid()&&liveSnapshot!=null)afterChoice.addItem(liveSnapshot);beforeChoice.setSelectedItem(find(beforeId));afterChoice.setSelectedItem(find(afterId));
            if(!valid()){body.add(SidebarUi.label("Log in to view character bank history"));return;}
            body.add(SidebarUi.label(bankAvailable?"Bank observed this session · open":"Bank closed / unavailable"));if(!status.isEmpty())body.add(SidebarUi.label(status));
            BankMemorySnapshot selected=find(afterId);
            if(selected!=null)
            {
                JPanel card=SidebarUi.section(selected.name);card.add(SidebarUi.label(time(selected.capturedAt)+" · "+selected.items.size()+" item types"));card.add(SidebarUi.label((selected.saturated()?"≥ ":"")+number(selected.value())+" gp observed"));if(selected.unpriced()>0)card.add(SidebarUi.label(selected.unpriced()+" item types unpriced"));
                String id=selected.id;long ownerEpoch=epoch;String ownerProfile=profile;if(!LIVE.equals(id))card.add(SidebarUi.row(SidebarUi.button("Rename",()->ownedAction(ownerProfile,ownerEpoch,()->rename(id,name.getText()))),SidebarUi.button("Delete",()->ownedAction(ownerProfile,ownerEpoch,()->delete(id)))));
                JPanel exports=SidebarUi.column();
                exports.add(SidebarUi.button("Snapshot JSON…",()->ownedAction(ownerProfile,ownerEpoch,()->{String text=exportSnapshotJson(id);if(!text.isEmpty())RecoveryTool.showText("Bank snapshot JSON",text,false);})));
                exports.add(SidebarUi.button("Items CSV…",()->ownedAction(ownerProfile,ownerEpoch,()->{String text=exportSnapshotCsv(id);if(!text.isEmpty())RecoveryTool.showText("Bank items CSV",text,false);})));
                exports.add(SidebarUi.button("Items TSV…",()->ownedAction(ownerProfile,ownerEpoch,()->{String text=exportSnapshotTsv(id);if(!text.isEmpty())RecoveryTool.showText("Bank items TSV",text,false);})));
                card.add(SidebarUi.disclosure("Export selected snapshot",exports));body.add(card);
            }
            BankMemoryDiff diff=difference();
            if("Bank contents".equals(viewMode.getSelectedItem())){renderContents(selected);return;}
            if(diff==null){body.add(SidebarUi.label(LIVE.equals(afterId)?"Open the bank to compare its current capture":"Choose two saved observations"));return;}
            JPanel summary=SidebarUi.section("CHANGES");summary.add(SidebarUi.label(diff.added+" added · "+diff.removed+" removed · "+diff.changed+" changed item types"));summary.add(SidebarUi.label("Quantity change · "+signed(diff.quantityValue)+" gp estimate"));summary.add(SidebarUi.label("Stored GE change · "+signed(diff.valueDelta)+" gp"));if(diff.unpriced>0)summary.add(SidebarUi.label(diff.unpriced+" quantity changes unpriced"));long diffEpoch=epoch;String diffProfile=profile;summary.add(SidebarUi.button("Export diff CSV…",()->ownedAction(diffProfile,diffEpoch,()->{String csv=exportDiffCsv();if(!csv.isEmpty())RecoveryTool.showText("Bank quantity / GE differences",csv,false);})));body.add(summary);
            String query=search.getText().trim().toLowerCase(Locale.ROOT);List<BankMemoryDiff.Change> shown=new ArrayList<>();for(BankMemoryDiff.Change row:diff.changes)if((priceChanges.isSelected()||!row.priceOnly())&&(query.isEmpty()||Integer.toString(row.id).equals(query)||row.name.toLowerCase(Locale.ROOT).contains(query)))shown.add(row);
            page=Math.min(page,Math.max(0,(shown.size()-1)/PAGE_SIZE));int end=Math.min(shown.size(),(page+1)*PAGE_SIZE);
            for(int i=page*PAGE_SIZE;i<end;i++)
            {
                BankMemoryDiff.Change change=shown.get(i);JPanel card=SidebarUi.section(change.name);card.add(SidebarUi.label(number(change.before)+" → "+number(change.after)+(change.priceOnly()?" · Price only":" · "+signed(change.delta))));card.add(SidebarUi.label("Quantity estimate · "+signed(change.quantityValue)+" gp"));long expected=epoch;String owner=profile;
                card.add(SidebarUi.button("Item details",()->thread.invokeLater(()->{if(valid()&&expected==epoch&&Objects.equals(owner,profile))SwingUtilities.invokeLater(()->{if(valid()&&expected==epoch)openItem.accept(change.id);});})));body.add(card);
            }
            if(shown.isEmpty())body.add(SidebarUi.label("No matching quantity changes"));if(shown.size()>PAGE_SIZE){JButton previous=SidebarUi.button("Previous",()->{page--;render();}),next=SidebarUi.button("Next",()->{page++;render();});previous.setEnabled(page>0);next.setEnabled(end<shown.size());body.add(SidebarUi.row(previous,next));body.add(SidebarUi.label("Page "+(page+1)+" / "+((shown.size()+PAGE_SIZE-1)/PAGE_SIZE)));}
        }
        finally{rendering=false;body.revalidate();body.repaint();panel.revalidate();panel.repaint();}
    }
    private void renderContents(BankMemorySnapshot snapshot)
    {
        if(snapshot==null){body.add(SidebarUi.label("No saved bank selected"));return;}
        String query=search.getText().trim().toLowerCase(Locale.ROOT);List<BankMemorySnapshot.Item> rows=new ArrayList<>();
        for(BankMemorySnapshot.Item item:snapshot.items.values())if(query.isEmpty()||Integer.toString(item.id).equals(query)||item.name.toLowerCase(Locale.ROOT).contains(query))rows.add(item);
        rows.sort(Comparator.comparingLong((BankMemorySnapshot.Item item)->BankMemorySnapshot.multiply(item.quantity,item.price)).reversed().thenComparing(item->item.name,String.CASE_INSENSITIVE_ORDER));
        page=Math.min(page,Math.max(0,(rows.size()-1)/PAGE_SIZE));int end=Math.min(rows.size(),(page+1)*PAGE_SIZE);
        for(int i=page*PAGE_SIZE;i<end;i++)
        {
            BankMemorySnapshot.Item item=rows.get(i);JPanel card=SidebarUi.section(item.name);card.add(SidebarUi.label(number(item.quantity)+" items · "+(item.price>0?number(BankMemorySnapshot.multiply(item.quantity,item.price))+" gp estimate":"Unpriced")));
            long expected=epoch;String owner=profile;card.add(SidebarUi.button("Item details",()->thread.invokeLater(()->{if(valid()&&epoch==expected&&Objects.equals(owner,profile))SwingUtilities.invokeLater(()->{if(valid()&&epoch==expected)openItem.accept(item.id);});})));body.add(card);
        }
        if(rows.isEmpty())body.add(SidebarUi.label("No matching saved items"));
        if(rows.size()>PAGE_SIZE){JButton previous=SidebarUi.button("Previous",()->{page--;render();}),next=SidebarUi.button("Next",()->{page++;render();});previous.setEnabled(page>0);next.setEnabled(end<rows.size());body.add(SidebarUi.row(previous,next));body.add(SidebarUi.label("Page "+(page+1)+" / "+((rows.size()+PAGE_SIZE-1)/PAGE_SIZE)));}
    }
    private void ownedAction(String owner,long ticket,Runnable action){if(valid()&&epoch==ticket&&Objects.equals(owner,profile))action.run();}
    private static String number(long amount){return String.format(Locale.ROOT,"%,d",amount);}
    private static String signed(long amount){return (amount>0?"+":"")+number(amount);}
    private static String time(long value){return DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value));}
}
