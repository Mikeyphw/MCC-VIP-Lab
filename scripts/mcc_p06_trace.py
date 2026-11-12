#!/usr/bin/env python3
"""P06: bounded, read-only per-file hash timeline plus sanitized LSPosed ad callbacks.

No in-app balance edits, no made-up ad events, no network calls. The root sampler
hashes three explicitly named files, never opens credential or database files.
Sampling is coarse: a write-and-revert between samples may be missed.
"""
import argparse
import json
import os
import re
import shlex
import subprocess
import tempfile
import sys
import time
from pathlib import Path

TARGET = 'br.art.code.meucronogramacapilar'
LABELS = ('DiamondBalance', 'LastSyncAzure', 'LastSyncHasura')
SHA256 = re.compile(r'^[0-9a-f]{64}$')
AD = re.compile(r'MCCADID:\s+(?P<event>[A-Z_]+) ad_id=(?P<id>\d+) type=(?P<kind>[a-z]+)')
JAVA_IO = re.compile(r'MCCP06IO:\s+(?P<event>JAVA_[A-Z_]+) label=(?P<label>DiamondBalance|LastSyncAzure|LastSyncHasura)\b')
BRIDGE = re.compile(r'MCCADQA:\s+BRIDGE_GROUP\s+(?P<name>[A-Za-z_]+)')
EPOCH = re.compile(r'^\s*(\d{10}\.\d+)\s')
REWARDS = {'REWARD_CALLBACK', 'REWARD_INTERSTITIAL_CALLBACK'}

class Refusal(RuntimeError):
    pass


def root_script():
    # Patterns use the exact target app's sandbox, with only label suffixes.
    # The shell prints no account-scoped basenames or file contents.
    return '''set -eu
base=/data/user/0/br.art.code.meucronogramacapilar/databases
[ -d "$base" ] || exit 32
for label in DiamondBalance LastSyncAzure LastSyncHasura; do
  count=0
  found=
  for f in "$base"/*-"$label"; do
    [ -f "$f" ] || continue
    count=$((count + 1))
    found=$f
  done
  if [ "$count" -eq 0 ]; then
    printf '%s|MISSING|MISSING\\n' "$label"
  elif [ "$count" -gt 1 ]; then
    printf '%s|MULTIPLE|MULTIPLE\\n' "$label"
  else
    bytes=$(wc -c < "$found")
    if [ "$bytes" -gt 1048576 ]; then
      printf '%s|TOO_LARGE|TOO_LARGE\\n' "$label"
    else
      digest=$(sha256sum "$found")
      digest=${digest%% *}
      printf '%s|%s|%s\\n' "$label" "$bytes" "$digest"
    fi
  fi
done'''


def adb(serial, argv, timeout=20):
    result = subprocess.run(['adb', '-s', serial, *argv], text=True, capture_output=True,
                            timeout=timeout, errors='replace')
    if result.returncode:
        raise Refusal('ADB command failed (rc=%d); check root and target installation: %s' %
                      (result.returncode, (result.stderr or result.stdout).strip()[:220]))
    return result.stdout


def parse_probe(text):
    parsed = {}
    for line in text.splitlines():
        fields = line.strip().split('|')
        if len(fields) != 3 or fields[0] not in LABELS:
            # Ignore incidental shell banners, but reject missing result rows below.
            continue
        label, size, digest = fields
        if label in parsed:
            raise Refusal('Duplicate result for ' + label)
        if size == 'MISSING' and digest == 'MISSING':
            parsed[label] = {'status': 'missing'}
        elif (size, digest) in (('MULTIPLE', 'MULTIPLE'), ('TOO_LARGE','TOO_LARGE')):
            raise Refusal(label + ' is ambiguous or oversized; refusing the sample')
        elif size.isdecimal() and 0 <= int(size) <= 1048576 and SHA256.fullmatch(digest):
            parsed[label] = {'status': 'present', 'bytes': int(size), 'sha256': digest}
        else:
            raise Refusal('Malformed probe result for ' + label)
    if set(parsed) != set(LABELS):
        raise Refusal('Root probe incomplete: expected 3 scoped labels; received ' + str(sorted(parsed)))
    if parsed['DiamondBalance']['status'] != 'present':
        raise Refusal('DiamondBalance file not found; cannot establish balance timeline')
    return parsed


def sample(serial):
    t0 = time.time()
    # Pass one remote-shell command string. shlex.quote prevents early command parsing.
    response = adb(serial, ['shell', 'su -c ' + shlex.quote(root_script())], timeout=22)
    t1 = time.time()
    return (t0 + t1) / 2.0, round(t1 - t0, 3), parse_probe(response)


def parse_logcat(log, start, end):
    events = []
    for line in log.splitlines():
        m = EPOCH.search(line)
        if not m:
            continue
        t = float(m.group(1))
        if not start - 1.0 <= t <= end + 1.0:
            continue
        match = AD.search(line)
        if match:
            ev = match.groupdict()
            # No SDK payloads, user IDs, real ad unit identifiers, or raw messages.
            events.append({'time': t, 'source': 'admob_bridge', 'event': ev['event'],
                           'ad_id': int(ev['id']), 'type': ev['kind']})
            continue
        match = JAVA_IO.search(line)
        if match:
            ev = match.groupdict()
            events.append({'time': t, 'source': 'java_file_api', 'event': ev['event'],
                           'label': ev['label']})
            continue
        match = BRIDGE.search(line)
        if match:
            name = match.group('name')
            if name.lower().startswith(('rewarded', 'onrewarded', 'onuserearned')):
                events.append({'time': t, 'source': 'method_channel', 'event': name})
    return events[:1200]


def changes(previous, current, t):
    events = []
    for label in LABELS:
        if previous.get(label) != current.get(label):
            events.append({'time': t, 'source': 'root_hash', 'event': 'FILE_CHANGED',
                           'label': label, 'before': previous.get(label),
                           'after': current.get(label)})
    return events


def analyze(report):
    events = sorted(report.get('events', []), key=lambda e: e['time'])
    reward_events = [e for e in events if e.get('source') == 'admob_bridge' and e.get('event') in REWARDS]
    diamond_changes = [e for e in events if e.get('event') == 'FILE_CHANGED' and e.get('label') == 'DiamondBalance']
    sync_events = [e for e in events if e.get('event') == 'FILE_CHANGED' and e.get('label') in ('LastSyncAzure', 'LastSyncHasura')]
    near = []
    for change in diamond_changes:
        prior = [ev for ev in reward_events if 0 <= change['time'] - ev['time'] <= 30]
        candidate = prior[-1] if prior else None
        near.append({'file_change_at': change['time'],
                     'prior_reward_callback': candidate is not None,
                     'delta_seconds': round(change['time'] - candidate['time'], 3) if candidate else None,
                     'ad_id': candidate['ad_id'] if candidate else None})
    labels_at = {label: [e['time'] for e in sync_events if e['label'] == label] for label in ('LastSyncAzure', 'LastSyncHasura')}
    return {
        'reward_callbacks': len(reward_events),
        'diamond_hash_changes': len(diamond_changes),
        'sync_marker_hash_changes': len(sync_events),
        'java_file_api_events': sum(e.get('source') == 'java_file_api' for e in events),
        'diamond_change_after_observed_reward': near,
        'sync_marker_change_times': labels_at,
        'limitations': [
            'Correlation within 30 seconds does not prove that the ad network verified or authorized a reward.',
            'Root polling observes persistent file hashes, not all Dart memory writes or file reads.',
            'A file may change and revert between samples without being noticed.',
            'Java file constructors may not run for Dart-native file I/O.',
            'Absent AdMob events may indicate a different mediation SDK or lost/disabled LSPosed logging.',
            'Sync marker hashes show local changes, not confirmed backend acceptance.',
            'Capture timestamps are approximate and include ADB command latency.'
        ]
    }


def show(report):
    summary = analyze(report)
    print('MCC_P06_REWARD_STORAGE_TIMELINE')
    print('samples=' + str(report.get('samples', 0)))
    for key in ('reward_callbacks','diamond_hash_changes','sync_marker_hash_changes','java_file_api_events'):
        print(key + '=' + str(summary[key]))
    start = report.get('started_epoch', 0)
    events = sorted(report.get('events', []), key=lambda e: e['time'])
    print('TIMELINE:')
    for e in events[:80]:
        fields = []
        for k in ('label','ad_id','type'):
            if k in e: fields.append(k + '=' + str(e[k]))
        print('  +%07.2fs %-20s %-31s %s' %
              (e['time']-start, e['source'], e['event'], ' '.join(fields)))
    for found in summary['diamond_change_after_observed_reward']:
        print('DIAMOND_CHANGE_CORRELATION prior_reward_callback=%s delta_seconds=%s' %
              (str(found['prior_reward_callback']).lower(), found['delta_seconds']))
    if not summary['diamond_hash_changes']:
        print('DIAMOND_CHANGE_NOT_OBSERVED: this capture does not prove that the balance remained unchanged')
    if not summary['reward_callbacks']:
        print('REWARD_CALLBACK_NOT_OBSERVED: different SDK, log loss, or no callback remain possible')
    print('MCC_P06_RESULT=LOCAL_TIMELINE_ONLY_NO_AUTHORIZATION_CLAIM')


def save_private(path, obj):
    path = path.expanduser()
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tempname = tempfile.mkstemp(prefix=path.name + '.', suffix='.tmp', dir=path.parent)
    tmp = Path(tempname)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as stream:
            json.dump(obj, stream, indent=2, sort_keys=True)
            stream.write('\n')
        os.replace(tmp, path)
        os.chmod(path, 0o600)
    except BaseException:
        try: tmp.unlink()
        except FileNotFoundError: pass
        raise


def capture(serial, seconds, interval, dest):
    if not 8 <= seconds <= 360:
        raise Refusal('--seconds must be 8..360')
    if not 0.25 <= interval <= 3.0:
        raise Refusal('--interval must be 0.25..3.0')
    start = time.time()
    print('MCC_P06_START read_only=true duration=%ds interval=%.2fs' % (seconds, interval), flush=True)
    sampled_at, latency, state = sample(serial)
    initial_state = dict(state)
    print('MCC_P06_BASELINE diamond_status=' + state['DiamondBalance']['status'], flush=True)
    events = []
    errors = []
    samples = 1
    deadline = time.monotonic() + seconds
    next_tick = time.monotonic() + interval
    consecutive_errors = 0
    while time.monotonic() < deadline:
        remain = next_tick - time.monotonic()
        if remain > 0: time.sleep(min(remain, max(0, deadline-time.monotonic())))
        if time.monotonic() >= deadline: break
        next_tick += interval
        try:
            when, elapsed, updated = sample(serial)
            consecutive_errors = 0
            samples += 1
            delta = changes(state, updated, when)
            if delta:
                for ev in delta:
                    print('MCC_P06_FILE_CHANGE +' + ('%.2f' % (when-start)) + 's ' + ev['label'], flush=True)
                events.extend(delta)
            state = updated
        except (Refusal, subprocess.TimeoutExpired) as exc:
            errors.append({'time': time.time(), 'error': str(exc)[:120]})
            consecutive_errors += 1
            if consecutive_errors >= 3:
                print('MCC_P06_STOP three consecutive sampling errors', flush=True)
                break
    end = time.time()
    try:
        raw = adb(serial, ['logcat','-d','-v','epoch','-s','VectorLegacyBridge:I','flutter:I','*:S'], timeout=25)
        events.extend(parse_logcat(raw, start, end))
    except (Refusal, subprocess.TimeoutExpired) as exc:
        errors.append({'time': time.time(), 'error': 'Logcat unavailable: '+str(exc)[:120]})
    report = {'schema': 1, 'audit': 'MCC_P06_REWARD_DIAMOND_READ_ONLY',
              'target': TARGET, 'started_epoch': start, 'ended_epoch': end,
              'sampling_interval_requested': interval, 'samples': samples,
              'initial': initial_state, 'final': state,
              'events': sorted(events, key=lambda e:e['time']), 'errors': errors,
              'analysis': None}
    report['analysis'] = analyze(report)
    save_private(dest, report)
    show(report)
    print('MCC_P06_REPORT=' + str(dest.expanduser()))
    if errors: print('MCC_P06_WARNING=Sampling/logcat errors occurred; see report')


def main(argv=None):
    ap=argparse.ArgumentParser(description='MCC P06 read-only rewarded-ad/storage timeline')
    sub=ap.add_subparsers(dest='mode', required=True)
    cap=sub.add_parser('capture', help='Watch three file hashes and reward callbacks in a limited window')
    cap.add_argument('--serial', default='localhost:5555')
    cap.add_argument('--seconds', type=int, default=120)
    cap.add_argument('--interval', type=float, default=0.5)
    cap.add_argument('--out', type=Path, default=Path.home()/'mcc-p06-trace.json')
    rep=sub.add_parser('report', help='Summarize an earlier P06 JSON report offline')
    rep.add_argument('--file', required=True, type=Path)
    args=ap.parse_args(argv)
    try:
        if args.mode=='capture':capture(args.serial,args.seconds,args.interval,args.out)
        else:
            report=json.loads(args.file.expanduser().read_text())
            if report.get('audit') != 'MCC_P06_REWARD_DIAMOND_READ_ONLY': raise Refusal('Unexpected report schema')
            show(report)
    except (Refusal, OSError, subprocess.TimeoutExpired, ValueError) as exc:
        print('MCC_P06_REFUSED:',str(exc),file=sys.stderr)
        return 1
    return 0

if __name__=='__main__':raise SystemExit(main())
