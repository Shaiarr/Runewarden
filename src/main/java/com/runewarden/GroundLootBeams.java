/* SPDX-License-Identifier: BSD-2-Clause
 * Model/animation recolouring adapted from RuneLite 1.13.2 Lootbeam.java.
 * Copyright (c) 2021, Trevor <https://github.com/Trevor159>.
 * Full BSD-2-Clause notice: resources/com/runewarden/licenses/ground-loot-beams-BSD-2-Clause.txt.
 */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.JagexColor;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.callback.ClientThread;

/** Owned native scene objects only. All model/object operations run on the client thread. */
final class GroundLootBeams
{
    static final int MAX_BEAMS=64;
    enum Style { LIGHT("Light"),MODERN("Modern");private final String label;Style(String label){this.label=label;}@Override public String toString(){return label;} }
    static final class Spot
    {
        final WorldView view;final LocalPoint local;final int plane;
        Spot(GroundLootTool.Entry item){view=item.view;local=item.local;plane=item.world.getPlane();}
        @Override public boolean equals(Object other){if(!(other instanceof Spot))return false;Spot spot=(Spot)other;return view==spot.view&&plane==spot.plane&&Objects.equals(local,spot.local);}
        @Override public int hashCode(){return 31*(31*System.identityHashCode(view)+plane)+local.hashCode();}
    }
    static final class Candidate
    {
        final Spot spot;final GroundLootTier tier;final long value;final boolean target;
        Candidate(GroundLootTool.Entry item,GroundLootTier tier){spot=new Spot(item);this.tier=tier;value=item.value;target=item.target();}
    }
    private static final class Key
    {
        final Style style;final GroundLootTier tier;
        Key(Style style,GroundLootTier tier){this.style=style;this.tier=tier;}
        @Override public boolean equals(Object other){return other instanceof Key&&((Key)other).style==style&&((Key)other).tier==tier;}
        @Override public int hashCode(){return 31*style.ordinal()+tier.ordinal();}
    }
    private static final class Beam
    {
        final RuneLiteObject object;Key key;
        Beam(RuneLiteObject object,Key key){this.object=object;this.key=key;}
    }
    private final Client client;private final ClientThread thread;
    private final Map<Spot,Beam> active=new LinkedHashMap<>();
    private final Map<Key,Model> models=new LinkedHashMap<>();
    private final Map<Style,Animation> animations=new java.util.EnumMap<>(Style.class);
    private final Map<Key,Integer> retryAfter=new LinkedHashMap<>();
    GroundLootBeams(Client client,ClientThread thread){this.client=client;this.thread=thread;}

    static List<Candidate> select(List<GroundLootTool.Entry> entries,GroundLootTool.Preferences settings)
    {
        if(!settings.enabled||!settings.beams)return Collections.emptyList();
        Map<Spot,Candidate> spots=new LinkedHashMap<>();
        for(GroundLootTool.Entry entry:entries)
        {
            GroundLootTier tier=GroundLootTier.beam(entry,settings);if(tier==null)continue;
            Candidate candidate=new Candidate(entry,tier),old=spots.get(candidate.spot);
            if(old==null||candidate.tier.ordinal()>old.tier.ordinal()||candidate.tier==old.tier&&candidate.value>old.value)spots.put(candidate.spot,candidate);
        }
        List<Candidate> result=new ArrayList<>(spots.values());
        result.sort(Comparator.comparing((Candidate candidate)->candidate.tier.ordinal()).reversed()
            .thenComparing(candidate->!candidate.target).thenComparing(Comparator.comparingLong((Candidate candidate)->candidate.value).reversed())
            .thenComparingInt(candidate->candidate.spot.local.getX()).thenComparingInt(candidate->candidate.spot.local.getY()));
        return Collections.unmodifiableList(new ArrayList<>(result.subList(0,Math.min(settings.beamLimit,result.size()))));
    }

    /** Called only by the native game-tick path; never by overlay painting or Swing. */
    synchronized void sync(List<Candidate> desired,GroundLootTool.Preferences settings,int tick)
    {
        Map<Spot,Candidate> next=new LinkedHashMap<>();for(Candidate candidate:desired)next.put(candidate.spot,candidate);
        active.entrySet().removeIf(entry->{if(next.containsKey(entry.getKey()))return false;entry.getValue().object.setActive(false);return true;});
        for(Candidate candidate:desired)
        {
            Key key=new Key(settings.beamStyle,candidate.tier);Beam previous=active.get(candidate.spot);
            if(previous!=null&&previous.key.equals(key))continue;
            Model model=model(key,tick);if(model==null)continue;
            Animation animation=animations.get(key.style);
            if(animation==null)
            {animation=client.loadAnimation(key.style==Style.LIGHT?AnimationID.ENAKH_LIGHT_STREAMING:AnimationID.FX_BEAM_IDLE);if(animation!=null)animations.put(key.style,animation);}
            if(animation==null)continue;
            RuneLiteObject object=previous==null?client.createRuneLiteObject():previous.object;
            if(object==null)continue;
            object.setAnimation(animation);object.setModel(model);object.setShouldLoop(true);
            object.setLocation(candidate.spot.local,candidate.spot.plane);
            // LocalPoint already identifies the observed native world view, including instances.
            object.setWorldView(candidate.spot.view.getId());object.setActive(true);
            active.put(candidate.spot,new Beam(object,key));
        }
    }
    private Model model(Key key,int tick)
    {
        Model cached=models.get(key);if(cached!=null)return cached;
        if(tick<retryAfter.getOrDefault(key,Integer.MIN_VALUE))return null;
        Model model;
        if(key.style==Style.LIGHT)model=client.loadModel(5809,new short[]{6371},new short[]{JagexColor.rgbToHSL(key.tier.color.getRGB(),1.0d)});
        else
        {
            ModelData data=client.loadModelData(43330);
            if(data==null)model=null;
            else
            {
                short hsl=JagexColor.rgbToHSL(key.tier.color.getRGB(),1.0d);int hue=JagexColor.unpackHue(hsl),sat=JagexColor.unpackSaturation(hsl),lum=JagexColor.unpackLuminance(hsl),satDelta=sat>2?1:0;
                model=data.cloneColors().recolor((short)26432,JagexColor.packHSL(hue,sat-satDelta,lum))
                    .recolor((short)26584,JagexColor.packHSL(hue,sat,Math.min(lum+24,JagexColor.LUMINANCE_MAX)))
                    .light(75+ModelData.DEFAULT_AMBIENT,1875+ModelData.DEFAULT_CONTRAST,ModelData.DEFAULT_X,ModelData.DEFAULT_Y,ModelData.DEFAULT_Z);
            }
        }
        if(model!=null){models.put(key,model);retryAfter.remove(key);}else retryAfter.put(key,tick+5);
        return model;
    }
    void clear()
    {
        List<RuneLiteObject> obsolete;
        synchronized(this){obsolete=new ArrayList<>();for(Beam beam:active.values())obsolete.add(beam.object);active.clear();models.clear();animations.clear();retryAfter.clear();}
        // Capture only the old owned objects: delayed cleanup cannot deactivate a new login's objects.
        Runnable remove=()->obsolete.forEach(object->object.setActive(false));
        if(!obsolete.isEmpty()){if(thread==null||client.isClientThread())remove.run();else thread.invoke(remove);}
    }
    synchronized int activeCount(){return active.size();}
    synchronized int cachedModels(){return models.size();}
}
