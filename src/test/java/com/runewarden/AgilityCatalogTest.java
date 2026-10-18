/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Function;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Compares the adapted dataset with the actual pinned client dependency, not a second handwritten fixture. */
public class AgilityCatalogTest
{
    @Test @SuppressWarnings("unchecked") public void everyNativeCourseRegionEndPointAndXpTotalMatchesThePinnedRuneLiteClient() throws Exception
    {
        Class<?> nativeCourses=Class.forName("net.runelite.client.plugins.agility.Courses");Object[] constants=nativeCourses.getEnumConstants();assertEquals(21,constants.length);assertEquals(constants.length,AgilityCourseCatalog.values().length);
        Method region=nativeCourses.getDeclaredMethod("getRegionId"),ends=nativeCourses.getDeclaredMethod("getCourseEndWorldPoints"),xp=nativeCourses.getDeclaredMethod("getTotalXpProvider");region.setAccessible(true);ends.setAccessible(true);xp.setAccessible(true);
        Client client=mock(Client.class);when(client.getRealSkillLevel(Skill.AGILITY)).thenReturn(80);
        for(Object reference:constants)
        {
            AgilityCourseCatalog actual=AgilityCourseCatalog.valueOf(((Enum<?>)reference).name());assertEquals(region.invoke(reference),actual.region);
            WorldPoint[] points=(WorldPoint[])ends.invoke(reference);assertArrayEquals(points==null?new WorldPoint[0]:points,actual.ends);
            double expected=((Function<Client,Double>)xp.invoke(reference)).apply(client);if(actual==AgilityCourseCatalog.PYRAMID)expected+=940;
            assertEquals(actual.name(),expected,actual.xp(client),.00001);
        }
    }
    @Test @SuppressWarnings("unchecked") public void allFiveNativeObstacleSetsMatchThePinnedClient() throws Exception
    {
        Class<?> nativeObstacles=Class.forName("net.runelite.client.plugins.agility.Obstacles");
        for(String name:new String[]{"OBSTACLE_IDS","PORTAL_OBSTACLE_IDS","TRAP_OBSTACLE_IDS","SEPULCHRE_OBSTACLE_IDS","SEPULCHRE_SKILL_OBSTACLE_IDS"})
        {Field original=nativeObstacles.getDeclaredField(name);original.setAccessible(true);Field adapted=AgilityObstacles.class.getDeclaredField(name);adapted.setAccessible(true);assertEquals(name,(Set<Integer>)original.get(null),adapted.get(null));}
    }
    @Test public void diaryAndPyramidXpChangesAlterGoalEstimates()
    {
        Client client=mock(Client.class);when(client.getRealSkillLevel(Skill.AGILITY)).thenReturn(99);assertEquals(1722,AgilityCourseCatalog.PYRAMID.xp(client),0);
        when(client.getVarbitValue(VarbitID.DESERT_DIARY_HARD_COMPLETE)).thenReturn(1);assertEquals(1016,AgilityCourseCatalog.POLLNIVNEACH.xp(client),0);
        when(client.getVarbitValue(VarbitID.FREMENNIK_DIARY_HARD_COMPLETE)).thenReturn(1);assertEquals(920,AgilityCourseCatalog.RELLEKA.xp(client),0);
    }
    @Test public void endTilesRequireTheExactPlaneAndCourse()
    {assertTrue(AgilityCourseCatalog.DRAYNOR.finish(new WorldPoint(3103,3261,0)));assertFalse(AgilityCourseCatalog.DRAYNOR.finish(new WorldPoint(3103,3261,1)));assertNull(AgilityCourseCatalog.region(0));assertNull(AgilityCourseCatalog.region(-1));}
}
