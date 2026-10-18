/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Item;
import net.runelite.api.widgets.Widget;

/** Reads the native visible five-widget potion rows; no scripts, foreign plugin access or mutations. */
final class PotionStorageObservation
{
    private PotionStorageObservation() { }
    static Item[] read(Widget[] widgets)
    {
        if(widgets==null||widgets.length==0||widgets.length>2000)return null;
        List<Item> items=new ArrayList<>();boolean recognised=false;
        for(int i=0;i+1<widgets.length;i+=5)
        {
            Widget item=widgets[i],label=widgets[i+1];if(item==null)continue;
            int one=LoadoutItemVariants.oneDose(item.getItemId());if(one<1)continue;
            if(label==null)return null;
            String text=label.getText();if(text==null)return null;
            text=text.replaceAll("<[^>]*>","").trim();
            if(!text.matches("[0-9][0-9,]*(?: doses?)?"))return null;
            long doses;
            try{doses=Long.parseLong(text.replaceAll("[^0-9]",""));}catch(NumberFormatException e){return null;}
            if(doses>Integer.MAX_VALUE)return null;
            recognised=true;if(doses>0)items.add(new Item(one,(int)doses));
        }
        return recognised?items.toArray(new Item[0]):null;
    }
}
