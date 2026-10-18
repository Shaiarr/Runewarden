/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

/** Adds only local Runewarden UI actions to menus; never dispatches a game action. */
@Singleton
final class ContextActions
{
    private final Client client;private final ConfigManager configs;private final RunewardenConfig config;
    private boolean started;private long epoch;
    private IntConsumer item=id->{};private Consumer<NPC> npc=value->{};private Consumer<Player> player=value->{};
    private Consumer<String> search=value->{},module=value->{};
    @Inject ContextActions(Client client,ConfigManager configs,RunewardenConfig config)
    {this.client=client;this.configs=configs;this.config=config;}
    void setActions(IntConsumer item,Consumer<NPC> npc,Consumer<Player> player,Consumer<String> search,Consumer<String> module)
    {this.item=item;this.npc=npc;this.player=player;this.search=search;this.module=module;}
    void start(){started=true;clear();}void stop(){started=false;clear();}void clear(){epoch++;}
    void onMenuOpened(MenuOpened event)
    {
        if(!started||!config.contextActions()||!client.isKeyPressed(KeyCode.KC_SHIFT)||client.getGameState()!=GameState.LOGGED_IN
            ||event==null||configs.getRSProfileKey()==null)return;
        MenuEntry[] entries=event.getMenuEntries();if(entries==null)return;
        long ticket=epoch;String owner=configs.getRSProfileKey();
        for(int i=entries.length-1;i>=0;i--)
        {
            MenuEntry entry=entries[i];if(entry==null||entry.getType()==MenuAction.RUNELITE||entry.getType()==MenuAction.CANCEL||entry.getType()==MenuAction.WALK)continue;
            NPC targetNpc=entry.getNpc();Player targetPlayer=entry.getPlayer();
            String target=plain(entry.getTarget());
            if(targetPlayer!=null&&targetPlayer!=client.getLocalPlayer())
            {add("Inspect equipment",target,owner,ticket,()->{if(targetPlayer.getWorldView()==client.getTopLevelWorldView())player.accept(targetPlayer);});return;}
            if(targetNpc!=null)
            {
                String destination=destination(entry.getOption(),target);
                if(destination!=null)add("Open "+destination,target,owner,ticket,()->module.accept(destination));
                add("Mob reference",target,owner,ticket,()->{if(targetNpc.getWorldView()==client.getTopLevelWorldView())npc.accept(targetNpc);});return;
            }
            int id=itemId(entry);
            if(id>0){add("Item reference",target,owner,ticket,()->item.accept(id));return;}
            if(target.isEmpty()||entry.getType()==MenuAction.CC_OP||entry.getType()==MenuAction.CC_OP_LOW_PRIORITY)continue;
            if(!objectAction(entry.getType()))continue;
            String destination=destination(entry.getOption(),target);
            if(destination!=null)add("Open "+destination,target,owner,ticket,()->module.accept(destination));
            add("Find guides",target,owner,ticket,()->search.accept(target));return;
        }
    }
    private void add(String option,String target,String owner,long ticket,Runnable action)
    {
        client.createMenuEntry(-1).setOption("Runewarden: "+option).setTarget(target).setType(MenuAction.RUNELITE)
            .onClick(clicked->{if(started&&epoch==ticket&&Objects.equals(owner,configs.getRSProfileKey())&&client.getGameState()==GameState.LOGGED_IN)action.run();});
    }
    static String plain(String text)
    {String value=text==null?"":Text.removeTags(text).replaceAll("\\s*\\(level-?[^)]*\\)","").trim();return value.equalsIgnoreCase("null")||value.equalsIgnoreCase("undefined")?"":value;}
    static String destination(String option,String target)
    {
        String action=plain(option).toLowerCase(Locale.ROOT),name=plain(target).toLowerCase(Locale.ROOT);
        if(name.contains("patch")&&(action.contains("inspect")||action.contains("rake")||action.contains("harvest")||action.contains("pick")||action.contains("check")))return "Farm runs";
        if(action.startsWith("mine")||action.startsWith("chop")||action.startsWith("fish")||action.startsWith("net")||action.startsWith("bait")||action.startsWith("lure")||action.startsWith("harpoon")||action.startsWith("cage"))return "Skilling coach";
        if(action.startsWith("smith")||action.startsWith("smelt"))return "Bank & XP";
        if(action.startsWith("cook")||action.startsWith("spin")||action.startsWith("craft"))return "Action progress";
        if(action.startsWith("jump")||action.startsWith("balance")||action.startsWith("cross")||action.startsWith("leap")||action.startsWith("squeeze"))return "Agility coach";
        return null;
    }
    static boolean objectAction(MenuAction type)
    {return type==MenuAction.GAME_OBJECT_FIRST_OPTION||type==MenuAction.GAME_OBJECT_SECOND_OPTION||type==MenuAction.GAME_OBJECT_THIRD_OPTION||type==MenuAction.GAME_OBJECT_FOURTH_OPTION||type==MenuAction.GAME_OBJECT_FIFTH_OPTION||type==MenuAction.EXAMINE_OBJECT;}
    static int itemId(MenuEntry entry)
    {
        if(entry.isItemOp())return entry.getItemId();
        MenuAction type=entry.getType();
        if(type==MenuAction.GROUND_ITEM_FIRST_OPTION||type==MenuAction.GROUND_ITEM_SECOND_OPTION||type==MenuAction.GROUND_ITEM_THIRD_OPTION||type==MenuAction.GROUND_ITEM_FOURTH_OPTION||type==MenuAction.GROUND_ITEM_FIFTH_OPTION||type==MenuAction.EXAMINE_ITEM_GROUND)return entry.getIdentifier();
        return -1;
    }
}
