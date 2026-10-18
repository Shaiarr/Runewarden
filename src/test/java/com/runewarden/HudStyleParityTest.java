/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import org.junit.Test;
import static org.junit.Assert.*;

/** Frame snapshots were captured from the unmodified 0.10.4 compiled classes. */
public class HudStyleParityTest
{
    @Test public void sharedPainterPreservesEveryPixelOfAllFramesAndLegacyPanel() throws Exception
    {
        String[] expected = {
            "40eb7d07f39df95ec866560e76d73ba35815a1bd8fb41d40bd3ec2f203a1c94b",
            "9d87295fc4854c5a26dd161a3dcdd8bbe777d1e176bbd53165efa1703eed3cd9",
            "d1a7b4626c8c19931ee8f7fadfb86a8bc84a4692f42a3073857ed658d9906830"
        };
        for (HudPreferences.Frame frame : HudPreferences.Frame.values())
        {
            BufferedImage image = image();
            Graphics2D graphics = graphics(image);
            try
            {
                HudStyle.panel(graphics, 230, 97, new HudPreferences.Style(100, 12, 67,
                    new Color(203, 171, 94), new Color(244, 233, 207), new Color(17, 31, 49),
                    HudPreferences.Visibility.ALWAYS, frame, HudPreferences.Values.CURRENT_MAX));
            }
            finally { graphics.dispose(); }
            assertEquals(frame.name(), expected[frame.ordinal()], digest(image));
        }
        BufferedImage image = image();
        Graphics2D graphics = graphics(image);
        try
        {
            HudStyle.panel(graphics, 230, 97, new RunewardenConfig()
            { @Override public int hudOpacity() { return 67; } });
        }
        finally { graphics.dispose(); }
        assertEquals("6b79df9747bad271d147ba0cf4e2f0ebf24e404dfa1b41d3a3a64acd76a3a63f", digest(image));
    }

    @Test public void sharedTextFitKeepsExistingEllipsisAndSmallWidthBehavior()
    {
        Graphics2D graphics = image().createGraphics();
        try
        {
            graphics.setFont(HudStyle.body(12));
            assertEquals("", HudStyle.fit(graphics, null, 0));
            assertEquals("abcdef", HudStyle.fit(graphics, "abcdef", graphics.getFontMetrics().stringWidth("abcdef")));
            assertEquals("ab…", HudStyle.fit(graphics, "abcdef", graphics.getFontMetrics().stringWidth("ab…")));
            assertEquals("a…", HudStyle.fit(graphics, "abcdef", 0));
            assertEquals("", HudStyle.fit(graphics, "", 0));
        }
        finally { graphics.dispose(); }
    }

    @Test public void sharedEncodingRetainsExistingWireFormatsAndStrictValidation()
    {
        assertEquals("RUNEWARDEN:UlcxfA", HudPreferences.share("RW1|"));
        assertEquals("RUNEWARDEN-LAYOUT:UlcxfA.UldQMXw", HudLayoutPositions.share("RW1|", "RWP1|"));
        assertEquals("RW1|", HudPreferences.unshare("RUNEWARDEN:UlcxfA"));
        assertArrayEquals(new String[] {"RW1|", "RWP1|"},
            HudLayoutPositions.unshare("RUNEWARDEN-LAYOUT:UlcxfA.UldQMXw"));
        assertTrue(HudPreferences.safeDecode("unsupported").isEmpty());
        try { HudPreferences.decode("unsupported"); fail("Shared input must still be rejected"); }
        catch (IllegalArgumentException expected) { }
    }

    private static BufferedImage image() { return new BufferedImage(280, 130, BufferedImage.TYPE_INT_ARGB); }
    private static Graphics2D graphics(BufferedImage image)
    {
        Graphics2D graphics = image.createGraphics();
        graphics.translate(7, 11);
        return graphics;
    }
    private static String digest(BufferedImage image) throws Exception
    {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
        {
            int pixel = image.getRGB(x, y);
            hash.update((byte) (pixel >>> 24)); hash.update((byte) (pixel >>> 16));
            hash.update((byte) (pixel >>> 8)); hash.update((byte) pixel);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : hash.digest()) hex.append(String.format("%02x", value & 255));
        return hex.toString();
    }
}
