/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EncounterPracticeToolTest
{
    @Test public void selectedPhaseOpensExactGuideAndDeferredJournalWritesCannotCrossProfiles() throws Exception
    {
        Client client = mock(Client.class); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        ConfigManager config = mock(ConfigManager.class); when(config.getRSProfileKey()).thenReturn("character-A");
        ClientThread thread = mock(ClientThread.class); List<Runnable> pending = new ArrayList<>();
        doAnswer(call -> { pending.add(call.getArgument(0)); return null; }).when(thread).invokeLater(any(Runnable.class));
        EncounterPracticeTool tool = new EncounterPracticeTool(client, thread, config, new GuideCatalog(new Gson()), new Gson());
        AtomicReference<String> opened = new AtomicReference<>(); tool.setOpenSectionAction((id, index) -> opened.set(id + "/" + index));
        JPanel[] panel = new JPanel[1]; tool.start(); tool.onGameTick();
        SwingUtilities.invokeAndWait(() -> panel[0] = tool.createPanel());
        try
        {
            SwingUtilities.invokeAndWait(() -> {
                button(panel[0], "Boss encounters").doClick();
                button(panel[0], "Abyssal Sire").doClick();
                combo(panel[0]).setSelectedIndex(1);
                button(panel[0], "Use phase guide & markers").doClick();
                button(panel[0], "Mark phase practised").doClick();
            });
            assertEquals("encounter:abyssal-sire/1", opened.get());
            assertFalse(pending.isEmpty());
            when(config.getRSProfileKey()).thenReturn("character-B");
            for (Runnable write : pending) write.run();
            verify(config, never()).setRSProfileConfiguration(anyString(), anyString(), any());
        }
        finally { tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }
    @Test public void journalBoundsInputAndRejectsUnknownGuidesAndInvalidPhaseHistory()
    {
        Gson gson = new Gson(); GuideCatalog catalog = new GuideCatalog(gson);
        assertTrue(EncounterPracticeTool.loadJournal("x".repeat(EncounterPracticeTool.MAX_SAVED_CHARS + 1), gson, catalog).encounters.isEmpty());
        assertTrue(EncounterPracticeTool.loadJournal("{broken", gson, catalog).encounters.isEmpty());
        EncounterPracticeTool.Journal journal = new EncounterPracticeTool.Journal();
        EncounterPracticeTool.Record record = new EncounterPracticeTool.Record();
        record.note = "n".repeat(500); record.practised.add(-1); record.practised.add(0); record.practised.add(1000);
        for (int i = 0; i < 25; i++) record.attempts.add(new EncounterPracticeSession.Attempt("Retry", i, "x".repeat(500)));
        journal.encounters.put("encounter:abyssal-sire", record);
        journal.encounters.put("quest:Cook's Assistant", record);
        journal.encounters.put("invented:unknown", record);
        EncounterPracticeTool.Journal loaded = EncounterPracticeTool.loadJournal(gson.toJson(journal), gson, catalog);
        assertEquals(1, loaded.encounters.size());
        EncounterPracticeTool.Record accepted = loaded.encounters.get("encounter:abyssal-sire");
        assertEquals(400, accepted.note.length()); assertEquals(java.util.Collections.singleton(0), accepted.practised);
        assertEquals(20, accepted.attempts.size()); assertEquals(5, accepted.attempts.get(0).durationMillis);
        assertEquals(400, accepted.attempts.get(0).note.length());
    }

    @Test public void preparationLinksTheSelectedBossAndRecordsContextThroughReview() throws Exception
    {
        Client client=mock(Client.class);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        ConfigManager config=mock(ConfigManager.class);when(config.getRSProfileKey()).thenReturn("character-A");
        ClientThread thread=mock(ClientThread.class);doAnswer(c->{((Runnable)c.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
        AtomicReference<String> target=new AtomicReference<>(),opened=new AtomicReference<>();AtomicReference<Object> saved=new AtomicReference<>();
        doAnswer(c->{saved.set(c.getArgument(2));return null;}).when(config).setRSProfileConfiguration(anyString(),anyString(),any());
        Gson gson=new Gson();GuideCatalog catalog=new GuideCatalog(gson);EncounterPracticeTool tool=new EncounterPracticeTool(client,thread,config,catalog,gson);
        tool.setCombatTargetAction(target::set);tool.setPreparationActions(()->opened.set("loadouts"),()->opened.set("combat"),()->opened.set("recovery"));
        tool.start();tool.onGameTick();JPanel[] panel=new JPanel[1];
        try
        {
            SwingUtilities.invokeAndWait(()->{
                panel[0]=tool.createPanel();button(panel[0],"Boss encounters").doClick();button(panel[0],"Abyssal Sire").doClick();
                button(panel[0],"Check loadout & supplies").doClick();assertEquals("loadouts",opened.get());
                button(panel[0],"Compare combat setups").doClick();assertEquals("combat",opened.get());assertEquals("Abyssal Sire",target.get());
                button(panel[0],"Start attempt").doClick();button(panel[0],"Retry").doClick();button(panel[0],"Open death recovery").doClick();assertEquals("recovery",opened.get());
            });
            EncounterPracticeTool.Record record=EncounterPracticeTool.loadJournal(String.valueOf(saved.get()),gson,catalog).encounters.get("encounter:abyssal-sire");
            assertEquals(1,record.attempts.size());assertEquals(0,record.attempts.get(0).phase);assertTrue(record.preparation.recoveryPending);assertEquals(0,record.unfinishedMillis);
            SwingUtilities.invokeAndWait(()->button(panel[0],"Review complete · prepare next attempt").doClick());
            record=EncounterPracticeTool.loadJournal(String.valueOf(saved.get()),gson,catalog).encounters.get("encounter:abyssal-sire");assertFalse(record.preparation.recoveryPending);assertEquals(1,record.attempts.size());
        }
        finally{tool.stop();SwingUtilities.invokeAndWait(()->{});}
    }

    @Test public void retainedPhaseButtonCannotOpenThePreviousCharactersGuide() throws Exception
    {
        Client client=mock(Client.class);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);ConfigManager config=mock(ConfigManager.class);when(config.getRSProfileKey()).thenReturn("character-A");
        EncounterPracticeTool tool=new EncounterPracticeTool(client,mock(ClientThread.class),config,new GuideCatalog(new Gson()),new Gson());AtomicReference<String> opened=new AtomicReference<>();tool.setOpenSectionAction((id,index)->opened.set(id));
        tool.start();tool.onGameTick();JButton[] retained=new JButton[1];
        try
        {
            SwingUtilities.invokeAndWait(()->{JPanel panel=tool.createPanel();button(panel,"Boss encounters").doClick();button(panel,"Abyssal Sire").doClick();retained[0]=button(panel,"Use phase guide & markers");});
            when(config.getRSProfileKey()).thenReturn("character-B");SwingUtilities.invokeAndWait(()->retained[0].doClick());assertNull(opened.get());
        }
        finally{tool.stop();SwingUtilities.invokeAndWait(()->{});}
    }

    private static JButton button(Container parent, String title)
    {
        for (Component child : parent.getComponents())
        {
            if (child instanceof JButton && title.equals(((JButton) child).getAccessibleContext().getAccessibleName())) return (JButton) child;
            if (child instanceof Container) { JButton found = findButton((Container) child, title); if (found != null) return found; }
        }
        throw new AssertionError("Missing button: " + title);
    }
    private static JButton findButton(Container parent, String title)
    {
        for (Component child : parent.getComponents())
        { if (child instanceof JButton && title.equals(((JButton) child).getAccessibleContext().getAccessibleName())) return (JButton) child; if (child instanceof Container) { JButton found = findButton((Container) child, title); if (found != null) return found; } }
        return null;
    }
    private static JComboBox<?> combo(Container parent)
    {
        for (Component child : parent.getComponents())
        { if (child instanceof JComboBox) return (JComboBox<?>) child; if (child instanceof Container) { JComboBox<?> found = findCombo((Container) child); if (found != null) return found; } }
        throw new AssertionError("Missing phase picker");
    }
    private static JComboBox<?> findCombo(Container parent)
    {
        for (Component child : parent.getComponents())
        { if (child instanceof JComboBox) return (JComboBox<?>) child; if (child instanceof Container) { JComboBox<?> found = findCombo((Container) child); if (found != null) return found; } }
        return null;
    }
}
