#!/usr/bin/env python3
"""Install exactly the successful MCC VIP Lab APK built from local HEAD."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

REPO = 'Mikeyphw/MCC-VIP-Lab'
ARTIFACT = 'mcc-vip-lab-debug'


def command(*args, cwd=None):
    p = subprocess.run(args, cwd=cwd, capture_output=True, text=True)
    if p.returncode:
        raise SystemExit('COMMAND FAILED: ' + ' '.join(args[:3]) + ': ' + (p.stderr.strip() or p.stdout.strip()))
    return p.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description='Download/install latest successful APK for exactly the local main commit')
    parser.add_argument('--serial', default='localhost:5555')
    parser.add_argument('--no-install', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    head = command('git', 'rev-parse', 'HEAD', cwd=root)
    current_branch = command('git','branch','--show-current',cwd=root)
    if current_branch != 'main': raise SystemExit('REFUSED: expected main branch')
    raw = command('gh', 'run', 'list', '-R', REPO, '-w', 'android.yml', '-b', 'main',
                  '-s', 'success', '-L', '1', '--json', 'databaseId,headSha')
    runs = json.loads(raw)
    if not runs:
        raise SystemExit('No successful Android workflow builds yet')
    latest = runs[0]
    if latest.get('headSha') != head:
        raise SystemExit('REFUSED_STALE_BUILD: latest successful run is not this checkout commit. Check `gh run list -R '+REPO+'` and try after your P02 build succeeds.')
    run = str(latest['databaseId'])
    with tempfile.TemporaryDirectory(prefix='mcc-vip-apk-', dir=os.environ.get('TMPDIR') or None) as td:
        command('gh','run','download',run,'-R',REPO,'-n',ARTIFACT,'-D',td)
        matches = list(Path(td).rglob('*.apk'))
        if len(matches) != 1:
            raise SystemExit(f'REFUSED: expected exactly one APK, found {len(matches)}')
        apk = matches[0]
        print('MCC_LATEST_SUCCESS_RUN',run)
        print('MCC_APK_SHA256',hashlib.sha256(apk.read_bytes()).hexdigest())
        if args.no_install:
            target=root/'artifacts'/'mcc-vip-lab-debug.apk'
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(apk.read_bytes())
            print('MCC_APK_SAVED',target)
        else:
            print(command('adb','-s',args.serial,'install','-r',str(apk)))
            print('MCC_APK_INSTALLED',args.serial)

if __name__ == '__main__': main()
