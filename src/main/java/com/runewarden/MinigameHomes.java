/* SPDX-License-Identifier: BSD-2-Clause
 * Customer bounds, material ranges and native message formats derived from
 * TheStonedTurtle/Mahogany-Homes (BSD-2-Clause). See docs/licenses/mahogany-homes.txt.
 */
package com.runewarden;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Confirmed assignment, never inferred from the shared furniture-state varp. */
final class MinigameHomes
{
    static final class State
    {
        int version=1;String customer;int tier;
        State(){} State(String customer,int tier){this.customer=customer;this.tier=tier;}
    }
    static final class Customer
    {
        final String name,hint;final int x,y,width,height;final WorldPoint destination;final boolean upstairs;final int[][] materials;
        Customer(String name,int x,int y,int width,int height,int px,int py,String hint,boolean upstairs,int[][] materials)
        {this.name=name;this.x=x;this.y=y;this.width=width;this.height=height;this.destination=new WorldPoint(px,py,0);this.hint=hint;this.upstairs=upstairs;this.materials=materials;}
        boolean contains(WorldPoint p){return p!=null&&p.getX()>=x&&p.getX()<x+width&&p.getY()>=y&&p.getY()<y+height&&p.getPlane()<=1;}
    }
    static final Customer[] CUSTOMERS={
        new Customer("Jess",2611,3290,14,7,2621,3292,"Upstairs of the building south of the church in East Ardougne",true,new int[][]{{9, 11, 0, 1},{9, 11, 0, 1},{12, 15, 0, 1},{14, 15, 0, 1}}),
        new Customer("Noella",2652,3317,15,8,2659,3322,"North of East Ardougne market",false,new int[][]{{11, 12, 0, 0},{11, 12, 0, 0},{11, 15, 0, 0},{12, 15, 0, 0}}),
        new Customer("Ross",2609,3313,11,9,2613,3316,"North of the church in East Ardougne",false,new int[][]{{8, 11, 0, 1},{8, 11, 0, 1},{8, 11, 1, 1},{10, 11, 0, 1}}),
        new Customer("Larry",3033,3360,10,9,3038,3364,"North of the fountain in Falador",false,new int[][]{{8, 12, 0, 1},{8, 12, 0, 1},{9, 12, 0, 1},{12, 12, 0, 1}}),
        new Customer("Norman",3034,3341,8,8,3038,3344,"South of the fountain in Falador",true,new int[][]{{11, 11, 0, 1},{10, 11, 0, 1},{9, 13, 0, 1},{12, 13, 0, 1}}),
        new Customer("Tau",3043,3340,10,11,3047,3345,"South east of the fountain in Falador",false,new int[][]{{8, 12, 0, 1},{8, 12, 0, 1},{9, 13, 0, 1},{12, 13, 0, 1}}),
        new Customer("Barbara",1746,3531,10,11,1750,3534,"South of Hosidius, near the mill",false,new int[][]{{3, 8, 0, 1},{3, 8, 0, 1},{9, 10, 0, 1},{9, 10, 0, 1}}),
        new Customer("Leela",1781,3589,9,8,1785,3592,"East of the town market in Hosidius",false,new int[][]{{8, 9, 0, 1},{8, 9, 0, 1},{9, 10, 0, 1},{12, 13, 0, 1}}),
        new Customer("Mariah",1762,3618,10,7,1766,3621,"West of the estate agents in Hosidius",false,new int[][]{{7, 11, 0, 1},{7, 11, 0, 1},{11, 14, 0, 1},{13, 14, 0, 1}}),
        new Customer("Bob",3234,3482,10,10,3238,3486,"North-east Varrock, opposite the church",false,new int[][]{{13, 14, 0, 0},{13, 14, 0, 0},{13, 17, 0, 0},{16, 17, 0, 0}}),
        new Customer("Jeff",3235,3445,10,12,3239,3450,"Middle of Varrock, west of the museum",false,new int[][]{{11, 13, 0, 0},{11, 13, 0, 0},{11, 16, 0, 0},{15, 16, 0, 0}}),
        new Customer("Sarah",3232,3381,8,7,3235,3384,"Along the south wall of Varrock",false,new int[][]{{11, 11, 0, 1},{11, 11, 0, 1},{11, 11, 0, 1},{11, 11, 0, 1}})
    };
    private static final String TIER="(Beginner|Novice|Adept|Expert)";
    private static final Pattern ASSIGNED=Pattern.compile("^"+TIER+" Contract: Go see ([A-Za-z]+)[ ,].*\\.$",Pattern.CASE_INSENSITIVE);
    private static final Pattern REMINDER=Pattern.compile("^You're currently on an? "+TIER+" Contract\\. Go see ([A-Za-z]+)[ ,].*You can get another job once you have furnished (?:his|her) home\\.$",Pattern.CASE_INSENSITIVE);
    private static final Pattern DIALOG=Pattern.compile("^(?:Please could you go|Go) see ([A-Za-z]+)[ ,].*You can get another job once you have furnished (?:his|her) home\\.$",Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUEST=Pattern.compile("^Could I have an? "+TIER+" contract please\\?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern FINISHED=Pattern.compile("^You have completed [\\d,]+ contracts with a total of [\\d,]+ points?\\.$");
    private volatile State state=new State();private boolean dirty;
    static boolean validState(State state){return state!=null&&state.version==1&&state.tier>=0&&state.tier<=4&&(state.customer==null||customer(state.customer)!=null);}
    State state(){return new State(state.customer,state.tier);}
    void restore(State next){state=validState(next)?new State(next.customer,next.tier):new State();dirty=false;}
    void clear(){state=new State();dirty=false;}
    boolean takeDirty(){boolean value=dirty;dirty=false;return value;}
    Customer assigned(){return customer(state.customer);}
    int tier(){return state.tier;}
    static Customer customer(String name){if(name!=null)for(Customer c:CUSTOMERS)if(c.name.equalsIgnoreCase(name))return c;return null;}
    static int tier(String value){String[] tiers={"beginner","novice","adept","expert"};for(int i=0;i<4;i++)if(tiers[i].equalsIgnoreCase(value))return i+1;return 0;}
    void observe(String raw,boolean chat)
    {
        String text=clean(raw);if(text.isEmpty())return;
        if(chat&&FINISHED.matcher(text).matches()){change(null,0);return;}
        Matcher match=ASSIGNED.matcher(text);
        if(chat&&match.matches()){accept(match.group(2),tier(match.group(1)));return;}
        match=REMINDER.matcher(text);if(!chat&&match.matches()){accept(match.group(2),tier(match.group(1)));return;}
        match=DIALOG.matcher(text);if(!chat&&match.matches()){accept(match.group(1),state.tier);return;}
        match=REQUEST.matcher(text);if(!chat&&match.matches()){change(null,tier(match.group(1)));}
    }
    private void accept(String name,int tier){Customer c=customer(name);if(c!=null)change(c.name,tier);}
    private void change(String customer,int tier)
    {if(!Objects.equals(state.customer,customer)||state.tier!=tier){state=new State(customer,tier);dirty=true;}}
    void observeDialog(Client client)
    {for(int id:new int[]{(231<<16)|6,(217<<16)|6}){Widget w=client.getWidget(id);if(w!=null&&!w.isHidden())observe(w.getText(),false);}}
    private static String clean(String raw)
    {return raw==null?"":Text.removeTags(raw.replace("<br>"," ")).replaceAll("\\s+"," ").trim();}
    String guideId(){return state.tier>0?"training:homes-"+new String[]{"beginner","novice","adept","expert"}[state.tier-1]:null;}
    List<MinigameActivityCatalog.Fact> facts()
    {
        Customer customer=assigned();if(customer==null)return List.of();List<MinigameActivityCatalog.Fact> facts=new ArrayList<>();
        facts.add(new MinigameActivityCatalog.Fact("Assigned customer",customer.name));
        facts.add(new MinigameActivityCatalog.Fact("Address",customer.hint));
        if(state.tier>0){int[] range=customer.materials[state.tier-1];facts.add(new MinigameActivityCatalog.Fact("Contract",new String[]{"Beginner","Novice","Adept","Expert"}[state.tier-1]));
            facts.add(new MinigameActivityCatalog.Fact("Full contract materials",range[0]+"–"+range[1]+" "+new String[]{"planks","oak planks","teak planks","mahogany planks"}[state.tier-1]+(range[3]>0?" · "+range[2]+"–"+range[3]+" steel bars":"")));}
        return facts;
    }
    MinigameActivityCatalog.Snapshot travel(Client client)
    {
        Customer c=assigned();if(c==null)return MinigameActivityCatalog.Snapshot.EMPTY;
        boolean inside=c.contains(client.getLocalPlayer().getWorldLocation());
        String next=inside?"Talk to "+c.name:"Travel to "+c.name;
        String body=inside?(c.upstairs?"Your customer is upstairs. ":"")+"Talk to your customer after completing the assigned furniture, then accept the tea and request another contract.":c.hint+". Use Route to customer to send this confirmed address to Shortest Path.";
        return new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.MAHOGANY_HOMES,"Mahogany Homes",guideId(),facts(),List.of(new MinigameActivityCatalog.Step(next,body)),java.util.Set.of(),inside?List.of():List.of(c.destination),true);
    }
}
