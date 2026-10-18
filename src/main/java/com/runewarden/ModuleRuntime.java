/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

/** Real module suspension and bounded in-client tick measurements. */
@Singleton
final class ModuleRuntime
{
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ModuleRuntime.class);
    static final String GROUP="runewardenmodules";
    static final class Module
    {
        final String name,key;final Runnable start,stop;boolean running;String failure="";
        final long[] samples=new long[1200];int count,cursor;long total,max;
        Module(String name,Runnable start,Runnable stop){this.name=name;this.key=name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");this.start=start;this.stop=stop;}
    }
    private final ConfigManager config;private final ClientThread thread;private final Map<String,Module> modules=new LinkedHashMap<>();
    private Runnable changeAction=()->{};
    void setChangeAction(Runnable action){changeAction=action==null?()->{}:action;}
    synchronized void enable(String name)
    {
        Module module=modules.get(name);long ticket=epoch;if(module==null)return;
        thread.invokeLater(()->{synchronized(ModuleRuntime.this){
            if(epoch!=ticket||modules.get(name)!=module)return;
            config.setConfiguration(GROUP,module.key,true);set(module,true);refresh();
        }});
    }
    private JPanel panel,body;private long epoch;private long started=System.nanoTime();
    @Inject ModuleRuntime(ConfigManager config,ClientThread thread){this.config=config;this.thread=thread;}
    synchronized void clear(){epoch++;modules.clear();started=System.nanoTime();}
    synchronized void registerStarted(String name,Runnable start,Runnable stop)
    {Module module=new Module(name,start,stop);module.running=true;modules.put(name,module);if(!wanted(module))set(module,false);refresh();}
    /** Client-only services start after registration, honoring the latest saved choice and lifecycle. */
    synchronized void registerDeferred(String name,Runnable start,Runnable stop)
    {
        Module module=new Module(name,start,stop);modules.put(name,module);long ticket=epoch;refresh();
        thread.invokeLater(()->{synchronized(ModuleRuntime.this){
            if(epoch!=ticket||modules.get(name)!=module||module.running||!wanted(module)||!module.failure.isEmpty())return;
            set(module,true);refresh();
        }});
    }
    synchronized boolean enabled(String name){Module module=modules.get(name);return module==null||module.running;}
    private boolean wanted(Module module){return !"false".equals(config.getConfiguration(GROUP,module.key));}
    synchronized void changed(){for(Module module:modules.values())if(wanted(module)!=module.running&&(!wanted(module)||module.failure.isEmpty()))set(module,wanted(module));refresh();}
    private void set(Module module,boolean enabled)
    {
        if(module.running==enabled)return;
        try{if(enabled){module.start.run();module.failure="";}else module.stop.run();module.running=enabled;}
        catch(RuntimeException failure){module.running=false;module.failure=failure.getClass().getSimpleName();LOG.warn("Runewarden module {} could not change its enabled state",module.name,failure);}
    }
    synchronized void tick(String name,Runnable action)
    {
        Module module=modules.get(name);if(module!=null&&!module.running)return;long began=System.nanoTime();
        try{action.run();}catch(RuntimeException failure){if(module==null)throw failure;module.failure=failure.getClass().getSimpleName();LOG.warn("Runewarden module {} suspended after a tick failure",module.name,failure);set(module,false);refresh();}
        finally{if(module!=null){long duration=Math.max(0,System.nanoTime()-began);module.samples[module.cursor++%module.samples.length]=duration;module.count=Math.min(module.count+1,module.samples.length);module.total+=duration;module.max=Math.max(module.max,duration);}}
    }
    synchronized String report()
    {
        StringBuilder out=new StringBuilder("Runewarden "+RunewardenVersion.VERSION+"\nJava "+System.getProperty("java.version")+"\nMeasured session: "+(System.nanoTime()-started)/1_000_000_000+" s\n");
        for(Module module:modules.values())
        {long[] sorted=Arrays.copyOf(module.samples,module.count);Arrays.sort(sorted);long p95=sorted.length==0?0:sorted[Math.min(sorted.length-1,(int)Math.ceil(sorted.length*.95)-1)];
            out.append(module.name).append(": ").append(module.running?"running":"suspended").append(", samples=").append(module.count).append(", p95=").append(String.format(Locale.ROOT,"%.3f",p95/1e6)).append(" ms, max=").append(String.format(Locale.ROOT,"%.3f",module.max/1e6)).append(" ms");if(!module.failure.isEmpty())out.append(", error=").append(module.failure);out.append('\n');}
        out.append("Data contains no character names, locations, inventory or configuration values.\n");return out.toString();
    }
    JPanel createPanel(){if(panel==null){panel=SidebarUi.column();panel.addHierarchyListener(event->{if(panel.isShowing())render();});panel.add(SidebarUi.heading("MODULES & DIAGNOSTICS"));body=SidebarUi.column();panel.add(body);render();}return panel;}
    private void refresh(){SwingUtilities.invokeLater(()->{if(panel!=null&&panel.isShowing())render();changeAction.run();});}
    private synchronized void render()
    {
        if(body==null)return;body.removeAll();
        body.add(SidebarUi.button("Copy support report…",()->RecoveryTool.showText("Runewarden diagnostics",report(),false)));
        for(Module module:modules.values())
        {
            JCheckBox toggle=new JCheckBox(module.name,module.running);toggle.setOpaque(false);toggle.setForeground(SidebarUi.TEXT);toggle.getAccessibleContext().setAccessibleName("Enable "+module.name);
            toggle.addActionListener(event->{boolean desired=toggle.isSelected();long ticket=epoch;thread.invokeLater(()->{synchronized(ModuleRuntime.this){if(epoch!=ticket||modules.get(module.name)!=module)return;config.setConfiguration(GROUP,module.key,desired);set(module,desired);refresh();}});});body.add(toggle);
            if(!module.failure.isEmpty())body.add(SidebarUi.label("Suspended · "+module.failure));
        }
        body.revalidate();body.repaint();
    }
}
