#!/usr/bin/env python3
"""Static P07 read-only implementation contract; hosted CI builds the Android APK."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
pkg=root/'app/src/main/java/dev/mikey/mccviplab'
entry=(pkg/'HookEntry.java').read_text()
assert entry.count('RewardDetailProbe.install(loader);')==1
src=(pkg/'RewardDetailProbe.java').read_text()
for token in ('onRewardedAdUserEarnedReward','onRewardedInterstitialAdUserEarnedReward',
              'onAdEvent','rewardItem','MCCP07:','SDK_REWARD','BRIDGE_REWARD',
              'RewardDetail.inspect','hookAllMethods'):
    assert token in src,token
for forbidden in ('param.setResult(', 'p.setResult(', 'args[0] =', 'args[1] =',
                  'setReward(', 'onUserEarnedRewardCallback(', 'FileOutputStream(',
                  'getAccessToken(', 'getCustomData(', 'responseBody(', 'getResponseBody('):
    assert forbidden not in src,forbidden
helper=(pkg/'RewardDetail.java').read_text()
assert 'unit = "other"' in helper and 'return new RewardDetail' in helper
assert 'item.toString()' not in helper
capture=(root/'scripts/mcc_p07_capture.py').read_text()
for token in ('MCCP07:', 'MCC_P07_REWARD_AUTHORIZATION_BOUNDARY_READ_ONLY',
              'P07_BACKEND_AUTHORIZATION_NOT_ESTABLISHED','raw_log_saved',
              'mcc_p06_trace', 'server_authorization', 'streaming_sanitized'):
    assert token in capture,token
assert 'subprocess.Popen' in capture and 'p06.capture' in capture
assert 'capture_stdout' not in capture
assert "'NOT_OBSERVED'" in capture
workflow=(root/'.github/workflows/android.yml').read_text()
assert 'scripts/verify_mcc_p07.py' in workflow
assert "test_mcc_p07*.py" in workflow
assert (root/'app/src/test/java/dev/mikey/mccviplab/RewardDetailTest.java').is_file()
assert (root/'scripts/test_mcc_p07.py').is_file()
print('MCC_P07_REWARD_DETAILS_READ_ONLY_CONTRACT_PASS')
