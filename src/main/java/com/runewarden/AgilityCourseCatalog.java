/*
 * Copyright (c) 2018, Seth <http://github.com/sethtroll>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.runewarden;

import java.util.Arrays;
import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;

/** Pinned native course totals/end points; lap completion still requires an observed XP/progress event. */
enum AgilityCourseCatalog
{
    GNOME(110.5, 9781, new WorldPoint(2484, 3437, 0), new WorldPoint(2487, 3437, 0)),
	SHAYZIEN_BASIC(153.5, 6200, new WorldPoint(1554, 3640, 0)),
	DRAYNOR(120.0, 12338, new WorldPoint(3103, 3261, 0)),
	AL_KHARID(216.0, 13105, new WorldPoint(3299, 3194, 0)),
	PYRAMID(722.0, 13356, new WorldPoint(3364, 2830, 0)),
	VARROCK(270.0, 12853, new WorldPoint(3236, 3417, 0)),
	PENGUIN(540.0, 10559, new WorldPoint(2652, 4039, 1)),
	BARBARIAN(153.2, 10039, new WorldPoint(2543, 3553, 0)),
	CANIFIS(240.0, 13878, new WorldPoint(3510, 3485, 0)),
	APE_ATOLL(580.0, 11050, new WorldPoint(2770, 2747, 0)),
	SHAYZIEN_ADVANCED(507.5, 5944, new WorldPoint(1522, 3625, 0)),
	FALADOR(586, 12084, new WorldPoint(3029, 3332, 0), new WorldPoint(3029, 3333, 0), new WorldPoint(3029, 3334, 0), new WorldPoint(3029, 3335, 0)),
	COLOSSAL_WYRM_BASIC(601.6),
	WILDERNESS(571.4, 11837, new WorldPoint(2993, 3933, 0), new WorldPoint(2994, 3933, 0), new WorldPoint(2995, 3933, 0)),
	WEREWOLF(730.0, 14234, new WorldPoint(3528, 9873, 0)),
	SEERS(570.0, 10806, new WorldPoint(2704, 3464, 0)),
	COLOSSAL_WYRM_ADVANCED(1053.6),
	POLLNIVNEACH(890.0, 13358, new WorldPoint(3363, 2998, 0)),
	RELLEKA(780.0, 10553, new WorldPoint(2653, 3676, 0)),
	PRIFDDINAS(1337.0, 12895, new WorldPoint(3240, 6109, 0)),
	ARDOUGNE(889.0, 10547, new WorldPoint(2668, 3297, 0));
    final double baseXp;final int region;final WorldPoint[] ends;
    AgilityCourseCatalog(double xp){this(xp,-1,new WorldPoint[0]);}
    AgilityCourseCatalog(double xp,int region,WorldPoint... ends){baseXp=xp;this.region=region;this.ends=ends;}
    String label(){if(this==RELLEKA)return "Rellekka";if(this==GNOME)return "Gnome Stronghold";if(this==BARBARIAN)return "Barbarian Outpost";if(this==PYRAMID)return "Agility Pyramid";if(this==SEERS)return "Seers' Village";String[] words=name().toLowerCase(Locale.ROOT).split("_");StringBuilder s=new StringBuilder();for(String w:words){if(s.length()>0)s.append(' ');s.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));}return s.toString();}
    boolean finish(WorldPoint point){return point!=null&&Arrays.asList(ends).contains(point);}
    double xp(Client client)
    {
        if(this==PYRAMID)return baseXp+Math.min(300+8*client.getRealSkillLevel(Skill.AGILITY),1000);
        if(this==POLLNIVNEACH&&client.getVarbitValue(VarbitID.DESERT_DIARY_HARD_COMPLETE)==1)return 1016;
        if(this==RELLEKA&&client.getVarbitValue(VarbitID.FREMENNIK_DIARY_HARD_COMPLETE)==1)return 920;
        return baseXp;
    }
    static AgilityCourseCatalog region(int id){for(AgilityCourseCatalog c:values())if(c.region>=0&&c.region==id)return c;return null;}
}
