/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.events.GameTick;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the real hover producer and drawing path with native-interface test doubles. */
public class ShiftHoverActivationTest
{
    @Test public void defaultModeDoesNoMenuOrCollisionWorkUntilShiftIsHeld() throws Exception
    {
        Fixture f = new Fixture();
        RunewardenConfig defaults = new RunewardenConfig() { };
        assertTrue(defaults.hoverNpcSight());
        assertFalse(defaults.hoverNpcSightAlways());
        f.tool.onClientTick();
        f.tool.onClientTick();
        assertFalse(f.tool.hoverActive());
        assertFalse(f.tool.isHoverReading());
        assertNull(f.tool.reading().target);
        assertEquals(0, f.menuReads);
        assertEquals(0, f.playerArea.queries + f.hoverArea.queries);

        f.shift = true;
        f.tool.onClientTick();
        assertSame(f.hovered, f.tool.reading().target);
        assertEquals(1, f.menuReads);
        assertEquals(1, f.playerArea.queries);
        assertEquals(1, f.hoverArea.queries);
        assertSame(f.view, f.playerArea.lastView);
        assertSame(f.hoverArea, f.playerArea.lastTarget);
    }

    @Test public void releaseClearsHoverAndRepressMeasuresTheCurrentSight() throws Exception
    {
        Fixture f = new Fixture();
        f.shift = true;
        f.tool.onClientTick();
        assertTrue(f.tool.reading().playerToNpc);
        int menuReads = f.menuReads, queries = f.playerArea.queries;

        f.shift = false;
        f.tool.onClientTick();
        assertNull(f.tool.reading().target);
        assertFalse(f.tool.isHoverReading());
        assertTrue(f.tool.reading().message.contains("Hold Shift"));
        assertEquals(menuReads, f.menuReads);
        assertEquals(queries, f.playerArea.queries);
        f.tool.onClientTick();
        assertEquals(queries, f.playerArea.queries);

        f.playerArea.clear = false;
        f.shift = true;
        f.tool.onClientTick();
        assertSame(f.hovered, f.tool.reading().target);
        assertFalse(f.tool.reading().playerToNpc);
        assertTrue(f.tool.reading().npcToPlayer);
        assertEquals(menuReads + 1, f.menuReads);
        assertEquals(queries + 1, f.playerArea.queries);
    }

    @Test public void alwaysOnIsOptionalAndTheMasterSwitchStillDisablesIt() throws Exception
    {
        Fixture f = new Fixture();
        f.config.always = true;
        f.tool.onClientTick();
        assertTrue(f.tool.hoverActive());
        assertSame(f.hovered, f.tool.reading().target);
        int menuReads = f.menuReads, queries = f.playerArea.queries;

        f.config.enabled = false;
        f.shift = true;
        f.tool.onClientTick();
        assertFalse(f.tool.hoverActive());
        assertNull(f.tool.reading().target);
        assertEquals(menuReads, f.menuReads);
        assertEquals(queries, f.playerArea.queries);

        f.config.enabled = true;
        f.shift = false;
        f.tool.onClientTick();
        assertSame(f.hovered, f.tool.reading().target);
        f.config.always = false;
        f.tool.onClientTick();
        assertNull(f.tool.reading().target);
        assertFalse(f.tool.isHoverReading());
    }

    @Test public void manualTargetReturnsAfterHoverAndKeepsUpdatingWithoutShift() throws Exception
    {
        Fixture f = new Fixture();
        f.tool.select(f.manual);
        assertSame(f.manual, f.tool.reading().target);
        assertFalse(f.tool.isHoverReading());

        f.shift = true;
        f.tool.onClientTick();
        assertSame(f.hovered, f.tool.reading().target);
        f.shift = false;
        f.tool.onClientTick();
        assertSame(f.manual, f.tool.reading().target);
        assertFalse(f.tool.isHoverReading());
        assertTrue(f.tool.reading().playerToNpc);

        f.playerArea.clear = false;
        f.manualArea.clear = false;
        f.tool.onGameTick(new GameTick());
        assertSame(f.manual, f.tool.reading().target);
        assertFalse(f.tool.reading().playerToNpc);
        assertFalse(f.tool.reading().npcToPlayer);
        assertSame(f.manualArea, f.playerArea.lastTarget);

        f.config.enabled = false;
        f.tool.onGameTick(new GameTick());
        assertSame(f.manual, f.tool.reading().target);
    }

    @Test public void overlaySuppressesStaleHoverImmediatelyOnReleaseBeforeAnotherClientTick() throws Exception
    {
        Fixture f = new Fixture();
        f.shift = true;
        f.tool.onClientTick();
        BufferedImage image = new BufferedImage(765, 503, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            f.overlay.render(graphics);
            f.cards.render(graphics);
            Rectangle bounds = f.cards.lastBounds();
            assertFalse(bounds.isEmpty());
            assertTrue(bounds.x >= 0 && bounds.y >= 0);
            assertTrue(bounds.x + bounds.width <= 765);
            assertTrue(bounds.y + bounds.height <= 503);

            f.shift = false;
            assertTrue("Producer still contains the previous frame until its next tick", f.tool.isHoverReading());
            assertSame(f.hovered, f.tool.reading().target);
            BufferedImage released = new BufferedImage(765, 503, BufferedImage.TYPE_INT_ARGB);
            Graphics2D releasedGraphics = released.createGraphics();
            try
            {
                f.overlay.render(releasedGraphics);
                f.cards.render(releasedGraphics);
            }
            finally { releasedGraphics.dispose(); }
            assertTrue(f.cards.lastBounds().isEmpty());
            assertEquals("No stale line, outline or hover card is painted", 0, paintedPixels(released));

            f.shift = true;
            f.overlay.render(graphics);
            f.cards.render(graphics);
            assertFalse(f.cards.lastBounds().isEmpty());
        }
        finally { graphics.dispose(); }
    }

    @Test public void alwaysModePaintsWithoutShiftAndMasterOffSuppressesItsStaleFrame() throws Exception
    {
        Fixture f = new Fixture();
        f.config.always = true;
        f.tool.onClientTick();
        BufferedImage image = new BufferedImage(765, 503, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            f.overlay.render(graphics);
            f.cards.render(graphics);
            assertFalse(f.cards.lastBounds().isEmpty());
            assertTrue(paintedPixels(image) > 0);

            f.config.enabled = false;
            f.overlay.render(graphics);
            f.cards.render(graphics);
            assertTrue(f.cards.lastBounds().isEmpty());
        }
        finally { graphics.dispose(); }
    }

    private static int paintedPixels(BufferedImage image)
    {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                if ((image.getRGB(x, y) >>> 24) != 0) count++;
        return count;
    }

    private static final class MutableConfig implements RunewardenConfig
    {
        boolean enabled = true, always;
        @Override public boolean hoverNpcSight() { return enabled; }
        @Override public boolean hoverNpcSightAlways() { return always; }
    }

    private static final class CountingArea extends WorldArea
    {
        int queries;
        boolean clear = true;
        WorldView lastView;
        WorldArea lastTarget;
        CountingArea(int x) { super(x, 3200, 1, 1, 0); }
        @Override public boolean hasLineOfSightTo(WorldView view, WorldArea target)
        { queries++; lastView = view; lastTarget = target; return clear; }
    }

    private static final class Fixture
    {
        final MutableConfig config = new MutableConfig();
        final CountingArea playerArea = new CountingArea(3200), hoverArea = new CountingArea(3205), manualArea = new CountingArea(3208);
        final Client client;
        final WorldView view;
        final Player player;
        final NPC hovered, manual;
        final HoverCardOverlay cards;
        final RunewardenSightTool tool;
        final RunewardenSightOverlay overlay;
        boolean shift;
        int menuReads;

        @SuppressWarnings("unchecked") Fixture() throws Exception
        {
            NPC[] actors = new NPC[2];
            IndexedObjectSet<NPC> npcs = nativeProxy(IndexedObjectSet.class, (proxy, method, args) ->
            {
                if (method.getName().equals("byIndex")) return (int) args[0] == 7 ? actors[0] : (int) args[0] == 8 ? actors[1] : null;
                if (method.getName().equals("iterator")) return Arrays.asList(actors).iterator();
                return defaultValue(method.getReturnType());
            });
            view = nativeProxy(WorldView.class, (proxy, method, args) ->
            {
                if (method.getName().equals("npcs")) return npcs;
                if (method.getName().equals("getCollisionMaps")) return new CollisionData[4];
                return defaultValue(method.getReturnType());
            });
            player = nativeProxy(Player.class, (proxy, method, args) ->
            {
                if (method.getName().equals("getWorldView")) return view;
                if (method.getName().equals("getWorldArea")) return playerArea;
                if (method.getName().equals("getCanvasTextLocation")) return new Point(200, 220);
                return defaultValue(method.getReturnType());
            });
            hovered = actor(7, "Hover goblin", hoverArea);
            manual = actor(8, "Manual goblin", manualArea);
            actors[0] = hovered; actors[1] = manual;
            MenuEntry entry = nativeProxy(MenuEntry.class, (proxy, method, args) ->
            {
                if (method.getName().equals("getType")) return MenuAction.NPC_SECOND_OPTION;
                if (method.getName().equals("getNpc")) return hovered;
                return defaultValue(method.getReturnType());
            });
            client = nativeProxy(Client.class, (proxy, method, args) ->
            {
                switch (method.getName())
                {
                    case "getGameState": return GameState.LOGGED_IN;
                    case "getLocalPlayer": return player;
                    case "getTopLevelWorldView": case "getWorldView": return view;
                    case "isKeyPressed": return (int) args[0] == KeyCode.KC_SHIFT && shift;
                    case "getMouseCanvasPosition": return new Point(250, 180);
                    case "getCanvasWidth": return 765;
                    case "getCanvasHeight": return 503;
                    case "getMenuEntries": menuReads++; return new MenuEntry[]{entry};
                    default: return defaultValue(method.getReturnType());
                }
            });
            cards = new HoverCardOverlay(client);
            tool = new RunewardenSightTool(client, null, null, null, config, null, cards);
            // Activation is isolated from unrelated native overlay/event registration.
            Field started = RunewardenSightTool.class.getDeclaredField("started");
            started.setAccessible(true); started.setBoolean(tool, true);
            overlay = new RunewardenSightOverlay(client, tool, cards);
        }

        private NPC actor(int index, String name, WorldArea area)
        {
            return nativeProxy(NPC.class, (proxy, method, args) ->
            {
                switch (method.getName())
                {
                    case "getIndex": return index;
                    case "getId": return 100 + index;
                    case "getName": return name;
                    case "getWorldView": return view;
                    case "getWorldArea": return area;
                    case "getCanvasTextLocation": return new Point(index == 7 ? 300 : 350, 220);
                    default: return defaultValue(method.getReturnType());
                }
            });
        }
    }

    private static <T> T nativeProxy(Class<T> api, InvocationHandler calls)
    {
        return api.cast(Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, (proxy, method, args) ->
        {
            if (method.getDeclaringClass() == Object.class)
            {
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                if (method.getName().equals("toString")) return "Test " + api.getSimpleName();
            }
            return calls.invoke(proxy, method, args);
        }));
    }

    private static Object defaultValue(Class<?> type)
    {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return 0;
    }
}
