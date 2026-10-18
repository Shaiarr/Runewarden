package com.runewarden;
import java.util.*;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;
public class ActivitySelectionTest
{
    @Test public void openingTheMethodsGuidePreservesItsSkillAndTarget()
    {ActivitySelection flow=new ActivitySelection();List<SharedActivitySession.Context> selections=new ArrayList<>();GuideCatalog.Entry guide=guide("training:test");SharedActivitySession.Context method=new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"method","My method",guide.id,"recipe",Skill.HERBLORE,10000);flow.update(MinigameActivityCatalog.Snapshot.EMPTY,guide,method,selections::add);assertTrue(selections.isEmpty());}
    @Test public void leavingAndReenteringTheSameActivityPreparesItAgain()
    {ActivitySelection flow=new ActivitySelection();List<SharedActivitySession.Context> selections=new ArrayList<>();MinigameActivityCatalog.Snapshot game=new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.TEMPOROSS,"Tempoross","minigame:tempoross",Collections.emptyList(),Collections.emptyList(),Collections.emptySet(),Collections.emptyList(),true);flow.update(game,null,null,selections::add);flow.update(game,null,null,selections::add);assertEquals(1,selections.size());flow.update(MinigameActivityCatalog.Snapshot.EMPTY,null,null,selections::add);flow.update(game,null,null,selections::add);assertEquals(2,selections.size());assertEquals(SharedActivitySession.Kind.MINIGAME,selections.get(1).kind);}
    @Test public void anUnrelatedGuideCanPrepareANewActivityWithoutTouchingARun()
    {ActivitySelection flow=new ActivitySelection();List<SharedActivitySession.Context> selections=new ArrayList<>();GuideCatalog.Entry guide=guide("quest:next");guide.category="quest";SharedActivitySession.Context method=new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"method","Current method","training:previous","recipe",Skill.COOKING,1000);flow.update(MinigameActivityCatalog.Snapshot.EMPTY,guide,method,selections::add);assertEquals(1,selections.size());assertEquals(SharedActivitySession.Kind.QUEST,selections.get(0).kind);assertEquals("recipe",method.methodId);}
    @Test public void directTrainingAndMinigameGuidesPreserveTheirRealSkillForMeasurements()
    {ActivitySelection flow=new ActivitySelection();List<SharedActivitySession.Context> selections=new ArrayList<>();GuideCatalog.Entry guide=guide("training:rift");guide.category="minigame";guide.skill="runecrafting";flow.update(MinigameActivityCatalog.Snapshot.EMPTY,guide,null,selections::add);assertEquals(SharedActivitySession.Kind.MINIGAME,selections.get(0).kind);assertEquals(Skill.RUNECRAFT,selections.get(0).skill);assertEquals(guide.id,selections.get(0).methodId);
        GuideCatalog.Entry wood=guide("training:wood");wood.skill="woodcutting";flow.update(MinigameActivityCatalog.Snapshot.EMPTY,wood,null,selections::add);assertEquals(SharedActivitySession.Kind.TRAINING,selections.get(1).kind);assertEquals(Skill.WOODCUTTING,selections.get(1).skill);}
    private GuideCatalog.Entry guide(String id){GuideCatalog.Entry entry=new GuideCatalog.Entry();entry.id=id;entry.title="Selected guide";entry.category="training";return entry;}
}
