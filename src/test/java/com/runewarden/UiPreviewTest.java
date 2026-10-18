/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Visual QA only: all screenshots are labelled mocked-client fixtures, never live account claims. */
public class UiPreviewTest
{
    @Test public void eightRealPagesAndNativeHudRenderOnTheEdt() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.LookAndFeel previous=javax.swing.UIManager.getLookAndFeel();
            try
            {
                assertTrue(net.runelite.client.ui.laf.RuneLiteLAF.setup());
                SidebarLayoutTest.Fixture fixture=new SidebarLayoutTest.Fixture();
                populateAccountFixture(fixture);
                Map<String,JComponent> pages=SidebarLayoutTest.allModules(fixture);
                assertEquals(Arrays.asList("Overview","Harvest runs","Timers","Gathering","Varlamore",
                    "Session loot","Routines & dailies","HUD settings"),new java.util.ArrayList<>(pages.keySet()));
                RunewardenPanel host=new RunewardenPanel();
                pages.forEach(host::addModule);
                JComponent wrapped=host.getWrappedPanel();
                wrapped.setSize(300,900);
                File directory=new File("build/previews");
                assertTrue(directory.isDirectory()||directory.mkdirs());
                for(Map.Entry<String,JComponent> page:pages.entrySet())
                {
                    host.selectModule(page.getKey());
                    for(int pass=0;pass<12;pass++)layout(wrapped);
                    assertTrue(page.getKey()+" must have readable width",page.getValue().getWidth()>=180);
                    assertTrue(page.getKey()+" must contain actual UI",page.getValue().getComponentCount()>0);
                    BufferedImage image=fixtureImage(300,928);
                    Graphics2D graphics=image.createGraphics();
                    try { graphics.translate(0,28); wrapped.printAll(graphics); }
                    finally { graphics.dispose(); }
                    write(directory,page.getKey().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+","-")+".png",image);
                }
                renderHud(directory);
            }
            catch(Exception error) { throw new AssertionError("Unable to render retained UI fixture",error); }
            finally
            {
                try { javax.swing.UIManager.setLookAndFeel(previous); }
                catch(Exception error) { throw new AssertionError(error); }
            }
        });
    }

    private static void renderHud(File directory) throws Exception
    {
        Client client=mock(Client.class);
        Player player=mock(Player.class);
        ItemManager items=mock(ItemManager.class);
        nativeIcons(items);
        RunewardenConfig config=new RunewardenConfig(){};
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getHealthRatio()).thenReturn(-1);
        when(client.getWorld()).thenReturn(349);
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(72);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(45);
        when(client.getEnergy()).thenReturn(7200);
        when(client.getVarpValue(VarPlayerID.SA_ENERGY)).thenReturn(750);
        ItemContainer worn=mock(ItemContainer.class),inventory=mock(ItemContainer.class);
        Item[] equipment=new Item[14]; equipment[3]=new Item(4151,1);
        when(worn.getItems()).thenReturn(equipment);
        when(worn.getItem(3)).thenReturn(equipment[3]);
        when(client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN)).thenReturn(worn);
        ItemComposition whip=mock(ItemComposition.class);
        when(whip.getName()).thenReturn("Abyssal whip");
        when(items.getItemComposition(4151)).thenReturn(whip);
        when(items.getItemStats(4151)).thenReturn(new ItemStats(true,0,0,ItemEquipmentStats.builder().str(82).build()));
        EnumComposition categories=mock(EnumComposition.class),styles=mock(EnumComposition.class);
        StructComposition stance=mock(StructComposition.class);
        when(client.getEnum(EnumID.WEAPON_STYLES)).thenReturn(categories);
        when(categories.getIntValue(anyInt())).thenReturn(1000);
        when(client.getEnum(1000)).thenReturn(styles);
        when(styles.getIntVals()).thenReturn(new int[]{2000});
        when(client.getStructComposition(2000)).thenReturn(stance);
        when(stance.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn("Accurate");
        Item[] contents=new Item[28]; contents[0]=new Item(4151,1); contents[1]=new Item(385,1); contents[27]=new Item(995,250000);
        when(inventory.getItems()).thenReturn(contents);
        when(client.getItemContainer(net.runelite.api.InventoryID.INVENTORY)).thenReturn(inventory);
        HudStyle.context(false,false,0,0);
        CommandDashboardTool dashboard=new CommandDashboardTool(client,items,mock(OverlayManager.class),config);
        InventoryPreviewOverlay preview=new InventoryPreviewOverlay(client,config,items);
        RunewardenResourceOverlay resources=new RunewardenResourceOverlay(client,config,mock(SpriteManager.class));
        dashboard.start(); dashboard.onGameTick(); preview.onGameTick();
        try
        {
            assertEquals(Integer.valueOf(24),dashboard.reading().maxHit.value);
            BufferedImage image=fixtureImage(640,460);
            Graphics2D graphics=image.createGraphics();
            try
            {
                graphics.translate(12,44);
                assertNotNull(resources.render(graphics));
                graphics.translate(0,80);
                assertNotNull(dashboard.overlay().render(graphics));
                graphics.translate(224,0);
                assertEquals(new Dimension(176,272),preview.render(graphics));
            }
            finally { graphics.dispose(); }
            write(directory,"hud.png",image);
        }
        finally { dashboard.stop(); preview.clear(); }
    }

    private static void populateAccountFixture(SidebarLayoutTest.Fixture fixture) throws Exception
    {
        final String profile="rsprofile.visual-qa";
        Client client=fixture.get(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
        net.runelite.client.config.ConfigManager config=fixture.get(net.runelite.client.config.ConfigManager.class);
        when(config.getRSProfileKey()).thenReturn(profile);
        ItemManager items=fixture.get(ItemManager.class);
        nativeIcons(items);

        // Supported immutable observations seed the real views; no live session or plugin is started.
        HarvestClockTool harvest=fixture.get(HarvestClockTool.class);
        harvest.setRunGroupSupport(FarmRunsTool::supportsGroup);
        HarvestSelections selections=(HarvestSelections)field(harvest,"selections");
        HarvestSelections.Session session=selections.bind(profile);
        long now=java.time.Instant.now().getEpochSecond();
        HarvestClockTool.Reading reading=new HarvestClockTool.Reading(true,false,"",Arrays.asList(
            new HarvestClockTool.Harvest("bird-house","Bird houses",net.runelite.client.plugins.timetracking.SummaryState.COMPLETED,0,false),
            new HarvestClockTool.Harvest("herb","Herbs",net.runelite.client.plugins.timetracking.SummaryState.COMPLETED,0,true),
            new HarvestClockTool.Harvest("tree","Trees",net.runelite.client.plugins.timetracking.SummaryState.IN_PROGRESS,now+7200,true)));
        java.lang.reflect.Field observation=HarvestClockTool.class.getDeclaredField("observation");
        observation.setAccessible(true);
        java.lang.reflect.Constructor<?> constructor=observation.getType().getDeclaredConstructors()[0];
        constructor.setAccessible(true); observation.set(harvest,constructor.newInstance(reading,session));
        assertEquals(3,harvest.selectedGroupIds().size());

        FarmPatchPlanner planner=fixture.get(FarmPatchPlanner.class);
        FarmPatchCatalog catalog=fixture.get(FarmPatchCatalog.class);
        FarmPatchPlanner.Route route=new FarmPatchPlanner.Route();
        route.seedItemId=5295; route.quantity=1;
        java.util.List<FarmPatchPlanner.StopReading> stops=new java.util.ArrayList<>();
        for(FarmPatchCatalog.Patch patch:catalog.forGuide("herb"))
        {
            if(!java.util.Set.of("Catherby","Ardougne","Falador").contains(patch.region))continue;
            route.stops.add(patch.id);
            stops.add(new FarmPatchPlanner.StopReading(patch.id,patch.region,
                stops.isEmpty()?"Harvestable · observed":"Not observed in this session","",true,false,null));
        }
        assertTrue(stops.size()>=3);
        FarmPatchPlanner.RunType type=FarmPatchPlanner.runTypes().stream().filter(value->"herb".equals(value.id)).findFirst().get();
        java.util.List<FarmPatchPlanner.SupplyReading> supplies=Arrays.asList(
            new FarmPatchPlanner.SupplyReading(5295,"Ranarr seed",route.stops.size(),8),
            new FarmPatchPlanner.SupplyReading(21483,"Ultracompost",route.stops.size(),3),
            new FarmPatchPlanner.SupplyReading(952,"Spade",1,1));
        set(planner,"profile",profile); set(planner,"started",true); set(planner,"activeSession",true);
        set(planner,"choices",Arrays.asList(new FarmPatchPlanner.CropChoice(5295,"Ranarr seed",32,1),
            new FarmPatchPlanner.CropChoice(5296,"Toadflax seed",38,1)));
        set(planner,"run",new FarmPatchPlanner.RunReading(profile,1,type,route,stops.get(0).id,stops.get(0).label,
            "Ready to prepare","Review supplies, then start your selected route.",stops,supplies));

        RunLootTool loot=fixture.get(RunLootTool.class);
        set(loot,"reading",new RunLootTool.Reading(true,profile,1,true,900000,Arrays.asList(
            new RunLootTool.LootItem(4151,"Abyssal whip",1,1500000,items.getImage(4151)),
            new RunLootTool.LootItem(995,"Coins",25000,1,null)),java.util.Map.of("NPC · Abyssal demon",12L),45000));

        RoutinePlannerTool routines=fixture.get(RoutinePlannerTool.class);
        set(routines,"profile",profile); set(routines,"claimsProfile",profile);
        set(routines,"claims",Arrays.asList(
            new ClaimableRoutineCatalog.Claim("battlestaves","Daily battlestaves","Zaff's shop, Varrock",
                "120 battlestaves available · 7,000 GP each","https://oldschool.runescape.wiki/w/Zaff",null,
                ClaimableRoutineCatalog.Status.NEEDS_PAYMENT,120,120,840000,java.util.Set.of(995)),
            new ClaimableRoutineCatalog.Claim("sand","Buckets of sand","Bert, Yanille",
                "84 buckets ready to collect","https://oldschool.runescape.wiki/w/Bert",null,
                ClaimableRoutineCatalog.Status.AVAILABLE,84,84,0,java.util.Set.of())));
    }

    private static void nativeIcons(ItemManager items) throws Exception
    {
        net.runelite.client.callback.ClientThread thread=mock(net.runelite.client.callback.ClientThread.class);
        net.runelite.client.util.AsyncBufferedImage loading=new net.runelite.client.util.AsyncBufferedImage(thread,36,32,BufferedImage.TYPE_INT_ARGB);
        when(items.getImage(anyInt())).thenReturn(loading);
        File cached=new File(System.getProperty("runewarden.previewAssets","../../v4-assets"),"whip.png");
        if(!cached.isFile())return;
        BufferedImage source=ImageIO.read(cached);
        net.runelite.client.util.AsyncBufferedImage whip=new net.runelite.client.util.AsyncBufferedImage(thread,36,32,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=whip.createGraphics();
        try { graphics.drawImage(source,(36-source.getWidth())/2,(32-source.getHeight())/2,null); }
        finally { graphics.dispose(); }
        when(items.getImage(4151)).thenReturn(whip);
        when(items.getImage(4151,1,false)).thenReturn(whip);
    }

    private static Object field(Object object,String name) throws Exception
    {
        java.lang.reflect.Field field=object.getClass().getDeclaredField(name);
        field.setAccessible(true); return field.get(object);
    }
    private static void set(Object object,String name,Object value) throws Exception
    {
        java.lang.reflect.Field field=object.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(object,value);
    }

    private static BufferedImage fixtureImage(int width,int height)
    {
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();
        try
        {
            graphics.setColor(new Color(20,20,21)); graphics.fillRect(0,0,width,height);
            graphics.setColor(new Color(224,190,113));
            graphics.drawString("VISUAL QA FIXTURE · mocked client state",8,18);
        }
        finally { graphics.dispose(); }
        return image;
    }
    private static void write(File directory,String name,BufferedImage image) throws Exception
    { assertTrue(ImageIO.write(image,"png",new File(directory,name))); }
    private static void layout(Container root)
    { root.doLayout();for(Component child:root.getComponents())if(child instanceof Container)layout((Container)child); }
}
