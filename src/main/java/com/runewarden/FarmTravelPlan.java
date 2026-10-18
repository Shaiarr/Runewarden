/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/** Per-stop travel choices: reusable equipment, consumed supplies and explicit charge budgets. */
final class FarmTravelPlan
{
    /** A selected Shortest Path result, captured on the client thread for the farm sidebar. */
    static final class RouteAdvice
    {
        static final RouteAdvice EMPTY=new RouteAdvice(null,null,null,false,false,false,Collections.emptyList());
        final String profile;final WorldPoint origin,target;final boolean owned,answered,reached;final List<String> transports;
        RouteAdvice(String profile,WorldPoint origin,WorldPoint target,boolean owned,boolean answered,boolean reached,List<String> transports)
        {this.profile=profile;this.origin=origin;this.target=target;this.owned=owned;this.answered=answered;this.reached=reached;this.transports=Collections.unmodifiableList(new java.util.ArrayList<>(transports));}
        String recommendation(String currentProfile,WorldPoint destination)
        {
            if(profile==null||!profile.equals(currentProfile)||origin==null||destination==null)return "";
            if(origin.getPlane()==destination.getPlane()&&origin.distanceTo2D(destination)<=8)return "Nearby · walk to patch";
            if(!owned||!answered||!reached||!destination.equals(target))return "";
            java.util.List<String> selected=new java.util.ArrayList<>();
            for(String transport:transports)if(transport!=null&&!transport.isBlank()&&selected.size()<6)selected.add(transport);
            return selected.isEmpty()?"Walk to patch":String.join(" → ",selected);
        }
    }
    static final class Stop
    {
        String label = "Walk / existing route";
        Map<Integer, Integer> carried = new LinkedHashMap<>(), consumed = new LinkedHashMap<>();
        String chargePool = ""; int chargesNeeded, availableCharges = -1; long coins;
        boolean accessConfirmed;
    }
    static final class Summary
    {
        final Map<Integer, Long> required, consumed; final Map<String, Integer> charges, available;
        final long coins; final int chargeShortfalls, uncheckedCharges;
        Summary(Map<Integer, Long> required, Map<Integer, Long> consumed, Map<String, Integer> charges, Map<String, Integer> available, long coins)
        {
            this.required = Collections.unmodifiableMap(required); this.consumed = Collections.unmodifiableMap(consumed);
            this.charges = Collections.unmodifiableMap(charges); this.available = Collections.unmodifiableMap(available); this.coins = coins;
            int shortfall = 0, unknown = 0; for (Map.Entry<String, Integer> charge : charges.entrySet()) { Integer stock = available.get(charge.getKey()); if (stock == null || stock < 0) unknown++; else shortfall += Math.max(0, charge.getValue() - stock); }
            chargeShortfalls = shortfall; uncheckedCharges = unknown;
        }
    }
    static boolean valid(Stop stop)
    {
        return stop != null && stop.label != null && stop.label.length() <= 100 && stop.label.indexOf('<') < 0 && stop.label.indexOf('>') < 0
            && stop.chargePool != null && stop.chargePool.length() <= 80 && stop.chargePool.indexOf('<') < 0 && stop.chargePool.indexOf('>') < 0
            && stop.chargesNeeded >= 0 && stop.chargesNeeded <= 1000 && stop.availableCharges >= -1 && stop.availableCharges <= 100_000
            && (stop.chargesNeeded == 0 || !stop.chargePool.isBlank())
            && stop.coins >= 0 && stop.coins <= 10_000_000 && validItems(stop.carried) && validItems(stop.consumed);
    }
    private static boolean validItems(Map<Integer, Integer> items)
    { return items != null && items.size() <= 12 && items.entrySet().stream().allMatch(entry -> entry.getKey() != null && entry.getKey() > 0 && entry.getKey() <= 1_000_000 && entry.getValue() != null && entry.getValue() > 0 && entry.getValue() <= 100_000); }
    static Stop copy(Stop stop)
    {
        Stop next = new Stop(); next.label = stop.label; next.carried = new LinkedHashMap<>(stop.carried); next.consumed = new LinkedHashMap<>(stop.consumed);
        next.chargePool = stop.chargePool; next.chargesNeeded = stop.chargesNeeded; next.availableCharges = stop.availableCharges; next.coins = stop.coins; next.accessConfirmed = stop.accessConfirmed; return next;
    }
    static Summary summarize(List<Stop> stops)
    {
        Map<Integer, Long> reusable = new LinkedHashMap<>(), consumed = new LinkedHashMap<>(); Map<String, Integer> charges = new LinkedHashMap<>(), available = new LinkedHashMap<>(); long coins = 0;
        for (Stop stop : stops)
        {
            if (!valid(stop)) throw new IllegalArgumentException("Invalid travel stop");
            stop.carried.forEach((id, quantity) -> reusable.merge(id, quantity.longValue(), Math::max));
            stop.consumed.forEach((id, quantity) -> consumed.merge(id, quantity.longValue(), Long::sum)); coins += stop.coins;
            if (stop.chargesNeeded > 0 && !stop.chargePool.isBlank())
            { charges.merge(stop.chargePool, stop.chargesNeeded, Integer::sum); if (stop.availableCharges >= 0) available.merge(stop.chargePool, stop.availableCharges, Math::min); }
        }
        Map<Integer, Long> required = new LinkedHashMap<>(consumed); reusable.forEach((id, quantity) -> required.merge(id, quantity, Long::sum));
        if (coins > 0) required.merge(995, coins, Long::sum);
        return new Summary(required, consumed, charges, available, coins);
    }
}
