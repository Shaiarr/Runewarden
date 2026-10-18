/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.*;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import shortestpath.transport.PohNexusPortal;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PohTravelCapabilitiesTest
{
    @Test public void aCapeAndConstructionLevelNeverProveUnobservedHouseFacilities()
    {
        Fixture f=new Fixture();assertEquals("Inventory",f.cap.routeOptions().get("useTeleportationItems"));
        when(f.client.getRealSkillLevel(Skill.CONSTRUCTION)).thenReturn(99);f.items(new Item(9789,1));f.cap.onGameTick();
        assertTrue(f.cap.snapshot().constructionCape);assertEquals(false,f.cap.routeOptions().get("usePoh"));assertTrue(f.foreignWrites.isEmpty());
    }
    @Test public void explicitNativeDeclarationsAreReadWithoutChangingNativeConfiguration()
    {
        Fixture f=new Fixture();f.raw.put("shortestpath::usePoh","true");f.raw.put("shortestpath::pohNexusPortals","[\"CATHERBY\"]");f.cap.onGameTick();
        assertEquals(true,f.cap.routeOptions().get("usePoh"));assertEquals(Set.of("CATHERBY"),f.cap.snapshot().portals);assertTrue(f.foreignWrites.isEmpty());
        f.raw.put("shortestpath::usePoh","false");f.cap.onGameTick();assertEquals(false,f.cap.routeOptions().get("usePoh"));
    }
    @Test public void menuCachesAndMalformedDeclarationsCannotInventKnownFacilities()
    {
        Fixture f=new Fixture();f.raw.put("shortestpath::usePoh","true");f.raw.put("shortestpath:rsprofile.a:portalNexusKeybinds","2|catherby=1");
        f.raw.put("shortestpath::pohNexusPortals","[\"INVENTED\"]");f.cap.onGameTick();assertEquals(false,f.cap.routeOptions().get("usePoh"));
        f.raw.put("shortestpath::pohNexusPortals","broken JSON");f.cap.onGameTick();assertEquals(false,f.cap.routeOptions().get("usePoh"));assertTrue(f.foreignWrites.isEmpty());
    }
    @Test public void revisionsIgnoreOrdinaryHarvestProductsButTrackTravelSupplies()
    {
        Fixture f=new Fixture();f.items(new Item(249,10));f.cap.onGameTick();long before=f.cap.revision();
        f.items(new Item(249,9),new Item(91,1));f.cap.onGameTick();assertEquals(before,f.cap.revision());
        f.items(new Item(9789,1));f.cap.onGameTick();assertTrue(f.cap.revision()>before);before=f.cap.revision();
        when(f.client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_1)).thenReturn(2);f.cap.onGameTick();assertTrue(f.cap.revision()>before);before=f.cap.revision();
        f.raw.put("shortestpath::pohNexusPortals","[\"CATHERBY\"]");f.cap.onGameTick();assertTrue(f.cap.revision()>before);
    }
    @Test public void legacyGlobalDeclarationsCannotLeakIntoAnotherCharacter()
    {
        Fixture f=new Fixture(false);f.raw.put("shortestpath::usePoh","true");f.raw.put("shortestpath::pohNexusPortals","[\"CATHERBY\"]");
        StringBuilder fingerprint=new StringBuilder();for(String key:PohTravelCapabilities.CONFIG_KEYS)fingerprint.append(key).append('=').append(f.raw.get("shortestpath::"+key)).append(';');
        Map<String,Object> applied=Map.of("version",1,"profile","rsprofile.a","fingerprint",fingerprint.toString());
        f.raw.put("runewarden::"+PohTravelCapabilities.LEASE_KEY,f.gson.toJson(applied));f.start();assertEquals(true,f.cap.routeOptions().get("usePoh"));
        f.profile.set("rsprofile.b");f.cap.onGameTick();assertEquals(false,f.cap.routeOptions().get("usePoh"));assertTrue(f.foreignWrites.isEmpty());
    }
    @Test public void onlyAnOwnedBuildingModeHouseCanLearnVisiblePortalDestinations()
    {
        Fixture f=new Fixture();WorldView view=mock(WorldView.class);Scene scene=mock(Scene.class);Player player=mock(Player.class);
        when(f.client.getTopLevelWorldView()).thenReturn(view);when(f.client.getWorldView(anyInt())).thenReturn(view);when(view.isInstance()).thenReturn(true);when(view.getScene()).thenReturn(scene);when(f.client.getLocalPlayer()).thenReturn(player);
        when(player.getLocalLocation()).thenReturn(new net.runelite.api.coords.LocalPoint(64,64));int[][][] chunks=new int[4][13][13];chunks[0][0][0]=((1856/8)<<14)|((7040/8)<<3);when(view.getInstanceTemplateChunks()).thenReturn(chunks);
        Tile tile=mock(Tile.class);GameObject portal=mock(GameObject.class);when(portal.getId()).thenReturn(33432);when(tile.getGameObjects()).thenReturn(new GameObject[]{portal});when(scene.getTiles()).thenReturn(new Tile[][][]{{{tile}}});
        net.runelite.api.widgets.Widget menu=mock(net.runelite.api.widgets.Widget.class);when(menu.getText()).thenReturn("1 : Barrows");when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.TelenexusTeleport.TEXT1)).thenReturn(menu);
        assertFalse(f.cap.scanOwnHouse());assertNull(f.cap.ownHouseOrigin());when(f.client.getVarbitValue(VarbitID.POH_BUILDING_MODE)).thenReturn(1);
        when(view.getInstanceTemplateChunks()).thenReturn(null);assertNull("A loading house has no verified origin",f.cap.ownHouseOrigin());assertFalse(f.cap.scanOwnHouse());
        when(view.getInstanceTemplateChunks()).thenReturn(chunks);assertTrue(f.cap.scanOwnHouse());f.cap.onGameTick();
        assertEquals(new net.runelite.api.coords.WorldPoint(1858,7051,0),f.cap.ownHouseOrigin());assertEquals(Set.of("CATHERBY","BARROWS"),f.cap.snapshot().observed);
        assertEquals("Observations do not silently rewrite Shortest Path's facilities",false,f.cap.routeOptions().get("usePoh"));assertTrue(f.foreignWrites.isEmpty());
        f.profile.set("rsprofile.b");f.cap.onGameTick();assertTrue(f.cap.snapshot().observed.isEmpty());
    }
    @Test public void portalCatalogMatchesTheRealCompanionEnumAndStopDisablesOverrides()
    {
        Fixture f=new Fixture();assertEquals(PohNexusPortal.values().length,f.cap.portals().size());
        for(PohTravelCapabilities.Portal portal:f.cap.portals()){assertEquals(portal.label,PohNexusPortal.valueOf(portal.key).toString());assertEquals(portal.names,PohNexusPortal.valueOf(portal.key).getDisplayInfos());}
        assertEquals("CATHERBY",f.cap.keyForName("<col=ffffff>1 : Catherby"));assertEquals("SENNTISTEN",f.cap.keyForName("Exam Centre"));assertNull(f.cap.keyForName("Catherby costs 1,000 runes"));
        f.cap.stop();assertEquals(false,f.cap.routeOptions().get("usePoh"));assertTrue(f.foreignWrites.isEmpty());
    }
    static final class Fixture
    {
        final Gson gson=new Gson();final Client client=mock(Client.class);final ClientThread thread=mock(ClientThread.class);final ConfigManager config=mock(ConfigManager.class);
        final AtomicReference<String> profile=new AtomicReference<>("rsprofile.a");final Map<String,String> raw=new HashMap<>();final List<String> foreignWrites=new ArrayList<>();final Queue<Runnable> queued=new ArrayDeque<>();final PohTravelCapabilities cap;
        Fixture(){this(true);}Fixture(boolean start)
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(config.getRSProfileKey()).thenAnswer(c->profile.get());
            when(config.getConfiguration(anyString(),anyString())).thenAnswer(c->raw.get(c.getArgument(0)+"::"+c.getArgument(1)));
            when(config.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(c->raw.get(c.getArgument(0)+":"+c.getArgument(1)+":"+c.getArgument(2)));
            doAnswer(c->{put(c.getArgument(0),null,c.getArgument(1),c.getArgument(2));return null;}).when(config).setConfiguration(anyString(),anyString(),any(Object.class));
            doAnswer(c->{put(c.getArgument(0),null,c.getArgument(1),c.getArgument(2));return null;}).when(config).setConfiguration(anyString(),anyString(),anyString());
            doAnswer(c->{put(c.getArgument(0),c.getArgument(1),c.getArgument(2),c.getArgument(3));return null;}).when(config).setConfiguration(anyString(),nullable(String.class),anyString(),anyString());
            doAnswer(c->{queued.add(c.getArgument(0));return null;}).when(thread).invokeLater(any(Runnable.class));
            cap=new PohTravelCapabilities(client,thread,config,new RunewardenDataStore(config,gson),gson);if(start)start();
        }
        void start(){cap.start();while(!queued.isEmpty())queued.remove().run();}
        void items(Item...items){ItemContainer inventory=mock(ItemContainer.class);when(inventory.getItems()).thenReturn(items);when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);}
        void put(String group,String owner,String key,Object value){String serialized=value instanceof Set?gson.toJson(value,Set.class):String.valueOf(value);raw.put(group+":"+(owner==null?"":owner)+":"+key,serialized);if("shortestpath".equals(group))foreignWrites.add(key);}
    }
}
