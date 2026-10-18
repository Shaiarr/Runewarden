/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.time.*;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.game.ItemManager;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.util.LinkBrowser;

/** Native daily claimables beside optional character-specific personal routines. */
@Singleton
final class RoutinePlannerTool
{
    static final String GROUP="runewardenroutines",KEY="routinesV1";
    enum Cadence { ONCE, DAILY, WEEKLY }
    static final class Routine {String id,label,module;Cadence cadence;long completed;}
    static final class Plan {int version=1;List<Routine> routines=new ArrayList<>();Set<String> hiddenClaims=new LinkedHashSet<>();}
    private final ConfigManager config;private final RunewardenDataStore data;private final Client client;private final ItemManager items;
    private volatile List<ClaimableRoutineCatalog.Claim> claims=Collections.emptyList();private volatile String claimsProfile;
    private int claimTicks,claimWarmup=3,claimPage;private long renderedMinute;private volatile long claimObservedAt;private JComboBox<String> viewMode;
    private volatile Map<Integer,java.awt.image.BufferedImage> claimIcons=Collections.emptyMap();
    List<ClaimableRoutineCatalog.Claim> claimables(){return configCurrent()&&Objects.equals(claimsProfile,config.getRSProfileKey())?claims:Collections.emptyList();}
    List<ClaimableRoutineCatalog.Claim> watchedClaimables(){List<ClaimableRoutineCatalog.Claim> result=new ArrayList<>();for(ClaimableRoutineCatalog.Claim claim:claimables())if(!plan.hiddenClaims.contains(claim.id))result.add(claim);return Collections.unmodifiableList(result);}
    long claimObservedAt(){return configCurrent()&&Objects.equals(claimsProfile,config.getRSProfileKey())?claimObservedAt:0;}
    private LocalDate renderedDay;private String profile;private volatile Long configProfileId;private volatile boolean configBound;private long generation;private Plan plan=new Plan(),undo;private boolean started;private JPanel panel,body;
    @Inject RoutinePlannerTool(ConfigManager config,RunewardenDataStore data,Client client,ItemManager items){this.config=config;this.data=data;this.client=client;this.items=items;}
    RoutinePlannerTool(ConfigManager config,RunewardenDataStore data){this(config,data,null,null);}
    void start(){started=true;clear();}void stop(){started=false;clear();}
    void onConfigProfileChanged(){clear();}
    void clear(){configBound=false;configProfileId=null;generation++;profile=null;plan=new Plan();undo=null;claimsProfile=null;claims=Collections.emptyList();claimObservedAt=0;claimTicks=0;claimWarmup=3;claimIcons=Collections.emptyMap();repaint();}
    void onGameTick()
    {
        if(!started)return;if(!configCurrent())clear();String current=config.getRSProfileKey();
        if(!Objects.equals(current,profile)){generation++;profile=current;configProfileId=nativeConfigProfileId();configBound=true;plan=current==null?new Plan():data.read(GROUP,current,KEY,1,Plan.class,RoutinePlannerTool::valid,Plan::new);undo=null;claimsProfile=null;claims=Collections.emptyList();claimObservedAt=0;claimWarmup=3;claimTicks=0;repaint();}
        observeClaims();if(panel!=null&&panel.isShowing()&&renderedMinute!=System.currentTimeMillis()/60000){renderedMinute=System.currentTimeMillis()/60000;repaint();}if(!Objects.equals(renderedDay,LocalDate.now(ZoneOffset.UTC))){renderedDay=LocalDate.now(ZoneOffset.UTC);repaint();}
    }
    private void observeClaims()
    {
        if(client==null||profile==null||client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null)
        {if(claimsProfile!=null){claimsProfile=null;claims=Collections.emptyList();claimObservedAt=0;claimWarmup=3;repaint();}return;}
        if(claimWarmup-->0||claimTicks++%5!=0)return;
        Map<Integer,Integer> bits=new LinkedHashMap<>(),varps=new LinkedHashMap<>();Map<String,Integer> claimed=new LinkedHashMap<>();
        for(int variable:ClaimableRoutineCatalog.variables())bits.put(variable,client.getVarbitValue(variable));
        varps.put(VarPlayerID.NZONE_REWARDPOINTS,client.getVarpValue(VarPlayerID.NZONE_REWARDPOINTS));
        for(Map.Entry<String,Integer> reward:ClaimableRoutineCatalog.rewardVariables().entrySet())claimed.put(reward.getKey(),client.getVarbitValue(reward.getValue())>0?1:0);
        List<ClaimableRoutineCatalog.Claim> next=ClaimableRoutineCatalog.project(new ClaimableRoutineCatalog.Facts(bits,varps,claimed,client.getVarcIntValue(VarClientID.PLAYERMEMBER)==1,System.currentTimeMillis()));
        boolean changed=claims.size()!=next.size();
        if(!changed)for(int index=0;index<next.size();index++)if(!next.get(index).detail.equals(claims.get(index).detail)||next.get(index).status!=claims.get(index).status){changed=true;break;}
        if(items!=null){Map<Integer,java.awt.image.BufferedImage> icons=new LinkedHashMap<>();for(ClaimableRoutineCatalog.Claim claim:next){java.awt.image.BufferedImage icon=items.getImage(claim.iconId);if(icon!=null)icons.put(claim.iconId,icon);}claimIcons=Collections.unmodifiableMap(icons);}
        claims=next;claimsProfile=profile;claimObservedAt=System.currentTimeMillis();if(changed)repaint();
    }
    static boolean valid(Plan plan)
    {
        if(plan==null||plan.version!=1||plan.routines==null||plan.routines.size()>60||plan.hiddenClaims==null||plan.hiddenClaims.size()>9)return false;for(String hidden:plan.hiddenClaims)if(!Set.of("battlestaves","essence","runes","flax","arrows","bonemeal","dynamite","sand","herb-boxes").contains(hidden))return false;Set<String> ids=new HashSet<>();
        for(Routine r:plan.routines)if(r==null||r.id==null||r.id.isEmpty()||r.id.length()>80||!ids.add(r.id)||r.label==null||r.label.trim().isEmpty()||r.label.length()>120
            ||r.module!=null&&r.module.length()>80||r.cadence==null||r.completed<0)return false;return true;
    }
    static boolean due(Routine r,Instant now)
    {
        if(r.completed==0)return true;if(r.cadence==Cadence.ONCE)return false;
        LocalDate last=Instant.ofEpochMilli(r.completed).atZone(ZoneOffset.UTC).toLocalDate(),today=now.atZone(ZoneOffset.UTC).toLocalDate();
        if(r.cadence==Cadence.DAILY)return today.isAfter(last);
        return today.isAfter(last)&&today.toEpochDay()-last.toEpochDay()>=7;
    }
    JPanel createPanel(){if(panel==null){panel=SidebarUi.column();panel.add(SidebarUi.heading("CLAIMS & ROUTINES"));viewMode=new JComboBox<>(new String[]{"Ready and available", "All daily claims", "My routines"});viewMode.setFont(GuideText.FONT);viewMode.addActionListener(event->{claimPage=0;render();});panel.add(viewMode);panel.addHierarchyListener(event->{if(panel.isShowing())render();});body=SidebarUi.column();panel.add(body);render();}return panel;}
    private void repaint(){SwingUtilities.invokeLater(this::render);}
    private void render()
    {
        if(body==null)return;body.removeAll();if(!configCurrent()||profile==null||!Objects.equals(profile,config.getRSProfileKey())){body.add(SidebarUi.label("Log in to open your routines"));body.revalidate();return;}
        renderedDay=LocalDate.now(ZoneOffset.UTC);renderedMinute=System.currentTimeMillis()/60000;final String owner=profile;final long ownerGeneration=generation;
        if(viewMode.getSelectedIndex()!=2){renderClaims(owner,ownerGeneration,viewMode.getSelectedIndex()==1);body.revalidate();body.repaint();return;}
        JTextField name=new JTextField();JComboBox<Cadence> cadence=new JComboBox<>(Cadence.values());
        JPanel add=SidebarUi.column();add.add(SidebarUi.field("Routine",name));add.add(SidebarUi.field("Repeat",cadence));
        add.add(SidebarUi.button("Add routine",()->{if(!current(owner,ownerGeneration))return;String text=name.getText().trim();if(text.isEmpty()||text.length()>120||plan.routines.size()>=60)return;remember();Routine r=new Routine();r.id=UUID.randomUUID().toString();r.label=text;r.cadence=(Cadence)cadence.getSelectedItem();r.module="";plan.routines.add(r);save();render();}));
        body.add(SidebarUi.disclosure("New routine",add));Instant now=Instant.now();
        for(Routine r:plan.routines)
        {
            JPanel card=SidebarUi.section(r.label);boolean due=due(r,now);card.add(SidebarUi.label((due?"Due":"Done")+" · "+r.cadence.name().toLowerCase(Locale.ROOT)));
            card.add(SidebarUi.row(SidebarUi.button(due?"Complete":"Reset",()->{if(!current(owner,ownerGeneration))return;remember();r.completed=due?System.currentTimeMillis():0;save();render();}),SidebarUi.button("Remove",()->{if(!current(owner,ownerGeneration))return;remember();plan.routines.remove(r);save();render();})));body.add(card);
        }
        if(undo!=null)body.add(SidebarUi.button("Undo last change",()->{if(!current(owner,ownerGeneration))return;Plan previous=undo;undo=null;plan=previous;save();render();}));
        body.revalidate();body.repaint();
    }
    private void renderClaims(String owner,long ownerGeneration,boolean all)
    {
        List<ClaimableRoutineCatalog.Claim> found=new ArrayList<>();
        for(ClaimableRoutineCatalog.Claim claim:all?claimables():watchedClaimables())if(all||claim.status==ClaimableRoutineCatalog.Status.AVAILABLE||claim.status==ClaimableRoutineCatalog.Status.NEEDS_PAYMENT||claim.status==ClaimableRoutineCatalog.Status.CLAIM_REWARD)found.add(claim);
        found.sort(Comparator.comparingInt(claim->claim.status==ClaimableRoutineCatalog.Status.AVAILABLE?0:claim.status==ClaimableRoutineCatalog.Status.CLAIM_REWARD?1:claim.status==ClaimableRoutineCatalog.Status.NEEDS_PAYMENT?2:3));
        if(claimsProfile==null){body.add(SidebarUi.label("Daily claims loading"));return;}
        Instant reset=ClaimableRoutineCatalog.nextReset(Instant.now());Duration left=Duration.between(Instant.now(),reset);
        body.add(SidebarUi.label("Daily reset · "+String.format(Locale.ROOT,"%dh %02dm",Math.max(0,left.toHours()),Math.max(0,left.toMinutes()%60))));
        if(found.isEmpty())body.add(SidebarUi.label("No claims ready"));
        int pages=Math.max(1,(found.size()+5)/6);claimPage=Math.max(0,Math.min(claimPage,pages-1));
        for(int index=claimPage*6;index<Math.min(found.size(),claimPage*6+6);index++)
        {
            ClaimableRoutineCatalog.Claim claim=found.get(index);JPanel card=SidebarUi.section(null);
            JPanel title=new JPanel(new java.awt.BorderLayout(6,0));title.setOpaque(false);java.awt.image.BufferedImage icon=claimIcons.get(claim.iconId);if(icon!=null)title.add(new JLabel(new ImageIcon(icon)),java.awt.BorderLayout.WEST);title.add(SidebarUi.heading(claim.title),java.awt.BorderLayout.CENTER);card.add(title);card.add(SidebarUi.heading(claim.statusText()));card.add(SidebarUi.label(claim.detail));card.add(SidebarUi.label(claim.destination));
            JCheckBox watched=new JCheckBox("Show reminder",!plan.hiddenClaims.contains(claim.id));watched.setOpaque(false);watched.setFont(GuideText.FONT);watched.setForeground(SidebarUi.TEXT);
            watched.addActionListener(event->{if(!current(owner,ownerGeneration))return;remember();if(watched.isSelected())plan.hiddenClaims.remove(claim.id);else plan.hiddenClaims.add(claim.id);save();render();});card.add(watched);
            card.add(SidebarUi.button("Activity source ↗",()->LinkBrowser.browse(claim.source)));body.add(card);
        }
        if(pages>1){JLabel count=new JLabel((claimPage+1)+" / "+pages,SwingConstants.CENTER);count.setFont(GuideText.FONT);count.setForeground(SidebarUi.TEXT);body.add(SidebarUi.row(SidebarUi.button("‹",()->{claimPage--;render();}),count,SidebarUi.button("›",()->{claimPage++;render();})));}
    }
    private boolean current(String owner,long expectedGeneration){return started&&generation==expectedGeneration&&configCurrent()&&Objects.equals(owner,profile)&&Objects.equals(owner,config.getRSProfileKey());}
    private Long nativeConfigProfileId(){ConfigProfile selected=config.getProfile();return selected==null?null:selected.getId();}
    private boolean configCurrent(){return !configBound||Objects.equals(configProfileId,nativeConfigProfileId());}
    private void remember(){undo=new Plan();undo.hiddenClaims.addAll(plan.hiddenClaims);for(Routine r:plan.routines){Routine copy=new Routine();copy.id=r.id;copy.label=r.label;copy.module=r.module;copy.cadence=r.cadence;copy.completed=r.completed;undo.routines.add(copy);}}
    private void save(){if(started&&configCurrent()&&profile!=null&&profile.equals(config.getRSProfileKey()))data.write(GROUP,profile,KEY,1,plan);}
}
