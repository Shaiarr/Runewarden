/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ContainerHudTest
{
    @Test public void checkedFishAcceptsNativeMarkupAndRejectsMalformedOrOverCapacityCounts()
    {
        assertEquals(0,ContainerHudRules.checkedFish("The barrel is empty."));
        assertEquals(28,ContainerHudRules.checkedFish("The barrel contains: 12 x raw sharks,<br>16 x raw lobsters"));
        assertEquals(5,ContainerHudRules.checkedFish("<col=ffffff>The barrel contains: 5 x raw Karambwan.</col>"));
        assertEquals(-1,ContainerHudRules.checkedFish("The barrel contains: 29 x raw sharks"));
        assertEquals(-1,ContainerHudRules.checkedFish("The barrel contains: 27 x raw sharks, 2 x raw lobsters"));
        assertEquals(-1,ContainerHudRules.checkedFish("The barrel contains: 0 x raw sharks"));
        assertEquals(-1,ContainerHudRules.checkedFish("Another player says 28 x raw sharks"));
        assertEquals(-1,ContainerHudRules.checkedFish("The barrel contains: 5 x raw sharks, nonsense"));
    }
    @Test public void allSevenPlankTypesUseNativeVarbitsAndCapacityIsCombined()
    {
        assertEquals(7,ContainerHudRules.Plank.values().length);assertEquals(28,ContainerHudRules.totalPlanks(new int[]{4,4,4,4,4,4,4}));
        assertEquals(-1,ContainerHudRules.totalPlanks(new int[]{28,1,0,0,0,0,0}));assertEquals(-1,ContainerHudRules.totalPlanks(new int[]{-1,0,0,0,0,0,0}));
        assertEquals(0,ContainerHudRules.totalPlanks(new int[7]));
        Fixture f=new Fixture();f.inventory(new Item(ItemID.PLANK_SACK,1));for(ContainerHudRules.Plank plank:ContainerHudRules.Plank.values())when(f.f.client.getVarbitValue(plank.varbit)).thenReturn(4);
        f.tool.onGameTick();ContainerHudTool.Container sack=f.tool.snapshot().containers.get(0);assertEquals(28,sack.count);assertEquals(7,sack.contents.size());assertEquals(ContainerHudTool.Location.INVENTORY,sack.location);
        verify(f.items).getImage(ItemID.PLANK_SACK);for(ContainerHudRules.Plank plank:ContainerHudRules.Plank.values())verify(f.items).getImage(plank.item);
    }
    @Test public void barrelNeedsANewNativeCheckResponseAndMenuClicksAloneDoNotSupplyCounts()
    {
        Fixture f=new Fixture();f.inventory(new Item(ItemID.FISH_BARREL_OPEN,1));f.tool.onGameTick();assertEquals(-1,f.barrel().count);
        Widget response=mock(Widget.class);when(response.isHidden()).thenReturn(false);when(response.getText()).thenReturn("The barrel contains: 12 x raw sharks");when(f.f.client.getWidget(193,2)).thenReturn(response);
        f.menu("Check",ItemID.FISH_BARREL_OPEN);f.tool.onGameTick();assertEquals(-1,f.barrel().count);
        WidgetLoaded loaded=new WidgetLoaded();loaded.setGroupId(193);f.tool.onWidgetLoaded(loaded);f.tool.onGameTick();assertEquals(12,f.barrel().count);
        f.menu("Fill",ItemID.FISH_BARREL_OPEN);f.tool.onGameTick();assertEquals(12,f.barrel().count);
        f.menu("Empty",ItemID.FISH_BARREL_OPEN);assertEquals(-1,f.barrel().count);
    }
    @Test public void verifiedFillUsesRemovedFishAndUnknownPartialContentsRemainUnknown()
    {
        FishBarrelReading reading=new FishBarrelReading();Map<Integer,Long> raw=Map.of(ItemID.FISH_BARREL_OPEN,1L,ItemID.RAW_SHARK,5L);
        reading.observe(true,true,raw,1,0);reading.menu("Fill",ItemID.FISH_BARREL_OPEN,1);reading.observe(true,true,Map.of(ItemID.FISH_BARREL_OPEN,1L),1,0);assertEquals(-1,reading.count());
        reading.menu("Check",ItemID.FISH_BARREL_OPEN,2);reading.widgetLoaded(193,2);reading.check("The barrel is empty.",2);
        reading.observe(true,true,raw,3,0);reading.menu("Fill",ItemID.FISH_BARREL_OPEN,3);reading.observe(true,true,Map.of(ItemID.FISH_BARREL_OPEN,1L),3,0);assertEquals(5,reading.count());
    }
    @Test public void liveCatchesAccountForFishEnteringInventoryAndCookingAmbiguity()
    {
        FishBarrelReading reading=known(27);Map<Integer,Long> raw=Map.of(ItemID.FISH_BARREL_OPEN,1L,ItemID.RAW_SHARK,1L);
        reading.chat("You catch a shark.",true,false);reading.chat("Rada's blessing enabled you to catch an extra fish.",true,false);reading.observe(true,true,raw,2,0);reading.tick(0);assertEquals(28,reading.count());
        reading=known(5);reading.chat("You catch a shark.",true,false);reading.tick(0);assertEquals(6,reading.count());
        reading.chat("You catch a shark.",true,false);reading.cookingChanged();reading.tick(100);assertEquals(-1,reading.count());
        assertFalse(ContainerHudRules.caughtFish("You catch some karambwanji."));assertFalse(ContainerHudRules.rawFish(ItemID.TBWT_RAW_KARAMBWANJI));
    }
    @Test public void nativeCheckAndFullReceiptsDoNotCountTheSameCatchTwice()
    {
        FishBarrelReading reading=known(27);reading.chat("You catch a shark.",true,false);reading.chat("The barrel is full. It may be emptied at a bank.",false,false);reading.tick(0);assertEquals(28,reading.count());
        reading=known(5);reading.chat("You catch a shark.",true,false);reading.menu("Check",ItemID.FISH_BARREL_OPEN,2);reading.widgetLoaded(193,2);reading.check("The barrel contains: 6 x raw sharks",2);reading.tick(0);assertEquals(6,reading.count());
    }
    @Test public void onlyNativeGameMessagesConfirmFullOrBankEmptyAndPrivateChatIsIgnored()
    {
        Fixture f=new Fixture();f.inventory(new Item(ItemID.FISH_BARREL_OPEN,1));f.tool.onGameTick();f.chat(ChatMessageType.PUBLICCHAT,"The barrel is full. It may be emptied at a bank.");assertEquals(-1,f.barrel().count);
        f.chat(ChatMessageType.GAMEMESSAGE,"The barrel is full. It may be emptied at a bank.");assertEquals(28,f.barrel().count);
        f.chat(ChatMessageType.GAMEMESSAGE,"You empty all of your containers into the bank.");assertEquals(28,f.barrel().count);
        f.bank();f.chat(ChatMessageType.GAMEMESSAGE,"You empty all of your containers into the bank.");assertEquals(0,f.barrel().count);
        f.chat(ChatMessageType.GAMEMESSAGE,"Your bank could not hold your fish.");assertEquals(-1,f.barrel().count);
    }
    @Test public void bankedBarrelRemainsUnknownAndPlankCountsDoNotBecomeAnInventoryOwnershipClaim()
    {
        Fixture f=new Fixture();f.bank();ItemContainer bank=mock(ItemContainer.class);when(bank.getItems()).thenReturn(new Item[]{new Item(ItemID.PLANK_SACK,1),new Item(ItemID.FISH_BARREL_CLOSED,1)});when(f.f.client.getItemContainer(InventoryID.BANK)).thenReturn(bank);
        when(f.f.client.getVarbitValue(ContainerHudRules.Plank.OAK.varbit)).thenReturn(12);f.tool.onGameTick();assertEquals(2,f.tool.snapshot().containers.size());
        ContainerHudTool.Container barrel=f.barrel(),sack=f.tool.snapshot().containers.stream().filter(row->row.name.equals("Plank sack")).findFirst().get();assertEquals(-1,barrel.count);assertEquals(ContainerHudTool.Location.BANK,barrel.location);assertEquals(12,sack.count);assertEquals(ContainerHudTool.Location.BANK,sack.location);
        when(f.f.client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER)).thenReturn(null);f.tool.onGameTick();assertTrue(f.tool.snapshot().containers.isEmpty());
    }
    @Test public void unrelatedVarbitsDoNotReadContainersAndProfileOrLogoutDiscardsPreviousState()
    {
        Fixture f=new Fixture();f.inventory(new Item(ItemID.PLANK_SACK,1));f.tool.onGameTick();clearInvocations(f.f.client);
        VarbitChanged event=new VarbitChanged();event.setVarbitId(999999);f.tool.onVarbitChanged(event);verify(f.f.client,never()).getItemContainer(any(InventoryID.class));
        f.f.profile.set("rsprofile.other");assertTrue(f.tool.snapshot().containers.isEmpty());when(f.f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);f.tool.onGameTick();assertTrue(f.tool.snapshot().containers.isEmpty());
    }
    @Test public void unknownAndFullContainersRenderInTheMovableBlackGoldHud() throws Exception
    {
        ContainerHudTool.Snapshot state=new ContainerHudTool.Snapshot("rsprofile.a",List.of(
            new ContainerHudTool.Container("Fish barrel",-1,ContainerHudTool.Location.INVENTORY,null,List.of()),
            new ContainerHudTool.Container("Plank sack",28,ContainerHudTool.Location.INVENTORY,null,List.of(new ContainerHudTool.Content("Oak",14,null),new ContainerHudTool.Content("Mahogany",14,null)))));
        BufferedImage image=new BufferedImage(300,215,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.translate(10,10);assertEquals(new Dimension(264,179),ContainerHudOverlay.paint(g,HudPreferences.Style.defaults(90),state));}finally{g.dispose();}
        File output=new File("build/reports/container-hud.png");output.getParentFile().mkdirs();ImageIO.write(image,"png",output);
        Fixture f=new Fixture();assertTrue(f.tool.overlay().isMovable());f.tool.setHudLocked(true);assertFalse(f.tool.overlay().isMovable());
    }
    private static FishBarrelReading known(int count)
    {FishBarrelReading reading=new FishBarrelReading();reading.observe(true,true,Map.of(ItemID.FISH_BARREL_OPEN,1L),1,0);reading.menu("Check",ItemID.FISH_BARREL_OPEN,1);reading.widgetLoaded(193,1);reading.check("The barrel contains: "+count+" x raw sharks",1);return reading;}
    private static final class Fixture
    {
        final ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();final ItemManager items=mock(ItemManager.class);final ItemContainer bag=mock(ItemContainer.class);final ContainerHudTool tool;
        Fixture(){when(bag.getItems()).thenReturn(new Item[0]);when(f.client.getItemContainer(InventoryID.INVENTORY)).thenReturn(bag);tool=new ContainerHudTool(f.client,f.config,f.store,mock(RunewardenConfig.class),items);tool.start();tool.onGameTick();}
        void inventory(Item... items){when(bag.getItems()).thenReturn(items);}
        void menu(String option,int item){MenuOptionClicked event=mock(MenuOptionClicked.class);when(event.getMenuOption()).thenReturn(option);when(event.getItemId()).thenReturn(item);tool.onMenuOptionClicked(event);}
        void bank(){Widget bank=mock(Widget.class);when(bank.isHidden()).thenReturn(false);when(f.client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER)).thenReturn(bank);}
        void chat(ChatMessageType type,String text){tool.onChatMessage(new ChatMessage(null,type,"",text,"",0));}
        ContainerHudTool.Container barrel(){return tool.snapshot().containers.stream().filter(row->row.name.equals("Fish barrel")).findFirst().get();}
    }
}
