/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JLabel;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.http.api.worlds.WorldType;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import org.junit.Test;
import static com.runewarden.WorldHopperToolTest.world;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real immutable backend snapshots, real Swing filters and explicit user actions. */
public class WorldHopperPanelTest
{
    @Test public void searchMembershipAndDangerFiltersOperateOnTheActualWorldList() throws Exception
    {
        Fixture f=new Fixture();SwingUtilities.invokeAndWait(()->{
            assertEquals(4,f.table().getColumnCount());assertEquals(4,f.table().getRowCount());
            f.select(301);assertFalse("The current world stays visible but cannot be hopped to",f.button("hop").isEnabled());
            f.search().setText("tempo");assertEquals(1,f.table().getRowCount());assertEquals(303,f.id(0));
            f.combo("membership").setSelectedItem("Free worlds");assertEquals(0,f.table().getRowCount());
            f.search().setText("");assertEquals(3,f.table().getRowCount());
            f.combo("membership").setSelectedItem("All worlds");f.check("safe").doClick(0);f.filter("type","PVP").doClick(0);
            assertEquals(1,f.table().getRowCount());assertEquals(305,f.id(0));
        });
        verify(f.backend.client,never()).createWorld();
    }
    @Test public void sortingAndFavouritesPreserveWorldIdentityAfterViewRowsMove() throws Exception
    {
        Fixture f=new Fixture();SwingUtilities.invokeAndWait(()->{
            f.combo("sort").setSelectedItem("Players");assertEquals(302,f.id(0));
            f.check("descending").doClick(0);assertEquals(303,f.id(0));
            f.select(304);f.button("favourite").doClick(0);
        });f.update();
        SwingUtilities.invokeAndWait(()->{
            assertEquals("A favourite stays pinned ahead of the reverse population sort",304,f.id(0));
            f.check("favourites").doClick(0);assertEquals(1,f.table().getRowCount());assertEquals(304,f.id(0));
            assertEquals(304,((Integer)f.table().getValueAt(f.table().getSelectedRow(),0)).intValue());
            f.button("hop").doClick(0);
        });f.backend.clientTasks();verify(f.backend.nativeWorld).setId(304);
    }
    @Test public void selectingARiskWorldNeedsAnExplicitHopAndInlineConfirmation() throws Exception
    {
        Fixture f=new Fixture();SwingUtilities.invokeAndWait(()->{f.check("safe").doClick(0);f.select(305);});
        f.backend.clientTasks();verify(f.backend.client,never()).createWorld();
        SwingUtilities.invokeAndWait(()->f.button("hop").doClick(0));f.update();
        SwingUtilities.invokeAndWait(()->{assertTrue(named(f.root,"worldhopper-confirmation").isVisible());f.button("cancel").doClick(0);});f.update();
        verify(f.backend.client,never()).createWorld();
        SwingUtilities.invokeAndWait(()->f.button("hop").doClick(0));f.update();
        SwingUtilities.invokeAndWait(()->f.button("confirm").doClick(0));f.backend.clientTasks();verify(f.backend.nativeWorld).setId(305);
    }
    @Test public void populatedPanelAndRiskConfirmationFitThe220PixelSidebar() throws Exception
    {
        Fixture f=new Fixture();SwingUtilities.invokeAndWait(()->{f.check("safe").doClick(0);f.select(305);f.button("hop").doClick(0);});f.update();
        SwingUtilities.invokeAndWait(()->{
            assertTrue(named(f.root,"worldhopper-confirmation").isVisible());
            for(int i=0;i<4;i++){f.root.setSize(220,f.root.getPreferredSize().height);layout(f.root);}
            assertEquals(4,f.table().getColumnCount());assertTrue(f.table().getWidth()<=220);
            for(String id:List.of("search","membership","region","type","safe","favourites","sort","descending","hop","favourite","confirm","cancel"))
            {
                Component control=named(f.root,"worldhopper-"+id);assertTrue(id+" must receive visible width",control.getWidth()>0);
                assertTrue(id+" overflows its parent",control.getX()+control.getWidth()<=control.getParent().getWidth());
            }
            Component reusable=null;
            for(int row=0;row<f.table().getRowCount();row++)
            {
                Component rendered=f.table().prepareRenderer(f.table().getCellRenderer(row,3),row,3);
                assertTrue("World information must use the fixed two-line renderer",rendered instanceof JPanel);
                if(reusable!=null)assertSame("The table must reuse one renderer rather than allocating per world",reusable,rendered);
                reusable=rendered;rendered.setSize(f.table().getCellRect(row,3,false).width,f.table().getRowHeight());layout((Container)rendered);
                JLabel primary=(JLabel)named((Container)rendered,"worldhopper-row-primary"),secondary=(JLabel)named((Container)rendered,"worldhopper-row-secondary");
                assertNotNull(primary);assertNotNull(secondary);
                for(JLabel line:List.of(primary,secondary))
                {
                    assertFalse("Narrow country/activity text must not wrap HTML into a third line",line.getText().toLowerCase(java.util.Locale.ROOT).contains("<html>"));
                    assertTrue("Each line must fit its native font height",line.getHeight()>=line.getFontMetrics(line.getFont()).getHeight());
                    java.awt.Rectangle bounds=SwingUtilities.convertRectangle(line.getParent(),line.getBounds(),rendered);
                    assertTrue("Both text lines must remain inside the 36px world row",bounds.y>=0&&bounds.y+bounds.height<=f.table().getRowHeight());
                }
                String country=f.backend.row(f.id(row)).location;
                assertTrue("The full country remains in the tooltip",((JComponent)rendered).getToolTipText().contains(country));
                f.select(f.id(row));assertTrue("Selected details retain the complete country",((GuideText)named(f.root,"worldhopper-selected")).getText().contains(country));
            }
            BufferedImage image=new BufferedImage(220,f.root.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
            try{f.root.printAll(g);}finally{g.dispose();}
            File output=new File("build/reports/world-hopper.png");output.getParentFile().mkdirs();
            try{assertTrue(ImageIO.write(image,"png",output));}catch(java.io.IOException error){throw new AssertionError(error);}
        });
    }
    @Test public void changingCharacterRestoresRegionAndTypeSelectionsWithAnIdenticalWorldList() throws Exception
    {
        Fixture f=new Fixture();String uk=f.backend.row(302).location,usa=f.backend.row(304).location;
        SwingUtilities.invokeAndWait(()->{f.filter("region",uk).doClick(0);f.filter("type","NORMAL").doClick(0);});f.update();
        f.backend.saved.put(RunewardenConfig.GROUP+"|rsprofile-b|worldHopperDisplay_region",usa);
        f.backend.saved.put(RunewardenConfig.GROUP+"|rsprofile-b|worldHopperDisplay_type","PVP");
        f.backend.saved.put(RunewardenConfig.GROUP+"|rsprofile-b|worldHopperDisplay_safe","false");
        f.backend.profile.set("rsprofile-b");f.backend.tick();f.update();
        SwingUtilities.invokeAndWait(()->{assertTrue(f.filter("region",usa).isSelected());assertFalse(f.filter("region",uk).isSelected());assertTrue(f.filter("type","PVP").isSelected());});
        f.backend.profile.set("rsprofile-a");f.backend.tick();f.update();
        SwingUtilities.invokeAndWait(()->{assertTrue(f.filter("region",uk).isSelected());assertFalse(f.filter("region",usa).isSelected());assertTrue(f.filter("type","NORMAL").isSelected());});
    }
    @Test public void oldCharacterControlsCannotEnqueueActionsBeforeTheSidebarRefreshes() throws Exception
    {
        Fixture f=new Fixture();SwingUtilities.invokeAndWait(()->{
            f.select(302);f.backend.profile.set("rsprofile-b");f.backend.tool.onProfileChanged();int queued=f.backend.queued.size();
            f.button("hop").doClick(0);f.button("cycle-next").doClick(0);f.button("favourite").doClick(0);
            assertEquals("Controls rendered for the old character must not queue a new character's hop",queued,f.backend.queued.size());
        });f.backend.clientTasks();verify(f.backend.client,never()).createWorld();
    }

    private static final class Fixture
    {
        final WorldHopperToolTest.Fixture backend=new WorldHopperToolTest.Fixture(List.of(world(301,500),world(302,100,"Fishing"),world(303,600,"Tempoross",WorldType.MEMBERS),
            World.builder().id(304).players(300).activity("Wintertodt").address("world304.runescape.com").region(WorldRegion.UNITED_STATES_OF_AMERICA).types(java.util.EnumSet.noneOf(WorldType.class)).build(),world(305,900,"PvP",WorldType.PVP)));
        final WorldHopperPanel ui;final JPanel root;
        Fixture() throws Exception
        {
            when(backend.client.getVarpValue(1780)).thenReturn(30);backend.tick();backend.show();ui=new WorldHopperPanel(backend.tool);AtomicReference<JPanel> panel=new AtomicReference<>();
            SwingUtilities.invokeAndWait(()->panel.set(ui.createPanel()));root=panel.get();update();
        }
        void update() throws Exception{backend.clientTasks();ui.refresh();SwingUtilities.invokeAndWait(()->{});}
        JTable table(){return (JTable)named(root,"worldhopper-table");}
        JTextField search(){return (JTextField)named(root,"worldhopper-search");}
        JComboBox<?> combo(String name){return (JComboBox<?>)named(root,"worldhopper-"+name);}
        JCheckBox check(String name){return (JCheckBox)named(root,"worldhopper-"+name);}
        JButton button(String name){return (JButton)named(root,"worldhopper-"+name);}
        JCheckBox filter(String name,String value){JButton button=button(name);button.doClick(0);JPopupMenu options=(JPopupMenu)button.getClientProperty("worldhopper.options");return (JCheckBox)named(options,"worldhopper-"+name+"-"+value);}
        int id(int row){return ((Integer)table().getValueAt(row,0)).intValue();}
        void select(int world){for(int i=0;i<table().getRowCount();i++)if(id(i)==world){table().setRowSelectionInterval(i,i);return;}throw new AssertionError("World "+world+" not visible");}
    }
    private static Component named(Container parent,String name)
    {for(Component child:parent.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container){Component found=named((Container)child,name);if(found!=null)return found;}}return null;}
    private static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container)layout((Container)child);}
}
