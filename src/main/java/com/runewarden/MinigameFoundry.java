/* SPDX-License-Identifier: BSD-2-Clause
 * Mould score data and observed tool constants derived from Easy Giants' Foundry.
 * Copyright (c) 2019 dillydill123 and project contributors; BSD-2-Clause.
 * Full upstream license: docs/licenses/easy-giants-foundry.txt.
 */
package com.runewarden;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.NpcID;

/** Pure owned-mould and action calculations, plus a read-only native adapter. */
final class MinigameFoundry
{
    /** Mould state is not sword progress: it resets to zero when the preform is picked up. */
    static final class CommissionProgress
    {
        private boolean initialized,sawFinished,rewardReceived,terminal,sawEmptyCommission;
        private int previousPoints;
        void clear(){initialized=sawFinished=rewardReceived=terminal=sawEmptyCommission=false;previousPoints=0;}
        boolean awaitingReward(){return sawFinished&&!terminal;}
        boolean observe(int points,int progress,boolean preform,boolean stored,int mould,int metals,boolean commission,boolean nativeSword)
        {
            if(!initialized){initialized=true;previousPoints=points;}
            boolean previouslyTerminal=terminal;
            if(terminal&&!commission)sawEmptyCommission=true;
            if(previouslyTerminal&&(sawEmptyCommission&&commission||preform&&progress<1000||metals>0))
            {terminal=false;sawEmptyCommission=false;sawFinished=rewardReceived=false;}
            if(progress>=1000&&nativeSword)sawFinished=true;
            if(sawFinished&&points>previousPoints)rewardReceived=true;
            // A finished gauge alone is only the hand-in objective. A native points award
            // followed by removal of the carried/stored sword establishes accepted hand-in.
            if(!previouslyTerminal&&sawFinished&&rewardReceived&&!preform&&!stored&&mould==0)
            {terminal=true;sawFinished=rewardReceived=false;sawEmptyCommission=!commission;}
            if(!terminal&&(preform&&progress<1000||!nativeSword&&metals>0)){sawFinished=false;rewardReceived=false;}
            previousPoints=points;return terminal;
        }
    }
    static MinigameActivityCatalog.Snapshot read(Client client,CommissionProgress journal,int currentTool,int[] band)
    {
        int mould=client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_MOULD_STATE);
        int progress=client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION),heat=client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_PREFORM_TEMPERATURE);
        int a=client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_COMMISSION_WORD_1),b=client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_COMMISSION_WORD_2);
        boolean commission=a>=1&&a<=6&&b>=1&&b<=6;
        boolean preform=contains(client,InventoryID.WORN,ItemID.GIANTS_FOUNDRY_PREFORM)||contains(client,InventoryID.INV,ItemID.GIANTS_FOUNDRY_PREFORM);
        boolean stored=client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_PREFORM_STORED)>0;
        int total=0;for(int i=0;i<6;i++)total+=Math.max(0,client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_BRONZE_COUNT+i));
        boolean nativeSword=preform||stored||mould==2||currentTool!=0;
        boolean accepted=journal.observe(client.getVarpValue(VarPlayerID.GIANTS_FOUNDRY_REWARD_SHOP_POINTS),progress,preform,stored,mould,total,commission,nativeSword);
        List<MinigameActivityCatalog.Fact> facts=new ArrayList<>();
        facts.add(fact("Progress",String.format(Locale.ROOT,"%.1f%%",Math.max(0,Math.min(1000,progress))/10d)));
        facts.add(fact("Heat",String.format(Locale.ROOT,"%.1f%%",Math.max(0,Math.min(1000,heat))/10d)));
        facts.add(fact("Quality",Integer.toString(client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_PREFORM_QUALITY))));
        addFacts(client,facts,currentTool,progress,band);
        Set<String> done=new LinkedHashSet<>();String title,body,step,key;Set<Integer> objects=Set.of(),npcs=Set.of();
        if(accepted)
        {
            title="Commission rewarded";body="Kovac accepted your completed sword and awarded reputation.";step="method:3";key="rewarded";
            done.addAll(Set.of("prepare:1","prepare:2","method:1","method:2","method:3","milestone:1"));
        }
        else if(stored)
        {
            title="Retrieve your stored preform";body="Take your existing preform from storage before continuing "+(progress>=1000?"to Kovac for the hand-in.":"the current tool sequence.");
            step=progress>=1000?"method:3":"method:2";key="retrieve";objects=Set.of(44778);done.addAll(Set.of("prepare:1","prepare:2","method:1"));
            if(progress>=1000)done.add("method:2");
        }
        else if(progress>=1000&&nativeSword)
        {
            title="Return the sword";body="Give your completed sword to Kovac for your commission reward.";step="method:3";key="hand-in";
            npcs=Set.of(NpcID.GIANTS_FOUNDRY_KOVAC);done.addAll(Set.of("prepare:1","prepare:2","method:1","method:2"));
        }
        else if(preform||currentTool!=0&&mould!=2)
        {
            step="method:2";done.addAll(Set.of("prepare:1","prepare:2","method:1"));
            String tool=currentTool==44619?"trip hammer":currentTool==44620?"grindstone":"polishing wheel";
            if(currentTool!=0)facts.add(fact("Current tool",tool.substring(0,1).toUpperCase(Locale.ROOT)+tool.substring(1)));
            if(currentTool!=0&&band!=null&&heat<band[0])
            {title="Heat the preform";body="Use the lava pool until the heat enters the native gauge's highlighted band for "+tool+".";objects=Set.of(44631);key="heat:"+currentTool;}
            else if(currentTool!=0&&band!=null&&heat>band[1])
            {title="Cool the preform";body="Use the waterfall until the heat enters the native gauge's highlighted band for "+tool+".";objects=Set.of(44632);key="cool:"+currentTool;}
            else if(currentTool!=0&&bonusActive(facts))
            {title="CLICK the "+tool;body="The native bonus is active. Click the current tool again for bonus progress.";objects=Set.of(currentTool);key="bonus:"+currentTool;}
            else if(currentTool!=0)
            {title="Work at the "+tool;body="Keep the heat inside this tool's native highlighted band while working the preform.";objects=Set.of(currentTool);key="work:"+currentTool;}
            else
            {title="Follow the current tool segment";body="Use the tool shown in the native progress bar, keeping heat within its highlighted band.";objects=Set.of(44619,44620,44621);key="work:unknown";}
        }
        else if(mould==2)
        {
            title="Collect the preform";body="Allow the poured metal to cool, then collect your sword preform from the mould jig.";step="method:1";key="collect";
            objects=Set.of(44777,44627,44628,44629);done.addAll(Set.of("prepare:1","prepare:2"));
        }
        else if(journal.awaitingReward())
        {
            title="Confirm the commission reward";body="Finish Kovac's hand-in dialogue to collect the commission reward.";step="method:3";key="reward-pending";
            npcs=Set.of(NpcID.GIANTS_FOUNDRY_KOVAC);done.addAll(Set.of("prepare:1","prepare:2","method:1","method:2"));
        }
        else if(!commission)
        {
            title="Get a commission from Kovac";body="Speak to Kovac for the sword's two commission attributes before setting the mould.";step="prepare:1";key="commission";
            npcs=Set.of(NpcID.GIANTS_FOUNDRY_KOVAC);
        }
        else if(mould==0&&total>=28)
        {
            title="Set the mould";body="Your 28-metal alloy is loaded. Select the Forte, Blade and Tip listed for your owned moulds.";step="prepare:2";key="mould";
            objects=Set.of(44777,44625,44626);done.add("prepare:1");
        }
        else if(total<28)
        {
            title="Load the remaining alloy";body="Add "+(28-total)+" more metal units to the crucible for your chosen 28-metal alloy.";step="method:1";key="alloy:"+total;objects=Set.of(44776,44622,44623,44624);
            if(mould==1)done.add("prepare:2");
        }
        else
        {
            title="Pour the sword mould";body="Your 28-metal alloy and mould are ready. Pour the crucible into the mould jig.";step="method:1";key="pour";
            objects=Set.of(44776);done.addAll(Set.of("prepare:1","prepare:2"));
        }
        MinigameActivityCatalog.Snapshot snapshot=new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.GIANTS_FOUNDRY,"Giants' Foundry","training:giants-foundry",facts,
            List.of(new MinigameActivityCatalog.Step(title,body)),objects,List.of(),true,npcs);
        return snapshot.withGuideProgress(step,"foundry:"+a+":"+b+":"+key,accepted,done);
    }
    /** Catalog already supplies a native phase; keep this adapter composable with other activities. */
    static MinigameActivityCatalog.Snapshot guideProgress(MinigameActivityCatalog.Snapshot snapshot,Client client){return snapshot;}
    private static boolean contains(Client client,int containerId,int itemId)
    {
        ItemContainer container=client.getItemContainer(containerId);if(container==null||container.getItems()==null)return false;
        for(Item item:container.getItems())if(item!=null&&item.getId()==itemId&&item.getQuantity()>0)return true;return false;
    }
    static final class Mould
    {
        final String name; final int part,level,unlock; final int[] scores;
        Mould(String name,int part,int level,int unlock,int[] scores)
        {this.name=name;this.part=part;this.level=level;this.unlock=unlock;this.scores=scores;}
        int score(int a,int b){return a>0&&a<7&&b>0&&b<7?scores[a]+scores[b]:-1;}
    }
    static final Mould[] MOULDS={
        new Mould("Chopper Forte",0,15,0,new int[]{0,0,4,4,4,0,0}),
        new Mould("Gladius Ricasso",0,15,0,new int[]{0,0,0,4,4,4,0}),
        new Mould("Disarming Forte",0,15,0,new int[]{0,4,4,0,0,0,4}),
        new Mould("Medusa Ricasso",0,15,0,new int[]{0,0,0,8,8,6,0}),
        new Mould("Serpent Ricasso",0,15,0,new int[]{0,6,8,8,0,0,0}),
        new Mould("Serrated Forte",0,15,0,new int[]{0,8,0,0,0,8,6}),
        new Mould("Stiletto Forte",0,50,13916,new int[]{0,8,10,8,0,0,0}),
        new Mould("Defender Base",0,51,13917,new int[]{0,0,0,8,8,10,0}),
        new Mould("Juggernaut Forte",0,61,13918,new int[]{0,0,0,0,4,4,16}),
        new Mould("Chopper Forte +1",0,70,13919,new int[]{0,0,4,18,3,0,0}),
        new Mould("Spiker!",0,79,13920,new int[]{0,1,0,0,0,2,22}),
        new Mould("Saw Blade",1,15,0,new int[]{0,0,4,0,4,0,4}),
        new Mould("Defenders Edge",1,15,0,new int[]{0,0,0,0,4,4,4}),
        new Mould("Fish Blade",1,15,0,new int[]{0,4,4,4,0,0,0}),
        new Mould("Medusa Blade",1,15,0,new int[]{0,0,0,6,8,8,0}),
        new Mould("Stiletto Blade",1,15,0,new int[]{0,8,6,8,0,0,0}),
        new Mould("Gladius Edge",1,15,0,new int[]{0,6,0,8,0,8,0}),
        new Mould("Flamberge Blade",1,48,13921,new int[]{0,8,8,0,0,0,10}),
        new Mould("Serpent Blade",1,50,13922,new int[]{0,10,8,8,0,0,0}),
        new Mould("Claymore Blade",1,59,13923,new int[]{0,0,0,4,16,4,0}),
        new Mould("Fleur de Blade",1,71,13924,new int[]{0,0,0,0,4,18,1}),
        new Mould("Choppa!",1,80,13925,new int[]{0,0,22,2,1,0,0}),
        new Mould("People Poker Point",2,15,0,new int[]{0,4,0,4,0,4,0}),
        new Mould("Chopper Tip",2,15,0,new int[]{0,0,4,0,4,0,4}),
        new Mould("Medusa's Head",2,15,0,new int[]{0,0,0,0,4,4,4}),
        new Mould("Serpent's Fang",2,15,0,new int[]{0,8,6,0,0,0,8}),
        new Mould("Gladius Point",2,15,0,new int[]{0,8,0,6,0,8,0}),
        new Mould("Saw Tip",2,15,0,new int[]{0,0,0,0,6,8,8}),
        new Mould("Corrupted Point",2,48,13926,new int[]{0,8,10,0,0,0,8}),
        new Mould("Defenders Tip",2,52,13927,new int[]{0,0,0,0,10,8,8}),
        new Mould("Serrated Tip",2,60,13928,new int[]{0,4,16,0,0,0,4}),
        new Mould("Needle Point",2,69,13929,new int[]{0,18,3,4,0,0,0}),
        new Mould("The Point!",2,81,13930,new int[]{0,0,1,22,2,0,0})
    };
    static final String[] ATTRIBUTES={"","Narrow","Light","Flat","Broad","Heavy","Spiked"};
    static List<Mould> bestOwned(int a,int b,int level,IntUnaryOperator bits)
    {
        if(a<1||a>6||b<1||b>6||a==b||(a==1&&b==4)||(a==4&&b==1)||(a==2&&b==5)||(a==5&&b==2)||(a==3&&b==6)||(a==6&&b==3))return List.of();
        Mould[] best=new Mould[3];
        for(Mould mould:MOULDS)
        {
            if(level<mould.level||mould.unlock!=0&&bits.applyAsInt(mould.unlock)!=1)continue;
            if(best[mould.part]==null||mould.score(a,b)>best[mould.part].score(a,b))best[mould.part]=mould;
        }
        return Arrays.stream(best).anyMatch(java.util.Objects::isNull)?List.of():List.of(best);
    }
    /** Metal contribution plus alloy bonus; a single pure metal has no alloy bonus. */
    static double alloyScore(int[] counts)
    {
        if(counts==null||counts.length!=6)return -1;
        int total=0;double[] contributions=new double[6];
        for(int i=0;i<6;i++){if(counts[i]<0||counts[i]>28)return -1;total+=counts[i];contributions[i]=10.0*(i+1)*counts[i]/28;}
        if(total!=28)return -1;
        Arrays.sort(contributions);double a=contributions[5],b=contributions[4];return a+b+a*b/10;
    }
    static int stageIndex(int progress,int count)
    {return progress<0||progress>=1000||count<1||count>16?-1:progress*count/1000;}
    static int[] actionsLeft(int progress,int stages,int base,int outfitPieces)
    {
        int index=stageIndex(progress,stages);if(index<0||base<=0)return new int[]{0,0};
        double remaining=(1000.0*(index+1)-progress*stages)/stages;
        int pieces=Math.max(0,Math.min(4,outfitPieces));
        int best=pieces==0?base:base+1,worst=pieces==4?base+1:base;
        return new int[]{(int)Math.ceil(remaining/best),(int)Math.ceil(remaining/worst)};
    }
    static int safeActions(int heat,int low,int high,int change)
    {
        if(low<0||high>1000||low>high||heat<low||heat>high||change==0)return 0;
        return 1+(change>0?(high-heat)/change:(heat-low)/-change);
    }
    // Measured native heat-change sequence, standard starts at 7, dunk/quench at 27.
    private static final int[] HEAT_CHANGES={7,8,9,11,13,15,17,19,21,24,27,30,33,37,41,45,49,53,57,62,67,72,77,83,89,95,91};
    static int heatTicksToBand(int heat,int low,int high,boolean fast)
    {
        if(heat<0||heat>1000||low<0||high>1000||low>high)return -1;
        if(heat>=low&&heat<=high)return 0;
        int sign=heat<low?1:-1,current=heat,ticks=0;
        for(int i=fast?10:0;i<HEAT_CHANGES.length;i++)
        {
            current+=sign*HEAT_CHANGES[i];ticks++;
            if(current>=low&&current<=high)return ticks;
            if(sign>0&&current>high||sign<0&&current<low)return -1;
        }
        return -1;
    }
    static String range(int[] values){return values[0]==values[1]?Integer.toString(values[0]):values[0]+"–"+values[1];}
    static void addFacts(Client client,List<MinigameActivityCatalog.Fact> facts,int tool,int progress,int[] band)
    {
        int a=client.getVarbitValue(13907),b=client.getVarbitValue(13908);
        List<Mould> best=bestOwned(a,b,client.getRealSkillLevel(Skill.SMITHING),client::getVarbitValue);
        if(!best.isEmpty())
        {
            facts.add(fact("Commission",ATTRIBUTES[a]+" + "+ATTRIBUTES[b]));int score=0;
            for(Mould mould:best){facts.add(fact(new String[]{"Forte","Blade","Tip"}[mould.part],mould.name+" · "+mould.score(a,b)));score+=mould.score(a,b);}
            facts.add(fact("Best owned mould score",Integer.toString(score)));
        }
        int[] counts=new int[6];int total=0;StringBuilder content=new StringBuilder();String[] names={"Bronze","Iron","Steel","Mithril","Adamant","Rune"};
        for(int i=0;i<6;i++){counts[i]=client.getVarbitValue(13931+i);total+=Math.max(0,counts[i]);if(counts[i]>0){if(content.length()>0)content.append(" · ");content.append(counts[i]).append(' ').append(names[i]);}}
        facts.add(fact("Crucible",total+" / 28"+(content.length()==0?"":" · "+content)));
        double score=alloyScore(counts);if(score>=0)facts.add(fact("Alloy score",String.format(Locale.ROOT,"%.1f",score)));
        Widget bar=client.getWidget(754,75);int stages=0;
        if(bar!=null&&bar.getChildren()!=null)for(Widget child:bar.getChildren())if(child!=null&&child.getSpriteId()>=4442&&child.getSpriteId()<=4444)stages++;
        if(tool!=0&&stageIndex(progress,stages)>=0)
        {
            int pieces=outfitPieces(client),base=tool==44619?20:10,change=tool==44619?-25:tool==44620?15:-17;
            facts.add(fact("Section",(stageIndex(progress,stages)+1)+" / "+stages));
            facts.add(fact("Actions to next tool",range(actionsLeft(progress,stages,base,pieces))));
            if(pieces>0&&pieces<4)facts.add(fact("Smith's uniform",pieces+" / 4 · action count varies"));
            if(band!=null)
            {
                int heat=client.getVarbitValue(13948);
                facts.add(fact("Actions before heat change",Integer.toString(safeActions(heat,band[0],band[1],change))));
                if(heat<band[0]||heat>band[1])
                {
                    int slow=heatTicksToBand(heat,band[0],band[1],false),fast=heatTicksToBand(heat,band[0],band[1],true);
                    String adjustment=(slow>=0?"Slow · "+slow+" ticks":"Slow · fine adjustment")+(fast>=0?" · Dunk/quench · "+fast+" ticks":"");
                    facts.add(fact("Estimated heat adjustment",adjustment));
                }
            }
            Widget bonus=client.getWidget(754,4);
            if(bonus!=null&&!bonus.isHidden()&&bonus.getChildren()!=null&&bonus.getChildren().length>0&&bonus.getChild(0)!=null&&bonus.getChild(0).getTextColor()==0xfcd703)
                facts.add(fact("Bonus", "CLICK current tool for extra progress"));
        }
    }
    static boolean bonusActive(List<MinigameActivityCatalog.Fact> facts)
    {for(MinigameActivityCatalog.Fact f:facts)if(f.label.equals("Bonus"))return true;return false;}
    private static int outfitPieces(Client client)
    {
        ItemContainer worn=client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN);if(worn==null||worn.getItems()==null)return 0;
        int pieces=0;for(Item item:worn.getItems())if(item!=null&&(item.getId()==27023||item.getId()==27025||item.getId()==27027||item.getId()==27029||item.getId()==27031))pieces++;
        return Math.min(4,pieces);
    }
    private static MinigameActivityCatalog.Fact fact(String a,String b){return new MinigameActivityCatalog.Fact(a,b);}
}
