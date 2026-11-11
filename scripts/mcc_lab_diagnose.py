#!/usr/bin/env python3
"""Offline parser for MCC VIP Lab logs; never sends telemetry or changes device state."""
import argparse
import json
import re
import subprocess
from pathlib import Path

TIME = re.compile(r'(?<!\d)(\d\d:\d\d:\d\d\.\d{3})(?!\d)')
KEY_TAGS = ('MCCVIPLAB:', 'MCCVIPSTATE:', 'MCCADQA:', 'MCCLOCALQA:')


def analyze(log):
    counts = {
        'vip_mapper_patches': 0, 'vip_customer_info_patches': 0,
        'ad_failure_groups': 0, 'ad_reward_bridge_groups': 0,
        'ad_impression_bridge_groups': 0, 'local_state_events': 0,
        'flutter_ad_error_lines': 0, 'flutter_balance_hint_lines': 0,
    }
    raw_failure_lines = 0
    vip = []
    state = []
    recent_failed = False
    synthetic_anomaly = False
    for line in log.splitlines():
        if 'MCCVIPLAB: PATCH_MAPPER_PASS' in line: counts['vip_mapper_patches'] += 1
        if 'MCCVIPLAB: PATCH_CUSTOMER_INFO_PASS' in line: counts['vip_customer_info_patches'] += 1
        if 'MCCVIPSTATE:' in line:
            event = line.split('MCCVIPSTATE:', 1)[1].strip()
            if len(vip) < 30: vip.append({'time': (TIME.search(line).group(1) if TIME.search(line) else '?'), 'observation': event})
        if 'MCCLOCALQA: PREF_' in line:
            counts['local_state_events'] += 1
            if len(state) < 30: state.append(line.split('MCCLOCALQA:', 1)[1].strip())
        if 'MCCADQA: BRIDGE_GROUP' in line:
            name = line.split('BRIDGE_GROUP', 1)[1].strip().split(' ', 1)[0].lower()
            if 'failed' in name:
                counts['ad_failure_groups'] += 1
                recent_failed = True
            if 'reward' in name or 'earned' in name:
                counts['ad_reward_bridge_groups'] += 1
                if recent_failed: synthetic_anomaly = True
            if 'impression' in name:
                counts['ad_impression_bridge_groups'] += 1
                recent_failed = False
        if 'MCCADQA: BRIDGE_EVENT onAdFailedToLoad' in line:
            raw_failure_lines += 1
        if 'flutter' in line and ('AdMob' in line or 'LoadAdError' in line):
            counts['flutter_ad_error_lines'] += 1
        if 'flutter' in line and ('diamondBalance' in line or 'incrementDiamondBalance' in line):
            counts['flutter_balance_hint_lines'] += 1
    # This flag is a hint only; a failed banner may coexist with an unrelated genuine rewarded ad.
    return {'counts': counts, 'legacy_ad_failure_entries': raw_failure_lines,
            'vip_observations': vip, 'local_state_observations': state,
            'heuristic_reward_after_prior_failure': synthetic_anomaly,
            'limitations': ['Bridge groups are not distinct ad requests.',
                            'Android SharedPreferences does not expose Flutter-only memory or server records.',
                            'A synthetic or observed callback does not establish verified diamond credit.',
                            'No conclusion about actual backend authorization from client logs alone.']}


def main():
    p = argparse.ArgumentParser(description='Inspect ad/VIP/diamond diagnostic log events')
    mode = p.add_mutually_exclusive_group(required=True)
    mode.add_argument('--file', type=Path)
    mode.add_argument('--serial', help='Read current adb logcat -d from a connected device')
    p.add_argument('--json-out', type=Path)
    args = p.parse_args()
    if args.file:
        log = args.file.read_text(encoding='utf-8', errors='replace')
    else:
        cp = subprocess.run(['adb', '-s', args.serial, 'logcat', '-d', '-v', 'threadtime'],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, errors='replace')
        if cp.returncode: raise SystemExit('ADB diagnostic failed: ' + cp.stderr.strip())
        log = cp.stdout
    result = analyze(log)
    print('MCC_P02_RUNTIME_DIAGNOSTICS')
    for k,v in result['counts'].items(): print(f'{k}={v}')
    print('heuristic_reward_after_prior_failure=' + str(result['heuristic_reward_after_prior_failure']).lower())
    print('VIP observations (sanitized):')
    for row in result['vip_observations'][-10:]: print(' ', row['time'], row['observation'])
    print('Local preference observations (sanitized):')
    for row in result['local_state_observations'][-10:]: print(' ', row)
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n', encoding='utf-8')
        print('WROTE', args.json_out)

if __name__ == '__main__': main()
