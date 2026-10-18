/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

/** Universal run controls, multi-skill measurements, milestones and comparable local history. */
@Singleton
public final class ActivityHubTool
{
    public static final class View
    {
        public final SharedActivitySession.Snapshot session;
        public final SharedActivitySession.RunRecord run;
        public final List<SharedActivitySession.RunRecord> history;
        View(SharedActivitySession.Snapshot session,SharedActivitySession.RunRecord run,List<SharedActivitySession.RunRecord> history)
        {this.session=session;this.run=run;this.history=history;}
    }
    private final Client client;private final ClientThread thread;private final ConfigManager config;private final SharedActivitySession session;
    private final Map<Skill,Integer> baselines=new EnumMap<>(Skill.class);
    private String profile,runId;private long startingTargetXp;private boolean started,visible;
    private Consumer<String> openGuide=id->{},openModule=id->{},openGoal=id->{};
    private final AtomicBoolean renderQueued=new AtomicBoolean();
    private volatile View view;
    private JPanel panel,current,historyRows;private JLabel historyCount,pageLabel;private JComboBox<String> filter,sort;private JTextField search;
    private SharedActivitySession.RunRecord selectedHistory;private int page;private long renderedRevision=-1,lastRenderTick=-1;
    @Inject public ActivityHubTool(Client client,ClientThread thread,ConfigManager config,SharedActivitySession session)
    {this.client=client;this.thread=thread;this.config=config;this.session=session;publish();}
    public void setOpenGuideAction(Consumer<String> action){openGuide=action==null?id->{}:action;}
    public void setOpenModuleAction(Consumer<String> action){openModule=action==null?id->{}:action;}
    public void setOpenGoalAction(Consumer<String> action){openGoal=action==null?id->{}:action;}
    public void start(){started=true;clear();}
    public void stop(){started=false;clear();}
    public void clear(){profile=runId=null;baselines.clear();startingTargetXp=0;publish();}
    public View view(){View v=view;return v.session.profile!=null&&!v.session.profile.equals(config.getRSProfileKey())?new View(session.snapshot(),null,Collections.emptyList()):v;}
    public void onGameTick()
    {
        if(!started)return;SharedActivitySession.Snapshot s=session.snapshot();
        if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null||s.profile==null){baselines.clear();publish();return;}
        if(!Objects.equals(profile,s.profile)){profile=s.profile;runId=null;baselines.clear();}
        bindRun(s);
        for(Skill skill:Skill.values())baselines.put(skill,client.getSkillExperience(skill));
        publish();if(visible&&client.getTickCount()-lastRenderTick>=2){lastRenderTick=client.getTickCount();queueRender();}
    }
    public void onGameStateChanged(GameStateChanged event)
    {if(event!=null&&event.getGameState()!=GameState.LOGGED_IN){baselines.clear();publish();queueRender();}}
    /** All observed skill deltas belong to the run, including secondary minigame XP and quest rewards. */
    public void onStatChanged(StatChanged event)
    {
        if(!started||event==null||event.getSkill()==null||client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null)return;
        SharedActivitySession.Snapshot s=session.snapshot();if(s.profile==null||!s.profile.equals(config.getRSProfileKey()))return;
        if(!Objects.equals(profile,s.profile)){profile=s.profile;runId=null;baselines.clear();}
        bindRun(s);Integer old=baselines.put(event.getSkill(),event.getXp());
        if(old==null||event.getXp()<=old||!s.running())return;
        session.observeXp(s.runId,event.getSkill(),(long)event.getXp()-old);
        int oldLevel=Math.min(99,Experience.getLevelForXp(old)),newLevel=Math.min(99,Experience.getLevelForXp(event.getXp()));
        if(newLevel>oldLevel)session.reachMilestone("level:"+event.getSkill()+":"+newLevel,event.getSkill().getName()+" level "+newLevel,"Goals");
        if(s.context!=null&&s.context.skill==event.getSkill()&&s.context.targetXp>startingTargetXp)
        {
            for(int percent:new int[]{25,50,75,100})
            {
                long threshold=startingTargetXp+(long)Math.ceil((s.context.targetXp-startingTargetXp)*percent/100d);
                if(event.getXp()>=threshold)session.reachMilestone("xp-target:"+percent,percent==100?"XP target reached":percent+"% of XP target","Goals");
            }
        }
        publish();queueRender();
    }
    private void bindRun(SharedActivitySession.Snapshot s)
    {
        if(Objects.equals(runId,s.runId))return;runId=s.runId;
        startingTargetXp=s.context==null||s.context.skill==null?0:baselines.getOrDefault(s.context.skill,client.getSkillExperience(s.context.skill));
    }
    private void publish(){SharedActivitySession.JournalView value=session.journalView();view=new View(value.session,value.run,value.history);}
    private void queueRender(){if(visible&&renderQueued.compareAndSet(false,true))SwingUtilities.invokeLater(()->{renderQueued.set(false);if(visible)render();});}
    /** A user-selected plan may start any activity through the same profile/run boundary as the hub. */
    public void startContext(SharedActivitySession.Context context)
    {SharedActivitySession.Snapshot expected=session.snapshot();thread.invoke(()->{if(same(expected)&&context!=null&&context.valid()){session.startActivity(context);publish();queueRender();}});}
    public void openGoal(String id){openGoal.accept(id);}
    private boolean same(SharedActivitySession.Snapshot expected)
    {SharedActivitySession.Snapshot now=session.snapshot();return started&&expected.profile!=null&&expected.profile.equals(config.getRSProfileKey())&&Objects.equals(expected.runId,now.runId);}
    private void control(SharedActivitySession.Snapshot expected,Runnable action)
    {thread.invoke(()->{if(same(expected)){action.run();publish();queueRender();}});}
    public JPanel createPanel()
    {
        if(panel!=null)return panel;panel=SidebarUi.column();panel.setName("activity-hub");current=SidebarUi.column();panel.add(current);
        JPanel manual=SidebarUi.column();JComboBox<SharedActivitySession.Kind> kind=new JComboBox<>(SharedActivitySession.Kind.values());skin(kind);manual.add(SidebarUi.field("Activity type",kind));
        JTextField title=new JTextField();skin(title);manual.add(SidebarUi.field("Run name",title));
        manual.add(SidebarUi.button("Start named run",()->{String name=title.getText().trim();if(name.isEmpty()||name.length()>180)return;
            startContext(new SharedActivitySession.Context((SharedActivitySession.Kind)kind.getSelectedItem(),"manual:"+java.util.UUID.randomUUID(),name,null,null,null,0));}));
        panel.add(SidebarUi.disclosure("Create a run",manual));
        JPanel milestone=SidebarUi.column();JTextField milestoneName=new JTextField();skin(milestoneName);milestone.add(SidebarUi.field("Milestone name",milestoneName));
        milestone.add(SidebarUi.button("Mark milestone",()->{String name=milestoneName.getText().trim();if(name.isEmpty()||name.length()>180)return;SharedActivitySession.Snapshot expected=session.snapshot();control(expected,()->session.reachMilestone("manual:"+java.util.UUID.randomUUID(),name,"Goals"));milestoneName.setText("");}));
        panel.add(SidebarUi.disclosure("Add a milestone",milestone));
        JCheckBox detect=new JCheckBox("<html>Start detected activities<br>automatically</html>",session.autoDetect());detect.setOpaque(false);detect.setForeground(SidebarUi.TEXT);detect.setFont(GuideText.FONT);
        detect.addActionListener(e->{boolean next=detect.isSelected();thread.invoke(()->session.setAutoDetect(next));});panel.add(detect);
        JPanel history=SidebarUi.section("Run history");historyCount=new JLabel();historyCount.setForeground(SidebarUi.GOLD);history.add(historyCount);
        search=new JTextField();search.setName("activity-history-search");skin(search);history.add(SidebarUi.field("Search runs",search));
        filter=new JComboBox<>(new String[]{"All activities","TRAINING","QUEST","MINIGAME","FARMING","BOSS","SLAYER","OTHER"});skin(filter);history.add(filter);
        sort=new JComboBox<>(new String[]{"Most recent","Most XP","Highest net loot","Longest active time"});skin(sort);history.add(sort);
        filter.addActionListener(e->{page=0;renderHistory();});sort.addActionListener(e->{page=0;renderHistory();});
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){changed();}public void removeUpdate(DocumentEvent e){changed();}public void changedUpdate(DocumentEvent e){changed();}private void changed(){page=0;renderHistory();}});
        historyRows=SidebarUi.column();history.add(historyRows);JButton previous=SidebarUi.button("‹",()->{page=Math.max(0,page-1);renderHistory();}),next=SidebarUi.button("›",()->{page++;renderHistory();});
        pageLabel=new JLabel();pageLabel.setForeground(SidebarUi.TEXT);history.add(SidebarUi.row(previous,pageLabel,next));panel.add(history);
        panel.addHierarchyListener(e->{if((e.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0){visible=panel.isShowing();if(visible){publish();render();}}});SidebarUi.install(panel);render();return panel;
    }
    private void render()
    {
        if(current==null)return;View v=view();current.removeAll();SharedActivitySession.Snapshot s=v.session;
        JPanel card=SidebarUi.section("Activity command");
        if(v.run==null){card.add(SidebarUi.heading("Ready for your next run"));if(s.selected!=null)card.add(SidebarUi.label("Prepared · "+s.selected.title));}
        else{card.add(SidebarUi.heading(v.run.context.title));card.add(SidebarUi.label(v.run.context.kind+" · "+s.state.name()));renderRecord(card,v.run,false);}
        JButton start=SidebarUi.wrappedButton(s.running()?"Start prepared as a new run":"Start prepared activity",()->control(s,session::startSelected));start.setEnabled(s.profile!=null&&s.selected!=null);card.add(start);
        if(v.run!=null&&s.state!=SharedActivitySession.State.FINISHED)
        {card.add(SidebarUi.row(SidebarUi.button(s.running()?"Pause":"Resume",()->control(s,s.running()?session::pause:session::resume)),SidebarUi.button("Finish run",()->control(s,session::finish))));}
        if(v.run!=null&&v.run.context.guideId!=null)card.add(SidebarUi.button("Open active guide",()->openGuide.accept(v.run.context.guideId)));
        if(s.selected!=null&&(v.run==null||!s.selected.id.equals(v.run.context.id)))card.add(SidebarUi.label("Next · "+s.selected.title));
        card.add(SidebarUi.row(SidebarUi.button("Goals",()->openModule.accept("Goals")),SidebarUi.button("Loot",()->openModule.accept("Run loot"))));current.add(card);
        renderHistory();panel.revalidate();panel.repaint();renderedRevision=s.revision;
    }
    private void renderRecord(JPanel card,SharedActivitySession.RunRecord run,boolean historic)
    {
        card.add(SidebarUi.label("Active "+TrainingProgressTool.time(run.activeMillis)+" · elapsed "+TrainingProgressTool.time(run.elapsedMillis)));
        card.add(SidebarUi.label(String.format(Locale.ROOT,"%,d XP · %,d GP net",run.totalXp(),run.netLoot())));
        if(run.activeMillis>=60_000)card.add(SidebarUi.label(String.format(Locale.ROOT,"%,.0f XP/h · %,d GP/h",run.xpPerHour(),run.netPerHour())));
        JPanel details=SidebarUi.column();for(Map.Entry<Skill,Long> xp:run.experience.entrySet())details.add(SidebarUi.label(xp.getKey().getName()+" · "+number(xp.getValue())+" XP"));
        if(run.actions>0||run.workingMillis>0)details.add(SidebarUi.label(number(run.actions)+" confirmed actions · "+TrainingProgressTool.time(run.workingMillis)+" working"));
        details.add(SidebarUi.label("Loot "+number(run.grossLoot)+" GP · costs "+number(run.costs)+" GP"));
        if(run.unpricedItems>0)details.add(SidebarUi.label(run.unpricedItems+" unpriced items"));
        for(Map.Entry<String,Long> source:run.lootSources.entrySet())details.add(SidebarUi.label(source.getKey()+" × "+number(source.getValue())));
        for(SharedActivitySession.Counter counter:run.outcomes.values())details.add(SidebarUi.label(counter.label+" · "+number(counter.value)));
        if(!run.milestones.isEmpty())details.add(SidebarUi.heading("Milestones"));
        for(SharedActivitySession.Milestone milestone:run.milestones)
            if(milestone.module==null)details.add(SidebarUi.label("✓ "+milestone.title));
            else details.add(SidebarUi.wrappedButton("✓ "+milestone.title,()->openModule.accept(milestone.module)));
        if(run.partial)details.add(SidebarUi.label("Continued from an earlier checkpoint"));
        card.add(SidebarUi.disclosure(historic?"Run breakdown":"XP, loot & milestones",details));
    }
    private void renderHistory()
    {
        if(historyRows==null)return;View v=view();historyRows.removeAll();
        if(selectedHistory!=null)
        {
            SharedActivitySession.RunRecord selected=selectedHistory;boolean found=false;for(SharedActivitySession.RunRecord r:v.history)if(r.runId.equals(selected.runId)){selected=r;found=true;break;}
            if(found){SharedActivitySession.RunRecord shown=selected;historyRows.add(SidebarUi.button("‹ Back to runs",()->{selectedHistory=null;renderHistory();}));
                historyRows.add(SidebarUi.heading(shown.context.title));renderRecord(historyRows,shown,true);
                if(shown.context.guideId!=null)historyRows.add(SidebarUi.button("Open guide",()->openGuide.accept(shown.context.guideId)));
                historyRows.add(SidebarUi.button("Prepare this activity",()->control(v.session,()->session.select(shown.context))));
                historyRows.add(SidebarUi.button("Remove saved run",()->control(v.session,()->{session.removeHistory(v.session.profile,shown.runId);selectedHistory=null;})));return;}
            selectedHistory=null;
        }
        String query=search==null?"":search.getText().trim().toLowerCase(Locale.ROOT);String kind=filter==null?"All activities":(String)filter.getSelectedItem();
        List<SharedActivitySession.RunRecord> matches=new ArrayList<>();for(SharedActivitySession.RunRecord run:v.history)
            if(("All activities".equals(kind)||run.context.kind.name().equals(kind))&&(query.isEmpty()||run.context.title.toLowerCase(Locale.ROOT).contains(query)))matches.add(run);
        int order=sort==null?0:sort.getSelectedIndex();if(order==1)matches.sort(Comparator.comparingLong(SharedActivitySession.RunRecord::totalXp).reversed());
        if(order==2)matches.sort(Comparator.comparingLong(SharedActivitySession.RunRecord::netLoot).reversed());if(order==3)matches.sort(Comparator.comparingLong((SharedActivitySession.RunRecord r)->r.activeMillis).reversed());
        int pages=Math.max(1,(matches.size()+5)/6);page=Math.max(0,Math.min(page,pages-1));historyCount.setText(matches.size()+" saved runs");pageLabel.setText((page+1)+" / "+pages);
        for(int i=page*6;i<Math.min(matches.size(),page*6+6);i++)
        {
            SharedActivitySession.RunRecord run=matches.get(i);historyRows.add(SidebarUi.wrappedButton(run.context.title+"\n"+number(run.totalXp())+" XP · "+number(run.netLoot())+" GP · "+TrainingProgressTool.time(run.activeMillis),()->{selectedHistory=run;renderHistory();}));
        }
        if(matches.isEmpty())historyRows.add(SidebarUi.label("No finished runs yet"));historyRows.revalidate();historyRows.repaint();
    }
    private static void skin(javax.swing.JComponent component)
    {component.setBackground(SidebarUi.CARD);component.setForeground(SidebarUi.TEXT);component.setFont(GuideText.FONT);
        if(component instanceof JTextField)((JTextField)component).setCaretColor(SidebarUi.GOLD);
        if(component instanceof JComboBox)((JComboBox<?>)component).setRenderer(new javax.swing.DefaultListCellRenderer(){@Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,Object value,int index,boolean selected,boolean focus){super.getListCellRendererComponent(list,value,index,selected,focus);setBackground(selected?new java.awt.Color(65,58,41):SidebarUi.CARD);setForeground(SidebarUi.TEXT);setFont(GuideText.FONT);return this;}});}
    private static String number(long value){return String.format(Locale.ROOT,"%,d",value);}
}
