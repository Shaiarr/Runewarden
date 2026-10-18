/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Color;

/** User-configurable GP bands, not an inferred drop rarity. */
enum GroundLootTier
{
    WHITE("White",new Color(243,240,232)),GREEN("Green",new Color(91,221,134)),BLUE("Blue",new Color(86,170,255)),
    PURPLE("Purple",new Color(192,111,255)),GOLD("Gold",new Color(235,195,105));
    final String label;final Color color;
    GroundLootTier(String label,Color color){this.label=label;this.color=color;}
    @Override public String toString(){return label;}
    static GroundLootTier value(long amount,GroundLootTool.Preferences settings)
    {
        GroundLootTier tier=null;
        for(GroundLootTier candidate:values())if(amount>=settings.threshold(candidate))tier=candidate;
        return tier;
    }
    static GroundLootTier beam(GroundLootTool.Entry entry,GroundLootTool.Preferences settings)
    {
        if(!settings.beams)return null;
        GroundLootTier value=value(entry.value,settings),target=null;
        if(entry.guideTarget&&settings.guideBeams||entry.manualTarget&&settings.manualBeams)target=settings.targetBeamTier;
        if(value!=null&&value.ordinal()<settings.minimumBeamTier.ordinal())value=null;
        return target==null?value:value==null||target.ordinal()>value.ordinal()?target:value;
    }
}
