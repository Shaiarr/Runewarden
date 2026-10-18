/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Historical portfolio movement and quantity-only estimates are intentionally separate. */
final class BankMemoryDiff
{
    static final class Change
    {
        final int id;final String name;final long before,after,delta,oldPrice,newPrice,quantityValue,valueDelta;
        Change(int id,BankMemorySnapshot.Item a,BankMemorySnapshot.Item b)
        {
            this.id=id;name=b==null?a.name:b.name;before=a==null?0:a.quantity;after=b==null?0:b.quantity;delta=after-before;
            oldPrice=a==null?0:a.price;newPrice=b==null?0:b.price;
            quantityValue=BankMemorySnapshot.multiply(delta,b==null?oldPrice:newPrice);
            valueDelta=BankMemorySnapshot.add(BankMemorySnapshot.multiply(after,newPrice),-BankMemorySnapshot.multiply(before,oldPrice));
        }
        boolean priceOnly(){return delta==0&&oldPrice!=newPrice;}
    }
    final BankMemorySnapshot before,after;final List<Change> changes;final long quantityValue,valueDelta;final int added,removed,changed,unpriced;
    BankMemoryDiff(BankMemorySnapshot before,BankMemorySnapshot after)
    {
        if(before==null||after==null)throw new IllegalArgumentException("Choose two snapshots");this.before=before;this.after=after;
        Set<Integer> ids=new TreeSet<>(before.items.keySet());ids.addAll(after.items.keySet());List<Change> rows=new ArrayList<>();long qValue=0;int plus=0,minus=0,edit=0,unknown=0;
        for(Integer id:ids)
        {
            Change change=new Change(id,before.items.get(id),after.items.get(id));
            if(change.before==0)plus++;else if(change.after==0)minus++;else if(change.delta!=0)edit++;
            if(change.delta!=0&&(change.after==0?change.oldPrice:change.newPrice)==0)unknown++;
            qValue=BankMemorySnapshot.add(qValue,change.quantityValue);if(change.delta!=0||change.priceOnly())rows.add(change);
        }
        rows.sort(Comparator.comparingLong((Change row)->Math.abs(row.quantityValue)).reversed().thenComparing(row->row.name,String.CASE_INSENSITIVE_ORDER).thenComparingInt(row->row.id));
        changes=Collections.unmodifiableList(rows);quantityValue=qValue;valueDelta=BankMemorySnapshot.add(after.value(),-before.value());added=plus;removed=minus;changed=edit;unpriced=unknown;
    }
    String csv()
    {
        StringBuilder text=new StringBuilder("item_id,item_name,before_quantity,after_quantity,quantity_delta,before_unit_ge,after_unit_ge,quantity_change_ge,stored_ge_change\r\n");
        for(Change row:changes)text.append(row.id).append(',').append(csvText(row.name)).append(',').append(row.before).append(',').append(row.after).append(',').append(row.delta).append(',').append(row.oldPrice).append(',').append(row.newPrice).append(',').append(row.quantityValue).append(',').append(row.valueDelta).append("\r\n");
        return text.toString();
    }
    static String csvText(String value)
    {String safe=value==null?"":value;if(!safe.isEmpty()&&"=+-@".indexOf(safe.charAt(0))>=0)safe="'"+safe;return "\""+safe.replace("\"","\"\"")+"\"";}
}
