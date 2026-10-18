/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RoutineNativeTest
{
    @Test public void nativeClaimSnapshotIsHiddenImmediatelyWhenCharacterChanges()
    {
        Fixture f=new Fixture();f.tick(8);assertEquals(9,f.tool.claimables().size());
        assertEquals(120,f.tool.claimables().stream().filter(c->c.id.equals("battlestaves")).findFirst().get().remaining);
        f.profile.set("rsprofile.b");assertTrue(f.tool.claimables().isEmpty());assertEquals(0,f.tool.claimObservedAt());
        f.tick(8);assertEquals(9,f.tool.claimables().size());
        when(f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);f.tick(1);assertTrue(f.tool.claimables().isEmpty());
    }
    @Test public void nativeCardsRenderAtSidebarWidthAndUserMayHideAnIndividualReminder() throws Exception
    {
        Fixture f=new Fixture();f.tick(8);AtomicReference<JCheckBox> watched=new AtomicReference<>();
        clearInvocations(f.client);
        SwingUtilities.invokeAndWait(()->{
            net.runelite.client.ui.laf.RuneLiteLAF.setup();JPanel panel=f.tool.createPanel();((JComboBox<?>)descendants(panel).stream().filter(c->c instanceof JComboBox).findFirst().get()).setSelectedIndex(1);panel.setSize(206,2200);layout(panel);
            watched.set((JCheckBox)descendants(panel).stream().filter(c->c instanceof JCheckBox&&((JCheckBox)c).getText().equals("Show reminder")).findFirst().get());
            watched.get().doClick();panel.setSize(206,panel.getPreferredSize().height);layout(panel);
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(206,Math.max(1,panel.getHeight()),java.awt.image.BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D graphics=image.createGraphics();panel.printAll(graphics);graphics.dispose();java.io.File file=new java.io.File("build/reports/native-claims-206px.png");file.getParentFile().mkdirs();try{javax.imageio.ImageIO.write(image,"png",file);}catch(java.io.IOException error){throw new AssertionError(error);}
            assertEquals(8,f.tool.watchedClaimables().size());
            for(Component c:descendants(panel))if(c instanceof JButton&&c.isVisible()&&c.getWidth()>0){JButton b=(JButton)c;assertTrue(b.getText(),b.getFontMetrics(b.getFont()).stringWidth(b.getText())+b.getInsets().left+b.getInsets().right<=b.getWidth());}
        });
        verifyNoInteractions(f.client);
        assertTrue(f.saved.values().stream().anyMatch(value->value.contains("battlestaves")));
        f.profile.set("rsprofile.b");SwingUtilities.invokeAndWait(()->watched.get().doClick());
        assertFalse(f.saved.keySet().stream().anyMatch(key->key.contains("rsprofile.b")));
    }
    @Test public void nativeSnapshotListsAndSuppliesAreReadOnly()
    {
        Fixture f=new Fixture();f.tick(8);try{f.tool.claimables().clear();fail();}catch(UnsupportedOperationException expected){}
        try{f.tool.watchedClaimables().clear();fail();}catch(UnsupportedOperationException expected){}
    }
    private static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container)layout((Container)c);}
    private static List<Component> descendants(Container root){List<Component> result=new ArrayList<>();for(Component c:root.getComponents()){result.add(c);if(c instanceof Container)result.addAll(descendants((Container)c));}return result;}
    private static final class Fixture
    {
        final Client client=mock(Client.class);final ConfigManager config=mock(ConfigManager.class);final AtomicReference<String> profile=new AtomicReference<>("rsprofile.a");
        final Map<String,String> saved=new HashMap<>();final RoutinePlannerTool tool;
        Fixture(){Gson gson=new Gson();DiaryCatalog diaries=new DiaryCatalog(gson);when(config.getRSProfileKey()).thenAnswer(call->profile.get());when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));when(client.getVarcIntValue(VarClientID.PLAYERMEMBER)).thenReturn(1);when(client.getVarbitValue(VarbitID.HANDSAND_QUEST)).thenReturn(160);
            when(client.getVarbitValue(diaries.find("varrock_elite").claimedVariable)).thenReturn(1);
            when(config.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(call->saved.get(call.getArgument(0)+":"+call.getArgument(1)+":"+call.getArgument(2)));
            doAnswer(call->{saved.put(call.getArgument(0)+":"+call.getArgument(1)+":"+call.getArgument(2),call.getArgument(3));return null;}).when(config).setConfiguration(anyString(),anyString(),anyString(),anyString());
            tool=new RoutinePlannerTool(config,new RunewardenDataStore(config,gson),client,diaries);tool.start();}
        void tick(int n){for(int i=0;i<n;i++)tool.onGameTick();}
    }
}
