/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GuideMarkerOverlayTest
{
    private Client client;
    private WorldView view;
    private GuideMarkerOverlay overlay;

    @Before public void prepare()
    {
        client = mock(Client.class);
        view = mock(WorldView.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getTopLevelWorldView()).thenReturn(view);
        when(view.getId()).thenReturn(-1);
        when(view.getBaseX()).thenReturn(3200);
        when(view.getBaseY()).thenReturn(3200);
        when(view.getSizeX()).thenReturn(104);
        when(view.getSizeY()).thenReturn(104);
        when(view.getScene()).thenReturn(mock(Scene.class));
        overlay = new GuideMarkerOverlay(client);
    }

    @Test public void ordinarySceneRejectsOffSceneTilesAndWrongPlanes()
    {
        overlay.select(pack(marker(3210, 3219, 0, 1), marker(3199, 3210, 0, 2),
            marker(3304, 3210, 0, 3), marker(3210, 3210, 1, 4)));
        List<GuideMarkerOverlay.SceneMarker> points = overlay.sceneMarkers();
        assertEquals(1, points.size());
        assertEquals(10, points.get(0).local.getSceneX());
        assertEquals(19, points.get(0).local.getSceneY());
        assertEquals(-1, points.get(0).local.getWorldView());
        verify(view, never()).getInstanceTemplateChunks();
    }

    @Test public void selectionCopiesNumbersCoordinatesLabelsAndColorsTogether()
    {
        GuideCatalog.Marker first = marker(3210, 3219, 0, 1), second = marker(3220, 3229, 0, 2);
        first.color = "#80abcdef";
        first.label = "Stand here";
        GuideCatalog.MarkerPack pack = pack(first, second);
        List<Integer> numbers = new ArrayList<>(Collections.singletonList(1));
        overlay.select(pack, numbers);
        numbers.set(0, 2);
        first.regionX = 0;
        first.label = "Changed";
        first.color = "#ffffff";
        pack.tiles.clear();
        assertSame(pack, overlay.selectedPack());
        assertEquals(Collections.singletonList(1), overlay.selectedNumbers());
        List<GuideMarkerOverlay.SceneMarker> points = overlay.sceneMarkers();
        assertEquals(1, points.size());
        assertEquals(10, points.get(0).local.getSceneX());
        assertEquals("1 · Stand here", points.get(0).label);
        assertEquals(new Color(0x80abcdef, true), points.get(0).color);
    }

    @Test public void switchingStepsOrPacksAndClearingDoesNotLeakThePreviousFilter()
    {
        GuideCatalog.MarkerPack pack = pack(marker(3210, 3210, 0, 1), marker(3220, 3220, 0, 2));
        overlay.select(pack, Collections.singletonList(1));
        assertEquals("1", overlay.sceneMarkers().get(0).label);
        overlay.select(pack, Collections.singletonList(2));
        assertEquals("2", overlay.sceneMarkers().get(0).label);
        overlay.select(pack);
        assertNull(overlay.selectedNumbers());
        assertEquals(2, overlay.sceneMarkers().size());
        overlay.select(null);
        assertNull(overlay.selectedPack());
        assertNull(overlay.selectedNumbers());
        assertTrue(overlay.sceneMarkers().isEmpty());
        overlay.select(pack, Collections.emptyList());
        assertTrue(overlay.sceneMarkers().isEmpty());
    }

    @Test public void instanceSupportsRotationDuplicateChunksAndDifferentSourcePlanes()
    {
        int[][][] chunks = instance();
        chunks[0][2][3] = template(3210, 3219, 2, 1);
        chunks[0][5][6] = template(3210, 3219, 2, 0);
        chunks[1][7][8] = template(3210, 3219, 2, 0);
        when(view.getBaseX()).thenReturn(8000);
        when(view.getBaseY()).thenReturn(8000);
        overlay.select(pack(marker(3210, 3219, 2, 1)));
        List<GuideMarkerOverlay.SceneMarker> points = overlay.sceneMarkers();
        assertEquals(2, points.size());
        assertEquals(19, points.get(0).local.getSceneX());
        assertEquals(29, points.get(0).local.getSceneY());
        assertEquals(42, points.get(1).local.getSceneX());
        assertEquals(51, points.get(1).local.getSceneY());
    }

    @Test public void unrelatedInstanceRegionsNeverTriggerPerMarkerChunkScans()
    {
        int[][][] chunks = instance();
        chunks[0][2][3] = template(3210, 3219, 0, 0);
        GuideCatalog.MarkerPack pack = pack();
        for (int i = 0; i < 1000; i++) pack.tiles.add(marker(1000 + i, 2000, 0, i));
        overlay.select(pack);
        clearInvocations(view);
        assertTrue(overlay.sceneMarkers().isEmpty());
        // A single context read, rather than one 4 x 13 x 13 scan per marker.
        verify(view, times(1)).getInstanceTemplateChunks();
    }

    @Test public void repeatedFramesReuseInstanceMappingWithoutRescanningEachMarker()
    {
        int[][][] chunks = instance();
        chunks[0][2][3] = template(3210, 3219, 0, 0);
        overlay.select(pack(marker(3210, 3219, 0, 1), marker(3211, 3218, 0, 2)));
        List<GuideMarkerOverlay.SceneMarker> points = overlay.sceneMarkers();
        assertEquals(2, points.size());
        clearInvocations(view);
        for (int frame = 0; frame < 60; frame++) assertSame(points, overlay.sceneMarkers());
        verify(view, times(60)).getInstanceTemplateChunks();
        verify(view, times(60)).getBaseX();
    }

    @Test public void inPlaceInstanceChangesReplaceResolvedPositions()
    {
        int[][][] chunks = instance();
        chunks[0][2][3] = template(3210, 3219, 0, 0);
        overlay.select(pack(marker(3210, 3219, 0, 1)));
        List<GuideMarkerOverlay.SceneMarker> before = overlay.sceneMarkers();
        assertEquals(18, before.get(0).local.getSceneX());
        chunks[0][2][3] = -1;
        chunks[0][8][9] = template(3210, 3219, 0, 0);
        List<GuideMarkerOverlay.SceneMarker> after = overlay.sceneMarkers();
        assertNotSame(before, after);
        assertEquals(66, after.get(0).local.getSceneX());
        assertEquals(75, after.get(0).local.getSceneY());
    }

    @Test public void movementAndPlaneChangesInvalidateTheSceneCache()
    {
        overlay.select(pack(marker(3210, 3219, 0, 1), marker(3210, 3219, 1, 2)));
        assertEquals(10, overlay.sceneMarkers().get(0).local.getSceneX());
        when(view.getBaseX()).thenReturn(3208);
        assertEquals(2, overlay.sceneMarkers().get(0).local.getSceneX());
        when(view.getPlane()).thenReturn(1);
        assertEquals("2", overlay.sceneMarkers().get(0).label);
        when(view.getBaseY()).thenReturn(3300);
        assertTrue(overlay.sceneMarkers().isEmpty());
    }

    @Test public void hidePreservesTheManualSelectionAndUnavailableScenesShowNothing()
    {
        GuideCatalog.MarkerPack pack = pack(marker(3210, 3219, 0, 1));
        overlay.select(pack);
        assertEquals(1, overlay.sceneMarkers().size());
        overlay.setVisible(false);
        assertTrue(overlay.sceneMarkers().isEmpty());
        assertSame(pack, overlay.selectedPack());
        overlay.setVisible(true);
        assertEquals(1, overlay.sceneMarkers().size());
        when(client.getGameState()).thenReturn(GameState.LOADING);
        assertTrue(overlay.sceneMarkers().isEmpty());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getTopLevelWorldView()).thenReturn(null);
        assertTrue(overlay.sceneMarkers().isEmpty());
        when(client.getTopLevelWorldView()).thenReturn(view);
        when(view.isInstance()).thenReturn(true);
        when(view.getInstanceTemplateChunks()).thenReturn(null, new int[4][][]);
        assertTrue(overlay.sceneMarkers().isEmpty());
        assertTrue(overlay.sceneMarkers().isEmpty());
    }

    private int[][][] instance()
    {
        int[][][] chunks = new int[4][13][13];
        for (int[][] plane : chunks) for (int[] row : plane) Arrays.fill(row, -1);
        when(view.isInstance()).thenReturn(true);
        when(view.getInstanceTemplateChunks()).thenReturn(chunks);
        return chunks;
    }

    private static int template(int x, int y, int plane, int rotation)
    {
        return (plane << 24) | ((x >> 3) << 14) | ((y >> 3) << 3) | (rotation << 1);
    }

    private static GuideCatalog.Marker marker(int x, int y, int plane, int number)
    {
        WorldPoint point = new WorldPoint(x, y, plane);
        GuideCatalog.Marker marker = new GuideCatalog.Marker();
        marker.regionId = point.getRegionID();
        marker.regionX = point.getRegionX();
        marker.regionY = point.getRegionY();
        marker.z = plane;
        marker.number = number;
        return marker;
    }

    private static GuideCatalog.MarkerPack pack(GuideCatalog.Marker... markers)
    {
        GuideCatalog.MarkerPack pack = new GuideCatalog.MarkerPack();
        pack.id = "test-pack";
        pack.title = "Test pack";
        pack.tiles = new ArrayList<>(Arrays.asList(markers));
        return pack;
    }
}
