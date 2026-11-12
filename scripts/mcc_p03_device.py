#!/usr/bin/env python3
"""Local MCC P03 device controls and UI checks. Uses ADB; no cloud mutations."""
import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

TARGET = 'br.art.code.meucronogramacapilar'
XML_PATH = '/data/local/tmp/mcc-p03-screen.xml'
MODES = {'observe', 'freeze', 'high'}
BANNER_MODES = {'observe', 'hide'}


def adb(serial, *args, check=True, timeout=45):
    cp = subprocess.run(['adb', '-s', serial, *args], text=True, capture_output=True,
                        errors='replace', timeout=timeout)
    if check and cp.returncode:
        raise RuntimeError(f'ADB failed: {cp.stderr.strip() or cp.stdout.strip()}')
    return cp.stdout


def get_settings(serial, key):
    return adb(serial,'shell','settings','get','global',key).strip()


def put_settings(serial, key, value):
    adb(serial,'shell','settings','put','global',key,str(value))


def current_app(serial):
    dump = adb(serial,'shell','dumpsys','window','windows',timeout=30,check=False)
    found = re.search(r'mCurrentFocus\s*=\s*[^\n]+', dump)
    if found:
        return TARGET in found.group(0)
    dump = adb(serial,'shell','dumpsys','activity','activities',timeout=30,check=False)
    matches = re.findall(r'(?m)^.*(?:topResumedActivity|mResumedActivity)\b.*$',dump)
    return bool(matches and TARGET in matches[-1])


def xml_nodes(serial):
    result = adb(serial,'shell','uiautomator','dump',XML_PATH,timeout=60,check=False)
    if 'dumped' not in result.lower() and 'hierarchy' not in result.lower():
        raise RuntimeError('UI hierarchy unavailable (Flutter semantics may be inaccessible): '+result.strip()[-170:])
    try:
        raw = adb(serial,'shell','cat',XML_PATH,timeout=30)
        tree = ET.fromstring(raw)
        return list(tree.iter('node'))
    finally:
        adb(serial,'shell','rm','-f',XML_PATH,check=False)


def popup_target(serial):
    if not current_app(serial):
        print('MCC_P03_POPUP_NOT_FOREGROUND: refusing taps outside target app')
        return None
    for node in xml_nodes(serial):
        label = (node.attrib.get('text','')+' '+node.attrib.get('content-desc','')).casefold()
        if 'continuar com bloqueio' in label:
            b = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
            if not b: continue
            x1,y1,x2,y2 = map(int,b.groups())
            if x2 > x1 and y2 > y1:
                return ((x1+x2)//2, (y1+y2)//2)
    return None


def popup_dismiss(serial, duration):
    start = time.monotonic()
    attempts = 0
    while True:
        try:
            hit = popup_target(serial)
            if hit:
                # Only taps the exact app-supplied 'Continuar com bloqueio' control.
                adb(serial,'shell','input','tap',str(hit[0]),str(hit[1]))
                print('MCC_P03_POPUP_DISMISS_TAPPED_CONTINUAR_COM_BLOQUEIO')
                return
        except Exception as e:
            print('MCC_P03_POPUP_UNAVAILABLE', str(e))
            if duration == 0: return
        if duration == 0 or time.monotonic()-start >= duration:
            print('MCC_P03_POPUP_BUTTON_NOT_FOUND (not a bypass)')
            return
        time.sleep(1.7)
        attempts += 1


def screen_scan(serial):
    if not current_app(serial):
        print('MCC_P03_SCREEN_NOT_FOREGROUND')
        return
    nodes = xml_nodes(serial)
    fields = {'vip':0,'diamond':0,'dns_popup':0,'banner':0}
    tokens = {'vip':['vip','premium','assinatura'],
              'diamond':['diamante','diamond','💎'],
              'dns_popup':['bloqueador de an','dns','continuar com bloqueio'],
              'banner':['anúncio','anuncio','publicidade']}
    for n in nodes:
        label = (n.attrib.get('text','')+' '+n.attrib.get('content-desc','')).casefold()
        for kind, words in tokens.items():
            if any(word in label for word in words):fields[kind]+=1
    for kind,count in fields.items():print(f'MCC_P03_UI_{kind.upper()}_LABELS={count}')
    print('MCC_P03_UI_NOTE=Accessibility labels are not proof of Dart business state')


def main():
    ap = argparse.ArgumentParser(description='MCC VIP Lab P03: local-only test controls')
    ap.add_argument('--serial',default='localhost:5555')
    sub = ap.add_subparsers(dest='command',required=True)
    sub.add_parser('status')
    diamond = sub.add_parser('diamond',help='Override known numeric local preference reads only')
    diamond.add_argument('mode',choices=sorted(MODES))
    diamond.add_argument('--value',type=int,default=999999)
    banner = sub.add_parser('banner',help='Hide native ad views only, not Flutter layout slots')
    banner.add_argument('mode',choices=sorted(BANNER_MODES))
    popup = sub.add_parser('popup',help='Find and click the existing Continue blocking button')
    popup.add_argument('--watch',type=int,default=0,metavar='SECONDS')
    sub.add_parser('screen',help='Summarize app UI semantics without dumping personal text')
    marker = sub.add_parser('mark',help='Add a test action marker to logcat')
    marker.add_argument('kind',choices=['vip','diamond','popup','banner'])
    args = ap.parse_args()
    try:
        if args.command=='status':
            print('MCC_P03_DIAMOND_MODE='+get_settings(args.serial,'mcc_lab_diamond_mode'))
            print('MCC_P03_DIAMOND_HIGH='+get_settings(args.serial,'mcc_lab_diamond_high'))
            print('MCC_P03_BANNER_MODE='+get_settings(args.serial,'mcc_lab_banner_mode'))
            print('MCC_P03_NOTICE=Modes are scoped to the target process by LSPosed')
        elif args.command=='diamond':
            if not 0 <= args.value <= 1000000000:
                raise ValueError('high value must be in 0..1000000000')
            put_settings(args.serial,'mcc_lab_diamond_high',args.value)
            put_settings(args.serial,'mcc_lab_diamond_mode',args.mode)
            print('MCC_P03_DIAMOND_MODE_SET='+args.mode)
            print('MCC_P03_DIAMOND_NOTE=Only exact known SharedPreferences keys can be overridden; Dart-only/server balances unaffected')
        elif args.command=='banner':
            put_settings(args.serial,'mcc_lab_banner_mode',args.mode)
            print('MCC_P03_NATIVE_BANNER_MODE='+args.mode)
            print('MCC_P03_BANNER_NOTE=May not remove Flutter-side reserved space; use only with blocked/test ad traffic')
        elif args.command=='popup':
            if args.watch < 0 or args.watch > 300:raise ValueError('--watch must be 0..300')
            popup_dismiss(args.serial,args.watch)
        elif args.command=='screen': screen_scan(args.serial)
        elif args.command=='mark':
            adb(args.serial,'shell','log','-t','MCCP03CASE',args.kind.upper()+'_FEATURE_ATTEMPT')
            print('MCC_P03_MARKED='+args.kind)
    except (RuntimeError,ValueError,subprocess.TimeoutExpired) as e:
        print('MCC_P03_DEVICE_REFUSED:',e,file=sys.stderr)
        return 1
    return 0

if __name__=='__main__':sys.exit(main())
