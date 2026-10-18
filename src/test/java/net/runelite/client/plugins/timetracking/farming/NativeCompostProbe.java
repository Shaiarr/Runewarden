/* SPDX-License-Identifier: GPL-3.0-only */
package net.runelite.client.plugins.timetracking.farming;

/** Test-only bridge to compare message evidence with the exact pinned RuneLite implementation. */
public final class NativeCompostProbe
{
    private NativeCompostProbe() { }
    public static String parse(String message)
    { CompostState result=CompostTracker.determineCompostUsed(message);return result==null?null:result.name(); }
}
