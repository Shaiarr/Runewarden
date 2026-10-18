/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Native objective identity and positive completion evidence; never a guessed completed checklist. */
final class MinigameGuideProgress
{
    private static final Pattern HOME_FINISHED=Pattern.compile("^You have completed [\\d,]+ contracts with a total of [\\d,]+ points?\\.$");
    private MinigameActivityCatalog.Snapshot previous, pending;
    private int previousCounter=-1;
    void clear(){previous=pending=null;previousCounter=-1;}

    void onMessage(String raw,String homeGuide)
    {
        if(raw==null)return;
        String text=Text.removeTags(raw).replaceAll("\\s+"," ").trim();
        if(homeGuide!=null&&HOME_FINISHED.matcher(text).matches())
            pending=completed(MinigameActivityCatalog.Activity.MAHOGANY_HOMES,"Mahogany Homes",homeGuide,"Contract completed");
    }

    /** Result panels and per-player finish bits work even when a guide is opened after the activity. */
    MinigameActivityCatalog.Snapshot terminal(Client client)
    {
        if(client==null||client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null)return null;
        if(pending!=null){MinigameActivityCatalog.Snapshot result=pending;pending=null;return result;}
        if(visible(client,InterfaceID.SoulWarsGameEnd.MAIN))
            return completed(MinigameActivityCatalog.Activity.SOUL_WARS,"Soul Wars","minigame:soul-wars","Match completed");
        if(visible(client,InterfaceID.CastlewarsScore.UNIVERSE))
            return completed(MinigameActivityCatalog.Activity.CASTLE_WARS,"Castle Wars","minigame:castle-wars","Match completed");
        if(visible(client,InterfaceID.BrewGameOver.BREW_WON_LOST))
            return completed(MinigameActivityCatalog.Activity.TROUBLE_BREWING,"Trouble Brewing","minigame:trouble-brewing","Round completed");
        if(visible(client,InterfaceID.TrawlerReward.UNIVERSAL))
            return completed(MinigameActivityCatalog.Activity.FISHING_TRAWLER,"Fishing Trawler","minigame:fishing-trawler","Trip results");
        // RuneLite BarbarianAssaultPlugin uses this exact queen-reward marker to identify a finished game.
        Widget queen=client.getWidget(InterfaceID.BarbassaultWavecomplete.BARBASSAULT_COMPL_QUEENREWARDS);
        if(queen!=null&&!queen.isHidden()&&queen.getText()!=null&&queen.getText().contains("<br>5"))
            return completed(MinigameActivityCatalog.Activity.BARBARIAN_ASSAULT,"Barbarian Assault","minigame:barbarian-assault","Queen game completed");
        if(visible(client,(746<<16)|2)&&client.getVarbitValue(VarbitID.GOTR_PLAYER_HAS_FINISHED_GAME)==1)
            return completed(MinigameActivityCatalog.Activity.GOTR,"Guardians of the Rift","training:guardians-rift","Game completed");
        return null;
    }

    MinigameActivityCatalog.Snapshot observe(MinigameActivityCatalog.Snapshot snapshot,Client client)
    {
        if(snapshot==null)return MinigameActivityCatalog.Snapshot.EMPTY;
        if(client==null||client.getGameState()!=GameState.LOGGED_IN){clear();return snapshot;}
        MinigameActivityCatalog.Snapshot observed=snapshot.activity==null?previous:snapshot;
        int counter=observed==null?-1:counter(client,observed.activity);
        boolean same=previous!=null&&observed!=null&&previous.activity==observed.activity
            &&java.util.Objects.equals(previous.guideId,observed.guideId);
        MinigameActivityCatalog.Snapshot completion=null;
        if(same&&counter>=0&&previousCounter>=0&&counter>previousCounter)
            completion=completed(observed.activity,observed.title,observed.guideId,"Native completed-game count increased");
        previous=snapshot.activity==null?null:snapshot;previousCounter=snapshot.activity==null?-1:counter;
        if(completion!=null)return completion;
        if(snapshot.activity==MinigameActivityCatalog.Activity.GIANTS_FOUNDRY)
            return MinigameFoundry.guideProgress(snapshot,client);
        if(snapshot.activity==MinigameActivityCatalog.Activity.PEST_CONTROL&&pestWon(client))
            return completed(snapshot.activity,snapshot.title,snapshot.guideId,"All four portals destroyed");
        return annotate(snapshot);
    }

    /** Exact identities are attached to objectives already selected from native state by our readers. */
    MinigameActivityCatalog.Snapshot annotate(MinigameActivityCatalog.Snapshot source)
    {
        if(source==null||source.activity==null||source.terminal||!source.objectiveKey.isEmpty()||source.steps.isEmpty())return source;
        if(source.activity==MinigameActivityCatalog.Activity.GIANTS_FOUNDRY)return source;
        String title=source.steps.get(0).title;
        if(title==null)return source;
        String id=null,key=null;
        switch(source.activity)
        {
            case WINTERTODT:
                if(title.equals("Chop bruma roots")){id="method:1";key="gather-roots";}
                else if(title.equals("Fletch or feed bruma roots")){id="method:1";key="fletch-roots";}
                else if(title.equals("Feed your kindling")||title.equals("Feed bruma roots")){id="method:2";key="feed-brazier";}
                else if(title.equals("Repair the brazier")){id="method:2";key="repair-brazier";}
                else if(title.equals("Light the brazier")){id="method:2";key="light-brazier";}
                else if(title.equals("Restore warmth")){id="method:3";key="restore-warmth";}
                else if(title.equals("Revive the pyromancer")){id="method:3";key="revive-pyromancer";}
                break;
            case GOTR:
                if(title.equals("Gather guardian fragments")||title.equals("Mine your fragment batch")){id="method:1";key="mine-fragments";}
                else if(title.equals("Craft guardian essence")){id="method:1";key="craft-essence";}
                else if(title.equals("Use an active altar")){id="method:2";key="craft-stones";}
                else if(title.equals("Empower the Great Guardian")){id="method:2";key="empower-guardian";}
                break;
            case TEMPOROSS:
                if(title.equals("Catch harpoonfish")){id="method:1";key="catch-fish";}
                else if(title.equals("Cook harpoonfish")){id="method:1";key="cook-fish";}
                else if(title.equals("Load cooked harpoonfish")||title.equals("Load raw harpoonfish")){id="method:2";key="load-fish";}
                else if(title.equals("Remain tethered")||title.equals("Tether for the incoming wave")){id="method:2";key="tether";}
                else if(title.equals("Fish the spirit pool")){id="method:3";key="spirit-pool";}
                break;
            case MIXOLOGY:
                if(title.equals("Prepare the mixing station")){id="method:1";key="prepare-paste";}
                else if(title.startsWith("Mix ")){id="method:2";key="mix-potion";}
                else if(title.equals("Use the agitator")||title.equals("Use the retort")||title.equals("Use the alembic")){id="method:2";key="process-potion";}
                else if(title.equals("Deliver the finished order")){id="method:2";key="deliver-order";}
                break;
            case MAHOGANY_HOMES:
                if(title.startsWith("Travel to ")){id="method:1";key="travel-to-customer";}
                else if(title.equals("Repair the furniture")){id="method:1";key="repair-furniture";}
                else if(title.equals("Remove damaged furniture")){id="method:1";key="remove-furniture";}
                else if(title.equals("Build the replacement")){id="method:1";key="build-furniture";}
                else if(title.equals("Check the assigned furniture")){id="method:1";key="refresh-furniture";}
                else if(title.startsWith("Talk to ")){id="method:2";key="speak-to-customer";}
                break;
            case PEST_CONTROL:
                if(title.equals("Clear nearby spinners")){id="method:2";key="clear-spinners";}
                else if(title.startsWith("Attack the ")&&title.endsWith(" portal")){id="method:2";key="attack-open-portal";}
                else if(title.equals("Defend the Void Knight")){id="method:2";key="defend-knight";}
                break;
            case BLAST_FURNACE:
                if(title.equals("Collect your bars")){id="method:2";key="collect-bars";}
                else if(title.equals("Load the conveyor belt")){id="method:2";key="load-ore";}
                break;
            case TITHE_FARM:
                if(title.equals("Plant and water")){id="method:1";key="plant";}
                else if(title.equals("Water dry plants")){id="method:1";key="water";}
                else if(title.equals("Clear dead plants")){id="method:1";key="clear-dead";}
                else if(title.equals("Harvest mature fruit")){id="method:2";key="harvest";}
                else if(title.equals("Deposit your harvested fruit")){id="method:2";key="deposit-fruit";}
                break;
            case BARBARIAN_ASSAULT:
                if(title.equals("Send the call and wait for yours")||title.equals("Call and follow your role")){id="method:2";key="send-call";}
                else if(title.equals("Restock healer supplies")){id="method:1";key="restock-supplies";}
                else if(title.contains("egg")){id="method:4";key="queen-egg";}
                else if(title.startsWith("Attack using ")){id="method:3";key="attacker-role";}
                else if(title.startsWith("Collect ")){id="method:3";key="collector-role";}
                else if(title.equals("Repair the runner trap")||title.startsWith("Bait runners with ")){id="method:3";key="defender-role";}
                else if(title.startsWith("Poison with ")){id="method:3";key="healer-role";}
                break;
            case NIGHTMARE_ZONE:
                if(title.equals("Maintain your dream supplies")){id="method:3";key="maintain-dream";}
                else if(title.equals("Check your protection")){id="method:2";key="check-protection";}
                break;
            case PYRAMID_PLUNDER:
                if(title.equals("Pass the spear trap")){id="method:2";key="pass-trap";}
                else if(title.startsWith("Advance toward room ")){id="method:2";key="advance-room";}
                else if(title.equals("Loot or move to the next room")){id="method:2";key="loot-room";}
                break;
            case MAGE_TRAINING_ARENA:
                if(title.equals("Enchant and deposit shapes")){id="method:3";key="enchant";}
                else if(title.equals("Check the current alchemy values")||title.startsWith("Alchemise ")){id="method:3";key="alchemy";}
                else if(title.equals("Convert bones and manage health")){id="method:4";key="graveyard";}
                else if(title.equals("Move the maze guardian")||title.startsWith("Pull ")||title.startsWith("Move ")||title.startsWith("Cast Telekinetic Grab")||title.startsWith("Stand ")||title.equals("Check the loaded maze")||title.equals("Maze complete")){id="method:2";key="telekinetic";}
                break;
            case VOLCANIC_MINE:
                if(title.equals("Return to the exit")){id="method:3";key="return-to-exit";}
                else if(title.startsWith("Check ")||title.startsWith("Maintain Vent ")||title.equals("Mine and maintain the chambers")){id="method:1";key="maintain-vents";}
                else if(title.equals("Mine the boulder")||title.equals("Mine and watch your vent role")){id="method:2";key="mine-boulder";}
                break;
            case BLAST_MINE:
                if(title.equals("Excavate a rock")){id="method:1";key="excavate";}
                else if(title.equals("Load dynamite")){id="method:1";key="load-dynamite";}
                else if(title.equals("Light and retreat")){id="method:2";key="light-charge";}
                else if(title.equals("Collect blasted ore")){id="method:2";key="collect-ore";}
                break;
            case FISHING_TRAWLER:
                if(title.equals("Bail rising water")){id="method:2";key="bail-water";}
                else if(title.equals("Contribute on your deck")){id="method:2";key="contribute";}
                break;
            case DRIFT_NET:
                if(title.startsWith("Empty net ")){id="method:3";key="empty-net";}
                else if(title.equals("Get replacement drift nets")){id="method:1";key="restock-nets";}
                else if(title.startsWith("Set net ")){id="method:1";key="set-net";}
                else if(title.equals("Herd fish shoals")){id="method:2";key="herd-fish";}
                break;
            case TROUBLE_BREWING:
                if(title.equals("Deposit your ingredients")){id="method:3";key="deposit-ingredients";}
                else if(title.equals("Cut logs into bark")){id="method:3";key="process-bark";}
                else if(title.equals("Collect a knife")){id="method:1";key="collect-tool";}
                else if(title.equals("Gather scrapey tree logs")){id="method:2";key="gather-logs";}
                break;
            case SOUL_WARS:
                if(title.equals("Take fragments to the obelisk")){id="method:3";key="offer-fragments";}
                else if(title.equals("Restock at your base")){id="method:1";key="restock";}
                else if(title.equals("Capture and contribute")){id="method:2";key="capture-obelisk";}
                break;
            case CASTLE_WARS:
                if(title.equals("Return the enemy standard")){id="method:3";key="return-standard";}
                else if(title.equals("Protect your standard")){id="method:3";key="protect-standard";}
                else if(title.equals("Collect bandages")){id="method:2";key="collect-bandages";}
                else if(title.equals("Attack or defend with your team")){id="method:4";key="team-objective";}
                break;
            default:break;
        }
        return key==null?source:source.withGuideProgress(id,source.activity.name().toLowerCase(Locale.ROOT)+":"+key,false,Collections.emptySet());
    }

    private static int counter(Client client,MinigameActivityCatalog.Activity activity)
    {
        switch(activity)
        {
            case WINTERTODT:return client.getVarpValue(VarPlayerID.TOTAL_WINTERTODT_KILLS);
            case TEMPOROSS:return client.getVarpValue(VarPlayerID.TOTAL_TEMPOROSS_KILLS);
            case GOTR:return client.getVarpValue(VarPlayerID.TOTAL_GOTR_KILLS);
            case SOUL_WARS:return client.getVarpValue(VarPlayerID.SOUL_WARS_TOTAL_GAMES);
            case CASTLE_WARS:return client.getVarpValue(VarPlayerID.CASTLEWARS_GAMES_PLAYED);
            default:return -1;
        }
    }
    private static boolean pestWon(Client client)
    {
        if(!visible(client,InterfaceID.PestStatusOverlay.PEST_STATUS_OVER_HEALTH))return false;
        int[] hp={InterfaceID.PestStatusOverlay.PEST_STATUS_PORTTXT1,InterfaceID.PestStatusOverlay.PEST_STATUS_PORTTXT2,
            InterfaceID.PestStatusOverlay.PEST_STATUS_PORTTXT3,InterfaceID.PestStatusOverlay.PEST_STATUS_PORTTXT4};
        for(int id:hp)if(!"0".equals(text(client,id)))return false;
        String knight=text(client,InterfaceID.PestStatusOverlay.PEST_STATUS_OVER_HEALTH);
        return knight.matches("[1-9][0-9]*");
    }
    private static MinigameActivityCatalog.Snapshot completed(MinigameActivityCatalog.Activity activity,String title,String guide,String reason)
    {
        return new MinigameActivityCatalog.Snapshot(activity,title,guide,List.of(new MinigameActivityCatalog.Fact("Result",reason)),
            List.of(new MinigameActivityCatalog.Step("Completed",reason)),Set.of(),List.of(),true)
            .withGuideProgress(null,activity.name().toLowerCase(Locale.ROOT)+":completed",true,Set.of());
    }
    private static boolean visible(Client client,int id)
    {Widget widget=client.getWidget(id>>>16,id&65535);return widget!=null&&!widget.isHidden();}
    private static String text(Client client,int id)
    {Widget widget=client.getWidget(id>>>16,id&65535);return widget==null||widget.isHidden()||widget.getText()==null?"":Text.removeTags(widget.getText()).trim();}
}
