/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.text.Caret;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;

/** Width-aware building blocks for RuneLite's narrow, scrollable sidebar. */
final class SidebarUi
{
    static final Color BACKGROUND = new Color(18, 19, 21);
    static final Color CARD = new Color(28, 29, 32);
    static final Color GOLD = new Color(224, 190, 113);
    static final Color TEXT = new Color(240, 238, 232);
    static final Color MUTED = new Color(173, 173, 170);
    private static final String INSTALLED = "runewarden.sidebar.layout";

    private SidebarUi() { }

    static JPanel column() { return new Column(); }

    static JPanel section(String title)
    {
        JPanel panel = column();
        panel.setOpaque(true); panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(83, 73, 48)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        if (title != null && !title.isEmpty()) panel.add(heading(title));
        return panel;
    }

    static JPanel row(Component... children)
    {
        JPanel panel = new JPanel(new GridLayout(1, Math.max(1, children.length), 6, 0));
        panel.setOpaque(false); panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (Component child : children) panel.add(child);
        return panel;
    }

    static GuideText label(String text)
    {
        GuideText label = new GuideText(text);
        label.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        return label;
    }

    static GuideText heading(String text)
    {
        GuideText label = label(text);
        label.setForeground(GOLD); label.setFont(GuideText.FONT.deriveFont(Font.BOLD));
        return label;
    }

    static JPanel field(String title, JComponent control)
    {
        JPanel panel = column();
        GuideText caption = label(title); caption.setForeground(MUTED);
        control.getAccessibleContext().setAccessibleName(title);
        caption.getAccessibleContext().setAccessibleName(title);
        panel.add(caption); panel.add(control);
        panel.setBorder(BorderFactory.createEmptyBorder(1, 0, 5, 0));
        return panel;
    }

    static JButton button(String text, Runnable action)
    {
        JButton button = new JButton(text);
        button.setFont(GuideText.FONT); button.setBackground(new Color(40, 40, 39));
        button.setForeground(GOLD); button.setFocusPainted(true);
        button.setMargin(new java.awt.Insets(6, 6, 6, 6));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMinimumSize(new Dimension(0, 32));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        button.addActionListener(event -> action.run());
        return button;
    }

    static JButton wrappedButton(String text,Runnable action)
    {
        JButton button=new WrappedButton(text);button.setFont(GuideText.FONT);button.setForeground(GOLD);button.setBackground(new Color(40,40,39));button.setFocusPainted(true);button.setMargin(new java.awt.Insets(6,6,6,6));button.setHorizontalAlignment(SwingConstants.LEFT);button.addActionListener(event->action.run());return button;
    }
    private static final class WrappedButton extends JButton
    {
        WrappedButton(String title){super("<html>"+title.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+"</html>");getAccessibleContext().setAccessibleName(title);}
        @Override public Dimension getPreferredSize()
        {
            Container parent=getParent();int width=parent==null||parent.getWidth()<=0?190:Math.max(60,parent.getWidth()-parent.getInsets().left-parent.getInsets().right);
            java.awt.Insets edge=getInsets();javax.swing.text.View view=(javax.swing.text.View)getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);int height=getFontMetrics(getFont()).getHeight();
            if(view!=null){view.setSize(Math.max(30,width-edge.left-edge.right),0);height=(int)Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS));}
            return new Dimension(width,height+edge.top+edge.bottom);
        }
        @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
        @Override public Dimension getMinimumSize(){return new Dimension(0,getPreferredSize().height);}
    }

    static JPanel disclosure(String title, JComponent content)
    {
        JPanel panel = column();
        JButton toggle = button("▸ " + title, () -> {});
        content.setVisible(false);
        toggle.addActionListener(event -> {
            content.setVisible(!content.isVisible());
            toggle.setText((content.isVisible() ? "▾ " : "▸ ") + title);
            panel.revalidate(); panel.repaint();
        });
        panel.add(toggle); panel.add(content);
        return panel;
    }

    /** Also catches content inserted later by a search, account refresh or timer. */
    static void install(Component component)
    {
        if (component instanceof JComponent)
        {
            JComponent swing = (JComponent) component;
            Container parent = swing.getParent();
            if (parent != null && parent.getLayout() instanceof BoxLayout
                && ((BoxLayout) parent.getLayout()).getAxis() == BoxLayout.Y_AXIS)
                swing.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (swing.getClientProperty(INSTALLED) != null) return;
            swing.putClientProperty(INSTALLED, Boolean.TRUE);
            if (swing instanceof JTextComponent && !(swing instanceof GuideText))
            {
                JTextComponent field = (JTextComponent) swing;
                Caret previous = field.getCaret();
                int dot = previous.getDot(), mark = previous.getMark();
                InputCaret caret = new InputCaret();
                caret.setBlinkRate(previous.getBlinkRate());
                if (previous instanceof DefaultCaret)
                    caret.setUpdatePolicy(((DefaultCaret) previous).getUpdatePolicy());
                field.setCaret(caret);
                caret.setDot(mark); caret.moveDot(dot);
                caret.setSelectionVisible(previous.isSelectionVisible());
            }
            if (swing instanceof javax.swing.AbstractButton)
            {
                javax.swing.AbstractButton action=(javax.swing.AbstractButton)swing;
                action.setFocusPainted(true);
                if(action.getAccessibleContext().getAccessibleName()==null)
                    action.getAccessibleContext().setAccessibleName(action.getText());
                action.addFocusListener(new java.awt.event.FocusAdapter()
                {
                    private javax.swing.border.Border before;
                    @Override public void focusGained(java.awt.event.FocusEvent event)
                    {
                        if(before!=null)return;before=action.getBorder();
                        final javax.swing.border.Border original=before;
                        action.setBorder(new javax.swing.border.AbstractBorder()
                        {
                            @Override public java.awt.Insets getBorderInsets(Component c){return original==null?new java.awt.Insets(0,0,0,0):original.getBorderInsets(c);}
                            @Override public void paintBorder(Component c,java.awt.Graphics graphics,int x,int y,int width,int height)
                            {if(original!=null)original.paintBorder(c,graphics,x,y,width,height);graphics.setColor(GOLD);graphics.drawRect(x+1,y+1,Math.max(0,width-3),Math.max(0,height-3));}
                        });
                    }
                    @Override public void focusLost(java.awt.event.FocusEvent event) { if(before!=null){action.setBorder(before);before=null;} }
                });
            }
            if (swing instanceof JLabel) ((JLabel) swing).setHorizontalAlignment(SwingConstants.LEFT);
            if (swing instanceof JTextField || swing instanceof javax.swing.JComboBox)
            {
                swing.setMinimumSize(new Dimension(0, 28));
                swing.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            }
            if (swing instanceof JCheckBox)
                ((JCheckBox) swing).setHorizontalAlignment(SwingConstants.LEFT);
        }
        if (component instanceof Container)
        {
            Container container = (Container) component;
            for (Component child : container.getComponents()) install(child);
            container.addContainerListener(new ContainerAdapter()
            {
                @Override public void componentAdded(ContainerEvent event) { install(event.getChild()); }
            });
        }
    }

    /** Only the field being edited may scroll to its caret; background refreshes stay put. */
    private static final class InputCaret extends DefaultCaret
    {
        @Override protected void adjustVisibility(java.awt.Rectangle bounds)
        {
            JTextComponent field = getComponent();
            if (field != null && field.isShowing() && field.isFocusOwner()) super.adjustVisibility(bounds);
        }
    }

    private static final class Column extends JPanel
    {
        Column()
        {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        @Override protected void addImpl(Component component, Object constraints, int index)
        {
            if (component instanceof JComponent)
                ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
            super.addImpl(component, constraints, index);
            install(component);
        }
        @Override public Dimension getMinimumSize()
        { return new Dimension(0, getPreferredSize().height); }
        @Override public Dimension getMaximumSize()
        { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    }
}
