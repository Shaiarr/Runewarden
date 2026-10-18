/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GuideNavigationPlanningTest
{
    @Test public void guideRouteCarriesSourceItemQuantitiesAndKnownLocksCannotClaimReachable()
    {
        Fixture f=new Fixture();GuideCatalog.Entry guide=guide();f.nav.setGuideContextSupplier(()->guide);
        AtomicReference<GoalCatalog.Facts> facts=new AtomicReference<>(facts(40));f.nav.setFactsSupplier(facts::get);
        assertFalse(f.nav.route(point()));assertEquals(-1,f.nav.snapshot().access.state);assertFalse(f.nav.snapshot().reached);verify(f.events,never()).post(any());
        facts.set(facts(55));assertTrue(f.nav.route(point()));PluginMessage path=f.sent().get(0);assertEquals("path",path.getName());
        Map<?,?> target=(Map<?,?>)path.getData().get("target");Map<?,?> supplies=(Map<?,?>)((List<?>)target.get("requirements")).get(0);assertEquals(Integer.valueOf(2),supplies.get(954));
    }
    @Test public void unobservedQuestAccessRemainsVisibleAlongsideNativeRouteInstructions()
    {
        Fixture f=new Fixture();GuideCatalog.Entry guide=guide();guide.requirementDetails=List.of(new JsonParser().parse("{\"kind\":\"quest\",\"key\":\"Legends' Quest\",\"label\":\"Legends' Quest\"}").getAsJsonObject());
        f.nav.setFactsSupplier(()->facts(99));assertTrue(f.nav.routeGuide(guide,point(),"Unknown quest access",true));
        PluginMessage request=f.sent().stream().filter(m->"getTarget".equals(m.getName())).findFirst().get();
        f.nav.onPluginMessage(new PluginMessage("shortestpath","currentTarget",Map.of("id",request.getData().get("id"),"set",true,"target",List.of(f.nav.snapshot().target))));
        f.nav.setRouteReader(plugin->new ShortestPathRouteReading(point(),point(),Set.of(point()),true,false,0,"REACHED","",List.of("Native route transport")));
        f.nav.onPluginMessage(new PluginMessage("shortestpath","transports",Map.of("displayInfo",List.of("Native route transport"))));
        assertTrue("Completed native results prove the geometric route; quest access stays unknown",f.nav.snapshot().reached);assertEquals(List.of("Native route transport"),f.nav.snapshot().transports);
        assertEquals(0,f.nav.snapshot().access.state);assertTrue(f.nav.snapshot().status.contains("check access"));
    }
    @Test public void bankOwnershipExplanationRetainsPreviousSessionAgeAndNeverTreatsUnseenBankAsEmpty()
    {
        Fixture f=new Fixture();GuideCatalog.Entry guide=guide();GuideNavigation.Access missing=f.nav.guideAccess(guide,facts(55),null);assertTrue(missing.bankState.contains("not observed"));
        Map<OwnershipLedger.Storage,OwnershipLedger.Observation> storages=new EnumMap<>(OwnershipLedger.Storage.class);
        storages.put(OwnershipLedger.Storage.BANK,new OwnershipLedger.Observation(Map.of(0,new OwnershipLedger.SlotItem(954,100)),System.currentTimeMillis()-120000,false));
        OwnershipLedger.View owned=new OwnershipLedger.View("rsprofile.a",storages,null,Map.of(),false,false,Map.of());
        GuideNavigation.Access access=f.nav.guideAccess(guide,facts(55),owned);assertTrue(access.bankState.contains("previous session"));assertTrue(access.bankState.contains("0 / 1"));
    }
    @Test public void typedAlternativesAndBoostableSkillsPreserveUncertainty()
    {
        GoalCatalog.Facts facts=facts(50);JsonParser json=new JsonParser();
        assertEquals(0,GuideNavigation.requirementState(json.parse("{\"kind\":\"skill\",\"key\":\"mining\",\"value\":55,\"boostable\":true}").getAsJsonObject(),facts));
        assertEquals(-1,GuideNavigation.requirementState(json.parse("{\"kind\":\"skill\",\"key\":\"mining\",\"value\":55}").getAsJsonObject(),facts));
        assertEquals(1,GuideNavigation.requirementState(json.parse("{\"anyOf\":[{\"kind\":\"skill\",\"key\":\"mining\",\"value\":55},{\"kind\":\"skill\",\"key\":\"mining\",\"value\":40}]}").getAsJsonObject(),facts));
    }
    @Test public void personalSupplyEditorKeepsGroupsAndAlternativeQuantitiesSeparate()
    {
        List<Map<Integer,Integer>> groups=GuideNavigation.parseItemRequirements("Spade x 1 | Bronze pickaxe x 2\nCoins x 1000");assertEquals(2,groups.size());assertEquals(Integer.valueOf(1),groups.get(0).get(952));assertEquals(Integer.valueOf(2),groups.get(0).get(1265));assertEquals(Map.of(995,1000),groups.get(1));
        for(String invalid:List.of("Coins x 0","imaginary item x 1","Coins x 1000001","0 x 1"))try{GuideNavigation.parseItemRequirements(invalid);fail(invalid);}catch(IllegalArgumentException expected){}
    }
    @Test public void nativeBankVisitsPersistARealStopAndInstancesDoNotRecordFabricatedWorldLocations()
    {
        Fixture f=new Fixture();Widget bank=mock(Widget.class);when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.ITEMS)).thenReturn(bank);f.nav.onGameTick();assertEquals(1,f.nav.bankStops().size());assertEquals(point(),f.nav.bankStops().get(0).point);
        when(f.client.isInInstancedRegion()).thenReturn(true);when(f.player.getWorldLocation()).thenReturn(new WorldPoint(4000,4000,0));f.nav.onGameTick();assertEquals(1,f.nav.bankStops().size());
        f.profile.set("rsprofile.b");assertTrue(f.nav.savedDestinations().isEmpty());assertTrue(f.nav.bankStops().isEmpty());
    }
    @Test public void allTravelViewsRenderAt206PixelsWithoutReadingNativeClientOnSwing() throws Exception
    {
        Fixture f=new Fixture();clearInvocations(f.client);
        SwingUtilities.invokeAndWait(()->{
            net.runelite.client.ui.laf.RuneLiteLAF.setup();JPanel panel=f.nav.createPanel();JComboBox<?> view=(JComboBox<?>)descendants(panel).stream().filter(c->c instanceof JComboBox&&((JComboBox<?>)c).getItemCount()==4).findFirst().get();
            for(int index=0;index<4;index++){view.setSelectedIndex(index);panel.setSize(206,2000);layout(panel);panel.setSize(206,Math.max(1,panel.getPreferredSize().height));layout(panel);for(Component c:descendants(panel))if(c instanceof GuideText&&c.isShowing())assertTrue(c.getHeight()>=c.getPreferredSize().height);}
            view.setSelectedIndex(1);findButton(panel,"New destination").doClick();panel.setSize(206,Math.max(1,panel.getPreferredSize().height));layout(panel);
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(206,panel.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();panel.printAll(graphics);graphics.dispose();java.io.File file=new java.io.File("build/reports/travel-notebook-206px.png");file.getParentFile().mkdirs();try{javax.imageio.ImageIO.write(image,"png",file);}catch(java.io.IOException error){throw new AssertionError(error);}
        });verifyNoInteractions(f.client);
    }
    private static GuideCatalog.Entry guide(){GuideCatalog.Entry guide=new GuideCatalog.Entry();guide.id="training:test-route";guide.title="Source-backed test route";guide.items=List.of(new JsonParser().parse("{\"name\":\"Rope\",\"quantity\":2}").getAsJsonObject());guide.requirementDetails=List.of(new JsonParser().parse("{\"kind\":\"skill\",\"key\":\"mining\",\"value\":55,\"label\":\"55 Mining\"}").getAsJsonObject());return guide;}
    private static GoalCatalog.Facts facts(int mining){return new GoalCatalog.Facts(Map.of(),Map.of(Skill.MINING,mining),Map.of(),100);}
    private static WorldPoint point(){return new WorldPoint(3200,3200,0);}
    private static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container)layout((Container)c);}
    private static List<Component> descendants(Container root){List<Component> result=new ArrayList<>();for(Component c:root.getComponents()){result.add(c);if(c instanceof Container)result.addAll(descendants((Container)c));}return result;}
    private static JButton findButton(Container root,String text){return (JButton)descendants(root).stream().filter(c->c instanceof JButton&&((JButton)c).getText().equals(text)).findFirst().get();}
    private static final class Fixture
    {
        final Client client=mock(Client.class);final Player player=mock(Player.class);final ConfigManager config=mock(ConfigManager.class);final PluginManager plugins=mock(PluginManager.class);final EventBus events=mock(EventBus.class);
        final AtomicReference<String> profile=new AtomicReference<>("rsprofile.a");final Map<String,String> saved=new HashMap<>();final GuideNavigation nav;
        Fixture(){when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);when(player.getWorldLocation()).thenReturn(point());when(config.getRSProfileKey()).thenAnswer(call->profile.get());Plugin plugin=new shortestpath.ShortestPathPlugin();when(plugins.getPlugins()).thenReturn(List.of(plugin));when(plugins.isPluginEnabled(plugin)).thenReturn(true); when(plugins.isPluginActive(plugin)).thenReturn(true);
            when(config.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(call->saved.get(call.getArgument(0)+":"+call.getArgument(1)+":"+call.getArgument(2)));doAnswer(call->{saved.put(call.getArgument(0)+":"+call.getArgument(1)+":"+call.getArgument(2),call.getArgument(3));return null;}).when(config).setConfiguration(anyString(),anyString(),anyString(),anyString());
            nav=new GuideNavigation(client,events,plugins,null,config,null);nav.setDataStore(new RunewardenDataStore(config,new Gson()));nav.start();nav.onGameTick();}
        List<PluginMessage> sent(){ArgumentCaptor<Object> captured=ArgumentCaptor.forClass(Object.class);verify(events,atLeastOnce()).post(captured.capture());List<PluginMessage> result=new ArrayList<>();for(Object object:captured.getAllValues())if(object instanceof PluginMessage)result.add((PluginMessage)object);return result;}
    }
}
