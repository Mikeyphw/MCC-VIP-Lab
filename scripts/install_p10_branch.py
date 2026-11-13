#!/usr/bin/env python3
"""Install exactly the successful feature-branch APK matching local HEAD.
No Gradle, no broad artifact lookup, and no install without explicit invocation.
"""
import argparse
import hashlib
import json
import os
import subprocess
import tempfile
from pathlib import Path

REPO = 'Mikeyphw/MCC-VIP-Lab'
BRANCH = 'feature/p10-auto-refill-diamonds-9'
ARTIFACT = 'mcc-vip-lab-debug'

def run(*args, cwd=None):
    p = subprocess.run(args, cwd=cwd, capture_output=True, text=True, errors='replace')
    if p.returncode:
        raise SystemExit('P10_INSTALL_REFUSED: ' + ' '.join(args[:3]) + ': ' + (p.stderr or p.stdout).strip()[:450])
    return p.stdout.strip()

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--serial', default='localhost:5555')
    ap.add_argument('--no-install', action='store_true', help='Download APK to artifacts/ only')
    args=ap.parse_args()
    root=Path(__file__).resolve().parents[1]
    branch=run('git','branch','--show-current',cwd=root)
    if branch != BRANCH: raise SystemExit('P10_INSTALL_REFUSED: checkout must be '+BRANCH)
    head=run('git','rev-parse','HEAD',cwd=root)
    if run('git','status','--porcelain',cwd=root): raise SystemExit('P10_INSTALL_REFUSED: dirty worktree')
    api=f'repos/{REPO}/actions/runs?branch={BRANCH}&status=success&per_page=30'
    runs=json.loads(run('gh','api',api)).get('workflow_runs',[])
    matches=[x for x in runs if x.get('head_sha')==head and x.get('path')=='.github/workflows/android.yml'
             and x.get('conclusion')=='success']
    if not matches: raise SystemExit('P10_INSTALL_REFUSED: no successful Android CI build for local HEAD '+head)
    run_id=str(matches[0]['id'])
    with tempfile.TemporaryDirectory(prefix='mcc-p10-apk-',dir=os.environ.get('TMPDIR') or None) as td:
        run('gh','run','download',run_id,'-R',REPO,'-n',ARTIFACT,'-D',td)
        apks=list(Path(td).rglob('*.apk'))
        if len(apks)!=1: raise SystemExit('P10_INSTALL_REFUSED: expected exactly one APK; found '+str(len(apks)))
        apk=apks[0]
        print('P10_SUCCESSFUL_CI_RUN='+run_id)
        print('P10_APK_SHA256='+hashlib.sha256(apk.read_bytes()).hexdigest())
        if args.no_install:
            target=root/'artifacts'/'mcc-p10-auto-refill-debug.apk'
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(apk.read_bytes())
            print('P10_APK_SAVED='+str(target))
        else:
            print(run('adb','-s',args.serial,'install','-r',str(apk)))
            print('P10_APK_INSTALLED='+args.serial)

if __name__ == '__main__': main()
