/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import net.runelite.client.config.ConfigManager;

/** The short, actionable front page. Work details stay in their linked modules. */
@Singleton
final class CommandCenterTool
{
    static final class Card
    {final String title,detail,module;Card(String title,String detail,String module){this.title=title;this.detail=detail;this.module=module;}}
    static final class Reading
    {
        static final Reading EMPTY=new Reading(null,List.of(),List.of());
        final String profile;final List<Card> cards;final List<AccountProgressTool.Opportunity> suggestions;
        Reading(String profile,List<Card> cards,List<AccountProgressTool.Opportunity> suggestions)
        {this.profile=profile;this.cards=Collections.unmodifiableList(new ArrayList<>(cards));this.suggestions=Collections.unmodifiableList(new ArrayList<>(suggestions));}
    }
    private final ConfigManager config;private final Supplier<Reading> reader;
    private volatile Reading reading=Reading.EMPTY;private volatile boolean started;private volatile long epoch;
    private JPanel panel,body;private Consumer<String> open=id->{};private Consumer<AccountProgressTool.Opportunity> opportunity=o->{};
    private String fingerprint="";
    @Inject CommandCenterTool(SharedActivitySession activity,WorkflowPlansTool plans,LoadoutReadinessTool loadouts,FarmRunsTool farming,
        RoutinePlannerTool routines,SupplyAwarenessTool supplies,DeathRecoveryTool deaths,AccountProgressTool account,ConfigManager config)
    {this(()->collect(activity,plans,loadouts,farming,routines,supplies,deaths,account),config);}
    CommandCenterTool(Supplier<Reading> reader,ConfigManager config){this.reader=reader;this.config=config;}
    void setActions(Consumer<String> open,Consumer<AccountProgressTool.Opportunity> opportunity)
    {this.open=open==null?id->{}:open;this.opportunity=opportunity==null?o->{}:opportunity;}
    void start(){started=true;clear();}void stop(){started=false;clear();}
    void clear(){epoch++;reading=Reading.EMPTY;fingerprint="";refresh();}
    void onGameTick()
    {
        if(!started)return;long ticket=epoch;String owner=config.getRSProfileKey();Reading next=reader.get();
        if(ticket!=epoch||!started)return;
        if(next==null||owner==null||!owner.equals(next.profile)||!owner.equals(config.getRSProfileKey()))next=Reading.EMPTY;
        String key=key(next);reading=next;if(!key.equals(fingerprint)){fingerprint=key;refresh();}
    }
    private static Reading collect(SharedActivitySession activity,WorkflowPlansTool plans,LoadoutReadinessTool loadouts,FarmRunsTool farming,
        RoutinePlannerTool routines,SupplyAwarenessTool supplies,DeathRecoveryTool deaths,AccountProgressTool account)
    {
        SharedActivitySession.JournalView state=activity.journalView();String owner=state.session.profile;List<Card> next=new ArrayList<>();
        if(state.run!=null)
        {SharedActivitySession.RunRecord r=state.run;next.add(new Card(r.context.title,r.state+" · "+duration(r.activeMillis)+" active · "+String.format(Locale.ROOT,"%,d XP · %,d GP net",r.totalXp(),r.netLoot()),"Activity hub"));}
        WorkflowPlan plan=plans.selected();if(plan!=null&&plan.next()!=null)next.add(new Card("Next: "+plan.next().label,plan.name+" · "+plan.completed()+" / "+plan.steps.size(),"Plans"));
        LoadoutPlan setup=loadouts.selected();LoadoutReadiness check=loadouts.readiness();
        if(setup!=null&&check!=null)next.add(new Card(setup.name,readinessDetail(setup,check),"Loadouts"));
        FarmPatchPlanner.RunReading farm=farming.currentRun();if(farm!=null&&farm.active&&Objects.equals(owner,farm.profile))next.add(new Card(farm.title,farm.nextStopLabel+" · "+farm.completedStops+" / "+farm.totalStops+" stops","Farm runs"));
        long low=supplies.rows().stream().filter(SupplyAwarenessTool.Row::low).count();if(low>0)next.add(new Card("Low supplies",low+" ammunition, rune or charge reminders","Effects & supplies"));
        long claims=routines.watchedClaimables().stream().filter(c->c.status==ClaimableRoutineCatalog.Status.AVAILABLE).count();if(claims>0)next.add(new Card("Ready to collect",claims+" selected daily rewards","My routines"));
        DeathRecoveryTool.Reading death=deaths.snapshot();if(death.deaths.stream().anyMatch(d->!d.acknowledged))next.add(new Card("Recovery outstanding","Open your last death and recovery supplies","Recovery"));
        return new Reading(owner,next,account.opportunities());
    }
    static String readinessDetail(LoadoutPlan setup,LoadoutReadiness check)
    {
        if(check.ready)return setup.checks().isEmpty()?"✓ Items ready":"✓ Checklist ready";
        long missing=check.lines.stream().filter(l->!l.ready()).count();long departure=check.checks.stream().filter(c->c.state!=LoadoutReadiness.CheckState.READY).count();
        List<String> parts=new ArrayList<>();if(missing>0)parts.add(missing+" item checks");if(departure>0)parts.add(departure+" departure checks");
        if(!check.spellbookKnown)parts.add("Spellbook not observed");else if(!check.spellbookMatches)parts.add("Change spellbook");
        return parts.isEmpty()?"Readiness checks pending":String.join(" · ",parts);
    }
    List<Card> cards(){Reading current=current();return current.cards;}
    private Reading current(){Reading current=reading;return started&&current.profile!=null&&current.profile.equals(config.getRSProfileKey())?current:Reading.EMPTY;}
    JPanel createPanel(){if(panel!=null)return panel;panel=SidebarUi.column();panel.setName("command-center-now");panel.addHierarchyListener(e->{if(panel.isShowing())render();});body=SidebarUi.column();panel.add(body);render();return panel;}
    private void refresh(){SwingUtilities.invokeLater(()->{if(panel!=null&&panel.isShowing())render();});}
    private boolean owns(String owner,long ticket){return started&&ticket==epoch&&owner!=null&&owner.equals(config.getRSProfileKey())&&owner.equals(reading.profile);}
    private void render()
    {
        if(body==null)return;body.removeAll();body.add(SidebarUi.heading("COMMAND CENTER"));Reading current=current();String owner=current.profile;long ticket=epoch;
        JButton find=SidebarUi.button("Find a plan",()->{if(started)open.accept("Find a plan");});find.setEnabled(started);body.add(find);
        JPanel launch = SidebarUi.section("CHOOSE AN ACTIVITY");
        String[][] journeys = {{"Questing", "Guides"}, {"Training", "Bank & XP"},
            {"Slayer", "Slayer"}, {"Farm runs", "Farm runs"}, {"Bossing", "Boss academy"}};
        for (String[] journey : journeys)
        {
            long launchEpoch = epoch;
            JButton button = SidebarUi.button(journey[0] + " →", () -> {
                if (started && launchEpoch == epoch) open.accept(journey[1]);
            });
            button.setName("runewarden.start." + journey[0].toLowerCase(Locale.ROOT).replace(' ', '-'));
            button.setEnabled(started); launch.add(button);
        }
        body.add(launch);
        if(current.cards.isEmpty())body.add(SidebarUi.label(started?"Choose an activity, goal or plan to begin":"Command center paused"));
        for(Card c:current.cards)
        {JPanel row=SidebarUi.section(c.title);row.add(SidebarUi.label(c.detail));row.add(SidebarUi.wrappedButton("Open "+c.module,()->{if(owns(owner,ticket))open.accept(c.module);}));body.add(row);}
        for(AccountProgressTool.Opportunity o:current.suggestions.subList(0,Math.min(3,current.suggestions.size())))
        {JPanel card=SidebarUi.section(o.title);card.add(SidebarUi.label(o.detail));card.add(SidebarUi.button("View opportunity",()->{if(owns(owner,ticket))opportunity.accept(o);}));body.add(card);}
        SidebarUi.install(body);body.revalidate();body.repaint();
    }
    private static String key(Reading state)
    {
        StringBuilder key=new StringBuilder(String.valueOf(state.profile));for(Card c:state.cards)key.append('\u0001').append(c.title).append('\u0002').append(c.detail).append('\u0003').append(c.module);
        for(AccountProgressTool.Opportunity o:state.suggestions.subList(0,Math.min(3,state.suggestions.size())))key.append('\u0004').append(o.id).append('\u0005').append(o.detail);return key.toString();
    }
    private static String duration(long millis){long minutes=millis/60_000;return minutes>=60?minutes/60+"h "+minutes%60+"m":minutes+"m";}
}
