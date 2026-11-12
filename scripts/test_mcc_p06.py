#!/usr/bin/env python3
"""Fully offline P06 contract tests, including a mocked root ADB snapshot."""
import hashlib
import re
import subprocess
import unittest
from unittest.mock import patch
from mcc_p06_trace import (LABELS, Refusal, analyze, changes, parse_probe,
                           parse_logcat, root_script, sample)

def sha(value):return hashlib.sha256(value.encode()).hexdigest()

def state(balance, azure='100', hasura='100'):
    return {x: {'status':'present','bytes':len(v),'sha256':sha(v)}
            for x,v in zip(LABELS,(balance,azure,hasura))}

class RootProbeTests(unittest.TestCase):
    def test_only_three_label_suffixes_and_no_write_shell_commands(self):
        s=root_script()
        self.assertIn('sha256sum',s)
        self.assertEqual(1, s.count('DiamondBalance'))
        self.assertNotIn('accessToken',s)
        self.assertNotIn('rm -',s)
        self.assertNotIn('echo 9999',s)

    def test_root_probe_accepts_known_hashes_without_raw_file_contents(self):
        inp='\n'.join(f'{name}|{n}|{digest}' for name,n,digest in
                      (('DiamondBalance','1',sha('0')),
                       ('LastSyncAzure','26',sha('2026-11-11T20:01:00+00:00')),
                       ('LastSyncHasura','MISSING','MISSING')))
        parsed=parse_probe(inp)
        self.assertEqual(sha('0'),parsed['DiamondBalance']['sha256'])
        self.assertEqual('missing',parsed['LastSyncHasura']['status'])

    def test_absent_diamond_file_refuses(self):
        inp='\n'.join(f'{name}|MISSING|MISSING' for name in LABELS)
        with self.assertRaisesRegex(Refusal,'DiamondBalance file not found'):
            parse_probe(inp)

    def test_ambiguous_target_fails_closed(self):
        with self.assertRaisesRegex(Refusal,'ambiguous'):
            parse_probe('DiamondBalance|MULTIPLE|MULTIPLE\nLastSyncAzure|MISSING|MISSING\nLastSyncHasura|MISSING|MISSING')

    def test_invalid_hash_or_missing_line_fails_closed(self):
        with self.assertRaises(Refusal):parse_probe('DiamondBalance|1|0123')
        with self.assertRaises(Refusal):parse_probe(f'DiamondBalance|1|{sha("0")}')

    def test_mock_adb_probe_never_requests_raw_contents(self):
        payload='\n'.join(f'{name}|{len(value)}|{sha(value)}' for name,value in
                         zip(LABELS,('1','123456789','987654321')))
        observed=[]
        def fake(cmd,**kwargs):
            observed.append(cmd)
            return subprocess.CompletedProcess(cmd,0,stdout=payload,stderr='')
        with patch('mcc_p06_trace.subprocess.run',side_effect=fake):
            t, lat, data=sample('localhost:5555')
        self.assertEqual(sha('1'),data['DiamondBalance']['sha256'])
        self.assertEqual('adb',observed[0][0])
        self.assertIn('su -c ', observed[0][-1])
        self.assertNotIn('cat /data/',observed[0][-1])

class TimelineTests(unittest.TestCase):
    def test_one_real_reward_fixture_generates_temporal_correlation_not_authorization(self):
        before=state('0','100','100')
        after=state('1','101','101')
        timeline=[
            {'time':1000.0,'source':'admob_bridge','event':'TRACK','ad_id':5,'type':'rewarded'},
            {'time':1001.0,'source':'admob_bridge','event':'IMPRESSION','ad_id':5,'type':'rewarded'},
            {'time':1002.0,'source':'admob_bridge','event':'REWARD_CALLBACK','ad_id':5,'type':'rewarded'},
            *changes(before,after,1003.0),
        ]
        output=analyze({'events':timeline})
        self.assertEqual(1,output['diamond_hash_changes'])
        self.assertEqual(2,output['sync_marker_hash_changes'])
        self.assertEqual(1,output['reward_callbacks'])
        self.assertEqual(5,output['diamond_change_after_observed_reward'][0]['ad_id'])
        self.assertEqual(1.0,output['diamond_change_after_observed_reward'][0]['delta_seconds'])
        self.assertTrue(any('does not prove' in x for x in output['limitations']))

    def test_banner_failure_never_inferred_reward(self):
        events=parse_logcat('''1700000000.00 42 42 I VectorLegacyBridge: MCCADID: TRACK ad_id=33 type=banner
1700000001.00 42 42 I VectorLegacyBridge: MCCADID: LOAD_FAILED ad_id=33 type=banner''',1699999999,1700000004)
        result=analyze({'events':events})
        self.assertEqual(0,result['reward_callbacks'])

    def test_stale_outside_window_not_reported(self):
        log='''1700000000.000 10 10 I VectorLegacyBridge: MCCADID: REWARD_CALLBACK ad_id=55 type=rewarded
1700000100.000 11 11 I VectorLegacyBridge: MCCADID: REWARD_CALLBACK ad_id=23 type=rewarded'''
        events=parse_logcat(log,1700000099,1700000101)
        self.assertEqual([23],[e['ad_id'] for e in events])

    def test_file_change_without_callback_remains_unattributed(self):
        report=analyze({'events':changes(state('0'),state('1'),42.)})
        self.assertFalse(report['diamond_change_after_observed_reward'][0]['prior_reward_callback'])

    def test_callback_after_file_change_is_not_prior_reward(self):
        ev=changes(state('0'),state('1'),10)
        ev.append({'time':11,'source':'admob_bridge','event':'REWARD_CALLBACK','ad_id':2,'type':'rewarded'})
        out=analyze({'events':ev})
        self.assertFalse(out['diamond_change_after_observed_reward'][0]['prior_reward_callback'])

    def test_java_io_logs_no_raw_paths_or_account_ids(self):
        logs='''1700000001.000 1 1 I VectorLegacyBridge: MCCP06IO: JAVA_OPEN_WRITE label=DiamondBalance status=attempt_observed
1700000002.000 1 1 I VectorLegacyBridge: MCCP06IO: JAVA_OPEN_READ label=LastSyncAzure status=attempt_observed'''
        found=parse_logcat(logs,1700000000,1700000003)
        self.assertEqual(2,len(found))
        self.assertTrue(all('label' in e for e in found))

    def test_method_channel_hint_not_counted_as_safely_verified_reward(self):
        log='1700000002.000 1 1 I VectorLegacyBridge: MCCADQA: BRIDGE_GROUP rewarded_complete prior_duplicates_collapsed=0'
        found=parse_logcat(log,1700000001,1700000003)
        self.assertEqual(1,len(found))
        self.assertEqual(0,analyze({'events':found})['reward_callbacks'])

if __name__=='__main__': unittest.main()
