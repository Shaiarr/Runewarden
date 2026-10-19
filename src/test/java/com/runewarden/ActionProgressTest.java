/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.runelite.api.Animation;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ActionProgressTest
{
    @Test public void nativeAnimationUsesClientCyclesAndClonesFrameLengths()
    {
        Animation animation=mock(Animation.class);int[] frames={5,10,15};when(animation.getFrameLengths()).thenReturn(frames);
        ActionProgressRules.Timing timing=ActionProgressRules.timing(animation);frames[0]=999;
        assertEquals(600,timing.durationMillis);assertEquals(0,timing.elapsedAtFrame(0));assertEquals(100,timing.elapsedAtFrame(1));assertEquals(300,timing.elapsedAtFrame(2));assertEquals(600,timing.elapsedAtFrame(99));
        when(animation.getFrameLengths()).thenReturn(null);when(animation.getDuration()).thenReturn(60);
        assertEquals(1200,ActionProgressRules.timing(animation).durationMillis);
        when(animation.getDuration()).thenReturn(0);assertNull(ActionProgressRules.timing(animation));
        when(animation.getDuration()).thenReturn(Integer.MAX_VALUE);assertNull(ActionProgressRules.timing(animation));
    }
    @Test public void cachedMayaAnimationUsesItsNativeClientTickCountInsteadOfClassicFrameLengths()
    {
        Animation animation=mock(Animation.class);when(animation.isMayaAnim()).thenReturn(true);
        when(animation.getFrameLengths()).thenReturn(null);when(animation.getNumFrames()).thenReturn(75);
        // This is the native cached-animation representation: no classic keyframe durations.
        assertEquals(0,animation.getDuration());ActionProgressRules.Timing timing=ActionProgressRules.timing(animation);
        assertNotNull(timing);assertEquals(1500,timing.durationMillis);assertEquals(500,timing.elapsedAtFrame(25));
        when(animation.getNumFrames()).thenReturn(0);assertNull(ActionProgressRules.timing(animation));
    }
    @Test public void sourcedSpellsAndStandardRecipesRespectToolAndFirstActionExceptions()
    {
        assertEquals(3000,ActionProgressRules.spellCycle(AnimationID.HUMAN_CASTHIGHLVLALCHEMY));
        assertEquals(1800,ActionProgressRules.spellCycle(AnimationID.HUMAN_CASTLOWLVLALCHEMY));
        assertEquals(1800,ActionProgressRules.spellCycle(AnimationID.HUMAN_CASTSUPERHEATITEM));
        assertEquals(1200,cycle("herblore:super_attack_3",AnimationID.HUMAN_HERBING_VIAL,true));
        assertEquals(0,cycle("herblore:ranarr_weed",AnimationID.HUMAN_HERBING_VIAL,true));
        assertEquals(1200,cycle("crafting:diamond",AnimationID.HUMAN_DIAMONDCUTTING,true));
        assertEquals(0,cycle("crafting:unpowered_orb",AnimationID.HUMAN_DIAMONDCUTTING,true));
        assertEquals(0,ActionProgressRules.recipeCycle(recipe("crafting:diamond"),AnimationID.HUMAN_DIAMONDCUTTING,Set.of(net.runelite.api.gameval.ItemID.JEWELLERS_CHISEL),true));
        assertEquals(1200,cycle("crafting:air_battlestaff",AnimationID.HUMAN_BATTLESTAFF_CRAFTING,true));
        assertEquals(1800,cycle("crafting:green_dhide_body",AnimationID.HUMAN_LEATHER_CRAFTING,true));
        assertEquals(1800,cycle("crafting:unpowered_orb",AnimationID.HUMAN_GLASSBLOWING,true));
        assertEquals(1800,cycle("crafting:diamond_bracelet",AnimationID.HUMAN_FURNACE,true));
        assertEquals(1800,cycle("fletching:magic_longbow_u",AnimationID.HUMAN_FLETCHING,true));
        assertEquals(1200,ActionProgressRules.recipeCycle(recipe("fletching:magic_longbow_u"),AnimationID.HUMAN_FLETCHING,Set.of(net.runelite.api.gameval.ItemID.FLETCHING_KNIFE),false));
        assertEquals(1800,ActionProgressRules.recipeCycle(recipe("fletching:magic_longbow_u"),AnimationID.HUMAN_FLETCHING,Set.of(net.runelite.api.gameval.ItemID.FLETCHING_KNIFE),true));
        assertEquals(1200,cycle("fletching:magic_longbow",AnimationID.STRINGING_MAGIC_LONGBOW,true));
        assertEquals(1800,cycle("cooking:shark",AnimationID.HUMAN_COOKING,true));
        assertEquals(2400,cycle("cooking:shark",AnimationID.HUMAN_COOKING,false));
        assertEquals(0,cycle("cooking:cooked_karambwan",AnimationID.HUMAN_COOKING,true));
        assertEquals(0,cycle("cooking:jug_of_wine",AnimationID.HUMAN_COOKING,true));
        assertEquals(0,cycle("crafting:bow_string",AnimationID.HUMAN_HERBING_VIAL,true));
        assertNull(ActionProgressRules.label(999999));
    }
    @Test public void cleaningCountsOnlyConfirmedTransformationsAndDoesNotInventAnInitialEta()
    {
        ActionRecipeCatalog.Recipe recipe=recipe("herblore:ranarr_weed");ActionBatchProgress batch=new ActionBatchProgress();batch.select(recipe);
        Map<Integer,Long> before=inputs(recipe,10),after=after(recipe,before,3);
        batch.inventory(before,1,true);batch.arm(1000);assertEquals(-1,batch.estimatedMillis());
        batch.inventory(after,1,true);batch.experience(Math.round(recipe.xp*3),1,true);assertEquals(0,batch.confirm(3,2200));
        assertEquals(3,batch.confirm(4,2800));assertEquals(3,batch.completed());assertEquals(7,batch.remaining());assertEquals(4200,batch.estimatedMillis());
        Map<Integer,Long> done=after(recipe,after,7);batch.inventory(done,5,true);batch.experience(Math.round(recipe.xp*7),5,true);assertEquals(7,batch.confirm(8,5200));
        assertEquals(0,batch.estimatedMillis());assertEquals(1,batch.fraction(),0.00001);
    }
    @Test public void wrongXpDroppingAndBankTransfersNeverCountAsCompletedActions()
    {
        ActionRecipeCatalog.Recipe recipe=recipe("herblore:ranarr_weed");Map<Integer,Long> before=inputs(recipe,5),after=after(recipe,before,1);
        ActionBatchProgress batch=new ActionBatchProgress();batch.select(recipe);batch.inventory(before,1,true);batch.arm(1000);
        batch.inventory(after,1,true);batch.experience(100,1,true);assertEquals(0,batch.confirm(4,2800));
        batch.select(recipe);batch.inventory(before,5,true);batch.inventory(Map.of(),5,true);batch.experience(Math.round(recipe.xp*5),5,true);assertEquals(0,batch.confirm(8,5200));
        batch.inventory(after,9,false);assertFalse(batch.visible());assertEquals(0,batch.completed());assertEquals(-1,batch.estimatedMillis());
    }
    @Test public void discardedMaterialsDoNotTurnAPartlyFinishedBatchIntoACompletedOne()
    {
        ActionRecipeCatalog.Recipe recipe=recipe("herblore:ranarr_weed");Map<Integer,Long> before=inputs(recipe,10),partial=after(recipe,before,3);
        ActionBatchProgress batch=new ActionBatchProgress();batch.select(recipe);batch.inventory(before,1,true);batch.arm(1000);batch.inventory(partial,1,true);batch.experience(Math.round(recipe.xp*3),1,true);batch.confirm(4,2800);
        Map<Integer,Long> dropped=new LinkedHashMap<>(partial);dropped.remove(recipe.ingredients.get(0).itemId);batch.inventory(dropped,5,true);
        assertEquals(10,batch.total());assertEquals(0.3,batch.fraction(),0.00001);assertEquals(-1,batch.estimatedMillis());
    }
    @Test public void automaticRecipeRecognitionAcceptsEitherEventOrderAndRejectsXpOnly()
    {
        ActionRecipeCatalog.Recipe recipe=recipe("crafting:diamond");Map<Integer,Long> before=inputs(recipe,10),after=after(recipe,before,2);
        for(boolean xpFirst:new boolean[]{true,false})
        {
            ActionRecipeDetector detector=new ActionRecipeDetector();detector.inventory(before,1,1000);
            if(xpFirst)detector.experience(recipe.skill,Math.round(recipe.xp*2),2,1600);
            detector.inventory(after,2,1600);if(!xpFirst)detector.experience(recipe.skill,Math.round(recipe.xp*2),2,1600);
            assertNull(detector.confirm(4));ActionRecipeDetector.Match match=detector.confirm(5);assertNotNull(match);assertEquals(recipe.id,match.recipe.id);assertEquals(2,match.count);
            ActionBatchProgress batch=new ActionBatchProgress();batch.adopt(match,5,3400);assertEquals(2,batch.completed());assertEquals(8,batch.remaining());
        }
        ActionRecipeDetector detector=new ActionRecipeDetector();detector.inventory(before,1,1000);detector.experience(recipe.skill,1000,1,1000);assertNull(detector.confirm(4));
    }
    @Test public void localHighAlchemyIsAThreeSecondCycleAndForeignAnimationsAreIgnored()
    {
        Fixture f=new Fixture();f.setAnimation(AnimationID.HUMAN_CASTHIGHLVLALCHEMY);f.event();
        ActionProgressTool.Snapshot reading=f.tool.snapshot();assertEquals("High Level Alchemy",reading.title);assertEquals("Cast cycle",reading.basis);assertTrue(reading.exactCycle);assertEquals(3000,reading.cycleEnd-reading.cycleStart);
        AnimationChanged other=new AnimationChanged();other.setActor(mock(NPC.class));f.tool.onAnimationChanged(other);assertSame(reading,f.tool.snapshot());
        f.setAnimation(-1);f.tick(2);assertTrue(f.tool.snapshot().cycle());f.tick(3);assertFalse(f.tool.snapshot().cycle());
    }
    @Test public void nativeIdleAnimationEventDoesNotCancelTheObservedHighAlchemyCooldown()
    {
        Fixture f=new Fixture();f.setAnimation(AnimationID.HUMAN_CASTHIGHLVLALCHEMY);f.event();long end=f.tool.snapshot().cycleEnd;
        f.f.now.addAndGet(600);f.setAnimation(-1);f.event();
        assertTrue("The actual AnimationChanged(-1) event must preserve the remaining cast time",f.tool.snapshot().cycle());
        assertEquals(end,f.tool.snapshot().cycleEnd);assertEquals(2400,end-f.f.now.get());
        f.tick(3);assertTrue(f.tool.snapshot().cycle());f.tick(1);assertFalse(f.tool.snapshot().cycle());
    }
    @Test public void nativeIdleEventStillEndsAnAnimationOnlyBar()
    {
        Fixture f=new Fixture();Animation animation=mock(Animation.class);when(animation.getFrameLengths()).thenReturn(new int[]{30,30});
        when(f.f.client.loadAnimation(AnimationID.HUMAN_GLASSBLOWING)).thenReturn(animation);f.setAnimation(AnimationID.HUMAN_GLASSBLOWING);f.event();assertTrue(f.tool.snapshot().cycle());
        f.f.now.addAndGet(200);f.setAnimation(-1);f.event();assertFalse(f.tool.snapshot().cycle());
    }
    @Test public void unknownActionUsesNativeAnimationWithAnExplicitAnimationBasis()
    {
        Fixture f=new Fixture();Animation animation=mock(Animation.class);when(animation.getFrameLengths()).thenReturn(new int[]{10,20,30});when(f.f.client.loadAnimation(AnimationID.HUMAN_HERBING_VIAL)).thenReturn(animation);
        f.setAnimation(AnimationID.HUMAN_HERBING_VIAL);when(f.f.player.getAnimationFrame()).thenReturn(1);f.event();
        ActionProgressTool.Snapshot reading=f.tool.snapshot();assertEquals("Animation",reading.basis);assertFalse(reading.exactCycle);assertEquals(1200,reading.cycleEnd-reading.cycleStart);assertEquals(f.f.now.get(),reading.cycleStart);
        f.setAnimation(999999);f.event();assertFalse(f.tool.snapshot().cycle());verify(f.f.client,never()).loadAnimation(999999);
    }
    @Test public void nativeCookingChoiceUsesSourcedCyclesWithoutInventingCompletedOutput()
    {
        Fixture f=new Fixture();ActionRecipeCatalog.Recipe shark=recipe("cooking:shark");f.inventory(inputs(shark,10));
        f.f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,shark.id,shark.name,null,shark.id,Skill.COOKING,0));
        f.tool.onGameTick();f.choose("Cook",shark.ingredients.get(0).itemId);f.setAnimation(AnimationID.HUMAN_COOKING);f.event();
        assertEquals("Standard cycle",f.tool.snapshot().basis);assertEquals(1800,f.tool.snapshot().cycleEnd-f.tool.snapshot().cycleStart);assertEquals(0,f.tool.snapshot().completed);
        f.inventory(inputs(shark,9));f.inventoryEvent();f.event();assertEquals(2400,f.tool.snapshot().cycleEnd-f.tool.snapshot().cycleStart);
    }
    @Test public void cleanClickArmsBatchAndAutomaticRecognitionWorksWithoutAnySelectedGuide()
    {
        Fixture f=new Fixture();ActionRecipeCatalog.Recipe recipe=recipe("herblore:ranarr_weed");Map<Integer,Long> before=inputs(recipe,10);f.inventory(before);f.tool.onGameTick();
        MenuOptionClicked clean=mock(MenuOptionClicked.class);when(clean.isItemOp()).thenReturn(true);when(clean.getMenuOption()).thenReturn("Clean");when(clean.getItemId()).thenReturn(recipe.ingredients.get(0).itemId);f.tool.onMenuOptionClicked(clean);
        assertTrue(f.tool.snapshot().batch());assertEquals(-1,f.tool.snapshot().batchEta);assertFalse(f.tool.snapshot().cycle());
        f.inventory(after(recipe,before,3));f.inventoryEvent();f.xp(recipe.skill,(int)Math.round(recipe.xp*3));f.tick(3);
        assertEquals(3,f.tool.snapshot().completed);assertEquals(7,f.tool.snapshot().remaining);assertFalse(f.tool.snapshot().cycle());
        Fixture automatic=new Fixture();ActionRecipeCatalog.Recipe diamond=recipe("crafting:diamond");before=inputs(diamond,10);automatic.inventory(before);automatic.tool.onGameTick();automatic.inventory(after(diamond,before,2));automatic.inventoryEvent();automatic.xp(diamond.skill,215);automatic.tick(3);
        assertEquals(diamond.name,automatic.tool.snapshot().batchTitle);assertEquals(2,automatic.tool.snapshot().completed);assertEquals(8,automatic.tool.snapshot().remaining);
    }
    @Test public void bankCombatProfileSwitchAndLogoutClearThePreviousAccountsDisplay()
    {
        Fixture f=new Fixture();f.setAnimation(AnimationID.HUMAN_CASTHIGHLVLALCHEMY);f.event();f.f.profile.set("rsprofile.other");assertFalse(f.tool.snapshot().cycle());f.tool.onGameTick();
        Widget bank=mock(Widget.class);when(bank.isHidden()).thenReturn(false);when(f.f.client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER)).thenReturn(bank);f.tool.onGameTick();assertFalse(f.tool.snapshot().cycle());
        when(f.f.client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER)).thenReturn(null);NPC enemy=mock(NPC.class);when(enemy.getCombatLevel()).thenReturn(100);when(f.f.player.getInteracting()).thenReturn(enemy);f.tool.onGameTick();assertFalse(f.tool.snapshot().cycle());
        when(f.f.player.getInteracting()).thenReturn(null);f.event();assertTrue(f.tool.snapshot().cycle());when(f.f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);f.tool.onGameTick();assertFalse(f.tool.snapshot().cycle());
    }
    @Test public void overlayShowsFillingBarsAndSecondsForCastsAndCleaningInEveryDisplayMode() throws Exception
    {
        BufferedImage image=new BufferedImage(850,220,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        ActionProgressTool.Snapshot state=new ActionProgressTool.Snapshot("rsprofile.a","High Level Alchemy","Cast cycle",1000,4000,2500,true,"Clean grimy ranarr weed",7,21,9000);
        try
        {
            int column=0;for(ActionProgressTool.Display display:ActionProgressTool.Display.values())
            {
                ActionProgressTool.Preferences prefs=new ActionProgressTool.Preferences();prefs.display=display;
                Graphics2D pane=(Graphics2D)g.create();pane.translate(10+column++*280,20);
                try{assertEquals(new Dimension(264,141),ActionProgressOverlay.paint(pane,HudPreferences.Style.defaults(90),state,prefs,2500));}finally{pane.dispose();}
            }
        }
        finally{g.dispose();}
        File output=new File("build/reports/action-progress-modes.png");output.getParentFile().mkdirs();ImageIO.write(image,"png",output);
        assertEquals("1.5s",ActionProgressOverlay.seconds(1500,false));assertEquals("~1.5s",ActionProgressOverlay.seconds(1500,true));
    }
    @Test public void unidentifiedAnimationDoesNotInventAnActionLabelOrProgressHud()
    {
        Fixture f=new Fixture();Animation animation=mock(Animation.class);when(animation.getFrameLengths()).thenReturn(new int[]{10,20,30});
        when(f.f.client.loadAnimation(123456)).thenReturn(animation);f.setAnimation(123456);when(f.f.player.getAnimationFrame()).thenReturn(99);f.event();
        assertFalse(f.tool.snapshot().cycle());assertEquals("",f.tool.snapshot().title);
        Graphics2D graphics=new BufferedImage(400,300,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try{assertNull(f.tool.overlay().render(graphics));}finally{graphics.dispose();}
        verify(f.f.client,never()).loadAnimation(123456);
    }
    @Test public void cachedNativeActionRendersAFillingBarAndCountdownWithNoSelectedRun()
    {
        Fixture f=new Fixture();Animation animation=mock(Animation.class);when(animation.isMayaAnim()).thenReturn(true);
        when(animation.getNumFrames()).thenReturn(75);when(f.f.client.loadAnimation(AnimationID.HUMAN_GLASSBLOWING)).thenReturn(animation);
        f.setAnimation(AnimationID.HUMAN_GLASSBLOWING);f.event();f.f.now.addAndGet(500);when(f.f.player.getAnimationFrame()).thenReturn(25);f.tool.onClientTick();
        ActionProgressTool.Snapshot reading=f.tool.snapshot();assertEquals(1500,reading.cycleEnd-reading.cycleStart);
        assertEquals(1000,reading.cycleEnd-f.f.now.get());assertEquals("Animation",reading.basis);assertFalse(reading.exactCycle);
        assertNull(f.f.session.snapshot().context);
        BufferedImage image=new BufferedImage(400,150,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
        try{assertEquals(new Dimension(264,83),f.tool.overlay().render(graphics));assertNotEquals(image.getRGB(60,67),image.getRGB(240,67));}finally{graphics.dispose();}
    }
    @Test public void clientTickFrameWrapKeepsARepeatedShortActionVisibleBetweenGameTicks()
    {
        Fixture f=new Fixture();Animation animation=mock(Animation.class);when(animation.getFrameLengths()).thenReturn(new int[]{10,10,10});
        when(f.f.client.loadAnimation(AnimationID.HUMAN_GLASSBLOWING)).thenReturn(animation);f.setAnimation(AnimationID.HUMAN_GLASSBLOWING);f.event();long first=f.tool.snapshot().cycleStart;
        f.f.now.addAndGet(580);when(f.f.player.getAnimationFrame()).thenReturn(2);f.tool.onClientTick();
        f.f.now.addAndGet(40);when(f.f.player.getAnimationFrame()).thenReturn(0);f.tool.onClientTick();
        assertTrue("The same animation restarted even though there was no AnimationChanged event",f.tool.snapshot().cycle());
        assertEquals(first+620,f.tool.snapshot().cycleStart);assertEquals(600,f.tool.snapshot().cycleEnd-f.tool.snapshot().cycleStart);
        f.f.now.addAndGet(580);when(f.f.player.getAnimationFrame()).thenReturn(2);f.tool.onGameTick();
        assertTrue("The next game-tick sample must not expire the restarted loop",f.tool.snapshot().cycle());
        verify(f.f.client,times(1)).loadAnimation(AnimationID.HUMAN_GLASSBLOWING);
    }
    @Test public void nativeCleanMenuDoesNotRequireTheOptionalItemOperationFlag()
    {
        Fixture f=new Fixture();ActionRecipeCatalog.Recipe recipe=recipe("herblore:ranarr_weed");f.inventory(inputs(recipe,10));f.tool.onGameTick();
        MenuOptionClicked clean=mock(MenuOptionClicked.class);Widget slot=mock(Widget.class);when(slot.getItemId()).thenReturn(recipe.ingredients.get(0).itemId);
        when(clean.getMenuOption()).thenReturn("<col=ffffff>Clean</col>");when(clean.getItemId()).thenReturn(-1);when(clean.getWidget()).thenReturn(slot);
        f.tool.onMenuOptionClicked(clean);assertTrue(f.tool.snapshot().batch());assertEquals(10,f.tool.snapshot().remaining);
    }
    @Test public void nativeSpellImageIsCachedOutsideRenderAndSurvivesItsIdleCooldown()
    {
        Fixture f=new Fixture();BufferedImage icon=solid(24,24,0xffd040ff);when(f.sprites.getSprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY,0)).thenReturn(icon);
        f.setAnimation(AnimationID.HUMAN_CASTHIGHLVLALCHEMY);f.event();
        assertEquals(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY,f.tool.snapshot().cycleSpriteId);assertEquals(0,f.tool.snapshot().cycleItemId);assertSame(icon,f.tool.snapshot().cycleIcon);
        f.setAnimation(-1);f.event();assertSame(icon,f.tool.snapshot().cycleIcon);
        BufferedImage image=new BufferedImage(300,150,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try{for(int i=0;i<10;i++)assertNotNull(f.tool.overlay().render(g));}finally{g.dispose();}
        assertEquals(0xffd040ff,image.getRGB(25,37));verify(f.sprites,times(1)).getSprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY,0);verifyNoInteractions(f.items);
    }
    @Test public void cleaningBarUsesTheActualCleanHerbIconRatherThanTheGrimyInput() throws Exception
    {
        Fixture f=new Fixture();ActionRecipeCatalog.Recipe ranarr=recipe("herblore:ranarr_weed");f.inventory(inputs(ranarr,10));f.tool.onGameTick();
        AsyncBufferedImage icon=itemImage(0xff38b658);when(f.items.getImage(ItemID.RANARR_WEED)).thenReturn(icon);
        MenuOptionClicked clean=mock(MenuOptionClicked.class);when(clean.getMenuOption()).thenReturn("Clean");when(clean.getItemId()).thenReturn(207);f.tool.onMenuOptionClicked(clean);
        assertEquals(257,f.tool.snapshot().batchItemId);assertSame(icon,f.tool.snapshot().batchIcon);verify(f.items).getImage(257);verify(f.items,never()).getImage(207);
        BufferedImage image=new BufferedImage(300,150,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try{assertEquals(new Dimension(264,90),f.tool.overlay().render(g));}finally{g.dispose();}
        assertEquals(0xff38b658,image.getRGB(25,40));assertNotEquals(0,image.getRGB(200,67));
        File output=new File("build/reports/action-progress-native-icon.png");output.getParentFile().mkdirs();ImageIO.write(image,"png",output);
    }
    @Test public void exactNativeProductAnimationsUseTheirOwnItemsWithoutASelectedRun()
    {
        Fixture f=new Fixture();when(f.items.getImage(ItemID.DIAMOND)).thenReturn(itemImage(0xff80e0ff));
        Animation animation=mock(Animation.class);when(animation.getFrameLengths()).thenReturn(new int[]{30,30});when(f.f.client.loadAnimation(AnimationID.HUMAN_DIAMONDCUTTING)).thenReturn(animation);
        f.inventory(inputs(recipe("crafting:diamond"),2));f.choose("Cut",ItemID.DIAMOND);f.setAnimation(AnimationID.HUMAN_DIAMONDCUTTING);f.event();assertNull(f.f.session.snapshot().context);assertEquals(1601,f.tool.snapshot().cycleItemId);assertNotNull(f.tool.snapshot().cycleIcon);
        assertEquals(ItemID.OPAL,ActionProgressRules.animationItem(AnimationID.HUMAN_OPALCUTTING));assertEquals(ItemID.MAGIC_SHORTBOW,ActionProgressRules.animationItem(AnimationID.STRINGING_MAGIC_SHORTBOW));
        verify(f.items).getImage(1601);
    }
    @Test public void nativeResourceClicksChooseTheSpecificLogOrOreAndNeverGuessGenericRocks()
    {
        Fixture f=new Fixture();Animation animation=mock(Animation.class);when(animation.getFrameLengths()).thenReturn(new int[]{30,30});
        when(f.f.client.loadAnimation(AnimationID.HUMAN_WOODCUTTING_RUNE_AXE)).thenReturn(animation);when(f.f.client.loadAnimation(AnimationID.HUMAN_MINING_RUNE_PICKAXE)).thenReturn(animation);
        MenuOptionClicked chop=mock(MenuOptionClicked.class);when(chop.getMenuOption()).thenReturn("Chop down");when(chop.getMenuTarget()).thenReturn("<col=ffff00>Yew</col>");f.tool.onMenuOptionClicked(chop);
        f.setAnimation(AnimationID.HUMAN_WOODCUTTING_RUNE_AXE);f.event();assertEquals(ItemID.YEW_LOGS,f.tool.snapshot().cycleItemId);
        MenuOptionClicked mine=mock(MenuOptionClicked.class);when(mine.getMenuOption()).thenReturn("Mine");when(mine.getMenuTarget()).thenReturn("Iron rocks");f.tool.onMenuOptionClicked(mine);
        f.setAnimation(AnimationID.HUMAN_MINING_RUNE_PICKAXE);f.event();assertEquals(ItemID.IRON_ORE,f.tool.snapshot().cycleItemId);
        when(mine.getMenuTarget()).thenReturn("Rocks");f.tool.onMenuOptionClicked(mine);assertEquals(0,f.tool.snapshot().cycleItemId);assertEquals(Skill.MINING,f.tool.snapshot().cycleSkill);
        verify(f.items).getImage(ItemID.YEW_LOGS);verify(f.items).getImage(ItemID.IRON_ORE);
    }
    @Test public void nativeMakeWidgetIdentifiesTheActualOutputBeforeAnyGuideWasSelected()
    {
        Fixture f=new Fixture();Animation animation=mock(Animation.class);when(animation.getFrameLengths()).thenReturn(new int[]{30,30});when(f.f.client.loadAnimation(AnimationID.HUMAN_FLETCHING)).thenReturn(animation);
        f.inventory(inputs(recipe("fletching:magic_longbow_u"),3));f.tool.onGameTick();
        when(f.items.getImage(70)).thenReturn(itemImage(0xffb87c36));MenuOptionClicked make=mock(MenuOptionClicked.class);when(make.getMenuOption()).thenReturn("Make-all");Widget widget=mock(Widget.class);when(widget.getItemId()).thenReturn(70);when(make.getItemId()).thenReturn(-1);when(make.getWidget()).thenReturn(widget);
        f.tool.onMenuOptionClicked(make);f.setAnimation(AnimationID.HUMAN_FLETCHING);f.event();assertNull(f.f.session.snapshot().context);assertEquals(70,f.tool.snapshot().cycleItemId);assertNotNull(f.tool.snapshot().cycleIcon);
    }
    @Test public void nativeProductReplacesAnOlderSelectedRecipeCaptionAsWellAsItsIcon()
    {
        Fixture f=new Fixture();ActionRecipeCatalog.Recipe magic=recipe("fletching:magic_longbow");
        f.f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,magic.id,magic.name,null,magic.id,Skill.FLETCHING,0));f.tool.onGameTick();
        f.inventory(inputs(recipe("fletching:yew_longbow"),2));f.setAnimation(AnimationID.STRINGING_YEW_LONGBOW);f.event();assertEquals(ItemID.YEW_LONGBOW,f.tool.snapshot().cycleItemId);assertEquals(recipe("fletching:yew_longbow").name,f.tool.snapshot().title);
    }
    @Test public void cleaningUsesOneActualIdentityEvenWhenTheSequenceIsNamedEmeraldCutting()
    {
        Fixture f=new Fixture();ActionRecipeCatalog.Recipe herb=recipe("herblore:toadflax"),emerald=recipe("crafting:emerald");
        f.f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,emerald.id,emerald.name,null,emerald.id,Skill.CRAFTING,0));
        Map<Integer,Long> before=inputs(herb,8);f.inventory(before);f.tool.onGameTick();
        AsyncBufferedImage icon=itemImage(0xff50af48);when(f.items.getImage(herb.icon)).thenReturn(icon);
        Animation animation=mock(Animation.class);when(animation.getFrameLengths()).thenReturn(new int[]{10,10,10});
        when(f.f.client.loadAnimation(AnimationID.HUMAN_EMERALDCUTTING)).thenReturn(animation);
        f.choose("Clean",herb.ingredients.get(0).itemId);f.setAnimation(AnimationID.HUMAN_EMERALDCUTTING);f.event();
        assertEquals(herb.name,f.tool.snapshot().title);assertEquals(herb.icon,f.tool.snapshot().cycleItemId);assertSame(icon,f.tool.snapshot().cycleIcon);
        assertEquals(Skill.HERBLORE,f.tool.snapshot().cycleSkill);assertEquals("Animation",f.tool.snapshot().basis);
        assertEquals(herb.name,f.tool.snapshot().batchTitle);assertEquals(herb.icon,f.tool.snapshot().batchItemId);
        f.inventory(after(herb,before,3));f.inventoryEvent();f.xp(Skill.HERBLORE,(int)Math.round(herb.xp*3));f.tick(3);
        assertEquals(3,f.tool.snapshot().completed);assertEquals(herb.name,f.tool.snapshot().title);
        verify(f.items,never()).getImage(emerald.icon);
    }
    @Test public void actualInventoryAndXpReplaceAnOlderNativeRecipeDespiteASelectedMethod()
    {
        Fixture f=new Fixture();ActionRecipeCatalog.Recipe emerald=recipe("crafting:emerald"),herb=recipe("herblore:toadflax");
        Animation animation=mock(Animation.class);when(animation.getFrameLengths()).thenReturn(new int[]{10,10,10});
        when(f.f.client.loadAnimation(AnimationID.HUMAN_EMERALDCUTTING)).thenReturn(animation);
        f.f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,emerald.id,emerald.name,null,emerald.id,Skill.CRAFTING,0));
        Map<Integer,Long> gems=inputs(emerald,4);f.inventory(gems);f.tool.onGameTick();f.choose("Cut",emerald.icon);
        f.setAnimation(AnimationID.HUMAN_EMERALDCUTTING);f.event();assertEquals(emerald.name,f.tool.snapshot().title);
        Map<Integer,Long> before=inputs(herb,8);f.setAnimation(-1);f.inventory(before);f.inventoryEvent();f.tick(4);
        f.setAnimation(AnimationID.HUMAN_EMERALDCUTTING);f.event();
        assertFalse("An old item operation cannot identify a new unrelated inventory",f.tool.snapshot().cycle());
        f.inventory(after(herb,before,3));f.inventoryEvent();f.xp(Skill.HERBLORE,(int)Math.round(herb.xp*3));f.tick(3);
        assertEquals(herb.name,f.tool.snapshot().batchTitle);assertEquals(3,f.tool.snapshot().completed);
        assertEquals(herb.name,f.tool.snapshot().title);assertEquals(herb.icon,f.tool.snapshot().cycleItemId);assertEquals(Skill.HERBLORE,f.tool.snapshot().cycleSkill);
    }
    @Test public void selectedMethodAndGemAnimationAliasWithoutItemProofNeverInventEmeraldWork()
    {
        Fixture f=new Fixture();ActionRecipeCatalog.Recipe emerald=recipe("crafting:emerald");
        f.f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,emerald.id,emerald.name,null,emerald.id,Skill.CRAFTING,0));
        f.inventory(inputs(emerald,8));f.tool.onGameTick();f.setAnimation(AnimationID.HUMAN_EMERALDCUTTING);f.event();
        assertFalse(f.tool.snapshot().cycle());assertFalse(f.tool.snapshot().batch());assertEquals(0,f.tool.snapshot().cycleItemId);
    }
    @Test public void completedHerbBatchDisappearsAndFreshMaterialsStartNewCounts()
    {
        Fixture f=new Fixture();ActionRecipeCatalog.Recipe herb=recipe("herblore:toadflax");Map<Integer,Long> before=inputs(herb,8);
        f.inventory(before);f.tool.onGameTick();f.choose("Clean",herb.ingredients.get(0).itemId);
        Animation animation=mock(Animation.class);when(animation.getFrameLengths()).thenReturn(new int[]{100,100});
        when(f.f.client.loadAnimation(AnimationID.HUMAN_EMERALDCUTTING)).thenReturn(animation);f.setAnimation(AnimationID.HUMAN_EMERALDCUTTING);f.event();
        f.inventory(after(herb,before,8));f.inventoryEvent();f.xp(Skill.HERBLORE,(int)Math.round(herb.xp*8));f.tick(3);
        assertFalse(f.tool.snapshot().batch());assertFalse("A completed batch must not retain its stale animation",f.tool.snapshot().cycle());
        Graphics2D g=new BufferedImage(400,200,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try{assertNull(f.tool.overlay().render(g));}finally{g.dispose();}
        f.inventory(inputs(herb,5));f.inventoryEvent();f.choose("Clean",herb.ingredients.get(0).itemId);
        assertTrue(f.tool.snapshot().batch());assertEquals(0,f.tool.snapshot().completed);assertEquals(5,f.tool.snapshot().batchTotal);
    }
    @Test public void idleClientFramesReuseOneSnapshotAndOneEmptyReading()
    {
        Fixture f=new Fixture();ActionProgressTool.Snapshot idle=f.tool.snapshot();
        for(int i=0;i<1000;i++){f.f.now.addAndGet(20);f.tool.onClientTick();assertSame(idle,f.tool.snapshot());}
        assertFalse(idle.cycle());assertFalse(idle.batch());verifyNoInteractions(f.items,f.sprites,f.skillIcons);
        f.tool.stop();ActionProgressTool.Snapshot empty=f.tool.snapshot();
        for(int i=0;i<100;i++){f.tool.onClientTick();assertSame(empty,f.tool.snapshot());}
    }
    @Test public void unchangedCastFramesReusePayloadWhileCountdownAdvancesAndExpires()
    {
        Fixture f=new Fixture();BufferedImage icon=solid(24,24,0xffe0b040);
        when(f.sprites.getSprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY,0)).thenReturn(icon);
        f.setAnimation(AnimationID.HUMAN_CASTHIGHLVLALCHEMY);f.event();ActionProgressTool.Snapshot cast=f.tool.snapshot();
        for(int i=0;i<100;i++){f.f.now.addAndGet(20);f.tool.onClientTick();assertSame(cast,f.tool.snapshot());}
        assertEquals(1000,cast.observedAt);assertEquals(1000,cast.cycleEnd-f.tool.now());
        assertEquals(1d/3,RadialTimer.fraction(f.tool.now(),cast.cycleStart,cast.cycleEnd),0.00001);
        verify(f.sprites,times(1)).getSprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY,0);
        f.f.now.set(cast.cycleEnd);f.tool.onClientTick();assertNotSame(cast,f.tool.snapshot());assertFalse(f.tool.snapshot().cycle());
        ActionProgressTool.Snapshot expired=f.tool.snapshot();f.f.now.addAndGet(20);f.tool.onClientTick();assertSame(expired,f.tool.snapshot());
    }
    @Test public void missingIconsRetryAtTheirDeadlineWithoutRepublishingTheSamePayload()
    {
        Fixture f=new Fixture();BufferedImage icon=solid(24,24,0xffe0b040);
        when(f.sprites.getSprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY,0)).thenReturn(null,null,icon);
        f.setAnimation(AnimationID.HUMAN_CASTHIGHLVLALCHEMY);f.event();ActionProgressTool.Snapshot pending=f.tool.snapshot();
        for(int i=0;i<49;i++){f.f.now.addAndGet(20);f.tool.onClientTick();assertSame(pending,f.tool.snapshot());}
        verify(f.sprites,times(1)).getSprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY,0);
        f.f.now.addAndGet(20);f.tool.onClientTick();assertSame(pending,f.tool.snapshot());
        verify(f.sprites,times(2)).getSprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY,0);
        f.f.now.addAndGet(999);f.tool.onClientTick();assertSame(pending,f.tool.snapshot());
        f.f.now.addAndGet(1);f.tool.onClientTick();assertNotSame(pending,f.tool.snapshot());assertSame(icon,f.tool.snapshot().cycleIcon);
        verify(f.sprites,times(3)).getSprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY,0);
    }
    @Test public void unchangedBatchExpiresOnClientFramesWithoutWaitingForAnotherInventoryEvent()
    {
        Fixture f=new Fixture();ActionRecipeCatalog.Recipe herb=recipe("herblore:toadflax");f.inventory(inputs(herb,8));f.tool.onGameTick();
        when(f.items.getImage(herb.icon)).thenReturn(itemImage(0xff50af48));f.choose("Clean",herb.ingredients.get(0).itemId);
        ActionProgressTool.Snapshot batch=f.tool.snapshot();assertTrue(batch.batch());
        for(int i=0;i<500;i++){f.f.now.addAndGet(20);f.tool.onClientTick();assertSame(batch,f.tool.snapshot());}
        f.f.now.incrementAndGet();f.tool.onClientTick();assertNotSame(batch,f.tool.snapshot());assertFalse(f.tool.snapshot().batch());
        verify(f.items,times(1)).getImage(herb.icon);
    }
    @Test public void clientFrameReuseStillRespondsToPreferencesBankCombatAndProfileChanges()
    {
        Fixture f=new Fixture();when(f.sprites.getSprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY,0)).thenReturn(solid(24,24,0xffe0b040));
        f.setAnimation(AnimationID.HUMAN_CASTHIGHLVLALCHEMY);f.event();ActionProgressTool.Snapshot cast=f.tool.snapshot();
        f.tool.preferences().animation=false;f.tool.onClientTick();assertNotSame(cast,f.tool.snapshot());assertFalse(f.tool.snapshot().cycle());
        f.tool.preferences().animation=true;f.tool.onClientTick();assertTrue(f.tool.snapshot().cycle());
        Widget bank=mock(Widget.class);when(f.f.client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER)).thenReturn(bank);
        f.tool.onClientTick();assertFalse(f.tool.snapshot().cycle());
        when(f.f.client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER)).thenReturn(null);f.event();assertTrue(f.tool.snapshot().cycle());
        NPC enemy=mock(NPC.class);when(enemy.getCombatLevel()).thenReturn(100);when(f.f.player.getInteracting()).thenReturn(enemy);
        f.tool.onClientTick();assertFalse(f.tool.snapshot().cycle());
        when(f.f.player.getInteracting()).thenReturn(null);f.event();assertTrue(f.tool.snapshot().cycle());
        f.f.profile.set("rsprofile.other");assertFalse(f.tool.snapshot().cycle());f.tool.onClientTick();assertEquals("rsprofile.other",f.tool.snapshot().profile);
        verify(f.sprites,times(2)).getSprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY,0);
    }
    private static BufferedImage solid(int width,int height,int color)
    {BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.setColor(new java.awt.Color(color,true));g.fillRect(0,0,width,height);}finally{g.dispose();}return image;}
    private static AsyncBufferedImage itemImage(int color)
    {AsyncBufferedImage image=new AsyncBufferedImage(mock(ClientThread.class),36,32,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.setColor(new java.awt.Color(color,true));g.fillRect(0,0,36,32);}finally{g.dispose();}image.loaded();return image;}
    private static long cycle(String id,int animation,boolean first){return ActionProgressRules.recipeCycle(recipe(id),animation,Set.of(),first);}
    private static ActionRecipeCatalog.Recipe recipe(String id){return ActionRecipeCatalog.recipes().stream().filter(recipe->id.equals(recipe.id)).findFirst().orElseThrow(()->new AssertionError(id));}
    private static Map<Integer,Long> inputs(ActionRecipeCatalog.Recipe recipe,long count)
    {Map<Integer,Long> items=new LinkedHashMap<>();for(ActionRecipeCatalog.Ingredient input:recipe.ingredients)items.merge(input.itemId,count*input.quantity,Long::sum);return items;}
    private static Map<Integer,Long> after(ActionRecipeCatalog.Recipe recipe,Map<Integer,Long> before,long count)
    {Map<Integer,Long> items=new LinkedHashMap<>(before);for(ActionRecipeCatalog.Ingredient input:recipe.ingredients)items.merge(input.itemId,-count*input.quantity,Long::sum);for(ActionRecipeCatalog.Ingredient output:recipe.products)items.merge(output.itemId,count*output.quantity,Long::sum);items.entrySet().removeIf(entry->entry.getValue()<=0);return items;}
    private static final class Fixture
    {
        final ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();final ItemContainer bag=mock(ItemContainer.class);final ActionProgressTool tool;final ItemManager items=mock(ItemManager.class);final SpriteManager sprites=mock(SpriteManager.class);final SkillIconManager skillIcons=mock(SkillIconManager.class);
        Fixture(){when(bag.getItems()).thenReturn(new Item[0]);when(f.client.getItemContainer(InventoryID.INVENTORY)).thenReturn(bag);tool=new ActionProgressTool(f.client,f.config,f.store,mock(RunewardenConfig.class),f.session,f.now::get,items,sprites,skillIcons);tool.start();tool.onGameTick();}
        void setAnimation(int animation){f.animation.set(animation);}
        void choose(String option,int item){MenuOptionClicked event=mock(MenuOptionClicked.class);when(event.getMenuOption()).thenReturn(option);when(event.getItemId()).thenReturn(item);tool.onMenuOptionClicked(event);}
        void inventory(Map<Integer,Long> items){when(bag.getItems()).thenReturn(items.entrySet().stream().map(item->new Item(item.getKey(),item.getValue().intValue())).toArray(Item[]::new));}
        void inventoryEvent(){ItemContainerChanged event=new ItemContainerChanged(InventoryID.INVENTORY.getId(),bag);tool.onItemContainerChanged(event);}
        void xp(Skill skill,int amount){tool.onStatChanged(new StatChanged(skill,amount,1,1));}
        void event(){AnimationChanged event=new AnimationChanged();event.setActor(f.player);tool.onAnimationChanged(event);}
        void tick(int count){for(int i=0;i<count;i++){f.now.addAndGet(600);f.tick.incrementAndGet();tool.onGameTick();}}
    }
}
