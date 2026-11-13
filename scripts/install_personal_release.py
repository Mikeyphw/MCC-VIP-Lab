#!/usr/bin/env python3
"""Install only a signed personal-release artifact built from the local main HEAD."""
import argparse, hashlib, json, os, subprocess, tempfile
from pathlib import Path

REPO='Mikeyphw/MCC-VIP-Lab'


def run(*args,cwd=None):
    p=subprocess.run(args,cwd=cwd,capture_output=True,text=True,errors='replace')
    if p.returncode:
        raise SystemExit('MCC_PERSONAL_INSTALL_REFUSED: '+args[0]+' exited '+str(p.returncode)+': '+
                         (p.stderr or p.stdout).strip()[:300])
    return p.stdout.strip()


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--serial',default='localhost:5555')
    ap.add_argument('--no-install',action='store_true')
    args=ap.parse_args()
    root=Path(__file__).resolve().parents[1]
    if run('git','branch','--show-current',cwd=root)!='main':
        raise SystemExit('MCC_PERSONAL_INSTALL_REFUSED: checkout must be main')
    if run('git','status','--porcelain',cwd=root):
        raise SystemExit('MCC_PERSONAL_INSTALL_REFUSED: dirty checkout')
    head=run('git','rev-parse','HEAD',cwd=root)
    api='repos/'+REPO+'/actions/workflows/personal-release.yml/runs?branch=main&status=success&per_page=30'
    runs=json.loads(run('gh','api',api))['workflow_runs']
    valid=[x for x in runs if x.get('head_sha')==head and x.get('conclusion')=='success']
    if not valid: raise SystemExit('MCC_PERSONAL_INSTALL_REFUSED: no signed CI artifact for exact HEAD '+head)
    with tempfile.TemporaryDirectory(prefix='mcc-personal-apk-',dir=os.environ.get('TMPDIR') or None) as td:
        run('gh','run','download',str(valid[0]['id']),'-R',REPO,'-n','mcc-personal-release','-D',td)
        apks=list(Path(td).rglob('*.apk'))
        if len(apks)!=1: raise SystemExit('MCC_PERSONAL_INSTALL_REFUSED: expected one APK')
        apk=apks[0]
        print('MCC_PERSONAL_APK_SHA256='+hashlib.sha256(apk.read_bytes()).hexdigest())
        if args.no_install:
            dest=root/'artifacts'/'mcc-vip-lab-personal.apk'
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(apk.read_bytes())
            print('MCC_PERSONAL_APK_SAVED='+str(dest))
        else:
            print(run('adb','-s',args.serial,'install','-r',str(apk)))
            print('MCC_PERSONAL_APK_INSTALLED')

if __name__=='__main__':main()
