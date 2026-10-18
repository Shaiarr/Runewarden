/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
/** Revision-pinned completion rewards, separate from variable sailing XP/hour or bank allocation. */
@Singleton
public final class SailingXpScenarios
{
    public static final class Trial
    {
        public String methodId,name,rank,source,timestamp;public int level,completionXp,supplyXp,firstRankBonus,revision;
        public long experience(long completions,boolean firstRank)
        {if(completions<0||completions>1_000_000)throw new IllegalArgumentException("Completions must be 0–1,000,000");return completions*(completionXp+(long)supplyXp)+(completions>0&&firstRank?firstRankBonus:0);}
        @Override public String toString(){return rank;}
    }
    public static final class Courier
    {
        public int id,level,xp,quantity;public String noticeBoard,cargoLocation,destination,cargo;
        public long experience(long tasks){if(tasks<0||tasks>1_000_000)throw new IllegalArgumentException("Tasks must be 0–1,000,000");return tasks*(long)xp;}
        @Override public String toString(){return noticeBoard+" · "+cargoLocation+" → "+destination+" ("+xp+" XP)";}
    }
    private static final class File{String courierSource;List<Trial> trials;List<Courier> courierTasks;}
    private final File data;
    @Inject public SailingXpScenarios(Gson gson)
    {
        data=gson.fromJson(new InputStreamReader(SailingXpScenarios.class.getResourceAsStream("/com/runewarden/sailing-xp-scenarios.json"),StandardCharsets.UTF_8),File.class);
        if(data==null||data.trials==null||data.courierTasks==null||data.courierSource==null)throw new IllegalStateException("Missing Sailing reward scenarios");
        HashSet<String> ranks=new HashSet<>();for(Trial t:data.trials)if(t==null||t.methodId==null||t.rank==null||!ranks.add(t.methodId+":"+t.rank)||t.level<1||t.level>99||t.completionXp<=0||t.supplyXp<0||t.firstRankBonus<0||t.source==null||t.revision<=0)throw new IllegalStateException("Invalid Sailing trial scenario");
        HashSet<Integer> ids=new HashSet<>();for(Courier c:data.courierTasks)if(c==null||c.id<=0||!ids.add(c.id)||c.level<1||c.level>99||c.xp<=0||c.quantity<=0||c.noticeBoard==null||c.cargoLocation==null||c.destination==null||c.cargo==null)throw new IllegalStateException("Invalid courier scenario");
    }
    public List<Trial> trials(String method){List<Trial> result=new ArrayList<>();for(Trial t:data.trials)if(t.methodId.equals(method))result.add(t);return Collections.unmodifiableList(result);}
    public List<Courier> couriers(){return Collections.unmodifiableList(data.courierTasks);}
    public String courierSource(){return data.courierSource;}
}
