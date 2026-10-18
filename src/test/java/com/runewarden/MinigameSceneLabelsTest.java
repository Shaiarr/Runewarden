/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.mockito.Mockito.*;

/** The native literal string "null" must never become a numbered scene label. */
public class MinigameSceneLabelsTest
{
    @Test public void foundryMouldWrapperUsesItsResolvedNativeName() throws Exception
    {
        Fixture fixture=new Fixture(44777,"null");
        ObjectComposition resolved=mock(ObjectComposition.class);when(resolved.getName()).thenReturn("<col=ffffff>Mould jig</col>");
        when(fixture.definition.getImpostorIds()).thenReturn(new int[]{44625});when(fixture.definition.getImpostor()).thenReturn(resolved);
        fixture.render("1 · Mould jig");
    }
    @Test public void missingOrPlaceholderMouldNamesUseTheKnownStationName() throws Exception
    {
        for(String name:new String[]{null,"null","NULL","<col=ffffff>null</col>"," ","\u00a0","undefined"})
            new Fixture(44777,name).render("1 · Mould jig");
    }
    @Test public void absentMouldDefinitionAndUnavailableImpostorKeepUsefulFallback() throws Exception
    {
        Fixture missing=new Fixture(44777,null);when(missing.client.getObjectDefinition(44777)).thenReturn(null);missing.render("1 · Mould jig");
        Fixture unresolved=new Fixture(44777,"null");when(unresolved.definition.getImpostorIds()).thenReturn(new int[]{44625});unresolved.render("1 · Mould jig");
    }
    @Test public void unavailableImpostorRetainsAValidBaseName() throws Exception
    {
        Fixture fixture=new Fixture(44777,"<col=00ff00>Commission mould</col>");when(fixture.definition.getImpostorIds()).thenReturn(new int[]{44625});fixture.render("1 · Commission mould");
    }
    @Test public void unknownResourceUsesGenericFallbackAndValidNamesKeepTheirText() throws Exception
    {
        new Fixture(29311,"null").render("1 · Resource");
        new Fixture(29311,"  Bruma roots  ").render("1 · Bruma roots");
    }
    @Test public void disablingNumbersStillUsesASanitisedResourceName() throws Exception
    {
        Fixture fixture=new Fixture(44777,"null");fixture.render("Mould jig",false);
    }
    @Test @SuppressWarnings("unchecked") public void npcPlaceholderKeepsItsHighlightWithAReadableFallback() throws Exception
    {
        Client client=mock(Client.class);WorldView view=mock(WorldView.class);net.runelite.api.NPC npc=mock(net.runelite.api.NPC.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));when(client.getTopLevelWorldView()).thenReturn(view);
        when(npc.getWorldView()).thenReturn(view);when(npc.getId()).thenReturn(11472);when(npc.getName()).thenReturn("<col=ffffff>null</col>");
        net.runelite.api.IndexedObjectSet<net.runelite.api.NPC> npcs=mock(net.runelite.api.IndexedObjectSet.class);when(npcs.iterator()).thenAnswer(e->List.of(npc).iterator());doReturn(npcs).when(view).npcs();
        MinigameActivityCatalog.Snapshot snapshot=new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.GIANTS_FOUNDRY,"Giants' Foundry",null,List.of(),List.of(),Set.of(),List.of(),true,Set.of(11472));
        MinigameCoach coach=new MinigameCoach(client,new MinigameActivityCatalog(),mock(ConfigManager.class),new Gson(),new RunewardenConfig(){},()->snapshot);
        coach.start();coach.onGameTick();Graphics2D graphics=new BufferedImage(600,400,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try{coach.scene().render(graphics);verify(npc).getCanvasTextLocation(graphics,"1 · NPC",20);}
        finally{graphics.dispose();coach.stop();SwingUtilities.invokeAndWait(()->{});}
    }
    private static final class Fixture
    {
        final Client client=mock(Client.class);final GameObject object=mock(GameObject.class);
        final ObjectComposition definition=mock(ObjectComposition.class);final MinigameCoach coach;
        Fixture(int id,String name)
        {
            WorldView view=mock(WorldView.class);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(client.getTopLevelWorldView()).thenReturn(view);when(object.getWorldView()).thenReturn(view);
            when(object.getId()).thenReturn(id);when(object.getWorldLocation()).thenReturn(new WorldPoint(3361,11487,0));
            when(client.getObjectDefinition(id)).thenReturn(definition);when(definition.getName()).thenReturn(name);
            MinigameActivityCatalog.Snapshot snapshot=new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.GIANTS_FOUNDRY,"Giants' Foundry",null,List.of(),List.of(),Set.of(id),List.of(),true);
            coach=new MinigameCoach(client,new MinigameActivityCatalog(),mock(ConfigManager.class),new Gson(),new RunewardenConfig(){},()->snapshot);
        }
        void render(String expected) throws Exception { render(expected,true); }
        void render(String expected,boolean numbered) throws Exception
        {
            coach.start();coach.preferences().numberedTargets=numbered;coach.onGameTick();coach.onGameObjectSpawned(object);
            Graphics2D graphics=new BufferedImage(600,400,BufferedImage.TYPE_INT_ARGB).createGraphics();
            try{coach.scene().render(graphics);verify(object).getCanvasTextLocation(graphics,expected,20);}
            finally{graphics.dispose();coach.stop();SwingUtilities.invokeAndWait(()->{});}
        }
    }
}
