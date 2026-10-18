/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/** Portable coaching instructions. Shared structures never include another character's completion. */
final class WorkflowPlan
{
    enum Kind
    {
        MODULE("Module"),GUIDE("Guide"),ITEM("Item checklist"),DESTINATION("Destination"),SKILL("Skill goal"),QUEST("Quest"),NOTE("Note");
        private final String label;Kind(String label){this.label=label;}@Override public String toString(){return label;}
    }
    static final class TrainingContext
    {
        final String methodId,guideId,title;final Skill skill;final long targetXp,nextMilestoneXp;
        TrainingContext(ActivityXpCatalog.Method method,long target,long milestone){methodId=method.id;guideId=method.guideId;title=method.name;skill=method.skill;targetXp=target;nextMilestoneXp=milestone;}
    }
    static final class Step
    {
        String id, label, reference; Kind kind; int itemId, x, y, plane; long target;
        boolean completed; String evidence="";
        Step copy(boolean progress)
        {
            Step s=new Step();s.id=id;s.label=label;s.reference=reference;s.kind=kind;s.itemId=itemId;
            s.x=x;s.y=y;s.plane=plane;s.target=target;s.completed=progress&&completed;s.evidence=progress?evidence:"";return s;
        }
        boolean valid()
        {
            if(!text(id,80)||!text(label,180)||kind==null||reference==null||reference.length()>250||evidence==null||evidence.length()>120)return false;
            switch(kind)
            {
                case MODULE:return text(reference,100);
                case GUIDE:case QUEST:return text(reference,250)&&(kind!=Kind.QUEST||reference.startsWith("quest:"));
                case ITEM:return itemId>0&&itemId<=1_000_000&&target>=1&&target<=Integer.MAX_VALUE;
                case DESTINATION:return x>0&&x<16384&&y>0&&y<16384&&plane>=0&&plane<=3;
                case SKILL:return GoalCatalog.skill(reference)!=null&&target>=0&&target<=200_000_000;
                default:return true;
            }
        }
        WorldPoint point(){return new WorldPoint(x,y,plane);}
    }
    String id, name; String notes=""; List<Step> steps=new ArrayList<>(); boolean archived;
    String methodId,guideId,trainingSkill;long trainingTargetXp;
    WorkflowPlan copy(boolean progress)
    {WorkflowPlan p=new WorkflowPlan();p.id=id;p.name=name;p.notes=notes;p.archived=progress&&archived;p.methodId=methodId;p.guideId=guideId;p.trainingSkill=trainingSkill;p.trainingTargetXp=trainingTargetXp;for(Step s:steps)p.steps.add(s.copy(progress));return p;}
    boolean valid()
    {
        if(!text(id,80)||!text(name,100)||notes==null||notes.length()>2000||steps==null||steps.size()>100)return false;
        if(guideId!=null&&!text(guideId,250))return false;
        if(methodId!=null){if(!text(methodId,250)||GoalCatalog.skill(trainingSkill)==null||trainingTargetXp<0||trainingTargetXp>200_000_000)return false;}
        else if(trainingSkill!=null||trainingTargetXp!=0)return false;
        Set<String> ids=new HashSet<>();for(Step s:steps)if(s==null||!s.valid()||!ids.add(s.id))return false;return true;
    }
    TrainingContext trainingContext(ActivityXpCatalog catalog)
    {
        if(!valid()||methodId==null||catalog==null)return null;ActivityXpCatalog.Method method=catalog.find(methodId);
        if(method==null||method.skill==null||method.skill!=GoalCatalog.skill(trainingSkill)||!Objects.equals(method.guideId,guideId))return null;
        long milestone=0;for(Step step:steps)if(!step.completed&&step.kind==Kind.SKILL&&GoalCatalog.skill(step.reference)==method.skill){milestone=step.target;break;}
        return new TrainingContext(method,trainingTargetXp,milestone);
    }
    String primaryGuideId()
    {if(guideId!=null)return guideId;for(Step step:steps)if(step.kind==Kind.QUEST||step.kind==Kind.GUIDE)return step.reference;return null;}
    Step next(){for(Step s:steps)if(!s.completed)return s;return null;}
    int completed(){return (int)steps.stream().filter(s->s.completed).count();}
    static WorkflowPlan create(String name,List<Step> steps)
    {WorkflowPlan p=new WorkflowPlan();p.id=UUID.randomUUID().toString();p.name=name;p.steps=new ArrayList<>(steps);if(!p.valid())throw new IllegalArgumentException("Invalid plan");return p;}
    static Step step(Kind kind,String label,String reference)
    {Step s=new Step();s.id=UUID.randomUUID().toString();s.kind=kind;s.label=label;s.reference=reference==null?"":reference;return s;}
    static String encode(WorkflowPlan plan,Gson gson)
    {if(!plan.valid())throw new IllegalArgumentException("Invalid plan");return "RWF2:"+Base64.getUrlEncoder().withoutPadding().encodeToString(gson.toJson(plan.copy(false)).getBytes(StandardCharsets.UTF_8));}
    static WorkflowPlan decode(String code,Gson gson)
    {
        if(code==null||code.length()>300_000||!code.startsWith("RWF1:")&&!code.startsWith("RWF2:"))throw new IllegalArgumentException("Invalid plan code");
        try
        {
            String raw=new String(Base64.getUrlDecoder().decode(code.substring(5).trim()),StandardCharsets.UTF_8);
            RunewardenDataStore.validateJson(raw,220_000);WorkflowPlan p=gson.fromJson(raw,WorkflowPlan.class);
            if(p==null||!p.valid())throw new IllegalArgumentException("Invalid plan structure");
            p=p.copy(false);p.id=UUID.randomUUID().toString();for(Step s:p.steps)s.id=UUID.randomUUID().toString();return p;
        }catch(RuntimeException ex){throw new IllegalArgumentException("Invalid plan structure");}
    }
    static boolean text(String value,int max)
    {return value!=null&&!value.trim().isEmpty()&&value.length()<=max&&value.indexOf('\0')<0&&value.indexOf('<')<0&&value.indexOf('>')<0;}
}
