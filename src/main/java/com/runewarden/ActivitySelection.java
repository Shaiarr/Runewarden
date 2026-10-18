/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;
import java.util.function.Consumer;
/** Guide following prepares a session without replacing a more precise linked training choice. */
final class ActivitySelection
{
    private String last;
    void reset(){last=null;}
    void update(MinigameActivityCatalog.Snapshot minigame,GuideCatalog.Entry guide,SharedActivitySession.Context prepared,Consumer<SharedActivitySession.Context> select)
    {
        String key=minigame.activity!=null?"minigame:"+minigame.activity.name():guide==null?null:guide.id;
        if(key==null){last=null;return;}if(key.equals(last))return;last=key;
        String linked=minigame.activity!=null?minigame.guideId:guide.id;
        if(linked!=null&&prepared!=null&&prepared.methodId!=null&&linked.equals(prepared.guideId))return;
        if(minigame.activity!=null)select.accept(new SharedActivitySession.Context(SharedActivitySession.Kind.MINIGAME,key,minigame.title,minigame.guideId,null,null,0));
        else
        {
            SharedActivitySession.Kind kind="quest".equals(guide.category)?SharedActivitySession.Kind.QUEST:
                "boss".equals(guide.category)||"waves".equals(guide.category)?SharedActivitySession.Kind.BOSS:
                "minigame".equals(guide.category)?SharedActivitySession.Kind.MINIGAME:
                "training".equals(guide.category)?SharedActivitySession.Kind.TRAINING:SharedActivitySession.Kind.OTHER;
            net.runelite.api.Skill skill=null;
            if(kind==SharedActivitySession.Kind.TRAINING||kind==SharedActivitySession.Kind.MINIGAME)
                try{if(guide.skill!=null)skill=net.runelite.api.Skill.valueOf("runecrafting".equalsIgnoreCase(guide.skill)?"RUNECRAFT":guide.skill.toUpperCase(java.util.Locale.ROOT));}catch(IllegalArgumentException ignored){}
            select.accept(new SharedActivitySession.Context(kind,guide.id,guide.title,guide.id,skill==null?null:guide.id,skill,0));
        }
    }
}
