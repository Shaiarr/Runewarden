/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TrainingMethodIntegrationTest
{
    private final ActivityXpCatalog catalog=new ActivityXpCatalog(new Gson(),new GuideCatalog(new Gson()));
    @Test public void trainingBrowserMethodUsesItsBankRecipeForMissingSupplyEvidence()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();
        TrainingProgressTool tool=new TrainingProgressTool(f.client,mock(ClientThread.class),f.config,f.store,f.session,mock(RunewardenConfig.class),null,catalog);
        ActivityXpCatalog.Method method=catalog.find("training:super-attack");
        f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,method.id,method.name,method.guideId,method.id,method.skill,0));
        when(f.client.getSkillExperience(Skill.HERBLORE)).thenReturn(1000);
        ItemContainer inventory=mock(ItemContainer.class);when(inventory.getItems()).thenReturn(new Item[0]);when(f.client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
        tool.start();tool.onGameTick();assertEquals(TrainingProgressTool.StopCause.MATERIALS_MISSING,tool.reading().cause);
    }
    @Test public void personalMethodPaceIsStoredPerProfileAndClearedExplicitly() throws Exception
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();
        ActivityXpTool tool=new ActivityXpTool(catalog,f.session,null,f.config,f.store);AtomicReference<JPanel> panel=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            panel.set(tool.createPanel());tool.openMethod("training:trees-yew");
            field(panel.get(),"training-xp.rate").setText("50000");button(panel.get(),"Save this method's pace").doClick();
            tool.openMethod("training:trees-magic");tool.openMethod("training:trees-yew");assertEquals("50,000",field(panel.get(),"training-xp.rate").getText());
            f.profile.set("rsprofile.bob");tool.openMethod("training:trees-yew");assertEquals("",field(panel.get(),"training-xp.rate").getText());
            field(panel.get(),"training-xp.rate").setText("30000");button(panel.get(),"Save this method's pace").doClick();
            f.profile.set("rsprofile.alice");tool.openMethod("training:trees-yew");assertEquals("50,000",field(panel.get(),"training-xp.rate").getText());
            field(panel.get(),"training-xp.rate").setText("");button(panel.get(),"Save this method's pace").doClick();tool.openMethod("training:trees-yew");assertEquals("",field(panel.get(),"training-xp.rate").getText());
        });
    }
    @Test public void knownMissingRequirementsPreventStartingButUnknownUnlocksStayReviewable() throws Exception
    {
        ActivityXpTool tool=new ActivityXpTool(catalog);AtomicReference<JPanel> panel=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            panel.set(tool.createPanel());tool.updateAccountFacts(new GoalCatalog.Facts(Map.of(Skill.HUNTER,0),Map.of(Skill.HUNTER,59),Map.of(),126));
            tool.openMethod("training:maniacal-monkey-hunter");assertFalse(button(panel.get(),"Start this training method").isEnabled());
            tool.updateAccountFacts(new GoalCatalog.Facts(Map.of(Skill.HUNTER,1000000),Map.of(Skill.HUNTER,60),Map.of(),126));tool.openMethod("training:maniacal-monkey-hunter");assertTrue(button(panel.get(),"Start this training method").isEnabled());
            tool.updateAccountFacts(null);tool.openMethod("training:maniacal-monkey-hunter");assertTrue(button(panel.get(),"Start this training method").isEnabled());
        });
    }
    private static JTextField field(Container root,String name)
    {for(Component child:root.getComponents()){if(child instanceof JTextField&&name.equals(child.getName()))return(JTextField)child;if(child instanceof Container){JTextField found=field((Container)child,name);if(found!=null)return found;}}return null;}
    private static JButton button(Container root,String text)
    {for(Component child:root.getComponents()){if(child instanceof JButton&&text.equals(((JButton)child).getText()))return(JButton)child;if(child instanceof Container){JButton found=button((Container)child,text);if(found!=null)return found;}}return null;}
}
