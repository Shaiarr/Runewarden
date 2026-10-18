/* SPDX-License-Identifier: BSD-2-Clause */
package shortestpath;

import net.runelite.client.plugins.Plugin;

/**
 * Identity-only fixture for Runewarden's public plugin-discovery and message tests.
 *
 * This class has no routing, transport, overlay, or game implementation. It is
 * compiled only into the test source set and is never included in the plugin JAR.
 * Tests of actual route calculation live in integration-tests and require the
 * separately supplied, unmodified upstream Shortest Path JAR.
 */
public final class ShortestPathPlugin extends Plugin
{
}
