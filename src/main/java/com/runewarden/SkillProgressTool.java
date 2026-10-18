/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.util.Text;

/** Independent live skill goals and measured session statistics, including native skill-tab entry points. */
@Singleton
public final class SkillProgressTool
{
    static final String GROUP="runewardenskills",KEY="goalsV1";
    private static final Pattern GUIDE=Pattern.compile("^View ([A-Za-z]+) guide$");
    private final Client client;
    private final ClientThread thread;
    private final ConfigManager config;
    private final RunewardenDataStore store;
    private final SkillIconManager icons;
    private final SkillProgressOverlay hud;
    private final SkillTabProgressOverlay skillTab;
    private final EnumMap<Skill,SkillProgressSession> sessions=new EnumMap<>(Skill.class);
    private Saved saved=new Saved();
    private volatile View view=View.EMPTY;
    private volatile Preferences preferences=new Preferences();
    private volatile boolean started,paused;
    private String profile;
    private int epoch;
    private volatile Skill selected=Skill.AGILITY;
    private Skill latest;
    private volatile Runnable openAction=()->{};
    private JPanel panel;
    private JComboBox<Skill> skillChoice;
    private GuideText currentLabel,sessionLabel,goalLabel,errorLabel;
    private JProgressBar progress;
    private boolean rendering;

    static final class Goal
    {
        int targetXp,startXp;
        Goal(){}
        Goal(int target,int start){targetXp=target;startXp=Math.min(start,Math.max(0,target-1));}
        boolean valid(){return targetXp>0&&targetXp<=Experience.MAX_SKILL_XP&&startXp>=0&&startXp<targetXp;}
    }
    static final class Saved
    {
        int version=1;Map<String,Goal> goals=new LinkedHashMap<>();
        boolean valid()
        {
            if(version!=1||goals==null||goals.size()>Skill.values().length)return false;
            for(Map.Entry<String,Goal> row:goals.entrySet())
            {try{Skill.valueOf(row.getKey());}catch(RuntimeException unknown){return false;}if(row.getValue()==null||!row.getValue().valid())return false;}
            return true;
        }
    }
    static final class Preferences
    {
        boolean hud=true,follow=true,session=true,remaining=true,eta=true,context=true,tabBars=true,tabTooltips=true;
        int idleTicks=100,rows=1;
    }
    static final class Row
    {
        final Skill skill;
        final int xp,level,boosted,target,start,remaining;
        final long gained,rate,eta;
        final boolean explicit,paused;
        Row(Skill skill,SkillProgressSession session,Goal goal,boolean paused)
        {
            this.skill=skill;xp=session.xp;level=session.level;boosted=session.boosted;gained=session.gained;rate=session.rate();
            explicit=goal!=null;
            int virtual=Experience.getLevelForXp(xp),next=Math.min(99,virtual+1);
            target=goal==null?Experience.getXpForLevel(next):goal.targetXp;
            start=goal==null?Experience.getXpForLevel(Math.min(99,virtual)):goal.startXp;
            remaining=Math.max(0,target-xp);eta=session.eta(remaining);this.paused=paused;
        }
        double fraction(){return target<=start?1:Math.max(0,Math.min(1,(xp-start)/(double)(target-start)));}
        String targetLabel()
        {int targetLevel=Experience.getLevelForXp(target);return Experience.getXpForLevel(targetLevel)==target?"Level "+targetLevel:format(target)+" XP";}
    }
    static final class View
    {
        static final View EMPTY=new View(null,Collections.emptyMap(),Collections.emptyList());
        final String profile;final Map<Skill,Row> skills;final List<Row> hud;
        View(String profile,Map<Skill,Row> rows,List<Row> hud)
        {this.profile=profile;EnumMap<Skill,Row> copied=new EnumMap<>(Skill.class);copied.putAll(rows);skills=Collections.unmodifiableMap(copied);this.hud=Collections.unmodifiableList(hud);}
    }
    @Inject public SkillProgressTool(Client client,ClientThread thread,ConfigManager config,RunewardenDataStore store,SkillIconManager icons,RunewardenConfig hudConfig,net.runelite.client.ui.overlay.tooltip.TooltipManager tooltips)
    {this.client=client;this.thread=thread;this.config=config;this.store=store;this.icons=icons;hud=new SkillProgressOverlay(client,this,hudConfig,icons);skillTab=new SkillTabProgressOverlay(client,this,hudConfig,tooltips);}
    public void setOpenAction(Runnable action){openAction=action==null?()->{}:action;}
    public void start()
    {started=true;int expected=++epoch;reloadPreferences();thread.invoke(()->{if(started&&expected==epoch)onGameTick();});}
    public void stop(){started=false;epoch++;sessions.clear();latest=null;view=View.EMPTY;paused=false;refreshPanel();}
    public void clear(){epoch++;sessions.clear();latest=null;profile=null;saved=new Saved();view=View.EMPTY;paused=false;refreshPanel();}
    public SkillProgressOverlay hud(){return hud;}
    public SkillTabProgressOverlay skillTab(){return skillTab;}
    public void setHudLocked(boolean locked){hud.setHudLocked(locked);}
    View reading(){View value=view;return started&&Objects.equals(value.profile,config.getRSProfileKey())?value:View.EMPTY;}
    Preferences preferences(){return preferences;}
    public void onGameTick()
    {
        if(!started)return;
        String current=config.getRSProfileKey();
        if(!Objects.equals(profile,current))
        {profile=current;sessions.clear();latest=null;saved=profile==null?new Saved():store.read(GROUP,profile,KEY,1,Saved.class,Saved::valid,Saved::new);reloadPreferences();paused=false;}
        if(!ready()){view=View.EMPTY;refreshPanel();return;}
        int tick=client.getTickCount();
        for(Skill skill:Skill.values())
        {
            SkillProgressSession session=sessions.computeIfAbsent(skill,key->new SkillProgressSession());
            if(session.observe(client.getSkillExperience(skill),client.getRealSkillLevel(skill),client.getBoostedSkillLevel(skill),tick,!paused)&&preferences.follow)latest=skill;
            session.clock(tick,preferences.idleTicks,paused);
        }
        publish();
    }
    public void onStatChanged(StatChanged event)
    {
        if(!ready()||event.getSkill()==null)return;
        SkillProgressSession session=sessions.computeIfAbsent(event.getSkill(),key->new SkillProgressSession());
        if(session.observe(event.getXp(),event.getLevel(),event.getBoostedLevel(),client.getTickCount(),!paused)&&preferences.follow)latest=event.getSkill();
        publish();
    }
    public void onGameStateChanged(GameStateChanged event)
    {
        if(!started)return;
        if(event.getGameState()==GameState.HOPPING||event.getGameState()==GameState.LOGIN_SCREEN||event.getGameState()==GameState.CONNECTION_LOST)
        {for(SkillProgressSession session:sessions.values())session.suspend();view=View.EMPTY;refreshPanel();}
    }
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if(!ready()||!preferences.context||WidgetUtil.componentToInterface(event.getActionParam1())!=InterfaceID.STATS||event.getOption()==null)return;
        Matcher matcher=GUIDE.matcher(Text.removeTags(event.getOption()));if(!matcher.matches())return;
        Skill skill=skill(matcher.group(1));if(skill==null)return;
        // Preserve the native guide and add a separate RuneLite-only client action.
        MenuEntry[] entries=client.getMenuEntries();
        if(entries!=null)for(MenuEntry entry:entries)if("Runewarden goal".equals(entry.getOption())&&skill.getName().equals(Text.removeTags(entry.getTarget())))return;
        int expected=epoch;String expectedProfile=profile;
        client.createMenuEntry(-1).setOption("Runewarden goal").setTarget(skill.getName()).setType(MenuAction.RUNELITE).onClick(entry->{
            if(!ready()||epoch!=expected||!Objects.equals(expectedProfile,config.getRSProfileKey()))return;
            selected=skill;latest=skill;config.setConfiguration(GROUP,"selected",skill.name());publish();SwingUtilities.invokeLater(()->{if(started&&epoch==expected&&Objects.equals(expectedProfile,config.getRSProfileKey()))openAction.run();});
        });
    }
    private boolean ready(){return started&&profile!=null&&Objects.equals(profile,config.getRSProfileKey())&&client.getGameState()==GameState.LOGGED_IN&&client.getLocalPlayer()!=null;}
    private void publish()
    {
        if(!ready()){view=View.EMPTY;refreshPanel();return;}
        EnumMap<Skill,Row> rows=new EnumMap<>(Skill.class);int tick=client.getTickCount();
        for(Map.Entry<Skill,SkillProgressSession> entry:sessions.entrySet())if(entry.getValue().initialized)
            rows.put(entry.getKey(),new Row(entry.getKey(),entry.getValue(),saved.goals.get(entry.getKey().name()),paused||entry.getValue().idle(tick,preferences.idleTicks)));
        List<Row> cards=new ArrayList<>();Skill primarySkill=preferences.follow&&latest!=null?latest:selected;Row primary=rows.get(primarySkill);if(primary!=null)cards.add(primary);
        List<Skill> recent=new ArrayList<>(rows.keySet());recent.sort(Comparator.comparingInt((Skill skill)->sessions.get(skill).lastGainTick).reversed());
        for(Skill skill:recent)if(cards.size()<preferences.rows&&skill!=primarySkill&&(rows.get(skill).gained>0||saved.goals.containsKey(skill.name())))cards.add(rows.get(skill));
        view=new View(profile,rows,cards);refreshPanel();
    }
    private void setGoal(Skill skill,int target)
    {
        if(!ready()||skill==null||target<=0||target>Experience.MAX_SKILL_XP)return;
        Row row=reading().skills.get(skill);if(row==null)return;
        saved.goals.put(skill.name(),new Goal(target,row.xp));store.write(GROUP,profile,KEY,1,saved);selected=skill;latest=skill;publish();
    }
    private void clearGoal(Skill skill)
    {if(!ready()||skill==null)return;saved.goals.remove(skill.name());store.write(GROUP,profile,KEY,1,saved);publish();}
    private void reloadPreferences()
    {
        Preferences p=new Preferences();p.hud=setting("hud");p.follow=setting("follow");p.session=setting("session");p.remaining=setting("remaining");p.eta=setting("eta");p.context=setting("context");p.tabBars=setting("tabBars");p.tabTooltips=setting("tabTooltips");
        p.rows=number("rows",1,1,4);int seconds=number("idleSeconds",60,0,600);p.idleTicks=(int)Math.ceil(seconds/0.6);preferences=p;String choice=config.getConfiguration(GROUP,"selected");if(choice!=null)try{selected=Skill.valueOf(choice);}catch(IllegalArgumentException ignored){}
    }
    private boolean setting(String key){return !"false".equals(config.getConfiguration(GROUP,key));}
    private int number(String key,int fallback,int min,int max)
    {try{String raw=config.getConfiguration(GROUP,key);return raw==null?fallback:Math.max(min,Math.min(max,Integer.parseInt(raw)));}catch(NumberFormatException invalid){return fallback;}}
    private void queue(Runnable action)
    {int expected=epoch;String expectedProfile=profile;thread.invoke(()->{if(started&&epoch==expected&&Objects.equals(expectedProfile,config.getRSProfileKey()))action.run();});}
    public JPanel createPanel()
    {
        panel=SidebarUi.column();JPanel status=SidebarUi.section("Skills progress");skillChoice=new JComboBox<>(Skill.values());skillChoice.setSelectedItem(selected);skillChoice.setRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean chosen,boolean focus){super.getListCellRendererComponent(list,value,index,chosen,focus);if(value instanceof Skill){Skill skill=(Skill)value;setText(skill.getName());java.awt.image.BufferedImage image=icons.getSkillImage(skill,true);setIcon(image==null?null:new ImageIcon(image));}return this;}});
        skillChoice.addActionListener(event->{if(rendering)return;Skill skill=(Skill)skillChoice.getSelectedItem();queue(()->{selected=skill;latest=skill;config.setConfiguration(GROUP,"selected",skill.name());publish();});});status.add(SidebarUi.field("Skill",skillChoice));currentLabel=SidebarUi.label("");goalLabel=SidebarUi.label("");sessionLabel=SidebarUi.label("");progress=new JProgressBar(0,1000);progress.setStringPainted(true);progress.setForeground(SidebarUi.GOLD);progress.setBackground(SidebarUi.BACKGROUND);status.add(currentLabel);status.add(progress);status.add(goalLabel);status.add(sessionLabel);
        status.add(SidebarUi.button("Pause / resume tracking",()->queue(()->{paused=!paused;publish();})));status.add(SidebarUi.button("Reset this skill session",()->{Skill skill=(Skill)skillChoice.getSelectedItem();queue(()->{SkillProgressSession session=sessions.get(skill);if(session!=null)session.reset();publish();});}));panel.add(status);
        JPanel goal=SidebarUi.section("Skill goal");JSpinner level=new JSpinner(new SpinnerNumberModel(99,2,126,1));goal.add(SidebarUi.field("Target level (up to virtual 126)",level));goal.add(SidebarUi.button("Set level goal",()->{Skill skill=(Skill)skillChoice.getSelectedItem();int target=Experience.getXpForLevel((Integer)level.getValue());queue(()->setGoal(skill,target));}));JTextField xp=new JTextField();goal.add(SidebarUi.field("Or target XP",xp));errorLabel=SidebarUi.label("");errorLabel.setForeground(new java.awt.Color(237,132,105));goal.add(errorLabel);goal.add(SidebarUi.button("Set XP goal",()->{try{int target=Integer.parseInt(xp.getText().replace(",","").trim());if(target<=0||target>Experience.MAX_SKILL_XP)throw new NumberFormatException();Skill skill=(Skill)skillChoice.getSelectedItem();errorLabel.setText("");queue(()->setGoal(skill,target));}catch(NumberFormatException invalid){errorLabel.setText("Enter XP between 1 and 200,000,000.");}}));goal.add(SidebarUi.button("Clear goal · use next level",()->{Skill skill=(Skill)skillChoice.getSelectedItem();queue(()->clearGoal(skill));}));panel.add(goal);
        JPanel options=SidebarUi.column();toggle(options,"Show HUD","hud",preferences.hud);toggle(options,"Follow latest trained skill","follow",preferences.follow);toggle(options,"Show session and rate","session",preferences.session);toggle(options,"Show remaining XP","remaining",preferences.remaining);toggle(options,"Show measured ETA","eta",preferences.eta);toggle(options,"Skill-tab goal menu","context",preferences.context);toggle(options,"Skill-tab progress bars","tabBars",preferences.tabBars);toggle(options,"Skill-tab progress tooltips","tabTooltips",preferences.tabTooltips);
        JSpinner rows=new JSpinner(new SpinnerNumberModel(preferences.rows,1,4,1));rows.addChangeListener(event->{config.setConfiguration(GROUP,"rows",rows.getValue());queue(()->{reloadPreferences();publish();});});options.add(SidebarUi.field("HUD skill cards",rows));JSpinner idle=new JSpinner(new SpinnerNumberModel((int)Math.round(preferences.idleTicks*.6),0,600,15));idle.addChangeListener(event->{config.setConfiguration(GROUP,"idleSeconds",idle.getValue());queue(()->{reloadPreferences();publish();});});options.add(SidebarUi.field("Pause after idle seconds (0 = never)",idle));panel.add(SidebarUi.disclosure("Display and session options",options));SidebarUi.install(panel);refreshPanel();return panel;
    }
    private void toggle(JPanel target,String text,String key,boolean selected)
    {JCheckBox box=new JCheckBox(text,selected);box.setFont(GuideText.FONT);box.setOpaque(false);box.setForeground(SidebarUi.TEXT);box.setName("skillProgress"+key);box.addActionListener(event->{config.setConfiguration(GROUP,key,box.isSelected());queue(()->{reloadPreferences();publish();});});target.add(box);}
    private void refreshPanel()
    {
        if(panel==null)return;View reading=reading();Skill skill=selected;Row row=reading.skills.get(skill);int expected=epoch;String expectedProfile=profile;JPanel target=panel;
        SwingUtilities.invokeLater(()->{if(panel!=target||epoch!=expected||!Objects.equals(expectedProfile,config.getRSProfileKey()))return;rendering=true;try{skillChoice.setSelectedItem(skill);currentLabel.setText(row==null?"Log in to view skill progress":row.skill.getName()+" · Level "+row.level+(row.boosted!=row.level?" ("+row.boosted+")":"")+"\n"+format(row.xp)+" XP");goalLabel.setText(row==null?"":"Goal: "+row.targetLabel()+"\n"+format(row.remaining)+" XP remaining");sessionLabel.setText(row==null?"":format(row.gained)+" session XP · "+(row.rate>0?format(row.rate)+" XP/hr":"Measuring rate")+"\n"+(row.paused?"Paused · ":"")+"ETA "+eta(row.eta));progress.setValue(row==null?0:(int)Math.round(row.fraction()*1000));progress.setString(row==null?"":String.format(Locale.ROOT,"%.1f%%",row.fraction()*100));}finally{rendering=false;}});
    }
    static Skill skill(String name){for(Skill skill:Skill.values())if(skill.getName().equalsIgnoreCase(name)||skill==Skill.RUNECRAFT&&"Runecrafting".equalsIgnoreCase(name))return skill;return null;}
    static String format(long amount){return String.format(Locale.ROOT,"%,d",amount);}
    static String eta(long seconds)
    {if(seconds<0)return "—";if(seconds==0)return "Complete";long hours=seconds/3600,minutes=seconds%3600/60;return hours>0?hours+"h "+minutes+"m":minutes>0?minutes+"m "+seconds%60+"s":seconds+"s";}
}
