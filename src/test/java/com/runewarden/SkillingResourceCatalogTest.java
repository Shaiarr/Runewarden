/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SkillingResourceCatalogTest
{
    private static final SkillingResourceCatalog CATALOG = new SkillingResourceCatalog(new com.google.gson.Gson());
    @Test public void everyBankedXpIngredientHasSourcesAndNamedActions()
    {
        for (ActionRecipeCatalog.Recipe recipe : ActionRecipeCatalog.recipes())
            for (ActionRecipeCatalog.Ingredient ingredient : recipe.ingredients)
            {
                SkillingResourceCatalog.Resource resource = CATALOG.find(ingredient.itemId);
                assertNotNull(ingredient.name, resource);
                assertNotNull(ingredient.name, resource.source);
                assertFalse(ingredient.name, resource.source.isEmpty());
                assertTrue(resource.actions().stream().anyMatch(action -> action.name.equals(recipe.name)
                    && action.xp == recipe.xp && action.quantity == ingredient.quantity && action.source.equals(recipe.source)));
            }
        assertTrue(CATALOG.size() > 250);
    }

    @Test public void yewShowsChoppingBurningAndFletchingAsSeparateActions()
    {
        SkillingResourceCatalog.Resource yew = CATALOG.find(1515);
        assertTrue(yew.actions().stream().anyMatch(a -> a.skill == Skill.WOODCUTTING && a.xp == 175));
        assertTrue(yew.actions().stream().anyMatch(a -> a.skill == Skill.FIREMAKING && a.xp == 202.5));
        assertTrue(yew.actions().stream().anyMatch(a -> a.skill == Skill.FLETCHING));
    }

    @Test public void coalNamesIngredientQuantityAndEntireSmeltingRecipe()
    {
        SkillingResourceCatalog.Resource coal = CATALOG.find(453);
        SkillingResourceCatalog.Action steel = coal.actions().stream().filter(a -> a.name.equals("Smelt steel bar")).findFirst().get();
        assertEquals(2, steel.quantity);
        assertEquals(17.5, steel.xp, 0.001);
        assertTrue(steel.ingredients.contains("2 × Coal"));
        assertTrue(steel.ingredients.contains("1 × Iron ore"));
        assertTrue(steel.summary().contains("XP / action (uses 2)"));
    }

    @Test public void lockedActionsAreMarkedAndExpandedShowsMethodRequirements()
    {
        String locked = SkillingResourceCatalog.tooltip(CATALOG.find(1515), true, skill -> 1);
        assertTrue(locked.contains("<col=ff9a86>"));
        String text = SkillingResourceCatalog.tooltip(CATALOG.find(536), true, skill -> 1, 50);
        assertTrue(text.contains("Dragon bones"));
        assertTrue(text.contains("Source:"));
        assertTrue(text.contains("XP / action"));
        assertTrue(text.toLowerCase().contains("burners"));
    }

    @Test public void seedHasStageSpecificXpAndSourceRevision()
    {
        String text = SkillingResourceCatalog.tooltip(CATALOG.find(5295), false, skill -> 99);
        assertNotNull(text);
        assertTrue(text.contains("herb patch"));
        assertTrue(text.contains("Farming 32"));
        assertTrue(text.contains("26.5"));
        assertTrue(text.contains("30.5"));
        assertTrue(CATALOG.find(5295).actions().get(0).source.contains("oldid="));
        assertFalse(text.contains("0 XP"));
    }

    @Test public void tooltipRowsAreBoundedForSmallCanvases()
    {
        String text = SkillingResourceCatalog.tooltip(CATALOG.find(314), true, skill -> 99, 8);
        assertTrue(text.split("<br>").length <= 8);
        assertTrue(text.contains("More methods"));
        for (String row : text.split("<br>"))
            assertTrue(row.replaceAll("<[^>]*>", "").length() <= 75);
    }

    @Test public void unknownItemsDoNotGetBlankOrInventedTooltips()
    { assertNull(CATALOG.find(-1)); assertNull(CATALOG.find(4151)); }

    @Test public void hoverRespectsVisibleParentAndExactItemRectangle()
    {
        Rectangle item = new Rectangle(10, 10, 32, 32), parent = new Rectangle(10, 20, 100, 100);
        assertFalse(ResourceTooltipOverlay.hovered(item, parent, new Point(15, 15)));
        assertTrue(ResourceTooltipOverlay.hovered(item, parent, new Point(15, 25)));
        assertFalse(ResourceTooltipOverlay.hovered(item, parent, new Point(43, 25)));
        assertTrue(ResourceTooltipOverlay.hovered(item, null, new Point(15, 25)));
    }

    @Test public void notedResourcesCanonicalizeButMenusAndHiddenItemsSuppressTooltips()
    {
        Client client = mock(Client.class); RunewardenConfig config = mock(RunewardenConfig.class);
        ItemManager manager = mock(ItemManager.class); TooltipManager tips = mock(TooltipManager.class);
        Widget widget = mock(Widget.class), parent = mock(Widget.class); WidgetItem item = mock(WidgetItem.class);
        when(config.resourceTooltips()).thenReturn(true); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(20, 20));
        when(item.getWidget()).thenReturn(widget); when(widget.getParent()).thenReturn(parent);
        when(parent.getBounds()).thenReturn(new Rectangle(0, 0, 100, 100));
        when(item.getCanvasBounds()).thenReturn(new Rectangle(10, 10, 32, 32)); when(item.getQuantity()).thenReturn(100);
        when(manager.canonicalize(1516)).thenReturn(1515);
        ResourceTooltipOverlay overlay = new ResourceTooltipOverlay(client, config, manager, tips, CATALOG);
        overlay.renderItemOverlay(null, 1516, item);
        ArgumentCaptor<Tooltip> capture = ArgumentCaptor.forClass(Tooltip.class);
        verify(tips).add(capture.capture()); assertTrue(capture.getValue().getText().contains("Yew logs"));
        clearInvocations(tips);
        when(client.isMenuOpen()).thenReturn(true); overlay.renderItemOverlay(null, 1516, item); verifyNoInteractions(tips);
        when(client.isMenuOpen()).thenReturn(false); when(widget.isHidden()).thenReturn(true);
        overlay.renderItemOverlay(null, 1516, item); verifyNoInteractions(tips);
        when(widget.isHidden()).thenReturn(false); when(item.getDraggingCanvasBounds()).thenReturn(new Rectangle());
        overlay.renderItemOverlay(null, 1516, item); verifyNoInteractions(tips);
    }

    @Test public void injectedResourceHoverUsesSharedTopCardAndClearsAfterLeaving()
    {
        Client client = mock(Client.class); RunewardenConfig config = mock(RunewardenConfig.class);
        ItemManager manager = mock(ItemManager.class); TooltipManager tips = mock(TooltipManager.class);
        Widget widget = mock(Widget.class); WidgetItem item = mock(WidgetItem.class);
        when(config.resourceTooltips()).thenReturn(true); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getCanvasWidth()).thenReturn(765); when(client.getCanvasHeight()).thenReturn(503);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(20, 450));
        when(item.getWidget()).thenReturn(widget); when(widget.getBounds()).thenReturn(new Rectangle(0, 400, 200, 103));
        when(item.getCanvasBounds()).thenReturn(new Rectangle(10, 440, 32, 32)); when(item.getQuantity()).thenReturn(100);
        when(manager.canonicalize(1516)).thenReturn(1515);
        HoverCardOverlay cards = new HoverCardOverlay(client);
        ResourceTooltipOverlay overlay = new ResourceTooltipOverlay(client, config, manager, tips, CATALOG, cards);
        overlay.renderItemOverlay(null, 1516, item);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(765, 503, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try
        {
            cards.render(graphics);
            assertFalse(cards.lastBounds().isEmpty());
            assertTrue(cards.lastBounds().y + cards.lastBounds().height <= 438);
            verifyNoInteractions(tips);
            when(client.getMouseCanvasPosition()).thenReturn(new Point(250, 450));
            overlay.renderItemOverlay(null, 1516, item); cards.render(graphics);
            assertTrue(cards.lastBounds().isEmpty());
        }
        finally { graphics.dispose(); }
    }
}
