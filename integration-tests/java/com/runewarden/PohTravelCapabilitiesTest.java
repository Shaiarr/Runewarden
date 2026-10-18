/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
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
    @Test public void carriedConsumablesUseTheActualPublicStringContractWithoutEnablingAnUnknownHouse()
    {
        Fixture f=new Fixture();assertEquals("Inventory",f.cap.routeOptions().get("useTeleportationItems"));assertEquals(false,f.cap.routeOptions().get("usePoh"));
        when(f.client.getRealSkillLevel(Skill.CONSTRUCTION)).thenReturn(99);f.items(new Item(9789,1));f.cap.onGameTick();
        assertTrue(f.cap.snapshot().constructionCape);assertEquals("A cape never proves the house contains Catherby",false,f.cap.routeOptions().get("usePoh"));assertTrue(f.foreignWrites.isEmpty());
    }
    @Test public void explicitHouseSelectionSerializesNativeEnumNamesAndIsolatedProfilesCannotBorrowIt()
    {
        Fixture f=new Fixture();PohTravelCapabilities.Saved selection=new PohTravelCapabilities.Saved();selection.portals.add("CATHERBY");assertTrue(f.cap.apply("rsprofile.a",selection));
        assertEquals(true,f.cap.routeOptions().get("usePoh"));assertEquals(Set.of(PohNexusPortal.CATHERBY),f.gson.fromJson(f.raw.get("shortestpath::pohNexusPortals"),new TypeToken<Set<PohNexusPortal>>(){}.getType()));
        assertEquals("NONE",f.raw.get("shortestpath::pohJewelleryBoxTier"));assertEquals("[]",f.raw.get("shortestpath::pohMountedItems"));
        int writes=f.foreignWrites.size();f.profile.set("rsprofile.b");f.cap.onGameTick();assertEquals(false,f.cap.routeOptions().get("usePoh"));assertTrue(f.cap.snapshot().portals.isEmpty());
        assertFalse(f.cap.apply("rsprofile.a",selection));assertEquals(writes,f.foreignWrites.size());
        f.profile.set("rsprofile.a");f.cap.onGameTick();assertEquals(true,f.cap.routeOptions().get("usePoh"));assertEquals(Set.of("CATHERBY"),f.cap.snapshot().portals);
    }
    @Test public void existingNativeHouseDeclarationsRemainEffectiveAndMenuCacheIsOnlyAnUnconfirmedSuggestion()
    {
        Fixture f=new Fixture(false);f.raw.put("shortestpath::usePoh","true");f.raw.put("shortestpath::pohNexusPortals","[\"CATHERBY\"]");f.raw.put("shortestpath:rsprofile.a:portalNexusKeybinds","2|catherby=1|barrows=A|unknown=2");f.start();
        assertEquals(true,f.cap.routeOptions().get("usePoh"));assertEquals(Set.of("CATHERBY"),f.cap.snapshot().portals);assertEquals(Set.of("CATHERBY","BARROWS"),f.cap.snapshot().suggestions);assertTrue(f.cap.snapshot().observed.isEmpty());
        assertFalse(f.cap.scanOwnHouse());assertTrue(f.cap.snapshot().observed.isEmpty());assertTrue(f.foreignWrites.isEmpty());
    }
    @Test public void transportRevisionIgnoresOrdinaryHerbProductsButChangesForCapeRunesAndNativeHouse()
    {
        Fixture f=new Fixture();f.items(new Item(249,10));f.cap.onGameTick();long before=f.cap.revision();f.items(new Item(249,9),new Item(91,1));f.cap.onGameTick();assertEquals(before,f.cap.revision());
        f.items(new Item(9789,1));f.cap.onGameTick();assertTrue(f.cap.revision()>before);before=f.cap.revision();
        f.items(new Item(9789,1),new Item(563,2));f.cap.onGameTick();assertTrue(f.cap.revision()>before);before=f.cap.revision();
        when(f.client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_1)).thenReturn(2);f.cap.onGameTick();assertTrue(f.cap.revision()>before);before=f.cap.revision();
        f.raw.put("shortestpath::pohNexusPortals","[\"CATHERBY\"]");f.cap.onGameTick();assertTrue(f.cap.revision()>before);
    }
    @Test public void futureProfileRecordAndInvalidSelectionNeverMutateNativeConfiguration()
    {
        Fixture f=new Fixture(false);String future="{\"version\":99,\"portals\":[],\"observed\":[],\"mounted\":[],\"jewellery\":\"NONE\"}";f.raw.put("runewarden:rsprofile.a:"+PohTravelCapabilities.KEY,future);f.start();
        PohTravelCapabilities.Saved selection=new PohTravelCapabilities.Saved();selection.portals.add("CATHERBY");assertFalse(f.cap.apply("rsprofile.a",selection));assertEquals(future,f.raw.get("runewarden:rsprofile.a:"+PohTravelCapabilities.KEY));assertTrue(f.foreignWrites.isEmpty());
        Fixture invalid=new Fixture();selection.portals.add("INVENTED");assertFalse(invalid.cap.apply("rsprofile.a",selection));assertTrue(invalid.foreignWrites.isEmpty());
    }
    @Test public void onlyAnActualOwnedHouseTemplateInBuildingModeCanLearnSceneAndNexusDestinations()
    {
        Fixture f=new Fixture();WorldView view=mock(WorldView.class);Scene scene=mock(Scene.class);Player player=mock(Player.class);
        when(f.client.getTopLevelWorldView()).thenReturn(view);when(f.client.getWorldView(anyInt())).thenReturn(view);when(view.isInstance()).thenReturn(true);when(view.getScene()).thenReturn(scene);when(f.client.getLocalPlayer()).thenReturn(player);
        when(player.getLocalLocation()).thenReturn(new net.runelite.api.coords.LocalPoint(64,64));int[][][] chunks=new int[4][13][13];chunks[0][0][0]=((1856/8)<<14)|((7040/8)<<3);when(view.getInstanceTemplateChunks()).thenReturn(chunks);
        Tile tile=mock(Tile.class);GameObject portal=mock(GameObject.class);when(portal.getId()).thenReturn(33432);when(tile.getGameObjects()).thenReturn(new GameObject[]{portal});when(scene.getTiles()).thenReturn(new Tile[][][]{{{tile}}});
        net.runelite.api.widgets.Widget menu=mock(net.runelite.api.widgets.Widget.class);when(menu.getText()).thenReturn("1 : Barrows");when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.TelenexusTeleport.TEXT1)).thenReturn(menu);
        assertFalse("A guest house must never be learned",f.cap.scanOwnHouse());when(f.client.getVarbitValue(VarbitID.POH_BUILDING_MODE)).thenReturn(1);assertTrue(f.cap.scanOwnHouse());f.cap.onGameTick();
        assertEquals(Set.of("CATHERBY","BARROWS"),f.cap.snapshot().observed);assertEquals(false,f.cap.routeOptions().get("usePoh"));assertTrue(f.foreignWrites.isEmpty());
        when(f.client.getVarbitValue(VarbitID.POH_BUILDING_MODE)).thenReturn(0);f.cap.onGameTick();assertEquals(Set.of("CATHERBY","BARROWS"),f.cap.snapshot().observed);
        f.profile.set("rsprofile.b");f.cap.onGameTick();assertTrue(f.cap.snapshot().observed.isEmpty());
    }
    @Test public void publicPortalCatalogMatchesEveryNativeEnumAndExactCatherbySceneObjects() throws Exception
    {
        Fixture f=new Fixture();assertEquals(PohNexusPortal.values().length,f.cap.portals().size());for(PohTravelCapabilities.Portal portal:f.cap.portals()){assertEquals(portal.label,PohNexusPortal.valueOf(portal.key).toString());assertEquals(portal.names,PohNexusPortal.valueOf(portal.key).getDisplayInfos());}
        assertEquals(List.of(33432,33435,33438),f.cap.portals().stream().filter(p->p.key.equals("CATHERBY")).findFirst().get().objectIds);
        assertEquals("CATHERBY",f.cap.keyForName("<col=ffffff>1 : Catherby"));assertEquals("SENNTISTEN",f.cap.keyForName("Exam Centre"));assertNull(f.cap.keyForName("Catherby costs 1,000 runes"));
        SwingUtilities.invokeAndWait(()->assertNotNull(f.cap.createPanel()));f.cap.stop();assertEquals(false,f.cap.routeOptions().get("usePoh"));
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
