package com.runewarden;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;
public class RoutinePlannerToolTest
{
    @Test public void dailyRolloverIsUtcAndOnceNeverBecomesDueAgain()
    {RoutinePlannerTool.Routine r=routine();r.completed=Instant.parse("2026-10-16T23:59:00Z").toEpochMilli();r.cadence=RoutinePlannerTool.Cadence.DAILY;assertFalse(RoutinePlannerTool.due(r,Instant.parse("2026-10-16T23:59:59Z")));assertTrue(RoutinePlannerTool.due(r,Instant.parse("2026-10-17T00:00:00Z")));r.cadence=RoutinePlannerTool.Cadence.ONCE;assertFalse(RoutinePlannerTool.due(r,Instant.parse("2030-01-01T00:00:00Z")));}
    @Test public void weeklyRepeatsSevenUtcDatesAfterUserCompletionWithoutClaimingGameReset()
    {RoutinePlannerTool.Routine r=routine();r.cadence=RoutinePlannerTool.Cadence.WEEKLY;r.completed=Instant.parse("2026-10-16T22:00:00Z").toEpochMilli();assertFalse(RoutinePlannerTool.due(r,Instant.parse("2026-10-22T23:00:00Z")));assertTrue(RoutinePlannerTool.due(r,Instant.parse("2026-10-23T00:00:00Z")));}
    @Test public void invalidAndDuplicateImportedRoutineEntriesAreRejected()
    {RoutinePlannerTool.Plan p=new RoutinePlannerTool.Plan();p.routines.add(routine());assertTrue(RoutinePlannerTool.valid(p));p.routines.add(routine());assertFalse(RoutinePlannerTool.valid(p));p.routines.clear();RoutinePlannerTool.Routine r=routine();r.completed=-1;p.routines.add(r);assertFalse(RoutinePlannerTool.valid(p));}
    @Test public void customRoutineCanCompleteResetRemoveAndUndoWithoutModuleLinks() throws Exception
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();String key=ActivitySessionTest.key(RoutinePlannerTool.GROUP,f.profile.get(),RoutinePlannerTool.KEY);
        f.settings.put(key,"{\"version\":1,\"routines\":[{\"id\":\"legacy\",\"label\":\"My herb run\",\"module\":\"Farm runs\",\"cadence\":\"DAILY\",\"completed\":0}],\"hiddenClaims\":[]}");
        RoutinePlannerTool tool=new RoutinePlannerTool(f.config,f.store);tool.start();tool.onGameTick();
        javax.swing.SwingUtilities.invokeAndWait(()->{
            javax.swing.JPanel panel=tool.createPanel();((javax.swing.JComboBox<?>)components(panel).stream().filter(c->c instanceof javax.swing.JComboBox).findFirst().get()).setSelectedIndex(2);
            assertFalse(components(panel).stream().filter(c->c instanceof javax.swing.JButton).map(c->((javax.swing.JButton)c).getText()).anyMatch(text->text.startsWith("Open ")));
            button(panel,"Complete").doClick();assertTrue(f.settings.get(key).contains("\"completed\":"));assertFalse(f.settings.get(key).contains("\"completed\":0"));
            button(panel,"Reset").doClick();assertTrue(f.settings.get(key).contains("\"completed\":0"));
            button(panel,"Remove").doClick();assertTrue(f.settings.get(key).contains("\"routines\":[]"));
            button(panel,"Undo last change").doClick();assertTrue(f.settings.get(key).contains("My herb run"));
            f.profile.set("rsprofile.bob");button(panel,"Complete").doClick();assertFalse(f.settings.containsKey(ActivitySessionTest.key(RoutinePlannerTool.GROUP,f.profile.get(),RoutinePlannerTool.KEY)));
        });tool.stop();
    }
    private static javax.swing.JButton button(java.awt.Container root,String text)
    {return (javax.swing.JButton)components(root).stream().filter(c->c instanceof javax.swing.JButton&&text.equals(((javax.swing.JButton)c).getText())).findFirst().orElseThrow();}
    private static java.util.List<java.awt.Component> components(java.awt.Container root)
    {java.util.List<java.awt.Component> result=new java.util.ArrayList<>();for(java.awt.Component child:root.getComponents()){result.add(child);if(child instanceof java.awt.Container)result.addAll(components((java.awt.Container)child));}return result;}
    private RoutinePlannerTool.Routine routine(){RoutinePlannerTool.Routine r=new RoutinePlannerTool.Routine();r.id="stable";r.label="My weekly run";r.module="Farm runs";r.cadence=RoutinePlannerTool.Cadence.ONCE;return r;}
}
