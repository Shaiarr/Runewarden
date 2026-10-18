/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** A quest reference hands control to the real companion and never resurrects our removed walkthrough. */
public class QuestHelperHandoffTest
{
    @Test public void questSelectionHandsOffTheExactQuestAndClearsOurPreviousGuide() throws Exception
    {
        Fixture f=new Fixture(); List<String> requests=new ArrayList<>();
        f.guides.setQuestHelperAction(id->{requests.add(id);return true;});
        SwingUtilities.invokeAndWait(()->{
            f.guides.createPanel();f.guides.openGuide("training:giants-foundry");assertNotNull(f.guides.selectedGuide());
            f.guides.openGuide("quest:Cook's Assistant");
            assertEquals(List.of("quest:Cook's Assistant"),requests);assertNull(f.guides.selectedGuide());
            assertNull(f.guides.currentStep());assertTrue(f.guides.currentStepItems().isEmpty());
            assertNull(f.coach.render(f.graphics));assertNull(f.markers.selectedPack());
        }); f.graphics.dispose();
        verify(f.config,never()).getRSProfileConfiguration(eq("runewarden"),eq(GuideProgress.positionKey("quest:Cook's Assistant")),eq(Integer.class));
    }
    @Test public void optimalQuestBrowserOpensQuestHelperAndHasNoOwnWalkthroughControls() throws Exception
    {
        Fixture f=new Fixture();List<String> requests=new ArrayList<>();f.guides.setQuestHelperAction(id->{requests.add(id);return true;});
        SwingUtilities.invokeAndWait(()->{
            JComponent panel=f.guides.createPanel();List<Component> all=descendants(panel);
            JComboBox<?> category=(JComboBox<?>)all.stream().filter(c->c instanceof JComboBox).findFirst().get();
            assertEquals("Quest Helper",category.getItemAt(1));category.setSelectedIndex(1);
            JList<?> list=(JList<?>)all.stream().filter(c->c instanceof JList).findFirst().get();
            assertEquals(238,list.getModel().getSize());list.setSelectedIndex(0);assertEquals(1,requests.size());
            assertNull(f.guides.selectedGuide());
            for(Component c:descendants(panel))if(c instanceof JCheckBox)
            {
                String title=((JCheckBox)c).getText();assertNotEquals("Follow quest progress",title);assertNotEquals("Highlight quest interactions",title);
            }
        });f.graphics.dispose();
    }
    @Test public void queuedQuestHandoffIsInvalidatedByEndRunAndByProfileChange() throws Exception
    {
        for(boolean profile:List.of(false,true))
        {
            Fixture f=new Fixture();List<String> requests=new ArrayList<>();f.guides.setQuestHelperAction(id->{requests.add(id);return true;});
            SwingUtilities.invokeAndWait(()->{
                Thread worker=new Thread(()->f.guides.openGuide("quest:Romeo & Juliet"));worker.start();
                try{worker.join(2000);}catch(InterruptedException e){throw new AssertionError(e);}assertFalse(worker.isAlive());
                if(profile)f.guides.onProfileChanged();else f.guides.clearSelection();
            });
            SwingUtilities.invokeAndWait(()->{assertTrue(requests.isEmpty());assertNull(f.guides.selectedGuide());});f.graphics.dispose();
        }
    }
    @Test public void unavailableCompanionNeverFallsBackToOurDeletedQuestObserver() throws Exception
    {
        Fixture f=new Fixture();f.guides.start();
        SwingUtilities.invokeAndWait(()->{f.guides.createPanel();f.guides.openForRun("quest:Romeo & Juliet");});
        clearInvocations(f.client);f.guides.onGameTick();
        SwingUtilities.invokeAndWait(()->{assertNull(f.guides.selectedGuide());assertNull(f.guides.currentStep());});
        verify(f.client,never()).getVarpValue(144);verify(f.client,never()).getVarbitValue(anyInt());
        verify(f.overlays,times(2)).add(any(Overlay.class));f.graphics.dispose();
    }
    private static List<Component> descendants(Container root)
    {
        List<Component> result=new ArrayList<>();for(Component c:root.getComponents()){result.add(c);if(c instanceof Container)result.addAll(descendants((Container)c));}return result;
    }
    private static final class Fixture
    {
        final Client client=mock(Client.class);final ClientThread thread=mock(ClientThread.class);final ConfigManager config=mock(ConfigManager.class);
        final OverlayManager overlays=mock(OverlayManager.class);final GuideMarkerOverlay markers=new GuideMarkerOverlay(client);
        final GuideCoachOverlay coach=new GuideCoachOverlay(client);final Graphics2D graphics=new BufferedImage(500,500,BufferedImage.TYPE_INT_ARGB).createGraphics();
        final RunewardenGuides guides;
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getIntStack()).thenReturn(new int[]{0});
            guides=new RunewardenGuides(client,thread,config,new GuideCatalog(new Gson()),new GuideNavigation(client,mock(EventBus.class),mock(PluginManager.class)),markers,coach,overlays);
        }
    }
}
