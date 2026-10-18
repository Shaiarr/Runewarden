/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ContextHudTest
{
    @Test public void compactHarvestPrioritizesDueAndReservesOneSlotForEarliestUpcoming()
    {
        HarvestClockTool.Harvest a = harvest("Bird houses", SummaryState.COMPLETED, 0);
        HarvestClockTool.Harvest b = harvest("Herb", SummaryState.IN_PROGRESS, 900);
        HarvestClockTool.Harvest c = harvest("Tree", SummaryState.COMPLETED, 0);
        HarvestClockTool.Harvest later = harvest("Fruit tree", SummaryState.IN_PROGRESS, 6000);
        HarvestClockTool.Harvest next = harvest("Hops", SummaryState.IN_PROGRESS, 2000);
        HarvestClockTool.Harvest unknown = harvest("Bush", SummaryState.UNKNOWN, 1);
        assertEquals(Arrays.asList(a, b, next), HarvestClockOverlay.compactRows(Arrays.asList(a, b, c, later, next, unknown), 1000));
        assertEquals(Arrays.asList(a, b, c), HarvestClockOverlay.compactRows(Arrays.asList(a, b, c, unknown), 1000));
        assertEquals(Collections.singletonList(next), HarvestClockOverlay.compactRows(Arrays.asList(later, next), 1000));
        assertTrue(HarvestClockOverlay.compactRows(Collections.singletonList(unknown), 1000).isEmpty());
    }

    @Test public void outfitCardUsesCompactGridAndHidesWithoutRelevantMissingPieces() throws Exception
    {
        Client client = mock(Client.class);
        RunewardenConfig config = mock(RunewardenConfig.class);
        ItemManager items = mock(ItemManager.class);
        Player player = mock(Player.class);
        ItemContainer equipment = mock(ItemContainer.class);
        when(equipment.getItems()).thenReturn(new Item[0]);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
        when(client.getLocalPlayer()).thenReturn(player);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(config.skillingOutfitReminder()).thenReturn(true);
        when(config.hudCompact()).thenReturn(true);
        when(config.hudOpacity()).thenReturn(85);
        SkillingCoach coach = new SkillingCoach(client, items, mock(ConfigManager.class), config);
        coach.startUp();
        SkillingCoachOverlay overlay = new SkillingCoachOverlay(client, config, coach, items);
        BufferedImage image = new BufferedImage(528, 340, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.scale(2, 2);
            assertNull(overlay.render(g));
            SkillingOutfits.Outfit outfit = SkillingOutfits.OUTFITS.get(Skill.AGILITY);
            coach.onStatChanged(new StatChanged(Skill.AGILITY, 100, 1, 1));
            coach.onGameTick(new GameTick());
            assertEquals(Skill.AGILITY, coach.reminder().skill);
            Dimension compact = overlay.render(g);
            assertEquals(new Dimension(264, 146), compact);
            File output = new File("build/reports/skilling-coach-hud.png");
            output.getParentFile().mkdirs();
            ImageIO.write(image, "png", output);
            when(config.hudCompact()).thenReturn(false);
            assertEquals(new Dimension(264, 236), overlay.render(g));
            for (SkillingOutfits.Piece piece : outfit.pieces) verify(items, times(2)).getImage(piece.image);
            for (SkillingOutfits.Piece piece : outfit.pieces)
                when(client.getVarbitValue(piece.benefitVarbit)).thenReturn(1);
            coach.onGameTick(new GameTick());
            assertNull(coach.reminder());
            assertNull(overlay.render(g));
            when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
            assertNull(overlay.render(g));
        }
        finally { g.dispose(); }
    }

    private static HarvestClockTool.Harvest harvest(String name, SummaryState state, long completion)
    {
        return new HarvestClockTool.Harvest(name, state, completion, true);
    }
}
