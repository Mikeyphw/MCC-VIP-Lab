#!/usr/bin/env python3
"""Static safety and scope checks for P03; hosted Gradle performs Android build."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
pkg=root/'app/src/main/java/dev/mikey/mccviplab'
entry=(pkg/'HookEntry.java').read_text()
for invocation in ('AdInstanceTrace.install(loader);',
                   'NativeBannerProbe.install(loader, appContext);',
                   'DiamondProbe.install(appContext);'):
    assert entry.count(invocation)==1,invocation
for cls in ('AdInstanceTrace.java','NativeBannerProbe.java','DiamondProbe.java','DiamondPolicy.java'):
    assert (pkg/cls).is_file(),cls
ad=(pkg/'AdInstanceTrace.java').read_text()
assert 'AdInstanceManager' in ad
assert 'onRewardedAdUserEarnedReward' in ad
assert 'REWARD_CONTEXT_' in ad and 'onRewardedInterstitialAdUserEarnedReward' in ad
assert 'onAdFailedToLoad' in ad
assert 'trackAd' in ad
assert 'setResult(' not in ad, 'ad callback observer must not modify callbacks'
assert 'invokeMethod(' not in ad
banner=(pkg/'NativeBannerProbe.java').read_text()
assert 'getPlatformView' in banner
assert 'View.GONE' in banner
assert 'setResult(' not in banner, 'banner probe must not fabricate plugin results'
diamond=(pkg/'DiamondProbe.java').read_text()
assert 'SharedPreferencesImpl' in diamond
assert 'WRITE_ATTEMPT' in diamond and 'putInt' in diamond and 'putLong' in diamond
assert 'p.args[1] =' not in diamond
assert 'Settings.Global' in diamond
assert 'setResult(changed)' in diamond
assert 'apply(' not in diamond and 'commit(' not in diamond
policy=(pkg/'DiamondPolicy.java').read_text()
assert 'exactKey' in policy
assert 'diamond_cache_timeout' not in policy
assert 'mcc_lab_banner_mode' in banner
assert 'mcc_lab_diamond_mode' in diamond
wf=(root/'.github/workflows/android.yml').read_text()
assert 'android-actions/setup-android@be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd' in wf
assert 'scripts/verify_mcc_p03.py' in wf
assert 'test_mcc_p03*.py' in wf
assert (root/'scripts/mcc_p03_device.py').is_file()
assert (root/'scripts/mcc_p03_report.py').is_file()
assert (root/'scripts/qa_reward_callback_fixture.py').is_file()
assert (root/'scripts/test_mcc_p03_callbacks.py').is_file()
print('MCC_P03_SCOPE_CALLBACK_IMMUTABILITY_AND_DIAGNOSTICS_PASS')
