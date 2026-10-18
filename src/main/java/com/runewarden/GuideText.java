/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;

/** Plain Swing text that measures its wrapped height against the real sidebar width. */
final class GuideText extends JTextArea
{
    private int widthHint;
    static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);

    GuideText(String text)
    {
        super(text == null ? "" : text);
        setFont(FONT);
        setForeground(new Color(240, 238, 232));
        setOpaque(false);
        setEditable(false);
        setFocusable(false);
        ((DefaultCaret) getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        setLineWrap(true);
        setWrapStyleWord(true);
        setAlignmentX(JComponent.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(4, 0, 5, 0));
        setMinimumSize(new Dimension(0, 20));
    }

    void setWrapWidth(int width) { widthHint = width; }

    /** Display labels must never move the viewport, including queued constructor caret work. */
    @Override public void scrollRectToVisible(Rectangle rectangle) { }

    private int contentWidth()
    {
        if (widthHint > 0) return Math.max(60, widthHint);
        Container parent = getParent();
        int width = parent == null ? getWidth() : parent.getWidth() - parent.getInsets().left - parent.getInsets().right;
        if (parent != null && parent.getLayout() instanceof BorderLayout)
        {
            BorderLayout layout = (BorderLayout) parent.getLayout();
            if (layout.getLayoutComponent(BorderLayout.CENTER) == this)
            {
                for (String direction : new String[] { BorderLayout.WEST, BorderLayout.EAST })
                {
                    Component sibling = layout.getLayoutComponent(direction);
                    if (sibling != null && sibling.isVisible()) width -= sibling.getPreferredSize().width + layout.getHgap();
                }
            }
        }
        return Math.max(60, width > 0 ? width : 190);
    }

    @Override public Dimension getPreferredSize()
    {
        int width = contentWidth();
        if (getWidth() != width) setSize(width, Short.MAX_VALUE);
        Dimension preferred = super.getPreferredSize();
        return new Dimension(width, preferred.height);
    }

    @Override public Dimension getMaximumSize()
    {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override public Dimension getMinimumSize()
    {
        return new Dimension(0, getPreferredSize().height);
    }
}
