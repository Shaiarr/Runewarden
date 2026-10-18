/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Objects;

/** One observed reward. Drop odds and ownership remain separate, optional reference facts. */
public final class RewardNotification
{
    public enum Kind { COLLECTION_LOG, RARE_DROP, MUSIC_UNLOCK, COMBAT_ACHIEVEMENT, DEMO }
    public final Kind kind;
    public final String title, itemName, detail, source, oddsText, ownershipText;
    public final int itemId, quantity;
    public final RarityTier tier;
    public final boolean rarityKnown;
    public final long createdAtMillis;

    public RewardNotification(Kind kind, String title, String itemName, int itemId, int quantity,
        RarityTier tier, String detail, String source, String oddsText, String ownershipText, boolean rarityKnown)
    {
        this.kind = Objects.requireNonNull(kind); this.tier = tier == null ? RarityTier.WHITE : tier;
        this.title = clean(title, 80); this.itemName = clean(itemName, 160);
        this.detail = clean(detail, 240); this.source = clean(source, 160);
        this.oddsText = clean(oddsText, 120); this.ownershipText = clean(ownershipText, 120);
        this.itemId = Math.max(-1, itemId); this.quantity = Math.max(1, quantity);
        this.rarityKnown = rarityKnown; createdAtMillis = System.currentTimeMillis();
    }

    private static String clean(String text, int max)
    {
        if (text == null) return "";
        String value = text.replaceAll("<[^>]*>", "").replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }
}
