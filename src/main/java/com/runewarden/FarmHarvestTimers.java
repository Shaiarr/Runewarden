/*
 * Copyright (c) 2018 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;

/** Split mixed native tabs by implementation while retaining native observations and growth ticks. */
@Singleton
final class FarmHarvestTimers
{
    static final class Group
    {
        final String id,name,type,legacyGroup;final int iconId;
        Group(String id,String name,String type,int icon,String legacy)
        {this.id=id;this.name=name;this.type=type;iconId=icon;legacyGroup=legacy;}
    }
    static final List<Group> GROUPS=List.of(
        new Group("tree","Trees","TREE",1515,"tree"),
        new Group("hardwood","Hardwood trees","HARDWOOD_TREE",6332,"tree"),
        new Group("spirit-tree","Spirit trees","SPIRIT_TREE",6063,"tree"),
        new Group("redwood","Redwood","REDWOOD",19669,"tree"),
        new Group("fruit-tree","Fruit trees","FRUIT_TREE",2114,"fruit-tree"),
        new Group("calquat","Calquat","CALQUAT",5980,"fruit-tree"),
        new Group("celastrus","Celastrus","CELASTRUS",1391,"fruit-tree"),
        new Group("crystal-tree","Crystal tree","CRYSTAL_TREE",23866,"fruit-tree"),
        new Group("hespori","Hespori","HESPORI",23044,"special"),
        new Group("seaweed","Seaweed","SEAWEED",21504,"special"),
        new Group("cactus","Cactus","CACTUS",6016,"special"),
        new Group("mushroom","Mushrooms","MUSHROOM",6004,"special"),
        new Group("belladonna","Belladonna","BELLADONNA",2398,"special"),
        new Group("anima","Anima","ANIMA",22940,"special"),
        new Group("compost","Compost bins","COMPOST",6032,"special"),
        new Group("big-compost","Big compost bin","BIG_COMPOST",21483,"special"),
        new Group("coral","Coral","CORAL",31481,"special"));
    private final Client client;private final ConfigManager config;private final FarmPatchCatalog catalog;
    @Inject FarmHarvestTimers(Client client,ConfigManager config,FarmPatchCatalog catalog)
    {this.client=client;this.config=config;this.catalog=catalog;}
    List<HarvestClockTool.Harvest> read(FarmingTracker tracker,String profile)
    {
        if(!current(profile))return List.of();long now=Instant.now().getEpochSecond();
        boolean soonest=Boolean.TRUE.equals(config.getConfiguration(TimeTrackingConfig.CONFIG_GROUP,TimeTrackingConfig.PREFER_SOONEST,boolean.class));
        Set<WorldType> worlds=client.getWorldType();boolean leagues=worlds!=null&&worlds.contains(WorldType.SEASONAL)&&!worlds.contains(WorldType.DEADMAN);
        List<HarvestClockTool.Harvest> result=new ArrayList<>();
        for(Group group:GROUPS)
        {
            boolean observed=false,planted=false;long completion=soonest?Long.MAX_VALUE:0;
            for(FarmPatchCatalog.Patch patch:catalog.patches())
            {
                if(!group.type.equals(patch.type))continue;
                String value=config.getConfiguration(TimeTrackingConfig.CONFIG_GROUP,profile,patch.regionId+"."+patch.varbit);
                if(value==null)continue;String[] fields=value.split(":",-1);if(fields.length!=2)continue;
                try
                {
                    long at=Long.parseLong(fields[1]);FarmPatchCatalog.State state=catalog.state(patch.type,Integer.parseInt(fields[0]));
                    FarmPatchCatalog.Crop produce=state==null?null:catalog.crop(state.crop);
                    if(at<=0||at>now||produce==null||produce.itemId<0)continue;
                    observed=true;
                    if("WEEDS".equals(state.crop)||"SCARECROW".equals(state.crop)||"EMPTY".equals(state.state)||"FILLING".equals(state.state))continue;
                    planted=true;int rate=leagues?state.tickMinutes/5:state.tickMinutes;
                    long done=rate>0?tracker.getTickTime(rate,state.stages-1-state.stage,at,profile):0;
                    completion=soonest?Math.min(completion,done):Math.max(completion,done);
                }
                catch(NumberFormatException ignored){ }
            }
            SummaryState state=!observed?SummaryState.UNKNOWN:!planted?SummaryState.EMPTY:completion<=now?SummaryState.COMPLETED:SummaryState.IN_PROGRESS;
            result.add(new HarvestClockTool.Harvest(group.id,group.name,state,state==SummaryState.COMPLETED?0:state==SummaryState.IN_PROGRESS?completion:-1,true));
        }
        return current(profile)?List.copyOf(result):List.of();
    }
    private boolean current(String profile)
    {return profile!=null&&!profile.isEmpty()&&profile.equals(config.getRSProfileKey())&&client.getGameState()==GameState.LOGGED_IN&&client.getLocalPlayer()!=null;}
}
