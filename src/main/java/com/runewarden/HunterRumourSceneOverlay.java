/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Highlights the exact catalogued hunting NPC only for a session-confirmed assignment. */
@Singleton
final class HunterRumourSceneOverlay extends Overlay
{
    private final Client client;private final HunterRumourCoach coach;
    @Inject HunterRumourSceneOverlay(Client client,HunterRumourCoach coach){this.client=client;this.coach=coach;setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_SCENE);}
    @Override public Dimension render(Graphics2D graphics)
    {
        if(client.getGameState()!=GameState.LOGGED_IN)return null;
        for(NPC npc:coach.sceneTargets())
        {Polygon tile=npc.getCanvasTilePoly();if(tile!=null)OverlayUtil.renderPolygon(graphics,tile,new Color(224,190,113,160));}
        return null;
    }
}
