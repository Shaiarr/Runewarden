/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.*;
import net.runelite.api.dbtable.DBRowConfig;
import net.runelite.api.gameval.DBTableID;

/** Bounded native-cache access requirements, matched by the exact native clue text. */
@Singleton
final class ClueAccessIndex
{
    static final class Access
    {
        final String label,state,guideId; final int itemId;
        Access(String label,String state,String guideId,int itemId)
        { this.label=label; this.state=state; this.guideId=guideId; this.itemId=itemId; }
    }
    private static final class Schema { int id,text,requirements; }
    private static final class Schemas { List<Schema> tables; }
    private static final class Row
    {
        final int id; final Schema schema;
        Row(int id,Schema schema) { this.id=id; this.schema=schema; }
    }
    private final Client client;
    private final List<Schema> tables;
    private final ArrayDeque<Row> pending=new ArrayDeque<>();
    private final Map<String,List<Integer>> byText=new HashMap<>();
    private boolean initialized,ready;
    @Inject ClueAccessIndex(Client client,Gson gson)
    {
        this.client=client; List<Schema> found=Collections.emptyList();
        try (InputStreamReader reader=new InputStreamReader(ClueAccessIndex.class.getResourceAsStream("/com/runewarden/clue-db-schemas.json"),StandardCharsets.UTF_8))
        { Schemas input=gson.fromJson(reader,Schemas.class); if (input!=null && input.tables!=null) found=input.tables; }
        catch (Exception ignored) { }
        tables=Collections.unmodifiableList(found);
    }
    void onGameTick()
    {
        if (!initialized)
        {
            initialized=true;
            for (Schema schema : tables)
            {
                List<Integer> rows=client.getDBTableRows(schema.id);
                if (rows!=null) for (Integer row : rows) if (row!=null && row>=0 && pending.size()<2000) pending.add(new Row(row,schema));
            }
            // A missing cache can become available after login; never seal an empty native index.
            if (pending.isEmpty()) { initialized=false; return; }
        }
        for (int i=0;i<24 && !pending.isEmpty();i++)
        {
            Row row=pending.removeFirst(); Object[] text=client.getDBTableField(row.id,row.schema.text,0);
            Object[] requirements=client.getDBTableField(row.id,row.schema.requirements,0);
            if (text==null || text.length!=1 || !(text[0] instanceof String)) continue;
            List<Integer> ids=new ArrayList<>();
            if (requirements!=null) for (Object value : requirements) if (value instanceof Integer && (Integer)value>=0 && ids.size()<32) ids.add((Integer)value);
            byText.put(ClueDataService.normalize((String)text[0]),Collections.unmodifiableList(ids));
        }
        ready=pending.isEmpty();
    }
    boolean ready() { return ready; }
    boolean matched(String text) { return byText.containsKey(ClueDataService.normalize(text)); }
    List<Access> access(String text)
    {
        List<Integer> ids=byText.get(ClueDataService.normalize(text)); List<Access> result=new ArrayList<>();
        if (ids==null) return result;
        for (int id : ids)
        {
            DBRowConfig definition=client.getDBRowConfig(id); if (definition==null) continue;
            int table=definition.getTableID();
            if (table==DBTableID.CluehelperRequirementStat.ID)
            {
                Integer stat=integer(id,DBTableID.CluehelperRequirementStat.COL_STAT),level=integer(id,DBTableID.CluehelperRequirementStat.COL_LEVEL);
                if (stat==null || level==null || stat<0 || stat>=Skill.values().length || level<1 || level>99) continue;
                Skill skill=Skill.values()[stat]; int base=client.getRealSkillLevel(skill),current=client.getBoostedSkillLevel(skill);
                result.add(new Access(level+" "+skill.getName(),base>=level ? "Base level met" : current>=level ? "Boosted level reached · boost acceptance not checked" : "Needs "+level+" · current "+current,"",-1));
            }
            else if (table==DBTableID.CluehelperRequirementQuest.ID)
            {
                String description=string(id,DBTableID.CluehelperRequirementQuest.COL_DESCRIPTION);
                Integer questRow=integer(id,DBTableID.CluehelperRequirementQuest.COL_QUEST),required=integer(id,DBTableID.CluehelperRequirementQuest.COL_VARSTATE);
                if (questRow==null) continue;
                String name=string(questRow,DBTableID.Quest.COL_DISPLAYNAME); Quest quest=null;
                for (Quest candidate : Quest.values()) if (candidate.getId()==questRow) { quest=candidate; break; }
                Integer end=integer(questRow,DBTableID.Quest.COL_ENDSTATE); String state="Quest-stage access unknown";
                if (quest!=null)
                {
                    QuestState observed=quest.getState(client);
                    if (observed==QuestState.FINISHED) state="Quest completed";
                    else if (required!=null && end!=null && required>=end) state="Quest completion required";
                    else if (observed==QuestState.NOT_STARTED && required!=null && required>0) state="Quest not started";
                }
                result.add(new Access(description.isEmpty() ? name : description,state,name.isEmpty() ? "" : "quest:"+name,-1));
            }
            else if (table==DBTableID.CluehelperRequirementObj.ID)
            {
                String description=string(id,DBTableID.CluehelperRequirementObj.COL_DESCRIPTION);
                Integer item=integer(id,DBTableID.CluehelperRequirementObj.COL_ITEM),inv=integer(id,DBTableID.CluehelperRequirementObj.COL_INV),count=integer(id,DBTableID.CluehelperRequirementObj.COL_COUNT);
                if (item==null || item<1 || inv==null || count==null || count<1) continue;
                ItemContainer container=client.getItemContainer(inv); long quantity=0;
                if (container!=null) for (Item value : container.getItems()) if (value.getId()==item) quantity+=value.getQuantity();
                result.add(new Access(description.isEmpty() ? "Item "+item+" × "+count : description,
                    container==null ? "Required container not observed" : quantity>=count ? "Required quantity present" : "Needs "+count+" · present "+quantity,"",item));
            }
            else
            {
                String description=string(id,0);
                if (!description.isEmpty()) result.add(new Access(description,"Access condition not evaluated","",-1));
            }
        }
        return Collections.unmodifiableList(result);
    }
    private Integer integer(int row,int column)
    { Object[] value=client.getDBTableField(row,column,0); return value!=null && value.length==1 && value[0] instanceof Integer ? (Integer)value[0] : null; }
    private String string(int row,int column)
    { Object[] value=client.getDBTableField(row,column,0); return value!=null && value.length==1 && value[0] instanceof String ? GuideNavigation.safe((String)value[0]) : ""; }
}
