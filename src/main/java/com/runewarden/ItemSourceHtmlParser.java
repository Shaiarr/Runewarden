/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/** Bounded rendered-Wiki reader. The table grid preserves merged source/header cells. */
final class ItemSourceHtmlParser
{
    private static final class Node
    {
        final String tag;
        final java.util.Map<String,String> attributes = new java.util.LinkedHashMap<>();
        final List<Node> children = new ArrayList<>();
        final StringBuilder own = new StringBuilder();
        Node parent;
        Node(String tag) { this.tag = tag; }
        String attr(String key) { return attributes.getOrDefault(key, ""); }
        String text()
        {
            StringBuilder out = new StringBuilder(own);
            for (Node child : children) out.append(' ').append(child.text());
            return out.toString().replace('\u00a0',' ').replaceAll("\\s+", " ").trim();
        }
        List<Node> descendants(String... tags)
        {
            List<Node> result = new ArrayList<>();
            for (Node child : children) { if (Arrays.asList(tags).contains(child.tag)) result.add(child); result.addAll(child.descendants(tags)); }
            return result;
        }
        int span(String key) { try { return Math.max(1,Math.min(32,Integer.parseInt(attr(key)))); } catch (RuntimeException e) { return 1; } }
    }
    private static final class Cell
    {
        final Node node; final String text; final boolean header;
        Cell(Node node) { this.node=node; text=node.text(); header="th".equals(node.tag); }
    }
    private static final Set<String> SOURCE_SECTIONS = Set.of("obtaining", "acquisition", "item sources", "sources", "spawns", "item spawns", "rewards", "quest reward", "quest rewards", "creation");
    private ItemSourceHtmlParser() { }

    static void append(ItemSourceService.Lookup result, String html)
    {
        Node root = parse(html);
        String heading = ""; Set<String> seen = new LinkedHashSet<>();
        for (Node node : root.descendants("h2","h3","table","p","li"))
        {
            if (node.tag.equals("h2") || node.tag.equals("h3")) { heading=clean(node.text()).toLowerCase(Locale.ROOT); continue; }
            if (node.tag.equals("table")) table(result,node,heading,seen);
            else if (!inside(node,"table") && (heading.isEmpty() || SOURCE_SECTIONS.contains(heading))) prose(result,node,heading,seen);
        }
    }
    private static void table(ItemSourceService.Lookup result, Node table, String heading, Set<String> seen)
    {
        if (inside(table,"table")) return;
        String css=table.attr("class").toLowerCase(Locale.ROOT);
        List<Node> captions=table.descendants("caption");
        String caption=captions.isEmpty()?"":captions.get(0).text().toLowerCase(Locale.ROOT);
        boolean drops=css.contains("item-drops"), shop=css.contains("shoplist")||css.contains("store-locations-list"), skill=css.contains("skill-info");
        boolean production=Set.of("products","creation","production").contains(heading) || css.contains("products-list");
        boolean acquisition=SOURCE_SECTIONS.contains(heading);
        if (!drops&&!shop&&!skill&&!production&&!acquisition) return;
        List<String> headers=null; List<String> training=new ArrayList<>(); String trainingName=result.title;
        java.util.Map<Integer,Cell> carried=new java.util.LinkedHashMap<>(); java.util.Map<Integer,Integer> remaining=new java.util.LinkedHashMap<>();
        for (Node row : table.descendants("tr"))
        {
            if (nearest(row,"table")!=table) continue;
            List<Cell> grid=new ArrayList<>();
            for (int i=0;i<64;i++) grid.add(null);
            for (Integer col:new ArrayList<>(carried.keySet()))
            { grid.set(col,carried.get(col)); int left=remaining.get(col)-1; if(left<=0){carried.remove(col);remaining.remove(col);}else remaining.put(col,left); }
            boolean hasHeader=false,hasData=false; int column=0;
            for (Node field:row.children)
            {
                if (!field.tag.equals("th")&&!field.tag.equals("td")) continue;
                while(column<64&&grid.get(column)!=null)column++;
                Cell cell=new Cell(field); hasHeader|=cell.header; hasData|=!cell.header;
                for(int n=0;n<field.span("colspan")&&column<64;n++,column++)
                { grid.set(column,cell); if(field.span("rowspan")>1){carried.put(column,cell);remaining.put(column,field.span("rowspan")-1);} }
            }
            while(!grid.isEmpty()&&grid.get(grid.size()-1)==null)grid.remove(grid.size()-1);
            if(grid.isEmpty())continue;
            if(skill)
            {
                List<String> values=new ArrayList<>(); Cell last=null;
                for(Cell cell:grid)if(cell!=null&&cell!=last){values.add(cell.text);last=cell;}
                if(values.size()==1&&!values.get(0).isEmpty())trainingName=values.get(0);
                else if(values.size()==2&&!values.get(1).isEmpty()&&!values.get(1).contains("(edit)")&&!values.get(1).matches("[−-][0-9.]+(?: xp)?"))training.add(values.get(0)+": "+values.get(1));
                continue;
            }
            if(hasHeader&&!hasData)
            { headers=new ArrayList<>();for(Cell cell:grid)headers.add(cell==null?"":cell.text);continue; }
            if(headers==null)continue;
            int name=index(headers,drops?new String[]{"source","monster","npc"}:shop?new String[]{"shop","seller"}:caption.equals("requirements")?new String[]{"skill"}:new String[]{"item","source","location","name"});
            if(name<0)name=0;
            // A two-column Item header often covers an image cell followed by the linked name.
            while(name+1<grid.size()&&name+1<headers.size()&&headers.get(name).equalsIgnoreCase(headers.get(name+1))&&(grid.get(name)==null||grid.get(name).text.isEmpty()))name++;
            if(name>=grid.size()||grid.get(name)==null||grid.get(name).text.isEmpty())
            { name=-1;for(int i=0;i<grid.size();i++)if(grid.get(i)!=null&&!grid.get(i).text.isEmpty()){name=i;break;} }
            if(name<0)continue;
            Cell named=grid.get(name);StringBuilder detail=new StringBuilder();Set<Cell> rendered=new LinkedHashSet<>();rendered.add(named);
            for(int i=0;i<Math.min(headers.size(),grid.size());i++)
            {
                Cell cell=grid.get(i);String header=clean(headers.get(i));
                if(cell==null||cell.text.isEmpty()||header.isEmpty()||header.equalsIgnoreCase("cost")||header.toLowerCase(Locale.ROOT).contains("league")||!rendered.add(cell))continue;
                if(detail.length()>0)detail.append(" · ");detail.append(header).append(": ").append(cell.text);
            }
            String kind=shop?"Shop":drops?"Drop / reward":production?"Production":"Acquisition";
            if(production&&caption.equals("requirements"))kind="Creation requirements";
            else if(production&&caption.equals("materials"))kind=rowMarkupContains(named.node,"mw-selflink")?"Creation output":"Creation ingredient";
            if(drops)for(String action:new String[]{"woodcutting","mining","fishing","farming","thieving"})if(rowMarkupContains(row,"drops-"+action))kind=Character.toUpperCase(action.charAt(0))+action.substring(1);
            add(result,seen,kind,named.text,detail.toString(),link(named.node,result.wiki));
        }
        if(skill&&!training.isEmpty())add(result,seen,"Training",trainingName,String.join(" · ",training),result.wiki);
    }
    private static void prose(ItemSourceService.Lookup result,Node node,String heading,Set<String> seen)
    {
        if(inside(node,"li")||node.text().length()>900)return;
        String text=node.text();String lower=text.toLowerCase(Locale.ROOT);
        if(!lower.matches("(?s).*(?:can be (?:obtained|purchased|bought|found|received|claimed)|is (?:obtained|purchased|bought|received|a reward)|are (?:obtained|purchased|bought|received)|reward(?:ed)? (?:from|for)|spawns? (?:in|at|on|near)).*"))return;
        if(lower.matches("(?s).*(?:no longer|previously|formerly|used to|cannot be|can't be|not obtainable).*"))return;
        String target=null,sourceName=null;
        java.util.regex.Matcher action=java.util.regex.Pattern.compile("(?i)obtain|purchas|bought|found|received|claimed|reward|spawn").matcher(text);
        int after=action.find()?action.start():0;
        for(Node anchor:node.descendants("a"))
        {
            String label=anchor.text(),href=anchor.attr("href");
            if(!label.isEmpty()&&text.indexOf(label,after)>=after&&(href.startsWith("/w/")||href.startsWith(ItemSourceService.HOST+"/w/"))&&!href.contains("File:")&&!href.contains("File%3A"))
            {target=href.startsWith("/")?ItemSourceService.HOST+href:href;sourceName=label;break;}
        }
        if(target==null)return;
        // Preserve short explicit acquisition sentences. Never synthesize rates or parse arbitrary article history.
        for(String sentence:text.split("(?<=[.!?])\\s+"))
        {
            String s=sentence.toLowerCase(Locale.ROOT);
            if(sentence.length()<15||sentence.length()>350||!s.matches("(?s).*(?:obtain|purchas|bought|found|received|claimed|reward|spawn).*"))continue;
            String kind=heading.equals("creation")?"Creation note":s.contains("reward")?"Reward":s.contains("spawn")?"Spawn":"Acquisition";
            add(result,seen,kind,sourceName,sentence,target);
        }
    }
    private static String link(Node node,String fallback)
    {
        for(Node a:node.descendants("a"))
        {
            String href=a.attr("href"), label=a.text();
            if((href.startsWith("/w/")||href.startsWith(ItemSourceService.HOST+"/w/"))&&!href.contains("File:")&&!href.contains("File%3A")&&!href.contains("action=edit")&&!label.isEmpty()&&!a.attr("class").contains("image"))
                return href.startsWith("/")?ItemSourceService.HOST+href:href;
        }
        return fallback;
    }
    private static void add(ItemSourceService.Lookup result,Set<String> seen,String kind,String name,String details,String wiki)
    {String key=kind+"|"+name+"|"+details;if(seen.add(key)&&result.sources.size()<10_000)result.sources.add(new ItemSourceService.Source(kind,name,details,wiki));}
    private static int index(List<String> headers,String[] choices)
    {for(String choice:choices)for(int i=0;i<headers.size();i++)if(clean(headers.get(i)).equalsIgnoreCase(choice))return i;return -1;}
    private static String clean(String value){return value.replaceAll("\\[edit(?: \\| edit source)?]","").trim();}
    private static Node nearest(Node node,String tag){for(Node p=node.parent;p!=null;p=p.parent)if(p.tag.equals(tag))return p;return null;}
    private static boolean inside(Node node,String tag){return nearest(node,tag)!=null;}
    private static boolean rowMarkupContains(Node node,String token){if(node.attr("class").contains(token))return true;for(Node child:node.children)if(rowMarkupContains(child,token))return true;return false;}
    private static Node parse(String html)
    {
        Node root=new Node("root");final Node[] current={root};final int[] count={0};
        try{new ParserDelegator().parse(new StringReader(html),new HTMLEditorKit.ParserCallback(){
            @Override public void handleStartTag(HTML.Tag tag,MutableAttributeSet attrs,int position){Node next=node(tag,attrs);next.parent=current[0];current[0].children.add(next);current[0]=next;}
            @Override public void handleEndTag(HTML.Tag tag,int position){for(Node n=current[0];n!=null&&n!=root;n=n.parent)if(n.tag.equals(tag.toString())){current[0]=n.parent;break;}}
            @Override public void handleSimpleTag(HTML.Tag tag,MutableAttributeSet attrs,int position){Node next=node(tag,attrs);next.parent=current[0];current[0].children.add(next);}
            @Override public void handleText(char[] text,int position){Node next=new Node("text");next.own.append(text);next.parent=current[0];current[0].children.add(next);}
            private Node node(HTML.Tag tag,MutableAttributeSet attrs){if(++count[0]>100_000)throw new IllegalArgumentException("Wiki markup exceeds node budget");int depth=0;for(Node p=current[0];p!=null;p=p.parent)if(++depth>96)throw new IllegalArgumentException("Wiki markup is too deeply nested");Node n=new Node(tag.toString());java.util.Enumeration<?> keys=attrs.getAttributeNames();while(keys.hasMoreElements()){Object key=keys.nextElement();n.attributes.put(key.toString(),String.valueOf(attrs.getAttribute(key)));}return n;}
        },true);}catch(IOException ignored){}return root;
    }
}
