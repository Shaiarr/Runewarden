#!/usr/bin/env python3
"""Reconstitute the retained official Hub snapshot and fetch text evidence at its pins.

The repository snapshot retains signed metadata and hashes, not third-party
README or Java contents. Only non-executable text files are downloaded; no JARs,
plugin builds or client configuration are fetched or run.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
SNAPSHOT=ROOT/'docs/hub-source-snapshot'
CERTIFICATE_SHA='284416508f83a69e9b5c34520ccf7a8874b948bd170908ab253fd688fcf28507'

def download(task):
    url,target,expected=task
    if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest()==expected:return
    request=urllib.request.Request(url,headers={'User-Agent':'Runewarden-source-audit/1.0'})
    with urllib.request.urlopen(request,timeout=45) as response:
        data=response.read(2_000_001)
    if len(data)>2_000_000:raise ValueError('Source file exceeds text evidence limit: '+url)
    if hashlib.sha256(data).hexdigest()!=expected:raise ValueError('Pinned source hash mismatch: '+url)
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes(data)

def restore(output,fetch=True):
    output.mkdir(parents=True,exist_ok=True)
    for name in ('manifest-signed.bin','api-latest-counts.json','hub-markers.json'):
        (output/name).write_bytes(gzip.decompress((SNAPSHOT/(name+'.gz')).read_bytes()))
    cert=(SNAPSHOT/'externalplugins.crt').read_bytes()
    if hashlib.sha256(cert).hexdigest()!=CERTIFICATE_SHA:raise ValueError('Pinned native certificate differs')
    (output/'externalplugins.crt').write_bytes(cert)
    raw=(output/'manifest-signed.bin').read_bytes();size=struct.unpack('>I',raw[:4])[0]
    if size!=512 or size+4>=len(raw):raise ValueError('Unexpected signed manifest envelope')
    (output/'manifest.sig').write_bytes(raw[4:4+size]);(output/'manifest-data.json').write_bytes(raw[4+size:])
    public=subprocess.run(['openssl','x509','-in',str(output/'externalplugins.crt'),'-pubkey','-noout'],check=True,capture_output=True).stdout
    (output/'manifest-public.pem').write_bytes(public)
    subprocess.run(['openssl','dgst','-sha256','-verify',str(output/'manifest-public.pem'),'-signature',str(output/'manifest.sig'),str(output/'manifest-data.json')],check=True,capture_output=True)
    tasks=[]
    for filename in ('readme-index.json','java-index.json'):
        index=json.loads((SNAPSHOT/filename).read_text())
        shutil.copyfile(SNAPSHOT/filename,output/filename)
        for slug,row in index.items():
            if filename=='readme-index.json':tasks.append((row['source'],output/'readmes'/(slug+'.md'),row['sha256']))
            else:
                for source in row.get('files',[]):tasks.append((source['source'],output/source['local'],source['sha256']))
    if fetch:
        with ThreadPoolExecutor(max_workers=8) as pool:
            futures=[pool.submit(download,task) for task in tasks]
            for future in as_completed(futures):future.result()
    return len(tasks)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--output-dir',type=Path,required=True);parser.add_argument('--metadata-only',action='store_true')
    args=parser.parse_args();count=restore(args.output_dir.resolve(),not args.metadata_only)
    print('Verified retained signed metadata; '+('prepared' if args.metadata_only else 'verified/fetched')+' '+str(count)+' source-text references.')
    print('Rebuild: python3 tools/build-hub-coverage-audit.py --source-dir '+str(args.output_dir.resolve()))

if __name__=='__main__':main()
