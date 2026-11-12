#!/usr/bin/env python3
"""P07: read-only live AdMob reward amount vs local diamond persistence audit.

Runs the existing P06 hash sampler and streams only whitelisted, redacted
logcat lines. Does NOT read HTTP request bodies, account IDs, tokens, or
simulate ad events / balances. Sync marker hashes are not backend proof.
"""
import argparse
import hashlib
import json
import re
import subprocess
import sys
import threading
from pathlib import Path
from contextlib import redirect_stdout
import mcc_p06_trace as p06

TAGS = ('MCCADID:', 'MCCP06IO:', 'MCCADQA:', 'MCCP07:')
DETAIL = re.compile(r'MCCP07:\s+(SDK_REWARD|BRIDGE_REWARD)\s+ad_id=(\d+|unknown)\s+amount=(\d+|unknown)\s+unit=(diamond|gem|coin|other|unknown)\s+status=(observed|unavailable)\b')
HEALTH = re.compile(r'MCCP07:\s+REWARD_DETAIL_PROBE_INSTALLED_READ_ONLY\s+hooks=(\d+)')


def parse_line(line):
    match = p06.EPOCH.search(line)
    if not match:
        return []
    stamp = float(match.group(1))
    detail = DETAIL.search(line)
    if detail:
        event, ident, amount, unit, status = detail.groups()
        return [{'time': stamp, 'source':'reward_details','event':event,
                 'ad_id': int(ident) if ident != 'unknown' else None,
                 'sdk_amount': int(amount) if amount != 'unknown' else None,
                 'unit':unit,'status':status}]
    health = HEALTH.search(line)
    if health:
        return [{'time': stamp, 'source':'reward_details','event':'PROBE_INSTALLED',
                 'hooks':min(int(health.group(1)), 3)}]
    return p06.parse_logcat(line, 0.0, 10**12)


class LiveSink:
    def __init__(self):
        self.events=[]
        self.lines=0
        self.error=None
        self.done=False
    def consume(self, stream):
        try:
            for raw in stream:
                if not any(tag in raw for tag in TAGS):
                    continue
                self.lines += 1
                # Never retain the raw line: parse and redact immediately.
                for event in parse_line(raw):
                    if len(self.events) >= 2000:
                        self.error='EVENT_LIMIT_REACHED'
                        return
                    self.events.append(event)
        except (ValueError, OSError) as error:
            self.error=type(error).__name__
        finally:
            self.done=True


def unique_events(previous, fresh, start, end):
    output=[]; seen=set()
    for event in sorted([*previous, *fresh],key=lambda x:x['time']):
        if not start-1 <= event['time'] <= end+1:
            continue
        key=(round(event['time'],3),event.get('source'),event.get('event'),
             event.get('ad_id'),event.get('label'),event.get('type'),
             event.get('sdk_amount'),event.get('unit'),event.get('status'))
        if key not in seen:
            seen.add(key)
            output.append(event)
    return output


def cached_number(state):
    if not isinstance(state,dict) or state.get('status')!='present':
        return None
    digest=state.get('sha256')
    count=state.get('bytes')
    if not isinstance(count,int) or count>4 or count<1:
        return None
    # Hash lookup ONLY. No file contents or account fields read.
    for value in range(1000):
        candidate=str(value).encode('ascii')
        if len(candidate)==count and hashlib.sha256(candidate).hexdigest()==digest:
            return value
    return None


def analysis(report):
    timeline=sorted(report.get('events',[]),key=lambda e:e['time'])
    callbacks=[x for x in timeline if x.get('source')=='admob_bridge' and x.get('event') in p06.REWARDS]
    details=[x for x in timeline if x.get('source')=='reward_details']
    changes=[x for x in timeline if x.get('event')=='FILE_CHANGED' and x.get('label')=='DiamondBalance']
    syncs=[x for x in timeline if x.get('event')=='FILE_CHANGED' and x.get('label') in ('LastSyncAzure','LastSyncHasura')]
    correlations=[]
    for callback in callbacks:
        ident=callback.get('ad_id')
        find=lambda event: next((e for e in details if e.get('event')==event and
                                  e.get('ad_id')==ident and abs(e['time']-callback['time'])<=2.0),None)
        native=find('SDK_REWARD');bridge=find('BRIDGE_REWARD')
        change=next((c for c in changes if 0 <= c['time']-callback['time']<=15),None)
        before=cached_number(change['before']) if change else None
        after=cached_number(change['after']) if change else None
        delta=after-before if before is not None and after is not None else None
        amount=native.get('sdk_amount') if native else None
        if amount is None and bridge: amount=bridge.get('sdk_amount')
        state='INSUFFICIENT_EVIDENCE'
        if delta is not None and amount is not None:
            state='MATCHES_SDK_AMOUNT' if delta==amount else 'DIFFERS_FROM_SDK_AMOUNT'
        elif delta is not None:
            state='FILE_DELTA_WITHOUT_REWARD_AMOUNT'
        synced=[]
        if change:
            synced=sorted({x['label'] for x in syncs if 0<=x['time']-change['time']<=15})
        correlations.append({'ad_id':ident,
            'native_reward_amount':native.get('sdk_amount') if native else None,
            'bridge_reward_amount':bridge.get('sdk_amount') if bridge else None,
            'native_unit':native.get('unit') if native else None,
            'bridge_unit':bridge.get('unit') if bridge else None,
            'local_before':before,'local_after':after,'local_delta':delta,
            'seconds_callback_to_file':round(change['time']-callback['time'],3) if change else None,
            'sync_markers_after_local_change':synced,'classification':state})
    return {'observed_reward_callbacks':len(callbacks),
            'native_amount_events':sum(d.get('event')=='SDK_REWARD' for d in details),
            'bridge_amount_events':sum(d.get('event')=='BRIDGE_REWARD' for d in details),
            'probe_installed_seen':any(d.get('event')=='PROBE_INSTALLED' for d in details),
            'correlations':correlations,
            'server_authorization':'NOT_OBSERVED',
            'limitations':[
              'AdMob reward item amount and application diamond credit may use different units.',
              'A local file change following a callback does not establish causality or backend authorization.',
              'Azure/Hasura sync marker hash changes do not prove server acceptance.',
              'The Flutter/Dart AOT credit function is not directly hooked.',
              'Only the first observed file change after a callback is correlated; other updates can overlap.',
              'Reward metadata hooks depend on matching Android plugin signatures; unavailable values are not inferred.',
              'Balance values are only recognized from bounded hashes of ASCII integers 0..999.'
            ]}


def show(report):
    a=analysis(report)
    print('MCC_P07_REWARD_AUTHORIZATION_BOUNDARY_READ_ONLY')
    print('reward_callbacks='+str(a['observed_reward_callbacks']))
    print('sdk_amount_events='+str(a['native_amount_events']))
    print('bridge_amount_events='+str(a['bridge_amount_events']))
    print('live_events='+str(report.get('p07_live_log',{}).get('parsed_live_events','unknown')))
    for row in a['correlations']:
        print('P07_CORRELATION ad_id=%s sdk_amount=%s bridge_amount=%s local_delta=%s lag_s=%s result=%s sync_markers=%s' %
              (row['ad_id'],row['native_reward_amount'],row['bridge_reward_amount'],
               row['local_delta'],row['seconds_callback_to_file'],row['classification'],
               ','.join(row['sync_markers_after_local_change'])))
    if not a['observed_reward_callbacks']:print('P07_REWARD_CALLBACK_NOT_OBSERVED')
    if a['observed_reward_callbacks'] and not a['native_amount_events']:
        print('P07_AMOUNT_NOT_OBSERVED: inspect module version/scope and reward-item signature')
    print('P07_BACKEND_AUTHORIZATION_NOT_ESTABLISHED')


class Progress:
    def __init__(self,output):
        self.output=output;self.buffer=''
    def write(self,data):
        self.buffer += data
        while '\n' in self.buffer:
            line,self.buffer=self.buffer.split('\n',1)
            if line.startswith(('MCC_P06_START','MCC_P06_BASELINE','MCC_P06_FILE_CHANGE','MCC_P06_STOP','MCC_P06_WARNING')):
                self.output.write(line+'\n');self.output.flush()
    def flush(self):
        self.output.flush()


def capture(serial,seconds,interval,dest):
    if not 8<=seconds<=360 or not 0.25<=interval<=3:
        raise ValueError('seconds=8..360 interval=0.25..3')
    cmd=['adb','-s',serial,'logcat','-v','epoch','-T','1','-s','VectorLegacyBridge:I','flutter:I','*:S']
    proc=subprocess.Popen(cmd,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,
                          text=True,bufsize=1,errors='replace')
    sink=LiveSink()
    reader=threading.Thread(target=sink.consume,args=(proc.stdout,),daemon=True)
    reader.start()
    try:
        if proc.poll() is not None:
            raise RuntimeError('Live logcat exited immediately')
        print('MCC_P07_LIVE_STREAM_STARTED redacted=true')
        with redirect_stdout(Progress(sys.stdout)):
            p06.capture(serial,seconds,interval,dest)
    finally:
        if proc.poll() is None:proc.terminate()
        try:proc.wait(timeout=4)
        except subprocess.TimeoutExpired:
            proc.kill();proc.wait(timeout=4)
        reader.join(timeout=4)
    if not sink.done:
        raise RuntimeError('Live logcat reader did not terminate; original P06 report retained')
    report=json.loads(dest.expanduser().read_text(encoding='utf-8'))
    if report.get('audit')!='MCC_P06_REWARD_DIAMOND_READ_ONLY':
        raise RuntimeError('Unexpected P06 report format')
    original=report['events']
    report['events']=unique_events(original,sink.events,report['started_epoch'],report['ended_epoch'])
    report['analysis']=p06.analyze(report)
    report['p07_analysis']=analysis(report)
    report['p07_live_log']={'mode':'streaming_sanitized','matched_tagged_lines':sink.lines,
          'parsed_live_events':len(sink.events), 'new_events_after_deduplication':len(report['events'])-len(original),
          'stream_error':sink.error,'raw_log_saved':False}
    p06.save_private(dest,report)
    show(report)
    print('MCC_P07_REPORT='+str(dest.expanduser()))
    if sink.error:print('MCC_P07_STREAM_WARNING='+sink.error)


def main(argv=None):
    ap=argparse.ArgumentParser(description='MCC P07 - read-only reward amount/bridge/sync correlation')
    sub=ap.add_subparsers(dest='mode',required=True)
    cp=sub.add_parser('capture')
    cp.add_argument('--serial',default='localhost:5555')
    cp.add_argument('--seconds',type=int,default=300)
    cp.add_argument('--interval',type=float,default=0.5)
    cp.add_argument('--out',type=Path,default=Path.home()/'mcc-p07-trace.json')
    rp=sub.add_parser('report')
    rp.add_argument('--file',required=True,type=Path)
    args=ap.parse_args(argv)
    try:
        if args.mode=='capture':capture(args.serial,args.seconds,args.interval,args.out)
        else:
            report=json.loads(args.file.expanduser().read_text(encoding='utf-8'))
            if report.get('audit')!='MCC_P06_REWARD_DIAMOND_READ_ONLY':
                raise RuntimeError('Unexpected report schema')
            show(report)
    except (OSError,ValueError,RuntimeError,subprocess.TimeoutExpired) as error:
        print('MCC_P07_REFUSED:',str(error),file=sys.stderr)
        return 1
    return 0

if __name__=='__main__':raise SystemExit(main())
