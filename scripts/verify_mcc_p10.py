#!/usr/bin/env python3
"""P10 personal: source contracts; hosted CI must compile Android and run JVM tests."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/dev/mikey/mccviplab'
get=lambda n:(java/n).read_text(encoding='utf-8')
entry=get('HookEntry.java')
assert entry.count('AutoRefillWatcher.install(appContext);')==1
assert entry.count('InAppRefillButton.install();')==1
assert 'LAST_TESTED_APP_VERSION = "20.2609.3009"' in entry
assert 'record_only=true' in entry
assert 'REFUSED_VERSION' not in entry
button=get('InAppRefillButton.java')
for item in ('findDiamond(', 'AccessibilityNodeProvider.HOST_VIEW_ID',
             'DiamondButtonPlacement.position(', 'setShareIdentityEnabled(true)',
             'RootRefillCommand.ACTION_FROM_TARGET', '♦ 9', 'requestAutomatic()'):
    assert item in button,item
assert 'getContentResolver().call(' not in button
assert 'ProcessBuilder(' not in button
assert 'grant root' not in button.lower()
watcher=get('AutoRefillWatcher.java')
for item in ('InAppRefillButton.requestAutomatic()', 'POLL_MS',
             'QUIET_PERIOD_MS','AutoRefillPolicy.exactZero'):
    assert item in watcher,item
for unwanted in ('ProcessBuilder(', 'getContentResolver().call(', 'Files.move('):
    assert unwanted not in watcher,unwanted
worker=get('AutoRefillWorker.java')
for item in ('new ProcessBuilder("su", "-c", RootRefillCommand.script(auto))',
             'getLaunchIntentSenderForPackage', 'WRITE_9_VERIFIED_',
             'LAUNCH_FAILED_', 'AUTO_SKIP_NONZERO'):
    assert item in worker,item
assert 'ROOT_REFUSED' not in worker
command=get('RootRefillCommand.java')
assert "printf '%s' '9' >" in command
assert 'MCC_REFILL_WRITE_VERIFIED' in command
assert 'am start' not in command
activity=get('RefillControlActivity.java')
for item in ('getLaunchedFromUid()', 'getLaunchedFromPackage()',
             'RootRefillCommand.ACTION_FROM_TARGET', 'startRefill('):
    assert item in activity,item
manifest=ET.fromstring((root/'app/src/main/AndroidManifest.xml').read_text())
ns='{http://schemas.android.com/apk/res/android}'
application=manifest.find('application')
assert not application.findall('provider'), 'Old exported refill provider must be gone'
assert any(x.attrib.get(ns+'name')=='.RefillControlActivity' for x in application.findall('activity'))
assert (java/'DiamondButtonPlacement.java').is_file()
assert (root/'app/src/test/java/dev/mikey/mccviplab/DiamondButtonPlacementTest.java').is_file()
assert (root/'app/src/test/java/dev/mikey/mccviplab/RootRefillCommandTest.java').is_file()
print('MCC_P10_PERSONAL_VERSION_RECORD_BUTTON_ROOT_HANDOFF_CONTRACT_PASS')
