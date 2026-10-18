/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;
import com.google.gson.Gson;
import java.util.HashSet;
import java.util.Map;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;
public class SailingXpScenariosTest
{
    final SailingXpScenarios scenarios=new SailingXpScenarios(new Gson());
    @Test public void allTwelveRankRewardsArePinnedAndFirstBonusesCountOnce()
    {int count=0;for(String method:new String[]{"training:tempor-tantrum","training:advanced-barracuda","activity:sailing-gwenith-glide"})for(SailingXpScenarios.Trial t:scenarios.trials(method)){count++;assertTrue(t.source.contains("oldid="+t.revision));assertEquals(2L*(t.completionXp+t.supplyXp)+t.firstRankBonus,t.experience(2,true));assertEquals(0,t.experience(0,true));}assertEquals(12,count);
        SailingXpScenarios.Trial gwen=scenarios.trials("activity:sailing-gwenith-glide").get(3);assertEquals(194_100,gwen.experience(10,false));assertEquals(244_100,gwen.experience(10,true));}
    @Test public void courierTaskIdsAndBaseRewardsAreExactAndUnknownTaskIsExcluded()
    {assertEquals(438,scenarios.couriers().size());HashSet<Integer> ids=new HashSet<>();for(SailingXpScenarios.Courier c:scenarios.couriers()){assertTrue(ids.add(c.id));assertTrue(c.xp>0);assertNotEquals(91,c.id);}SailingXpScenarios.Courier first=scenarios.couriers().stream().filter(t->t.id==1).findFirst().get();assertEquals(780,first.experience(10));assertEquals("Port Sarim",first.cargoLocation);}
    @Test public void measuredMethodPaceUsesOnlyMatchingAttributedTrainingXpAndWeightsTime()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();ActivityXpCatalog catalog=new ActivityXpCatalog(new Gson(),new GuideCatalog(new Gson()));
        ActivityXpCatalog.Method chosen=catalog.find("training:tempoross");ActivityXpTool tool=new ActivityXpTool(catalog,f.session,scenarios);
        f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,chosen.id,chosen.name,chosen.guideId,chosen.id,chosen.skill,0));f.tick(100);
        f.session.observeXp(f.session.snapshot().runId,chosen.skill,1000);f.session.reportTraining(f.session.snapshot().runId,1000,1,60000);f.session.observeXp(f.session.snapshot().runId,chosen.skill,5000);f.session.finish();assertEquals(60_000,tool.measuredRate(chosen),.01);
        ActivityXpCatalog.Method other=catalog.find("training:sailing-courier");assertEquals(0,tool.measuredRate(other),.01);
        f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,chosen.id,chosen.name,chosen.guideId,chosen.id,chosen.skill,0));f.tick(200);f.session.observeXp(f.session.snapshot().runId,chosen.skill,1000);f.session.reportTraining(f.session.snapshot().runId,1000,1,120000);
        assertEquals(40_000,tool.measuredRate(chosen),.01);
    }
    @Test public void invalidAndOverflowScenariosAreRejected()
    {for(long n:new long[]{-1,1_000_001,Long.MAX_VALUE}){try{scenarios.trials("training:tempor-tantrum").get(0).experience(n,false);fail();}catch(IllegalArgumentException expected){}try{scenarios.couriers().get(0).experience(n);fail();}catch(IllegalArgumentException expected){}}}
    @Test public void trainingBrowserOpensTrialAndCourierScenariosAtObservedLevels() throws Exception
    {ActivityXpCatalog catalog=new ActivityXpCatalog(new Gson(),new GuideCatalog(new Gson()));ActivityXpTool tool=new ActivityXpTool(catalog,null,scenarios);
        SwingUtilities.invokeAndWait(()->{tool.updateSnapshot(Map.of(),Map.of(Skill.SAILING,10_000_000),Map.of(Skill.SAILING,99),true);javax.swing.JPanel panel=tool.createPanel();tool.openMethod("activity:sailing-gwenith-glide");assertTrue(panel.getPreferredSize().width<=225);tool.openMethod("training:sailing-courier");assertTrue(panel.getPreferredSize().width<=225);});}
}
