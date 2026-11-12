#!/usr/bin/env python3
"""Static read-only and scope contract (hosted CI separately compiles Android)."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/dev/mikey/mccviplab'
entry=(java/'HookEntry.java').read_text()
assert entry.count('DiamondFileIOTrace.install();')==1
source=(java/'DiamondFileIOTrace.java').read_text()
for s in ('FileInputStream.class', 'FileOutputStream.class', 'RandomAccessFile.class',
          'hookAllConstructors', 'DiamondFileLabel.label'):
    assert s in source,s
helper=(java/'DiamondFileLabel.java').read_text()
for s in ('DiamondBalance', 'LastSyncAzure', 'LastSyncHasura', 'static String label(Object source)'):
    assert s in helper, s
for forbidden in ('de.robv.android.xposed', 'android.', 'XposedBridge', 'XposedHelpers'):
    assert forbidden not in helper, forbidden
junit=(root/'app/src/test/java/dev/mikey/mccviplab/DiamondFileIOTraceTest.java').read_text()
assert 'DiamondFileLabel.label(' in junit
assert 'DiamondFileIOTrace.label(' not in junit
for forbidden in ('param.setResult(', 'p.setResult(', 'FileOutputStream(', 'FileWriter(', 'getContents(',
                  'getBytes(', 'getAbsolutePath()', 'p.args[0] =', 'p.args[1] ='):
    assert forbidden not in source,forbidden
script=(root/'scripts/mcc_p06_trace.py').read_text()
for s in ('sha256sum', 'parse_logcat', 'LastSyncAzure'):
    assert s in script,s
assert "adb(serial, ['shell', 'su -c ' + shlex.quote(root_script())]" in script
assert "'REWARD_CALLBACK', 'REWARD_INTERSTITIAL_CALLBACK'" in script
stubs=(root/'scripts/rebuild_xposed_stubs.py').read_text()
assert 'hookAllConstructors' in stubs
wf=(root/'.github/workflows/android.yml').read_text()
assert 'scripts/verify_mcc_p06.py' in wf and "test_mcc_p06*.py" in wf
assert 'android-actions/setup-android@be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd' in wf
assert (root/'app/src/test/java/dev/mikey/mccviplab/DiamondFileIOTraceTest.java').is_file()
print('MCC_P06_READ_ONLY_SCOPED_LOGGING_AND_TOOLCHAIN_CONTRACT_PASS')
