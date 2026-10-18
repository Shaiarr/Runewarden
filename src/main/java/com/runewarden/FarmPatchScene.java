/* SPDX-License-Identifier: BSD-2-Clause */
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
import net.runelite.api.Constants;
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
    static Snapshot scan(Client client, WorldView view, WorldPoint player, List<FarmPatchCatalog.Patch> patches)
    {
        if (view==null || view.isInstance() || view.getScene()==null || player==null || patches.isEmpty()) return Snapshot.EMPTY;
        Tile[][][] tiles=view.getScene().getTiles();int plane=view.getPlane();
        if(tiles==null || plane<0 || plane>=tiles.length || tiles[plane]==null) return Snapshot.EMPTY;
        Set<Integer> loadedRegions=new HashSet<>();
        for(int x=view.getBaseX()>>6;x<=(view.getBaseX()+Constants.SCENE_SIZE-1)>>6;x++)
            for(int y=view.getBaseY()>>6;y<=(view.getBaseY()+Constants.SCENE_SIZE-1)>>6;y++)loadedRegions.add((x<<8)|y);
        Map<Integer,List<FarmPatchCatalog.Patch>> byVarbit=new LinkedHashMap<>();
        for(FarmPatchCatalog.Patch patch:patches) if(!patch.type.contains("COMPOST") && patch.sentRegions!=null && !Collections.disjoint(patch.sentRegions,loadedRegions))
            byVarbit.computeIfAbsent(patch.varbit,key->new ArrayList<>()).add(patch);
        if(byVarbit.isEmpty())return Snapshot.EMPTY;
        Map<Integer,ObjectComposition> definitions=new LinkedHashMap<>();
        List<Anchor> anchors=new ArrayList<>();Set<GameObject> seen=Collections.newSetFromMap(new IdentityHashMap<>());Set<String> found=new HashSet<>();
        // Discovery covers the loaded native plane, independently of the player's distance.
        // The object's own region disambiguates varbits reused at different patch sites.
        for(int x=0;x<Math.min(Constants.SCENE_SIZE,tiles[plane].length);x++)
        {
            if(tiles[plane][x]==null)continue;
            for(int y=0;y<Math.min(Constants.SCENE_SIZE,tiles[plane][x].length);y++)
            {
                if(anchors.size()>=24)break;Tile tile=tiles[plane][x][y];if(tile==null || tile.getGameObjects()==null)continue;
                for(GameObject object:tile.getGameObjects())
                {
                    if(object==null || object.getWorldView()!=view || object.getPlane()!=plane || !seen.add(object))continue;
                    int id=object.getId();
                    if(!definitions.containsKey(id))definitions.put(id,client.getObjectDefinition(id));
                    ObjectComposition definition=definitions.get(id);if(definition==null)continue;
                    List<FarmPatchCatalog.Patch> candidates=byVarbit.get(definition.getVarbitId());if(candidates==null)continue;
                    FarmPatchCatalog.Patch patch=null;
                    for(FarmPatchCatalog.Patch candidate:candidates)if(candidate.sentAt(object.getWorldLocation()))
                    {if(patch!=null){patch=null;break;}patch=candidate;}
                    if(patch!=null && found.add(patch.id))anchors.add(new Anchor(patch.id,object));
                }
            }
            if(anchors.size()>=24)break;
        }
        return new Snapshot(view,view.getScene(),anchors,view.getBaseX(),view.getBaseY(),plane);
    }
}
