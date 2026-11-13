#!/usr/bin/env python3
"""Install only a successful P11 branch artifact with an exact HEAD match; no local Gradle."""
import argparse, hashlib, json, os, subprocess, tempfile
from pathlib import Path

REPO='Mikeyphw/MCC-VIP-Lab'
BRANCH='feature/p11-banner-upsell-cleanup'

def run(*cmd,cwd=None):
    p=subprocess.run(cmd,cwd=cwd,capture_output=True,text=True,errors='replace')
    if p.returncode:
        raise SystemExit('MCC_P11_INSTALL_REFUSED '+ ' '.join(cmd[:3]) + ' '+(p.stderr or p.stdout)[:450].strip())
    return p.stdout.strip()

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--serial',default='localhost:5555')
    ap.add_argument('--channel',choices=('signed','debug'),default='signed')
    ap.add_argument('--no-install',action='store_true')
    a=ap.parse_args()
    root=Path(__file__).resolve().parents[1]
    if run('git','branch','--show-current',cwd=root)!=BRANCH:
        raise SystemExit('MCC_P11_INSTALL_REFUSED expected feature branch '+BRANCH)
    if run('git','status','--porcelain',cwd=root):
        raise SystemExit('MCC_P11_INSTALL_REFUSED dirty checkout')
    head=run('git','rev-parse','HEAD',cwd=root)
    workflow='personal-release.yml' if a.channel=='signed' else 'android.yml'
    name='mcc-personal-release' if a.channel=='signed' else 'mcc-vip-lab-debug'
    result=json.loads(run('gh','api',f'repos/{REPO}/actions/runs?branch={BRANCH}&status=success&per_page=35'))
    runs=[x for x in result.get('workflow_runs',[]) if x.get('head_sha')==head and
          x.get('conclusion')=='success' and x.get('path')=='.github/workflows/'+workflow]
    if not runs:
        raise SystemExit('MCC_P11_INSTALL_REFUSED no successful '+workflow+' run for exact '+head)
    run_id=str(runs[0]['id'])
    with tempfile.TemporaryDirectory(prefix='mcc-p11-',dir=os.environ.get('TMPDIR') or None) as tmp:
        run('gh','run','download',run_id,'-R',REPO,'-n',name,'-D',tmp)
        apks=list(Path(tmp).rglob('*.apk'))
        if len(apks)!=1: raise SystemExit('MCC_P11_INSTALL_REFUSED expected exactly one APK')
        apk=apks[0]
        print('MCC_P11_RUN='+run_id)
        print('MCC_P11_APK_SHA256='+hashlib.sha256(apk.read_bytes()).hexdigest())
        if a.no_install:
            output=root/'artifacts'/('mcc-p11-'+a.channel+'.apk')
            output.parent.mkdir(exist_ok=True,parents=True)
            output.write_bytes(apk.read_bytes())
            print('MCC_P11_APK_SAVED='+str(output))
        else:
            print(run('adb','-s',a.serial,'install','-r',str(apk)))
            print('MCC_P11_INSTALLED='+a.serial)

if __name__=='__main__':main()
