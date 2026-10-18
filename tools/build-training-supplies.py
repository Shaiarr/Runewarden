#!/usr/bin/env python3
"""Explicit bank contracts for the ten added training routes; preserve other contracts."""
import json
from collections import Counter
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources/com/runewarden'
guides={entry['id']:entry for entry in json.loads((RES/'guides.json').read_text())['entries']}
policies=json.loads((RES/'guide-supply-plans.json').read_text())
legacy=json.loads((RES/'guide-bank-supplies.json').read_text())
pickaxes=[1265,1267,1269,1271,1273,1275,11920,12797,13243,23680,20014,23276,12297]
# Normal, top-level RuneLite1.13.2 gameval item IDs. No noted/placeholder aliases.
rows={
'feldip-weasels':([('Noose wand',[10150]),('Ring of pursuit',[21126])],['run-energy','teleports'],'Tracking route; ring of pursuit is a recommended route accelerator.'),
'ruby-harvests':([('Butterfly net',[10010,11259]),('Butterfly jar',[10012]),('Bird snare',[10006])],['teleports'],'Butterflies plus bird snares; two ordinary traps are available from20Hunter.'),
'red-crabs':([('Hammer',[2347]),('Saw',[8794]),('Plank',[960]),('Bucket',[1925]),('Fish offcuts',[11334])],['nails'],'Hammer, saw, planks, nails and buckets are one-time trap construction supplies; fish offcuts are recurring bait.'),
'embertailed-jerboas':([('Box trap',[10008])],['teleports'],'Box traps unlocked by Eagles\' Peak progress; three ordinary traps available from40Hunter.'),
'razor-backed-kebbits':([('Noose wand',[10150]),('Ring of pursuit',[21126])],['run-energy','teleports'],'Spare pursuit rings and run restoration support the reference route; no caught kebbit outputs are inputs.'),
'rainbow-crabs':([('Hammer',[2347]),('Saw',[8794]),('Plank',[960]),('Bucket',[1925]),('Fine fish offcuts',[32307]),('Pestle and mortar',[233])],['nails'],'Permanent trap construction supplies are separate from recurring fine-offcut bait. Crush caught crabs to keep paste stackable.'),
'moonlight-moths':([('Butterfly net',[10010,11259]),('Butterfly jar',[10012])],['teleports'],'Net/jar route; release moths to reuse jars. Eligible barehand alternatives can omit these tools.'),
'rubium-rocks':([('Any pickaxe',pickaxes),('Rope',[954])],['dragonfire-protection'],'Small rubium rocks require48Mining; rope is needed only for first entry. Do not confuse these with68Mining geode deposits.'),
'lead-rocks':([('Any pickaxe',pickaxes),('Superior or expert mining gloves',[21345,21392])],[],'Gloves are alternatives for the efficient route; drop mined lead. Varrock armour and celestial-ring modifiers remain optional gear choices.'),
'infernal-shale':([('Any pickaxe',pickaxes),('Hammer',[2347]),('Chisel',[1755]),("Jim's wet cloth",[30808])],[],'Wet cloth is optional for the manual tick method. Shale and crushed shale are outputs and are excluded from input filtering.')}
report=json.loads((ROOT/'docs/bank-supply-coverage.json').read_text());reports={row['id']:row for row in report['entries']}
for key,(supplies,families,note) in rows.items():
 mid='training:'+key;guide=guides[mid];ids=list(dict.fromkeys(i for _,items in supplies for i in items));sources=guide['sourceUrls']
 policies[mid]=dict(kind='items',itemIds=ids,families=families,itemNames=[],source=sources,notes=note,unresolved=[])
 legacy['methods'][mid]=[dict(name=name,itemIds=items,evidence=note) for name,items in supplies]
 reports[mid]=dict(id=mid,title=guide['title'],category='training',kind='items',namedItemCount=len(ids),families=families,contextChoices=[],notes=note,source=sources)
(RES/'guide-supply-plans.json').write_text(json.dumps(policies,indent=2,ensure_ascii=False)+'\n')
(RES/'guide-bank-supplies.json').write_text(json.dumps(legacy,indent=2,ensure_ascii=False)+'\n')
report['entries']=list(reports.values());report['catalogEntries']=len(guides);report['counts']=dict(Counter(row['kind'] for row in report['entries']))
assert set(reports)==set(guides),'Coverage report does not match guides'
(ROOT/'docs/bank-supply-coverage.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
print('Ten training bank contracts added; total coverage:',report['catalogEntries'],report['counts'])
