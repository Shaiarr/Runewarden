/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;

/** Compact world list. All account reads and hopping remain in WorldHopperTool. */
@Singleton
final class WorldHopperPanel
{
    private static final String ALL_REGIONS="All regions",ALL_TYPES="All types";
    private static final String[] MEMBERSHIP={"All worlds","Free worlds","Members worlds"};
    private static final String[] SORTS={"World","Players","Ping","Type","Location","Activity"};
    private static final Map<String,ImageIcon> FLAGS=loadFlags();
    private static final Color SELECTED=new Color(80,67,39),DANGER=new Color(236,137,126),STRIPE=new Color(23,24,27);
    private final WorldHopperTool tool;private final AtomicBoolean refreshQueued=new AtomicBoolean();
    private JPanel root,confirmation;private JTextField search;private JComboBox<String> membership,sort;private MultiFilter region,type;
    private JCheckBox safe,favourites,descending,showActivity,showLocation,pinFavourites,doubleClickHop;
    private JButton hop,favourite,previous,refreshPings;private GuideText current,status,selectedDetails,warning;private JTable table;
    private final WorldModel model=new WorldModel();private TableRowSorter<WorldModel> sorter;private JScrollPane scroll;
    private final Map<String,String> pendingPreferences=new HashMap<>();private WorldHopperTool.Snapshot state;private String loadedProfile;private boolean loading,sorting;private long confirmationToken;

    @Inject WorldHopperPanel(WorldHopperTool tool){this.tool=tool;tool.setChangeListener(this::refresh);}

    JPanel createPanel()
    {
        if(root!=null)return root;
        root=SidebarUi.column();root.setName("worldhopper-panel");root.add(SidebarUi.heading("WORLD HOPPER"));
        current=SidebarUi.label("");current.setName("worldhopper-current");root.add(current);
        root.add(SidebarUi.row(button("Return world","previous",()->{if(displayedProfileCurrent())tool.previousWorld();}),button("Refresh","refresh-worlds",tool::refreshWorlds)));
        root.add(SidebarUi.row(button("‹ Previous","cycle-previous",()->{if(displayedProfileCurrent())tool.cycle(false);}),button("Next ›","cycle-next",()->{if(displayedProfileCurrent())tool.cycle(true);})));
        search=new JTextField();search.setBackground(SidebarUi.CARD);search.setForeground(SidebarUi.TEXT);search.setCaretColor(SidebarUi.GOLD);search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(83,73,48)),BorderFactory.createEmptyBorder(4,4,4,4)));search.setName("worldhopper-search");search.setToolTipText("World number or activity");root.add(SidebarUi.field("Find a world",search));
        membership=combo("membership",MEMBERSHIP);region=new MultiFilter("region","regions");type=new MultiFilter("type","types");
        root.add(SidebarUi.row(SidebarUi.field("Membership",membership),SidebarUi.field("Region",region)));root.add(SidebarUi.field("World type",type));
        safe=check("Recommended worlds only","safe",true);safe.setToolTipText("Available worlds without PvP, high risk, or special game modes");
        favourites=check("Favourites only","favourites",false);root.add(safe);root.add(favourites);
        sort=combo("sort",SORTS);descending=check("Reverse","descending",false);root.add(SidebarUi.row(SidebarUi.field("Sort by",sort),descending));
        buildTable();root.add(scroll);
        selectedDetails=SidebarUi.label("Select a world");selectedDetails.setName("worldhopper-selected");root.add(selectedDetails);
        hop=button("Hop","hop",this::hopSelected);favourite=button("☆ Favourite","favourite",this::toggleSelectedFavourite);root.add(SidebarUi.row(hop,favourite));
        confirmation=SidebarUi.section("Confirm world hop");confirmation.setName("worldhopper-confirmation");warning=SidebarUi.label("");confirmation.add(warning);
        confirmation.add(SidebarUi.row(button("Confirm hop","confirm",()->tool.confirmHop(confirmationToken)),button("Cancel","cancel",tool::cancelHop)));confirmation.setVisible(false);root.add(confirmation);
        status=SidebarUi.label("");status.setName("worldhopper-status");root.add(status);refreshPings=button("Refresh pings","refresh-pings",tool::refreshPings);root.add(refreshPings);
        JPanel display=SidebarUi.column();showActivity=check("Show activity in the list","showActivity",true);showLocation=check("Show location in the list","showLocation",true);pinFavourites=check("Pin favourites first","pinFavourites",true);doubleClickHop=check("Double-click to hop","doubleClickHop",true);
        for(JCheckBox box:new JCheckBox[]{showActivity,showLocation,pinFavourites,doubleClickHop})display.add(box);root.add(SidebarUi.disclosure("Display options",display));
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){changed("search",search.getText());}public void removeUpdate(DocumentEvent e){changed("search",search.getText());}public void changedUpdate(DocumentEvent e){changed("search",search.getText());}});
        for(JComboBox<String> box:List.of(membership,sort))box.addActionListener(e->changed(box.getName().substring("worldhopper-".length()),String.valueOf(box.getSelectedItem())));
        for(JCheckBox box:List.of(safe,favourites,descending,showActivity,showLocation,pinFavourites,doubleClickHop))box.addActionListener(e->changed(box.getName().substring("worldhopper-".length()),Boolean.toString(box.isSelected())));
        root.addHierarchyListener(e->{if((e.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0){tool.setVisible(root.isShowing());if(root.isShowing())refresh();}});
        SidebarUi.install(root);render();return root;
    }
    void refresh()
    {
        if(!refreshQueued.compareAndSet(false,true))return;
        SwingUtilities.invokeLater(()->{refreshQueued.set(false);if(root!=null)render();});
    }
    private void buildTable()
    {
        sorter=new TableRowSorter<>(model);table=new JTable(model);table.setName("worldhopper-table");table.setRowSorter(sorter);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setFont(GuideText.FONT.deriveFont(11f));table.setRowHeight(36);table.setBackground(SidebarUi.BACKGROUND);table.setForeground(SidebarUi.TEXT);table.setGridColor(SidebarUi.CARD);table.setShowGrid(false);table.setIntercellSpacing(new Dimension(0,1));table.setSelectionBackground(SELECTED);table.setSelectionForeground(SidebarUi.TEXT);table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);table.getTableHeader().setFont(GuideText.FONT.deriveFont(10f));table.getTableHeader().setBackground(SidebarUi.CARD);table.getTableHeader().setForeground(SidebarUi.GOLD);
        // Region and activity stay available to the sorter; a compact information cell presents them.
        for(int index=6;index>=4;index--)table.removeColumn(table.getColumnModel().getColumn(index));
        int[] widths={43,40,37,90};for(int i=0;i<widths.length;i++){TableColumn column=table.getColumnModel().getColumn(i);column.setMinWidth(i==3?45:30);column.setPreferredWidth(widths[i]);column.setCellRenderer(new WorldRenderer(i));}
        sorter.setComparator(0,Comparator.comparingInt(Integer.class::cast));sorter.setComparator(1,(a,b)->compareMissing((Integer)a,(Integer)b,1,false));sorter.setComparator(2,(a,b)->compareMissing((Integer)a,(Integer)b,2,true));
        sorter.addRowSorterListener(e->{if(e.getType()!=javax.swing.event.RowSorterEvent.Type.SORT_ORDER_CHANGED||loading||sorting||state==null||!displayedProfileCurrent())return;for(RowSorter.SortKey key:sorter.getSortKeys())if(key.getColumn()<SORTS.length){loading=true;try{sort.setSelectedItem(SORTS[key.getColumn()]);descending.setSelected(key.getSortOrder()==SortOrder.DESCENDING);}finally{loading=false;}tool.saveDisplay("sort",String.valueOf(sort.getSelectedItem()));tool.saveDisplay("descending",Boolean.toString(descending.isSelected()));break;}});
        table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!loading)updateSelection();});
        table.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e)&&e.getClickCount()==2&&table.rowAtPoint(e.getPoint())>=0&&doubleClickHop.isSelected())hopSelected();}@Override public void mousePressed(MouseEvent e){popup(e);}@Override public void mouseReleased(MouseEvent e){popup(e);}});
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"runewarden-hop");table.getActionMap().put("runewarden-hop",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){hopSelected();}});
        scroll=new JScrollPane(table);scroll.setColumnHeaderView(table.getTableHeader());scroll.setName("worldhopper-worlds");scroll.setPreferredSize(new Dimension(190,340));scroll.setMinimumSize(new Dimension(0,180));scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE,500));scroll.setBorder(BorderFactory.createLineBorder(new Color(73,63,43)));scroll.getViewport().setBackground(SidebarUi.BACKGROUND);
    }
    private void popup(MouseEvent event)
    {
        if(!event.isPopupTrigger())return;int row=table.rowAtPoint(event.getPoint());if(row<0)return;table.setRowSelectionInterval(row,row);WorldHopperTool.WorldRow world=selected();if(world==null)return;
        String owner=state.profile;JPopupMenu menu=new JPopupMenu();JMenuItem favouriteItem=new JMenuItem(world.favourite?"Remove favourite":"Add favourite");favouriteItem.addActionListener(e->{if(profileCurrent(owner))tool.toggleFavourite(world.id);});menu.add(favouriteItem);JMenuItem hopItem=new JMenuItem("Hop to world "+world.id);hopItem.setEnabled(world.eligible&&world.id!=state.currentWorld);hopItem.addActionListener(e->{if(profileCurrent(owner))tool.selectWorld(world.id);});menu.add(hopItem);menu.show(table,event.getX(),event.getY());
    }
    private void render()
    {
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("World list must render on EDT");
        WorldHopperTool.Snapshot next=tool.snapshot();if(next==null)return;Integer selected=selected()==null?null:selected().id;int viewport=scroll.getVerticalScrollBar().getValue();loading=true;
        try
        {
            state=next;boolean accountChanged=!Objects.equals(loadedProfile,next.profile);if(accountChanged){loadedProfile=next.profile;pendingPreferences.clear();loadPreferences();}
            region.options(regions(next.rows),preference("region",""));type.options(types(next.rows),preference("type",""));
            current.setText((next.currentWorld>0?"World "+next.currentWorld:"Not in a world")+(next.lastWorld>0?"  ·  Previous "+next.lastWorld:""));
            previous.setEnabled(next.lastWorld>0&&next.lastWorld!=next.currentWorld);status.setText(next.status==null?"":next.status);refreshPings.setEnabled(!next.pingRunning);refreshPings.setText(next.pingRunning?"Measuring pings…":"Refresh pings");
            model.rows=next.rows;model.fireTableDataChanged();applyFilterAndSort();
            if(selected!=null)for(int row=0;row<model.rows.size();row++)if(model.rows.get(row).id==selected){int view=table.convertRowIndexToView(row);if(view>=0)table.setRowSelectionInterval(view,view);break;}
            scroll.getVerticalScrollBar().setValue(viewport);
            confirmation.setVisible(next.confirmation!=null);if(next.confirmation!=null){confirmationToken=next.confirmation.token;warning.setText("World "+next.confirmation.world+"\n"+next.confirmation.message);}else confirmationToken=0;
        }
        finally{loading=false;}
        updateSelection();root.revalidate();root.repaint();
    }
    private void loadPreferences()
    {
        search.setText(tool.display("search",""));membership.setSelectedItem(tool.display("membership",MEMBERSHIP[0]));sort.setSelectedItem(tool.display("sort",SORTS[0]));
        for(JCheckBox box:List.of(safe,favourites,descending,showActivity,showLocation,pinFavourites,doubleClickHop)){String key=box.getName().substring("worldhopper-".length());boolean fallback=box!=favourites&&box!=descending;box.setSelected(Boolean.parseBoolean(tool.display(key,Boolean.toString(fallback))));}
    }
    private String preference(String key,String fallback){return pendingPreferences.getOrDefault(key,tool.display(key,fallback));}
    private void changed(String key,String value)
    {
        if(loading||state==null||!displayedProfileCurrent())return;pendingPreferences.put(key,value);tool.saveDisplay(key,value);Integer selected=selected()==null?null:selected().id;applyFilterAndSort();if(selected!=null)for(int i=0;i<table.getRowCount();i++)if((Integer)table.getValueAt(i,0)==selected){table.setRowSelectionInterval(i,i);break;}updateSelection();table.repaint();
    }
    private int compareMissing(int a,int b,int column,boolean zeroMissing)
    {
        boolean missingA=zeroMissing?a<=0:a<0,missingB=zeroMissing?b<=0:b<0;if(missingA==missingB)return Integer.compare(a,b);
        boolean reverse=false;for(RowSorter.SortKey key:sorter.getSortKeys())if(key.getColumn()==column){reverse=key.getSortOrder()==SortOrder.DESCENDING;break;}
        return (missingA?1:-1)*(reverse?-1:1);
    }
    private void applyFilterAndSort()
    {
        if(sorter==null)return;String needle=search.getText().trim().toLowerCase(Locale.ROOT).replaceFirst("^#","");String members=String.valueOf(membership.getSelectedItem());Set<String> countries=region.selected(),kinds=type.selected();
        sorter.setRowFilter(new RowFilter<WorldModel,Integer>(){@Override public boolean include(Entry<? extends WorldModel,? extends Integer> entry){WorldHopperTool.WorldRow world=model.rows.get(entry.getIdentifier());if(safe.isSelected()&&(!world.safe||!world.eligible&&(state==null||world.id!=state.currentWorld)))return false;if(favourites.isSelected()&&!world.favourite)return false;if(members.equals(MEMBERSHIP[1])&&world.members||members.equals(MEMBERSHIP[2])&&!world.members)return false;if(!countries.isEmpty()&&!countries.contains(world.location))return false;if(!kinds.isEmpty()&&kinds.stream().noneMatch(kind->typeMatches(world,kind)))return false;return needle.isEmpty()||(world.id+" "+text(world.activity)).toLowerCase(Locale.ROOT).contains(needle);}});
        int column=Math.max(0,List.of(SORTS).indexOf(String.valueOf(sort.getSelectedItem())));List<RowSorter.SortKey> keys=new ArrayList<>();if(pinFavourites.isSelected())keys.add(new RowSorter.SortKey(6,SortOrder.DESCENDING));keys.add(new RowSorter.SortKey(column,descending.isSelected()?SortOrder.DESCENDING:SortOrder.ASCENDING));if(column!=0)keys.add(new RowSorter.SortKey(0,SortOrder.ASCENDING));sorting=true;try{sorter.setSortKeys(keys);}finally{sorting=false;}
    }
    private static SortedSet<String> regions(List<WorldHopperTool.WorldRow> rows){SortedSet<String> result=new TreeSet<>();for(WorldHopperTool.WorldRow row:rows)if(row.location!=null&&!row.location.isEmpty())result.add(row.location);return result;}
    private static SortedSet<String> types(List<WorldHopperTool.WorldRow> rows){SortedSet<String> result=new TreeSet<>();result.add("NORMAL");for(WorldHopperTool.WorldRow row:rows)for(String type:row.types)if(!type.equals("MEMBERS"))result.add(type);return result;}
    private static boolean typeMatches(WorldHopperTool.WorldRow row,String selected){if(selected.equals("NORMAL"))return row.types.stream().allMatch(t->t.equals("MEMBERS")||t.equals("SKILL_TOTAL")||t.equals("LAST_MAN_STANDING"));if(selected.equals("HIGH_RISK"))return row.types.contains("HIGH_RISK")&&!row.types.contains("PVP");if(selected.equals("SEASONAL"))return row.types.contains("SEASONAL")||row.types.contains("TOURNAMENT");return row.types.contains(selected);}
    private static String typeName(String value){if(value.equals("PVP"))return "PvP";if(value.equals("PVP_ARENA"))return "PvP Arena";String lower=value.toLowerCase(Locale.ROOT).replace('_',' ');return lower.isEmpty()?"":Character.toUpperCase(lower.charAt(0))+lower.substring(1);}
    /** Retains a saved selection while the native world list is still loading. */
    private final class MultiFilter extends JButton
    {
        private final String key,label;private final SortedSet<String> values=new TreeSet<>(),chosen=new TreeSet<>();
        MultiFilter(String key,String label){this.key=key;this.label=label;setName("worldhopper-"+key);setBackground(SidebarUi.CARD);setForeground(SidebarUi.TEXT);setFont(GuideText.FONT.deriveFont(11f));setMargin(new Insets(5,4,5,4));setMinimumSize(new Dimension(0,26));addActionListener(e->open());updateText();}
        void options(Collection<String> values,String encoded)
        {
            this.values.clear();this.values.addAll(values);chosen.clear();if(encoded!=null&&!encoded.equals(ALL_REGIONS)&&!encoded.equals(ALL_TYPES)&&!encoded.isEmpty())for(String value:encoded.split("\\|"))if(!value.isEmpty())chosen.add(key.equals("type")?(value.equals("Normal")?"NORMAL":value.toUpperCase(Locale.ROOT).replace(' ','_')):value);updateText();
        }
        Set<String> selected(){return Set.copyOf(chosen);}
        private String encoded(){return String.join("|",chosen);}
        private String title(String value){return key.equals("type")?typeName(value):value;}
        private void updateText(){setText(chosen.isEmpty()?"All "+label:chosen.size()==1?title(chosen.first()):chosen.size()+" "+label);setToolTipText(chosen.isEmpty()?"All "+label:String.join(", ",chosen.stream().map(this::title).toArray(String[]::new)));}
        private void choose(String value,boolean selected){if(selected)chosen.add(value);else chosen.remove(value);updateText();changed(key,encoded());}
        private void open()
        {
            String owner=state==null?null:state.profile;JPopupMenu popup=new JPopupMenu();popup.setBackground(SidebarUi.CARD);JPanel choices=SidebarUi.column();
            JCheckBox all=check("All "+label,"filter-"+key+"-all",chosen.isEmpty());choices.add(all);List<JCheckBox> boxes=new ArrayList<>();
            all.addActionListener(e->{if(!profileCurrent(owner)){popup.setVisible(false);return;}chosen.clear();for(JCheckBox box:boxes)box.setSelected(false);all.setSelected(true);updateText();changed(key,encoded());});
            for(String value:values){JCheckBox box=check(title(value),key+"-"+value,chosen.contains(value));box.addActionListener(e->{if(!profileCurrent(owner)){popup.setVisible(false);return;}choose(value,box.isSelected());all.setSelected(chosen.isEmpty());});boxes.add(box);choices.add(box);}
            JScrollPane viewport=new JScrollPane(choices);viewport.setBorder(BorderFactory.createEmptyBorder());viewport.setPreferredSize(new Dimension(Math.max(180,getWidth()),Math.min(280,Math.max(58,choices.getPreferredSize().height))));popup.add(viewport);JMenuItem done=new JMenuItem("Done");done.addActionListener(e->popup.setVisible(false));popup.add(done);putClientProperty("worldhopper.options",popup);if(isShowing())popup.show(this,0,getHeight());
        }
    }
    private void updateSelection()
    {
        WorldHopperTool.WorldRow world=selected();if(world==null){hop.setEnabled(false);favourite.setEnabled(false);selectedDetails.setText(table.getRowCount()==0?"No worlds match your filters":"Select a world");return;}
        hop.setEnabled(world.eligible&&state!=null&&world.id!=state.currentWorld);hop.setText(state!=null&&world.id==state.currentWorld?"Current world":"Hop to "+world.id);favourite.setEnabled(true);favourite.setText(world.favourite?"★ Favourite":"☆ Favourite");
        selectedDetails.setText("World "+world.id+" · "+location(world)+" · "+(world.members?"Members":"Free")+"\n"+text(world.activity)+(world.reason!=null&&!world.reason.isEmpty()?"\n"+world.reason:world.risk!=null&&!world.risk.isEmpty()?"\n"+world.risk:""));
    }
    private WorldHopperTool.WorldRow selected(){if(table==null||table.getSelectedRow()<0)return null;int row=table.convertRowIndexToModel(table.getSelectedRow());return row>=0&&row<model.rows.size()?model.rows.get(row):null;}
    private boolean profileCurrent(String profile){WorldHopperTool.Snapshot now=tool.snapshot();return now!=null&&Objects.equals(profile,now.profile);}
    private boolean displayedProfileCurrent(){return state!=null&&profileCurrent(state.profile);}
    private void hopSelected(){WorldHopperTool.WorldRow row=selected();if(row!=null&&row.eligible&&state!=null&&row.id!=state.currentWorld&&displayedProfileCurrent())tool.selectWorld(row.id);}
    private void toggleSelectedFavourite(){WorldHopperTool.WorldRow row=selected();if(row!=null&&displayedProfileCurrent())tool.toggleFavourite(row.id);}
    private JButton button(String label,String name,Runnable action){JButton button=SidebarUi.button(label,action);button.setName("worldhopper-"+name);if(name.equals("previous"))previous=button;return button;}
    private static JCheckBox check(String label,String name,boolean selected){JCheckBox box=new JCheckBox(label,selected);box.setName("worldhopper-"+name);box.setOpaque(false);box.setForeground(SidebarUi.TEXT);box.setFont(GuideText.FONT);return box;}
    private static JComboBox<String> combo(String name,String[] values){JComboBox<String> result=new JComboBox<>(values);result.setName("worldhopper-"+name);result.setFont(GuideText.FONT.deriveFont(11f));result.setForeground(SidebarUi.TEXT);result.setBackground(SidebarUi.CARD);result.setMinimumSize(new Dimension(0,26));return result;}
    private static Map<String,ImageIcon> loadFlags()
    {
        Map<String,ImageIcon> result=new HashMap<>();
        for(String name:new String[]{"us","us_east","us_west","uk","aus","ger","br","jp","sg","za"})
            try(InputStream stream=WorldHopperPanel.class.getResourceAsStream("/net/runelite/client/plugins/worldhopper/flag_"+name+".png"))
            {if(stream!=null){BufferedImage image=ImageIO.read(stream);if(image!=null&&image.getWidth()<=32&&image.getHeight()<=24)result.put(name,new ImageIcon(image));}}
            catch(IOException|RuntimeException unavailable){/* The world list remains usable without an optional native flag. */}
        return Collections.unmodifiableMap(result);
    }
    private static Icon flag(WorldHopperTool.WorldRow row)
    {
        String name;switch(row.region){case 0:name=row.geography==-42?"us_east":row.geography==-73?"us_west":"us";break;case 1:name="uk";break;case 3:name="aus";break;case 7:name="ger";break;case 8:name="br";break;case 9:name="jp";break;case 10:name="sg";break;case 12:name="za";break;default:return null;}return FLAGS.get(name);
    }
    private static String location(WorldHopperTool.WorldRow row)
    {return text(row.location)+(row.region==0?(row.geography==-42?" · East coast":row.geography==-73?" · West coast":""):"");}
    private static String text(String value){return value==null?"":value;}
    private static String html(String value){return text(value).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private final class WorldRenderer extends DefaultTableCellRenderer
    {
        private final int column;
        private final JPanel information=new JPanel(new BorderLayout(3,0)),lines=new JPanel(new GridLayout(2,1));
        private final JLabel flagLabel=new JLabel(),primary=new JLabel(),secondary=new JLabel();
        WorldRenderer(int column)
        {
            this.column=column;setBorder(BorderFactory.createEmptyBorder(2,3,2,3));
            information.setBorder(BorderFactory.createEmptyBorder(2,3,2,3));lines.setOpaque(false);
            primary.setName("worldhopper-row-primary");secondary.setName("worldhopper-row-secondary");flagLabel.setName("worldhopper-row-flag");
            flagLabel.setVerticalAlignment(SwingConstants.CENTER);lines.add(primary);lines.add(secondary);
            information.add(flagLabel,BorderLayout.WEST);information.add(lines,BorderLayout.CENTER);
        }
        @Override public Component getTableCellRendererComponent(JTable list,Object value,boolean selected,boolean focused,int viewRow,int viewColumn)
        {
            super.getTableCellRendererComponent(list,value,selected,focused,viewRow,viewColumn);WorldHopperTool.WorldRow row=model.rows.get(list.convertRowIndexToModel(viewRow));
            setBackground(selected?SELECTED:viewRow%2==0?SidebarUi.BACKGROUND:STRIPE);setForeground(row.id==state.currentWorld?SidebarUi.GOLD:!row.eligible?SidebarUi.MUTED:!row.safe?DANGER:SidebarUi.TEXT);setHorizontalAlignment(column>0&&column<3?SwingConstants.RIGHT:SwingConstants.LEFT);setIcon(null);
            String tooltip="<html>World "+row.id+" · "+html(WorldHopperPanel.location(row))+" · "+(row.members?"Members":"Free")+"<br>"+html(row.activity)+"<br>"+html(row.typeLabel)+(row.reason==null||row.reason.isEmpty()?"":"<br>"+html(row.reason))+(row.risk==null||row.risk.isEmpty()?"":"<br>"+html(row.risk))+"</html>";
            if(column==0)setText((row.favourite?"★":"")+row.id);else if(column==1)setText(row.population<0?"OFF":Integer.toString(row.population));else if(column==2)setText(row.ping<=0?"—":Integer.toString(row.ping));
            else
            {
                String main=showActivity.isSelected()&&row.activity!=null&&!row.activity.equals("-")&&!row.activity.isEmpty()?row.activity:row.typeLabel;
                information.setBackground(getBackground());primary.setForeground(getForeground());secondary.setForeground(SidebarUi.MUTED);
                primary.setFont(list.getFont());secondary.setFont(list.getFont());primary.setText(main);secondary.setText(showLocation.isSelected()?row.location:row.typeLabel);
                Icon icon=showLocation.isSelected()?flag(row):null;flagLabel.setIcon(icon);flagLabel.setVisible(icon!=null);
                information.setToolTipText(tooltip);return information;
            }
            setToolTipText(tooltip);return this;
        }
    }
    private static final class WorldModel extends AbstractTableModel
    {
        List<WorldHopperTool.WorldRow> rows=List.of();private static final String[] HEADERS={"World","Players","Ping","World info","Location","Activity","Favourite"};
        @Override public int getRowCount(){return rows.size();}@Override public int getColumnCount(){return HEADERS.length;}@Override public String getColumnName(int column){return HEADERS[column];}
        @Override public Class<?> getColumnClass(int column){return column<3?Integer.class:column==6?Boolean.class:String.class;}
        @Override public Object getValueAt(int index,int column){WorldHopperTool.WorldRow row=rows.get(index);switch(column){case 0:return row.id;case 1:return row.population;case 2:return row.ping;case 3:return text(row.typeLabel);case 4:return text(row.location);case 5:return text(row.activity);default:return row.favourite;}}
    }
}
