/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ContextActionsTest
{
    @Test public void shiftAddsOnlyLocalActionsAndOldProfileCallbacksAreRejected()
    {
        Client client=mock(Client.class);ConfigManager config=mock(ConfigManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(config.getRSProfileKey()).thenReturn("alice");
        ContextActions actions=new ContextActions(client,config,new RunewardenConfig(){});AtomicInteger selected=new AtomicInteger();
        actions.setActions(selected::set,n->{},p->{},s->{},s->{});actions.start();
        MenuEntry nativeEntry=mock(MenuEntry.class);when(nativeEntry.getType()).thenReturn(MenuAction.CC_OP);
        when(nativeEntry.isItemOp()).thenReturn(true);when(nativeEntry.getItemId()).thenReturn(1511);when(nativeEntry.getTarget()).thenReturn("<col=ff9040>Logs</col>");
        MenuOpened event=new MenuOpened();event.setMenuEntries(new MenuEntry[]{nativeEntry});
        MenuEntry added=mock(MenuEntry.class,RETURNS_SELF);when(client.createMenuEntry(-1)).thenReturn(added);
        actions.onMenuOpened(event);verify(client,never()).createMenuEntry(anyInt());
        when(client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(true);actions.onMenuOpened(event);
        verify(added).setType(MenuAction.RUNELITE);verify(added).setOption("Runewarden: Item reference");
        org.mockito.ArgumentCaptor<Consumer> callback=org.mockito.ArgumentCaptor.forClass(Consumer.class);
        verify(added).onClick(callback.capture());callback.getValue().accept(added);assertEquals(1511,selected.get());
        selected.set(0);when(config.getRSProfileKey()).thenReturn("bob");callback.getValue().accept(added);assertEquals(0,selected.get());
        when(config.getRSProfileKey()).thenReturn("alice");actions.clear();callback.getValue().accept(added);assertEquals(0,selected.get());
    }
    @Test public void routingRecognisesActionsWithoutChangingNativeMenuOptions()
    {
        assertEquals("Farm runs",ContextActions.destination("Inspect","Herb patch"));
        assertEquals("Skilling coach",ContextActions.destination("Chop down","Yew"));
        assertEquals("Agility coach",ContextActions.destination("Cross","Rope"));
        assertEquals("Skilling coach",ContextActions.destination("Harpoon","Fishing spot"));
        assertNull(ContextActions.destination("Attack","Guard"));
        assertFalse(ContextActions.objectAction(MenuAction.NPC_FIRST_OPTION));
        assertEquals("Guard",ContextActions.plain("<col=ffff00>Guard</col> (level-21)"));
        assertEquals("",ContextActions.plain("null"));
    }
}
