/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Local historical native bank observation, never a claim of carried or currently available supplies. */
final class BankMemorySnapshot
{
    static final int MAX_ITEMS=2500;
    static final long MAX_QUANTITY=5_400_000_000_000L,MAX_PRICE=1_000_000_000_000L;
    static final class Item
    {
        final int id;final String name;final long quantity,price;
        Item(int id,String name,long quantity,long price)
        {if(id<1||id>1_000_000||name==null||name.length()>100||quantity<1||quantity>MAX_QUANTITY||price<0||price>MAX_PRICE)throw new IllegalArgumentException("Invalid bank item");this.id=id;this.name=clean(name,100);this.quantity=quantity;this.price=price;}
        Item checked(){return new Item(id,name,quantity,price);}
    }
    final String id,name;final long capturedAt,observedAt;final Map<Integer,Item> items;
    BankMemorySnapshot(String id,String name,long captured,long observed,Map<Integer,Item> items)
    {
        if(id==null||!id.matches("[A-Za-z0-9_-]{1,64}")||name==null||name.trim().isEmpty()||name.length()>80||captured<1||observed<1||observed>captured||items==null||items.size()>MAX_ITEMS)throw new IllegalArgumentException("Invalid bank snapshot");
        this.id=id;this.name=clean(name,80);if(this.name.isEmpty())throw new IllegalArgumentException("Enter a snapshot name");capturedAt=captured;observedAt=observed;Map<Integer,Item> safe=new LinkedHashMap<>();
        for(Map.Entry<Integer,Item> entry:items.entrySet()){if(entry.getKey()==null||entry.getValue()==null||entry.getKey()!=entry.getValue().id)throw new IllegalArgumentException("Invalid snapshot item key");safe.put(entry.getKey(),entry.getValue().checked());}this.items=Collections.unmodifiableMap(safe);
    }
    BankMemorySnapshot checked(){return new BankMemorySnapshot(id,name,capturedAt,observedAt,items);}
    BankMemorySnapshot renamed(String name){return new BankMemorySnapshot(id,name,capturedAt,observedAt,items);}
    long quantity(){long total=0;for(Item item:items.values())total=add(total,item.quantity);return total;}
    long value(){long total=0;for(Item item:items.values())total=add(total,multiply(item.quantity,item.price));return total;}
    int unpriced(){int count=0;for(Item item:items.values())if(item.price==0)count++;return count;}
    boolean saturated(){return value()==Long.MAX_VALUE;}
    static long multiply(long quantity,long price){if(quantity==0||price==0)return 0;if(quantity>0)return quantity>Long.MAX_VALUE/price?Long.MAX_VALUE:quantity*price;return quantity< -Long.MAX_VALUE/price?-Long.MAX_VALUE:quantity*price;}
    static long add(long a,long b){if(b>0&&a>Long.MAX_VALUE-b)return Long.MAX_VALUE;if(b<0&&a< -Long.MAX_VALUE-b)return -Long.MAX_VALUE;return a+b;}
    static String clean(String text,int maximum){String value=net.runelite.client.util.Text.removeTags(text).replace('\n',' ').replace('\r',' ').replace('\t',' ').trim();return value.substring(0,Math.min(maximum,value.length()));}
    @Override public String toString(){return name;}
}
