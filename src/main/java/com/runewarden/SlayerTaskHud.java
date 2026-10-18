/*
 * Copyright (c) 2017, Tyler <https://github.com/tylerthardy>
 * Copyright (c) 2018, Shaun Dreclin <shaundreclin@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;

/** Character-local reporting from native task DB rows and client variables. */
@Singleton
final class SlayerTaskHud
{
    static final class Reading
    {
        static final Reading EMPTY=new Reading("","",0,0,0,0,"",4155,"",false);
        final String name,location,streakLabel,itemNote;
        final int remaining,initial,points,streak,iconId;
        final boolean context;
        Reading(String name,String location,int remaining,int initial,int points,int streak,String streakLabel,int iconId,String note,boolean context)
        {this.name=name;this.location=location;this.remaining=remaining;this.initial=initial;this.points=points;this.streak=streak;this.streakLabel=streakLabel;this.iconId=iconId;itemNote=note;this.context=context;}
        boolean active(){return remaining>0&&!name.isEmpty();}
        String area(){return location.isEmpty()?"Any assigned area":location;}
        String counts(){return remaining+" remaining"+(initial>0?" / "+initial:"");}
        String wiki(){return "https://oldschool.runescape.wiki/w/Special:Search?search="+java.net.URLEncoder.encode(name+" Slayer task",StandardCharsets.UTF_8);}
        String key(){return name+'\n'+location+'\n'+remaining+':'+initial+':'+points+':'+streak+':'+streakLabel+':'+context;}
    }
    private static final class NativeData {int version;String runeLiteVersion;List<TaskFact> tasks;}
    private static final class LocationData {int version;List<LocationFact> tasks;}
    private static final class LocationFact {String name;List<Place> locations;}
    private static final class Place {String name;}
    private static final class TaskFact {String name,note;int icon;List<String> aliases;transient List<Pattern> patterns;}
    private final Map<String,TaskFact> tasks;private final Map<String,String> locations;
    private final Client client;private final ConfigManager config;
    private volatile boolean started;private volatile String profile;private volatile Long configProfile;
    private volatile Reading captured=Reading.EMPTY;private volatile boolean observed;private int warmup=3,rewardBefore=-1,rewardTicks;private volatile int lastReward=-1;
    private int areaTask=-1,areaMaster=-1,assignedArea=-1;private volatile String observedPlaces="";
    private final AtomicBoolean repaintQueued=new AtomicBoolean();
    private JPanel panel,body,taskCard;private GuideText state,title,counts,location,places,points,streak,equipment,reward,lastCompletion;

    @Inject SlayerTaskHud(Client client,ConfigManager config,Gson gson){this.client=client;this.config=config;tasks=loadTasks(gson);locations=loadLocations(gson);}
    void start(){started=true;clear();}
    void stop(){started=false;clear();}
    void clear(){captureProfile(null,null);}
    private void captureProfile(String nextProfile,Long nextConfig)
    {captured=Reading.EMPTY;observed=false;profile=nextProfile;configProfile=nextConfig;warmup=3;rewardBefore=-1;rewardTicks=0;lastReward=-1;areaTask=-1;observedPlaces="";refresh();}
    Reading reading()
    {
        return started&&client.getGameState()==GameState.LOGGED_IN&&client.getLocalPlayer()!=null
            &&profile!=null&&Objects.equals(profile,config.getRSProfileKey())&&Objects.equals(configProfile,selectedConfig())?captured:Reading.EMPTY;
    }
    private Long selectedConfig(){ConfigProfile selected=config.getProfile();return selected==null?null:selected.getId();}
    void onGameTick()
    {
        if(!started)return;
        if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null){if(profile!=null)clear();return;}
        String nextProfile=config.getRSProfileKey();Long nextConfig=selectedConfig();
        if(nextProfile==null){if(profile!=null)clear();return;}
        if(!Objects.equals(profile,nextProfile)||!Objects.equals(configProfile,nextConfig))
            captureProfile(nextProfile,nextConfig);
        if(warmup-->0)return;
        Reading next=readTask();
        if(captured.active()&&!next.active()&&client.getVarpValue(VarPlayerID.SLAYER_COUNT)==0)
        {rewardBefore=captured.points;rewardTicks=5;lastReward=-1;}
        if(rewardTicks>0)
        {
            int gained=client.getVarbitValue(VarbitID.SLAYER_POINTS)-rewardBefore;
            if(gained>0){lastReward=gained;rewardTicks=0;refresh();}
            else if(--rewardTicks==0)refresh();
        }
        if(!observed||!next.key().equals(captured.key())){observed=true;captured=next;refresh();}
    }
    private Reading readTask()
    {
        int remaining=client.getVarpValue(VarPlayerID.SLAYER_COUNT);
        if(remaining<=0||remaining>100000)return Reading.EMPTY;
        int task=client.getVarpValue(VarPlayerID.SLAYER_TARGET);
        try
        {
            int row;
            if(task==98)
            {
                Integer sub=singleRow(DBTableID.SlayerTaskSublist.ID,DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
                Object value=sub==null?null:field(sub,DBTableID.SlayerTaskSublist.COL_TASK);
                if(!(value instanceof Integer))return Reading.EMPTY;row=(Integer)value;
            }
            else
            {Integer resolved=singleRow(DBTableID.SlayerTask.ID,DBTableID.SlayerTask.COL_ID,task);if(resolved==null)return Reading.EMPTY;row=resolved;}
            Object value=field(row,DBTableID.SlayerTask.COL_NAME_UPPERCASE);
            String name=clean(value);if(name.isEmpty())return Reading.EMPTY;
            int areaId=client.getVarpValue(VarPlayerID.SLAYER_AREA);String area="";
            if(areaId>0)
            {
                Integer areaRow=singleRow(DBTableID.SlayerArea.ID,DBTableID.SlayerArea.COL_AREA_ID,areaId);
                if(areaRow==null)return Reading.EMPTY;
                area=clean(field(areaRow,DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER));if(area.isEmpty())return Reading.EMPTY;
            }
            int initial=client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
            if(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID)==2)
            {
                long modified=(long)initial+(client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE)==1?-1L:1L)*client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE);
                initial=modified>0&&modified<=100000?(int)modified:0;
            }
            if(initial<remaining||initial>100000)initial=0;
            int master=client.getVarbitValue(VarbitID.SLAYER_MASTER);
            int streak=master==7?client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED):master==10?
                client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED):client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
            readNativePlaces(row,master,areaId);
            TaskFact fact=tasks.get(name.toLowerCase(Locale.ROOT));
            boolean nearby=fact!=null&&targetNearby(fact);
            boolean context=nearby&&(area.isEmpty()||client.getTopLevelWorldView()!=null&&!client.getTopLevelWorldView().isInstance()&&areaMatches(area,client.getLocalPlayer().getWorldLocation()));
            return new Reading(name,area,remaining,initial,Math.max(0,client.getVarbitValue(VarbitID.SLAYER_POINTS)),Math.max(0,streak),
                master==7?"Wilderness streak":master==10?"Mortimer streak":"Task streak",fact==null?4155:fact.icon,itemNote(name),context);
        }
        catch(IllegalArgumentException|IndexOutOfBoundsException|ClassCastException unavailable){return Reading.EMPTY;}
    }
    private int areaRetry;private boolean areaLoaded;
    private void readNativePlaces(int task,int master,int area)
    {
        boolean same=areaTask==task&&areaMaster==master&&assignedArea==area;
        if(same&&(areaLoaded||areaRetry-->0))return;
        if(!same){areaTask=task;areaMaster=master;assignedArea=area;observedPlaces="";refresh();}
        areaLoaded=false;areaRetry=5;
        try
        {
            List<Integer> rows=client.getDBRowsByValue(DBTableID.SlayerMasterTask.ID,DBTableID.SlayerMasterTask.COL_MASTER_ID,0,master);
            if(rows==null||rows.isEmpty())return;
            if(rows.size()>400){areaLoaded=true;return;}List<String> found=new ArrayList<>();
            for(int row:rows)
            {
                Object target=field(row,DBTableID.SlayerMasterTask.COL_TASK);if(!(target instanceof Integer)||(Integer)target!=task)continue;
                for(int index=0;index<32;index++)
                {
                    Object[] values=client.getDBTableField(row,DBTableID.SlayerMasterTask.COL_AREAS,index);
                    if(values==null||values.length==0||!(values[0] instanceof Integer)||(Integer)values[0]<=0)break;
                    int areaRow=(Integer)values[0];Object id=field(areaRow,DBTableID.SlayerArea.COL_AREA_ID);
                    if(area>0&&(!Integer.valueOf(area).equals(id)))continue;
                    String name=clean(field(areaRow,DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER)),hint=clean(field(areaRow,DBTableID.SlayerArea.COL_AREA_HINT));
                    if(!name.isEmpty()){String label=name+(hint.isEmpty()?"":" ("+hint+")");if(!found.contains(label))found.add(label);}
                }
            }
            observedPlaces=String.join(" · ",found);areaLoaded=true;refresh();
        }
        catch(IllegalArgumentException|IndexOutOfBoundsException|ClassCastException unavailable){observedPlaces="";}
    }
    private Integer singleRow(int table,int column,int value)
    {List<Integer> rows=client.getDBRowsByValue(table,column,0,value);return rows!=null&&rows.size()==1?rows.get(0):null;}
    private Object field(int row,int column)
    {Object[] values=client.getDBTableField(row,column,0);return values!=null&&values.length==1?values[0]:null;}
    private static String clean(Object value)
    {if(!(value instanceof String))return "";String text=Text.removeTags((String)value).trim();return text.length()>120?"":text;}
    private boolean targetNearby(TaskFact fact)
    {
        WorldPoint player=client.getLocalPlayer().getWorldLocation();if(player==null)return false;
        List<NPC> npcs=client.getNpcs();if(npcs==null)return false;
        for(NPC npc:npcs)
        {
            if(npc==null||npc.getWorldLocation()==null||npc.getWorldLocation().getPlane()!=player.getPlane()||npc.getWorldLocation().distanceTo(player)>32)continue;
            NPCComposition composition=npc.getTransformedComposition();if(composition==null||composition.getName()==null)continue;
            boolean actionable=false;String[] actions=composition.getActions();if(actions!=null)for(String action:actions)if("Attack".equals(action)||"Pick".equals(action))actionable=true;
            if(!actionable)continue;
            String name=composition.getName().replace('\u00a0',' ');
            for(Pattern pattern:fact.patterns)if(pattern.matcher(name).find())return true;
        }
        return false;
    }
    // Map-area identity facts: RLHD areas.json, commit 5be607e0d99737f69b3bf4282b2655fe959f1a63.
    static boolean areaMatches(String area,WorldPoint point)
    {
        if(point==null||area==null)return false;int region=point.getRegionID(),plane=point.getPlane();
        switch(area.toLowerCase(Locale.ROOT))
        {
            case "catacombs of kourend":return plane==0&&point.getX()>=1584&&point.getX()<=1751&&point.getY()>=9976&&point.getY()<=10111;
            case "slayer tower":return region==13723&&plane==0||plane>=0&&plane<=3&&point.getX()>=3405&&point.getX()<=3452&&point.getY()>=3531&&point.getY()<=3579;
            case "fremennik slayer dungeon":return plane==0&&(region==10907||region==10908||region==11164);
            case "brimhaven dungeon":return box(point,2624,9408,2732,9599)||box(point,2733,9408,2751,9505)||box(point,2733,9506,2746,9514);
            case "kalphite cave":return box(point,3264,9472,3330,9550)||box(point,3331,9475,3349,9529);
            case "kalphite lair":return region==13972&&(plane==0||plane==2);
            case "ancient cavern":return region==6482||region==6483||region==6994||region==6995;
            case "lighthouse":case "lighthouse dungeon":return box(point,2496,4608,2542,4666)||box(point,2497,9984,2543,10042);
            case "kraken cove":return plane==0&&region==9116;
            case "brine rat cavern":return plane==0&&region==10910;
            case "smoke devil dungeon":return plane==0&&(region==9363||region==9619);
            case "smoke dungeon":return plane==0&&(region==12690||region==12946||region==13202);
            case "isle of souls dungeon":return plane==0&&region==8593;
            case "chasm of fire":return region==5789||region==6045;
            case "stronghold of security":return plane==0&&(region==7505||region==8017||region==8530||region==9297);
            default:return false;
        }
    }
    private static boolean box(WorldPoint point,int left,int bottom,int right,int top)
    {return point.getX()>=left&&point.getX()<=right&&point.getY()>=bottom&&point.getY()<=top;}
    String itemNote(String task)
    {TaskFact fact=tasks.get(task.toLowerCase(Locale.ROOT));return fact==null||fact.note==null?"":fact.note;}
    int lastCompletionPoints()
    {return started&&profile!=null&&Objects.equals(profile,config.getRSProfileKey())&&Objects.equals(configProfile,selectedConfig())&&client.getGameState()==GameState.LOGGED_IN?lastReward:-1;}
    private static Map<String,TaskFact> loadTasks(Gson gson)
    {
        try(InputStreamReader input=new InputStreamReader(SlayerTaskHud.class.getResourceAsStream("/com/runewarden/slayer-native-tasks.json"),StandardCharsets.UTF_8))
        {
            NativeData data=gson.fromJson(input,NativeData.class);
            if(data==null||data.version!=1||!"1.13.2".equals(data.runeLiteVersion)||data.tasks==null||data.tasks.size()>200)throw new IllegalArgumentException("Invalid native Slayer facts");
            Map<String,TaskFact> result=new HashMap<>();
            for(TaskFact task:data.tasks)
            {
                if(task.name==null||task.name.isEmpty()||task.icon<=0||task.aliases==null||task.aliases.size()>20)throw new IllegalArgumentException("Invalid native Slayer task");
                List<String> names=new ArrayList<>(task.aliases);String singular=task.name.replaceAll("s$","");names.add(singular);
                if(singular.regionMatches(true,0,"The ",0,4))names.add(singular.substring(4));
                task.patterns=new ArrayList<>();
                for(String name:names)task.patterns.add(Pattern.compile("(?:\\s|^)"+Pattern.quote(name)+"(?:\\s|$)",Pattern.CASE_INSENSITIVE));
                result.put(task.name.toLowerCase(Locale.ROOT),task);
            }
            return Collections.unmodifiableMap(result);
        }
        catch(java.io.IOException error){throw new IllegalStateException("Missing native Slayer facts",error);}
    }
    private static Map<String,String> loadLocations(Gson gson)
    {
        try(InputStreamReader input=new InputStreamReader(SlayerTaskHud.class.getResourceAsStream("/com/runewarden/slayer-task-locations.json"),StandardCharsets.UTF_8))
        {
            LocationData data=gson.fromJson(input,LocationData.class);if(data==null||data.version!=1||data.tasks==null||data.tasks.size()>100)throw new IllegalArgumentException("Invalid Slayer locations");
            Map<String,String> result=new HashMap<>();
            for(LocationFact task:data.tasks)
            {
                if(task.name==null||task.locations==null||task.locations.size()>20)throw new IllegalArgumentException("Invalid Slayer location");
                List<String> names=new ArrayList<>();for(Place place:task.locations)if(place.name!=null&&!place.name.isEmpty())names.add(place.name);
                result.put(task.name.toLowerCase(Locale.ROOT),String.join(" · ",names));
            }
            return Collections.unmodifiableMap(result);
        }
        catch(java.io.IOException error){throw new IllegalStateException("Missing Slayer locations",error);}
    }
    String locationExamples(String task){return locations.getOrDefault(task.toLowerCase(Locale.ROOT),"");}
    JPanel createPanel()
    {
        if(panel==null)
        {
            panel=SidebarUi.column();panel.add(SidebarUi.heading("SLAYER"));body=SidebarUi.column();panel.add(body);
            state=SidebarUi.label("");body.add(state);taskCard=SidebarUi.section(null);body.add(taskCard);
            title=SidebarUi.heading("");counts=SidebarUi.heading("");location=SidebarUi.label("");places=SidebarUi.label("");points=SidebarUi.label("");streak=SidebarUi.label("");equipment=SidebarUi.label("");reward=SidebarUi.label("Completion reward · Unknown");
            for(GuideText field:new GuideText[]{title,counts,location,places,points,streak,equipment,reward})taskCard.add(field);
            taskCard.add(SidebarUi.button("Task locations & equipment ↗",()->{Reading view=reading();if(view.active())LinkBrowser.browse(view.wiki());}));
            lastCompletion=SidebarUi.label("");body.add(lastCompletion);panel.addHierarchyListener(event->{if(panel.isShowing())render();});render();
        }
        return panel;
    }
    private void refresh()
    {if(repaintQueued.compareAndSet(false,true))SwingUtilities.invokeLater(()->{repaintQueued.set(false);render();});}
    private static void text(GuideText field,String value){if(!field.getText().equals(value))field.setText(value);}
    private void render()
    {
        if(body==null)return;Reading view=reading();boolean active=view.active();taskCard.setVisible(active);state.setVisible(!active);
        if(active)
        {
            text(title,view.name);text(counts,view.counts());text(location,"Assigned location · "+view.area());
            String examples=observedPlaces.isEmpty()?locationExamples(view.name):observedPlaces;text(places,"Found at · "+examples);places.setVisible(!examples.isEmpty());
            text(points,"Points balance · "+view.points);text(streak,view.streakLabel+" · "+view.streak);
            text(equipment,view.itemNote);equipment.setVisible(!view.itemNote.isEmpty());
        }
        else text(state,profile==null||!Objects.equals(profile,config.getRSProfileKey())?"Log in to view your Slayer task":!observed?"Loading Slayer task":"No active Slayer task");
        int gained=lastCompletionPoints();lastCompletion.setVisible(gained>=0);if(gained>=0)text(lastCompletion,"Last completion · +"+gained+" points observed");
        body.revalidate();body.repaint();
    }
}
