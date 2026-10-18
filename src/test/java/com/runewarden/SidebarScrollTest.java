/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class SidebarScrollTest
{
    @Test public void newLabelsInAVisiblePageDoNotPullTheReadersPositionDown() throws Exception
    {
        Fixture fixture=new Fixture();
        SwingUtilities.invokeAndWait(()->{fixture.host.selectModule("Tall page");fixture.layout();});
        drain(fixture);
        SwingUtilities.invokeAndWait(()->{
            fixture.scroll().getVerticalScrollBar().setValue(400);
            fixture.tall.add(SidebarUi.label("New native account reading at the bottom of the refreshed page"));
            fixture.layout();
        });
        drain(fixture);
        SwingUtilities.invokeAndWait(()->assertEquals("Queued label caret work must not move the viewport",400,fixture.scroll().getVerticalScrollBar().getValue()));
    }

    @Test public void editableFieldsKeepSelectionAndTypingBehaviour() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            JTextField field=new JTextField("abc");field.setCaretPosition(1);field.moveCaretPosition(3);
            SidebarUi.install(field);
            assertEquals("bc",field.getSelectedText());
            field.replaceSelection("x");
            assertEquals("ax",field.getText());assertEquals(2,field.getCaretPosition());
        });
    }

    @Test public void delayedTextUpdatesMustNotDragANewPageToTheBottom() throws Exception
    {
        Fixture fixture=new Fixture();
        SwingUtilities.invokeAndWait(()->{
            fixture.host.selectModule("Tall page");fixture.layout();
        });
        drain(fixture);
        SwingUtilities.invokeAndWait(()->{
            assertEquals(0,fixture.scroll().getVerticalScrollBar().getValue());
            fixture.bottom.setText("Updated account value that is longer than the original value");
            fixture.layout();
        });
        drain(fixture);
        SwingUtilities.invokeAndWait(()->assertEquals("Background field updates must not scroll the page",0,fixture.scroll().getVerticalScrollBar().getValue()));
    }

    @Test public void returningToAModuleRestoresTheReadersPlaceAfterLayout() throws Exception
    {
        Fixture fixture=new Fixture();
        SwingUtilities.invokeAndWait(()->{fixture.host.selectModule("Tall page");fixture.layout();});
        drain(fixture);
        SwingUtilities.invokeAndWait(()->{
            fixture.scroll().getVerticalScrollBar().setValue(250);
            fixture.host.selectModule("Short page");fixture.layout();
        });
        drain(fixture);
        SwingUtilities.invokeAndWait(()->{fixture.host.selectModule("Tall page");fixture.layout();});
        drain(fixture);
        SwingUtilities.invokeAndWait(()->assertEquals(250,fixture.scroll().getVerticalScrollBar().getValue()));
    }

    private static void drain(Fixture fixture)throws Exception
    {for(int i=0;i<4;i++)SwingUtilities.invokeAndWait(fixture::layout);}
    private static final class Fixture
    {
        RunewardenPanel host;JTextField bottom;JPanel tall;
        Fixture()throws Exception
        {
            SwingUtilities.invokeAndWait(()->{
                host=new RunewardenPanel();
                tall=SidebarUi.column();
                for(int i=0;i<50;i++)tall.add(SidebarUi.label("Long equipment row "+i+" with several words to wrap within the native sidebar width."));
                bottom=new JTextField("Initial value");tall.add(bottom);
                host.addModule("Tall page",tall);host.addModule("Short page",SidebarUi.column());
            });
        }
        JScrollPane scroll(){return find(host.getWrappedPanel());}
        void layout(){host.getWrappedPanel().setSize(242,500);for(int i=0;i<8;i++)deepLayout(host.getWrappedPanel());}
    }
    private static JScrollPane find(Container parent)
    {
        for(Component child:parent.getComponents())
        {if(child instanceof JScrollPane)return (JScrollPane)child;if(child instanceof Container){JScrollPane result=find((Container)child);if(result!=null)return result;}}
        return null;
    }
    private static void deepLayout(Container parent)
    {parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container)deepLayout((Container)child);}
}
