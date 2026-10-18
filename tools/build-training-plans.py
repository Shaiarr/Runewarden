#!/usr/bin/env python3
"""Rebuild explicit training metadata from bundled authored guides and pinned recipe facts.

No network fetches. Curated reference rate bands already in activity-xp-methods.json
are retained; no interpolation of Wiki rates or inference of live unlocks occurs.
"""
import json
import re
from collections import Counter
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/'src/main/resources/com/runewarden'
data = json.loads((RES/'activity-xp-methods.json').read_text())
guides = {entry['id']: entry for entry in json.loads((RES/'guides.json').read_text())['entries']}
recipes = {entry['id']: entry for entry in json.loads((RES/'banked-xp-recipes.json').read_text())}
methods = {method['id']: method for method in data['methods'] if '-fletching-knife' not in method['id']}

def plain(text):
    return re.sub(r'\[\[([^]|]+)\|([^]]+)\]\]',r'\2',re.sub(r'\[\[([^]]+)\]\]',r'\1',text))

def guide_plan(guide, method=None):
    skill = guide.get('skill')
    if not skill: return
    skill = 'RUNECRAFT' if skill.lower() == 'runecrafting' else skill.upper()
    text = (guide.get('subtitle') or '').lower()
    method = method if method is not None else dict(id=guide['id'], guideId=guide['id'], name=guide['title'],skill=skill,
        level=max([1]+[r.get('value',1) for r in guide.get('requirementDetails',[]) if r.get('kind')=='skill' and r.get('key','').upper() in [skill,'RUNECRAFTING' if skill=='RUNECRAFT' else skill]]),
        intensity='AFK' if any(s in text for s in ['afk','relaxed']) else 'Intensive' if any(s in text for s in ['intens','tick','sweaty']) else 'Normal', kind='time',
        source=guide['source'],note=guide.get('subtitle') or '',rates=[])
    sections = {section['id']: [plain(step['text']) for step in section.get('steps',[])] for section in guide.get('sections',[])}
    # These are authored method-specific preparation, workflow and goals, not generated fallback prose.
    method['preparation'] = sections.get('prepare',method.get('preparation',[]))
    method['loop'] = sections.get('method',method.get('loop',[]))
    method['milestones'] = sections.get('milestones',method.get('milestones',[]))
    method['unlocks'] = guide.get('requirementDetails',[])
    method['requirements'] = list(dict.fromkeys(method.get('requirements',[])+guide.get('requirements',[])))
    method['explicitPlan'] = all(method.get(key) for key in ['preparation','loop','milestones'])
    method.setdefault('attention','Check supplies, safety and interruptions throughout the session.' if method['intensity']=='AFK' else 'Manual inputs throughout the method.')
    method.setdefault('rateConditions',[method['note']] if method.get('rates') else [])
    return method

for guide in guides.values():
    if guide['category']=='training':
        methods[guide['id']] = guide_plan(guide, methods.get(guide['id']))
for method in list(methods.values()):
    if not method: continue
    guide = guides.get(method['guideId'])
    if guide and not method.get('explicitPlan'): guide_plan(guide, method)
# Exact separate skill selection for melee/HP is essential: an Attack guide alone is not Strength coverage.
for skill in ['ATTACK','STRENGTH','DEFENCE','HITPOINTS']:
    for base,intensity,attention in [('sand-crabs','AFK','Check health and aggression; reset aggression manually when needed.'),
                                    ('low-level-melee','Normal','Choose a safe opponent and watch food and health.'),
                                    ('scurrius-training','Intensive','React manually to every mechanic; this is active combat.')]:
        guide = guides['training:'+base]
        mid = 'training:'+base if skill=='ATTACK' else 'activity:'+skill.lower()+'-'+base
        method = guide_plan(guide,methods.get(mid)) if skill=='ATTACK' else guide_plan(guide)
        method.update(id=mid, skill=skill, intensity=intensity, name=guide['title']+' · '+skill.title(),attention=attention)
        method['preparation']=list(method['preparation'])+[('Select a style that awards '+skill.title()+' XP; avoid shared/controlled styles for this scenario.' if skill!='HITPOINTS' else 'Use normal damage-based combat; special XP multipliers vary by monster.')]
        method['xpPerAction']=4 if skill!='HITPOINTS' and base!='scurrius-training' else 0
        method['actionUnit']='damage dealt'
        method['rateBasis']='Standard damage-based combat XP; monster XP multipliers and shared styles excluded. Hitpoints and Scurrius use observed XP instead of an approximate damage model.'
        methods[mid]=method
# Recipe identities join the quantitative planner to the bank model, without allocating the same stock again.
links={
'glass-lanterns':'crafting:lantern_lens','glass-orbs':'crafting:unpowered_orb','glass-light-orbs':'crafting:light_orb',
'spin-flax-wheel':'crafting:bow_string','air-battlestaves':'crafting:air_battlestaff','earth-battlestaves':'crafting:earth_battlestaff',
'water-battlestaves':'crafting:water_battlestaff','fire-battlestaves':'crafting:fire_battlestaff',
'wooden-chairs':None,'oak-larders':'construction:oak_larder','teak-benches':'construction:teak_bench','mahogany-tables':'construction:mahogany_table',
'attack-potions':'herblore:attack_potion_3','antipoisons':'herblore:antipoison_3','strength-potions':'herblore:strength_potion_3','energy-potions':'herblore:energy_potion_3',
'prayer-potions':'herblore:prayer_potion_3','super-attack':'herblore:super_attack_3','super-energy':'herblore:super_energy_3','super-strength':'herblore:super_strength_3',
'super-restore':'herblore:super_restore_3','super-defence':'herblore:super_defence_3','ranging-potions':'herblore:ranging_potion_3','magic-potions':'herblore:magic_potion_3','saradomin-brews':'herblore:saradomin_brew_3',
'headless-arrows':'fletching:headless_arrow','wines':'cooking:jug_of_wine','karambwan-cooking':'cooking:cooked_karambwan','karambwan-1tick':'cooking:cooked_karambwan',
'shrimp-cooking-intro':'cooking:shrimps'}
for gem in ['sapphire','emerald','ruby','diamond','dragonstone']:links['cut-'+gem+('s' if gem!='ruby' else 'ies')]='crafting:'+gem
links['cut-rubies']='crafting:ruby'
for gem in ['emerald','ruby','diamond']:links[gem+'-bracelets']='crafting:'+gem+'_bracelet'
for color in ['green','blue','red','black']:links[color+'-dhide']='crafting:'+color+'_dhide_body'
for wood in ['oak','willow','maple','yew','magic']:links[wood+'-longbows']='fletching:'+wood+'_longbow_u'
for metal in ['mithril','adamant','rune','amethyst','dragon']:links[metal+'-darts']='fletching:'+metal+'_dart'
for wood in ['logs','oak','willow','maple','yew','magic','redwood']:links['burn-'+wood]='firemaking:'+(wood if wood=='logs' else wood+'_logs')
for fish,key in [('trout','trout'),('salmon','salmon'),('lobster','lobster'),('swordfish','swordfish'),('monkfish','monkfish'),('sharks','shark'),('anglers','anglerfish')]:links['cook-'+fish]='cooking:'+key
for key,rid in links.items():
    method=methods.get('training:'+key)
    if not method or rid is None:continue
    recipe=recipes[rid]
    method.update(recipeId=rid,xpPerAction=recipe['xp'],actionUnit='successful products',rateBasis=recipe['source'])
# Exact successful actions/catches, not guessed hourly throughput or failure rates.
actions={
'high-alchemy':(65,'casts','High_Level_Alchemy'),
'karambwan':(50,'fish','Raw_karambwan'),'monkfish':(120,'fish','Raw_monkfish'),'shark-fishing':(110,'fish','Raw_shark'),'anglerfish':(120,'fish','Raw_anglerfish'),
'iron-power':(35,'ores','Iron_ore'),'coal-mining':(50,'ores','Coal'),'amethyst':(240,'minerals','Amethyst'),
'trees-normal':(25,'logs','Tree'),'trees-oak':(37.5,'logs','Oak_tree'),'trees-willow':(67.5,'logs','Willow_tree'),'trees-maple':(100,'logs','Maple_tree'),
'trees-teak':(85,'logs','Teak_tree'),'trees-yew':(175,'logs','Yew_tree'),'trees-magic':(250,'logs','Magic_tree'),'trees-redwood':(380,'logs','Redwood_tree'),
'pickpocket-men':(8,'successful pickpockets','Man'),'fruit-stalls':(28.5,'successful steals','Fruit_Stall'),'ardougne-knights':(84.3,'successful pickpockets','Knight_of_Ardougne'),
'master-farmers':(43,'successful pickpockets','Master_Farmer'),'red-chinchompas':(265,'catches','Carnivorous_chinchompa'),
'maniacal-monkey-hunter':(1000,'catches','Maniacal_monkey_(Hunter)'),'falconry':(104,'spotted kebbits','Spotted_kebbit'),
'air-runes':(5,'essence','Air_rune'),'fire-runes':(7,'essence','Fire_rune'),'body-runes':(7.5,'essence','Body_rune'),
'nature-runes':(9,'essence','Nature_rune'),'astral-runes':(8.7,'essence','Astral_rune'),'law-runes':(9.5,'essence','Law_rune'),
'death-runes':(10,'essence','Death_rune'),'true-blood-runes':(10.5,'essence','Blood_rune'),'arceuus-blood':(23.8,'dark essence fragments','Blood_rune'),
'arceuus-soul':(29.7,'dark essence fragments','Soul_rune'),'wrath-runes':(8,'essence','Wrath_rune'),
'cannonballs':(25.6,'steel bars','Cannonball')}
for key,(xp,unit,page) in actions.items():
    method=methods.get('training:'+key)
    if method:method.update(xpPerAction=xp,actionUnit=unit,rateBasis='https://oldschool.runescape.wiki/w/'+page)
# Curated sources retrievable via public Wiki index. Keep rate assumptions visible in detail disclosure.
def rates(mid,bands,source,conditions):
    m=methods[mid];m['rates']=[dict(minLevel=low,maxLevel=high,minXpPerHour=a,maxXpPerHour=b,scalesWithLevel=False) for low,high,a,b in bands]
    m['rateBasis']=source;m['rateConditions']=conditions
rates('training:high-alchemy',[(55,99,78000,78000)],'https://oldschool.runescape.wiki/w/High_Level_Alchemy',
      ['Continuous 5-tick casts: 1,200 casts/hour; excludes interruptions and free casts that award no XP.'])
rates('training:arceuus-blood',[(77,99,38000,38000)],'https://oldschool.runescape.wiki/w/Runecraft',
      ['Wiki approximate training rate; route, pouches and interruptions change pace. No daeyalt or outfit XP multiplier assumed.'])
rates('training:arceuus-soul',[(90,99,47000,47000)],'https://oldschool.runescape.wiki/w/Runecraft',
      ['Wiki approximate training rate; route and interruptions change pace.'])
rates('training:lava-runes',[(75,99,68000,75000)],'https://oldschool.runescape.wiki/w/Runecraft',
      ['Binding necklaces, Magic Imbue, fast transport and practised solo route. Faster newer setups may differ.'])
# Tithe is repeatable active Farming; tree-run time is not sustainable XP/hour.
tithe_id='training:tithe-farm' if 'training:tithe-farm' in guides else 'minigame:tithe-farm'
if tithe_id in guides:
    tithe=guide_plan(guides[tithe_id])
    if tithe:
        methods[tithe_id]=tithe
        rates(tithe_id,[(34,53,28273,28273),(54,73,65970,65970),(74,99,108380,108380)],'https://oldschool.runescape.wiki/w/Tithe_Farm/Strategies',
              ['20×5 strategy, 19m30s per100fruit, no deaths, no farmer outfit. Learning and reduced planting batches are slower.'])
        tithe['intensity']='Intensive'
        tithe['preparation']=['Bring a spade, seed dibber and eight filled watering cans or Gricoller\'s can.','Choose golovanova at34, bologano at54 or logavano at74 Farming.']
        tithe['loop']=['Practice eight plants first; water each immediately after planting.','Water in planting order through every growth stage.','Harvest, deposit fruit and refill cans before increasing the batch.']
        tithe['milestones']=['Keep eight plants alive.','Finish100fruit with no deaths.','Practice20plant batches before timing a full20×5 round.']
        tithe['explicitPlan']=True
# Passive runs must never present active-time extrapolation as continuous training.
for m in methods.values():
    if not m:continue
    if m['skill']=='FARMING' and m['id']!=tithe_id:
        m['attention']='Short manual runs separated by crop growth; not continuous AFK training.'
        m['rateConditions']=['Growth time, patch availability, disease and protection differ; use a measured per-run result, not active-time XP/hour.']
    if m['skill']=='SLAYER':
        m['attention']='Task-dependent combat; choose low-input eligible tasks for relaxed sessions. No task is universally AFK.'
        m['rateConditions']=['Master, task list, blocks, skips, travel, gear and targets determine XP/hour; use your measured pace.']
    if m['skill'] in ['AGILITY','THIEVING','CONSTRUCTION'] and m['intensity']=='AFK':
        m['attention']='Lower-input option; repeated manual actions remain necessary.'
    if m['skill']=='SAILING':
        m['rateConditions']=['Current source marks universal hourly rates obsolete or provisional. Use exact trial/delivery rewards or your measured pace.']
    if m['id'].startswith('activity:foundry-'):
        m['milestones']=['Forge one sword without damaging its quality.','Complete five commissions and record final quality.','Unlock better moulds; compare the next eligible alloy at50,70or85 Smithing.']
    if m.get('recipeId') and m['skill']=='COOKING':
        m['rateConditions']=['XP per successful product only. Burns and bad wine use supplies without granting this XP. Equipment and location alter burn chance.']
    m['preparation']=[re.sub(r'(\d)([A-Za-z])',r'\1 \2',s) for s in m.get('preparation',[])]
    m['milestones']=[re.sub(r'(\d)([A-Za-z])',r'\1 \2',s) for s in m.get('milestones',[])]
# Current primary-source tables retrieved with the public Wiki API on17October2026.
# Adjacent published rows form a benchmark envelope, never an invented point estimate.
revisions={'Pay-to-play_Crafting_training':15360741,'Pay-to-play_Cooking_training':15371535,
'Pay-to-play_Fletching_training':15366667,'Pay-to-play_Herblore_training':15330632,
'Pay-to-play_Mining_training':15373229,'Pay-to-play_Fishing_training':15368158,
'Pay-to-play_Woodcutting_training':15371264,'Pay-to-play_Thieving_training':15372726,
'Pay-to-play_Firemaking_training':15303385,'Runecraft_training':15373990,
'Hunter_training':15366559,'Prayer_training':15373686,'Slayer_training':15371263,
'Pay-to-play_Magic_training':15366659,'Pay-to-play_Ranged_training':15366665}
def sourced(mid,bands,page,conditions):
    source='https://oldschool.runescape.wiki/w/'+page+'?oldid='+str(revisions[page])
    rates(mid,bands,source,conditions)
def benchmark(mid,points,page,conditions):
    bands=[]
    for i,(level,xp) in enumerate(points):
        end=points[i+1][0]-1 if i+1<len(points) else 99
        high=points[i+1][1] if i+1<len(points) else xp
        bands.append((level,end,min(xp,high),max(xp,high)))
    sourced(mid,bands,page,conditions+['Intermediate levels use the envelope between adjacent published benchmark rows; no exact intermediate pace is asserted.'])
for m in list(methods.values()):
    rid=m.get('recipeId')
    if not rid:continue
    xp=m['xpPerAction'];skill=m['skill'];low=m['level']
    if skill=='HERBLORE' and '_3' in rid:
        sourced(m['id'],[(low,99,2500*xp,2500*xp)],'Pay-to-play_Herblore_training',['Standard14×14 inventories,2500finished potions/hour, prepared unfinished potions and quick banking. Herb growing, cleaning and unfinished-potion preparation excluded.'])
        m['intensity']='Normal'
    elif skill=='CRAFTING':
        if 'cut-' in m['id']: throughput=2780;condition='2780gems/hour, ordinary chisel and efficient banking. Gem acquisition excluded.'
        elif 'glass-' in m['id']:throughput=1750;condition='1750glass products/hour; prepared molten glass. Sand, seaweed and Superglass Make time excluded.'
        elif 'battlestaves' in m['id']:throughput=2450;condition='2450battlestaves/hour, bought/prepared orbs and staves, good banking. Orb charging and alching excluded.'
        elif '-dhide' in m['id']:throughput=1685;condition='1685bodies/hour, ordinary needle/thread, prepared leather and good banking. Tanning and alching excluded.'
        elif 'bracelets' in m['id']:throughput=1400;condition='1400gem-and-gold products/hour with a convenient bank/furnace route; smelting bars excluded.'
        else:continue
        sourced(m['id'],[(low,99,throughput*xp,throughput*xp)],'Pay-to-play_Crafting_training',[condition])
    elif skill=='FLETCHING' and 'longbow_u' in rid:
        sourced(m['id'],[(low,99,1800*xp,1800*xp)],'Pay-to-play_Fletching_training',['1800unstrung bows/hour with a standard knife and good banking. Log gathering and stringing excluded.'])
        fast=dict(m);fast.update(id='activity:'+m['id'].split(':')[1]+'-fletching-knife',name=m['name']+' · fletching knife',intensity='Normal',unlocks=list(m['unlocks'])+[dict(kind='fact',key='item:Fletching knife',label='Fletching knife owned and used')],rates=[])
        methods[fast['id']]=fast
        sourced(fast['id'],[(low,99,2700*xp,2700*xp)],'Pay-to-play_Fletching_training',['2700unstrung bows/hour using the Fletching knife; good banking. Log gathering and stringing excluded.'])
    elif skill=='FIREMAKING':
        sourced(m['id'],[(low,99,1485*xp,1485*xp)],'Pay-to-play_Firemaking_training',['1485successfully lit logs/hour, continuous manual fire lines and efficient banking; acquisition time excluded. Early ignition failures lower pace.'])
sourced('training:wines',[(68,99,470000,490000)],'Pay-to-play_Cooking_training',['At68+Cooking wine cannot fail. Continuous14wine inventories with quick banking; fermentation can finish in bank. Below68 use observed pace.'])
# Modern routes and unlocks; earlier benchmark ranges in the old generic Runecraft article are replaced.
sourced('training:arceuus-blood',[(77,99,36000,36000)],'Runecraft_training',['Medium Kourend diary, running between dark altar and blood altar, no blood essence. Extra rune yield is not extraXP.'])
sourced('training:arceuus-soul',[(90,99,44000,44000)],'Runecraft_training',['Medium Kourend diary and running the soul altar route. Extra rune yield is not extraXP.'])
sourced('training:guardians-rift',[(40,49,25000,25000),(50,74,40000,40000),(75,84,50000,50000),(85,98,65000,65000),(99,99,70000,70000)],'Runecraft_training',['Best available essence pouches. At85+assumes small coordinated teams; mass-world results may be slower. Side-skill XP is excluded.'])
benchmark('training:karambwan',[(65,26500),(70,28600),(75,30600),(80,32700),(85,34700),(90,36700),(95,38600),(99,40000)],'Pay-to-play_Fishing_training',['Fish barrel, no angler outfit, banking and bait gathering included. Around10%less without barrel.'])
benchmark('training:monkfish',[(62,35800),(70,37000),(80,38500),(90,40000),(99,42000)],'Pay-to-play_Fishing_training',['Small fishing net, Swan Song and a viable Piscatoris bank route.'])
benchmark('training:barbarian-fishing',[(48,23000),(58,37000),(70,48000),(80,52000),(90,54000),(99,57000)],'Pay-to-play_Fishing_training',['No angler outfit; dropping time included. Leaping salmon require30Strength/Agility and sturgeon require45in each. Side-skill XP excluded.'])
benchmark('training:barbarian-3tick',[(48,41000),(58,68000),(70,90000),(80,97000),(90,103000),(99,108000)],'Pay-to-play_Fishing_training',['Manual3tick fishing; no angler outfit. Side-skillXPexcluded; leaping salmon require30Strength/Agility and sturgeon45in each.'])
benchmark('training:maniacal-monkey-hunter',[(60,51000),(65,59000),(70,66000),(75,74000),(80,82000),(85,89000),(90,96000),(95,104000),(99,110000)],'Hunter_training',['Baskets of bananas, banking roughly every50minutes, includes some idle time. Reset trap manually about every25–30seconds.'])
benchmark('training:ardougne-knights',[(55,86000),(60,98000),(65,109000),(70,124000),(75,141000),(80,159000),(85,182000),(90,209000),(95,240000)],'Pay-to-play_Thieving_training',['Medium Ardougne diary and dodgy necklaces; coin-pouch opening and some mistakes included. Rogue outfit increases loot, notXP.'])
benchmark('training:stealing-artefacts',[(49,150000),(55,163000),(60,174000),(65,186000),(70,197000),(75,208000),(80,219000),(85,230000),(90,241000),(95,252000),(99,261000)],'Pay-to-play_Thieving_training',['Practised route, stamina potions, Book of the Dead/Kharedst memoirs and correctly lured guards. Getting caught lowers rates.'])
sourced('training:fruit-stalls',[(25,99,34200,42750)],'Pay-to-play_Thieving_training',['One stall at34200or both stalls at42750XP/hour; drop fruit and restore run.'])
sourced('training:iron-power',[(15,59,45000,55000),(60,99,70000,80000)],'Pay-to-play_Mining_training',['Drop iron; level60+uses Mining Guild, performance and pickaxe matter.'])
benchmark('training:trees-teak',[(35,34000),(41,42000),(50,46000),(61,65000),(71,74000),(80,80000),(90,87000),(99,93000)],'Pay-to-play_Woodcutting_training',['Best regular axe before61; dragon felling axe from61; crystal felling axe from71, with rations. No Forestry eventXP.'])
benchmark('training:teak-two-tick',[(35,78000),(41,97000),(50,108000),(61,151000),(71,172000),(80,186000),(90,201000),(99,215000)],'Pay-to-play_Woodcutting_training',['Manual2tick technique, best regular axe before61, dragon felling axe61–70and crystal felling axe71+, with rations.'])
benchmark('training:teak-onehalf-tick',[(35,93000),(41,115000),(50,126000),(61,173000),(71,194000),(80,208000),(90,222000),(99,235000)],'Pay-to-play_Woodcutting_training',['Manual1.5tick Fossil Island technique, best regular axe before61, dragon felling axe61–70and crystal felling axe71+, with rations.'])
sourced('training:trees-redwood',[(90,98,55000,60000),(99,99,65000,65000)],'Pay-to-play_Woodcutting_training',['Dragon/infernal axe; higher rates require other axes and setups.'])
sourced('training:bloodwood-active',[(77,99,130000,210000)],'Pay-to-play_Woodcutting_training',['The Blood Moon Rises completed; three-tree route and dragon felling axe. Crystal felling axe can exceed this range.'])
sourced('training:engorged-bloodwood',[(77,99,70000,70000)],'Pay-to-play_Woodcutting_training',['Engorged tree near Ruined Village; lower-attention approximate reference.'])
sourced('training:plank-make',[(86,99,90000,90000)],'Pay-to-play_Magic_training',['Automatic Plank Make on mahogany logs, roughly1000casts/hour and banking. Coins and runes are consumed. Manual casting is a different faster method.'])
# Bone choices are explicit, avoiding a misleading one-rate-fits-all Prayer method.
for mode,throughput,intensity in [('automatic',1070,'AFK'),('manual',2550,'Intensive')]:
    for bone,level,xp in [('dragon',1,252),('superior-dragon',70,525)]:
        base=methods['training:gilded-altar'];m=dict(base)
        mid='activity:gilded-'+bone+'-'+mode
        m.update(id=mid,name=bone.replace('-',' ').title()+' bones · '+mode+' altar',level=level,intensity=intensity,xpPerAction=xp,actionUnit='bones offered',recipeId='prayer:'+bone.replace('-','_')+'_bones:gilded',unlocks=list(base['unlocks']),preparation=list(base['preparation'])+['Use'+bone.replace('-',' ')+'bones at a gilded altar with both incense burners lit.'],rates=[])
        methods[mid]=m
        sourced(mid,[(level,99,throughput*xp,throughput*xp)],'Prayer_training',[str(throughput)+'bones/hour; both burners lit, good banking. No runners, zealot saved-bone procs or other bonuses.'])
        m['attention']='An inventory offers automatically, then refill manually.' if mode=='automatic' else 'Manually use each bone on the altar and bank throughout.'
# Slayer preferences are task plans, never a promise every assignment supports the same method.
for variant,intensity,extra,details in [('relaxed','AFK',[],['Use a low-input assigned monster with a safe gear/sustain setup.','Maintain aggression or manually retarget, check health and bank before supplies run out.']),('barrage','Intensive',[dict(kind='skill',key='slayer',value=65,label='65Slayer for dust devils'),dict(kind='quest',key='Desert Treasure I',label='Ancient Magicks unlocked'),dict(kind='skill',key='magic',value=70,label='70Magic for Ice Burst')],['Use an eligible dust-devil or stronger multi-combat task; wear the required face protection.','Stack monsters manually, burst/barrage, loot and bank according to your budget.'])]:
    m=dict(methods['training:slayer-duradel']);mid='activity:slayer-'+variant;m.update(id=mid,name='Slayer · '+('low-input task plan' if variant=='relaxed' else 'multi-target burst/barrage plan'),intensity=intensity,unlocks=list(m['unlocks'])+extra,loop=details,rates=[])
    methods[mid]=m
# Revisions identify source facts; summaries and guide steps are Runewarden-authored.

sourced('training:feldip-weasels',[(9,99,13000,13000)],'Hunter_training',['Feldip weasel tracking with noose wand and rings of pursuit; practised route.'])
sourced('training:ruby-harvests',[(20,99,20000,20000)],'Hunter_training',['Ruby harvests combined with two copper-longtail bird snares. Two ordinary traps unlock at 20 Hunter; lower levels require personal pace.'])
sourced('training:red-crabs',[(21,99,31000,31000)],'Hunter_training',['Two neighbouring crab traps, baited with fish offcuts, nearby banking.'])
sourced('training:embertailed-jerboas',[(40,99,50000,50000)],'Hunter_training',['Three box traps at 40+ Hunter. At 39 only two ordinary traps are available; use personal pace.'])
sourced('training:razor-backed-kebbits',[(49,99,100000,130000)],'Hunter_training',['Noose wand and rings of pursuit. Up to 100000 without staminas; up to 130000 with a practised route and run restoration. Alt-account resupply rates excluded.'])
sourced('training:rainbow-crabs',[(77,79,122000,122000),(80,99,155000,155000)],'Hunter_training',['Four traps at 77–79 or five at 80+, fine fish offcuts, crush crabs into paste instead of banking. Static catch rate.'])
methods['training:razor-backed-kebbits'].update(xpPerAction=348.5,actionUnit='successful catches')
methods['training:rainbow-crabs'].update(xpPerAction=216,actionUnit='successful catches')
methods['training:rubium-rocks'].update(xpPerAction=30,actionUnit='successful ores')
# Readable quantities in authored additions; retain source URLs and keys verbatim.
for m in methods.values():
    for field in ['preparation','loop','milestones','rateConditions']:
        m[field]=[re.sub(r'(\d)([A-Za-z])',r'\1 \2',text) for text in m.get(field,[])]
# No fake complete plan: generic introduction records without a loop/milestones remain guide-only.
data['methods']=[m for m in methods.values() if m]
data['planVersion']=1
data['planSources']={'builtFrom':'Bundled authored training guides; exact XP/action from RuneLite1.13.2 recipe catalog and per-method Wiki sources.',
                    'ratePolicy':'Hourly bands are only retained/imported where sourced. Missing bands require personal pace; no interpolation.',
                    'updated':'2026-10-17','revisions':revisions}
(RES/'activity-xp-methods.json').write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
print('Methods',len(data['methods']),'explicit',sum(m.get('explicitPlan',False) for m in data['methods']))
print('Plans by skill',dict(sorted(Counter(m['skill'] for m in data['methods'] if m.get('explicitPlan')).items())))
print('Rate-bearing',sum(bool(m.get('rates')) for m in data['methods']),'exact action models',sum(m.get('xpPerAction',0)>0 for m in data['methods']))
