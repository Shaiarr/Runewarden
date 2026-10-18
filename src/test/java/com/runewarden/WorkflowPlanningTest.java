/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Independent QA of the plan model, portable formats and populated account-aware workflows. */
public class WorkflowPlanningTest
{
    @Test public void sharingStripsCompletionEvidenceArchiveAndRegeneratesIdentifiers()
    {
        Gson gson=new Gson();WorkflowPlan.Step step=WorkflowPlan.step(WorkflowPlan.Kind.NOTE,"Trip checklist","Take a teleport");step.completed=true;step.evidence="Private achievement";
        WorkflowPlan plan=WorkflowPlan.create("A route",List.of(step));plan.archived=true;
        String encoded=WorkflowPlan.encode(plan,gson);WorkflowPlan imported=WorkflowPlan.decode(encoded,gson);
        assertNotEquals(plan.id,imported.id);assertNotEquals(step.id,imported.steps.get(0).id);assertFalse(imported.archived);assertFalse(imported.steps.get(0).completed);assertEquals("",imported.steps.get(0).evidence);
        assertEquals("Take a teleport",imported.steps.get(0).reference);assertTrue(plan.archived);assertTrue(step.completed);
        String raw=new String(Base64.getUrlDecoder().decode(encoded.substring(5)),java.nio.charset.StandardCharsets.UTF_8);assertFalse(raw.contains("Private achievement"));
    }
    @Test public void malformedOversizedNestedAndDuplicateStepPlansAreRejected()
    {
        Gson gson=new Gson();for(String code:List.of("RWF2:abc","RWF1:not-base64%%","RWF1:"+"a".repeat(300_000),"RWF1:"+Base64.getUrlEncoder().encodeToString(("[".repeat(60)+"0"+"]".repeat(60)).getBytes(java.nio.charset.StandardCharsets.UTF_8))))
            try{WorkflowPlan.decode(code,gson);fail(code.substring(0,Math.min(code.length(),20)));}catch(IllegalArgumentException expected){}
        WorkflowPlan.Step a=WorkflowPlan.step(WorkflowPlan.Kind.NOTE,"One","");WorkflowPlan p=WorkflowPlan.create("Route",List.of(a));p.steps.add(a.copy(false));assertFalse(p.valid());
        p.steps.clear();for(int i=0;i<101;i++)p.steps.add(WorkflowPlan.step(WorkflowPlan.Kind.NOTE,"Step "+i,""));assertFalse(p.valid());
        WorkflowPlan.Step skill=WorkflowPlan.step(WorkflowPlan.Kind.SKILL,"Invalid target","Mining");skill.target=200_000_001;assertFalse(skill.valid());skill.target=100;skill.reference="Unknown skill";assertFalse(skill.valid());
    }
    @Test public void savedPlansRejectOrphanSelectionAndDuplicateIds()
    {
        WorkflowPlansTool.Saved saved=new WorkflowPlansTool.Saved();saved.selected="missing";assertFalse(WorkflowPlansTool.valid(saved));
        WorkflowPlan p=WorkflowPlan.create("One",List.of());saved.selected=p.id;saved.plans.add(p);assertTrue(WorkflowPlansTool.valid(saved));saved.plans.add(p.copy(true));assertFalse(WorkflowPlansTool.valid(saved));
    }
    @Test public void preferencesValidateEnumsNullsBoundsAndPersonalConstraintFields()
    {
        OpportunityPlannerTool.Preferences p=new OpportunityPlannerTool.Preferences();assertTrue(OpportunityPlannerTool.valid(p));
        p.skill="Mining with hidden corruption";assertFalse(OpportunityPlannerTool.valid(p));p.skill="Any skill";p.effort=null;assertFalse(OpportunityPlannerTool.valid(p));p.effort="AFK";p.kind="Anything";assertFalse(OpportunityPlannerTool.valid(p));
        p.kind="Training";p.minutes=721;assertFalse(OpportunityPlannerTool.valid(p));p.minutes=30;p.targetLevel=1;assertFalse(OpportunityPlannerTool.valid(p));p.targetLevel=99;
        OpportunityPlannerTool.Constraint constraint=new OpportunityPlannerTool.Constraint();constraint.party=null;p.methods.put("test",constraint);assertFalse(OpportunityPlannerTool.valid(p));constraint.party="Solo";constraint.risk="Safe";constraint.gpPerHour=2_000;assertTrue(OpportunityPlannerTool.valid(p));
    }
    @Test public void afkAndTargetLevelFiltersUseActualMethodMetadata()
    {
        Fixture f=new Fixture();OpportunityPlannerTool.Preferences p=new OpportunityPlannerTool.Preferences();p.effort="AFK";p.targetLevel=40;
        List<OpportunityPlannerTool.Recommendation> result=f.opportunities.recommendations(p,f.facts(35),List.of(),"");assertFalse(result.isEmpty());
        for(OpportunityPlannerTool.Recommendation r:result){assertEquals("AFK",GoalCatalog.intensity(r.method.intensity));assertTrue(r.method.level<=40);}
    }
    @Test public void rateGapsStayUnknownAndActualLevelBandsAreUsed()
    {
        Fixture f=new Fixture();OpportunityPlannerTool.Preferences p=new OpportunityPlannerTool.Preferences();p.minutes=30;
        OpportunityPlannerTool.Recommendation gap=findMethod(f.opportunities.recommendations(p,f.facts(50),List.of(),"Tempoross"),"training:tempoross");assertNull(gap.lowXp);assertNull(gap.highXp);
        OpportunityPlannerTool.Recommendation start=findMethod(f.opportunities.recommendations(p,f.facts(35),List.of(),"Tempoross"),"training:tempoross");assertEquals(10_000,start.lowXp,.001);assertEquals(10_000,start.highXp,.001);
        OpportunityPlannerTool.Recommendation end=findMethod(f.opportunities.recommendations(p,f.facts(99),List.of(),"Tempoross"),"training:tempoross");assertEquals(22_500,end.lowXp,.001);
    }
    @Test public void finishedQuestsAreRemovedAndAuthoredOrderIsPreservedAmongEqualReadiness()
    {
        Fixture f=new Fixture();OpportunityPlannerTool.Preferences p=new OpportunityPlannerTool.Preferences();p.kind="Quests";
        Map<String,QuestState> finished=new LinkedHashMap<>();GuideCatalog.Entry first=f.guides.search("","quest").get(0);finished.put(GoalCatalog.questKey(first.title),QuestState.FINISHED);
        GoalCatalog.Facts facts=f.facts(99,finished);List<OpportunityPlannerTool.Recommendation> result=f.opportunities.recommendations(p,facts,List.of(),"");assertFalse(result.isEmpty());assertFalse(result.stream().anyMatch(r->r.guide.id.equals(first.id)));
        List<OpportunityPlannerTool.Recommendation> equal=new ArrayList<>();for(OpportunityPlannerTool.Recommendation r:result)if(r.blocked==0&&!r.goal&&r.unknown==0)equal.add(r);
        List<String> authored=new ArrayList<>();for(GuideCatalog.Entry g:f.guides.search("","quest"))authored.add(g.id);int last=-1;for(OpportunityPlannerTool.Recommendation r:equal){int index=authored.indexOf(r.guide.id);assertTrue(index>last);last=index;}
    }
    @Test public void budgetAndPersonalRiskGroupFiltersNeverGuessUnknownConstraints()
    {
        Fixture f=new Fixture();OpportunityPlannerTool.Preferences p=new OpportunityPlannerTool.Preferences();p.party="Solo";p.risk="Safe";p.budget=500;
        ActivityXpCatalog.Method method=f.methods.methods(null).stream().filter(m->m.level<=50).findFirst().get();OpportunityPlannerTool.Constraint c=new OpportunityPlannerTool.Constraint();c.party="Solo";c.risk="Safe";c.gpPerHour=1200;p.methods.put(method.id,c);
        List<OpportunityPlannerTool.Recommendation> found=f.opportunities.recommendations(p,f.facts(99),List.of(),"");assertFalse(found.stream().anyMatch(r->r.method.id.equals(method.id)));
        c.gpPerHour=1000;found=f.opportunities.recommendations(p,f.facts(99),List.of(),"");assertEquals(Long.valueOf(500),findMethod(found,method.id).cost);
        c.risk="Wilderness";found=f.opportunities.recommendations(p,f.facts(99),List.of(),"");assertFalse(found.stream().anyMatch(r->r.method.id.equals(method.id)));
    }
    @Test public void generatedPlansQueueTypedUnlocksAndReachExactFinalMilestone()
    {
        Fixture f=new Fixture();ActivityXpCatalog.Method method=f.methods.methods(Skill.MINING).stream().filter(m->m.level<=40).findFirst().get();GuideCatalog.Entry guide=f.guides.entry(method.guideId);
        List<GoalCatalog.Requirement> requirements=List.of(new GoalCatalog.Requirement("40 Mining",-1,Skill.MINING,40,null),new GoalCatalog.Requirement("Verify transport",0,null,0,null));
        OpportunityPlannerTool.Recommendation recommendation=new OpportunityPlannerTool.Recommendation(method.name,"",method,guide,requirements,false,null,null,null);
        WorkflowPlan plan=f.opportunities.build(recommendation,f.facts(1),52);assertTrue(plan.valid());assertEquals(WorkflowPlan.Kind.SKILL,plan.steps.get(0).kind);assertEquals(Experience.getXpForLevel(40),plan.steps.get(0).target);
        WorkflowPlan.Step last=plan.steps.get(plan.steps.size()-1);assertEquals(Experience.getXpForLevel(52),last.target);assertTrue(plan.steps.stream().anyMatch(s->s.kind==WorkflowPlan.Kind.NOTE&&s.label.equals("Check: Verify transport")));
        assertTrue(plan.steps.stream().anyMatch(s->s.kind==WorkflowPlan.Kind.MODULE&&s.reference.equals("Loadouts")));
        boolean prepared=false;for(WorkflowPlan.Step step:plan.steps){if(step.kind==WorkflowPlan.Kind.MODULE)prepared=true;if(prepared&&step.kind==WorkflowPlan.Kind.SKILL)assertTrue("Milestone falls below already required level",step.target>Experience.getXpForLevel(40));}
    }
    @Test public void chosenMethodSurvivesPreparationSharingAndMilestonesWithFinalEtaTarget()
    {
        Fixture f=new Fixture();ActivityXpCatalog.Method method=f.methods.methods(Skill.MINING).stream().filter(m->m.level<=40).findFirst().get();GuideCatalog.Entry guide=f.guides.entry(method.guideId);
        WorkflowPlan plan=f.opportunities.build(new OpportunityPlannerTool.Recommendation(method.name,"",method,guide,List.of(),false,null,null,null),f.facts(40),52);
        assertEquals(WorkflowPlan.Kind.MODULE,plan.next().kind);WorkflowPlan.TrainingContext context=plan.trainingContext(f.methods);assertNotNull(context);assertEquals(method.id,context.methodId);assertEquals(Skill.MINING,context.skill);assertEquals(Experience.getXpForLevel(52),context.targetXp);assertEquals(Experience.getXpForLevel(45),context.nextMilestoneXp);
        plan.steps.stream().filter(s->s.kind==WorkflowPlan.Kind.SKILL).findFirst().get().completed=true;context=plan.trainingContext(f.methods);assertEquals(Experience.getXpForLevel(52),context.targetXp);assertEquals(Experience.getXpForLevel(50),context.nextMilestoneXp);
        String code=WorkflowPlan.encode(plan,f.gson);assertTrue(code.startsWith("RWF2:"));WorkflowPlan imported=WorkflowPlan.decode(code,f.gson);assertEquals(method.id,imported.trainingContext(f.methods).methodId);assertEquals(Experience.getXpForLevel(45),imported.trainingContext(f.methods).nextMilestoneXp);
        assertNotNull(WorkflowPlan.decode("RWF1:"+code.substring(5),f.gson));imported.trainingSkill="Magic";assertNull(imported.trainingContext(f.methods));
    }
    @Test public void skillAndMethodMilestonesHandoffExactTargetAndSelectedMethod() throws Exception
    {
        Fixture f=new Fixture();f.start();WorkflowPlan.Step skill=WorkflowPlan.step(WorkflowPlan.Kind.SKILL,"Mining 60","Mining");skill.target=Experience.getXpForLevel(60);WorkflowPlan p=WorkflowPlan.create("Skill route",List.of(skill));f.plans.add(p);
        AtomicReference<Skill> openedSkill=new AtomicReference<>();AtomicReference<Long> openedTarget=new AtomicReference<>();f.plans.setOpenSkillAction((s,target)->{openedSkill.set(s);openedTarget.set(target);});JPanel[] panel=new JPanel[1];SwingUtilities.invokeAndWait(()->panel[0]=f.plans.createPanel());flush();SwingUtilities.invokeAndWait(()->button(panel[0],"Open next step").doClick());assertEquals(Skill.MINING,openedSkill.get());assertEquals(Long.valueOf(skill.target),openedTarget.get());
        ActivityXpCatalog.Method method=f.methods.methods(Skill.MINING).stream().filter(m->m.level<=40&&f.guides.entry(m.guideId)!=null).findFirst().get();WorkflowPlan linked=WorkflowPlan.create("Method route",List.of(WorkflowPlan.step(WorkflowPlan.Kind.GUIDE,"Method guide",method.guideId)));linked.methodId=method.id;linked.guideId=method.guideId;linked.trainingSkill=method.skill.name();linked.trainingTargetXp=Experience.getXpForLevel(99);f.plans.add(linked);AtomicReference<String> openedMethod=new AtomicReference<>();f.plans.setOpenMethodAction(openedMethod::set);
        SwingUtilities.invokeAndWait(()->{renderNow(f.plans);button(panel[0],"Open next step").doClick();});assertEquals(method.id,openedMethod.get());
    }
    @Test public void successfulManualAndNativeMilestonesEmitOnceWithSeparateEvidence()
    {
        Fixture f=new Fixture();f.start();List<String> events=new ArrayList<>();f.plans.setCompletionAction((plan,step)->events.add(plan.id+"|"+step.evidence));
        WorkflowPlan.Step manual=WorkflowPlan.step(WorkflowPlan.Kind.NOTE,"Manual step",""),skill=WorkflowPlan.step(WorkflowPlan.Kind.SKILL,"Native skill","Mining");skill.target=100;WorkflowPlan p=WorkflowPlan.create("Events",List.of(manual,skill));f.plans.add(p);
        f.plans.complete(p.id,manual.id,true);f.plans.complete(p.id,manual.id,true);assertEquals(List.of(p.id+"|Marked complete"),events);when(f.client.getSkillExperience(Skill.MINING)).thenReturn(100);f.plans.onGameTick();f.plans.onGameTick();assertEquals(2,events.size());assertEquals(p.id+"|Observed in game",events.get(1));
        f.plans.undo();f.plans.redo();assertEquals(2,events.size());
    }
    @Test public void typedMilestoneEditorCreatesSkillsItemsQuestsAndNotesWithoutInternalIds() throws Exception
    {
        Fixture f=new Fixture();f.start();f.plans.setModuleChoices(List.of("Guides","Loadouts","Travel"));f.plans.add(WorkflowPlan.create("Typed authoring",List.of()));JPanel[] panel=new JPanel[1];SwingUtilities.invokeAndWait(()->panel[0]=f.plans.createPanel());flush();
        SwingUtilities.invokeAndWait(()->{
            combo(panel[0],"workflow.step-type").setSelectedItem(WorkflowPlan.Kind.SKILL);combo(panel[0],"workflow.skill").setSelectedItem(Skill.MINING);spinner(panel[0],"workflow.target").setValue(60);button(panel[0],"Add step").doClick();
            assertEquals(Experience.getXpForLevel(60),f.plans.selected().steps.get(0).target);renderNow(f.plans);
            combo(panel[0],"workflow.step-type").setSelectedItem(WorkflowPlan.Kind.ITEM);field(panel[0],"workflow.item-search").setText("Rope");button(panel[0],"Search items").doClick();ItemKnowledgeCatalog.ItemRef item=(ItemKnowledgeCatalog.ItemRef)combo(panel[0],"workflow.item").getSelectedItem();assertEquals(954,item.id);button(panel[0],"Add step").doClick();assertEquals(954,f.plans.selected().steps.get(1).itemId);renderNow(f.plans);
            combo(panel[0],"workflow.step-type").setSelectedItem(WorkflowPlan.Kind.QUEST);field(panel[0],"workflow.guide-search").setText("Cook's Assistant");button(panel[0],"Search guides").doClick();button(panel[0],"Add step").doClick();assertEquals(WorkflowPlan.Kind.QUEST,f.plans.selected().steps.get(2).kind);assertTrue(f.plans.selected().steps.get(2).reference.startsWith("quest:"));renderNow(f.plans);
            combo(panel[0],"workflow.step-type").setSelectedItem(WorkflowPlan.Kind.NOTE);JTextArea note=(JTextArea)named(panel[0],"workflow.note");note.setText("Bring your teleport before leaving the bank.");button(panel[0],"Add step").doClick();assertEquals(note.getText(),f.plans.selected().steps.get(3).reference);assertFalse(text(panel[0]).contains("item ID"));
        });
    }
    @Test public void actualFindButtonBuildsMethodAwarePlanFromAccountFilters() throws Exception
    {
        Fixture f=new Fixture();f.start();JPanel[] panel=new JPanel[1];SwingUtilities.invokeAndWait(()->panel[0]=f.opportunities.createPanel());flush();
        SwingUtilities.invokeAndWait(()->{
            JTextField search=descendants(panel[0]).stream().filter(c->c instanceof JTextField&&"Activity or quest".equals(((JTextField)c).getAccessibleContext().getAccessibleName())).map(c->(JTextField)c).findFirst().get();search.setText("Tempoross");button(panel[0],"Find a plan").doClick();renderNow(f.opportunities);button(panel[0],"Build this plan").doClick();
        });WorkflowPlan plan=f.plans.selected();assertNotNull(plan);assertEquals("training:tempoross",plan.methodId);assertNotNull(plan.trainingContext(f.methods));
    }
    @Test public void tooLowTargetsAndOversizedGeneratedRoutesFailExplicitly()
    {
        Fixture f=new Fixture();ActivityXpCatalog.Method high=f.methods.methods(null).stream().filter(m->m.level>50).findFirst().get();
        OpportunityPlannerTool.Recommendation r=new OpportunityPlannerTool.Recommendation(high.name,"",high,null,List.of(),false,null,null,null);
        try{f.opportunities.build(r,f.facts(1),20);fail();}catch(IllegalArgumentException expected){}
        List<GoalCatalog.Requirement> many=new ArrayList<>();for(int i=0;i<100;i++)many.add(new GoalCatalog.Requirement("Manual check "+i,0,null,0,null));
        r=new OpportunityPlannerTool.Recommendation("Huge route","",null,null,many,false,null,null,null);try{f.opportunities.build(r,f.facts(1),99);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("100"));}
    }
    @Test public void manualCompletionMoveUndoRedoAndSelectAreDetachedAndProfileBound()
    {
        Fixture f=new Fixture();f.start();WorkflowPlan.Step a=WorkflowPlan.step(WorkflowPlan.Kind.NOTE,"One",""),b=WorkflowPlan.step(WorkflowPlan.Kind.NOTE,"Two","");WorkflowPlan p=WorkflowPlan.create("Route",List.of(a,b));f.plans.add(p);
        f.plans.complete(p.id,a.id,true);assertEquals(1,f.plans.selected().completed());f.plans.move(p.id,b.id,-1);assertEquals(b.id,f.plans.selected().steps.get(0).id);
        f.plans.undo();assertEquals(a.id,f.plans.selected().steps.get(0).id);f.plans.undo();assertEquals(0,f.plans.selected().completed());f.plans.redo();assertEquals(1,f.plans.selected().completed());
        WorkflowPlan detached=f.plans.selected();detached.steps.clear();assertEquals(2,f.plans.selected().steps.size());WorkflowPlan another=WorkflowPlan.create("Second",List.of());f.plans.add(another);f.plans.select(p.id);assertEquals(p.id,f.plans.selected().id);
        f.owner.set("rsprofile.b");assertTrue(f.plans.plans().isEmpty());assertNull(f.opportunities.facts());
    }
    @Test public void failedUndoDoesNotConsumeItsHistoryEntry()
    {
        Fixture f=new Fixture();f.start();WorkflowPlan p=WorkflowPlan.create("A route",List.of(WorkflowPlan.step(WorkflowPlan.Kind.NOTE,"Check","")));f.plans.add(p);f.plans.complete(p.id,p.steps.get(0).id,true);
        String address=RunewardenConfig.GROUP+"|"+f.owner.get()+"|"+WorkflowPlansTool.KEY;f.settings.put(address,"{\"version\":99}");
        f.store.read(RunewardenConfig.GROUP,f.owner.get(),WorkflowPlansTool.KEY,1,WorkflowPlansTool.Saved.class,WorkflowPlansTool::valid,WorkflowPlansTool.Saved::new);f.plans.undo();assertEquals(1,f.plans.selected().completed());
        f.store.reset(new RunewardenDataStore.Address(RunewardenConfig.GROUP,f.owner.get(),WorkflowPlansTool.KEY));f.plans.undo();assertEquals(0,f.plans.selected().completed());
    }
    @Test public void exactNativeQuestAndSkillFactsCompleteWhileManualStepsStayManual()
    {
        Fixture f=new Fixture();f.start();WorkflowPlan.Step quest=WorkflowPlan.step(WorkflowPlan.Kind.QUEST,"Quest","quest:the-ides-of-milk"),skill=WorkflowPlan.step(WorkflowPlan.Kind.SKILL,"Mining target","Mining"),note=WorkflowPlan.step(WorkflowPlan.Kind.NOTE,"Practice","");skill.target=100;
        WorkflowPlan p=WorkflowPlan.create("Native proofs",List.of(quest,skill,note));f.plans.add(p);when(f.client.getIntStack()).thenReturn(new int[]{2});when(f.client.getSkillExperience(Skill.MINING)).thenReturn(100);f.plans.onGameTick();
        WorkflowPlan result=f.plans.selected();assertTrue(result.steps.get(0).completed);assertTrue(result.steps.get(1).completed);assertFalse(result.steps.get(2).completed);assertEquals("Observed in game",result.steps.get(0).evidence);
    }
    @Test public void referencedQuestScanIsBoundedAndSkillReadsAreDeduplicated()
    {
        Fixture f=new Fixture();f.start();List<WorkflowPlan.Step> steps=new ArrayList<>();for(Quest q:Quest.values()){steps.add(WorkflowPlan.step(WorkflowPlan.Kind.QUEST,q.getName(),"quest:"+q.getName()));if(steps.size()==20)break;}
        for(int i=0;i<20;i++){WorkflowPlan.Step s=WorkflowPlan.step(WorkflowPlan.Kind.SKILL,"Mining "+i,"Mining");s.target=100+i;steps.add(s);}f.plans.add(WorkflowPlan.create("Bounded scan",steps));clearInvocations(f.client);f.plans.onGameTick();
        verify(f.client,times(8)).getIntStack();verify(f.client,times(1)).getSkillExperience(Skill.MINING);
    }
    @Test public void staleRenderedStartOpenAndCompleteCannotTouchTheNextProfile() throws Exception
    {
        Fixture f=new Fixture();f.start();WorkflowPlan p=WorkflowPlan.create("A's plan",List.of(WorkflowPlan.step(WorkflowPlan.Kind.MODULE,"Open supplies","Loadouts")));f.plans.add(p);AtomicReference<String> opened=new AtomicReference<>();f.plans.setActions(opened::set,id->{},id->{},point->{},plan->opened.set(plan.name));
        JPanel[] panel=new JPanel[1];SwingUtilities.invokeAndWait(()->panel[0]=f.plans.createPanel());flush();JButton start=button(panel[0],"Start this plan"),open=button(panel[0],"Open next step"),done=button(panel[0],"Complete step →");
        f.owner.set("rsprofile.b");f.account.onGameTick();f.plans.onGameTick();WorkflowPlan b=WorkflowPlan.create("B's plan",List.of(WorkflowPlan.step(WorkflowPlan.Kind.NOTE,"B's step","")));f.plans.add(b);
        SwingUtilities.invokeAndWait(()->{start.doClick();open.doClick();done.doClick();});assertNull(opened.get());assertEquals(0,f.plans.selected().completed());assertEquals(b.id,f.plans.selected().id);
    }
    @Test public void queuedOldProfileWritesAndImportReviewsAreDiscarded() throws Exception
    {
        Fixture f=new Fixture();f.start();WorkflowPlan p=WorkflowPlan.create("Shared route",List.of(WorkflowPlan.step(WorkflowPlan.Kind.NOTE,"Step","Body")));JPanel[] panel=new JPanel[1];SwingUtilities.invokeAndWait(()->panel[0]=f.plans.createPanel());flush();
        SwingUtilities.invokeAndWait(()->{JTextArea code=descendants(panel[0]).stream().filter(c->c instanceof JTextArea).map(c->(JTextArea)c).findFirst().get();code.setText(WorkflowPlan.encode(p,f.gson));button(panel[0],"Preview import").doClick();});
        f.owner.set("rsprofile.b");f.account.onGameTick();f.plans.onGameTick();SwingUtilities.invokeAndWait(()->button(panel[0],"Import reviewed plan").doClick());assertTrue(f.plans.plans().isEmpty());
        List<Runnable> pending=new ArrayList<>();doAnswer(call->{pending.add(call.getArgument(0));return null;}).when(f.thread).invokeLater(any(Runnable.class));f.plans.add(p);f.owner.set("rsprofile.c");f.account.onGameTick();f.plans.onGameTick();pending.forEach(Runnable::run);assertTrue(f.plans.plans().isEmpty());
    }
    @Test public void populatedPlansAndFindPanelsPageAndFit206PixelsWithoutNativeReadsOnSwing() throws Exception
    {
        Fixture f=new Fixture();f.start();List<WorkflowPlan.Step> steps=new ArrayList<>();for(int i=0;i<100;i++)steps.add(WorkflowPlan.step(WorkflowPlan.Kind.NOTE,"Milestone "+i,"A readable explanation for this stage of the route."));f.plans.add(WorkflowPlan.create("Long route",steps));
        JPanel[] panels=new JPanel[2];SwingUtilities.invokeAndWait(()->{panels[0]=f.plans.createPanel();panels[1]=f.opportunities.createPanel();});flush();clearInvocations(f.client);SwingUtilities.invokeAndWait(()->{
            JPanel plan=panels[0],find=panels[1];assertEquals(8,descendants(plan).stream().filter(c->c instanceof JButton&&"Done".equals(((JButton)c).getText())).count());assertTrue(text(find).contains("options"));assertNotNull(button(find,"Build this plan"));
            assertTrue(text(plan).contains("A readable explanation"));assertNotNull(button(plan,"Next"));
            for(JPanel panel:List.of(plan,find)){panel.setSize(206,15_000);layout(panel);panel.setSize(206,panel.getPreferredSize().height);layout(panel);for(Component c:descendants(panel))if(visible(c,panel)&&(c instanceof GuideText||c instanceof JTextField||c instanceof JComboBox)){assertTrue(c.getX()>=0);assertTrue(c.getX()+c.getWidth()<=c.getParent().getWidth());if(c instanceof GuideText)assertTrue(((GuideText)c).getText(),c.getHeight()>=c.getPreferredSize().height-1);}}
            int height=Math.max(plan.getHeight(),find.getHeight());BufferedImage image=new BufferedImage(424,height,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D graphics=image.createGraphics();graphics.setColor(SidebarUi.BACKGROUND);graphics.fillRect(0,0,424,height);plan.printAll(graphics);java.awt.Graphics2D second=(java.awt.Graphics2D)graphics.create(218,0,206,height);find.printAll(second);second.dispose();graphics.dispose();
            try{java.io.File output=new java.io.File("build/reports/plans-and-find-206px.png");output.getParentFile().mkdirs();javax.imageio.ImageIO.write(image,"png",output);}catch(java.io.IOException error){throw new AssertionError(error);}
        });flush();verifyNoInteractions(f.client);
    }
    private static OpportunityPlannerTool.Recommendation findMethod(List<OpportunityPlannerTool.Recommendation> list,String id){return list.stream().filter(r->r.method!=null&&id.equals(r.method.id)).findFirst().orElseThrow(()->new AssertionError(id));}
    private static List<Component> descendants(Container root){List<Component> list=new ArrayList<>();for(Component c:root.getComponents()){list.add(c);if(c instanceof Container)list.addAll(descendants((Container)c));}return list;}
    private static JButton button(Container root,String text){return descendants(root).stream().filter(c->c instanceof JButton&&text.equals(((JButton)c).getText())).map(c->(JButton)c).findFirst().orElseThrow(()->new AssertionError(text));}
    private static String text(Container root){StringBuilder text=new StringBuilder();for(Component c:descendants(root))if(c instanceof GuideText)text.append(((GuideText)c).getText()).append('\n');return text.toString();}
    private static void layout(Container root){for(int i=0;i<8;i++)deepLayout(root);}private static void deepLayout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container)deepLayout((Container)c);}
    private static boolean visible(Component c,Component root){for(Component p=c;p!=null&&p!=root;p=p.getParent())if(!p.isVisible())return false;return true;}
    private static void renderNow(Object tool){try{java.lang.reflect.Method method=tool.getClass().getDeclaredMethod("render");method.setAccessible(true);method.invoke(tool);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static Component named(Container root,String name){return descendants(root).stream().filter(c->name.equals(c.getName())).findFirst().orElseThrow(()->new AssertionError(name));}
    private static JComboBox<?> combo(Container root,String name){return (JComboBox<?>)named(root,name);}private static JTextField field(Container root,String name){return (JTextField)named(root,name);}private static JSpinner spinner(Container root,String name){return (JSpinner)named(root,name);}
    private static void flush() throws Exception{SwingUtilities.invokeAndWait(()->{});SwingUtilities.invokeAndWait(()->{});}
    static final class Fixture
    {
        final SidebarLayoutTest.Fixture graph=new SidebarLayoutTest.Fixture();final Gson gson=graph.get(Gson.class);final Client client=graph.get(Client.class);final ClientThread thread=graph.get(ClientThread.class);final ConfigManager config=graph.get(ConfigManager.class);
        final AtomicReference<String> owner=new AtomicReference<>("rsprofile.a");final Map<String,String> settings=new HashMap<>();final RunewardenDataStore store;final GuideCatalog guides;final ActivityXpCatalog methods;final AccountProgressTool account;final WorkflowPlansTool plans;final OpportunityPlannerTool opportunities;
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));when(client.getIntStack()).thenReturn(new int[]{1});when(client.getRealSkillLevel(any(Skill.class))).thenReturn(50);
            when(config.getRSProfileKey()).thenAnswer(call->owner.get());when(config.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(call->settings.get(call.getArgument(0)+"|"+call.getArgument(1)+"|"+call.getArgument(2)));
            when(config.getConfiguration(anyString(),anyString())).thenAnswer(call->settings.get("global|"+call.getArgument(0)+"|"+call.getArgument(1)));
            doAnswer(call->{settings.put(call.getArgument(0)+"|"+call.getArgument(1)+"|"+call.getArgument(2),call.getArgument(3));return null;}).when(config).setConfiguration(anyString(),anyString(),anyString(),anyString());
            doAnswer(call->{settings.put("global|"+call.getArgument(0)+"|"+call.getArgument(1),call.getArgument(2));return null;}).when(config).setConfiguration(anyString(),anyString(),anyString());
            doAnswer(call->{settings.remove(call.getArgument(0)+"|"+call.getArgument(1)+"|"+call.getArgument(2));return null;}).when(config).unsetConfiguration(anyString(),anyString(),anyString());
            doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
            store=graph.get(RunewardenDataStore.class);guides=graph.get(GuideCatalog.class);methods=graph.get(ActivityXpCatalog.class);account=graph.get(AccountProgressTool.class);plans=graph.get(WorkflowPlansTool.class);opportunities=graph.get(OpportunityPlannerTool.class);
        }
        void start(){account.start();for(int i=0;i<8;i++)account.onGameTick();plans.start();plans.onGameTick();opportunities.start();opportunities.onGameTick();}
        GoalCatalog.Facts facts(int level){return facts(level,Collections.emptyMap());}
        GoalCatalog.Facts facts(int level,Map<String,QuestState> quests){Map<Skill,Integer> levels=new EnumMap<>(Skill.class),xp=new EnumMap<>(Skill.class);for(Skill skill:Skill.values()){levels.put(skill,level);xp.put(skill,Experience.getXpForLevel(level));}return new GoalCatalog.Facts(xp,levels,quests,126);}
    }
}
