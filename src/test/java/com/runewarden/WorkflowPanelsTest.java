/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises populated narrow panels and ensures Swing only consumes game-thread snapshots. */
public class WorkflowPanelsTest
{
    @Test public void populatedClueTravelAndProductionPanelsFitAndKeepClientReadsOffSwing() throws Exception
    {
        Client client=mock(Client.class); Player player=mock(Player.class); ClientThread thread=mock(ClientThread.class);
        ConfigManager config=mock(ConfigManager.class); PluginManager plugins=mock(PluginManager.class); ItemManager items=mock(ItemManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
        when(player.getName()).thenReturn("Test character"); when(player.getWorldLocation()).thenReturn(new WorldPoint(3200,3200,0));
        when(config.getRSProfileKey()).thenReturn("rsprofile.test"); when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
        when(items.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0));
        when(items.getImage(anyInt())).thenReturn(new AsyncBufferedImage(thread,32,32,BufferedImage.TYPE_INT_ARGB));
        Widget bankWidget=mock(Widget.class); when(client.getWidget(InterfaceID.Bankmain.ITEMS)).thenReturn(bankWidget);
        ItemContainer bank=mock(ItemContainer.class),inventory=mock(ItemContainer.class),equipment=mock(ItemContainer.class);
        when(bank.getItems()).thenReturn(new Item[]{new Item(1617,100),new Item(2357,40)});
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.SARADOMINBOOK_COMPLETE,1)}); when(equipment.getItems()).thenReturn(new Item[0]);
        when(client.getItemContainer(InventoryID.BANK)).thenReturn(bank); when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory); when(client.getItemContainer(InventoryID.WORN)).thenReturn(equipment);
        OwnershipLedger owned=new OwnershipLedger(client,config,new Gson(),() -> 1000L); owned.start(); owned.onGameTick();
        ClueScrollPlugin nativeClue=mock(ClueScrollPlugin.class); when(plugins.getPlugins()).thenReturn(Collections.singletonList(nativeClue)); when(plugins.isPluginEnabled(nativeClue)).thenReturn(true);
        when(nativeClue.getClue()).thenReturn(EmoteClue.forText("Bow upstairs in the Edgeville Monastery. Equip a completed prayer book."));
        ClueDataService data=new ClueDataService(client,plugins,owned,config,new Gson()); data.onGameTick(); data.onGameTick(); data.onGameTick();
        assertFalse(data.snapshot().key.isEmpty());
        GuideNavigation travel=new GuideNavigation(client,mock(EventBus.class),plugins,thread,config,null);
        ClueCoachTool clues=new ClueCoachTool(data,travel,thread,items);
        BankedExperience production=new BankedExperience(client,items); production.start(); production.onGameTick();
        clearInvocations(client);
        SwingUtilities.invokeAndWait(() -> {
            LookAndFeel previousLook=UIManager.getLookAndFeel();
            try
            {
            net.runelite.client.ui.laf.RuneLiteLAF.setup();
            List<JPanel> panels=Arrays.asList(clues.createPanel(),travel.createPanel(),production.createPanel());
            for (JPanel panel : panels)
            {
                for (Component component : descendants(panel)) if (component instanceof JComboBox && "banked-xp.finish-method".equals(component.getName()))
                    for (int i=0;i<((JComboBox<?>)component).getItemCount();i++)
                        if (((JComboBox<?>)component).getItemAt(i) instanceof BankedExperienceCalculator.Recipe
                            && "crafting:diamond_bracelet".equals(((BankedExperienceCalculator.Recipe)((JComboBox<?>)component).getItemAt(i)).id))
                        { ((JComboBox<?>)component).setSelectedIndex(i); break; }
                panel.setSize(206,5000); layout(panel); panel.setSize(206,panel.getPreferredSize().height); layout(panel);
                for (Component component : descendants(panel)) if (component instanceof GuideText)
                    assertTrue("Wrapped text clipped: "+((GuideText)component).getText(),component.getHeight()>=component.getPreferredSize().height);
            }
            try
            {
                int height=0; for (JPanel panel : panels) height=Math.max(height,panel.getHeight());
                BufferedImage image=new BufferedImage(206*3+24,height,BufferedImage.TYPE_INT_ARGB); Graphics2D graphics=image.createGraphics();
                graphics.setColor(SidebarUi.BACKGROUND); graphics.fillRect(0,0,image.getWidth(),image.getHeight());
                for (int i=0;i<panels.size();i++) { Graphics2D cell=(Graphics2D)graphics.create((206+12)*i,0,206,height); panels.get(i).printAll(cell); cell.dispose(); }
                graphics.dispose(); java.io.File output=new java.io.File("build/reports/workflow-panels-206px.png"); output.getParentFile().mkdirs(); ImageIO.write(image,"png",output);
            }
            catch (java.io.IOException error) { throw new RuntimeException(error); }
            }
            finally
            {
                try { UIManager.setLookAndFeel(previousLook); }
                catch (UnsupportedLookAndFeelException error) { throw new RuntimeException(error); }
            }
        });
        verifyNoInteractions(client);
    }
    private static List<Component> descendants(Container root)
    { List<Component> all=new ArrayList<>(); for (Component child : root.getComponents()) { all.add(child); if (child instanceof Container) all.addAll(descendants((Container)child)); } return all; }
    private static void layout(Container root) { root.doLayout(); for (Component child : root.getComponents()) if (child instanceof Container) layout((Container)child); }
}
