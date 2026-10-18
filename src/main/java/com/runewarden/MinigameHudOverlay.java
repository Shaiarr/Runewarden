/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Native facts, plus current skilling guidance only where the release policy permits it. */
final class MinigameHudOverlay extends Overlay
{
    private static final int WIDTH = 284;
    private final Client client;
    private final MinigameCoach coach;
    private final RunewardenConfig config;
    private MinigameActivityCatalog.Snapshot cached;
    private Card card;
    private boolean layoutCompact;
    private RunewardenConfig.TimerDisplay layoutTimerDisplay;
    private static final RunewardenConfig THEME = new RunewardenConfig() {};
    private static final class Card
    {
        final List<String> title, action, body, facts;
        final int height;
        Card(List<String> title, List<String> action, List<String> body, List<String> facts,int clockCount)
        {
            this.title = title; this.action = action; this.body = body; this.facts = facts;
            height = 47 + title.size() * 16 + facts.size() * 16 + action.size() * 16 + body.size() * 16 + clockCount * 32;
        }
    }
    MinigameHudOverlay(Client client, MinigameCoach coach)
    {this(client,coach,THEME);}
    MinigameHudOverlay(Client client,MinigameCoach coach,RunewardenConfig config)
    {
        this.client = client; this.coach = coach;this.config=config;
        setPosition(OverlayPosition.TOP_LEFT); setLayer(OverlayLayer.ABOVE_WIDGETS); setMovable(true); setResizable(false);
    }
    void setHudLocked(boolean locked) { setMovable(!locked); }
    @Override public Dimension render(Graphics2D graphics)
    {
        MinigameActivityCatalog.Snapshot snapshot = coach.reading(); MinigameCoach.Preferences preferences = coach.preferences();
        if (!coach.started() || coach.guidanceDismissed() || !preferences.autoHud || snapshot.activity == null || client.getGameState() != GameState.LOGGED_IN) return null;
        HudPreferences.Style style=HudStyle.settings(config,"minigame");if(!HudStyle.visible(style))return null;
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            RunewardenConfig.TimerDisplay timers=config.minigameTimerDisplay();if(timers==null)timers=RunewardenConfig.TimerDisplay.TEXT;
            List<MinigameCountdowns.Clock> clocks=timers==RunewardenConfig.TimerDisplay.TEXT?List.of():coach.countdowns();
            if (snapshot != cached||layoutCompact!=preferences.compactHud||layoutTimerDisplay!=timers) { cached = snapshot;layoutCompact=preferences.compactHud;layoutTimerDisplay=timers;card = layout(g, snapshot, preferences.compactHud,timers,clocks); }
            double scale = Math.min(preferences.scalePercent / 100d * style.scale / 100d * style.font / 12d,
                Math.min(Math.max(.5, (client.getCanvasWidth() > 0 ? client.getCanvasWidth() - 16 : 484) / (double) WIDTH),
                    Math.max(.5, (client.getCanvasHeight() > 0 ? client.getCanvasHeight() - 24 : 476) / (double) card.height)));
            g.scale(scale, scale); HudStyle.panel(g, WIDTH, card.height, style);
            g.setFont(HudStyle.label(10)); g.setColor(style.accent); g.drawString("MINIGAME", 12, 19);
            String mode = MinigameReleasePolicy.nativeFactsOnly(snapshot.activity) ? "STATUS" : snapshot.liveSupported ? "COACH" : "GUIDE";
            g.drawString(mode, WIDTH - 12 - g.getFontMetrics().stringWidth(mode), 19);
            int y = 40; g.setFont(HudStyle.label(12)); g.setColor(style.text);
            for (String line : card.title) { g.drawString(line, 12, y); y += 16; }
            g.setFont(HudStyle.body(11)); g.setColor(style.muted);
            for (String line : card.facts) { g.drawString(line, 12, y); y += 16; }
            for(MinigameCountdowns.Clock clock:clocks)
            {
                RadialTimer.paint(g,24,y+2,12,clock.remainingFraction(),style.accent,null,false);
                g.setFont(HudStyle.body(11));g.setColor(style.text);g.drawString(clock.label,44,y+6);
                if(timers==RunewardenConfig.TimerDisplay.BOTH)g.drawString(clock.text(),WIDTH-12-g.getFontMetrics().stringWidth(clock.text()),y+6);
                y+=32;
            }
            y += 5; g.setFont(HudStyle.label(12)); g.setColor(style.accent);
            for (String line : card.action) { g.drawString(line, 12, y); y += 16; }
            g.setFont(HudStyle.body(12)); g.setColor(style.text);
            for (String line : card.body) { g.drawString(line, 12, y); y += 16; }
            return new Dimension((int) Math.ceil(WIDTH * scale), (int) Math.ceil(card.height * scale));
        }
        finally { g.dispose(); }
    }
    private static int foundryPriority(String label,MinigameActivityCatalog.Snapshot snapshot)
    {
        boolean mould=snapshot.steps.stream().anyMatch(step->step.title.equals("Set the mould"));
        if(mould)return label.equals("Commission")?0:label.equals("Forte")?1:label.equals("Blade")?2:label.equals("Tip")?3:label.equals("Best owned mould score")?4:10;
        return label.equals("Bonus")?0:label.equals("Actions to next tool")?1:label.equals("Actions before heat change")?2:label.equals("Estimated heat adjustment")?2:label.equals("Heat")?3:label.equals("Progress")?4:label.equals("Section")?5:10;
    }
    private static Card layout(Graphics2D g, MinigameActivityCatalog.Snapshot snapshot,boolean compact,RunewardenConfig.TimerDisplay timers,List<MinigameCountdowns.Clock> clocks)
    {
        g.setFont(HudStyle.label(12)); List<String> title = GuideCoachOverlay.wrap(g.getFontMetrics(), snapshot.title, WIDTH - 24, 2);
        List<String> facts = new ArrayList<>(); g.setFont(HudStyle.body(11));
        List<MinigameActivityCatalog.Fact> ordered=new ArrayList<>(snapshot.facts);
        if(snapshot.activity==MinigameActivityCatalog.Activity.GIANTS_FOUNDRY)
            ordered.sort(java.util.Comparator.comparingInt(f->foundryPriority(f.label,snapshot)));
        if(snapshot.activity==MinigameActivityCatalog.Activity.MAGE_TRAINING_ARENA)
            ordered.sort(java.util.Comparator.comparingInt(f->f.label.equals("Pulls remaining")?0:f.label.equals("Pull sequence")?1:2));
        for (MinigameActivityCatalog.Fact fact : ordered)
        {
            if(timers!=RunewardenConfig.TimerDisplay.TEXT&&clocks.stream().anyMatch(clock->clock.label.equals(fact.label)))continue;
            int maximum=compact?2:6;
            if (facts.size() >= maximum) break;
            facts.addAll(GuideCoachOverlay.wrap(g.getFontMetrics(), fact.label + " · " + fact.value, WIDTH - 24, Math.min(2, maximum - facts.size())));
        }
        if (!MinigameReleasePolicy.allowsAutomaticGuidance(snapshot.activity))
            return new Card(title, List.of(), List.of(), facts,clocks.size());
        String next = snapshot.steps.isEmpty() ? "Open the activity guide" : snapshot.steps.get(0).title;
        String instruction = snapshot.steps.isEmpty() ? "" : snapshot.steps.get(0).instruction;
        g.setFont(HudStyle.label(12)); List<String> action = GuideCoachOverlay.wrap(g.getFontMetrics(), "NEXT · " + next, WIDTH - 24, 2);
        g.setFont(HudStyle.body(12)); List<String> body = GuideCoachOverlay.wrap(g.getFontMetrics(), instruction, WIDTH - 24, compact?2:6);
        return new Card(title, action, body, facts,clocks.size());
    }
}
