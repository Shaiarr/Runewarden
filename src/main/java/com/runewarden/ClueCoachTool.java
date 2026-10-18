/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.event.HierarchyEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;
import net.runelite.client.util.LinkBrowser;

/** A compact preparation and travel flow around the native clue and puzzle engines. */
@Singleton
public final class ClueCoachTool
{
    private final ClueDataService data;
    private final GuideNavigation navigation;
    private final ClientThread clientThread;
    private final ItemManager items;
    private boolean started;
    private volatile boolean visible;
    private volatile long epoch;
    private String previousKey="", fingerprint="";
    private volatile ClueDataService.StashReference selectedStash;
    private JPanel panel,body;
    private JComboBox<String> mode,filter;
    private JTextField search;
    private int page;
    private final AtomicBoolean refreshQueued=new AtomicBoolean();
    private Consumer<String> openGuide=id -> { }, openModule=id -> { };
    private IntConsumer openItem=id -> { };
    private Consumer<java.util.Set<Integer>> bankFocus=ids->{};
    private net.runelite.client.config.ConfigManager config;
    private boolean autoBank=true,bankWasOpen;
    private static final Color READY=new Color(107,211,147), MISSING=new Color(238,160,101);

    ClueCoachTool(ClueDataService data,GuideNavigation navigation,ClientThread clientThread,ItemManager items)
    { this.data=data; this.navigation=navigation; this.clientThread=clientThread; this.items=items; }
    @Inject ClueCoachTool(ClueDataService data,GuideNavigation navigation,ClientThread clientThread,ItemManager items,net.runelite.client.config.ConfigManager config)
    {this(data,navigation,clientThread,items);this.config=config;autoBank=!"false".equals(config.getConfiguration(ClueDataService.GROUP,"autoBank"));}
    public void setBankFocusAction(Consumer<java.util.Set<Integer>> action){bankFocus=action==null?ids->{}:action;}
    ClueDataService.Snapshot snapshot(){return data.snapshot();}
    java.util.Set<Integer> targetItemIds()
    {java.util.Set<Integer> ids=new java.util.LinkedHashSet<>();if(started)for(java.util.Map<Integer,Integer> group:data.snapshot().requirements)ids.addAll(group.keySet());return java.util.Collections.unmodifiableSet(ids);}
    public void setOpenGuideAction(Consumer<String> action) { openGuide=action==null ? id -> { } : action; }
    public void setOpenItemAction(IntConsumer action) { openItem=action==null ? id -> { } : action; }
    public void setModuleAction(Consumer<String> action) { openModule=action==null ? id -> { } : action; }
    public void start() { started=true; clear(); }
    public void stop() { started=false; clear(); }
    public void clear()
    { epoch++;bankFocus.accept(java.util.Collections.emptySet());bankWasOpen=false;data.clear(); previousKey=""; fingerprint=""; selectedStash=null; refresh(); }

    public void onGameTick()
    {
        if (!started) return;
        data.onGameTick(); ClueDataService.Snapshot reading=data.snapshot();
        boolean clueChanged=!Objects.equals(previousKey,reading.key);
        if (clueChanged)
        { if (!previousKey.isEmpty()) {navigation.clearOwned("clue");bankFocus.accept(java.util.Collections.emptySet());} previousKey=reading.key; }
        boolean bank=data.bankOpen();
        if(autoBank&&reading.profile!=null&&!reading.key.isEmpty()&&bank&&(clueChanged||!bankWasOpen))bankFocus.accept(targetItemIds());
        bankWasOpen=bank;
        ClueDataService.StashReference selected=selectedStash;
        if (selected!=null && selected.unit()!=null) data.observeBuilt(selected.unit());
        String next=reading.key+reading.profile+reading.instruction+reading.status;
        for (ClueDataService.Need need : reading.needs) next+=need.name+need.state;
        next+=reading.accessStatus;
        for (ClueAccessIndex.Access gate : reading.access) next+=gate.label+gate.state;
        if (reading.stash!=null) next+=data.stashState(reading.stash).label();
        if (selected!=null) next+=data.stashState(selected.unit()).label();
        if (!next.equals(fingerprint)) { fingerprint=next; if (visible) refresh(); }
    }

    public JPanel createPanel()
    {
        panel=SidebarUi.column(); panel.setName("clue-coach");
        javax.swing.JCheckBox followBank=new javax.swing.JCheckBox("Filter bank for current clue",autoBank);followBank.setOpaque(false);followBank.setForeground(SidebarUi.TEXT);
        followBank.addActionListener(event->{boolean value=followBank.isSelected();clientThread.invokeLater(()->{autoBank=value;bankWasOpen=false;if(config!=null)config.setConfiguration(ClueDataService.GROUP,"autoBank",value);if(!value)bankFocus.accept(java.util.Collections.emptySet());});});panel.add(followBank);
        panel.addHierarchyListener(event -> { if ((event.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0)
            { visible=panel.isShowing(); if (visible) refresh(); } });
        mode=new JComboBox<>(new String[]{"Current clue","STASH units"});
        mode.addActionListener(event -> { selectedStash=null; page=0; render(); }); panel.add(mode);
        body=SidebarUi.column(); panel.add(body); render(); return panel;
    }

    private void render()
    {
        if (body==null) return; body.removeAll();
        if (mode.getSelectedIndex()==0) renderClue(); else renderStashes();
        body.revalidate(); body.repaint();
    }

    private void renderClue()
    {
        ClueDataService.Snapshot reading=data.snapshot(); JPanel current=SidebarUi.section(reading.title);
        if (!reading.status.isEmpty()) current.add(SidebarUi.label(reading.status));
        if (!reading.instruction.isEmpty()) current.add(SidebarUi.heading(reading.instruction));
        if (!reading.enemy.isEmpty()) current.add(SidebarUi.label("Encounter · "+reading.enemy));
        for (String detail : reading.details) current.add(SidebarUi.label(detail)); body.add(current);
        if (!reading.access.isEmpty() || !reading.accessStatus.isEmpty())
        {
            JPanel access=SidebarUi.section("Access requirements");
            if (!reading.accessStatus.isEmpty()) access.add(SidebarUi.label(reading.accessStatus));
            for (ClueAccessIndex.Access gate : reading.access)
            {
                access.add(SidebarUi.heading(gate.label)); access.add(SidebarUi.label(gate.state));
                if (!gate.guideId.isEmpty()) access.add(SidebarUi.button("Quest Helper",() -> openGuide.accept(gate.guideId)));
                if (gate.itemId>0) access.add(itemButton(gate.itemId,"Item source",reading));
            }
            body.add(access);
        }
        if (!reading.needs.isEmpty())
        {
            JPanel supplies=SidebarUi.section("Prepare this step");
            for (ClueDataService.Need need : reading.needs)
            {
                GuideText name=SidebarUi.heading((need.fulfilled ? "✓ " : "○ ")+need.name); name.setForeground(need.fulfilled ? READY : MISSING);
                supplies.add(name); supplies.add(SidebarUi.label(need.state));
                if (need.itemId>0) supplies.add(itemButton(need.itemId,"Item sources",reading));
            }
            supplies.add(SidebarUi.button("Open loadouts",() -> openModule.accept("Loadouts"))); body.add(supplies);
            supplies.add(SidebarUi.button("Focus clue supplies",()->execute(reading,()->{bankFocus.accept(targetItemIds());openModule.accept("Bank & XP");})));
        }
        if (reading.stash!=null)
        {
            JPanel stash=SidebarUi.section("This step’s STASH"); stash.add(SidebarUi.label(data.stashState(reading.stash).label()));
            stash.add(SidebarUi.button("View STASH",() -> {
                for (ClueDataService.StashReference row : data.stashes()) if (row.key.equals(reading.stash.name())) selectedStash=row;
                mode.setSelectedIndex(1); // The mode listener clears selection, so restore the chosen unit afterward.
                for (ClueDataService.StashReference row : data.stashes()) if (row.key.equals(reading.stash.name())) selectedStash=row;
                render();
            })); body.add(stash);
        }
        if (!reading.destinations.isEmpty())
        {
            JPanel travel=SidebarUi.section("Travel to the step");
            int index=0;
            for (WorldPoint point : reading.destinations)
            {
                final String label=reading.destinations.size()==1 ? "Preview route" : "Preview destination "+(++index);
                travel.add(SidebarUi.button(label,() -> execute(reading,() -> {
                    navigation.preview(reading.title,point,reading.requirements); openModule.accept("Travel");
                })));
                travel.add(SidebarUi.button("Draw route",() -> execute(reading,() -> navigation.routeOwned("clue",point,reading.title,reading.requirements))));
            }
            travel.add(SidebarUi.button("Bank detour",() -> execute(reading,() -> {
                navigation.preview("Last observed bank",navigation.lastBank(),Collections.emptyList()); openModule.accept("Travel");
            })));
            body.add(travel);
        }
        body.add(SidebarUi.button("Treasure Trails reference",() -> LinkBrowser.browse("https://oldschool.runescape.wiki/w/Treasure_Trails")));
    }

    private void renderStashes()
    {
        ClueDataService.StashReference selected=selectedStash;
        if (selected!=null) { renderStash(selected); return; }
        JPanel finder=SidebarUi.section("STASH units");
        String query=search==null ? "" : search.getText(); int prior=filter==null ? 0 : filter.getSelectedIndex();
        search=new JTextField(query); finder.add(SidebarUi.field("Location",search));
        filter=new JComboBox<>(new String[]{"All units","Not observed","Not built","Not filled when observed"}); filter.setSelectedIndex(prior); finder.add(filter);
        finder.add(SidebarUi.button("Find units",() -> { page=0; render(); })); search.addActionListener(e -> { page=0; render(); }); body.add(finder);
        List<ClueDataService.StashReference> matches=new ArrayList<>();
        for (ClueDataService.StashReference row : data.stashes())
        {
            ClueDataService.StashState state=data.stashState(row.unit());
            if (!ClueDataService.normalize(row.title+" "+row.chartText).contains(ClueDataService.normalize(query))) continue;
            if (prior==1 && (state.built!=null || state.filled!=null) || prior==2 && !Boolean.FALSE.equals(state.built)
                || prior==3 && !Boolean.FALSE.equals(state.filled)) continue;
            matches.add(row);
        }
        matches.sort(Comparator.comparing(row -> row.title)); int pages=Math.max(1,(matches.size()+5)/6); page=Math.min(page,pages-1);
        body.add(SidebarUi.label(matches.size()+" units · page "+(page+1)+" / "+pages));
        for (int i=page*6;i<Math.min(matches.size(),page*6+6);i++)
        {
            ClueDataService.StashReference row=matches.get(i); JPanel card=SidebarUi.section(row.title);
            card.add(SidebarUi.label(data.stashState(row.unit()).label()));
            card.add(SidebarUi.button("View unit",() -> { selectedStash=row; render(); })); body.add(card);
        }
        JButton previous=SidebarUi.button("Previous",() -> { page--; render(); }); previous.setEnabled(page>0);
        JButton next=SidebarUi.button("Next",() -> { page++; render(); }); next.setEnabled(page+1<pages); body.add(SidebarUi.row(previous,next));
    }

    private void renderStash(ClueDataService.StashReference selected)
    {
        body.add(SidebarUi.button("← All STASH units",() -> { selectedStash=null; render(); }));
        JPanel card=SidebarUi.section(selected.title); card.add(SidebarUi.label(selected.chartText));
        ClueDataService.StashState state=data.stashState(selected.unit()); card.add(SidebarUi.heading(state.label()));
        if (state.observedAt>0) card.add(SidebarUi.label("Chart seen · "+java.time.Instant.ofEpochMilli(state.observedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()));
        card.add(SidebarUi.heading("Accepted item examples"));
        if (selected.itemIds!=null) for (int id : selected.itemIds) card.add(itemButton(id,"Accepted item example",null));
        STASHUnit unit=selected.unit();
        if (unit!=null) for (WorldPoint point : unit.getWorldPoints())
            card.add(SidebarUi.button("Preview STASH route",() -> execute(null,() -> {
                navigation.preview(selected.title,point,Collections.emptyList()); openModule.accept("Travel");
            })));
        card.add(SidebarUi.button("Construction reference",() -> LinkBrowser.browse("https://oldschool.runescape.wiki/w/STASH")));
        body.add(card);
    }

    private JButton itemButton(int id,String caption,ClueDataService.Snapshot expected)
    {
        String name="Item "+id;
        for (Map.Entry<String,List<Integer>> row : GuideItemResolver.nameIndex().entrySet()) if (row.getValue().contains(id)) { name=row.getKey(); break; }
        JButton button=SidebarUi.button(name,() -> { if (expected==null || Objects.equals(expected.key,data.snapshot().key)) openItem.accept(id); });
        button.setToolTipText(caption); if (items!=null && items.getImage(id)!=null) items.getImage(id).addTo(button); return button;
    }
    private void execute(ClueDataService.Snapshot expected,Runnable action)
    {
        long generation=epoch; String account=data.activeProfile();
        clientThread.invokeLater(() -> {
            if (started && generation==epoch && data.isCurrentProfile(account)
                && (expected==null || Objects.equals(expected.key,data.snapshot().key))) action.run();
        });
    }
    private void refresh()
    {
        if (panel==null || !refreshQueued.compareAndSet(false,true)) return; long generation=epoch;
        SwingUtilities.invokeLater(() -> { refreshQueued.set(false); if (generation==epoch) render(); });
    }
}
