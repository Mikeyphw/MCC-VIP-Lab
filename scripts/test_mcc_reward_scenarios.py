"""Pure offline synthetic events: does NOT inject callbacks into the original app."""
import unittest
from mcc_lab_diagnose import analyze

class RewardScenarioTest(unittest.TestCase):
    def test_failed_load_no_completion(self):
        r = analyze('''11-11 20:01:00.000 I VectorLegacyBridge: MCCADQA: BRIDGE_GROUP onAdFailedToLoad prior_duplicates_collapsed=0\n''')
        self.assertEqual(r['counts']['ad_failure_groups'], 1)
        self.assertEqual(r['counts']['ad_reward_bridge_groups'], 0)
        self.assertFalse(r['heuristic_reward_after_prior_failure'])

    def test_synthetic_completion_after_failure_flagged_not_credited(self):
        r = analyze('''11-11 20:01:00.000 I VectorLegacyBridge: MCCADQA: BRIDGE_GROUP onAdFailedToLoad prior_duplicates_collapsed=0\n11-11 20:01:01.000 I VectorLegacyBridge: MCCADQA: BRIDGE_GROUP rewarded_complete prior_duplicates_collapsed=0\n''')
        self.assertTrue(r['heuristic_reward_after_prior_failure'])
        self.assertEqual(r['counts']['ad_reward_bridge_groups'], 1)
        self.assertNotIn('diamond_credits', r['counts'])  # Never infer credit from callback

    def test_impression_clears_crude_failure_heuristic(self):
        r = analyze('''MCCADQA: BRIDGE_GROUP onAdFailedToLoad prior_duplicates_collapsed=0\nMCCADQA: BRIDGE_GROUP onAdImpression prior_duplicates_collapsed=0\nMCCADQA: BRIDGE_GROUP rewarded_complete prior_duplicates_collapsed=0\n''')
        self.assertFalse(r['heuristic_reward_after_prior_failure'])

    def test_entitlement_before_after_retained(self):
        r = analyze('''MCCVIPSTATE: MAPPER_BEFORE listed_active=false listed_all=false isActive=unknown verification=not_present\nMCCVIPSTATE: MAPPER_AFTER listed_active=true listed_all=true isActive=true verification=VERIFIED\n''')
        self.assertEqual(len(r['vip_observations']), 2)
        self.assertIn('listed_active=false', r['vip_observations'][0]['observation'])

    def test_legacy_noise_not_treated_as_distinct_requests(self):
        r = analyze('''MCCADQA: BRIDGE_EVENT onAdFailedToLoad\nMCCADQA: BRIDGE_EVENT onAdFailedToLoad\n''')
        self.assertEqual(r['counts']['ad_failure_groups'], 0)
        self.assertEqual(r['legacy_ad_failure_entries'], 2)

if __name__ == '__main__': unittest.main()
