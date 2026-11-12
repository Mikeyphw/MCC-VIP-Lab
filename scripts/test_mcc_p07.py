#!/usr/bin/env python3
"""P07 offline regression: reward amount vs stored balance, no fake device ads."""
import hashlib
import io
import unittest
from mcc_p07_capture import parse_line, analysis, cached_number, unique_events, LiveSink


def state(value):
    value=str(value)
    return {'status':'present','bytes':len(value),'sha256':hashlib.sha256(value.encode()).hexdigest()}

class P07AnalysisTests(unittest.TestCase):
    def test_sdk_amount_one_and_delta_four_reports_difference(self):
        t=1000.0
        events=[{'time':t, 'source':'admob_bridge','event':'REWARD_CALLBACK','ad_id':1,'type':'rewarded'},
                {'time':t+.02,'source':'reward_details','event':'SDK_REWARD','ad_id':1,'sdk_amount':1,'unit':'other'},
                {'time':t+.14,'source':'reward_details','event':'BRIDGE_REWARD','ad_id':1,'sdk_amount':1,'unit':'other'},
                {'time':t+.55,'source':'root_hash','event':'FILE_CHANGED','label':'DiamondBalance',
                 'before':state(0),'after':state(4)},
                {'time':t+3.0,'source':'root_hash','event':'FILE_CHANGED','label':'LastSyncAzure'}]
        r=analysis({'events':events})
        self.assertEqual(1,r['observed_reward_callbacks'])
        self.assertEqual('DIFFERS_FROM_SDK_AMOUNT',r['correlations'][0]['classification'])
        self.assertEqual(4,r['correlations'][0]['local_delta'])
        self.assertEqual(1,r['correlations'][0]['native_reward_amount'])
        self.assertEqual('NOT_OBSERVED',r['server_authorization'])

    def test_sdk_amount_four_matches_balance(self):
        events=[{'time':1,'source':'admob_bridge','event':'REWARD_CALLBACK','ad_id':1},
                {'time':1.01,'source':'reward_details','event':'SDK_REWARD','ad_id':1,'sdk_amount':4},
                {'time':1.1,'source':'root_hash','event':'FILE_CHANGED','label':'DiamondBalance',
                 'before':state(0),'after':state(4)}]
        self.assertEqual('MATCHES_SDK_AMOUNT',analysis({'events':events})['correlations'][0]['classification'])

    def test_safe_unknown_amount_and_unattributable_write(self):
        events=[{'time':2,'source':'admob_bridge','event':'REWARD_CALLBACK','ad_id':1},
                {'time':2.6,'source':'root_hash','event':'FILE_CHANGED','label':'DiamondBalance',
                 'before':state(3),'after':state(4)}]
        self.assertEqual('FILE_DELTA_WITHOUT_REWARD_AMOUNT',analysis({'events':events})['correlations'][0]['classification'])
        self.assertIsNone(cached_number({'status':'present','bytes':30,'sha256':'x'*64}))

    def test_parser_redacts_untrusted_log_contents(self):
        line='1700000001.125 1 1 I VectorLegacyBridge: MCCP07: SDK_REWARD ad_id=12 amount=1 unit=other status=observed'
        events=parse_line(line)
        self.assertEqual(1,events[0]['sdk_amount'])
        self.assertEqual(12,events[0]['ad_id'])
        self.assertEqual('other',events[0]['unit'])
        self.assertEqual([],parse_line('1700000001.125 I VectorLegacyBridge: MCCP07: SDK_REWARD ad_id=12 amount=1 unit=Bearer_SECRET status=observed'))
        self.assertEqual([],parse_line('random account_token=abcdef'))

    def test_stream_live_dedup(self):
        sink=LiveSink()
        s='1700000001.125 1 1 I VectorLegacyBridge: MCCP07: BRIDGE_REWARD ad_id=12 amount=4 unit=diamond status=observed\n'
        sink.consume(io.StringIO('secret unrelated\n'+s))
        self.assertTrue(sink.done)
        self.assertEqual(1,sink.lines)
        self.assertEqual(1,len(unique_events(sink.events,sink.events,1700000000,1700000002)))
        self.assertEqual([],unique_events(sink.events,[],1700000009,1700000012))

    def test_boundary_no_phantom_server_validation(self):
        output=analysis({'events':[]})
        self.assertEqual('NOT_OBSERVED',output['server_authorization'])
        self.assertEqual(0,output['observed_reward_callbacks'])
        self.assertFalse(output['probe_installed_seen'])

if __name__=='__main__':unittest.main()
