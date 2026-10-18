/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Locale;

/** Local character identity used to discard observations when the player changes. */
final class PlayerIdentity
{
    private PlayerIdentity() { }

    static String normalizedName(String name)
    {
        return name == null ? "" : name.replace('_', ' ').replace('\u00a0', ' ')
            .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
