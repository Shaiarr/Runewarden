/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.*;
import java.util.function.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.QuestState;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

/** Editable, ordered cross-module coaching plans with bounded safe saves and progress-free sharing. */
@Singleton
final class WorkflowPlansTool
{
    static final String KEY="workflowPlansV1";
    static final class Saved {int version=1;String selected;List<WorkflowPlan> plans=new ArrayList<>();}
    private final Client client;private final ClientThread thread;private final ConfigManager config;
    private final RunewardenDataStore data;private final Gson gson;private final AccountProgressTool progress;private final GuideCatalog guideCatalog;private final ItemKnowledgeCatalog itemCatalog;
    private final Deque<Saved> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private final Map<String,Quest> questIndex=new LinkedHashMap<>();private final Map<String,QuestState> nativeQuests=new LinkedHashMap<>();private int questCursor;
    private volatile Saved saved=new Saved();private volatile String profile;private volatile long epoch;private boolean started;
    private JPanel panel,body;private JTextField name;private JComboBox<WorkflowPlan> choices;private JTextArea code;
    private String message="";private WorkflowPlan preview;private boolean rendering;private int page;
    private String previewOwner,viewOwner,displayedPlan;private long previewEpoch,viewEpoch;
    private Consumer<String> module=id->{},guide=id->{};private IntConsumer item=id->{};
    private Consumer<WorldPoint> route=p->{};private Consumer<WorkflowPlan> start=p->{};
    private BiConsumer<Skill,Long> openSkill=(skill,target)->module.accept("Goals");private Consumer<String> openMethod=id->{};
    private BiConsumer<WorkflowPlan,WorkflowPlan.Step> completion=(plan,step)->{};
    private List<String> moduleChoices=List.of("Guides","Goals","Loadouts","Bank & XP","Training session","Farm runs","Slayer","Boss academy","Minigames","Travel","Item database");
    private volatile WorldPoint playerLocation;
    WorkflowPlansTool(Client client,ClientThread thread,ConfigManager config,RunewardenDataStore data,Gson gson,AccountProgressTool progress)
    {this(client,thread,config,data,gson,progress,null,null);}
    @Inject WorkflowPlansTool(Client client,ClientThread thread,ConfigManager config,RunewardenDataStore data,Gson gson,AccountProgressTool progress,GuideCatalog guideCatalog,ItemKnowledgeCatalog itemCatalog)
    {this.client=client;this.thread=thread;this.config=config;this.data=data;this.gson=gson;this.progress=progress;this.guideCatalog=guideCatalog;this.itemCatalog=itemCatalog;for(Quest q:Quest.values())questIndex.put(GoalCatalog.questKey(q.getName()),q);}
    void setOpenSkillAction(BiConsumer<Skill,Long> action){openSkill=action==null?(skill,target)->module.accept("Goals"):action;}
    void setOpenMethodAction(Consumer<String> action){openMethod=action==null?id->{}:action;}
    void setCompletionAction(BiConsumer<WorkflowPlan,WorkflowPlan.Step> action){completion=action==null?(plan,step)->{}:action;}
    void setModuleChoices(List<String> choices){List<String> valid=new ArrayList<>();if(choices!=null)for(String choice:choices)if(WorkflowPlan.text(choice,100)&&!valid.contains(choice)&&valid.size()<100)valid.add(choice);if(!valid.isEmpty())moduleChoices=List.copyOf(valid);}
    void setActions(Consumer<String> module,Consumer<String> guide,IntConsumer item,Consumer<WorldPoint> route,Consumer<WorkflowPlan> start)
    {this.module=module;this.guide=guide;this.item=item;this.route=route;this.start=start;}
    void start(){started=true;clear();}void stop(){started=false;clear();}
    void clear(){profile=null;epoch++;saved=new Saved();undo.clear();redo.clear();nativeQuests.clear();questCursor=0;playerLocation=null;preview=null;previewOwner=null;message="";page=0;displayedPlan=null;long ticket=epoch;
        SwingUtilities.invokeLater(()->{if(epoch==ticket){if(code!=null)code.setText("");if(name!=null)name.setText("");}});refresh();}
    void onGameTick()
    {
        if(!started)return;String current=config.getRSProfileKey();
        if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null||current==null){if(profile!=null)clear();return;}
        if(!current.equals(profile)){clear();profile=current;saved=data.read(RunewardenConfig.GROUP,profile,KEY,1,Saved.class,WorkflowPlansTool::valid,Saved::new);refresh();}
        playerLocation=client.getLocalPlayer().getWorldLocation();
        AccountProgressTool.Snapshot facts=progress.reading();if(!facts.live||!current.equals(facts.profile))return;
        Set<String> wanted=new LinkedHashSet<>();Map<Skill,Integer> xp=new EnumMap<>(Skill.class);
        for(WorkflowPlan plan:saved.plans)if(!plan.archived)for(WorkflowPlan.Step step:plan.steps)if(!step.completed)
        {if(step.kind==WorkflowPlan.Kind.QUEST)wanted.add(GoalCatalog.questKey(step.reference.substring(6)));else if(step.kind==WorkflowPlan.Kind.SKILL){Skill skill=GoalCatalog.skill(step.reference);if(skill!=null&&!xp.containsKey(skill))xp.put(skill,client.getSkillExperience(skill));}}
        List<String> questKeys=new ArrayList<>(wanted);for(int i=0;i<Math.min(8,questKeys.size());i++)
        {String key=questKeys.get(Math.floorMod(questCursor++,questKeys.size()));Quest quest=questIndex.get(key);if(quest!=null)try{nativeQuests.put(key,quest.getState(client));}catch(RuntimeException loading){}}
        Map<String,QuestState> observed=new LinkedHashMap<>(facts.quests);observed.putAll(nativeQuests);Set<String> completions=new HashSet<>();
        for(WorkflowPlan plan:saved.plans)if(!plan.archived)for(WorkflowPlan.Step step:plan.steps)if(!step.completed)
        {
            boolean met=step.kind==WorkflowPlan.Kind.QUEST&&observed.get(GoalCatalog.questKey(step.reference.substring(6)))==QuestState.FINISHED;
            if(step.kind==WorkflowPlan.Kind.SKILL){Skill skill=GoalCatalog.skill(step.reference);met=skill!=null&&xp.getOrDefault(skill,0)>=step.target;}
            if(met)completions.add(plan.id+"\n"+step.id);
        }
        if(completions.isEmpty())return;Saved next=copy(saved);for(WorkflowPlan plan:next.plans)for(WorkflowPlan.Step step:plan.steps)if(completions.contains(plan.id+"\n"+step.id)){step.completed=true;step.evidence="Observed in game";}
        if(data.write(RunewardenConfig.GROUP,profile,KEY,1,next)){saved=next;for(WorkflowPlan plan:next.plans)for(WorkflowPlan.Step step:plan.steps)if(completions.contains(plan.id+"\n"+step.id))completion.accept(plan.copy(true),step.copy(true));refresh();}
    }
    List<WorkflowPlan> plans(){if(!current())return List.of();List<WorkflowPlan> out=new ArrayList<>();for(WorkflowPlan p:saved.plans)out.add(p.copy(true));return Collections.unmodifiableList(out);}
    WorkflowPlan selected(){for(WorkflowPlan p:plans())if(p.id.equals(saved.selected))return p;return null;}
    void select(String id){mutate(next->{if(find(next,id)!=null)next.selected=id;});}
    void add(WorkflowPlan plan)
    {if(plan==null||!plan.valid())return;mutate(next->{if(next.plans.size()>=30)throw new IllegalArgumentException("30 plans saved");next.plans.add(plan.copy(false));next.selected=plan.id;});}
    void appendGuide(GuideCatalog.Entry entry)
    {
        if(entry==null)return;WorkflowPlan p=selected();
        WorkflowPlan.Step step=WorkflowPlan.step("quest".equals(entry.category)?WorkflowPlan.Kind.QUEST:WorkflowPlan.Kind.GUIDE,entry.title,entry.id);
        if(p==null)add(WorkflowPlan.create(entry.title,List.of(WorkflowPlan.step(WorkflowPlan.Kind.MODULE,"Prepare supplies","Loadouts"),step)));
        else mutate(next->{WorkflowPlan q=find(next,p.id);if(q!=null){if(q.steps.size()>=100)throw new IllegalArgumentException("100 steps in this plan");q.steps.add(step);}});
    }
    void complete(String planId,String stepId,boolean completed)
    {boolean[] changed={false};mutate(profile,epoch,next->{WorkflowPlan p=find(next,planId);if(p!=null)for(WorkflowPlan.Step s:p.steps)if(s.id.equals(stepId)){changed[0]=completed&&!s.completed;s.completed=completed;s.evidence=completed?"Marked complete":"";}},next->{if(changed[0]){WorkflowPlan p=find(next,planId);if(p!=null)for(WorkflowPlan.Step s:p.steps)if(s.id.equals(stepId))completion.accept(p.copy(true),s.copy(true));}});}
    void move(String planId,String stepId,int delta)
    {mutate(next->{WorkflowPlan p=find(next,planId);if(p!=null)for(int i=0;i<p.steps.size();i++)if(p.steps.get(i).id.equals(stepId)){int to=i+delta;if(to>=0&&to<p.steps.size())Collections.swap(p.steps,i,to);break;}});}
    void undo(){restore(undo,redo);}void redo(){restore(redo,undo);}
    private void restore(Deque<Saved> from,Deque<Saved> to)
    {String owner=profile;long ticket=epoch;thread.invokeLater(()->{if(!guard(owner,ticket)||from.isEmpty())return;Saved next=from.peekLast();if(data.write(RunewardenConfig.GROUP,owner,KEY,1,next)){from.removeLast();push(to,copy(saved));saved=next;message="";}else message="Open Backup & recovery to restore this save";refresh();});}
    private void mutate(Consumer<Saved> action)
    {
        mutate(profile,epoch,action);
    }
    private void mutate(String owner,long ticket,Consumer<Saved> action)
    {mutate(owner,ticket,action,next->{});}
    private void mutate(String owner,long ticket,Consumer<Saved> action,Consumer<Saved> after)
    {
        thread.invokeLater(()->{
            if(!guard(owner,ticket))return;Saved next=copy(saved);
            try{action.accept(next);if(!valid(next))throw new IllegalArgumentException("Invalid plan");
                if(data.write(RunewardenConfig.GROUP,owner,KEY,1,next)){push(undo,copy(saved));redo.clear();saved=next;message="";after.accept(next);}else message="Open Backup & recovery to restore this save";
            }catch(IllegalArgumentException invalid){message=invalid.getMessage();}refresh();
        });
    }
    private boolean current(){return started&&profile!=null&&profile.equals(config.getRSProfileKey());}
    private boolean uiGuard(String owner,long ticket){return current()&&epoch==ticket&&Objects.equals(profile,owner);}
    private Runnable bound(String owner,long ticket,Runnable action){return ()->{if(uiGuard(owner,ticket))action.run();};}
    private void dispatch(String owner,long ticket,Runnable action){thread.invokeLater(()->{if(guard(owner,ticket))action.run();});}
    private boolean guard(String owner,long ticket){return current()&&epoch==ticket&&Objects.equals(profile,owner)&&client.getGameState()==GameState.LOGGED_IN;}
    private static void push(Deque<Saved> to,Saved state){if(to.size()>=20)to.removeFirst();to.addLast(state);}
    private static Saved copy(Saved s){Saved n=new Saved();n.selected=s.selected;for(WorkflowPlan p:s.plans)n.plans.add(p.copy(true));return n;}
    static boolean valid(Saved s){if(s==null||s.version!=1||s.plans==null||s.plans.size()>30||s.selected!=null&&s.selected.length()>80)return false;Set<String> ids=new HashSet<>();for(WorkflowPlan p:s.plans)if(p==null||!p.valid()||!ids.add(p.id))return false;return s.selected==null||ids.contains(s.selected);}
    private static WorkflowPlan find(Saved state,String id){for(WorkflowPlan p:state.plans)if(p.id.equals(id))return p;return null;}
    JPanel createPanel()
    {
        if(panel!=null)return panel;panel=SidebarUi.column();panel.setName("workflow-plans");panel.addHierarchyListener(e->{if(panel.isShowing())render();});
        panel.add(SidebarUi.heading("YOUR PLANS"));choices=new JComboBox<>();choices.setRenderer(new DefaultListCellRenderer(){@Override public java.awt.Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){return super.getListCellRendererComponent(l,v instanceof WorkflowPlan?((WorkflowPlan)v).name:v,i,s,f);}});
        choices.addActionListener(e->{if(!rendering&&uiGuard(viewOwner,viewEpoch)){WorkflowPlan p=(WorkflowPlan)choices.getSelectedItem();if(p!=null)mutate(viewOwner,viewEpoch,n->n.selected=p.id);}});panel.add(choices);
        name=new JTextField();panel.add(SidebarUi.field("Plan name",name));
        panel.add(SidebarUi.button("Create plan",()->{if(!uiGuard(viewOwner,viewEpoch))return;String title=name.getText().trim();try{add(WorkflowPlan.create(title,List.of()));}catch(IllegalArgumentException ex){message="Enter a plan name";render();}}));
        panel.add(SidebarUi.row(SidebarUi.button("Undo",()->{if(uiGuard(viewOwner,viewEpoch))undo();}),SidebarUi.button("Redo",()->{if(uiGuard(viewOwner,viewEpoch))redo();})));body=SidebarUi.column();panel.add(body);
        JPanel transfer=SidebarUi.column();code=new JTextArea(5,12);code.setLineWrap(true);code.setWrapStyleWord(true);JScrollPane scroll=new JScrollPane(code);scroll.setPreferredSize(new java.awt.Dimension(170,110));scroll.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,110));transfer.add(scroll);
        transfer.add(SidebarUi.button("Export selected plan",()->{if(!uiGuard(viewOwner,viewEpoch))return;WorkflowPlan p=selected();if(p!=null)code.setText(WorkflowPlan.encode(p,gson));}));
        transfer.add(SidebarUi.button("Preview import",()->{if(!uiGuard(viewOwner,viewEpoch))return;try{preview=WorkflowPlan.decode(code.getText().trim(),gson);previewOwner=profile;previewEpoch=epoch;message="Import "+preview.name+" · "+preview.steps.size()+" steps · progress starts empty";}catch(IllegalArgumentException ex){preview=null;previewOwner=null;message=ex.getMessage();}render();}));
        transfer.add(SidebarUi.button("Import reviewed plan",()->{WorkflowPlan p=preview;if(p!=null&&uiGuard(previewOwner,previewEpoch)){preview=null;add(p);}else {preview=null;previewOwner=null;}}));panel.add(SidebarUi.disclosure("Share plans",transfer));render();return panel;
    }
    private void refresh(){SwingUtilities.invokeLater(()->{if(panel!=null&&panel.isShowing())render();});}
    private void render()
    {
        if(body==null)return;rendering=true;WorkflowPlan p=selected();viewOwner=profile;viewEpoch=epoch;choices.removeAllItems();for(WorkflowPlan q:plans()){choices.addItem(q);if(p!=null&&q.id.equals(p.id))choices.setSelectedItem(q);}if(p==null)choices.setSelectedIndex(-1);rendering=false;
        body.removeAll();if(!message.isEmpty())body.add(SidebarUi.label(message));if(!current()){body.add(SidebarUi.label("Log in to open your plans"));finish();return;}
        if(p==null){body.add(SidebarUi.label("Choose an opportunity or create a plan"));finish();return;}
        final WorkflowPlan plan=p;final String owner=profile;final long ticket=epoch;
        if(!Objects.equals(displayedPlan,plan.id)){displayedPlan=plan.id;page=0;}
        page=Math.max(0,Math.min(page,Math.max(0,(plan.steps.size()-1)/8)));
        body.add(SidebarUi.heading(p.completed()+" / "+p.steps.size()+" milestones"));
        if(p.next()!=null){JPanel next=SidebarUi.section("UP NEXT");next.add(SidebarUi.heading(p.next().label));next.add(SidebarUi.button("Open next step",bound(owner,ticket,()->open(owner,ticket,plan.id,plan.next()))));next.add(SidebarUi.button("Complete step →",bound(owner,ticket,()->complete(plan.id,plan.next().id,true))));body.add(next);}
        body.add(SidebarUi.button("Start this plan",bound(owner,ticket,()->dispatch(owner,ticket,()->{WorkflowPlan live=find(saved,plan.id);if(live!=null)start.accept(live.copy(true));}))));
        if(plan.steps.size()>8)body.add(pager(owner,ticket,plan.steps.size()));
        for(int index=page*8;index<Math.min(plan.steps.size(),page*8+8);index++)
        {
            WorkflowPlan.Step s=plan.steps.get(index);
            JPanel card=SidebarUi.section((s.completed?"✓ ":"○ ")+s.label);if(!s.evidence.isEmpty())card.add(SidebarUi.label(s.evidence));
            if(s.kind==WorkflowPlan.Kind.NOTE&&!s.reference.isBlank())card.add(SidebarUi.label(s.reference));
            if(s.kind==WorkflowPlan.Kind.SKILL)card.add(SidebarUi.label(String.format(Locale.ROOT,"%,d XP",s.target)));
            card.add(SidebarUi.row(SidebarUi.button("Open",bound(owner,ticket,()->open(owner,ticket,plan.id,s))),SidebarUi.button(s.completed?"Reopen":"Done",bound(owner,ticket,()->complete(plan.id,s.id,!s.completed)))));
            JPanel edit=SidebarUi.column();JTextField label=new JTextField(s.label);edit.add(SidebarUi.field("Step title",label));edit.add(SidebarUi.button("Rename",bound(owner,ticket,()->{String text=label.getText().trim();mutate(owner,ticket,n->{WorkflowPlan q=find(n,plan.id);if(q!=null)for(WorkflowPlan.Step t:q.steps)if(t.id.equals(s.id))t.label=text;});})));
            edit.add(SidebarUi.row(SidebarUi.button("↑ Move",bound(owner,ticket,()->move(plan.id,s.id,-1))),SidebarUi.button("↓ Move",bound(owner,ticket,()->move(plan.id,s.id,1)))));edit.add(SidebarUi.button("Remove step",bound(owner,ticket,()->mutate(owner,ticket,n->{WorkflowPlan q=find(n,plan.id);if(q!=null)q.steps.removeIf(t->t.id.equals(s.id));}))));card.add(SidebarUi.disclosure("Edit step",edit));body.add(card);
        }
        body.add(SidebarUi.disclosure("Add milestone",stepEditor(owner,ticket,plan.id)));
        JPanel manage=SidebarUi.column();JTextField rename=new JTextField(p.name);manage.add(SidebarUi.field("Plan title",rename));manage.add(SidebarUi.button("Rename plan",bound(owner,ticket,()->{String titleText=rename.getText().trim();mutate(owner,ticket,n->{WorkflowPlan q=find(n,plan.id);if(q!=null)q.name=titleText;});})));manage.add(SidebarUi.button("Reset progress",bound(owner,ticket,()->mutate(owner,ticket,n->{WorkflowPlan q=find(n,plan.id);if(q!=null)for(WorkflowPlan.Step s:q.steps){s.completed=false;s.evidence="";}}))));manage.add(SidebarUi.button("Remove plan",bound(owner,ticket,()->mutate(owner,ticket,n->{n.plans.removeIf(q->q.id.equals(plan.id));if(plan.id.equals(n.selected))n.selected=null;}))));body.add(SidebarUi.disclosure("Manage plan",manage));finish();
    }
    private JPanel stepEditor(String owner,long ticket,String planId)
    {
        JPanel editor=SidebarUi.column(),fields=SidebarUi.column();JComboBox<WorkflowPlan.Kind> kind=new JComboBox<>(WorkflowPlan.Kind.values());kind.setName("workflow.step-type");kind.setMaximumRowCount(8);
        JTextField title=new JTextField();title.setName("workflow.step-title");editor.add(SidebarUi.field("Milestone type",kind));editor.add(SidebarUi.field("Title · optional",title));editor.add(fields);
        java.util.concurrent.atomic.AtomicReference<Supplier<WorkflowPlan.Step>> draft=new java.util.concurrent.atomic.AtomicReference<>();
        Runnable choose=()->{
            fields.removeAll();WorkflowPlan.Kind selected=(WorkflowPlan.Kind)kind.getSelectedItem();
            switch(selected)
            {
                case MODULE:
                {
                    JComboBox<String> modules=new JComboBox<>(moduleChoices.toArray(new String[0]));modules.setName("workflow.module");modules.setMaximumRowCount(10);fields.add(SidebarUi.field("Open module",modules));
                    draft.set(()->WorkflowPlan.step(selected,(String)modules.getSelectedItem(),(String)modules.getSelectedItem()));break;
                }
                case SKILL:
                {
                    JComboBox<Skill> skills=new JComboBox<>(Skill.values());skills.setName("workflow.skill");skills.setMaximumRowCount(10);skills.setRenderer(renderer(value->value instanceof Skill?((Skill)value).getName():"Choose skill"));
                    JComboBox<String> mode=new JComboBox<>(new String[]{"Target level","Target XP"});mode.setName("workflow.target-mode");JSpinner target=new JSpinner(new SpinnerNumberModel(99,1,99,1));target.setName("workflow.target");
                    mode.addActionListener(event->target.setModel(mode.getSelectedIndex()==0?new SpinnerNumberModel(99,1,99,1):new SpinnerNumberModel(13_034_431L,0L,200_000_000L,1000L)));
                    fields.add(SidebarUi.field("Skill",skills));fields.add(SidebarUi.field("Goal",mode));fields.add(target);
                    draft.set(()->{commit(target);Skill skill=(Skill)skills.getSelectedItem();long xp=mode.getSelectedIndex()==0?net.runelite.api.Experience.getXpForLevel(((Number)target.getValue()).intValue()):((Number)target.getValue()).longValue();WorkflowPlan.Step step=WorkflowPlan.step(selected,skill.getName()+(mode.getSelectedIndex()==0?" level "+target.getValue():" · "+xp+" XP"),skill.name());step.target=xp;return step;});break;
                }
                case GUIDE:case QUEST:
                {
                    JTextField query=new JTextField();query.setName("workflow.guide-search");JComboBox<GuideCatalog.Entry> matches=new JComboBox<>();matches.setName("workflow.guide");matches.setMaximumRowCount(10);matches.setRenderer(renderer(value->value instanceof GuideCatalog.Entry?((GuideCatalog.Entry)value).title:"Choose guide"));
                    Runnable search=()->{matches.removeAllItems();if(guideCatalog!=null){List<GuideCatalog.Entry> found=guideCatalog.search(query.getText(),selected==WorkflowPlan.Kind.QUEST?"quest":"");for(int i=0;i<Math.min(30,found.size());i++)matches.addItem(found.get(i));}};
                    fields.add(SidebarUi.field(selected==WorkflowPlan.Kind.QUEST?"Find quest":"Find guide",query));fields.add(SidebarUi.button("Search guides",bound(owner,ticket,search)));fields.add(matches);query.addActionListener(event->{if(uiGuard(owner,ticket))search.run();});search.run();
                    draft.set(()->{GuideCatalog.Entry entry=(GuideCatalog.Entry)matches.getSelectedItem();if(entry==null)throw new IllegalArgumentException("Choose a guide");return WorkflowPlan.step(selected,entry.title,entry.id);});break;
                }
                case ITEM:
                {
                    JTextField query=new JTextField();query.setName("workflow.item-search");JComboBox<ItemKnowledgeCatalog.ItemRef> matches=new JComboBox<>();matches.setName("workflow.item");matches.setMaximumRowCount(10);matches.setRenderer(renderer(value->value instanceof ItemKnowledgeCatalog.ItemRef?((ItemKnowledgeCatalog.ItemRef)value).name:"Choose item"));
                    JSpinner quantity=new JSpinner(new SpinnerNumberModel(1,1,Integer.MAX_VALUE,1));quantity.setName("workflow.quantity");
                    Runnable search=()->{matches.removeAllItems();String needle=query.getText().trim();if(needle.length()<2)return;if(itemCatalog!=null&&itemCatalog.loaded()){List<ItemKnowledgeCatalog.ItemRef> found=itemCatalog.search(needle);for(int i=0;i<Math.min(30,found.size());i++)matches.addItem(found.get(i));}
                        else {String key=needle.toLowerCase(Locale.ROOT);Set<Integer> seen=new HashSet<>();for(int id:GuideItemResolver.resolveName(needle))if(seen.add(id))matches.addItem(new ItemKnowledgeCatalog.ItemRef(id,needle));for(Map.Entry<String,List<Integer>> entry:GuideItemResolver.nameIndex().entrySet())if(entry.getKey().contains(key))for(int id:entry.getValue())if(seen.add(id)&&matches.getItemCount()<30)matches.addItem(new ItemKnowledgeCatalog.ItemRef(id,entry.getKey()));}};
                    fields.add(SidebarUi.field("Find item",query));fields.add(SidebarUi.button("Search items",bound(owner,ticket,search)));fields.add(matches);fields.add(SidebarUi.field("Quantity",quantity));query.addActionListener(event->{if(uiGuard(owner,ticket))search.run();});
                    draft.set(()->{commit(quantity);ItemKnowledgeCatalog.ItemRef item=(ItemKnowledgeCatalog.ItemRef)matches.getSelectedItem();if(item==null)throw new IllegalArgumentException("Choose an item");WorkflowPlan.Step step=WorkflowPlan.step(selected,item.name,"");step.itemId=item.id;step.target=((Number)quantity.getValue()).longValue();return step;});break;
                }
                case DESTINATION:
                {
                    WorldPoint position=playerLocation;JSpinner x=new JSpinner(new SpinnerNumberModel(position==null?3200:position.getX(),1,16383,1)),y=new JSpinner(new SpinnerNumberModel(position==null?3200:position.getY(),1,16383,1)),plane=new JSpinner(new SpinnerNumberModel(position==null?0:position.getPlane(),0,3,1));x.setName("workflow.x");y.setName("workflow.y");plane.setName("workflow.plane");
                    fields.add(SidebarUi.field("World X",x));fields.add(SidebarUi.field("World Y",y));fields.add(SidebarUi.field("Floor",plane));fields.add(SidebarUi.button("Use current location",bound(owner,ticket,()->{WorldPoint point=playerLocation;if(point!=null){x.setValue(point.getX());y.setValue(point.getY());plane.setValue(point.getPlane());}})));
                    draft.set(()->{commit(x);commit(y);commit(plane);WorkflowPlan.Step step=WorkflowPlan.step(selected,"Destination "+x.getValue()+", "+y.getValue(),"");step.x=((Number)x.getValue()).intValue();step.y=((Number)y.getValue()).intValue();step.plane=((Number)plane.getValue()).intValue();return step;});break;
                }
                default:
                {
                    JTextArea note=new JTextArea(4,12);note.setName("workflow.note");note.setLineWrap(true);note.setWrapStyleWord(true);JScrollPane scroll=new JScrollPane(note);scroll.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,100));scroll.setPreferredSize(new java.awt.Dimension(170,100));fields.add(SidebarUi.field("Note",scroll));
                    draft.set(()->{if(note.getText().length()>250)throw new IllegalArgumentException("Keep the note within 250 characters");return WorkflowPlan.step(selected,"Note",note.getText());});break;
                }
            }
            SidebarUi.install(fields);fields.revalidate();fields.repaint();
        };
        kind.addActionListener(event->choose.run());choose.run();
        editor.add(SidebarUi.button("Add step",bound(owner,ticket,()->{try{WorkflowPlan.Step step=draft.get().get();if(!title.getText().trim().isEmpty())step.label=title.getText().trim();if(!step.valid())throw new IllegalArgumentException("Check the milestone details");mutate(owner,ticket,n->{WorkflowPlan plan=find(n,planId);if(plan!=null)plan.steps.add(step);});}catch(RuntimeException invalid){message=invalid.getMessage()==null?"Check the milestone details":invalid.getMessage();render();}})));return editor;
    }
    private static DefaultListCellRenderer renderer(Function<Object,String> text)
    {return new DefaultListCellRenderer(){@Override public java.awt.Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){super.getListCellRendererComponent(list,text.apply(value),index,selected,focus);setToolTipText(text.apply(value));return this;}};}
    private static void commit(JSpinner spinner){try{spinner.commitEdit();}catch(java.text.ParseException invalid){throw new IllegalArgumentException("Enter a valid number");}}
    private JPanel pager(String owner,long ticket,int total)
    {
        JButton previous=SidebarUi.button("Previous",bound(owner,ticket,()->{page=Math.max(0,page-1);render();}));previous.setEnabled(page>0);
        JButton next=SidebarUi.button("Next",bound(owner,ticket,()->{page++;render();}));next.setEnabled((page+1)*8<total);
        JPanel controls=SidebarUi.column();controls.add(SidebarUi.row(previous,next));controls.add(SidebarUi.label((page+1)+" / "+((total+7)/8)));return controls;
    }
    private void open(String owner,long ticket,String planId,WorkflowPlan.Step requested)
    {
        if(requested==null||!uiGuard(owner,ticket))return;
        dispatch(owner,ticket,()->{WorkflowPlan plan=find(saved,planId);if(plan==null)return;WorkflowPlan.Step step=null;for(WorkflowPlan.Step candidate:plan.steps)if(candidate.id.equals(requested.id)){step=candidate;break;}if(step==null)return;
            switch(step.kind){case MODULE:module.accept(step.reference);break;case GUIDE:case QUEST:if(plan.methodId!=null&&Objects.equals(plan.guideId,step.reference))openMethod.accept(plan.methodId);guide.accept(step.reference);break;case ITEM:item.accept(step.itemId);break;case DESTINATION:route.accept(step.point());module.accept("Travel");break;case SKILL:openSkill.accept(GoalCatalog.skill(step.reference),step.target);break;default:break;}});
    }
    private void finish(){SidebarUi.install(body);body.revalidate();body.repaint();}
}
