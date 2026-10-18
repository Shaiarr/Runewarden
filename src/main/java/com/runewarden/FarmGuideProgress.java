/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Collections;

/** Farm guide objectives come from observed patch state or the player's checked run stops. */
final class FarmGuideProgress
{
    private FarmGuideProgress() { }
    static GuideLiveProgress.Reading read(String profile,String guide,FarmPatchPlanner.Route run,
        FarmPatchPlanner.PatchReading patch,FarmPatchCatalog catalog)
    {
        if(profile==null || guide==null)return GuideLiveProgress.Reading.UNKNOWN;
        if(run!=null && run.startedAt>0 && !run.stops.isEmpty() && run.completed.containsAll(run.stops))
            return new GuideLiveProgress.Reading(profile,guide,null,"farm-finished:"+run.startedAt,"Run complete",
                run.completed.size()+" / "+run.stops.size()+" stops checked",true,Collections.emptySet());
        if(patch==null || patch.state==null)return GuideLiveProgress.Reading.UNKNOWN;
        FarmPatchCatalog.State state=patch.state;String phase=phase(guide,patch,run);
        FarmPatchCatalog.Crop crop=catalog.crop(state.crop);String name=crop==null?patch.patch.type.replace('_',' '):crop.name;
        String evidence=run==null?null:run.evidence.get(patch.patch.id);
        String body;
        if("DISEASED".equals(state.state))body="Cure the diseased "+name.toLowerCase(java.util.Locale.ROOT)+" before continuing.";
        else if("DEAD".equals(state.state))body="Clear the dead crop, then prepare and replant this patch.";
        else if("WEEDS".equals(state.crop)||"EMPTY".equals(state.state))body=state.stage==0?"This patch is empty. Prepare its soil and plant the next crop.":"Rake this patch, prepare its soil and plant the next crop.";
        else if("GROWING".equals(state.state) && patch.patch.healthCheck && state.stages>0 && state.stage>=state.stages-1)
            body=name+" is fully grown. Check its health before clearing or harvesting it.";
        else if("HARVESTABLE".equals(state.state))
            body="HESPORI".equals(patch.patch.type)?"Hespori is grown. Prepare for the fight.":patch.patch.healthCheck?"Health is already checked. Harvest if wanted, then clear and replace the tree.":"Harvest this grown crop before replanting.";
        else if("Replant observed · confirm compost / payment".equals(evidence))
            body=name+" is already planted. Confirm the chosen compost or gardener payment before checking this stop.";
        else if("GROWING".equals(state.state))body=name+" is planted and growing. "+FarmPatchOverlay.timerLabel(patch,System.currentTimeMillis()/1000)+" · "+FarmCompostEvidence.label(patch.observedCompost)+".";
        else return GuideLiveProgress.Reading.UNKNOWN;
        String key="farm:"+(run==null?0:run.startedAt)+":"+patch.patch.id+":"+state.crop+":"+state.state+":"+state.stage+":"+patch.observedCompost+":"+evidence;
        return new GuideLiveProgress.Reading(profile,guide,phase,key,patch.patch.label(),body,false,Collections.emptySet());
    }
    private static String phase(String guide,FarmPatchPlanner.PatchReading reading,FarmPatchPlanner.Route route)
    {
        FarmPatchCatalog.State state=reading.state;String crop=state.state;
        if("DISEASED".equals(crop))return null;
        boolean empty="WEEDS".equals(state.crop)||"EMPTY".equals(crop),dead="DEAD".equals(crop),harvest="HARVESTABLE".equals(crop);
        boolean health="GROWING".equals(crop)&&reading.patch.healthCheck&&state.stages>0&&state.stage>=state.stages-1;
        boolean protection=route!=null&&"Replant observed · confirm compost / payment".equals(route.evidence.get(reading.patch.id));
        switch(guide)
        {
            case "training:herb-runs":return harvest?"method:1":empty||dead||protection?"method:2":"method:3";
            case "training:allotment-runs":return harvest?"method:3":empty||dead?"method:1":"method:2";
            case "training:giant-seaweed":return harvest?"method:1":empty||dead||protection?"method:2":"method:3";
            case "training:tree-runs":return health?"method:1":harvest||dead?"method:2":"method:3";
            case "training:fruit-tree-runs":return health?"method:1":harvest||empty||dead?"method:2":"method:3";
            case "training:hardwood-runs":return health?"method:1":harvest||empty||dead?"method:2":"method:3";
            case "training:calquat-runs":case "training:crystal-tree-runs":case "training:celastrus-runs":case "training:redwood-farming":
                return empty||dead?"method:1":harvest?"method:3":"method:2";
            case "training:hespori":return harvest||health?"method:2":"method:1";
            default:return null;
        }
    }
}
