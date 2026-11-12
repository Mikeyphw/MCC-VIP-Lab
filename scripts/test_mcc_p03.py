#!/usr/bin/env python3
import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE = Path(__file__).with_name('mcc_p03_report.py')
spec = importlib.util.spec_from_file_location('mcc_p03_report', MODULE)
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


class P03Tests(unittest.TestCase):
    def test_correlates_per_id_and_flags_reward_without_impression(self):
        log = '''MCCADID: TRACK ad_id=22 type=banner
MCCADID: LOAD_FAILED ad_id=22 type=banner
MCCADID: TRACK ad_id=9 type=rewarded
MCCADID: LOAD_FAILED ad_id=9 type=rewarded
MCCADID: REWARD_CALLBACK ad_id=9 type=rewarded
MCCVIPLAB: PATCH_MAPPER_PASS entitlement=premium_noads
MCCDIAMOND: READ candidate_id=abcdef1234 exact=true observed=2 mode=high returned=999999 changed=true
MCCP03CASE: VIP_FEATURE_ATTEMPT'''
        a=report.analyze(log)
        self.assertEqual(2,a['distinct_observed_ad_ids'])
        self.assertEqual(1,a['counts']['diamond_override_applied'])
        self.assertEqual(1,a['counts']['vip_mapper_patch'])
        self.assertEqual(1,a['counts']['ad_reward_callback'])
        self.assertTrue(any('ad_id=9' in x for x in a['synthetic_review_flags']))
    def test_banner_failure_is_not_reward_credit(self):
        log='''MCCADID: TRACK ad_id=7 type=banner
MCCADID: LOAD_FAILED ad_id=7 type=banner
MCCADQA: BRIDGE_GROUP onAdFailedToLoad prior_duplicates_collapsed=5'''
        a=report.analyze(log)
        self.assertEqual(1,a['counts']['ad_load_failed'])
        self.assertEqual(0,a['counts'].get('ad_reward_callback',0))
        self.assertEqual([],a['synthetic_review_flags'])
    def test_absent_diamond_data_is_inconclusive(self):
        a=report.analyze('MCCVIPSTATE: MAPPER_AFTER listed_active=true')
        self.assertFalse(a['counts'].get('diamond_override_applied',0))
        self.assertEqual(0,a['distinct_observed_ad_ids'])
    def test_real_impression_avoids_false_positive(self):
        a=report.analyze('''MCCADID: TRACK ad_id=5 type=rewarded
MCCADID: IMPRESSION ad_id=5 type=rewarded
MCCADID: REWARD_CALLBACK ad_id=5 type=rewarded''')
        self.assertEqual([],a['synthetic_review_flags'])
    def test_different_ad_ids_not_conflated(self):
        a=report.analyze('''MCCADID: LOAD_FAILED ad_id=1 type=banner
MCCADID: REWARD_CALLBACK ad_id=2 type=rewarded''')
        self.assertFalse(any('ad_id=1' in x for x in a['synthetic_review_flags']))
        self.assertTrue(any('ad_id=2' in x for x in a['synthetic_review_flags']))

if __name__=='__main__':unittest.main()
