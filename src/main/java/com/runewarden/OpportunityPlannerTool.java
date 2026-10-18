/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.*;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

/** Account-aware plan discovery, backed by the actual method catalogue and typed prerequisites. */
@Singleton
final class OpportunityPlannerTool
{
    static final class Constraint
    {String party="Unchecked",risk="Unchecked";long gpPerHour=-1;}
    static final class Preferences
    {int version=1,minutes=30,targetLevel=99;String skill="Any skill",effort="Any effort",kind="Training",party="Any",risk="Any";long budget;boolean unchecked=true;Map<String,Constraint> methods=new LinkedHashMap<>();}
    static final class Recommendation
    {
        final String title,reason;final ActivityXpCatalog.Method method;final GuideCatalog.Entry guide;
        final List<GoalCatalog.Requirement> requirements;final int blocked,unknown;final boolean goal;
        final Double lowXp,highXp;final Long cost;
        Recommendation(String title,String reason,ActivityXpCatalog.Method method,GuideCatalog.Entry guide,List<GoalCatalog.Requirement> requirements,boolean goal,Double lowXp,Double highXp,Long cost)
        {this.title=title;this.reason=reason;this.method=method;this.guide=guide;this.requirements=List.copyOf(requirements);this.goal=goal;this.lowXp=lowXp;this.highXp=highXp;this.cost=cost;blocked=(int)requirements.stream().filter(r->r.state<0).count();unknown=(int)requirements.stream().filter(r->r.state==0).count();}
    }
    private final Client client;private final ClientThread thread;private final ConfigManager config;private final RunewardenDataStore data;
    private final GuideCatalog guides;private final ActivityXpCatalog methods;private final GoalCatalog goals;private final GoalPlannerTool personal;
    private final AccountProgressTool account;private final WorkflowPlansTool plans;
    private volatile String profile;private volatile long epoch;private boolean started;private int ticks,page;
    private volatile GoalCatalog.Facts facts;private volatile Preferences preferences=new Preferences();
    private volatile List<GoalPlan> goalSnapshot=List.of();private String query="",message="";
    private final Map<String,QuestState> questObservations=new LinkedHashMap<>();private int questCursor;
    private JPanel panel,results;private JTextField search,budget;private JComboBox<String> skill,effort,kind,party,risk;private String viewOwner;private long viewEpoch;
    private JSpinner minutes,targetLevel;private JCheckBox unchecked;private boolean rendering;
    private Consumer<String> module=id->{},openGuide=id->{},openMethod=id->{};
    @Inject OpportunityPlannerTool(Client client,ClientThread thread,ConfigManager config,RunewardenDataStore data,GuideCatalog guides,
        ActivityXpCatalog methods,GoalCatalog goals,GoalPlannerTool personal,AccountProgressTool account,WorkflowPlansTool plans)
    {this.client=client;this.thread=thread;this.config=config;this.data=data;this.guides=guides;this.methods=methods;this.goals=goals;this.personal=personal;this.account=account;this.plans=plans;}
    void setActions(Consumer<String> module,Consumer<String> guide,Consumer<String> method){this.module=module;openGuide=guide;openMethod=method;}
    void start(){started=true;clear();}void stop(){started=false;clear();}
    void clear(){profile=null;epoch++;facts=null;preferences=new Preferences();goalSnapshot=List.of();questObservations.clear();questCursor=0;page=0;ticks=0;query="";message="";long ticket=epoch;SwingUtilities.invokeLater(()->{if(epoch==ticket&&search!=null)search.setText("");});refresh(true);}
    GoalCatalog.Facts facts(){return current()?facts:null;}
    void onGameTick()
    {
        if(!started)return;String current=config.getRSProfileKey();
        if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null||current==null){if(profile!=null)clear();return;}
        if(!current.equals(profile)){clear();profile=current;preferences=data.read(RunewardenConfig.GROUP,profile,"opportunityPreferencesV1",1,Preferences.class,OpportunityPlannerTool::valid,Preferences::new);refresh(true);}
        Quest[] quests=Quest.values();for(int i=0;i<10&&quests.length>0;i++){Quest q=quests[Math.floorMod(questCursor++,quests.length)];try{questObservations.put(GoalCatalog.questKey(q.getName()),q.getState(client));}catch(RuntimeException loading){ }}
        if(ticks++%5!=0)return;AccountProgressTool.Snapshot observed=account.reading();if(!observed.live||!current.equals(observed.profile))return;
        Map<Skill,Integer> xp=new EnumMap<>(Skill.class);for(Skill s:Skill.values())if(s!=Skill.OVERALL)xp.put(s,client.getSkillExperience(s));
        Map<String,QuestState> states=new LinkedHashMap<>(observed.quests);states.putAll(questObservations);
        facts=new GoalCatalog.Facts(xp,observed.levels,states,client.getLocalPlayer().getCombatLevel());
        goalSnapshot=personal.goalPlans();refresh(false);
    }
    static boolean valid(Preferences p)
    {
        if(p==null||p.version!=1||p.minutes<5||p.minutes>720||p.targetLevel<2||p.targetLevel>99||p.budget<0||p.budget>100_000_000_000L||p.methods==null||p.methods.size()>500)return false;
        if(p.skill==null||p.effort==null||p.kind==null||p.party==null||p.risk==null)return false;
        if(!"Any skill".equals(p.skill)&&GoalCatalog.skill(p.skill)==null||!List.of("Any effort","AFK","Normal","Intensive").contains(p.effort)
            ||!List.of("Training","Quests","Both").contains(p.kind)||!List.of("Any","Solo","Team").contains(p.party)||!List.of("Any","Safe","Wilderness").contains(p.risk))return false;
        for(Map.Entry<String,Constraint> e:p.methods.entrySet())if(!WorkflowPlan.text(e.getKey(),250)||e.getValue()==null||e.getValue().party==null||e.getValue().risk==null||!List.of("Unchecked","Solo","Team","Either").contains(e.getValue().party)||!List.of("Unchecked","Safe","Wilderness").contains(e.getValue().risk)||e.getValue().gpPerHour< -1||e.getValue().gpPerHour>1_000_000_000)return false;return true;
    }
    List<Recommendation> recommendations(Preferences p,GoalCatalog.Facts f,List<GoalPlan> active,String query)
    {
        if(f==null||!valid(p))return List.of();List<Recommendation> out=new ArrayList<>();String needle=Objects.toString(query,"").toLowerCase(Locale.ROOT).trim();
        Skill selected=GoalCatalog.skill(p.skill);
        if(!"Quests".equals(p.kind))for(ActivityXpCatalog.Method m:methods.methods(null))
        {
            if(m.skill==null||m.level>p.targetLevel||selected!=null&&m.skill!=selected||!"Any effort".equals(p.effort)&&!p.effort.equals(GoalCatalog.intensity(m.intensity))||!m.name.toLowerCase(Locale.ROOT).contains(needle))continue;
            GuideCatalog.Entry g=guides.entry(m.guideId);List<GoalCatalog.Requirement> req=new ArrayList<>(goals.requirements(m,f));
            if(req.stream().anyMatch(r->r.skill==m.skill&&r.level>p.targetLevel))continue;
            Constraint c=p.methods.get(m.id);String extra="";Long cost=null;
            if(c!=null&&c.gpPerHour>=0){cost=Math.round(c.gpPerHour*p.minutes/60.0);if(p.budget>0&&cost>p.budget)continue;}
            else if(p.budget>0)extra=" · cost unchecked";
            if(!"Any".equals(p.party))
            {if(c==null||"Unchecked".equals(c.party)){if(!p.unchecked)continue;extra+=" · group size unchecked";}else if(!"Either".equals(c.party)&&!p.party.equals(c.party))continue;}
            if(!"Any".equals(p.risk))
            {if(c==null||"Unchecked".equals(c.risk)){if(!p.unchecked)continue;extra+=" · risk unchecked";}else if(!p.risk.equals(c.risk))continue;}
            if(!p.unchecked&&(req.stream().anyMatch(r->r.state==0)||p.budget>0&&cost==null))continue;
            boolean target=active.stream().anyMatch(goal->!goal.archived&&goal.skill()==m.skill&&goal.targetXp>f.xp(m.skill));
            ActivityXpCatalog.Rate rate=m.ratesAt(f.level(m.skill));Double low=null,high=null;
            if(rate!=null&&rate.low(f.level(m.skill))>0){low=rate.low(f.level(m.skill))*p.minutes/60.0;high=rate.high(f.level(m.skill))*p.minutes/60.0;}
            else if(m.minXpPerHour>0&&m.rates.isEmpty()){low=m.minXpPerHour*p.minutes/60.0;high=m.maxXpPerHour*p.minutes/60.0;}
            String why=(target?"Your "+m.skill.getName()+" goal · ":"")+GoalCatalog.intensity(m.intensity)+" · level "+m.level+extra;
            out.add(new Recommendation(m.name,why,m,g,req,target,low,high,cost));
        }
        if(!"Training".equals(p.kind))for(GuideCatalog.Entry g:guides.search(Objects.toString(query,""),"quest"))
        {
            if(f.finished(g))continue;List<GoalCatalog.Requirement> req=goals.requirements(g,f);if(!p.unchecked&&req.stream().anyMatch(r->r.state==0))continue;
            boolean target=active.stream().anyMatch(goal->!goal.archived&&g.id.equals(goal.questGuideId));
            out.add(new Recommendation(g.title,target?"Your quest goal":"Optimal quest route",null,g,req,target,null,null,null));
        }
        // Stable sorting keeps the authored optimal quest order within otherwise equal readiness.
        out.sort(Comparator.comparingInt((Recommendation r)->r.blocked==0?0:1).thenComparingInt(r->r.goal?0:1).thenComparingInt(r->r.unknown==0?0:1));
        return Collections.unmodifiableList(out);
    }
    WorkflowPlan build(Recommendation r,GoalCatalog.Facts f,int target)
    {
        if(r==null||f==null||target<2||target>99||r.method!=null&&r.method.level>target)throw new IllegalArgumentException("Choose a target that reaches this method's required level");
        List<WorkflowPlan.Step> steps=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(GoalPlan goal:goals.unlockGoals(r.requirements,f,List.of()))
        {
            if(goal.skill()!=null){WorkflowPlan.Step s=WorkflowPlan.step(WorkflowPlan.Kind.SKILL,goal.skill().getName()+" level "+Experience.getLevelForXp(goal.targetXp),goal.skill);s.target=goal.targetXp;if(seen.add(s.kind+s.reference+s.target))steps.add(s);}
            else if(goal.questGuideId!=null){GuideCatalog.Entry g=guides.entry(goal.questGuideId);if(g!=null&&seen.add(g.id))steps.add(WorkflowPlan.step(WorkflowPlan.Kind.QUEST,g.title,g.id));}
        }
        for(GoalCatalog.Requirement req:r.requirements)if(req.state==0)steps.add(WorkflowPlan.step(WorkflowPlan.Kind.NOTE,label("Check: "+req.label,180),""));
        steps.add(WorkflowPlan.step(WorkflowPlan.Kind.MODULE,"Prepare equipment and supplies","Loadouts"));
        if(r.guide!=null)steps.add(WorkflowPlan.step(r.method==null?WorkflowPlan.Kind.QUEST:WorkflowPlan.Kind.GUIDE,label(r.guide.title,180),r.guide.id));
        if(r.method!=null)
        {
            int from=Experience.getLevelForXp(f.xp(r.method.skill));
            for(WorkflowPlan.Step step:steps)if(step.kind==WorkflowPlan.Kind.SKILL&&GoalCatalog.skill(step.reference)==r.method.skill)
            {int prerequisite=Experience.getLevelForXp((int)step.target);if(prerequisite>target)throw new IllegalArgumentException("This method's unlocks exceed the requested target level");from=Math.max(from,prerequisite);}
            from=Math.max(from,r.method.level);int to=Math.max(from,Math.min(99,target));
            for(int level=Math.min(to,((from/5)+1)*5);from<to&&level<=to;level=Math.min(to,level+5))
            {WorkflowPlan.Step s=WorkflowPlan.step(WorkflowPlan.Kind.SKILL,r.method.skill.getName()+" level "+level,r.method.skill.name());s.target=Experience.getXpForLevel(level);steps.add(s);if(level==to)break;}
        }
        if(steps.size()>100)throw new IllegalArgumentException("This route exceeds 100 milestones; choose a smaller goal");
        WorkflowPlan plan=WorkflowPlan.create(label(r.title,100),steps);plan.guideId=r.guide==null?null:r.guide.id;
        if(r.method!=null){plan.methodId=r.method.id;plan.guideId=r.method.guideId;plan.trainingSkill=r.method.skill.name();plan.trainingTargetXp=Experience.getXpForLevel(target);}
        if(!plan.valid())throw new IllegalArgumentException("The selected method has invalid plan metadata");return plan;
    }
    private static String label(String text,int max){String safe=text.replace('<','‹').replace('>','›');return safe.length()<=max?safe:safe.substring(0,max-1)+"…";}
    JPanel createPanel()
    {
        if(panel!=null)return panel;panel=SidebarUi.column();panel.setName("opportunity-planner");panel.addHierarchyListener(e->{if(panel.isShowing())refresh(true);});panel.add(SidebarUi.heading("FIND A PLAN"));
        search=new JTextField();panel.add(SidebarUi.field("Activity or quest",search));kind=new JComboBox<>(new String[]{"Training","Quests","Both"});panel.add(SidebarUi.field("Plan type",kind));
        List<String> names=new ArrayList<>();names.add("Any skill");for(Skill s:Skill.values())if(s!=Skill.OVERALL)names.add(s.getName());skill=new JComboBox<>(names.toArray(new String[0]));panel.add(SidebarUi.field("Skill",skill));
        effort=new JComboBox<>(new String[]{"Any effort","AFK","Normal","Intensive"});panel.add(SidebarUi.field("Effort",effort));minutes=new JSpinner(new SpinnerNumberModel(30,5,720,5));targetLevel=new JSpinner(new SpinnerNumberModel(99,2,99,1));
        panel.add(SidebarUi.field("Minutes available",minutes));panel.add(SidebarUi.field("Target level",targetLevel));
        JPanel options=SidebarUi.column();budget=new JTextField("0");options.add(SidebarUi.field("GP budget · 0 = any",budget));party=new JComboBox<>(new String[]{"Any","Solo","Team"});risk=new JComboBox<>(new String[]{"Any","Safe","Wilderness"});options.add(SidebarUi.field("Group size",party));options.add(SidebarUi.field("Risk",risk));unchecked=new JCheckBox("Include unchecked options",true);options.add(unchecked);panel.add(SidebarUi.disclosure("Planning preferences",options));
        panel.add(SidebarUi.button("Find a plan",this::applyFilters));search.addActionListener(e->applyFilters());results=SidebarUi.column();panel.add(results);refresh(true);return panel;
    }
    private void applyFilters()
    {
        if(!guard(viewOwner,viewEpoch))return;try
        {
            Preferences next=new Preferences();next.skill=(String)skill.getSelectedItem();next.effort=(String)effort.getSelectedItem();next.kind=(String)kind.getSelectedItem();next.minutes=(Integer)minutes.getValue();next.targetLevel=(Integer)targetLevel.getValue();next.budget=Long.parseLong(budget.getText().replace(",","").trim());next.party=(String)party.getSelectedItem();next.risk=(String)risk.getSelectedItem();next.unchecked=unchecked.isSelected();next.methods=new LinkedHashMap<>(preferences.methods);if(!valid(next))throw new IllegalArgumentException();query=search.getText().trim();
            String owner=profile;long ticket=epoch;thread.invokeLater(()->{if(guard(owner,ticket)&&client.getGameState()==GameState.LOGGED_IN&&data.write(RunewardenConfig.GROUP,owner,"opportunityPreferencesV1",1,next)){preferences=next;page=0;message="";refresh(false);}});
        }catch(RuntimeException invalid){message="Check your time, target and budget";render();}
    }
    private boolean current(){return started&&profile!=null&&profile.equals(config.getRSProfileKey());}
    private boolean guard(String owner,long ticket){return current()&&epoch==ticket&&Objects.equals(profile,owner);}
    private void refresh(boolean controls){SwingUtilities.invokeLater(()->{if(panel==null)return;if(controls){rendering=true;viewOwner=profile;viewEpoch=epoch;Preferences p=preferences;skill.setSelectedItem(p.skill);effort.setSelectedItem(p.effort);kind.setSelectedItem(p.kind);minutes.setValue(p.minutes);targetLevel.setValue(p.targetLevel);budget.setText(Long.toString(p.budget));party.setSelectedItem(p.party);risk.setSelectedItem(p.risk);unchecked.setSelected(p.unchecked);rendering=false;}if(panel.isShowing()||controls)render();});}
    private void render()
    {
        if(results==null)return;results.removeAll();if(!message.isEmpty())results.add(SidebarUi.label(message));
        GoalCatalog.Facts f=facts;Preferences p=preferences;if(!current()||f==null){results.add(SidebarUi.label("Log in to find plans for your account"));finish();return;}
        List<Recommendation> found=recommendations(p,f,goalSnapshot,query);page=Math.max(0,Math.min(page,Math.max(0,(found.size()-1)/6)));results.add(SidebarUi.heading(found.size()+" options"));String owner=profile;long ticket=epoch;
        for(int i=page*6;i<Math.min(found.size(),page*6+6);i++)
        {
            Recommendation r=found.get(i);JPanel card=SidebarUi.section(r.title);card.add(SidebarUi.label(r.reason));card.add(SidebarUi.label(r.blocked>0?r.blocked+" unlocks needed":r.unknown>0?r.unknown+" access checks":"✓ Known requirements met"));
            if(r.lowXp!=null)card.add(SidebarUi.label(String.format(Locale.ROOT,"%,.0f–%,.0f XP / %d min · reference",r.lowXp,r.highXp,p.minutes)));
            if(r.cost!=null)card.add(SidebarUi.label(String.format(Locale.ROOT,"%,d GP / %d min · your cost",r.cost,p.minutes)));
            JPanel requirements=SidebarUi.column();for(GoalCatalog.Requirement req:r.requirements){requirements.add(SidebarUi.label(req.display()));if(req.questGuideId!=null)requirements.add(SidebarUi.button("Quest Helper",()->{if(guard(owner,ticket))openGuide.accept(req.questGuideId);}));}if(requirements.getComponentCount()>0)card.add(SidebarUi.disclosure("Requirements",requirements));
            card.add(SidebarUi.button("Build this plan",()->{if(guard(owner,ticket)){try{plans.add(build(r,f,p.targetLevel));module.accept("Plans");}catch(IllegalArgumentException invalid){message=invalid.getMessage();render();}}}));
            if(r.guide!=null)card.add(SidebarUi.button("quest".equals(r.guide.category)?"Open Quest Helper":"Open guide",()->{if(guard(owner,ticket))openGuide.accept(r.guide.id);}));if(r.method!=null){card.add(SidebarUi.button("Training estimate",()->{if(guard(owner,ticket))openMethod.accept(r.method.id);}));card.add(SidebarUi.disclosure("My method settings",methodSettings(r.method,owner,ticket)));}results.add(card);
        }
        if(found.size()>6){JButton previous=SidebarUi.button("Previous",()->{if(guard(owner,ticket)){page--;render();}}),next=SidebarUi.button("Next",()->{if(guard(owner,ticket)){page++;render();}});previous.setEnabled(page>0);next.setEnabled((page+1)*6<found.size());results.add(SidebarUi.row(previous,next));results.add(SidebarUi.label((page+1)+" / "+((found.size()+5)/6)));}
        finish();
    }
    private JPanel methodSettings(ActivityXpCatalog.Method method,String owner,long ticket)
    {
        Constraint old=preferences.methods.getOrDefault(method.id,new Constraint());JPanel p=SidebarUi.column();JComboBox<String> group=new JComboBox<>(new String[]{"Unchecked","Solo","Team","Either"}),danger=new JComboBox<>(new String[]{"Unchecked","Safe","Wilderness"});group.setSelectedItem(old.party);danger.setSelectedItem(old.risk);JTextField cost=new JTextField(old.gpPerHour<0?"":Long.toString(old.gpPerHour));p.add(SidebarUi.field("Group size · personal",group));p.add(SidebarUi.field("Risk · personal",danger));p.add(SidebarUi.field("Your GP cost per hour",cost));
        p.add(SidebarUi.button("Save method settings",()->{try{Constraint c=new Constraint();c.party=(String)group.getSelectedItem();c.risk=(String)danger.getSelectedItem();c.gpPerHour=cost.getText().trim().isEmpty()?-1:Long.parseLong(cost.getText().replace(",","").trim());thread.invokeLater(()->{if(!guard(owner,ticket))return;Preferences n=new Preferences();Preferences oldPreferences=preferences;n.minutes=oldPreferences.minutes;n.targetLevel=oldPreferences.targetLevel;n.skill=oldPreferences.skill;n.effort=oldPreferences.effort;n.kind=oldPreferences.kind;n.party=oldPreferences.party;n.risk=oldPreferences.risk;n.budget=oldPreferences.budget;n.unchecked=oldPreferences.unchecked;n.methods=new LinkedHashMap<>(oldPreferences.methods);n.methods.put(method.id,c);if(valid(n)&&data.write(RunewardenConfig.GROUP,owner,"opportunityPreferencesV1",1,n)){preferences=n;refresh(false);}});}catch(RuntimeException ex){message="Enter a non-negative hourly cost";render();}}));return p;
    }
    private void finish(){SidebarUi.install(results);results.revalidate();results.repaint();}
}
