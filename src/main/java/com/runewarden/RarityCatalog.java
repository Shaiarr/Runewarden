/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Offline reference metrics. Sample ownership and source drop probability remain separate. */
@Singleton
public final class RarityCatalog
{
    public static final class Result
    {
        public final boolean exactOdds;
        public final Double denominator;
        public final String oddsText, source, basis, wiki;
        public final Double ownershipPercent;
        public final String ownershipText;
        public final String populationLabel, snapshotDate;
        Result(boolean exactOdds, Double denominator, String oddsText, String source, String basis,
            String wiki, Double ownershipPercent, String ownershipText, String populationLabel, String snapshotDate)
        {
            this.exactOdds = exactOdds; this.denominator = denominator; this.oddsText = oddsText;
            this.source = source; this.basis = basis; this.wiki = wiki;
            this.ownershipPercent = ownershipPercent; this.ownershipText = ownershipText;
            this.populationLabel = populationLabel; this.snapshotDate = snapshotDate;
        }
    }
    private static final class Rate
    {
        String source;
        double probability;
        Double maximumProbability;
        boolean conditional;
        String basis;
        String oddsText;
    }
    private static final class Entry
    {
        int id;
        String name;
        Double ownershipPercent;
        List<Rate> rates;
    }
    private static final class Data
    {
        String source, snapshotDate, populationLabel;
        List<Entry> items;
    }
    private final Data data;
    private final Map<Integer, Entry> items = new HashMap<>();
    private final Map<String, Integer> names = new HashMap<>();

    @Inject RarityCatalog(Gson gson)
    {
        try (InputStream stream = getClass().getResourceAsStream("/com/runewarden/rarity-catalog.json"))
        {
            if (stream == null) throw new IllegalStateException("Missing rarity reference data");
            data = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Data.class);
            if (data == null || data.items == null || data.snapshotDate == null || data.populationLabel == null)
                throw new IllegalStateException("Invalid rarity reference data");
        }
        catch (java.io.IOException error) { throw new IllegalStateException("Could not load rarity reference data", error); }
        for (Entry entry : data.items)
        {
            if (entry == null || entry.id <= 0 || entry.name == null || entry.name.trim().isEmpty()) continue;
            items.put(entry.id, entry);
            String key = normalize(entry.name);
            names.merge(key, entry.id, (oldId, newId) -> oldId.equals(newId) ? oldId : -1);
            // Collection-log chat sometimes omits only the charge-state suffix. Keep ambiguous aliases unknown.
            String uncharged = entry.name.replaceFirst("(?i) \\(uncharged\\)$", "");
            if (!uncharged.equals(entry.name)) names.merge(normalize(uncharged), entry.id, (oldId, newId) -> oldId.equals(newId) ? oldId : -1);
        }
    }

    public Result forItem(int itemId) { return classify(itemId, null); }

    public Result classify(int itemId, String source)
    {
        Entry entry = items.get(itemId);
        Double ownership = entry == null ? null : entry.ownershipPercent;
        if (ownership != null && (!Double.isFinite(ownership) || ownership < 0 || ownership > 100)) ownership = null;
        String ownershipText = ownership == null ? "" : "Owned by " + (ownership == 0 ? "<0.1" : number(ownership))
            + "% of " + data.populationLabel + " (snapshot " + data.snapshotDate + ")";
        String wiki = itemId > 0 ? "https://oldschool.runescape.wiki/w/Special:Lookup?type=item&id=" + itemId
            : "https://oldschool.runescape.wiki/w/Collection_log/Table";
        if (entry == null || entry.rates == null || entry.rates.isEmpty())
            return result(false, null, "", source, "No catalogued source rate", wiki, ownership, ownershipText);
        List<Rate> candidates = new ArrayList<>();
        if (source == null || source.trim().isEmpty()) candidates.addAll(entry.rates);
        else
        {
            String key = sourceKey(source);
            for (Rate rate : entry.rates) if (sourceKey(rate.source).equals(key)) candidates.add(rate);
        }
        if (candidates.size() != 1)
            return result(false, null, candidates.isEmpty() ? "" : "Varies by source", source,
                candidates.isEmpty() ? "Source not catalogued for this item" : "Source is not uniquely identified",
                wiki, ownership, ownershipText);
        Rate rate = candidates.get(0);
        if (!Double.isFinite(rate.probability) || rate.probability <= 0 || rate.probability > 1)
            return result(false, null, "", rate.source, "Rate unavailable", wiki, ownership, ownershipText);
        if (rate.conditional || rate.maximumProbability != null)
            return result(false, null, rate.oddsText == null ? "Conditional rate" : rate.oddsText,
                rate.source, rate.basis == null ? "Chance depends on unobserved conditions" : rate.basis,
                wiki, ownership, ownershipText);
        return result(true, denominator(rate.probability), rate.oddsText == null ? odds(rate.probability) : rate.oddsText,
            rate.source, rate.basis == null ? "Wiki source reference chance" : rate.basis, wiki, ownership, ownershipText);
    }

    public int itemId(String name) { return names.getOrDefault(normalize(name), -1); }
    public String itemName(int id) { Entry entry = items.get(id); return entry == null ? "Item " + id : entry.name; }
    int size() { return items.size(); }
    String snapshotDate() { return data.snapshotDate; }

    private Result result(boolean exact, Double denominator, String oddsText, String source, String basis,
        String wiki, Double ownership, String ownershipText)
    {
        return new Result(exact, denominator, oddsText, source == null ? "" : source, basis, wiki,
            ownership, ownershipText, data.populationLabel, data.snapshotDate);
    }
    private static String sourceKey(String text)
    {
        String key = normalize(text == null ? "" : text.replaceFirst("(?i) \\(standard\\)$", ""));
        if (key.equals("chambers of xeric") || key.equals("chambers of xeric challenge mode")) return "ancient chest";
        if (key.equals("theatre of blood") || key.equals("theatre of blood hard mode") || key.equals("theatre of blood entry mode")) return "monumental chest";
        if (key.equals("tombs of amascut") || key.equals("tombs of amascut expert mode") || key.equals("tombs of amascut entry mode")) return "chest tombs of amascut";
        if (key.equals("barrows")) return "chest barrows";
        if (key.equals("the gauntlet")) return "gauntlet";
        if (key.equals("the corrupted gauntlet")) return "corrupted gauntlet";
        return key;
    }
    private static String normalize(String text)
    {
        return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
            .replace('’', '\'').replaceAll("[^a-z0-9]+", " ").trim();
    }
    private static double denominator(double probability)
    {
        double value = 1.0 / probability, integer = Math.rint(value);
        return Math.abs(value - integer) <= 1e-9 * Math.max(1, value) ? integer : value;
    }
    private static String odds(double probability) { return probability == 1 ? "Always" : "1/" + number(1 / probability); }
    private static String number(double number)
    {
        double rounded = Math.rint(number);
        if (Math.abs(number - rounded) < 0.00001) return String.format(Locale.ROOT, "%,.0f", rounded);
        return String.format(Locale.ROOT, "%,.2f", number).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
