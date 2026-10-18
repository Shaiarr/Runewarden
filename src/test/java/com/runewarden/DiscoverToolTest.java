/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscoverToolTest
{
    @Test public void categoriesAndPagingKeepLargeSearchesToEightReadableActions() throws Exception
    {
        Fixture f=new Fixture();f.tool.addProvider(q->{List<DiscoverTool.Hit> hits=new ArrayList<>();for(int i=0;i<60;i++)hits.add(new DiscoverTool.Hit("Achievements","Test achievement "+i,"Preparation detail",()->{}));return hits;});
        JPanel[] p={null};SwingUtilities.invokeAndWait(()->{p[0]=f.tool.createPanel();((JTextField)named(p[0],"runewardenSearchQuery")).setText("test");});flushSearch(f);
        SwingUtilities.invokeAndWait(()->
        {
            JComboBox<?> scope=(JComboBox<?>)named(p[0],"runewardenSearchScope");scope.setSelectedItem("Achievements");
            assertEquals(8,buttons((Container)named(p[0],"runewardenSearchResults")).size());assertTrue(text(p[0]).contains("Test achievement 0"));
            ((JButton)named(p[0],"runewardenSearchNext")).doClick(0);assertTrue(text(p[0]).contains("Test achievement 8"));assertFalse(text(p[0]).contains("Test achievement 0"));
            for(int i=0;i<8;i++)((JButton)named(p[0],"runewardenSearchNext")).doClick(0);
            assertFalse(((JButton)named(p[0],"runewardenSearchNext")).isEnabled());assertEquals(4,buttons((Container)named(p[0],"runewardenSearchResults")).size());
            for(int pass=0;pass<4;pass++){p[0].setSize(206,p[0].getPreferredSize().height);layout(p[0]);}bounds(p[0]);
        });
    }
    @Test public void oldRenderedProviderButtonsCannotOpenAnotherProfilesAccountData() throws Exception
    {
        Fixture f=new Fixture();AtomicInteger opened=new AtomicInteger();f.tool.addProvider(q->List.of(new DiscoverTool.Hit("Goals","Special test goal","",opened::incrementAndGet)));
        SwingUtilities.invokeAndWait(()->{f.panel=f.tool.createPanel();((JTextField)named(f.panel,"runewardenSearchQuery")).setText("test");});flushSearch(f);
        JButton[] saved={null};SwingUtilities.invokeAndWait(()->{((JComboBox<?>)named(f.panel,"runewardenSearchScope")).setSelectedItem("Goals");saved[0]=buttons((Container)named(f.panel,"runewardenSearchResults")).get(0);});
        f.profile.set("rsprofile-b");SwingUtilities.invokeAndWait(()->saved[0].doClick(0));assertEquals(0,opened.get());
        f.tool.clearAccount();flush();SwingUtilities.invokeAndWait(()->{((JComboBox<?>)named(f.panel,"runewardenSearchScope")).setSelectedItem("Goals");buttons((Container)named(f.panel,"runewardenSearchResults")).get(0).doClick(0);});assertEquals(1,opened.get());
    }
    @Test public void providerReplacementAndRepeatedSetupDoNotDuplicateOrKeepOldActions() throws Exception
    {
        Fixture f=new Fixture();AtomicInteger opened=new AtomicInteger();java.util.function.Function<String,List<DiscoverTool.Hit>> provider=q->List.of(new DiscoverTool.Hit("Plans","Test old plan","",opened::incrementAndGet));
        f.tool.addProvider(provider);f.tool.addProvider(provider);assertEquals(1,f.tool.providerCount());
        SwingUtilities.invokeAndWait(()->{f.panel=f.tool.createPanel();((JTextField)named(f.panel,"runewardenSearchQuery")).setText("test");});flushSearch(f);
        JButton[] old={null};SwingUtilities.invokeAndWait(()->{((JComboBox<?>)named(f.panel,"runewardenSearchScope")).setSelectedItem("Plans");old[0]=buttons((Container)named(f.panel,"runewardenSearchResults")).get(0);});
        f.tool.clearProviders();f.tool.addProvider(q->List.of(new DiscoverTool.Hit("Plans","Test replacement plan","",()->{})));flush();
        assertEquals(1,f.tool.providerCount());SwingUtilities.invokeAndWait(()->old[0].doClick(0));assertEquals(0,opened.get());
        SwingUtilities.invokeAndWait(()->{assertTrue(text(f.panel).contains("Test replacement plan"));assertFalse(text(f.panel).contains("Test old plan"));});
    }
    @Test public void aProviderCannotExpandTheResultSetWithoutBound()
    {
        Fixture f=new Fixture();for(int p=0;p<20;p++){final int source=p;f.tool.addProvider(q->{List<DiscoverTool.Hit> hits=new ArrayList<>();for(int i=0;i<1000;i++)hits.add(new DiscoverTool.Hit("Test "+source,"Test "+source+":"+i,"",()->{}));return hits;});}
        assertEquals(DiscoverTool.MAX_RESULTS,f.tool.collect("test").size());
    }
    @Test public void broadQueriesRetainLaterAccountCategoriesInsteadOfFillingOnlyWithGuidesAndItems()
    {
        Fixture f=new Fixture();for(int p=0;p<8;p++){final int source=p;f.tool.addProvider(q->{List<DiscoverTool.Hit> hits=new ArrayList<>();for(int i=0;i<96;i++)hits.add(new DiscoverTool.Hit("Type "+source,"Test "+source+":"+i,"",()->{}));return hits;});}
        List<DiscoverTool.Hit> hits=f.tool.collect("test");assertEquals(512,hits.size());
        for(int p=0;p<8;p++){final String category="Type "+p;assertTrue(hits.stream().anyMatch(h->h.category.equals(category)));}
    }
    private static class Fixture
    {
        final AtomicReference<String> profile=new AtomicReference<>("rsprofile-a");final DiscoverTool tool;JPanel panel;
        Fixture(){GuideCatalog guides=new GuideCatalog(new Gson());tool=new DiscoverTool(guides,new ItemKnowledgeCatalog(new Gson(),guides),profile::get);tool.setActions(List.of("Search","Plans"),s->{},s->{},i->{});}
    }
    private static void flushSearch(Fixture f)throws Exception{SwingUtilities.invokeAndWait(()->{JTextField field=(JTextField)named(f.panel==null?f.tool.createPanel():f.panel,"runewardenSearchQuery");field.setText("test");f.tool.clearAccount();});flush();}
    private static void flush()throws Exception{SwingUtilities.invokeAndWait(()->{});}
    private static Component named(Container parent,String name){for(Component c:parent.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container){Component result=named((Container)c,name);if(result!=null)return result;}}return null;}
    private static List<JButton> buttons(Container parent){List<JButton> out=new ArrayList<>();for(Component c:parent.getComponents()){if(c instanceof JButton)out.add((JButton)c);else if(c instanceof Container)out.addAll(buttons((Container)c));}return out;}
    private static String text(Container parent){StringBuilder out=new StringBuilder();for(Component c:parent.getComponents()){if(c instanceof AbstractButton)out.append(((AbstractButton)c).getText());if(c instanceof JLabel)out.append(((JLabel)c).getText());if(c instanceof GuideText)out.append(((GuideText)c).getText());if(c instanceof Container)out.append(text((Container)c));}return out.toString();}
    private static void layout(Container parent){parent.doLayout();for(Component c:parent.getComponents())if(c instanceof Container)layout((Container)c);}
    private static void bounds(Container parent){for(Component c:parent.getComponents()){if(!c.isVisible())continue;assertTrue(c.getX()+c.getWidth()<=parent.getWidth());assertTrue(c.getY()+c.getHeight()<=parent.getHeight());if(c instanceof Container)bounds((Container)c);}}
}
