/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CombatCalculatorToolTest
{
    private static final class Fixture
    {
        final Client client = mock(Client.class);
        final ItemManager items = mock(ItemManager.class);
        final ConfigManager config = mock(ConfigManager.class);
        final Item[] gear = new Item[14];
        final CombatCalculatorTool tool;
        Fixture(String style, int index)
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(config.getRSProfileKey()).thenReturn("character-A");
            ItemContainer worn = mock(ItemContainer.class);
            when(client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN)).thenReturn(worn);
            when(worn.getItems()).thenReturn(gear);
            for (Skill skill : new Skill[] {Skill.STRENGTH, Skill.RANGED, Skill.MAGIC})
            { when(client.getRealSkillLevel(skill)).thenReturn(99); when(client.getBoostedSkillLevel(skill)).thenReturn(99); }
            when(client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(index);
            EnumComposition categories = mock(EnumComposition.class), styles = mock(EnumComposition.class);
            when(client.getEnum(EnumID.WEAPON_STYLES)).thenReturn(categories); when(categories.getIntValue(anyInt())).thenReturn(1000);
            when(client.getEnum(1000)).thenReturn(styles); when(styles.getIntVals()).thenReturn(new int[] {2000, 2001, 2002, 2003, 2004, 2005});
            StructComposition entry = mock(StructComposition.class); when(client.getStructComposition(2000 + index)).thenReturn(entry);
            when(entry.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn(style);
            ClientThread thread = mock(ClientThread.class);
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
            Gson gson = new Gson(); tool = new CombatCalculatorTool(client, thread, items, new MobKnowledgeCatalog(gson), new CombatSpellCatalog(gson), config);
            tool.start();
        }
        void weapon(int id, String name, int strength, int ranged, float magic)
        {
            gear[3] = new Item(id, 1); ItemComposition composition = mock(ItemComposition.class);
            when(composition.getName()).thenReturn(name); when(items.getItemComposition(id)).thenReturn(composition);
            when(items.getItemStats(id)).thenReturn(new ItemStats(true, 0, 0, ItemEquipmentStats.builder().str(strength).rstr(ranged).mdmg(magic).build()));
        }
    }
    @Test public void wornWeaponAndBonusChangesRecalculateWithoutManualInputs()
    {
        Fixture f = new Fixture("Aggressive", 1);
        f.weapon(1, "Dragon scimitar", 66, 0, 0);
        assertEquals(Integer.valueOf(22), f.tool.read().maxHit);
        f.weapon(2, "Abyssal whip", 82, 0, 0);
        assertEquals(Integer.valueOf(25), f.tool.read().maxHit);
        when(f.client.isPrayerActive(Prayer.PIETY)).thenReturn(true);
        assertEquals(Integer.valueOf(30), f.tool.read().maxHit);
        verify(f.client, atLeastOnce()).getItemContainer(net.runelite.api.gameval.InventoryID.WORN);
    }
    @Test public void autocastUsesPublishedSpellObjectWithoutUserSelectionOrItemMetadata()
    {
        Fixture f = new Fixture("Casting", 4); f.weapon(1, "Kodai wand", 0, 0, 5);
        when(f.client.getVarpValue(VarPlayerID.AUTOCAST_SPELL_OBJ)).thenReturn(4651);
        assertEquals(Integer.valueOf(31), f.tool.read().maxHit);
        assertEquals("BASE MAGIC", f.tool.read().scope);
        assertTrue(f.tool.read().reason.startsWith("Ice Barrage"));
        verify(f.items, never()).getItemComposition(4651);
    }
    @Test public void rememberedAutocastCannotReplaceTheEquippedPoweredStaffFormula()
    {
        Fixture f = new Fixture("Accurate", 0); f.weapon(27275, "Tumeken's shadow", 0, 0, 0);
        when(f.client.getVarpValue(VarPlayerID.AUTOCAST_SPELL_OBJ)).thenReturn(4651);
        assertEquals(Integer.valueOf(34), f.tool.read().maxHit);
        assertTrue(f.tool.read().reason.startsWith("tumeken's shadow"));
        assertEquals("BASE MAGIC", f.tool.read().scope);
    }
    @Test public void observedEquippedBlowpipeCheckProvidesDartThenWeaponSwitchInvalidatesIt()
    {
        Fixture f = new Fixture("Ranging", 1); f.weapon(12926, "Toxic blowpipe", 0, 20, 0);
        when(f.client.getTickCount()).thenReturn(10); assertNull(f.tool.read().maxHit);
        MenuOptionClicked check = mock(MenuOptionClicked.class);
        when(check.getMenuOption()).thenReturn("Check"); when(check.getItemId()).thenReturn(12926);
        when(check.getParam1()).thenReturn(InterfaceID.WORNITEMS << 16 | 3);
        f.tool.onMenuOptionClicked(check);
        ChatMessage message = new ChatMessage(); message.setType(ChatMessageType.GAMEMESSAGE);
        message.setMessage("Darts: <col=007f00>Dragon dart x 1,000</col>. Scales: 100%. "); f.tool.onChatMessage(message);
        assertEquals(Integer.valueOf(CurrentMaxHit.baseHit(107, 55)), f.tool.read().maxHit);
        assertEquals("BASE RANGED", f.tool.read().scope);
        f.weapon(1, "Rune crossbow", 0, 0, 0); f.tool.read();
        f.weapon(12926, "Toxic blowpipe", 0, 20, 0); assertNull(f.tool.read().maxHit);
    }
    @Test public void inventoryCopyCheckAndPlayerChatNeverOverrideEquippedAmmunition()
    {
        Fixture f = new Fixture("Ranging", 1); f.weapon(12926, "Toxic blowpipe", 0, 20, 0);
        f.tool.read();
        MenuOptionClicked check = mock(MenuOptionClicked.class); when(check.getMenuOption()).thenReturn("Check"); when(check.getItemId()).thenReturn(12926);
        when(check.getParam1()).thenReturn(InterfaceID.INVENTORY << 16); f.tool.onMenuOptionClicked(check);
        ChatMessage message = new ChatMessage(); message.setType(ChatMessageType.GAMEMESSAGE); message.setMessage("Darts: Dragon dart x 1,000. Scales: 100%.");
        f.tool.onChatMessage(message); assertNull(f.tool.read().maxHit);
        when(check.getParam1()).thenReturn(InterfaceID.WORNITEMS << 16); f.tool.onMenuOptionClicked(check);
        message.setType(ChatMessageType.PUBLICCHAT); f.tool.onChatMessage(message); assertNull(f.tool.read().maxHit);
    }
    @Test public void shadowIdentifiesExactToaTargetAutomaticallyAndRevertsWhenTargetEnds()
    {
        Fixture f = new Fixture("Accurate", 0); f.weapon(27275, "Tumeken's shadow", 0, 0, 30);
        when(f.client.isPrayerActive(Prayer.AUGURY)).thenReturn(true); assertEquals(Integer.valueOf(65), f.tool.read().maxHit);
        Player player = mock(Player.class); NPC target = mock(NPC.class); NPCComposition npc = mock(NPCComposition.class);
        when(f.client.getLocalPlayer()).thenReturn(player); when(player.getInteracting()).thenReturn(target);
        when(target.getTransformedComposition()).thenReturn(npc); when(npc.getId()).thenReturn(11789);
        assertEquals(Integer.valueOf(69), f.tool.read().maxHit); assertEquals("BASE MAGIC", f.tool.read().scope);
        assertTrue(f.tool.read().reason.contains("ToA target observed"));
        when(player.getInteracting()).thenReturn(null); assertEquals(Integer.valueOf(65), f.tool.read().maxHit);
        assertFalse(CombatCalculatorTool.isTombsTarget(1));
    }
    @Test public void sidebarStartsWithLargeAutomaticResultAndRelevantControlsCollapsedAt206Pixels() throws Exception
    {
        Fixture f = new Fixture("Aggressive", 1); f.weapon(1, "Dragon scimitar", 66, 0, 0);
        JPanel[] panel = new JPanel[1]; SwingUtilities.invokeAndWait(() -> panel[0] = f.tool.createPanel()); SwingUtilities.invokeAndWait(() -> {});
        try
        {
            SwingUtilities.invokeAndWait(() -> {
                panel[0].setSize(206, 800); layout(panel[0]);
                JLabel value = (JLabel) named(panel[0], "maxHitValue"); assertEquals("22", value.getText()); assertTrue(value.getFont().getSize() >= 28);
                assertFalse(named(panel[0], "maxHitDart").isShowing());
                assertTrue(allText(panel[0]).contains("Dragon scimitar"));
                assertVisibleWidths(panel[0]);
            });
        }
        finally { f.tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }
    @Test public void checkMessageParsesSteelAmethystAndRejectsUnstructuredGuesses()
    {
        assertEquals(CombatCalculatorTool.Dart.STEEL, CombatCalculatorTool.dartFromCheck("Darts: Steel dart x 2,000. Scales: 50%."));
        assertEquals(CombatCalculatorTool.Dart.AMETHYST, CombatCalculatorTool.dartFromCheck("Darts: Amethyst dart x 1. Scales: 50%."));
        assertEquals(CombatCalculatorTool.Dart.UNKNOWN, CombatCalculatorTool.dartFromCheck("You own Dragon darts"));
    }
    private static void layout(Container container) { container.doLayout(); for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child); }
    private static Component named(Container parent, String name)
    { for (Component child : parent.getComponents()) { if (name.equals(child.getName())) return child; if (child instanceof Container) { Component result = find((Container) child, name); if (result != null) return result; } } throw new AssertionError(name); }
    private static Component find(Container parent, String name)
    { for (Component child : parent.getComponents()) { if (name.equals(child.getName())) return child; if (child instanceof Container) { Component result = find((Container) child, name); if (result != null) return result; } } return null; }
    private static String allText(Container parent)
    { StringBuilder text = new StringBuilder(); for (Component child : parent.getComponents()) { if (child instanceof javax.swing.text.JTextComponent) text.append(((javax.swing.text.JTextComponent) child).getText()); if (child instanceof Container) text.append(allText((Container) child)); } return text.toString(); }
    private static void assertVisibleWidths(Container parent)
    { for (Component child : parent.getComponents()) if (child.isVisible()) { assertTrue(child.getClass().getSimpleName() + " clipped", child.getX() >= 0 && child.getX() + child.getWidth() <= parent.getWidth()); if (child instanceof Container) assertVisibleWidths((Container) child); } }
}
