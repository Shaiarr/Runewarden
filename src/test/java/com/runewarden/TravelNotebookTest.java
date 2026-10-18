/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;
import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TravelNotebookTest
{
    @Test public void savedDestinationsCanBeCreatedEditedAndRemovedWithExactItemAlternatives()
    {
        Fixture f=new Fixture();Map<Integer,Integer> group=new LinkedHashMap<>();group.put(952,1);group.put(1265,2);
        assertTrue(f.book.put(null,"My dig stop",point(3200),"quest:Cook's Assistant",List.of(group)));
        TravelNotebook.Entry first=f.book.destinations().get(0);group.put(952,99);assertEquals(Integer.valueOf(1),first.requirements.get(0).get(952));
        assertTrue(f.book.put(first.id,"New name",point(3300),null,List.of(Map.of(995,1000))));assertEquals(1,f.book.destinations().size());assertEquals("New name",f.book.destinations().get(0).name);
        f.book.clear();f.book.bind("rsprofile.a");assertEquals(point(3300),f.book.destinations().get(0).point);assertTrue(f.book.remove(first.id));assertTrue(f.book.destinations().isEmpty());
    }
    @Test public void characterSwitchImmediatelyHidesPreviousDestinationsAndRejectsStaleMutations()
    {
        Fixture f=new Fixture();f.book.put(null,"First",point(3200),null,List.of());String id=f.book.destinations().get(0).id;f.profile.set("rsprofile.b");
        assertTrue(f.book.destinations().isEmpty());assertFalse(f.book.remove(id));assertFalse(f.book.put(id,"Changed",point(3300),null,List.of()));
        f.book.bind("rsprofile.b");assertTrue(f.book.destinations().isEmpty());assertTrue(f.book.put(null,"Second",point(3400),null,List.of()));
        f.profile.set("rsprofile.a");f.book.bind("rsprofile.a");assertEquals("First",f.book.destinations().get(0).name);
    }
    @Test public void genuinelyObservedBankStopsDeduplicateNearTilesAndStayBounded()
    {
        Fixture f=new Fixture();assertTrue(f.book.observeBank(point(3200),1000));assertFalse(f.book.observeBank(point(3201),2000));assertEquals(1,f.book.banks().size());
        assertTrue(f.book.observeBank(point(3202),62000));assertEquals(point(3202),f.book.banks().get(0).point);
        for(int n=0;n<20;n++)f.book.observeBank(point(3400+n*20),100000+n*100000L);assertEquals(12,f.book.banks().size());
        String id=f.book.banks().get(0).id;assertTrue(f.book.forgetBank(id));assertEquals(11,f.book.banks().size());
    }
    @Test public void futureNotebookIsPreservedAndCannotBeOverwritten()
    {
        Fixture f=new Fixture();f.saved.put("runewarden:rsprofile.a:"+TravelNotebook.KEY,"{\"version\":99,\"destinations\":[],\"banks\":[]}");f.book.clear();f.book.bind("rsprofile.a");
        assertFalse(f.book.writable());assertFalse(f.book.put(null,"New",point(3200),null,List.of()));assertTrue(f.saved.get("runewarden:rsprofile.a:"+TravelNotebook.KEY).contains("99"));
    }
    @Test public void destinationsAreDetachedAndInvalidSavedDataIsRejected()
    {
        Fixture f=new Fixture();f.book.put(null,"Saved",point(3200),null,List.of(Map.of(952,1)));
        try{f.book.destinations().clear();fail();}catch(UnsupportedOperationException expected){}
        try{f.book.destinations().get(0).requirements.get(0).clear();fail();}catch(UnsupportedOperationException expected){}
        for(String invalid:List.of("","<bad>","a\nb\nc"))assertFalse(f.book.put(null,invalid,point(3200),null,List.of()));
        TravelNotebook.Saved data=new TravelNotebook.Saved();TravelNotebook.StoredPoint p=new TravelNotebook.StoredPoint();p.id="same";p.name="x";p.x=3200;p.y=3200;data.destinations.add(p);assertTrue(TravelNotebook.valid(data));data.banks.add(p);assertFalse(TravelNotebook.valid(data));
    }
    @Test public void notebookLimitAndInvalidRequirementsCannotGrowUnbounded()
    {
        Fixture f=new Fixture();for(int n=0;n<60;n++)assertTrue(f.book.put(null,"Destination "+n,point(3200+n),null,List.of()));assertFalse(f.book.put(null,"One too many",point(3500),null,List.of()));
        assertFalse(f.book.put(f.book.destinations().get(0).id,"Invalid",point(3200),null,List.of(Map.of(995,0))));
    }
    private static WorldPoint point(int x){return new WorldPoint(x,3200,0);}
    private static final class Fixture
    {
        final ConfigManager config=mock(ConfigManager.class);final AtomicReference<String> profile=new AtomicReference<>("rsprofile.a");final Map<String,String> saved=new HashMap<>();final TravelNotebook book;
        Fixture(){when(config.getRSProfileKey()).thenAnswer(call->profile.get());when(config.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(call->saved.get(call.getArgument(0)+":"+call.getArgument(1)+":"+call.getArgument(2)));doAnswer(call->{saved.put(call.getArgument(0)+":"+call.getArgument(1)+":"+call.getArgument(2),call.getArgument(3));return null;}).when(config).setConfiguration(anyString(),anyString(),anyString(),anyString());book=new TravelNotebook(config,new RunewardenDataStore(config,new Gson()));book.bind("rsprofile.a");}
    }
}
