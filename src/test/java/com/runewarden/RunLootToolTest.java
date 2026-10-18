/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.LootManager;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RunLootToolTest
{
    @Test public void canonicalNpcLootCountsExactlyOnceWithoutEnablingNativePlugin() throws Exception
    {
        Fixture fixture = new Fixture();
        try
        {
            ServerNpcLoot drop = fixture.npc(new ItemStack(440, 2));
            fixture.tool.onServerNpcLoot(drop);
            fixture.tool.onServerNpcLoot(drop);
            fixture.tool.onLootReceived(new LootReceived("Goblin", 2, LootRecordType.NPC,
                Collections.singletonList(new ItemStack(440, 2)), 1, null));
            assertEquals(2, fixture.tool.reading().items.get(0).quantity);
            assertEquals(200, fixture.tool.reading().value);
            assertEquals(Long.valueOf(1), fixture.tool.reading().sources.get("NPC · Goblin"));
            verifyNoInteractions(fixture.producer);
        }
        finally { fixture.stop(); }
    }

    @Test public void distinctKillsWithIdenticalLootOnOneTickAreBothCounted() throws Exception
    {
        Fixture fixture = new Fixture();
        try
        {
            fixture.tool.onServerNpcLoot(fixture.npc(new ItemStack(440, 3)));
            fixture.tool.onServerNpcLoot(fixture.npc(new ItemStack(440, 3)));
            assertEquals(6, fixture.tool.reading().items.get(0).quantity);
            assertEquals(Long.valueOf(2), fixture.tool.reading().sources.get("NPC · Goblin"));
        }
        finally { fixture.stop(); }
    }

    @Test public void notedStacksMergeWithRealItemAndUnknownPriceStillRetainsQuantity() throws Exception
    {
        Fixture fixture = new Fixture();
        try
        {
            when(fixture.items.canonicalize(441)).thenReturn(440);
            when(fixture.items.getItemPrice(100)).thenReturn(0L);
            fixture.tool.onServerNpcLoot(fixture.npc(new ItemStack(441, 3), new ItemStack(440, 2), new ItemStack(100, 7)));
            assertEquals(2, fixture.tool.reading().items.size());
            assertEquals(5, fixture.tool.reading().items.get(0).quantity);
            assertEquals(500, fixture.tool.reading().value);
            assertEquals(7, fixture.tool.reading().items.get(1).quantity);
            assertEquals(0, fixture.tool.reading().items.get(1).unitPrice);
            assertEquals(1, fixture.tool.reading().unpricedItems);
        }
        finally { fixture.stop(); }
    }

    @Test public void chestAndMinigameEventsHaveOwnSourcesAndAreNotMultipliedByAmount() throws Exception
    {
        Fixture fixture = new Fixture();
        try
        {
            fixture.tool.onLootReceived(new LootReceived("Wintertodt", -1, LootRecordType.EVENT,
                Arrays.asList(new ItemStack(440, 10), new ItemStack(995, 200)), 4, null));
            assertEquals(1200, fixture.tool.reading().value);
            assertEquals(Long.valueOf(4), fixture.tool.reading().sources.get("Wintertodt"));
            assertEquals(10, fixture.tool.reading().items.stream().filter(item -> item.id == 440).findFirst().get().quantity);
        }
        finally { fixture.stop(); }
    }

    @Test public void pauseSkipsDropsAndNewRunRejectsThePreviouslyVisibleButtonSession() throws Exception
    {
        Fixture fixture = new Fixture();
        try
        {
            RunLootTool.Reading old = fixture.tool.reading();
            fixture.tool.setPaused(old.profile, old.epoch, true);
            fixture.tool.onServerNpcLoot(fixture.npc(new ItemStack(440, 2)));
            assertTrue(fixture.tool.reading().items.isEmpty());
            fixture.tool.setPaused(old.profile, old.epoch, false);
            fixture.tool.onServerNpcLoot(fixture.npc(new ItemStack(440, 2)));
            assertEquals(200, fixture.tool.reading().value);
            fixture.tool.newRun(old.profile, old.epoch);
            assertEquals(0, fixture.tool.reading().value);
            fixture.tool.setPaused(old.profile, old.epoch, true);
            assertFalse(fixture.tool.reading().paused);
        }
        finally { fixture.stop(); }
    }

    @Test public void accountSwitchImmediatelyHidesOldLootAndStaleActionsCannotMutateNewRun() throws Exception
    {
        Fixture fixture = new Fixture();
        try
        {
            fixture.tool.onServerNpcLoot(fixture.npc(new ItemStack(440, 2)));
            RunLootTool.Reading old = fixture.tool.reading();
            fixture.profile.set("different-character");
            assertFalse(fixture.tool.reading().active);
            fixture.tool.onServerNpcLoot(fixture.npc(new ItemStack(440, 5)));
            fixture.tool.onGameTick();
            assertEquals(0, fixture.tool.reading().value);
            fixture.tool.setPaused(old.profile, old.epoch, true);
            assertFalse(fixture.tool.reading().paused);
            fixture.tool.onServerNpcLoot(fixture.npc(new ItemStack(440, 1)));
            fixture.tool.newRun(old.profile, old.epoch);
            assertEquals(100, fixture.tool.reading().value);
        }
        finally { fixture.stop(); }
    }

    @Test public void inventoryOrBankChangesNeverCountAsLootAndLogoutHidesCurrentRun() throws Exception
    {
        Fixture fixture = new Fixture();
        try
        {
            // Tick includes no inventory/bank reads and cannot turn a withdrawal into loot.
            fixture.tool.onGameTick(); fixture.tool.onGameTick();
            assertEquals(0, fixture.tool.reading().value);
            verify(fixture.client, never()).getItemContainer(anyInt());
            Player target = mock(Player.class); when(target.getName()).thenReturn("Opponent");
            fixture.tool.onPlayerLootReceived(new PlayerLootReceived(target, Collections.singletonList(new ItemStack(995, 8))));
            fixture.tool.onLootReceived(new LootReceived("Opponent", 126, LootRecordType.PLAYER,
                Collections.singletonList(new ItemStack(995, 8)), 1, null));
            assertEquals(8, fixture.tool.reading().value);
            when(fixture.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
            fixture.tool.onGameTick(); assertFalse(fixture.tool.reading().active);
            assertTrue(fixture.tool.reading().items.isEmpty());
            when(fixture.client.getGameState()).thenReturn(GameState.LOGGED_IN);
            fixture.tool.onGameTick();
            assertEquals("Same-character world hops/login retain the explicitly controlled run", 8, fixture.tool.reading().value);
        }
        finally { fixture.stop(); }
    }

    @Test public void suspensionHidesLootImmediatelyAndInvalidatesQueuedButtons() throws Exception
    {
        Fixture fixture = new Fixture();
        try
        {
            fixture.tool.onServerNpcLoot(fixture.npc(new ItemStack(440, 2)));
            RunLootTool.Reading old = fixture.tool.reading();
            fixture.tool.suspend(); assertFalse(fixture.tool.reading().active);
            fixture.tool.onGameTick();
            assertEquals(200, fixture.tool.reading().value);
            fixture.tool.newRun(old.profile, old.epoch);
            assertEquals(200, fixture.tool.reading().value);
        }
        finally { fixture.stop(); }
    }

    @Test public void totalsNeverWrapNegative()
    {
        assertEquals(Long.MAX_VALUE, RunLootTool.add(Long.MAX_VALUE - 1, 10));
        assertEquals(Long.MAX_VALUE, RunLootTool.multiply(Long.MAX_VALUE, 2));
    }

    private static final class Fixture
    {
        final Client client = mock(Client.class);
        final ItemManager items = mock(ItemManager.class);
        final ConfigManager config = mock(ConfigManager.class);
        final LootManager producer = mock(LootManager.class);
        final AtomicReference<String> profile = new AtomicReference<>("first-character");
        final RunLootTool tool = new RunLootTool(client, mock(ClientThread.class), config, items, producer);
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(config.getRSProfileKey()).thenAnswer(invocation -> profile.get());
            when(items.canonicalize(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
            when(items.getItemPrice(anyInt())).thenReturn(100L);
            ItemComposition iron = mock(ItemComposition.class); when(iron.getName()).thenReturn("Iron ore");
            when(items.getItemComposition(440)).thenReturn(iron);
            tool.start(); tool.onGameTick();
        }
        ServerNpcLoot npc(ItemStack... stacks)
        {
            NPCComposition npc = mock(NPCComposition.class); when(npc.getName()).thenReturn("Goblin");
            return new ServerNpcLoot(npc, Arrays.asList(stacks));
        }
        void stop() throws Exception { tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }
}
