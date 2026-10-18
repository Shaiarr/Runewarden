/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.event.*;
import net.runelite.client.config.ConfigManager;

/** One local search with bounded results, category scope and profile-bound rendered actions. */
@Singleton
final class DiscoverTool
{
    static final int PAGE_SIZE=8,MAX_RESULTS=512,MAX_PER_SOURCE=96;
    private final GuideCatalog guides;private final ItemKnowledgeCatalog items;private final Supplier<String> profile;
    private volatile List<String> modules=Collections.emptyList();
    private volatile Consumer<String> openModule=id->{},openGuide=id->{};private volatile IntConsumer openItem=id->{};
    private JPanel panel,results;private JTextField query;private JComboBox<String> scope;private JLabel counter;private JButton back,next;
    private int page;private boolean rendering;private volatile List<Hit> lastHits=List.of();private volatile String renderedProfile;
    private final AtomicLong generation=new AtomicLong();private final AtomicBoolean scheduled=new AtomicBoolean();
    static final class Hit
    {
        final String category,title,detail;final Runnable open;
        Hit(String category,String title,String detail,Runnable open)
        {this.category=category;this.title=title;this.detail=detail;this.open=open;}
    }
    private final CopyOnWriteArrayList<java.util.function.Function<String,List<Hit>>> providers=new CopyOnWriteArrayList<>();
    void addProvider(java.util.function.Function<String,List<Hit>> provider)
    {if(provider!=null&&!providers.contains(provider)){providers.add(provider);generation.incrementAndGet();scheduleSearch();}}
    void clearProviders(){providers.clear();generation.incrementAndGet();scheduleSearch();}
    void openQuery(String value)
    {
        String text=value==null?"":value.trim();
        SwingUtilities.invokeLater(()->{createPanel();query.setText(text);search();});
    }
    void clearAccount(){renderedProfile=null;generation.incrementAndGet();scheduleSearch();}
    int providerCount(){return providers.size();}
    @Inject DiscoverTool(GuideCatalog guides,ItemKnowledgeCatalog items,ConfigManager config)
    {this(guides,items,config::getRSProfileKey);}
    DiscoverTool(GuideCatalog guides,ItemKnowledgeCatalog items)
    {this(guides,items,()->null);}
    DiscoverTool(GuideCatalog guides,ItemKnowledgeCatalog items,Supplier<String> profile)
    {this.guides=guides;this.items=items;this.profile=profile;}
    void setActions(List<String> modules,Consumer<String> module,Consumer<String> guide,IntConsumer item)
    {
        this.modules=Collections.unmodifiableList(new ArrayList<>(modules));openModule=module==null?id->{}:module;
        openGuide=guide==null?id->{}:guide;openItem=item==null?id->{}:item;generation.incrementAndGet();scheduleSearch();
    }
    JPanel createPanel()
    {
        if(panel!=null)return panel;panel=SidebarUi.column();panel.add(SidebarUi.heading("FIND ANYTHING"));
        query=new JTextField();query.setName("runewardenSearchQuery");query.getAccessibleContext().setAccessibleName("Search Runewarden");panel.add(SidebarUi.field("Search",query));
        scope=new JComboBox<>(new String[]{"All results"});scope.setName("runewardenSearchScope");scope.setFont(GuideText.FONT);scope.setBackground(SidebarUi.CARD);scope.setForeground(SidebarUi.TEXT);
        panel.add(SidebarUi.field("Search in",scope));scope.addActionListener(e->{if(!rendering){page=0;renderResults();}});
        JPanel pages=new JPanel(new java.awt.GridLayout(1,3,4,0));pages.setOpaque(false);pages.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,32));
        back=SidebarUi.button("Back",()->{if(page>0){page--;renderResults();}});back.setName("runewardenSearchBack");
        next=SidebarUi.button("Next",()->{page++;renderResults();});next.setName("runewardenSearchNext");
        counter=new JLabel("1 / 1",SwingConstants.CENTER);counter.setFont(GuideText.FONT);counter.setForeground(SidebarUi.GOLD);pages.add(back);pages.add(counter);pages.add(next);panel.add(pages);
        results=SidebarUi.column();results.setName("runewardenSearchResults");panel.add(results);
        javax.swing.Timer delay=new javax.swing.Timer(180,e->search());delay.setRepeats(false);
        query.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){delay.restart();}public void removeUpdate(DocumentEvent e){delay.restart();}public void changedUpdate(DocumentEvent e){delay.restart();}});
        panel.addHierarchyListener(e->{if(panel.isShowing()&&!Objects.equals(renderedProfile,profile.get()))search();});
        search();return panel;
    }
    private void scheduleSearch()
    {
        if(!scheduled.compareAndSet(false,true))return;
        SwingUtilities.invokeLater(()->{scheduled.set(false);if(query!=null)search();});
    }
    private void search()
    {
        if(query==null||results==null)return;String owner=profile.get();long ticket=generation.get();
        List<Hit> found=collect(query.getText().trim());
        if(ticket!=generation.get()||!Objects.equals(owner,profile.get())){scheduleSearch();return;}
        List<Hit> guarded=new ArrayList<>();for(Hit hit:found)
        {
            if(hit==null||hit.category==null||hit.title==null||hit.open==null)continue;
            guarded.add(new Hit(hit.category,hit.title,hit.detail,()->{if(ticket==generation.get()&&Objects.equals(owner,profile.get()))hit.open.run();}));
        }
        lastHits=Collections.unmodifiableList(guarded);renderedProfile=owner;page=0;String selected=(String)scope.getSelectedItem();
        SortedSet<String> categories=new TreeSet<>();for(Hit hit:guarded)categories.add(hit.category);
        rendering=true;scope.removeAllItems();scope.addItem("All results");for(String category:categories)scope.addItem(category);
        scope.setSelectedItem(selected!=null&&categories.contains(selected)?selected:"All results");rendering=false;renderResults();
    }
    List<Hit> collect(String text)
    {
        String needle=text.toLowerCase(Locale.ROOT);List<Hit> found=new ArrayList<>();
        for(String module:modules)if(module.toLowerCase(Locale.ROOT).contains(needle)&&found.size()<MAX_PER_SOURCE)
            found.add(new Hit("Modules",module,"",()->openModule.accept(module)));
        if(text.isEmpty())return found;
        int count=0;for(GuideCatalog.Entry entry:guides.search(text,""))
        {if(count++>=MAX_PER_SOURCE)break;found.add(new Hit("Guides",entry.title,entry.subtitle,()->openGuide.accept(entry.id)));}
        if(text.matches("[1-9][0-9]{0,5}"))
        {int id=Integer.parseInt(text);found.add(new Hit("Items","Item #"+id,"",()->openItem.accept(id)));}
        if(items.loaded())
        {int hits=0;for(ItemKnowledgeCatalog.ItemRef item:items.search(text)){if(hits++>=MAX_PER_SOURCE)break;found.add(new Hit("Items",item.name,"Item #"+item.id,()->openItem.accept(item.id)));}}
        for(java.util.function.Function<String,List<Hit>> provider:providers)
        {
            List<Hit> provided=provider.apply(needle);if(provided==null)continue;int kept=0;
            for(Hit hit:provided){if(kept++>=MAX_PER_SOURCE)break;if(hit!=null)found.add(hit);}
        }
        // Allocate across categories so a broad guide/item query cannot hide later account providers.
        Map<String,Deque<Hit>> categories=new LinkedHashMap<>();for(Hit hit:found)
        {if(hit.category==null||hit.title==null||hit.open==null)continue;Deque<Hit> bucket=categories.computeIfAbsent(hit.category,k->new ArrayDeque<>());if(bucket.size()<MAX_PER_SOURCE)bucket.addLast(hit);}
        List<Hit> balanced=new ArrayList<>();boolean more=true;
        while(more&&balanced.size()<MAX_RESULTS)
        {more=false;for(Deque<Hit> bucket:categories.values())if(!bucket.isEmpty()){balanced.add(bucket.removeFirst());more=true;if(balanced.size()>=MAX_RESULTS)break;}}
        return Collections.unmodifiableList(balanced);
    }
    private void renderResults()
    {
        if(results==null)return;results.removeAll();String selected=(String)scope.getSelectedItem();List<Hit> filtered=new ArrayList<>();
        for(Hit hit:lastHits)if("All results".equals(selected)||hit.category.equals(selected))filtered.add(hit);
        int pages=Math.max(1,(filtered.size()+PAGE_SIZE-1)/PAGE_SIZE);page=Math.max(0,Math.min(pages-1,page));
        counter.setText((page+1)+" / "+pages);back.setEnabled(page>0);next.setEnabled(page+1<pages);
        String last="";for(Hit hit:filtered.subList(Math.min(page*PAGE_SIZE,filtered.size()),Math.min((page+1)*PAGE_SIZE,filtered.size())))
        {
            if(!hit.category.equals(last)){results.add(SidebarUi.heading(hit.category));last=hit.category;}
            results.add(SidebarUi.wrappedButton(hit.title,hit.open));if(hit.detail!=null&&!hit.detail.isEmpty())results.add(SidebarUi.label(hit.detail));
        }
        if(filtered.isEmpty())results.add(SidebarUi.label("No matches"));
        SidebarUi.install(results);results.revalidate();results.repaint();
    }
}
