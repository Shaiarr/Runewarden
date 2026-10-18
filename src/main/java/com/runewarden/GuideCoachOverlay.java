/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Movable coach card mirroring only the player's selected guide step. */
@Singleton
final class GuideCoachOverlay extends Overlay implements MouseListener
{
    private static final int WIDTH = 264;
    private static final int PADDING = 12;
    private final Client client;
    private final RunewardenConfig config;
    private volatile Card card;
    private volatile boolean visible = true;
    private final MouseManager mouse;
    private boolean started,swallowClick;
    private volatile Runnable closeAction=()->{};
    private volatile Rectangle closeBounds=new Rectangle();

    private static final class Card
    {
        final String title, section, body;
        final int position, total,completed;
        Card(String title, String section, String body, int position, int total,int completed)
        {
            this.title = title;
            this.section = section;
            this.body = body;
            this.position = position;
            this.total = total;this.completed=completed;
        }
    }

    GuideCoachOverlay(Client client) { this(client, new RunewardenConfig() {}); }

    GuideCoachOverlay(Client client,RunewardenConfig config){this(client,config,null);}
    @Inject GuideCoachOverlay(Client client, RunewardenConfig config,MouseManager mouse)
    {
        this.client = client;
        this.config = config;this.mouse=mouse;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setMovable(true);
        setResizable(false);
        addMenuEntry(MenuAction.RUNELITE_OVERLAY,"Close guide","Runewarden",event->closeAction.run());
    }

    void select(String title, String section, String body, int position, int total)
    {
        select(title,section,body,position,total,position);
    }
    void select(String title,String section,String body,int position,int total,int completed)
    {card=new Card(title,section,body,position,total,completed);}
    String instruction(){Card current=card;return current==null?"":current.body;}
    void setCloseAction(Runnable action){closeAction=action==null?()->{}:action;}
    void start(){if(!started&&mouse!=null){started=true;mouse.registerMouseListener(this);}}
    void stop(){if(started&&mouse!=null)mouse.unregisterMouseListener(this);started=false;swallowClick=false;closeBounds=new Rectangle();}
    void clear() { card = null;closeBounds=new Rectangle(); }
    void setVisible(boolean value) { visible = value;if(!value)closeBounds=new Rectangle(); }

    /** Pixel bounds remain correct for wide glyphs, long item names and unbroken tokens. */
    static List<String> wrap(FontMetrics metrics, String text, int width, int maximum)
    {
        List<String> lines = new ArrayList<>();
        String remaining = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (width <= 0 || maximum <= 0) return lines;
        while (!remaining.isEmpty() && lines.size() < maximum)
        {
            if (metrics.stringWidth(remaining) <= width) { lines.add(remaining); remaining = ""; break; }
            int end = 1;
            while (end < remaining.length() && metrics.stringWidth(remaining.substring(0, end + 1)) <= width) end++;
            int space = remaining.lastIndexOf(' ', end);
            if (space > 0) end = space;
            lines.add(remaining.substring(0, end).trim());
            remaining = remaining.substring(end).trim();
        }
        if (!remaining.isEmpty() && !lines.isEmpty())
        {
            int last = lines.size() - 1;
            String line = lines.get(last);
            while (!line.isEmpty() && metrics.stringWidth(line + "…") > width) line = line.substring(0, line.length() - 1);
            lines.set(last, line.stripTrailing() + "…");
        }
        return lines;
    }

    void setHudLocked(boolean locked) { setMovable(!locked); }

    @Override public Dimension render(Graphics2D graphics)
    {
        HudPreferences.Style style = HudStyle.settings(config, "guide");
        if (!HudStyle.visible(style)) {closeBounds=new Rectangle();return null;}
        Card selected = card;
        if (!visible || !config.guideHud() || selected == null || client.getGameState() != GameState.LOGGED_IN) {closeBounds=new Rectangle();return null;}
        Graphics2D g = HudStyle.canvas(graphics, style);
        try
        {
            g.setFont(HudStyle.label(12));
            List<String> titles = wrap(g.getFontMetrics(), selected.title, WIDTH - PADDING * 2, 2);
            g.setFont(HudStyle.body(12));
            List<String> sections = wrap(g.getFontMetrics(), selected.section, WIDTH - PADDING * 2, 1);
            List<String> body = wrap(g.getFontMetrics(), selected.body, WIDTH - PADDING * 2, config.hudCompact() ? 5 : 10);
            int contentHeight = 39 + (titles.size() + sections.size() + body.size()) * 16 + (selected.total > 0 ? 26 : 0);
            int height=contentHeight+24;
            HudStyle.panel(g, WIDTH, height, style);
            g.setFont(HudStyle.label(12));
            g.setColor(style.accent);
            g.drawString("GUIDE", PADDING, 20);
            String step = selected.total > 0 ? Math.max(1, selected.position) + " / " + selected.total : "";
            g.drawString(step, WIDTH - PADDING - g.getFontMetrics().stringWidth(step), 20);
            int y = 42;
            g.setColor(style.text);
            for (String line : titles) { g.drawString(line, PADDING, y); y += 16; }
            g.setFont(HudStyle.body(12));
            g.setColor(HudStyle.muted(style));
            for (String line : sections) { g.drawString(line, PADDING, y); y += 16; }
            g.setColor(style.text);
            for (String line : body) { g.drawString(line, PADDING, y); y += 16; }
            if (selected.total > 0)
            {
                g.setFont(HudStyle.body(10));
                g.setColor(HudStyle.muted(style));
                g.drawString("Completed steps",PADDING,contentHeight-22);
                g.setColor(style.accent.darker().darker());
                g.fillRoundRect(PADDING, contentHeight - 15, WIDTH - PADDING * 2, 8, 6, 6);
                g.setColor(style.accent);
                int progress = (int) ((WIDTH - PADDING * 2) * Math.min(1d, Math.max(0d, (double) selected.completed / selected.total)));
                if(progress>0)g.fillRoundRect(PADDING, contentHeight - 15, progress, 8, 6, 6);
            }
            g.setFont(HudStyle.label(10));g.setColor(style.accent);
            g.drawRoundRect(WIDTH-94,height-23,82,18,5,5);g.drawString("Close guide",WIDTH-84,height-10);
            double scale=style.scale/100d*style.font/12d;
            Rectangle bounds=getBounds();
            closeBounds=new Rectangle(bounds.x+(int)Math.floor((WIDTH-94)*scale),bounds.y+(int)Math.floor((height-23)*scale),
                (int)Math.ceil(83*scale),(int)Math.ceil(19*scale));
            return HudStyle.dimension(style, WIDTH, height);
        }
        finally { g.dispose(); }
    }
    @Override public MouseEvent mousePressed(MouseEvent event)
    {
        if(event.getButton()==MouseEvent.BUTTON1&&!event.isControlDown()&&!event.isAltDown()
            &&card!=null&&visible&&config.guideHud()&&client.getGameState()==GameState.LOGGED_IN&&closeBounds.contains(event.getPoint()))
        {swallowClick=true;event.consume();closeAction.run();}
        return event;
    }
    @Override public MouseEvent mouseReleased(MouseEvent event){if(swallowClick)event.consume();return event;}
    @Override public MouseEvent mouseClicked(MouseEvent event){if(swallowClick){event.consume();swallowClick=false;}return event;}
    @Override public MouseEvent mouseEntered(MouseEvent event){return event;}
    @Override public MouseEvent mouseExited(MouseEvent event){return event;}
    @Override public MouseEvent mouseDragged(MouseEvent event){return event;}
    @Override public MouseEvent mouseMoved(MouseEvent event){return event;}

}
