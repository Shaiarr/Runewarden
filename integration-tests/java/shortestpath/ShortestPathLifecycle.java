/* SPDX-License-Identifier: GPL-3.0-only */
package shortestpath;

/** Test-only access to the upstream protected plugin lifecycle; no substituted plugin implementation. */
public final class ShortestPathLifecycle
{
    private ShortestPathLifecycle() { }
    public static void start(ShortestPathPlugin plugin) { plugin.startUp(); }
    public static void stop(ShortestPathPlugin plugin) { plugin.shutDown(); }
}
