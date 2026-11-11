#!/usr/bin/env python3
"""Offline, dependency-free guard against accidentally downgrading Android Actions."""
from pathlib import Path
import re

wf=(Path(__file__).resolve().parents[1]/'.github/workflows/android.yml').read_text(encoding='utf-8')
expected = {
 'actions/checkout': ('fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09', 'v5'),
 'actions/setup-java': ('b6effb05e454b25005698d916606bdc6ffcbf961', 'v5'),
 'gradle/actions/setup-gradle': ('0723195856401067f7a2779048b490ace7a47d7c', 'v5.0.2'),
 'android-actions/setup-android': ('be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd', 'v4'),
}
for name,(sha,version) in expected.items():
 matches=re.findall(r'^\s*uses:\s*'+re.escape(name)+r'@([^\s#]+)\s*(?:#\s*(.*))?$',wf,flags=re.M)
 assert len(matches)==1,(name,'expected exactly one use',matches)
 assert matches[0][0]==sha,(name,'wrong SHA',matches[0][0])
 assert version in matches[0][1].split(),(name,'version label missing',matches[0][1])
for required in ("runs-on: ubuntu-24.04", "java-version: '17'", "gradle-version: '8.11.1'", "platforms;android-35", "build-tools;35.0.0", ":app:testDebugUnitTest :app:assembleDebug", "mcc-vip-lab-debug"):
 assert required in wf, ('missing workflow contract',required)
assert 'setup-android@v3' not in wf and 'setup-android@v4' not in wf, 'expected SHA-pinned setup-android'
print('MCC_VIP_LAB_ANDROID_V4_TOOLCHAIN_CONTRACT_PASS')
