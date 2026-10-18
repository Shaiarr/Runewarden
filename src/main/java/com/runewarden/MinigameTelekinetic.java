/* SPDX-License-Identifier: BSD-2-Clause
 * Collision-aware pull model follows RuneLite's MTA TelekineticRoom semantics.
 * Upstream attribution: docs/licenses/runelite-telekinetic.txt.
 */
package com.runewarden;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;

/** Shortest number of telekinetic pulls through the actual currently loaded maze. */
final class MinigameTelekinetic
{
    enum Pull
    {
        NORTH(0,1), SOUTH(0,-1), EAST(1,0), WEST(-1,0);
        final int dx,dy;Pull(int dx,int dy){this.dx=dx;this.dy=dy;}
        String display(){String lower=name().toLowerCase(java.util.Locale.ROOT);return Character.toUpperCase(lower.charAt(0))+lower.substring(1);}
    }
    interface Movement { boolean canStep(int x,int y,int dx,int dy); }
    static final class Bounds
    {
        final int minX,minY,maxX,maxY,plane;
        Bounds(int minX,int minY,int maxX,int maxY,int plane){this.minX=minX;this.minY=minY;this.maxX=maxX;this.maxY=maxY;this.plane=plane;}
        boolean contains(WorldPoint p){return p!=null&&p.getPlane()==plane&&p.getX()>=minX&&p.getX()<=maxX&&p.getY()>=minY&&p.getY()<=maxY;}
        boolean valid(){return maxX>minX&&maxY>minY&&maxX-minX<=32&&maxY-minY<=32&&plane>=0&&plane<4;}
    }
    private static final class Edge
    {
        final WorldPoint before;final Pull pull;Edge(WorldPoint before,Pull pull){this.before=before;this.pull=pull;}
    }
    static List<Pull> solve(WorldPoint start,WorldPoint finish,Bounds bounds,Movement movement)
    {
        if(bounds==null||!bounds.valid()||!bounds.contains(start)||!bounds.contains(finish)||movement==null||start.equals(finish))return List.of();
        ArrayDeque<WorldPoint> queue=new ArrayDeque<>();Map<WorldPoint,Edge> seen=new HashMap<>();queue.add(start);seen.put(start,null);
        while(!queue.isEmpty()&&seen.size()<=1089)
        {
            WorldPoint at=queue.remove();
            for(Pull pull:Pull.values())
            {
                WorldPoint end=at;
                for(int distance=0;distance<33;distance++)
                {
                    WorldPoint next=new WorldPoint(end.getX()+pull.dx,end.getY()+pull.dy,end.getPlane());
                    if(!bounds.contains(next)||!movement.canStep(end.getX(),end.getY(),pull.dx,pull.dy))break;
                    end=next;
                }
                if(seen.containsKey(end))continue;seen.put(end,new Edge(at,pull));
                if(end.equals(finish))
                {
                    List<Pull> result=new ArrayList<>();WorldPoint cursor=end;
                    while(!cursor.equals(start)){Edge e=seen.get(cursor);if(e==null)return List.of();result.add(e.pull);cursor=e.before;}
                    Collections.reverse(result);return Collections.unmodifiableList(result);
                }
                queue.add(end);
            }
        }
        return List.of();
    }
    static WorldPoint standingTile(Bounds bounds,Pull pull,WorldPoint player)
    {
        if(bounds==null||!bounds.valid()||player==null)return null;WorldPoint best=null;int distance=Integer.MAX_VALUE;
        for(int x=bounds.minX+1;x<bounds.maxX;x++)for(int y=bounds.minY+1;y<bounds.maxY;y++)
        {
            int px=pull==Pull.WEST?bounds.minX:pull==Pull.EAST?bounds.maxX:x;
            int py=pull==Pull.SOUTH?bounds.minY:pull==Pull.NORTH?bounds.maxY:y;
            WorldPoint candidate=new WorldPoint(px,py,bounds.plane);int d=Math.abs(px-player.getX())+Math.abs(py-player.getY());
            if(d<distance){best=candidate;distance=d;}
        }
        return best;
    }
    private Scene cachedScene;private WorldPoint cachedStart,cachedFinish;private long cachedWalls;private List<Pull> cachedPath=List.of();
    void clear(){cachedScene=null;cachedStart=null;cachedFinish=null;cachedPath=List.of();}
    MinigameActivityCatalog.Snapshot enrich(MinigameActivityCatalog.Snapshot original,Client client)
    {
        if(original.activity!=MinigameActivityCatalog.Activity.MAGE_TRAINING_ARENA||original.facts.stream().noneMatch(f->f.label.equals("Room")&&f.value.equals("Telekinetic Theatre")))return original;
        WorldView view=client.getTopLevelWorldView();if(view==null||view.getScene()==null||view.npcs()==null||view.getCollisionMaps()==null||view.getPlane()<0||view.getPlane()>=view.getCollisionMaps().length||view.getCollisionMaps()[view.getPlane()]==null)return original;
        NPC guardian=null;int found=0;
        for(NPC npc:view.npcs())if(npc!=null&&(npc.getId()==NpcID.MAGICTRAINING_GUARD_MAZE_INCOMPLETE||npc.getId()==NpcID.MAGICTRAINING_GUARD_MAZE_MOVING||npc.getId()==NpcID.MAGICTRAINING_GUARD_MAZE_COMPLETE))
        {guardian=npc;if(++found>1)return original;}
        if(guardian==null||guardian.getWorldLocation()==null)return original;
        if(guardian.getId()==NpcID.MAGICTRAINING_GUARD_MAZE_COMPLETE)
            return result(original,"Maze complete","Speak to the maze guardian to collect points and start the next maze.",List.of(),List.of(),Set.of(NpcID.MAGICTRAINING_GUARD_MAZE_COMPLETE));
        if(guardian.getId()==NpcID.MAGICTRAINING_GUARD_MAZE_MOVING)
            return result(original,"Guardian moving","Wait for the guardian to stop. The next pull is recalculated from its confirmed tile.",List.of(),List.of(),Set.of());
        WorldPoint start=guardian.getWorldLocation(),finish=null;Scene scene=view.getScene();Tile[][][] tiles=scene.getTiles();int plane=view.getPlane();
        if(tiles==null||plane>=tiles.length||tiles[plane]==null)return original;
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,walls=0,finishes=0;long hash=0;
        int sceneX=start.getX()-view.getBaseX(),sceneY=start.getY()-view.getBaseY();
        for(int x=Math.max(0,sceneX-20);x<=Math.min(tiles[plane].length-1,sceneX+20);x++)
        {
            if(tiles[plane][x]==null)continue;
            for(int y=Math.max(0,sceneY-20);y<=Math.min(tiles[plane][x].length-1,sceneY+20);y++)
            {
                Tile tile=tiles[plane][x][y];if(tile==null)continue;WallObject wall=tile.getWallObject();
                if(wall!=null&&wall.getId()==ObjectID.MAGICTRAINING_MINIWALL)
                {WorldPoint point=wall.getWorldLocation();if(point==null)continue;walls++;minX=Math.min(minX,point.getX());maxX=Math.max(maxX,point.getX());minY=Math.min(minY,point.getY());maxY=Math.max(maxY,point.getY());hash=hash*31+wall.getHash();hash=hash*31+wall.getOrientationA();hash=hash*31+wall.getOrientationB();}
                if(tile.getGroundObject()!=null&&tile.getGroundObject().getId()==ObjectID.MAGICTRAINING_MINI_STATUE_PITFALL)
                {finish=tile.getGroundObject().getWorldLocation();finishes++;}
            }
        }
        Bounds bounds=new Bounds(minX,minY,maxX,maxY,plane);
        if(walls<4||finishes!=1||!bounds.valid()||!bounds.contains(start)||!bounds.contains(finish)
            ||!WorldPoint.isInScene(view,minX,minY)||!WorldPoint.isInScene(view,maxX,maxY))return original;
        int[][] collisionFlags=view.getCollisionMaps()[plane].getFlags();
        if(collisionFlags==null)return original;
        for(int x=minX-view.getBaseX();x<=maxX-view.getBaseX();x++)
        {
            if(x<0||x>=collisionFlags.length||collisionFlags[x]==null)return original;
            for(int y=minY-view.getBaseY();y<=maxY-view.getBaseY();y++)
            {if(y<0||y>=collisionFlags[x].length)return original;hash=hash*31+collisionFlags[x][y];}
        }
        if(start.equals(finish))return result(original,"Maze complete","Speak to the maze guardian to collect points and start the next maze.",List.of(),List.of(),Set.of());
        if(cachedScene!=scene||!start.equals(cachedStart)||!finish.equals(cachedFinish)||hash!=cachedWalls)
        {
            cachedScene=scene;cachedStart=start;cachedFinish=finish;cachedWalls=hash;
            cachedPath=solve(start,finish,bounds,(x,y,dx,dy)->new WorldPoint(x,y,plane).toWorldArea().canTravelInDirection(view,dx,dy));
        }
        if(cachedPath.isEmpty())return result(original,"Check the loaded maze","A complete pull route is not available from the observed walls and collision map.",List.of(),List.of(),Set.of());
        Pull next=cachedPath.get(0);WorldPoint stand=standingTile(bounds,next,client.getLocalPlayer().getWorldLocation());
        List<MinigameActivityCatalog.Fact> facts=new ArrayList<>();facts.add(new MinigameActivityCatalog.Fact("Pulls remaining",Integer.toString(cachedPath.size())));
        StringBuilder sequence=new StringBuilder();for(Pull pull:cachedPath){if(sequence.length()>0)sequence.append(" → ");sequence.append(pull.display());}facts.add(new MinigameActivityCatalog.Fact("Pull sequence",sequence.toString()));
        boolean positioned=stand!=null&&sameSide(bounds,next,client.getLocalPlayer().getWorldLocation());
        return result(original,(positioned?"Cast Telekinetic Grab · ":"Stand ")+next.display(),"Stand along the "+next.display().toLowerCase(java.util.Locale.ROOT)+" edge and cast Telekinetic Grab on the guardian. "+(cachedPath.size()>1?"Next side: "+cachedPath.get(1).display()+".":"This pull reaches the finish."),facts,stand==null?List.of():List.of(stand),Set.of(NpcID.MAGICTRAINING_GUARD_MAZE_INCOMPLETE));
    }
    static boolean sameSide(Bounds b,Pull p,WorldPoint at)
    {
        if(at==null||at.getPlane()!=b.plane)return false;
        if(p==Pull.NORTH||p==Pull.SOUTH)return at.getX()>b.minX&&at.getX()<b.maxX&&(p==Pull.NORTH?at.getY()>=b.maxY:at.getY()<=b.minY);
        return at.getY()>b.minY&&at.getY()<b.maxY&&(p==Pull.EAST?at.getX()>=b.maxX:at.getX()<=b.minX);
    }
    private static MinigameActivityCatalog.Snapshot result(MinigameActivityCatalog.Snapshot original,String title,String body,List<MinigameActivityCatalog.Fact> added,List<WorldPoint> tiles,Set<Integer> npcs)
    {
        List<MinigameActivityCatalog.Fact> facts=new ArrayList<>(original.facts);facts.addAll(added);
        return new MinigameActivityCatalog.Snapshot(original.activity,original.title,original.guideId,facts,List.of(new MinigameActivityCatalog.Step(title,body)),Set.of(),tiles,true,npcs).preserveGuideProgress(original);
    }
}
