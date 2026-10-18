package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.SpriteManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RunewardenResourceOverlayTest
{
    @Test public void readsTrueBoostedValuesAndCorrectResourceUnits()
    {
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(player);
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(115);
        when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(77);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(13);
        when(client.getVarpValue(VarPlayerID.SA_ENERGY)).thenReturn(525);
        when(client.getEnergy()).thenReturn(8342);
        when(client.getVarpValue(VarPlayerID.OPTION_RUN)).thenReturn(1);
        when(player.getPoseAnimation()).thenReturn(824);
        when(player.getRunAnimation()).thenReturn(824);
        when(player.getIdlePoseAnimation()).thenReturn(808);
        RunewardenResourceOverlay.ResourceState state = RunewardenResourceOverlay.read(client);
        assertNotNull(state);
        assertEquals(115, state.health);
        assertEquals(99, state.maxHealth);
        assertEquals(13, state.prayer);
        assertEquals(77, state.maxPrayer);
        assertEquals(52.5, state.special, .0001);
        assertEquals(83.42, state.energy, .0001);
        assertTrue(state.runEnabled);
        assertTrue(state.running);
    }

    @Test public void hiddenWhenLoggedOutOrSkillsNotReady()
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        assertNull(RunewardenResourceOverlay.read(client));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        assertNull(RunewardenResourceOverlay.read(client));
    }

    @Test public void runToggleDoesNotClaimStationaryPlayerIsRunning()
    {
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(player);
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(10);
        when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(1);
        when(client.getVarpValue(VarPlayerID.OPTION_RUN)).thenReturn(1);
        when(player.getPoseAnimation()).thenReturn(808);
        when(player.getIdlePoseAnimation()).thenReturn(808);
        when(player.getRunAnimation()).thenReturn(824);
        RunewardenResourceOverlay.ResourceState state = RunewardenResourceOverlay.read(client);
        assertTrue(state.runEnabled);
        assertFalse(state.running);
        assertEquals(0, state.health);
    }

    @Test public void rendersHudWithUnavailableSpritesAndCanBeDisabled() throws Exception
    {
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        RunewardenConfig config = mock(RunewardenConfig.class);
        SpriteManager sprites = mock(SpriteManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(player);
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(18);
        when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(77);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(6);
        when(client.getVarpValue(VarPlayerID.SA_ENERGY)).thenReturn(525);
        when(client.getEnergy()).thenReturn(8342);
        when(client.getVarpValue(VarPlayerID.OPTION_RUN)).thenReturn(1);
        RunewardenResourceOverlay overlay = new RunewardenResourceOverlay(client, config, sprites);
        BufferedImage preview = new BufferedImage(900, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = preview.createGraphics();
        graphics.scale(2, 2);
        assertNull(overlay.render(graphics));
        when(config.resourceHud()).thenReturn(true);
        Dimension size = overlay.render(graphics);
        graphics.dispose();
        assertEquals(450, size.width);
        assertEquals(64, size.height);
        assertNotEquals(0, preview.getRGB(12, 12));
        File output = new File("build/reports/resource-hud-render.png");
        output.getParentFile().mkdirs();
        ImageIO.write(preview, "png", output);
        when(config.hudCompact()).thenReturn(true);
        when(config.hudOpacity()).thenReturn(85);
        BufferedImage compact = new BufferedImage(788, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D compactGraphics = compact.createGraphics();
        compactGraphics.scale(2, 2);
        Dimension compactSize = overlay.render(compactGraphics);
        compactGraphics.dispose();
        assertEquals(394, compactSize.width);
        assertEquals(50, compactSize.height);
        assertTrue(compactSize.width * compactSize.height < size.width * size.height);
        ImageIO.write(compact, "png", new File("build/reports/resource-hud-compact.png"));
    }
}
