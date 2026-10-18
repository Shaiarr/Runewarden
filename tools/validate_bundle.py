#!/usr/bin/env python3
"""Validate and fingerprint the data actually shipped, without runtime web access."""
import argparse, hashlib, json, pathlib, sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
DATA=ROOT/'src/main/resources/com/runewarden'
MANIFEST=ROOT/'docs/data-manifest.json'
def no_duplicates(pairs):
    result={}
    for key,value in pairs:
        if key in result:raise ValueError(f'Duplicate JSON key: {key}')
        result[key]=value
    return result

def build():
    files=[]
    for path in sorted(DATA.rglob('*.json')):
        raw=path.read_bytes()
        payload=json.loads(raw,object_pairs_hook=no_duplicates)
        if not isinstance(payload,(dict,list)):
            raise ValueError(f'{path.name}: expected object or list')
        sources={}
        if isinstance(payload,dict):
            for key,value in payload.items():
                if isinstance(value,(str,int,float)) and any(word in key.lower() for word in ('source','commit','revision','retriev','cutoff','license','licence','date')):
                    sources[key]=value
        files.append({'file':path.relative_to(ROOT).as_posix(),'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest(),'provenance':sources})
    guides=json.loads((DATA/'guides.json').read_text())['entries']
    ids=[g['id'] for g in guides]
    if len(ids)!=len(set(ids)) or any(not g.get('title') or (g.get('category')!='quest' and not g.get('sections')) or (g.get('category')=='quest' and (g.get('sections') or g.get('items') or g.get('recommended') or g.get('markerPackId'))) for g in guides):
        raise ValueError('Invalid or duplicate guides')
    markers=json.loads((DATA/'marker-packs.json').read_text())['packs']
    if len({p['id'] for p in markers})!=len(markers):
        raise ValueError('Duplicate marker pack')
    for pack in markers:
        for tile in pack['tiles']:
            if not 0<=tile['regionX']<64 or not 0<=tile['regionY']<64 or not 0<=tile['z']<=3:
                raise ValueError('Marker outside native tile bounds')
    diary_path=DATA/'diary-catalog.json'
    if diary_path.exists():
        diaries=json.loads(diary_path.read_text())['diaries']
        if len(diaries)!=48 or len({d['id'] for d in diaries})!=48:
            raise ValueError('Diary catalogue must contain all 48 distinct tiers')
    return {'format':'runewarden-data-manifest','version':1,'runeLiteVersion':'1.13.2','files':files}
def main():
    args=argparse.ArgumentParser();args.add_argument('--write',action='store_true');args=args.parse_args()
    value=build();text=json.dumps(value,indent=2,ensure_ascii=False)+'\n'
    if args.write:MANIFEST.write_text(text);print(f'Fingerprint recorded for {len(value["files"])} datasets')
    elif not MANIFEST.exists() or MANIFEST.read_text()!=text:
        sys.exit('Bundled data changed. Review it, then run python3 tools/validate_bundle.py --write')
    else:print(f'Validated {len(value["files"])} bundled datasets and their SHA-256 fingerprints')
if __name__=='__main__':main()
