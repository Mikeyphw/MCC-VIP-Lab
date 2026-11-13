#!/usr/bin/env python3
"""P10 HF01: proven P09 lifecycle, UI control and scoped module bridge."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/dev/mikey/mccviplab'
get = lambda name: (java / name).read_text(encoding='utf-8')
entry = get('HookEntry.java')
assert entry.count('AutoRefillWatcher.install(appContext);') == 1
assert entry.count('InAppRefillButton.install();') == 1
watcher = get('AutoRefillWatcher.java')
for token in ('AUTO_ZERO', 'RootRefillCommand.PROVIDER_AUTHORITY',
              'FileObserver.CLOSE_WRITE', 'POLL_MS', 'QUIET_PERIOD_MS',
              'AutoRefillPolicy.exactZero', 'getContentResolver().call('):
    assert token in watcher, token
for forbidden in ('new FileOutputStream(', 'Files.move(', 'setResult(', 'ProcessBuilder(',
                  'onRewardedAdUserEarnedReward'):
    assert forbidden not in watcher, forbidden
button = get('InAppRefillButton.java')
for token in ('MANUAL_SET_9', 'getContentResolver().call(', 'FlutterActivity',
              'TARGET_UI_BUTTON_ADDED', 'root.addView(button, lp)', '"♦ Set 9"'):
    assert token in button, token
assert 'AdActivity' not in button
provider = get('ModuleRefillProvider.java')
for token in ('Binder.getCallingUid()', 'getPackagesForUid(caller)',
              'RootRefillCommand.TARGET.equals(name)', 'SecurityException',
              '"AUTO_ZERO"', '"MANUAL_SET_9"'):
    assert token in provider, token
worker = get('AutoRefillWorker.java')
for token in ('new ProcessBuilder("su", "-c", RootRefillCommand.script(auto))',
              'last_auto_attempt_ms', 'waitFor(', 'AUTO_0_TO_9_RESTART_PASS',
              'MANUAL_SET_9_RESTART_PASS'):
    assert token in worker, token
command = get('RootRefillCommand.java')
for token in ('am force-stop', 'printf \'%s\' \'9\'',
              'am start -a android.intent.action.MAIN', 'MCC_REFILL_P09_SEQUENCE_PASS',
              'trap \'restart_target\' EXIT'):
    assert token in command, token
assert command.index('am force-stop') < command.index("printf '%s' '9'")
manifest = ET.fromstring((root/'app/src/main/AndroidManifest.xml').read_text())
ns = '{http://schemas.android.com/apk/res/android}'
application = manifest.find('application')
assert any(a.attrib.get(ns+'name') == '.RefillControlActivity' and
           a.attrib.get(ns+'exported') == 'true' for a in application.findall('activity'))
assert any(p.attrib.get(ns+'name') == '.ModuleRefillProvider' and
           p.attrib.get(ns+'authorities') == 'dev.mikey.mccviplab.refill' and
           p.attrib.get(ns+'exported') == 'true' for p in application.findall('provider'))
workflow = (root/'.github/workflows/android.yml').read_text()
assert 'feature/p10-auto-refill-diamonds-9' in workflow
assert 'python3 scripts/verify_mcc_p10.py' in workflow
assert (root/'app/src/test/java/dev/mikey/mccviplab/RootRefillCommandTest.java').is_file()
assert (root/'app/src/test/java/dev/mikey/mccviplab/AutoRefillPolicyTest.java').is_file()
print('MCC_P10_HF01_P09_LIFECYCLE_UI_AND_RESTART_SOURCE_CONTRACT_PASS')
