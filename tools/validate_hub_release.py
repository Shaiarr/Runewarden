#!/usr/bin/env python3
"""Mechanical production API and supported release checks; not Hub approval."""
from pathlib import Path
import re

tokens = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')
def code(path):
    return tokens.sub(' ', path.read_text())

rules = {
    'forbidden Java/API type': r'\b(?:Runtime|ProcessBuilder|ClassLoader|URLClassLoader|ObjectInputStream|ObjectOutputStream|Robot|Unsafe|KeyboardFocusManager|HttpURLConnection|HttpClient|FileInputStream|FileOutputStream|RandomAccessFile)\b',
    'reflection': r'java\s*\.\s*lang\s*\.\s*reflect\b|\bClass\s*\.\s*forName\s*\(|\.(?:getDeclaredField|getDeclaredMethod|setAccessible)\s*\(',
    'direct file IO': r'java\s*\.\s*io\s*\.\s*File\b|java\s*\.\s*nio\s*\.\s*file\s*\.\s*(?:Files|Path)\b',
    'private HTTP/JSON instance': r'\bnew\s+(?:Gson|OkHttpClient)\s*\(',
    'game input or packet API': r'\.(?:menuAction|invokeMenuAction|setCanSendPackets|dispatchEvent)\s*\(',
    'native plugin activation': r'\.(?:setPluginEnabled|startPlugin)\s*\(',
    'external desktop/process access': r'\bDesktop\s*\.|\bThread\s*\.\s*(?:sleep|interrupt)\s*\(',
    'direct Java Sound API': r'\bjavax\s*\.\s*sound\b',
}
def validate(root):
    root = Path(root)
    production = root / 'src/main/java'
    failures = []
    for path in production.rglob('*.java'):
        text = code(path)
        for label, pattern in rules.items():
            if re.search(pattern, text): failures.append(f'{path.relative_to(root).as_posix()}: {label}')
        # RuneLite exposes these APIs for an explicitly requested world change. Only
        # the manually operated hopper owns them; its intent/lifecycle guards have
        # producer regressions. Combat, activity and timer modules may never hop.
        if path != production / 'com/runewarden/WorldHopperTool.java' and re.search(
            r'\.(?:hopToWorld|changeWorld)\s*\(', text):
            failures.append(f'{path.relative_to(root).as_posix()}: world hopping outside the manual hopper')

    owned = production / 'com/runewarden'
    wave = code(owned / 'WaveExportTool.java')
    if re.search(r'\b(?:Capture|NPC|Player|WorldView|LocalPoint|WorldPoint)\b|\bclientThread\s*\.\s*invokeLater\s*\(', wave):
        failures.append('Wave references still access or export arena state')
    if re.search(r'\.(?:getConfiguration|setConfiguration|unsetConfiguration)\s*\(', code(owned / 'HudLayoutPositions.java')):
        failures.append('HUD positions still read/write native configuration directly')
    return failures

def main():
    failures = validate(Path(__file__).resolve().parents[1])
    if failures:
        raise SystemExit('\n'.join(failures))
    print('Hub release surface checks passed; external review remains required')

if __name__ == '__main__': main()
