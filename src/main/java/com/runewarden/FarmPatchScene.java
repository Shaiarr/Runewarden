/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/** Client-thread, bounded scene discovery using the same object-varbit identity as core farming. */
final class FarmPatchScene
{
    static final class Anchor
    {
        final String patchId; final GameObject object;
        Anchor(String patch, GameObject object) { patchId = patch; this.object = object; }
    }
    static final class Snapshot
    {
        static final Snapshot EMPTY = new Snapshot(null,null,List.of(),0,0,0);
        final WorldView view; final net.runelite.api.Scene scene; final List<Anchor> anchors; final int baseX,baseY,plane;
        Snapshot(WorldView view, net.runelite.api.Scene scene, List<Anchor> anchors,int baseX,int baseY,int plane)
        { this.view=view; this.scene=scene; this.anchors=Collections.unmodifiableList(new ArrayList<>(anchors));this.baseX=baseX;this.baseY=baseY;this.plane=plane; }
        boolean matches(WorldView current)
        { return current != null && current == view && !current.isInstance() && current.getScene() == scene && current.getBaseX()==baseX && current.getBaseY()==baseY && current.getPlane()==plane; }
    }
    static Snapshot scan(Client client, WorldView view, WorldPoint player, List<FarmPatchCatalog.Patch> local)
    {
        if (view==null || view.isInstance() || view.getScene()==null || player==null || local.isEmpty()) return Snapshot.EMPTY;
        Tile[][][] tiles=view.getScene().getTiles();int plane=view.getPlane();
        if(tiles==null || plane<0 || plane>=tiles.length || tiles[plane]==null) return Snapshot.EMPTY;
        Map<Integer,FarmPatchCatalog.Patch> byVarbit=new LinkedHashMap<>();
        for(FarmPatchCatalog.Patch patch:local) if(!patch.type.contains("COMPOST") && patch.sentAt(player)) byVarbit.put(patch.varbit,patch);
        List<Anchor> anchors=new ArrayList<>();Set<GameObject> seen=Collections.newSetFromMap(new IdentityHashMap<>());Set<String> found=new HashSet<>();
        int centerX=player.getX()-view.getBaseX(),centerY=player.getY()-view.getBaseY(),visited=0;
        for(int x=Math.max(0,centerX-16);x<=centerX+16 && x<tiles[plane].length;x++)
        {
            if(tiles[plane][x]==null)continue;
            for(int y=Math.max(0,centerY-16);y<=centerY+16 && y<tiles[plane][x].length;y++)
            {
                if(++visited>1089 || anchors.size()>=24)break;Tile tile=tiles[plane][x][y];if(tile==null || tile.getGameObjects()==null)continue;
                for(GameObject object:tile.getGameObjects())
                {
                    if(object==null || object.getWorldView()!=view || !seen.add(object))continue;
                    ObjectComposition definition=client.getObjectDefinition(object.getId());if(definition==null)continue;
                    FarmPatchCatalog.Patch patch=byVarbit.get(definition.getVarbitId());
                    if(patch!=null && found.add(patch.id))anchors.add(new Anchor(patch.id,object));
                }
            }
            if(visited>1089 || anchors.size()>=24)break;
        }
        return new Snapshot(view,view.getScene(),anchors,view.getBaseX(),view.getBaseY(),plane);
    }
}
