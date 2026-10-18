#!/usr/bin/env python3
"""Build exact ID-encoded dose/charge facts from the pinned RuneLite source archives."""
import argparse,json,pathlib,re,zipfile
p=argparse.ArgumentParser();p.add_argument('client_sources');p.add_argument('api_sources');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
with zipfile.ZipFile(a.client_sources) as z:source=z.read('net/runelite/client/plugins/itemcharges/ItemWithCharge.java').decode()
with zipfile.ZipFile(a.api_sources) as z:ids=z.read('net/runelite/api/gameval/ItemID.java').decode()
# Only top-level constants: nested certificate and placeholder IDs must never shadow real IDs.
clean=re.sub(r'/\*.*?\*/|//[^\n]*','',ids,flags=re.S);depth=0;constants={}
for line in clean.splitlines():
 if depth==1:
  m=re.search(r'public static final int ([A-Z0-9_]+) = ([0-9]+);',line)
  if m:constants[m[1]]=int(m[2])
 depth+=line.count('{')-line.count('}')
rows=[]
for kind,name,count in re.findall(r'\b[A-Z0-9_]+\(\s*([A-Z0-9_]+),\s*ItemID\.([A-Z0-9_]+),\s*([0-9]+)\)',source):
 if name not in constants:raise ValueError(name)
 rows.append({'itemId':constants[name],'type':kind,'charges':int(count)})
if len(rows)<400 or len({r['itemId'] for r in rows})!=len(rows):raise ValueError('Unexpected charge reference')
data={'runeLiteVersion':'1.13.2','source':'https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/itemcharges/ItemWithCharge.java','license':'BSD-2-Clause','items':rows}
(root/'src/main/resources/com/runewarden/item-charge-reference.json').write_text(json.dumps(data,indent=2)+'\n')
(root/'src/main/resources/com/runewarden/licenses/item-charge-reference-BSD-2-Clause.txt').write_text(source[:source.index('package ')])
print(f'{len(rows)} exact item-state charge references')
