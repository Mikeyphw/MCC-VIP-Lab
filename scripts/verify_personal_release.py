#!/usr/bin/env python3
"""Personal signed artifact must require private signing material and stay unpublished."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re
root=Path(__file__).resolve().parents[1]
w=(root/'.github/workflows/personal-release.yml').read_text()
for n in ('workflow_dispatch','MCC_PERSONAL_KEYSTORE_B64','MCC_PERSONAL_STORE_PASSWORD',
          'MCC_PERSONAL_KEY_ALIAS','MCC_PERSONAL_KEY_PASSWORD',
          ':app:testDebugUnitTest :app:assembleRelease',
          'apksigner" verify', 'mcc-personal-release', 'sha256sum'):
    assert n in w,n
assert 'create-release' not in w and 'gh release create' not in w
assert 'contents: read' in w
assert '.jks' not in '\n'.join(x.as_posix() for x in root.rglob('*') if x.is_file())
gradle=(root/'app/build.gradle').read_text()
assert "versionCode 2" in gradle
assert "versionName '1.0.0-personal.1'" in gradle
assert 'MCC_SIGNING_KEYSTORE_PATH' in gradle
assert 'signingConfig' in gradle
# Google Play's target-SDK lint check blocks private sideload releases targeting
# Android 28. Suppress exactly that one diagnostic, never release lint globally.
def assert_scoped_personal_lint_policy(source):
    assert re.search(r'(?m)^\s*targetSdk\s+28\s*$', source), 'target SDK unexpectedly changed'
    assert not re.search(r'(?i)\babortOnError\s+false\b', source), 'fatal lint disabled'
    assert not re.search(r'(?i)\bcheckReleaseBuilds\s+false\b', source), 'release lint disabled'
    assert not re.search(r'\blintOptions\s*\{', source), 'legacy lintOptions unexpected'
    blocks = re.findall(r'(?ms)^\s*lint\s*\{([^{}]*)\}', source)
    assert len(blocks) == 1, 'expected one scoped lint block'
    directives = [line.strip() for line in blocks[0].splitlines()
                  if line.strip() and not line.lstrip().startswith('//')]
    assert directives == ["disable 'ExpiredTargetSdkVersion'"], 'unapproved lint suppression'

assert_scoped_personal_lint_policy(gradle)
m=ET.fromstring((root/'app/src/main/AndroidManifest.xml').read_text())
assert m.attrib.get('package') is None
print('MCC_PERSONAL_RELEASE_HOSTED_SIGNING_AND_METADATA_CONTRACT_PASS')
