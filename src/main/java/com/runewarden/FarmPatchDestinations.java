/*
 * Copyright (c) 2018, Torkel Velure <https://github.com/TorkelV>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.runewarden;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/** Native RuneLite 1.13.2 map markers; combined garden markers identify the farm area.
 * Source: https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/FarmingPatchLocation.java
 * Visible patch objects and player-pinned stops take precedence over these map markers. */
final class FarmPatchDestinations
{
    static final class Site
    {
        final List<String> types; final WorldPoint point;
        Site(String types,int x,int y,int plane){this.types=Arrays.asList(types.split(","));point=new WorldPoint(x,y,plane);}
    }
    private static final List<Site> SITES=Collections.unmodifiableList(Arrays.asList(
        new Site("ALLOTMENT",3793,2836,0),
        new Site("ALLOTMENT",1269,3730,0),
        new Site("ALLOTMENT,FLOWER",3289,6100,0),
        new Site("ALLOTMENT,HERB,FLOWER",1729,3558,0),
        new Site("ALLOTMENT,HERB,FLOWER",3598,3524,0),
        new Site("ALLOTMENT,HERB,FLOWER",3052,3309,0),
        new Site("ALLOTMENT,HERB,FLOWER",2810,3462,0),
        new Site("ALLOTMENT,HERB,FLOWER",2663,3375,0),
        new Site("ALLOTMENT,HERB,FLOWER",1587,3101,0),
        new Site("ANIMA,HERB",1235,3724,0),
        new Site("BELLADONNA",3084,3356,0),
        new Site("BELLADONNA",1449,3354,0),
        new Site("BUSH",2938,3223,0),
        new Site("BUSH",2589,3862,0),
        new Site("BUSH",3182,3356,0),
        new Site("BUSH",2615,3224,0),
        new Site("BUSH,FLOWER",1259,3729,0),
        new Site("CACTUS",3313,3201,0),
        new Site("CACTUS",1264,3745,0),
        new Site("CALQUAT",2793,3099,0),
        new Site("CALQUAT",1365,3035,0),
        new Site("CALQUAT",3125,2403,0),
        new Site("CELASTRUS,FRUIT_TREE",1242,3755,0),
        new Site("CORAL",3296,8861,0),
        new Site("CRYSTAL_TREE",3292,6120,0),
        new Site("FLOWER",1352,3025,0),
        new Site("FRUIT_TREE",2487,3181,0),
        new Site("FRUIT_TREE",2343,3160,0),
        new Site("FRUIT_TREE",2472,3445,0),
        new Site("FRUIT_TREE",2858,3432,0),
        new Site("FRUIT_TREE",2765,3211,0),
        new Site("FRUIT_TREE",1347,3058,0),
        new Site("GRAPES",1807,3555,0),
        new Site("HARDWOOD_TREE",3707,3838,0),
        new Site("HARDWOOD_TREE",1683,2971,0),
        new Site("HARDWOOD_TREE",2472,2705,0),
        new Site("HERB",3789,2840,0),
        new Site("HERB",2847,3933,0),
        new Site("HERB",2828,3696,0),
        new Site("HESPORI",1182,10068,0),
        new Site("HOPS",2572,3102,0),
        new Site("HOPS",2661,3523,0),
        new Site("HOPS",3224,3313,0),
        new Site("HOPS",2812,3334,0),
        new Site("HOPS",1367,2938,0),
        new Site("MUSHROOM",3449,3471,0),
        new Site("REDWOOD",1233,3754,0),
        new Site("SEAWEED",3730,10271,0),
        new Site("SPIRIT_TREE",3056,3259,0),
        new Site("SPIRIT_TREE",1690,3540,0),
        new Site("SPIRIT_TREE",3614,3856,0),
        new Site("SPIRIT_TREE",2799,3205,0),
        new Site("SPIRIT_TREE",1254,3753,0),
        new Site("TREE",3226,3457,0),
        new Site("TREE",2933,3436,0),
        new Site("TREE",3189,3233,0),
        new Site("TREE",2434,3418,0),
        new Site("TREE",3005,3375,0),
        new Site("TREE",1234,3736,0),
        new Site("TREE",1366,3318,0)
    ));
    private FarmPatchDestinations(){}
    static WorldPoint forPatch(FarmPatchCatalog.Patch patch)
    {
        if(patch==null)return null;WorldPoint result=null;
        for(Site site:SITES)if(site.types.contains(patch.type) && patch.sentAt(site.point))
        {
            if(result!=null && !result.equals(site.point))return null; // Ambiguity never becomes a guessed destination.
            result=site.point;
        }
        return result;
    }
    static List<Site> sites(){return SITES;}
}
