#!/usr/bin/env python3
"""Idempotent authored additions for current Wiki training routes, 17 October 2026."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];path=ROOT/'src/main/resources/com/runewarden/guides.json'
data=json.loads(path.read_text());entries=data['entries'];known={e['id'] for e in entries}
def skill(key,value):return dict(kind='skill',key=key,value=value,label=f'{value} {key.title()}')
def quest(key,started=False):return dict(kind='quest',key=key,label=key+(' progressed' if started else ' completed'),started=started)
def fact(label):return dict(kind='fact',key='unlock:'+label,label=label)
def item(name,quantity=1,itemId=None):
 r=dict(name=name,quantity=quantity)
 if itemId:r['itemId']=itemId
 return r
def add(key,title,sk,level,attention,page,revision,prepare,loop,milestones,needs=None,items=None,recommended=None):
 mid='training:'+key
 if mid in known:return
 req=[skill(sk,level)]+(needs or [])
 sections=[]
 for section,steps in [('prepare',prepare),('method',loop),('milestones',milestones)]:sections.append(dict(id=section,title=section.title(),steps=[dict(id=f'{section}:{i+1}',text=text) for i,text in enumerate(steps)]))
 entries.append(dict(id=mid,title=title,category='training',skill=sk,subtitle=attention,source=f'https://oldschool.runescape.wiki/w/{page}?oldid={revision}',sourceUrls=[f'https://oldschool.runescape.wiki/w/{page}?oldid={revision}'],license='GPL-3.0-only',requirements=[r['label'] for r in req],requirementDetails=req,optionalUnlocks=[],items=items or [],recommended=recommended or [],sections=sections))
 known.add(mid)
H=15366559;M=15373229
add('feldip-weasels','Feldip weasel tracking','hunter',9,'Feldip Hunter area · normal','Hunter_training',H,
 ['Equip a [[Noose wand]] and travel to the Feldip Hunter area.','Bring rings of pursuit and a route back to a bank.'],
 ['Inspect a burrow to begin a trail.','Follow the tracks and inspect the trail endpoints; a ring of pursuit reveals the full trail.','Attack the bush at the end to catch the weasel, then start another trail.'],
 ['Finish one full trail.','Catch ten weasels without losing the trail.','Reach 15 Hunter for ruby harvests.'],items=[item('Noose wand',1,10150)],recommended=[item('Ring of pursuit')])
add('ruby-harvests','Ruby harvests and copper longtails','hunter',15,'Piscatoris Hunter area · intensive','Hunter_training',H,
 ['Bring a [[Butterfly net]], empty [[Butterfly jar]]s and a [[Bird snare]].','Use one bird snare before 20 Hunter; the second trap slot unlocks at 20.'],
 ['Set a snare for copper longtails.','Catch ruby harvests while the bird trap is waiting, and release them to reuse the jars.','Reset fallen or caught bird traps before returning to butterflies.'],
 ['Catch five butterflies and reset a bird trap.','Unlock the second snare at 20 Hunter.','Reach 21 Hunter for red crabs.'],items=[item('Butterfly net',1,10010),item('Butterfly jar',5,10012),item('Bird snare',2,10006)])
add('red-crabs','Red crab trapping','hunter',21,'The Pandemonium · normal','Hunter_training',H,
 ['At The Pandemonium, build two neighbouring crab traps using a hammer, saw, two planks, four nails and two buckets.','Bring [[Fish offcuts]] for bait; the traps remain built for future visits.'],
 ['Bait both traps with fish offcuts.','Collect trapped crabs and reset each trap.','Bank at the nearby bank and continue the same circuit.'],
 ['Build and bait both traps.','Finish five trap circuits.','Reach 39 Hunter for embertailed jerboas.'],needs=[quest('Pandemonium',True),skill('construction',10)],items=[item('Hammer',1,2347),item('Saw',1,8794),item('Plank',2,960),item('Steel nails',4,1539),item('Bucket',2,1925),item('Fish offcuts')])
add('embertailed-jerboas','Embertailed jerboa box trapping','hunter',39,'Locus Oasis · intensive','Hunter_training',H,
 ["Progress Eagles' Peak until box traps are unlocked and bring [[Box trap]]s.",'Travel to Locus Oasis near fairy ring AJP. Three trap slots become available at 40 Hunter.'],
 ['Set box traps in the jerboa area.','Reset failed traps and collect successful catches.','Use three traps from 40 Hunter, keeping the circuit small enough to reset promptly.'],
 ['Catch your first jerboa.','Maintain three traps after 40 Hunter.','Reach 43 Hunter for falconry.'],needs=[quest("Eagles' Peak",True)],items=[item('Box trap',3,10008)])
add('razor-backed-kebbits','Razor-backed kebbit tracking','hunter',49,'Piscatoris Hunter area · intensive','Hunter_training',H,
 ['Equip a [[Noose wand]] and [[Ring of pursuit]]; bring spare rings and run-energy restoration.','Travel east from fairy ring AKQ towards the burrows before the falconry.'],
 ['Search a burrow with a ring of pursuit equipped.','Follow the revealed trail and attack the indicated bush.','Start another trail, replacing an exhausted ring and restoring run energy when needed.'],
 ['Complete a trail with a ring of pursuit.','Catch 25 kebbits and compare run-energy use.','Reach 72 for expert rumours or 73 for black chinchompas.'],items=[item('Noose wand',1,10150),item('Ring of pursuit')],recommended=[item('Stamina potion'),item('Weight-reducing clothing')])
add('rainbow-crabs','Rainbow crab trapping','hunter',77,'The Crown Jewel · normal','Hunter_training',H,
 ['Sail to The Crown Jewel with a saw, hammer, five buckets, five planks and ten nails to build the traps.','Bring [[Fine fish offcuts]] and a [[Pestle and mortar]]; use four traps until the fifth trap slot is available.'],
 ['Bait each usable trap with fine fish offcuts.','Collect the crabs and crush them into stackable rainbow crab paste.','Reset the traps and continue the circuit instead of banking every catch.'],
 ['Build the permanent traps.','Finish ten circuits using four traps.','Use the fifth trap from 80 Hunter and compare your measured pace.'],needs=[quest('Pandemonium'),skill('sailing',64)],items=[item('Hammer',1,2347),item('Saw',1,8794),item('Bucket',5,1925),item('Plank',5,960),item('Steel nails',10,1539),item('Fine fish offcuts',100,32307),item('Pestle and mortar',1,233)])
add('moonlight-moths','Moonlight moths','hunter',75,'Hunter Guild / Spacious Cove · normal','Hunter_training',H,
 ['Bring a [[Butterfly net]] and empty [[Butterfly jar]]s, or use an eligible barehand method.','Travel to the Hunter Guild basement or Spacious Cove.'],
 ['Catch a moonlight moth.','Release the moth to free the jar when training for experience.','Continue between nearby moths and compare the faster net or barehand option when unlocked.'],
 ['Catch and release ten moths.','Finish a ten-minute session.','Compare catch consistency at 85 Hunter.'],needs=[quest('Children of the Sun')],items=[item('Butterfly net',1,10010),item('Butterfly jar',5,10012)])
add('rubium-rocks','Rubium rocks','mining',48,'Charred Dungeon · normal','Rubium_rocks',15370114,
 ['Bring a pickaxe, a rope for first entry and dragonfire protection for the dungeon route.','Sail to Charred Island with 60 Sailing and enter the Charred Dungeon.'],
 ['Mine the small rubium rocks for directly stackable rubium splinters.','Move to the next rock when it depletes.','Stay on the small-rock circuit; the separate large deposits require 68 Mining and produce geodes instead.'],
 ['Reach the rocks safely.','Mine 100 rubium splinters.','Compare a 20-minute circuit against large deposits after 68 Mining.'],needs=[quest('Pandemonium'),skill('sailing',60),fact('Charred Dungeon access and dragonfire protection')],items=[item('Any pickaxe'),item('Rope',1,954),item('Dragonfire protection')])
add('lead-rocks','Lead rock power-mining','mining',60,'Deepfin Mine · intensive','Pay-to-play_Mining_training',M,
 ['Reach Deepfin Mine with 67 Sailing; bring your best usable pickaxe.','Wear superior or expert mining gloves for the efficient route; Varrock armour and a charged celestial ring can improve output.'],
 ['With superior gloves, rotate through three rocks; expert gloves permit two adjacent rocks.','Drop each ore and immediately return to mining.','Continue the circuit, watching the glove effect and ring charges if used.'],
 ['Complete a full circuit without waiting for rocks.','Practice dropping between successful mining rolls.','Compare 15 minutes against your iron-mining pace.'],needs=[quest('Pandemonium'),skill('sailing',67),fact('Superior or expert mining gloves for the reference route')],items=[item('Any pickaxe'),item('Superior mining gloves')],recommended=[item('Varrock armour'),item('Celestial ring')])
add('infernal-shale','Infernal shale mining and crushing','mining',78,'Chasm of Fire · intensive','Pay-to-play_Mining_training',M,
 ['Complete A Kingdom Divided and bring a pickaxe, hammer and chisel.','Use the standard mining loop first; the high-intensity method additionally needs Jim\'s wet cloth.'],
 ['Mine infernal shale in the Chasm of Fire.','Crush the shale with a hammer and chisel to make the tradeable output; crushing grants no Mining XP.','Bank or store the crushed shale and return. Only use the manual tick method after practising it.'],
 ['Mine and crush your first batch.','Measure a normal 15-minute session.','Compare the manual tick method against its extra effort.'],needs=[quest('A Kingdom Divided')],items=[item('Any pickaxe'),item('Hammer',1,2347),item('Chisel',1,1755)],recommended=[item("Jim's wet cloth")])
# Deposit68 and rocks48 are deliberately separate resources. Do not lower the deposit requirement.
path.write_text(json.dumps(data,separators=(',',':'),ensure_ascii=False)+'\n')
print('Added/retained ten current training routes; guide count:',len(entries))
