#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/AndroidManifest.xml'
node=ET.parse(p).getroot().find('application')
assert node is not None
android='{http://schemas.android.com/apk/res/android}'
meta={e.attrib.get(android+'name'):e.attrib.get(android+'value') for e in node.findall('meta-data')}
assert meta.get('xposedscope')=='br.art.code.meucronogramacapilar',meta
assert meta.get('xposedmodule')=='true'
assert (root/'app/src/main/assets/xposed_init').read_text().strip()=='dev.mikey.mccviplab.HookEntry'
assert (root/'app/src/main/assets/xposed_scope').read_text().strip()=='br.art.code.meucronogramacapilar'
entry=(root/'app/src/main/java/dev/mikey/mccviplab/HookEntry.java').read_text()
assert 'AdEventProbe.install(loader);' in entry
assert 'TARGET = "br.art.code.meucronogramacapilar"' in entry
probe=(root/'app/src/main/java/dev/mikey/mccviplab/AdEventProbe.java').read_text()
assert 'FLUTTER_BRIDGE_PROBE_INSTALLED_READ_ONLY' in probe
assert 'setResult(' not in probe and '.success(' not in probe
print('MCC_AD_QA_SCOPE_AND_READ_ONLY_CONTRACT_PASS')
