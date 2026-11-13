#!/usr/bin/env python3
"""P10 scoped auto-refill contract; Android build and JVM tests run in hosted CI."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/dev/mikey/mccviplab'
entry = (java / 'HookEntry.java').read_text()
watcher = (java / 'AutoRefillWatcher.java').read_text()
policy = (java / 'AutoRefillPolicy.java').read_text()
workflow = (root / '.github/workflows/android.yml').read_text()
assert entry.count('AutoRefillWatcher.install(appContext);') == 1
assert 'AtomicBoolean STARTED' in watcher
for item in ('FileObserver.CLOSE_WRITE', 'FileObserver.MOVED_TO', 'FileObserver.CREATE',
             'FALLBACK_POLL_MS', 'AutoRefillPolicy.nineOnlyWhenZero',
             'StandardCopyOption.ATOMIC_MOVE', 'stillZero(target',
             'mcc_lab_refill_9_enabled', 'ui_refresh_unverified=true'):
    assert item in watcher, item
assert "contents.length == 1 && contents[0] == (byte) '0'" in policy
assert "new byte[] { (byte) '9' }" in policy
assert 'files.length != 1' in policy
for forbidden in ('getAccessToken(', 'HttpURLConnection', 'URL(', 'setReward(', 'onUserEarnedReward(',
                  'su -c', 'Runtime.getRuntime().exec', 'param.setResult(', 'Settings.Global.put'):
    assert forbidden not in watcher, forbidden
assert 'feature/p10-auto-refill-diamonds-9' in workflow
assert 'python3 scripts/verify_mcc_p10.py' in workflow
assert (root / 'app/src/test/java/dev/mikey/mccviplab/AutoRefillPolicyTest.java').is_file()
print('MCC_P10_SCOPED_ZERO_ONLY_AUTOREFILL_CONTRACT_PASS')
