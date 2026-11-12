#!/usr/bin/env python3
"""Local timeline summary for MCC P03 logs; no network or device mutations."""
import argparse
import re
import subprocess
from collections import Counter, defaultdict
from pathlib import Path

CASE = re.compile(r'MCCP03CASE:\s+(\w+)')
AD = re.compile(r'MCCADID:\s+(\w+)\s+ad_id=(\d+)\s+type=(\w+)')
DIAMOND = re.compile(r'MCCDIAMOND:\s+(\w+)\s+(.*)')

def analyze(log):
    counts=Counter(); by_id=defaultdict(list); notes=[]; marks=[]
    reward_was_seen = False
    reward_write_sequences = 0
    for line in log.splitlines():
        marker=CASE.search(line)
        if marker:marks.append(marker.group(1))
        ad=AD.search(line)
        if ad:
            event,ident,typ=ad.groups();counts['ad_'+event.lower()]+=1
            if len(by_id)<200 or ident in by_id:by_id[ident].append((typ,event))
            if event in ('REWARD_CALLBACK','REWARD_INTERSTITIAL_CALLBACK'):reward_was_seen=True
        d=DIAMOND.search(line)
        if d:
            name,extra=d.groups();counts['diamond_'+name.lower()]+=1
            if name == 'WRITE_ATTEMPT' and 'exact=true' in extra:
                counts['diamond_exact_write_attempt'] += 1
                if reward_was_seen:reward_write_sequences += 1
            if name in ('READ','READ_ALL'):
                if 'changed=true' in extra:counts['diamond_override_applied']+=1
                if 'exact=false' in extra:counts['diamond_candidate_unmatched']+=1
                if 'exact=true' in extra:counts['diamond_exact_key_seen']+=1
        if 'MCCBANNER: NATIVE_VIEW_HIDDEN_LOCAL_ONLY' in line: counts['native_banner_hidden']+=1
        if 'MCCBANNER: NATIVE_VIEW_OBSERVED' in line:counts['native_banner_observed']+=1
        if 'MCCVIPLAB: PATCH_MAPPER_PASS' in line:counts['vip_mapper_patch']+=1
        if 'MCCVIPLAB: PATCH_CUSTOMER_INFO_PASS' in line:counts['vip_customer_info_patch']+=1
        if 'MCCVIPFLOW: PURCHASE_CALL' in line:counts['vip_purchase_call']+=1
    for ident,events in by_id.items():
        types={x[0] for x in events}; seq={x[1] for x in events}
        if 'REWARD_CALLBACK' in seq or 'REWARD_INTERSTITIAL_CALLBACK' in seq:
            if 'IMPRESSION' not in seq: notes.append(f'ad_id={ident} reward callback without observed impression (not proof of fraud)')
            if 'LOAD_FAILED' in seq: notes.append(f'ad_id={ident} reward callback after failure (needs validation)')
        if 'banner' in types and ('REWARD_CALLBACK' in seq):notes.append(f'ad_id={ident} banner/reward mismatch')
    if reward_write_sequences:notes.append('Reward callback appeared before numeric diamond write attempt; no server credit verified')
    return {'counts':dict(sorted(counts.items())),
            'callback_followed_by_local_write_attempts':reward_write_sequences,
            'distinct_observed_ad_ids':len(by_id),
            'synthetic_review_flags':notes[:15],
            'test_markers':marks[-30:],
            'limitations':[
              'VIP spoofing is not proof of authorized feature access.',
              'No SharedPreferences diamond-key event means the Java preference override has not been demonstrated.',
              'A callback can be simulated in an offline fixture; this cannot prove the production app grants credit.',
              'Client reward callbacks are not verified ad-network server-side reward transactions.',
              'WRITE_ATTEMPT does not confirm apply(), commit(), Dart balance changes, or server credit.',
              'Banner hide only affects returned native platform views, not Dart layout slots.',
              'Dart-only DNS popup cannot be suppressed by Java hooks; optional UI test presses existing Continue blocking control.'
            ]}

def main():
    ap=argparse.ArgumentParser()
    g=ap.add_mutually_exclusive_group(required=True)
    g.add_argument('--file',type=Path);g.add_argument('--serial')
    args=ap.parse_args()
    if args.file:src=args.file.read_text(errors='replace')
    else:
        c=subprocess.run(['adb','-s',args.serial,'logcat','-d','-v','threadtime'],capture_output=True,text=True,errors='replace')
        if c.returncode:raise SystemExit('ADB logcat failed: '+c.stderr.strip())
        src=c.stdout
    obj=analyze(src)
    print('MCC_P03_RUNTIME_DIAGNOSTICS')
    for key,value in obj['counts'].items():print(f'{key}={value}')
    print('distinct_observed_ad_ids='+str(obj['distinct_observed_ad_ids']))
    print('callback_followed_by_local_write_attempts='+str(obj['callback_followed_by_local_write_attempts']))
    for marker in obj['test_markers']:print('TEST_MARKER='+marker)
    for flag in obj['synthetic_review_flags']:print('REVIEW_FLAG='+flag)
    if not obj['counts'].get('diamond_override_applied'): print('DIAMOND_OVERRIDE_NOT_DEMONSTRATED')
    print('ACTUAL_VIP_ACCESS_NOT_DEMONSTRATED_BY_LOGS')

if __name__=='__main__':main()
