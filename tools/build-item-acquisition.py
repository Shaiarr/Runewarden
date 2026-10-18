#!/usr/bin/env python3
"""Build compact direct acquisition facts from existing Wiki-licensed exact-ID snapshots.
No network, derived drop probabilities, custom rarity, shared-table expansion or shop-price guesses.
Usage: build-item-acquisition.py --drops npc-drops.json --shops shops-items-by-shop.json
       --items items-complete.json [--output src/main/resources/com/runewarden/item-acquisition.json]
"""
import argparse, collections, hashlib, json, pathlib, urllib.parse

def build(drops,shops,items):
    cards=collections.defaultdict(list); seen=set()
    def wiki(title): return 'https://oldschool.runescape.wiki/w/'+urllib.parse.quote(title.replace(' ','_'),safe='()\'')
    def add(item,kind,name,detail,url):
        if not isinstance(item,int) or item<1 or item>1000000 or str(item) not in items:return
        key=(item,kind,name,detail)
        if key in seen:return
        seen.add(key);cards[item].append(dict(kind=kind,name=name,details=detail,wiki=url,curated=True))
    for table in drops['tables'].values():
        page=table.get('wiki_page'); label=table.get('table_name',page)
        if not page:continue
        for entry in table.get('entries',[])+table.get('tertiary',[]):
            if 'item_id' not in entry:continue
            fields=[];q=entry.get('quantity_raw')
            if q: fields.append('Quantity: '+str(q))
            elif isinstance(entry.get('quantity'),list) and len(entry['quantity'])==2:
                low,high=entry['quantity'];fields.append('Quantity: '+(str(low) if low==high else str(low)+'–'+str(high))+(' (noted)' if entry.get('noted') else ''))
            # Raw explicit Wiki rarity is admissible. Export-derived weights/floats and assumed rates are not.
            if entry.get('rarity_raw') and not entry.get('assumed_rarity'):fields.append('Rarity: '+str(entry['rarity_raw']))
            elif entry.get('always'):fields.append('Rarity: Always')
            if entry.get('section'):fields.append('Table: '+str(entry['section']))
            if entry.get('separate_roll'):fields.append('Roll: Separate roll')
            if entry.get('notes'):fields.append('Conditions: '+'; '.join(str(n) for n in entry['notes']))
            add(entry['item_id'],'Drop / reward',label,' · '.join(fields),wiki(page))
    for name,shop in shops.items():
        for item in shop.get('items',[]):
            fields=[]
            if isinstance(item.get('stock'),int):fields.append('Stock: '+str(item['stock']))
            if isinstance(item.get('restock_time'),int):fields.append('Restock: '+str(item['restock_time'])+' seconds')
            if item.get('currency'):fields.append('Currency: '+str(item['currency']))
            add(item.get('id'),'Shop',name,' · '.join(fields),wiki(name))
    for key,item in items.items():
        if item.get('noted') or item.get('placeholder'):continue
        equip=item.get('equipment') or {}; requirements=equip.get('requirements') or {}
        fields=[str(skill).capitalize()+' '+str(level) for skill,level in sorted(requirements.items()) if isinstance(level,int) and level>0]
        if fields:add(int(key),'Requirements','Equip',' · '.join(fields),item.get('wiki_url') or wiki(item['name']))
    result=[]
    for id,sources in sorted(cards.items()):
        item=items[str(id)]
        if item.get('noted') or item.get('placeholder'):continue
        result.append(dict(itemId=id,title=item.get('wiki_name') or item['name'],wiki=item.get('wiki_url') or wiki(item['name']),retrieved='2026-10-07',sources=sources))
    return dict(source='OSRS Wiki contributors; acquisition snapshot CC BY-NC-SA 3.0; equipment identity/requirements osrsreboxed export',retrieved='2026-10-07',scope='Direct table entries and shops only; probabilities, derived custom rarity, shared tables and unverified prices excluded',items=result)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--drops',required=True,type=pathlib.Path);parser.add_argument('--shops',required=True,type=pathlib.Path);parser.add_argument('--items',required=True,type=pathlib.Path);parser.add_argument('--output',type=pathlib.Path,default=pathlib.Path('src/main/resources/com/runewarden/item-acquisition.json'));args=parser.parse_args()
    paths=[args.drops,args.shops,args.items];data=build(*(json.loads(p.read_text()) for p in paths));data['inputs']=[dict(file=p.name,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in paths]
    args.output.parent.mkdir(parents=True,exist_ok=True)
    buckets=collections.defaultdict(list)
    for item in data['items']:buckets[item['itemId']%64].append(item)
    folder=args.output.parent/'item-acquisition';folder.mkdir(exist_ok=True)
    for bucket,items in sorted(buckets.items()):(folder/('%02d.json'%bucket)).write_text(json.dumps(dict(items=items),ensure_ascii=False,separators=(',',':'))+'\n')
    cards=sum(len(i['sources']) for i in data['items']);kinds=dict(collections.Counter(s['kind'] for i in data['items'] for s in i['sources']))
    data['cards']=cards;data['kinds']=kinds;data['items']=[dict(itemId=i['itemId'],title=i['title'],bucket=i['itemId']%64,sourceCount=len(i['sources'])) for i in data['items']]
    args.output.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n')
    print(json.dumps(dict(items=len(data['items']),cards=cards,indexBytes=args.output.stat().st_size,shards=len(buckets),kinds=kinds)))
if __name__=='__main__':main()
