/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import net.runelite.client.config.ConfigManager;

/** User-reviewed, character-bound backup of this plugin's settings only. */
@Singleton
final class RecoveryTool
{
    static final int MAX_BACKUP=12_000_000, MAX_SETTINGS=3000, MAX_VALUE=1_500_000;
    static final Set<String> GROUPS=Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
        "runewarden","runewardenrewards","runewardenminigames","runewardenfield","runewardendeaths",
        "runewardengroundloot","runewardenthieving","runewardenagility","runewardenactionprogress","runewardenequipmentinspector","runewardensailing","runewardenskills","runewardenjuggling","runewardencontainerhud","runewardenminigameevents","runewardenbankmemory","runewardenactivity","runewardentraining","runewardenclues","runewardenslayer","runewardenmodules","runewardenroutines")));
    static final class Setting { String group,key,value; boolean character; }
    static final class Backup { int version=1; String created,profile; List<Setting> settings=new ArrayList<>(); }
    private final ConfigManager config; private final Gson gson; private final RunewardenDataStore data;
    private Consumer<Runnable> apply=Runnable::run;
    private JPanel panel,body; private Backup preview,undo;private Set<String> addedByRestore=Collections.emptySet(); private String message="";
    @Inject RecoveryTool(ConfigManager config,Gson gson,RunewardenDataStore data) {this.config=config;this.gson=gson;this.data=data;}
    void setApplyAction(Consumer<Runnable> action) {apply=action==null?Runnable::run:action;}
    Backup capture()
    {
        Backup result=new Backup();result.profile=config.getRSProfileKey();result.created=Instant.now().toString();
        Set<String> groups=new LinkedHashSet<>(GROUPS);
        for(String whole:config.getConfigurationKeys("runewarden"))
        {int dot=whole.indexOf('.');if(dot>0&&RunewardenDataStore.owned(whole.substring(0,dot))&&!RunewardenDataStore.GROUP.equals(whole.substring(0,dot)))groups.add(whole.substring(0,dot));}
        for(String group:groups)
        {
            for(String whole:config.getConfigurationKeys(group+"."))
            {String key=whole.substring(group.length()+1);if(key.indexOf('.')<0) add(result,group,key,false,config.getConfiguration(group,key));}
            if(result.profile!=null)
                for(String key:config.getRSProfileConfigurationKeys(group,result.profile,""))
                    add(result,group,key,true,config.getConfiguration(group,result.profile,key));
        }
        return result;
    }
    private static void add(Backup out,String group,String key,boolean character,String value)
    {if(value==null)return;Setting row=new Setting();row.group=group;row.key=key;row.character=character;row.value=value;out.settings.add(row);}
    String exportBackup() {String raw=gson.toJson(capture());if(raw.length()>MAX_BACKUP)throw new IllegalArgumentException("Backup exceeds 12 MB");return raw;}
    static Backup parse(String raw,Gson gson)
    {
        if(raw==null||raw.length()>MAX_BACKUP)throw new IllegalArgumentException("Backup exceeds 12 MB");
        RunewardenDataStore.validateJson(raw,MAX_BACKUP);
        final Backup result;
        try {result=gson.fromJson(raw,Backup.class);}catch(RuntimeException ex){throw new IllegalArgumentException("Invalid backup JSON");}
        if(result==null||result.version!=1||result.settings==null||result.settings.size()>MAX_SETTINGS)
            throw new IllegalArgumentException("Unsupported backup");
        if(result.profile!=null&&(!result.profile.startsWith("rsprofile")||result.profile.length()>180||result.profile.indexOf('\n')>=0))
            throw new IllegalArgumentException("Invalid character binding");
        Set<String> seen=new HashSet<>();
        for(Setting row:result.settings)
        {
            if(row==null||!RunewardenDataStore.owned(row.group)||RunewardenDataStore.GROUP.equals(row.group)||row.key==null||row.key.isEmpty()||row.key.length()>250
                ||row.key.indexOf('\n')>=0||row.key.indexOf('.')>=0||row.value==null||row.value.length()>(RunewardenDataStore.GROUP.equals(row.group)?7_000_000:MAX_VALUE)
                ||row.character&&result.profile==null||!seen.add(row.group+"/"+row.character+"/"+row.key))
                throw new IllegalArgumentException("Invalid or repeated Runewarden setting");
        }
        return result;
    }
    boolean previewImport(String raw)
    {
        preview=parse(raw,gson);
        if(preview.profile!=null&&!Objects.equals(preview.profile,config.getRSProfileKey()))
        {preview=null;throw new IllegalArgumentException("Log into the character this backup belongs to");}
        render();return true;
    }
    void restorePreview()
    {
        Backup checked=preview;if(checked==null)return;
        if(checked.profile!=null&&!Objects.equals(checked.profile,config.getRSProfileKey())){message="Character changed; preview again";preview=null;render();return;}
        undo=capture();Set<String> old=new HashSet<>();for(Setting row:undo.settings)old.add(address(row));addedByRestore=new HashSet<>();for(Setting row:checked.settings)if(!old.contains(address(row)))addedByRestore.add(address(row));preview=null;apply.accept(()->{message=restore(checked)?"Backup restored":"Character changed; nothing restored";refresh();});render();
    }
    void undoRestore()
    {
        Backup previous=undo;if(previous==null)return;Set<String> added=new HashSet<>(addedByRestore);
        apply.accept(()->{
            if(previous.profile!=null&&!Objects.equals(previous.profile,config.getRSProfileKey())){message="Character changed; nothing restored";refresh();return;}
            for(String key:added){String[] parts=key.split("\n",3);config.unsetConfiguration(parts[0],"true".equals(parts[1])?previous.profile:null,parts[2]);}
            if(restore(previous)){undo=null;addedByRestore=Collections.emptySet();message="Previous settings restored";}refresh();
        });
    }
    private static String address(Setting row){return row.group+"\n"+row.character+"\n"+row.key;}
    private boolean restore(Backup backup)
    {
        if(backup.profile!=null&&!Objects.equals(backup.profile,config.getRSProfileKey()))return false;
        // Merge: keys absent from an older backup remain intact.
        for(Setting row:backup.settings)config.setConfiguration(row.group,row.character?backup.profile:null,row.key,row.value);
        return true;
    }
    JPanel createPanel()
    {
        if(panel==null){panel=SidebarUi.column();panel.addHierarchyListener(event->{if(panel.isShowing())render();});panel.add(SidebarUi.heading("RECOVERY & BACKUP"));body=SidebarUi.column();panel.add(body);render();}return panel;
    }
    void refresh(){SwingUtilities.invokeLater(()->{if(panel!=null&&panel.isShowing())render();});}
    private void render()
    {
        if(body==null)return;body.removeAll();
        body.add(SidebarUi.button("Export character backup",()->{try{showText("Runewarden backup",exportBackup(),false);}catch(IllegalArgumentException error){message=error.getMessage();render();}}));
        body.add(SidebarUi.button("Import backup…",()->{String raw=showText("Preview Runewarden backup","",true);if(raw!=null)try{previewImport(raw);message="";}catch(IllegalArgumentException ex){message=ex.getMessage();render();}}));
        if(preview!=null)
        {
            JPanel review=SidebarUi.section("RESTORE PREVIEW");int character=0;for(Setting row:preview.settings)if(row.character)character++;
            review.add(SidebarUi.label(character+" character settings\n"+(preview.settings.size()-character)+" shared settings\n"+Objects.toString(preview.created,"Unknown date")));
            review.add(SidebarUi.button("Restore these settings",this::restorePreview));review.add(SidebarUi.button("Cancel",()->{preview=null;render();}));body.add(review);
        }
        if(undo!=null)body.add(SidebarUi.button("Undo last restore",this::undoRestore));
        if(!message.isEmpty())body.add(SidebarUi.label(message));
        for(RunewardenDataStore.Problem problem:data.problems(config.getRSProfileKey()))
        {
            JPanel card=SidebarUi.section(problem.address.key);card.add(SidebarUi.label(problem.reason));
            if(problem.recoveryAvailable)card.add(SidebarUi.button("Recover last valid save",()->{if(current(problem.address))apply.accept(()->{if(current(problem.address))data.recover(problem.address);});}));
            card.add(SidebarUi.button("Reset this save…",()->{if(!current(problem.address))return;if(JOptionPane.showConfirmDialog(panel,"Reset "+problem.address.key+"? The rejected copy is retained.","Reset saved data",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)apply.accept(()->{if(current(problem.address))data.reset(problem.address);});}));body.add(card);
        }
        body.revalidate();body.repaint();
    }
    private boolean current(RunewardenDataStore.Address address){return address.profile==null||Objects.equals(address.profile,config.getRSProfileKey());}
    static String showText(String title,String value,boolean edit)
    {
        JTextArea text=new JTextArea(value,18,55);text.setEditable(edit);text.setLineWrap(true);text.setWrapStyleWord(true);text.getAccessibleContext().setAccessibleName(title);
        int chosen=JOptionPane.showConfirmDialog(null,new JScrollPane(text),title,edit?JOptionPane.OK_CANCEL_OPTION:JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE);
        return edit&&chosen!=JOptionPane.OK_OPTION?null:text.getText();
    }
}
