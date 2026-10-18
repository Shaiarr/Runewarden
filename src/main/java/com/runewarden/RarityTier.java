/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;

/** Ordered presentation tiers; probability classification belongs to RewardSettings. */
public enum RarityTier
{
    WHITE("White", new Color(241, 244, 248)),
    GREEN("Green", new Color(92, 221, 137)),
    BLUE("Blue", new Color(92, 172, 255)),
    PURPLE("Purple", new Color(190, 126, 255)),
    GOLD("Gold", new Color(255, 210, 100));

    private final String label;
    private final Color color;
    RarityTier(String label, Color color) { this.label = label; this.color = color; }
    public String label() { return label; }
    public Color color() { return color; }
}
