#!/usr/bin/env python3
import unittest
from qa_reward_callback_fixture import run, Fixture
from mcc_p03_report import analyze


class SyntheticCallbackTests(unittest.TestCase):
    def test_callback_only_model_is_spoofable(self):
        a = run()
        self.assertEqual((3, 0), a['fake_callback_once'])
        self.assertEqual((6, 0), a['fake_callback_duplicate'])
        self.assertEqual((6, 3), a['verified_fixture_final'])

    def test_unverified_ssv_does_not_credit(self):
        f = Fixture()
        f.verified_ssv('bad', False)
        self.assertEqual(0, f.verified_diamonds)

    def test_duplicate_verified_transaction_is_idempotent(self):
        f = Fixture()
        f.verified_ssv('verified-test', True)
        f.verified_ssv('verified-test', True)
        self.assertEqual(3, f.verified_diamonds)

    def test_callback_is_distinct_from_local_write_and_server_credit(self):
        a = analyze('''MCCADID: TRACK ad_id=23 type=rewarded
MCCADID: LOAD_FAILED ad_id=23 type=rewarded
MCCADID: REWARD_CALLBACK ad_id=23 type=rewarded
MCCADID: REWARD_CONTEXT_FAILED_SEEN ad_id=23 type=rewarded
MCCDIAMOND: WRITE_ATTEMPT candidate_id=abcd123456 exact=true requested=500 mode=observe_only''')
        self.assertEqual(1,a['counts']['ad_reward_callback'])
        self.assertEqual(1,a['counts']['diamond_exact_write_attempt'])
        self.assertEqual(1,a['callback_followed_by_local_write_attempts'])
        self.assertTrue(any('without observed impression' in s for s in a['synthetic_review_flags']))

    def test_ad_failure_without_callback_does_not_imply_rewards(self):
        a = analyze('''MCCADID: TRACK ad_id=24 type=banner
MCCADID: LOAD_FAILED ad_id=24 type=banner''')
        self.assertEqual(0,a['counts'].get('ad_reward_callback',0))
        self.assertEqual(0,a['counts'].get('diamond_exact_write_attempt',0))
        self.assertEqual(0,a['callback_followed_by_local_write_attempts'])


if __name__ == '__main__':unittest.main()
